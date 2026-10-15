import type { GameSocket } from "../../network/GameSocket";
import { ChatOverlay } from "./ChatOverlay";
import { useRoomChat } from "./useRoomChat";

export function RoomChat({ socket }: { socket: GameSocket }) {
	const chat = useRoomChat(socket);
	return (
		<>
			<button type="button" aria-keyshortcuts="Enter" onClick={chat.openChat}>
				Chat
			</button>
			<ChatOverlay chat={chat} socket={socket} />
		</>
	);
}
