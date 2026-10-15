import { expect, test } from "@playwright/test";

test.skip(
	process.env.E2E_PACKAGED !== "true",
	"Requires the packaged web JAR and isolated MySQL",
);
test("the production web JAR supports Community save and a two-player Room through its own origin", async ({
	browser,
}) => {
	test.setTimeout(60_000);
	const account = await browser.newContext(),
		guest = await browser.newContext();
	try {
		for (const context of [account, guest])
			await context.addInitScript(() =>
				localStorage.setItem(
					"jumpking.options",
					JSON.stringify({ sound: false, displayTimer: true }),
				),
			);
		const a = await account.newPage(),
			b = await guest.newPage();
		await a.goto("/");
		const username =
			"jar_" + crypto.randomUUID().replaceAll("-", "").slice(0, 12);
		await a
			.getByRole("button", { name: "Create Account", exact: true })
			.click();
		await a.getByLabel("Username", { exact: true }).fill(username);
		await a
			.getByLabel("Email", { exact: true })
			.fill(username + "@example.test");
		await a
			.getByLabel("Password", { exact: true })
			.fill("A secure packaged browser password");
		await a
			.getByRole("button", { name: "Create Account", exact: true })
			.click();
		await expect(
			a.getByRole("button", { name: username, exact: true }),
		).toBeVisible();
		await a
			.getByRole("button", { name: "Community Tower", exact: true })
			.click();
		await a.getByRole("button", { name: "New Game", exact: true }).click();
		await expect(
			a.getByRole("button", { name: "Menu", exact: true }),
		).toBeVisible();
		await a.getByRole("button", { name: "Menu", exact: true }).click();
		await a.getByRole("button", { name: "Save & Exit", exact: true }).click();
		await expect(
			a.getByRole("button", { name: "New Solo Game", exact: true }),
		).toBeVisible();
		await a
			.getByRole("button", { name: "Community Tower", exact: true })
			.click();
		await expect(
			a.getByRole("button", { name: "Continue", exact: true }),
		).toBeVisible();
		await a.getByRole("button", { name: "Back", exact: true }).click();
		await a
			.getByRole("button", { name: "Play with Friends", exact: true })
			.click();
		await a.getByLabel("Nickname", { exact: true }).fill("JAR Host");
		await a.getByRole("button", { name: "Create Room", exact: true }).click();
		const code = await a.locator(".room-code code").innerText();
		await expect(
			a.getByRole("button", { name: "Start", exact: true }),
		).toBeDisabled();
		await b.goto("/");
		await b.getByRole("button", { name: "Join by ID", exact: true }).click();
		await b.getByLabel("Room ID", { exact: true }).fill(code);
		await b.getByRole("button", { name: "Join", exact: true }).click();
		await b.getByLabel("Nickname", { exact: true }).fill("JAR Guest");
		await b.getByRole("button", { name: "Join", exact: true }).click();
		await a.bringToFront();
		await a.getByRole("button", { name: "Start", exact: true }).click();
		for (const page of [a, b])
			await expect(
				page.getByRole("button", { name: "Menu", exact: true }),
			).toBeVisible({ timeout: 15_000 });
		if (
			await a.getByRole("button", { name: "Resume", exact: true }).isVisible()
		)
			await a.getByRole("button", { name: "Resume", exact: true }).click();
		await a.keyboard.down("Space");
		await a.waitForTimeout(220);
		await a.keyboard.up("Space");
		await expect(a.locator(".game-hud")).toContainText("Jumps: 1");
		await a.getByRole("button", { name: "Menu", exact: true }).click();
		await a.getByRole("button", { name: "Pause Room", exact: true }).click();
		await expect(
			b.getByRole("button", { name: "Room Paused", exact: true }),
		).toBeVisible();
		await a.getByRole("button", { name: "End Match", exact: true }).click();
		for (const page of [a, b]) {
			await expect(
				page.getByRole("heading", { name: "Results", exact: true }),
			).toBeVisible();
			await expect(
				page.getByText("Results saved", { exact: true }),
			).toBeVisible();
			await expect(page.getByRole("table").locator("tbody tr")).toHaveCount(2);
			await page.getByRole("button", { name: "Leave", exact: true }).click();
			await expect(
				page.getByRole("button", { name: "New Solo Game", exact: true }),
			).toBeVisible();
		}
		await a.getByRole("button", { name: "History", exact: true }).click();
		await a
			.getByLabel("Game history")
			.getByRole("button")
			.filter({ hasText: "ROOM" })
			.click();
		await expect(a.getByLabel("Match participants")).toContainText("JAR Guest");
	} finally {
		await account.close();
		await guest.close();
	}
});
