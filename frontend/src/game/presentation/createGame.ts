import Phaser from 'phaser';
import { TowerScene } from '../scenes/TowerScene';
import type { AssetManifest } from '../content/assets';
import type { GameRuntime, PresentationHooks } from './types';
import { PRESENTATION } from './config';

export function createGame(parent: HTMLElement, manifest: AssetManifest, runtime: GameRuntime,
  onReady: () => void, onError: (message: string) => void, hooks: PresentationHooks = {}): Phaser.Game {
  return new Phaser.Game({
    type: Phaser.AUTO, width: PRESENTATION.width, height: PRESENTATION.height, parent,
    pixelArt: true, roundPixels: true, backgroundColor: '#000000',
    scale: { mode: Phaser.Scale.FIT, autoCenter: Phaser.Scale.CENTER_BOTH },
    scene: [new TowerScene(manifest, runtime, onReady, onError, hooks)], audio: { noAudio: true },
  });
}
