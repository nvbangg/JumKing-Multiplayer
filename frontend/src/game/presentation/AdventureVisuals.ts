import Phaser from 'phaser';
import type { AssetManifest } from '../content/assets';
import { ADVENTURE, availableItems } from '../simulation/Adventure';
import type { WorldFrame } from './types';
import { PRESENTATION as V } from './config';

export class AdventureVisuals {
  private items = new Map<string, Phaser.GameObjects.Image>();
  private birds = new Map<string, Phaser.GameObjects.Sprite>();
  private walls = new Map<string, Phaser.GameObjects.Image>();
  constructor(private scene: Phaser.Scene, private manifest: AssetManifest) {
    for (const bird of manifest.birds) {
      const texture = scene.textures.get(bird.texture);
      for (let i = 0; i < 8; i++) if (!texture.has('idle:' + i)) texture.add('idle:' + i, 0, i % 4 * 32, Math.floor(i / 4) * 32, 32, 32);
      for (let i = 0; i < 9; i++) if (!texture.has('fly:' + i)) texture.add('fly:' + i, 0, i % 3 * 48, 64 + Math.floor(i / 3) * 32, 48, 32);
    }
    for (const wall of manifest.walls) this.walls.set(wall.id, scene.add.image(wall.position.X, wall.position.Y - wall.screen * V.height, wall.texture).setOrigin(0).setDepth(23));
  }
  render(frame: WorldFrame): void {
    const screen = frame.self.state.screen, state = frame.self.adventure;
    for (const wall of this.manifest.walls) this.walls.get(wall.id)!.setVisible(wall.screen === screen).setAlpha((frame.world?.walls[wall.id]?.fadeTicks ?? ADVENTURE.tuning.wallFadeTicks) / ADVENTURE.tuning.wallFadeTicks);
    if (!state) return;
    const items = availableItems(state);
    const wanted = new Set<string>();
    for (const item of items.filter(value => value.screen === screen)) {
      const texture = this.manifest.items.find(value => value.id === item.id)?.texture || this.manifest.itemTextures[item.item];
      if (!texture) continue;
      wanted.add(item.id);
      if (!this.items.has(item.id)) this.items.set(item.id, this.scene.add.image(item.x, item.y - item.screen * V.height, texture).setOrigin(0.5, 1).setDepth(9).setName('item:' + item.id));
    }
    for (const [id, image] of this.items) if (!wanted.has(id)) { image.destroy(); this.items.delete(id); }
    for (const definition of this.manifest.birds) {
      const current = state.birds[definition.name]; if (!current) continue;
      const perch = definition.positions[current.index]!;
      let sprite = this.birds.get(definition.name);
      if (!sprite) { sprite = this.scene.add.sprite(0, 0, definition.texture, 'idle:0').setOrigin(0.5).setDepth(9).setName('bird:' + definition.name); this.birds.set(definition.name, sprite); }
      const animation = current.flying ? 'fly:' + ((perch.treasure ? current.vy > 0 ? 6 : 3 : 0) + Math.floor(current.ticks / 6) % 3) : 'idle:' + ((perch.treasure ? 4 : 0) + [0, 2, 3][Math.floor(frame.self.state.ticks / 12) % 3]!);
      sprite.setVisible(!current.done && perch.screen === screen).setPosition(current.x, current.y).setFlipX((current.flying ? perch.flyDirection : perch.direction) < 0).setFrame(animation);
    }
  }
}
