export type TutorialStep = "move" | "charge" | "jump" | "completed";

const TUTORIAL_PREFIX = "jumpking.tutorial";

export function isTutorialCompleted(
	accountId?: string,
	totalJumps?: number,
): boolean {
	if (typeof totalJumps === "number" && totalJumps > 0) return true;
	try {
		const key = accountId ? `${TUTORIAL_PREFIX}.${accountId}` : TUTORIAL_PREFIX;
		return localStorage.getItem(key) === "true";
	} catch {
		return false;
	}
}

export function saveTutorialCompleted(accountId?: string): void {
	try {
		const key = accountId ? `${TUTORIAL_PREFIX}.${accountId}` : TUTORIAL_PREFIX;
		localStorage.setItem(key, "true");
	} catch {
		// Bỏ qua nếu localStorage bị chặn hoặc đầy
	}
}

export function nextTutorialStep(
	current: TutorialStep,
	state: { x: number; charge: number; grounded: boolean },
	originX: number,
): TutorialStep {
	switch (current) {
		case "move":
			return Math.abs(state.x - originX) >= 8 ? "charge" : "move";
		case "charge":
			return state.charge >= 4 ? "jump" : "charge";
		case "jump":
			return !state.grounded ? "completed" : "jump";
		case "completed":
			return "completed";
	}
}
