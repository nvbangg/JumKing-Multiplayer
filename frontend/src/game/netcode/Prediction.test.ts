import { expect, test, vi } from 'vitest';
import mapData from '../../../../shared/maps/main-babe.json';
import { parseMap } from '../simulation/map';
import { GameSimulation } from '../simulation/GameSimulation';
import { Prediction } from './Prediction';
import { PeerInterpolation } from './PeerInterpolation';
import { OnlineRuntime, type OnlineSnapshot } from './OnlineRuntime';
import { GameSocket, type RoomState } from '../../network/GameSocket';

const map = parseMap(mapData), right = { left: false, right: true, jump: false };
const room: RoomState = { roomId: 'ABCDEF', kind: 'COMMUNITY', state: 'PLAYING', hostPlayerId: null, settings: null, currentPlay: null, result: null, pauseExpiresAt: null,
  members: [{ playerId: 'self', nickname: 'Player', username: 'Player', colorSlot: 0, connected: true, paused: false, nextMatchConfirmed: null }] };
function snapshot(game: GameSimulation, tick: number, ack: number, generation = 1): OnlineSnapshot {
  return { roomId: room.roomId, serverTick: tick, playId: 'run', generation, elapsedTimeMs: 0, remainingTimeMs: null,
    self: { lastProcessedInputSeq: ack, status: 'ACTIVE', paused: false, simulation: game.snapshot(), metrics: {} }, peers: [] };
}
test('online ending waits for the private committed notice and matching finished snapshot', () => {
  const socket = new GameSocket(() => {}, () => {}); socket.selfId = 'self'; socket.playContext = { playId: 'run', generation: 1 };
  const game = new GameSimulation(map), runtime = new OnlineRuntime(map, socket, room, () => right, () => {}, false);
  const notice = { playerId: 'self', playId: 'run', showEnding: true, ending: { screen: 42, equipped: ['Shoes'], ravenDone: true } };
  runtime.receive(snapshot(game, 1, 0) as unknown as Record<string, unknown>);
  runtime.finish({ ...notice, playerId: 'other' }); expect(runtime.frame().ending).toBeUndefined();
  runtime.finish(notice); expect(runtime.frame().ending).toBeUndefined();
  const finished = snapshot(game, 2, 0); finished.self.status = 'FINISHED'; finished.self.paused = true;
  runtime.receive(finished as unknown as Record<string, unknown>);
  expect(runtime.frame().ending).toEqual({ id: 'run', screen: 42, equipped: ['Shoes'], ravenDone: true });
  runtime.finish({ ...notice, ending: { ...notice.ending, equipped: ['Crown'] } }); expect(runtime.frame().ending!.equipped).toEqual(['Shoes']);
  socket.playContext = { playId: 'next', generation: 2 };
  runtime.receive({ ...snapshot(game, 3, 0, 2), playId: 'next' } as unknown as Record<string, unknown>);
  expect(runtime.frame().ending).toBeUndefined(); runtime.finish(notice); expect(runtime.frame().ending).toBeUndefined();
});
test('prediction responds before an ACK and replays only unacknowledged input after correction', () => {
  const local = new GameSimulation(map), server = new GameSimulation(map), prediction = new Prediction(local, 180);
  for (let seq = 1; seq <= 20; seq++) { prediction.push(seq, right); if (seq <= 7) server.tick(right); }
  const before = local.snapshot(); expect(before.physics.x).toBeGreaterThan(map.spawn.x);
  prediction.reconcile(server.snapshot(), 7, true);
  expect(prediction.pending).toBe(13); expect(local.snapshot()).toEqual(before);
  prediction.reconcile(before, 20, true); expect(prediction.pending).toBe(0); expect(local.snapshot()).toEqual(before);
});
test('input overflow is explicit, and cancel drops pending jump edges without launching', () => {
  const game = new GameSimulation(map), prediction = new Prediction(game, 2);
  prediction.push(1, { ...right, jump: true }); prediction.push(2, { ...right, jump: true }); const before = game.snapshot();
  expect(() => prediction.push(3, right)).toThrow('acknowledgements'); expect(game.snapshot()).toEqual(before);
  prediction.cancel(3); expect(prediction.pending).toBe(1); expect(game.physics.snapshot().charge).toBe(0); expect(game.physics.snapshot().jumps).toBe(0);
  const authoritative = new GameSimulation(map); authoritative.physics.cancelInput(); authoritative.tick({ left: false, right: false, jump: false });
  prediction.reconcile(authoritative.snapshot(), 3, true); expect(prediction.pending).toBe(0); expect(game.physics.snapshot().jumps).toBe(0);
});
test('paused reconciliation restores authority without replay and rejects malformed ACK', () => {
  const game = new GameSimulation(map), server = new GameSimulation(map), prediction = new Prediction(game, 180);
  prediction.push(1, right); prediction.push(2, right); prediction.reconcile(server.snapshot(), 0, false);
  expect(game.snapshot()).toEqual(server.snapshot()); expect(prediction.pending).toBe(2);
  expect(() => prediction.reconcile(server.snapshot(), 1.5, true)).toThrow('acknowledgement'); prediction.reset(); expect(prediction.pending).toBe(0);
});
test('remote interpolation follows server tick spacing and never extrapolates or resurrects removed peers', () => {
  const game = new GameSimulation(map), previous = snapshot(game, 3, 0), current = snapshot(game, 6, 0);
  const peer = { playerId: 'other', nickname: 'Other', colorSlot: 1, state: game.physics.snapshot(), equipment: [], status: 'ACTIVE' };
  previous.peers = [{ ...peer, state: { ...peer.state, x: 100 } }]; current.peers = [{ ...peer, state: { ...peer.state, x: 120 } }];
  const interpolation = new PeerInterpolation(60); interpolation.receive(previous, 0); interpolation.receive(current, 100);
  expect(interpolation.frame(125)[0]!.state.x).toBe(110); expect(interpolation.frame(200)[0]!.state.x).toBe(120);
  interpolation.receive({ ...current, serverTick: 9, peers: [] }, 200); expect(interpolation.frame(220)).toEqual([]);
});
test('runtime ignores stale snapshots and clears sequence/prediction on generation change', () => {
  const socket = new GameSocket(() => {}, () => {}); socket.selfId = 'self'; socket.input = vi.fn();
  const server = new GameSimulation(map), runtime = new OnlineRuntime(map, socket, room, () => right, () => {}, false);
  runtime.receive(snapshot(server, 1, 0) as unknown as Record<string, unknown>); runtime.advance(51);
  expect(runtime.game.physics.snapshot().x).toBeGreaterThan(map.spawn.x); expect(socket.input).toHaveBeenCalledTimes(3);
  server.tick(right); runtime.receive(snapshot(server, 2, 1) as unknown as Record<string, unknown>);
  const corrected = runtime.game.snapshot(); runtime.receive(snapshot(server, 1, 0) as unknown as Record<string, unknown>); expect(runtime.game.snapshot()).toEqual(corrected);
  runtime.receive(snapshot(server, 2, 0, 2) as unknown as Record<string, unknown>); runtime.advance(17);
  expect(socket.input).toHaveBeenLastCalledWith('PLAYER_INPUT', expect.objectContaining({ generation: 2, seq: 1 }));
  runtime.receive(snapshot(server, 100, 2, 1) as unknown as Record<string, unknown>); expect(runtime.latest!.generation).toBe(2);
  socket.playContext = { playId: 'run', generation: 3 }; const count = vi.mocked(socket.input).mock.calls.length;
  runtime.advance(100); runtime.cancel(); expect(socket.input).toHaveBeenCalledTimes(count);
  runtime.receive({ ...snapshot(server, 101, 1, 2), self: { ...snapshot(server, 101, 1, 2).self, paused: true } } as unknown as Record<string, unknown>);
  expect(runtime.latest!.serverTick).toBe(2);
});
