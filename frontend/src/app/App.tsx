import { useEffect, useRef, useState } from 'react';
import { SoloGame } from '../features/solo/SoloGame';
import { currentSolo, soloRuns, StorageUnavailable, type SoloRun } from '../features/solo/storage';
import { readOptions, saveOptions } from '../features/solo/options';
import { AuthForm } from '../features/account/AuthForm';
import { StatisticsPanel } from '../features/records/StatisticsPanel';
import { duration, soloHistory, soloStatistics } from '../features/records/format';
import { MenuPanel } from '../components/MenuPanel';
import { api, backendOrigin, clearCsrf } from '../network/api';
import { GameSocket, type MatchResult, type RoomState } from '../network/GameSocket';
import { EMPTY_STATS, type HistoryItem, type Identity, type Statistics } from '../network/contracts';
import { Lobby } from '../features/rooms/Lobby';
import { Results } from '../features/rooms/Results';
import { OptionsButtons, ControlsHelp } from '../features/solo/OptionsPanel';
import { AudioBus } from '../game/presentation/AudioBus';
import { loadManifest } from '../game/content/assets';

type Screen = 'home' | 'play' | 'sign-in' | 'register' | 'merge-history' | 'account' | 'community' | 'leaderboard' | 'history' | 'detail' | 'options' | 'controls' | 'nickname' | 'join' | 'lobby' | 'results';
interface SoloLaunch { profile: string; action: 'new' | 'continue' | 'auto'; expectedRunId: string | null }
const initialIdentity: Identity = { authenticated: false, guest: false, account: null };
const routes: Partial<Record<Screen, string>> = { home: '/', play: '/play', history: '/history', community: '/community', options: '/options', account: '/account', 'sign-in': '/sign-in', register: '/register' };
function locationScreen(): Screen { return typeof location === 'undefined' ? 'home' : (Object.entries(routes).find(([, path]) => path === location.pathname)?.[0] as Screen | undefined) || 'home'; }
function restoredLaunch(): SoloLaunch {
  const saved = typeof window !== 'undefined' ? window.history.state?.solo : null;
  return { profile: typeof saved?.profile === 'string' ? saved.profile : 'guest', action: 'auto', expectedRunId: null };
}

export function App() {
  const [screen, setScreenState] = useState<Screen>(locationScreen);
  const setScreen = (next: Screen) => {
    if (routes[next] && location.pathname !== routes[next]) window.history.pushState(null, '', routes[next]);
    setScreenState(next);
  };
  const [identity, setIdentity] = useState<Identity>(initialIdentity);
  const [activeSolo, setActiveSolo] = useState<SoloRun | null>(null);
  const [launch, setLaunch] = useState<SoloLaunch>(restoredLaunch);
  const [statistics, setStatistics] = useState<Statistics>({ ...EMPTY_STATS });
  const [history, setHistory] = useState<HistoryItem[]>([]);
  const [detail, setDetail] = useState<HistoryItem | null>(null);
  const [communityRun, setCommunityRun] = useState<{ id: string } | null>(null);
  const [leaderboard, setLeaderboard] = useState<{ id: string; rank: number; username: string; maxProgress: number; finishTimeMs: number | null }[]>([]);
  const [room, setRoom] = useState<RoomState | null>(null);
  const [result, setResult] = useState<MatchResult | null>(null);
  const resultRef = useRef<MatchResult | null>(null);
  const [roomIntent, setRoomIntent] = useState<{ roomId?: string }>({});
  const [options, setOptions] = useState(readOptions);
  const [flyAllowed, setFlyAllowed] = useState(__SOLO_FLY_ENABLED__);
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);
  const socket = useRef<GameSocket | null>(null);
  const profile = identity.account ? backendOrigin() + ':account:' + identity.account.id : 'guest';
  const playing = screen === 'play';
  useEffect(() => {
    if (playing || !options.sound) return;
    let disposed = false, music: AudioBus | undefined;
    const controller = new AbortController();
    void loadManifest(controller.signal).then(manifest => {
      if (disposed) return;
      music = new AudioBus(manifest, true, setError, false); music.setMusic('audio/music/menu loop/menu_loop');
    }).catch(() => { if (!disposed) setError('Menu sound could not be loaded'); });
    return () => { disposed = true; controller.abort(); music?.dispose(); };
  }, [playing, options.sound]);
  useEffect(() => {
    setActiveSolo(null); setCommunityRun(null); setStatistics({ ...EMPTY_STATS }); setHistory([]); setDetail(null);
  }, [profile]);
  const back = () => {
    const next = screen === 'controls' ? 'options' : screen === 'leaderboard' ? 'community' : screen === 'detail' ? 'history' : 'home';
    if (routes[next]) window.history.replaceState(null, '', routes[next]);
    setError(''); setScreenState(next);
  };
  const execute = async (action: () => Promise<void>) => {
    if (pending) return;
    setPending(true); setError('');
    try { await action(); }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Unable to complete this action'); }
    finally { setPending(false); }
  };

  useEffect(() => {
    let disposed = false;
    const pop = () => { setLaunch(restoredLaunch()); setScreenState(locationScreen()); };
    window.addEventListener('popstate', pop);
    void api<Identity>('/auth/me').then(value => {
      if (typeof value.authenticated !== 'boolean' || typeof value.guest !== 'boolean') return;
      if (!disposed) setIdentity(value);
    }).catch(() => {});
    void api<{ features: { flyEnabled: boolean } }>('/game-config').then(config => { if (!disposed) setFlyAllowed(__SOLO_FLY_ENABLED__ || config.features.flyEnabled); }).catch(() => {});
    return () => { disposed = true; window.removeEventListener('popstate', pop); socket.current?.close(); };
  }, []);

  useEffect(() => {
    if (screen === 'play' || screen === 'lobby') return;
    let disposed = false;
    void soloRuns(profile).then(runs => {
      if (disposed) return;
      setActiveSolo(runs.find(run => run.status === 'ACTIVE') || null);
      if (!identity.authenticated) setStatistics(soloStatistics(runs));
      if (screen === 'history') setHistory(soloHistory(runs));
    }).catch(cause => { if (!disposed) setError(cause instanceof Error ? cause.message : 'Browser storage is unavailable'); });
    if (identity.authenticated || identity.guest) {
      void api<{ total: Statistics }>('/profile/me').then(result => {
        if (disposed) return;
        if (identity.authenticated) setStatistics(result.total);
        else void soloRuns(profile).then(runs => {
          if (disposed) return;
          const local = soloStatistics(runs), total = result.total;
          const finishes = [local.bestFinishTimeMs, total.bestFinishTimeMs].filter((value): value is number => value !== null);
          setStatistics({ ...total, totalPlayTimeMs: total.totalPlayTimeMs + local.totalPlayTimeMs, totalJumps: total.totalJumps + local.totalJumps, totalFalls: total.totalFalls + local.totalFalls, finishes: total.finishes + local.finishes, bestFinishTimeMs: finishes.length ? Math.min(...finishes) : null });
        });
      }).catch(() => {});
    }
    if (screen === 'community' && identity.authenticated) void api<{ run: { id: string } | null }>('/community/current').then(result => { if (!disposed) setCommunityRun(result.run); }).catch(cause => { if (!disposed) setError(cause.message); });
    if (screen === 'leaderboard') void api<{ entries: typeof leaderboard }>('/leaderboard?limit=100').then(result => { if (!disposed) setLeaderboard(result.entries); }).catch(cause => { if (!disposed) setError(cause.message); });
    if (screen === 'history' && (identity.authenticated || identity.guest)) void api<{ items: HistoryItem[] }>('/history?page=0&size=100').then(async result => {
      const local = soloHistory(await soloRuns(profile));
      const combined = new Map(local.map(item => [item.kind + ':' + item.id, item]));
      for (const item of result.items) combined.set(item.kind + ':' + item.id, item);
      if (!disposed) setHistory([...combined.values()].sort((a, b) => b.endedAt.localeCompare(a.endedAt) || b.id.localeCompare(a.id)));
    }).catch(cause => { if (!disposed) setError(cause.message); });
    return () => { disposed = true; };
  }, [profile, screen, identity.authenticated, identity.guest]);

  const openSolo = (action: 'new' | 'continue') => { void execute(async () => {
    const current = await currentSolo(profile).catch(cause => {
      if (action === 'new' && cause instanceof StorageUnavailable && window.confirm('Browser storage is unavailable. Start without saving?')) return null;
      throw cause;
    });
    if (action === 'new' && current && !window.confirm('Replace your unfinished Solo game?')) return;
    window.history.pushState({ solo: { profile } }, '', '/play');
    setLaunch({ profile, action, expectedRunId: current?.id || null }); setScreenState('play');
  }); };
  const connectedSocket = async () => {
    if (!identity.authenticated && !identity.guest) setIdentity(await api<Identity>('/auth/guest', 'POST'));
    if (!socket.current) socket.current = new GameSocket((type, payload) => {
      if (type === 'ROOM_STATE') {
        const next = payload as unknown as RoomState;
        setRoom(next);
        const self = next.members?.find(member => member.playerId === socket.current?.selfId);
        setScreen(resultRef.current && self?.nextMatchConfirmed !== true && (next.state === 'WAITING' || next.state === 'FINISHED') ? 'results' : 'lobby');
      }
      else if (type === 'GAME_FINISHED') { const finished = payload as unknown as MatchResult; resultRef.current = finished; setResult(finished); setScreen('results'); }
      else if (type === 'GAME_STARTED') { resultRef.current = null; setResult(null); }
      else if (type === 'ROOM_LEFT') { setRoom(null); setResult(null); resultRef.current = null; socket.current?.close(); socket.current = null; back(); }
      else if (type === 'ERROR') setError(String(payload.message));
    }, () => setError('Disconnected from the game server'));
    await socket.current.connect();
    return socket.current;
  };
  const command = (type: string, payload: Record<string, unknown> = {}) => { void execute(async () => { const connection = await connectedSocket(); await connection.command(type, payload); }); };
  const updateOptions = (key: keyof typeof options) => {
    const next = { ...options, [key]: !options[key] };
    setOptions(next);
    try { saveOptions(next); setError(''); } catch { setError('Options could not be saved on this device'); }
  };

  if (screen === 'play') return <SoloGame {...launch} onExit={() => { setOptions(readOptions()); back(); }} />;
  return <main className="home">
    <header className="brand"><h1><img src="/assets/title_logo.png" alt="JumpKing" /></h1></header>
    <div className={screen === 'home' ? 'home-layout' : 'single-panel'}>
      <MenuPanel title={screen === 'home' ? undefined : ({ 'sign-in': 'Sign In', register: 'Create Account', 'merge-history': 'Guest Room History', account: 'Account', community: 'Community Tower', leaderboard: 'Leaderboard', history: 'History', detail: 'Game Details', options: 'Options', controls: 'Controls', nickname: roomIntent.roomId ? 'Join Room' : 'Play with Friends', join: 'Join by ID', lobby: room?.kind === 'COMMUNITY' ? 'Community Tower' : 'Lobby', results: 'Results' } as Partial<Record<Screen, string>>)[screen]} onBack={screen === 'home' || screen === 'lobby' || screen === 'results' ? undefined : back}>
        {screen === 'home' && <>
          {activeSolo && <button onClick={() => openSolo('continue')} disabled={pending}>Continue Solo</button>}
          <button onClick={() => openSolo('new')} disabled={pending}>New Solo Game</button>
          <button onClick={() => { setRoomIntent({}); setScreen('nickname'); }}>Play with Friends</button>
          <button onClick={() => setScreen('join')}>Join by ID</button>
          <button onClick={() => setScreen('community')}>Community Tower</button>
          <button onClick={() => setScreen('history')}>History</button>
          <button onClick={() => setScreen('options')}>Options</button>
          <div className="account-actions">{identity.account ? <button onClick={() => setScreen('account')}>{identity.account.username}</button> : <><button onClick={() => setScreen('sign-in')}>Sign In</button><button onClick={() => setScreen('register')}>Create Account</button></>}</div>
        </>}
        {(screen === 'sign-in' || screen === 'register') && <AuthForm key={screen} register={screen === 'register'} onSuccess={value => { setIdentity(value); if (value.guestHistoryAvailable) setScreen('merge-history'); else back(); }} onSwitch={() => setScreen(screen === 'register' ? 'sign-in' : 'register')} />}
        {screen === 'merge-history' && <><p>Add this Guest's Room history to your account?</p><button disabled={pending} onClick={() => { void execute(async () => { const result = await api<{ merged: boolean }>('/auth/guest-history/merge', 'POST'); if (!result.merged) throw new Error('History could not be merged'); back(); }); }}>Merge Guest Room History</button><button disabled={pending} onClick={back}>Skip</button></>}
        {screen === 'account' && identity.account && <><dl className="account-info"><dt>Username</dt><dd>{identity.account.username}</dd><dt>Email</dt><dd>{identity.account.email}</dd></dl><button disabled={pending} onClick={() => { void execute(async () => { await api('/auth/logout', 'POST'); clearCsrf(); setIdentity(initialIdentity); setIdentity(await api<Identity>('/auth/me').catch(() => initialIdentity)); back(); }); }}>Sign Out</button></>}
        {screen === 'community' && <>
          {!identity.authenticated ? <><p>Sign in to play Community Tower.</p><button onClick={() => setScreen('sign-in')}>Sign In</button><button onClick={() => setScreen('register')}>Create Account</button></> : <>
            {communityRun && <button disabled={pending} onClick={() => command('JOIN_COMMUNITY')}>Continue</button>}
            <button disabled={pending} onClick={() => { if (!communityRun || window.confirm('Replace your unfinished Community game?')) command('JOIN_COMMUNITY', { newRun: true, expectedRunId: communityRun?.id || null }); }}>New Game</button>
          </>}
          <button onClick={() => setScreen('leaderboard')}>Leaderboard</button>
        </>}
        {screen === 'leaderboard' && <ol className="leaderboard">{leaderboard.map(entry => <li key={entry.id}><span>{entry.rank}. {entry.username}</span><strong>{entry.finishTimeMs === null ? Math.floor(entry.maxProgress) + ' px' : duration(entry.finishTimeMs)}</strong></li>)}{!leaderboard.length && !error && <p>No results yet.</p>}</ol>}
        {screen === 'history' && <div className="history-list">{history.map(item => <button key={item.kind + ':' + item.id} onClick={() => { setDetail(item); setScreen('detail'); }}><span className="kind-label">{item.kind}</span><span>{new Date(item.endedAt).toLocaleDateString()} · {item.status}</span><span>{duration(item.finishTimeMs ?? item.playTimeMs)}</span></button>)}{!history.length && <p>No completed games.</p>}</div>}
        {screen === 'detail' && detail && <dl className="record-detail"><dt>Type</dt><dd>{detail.kind}</dd><dt>Result</dt><dd>{detail.status}</dd><dt>Play Time</dt><dd>{duration(detail.playTimeMs)}</dd><dt>Finish Time</dt><dd>{duration(detail.finishTimeMs)}</dd><dt>Jumps</dt><dd>{detail.jumps}</dd><dt>Falls</dt><dd>{detail.falls}</dd><dt>Peak</dt><dd>{Math.floor(detail.maxProgress)} px</dd>{detail.room && <><dt>Room</dt><dd>{detail.room.roomCode}</dd><dt>Mode</dt><dd>{detail.room.mode}</dd><dt>Rank</dt><dd>{detail.room.rank}</dd></>}</dl>}
        {screen === 'options' && <OptionsButtons options={options} flyAllowed={flyAllowed} onChange={updateOptions} onControls={() => setScreen('controls')} onError={setError} />}
        {screen === 'controls' && <ControlsHelp flyAllowed={flyAllowed} />}
        {screen === 'nickname' && <form className="menu-form" onSubmit={event => {
          event.preventDefault(); const nickname = String(new FormData(event.currentTarget).get('nickname')).trim();
          command(roomIntent.roomId ? 'JOIN_ROOM' : 'CREATE_ROOM', { ...(roomIntent.roomId ? { roomId: roomIntent.roomId } : {}), nickname });
        }}><label>Nickname<input name="nickname" defaultValue={identity.account?.username.slice(0, 16) || ''} required maxLength={16} /></label><button disabled={pending}>{pending ? 'Connecting…' : roomIntent.roomId ? 'Join' : 'Create Room'}</button></form>}
        {screen === 'join' && <form className="menu-form" onSubmit={event => {
          event.preventDefault(); const roomId = String(new FormData(event.currentTarget).get('roomId')).trim().toUpperCase();
          void execute(async () => {
            const found = await api<{ kind: 'ROOM' | 'COMMUNITY'; joinable: boolean }>('/rooms/' + encodeURIComponent(roomId));
            if (!found.joinable) throw new Error('This room is not accepting new players');
            if (found.kind === 'ROOM') { setRoomIntent({ roomId }); setScreen('nickname'); }
            else { if (!identity.authenticated) throw new Error('Sign in to join Community Tower'); const connection = await connectedSocket(); await connection.command('JOIN_ROOM', { roomId }); }
          });
        }}><label>Room ID<input name="roomId" required minLength={6} maxLength={6} autoCapitalize="characters" autoComplete="off" pattern="[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}" onInput={event => { event.currentTarget.value = event.currentTarget.value.toUpperCase(); }} /></label><button disabled={pending}>{pending ? 'Looking up…' : 'Join'}</button></form>}
        {screen === 'lobby' && room && <Lobby room={room} selfId={socket.current?.selfId || null} pending={pending} command={command} />}
        {screen === 'results' && result && <Results result={result} room={room} selfId={socket.current?.selfId || null} pending={pending} onAgain={() => command('PLAY_AGAIN', { matchId: result.playId })} onLeave={() => command('LEAVE_ROOM')} />}
        {error && <p className="error" role="alert">{error}</p>}
      </MenuPanel>
      {screen === 'home' && <StatisticsPanel statistics={statistics} />}
    </div>
  </main>;
}
