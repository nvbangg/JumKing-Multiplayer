import type { PlayerState } from '../simulation/types';

export interface RenderPlayer { id: string; nickname: string; color: string; state: PlayerState; equipment: string[] }
export interface WorldFrame { self: RenderPlayer; peers: RenderPlayer[] }
export interface GameRuntime { advance(deltaMs: number): void; frame(): WorldFrame }
