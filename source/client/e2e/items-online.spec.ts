import { expect, test } from "@playwright/test";
import {
	joinRoom,
	leaveOnline,
	observeOnline,
	registerOnline,
	startRoom,
} from "./support/online";

test.skip(
	process.env.E2E_ITEMS_ONLINE !== "true",
	"Requires isolated item integration harness",
);
for (const mobile of [false, true])
	test(`Room items creation, use and cooldown (${mobile ? "mobile" : "desktop"})`, async ({
		browser,
	}, info) => {
		test.setTimeout(50000);
		const first = await browser.newContext(
			mobile
				? {
						viewport: { width: 390, height: 844 },
						hasTouch: true,
						isMobile: true,
					}
				: {},
		);
		const second = await browser.newContext();
		const host = await first.newPage(),
			peer = await second.newPage();
		const own = observeOnline(host),
			other = observeOnline(peer);
		const errors: string[] = [];
		let seenSlip = false,
			seenRock = false;
		for (const page of [host, peer]) {
			page.on("pageerror", (e) => errors.push(e.message));
			page.on("websocket", (socket) =>
				socket.on("framereceived", (frame) => {
					const m = JSON.parse(frame.payload.toString());
					if (m.type === "GAME_SNAPSHOT") {
						seenSlip ||= m.payload.self?.simulation?.physics?.slipping === true;
						seenRock ||= m.payload.items?.rocks?.length > 0;
					}
				}),
			);
		}
		try {
			await registerOnline(host, "item_host");
			await registerOnline(peer, "item_peer");
			await host.getByRole("button", { name: "Room", exact: true }).click();
			await host.getByText("New Room Settings", { exact: true }).click();
			await host.getByLabel("Items", { exact: true }).check();
			await expect(
				host.getByLabel("Item Cooldown", { exact: true }),
			).toHaveValue("3");
			await host.getByLabel("Item Cooldown", { exact: true }).selectOption("1");
			await host
				.getByRole("button", { name: "Create Room", exact: true })
				.click();
			const id = (await host.locator(".room-code code").innerText()).trim();
			await expect(host.getByLabel("Items", { exact: true })).toBeChecked();
			await joinRoom(peer, id);
			await expect(peer.getByLabel("Items", { exact: true })).toBeDisabled();
			await host.getByLabel("Map", { exact: true }).selectOption("1");
			await host.screenshot({
				path: info.outputPath(
					mobile ? "lobby-mobile.png" : "lobby-desktop.png",
				),
			});
			await startRoom(host, peer);
			await expect.poll(() => own.snapshot?.items?.held).toMatch(/BANANA|ROCK/);
			const kind = own.snapshot?.items?.held;
			const button = host.getByRole("button", {
				name: "Use item",
				exact: true,
			});
			await expect(button).toBeEnabled();
			if (mobile) await button.tap();
			else await button.click();
			if (kind === "BANANA") {
				await expect(button).toHaveAttribute("aria-pressed", "true");
				expect(own.snapshot?.items?.held).toBe("BANANA");
				if (mobile) {
					const jump = await host
						.getByRole("button", { name: "Jump", exact: true })
						.boundingBox();
					if (!jump) throw new Error("Jump button is missing");
					const cdp = await first.newCDPSession(host);
					await cdp.send("Input.dispatchTouchEvent", {
						type: "touchStart",
						touchPoints: [
							{ x: jump.x + jump.width / 2, y: jump.y + jump.height / 2 },
						],
					});
					await host.waitForTimeout(150);
					await cdp.send("Input.dispatchTouchEvent", {
						type: "touchEnd",
						touchPoints: [],
					});
					await cdp.detach();
				} else {
					await host.keyboard.down("Space");
					await host.waitForTimeout(150);
					await host.keyboard.up("Space");
				}
				// Bẫy có thể được đặt và giẫm giữa hai snapshot 20 Hz; kiểm tra tiêu thụ và tác động thật.
				await expect
					.poll(() => own.snapshot?.items?.cooldownMs)
					.toBeGreaterThan(0);
				await expect.poll(() => seenSlip).toBe(true);
				await expect.poll(() => own.snapshot?.items?.bananas.length).toBe(0);
			} else {
				await expect.poll(() => seenRock).toBe(true);
				await expect
					.poll(() => other.snapshot?.self.simulation.physics.slipping)
					.toBe(true);
			}
			await expect
				.poll(() => own.snapshot?.items?.cooldownMs)
				.toBeGreaterThan(0);
			await expect(button).toBeDisabled();
			await host.screenshot({
				path: info.outputPath(
					mobile ? "items-mobile.png" : "items-desktop.png",
				),
			});
			const bounds = await button.boundingBox(),
				viewport = host.viewportSize();
			expect(
				bounds &&
					viewport &&
					bounds.x >= 0 &&
					bounds.x + bounds.width <= viewport.width,
			).toBeTruthy();
			expect(errors).toEqual([]);
		} finally {
			await leaveOnline(host);
			await leaveOnline(peer);
			await first.close();
			await second.close();
		}
	});
