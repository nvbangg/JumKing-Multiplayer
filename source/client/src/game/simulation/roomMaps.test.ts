import { expect, test } from "vitest";
import checkpoints from "../../../../common/fixtures/main-babe-checkpoints.json";
import route from "../../../../common/fixtures/main-babe-route.json";
import map from "../../../../common/maps/main-babe.json";
import { GameSimulation } from "./GameSimulation";
import { ROOM_MAPS, roomMap } from "./roomMaps";
import { PHYSICS } from "./tuning";
import { NEUTRAL_INPUT, type PlayerInput } from "./types";

const floorEnds = [208, 328, 56, 280, 312, 344, 280, 304, 224, 424];

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
		[1, 3, 136, 1088, true],
		[2, 5, 152, 1800, true],
		[3, 9, 8, 3384, true],
		[4, 13, 216, 4912, true],
		[5, 18, 240, 6584, true],
		[6, 21, 272, 7648, true],
		[7, 24, 208, 8744, true],
		[8, 31, 264, 11304, true],
		[9, 35, 192, 12816, true],
		[10, 42, 112, 15304, true],
	]);
});

for (const goal of ROOM_MAPS)
	test(`Room ${goal.level}: Finish requires landing on the selected floor`, () => {
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
		game.physics.restore({ ...state, y: state.y + 0.25 });
		expect(game.finished).toBe(false);
		game.physics.restore({ ...state, y: state.y - 0.25 });
		expect(game.finished).toBe(false);
		game.physics.restore({ ...state, grounded: false });
		expect(game.finished).toBe(false);
		game.physics.restore({ ...state, screen: state.screen - 1 });
		expect(game.finished).toBe(false);
		const floorEnd = floorEnds[goal.level - 1]!;
		game.physics.restore({ ...state, x: floorEnd });
		expect(game.finished).toBe(false);
		// Thân còn chạm mép nền vẫn hợp lệ, kể cả khi tâm đã qua mép phải.
		game.physics.restore({ ...state, x: floorEnd - 0.25 });
		expect(game.finished).toBe(true);
	});

test.each([
	[6, 8489],
	[9, 14236],
	[10, 16957],
] as const)(
	"Room %i: another platform at tick %i does not satisfy Finish",
	(level, ticks) => {
		const game = new GameSimulation(roomMap(map, level)),
			point = checkpoints.find((checkpoint) => checkpoint.ticks === ticks)!;
		expect(point).toBeDefined();
		game.physics.restore({ ...point, codecVersion: 1 });
		expect(point.grounded).toBe(true);
		expect(point.x + PHYSICS.bodyWidth / 2).toBeGreaterThanOrEqual(
			game.map.finish.minCenterX,
		);
		expect(game.physics.progress).toBeGreaterThanOrEqual(
			game.map.finish.minProgress!,
		);
		expect(game.finished).toBe(false);
	},
);

test("the Room 10 floor does not change the native Solo and Community Finish", () => {
	expect(map.finish).toEqual({
		screen: 42,
		minCenterX: 336,
		requiresGrounded: true,
	});
	const original = new GameSimulation(map),
		room = new GameSimulation(roomMap(map, 10));
	for (const [ticks, originalFinished, roomFinished] of [
		[16861, false, true],
		[16957, true, false],
	] as const) {
		const point = checkpoints.find((checkpoint) => checkpoint.ticks === ticks)!;
		original.physics.restore({ ...point, codecVersion: 1 });
		room.physics.restore({ ...point, codecVersion: 1 });
		expect(original.finished).toBe(originalFinished);
		expect(room.finished).toBe(roomFinished);
	}
});

for (const goal of ROOM_MAPS)
	test(`Room ${goal.level}: reachable prefix and full-map reward boundary`, () => {
		const world = roomMap(map, goal.level),
			game = new GameSimulation(world);
		expect(world.screens).toBe(map.screens);
		expect(world.spawn).toBe(map.spawn);
		let stopAt = goal.level === 6 ? 8489 : goal.level === 9 ? 14391 : Infinity;
		const step = (input: PlayerInput = NEUTRAL_INPUT) => {
			if (!game.finished && game.physics.snapshot().ticks < stopAt)
				game.tick(input);
		};
		const repeat = (ticks: number, input: PlayerInput = NEUTRAL_INPUT) => {
			for (let i = 0; i < ticks; i++) step(input);
		};
		repeat(30);
		for (const [index, action] of route.entries()) {
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
			expect(game.physics.snapshot(), `route checkpoint ${index}`).toEqual(
				checkpoints[index],
			);
			if (game.physics.snapshot().ticks === stopAt) break;
		}
		if (goal.level === 6 || goal.level === 9) {
			expect(game.finished).toBe(false);
			expect(game.physics.snapshot().ticks).toBe(stopAt);
			stopAt = Infinity;
			// Hai nền được chọn nằm ngoài nhánh đi thẳng của canonical route.
			if (goal.level === 6) {
				repeat(48, { ...NEUTRAL_INPUT, right: true });
				step();
				repeat(18, { ...NEUTRAL_INPUT, jump: true });
				step({ ...NEUTRAL_INPUT, left: true });
				repeat(31);
			} else {
				repeat(2, { ...NEUTRAL_INPUT, left: true });
				step();
				repeat(4, { ...NEUTRAL_INPUT, jump: true });
				step({ ...NEUTRAL_INPUT, left: true });
				repeat(46);
			}
		}
		const state = game.physics.snapshot();
		expect(game.finished).toBe(true);
		expect(state.screen).toBe(goal.finish.screen);
		expect(state.falls).toBe(goal.level === 9 ? 1 : 0);
		expect(state.freeFlight).toBe(false);
		expect(game.adventure.completed).toBe(goal.level === 10);
		expect(game.physics.progress).toBe(goal.finish.minProgress);
		expect(state.x).toBeLessThan(floorEnds[goal.level - 1]!);
		expect(state.x + PHYSICS.bodyWidth).toBeGreaterThan(goal.finish.minCenterX);
		expect(state.ticks).toBe(
			[1021, 1815, 4078, 5524, 7367, 8588, 9253, 12780, 14445, 16861][
				goal.level - 1
			],
		);
		const airborne = { ...state, grounded: false };
		game.physics.restore(airborne);
		expect(game.finished).toBe(false);
	});

test("invalid Room lengths fail closed", () => {
	for (const level of [0, 11, 1.5, NaN])
		expect(() => roomMap(map, level)).toThrow();
});
