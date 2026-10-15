import { expect, test } from '@playwright/test';
import type { OnlineSnapshot } from '../src/game/netcode/OnlineRuntime';
import { leaveOnline } from './support/online';

test.skip(process.env.E2E_AUTH_ONLINE !== 'true', 'Requires the isolated backend/MySQL integration harness');
test.afterEach(async ({ page }) => { await leaveOnline(page); });
test('server pickup and equipment persist across Continue without repeating the pickup', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('jumpking.options', JSON.stringify({ sound: false })));
  const snapshots: OnlineSnapshot[] = [], cues: Record<string, unknown>[] = [], errors: unknown[] = [];
  page.on('websocket', socket => socket.on('framereceived', frame => { const message = JSON.parse(frame.payload.toString()); if (message.type === 'GAME_SNAPSHOT') snapshots.push(message.payload); if (message.type === 'ADVENTURE_EVENTS') cues.push(message.payload); if (message.type === 'ERROR') errors.push(message.payload); }));
  await page.goto('/'); await page.getByRole('button', { name: 'Sign In', exact: true }).click();
  await page.getByLabel('Email', { exact: true }).fill(process.env.E2E_ADVENTURE_EMAIL!); await page.getByLabel('Password', { exact: true }).fill(process.env.E2E_ADVENTURE_PASSWORD!); await page.getByRole('button', { name: 'Sign In', exact: true }).click();
  await page.getByRole('button', { name: 'Community Tower', exact: true }).click(); await page.getByRole('button', { name: 'Continue', exact: true }).click(); await expect(page.getByRole('button', { name: 'Menu', exact: true })).toBeVisible({ timeout: 15_000 });
  await expect.poll(() => snapshots.at(-1)?.self.simulation.adventure.inventory.Cap).toBe(1);
  expect(cues.flatMap(payload => payload.events as { type: string; id: string }[]).filter(cue => cue.type === 'PICKUP' && cue.id === 'world:0')).toHaveLength(1);
  await page.getByRole('button', { name: 'Menu', exact: true }).click(); await page.getByRole('button', { name: 'Inventory', exact: true }).click();
  await page.getByRole('switch', { name: 'Cap: On', exact: true }).click(); await expect(page.getByRole('switch', { name: 'Cap: Off', exact: true })).toBeVisible();
  await page.getByRole('switch', { name: 'Cap: Off', exact: true }).click(); await expect(page.getByRole('switch', { name: 'Cap: On', exact: true })).toBeVisible();
  await expect.poll(() => snapshots.at(-1)?.self.simulation.adventure.equipped.includes('Cap')).toBe(true);
  await page.getByRole('button', { name: 'Back', exact: true }).click(); await page.getByRole('button', { name: 'Save & Exit', exact: true }).click(); await expect(page.getByRole('button', { name: 'New Solo Game', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Community Tower', exact: true }).click(); await page.getByRole('button', { name: 'Continue', exact: true }).click(); await expect(page.getByRole('button', { name: 'Menu', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Menu', exact: true }).click(); await page.getByRole('button', { name: 'Inventory', exact: true }).click(); await expect(page.getByRole('switch', { name: 'Cap: On', exact: true })).toBeVisible();
  await page.getByRole('switch', { name: 'Cap: On', exact: true }).click(); await expect(page.getByRole('switch', { name: 'Cap: Off', exact: true })).toBeVisible();
  expect(cues.flatMap(payload => payload.events as { type: string; id: string }[]).filter(cue => cue.type === 'PICKUP' && cue.id === 'world:0')).toHaveLength(1);
  await page.getByRole('button', { name: 'Back', exact: true }).click(); await page.getByRole('button', { name: 'Save & Exit', exact: true }).click(); await expect(page.getByRole('button', { name: 'New Solo Game', exact: true })).toBeVisible(); expect(errors).toEqual([]);
});
