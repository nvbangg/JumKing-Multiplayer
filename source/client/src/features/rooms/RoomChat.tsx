import type { GameSocket } from '../../network/GameSocket';
import { useRoomChat } from './useRoomChat';
import { ChatOverlay } from './ChatOverlay';

export function RoomChat({ socket }: { socket: GameSocket }) {
  const chat = useRoomChat(socket);
  return <><button aria-keyshortcuts="Enter" onClick={chat.openChat}>Chat</button><ChatOverlay chat={chat} socket={socket} /></>;
}
