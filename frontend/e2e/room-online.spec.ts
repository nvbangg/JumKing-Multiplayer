import { expect, test } from '@playwright/test';

test.skip(process.env.E2E_AUTH_ONLINE !== 'true', 'Requires the isolated backend/MySQL integration harness');
test('two Guest browsers create and join a Room, share settings and transfer Host on Leave', async ({ browser }) => {
  const first = await browser.newContext(), second = await browser.newContext();
  try {
    for (const context of [first, second]) await context.addInitScript(() => { if (location.protocol.startsWith('http')) localStorage.setItem('jumpking.options', JSON.stringify({ sound: false })); });
    const a = await first.newPage(), b = await second.newPage();
    await a.goto('/'); await a.getByRole('button', { name: 'Play with Friends', exact: true }).click();
    await a.getByLabel('Nickname', { exact: true }).fill('First Guest'); await a.getByRole('button', { name: 'Create Room', exact: true }).click();
    await expect(a.getByRole('button', { name: 'Start', exact: true })).toBeDisabled();
    const room = await a.locator('.room-code code').innerText(); expect(room).toMatch(/^[A-HJ-NP-Z2-9]{6}$/);
    await b.goto('/'); await b.getByRole('button', { name: 'Join by ID', exact: true }).click(); await b.getByLabel('Room ID', { exact: true }).fill(room); await b.getByRole('button', { name: 'Join', exact: true }).click();
    await b.getByLabel('Nickname', { exact: true }).fill('Second Guest'); await b.getByRole('button', { name: 'Join', exact: true }).click();
    await expect(a.locator('.players')).toContainText('Second Guest'); await expect(b.locator('.players')).toContainText('First Guest');
    await expect(a.getByRole('button', { name: 'Start', exact: true })).toBeEnabled(); await expect(b.getByLabel('Mode', { exact: true })).toBeDisabled();
    await a.getByRole('button', { name: 'Chat', exact: true }).click(); await a.getByLabel('Message', { exact: true }).fill('<b>Hello Room</b>'); await a.getByRole('button', { name: 'Send', exact: true }).click();
    await expect(a.getByRole('log')).toContainText('<b>Hello Room</b>'); await a.getByRole('button', { name: 'Close chat', exact: true }).click();
    await b.getByRole('button', { name: 'Chat', exact: true }).click(); await expect(b.getByRole('log')).toContainText('First Guest: <b>Hello Room</b>'); expect(await b.getByRole('log').locator('b').count()).toBe(0); await b.getByRole('button', { name: 'Close chat', exact: true }).click();
    await a.getByLabel('Mode', { exact: true }).selectOption('TIME_LIMIT'); await a.getByLabel('Minutes', { exact: true }).fill('12');
    await expect(b.getByLabel('Minutes', { exact: true })).toHaveValue('12');
    await a.getByLabel('Mode', { exact: true }).selectOption('NO_WALK'); await expect(b.getByLabel('Mode', { exact: true })).toHaveValue('NO_WALK');
    await a.getByRole('button', { name: 'Leave', exact: true }).click(); await expect(a.getByRole('button', { name: 'New Solo Game', exact: true })).toBeVisible();
    await expect(b.getByLabel('Mode', { exact: true })).toBeEnabled(); await expect(b.getByRole('button', { name: 'Start', exact: true })).toBeDisabled(); expect(await b.locator('.room-code code').innerText()).toBe(room);
    await b.getByRole('button', { name: 'Leave', exact: true }).click(); await expect(b.getByRole('button', { name: 'New Solo Game', exact: true })).toBeVisible();
    expect((await (await a.request.get('/api/history')).json()).items).toEqual([]);
  } finally { await first.close(); await second.close(); }
});
