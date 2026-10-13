import { expect, test } from 'vitest';
import { SoloRuntime } from './SoloRuntime';
import { NEUTRAL_INPUT } from '../../game/simulation/types';
import map from '../../../../shared/maps/main-babe.json';

test('fixed ticks accumulate small frames and pause/resume discards elapsed wall time', () => {
  const session = new SoloRuntime(map, () => NEUTRAL_INPUT, () => {});
  session.advance(8); session.advance(8);
  expect(session.simulation.snapshot().ticks).toBe(0);
  session.advance(1);
  expect(session.simulation.snapshot().ticks).toBe(1);
  session.pause(); session.advance(5000);
  expect(session.simulation.snapshot().ticks).toBe(1);
  session.resume(); session.advance(1000 / 60);
  expect(session.simulation.snapshot().ticks).toBe(2);
});
