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
