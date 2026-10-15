import { expect, test } from '@playwright/test';
import checkpoints from '../../common/fixtures/main-babe-checkpoints.json' with { type: 'json' };

test('Solo picks up Cap, saves equipment choice and retains wardrobe in a new game', async ({ page }) => {
  await page.goto('/');
  await page.evaluate(async () => {
    const storage = '/src/features/solo/storage.ts', simulation = '/src/game/simulation/GameSimulation.ts';
    const { SoloWriter } = await import(storage), { GameSimulation } = await import(simulation);
    const map = await (await fetch('/shared/maps/main-babe.json')).json(), game = new GameSimulation(map);
    game.physics.restore({ ...game.physics.snapshot(), x: 311, y: 118 - 5 * map.screenHeight, screen: 5, vy: 0 });
    const writer = await SoloWriter.open('guest', 'new', game.snapshot());
    writer.observe(game.snapshot(), map.progressOriginY, false); await writer.save(); await writer.close();
  });
  await page.reload();
  await page.getByRole('button', { name: 'Continue Solo', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Menu', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Menu', exact: true }).click();
  await page.getByRole('button', { name: 'Inventory', exact: true }).click();
  await expect(page.getByRole('switch', { name: 'Cap: On', exact: true })).toBeVisible();
  await page.getByRole('switch', { name: 'Cap: On', exact: true }).click();
  await page.getByRole('button', { name: 'Back', exact: true }).click();
  await page.getByRole('button', { name: 'Save & Exit', exact: true }).click();
  page.once('dialog', dialog => dialog.accept());
  await page.getByRole('button', { name: 'New Solo Game', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Menu', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Menu', exact: true }).click();
  await page.getByRole('button', { name: 'Inventory', exact: true }).click();
  await expect(page.getByRole('switch', { name: 'Cap: Off', exact: true })).toBeVisible();
});

test('IndexedDB upgrades physics-only saves without deleting run identity or totals', async ({ page }) => {
  await page.goto('/assets/title_logo.png');
  await page.evaluate(async physics => {
    await new Promise<void>((resolve, reject) => {
      const open = indexedDB.open('jumpking', 1);
      open.onupgradeneeded = () => {
        const db = open.result;
        db.createObjectStore('profiles', { keyPath: 'key' });
        db.createObjectStore('runs', { keyPath: 'id' }).createIndex('profile', 'profile');
      };
      open.onerror = () => reject(open.error);
      open.onsuccess = () => {
        const db = open.result, tx = db.transaction(['profiles', 'runs'], 'readwrite');
        tx.objectStore('profiles').put({ key: 'guest', activeRunId: 'old-run', writerId: null, leaseExpiresAt: 0 });
        tx.objectStore('runs').put({ id: 'old-run', profile: 'guest', revision: 3, syncedRevision: 0, status: 'ACTIVE', playTicks: physics.ticks,
          jumps: physics.jumps, falls: physics.falls, currentProgress: 144, maxProgress: 144, peakReachedTicks: physics.ticks,
          finishTicks: null, startedAt: '2026-01-01T00:00:00.000Z', endedAt: null, snapshot: physics });
        tx.oncomplete = () => { db.close(); resolve(); }; tx.onerror = () => reject(tx.error);
      };
    });
  }, checkpoints[0]!);
  await page.goto('/');
  await page.getByRole('button', { name: 'Continue Solo', exact: true }).click();
  await expect(page.getByText('Jumps: 1', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Menu', exact: true }).click();
  await page.getByRole('button', { name: 'Save & Exit', exact: true }).click();
  const restored = await page.evaluate(async () => {
    const path = '/src/features/solo/storage.ts', { currentSolo } = await import(path);
    const run = await currentSolo('guest'); return { id: run.id, jumps: run.jumps, ticks: run.playTicks, adventureCodec: run.snapshot.adventure.codecVersion, worldTick: run.snapshot.world.lastTick };
  });
  expect(restored).toMatchObject({ id: 'old-run', jumps: 1, adventureCodec: 1 });
  expect(restored.ticks).toBeGreaterThanOrEqual(111); expect(restored.worldTick).toBe(restored.ticks);
});
