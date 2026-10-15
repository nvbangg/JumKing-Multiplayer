import { expect, test } from "@playwright/test";
import { leaveOnline } from "./support/online";

test.skip(
	process.env.E2E_AUTH_ONLINE !== "true",
	"Requires the isolated backend/MySQL integration harness",
);
test("Community records refresh only while expanded and never more than once per ten seconds", async ({
	page,
}, info) => {
	test.setTimeout(60_000);
	const requests: number[] = [],
		errors: string[] = [];
	page.on("request", (request) => {
		if (new URL(request.url()).pathname === "/api/leaderboard")
			requests.push(Date.now());
	});
	page.on("pageerror", (error) => errors.push(error.message));
	try {
		const name =
			"board_" + crypto.randomUUID().replaceAll("-", "").slice(0, 12);
		await page.addInitScript(() =>
			localStorage.setItem(
				"jumpking.options",
				JSON.stringify({ sound: false }),
			),
		);
		await page.goto("/");
		await page
			.getByRole("button", { name: "Create Account", exact: true })
			.click();
		await page.getByLabel("Username", { exact: true }).fill(name);
		await page
			.getByLabel("Email", { exact: true })
			.fill(name + "@example.test");
		await page
			.getByLabel("Password", { exact: true })
			.fill("A secure leaderboard test password");
		await page
			.getByRole("button", { name: "Create Account", exact: true })
			.click();
		await expect(page.getByRole("button", { name, exact: true })).toBeVisible();
		await page
			.getByRole("button", { name: "Community Tower", exact: true })
			.click();
		await page.getByRole("button", { name: "New Game", exact: true }).click();
		await expect(
			page.getByRole("button", { name: "Menu", exact: true }),
		).toBeVisible({ timeout: 15_000 });
		expect(requests).toHaveLength(0);
		const summary = page
			.locator("summary")
			.filter({ hasText: /^Leaderboard$/ });
		await summary.click();
		await expect(
			page.getByRole("list", { name: "Community records" }),
		).toBeVisible();
		expect(requests).toHaveLength(1);
		await page
			.locator("summary")
			.filter({ hasText: /^Players$/ })
			.click();
		await page.screenshot({ path: info.outputPath("community-boards.png") });
		await summary.click();
		await summary.click();
		expect(requests).toHaveLength(1);
		await expect.poll(() => requests.length, { timeout: 12_000 }).toBe(2);
		expect(requests[1]! - requests[0]!).toBeGreaterThanOrEqual(9900);
		await summary.click();
		const closedCount = requests.length;
		await page.waitForTimeout(10_500);
		expect(requests).toHaveLength(closedCount);
		await page.getByRole("button", { name: "Menu", exact: true }).click();
		await page
			.getByRole("button", { name: "Save & Exit", exact: true })
			.click();
		expect(errors).toEqual([]);
	} finally {
		await leaveOnline(page);
	}
});
