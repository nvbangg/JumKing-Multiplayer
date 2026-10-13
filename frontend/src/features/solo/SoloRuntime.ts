import { Simulation } from '../../game/simulation/Simulation';
import { GameSimulation, type GameSnapshot, type WorldEvent } from '../../game/simulation/GameSimulation';
import type { AdventureEvent } from '../../game/simulation/Adventure';
import { PHYSICS } from '../../game/simulation/tuning';
import { PRESENTATION } from '../../game/presentation/config';
import type { GameRuntime, WorldFrame } from '../../game/presentation/types';
import type { GameMap, MovementEffect, PlayerInput, PlayerState } from '../../game/simulation/types';
import rules from '../../../../shared/rules.json';

export class SoloRuntime implements GameRuntime {
  readonly game: GameSimulation;
  readonly simulation: Simulation;
  paused = false;
  private accumulatedMs = 0;
  constructor(map: GameMap, private readInput: () => PlayerInput, private onTick: (state: PlayerState) => void, readonly nickname = 'Player', private onEffects: (effects: MovementEffect[], state: PlayerState) => void = () => {}, private onEvents: (events: AdventureEvent[], world: WorldEvent[], state: GameSnapshot) => void = () => {}) {
    this.game = new GameSimulation(map, __SOLO_FLY_ENABLED__); this.simulation = this.game.physics;
  }
  advance(deltaMs: number): void {
    if (this.paused || this.simulation.finished) { this.accumulatedMs = 0; return; }
    this.accumulatedMs += Math.max(0, Math.min(PRESENTATION.maxFrameMs, deltaMs));
    const step = 1000 / PHYSICS.tickRate;
    while (this.accumulatedMs >= step && !this.simulation.finished && !this.paused) {
      const input = this.readInput();
      if (this.paused) break;
      const events = this.game.tick(input); this.accumulatedMs -= step;
      const state = this.simulation.snapshot(); this.onEffects(events.movement, state); this.onEvents(events.adventure, events.world, this.game.snapshot()); this.onTick(state);
    }
  }
  pause(): void { this.paused = true; this.accumulatedMs = 0; this.simulation.cancelInput(); }
  resume(): void { this.paused = false; this.accumulatedMs = 0; }
  frame(): WorldFrame {
    const snapshot = this.game.snapshot();
    return { self: { id: 'self', nickname: this.nickname, color: rules.appearanceColors[0]!, equipment: snapshot.adventure.equipped, state: snapshot.physics, adventure: snapshot.adventure }, peers: [], world: snapshot.world };
  }
}
