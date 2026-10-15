import type { Options } from './options';

export function OptionsButtons({ options, flyAllowed, onChange, onControls, onError }: {
  options: Options; flyAllowed: boolean; onChange: (key: keyof Options) => void; onControls: () => void; onError: (message: string) => void;
}) {
  return <>
    <button role="switch" aria-checked={options.sound} onClick={() => onChange('sound')}>Sound: {options.sound ? 'On' : 'Off'}</button>
    <button disabled={typeof document !== 'undefined' && !document.fullscreenEnabled} onClick={() => {
      const action = document.fullscreenElement ? document.exitFullscreen() : document.documentElement.requestFullscreen();
      void action.catch(() => onError('Fullscreen is unavailable in this browser'));
    }}>Fullscreen</button>
    <button role="switch" aria-checked={options.displayTimer} onClick={() => onChange('displayTimer')}>Display Timer: {options.displayTimer ? 'On' : 'Off'}</button>
    {flyAllowed && <button role="switch" aria-checked={options.fly} onClick={() => onChange('fly')}>Fly Mode: {options.fly ? 'On' : 'Off'}</button>}
    <button onClick={onControls}>Controls</button>
  </>;
}
export function ControlsHelp({ flyAllowed }: { flyAllowed: boolean }) {
  return <dl className="record-detail"><dt>Move</dt><dd>A / D or ← / →</dd><dt>Jump</dt><dd>Hold Space / W / ↑, then release</dd><dt>Menu</dt><dd>Escape</dd><dt>Chat Online</dt><dd>Enter to open/send, Escape to close</dd><dt>Gamepad</dt><dd>D-pad / left stick, A, Start</dd><dt>Touch</dt><dd>Direction and Jump buttons</dd>{flyAllowed && <><dt>Fly</dt><dd>Double-tap Space, WASD / arrows</dd></>}</dl>;
}
