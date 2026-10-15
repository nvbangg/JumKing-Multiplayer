import { expect, test } from '@playwright/test';

test.skip(process.env.E2E_AUTH_ONLINE !== 'true', 'Requires the isolated backend/MySQL integration harness');
test.beforeEach(async ({ page }) => { await page.addInitScript(() => localStorage.setItem('jumpking.options', JSON.stringify({ sound: false }))); });

test('account forms persist the session and keep Guest Solo separate through sign out', async ({ page, context }, info) => {
  const name = 'player_' + Date.now(), email = name + '@example.test', password = 'A secure browser password';
  await page.goto('/'); await page.getByRole('button', { name: 'New Solo Game', exact: true }).click();
  await page.getByRole('button', { name: 'Menu', exact: true }).click(); await page.getByRole('button', { name: 'Save & Exit', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Continue Solo', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Create Account', exact: true }).click();
  await page.getByLabel('Username', { exact: true }).fill(name); await page.getByLabel('Email', { exact: true }).fill(email); await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Create Account', exact: true }).click();
  await expect(page.getByRole('button', { name, exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Continue Solo', exact: true })).toBeHidden();
  const cookie = (await context.cookies()).find(cookie => cookie.name === 'JSESSIONID'); expect(cookie?.httpOnly).toBe(true); expect(cookie?.sameSite).toBe('Lax');
  await page.getByRole('button', { name, exact: true }).click(); await expect(page.getByText(email, { exact: true })).toBeVisible();
  await expect(page.getByLabel('Total statistics')).toBeHidden();
  await page.reload(); await expect(page.getByText(email, { exact: true })).toBeVisible();
  await page.screenshot({ path: info.outputPath('account.png'), fullPage: true });
  await page.getByRole('button', { name: 'Sign Out', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Sign In', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Continue Solo', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Sign In', exact: true }).click();
  await page.getByLabel('Email', { exact: true }).fill(email); await page.getByLabel('Password', { exact: true }).fill('incorrect');
  await page.getByRole('button', { name: 'Sign In', exact: true }).click(); await expect(page.getByRole('alert')).toHaveText('Email or password is incorrect');
  await page.getByLabel('Password', { exact: true }).fill(password); await page.getByRole('button', { name: 'Sign In', exact: true }).click();
  await expect(page.getByRole('button', { name, exact: true })).toBeVisible();
});

test('two browser identities stay isolated and duplicate registration is a safe error', async ({ browser }) => {
  const first = await browser.newContext(), second = await browser.newContext();
  try {
    const a = await first.newPage(), b = await second.newPage(), name = 'isolated_' + Date.now(), email = name + '@example.test';
    for (const page of [a, b]) { await page.goto('/'); await page.getByRole('button', { name: 'Create Account', exact: true }).click(); await page.getByLabel('Username', { exact: true }).fill(name); await page.getByLabel('Email', { exact: true }).fill(email); await page.getByLabel('Password', { exact: true }).fill('A secure second password'); }
    await a.getByRole('button', { name: 'Create Account', exact: true }).click(); await expect(a.getByRole('button', { name, exact: true })).toBeVisible();
    await b.getByRole('button', { name: 'Create Account', exact: true }).click(); await expect(b.getByRole('alert')).toHaveText('Username or email is unavailable');
    await b.getByRole('button', { name: 'Back', exact: true }).click(); await expect(b.getByRole('button', { name: 'Sign In', exact: true })).toBeVisible();
    await a.reload(); await expect(a.getByRole('button', { name, exact: true })).toBeVisible();
  } finally { await first.close(); await second.close(); }
});

test('Guest Room history offers Skip and merges only after explicit confirmation', async ({ page, context }) => {
  const name = process.env.E2E_MERGE_USERNAME!, email = process.env.E2E_MERGE_EMAIL!, password = process.env.E2E_MERGE_PASSWORD!, token = process.env.E2E_MERGE_GUEST!;
  expect(name).toBeTruthy(); expect(token).toBeTruthy();
  await context.addCookies([{ name: 'JK_GUEST', value: token, url: process.env.E2E_BASE_URL!, httpOnly: true, sameSite: 'Lax' }]);
  const signIn = async () => {
    await page.getByRole('button', { name: 'Sign In', exact: true }).click();
    await page.getByLabel('Email', { exact: true }).fill(email); await page.getByLabel('Password', { exact: true }).fill(password);
    await page.getByRole('button', { name: 'Sign In', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Merge Guest Progress', exact: true })).toBeVisible();
  };
  await page.goto('/'); await signIn(); await page.getByRole('button', { name: 'Skip', exact: true }).click();
  await page.getByRole('button', { name, exact: true }).click(); await page.getByRole('button', { name: 'Sign Out', exact: true }).click();
  await signIn(); await page.getByRole('button', { name: 'Merge Guest Progress', exact: true }).click();
  await expect(page.getByRole('button', { name, exact: true })).toBeVisible();
  expect((await context.cookies()).find(cookie => cookie.name === 'JK_GUEST')).toBeUndefined();
});

test('public map/config match the server contract and a rejected handshake closes cleanly', async ({ page }) => {
  let connections = 0; page.on('websocket', socket => { if (new URL(socket.url()).pathname === '/ws/game') connections++; }); await page.goto('/');
  const result = await page.evaluate(async () => {
    const path = '/src/network/GameSocket.ts', { GameSocket } = await import(path);
    const map = await (await fetch('/api/map')).json(), config = await (await fetch('/api/game-config')).json();
    let disconnected = 0; const socket = new GameSocket(() => {}, () => { disconnected++; });
    const first = socket.connect(), second = socket.connect(); const shared = first === second;
    const attempts = await Promise.allSettled([first, second]); socket.close();
    return { screens: map.screens.length, appearances: config.appearances.length, fly: config.features.flyEnabled, shared, disconnected, rejected: attempts.every(result => result.status === 'rejected') };
  });
  expect(result).toEqual({ screens: 43, appearances: 10, fly: false, shared: true, disconnected: 1, rejected: true }); expect(connections).toBe(1);
});
