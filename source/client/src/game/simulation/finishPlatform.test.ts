import { expect, test } from "vitest";
import map from "../../../../common/maps/main-babe.json";
import { findFinishPlatform } from "./finishPlatform";
import { GameSimulation } from "./GameSimulation";
import { ROOM_MAPS, roomMap } from "./roomMaps";
import { PHYSICS } from "./tuning";
import { NEUTRAL_INPUT } from "./types";

test("Room markers use the approved landing platforms", () => {
	expect(
		ROOM_MAPS.map(({ level }) => {
			const marker = findFinishPlatform(roomMap(map, level))!;
			return [level, marker.x, marker.y, marker.width];
		}),
	).toEqual([
		[1, 136, -760, 72],
		[2, 8, -3056, 48],
		[3, 216, -4584, 64],
		[4, 240, -6256, 72],
		[5, 208, -8416, 72],
		[6, 192, -8904, 56],
		[7, 264, -10976, 40],
		[8, 256, -12488, 32],
		[9, 248, -13584, 40],
		[10, 336, -14976, 88],
	]);
});

test("Room 6 does not Finish on the separate right platform at the same height", () => {
	const game = new GameSimulation(roomMap(map, 6));
	game.physics.restore({
		...game.physics.snapshot(),
		x: 456 - PHYSICS.bodyWidth / 2,
		y: -8904 - PHYSICS.bodyHeight - 2,
		screen: 25,
		grounded: false,
	});
	for (let tick = 0; tick < PHYSICS.tickRate; tick++) game.tick(NEUTRAL_INPUT);
	expect(game.physics.snapshot().grounded).toBe(true);
	expect(game.physics.progress).toBe(9232);
	expect(game.finished).toBe(false);
});

for (const goal of ROOM_MAPS)
	test(`Room ${goal.level}: landing at the marker satisfies the real Finish rule`, () => {
		const world = roomMap(map, goal.level),
			marker = findFinishPlatform(world)!;
		expect(marker).toBeDefined();
		expect(marker.screen).toBe(goal.finish.screen);
		const game = new GameSimulation(world);
		game.physics.restore({
			...game.physics.snapshot(),
			x: marker.x + (marker.width - PHYSICS.bodyWidth) / 2,
			y: marker.y - PHYSICS.bodyHeight - 2,
			screen: marker.screen,
			grounded: false,
		});
		expect(game.finished).toBe(false);
		for (let tick = 0; tick < PHYSICS.tickRate && !game.finished; tick++)
			game.tick(NEUTRAL_INPUT);
		expect(game.finished).toBe(true);
		expect(game.physics.snapshot().grounded).toBe(true);
		expect(game.physics.snapshot().y + PHYSICS.bodyHeight).toBe(marker.y);
	});
