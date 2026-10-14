import { GameSimulation, type GameSnapshot } from '../simulation/GameSimulation';
import type { GameMap, PlayerInput, PlayerState } from '../simulation/types';
import type { GameRuntime, RenderPlayer, WorldFrame } from '../presentation/types';
import type { GameSocket, RoomState } from '../../network/GameSocket';
import { PHYSICS } from '../simulation/tuning';
import { PRESENTATION } from '../presentation/config';
import rules from '../../../../shared/rules.json';

export interface OnlineSnapshot {
  roomId: string; serverTick: number; playId: string; generation: number; elapsedTimeMs: number; remainingTimeMs: number | null;
  self: { lastProcessedInputSeq: number; status: string; paused: boolean; simulation: GameSnapshot; metrics: Record<string, unknown> };
  peers: { playerId: string; nickname: string; colorSlot: number; state: PlayerState; equipment: string[]; status: string }[];
}
export class OnlineRuntime implements GameRuntime {
  readonly game: GameSimulation;
  latest: OnlineSnapshot | null = null;
  menu = false;
  private accumulatedMs = 0;
  private sequence = 0;
  constructor(map: GameMap, private socket: GameSocket, private room: RoomState, private readInput: () => PlayerInput,
    private changed: (state: PlayerState, snapshot: OnlineSnapshot) => void, flyAllowed: boolean) {
    this.game = new GameSimulation(map, flyAllowed, room.settings?.mode === 'NO_WALK');
  }
  updateRoom(room: RoomState): void { this.room = room; }
  receive(payload: Record<string, unknown>): void {
    const value = payload as unknown as OnlineSnapshot;
    if (value.roomId !== this.room.roomId || !Number.isSafeInteger(value.serverTick) || value.serverTick < 0 || !Number.isSafeInteger(value.generation) || value.generation < 1 || typeof value.playId !== 'string') throw new Error('Invalid game snapshot');
    if (this.latest && value.playId === this.latest.playId && value.generation === this.latest.generation && value.serverTick <= this.latest.serverTick) return;
    if (this.latest && value.playId === this.latest.playId && value.generation < this.latest.generation) return;
    if (!this.latest || value.playId !== this.latest.playId || value.generation !== this.latest.generation) { this.sequence = 0; this.accumulatedMs = 0; }
    this.game.restore(value.self.simulation); this.latest = value; this.changed(this.game.physics.snapshot(), value);
  }
  advance(deltaMs: number): void {
    if (!this.latest || this.menu || this.latest.self.paused || this.latest.self.status !== 'ACTIVE') { this.accumulatedMs = 0; return; }
    this.accumulatedMs += Math.max(0, Math.min(PRESENTATION.maxFrameMs, deltaMs));
    const step = 1000 / PHYSICS.tickRate;
    while (this.accumulatedMs >= step) {
      this.accumulatedMs -= step;
      this.socket.input('PLAYER_INPUT', { playId: this.latest.playId, generation: this.latest.generation, seq: ++this.sequence, ...this.readInput() });
    }
  }
  cancel(): void {
    if (!this.latest) return;
    this.socket.input('CANCEL_INPUT', { playId: this.latest.playId, generation: this.latest.generation, seq: ++this.sequence });
  }
  frame(): WorldFrame {
    const saved = this.game.snapshot(), self = this.room.members.find(member => member.playerId === this.socket.selfId);
    const color = (slot: number) => rules.appearanceColors[slot] || rules.appearanceColors[0]!;
    const peers: RenderPlayer[] = (this.latest?.peers || []).map(peer => ({ id: peer.playerId, nickname: peer.nickname, color: color(peer.colorSlot), state: peer.state, equipment: peer.equipment }));
    return { self: { id: this.socket.selfId || 'self', nickname: self?.nickname || 'Player', color: color(self?.colorSlot || 0), state: saved.physics, equipment: saved.adventure.equipped, adventure: saved.adventure }, peers, world: saved.world };
  }
}
