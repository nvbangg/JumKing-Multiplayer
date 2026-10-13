import { Simulation } from '../../game/simulation/Simulation';
import { PHYSICS } from '../../game/simulation/tuning';
import { PRESENTATION } from '../../game/presentation/config';
import type { GameRuntime, WorldFrame } from '../../game/presentation/types';
import type { GameMap, PlayerInput, PlayerState } from '../../game/simulation/types';
import rules from '../../../../shared/rules.json';

export class SoloRuntime implements GameRuntime {
  readonly simulation: Simulation;
  paused = false;
  private accumulatedMs = 0;
  constructor(map: GameMap, private readInput: () => PlayerInput, private onTick: (state: PlayerState) => void, readonly nickname = 'Player') {
    this.simulation = new Simulation(map, __SOLO_FLY_ENABLED__);
  }
  advance(deltaMs: number): void {
    if (this.paused || this.simulation.finished) { this.accumulatedMs = 0; return; }
    this.accumulatedMs += Math.max(0, Math.min(PRESENTATION.maxFrameMs, deltaMs));
    const step = 1000 / PHYSICS.tickRate;
    while (this.accumulatedMs >= step && !this.simulation.finished) {
      this.simulation.tick(this.readInput()); this.accumulatedMs -= step;
      this.onTick(this.simulation.snapshot());
    }
  }
  pause(): void { this.paused = true; this.accumulatedMs = 0; this.simulation.cancelInput(); }
  resume(): void { this.paused = false; this.accumulatedMs = 0; }
  frame(): WorldFrame {
    return { self: { id: 'self', nickname: this.nickname, color: rules.appearanceColors[0]!, equipment: [], state: this.simulation.snapshot() }, peers: [] };
  }
}
