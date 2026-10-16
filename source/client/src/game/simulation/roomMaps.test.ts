import { expect, test } from "vitest";
import checkpoints from "../../../../common/fixtures/main-babe-checkpoints.json";
import route from "../../../../common/fixtures/main-babe-route.json";
import map from "../../../../common/maps/main-babe.json";
import { GameSimulation } from "./GameSimulation";
import { ROOM_MAPS, roomMap } from "./roomMaps";
import { PHYSICS } from "./tuning";
import { NEUTRAL_INPUT, type PlayerInput } from "./types";

test("Room maps keep the ten approved Finish boundaries", () => {
	expect(
		ROOM_MAPS.map(({ level, finish }) => [
			level,
			finish.screen,
			finish.minCenterX,
			finish.minProgress,
			finish.requiresGrounded,
		]),
	).toEqual([
		[1, 5, 288, 2048, true],
		[2, 9, 248, 3480, true],
		[3, 13, 216, 4912, true],
		[4, 18, 128, 6752, true],
		[5, 24, 88, 8912, true],
		[6, 25, 192, 9232, true],
		[7, 31, 16, 11392, true],
		[8, 35, 256, 12816, true],
		[9, 38, 248, 13912, true],
		[10, 42, 336, 0, true],
	]);
});

for (const goal of ROOM_MAPS)
	test(`Room ${goal.level}: Finish includes both boundaries and requires landing`, () => {
		const world = roomMap(map, goal.level),
			game = new GameSimulation(world),
			state = {
				...game.physics.snapshot(),
				x: goal.finish.minCenterX - PHYSICS.bodyWidth / 2,
				y: world.progressOriginY - goal.finish.minProgress - PHYSICS.bodyHeight,
				screen: goal.finish.screen,
				grounded: true,
			};
		game.physics.restore(state);
		expect(game.finished).toBe(true);
		game.physics.restore({ ...state, x: state.x - 0.25 });
		expect(game.finished).toBe(false);
		// Progress bị chặn tại 0 nên Map 10 không có giá trị dưới ngưỡng.
		if (goal.finish.minProgress > 0) {
			game.physics.restore({ ...state, y: state.y + 0.25 });
			expect(game.finished).toBe(false);
		}
		game.physics.restore({ ...state, grounded: false });
		expect(game.finished).toBe(false);
		game.physics.restore({ ...state, screen: state.screen - 1 });
		expect(game.finished).toBe(false);
	});

test.each([
	[1, 2064, 73.875, 2056, false],
	[1, 2217, 303.75, 2048, true],
	[5, 9469, 91.5, 8912, true],
	[8, 14236, 289.75, 12816, true],
] as const)(
	"Room %i: canonical landing at tick %i has the expected Finish result",
	(level, ticks, centerX, progress, finished) => {
		const game = new GameSimulation(roomMap(map, level)),
			point = checkpoints.find((checkpoint) => checkpoint.ticks === ticks)!;
		expect(point).toBeDefined();
		game.physics.restore({ ...point, codecVersion: 1 });
		expect(point.x + PHYSICS.bodyWidth / 2).toBe(centerX);
		expect(game.physics.progress).toBe(progress);
		expect(game.finished).toBe(finished);
	},
);

test("Room 10 keeps the original Main Babe Finish", () => {
	expect(roomMap(map, 10).finish).toEqual({ ...map.finish, minProgress: 0 });
});

for (const goal of ROOM_MAPS)
	test(`Room ${goal.level}: reachable prefix and full-map reward boundary`, () => {
		const world = roomMap(map, goal.level),
			game = new GameSimulation(world);
		expect(world.screens).toBe(map.screens);
		expect(world.spawn).toBe(map.spawn);
		const step = (input: PlayerInput = NEUTRAL_INPUT) => {
			if (!game.finished) game.tick(input);
		};
		for (let i = 0; i < 30; i++) step();
		for (const action of route) {
			if ("walk" in action) {
				for (let i = 0; i < action.ticks!; i++)
					step({
						left: action.walk! < 0,
						right: action.walk! > 0,
						jump: false,
					});
			} else {
				step();
				for (let i = 0; i < action.charge!; i++)
					step({
						left: action.charge === 36 && action.direction! < 0,
						right: action.charge === 36 && action.direction! > 0,
						jump: true,
					});
				if (action.charge !== 36)
					step({
						left: action.direction! < 0,
						right: action.direction! > 0,
						jump: false,
					});
				for (let i = 0; i < 180; i++) {
					step();
					if (game.physics.snapshot().grounded && i > 0) break;
				}
			}
			if (game.finished) break;
		}
		const state = game.physics.snapshot();
		expect(game.finished).toBe(true);
		expect(state.screen).toBe(goal.finish.screen);
		expect(state.falls).toBe(0);
		expect(state.freeFlight).toBe(false);
		expect(game.adventure.completed).toBe(goal.level === 10);
		const landing = checkpoints.find(
			(point) =>
				point.screen === goal.finish.screen &&
				point.grounded &&
				map.progressOriginY - point.y - 26 >= goal.finish.minProgress &&
				point.x + 9 >= goal.finish.minCenterX,
		);
		expect(landing).toBeDefined();
		expect(state.ticks).toBeLessThanOrEqual(landing!.ticks);
		const airborne = { ...state, grounded: false };
		game.physics.restore(airborne);
		expect(game.finished).toBe(false);
	});

test("invalid Room lengths fail closed", () => {
	for (const level of [0, 11, 1.5, NaN])
		expect(() => roomMap(map, level)).toThrow();
});
