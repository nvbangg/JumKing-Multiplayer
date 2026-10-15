import { useEffect, useRef, useState } from "react";
import { PRESENTATION } from "../../game/presentation/config";
import { api } from "../../network/api";
import type { LeaderboardEntry } from "../../network/contracts";
import { duration } from "../records/format";

export function InGameLeaderboard() {
	const panel = useRef<HTMLDetailsElement>(null);
	const [entries, setEntries] = useState<LeaderboardEntry[] | null>(null),
		[error, setError] = useState("");
	useEffect(() => {
		const element = panel.current!;
		let disposed = false,
			pending = false,
			lastRequest = -Infinity,
			timer: ReturnType<typeof setInterval> | undefined;
		const refresh = async () => {
			const now = performance.now();
			if (
				!element.open ||
				document.hidden ||
				pending ||
				now - lastRequest < PRESENTATION.leaderboardRefreshMs
			)
				return;
			pending = true;
			lastRequest = now;
			try {
				const result = await api<{ entries: LeaderboardEntry[] }>(
					"/leaderboard?limit=" + PRESENTATION.inGameLeaderboardLimit,
				);
				if (!disposed) {
					setEntries(result.entries);
					setError("");
				}
			} catch (cause) {
				if (!disposed)
					setError(
						cause instanceof Error
							? cause.message
							: "Leaderboard is unavailable",
					);
			} finally {
				pending = false;
			}
		};
		const toggle = () => {
			clearInterval(timer);
			if (element.open) {
				void refresh();
				timer = setInterval(() => {
					void refresh();
				}, PRESENTATION.leaderboardRefreshMs);
			}
		};
		const visible = () => {
			if (!document.hidden) void refresh();
		};
		element.addEventListener("toggle", toggle);
		document.addEventListener("visibilitychange", visible);
		return () => {
			disposed = true;
			clearInterval(timer);
			element.removeEventListener("toggle", toggle);
			document.removeEventListener("visibilitychange", visible);
		};
	}, []);
	return (
		<details
			ref={panel}
			className="in-game-panel"
			aria-label="Community leaderboard"
		>
			<summary>Leaderboard</summary>
			{entries && (
				<ol aria-label="Community records">
					{entries.map((entry) => (
						<li key={entry.id}>
							<span>
								{entry.rank}. {entry.username}
							</span>
							<strong>
								{entry.finishTimeMs === null
									? Math.floor(entry.maxProgress) + " px"
									: duration(entry.finishTimeMs)}
							</strong>
						</li>
					))}
				</ol>
			)}
			{entries?.length === 0 && <p>No records yet.</p>}
			{!entries && !error && <p role="status">Loading…</p>}
			{error && <p role="alert">{error}</p>}
		</details>
	);
}
