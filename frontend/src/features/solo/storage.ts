import type { PlayerState } from '../../game/simulation/types';
import { PHYSICS } from '../../game/simulation/tuning';
import { uuid } from '../../network/ids';

const STORE = Object.freeze({ name: 'jumpking', version: 1, writerLeaseMs: 10000 });
export type RunStatus = 'ACTIVE' | 'FINISHED' | 'ABANDONED';
export interface SoloRun {
  id: string; profile: string; revision: number; syncedRevision: number; status: RunStatus;
  playTicks: number; jumps: number; falls: number; currentProgress: number; maxProgress: number;
  peakReachedTicks: number; finishTicks: number | null; startedAt: string; endedAt: string | null;
  snapshot: PlayerState | null;
}
interface Profile { key: string; activeRunId: string | null; writerId: string | null; leaseExpiresAt: number }

export class StorageUnavailable extends Error {
  constructor(message = 'Browser storage is unavailable. This game cannot be saved.') { super(message); }
}
function request<T>(value: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => { value.onsuccess = () => resolve(value.result); value.onerror = () => reject(value.error || new Error('Browser storage failed')); });
}
function completed(tx: IDBTransaction): Promise<void> {
  return new Promise((resolve, reject) => { tx.oncomplete = () => resolve(); tx.onabort = tx.onerror = () => reject(tx.error || new Error('Browser storage transaction failed')); });
}
export async function openStore(): Promise<IDBDatabase> {
  if (!globalThis.indexedDB) throw new StorageUnavailable();
  return new Promise<IDBDatabase>((resolve, reject) => {
    const open = indexedDB.open(STORE.name, STORE.version);
    let blocked = false;
    open.onupgradeneeded = () => {
      const db = open.result;
      db.createObjectStore('profiles', { keyPath: 'key' });
      db.createObjectStore('runs', { keyPath: 'id' }).createIndex('profile', 'profile');
    };
    open.onerror = () => reject(open.error || new Error('Browser storage is unavailable'));
    open.onblocked = () => { blocked = true; reject(new Error('Close other JumpKing tabs to update browser storage')); };
    open.onsuccess = () => { const db = open.result; if (blocked) { db.close(); return; } db.onversionchange = () => db.close(); resolve(db); };
  }).catch(cause => { throw new StorageUnavailable(cause instanceof Error ? cause.message : undefined); });
}
export async function soloRuns(profile: string): Promise<SoloRun[]> {
  const db = await openStore();
  try { return await request(db.transaction('runs').objectStore('runs').index('profile').getAll(profile)); }
  finally { db.close(); }
}
export async function currentSolo(profile: string): Promise<SoloRun | null> {
  const runs = await soloRuns(profile);
  return runs.find(run => run.status === 'ACTIVE') || null;
}
function terminalTime(startedAt: string): string { return new Date(Math.max(Date.now(), Date.parse(startedAt))).toISOString(); }
async function exclusiveWriter(profile: string): Promise<(() => Promise<void>) | null> {
  if (!navigator.locks) return null;
  let release!: () => void;
  let accept!: (granted: boolean) => void;
  let fail!: (cause: unknown) => void;
  const granted = new Promise<boolean>((resolve, reject) => { accept = resolve; fail = reject; });
  const held = navigator.locks.request('jumpking-solo:' + profile, { ifAvailable: true }, async lock => {
    if (!lock) { accept(false); return; }
    const lifetime = new Promise<void>(resolve => { release = resolve; });
    accept(true); await lifetime;
  });
  void held.catch(fail);
  if (!await granted) throw new Error('Solo is open in another tab. Close it and try again.');
  return async () => { release(); await held; };
}

export class SoloWriter {
  private queue: Promise<unknown> = Promise.resolve();
  private closed = false;
  private committedRevision: number;
  private observed: SoloRun;
  private constructor(private db: IDBDatabase, readonly writerId: string, run: SoloRun, private releaseLock: (() => Promise<void>) | null) {
    this.observed = structuredClone(run); this.committedRevision = run.revision;
  }
  get run(): SoloRun { return structuredClone(this.observed); }

  static async open(profile: string, action: 'new' | 'continue' | 'auto', initial: PlayerState, expectedRunId: string | null = null): Promise<SoloWriter> {
    const db = await openStore(), writerId = uuid();
    let releaseLock: (() => Promise<void>) | null;
    try { releaseLock = await exclusiveWriter(profile); }
    catch (cause) { db.close(); throw cause; }
    let tx: IDBTransaction;
    try { tx = db.transaction(['profiles', 'runs'], 'readwrite'); }
    catch (cause) { db.close(); await releaseLock?.(); throw cause; }
    const done = completed(tx);
    try {
      const profiles = tx.objectStore('profiles'), runs = tx.objectStore('runs');
      const existing: Profile | undefined = await request(profiles.get(profile));
      if (!releaseLock && existing?.writerId && existing.leaseExpiresAt > Date.now()) throw new Error('Solo is open in another tab. Close it and try again.');
      let run: SoloRun | undefined = existing?.activeRunId ? await request(runs.get(existing.activeRunId)) : undefined;
      if (action === 'continue' && (!run || run.status !== 'ACTIVE' || !run.snapshot)) throw new Error('No unfinished Solo game is available');
      if (action === 'new' && (run?.id || null) !== expectedRunId) throw new Error('The Solo save changed. Return to the menu and try again.');
      if (action === 'new' || !run) {
        if (run?.status === 'ACTIVE') runs.put({ ...run, status: 'ABANDONED', endedAt: terminalTime(run.startedAt), snapshot: null, revision: run.revision + 1 });
        run = {
          id: uuid(), profile, revision: 1, syncedRevision: 0, status: 'ACTIVE',
          playTicks: 0, jumps: 0, falls: 0, currentProgress: 0, maxProgress: 0, peakReachedTicks: 0,
          finishTicks: null, startedAt: new Date().toISOString(), endedAt: null, snapshot: structuredClone(initial),
        };
        runs.put(run);
      }
      profiles.put({ key: profile, activeRunId: run.id, writerId, leaseExpiresAt: Date.now() + STORE.writerLeaseMs } satisfies Profile);
      await done;
      return new SoloWriter(db, writerId, run, releaseLock);
    } catch (error) { try { tx.abort(); } catch { } await done.catch(() => {}); db.close(); await releaseLock?.(); throw error; }
  }

  observe(state: PlayerState, progressOriginY: number, finished: boolean): void {
    if (this.observed.status !== 'ACTIVE') return;
    const progress = Math.max(0, progressOriginY - state.y - PHYSICS.bodyHeight);
    if (progress > this.observed.maxProgress) { this.observed.maxProgress = progress; this.observed.peakReachedTicks = state.ticks; }
    Object.assign(this.observed, { playTicks: state.ticks, jumps: state.jumps, falls: state.falls, currentProgress: progress, snapshot: structuredClone(state) });
    if (finished) Object.assign(this.observed, { status: 'FINISHED', finishTicks: state.ticks, endedAt: terminalTime(this.observed.startedAt) });
  }

  save(): Promise<SoloRun> {
    const view = this.run;
    const pending = this.queue.then(async () => {
      if (this.closed) throw new Error('Solo writer is closed');
      const tx = this.db.transaction(['profiles', 'runs'], 'readwrite'), done = completed(tx);
      try {
        const profiles = tx.objectStore('profiles'), runs = tx.objectStore('runs');
        const profile: Profile = await request(profiles.get(view.profile));
        const stored: SoloRun = await request(runs.get(view.id));
        if (!profile || profile.writerId !== this.writerId || !stored || stored.revision !== this.committedRevision) throw new Error('Solo is active in another tab. This tab has been paused.');
        const next: SoloRun = { ...view, revision: stored.revision + 1, syncedRevision: stored.syncedRevision, snapshot: view.status === 'ACTIVE' ? view.snapshot : null };
        const metricsSame = ['status', 'playTicks', 'jumps', 'falls', 'currentProgress', 'maxProgress', 'peakReachedTicks', 'finishTicks'].every(key => stored[key as keyof SoloRun] === view[key as keyof SoloRun]);
        if (metricsSame && JSON.stringify(stored.snapshot) === JSON.stringify(next.snapshot)) {
          profiles.put({ ...profile, leaseExpiresAt: Date.now() + STORE.writerLeaseMs }); await done; return stored;
        }
        if (stored.status !== 'ACTIVE') {
          if (stored.status !== view.status || stored.playTicks !== view.playTicks) throw new Error('This Solo game has already ended');
          profiles.put({ ...profile, leaseExpiresAt: Date.now() + STORE.writerLeaseMs });
          await done;
          return stored;
        }
        runs.put(next);
        profiles.put({ ...profile, activeRunId: next.status === 'ACTIVE' ? next.id : null, leaseExpiresAt: Date.now() + STORE.writerLeaseMs });
        await done;
        this.committedRevision = next.revision;
        this.observed.revision = next.revision; this.observed.syncedRevision = next.syncedRevision;
        return next;
      } catch (error) { try { tx.abort(); } catch { } await done.catch(() => {}); throw error; }
    });
    this.queue = pending.catch(() => {});
    return pending;
  }

  async close(): Promise<void> {
    await this.queue;
    if (this.closed) return;
    this.closed = true;
    try {
      const tx = this.db.transaction('profiles', 'readwrite'), done = completed(tx);
      const store = tx.objectStore('profiles');
      const profile: Profile = await request(store.get(this.observed.profile));
      if (profile?.writerId === this.writerId) store.put({ ...profile, writerId: null, leaseExpiresAt: 0 });
      await done;
    } finally { this.db.close(); await this.releaseLock?.(); }
  }
}
