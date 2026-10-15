import { expect, test } from '@playwright/test';
import type { OnlineSnapshot } from '../src/game/netcode/OnlineRuntime';
import { leaveOnline } from './support/online';

test.skip(process.env.E2E_ROOM_FLY !== 'true', 'Requires the local-profile Room flight harness');
for (const mode of ['RACE', 'HARDCORE', 'TIME_LIMIT', 'NO_WALK']) test(`two browsers can enable and cancel flight in ${mode}`, async ({ browser }) => {
  const first = await browser.newContext(), second = await browser.newContext();
  try {
    for (const context of [first, second]) await context.addInitScript(() => { if (location.protocol.startsWith('http')) localStorage.setItem('jumpking.options', JSON.stringify({ sound: false })); });
    const a = await first.newPage(), b = await second.newPage(), snapshots: OnlineSnapshot[] = [], errors: unknown[] = [];
    for (const page of [a, b]) page.on('pageerror', error => errors.push(error.message));
    a.on('websocket', socket => socket.on('framereceived', frame => { const message = JSON.parse(frame.payload.toString()); if (message.type === 'GAME_SNAPSHOT') snapshots.push(message.payload); if (message.type === 'ERROR') errors.push(message.payload); }));
    await a.goto('/'); await a.getByRole('button', { name: 'Play with Friends', exact: true }).click(); await a.getByLabel('Nickname', { exact: true }).fill('Flying Host'); await a.getByRole('button', { name: 'Create Room', exact: true }).click();
    await a.getByLabel('Mode', { exact: true }).selectOption(mode); const room = await a.locator('.room-code code').innerText();
    await b.goto('/'); await b.getByRole('button', { name: 'Join by ID', exact: true }).click(); await b.getByLabel('Room ID', { exact: true }).fill(room); await b.getByRole('button', { name: 'Join', exact: true }).click(); await b.getByLabel('Nickname', { exact: true }).fill('Peer'); await b.getByRole('button', { name: 'Join', exact: true }).click();
    await a.bringToFront(); await a.getByRole('button', { name: 'Start', exact: true }).click(); await expect(a.getByRole('button', { name: 'Menu', exact: true })).toBeVisible({ timeout: 15_000 });
    if (!await a.getByRole('button', { name: 'Resume', exact: true }).isVisible()) await a.getByRole('button', { name: 'Menu', exact: true }).click();
    await a.getByRole('button', { name: 'Options', exact: true }).click(); await a.getByRole('switch', { name: 'Fly Mode: Off', exact: true }).click();
    await a.getByRole('button', { name: 'Back', exact: true }).click(); await a.getByRole('button', { name: 'Resume', exact: true }).click();
    await a.keyboard.press('Space'); await a.waitForTimeout(80); await a.keyboard.press('Space'); await expect.poll(() => snapshots.at(-1)?.self.simulation.physics.freeFlight).toBe(true);
    const y = snapshots.at(-1)!.self.simulation.physics.y;
    await a.keyboard.down('ArrowUp'); await a.waitForTimeout(200); await a.keyboard.up('ArrowUp'); await expect.poll(() => snapshots.at(-1)?.self.simulation.physics.y).toBeLessThan(y - 20);
    await a.keyboard.press('Space'); await a.waitForTimeout(80); await a.keyboard.press('Space'); await expect.poll(() => snapshots.at(-1)?.self.simulation.physics.freeFlight).toBe(false);
    await a.getByRole('button', { name: 'Menu', exact: true }).click(); await a.getByRole('button', { name: 'End Match', exact: true }).click(); await expect(a.getByRole('button', { name: 'Play Again', exact: true })).toBeEnabled();
    const history = (await (await a.request.get('/api/history')).json()).items; expect(history[0].room.mode).toBe(mode); expect(history[0].maxProgress).toBeGreaterThan(20); expect(errors).toEqual([]);
    await a.getByRole('button', { name: 'Leave', exact: true }).click(); await b.getByRole('button', { name: 'Leave', exact: true }).click();
  } finally { for (const context of [first, second]) for (const page of context.pages()) await leaveOnline(page); await first.close(); await second.close(); }
});
