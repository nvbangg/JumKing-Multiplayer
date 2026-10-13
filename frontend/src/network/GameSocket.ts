import { ApiError, backendOrigin } from './api';
import { uuid } from './ids';

const NETWORK = Object.freeze({ timeoutMs: 8000, heartbeatMs: 10000, maxPending: 64 });
interface Message { v: number; type: string; payload: Record<string, unknown>; requestId?: string }
export interface RoomMember { playerId: string; nickname: string; username: string | null; colorSlot: number; connected: boolean; paused: boolean | null; nextMatchConfirmed: boolean | null }
export interface RoomState {
  roomId: string; kind: 'ROOM' | 'COMMUNITY'; state: 'WAITING' | 'COUNTDOWN' | 'PLAYING' | 'PAUSED' | 'FINISHED';
  hostPlayerId: string | null; settings: { mode: string; timeLimitMinutes: number | null } | null; members: RoomMember[];
  currentPlay: Record<string, unknown> | null; result: unknown; pauseExpiresAt: string | null;
}
export interface ResultRow {
  playerId: string; nickname: string; status: string; rank: number; currentProgress: number;
  maxProgress: number; isWinner: boolean; finishTimeMs: number | null;
}
export interface MatchResult { playId: string; reason: string; durationMs: number; results: ResultRow[]; saveStatus: 'PENDING' | 'SAVED' | 'FAILED' }
export class GameSocket {
  private socket: WebSocket | null = null;
  private heartbeat: ReturnType<typeof setInterval> | null = null;
  private pending = new Map<string, { resolve: (value: Message) => void; reject: (error: Error) => void; timer: ReturnType<typeof setTimeout> }>();
  selfId: string | null = null;
  constructor(private onMessage: (type: string, payload: Record<string, unknown>) => void, private onDisconnect: () => void) {}
  async connect(): Promise<void> {
    if (this.socket?.readyState === WebSocket.OPEN) return;
    const url = new URL('/ws/game', backendOrigin()); url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
    const socket = new WebSocket(url); this.socket = socket;
    await new Promise<void>((resolve, reject) => {
      const timer = setTimeout(() => { socket.close(); reject(new ApiError('CONNECTION_FAILED', 'Connection timed out')); }, NETWORK.timeoutMs);
      socket.onopen = () => { clearTimeout(timer); resolve(); };
      socket.onerror = () => { clearTimeout(timer); reject(new ApiError('CONNECTION_FAILED', 'Unable to connect to the game server')); };
      socket.onclose = () => {
        clearTimeout(timer); reject(new ApiError('CONNECTION_FAILED', 'The game connection was closed'));
        if (this.socket !== socket) return;
        this.clearPending(); if (this.heartbeat) clearInterval(this.heartbeat); this.heartbeat = null; this.onDisconnect();
      };
      socket.onmessage = event => {
        if (this.socket !== socket) return;
        let message: Message;
        try { message = JSON.parse(event.data); }
        catch { socket.close(1002, 'Invalid message'); return; }
        if (message.v !== 1 || typeof message.type !== 'string' || !message.payload || typeof message.payload !== 'object') { socket.close(1002, 'Invalid protocol'); return; }
        const request = message.requestId ? this.pending.get(message.requestId) : undefined;
        if (request && message.requestId) {
          clearTimeout(request.timer); this.pending.delete(message.requestId);
          if (message.type === 'ERROR') request.reject(new ApiError(String(message.payload.code), String(message.payload.message)));
          else request.resolve(message);
        }
        if (message.type === 'ROOM_JOINED' || message.type === 'RECONNECT_OK') this.selfId = String(message.payload.playerId);
        this.onMessage(message.type, message.payload);
      };
    });
    this.heartbeat = setInterval(() => {
      if (socket.readyState === WebSocket.OPEN) socket.send(JSON.stringify({ v: 1, type: 'HEARTBEAT', payload: { nonce: uuid() }, requestId: uuid() }));
    }, NETWORK.heartbeatMs);
  }
  command(type: string, payload: Record<string, unknown> = {}): Promise<Message> {
    if (this.socket?.readyState !== WebSocket.OPEN) return Promise.reject(new ApiError('CONNECTION_FAILED', 'The game connection is unavailable'));
    if (this.pending.size >= NETWORK.maxPending) return Promise.reject(new ApiError('RATE_LIMITED', 'Too many pending commands'));
    const requestId = uuid();
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => { this.pending.delete(requestId); reject(new ApiError('COMMAND_TIMEOUT', 'The server has not confirmed this action')); }, NETWORK.timeoutMs);
      this.pending.set(requestId, { resolve, reject, timer });
      this.socket!.send(JSON.stringify({ v: 1, type, requestId, payload }));
    });
  }
  private clearPending(): void {
    for (const pending of this.pending.values()) { clearTimeout(pending.timer); pending.reject(new ApiError('CONNECTION_FAILED', 'The game connection was closed')); }
    this.pending.clear();
  }
  close(): void {
    const socket = this.socket; this.socket = null;
    if (this.heartbeat) clearInterval(this.heartbeat); this.heartbeat = null; this.clearPending(); socket?.close();
  }
}
