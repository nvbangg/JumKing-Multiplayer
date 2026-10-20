import { afterEach, beforeEach, expect, test, vi } from "vitest";
import {
	isTutorialCompleted,
	nextTutorialStep,
	saveTutorialCompleted,
} from "./tutorial";

class MockStorage implements Storage {
	private map = new Map<string, string>();
	get length() {
		return this.map.size;
	}
	clear() {
		this.map.clear();
	}
	getItem(key: string): string | null {
		return this.map.get(key) ?? null;
	}
	key(index: number): string | null {
		return Array.from(this.map.keys())[index] ?? null;
	}
	removeItem(key: string) {
		this.map.delete(key);
	}
	setItem(key: string, value: string) {
		this.map.set(key, String(value));
	}
}

let mockStorage: MockStorage;

beforeEach(() => {
	mockStorage = new MockStorage();
	vi.stubGlobal("localStorage", mockStorage);
});

afterEach(() => {
	vi.unstubAllGlobals();
	vi.restoreAllMocks();
});

test("isTutorialCompleted returns true when totalJumps > 0 even without localStorage", () => {
	expect(isTutorialCompleted("user1", 10)).toBe(true);
	expect(isTutorialCompleted("user1", 0)).toBe(false);
	expect(isTutorialCompleted(undefined, 0)).toBe(false);
});

test("saveTutorialCompleted marks account as completed in localStorage", () => {
	expect(isTutorialCompleted("user1", 0)).toBe(false);
	saveTutorialCompleted("user1");
	expect(isTutorialCompleted("user1", 0)).toBe(true);
	expect(isTutorialCompleted("user2", 0)).toBe(false);
});

test("isTutorialCompleted handles localStorage exceptions gracefully", () => {
	vi.spyOn(mockStorage, "getItem").mockImplementation(() => {
		throw new Error("Storage blocked");
	});
	expect(isTutorialCompleted("user1", 0)).toBe(false);
});

test("nextTutorialStep transitions from move to charge when moved >= 8 pixels", () => {
	const originX = 100;
	expect(
		nextTutorialStep("move", { x: 104, charge: 0, grounded: true }, originX),
	).toBe("move");
	expect(
		nextTutorialStep("move", { x: 108, charge: 0, grounded: true }, originX),
	).toBe("charge");
	expect(
		nextTutorialStep("move", { x: 92, charge: 0, grounded: true }, originX),
	).toBe("charge");
});

test("nextTutorialStep transitions from charge to jump when charge >= 4 ticks", () => {
	expect(
		nextTutorialStep("charge", { x: 100, charge: 3, grounded: true }, 100),
	).toBe("charge");
	expect(
		nextTutorialStep("charge", { x: 100, charge: 4, grounded: true }, 100),
	).toBe("jump");
});

test("nextTutorialStep transitions from jump to completed when airborne", () => {
	expect(
		nextTutorialStep("jump", { x: 100, charge: 0, grounded: true }, 100),
	).toBe("jump");
	expect(
		nextTutorialStep("jump", { x: 100, charge: 0, grounded: false }, 100),
	).toBe("completed");
	expect(
		nextTutorialStep("completed", { x: 100, charge: 0, grounded: true }, 100),
	).toBe("completed");
});
