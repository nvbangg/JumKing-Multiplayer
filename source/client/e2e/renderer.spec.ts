import { expect, test } from '@playwright/test';
import checkpoints from '../../common/fixtures/main-babe-checkpoints.json' with { type: 'json' };

test('renderer draws all 43 screens and keeps self above translucent peers', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('console', message => { if (/Texture .* has no frame/.test(message.text())) errors.push(message.text()); });
  await page.goto('/');
  const output = await page.evaluate(async states => {
    const modulePath = '/src/game/presentation/createGame.ts';
    const { createGame } = await import(modulePath);
    const manifest = await (await fetch('/assets/manifest.json')).json();
    const parent = document.createElement('div');
    parent.style.cssText = 'position:fixed;inset:0;background:black;z-index:100'; document.body.append(parent);
    let state = states[0]!;
    const failures: string[] = [];
    let ready!: () => void;
    const loaded = new Promise<void>(resolve => { ready = resolve; });
    const runtime = {
      advance() {},
      frame: () => ({ self: { id: 'self', nickname: 'You', color: '#ffffff', state, equipment: ['Cap'] }, peers: [{ id: 'peer', nickname: 'Friend', color: '#ffffff', state: { ...state, x: state.x + 30 }, equipment: ['Cap'] }] }),
    };
    const game = createGame(parent, manifest, runtime, ready, (error: string) => failures.push(error));
    await loaded;
    const frames = () => new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve())));
    const screens: number[] = [];
    try {
      for (let screen = 0; screen < 43; screen++) {
        state = states.find(candidate => candidate.screen === screen) || { ...states[0]!, screen, x: 231, y: -screen * 360 + 302 };
        await frames();
        const scene = game.scene.getScene('Tower');
        const sprites = scene.children.list.filter((child: { type: string; name: string; texture?: { key: string } }) => child.type === 'Sprite' && child.texture?.key === 'king/base' && child.name.startsWith('player:'));
        if (sprites.length !== 2 || !sprites.some((sprite: { alpha: number }) => sprite.alpha === 1) || !sprites.some((sprite: { alpha: number }) => sprite.alpha === 0.7)) failures.push('Avatar opacity ' + screen);
        const self = sprites.find((sprite: { alpha: number }) => sprite.alpha === 1);
        const peer = sprites.find((sprite: { alpha: number }) => sprite.alpha === 0.7);
        if (self.depth <= peer.depth) failures.push('Self depth ' + screen);
        const caps = scene.children.list.filter((child: { type: string; texture?: { key: string } }) => child.type === 'Sprite' && child.texture?.key === 'king/cap');
        if (caps.length !== 2 || !caps.some((cap: { alpha: number; depth: number }) => cap.alpha === 1 && cap.depth > self.depth) || !caps.some((cap: { alpha: number; depth: number }) => cap.alpha === 0.7 && cap.depth < self.depth)) failures.push('Equipment ' + screen);
        if (scene.cameras.main.scrollY !== -screen * 360) failures.push('Camera ' + screen);
        const labels = scene.children.list.filter((child: { type: string }) => child.type === 'Text');
        if (labels.length !== 2 || labels.some((label: { alpha: number; visible: boolean }) => label.alpha !== 1 || !label.visible)) failures.push('Names ' + screen);
        screens.push(screen);
      }
      return { screens, failures };
    } finally { game.destroy(true); parent.remove(); }
  }, checkpoints);
  expect(output.screens).toHaveLength(43);
  expect(output.failures).toEqual([]);
  expect(errors).toEqual([]);
});
