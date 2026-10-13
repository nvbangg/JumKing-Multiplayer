import { useEffect, useRef, useState } from 'react';
import { loadManifest } from '../../game/content/assets';
import { parseMap } from '../../game/simulation/map';
import { InputController } from '../../game/input/InputController';
import { SoloRuntime } from './SoloRuntime';
import { PRESENTATION } from '../../game/presentation/config';
import type { PlayerState } from '../../game/simulation/types';
import rules from '../../../../shared/rules.json';

export function SoloGame() {
  const container = useRef<HTMLDivElement>(null);
  const runtime = useRef<SoloRuntime | null>(null);
  const input = useRef<InputController | null>(null);
  const [state, setState] = useState<PlayerState | null>(null);
  const [error, setError] = useState('');
  const [ready, setReady] = useState(false);
  const [paused, setPaused] = useState(false);
  const pause = () => { runtime.current?.pause(); input.current?.cancel(); setPaused(true); };
  useEffect(() => {
    const controller = new AbortController();
    let disposed = false;
    let game: { destroy(removeCanvas: boolean): void } | undefined;
    let lastHud = 0;
    let lastActivity = performance.now();
    const activity = () => { lastActivity = performance.now(); };
    const controls = new InputController(activity, pause);
    input.current = controls;
    const hidden = () => { if (document.hidden) pause(); };
    window.addEventListener('blur', pause); document.addEventListener('visibilitychange', hidden);
    const afk = window.setInterval(() => {
      if (runtime.current && !runtime.current.paused && performance.now() - lastActivity >= rules.afkMs) pause();
    }, PRESENTATION.afkPollMs);
    void (async () => {
      const [renderer, manifest, response] = await Promise.all([
        import('../../game/presentation/createGame'), loadManifest(controller.signal), fetch('/shared/maps/main-babe.json', { signal: controller.signal }),
      ]);
      if (!response.ok) throw new Error('Game map is unavailable');
      const map = parseMap(await response.json());
      if (disposed) return;
      const session = new SoloRuntime(map, () => controls.read(), frame => {
        const now = performance.now();
        if (now - lastHud >= PRESENTATION.hudIntervalMs) { setState(frame); lastHud = now; }
      });
      runtime.current = session;
      activity();
      game = renderer.createGame(container.current!, manifest, session, () => { setReady(true); setState(session.simulation.snapshot()); }, message => { session.pause(); controls.cancel(); setError(message); });
    })().catch(cause => { if (!disposed) setError(cause instanceof Error ? cause.message : 'Unable to load game'); });
    return () => { disposed = true; controller.abort(); clearInterval(afk); controls.dispose(); game?.destroy(true); runtime.current = null; input.current = null; window.removeEventListener('blur', pause); document.removeEventListener('visibilitychange', hidden); };
  }, []);
  return <main className="play-page">
    <div className="game-viewport" ref={container} aria-label="Main Babe game" />
    {!ready && !error && <p className="game-message" role="status">Loading…</p>}
    {error && <p className="game-message" role="alert">{error}</p>}
    {ready && <div className="game-hud"><span>Jumps: {state?.jumps ?? 0}</span><button onClick={pause}>Menu</button></div>}
    {paused && <section className="game-overlay" aria-label="Game menu"><h2>Paused</h2><button onClick={() => { input.current?.resume(); runtime.current?.resume(); setPaused(false); }}>Resume</button><button onClick={() => { location.href = '/'; }}>Leave</button></section>}
  </main>;
}
