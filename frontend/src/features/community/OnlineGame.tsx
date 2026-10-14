import { useEffect, useRef, useState } from 'react';
import { GameSocket, type RoomState } from '../../network/GameSocket';
import { api } from '../../network/api';
import { OnlineRuntime } from '../../game/netcode/OnlineRuntime';
import { loadManifest } from '../../game/content/assets';
import { parseMap } from '../../game/simulation/map';
import { InputController } from '../../game/input/InputController';
import { TouchControls } from '../../game/input/TouchControls';
import { AudioBus } from '../../game/presentation/AudioBus';
import { MenuPanel } from '../../components/MenuPanel';
import { OptionsButtons, ControlsHelp } from '../solo/OptionsPanel';
import { readOptions, saveOptions, type Options } from '../solo/options';
import { duration } from '../records/format';
import { PlayerEffects } from '../../game/netcode/PlayerEffects';
import { PRESENTATION } from '../../game/presentation/config';
import { ChatPanel, type ChatMessage } from '../../components/ChatPanel';
import rules from '../../../../shared/rules.json';
import { FlightToggle } from '../../game/input/FlightToggle';
import { ADVENTURE, type AdventureState } from '../../game/simulation/Adventure';
import { AdventureEffects } from '../../game/netcode/AdventureEffects';

export function OnlineGame({ socket, room, onHome, onResults }: { socket: GameSocket; room: RoomState; onHome: () => void; onResults: () => void }) {
  const container = useRef<HTMLDivElement>(null), runtime = useRef<OnlineRuntime | null>(null), input = useRef<InputController | null>(null), audio = useRef<AudioBus | null>(null);
  const [ready, setReady] = useState(false), [menu, setMenu] = useState<'pause' | 'options' | 'controls' | 'inventory' | null>(null);
  const [inventory, setInventory] = useState<AdventureState | null>(null);
  const flight = useRef<FlightToggle | null>(null);
  const [flying, setFlying] = useState(false);
  const [options, setOptions] = useState(readOptions), [flyAllowed, setFlyAllowed] = useState(false);
  const [error, setError] = useState(''), [pending, setPending] = useState(false), [elapsed, setElapsed] = useState(0), [jumps, setJumps] = useState(0), [saveStatus, setSaveStatus] = useState('SAVED');
  const [transferId, setTransferId] = useState('');
  const [remaining, setRemaining] = useState<number | null>(null);
  const [disconnected, setDisconnected] = useState(false);
  const [chatOpen, setChatOpen] = useState(false), [messages, setMessages] = useState<ChatMessage[]>(() => socket.chatHistory as unknown as ChatMessage[]);
  const [ending, setEnding] = useState<'playing' | 'picture' | null>(null), [pictureReady, setPictureReady] = useState(false);
  const endingActive = useRef(false), skipEnding = useRef<(() => void) | null>(null);
  useEffect(() => { if (ending !== 'picture') return; const timer = setTimeout(() => setPictureReady(true), PRESENTATION.endingPictureGuardMs); return () => clearTimeout(timer); }, [ending]);
  const latestRoom = useRef(room); latestRoom.current = room;
  const pauseRef = useRef<() => void>(() => {});
  const pause = () => {
    if (endingActive.current) { skipEnding.current?.(); return; }
    if (!runtime.current || runtime.current.menu) return;
    runtime.current.menu = true; runtime.current.cancel(); flight.current?.cancel(); input.current?.cancel(); audio.current?.pause(true); setMenu('pause');
    if (room.kind === 'COMMUNITY') { setSaveStatus('PENDING'); void socket.command('SET_COMMUNITY_PAUSED', { paused: true }).catch(cause => setError(cause.message)); }
  };
  pauseRef.current = pause;
  useEffect(() => { runtime.current?.updateRoom(room); if (room.kind === 'ROOM' && room.state === 'PAUSED') pauseRef.current(); }, [room]);
  useEffect(() => { setMessages(socket.chatHistory as unknown as ChatMessage[]); setChatOpen(false); }, [room.roomId, socket]);
  useEffect(() => {
    let disposed = false, game: { destroy(removeCanvas: boolean): void } | undefined;
    const controller = new AbortController();
    let lastActivity = performance.now();
    const effects = new PlayerEffects(), adventureEffects = new AdventureEffects();
    let cueManifest: Awaited<ReturnType<typeof loadManifest>> | null = null;
    const controls = new InputController(() => { lastActivity = performance.now(); }, () => pauseRef.current(), () => { runtime.current?.cancel(); flight.current?.cancel(); }, now => flight.current?.press(now)); input.current = controls;
    const afk = window.setInterval(() => { if (latestRoom.current.kind === 'ROOM' && runtime.current && !runtime.current.menu && performance.now() - lastActivity >= rules.afkMs) pauseRef.current(); }, PRESENTATION.afkPollMs);
    const hidden = () => { if (document.hidden) pauseRef.current(); }; const blur = () => pauseRef.current();
    document.addEventListener('visibilitychange', hidden); window.addEventListener('blur', blur);
    const unsubscribe = socket.subscribe((type, payload) => {
      try {
        if (type === 'GAME_SNAPSHOT') runtime.current?.receive(payload);
        if (type === 'PLAYER_FINISHED') runtime.current?.finish(payload);
        if (type === 'ADVENTURE_EVENTS' && cueManifest && runtime.current?.latest) {
          for (const cue of adventureEffects.receive(payload, latestRoom.current.roomId, runtime.current.latest.playId)) {
            const key = cue.kind === 'WORLD' ? cue.type === 'JINGLE' ? 'audio/music/event_music/ending_jingle' : cueManifest.walls.find(wall => wall.id === cue.id)?.appearSound || 'audio/illusion'
              : cue.type === 'FLEE' ? 'audio/event_sfx/bird_fly' : cue.type === 'SPEECH' ? 'audio/talking' : 'audio/plink';
            audio.current?.play(key);
          }
        }
        if (type === 'PLAYER_EFFECTS' && runtime.current?.latest) {
          const session = runtime.current;
          for (const effect of effects.receive(payload, latestRoom.current.roomId, socket.selfId || '', session.latest!.playId, session.game.physics.snapshot().screen)) {
            audio.current?.effect(effect.effect, { snow: effect.terrain === 'SNOW', ice: effect.terrain === 'ICE' }, effect.playerId === socket.selfId ? PRESENTATION.effectVolume : PRESENTATION.peerEffectVolume);
          }
        }
        if (type === 'CONNECTION_CLOSED') { if (runtime.current) runtime.current.menu = true; controls.cancel(); audio.current?.pause(true); setDisconnected(true); setMenu('pause'); setError('Disconnected from the game server'); }
        if (type === 'RECONNECT_OK') { setDisconnected(false); setError(''); if (runtime.current) runtime.current.menu = true; controls.cancel(); setMenu('pause'); }
        if (type === 'CHAT_MESSAGE' || type === 'CHAT_HISTORY') setMessages(socket.chatHistory as unknown as ChatMessage[]);
        if (type === 'COMMUNITY_RUN_STATE' || type === 'SAVE_STATUS') { if (typeof payload.saveStatus === 'string') setSaveStatus(payload.saveStatus); }
      } catch (cause) { setError(cause instanceof Error ? cause.message : 'Invalid game state'); runtime.current?.cancel(); }
    });
    void (async () => {
      const [renderer, manifest, mapData, config] = await Promise.all([import('../../game/presentation/createGame'), loadManifest(controller.signal), api<unknown>('/map'), api<{ features: { flyEnabled: boolean } }>('/game-config')]);
      if (disposed) return;
      cueManifest = manifest; setFlyAllowed(config.features.flyEnabled); audio.current = new AudioBus(manifest, readOptions().sound, setError);
      const session = new OnlineRuntime(parseMap(mapData), socket, latestRoom.current, () => controls.read(), (state, snapshot) => {
        setElapsed(snapshot.elapsedTimeMs); setRemaining(snapshot.remainingTimeMs); setJumps(state.jumps); setFlying(state.freeFlight); audio.current?.setScreen(state.screen);
        setInventory(snapshot.self.simulation.adventure);
        if (!readOptions().fly && state.freeFlight) session.setFlight(false);
        if (snapshot.self.paused && !session.menu) { session.menu = true; controls.cancel(); setMenu('pause'); audio.current?.pause(true); }
      }, config.features.flyEnabled, message => { setError(message); setMenu('pause'); controls.cancel(); });
      runtime.current = session;
      if (socket.resumeRequired) { session.menu = true; controls.cancel(); setMenu('pause'); audio.current?.pause(true); }
      flight.current = new FlightToggle(config.features.flyEnabled, () => session.flying, enabled => { session.setFlight(enabled); setFlying(enabled); });
      flight.current.setEnabled(readOptions().fly);
      if (socket.lastFinish) session.finish(socket.lastFinish);
      if (socket.lastSnapshot) session.receive(socket.lastSnapshot);
      game = renderer.createGame(container.current!, manifest, session, () => { setReady(true); if (document.hidden || !document.hasFocus()) pauseRef.current(); }, setError, {
        onEndingStart: skip => { skipEnding.current = skip; endingActive.current = true; controls.cancel(); session.cancel(); setMenu(null); setChatOpen(false); setPictureReady(false); setEnding('playing'); audio.current?.beginEnding(); },
        onEndingComplete: () => setEnding('picture'), onSound: key => audio.current?.play(key),
      });
    })().catch(cause => { if (!disposed) setError(cause instanceof Error ? cause.message : 'Unable to load online game'); });
    return () => { disposed = true; controller.abort(); clearInterval(afk); unsubscribe(); controls.dispose(); runtime.current?.cancel(); runtime.current = null; input.current = null; flight.current = null; game?.destroy(true); audio.current?.dispose(); audio.current = null;
      document.removeEventListener('visibilitychange', hidden); window.removeEventListener('blur', blur);
    };
  }, [socket, room.roomId]);
  const command = async (type: string, payload: Record<string, unknown> = {}) => {
    if (pending) return; setPending(true); setError('');
    try { await socket.command(type, payload); }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Action failed'); }
    finally { setPending(false); }
  };
  const resume = async () => {
    if (room.kind === 'ROOM' && room.state === 'PAUSED') {
      if (room.hostPlayerId !== socket.selfId) return;
      setPending(true);
      try { await socket.command('SET_ROOM_PAUSED', { playId: socket.playContext?.playId, paused: false }); }
      catch (cause) { setError(cause instanceof Error ? cause.message : 'Resume failed'); return; }
      finally { setPending(false); }
    }
    if (room.kind === 'COMMUNITY') {
      setPending(true);
      try { await socket.command('SET_COMMUNITY_PAUSED', { paused: false }); }
      catch (cause) { setError(cause instanceof Error ? cause.message : 'Resume failed'); return; }
      finally { setPending(false); }
    }
    socket.resumeRequired = false; if (runtime.current) runtime.current.menu = false; input.current?.resume(); audio.current?.pause(false); setMenu(null);
  };
  const updateOptions = (key: keyof Options) => {
    const next = { ...options, [key]: !options[key] }; setOptions(next); audio.current?.setEnabled(next.sound);
    if (key === 'fly') flight.current?.setEnabled(next.fly);
    try { saveOptions(next); } catch { setError('Options could not be saved on this device'); }
  };
  const newGame = async () => {
    if (pending) return;
    const finished = runtime.current?.latest?.self.status === 'FINISHED';
    if (!finished && !window.confirm('Replace your unfinished Community game?')) return;
    setPending(true); setError('');
    try {
      await socket.command('NEW_COMMUNITY_RUN', { expectedRunId: finished ? null : runtime.current?.latest?.playId });
      endingActive.current = false; skipEnding.current = null; setEnding(null); setPictureReady(false);
      if (runtime.current) runtime.current.menu = false; input.current?.resume(); audio.current?.pause(false); setMenu(null);
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'New Game failed'); } finally { setPending(false); }
  };
  return <main className="play-page">
    <div className="game-viewport" ref={container} aria-label="Online Main Babe game" />
    {!ready && <p className="game-message" role="status">Loading…</p>}
    {ready && !ending && <div className="game-hud"><span>Jumps: {jumps}{options.displayTimer && <span className="timer" aria-label="Play time">{duration(elapsed)}</span>}{remaining !== null && <span className="timer" aria-label="Time left">Time Left: {duration(Math.ceil(remaining / 1000) * 1000)}</span>}</span><span className="room-code" aria-label="Room ID">{room.roomId}</span><span><button onClick={() => { runtime.current?.cancel(); input.current?.cancel(); setChatOpen(true); }}>Chat</button><button onClick={pause}>Menu</button></span></div>}
    {ready && !ending && !menu && !chatOpen && <TouchControls input={input} flying={flying} />}
    {ending && <div className="ending-actions">{ending === 'playing' ? <button onClick={() => skipEnding.current?.()}>Skip</button> : <>{room.kind === 'ROOM'
      ? <button disabled={!pictureReady} onClick={onResults}>View Results</button>
      : <button disabled={!pictureReady || pending} onClick={() => { void newGame(); }}>New Game</button>}<button disabled={!pictureReady || pending || disconnected} onClick={() => { void command('LEAVE_ROOM'); }}>Back to Menu</button></>}</div>}
    {ready && chatOpen && <ChatPanel messages={messages} colors={rules.appearanceColors} onSend={async text => { await socket.command('CHAT_SEND', { text }); }} onClose={() => { setChatOpen(false); if (!runtime.current?.menu) input.current?.resume(); }} />}
    {menu && <section className="game-overlay" aria-label="Game menu"><MenuPanel title={menu === 'pause' ? 'Paused' : menu === 'options' ? 'Options' : menu === 'inventory' ? 'Inventory' : 'Controls'} onBack={menu === 'pause' ? undefined : () => setMenu(menu === 'controls' ? 'options' : 'pause')}>
      {menu === 'pause' && <>{disconnected ? <><button disabled={pending} onClick={() => { setPending(true); void socket.reconnect().catch(cause => setError(cause.message)).finally(() => setPending(false)); }}>Reconnect</button><button onClick={onHome}>Back to Menu</button></> : <button disabled={pending || (room.state === 'PAUSED' && room.hostPlayerId !== socket.selfId)} onClick={() => { void resume(); }}>{room.kind === 'ROOM' && room.state === 'PAUSED' ? room.hostPlayerId === socket.selfId ? 'Resume Room' : 'Room Paused' : 'Resume'}</button>}<button onClick={() => setMenu('options')}>Options</button><button onClick={() => { runtime.current?.cancel(); input.current?.cancel(); setChatOpen(true); }}>Open Chat</button>
        {room.kind === 'COMMUNITY' && <button disabled={pending || disconnected} onClick={() => { void newGame(); }}>New Game</button>}
        <button onClick={() => setMenu('inventory')}>Inventory</button>
        {room.kind === 'ROOM' && room.hostPlayerId === socket.selfId && room.state === 'PLAYING' && <button disabled={pending || disconnected} onClick={() => { void command('SET_ROOM_PAUSED', { playId: socket.playContext?.playId, paused: true }); }}>Pause Room</button>}
        {room.kind === 'ROOM' && room.hostPlayerId === socket.selfId && ['PLAYING', 'PAUSED'].includes(room.state) && <button disabled={pending || disconnected} onClick={() => { void command('END_MATCH'); }}>End Match</button>}
        <button disabled={pending || disconnected} onClick={() => { void command('LEAVE_ROOM'); }}>{room.kind === 'COMMUNITY' ? 'Save & Exit' : 'Leave Room'}</button>{room.kind === 'COMMUNITY' && <small role="status">{saveStatus === 'SAVED' ? 'Saved' : saveStatus === 'FAILED' ? 'Save failed' : 'Saving…'}</small>}</>}
      {menu === 'pause' && room.kind === 'COMMUNITY' && <form className="menu-form" onSubmit={event => { event.preventDefault(); void command('JOIN_ROOM', { roomId: transferId }); }}>
        <label>Community ID<input value={transferId} minLength={6} maxLength={6} pattern="[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}" required onChange={event => setTransferId(event.currentTarget.value.toUpperCase())} /></label>
        <button disabled={pending || disconnected}>Join Community</button>
      </form>}
      {menu === 'options' && <OptionsButtons options={options} flyAllowed={flyAllowed} onChange={updateOptions} onControls={() => setMenu('controls')} onError={setError} />}
      {menu === 'controls' && <ControlsHelp flyAllowed={flyAllowed} />}
      {menu === 'inventory' && <>
        {Object.entries(inventory?.inventory || {}).filter(([, amount]) => amount > 0).map(([item, amount]) => ADVENTURE.skins.some(skin => skin.item === item)
          ? <button key={item} disabled={pending || disconnected} role="switch" aria-checked={inventory!.equipped.includes(item)} onClick={() => { void command('EQUIP_ITEM', { itemId: item, equipped: !inventory!.equipped.includes(item) }); }}>{item}: {inventory!.equipped.includes(item) ? 'On' : 'Off'}</button>
          : <p key={item}>{item}: {amount}</p>)}
        {!Object.values(inventory?.inventory || {}).some(amount => amount > 0) && <p>No items yet.</p>}
      </>}
      {error && <p role="alert">{error}</p>}
    </MenuPanel></section>}
    {error && !menu && <p className="save-error" role="alert">{error}</p>}
  </main>;
}
