import type { GameSocket, RoomState } from '../../network/GameSocket';
import { RoomChat } from './RoomChat';
import rules from '../../../../common/rules.json';

export function Lobby({ room, selfId, pending, command, socket }: {
  room: RoomState; selfId: string | null; pending: boolean; command: (type: string, payload?: Record<string, unknown>) => void; socket?: GameSocket | null;
}) {
  const host = room.hostPlayerId === selfId;
  return <>
    <p className="room-code">Room ID <code>{room.roomId}</code></p>
    {room.state === 'COUNTDOWN' && <p role="status">Starting…</p>}
    <ul className="players">{room.members.map(member => <li key={member.playerId}>
      <span>{member.nickname}</span><span>{member.playerId === room.hostPlayerId ? 'Host' : ''}{!member.connected ? ' · Offline' : ''}{member.nextMatchConfirmed ? ' · Confirmed' : ''}</span>
    </li>)}</ul>
    {room.settings && <>
      <label className="mode-setting">Visibility<select aria-label="Visibility" value={room.visibility || 'PRIVATE'} disabled={!host || room.state !== 'WAITING' || pending}
        onChange={event => command('UPDATE_ROOM_SETTINGS', { visibility: event.target.value })}>
        <option value="PRIVATE">Private</option><option value="PUBLIC">Public</option>
      </select></label>
      <label className="mode-setting">Mode<select aria-label="Mode" value={room.settings.mode} disabled={!host || room.state !== 'WAITING' || pending}
        onChange={event => command('UPDATE_ROOM_SETTINGS', { mode: event.target.value, timeLimitMinutes: event.target.value === 'TIME_LIMIT' ? rules.timeLimitDefaultMinutes : null })}>
        <option value="RACE">Race</option><option value="HARDCORE">Hardcore</option><option value="TIME_LIMIT">Time Limit</option><option value="NO_WALK">No Walk</option>
      </select></label>
      {room.settings.mode === 'TIME_LIMIT' && <label className="mode-setting">Minutes<input type="number" min={rules.timeLimitMinutesMin} max={rules.timeLimitMinutesMax}
        value={room.settings.timeLimitMinutes || rules.timeLimitDefaultMinutes} disabled={!host || room.state !== 'WAITING' || pending}
        onChange={event => command('UPDATE_ROOM_SETTINGS', { mode: 'TIME_LIMIT', timeLimitMinutes: Number(event.target.value) })} /></label>}
    </>}
    {host && room.state === 'WAITING' && <button disabled={pending || room.members.filter(member => member.connected && member.nextMatchConfirmed !== false).length < rules.minimumRoomPlayers}
      onClick={() => command('START_GAME')}>Start</button>}
    {host && (room.state === 'PLAYING' || room.state === 'PAUSED') && <>
      <button disabled={pending} onClick={() => command('SET_ROOM_PAUSED', { playId: room.currentPlay?.id, paused: room.state !== 'PAUSED' })}>{room.state === 'PAUSED' ? 'Resume Room' : 'Pause Room'}</button>
      <button disabled={pending} onClick={() => command('END_MATCH')}>End Match</button>
    </>}
    <button disabled={pending} onClick={() => command('LEAVE_ROOM')}>Leave</button>
    {socket && <RoomChat socket={socket} />}
  </>;
}
