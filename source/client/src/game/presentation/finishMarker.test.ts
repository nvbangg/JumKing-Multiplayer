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
		[1, 136, -760, 72],
		[2, 152, -1472, 176],
		[3, 8, -3056, 48],
		[4, 216, -4584, 64],
		[5, 240, -6256, 72],
		[6, 272, -7320, 72],
		[7, 208, -8416, 72],
		[8, 264, -10976, 40],
		[9, 192, -12488, 32],
		[10, 112, -14976, 312],
	]);
});

test("Room 9 does not Finish on the right platform at the same height", () => {
	const game = new GameSimulation(roomMap(map, 9));
	game.physics.restore({
		...game.physics.snapshot(),
		x: 272 - PHYSICS.bodyWidth / 2,
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
