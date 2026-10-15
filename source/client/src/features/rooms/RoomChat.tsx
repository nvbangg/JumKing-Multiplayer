import { useEffect, useState } from 'react';
import { ChatPanel, type ChatMessage } from '../../components/ChatPanel';
import type { GameSocket } from '../../network/GameSocket';
import rules from '../../../../common/rules.json';

export function RoomChat({ socket }: { socket: GameSocket }) {
  const [open, setOpen] = useState(false), [messages, setMessages] = useState(() => socket.chatHistory as unknown as ChatMessage[]);
  useEffect(() => socket.subscribe(type => { if (type === 'CHAT_MESSAGE' || type === 'CHAT_HISTORY') setMessages(socket.chatHistory as unknown as ChatMessage[]); }), [socket]);
  return <><button onClick={() => setOpen(true)}>Chat</button>{open && <ChatPanel messages={messages} colors={rules.appearanceColors} onSend={async text => { await socket.command('CHAT_SEND', { text }); }} onClose={() => setOpen(false)} />}</>;
}
