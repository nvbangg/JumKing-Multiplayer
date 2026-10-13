import raw from '../../../../shared/physics.json';

const gravity = Math.fround(raw.gravityNumerator / raw.gravityDenominator);
export const PHYSICS = Object.freeze({ ...raw, gravity, jumpVelocity: -Math.fround(raw.jumpGravityMultiplier * gravity) });
