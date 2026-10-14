import { expect, test } from '@playwright/test';

test.skip(process.env.E2E_AUTH_ONLINE !== 'true', 'Requires the isolated backend/MySQL integration harness');
test('committed Community Finish plays only for its owner and New Game clears the ending', async ({ browser }, info) => {
  const first = await browser.newContext(), second = await browser.newContext();
  try {
    for (const context of [first, second]) await context.addInitScript(() => { if (location.protocol.startsWith('http')) localStorage.setItem('jumpking.options', JSON.stringify({ sound: false })); });
    const winner = await first.newPage(), peer = await second.newPage(), errors: string[] = [], finishEvents: Record<string, unknown>[] = [], peerFinishes: unknown[] = [];
    for (const page of [winner, peer]) page.on('pageerror', error => errors.push(error.message));
    winner.on('websocket', socket => { socket.on('framereceived', frame => { const message = JSON.parse(frame.payload.toString()); if (message.type === 'PLAYER_FINISHED') finishEvents.push(message.payload); }); });
    peer.on('websocket', socket => { socket.on('framereceived', frame => { const message = JSON.parse(frame.payload.toString()); if (message.type === 'PLAYER_FINISHED') peerFinishes.push(message.payload); }); });
    const name = 'ending_peer_' + crypto.randomUUID().replaceAll('-', '').slice(0, 10);
    await peer.goto('/'); await peer.getByRole('button', { name: 'Create Account', exact: true }).click();
    await peer.getByLabel('Username', { exact: true }).fill(name); await peer.getByLabel('Email', { exact: true }).fill(name + '@example.test'); await peer.getByLabel('Password', { exact: true }).fill('A secure ending peer password');
    await peer.getByRole('button', { name: 'Create Account', exact: true }).click(); await expect(peer.getByRole('button', { name, exact: true })).toBeVisible();
    await peer.getByRole('button', { name: 'Community Tower', exact: true }).click(); await peer.getByRole('button', { name: 'New Game', exact: true }).click(); await expect(peer.getByRole('button', { name: 'Menu', exact: true })).toBeVisible({ timeout: 15_000 });
    const roomId = await peer.getByLabel('Room ID', { exact: true }).innerText();
    await winner.goto('/'); await winner.getByRole('button', { name: 'Sign In', exact: true }).click();
    await winner.getByLabel('Email', { exact: true }).fill(process.env.E2E_FINISH_EMAIL!); await winner.getByLabel('Password', { exact: true }).fill(process.env.E2E_FINISH_PASSWORD!); await winner.getByRole('button', { name: 'Sign In', exact: true }).click();
    await winner.getByRole('button', { name: 'Join by ID', exact: true }).click(); await winner.getByLabel('Room ID', { exact: true }).fill(roomId); await winner.getByRole('button', { name: 'Join', exact: true }).click();
    await expect(winner.getByRole('button', { name: 'Skip', exact: true })).toBeVisible({ timeout: 15_000 });
    expect(finishEvents).toHaveLength(1); expect(finishEvents[0]!.playId).toBe(process.env.E2E_FINISH_RUN); expect(peerFinishes).toEqual([]);
    expect((await (await winner.request.get('/api/community/current')).json()).run).toBeNull();
    const history = (await (await winner.request.get('/api/history')).json()).items;
    expect(history.filter((row: { id: string }) => row.id === process.env.E2E_FINISH_RUN)).toHaveLength(1); expect(history[0].status).toBe('FINISHED'); expect(history[0].finishTimeMs).toBe(282616);
    await winner.getByRole('button', { name: 'Skip', exact: true }).click(); await expect(winner.getByRole('button', { name: 'New Game', exact: true })).toBeEnabled();
    await winner.screenshot({ path: info.outputPath('community-ending-picture.png') });
    await winner.getByRole('button', { name: 'New Game', exact: true }).click(); await expect(winner.getByRole('button', { name: 'Menu', exact: true })).toBeVisible(); await expect(winner.getByRole('button', { name: 'Skip', exact: true })).toBeHidden();
    await expect(winner.locator('.game-hud')).toContainText('Jumps: 0');
    await winner.getByRole('button', { name: 'Menu', exact: true }).click(); await winner.getByRole('button', { name: 'Save & Exit', exact: true }).click(); await expect(winner.getByRole('button', { name: 'New Solo Game', exact: true })).toBeVisible();
    await peer.bringToFront(); if (await peer.getByRole('button', { name: 'Resume', exact: true }).isVisible()) await peer.getByRole('button', { name: 'Resume', exact: true }).click();
    await peer.keyboard.down('Space'); await peer.waitForTimeout(220); await peer.keyboard.up('Space'); await expect(peer.locator('.game-hud')).toContainText('Jumps: 1');
    await peer.getByRole('button', { name: 'Menu', exact: true }).click(); await peer.getByRole('button', { name: 'Save & Exit', exact: true }).click(); await expect(peer.getByRole('button', { name: 'New Solo Game', exact: true })).toBeVisible();
    expect(peerFinishes).toEqual([]); expect(errors).toEqual([]);
  } finally { await first.close(); await second.close(); }
});
