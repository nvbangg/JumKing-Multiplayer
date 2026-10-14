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

export function OnlineGame({ socket, room }: { socket: GameSocket; room: RoomState }) {
  const container = useRef<HTMLDivElement>(null), runtime = useRef<OnlineRuntime | null>(null), input = useRef<InputController | null>(null), audio = useRef<AudioBus | null>(null);
  const [ready, setReady] = useState(false), [menu, setMenu] = useState<'pause' | 'options' | 'controls' | null>(null);
  const [options, setOptions] = useState(readOptions), [flyAllowed, setFlyAllowed] = useState(false);
  const [error, setError] = useState(''), [pending, setPending] = useState(false), [elapsed, setElapsed] = useState(0), [jumps, setJumps] = useState(0), [saveStatus, setSaveStatus] = useState('SAVED');
  const latestRoom = useRef(room); latestRoom.current = room;
  const pauseRef = useRef<() => void>(() => {});
  const pause = () => {
    if (!runtime.current || runtime.current.menu) return;
    runtime.current.menu = true; runtime.current.cancel(); input.current?.cancel(); audio.current?.pause(true); setMenu('pause');
    if (room.kind === 'COMMUNITY') void socket.command('SET_COMMUNITY_PAUSED', { paused: true }).catch(cause => setError(cause.message));
  };
  pauseRef.current = pause;
  useEffect(() => { runtime.current?.updateRoom(room); }, [room]);
  useEffect(() => {
    let disposed = false, game: { destroy(removeCanvas: boolean): void } | undefined;
    const controller = new AbortController();
    const controls = new InputController(() => {}, () => pauseRef.current(), () => runtime.current?.cancel()); input.current = controls;
    const hidden = () => { if (document.hidden) pauseRef.current(); }; const blur = () => pauseRef.current();
    document.addEventListener('visibilitychange', hidden); window.addEventListener('blur', blur);
    const unsubscribe = socket.subscribe((type, payload) => {
      try {
        if (type === 'GAME_SNAPSHOT') runtime.current?.receive(payload);
        if (type === 'COMMUNITY_RUN_STATE' || type === 'SAVE_STATUS') { if (typeof payload.saveStatus === 'string') setSaveStatus(payload.saveStatus); }
      } catch (cause) { setError(cause instanceof Error ? cause.message : 'Invalid game state'); runtime.current?.cancel(); }
    });
    void (async () => {
      const [renderer, manifest, mapData, config] = await Promise.all([import('../../game/presentation/createGame'), loadManifest(controller.signal), api<unknown>('/map'), api<{ features: { flyEnabled: boolean } }>('/game-config')]);
      if (disposed) return;
      setFlyAllowed(config.features.flyEnabled); audio.current = new AudioBus(manifest, readOptions().sound, setError);
      const session = new OnlineRuntime(parseMap(mapData), socket, latestRoom.current, () => controls.read(), (state, snapshot) => {
        setElapsed(snapshot.elapsedTimeMs); setJumps(state.jumps); audio.current?.setScreen(state.screen);
        if (snapshot.self.paused && !session.menu) { session.menu = true; controls.cancel(); setMenu('pause'); audio.current?.pause(true); }
      }, config.features.flyEnabled, message => { setError(message); setMenu('pause'); controls.cancel(); });
      runtime.current = session;
      if (socket.lastSnapshot) session.receive(socket.lastSnapshot);
      game = renderer.createGame(container.current!, manifest, session, () => setReady(true), setError);
    })().catch(cause => { if (!disposed) setError(cause instanceof Error ? cause.message : 'Unable to load online game'); });
    return () => { disposed = true; controller.abort(); unsubscribe(); controls.dispose(); runtime.current?.cancel(); runtime.current = null; input.current = null; game?.destroy(true); audio.current?.dispose(); audio.current = null;
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
    if (room.kind === 'COMMUNITY') {
      setPending(true);
      try { await socket.command('SET_COMMUNITY_PAUSED', { paused: false }); }
      catch (cause) { setError(cause instanceof Error ? cause.message : 'Resume failed'); return; }
      finally { setPending(false); }
    }
    if (runtime.current) runtime.current.menu = false; input.current?.resume(); audio.current?.pause(false); setMenu(null);
  };
  const updateOptions = (key: keyof Options) => {
    const next = { ...options, [key]: !options[key] }; setOptions(next); audio.current?.setEnabled(next.sound);
    try { saveOptions(next); } catch { setError('Options could not be saved on this device'); }
  };
  return <main className="play-page">
    <div className="game-viewport" ref={container} aria-label="Online Main Babe game" />
    {!ready && <p className="game-message" role="status">Loading…</p>}
    {ready && <div className="game-hud"><span>Jumps: {jumps}{options.displayTimer && <span className="timer" aria-label="Play time">{duration(elapsed)}</span>}</span><span className="room-code" aria-label="Room ID">{room.roomId}</span><button onClick={pause}>Menu</button></div>}
    {ready && !menu && <TouchControls input={input} flying={false} />}
    {menu && <section className="game-overlay" aria-label="Game menu"><MenuPanel title={menu === 'pause' ? 'Paused' : menu === 'options' ? 'Options' : 'Controls'} onBack={menu === 'pause' ? undefined : () => setMenu(menu === 'controls' ? 'options' : 'pause')}>
      {menu === 'pause' && <><button disabled={pending} onClick={() => { void resume(); }}>Resume</button><button onClick={() => setMenu('options')}>Options</button><button disabled={pending} onClick={() => { void command('LEAVE_ROOM'); }}>{room.kind === 'COMMUNITY' ? 'Save & Exit' : 'Leave Room'}</button>{room.kind === 'COMMUNITY' && <small role="status">{saveStatus === 'SAVED' ? 'Saved' : saveStatus === 'FAILED' ? 'Save failed' : 'Saving…'}</small>}</>}
      {menu === 'options' && <OptionsButtons options={options} flyAllowed={flyAllowed} onChange={updateOptions} onControls={() => setMenu('controls')} onError={setError} />}
      {menu === 'controls' && <ControlsHelp flyAllowed={flyAllowed} />}
      {error && <p role="alert">{error}</p>}
    </MenuPanel></section>}
    {error && !menu && <p className="save-error" role="alert">{error}</p>}
  </main>;
}
