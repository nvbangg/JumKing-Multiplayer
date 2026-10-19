import { useEffect, useRef, useState } from "react";
import { loadManifest } from "../../game/content/assets";
import {
	SpectateRuntime,
	type SpectateStanding,
} from "../../game/netcode/SpectateRuntime";
import { parseMap } from "../../game/simulation/map";
import { api } from "../../network/api";
import type { GameSocket } from "../../network/GameSocket";
import { required } from "../../required";

export function SpectateView({
	socket,
	onStop,
}: {
	socket: GameSocket;
	onStop: () => void;
}) {
	const container = useRef<HTMLElement>(null);
	const runtime = useRef<SpectateRuntime | null>(null);
	const [ready, setReady] = useState(false);
	const [error, setError] = useState("");
	const [players, setPlayers] = useState<SpectateStanding[]>([]);
	const [focus, setFocus] = useState<string | null>(
		socket.spectate?.focusPlayerId ?? null,
	);

	useEffect(() => {
		let disposed = false;
		let game: { destroy(removeCanvas: boolean): void } | undefined;
		const controller = new AbortController();
		const unsubscribe = socket.subscribe((type, payload) => {
			try {
				if (type === "GAME_SNAPSHOT") runtime.current?.receive(payload);
				if (type === "CONNECTION_CLOSED") {
					runtime.current = null;
					setError("Disconnected from the game server");
				}
			} catch (cause) {
				setError(cause instanceof Error ? cause.message : "Invalid game state");
			}
		});
		void (async () => {
			const [renderer, manifest, mapData] = await Promise.all([
				import("../../game/presentation/createGame"),
				loadManifest(controller.signal),
				api<unknown>("/map"),
			]);
			if (disposed) return;
			const state = socket.spectate;
			if (!state) return;
			const session = new SpectateRuntime(
				parseMap(mapData),
				state,
				(snapshot) => {
					setFocus(snapshot.focusPlayerId ?? null);
					setPlayers(snapshot.players || []);
				},
			);
			runtime.current = session;
			if (socket.lastSnapshot) session.receive(socket.lastSnapshot);
			game = renderer.createGame(
				required(container.current),
				manifest,
				session,
				() => setReady(true),
				setError,
			);
		})().catch((cause) => {
			if (!disposed)
				setError(
					cause instanceof Error
						? cause.message
						: "Unable to load the spectate view",
				);
		});
		return () => {
			disposed = true;
			controller.abort();
			unsubscribe();
			game?.destroy(true);
			runtime.current = null;
		};
	}, [socket]);

	const pick = (playerId: string) => {
		setFocus(playerId);
		void socket
			.command("SPECTATE_FOCUS", { playerId })
			.catch((cause) =>
				setError(cause instanceof Error ? cause.message : "Focus failed"),
			);
	};

	return (
		<main className="play-page">
			<section
				className="game-viewport"
				ref={container}
				aria-label="Spectated game"
			/>
			{!ready && !error && (
				<p className="game-message" role="status">
					Loading…
				</p>
			)}
			{ready && (
				<div className="game-hud">
					<span>Spectating</span>
					<button type="button" onClick={onStop}>
						Stop
					</button>
				</div>
			)}
			{ready && (
				<aside className="in-game-panels">
					<section className="players-panel">
						<h2>Players</h2>
						<ul className="live-players">
							{players.map((player) => (
								<li key={player.playerId}>
									<button
										type="button"
										aria-pressed={focus === player.playerId}
										onClick={() => pick(player.playerId)}
									>
										{player.nickname || player.playerId}
									</button>
								</li>
							))}
						</ul>
					</section>
				</aside>
			)}
			{error && (
				<p className="save-error" role="alert">
					{error}
				</p>
			)}
		</main>
	);
}
