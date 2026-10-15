import type { OnlineSnapshot } from './OnlineRuntime';

export class PeerInterpolation {
  private previous: OnlineSnapshot | null = null;
  private current: OnlineSnapshot | null = null;
  private receivedAt = 0;
  constructor(private tickRate: number) {}
  reset(): void { this.previous = this.current = null; }
  receive(value: OnlineSnapshot, now: number): void { this.previous = this.current; this.current = value; this.receivedAt = now; }
  frame(now: number): OnlineSnapshot['peers'] {
    if (!this.current) return [];
    const current = this.current, previous = this.previous;
    if (!previous || current.serverTick <= previous.serverTick) return current.peers;
    const interval = (current.serverTick - previous.serverTick) * 1000 / this.tickRate;
    const alpha = Math.max(0, Math.min(1, (now - this.receivedAt) / interval));
    return current.peers.map(peer => {
      const old = previous.peers.find(value => value.playerId === peer.playerId);
      if (!old || Math.abs(peer.state.screen - old.state.screen) > 1) return peer;
      return { ...peer, state: { ...peer.state, x: old.state.x + (peer.state.x - old.state.x) * alpha,
        y: old.state.y + (peer.state.y - old.state.y) * alpha, screen: alpha < 1 ? old.state.screen : peer.state.screen } };
    });
  }
}
