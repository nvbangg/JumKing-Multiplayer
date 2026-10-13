import type { PlayerState } from '../simulation/types';
import type { AdventureState } from '../simulation/Adventure';
import type { WorldState } from '../simulation/GameSimulation';

export interface RenderPlayer { id: string; nickname: string; color: string; state: PlayerState; equipment: string[]; adventure?: AdventureState }
export interface WorldFrame { self: RenderPlayer; peers: RenderPlayer[]; world?: WorldState }
export interface GameRuntime { advance(deltaMs: number): void; frame(): WorldFrame }
