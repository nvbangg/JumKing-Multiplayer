import { useEffect, useRef, useState } from 'react';
import { NETWORK } from '../network/config';

export interface ChatMessage { id: string; playerId: string; nickname: string; text: string; sentAt: string; colorSlot: number }
export function ChatMessages({ messages, colors }: { messages: ChatMessage[]; colors: readonly string[] }) {
  const list = useRef<HTMLDivElement>(null);
  useEffect(() => { if (list.current) list.current.scrollTop = list.current.scrollHeight; }, [messages]);
  return <div ref={list} className="chat-messages" role="log" aria-label="Messages" aria-live="polite">{messages.map(message => <p key={message.id}><strong style={{ color: colors[message.colorSlot] || '#ffffff' }}>{message.nickname}: </strong>{message.text}</p>)}</div>;
}
export function ChatPanel({ messages, colors, onSend, onClose, focusRequest = 0 }: {
  messages: ChatMessage[]; colors: readonly string[]; onSend: (text: string) => Promise<void>; onClose: () => void; focusRequest?: number;
}) {
  const [text, setText] = useState(''), [error, setError] = useState(''), [sending, setSending] = useState(false);
  const input = useRef<HTMLInputElement>(null);
  useEffect(() => { input.current?.focus(); }, [focusRequest]);
  return <section className="chat-panel" aria-label="Room chat" onKeyDown={event => { if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); onClose(); } }}>
    <header><span>Chat</span><button onClick={onClose} aria-label="Close chat">×</button></header>
    <ChatMessages messages={messages} colors={colors} />
    <form onSubmit={event => {
      event.preventDefault(); if (sending || !text.trim()) return;
      if ([...text].length > NETWORK.maxChatCodePoints) { setError('Messages can contain up to ' + NETWORK.maxChatCodePoints + ' characters'); return; }
      setSending(true); setError(''); void onSend(text).then(() => { setText(''); input.current?.focus(); }).catch(cause => setError(cause instanceof Error ? cause.message : 'Message could not be sent')).finally(() => setSending(false));
    }}><label className="visually-hidden" htmlFor="chat-message">Message</label><input ref={input} id="chat-message" value={text} onChange={event => setText(event.currentTarget.value)} autoComplete="off" /><button disabled={sending || !text.trim()}>Send</button></form>
    {error && <p role="alert">{error}</p>}
  </section>;
}
