import { expect, test } from "vitest";
import map from "../../../../common/maps/main-babe.json";
import { GameSimulation } from "../simulation/GameSimulation";
import { ROOM_MAPS, roomMap } from "../simulation/roomMaps";
import { PHYSICS } from "../simulation/tuning";
import { NEUTRAL_INPUT } from "../simulation/types";
import { finishMarker } from "./finishMarker";

test("Room markers use the approved landing platforms", () => {
	expect(
		ROOM_MAPS.map(({ level }) => {
			const marker = finishMarker(roomMap(map, level))!;
			return [level, marker.x, marker.y, marker.width];
		}),
	).toEqual([
		[1, 288, -1720, 184],
		[2, 248, -3152, 48],
		[3, 216, -4584, 64],
		[4, 128, -6424, 48],
		[5, 96, -8584, 8],
		[6, 192, -8904, 56],
		[7, 16, -11064, 184],
		[8, 256, -12488, 32],
		[9, 248, -13584, 40],
		[10, 336, -14976, 88],
	]);
});

test("Room 8 does not Finish on the left platform at the same height", () => {
	const game = new GameSimulation(roomMap(map, 8));
	game.physics.restore({
		...game.physics.snapshot(),
		x: 208 - PHYSICS.bodyWidth / 2,
		y: -12488 - PHYSICS.bodyHeight - 2,
		screen: 35,
		grounded: false,
	});
	for (let tick = 0; tick < PHYSICS.tickRate; tick++) game.tick(NEUTRAL_INPUT);
	expect(game.physics.snapshot().grounded).toBe(true);
	expect(game.physics.progress).toBe(12816);
	expect(game.finished).toBe(false);
});

for (const goal of ROOM_MAPS)
	test(`Room ${goal.level}: landing at the marker satisfies the real Finish rule`, () => {
		const world = roomMap(map, goal.level),
			marker = finishMarker(world)!;
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
