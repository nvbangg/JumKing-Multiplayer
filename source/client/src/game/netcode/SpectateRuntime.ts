import rules from "../../../../common/rules.json";
import type { SpectateState } from "../../network/GameSocket";
import { required } from "../../required";
import type {
	GameRuntime,
	RenderPlayer,
	RenderPose,
	WorldFrame,
} from "../presentation/types";
import { findFinishPlatform } from "../simulation/finishPlatform";
import type { GameSnapshot } from "../simulation/GameSimulation";
import { roomMap } from "../simulation/roomMaps";
import { PHYSICS } from "../simulation/tuning";
import type { GameMap } from "../simulation/types";
import type { OnlineSnapshot } from "./OnlineRuntime";
import { PeerInterpolation } from "./PeerInterpolation";

interface SpectatePeer {
	playerId: string;
	nickname: string;
	colorSlot: number;
	crowned: boolean;
	state: RenderPose;
}
export interface SpectateStanding {
	playerId: string;
	nickname: string;
	colorSlot: number;
	rank: number;
	currentProgress: number;
}
export interface SpectateSnapshot {
	roomId: string;
	serverTick: number;
	playId: string;
	generation: number;
	elapsedTimeMs: number;
	spectate?: boolean;
	focusPlayerId?: string;
	players?: SpectateStanding[];
	self: {
		playerId: string;
		nickname: string;
		colorSlot: number;
		crowned: boolean;
		status: string;
		paused: boolean;
		simulation: GameSnapshot;
	};
	peers: SpectatePeer[];
}

const PLACEHOLDER: RenderPose = {
	x: 0,
	y: 0,
	vx: 0,
	vy: 0,
	grounded: false,
	charge: 0,
	direction: 1,
	splat: 0,
	knocked: false,
	ice: false,
	snow: false,
	wind: false,
	screen: 0,
	ticks: 0,
	jumps: 0,
	falls: 0,
};

export class SpectateRuntime implements GameRuntime {
	readonly finishMarker: GameRuntime["finishMarker"];
	latest: SpectateSnapshot | null = null;
	players: SpectateStanding[] = [];
	private peers = new PeerInterpolation(PHYSICS.tickRate);
	constructor(
		map: GameMap,
		private spectate: SpectateState,
		private changed: (snapshot: SpectateSnapshot) => void,
	) {
		if (spectate.kind === "ROOM")
			this.finishMarker = findFinishPlatform(roomMap(map, 10));
	}
	updateSpectate(spectate: SpectateState): void {
		this.spectate = spectate;
	}
	receive(payload: Record<string, unknown>): void {
		const value = payload as unknown as SpectateSnapshot;
		if (
			!value ||
			value.roomId !== this.spectate.roomId ||
			!Number.isSafeInteger(value.serverTick) ||
			value.serverTick < 0 ||
			typeof value.playId !== "string" ||
			!value.self ||
			!value.self.simulation
		)
			throw new Error("Invalid spectate snapshot");
		if (
			this.latest &&
			value.playId === this.latest.playId &&
			value.self.playerId === this.latest.self.playerId &&
			value.serverTick <= this.latest.serverTick
		)
			return;
		this.peers.receive(value as unknown as OnlineSnapshot, performance.now());
		this.latest = value;
		this.players = value.players || [];
		this.changed(value);
	}
	advance(): void {}
	frame(): WorldFrame {
		const color = (slot: number) =>
			rules.appearanceColors[slot] || required(rules.appearanceColors[0]);
		const peers: RenderPlayer[] = this.peers
			.frame(performance.now())
			.map((peer) => ({
				id: peer.playerId,
				nickname: peer.nickname,
				color: color(peer.colorSlot),
				state: peer.state,
				crowned: peer.crowned,
			}));
		const self = this.latest?.self;
		return {
			self: {
				id: self?.playerId || "spectate",
				nickname: self?.nickname || "Player",
				color: color(self?.colorSlot || 0),
				state: self?.simulation.physics || PLACEHOLDER,
				crowned: !!self?.crowned,
				adventure: self?.simulation.adventure,
			},
			peers,
			world: self?.simulation.world,
		};
	}
}
