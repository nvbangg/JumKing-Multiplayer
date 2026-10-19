import { useEffect, useState } from "react";
import { api } from "../../network/api";

interface LiveRoom {
	roomId: string;
	kind: "ROOM" | "COMMUNITY";
	mode: string;
	mapLevel: number;
	state: string;
	playerCount: number;
	capacity: number;
}

export function LiveRooms({
	kind,
	onSpectate,
}: {
	kind: "ROOM" | "COMMUNITY";
	onSpectate: (roomId: string) => void;
}) {
	const [rooms, setRooms] = useState<LiveRoom[]>([]);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState("");
	const [revision, setRevision] = useState(0);
	// biome-ignore lint/correctness/useExhaustiveDependencies: Revision là tín hiệu của nút Refresh.
	useEffect(() => {
		let active = true;
		setLoading(true);
		setError("");
		void api<{ rooms: LiveRoom[] }>("/rooms/live")
			.then((result) => {
				if (active) setRooms(result.rooms);
			})
			.catch((cause) => {
				if (active) setError(cause.message);
			})
			.finally(() => {
				if (active) setLoading(false);
			});
		return () => {
			active = false;
		};
	}, [revision]);
	const visible = rooms.filter((room) => room.kind === kind);
	const label = (room: LiveRoom) =>
		room.kind === "COMMUNITY"
			? "Community"
			: room.mode
					.split("_")
					.map((word) => word[0] + word.slice(1).toLowerCase())
					.join(" ");
	return (
		<>
			{loading && <p role="status">Loading…</p>}
			{error && <p role="alert">{error}</p>}
			{!loading && !error && !visible.length && (
				<p>No live games available to spectate.</p>
			)}
			<ul className="public-rooms" aria-label="Live games">
				{visible.map((room) => (
					<li key={room.roomId}>
						<button
							type="button"
							disabled={loading}
							onClick={() => onSpectate(room.roomId)}
						>
							{room.roomId} · {label(room)} ·{" "}
							{room.kind === "ROOM" ? `Map ${room.mapLevel} · ` : ""}
							{room.state} · {room.playerCount}/{room.capacity}
						</button>
					</li>
				))}
			</ul>
			<button
				type="button"
				disabled={loading}
				onClick={() => setRevision((value) => value + 1)}
			>
				Refresh
			</button>
		</>
	);
}
