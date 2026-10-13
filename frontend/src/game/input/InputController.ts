import { NEUTRAL_INPUT, type PlayerInput } from '../simulation/types';

const relevant = new Set(['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'KeyA', 'KeyD', 'KeyW', 'KeyS', 'Space']);
export class InputController {
  private keys = new Set<string>();
  private canceled = false;
  constructor(private onActivity: () => void, private onMenu: () => void) {
    window.addEventListener('keydown', this.keyDown);
    window.addEventListener('keyup', this.keyUp);
  }
  private keyDown = (event: KeyboardEvent) => {
    if ((event.target as HTMLElement)?.closest('input,textarea,select,[contenteditable="true"]')) return;
    if (event.code === 'Escape' && !event.repeat) { this.onMenu(); return; }
    if (!relevant.has(event.code)) return;
    event.preventDefault();
    if (!event.repeat) { this.keys.add(event.code); this.onActivity(); }
  };
  private keyUp = (event: KeyboardEvent) => {
    if (!relevant.has(event.code)) return;
    if (this.keys.delete(event.code)) this.onActivity();
  };
  read(): PlayerInput {
    if (this.canceled) return { ...NEUTRAL_INPUT };
    const k = this.keys;
    return { left: k.has('KeyA') || k.has('ArrowLeft'), right: k.has('KeyD') || k.has('ArrowRight'), jump: k.has('Space') || k.has('KeyW') || k.has('ArrowUp'), up: k.has('KeyW') || k.has('ArrowUp'), down: k.has('KeyS') || k.has('ArrowDown') };
  }
  cancel(): void { this.keys.clear(); this.canceled = true; }
  resume(): void { this.keys.clear(); this.canceled = false; this.onActivity(); }
  dispose(): void { window.removeEventListener('keydown', this.keyDown); window.removeEventListener('keyup', this.keyUp); this.keys.clear(); }
}
