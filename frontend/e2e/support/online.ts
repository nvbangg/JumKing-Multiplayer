import type { Page } from '@playwright/test';

/** Dọn đúng membership của browser, kể cả khi assertion thất bại; không xóa dữ liệu server. */
export async function leaveOnline(page: Page): Promise<void> {
  if (page.isClosed() || !page.url().startsWith('http')) return;
  await page.evaluate(async () => {
    const socketPath = '/src/network/GameSocket.ts', reconnectPath = '/src/network/reconnect.ts';
    const { loadReconnect } = await import(reconnectPath);
    const me = await (await fetch('/api/auth/me')).json();
    const profile = location.origin + (me.account ? ':account:' + me.account.id : ':guest');
    if (!await loadReconnect(profile)) return;
    const { GameSocket } = await import(socketPath), socket = new GameSocket(() => {}, () => {});
    try { await socket.restore(profile); if (socket.selfId) await socket.command('LEAVE_ROOM'); }
    finally { socket.close(); }
  });
}
