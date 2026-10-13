import { useEffect, useRef, useState } from 'react';
import { loadManifest } from '../../game/content/assets';
import { parseMap } from '../../game/simulation/map';
import { InputController } from '../../game/input/InputController';
import { SoloRuntime } from './SoloRuntime';
import { PRESENTATION } from '../../game/presentation/config';
import type { PlayerState } from '../../game/simulation/types';
import rules from '../../../../shared/rules.json';
import { SoloWriter, StorageUnavailable } from './storage';
import { TouchControls } from '../../game/input/TouchControls';

export function SoloGame({ profile = 'guest', action = 'auto', expectedRunId = null, onExit }: {
  profile?: string; action?: 'new' | 'continue' | 'auto'; expectedRunId?: string | null; onExit?: () => void;
}) {
  const container = useRef<HTMLDivElement>(null);
  const runtime = useRef<SoloRuntime | null>(null);
  const input = useRef<InputController | null>(null);
  const writer = useRef<SoloWriter | null>(null);
  const pauseRequested = useRef(false);
  const closing = useRef(false);
  const [state, setState] = useState<PlayerState | null>(null);
  const [error, setError] = useState('');
  const [ready, setReady] = useState(false);
  const [paused, setPaused] = useState(false);
  const [saveStatus, setSaveStatus] = useState<'SAVED' | 'PENDING' | 'FAILED'>('PENDING');
  const [saveError, setSaveError] = useState('');
  const [leaving, setLeaving] = useState(false);
  const flush = async () => {
    const session = runtime.current, storage = writer.current;
    if (!session || !storage) return;
    storage.observe(session.simulation.snapshot(), session.simulation.map.progressOriginY, session.simulation.finished);
    setSaveStatus('PENDING');
    try { await storage.save(); setSaveStatus('SAVED'); setSaveError(''); }
    catch (cause) { session.pause(); input.current?.cancel(); setPaused(true); setSaveStatus('FAILED'); setSaveError(cause instanceof Error ? cause.message : 'Could not save Solo'); throw cause; }
  };
  const pause = () => { pauseRequested.current = true; runtime.current?.pause(); input.current?.cancel(); setPaused(true); void flush().catch(() => {}); };
  useEffect(() => {
    const controller = new AbortController();
    let disposed = false;
    let game: { destroy(removeCanvas: boolean): void } | undefined;
    let lastHud = 0;
    let lastActivity = performance.now();
    const activity = () => { lastActivity = performance.now(); };
    const controls = new InputController(activity, pause, () => runtime.current?.simulation.cancelInput());
    input.current = controls;
    const hidden = () => { if (document.hidden) pause(); };
    window.addEventListener('blur', pause); document.addEventListener('visibilitychange', hidden);
    const afk = window.setInterval(() => {
      if (runtime.current && !runtime.current.paused && performance.now() - lastActivity >= rules.afkMs) pause();
    }, PRESENTATION.afkPollMs);
    const autosave = window.setInterval(() => { if (!closing.current) void flush().catch(() => {}); }, rules.soloSaveMs);
    const pageHide = () => { closing.current = true; pause(); void flush().then(() => writer.current?.close()).catch(() => {}); };
    const pageShow = (event: PageTransitionEvent) => { if (event.persisted) location.reload(); };
    window.addEventListener('pagehide', pageHide); window.addEventListener('pageshow', pageShow);
    void (async () => {
      const [renderer, manifest, response] = await Promise.all([
        import('../../game/presentation/createGame'), loadManifest(controller.signal), fetch('/shared/maps/main-babe.json', { signal: controller.signal }),
      ]);
      if (!response.ok) throw new Error('Game map is unavailable');
      const map = parseMap(await response.json());
      if (disposed) return;
      let finished = false;
      let savedScreen = 0;
      const session = new SoloRuntime(map, () => controls.read(), frame => {
        writer.current?.observe(frame, map.progressOriginY, session.simulation.finished);
        if (frame.screen !== savedScreen) { savedScreen = frame.screen; void flush().catch(() => {}); }
        if (session.simulation.finished && !finished) { finished = true; controls.cancel(); void flush().catch(() => {}); }
        const now = performance.now();
        if (now - lastHud >= PRESENTATION.hudIntervalMs) { setState(frame); lastHud = now; }
      });
      const storage = await SoloWriter.open(profile, action, session.simulation.snapshot(), expectedRunId).catch(cause => {
        if (cause instanceof StorageUnavailable && action !== 'continue' && expectedRunId === null) { setSaveStatus('FAILED'); setSaveError(cause.message); return null; }
        throw cause;
      });
      if (disposed) { await storage?.close(); return; }
      try { if (storage?.run.snapshot) { session.simulation.restore(storage.run.snapshot); session.simulation.cancelInput(); savedScreen = storage.run.snapshot.screen; } }
      catch (cause) { await storage?.close(); throw cause; }
      writer.current = storage;
      runtime.current = session;
      if (pauseRequested.current) { session.pause(); controls.cancel(); }
      if (storage) setSaveStatus('SAVED');
      activity();
      game = renderer.createGame(container.current!, manifest, session, () => { setReady(true); setState(session.simulation.snapshot()); }, message => { session.pause(); controls.cancel(); setError(message); });
    })().catch(cause => { if (!disposed) setError(cause instanceof Error ? cause.message : 'Unable to load game'); });
    return () => {
      disposed = true; controller.abort(); clearInterval(afk); clearInterval(autosave);
      const storage = writer.current, session = runtime.current;
      if (storage && session) { session.pause(); storage.observe(session.simulation.snapshot(), session.simulation.map.progressOriginY, session.simulation.finished); void storage.save().finally(() => storage.close()).catch(() => {}); }
      writer.current = null; controls.dispose(); game?.destroy(true); runtime.current = null; input.current = null;
      window.removeEventListener('blur', pause); document.removeEventListener('visibilitychange', hidden);
      window.removeEventListener('pagehide', pageHide); window.removeEventListener('pageshow', pageShow);
    };
  }, [profile, action, expectedRunId]);
  const leave = async () => {
    if (leaving) return;
    if (!writer.current && !window.confirm('Leave without saving this game?')) return;
    closing.current = true; pause(); setLeaving(true);
    try { await flush(); await writer.current?.close(); writer.current = null; if (onExit) onExit(); else location.href = '/'; }
    catch { closing.current = false; setLeaving(false); }
  };
  const discardUnsaved = async () => {
    if (leaving || !window.confirm('Leave without saving your latest progress?')) return;
    closing.current = true; runtime.current?.pause(); input.current?.cancel(); setLeaving(true);
    try { await writer.current?.close(); }
    catch { /* Giải phóng phiên ngay cả khi storage không còn truy cập được. */ }
    writer.current = null;
    if (onExit) onExit(); else location.href = '/';
  };
  return <main className="play-page">
    <div className="game-viewport" ref={container} aria-label="Main Babe game" />
    {!ready && !error && <p className="game-message" role="status">Loading…</p>}
    {error && <div className="game-message" role="alert"><p>{error}</p><button onClick={() => { void (async () => {
      try { await writer.current?.close(); } catch { }
      writer.current = null;
      if (onExit) onExit(); else location.href = '/';
    })(); }}>Back</button></div>}
    {saveError && <p className="save-error" role="alert">{saveError}</p>}
    {ready && <div className="game-hud"><span>Jumps: {state?.jumps ?? 0}</span><button onClick={pause}>Menu</button></div>}
    {ready && !paused && <TouchControls input={input} flying={state?.freeFlight} />}
    {paused && <section className="game-overlay" aria-label="Game menu"><h2>Paused</h2>
      <button disabled={leaving} onClick={() => { pauseRequested.current = false; input.current?.resume(); runtime.current?.resume(); setPaused(false); }}>Resume</button>
      <button disabled={leaving} onClick={() => { void leave(); }}>{leaving ? 'Saving…' : 'Save & Exit'}</button>
      {saveStatus === 'FAILED' && <button disabled={leaving} onClick={() => { void discardUnsaved(); }}>Leave Without Saving</button>}
      <small role="status">{saveStatus === 'SAVED' ? 'Saved' : saveStatus === 'PENDING' ? 'Saving…' : 'Save failed'}</small>
    </section>}
  </main>;
}
