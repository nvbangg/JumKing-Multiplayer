export const PRESENTATION = Object.freeze({
  width: 480, height: 360, spriteSize: 48, spriteStartX: 224, spriteColumns: 4,
  peerOpacity: 0.7, maxFrameMs: 250, hudIntervalMs: 100, afkPollMs: 1000, nameFontSize: 9, nameGap: 3,
  gamepadDeadZone: 0.35,
  effectVolume: 0.65, ambienceVolume: 0.6,
  foregroundDepth: 20, selfDepth: 10, peerDepth: 8, nameDepth: 30,
});
export const PLAYER_FRAMES = ['idle', 'walk_one', 'walk_smear', 'walk_two', 'charge', 'up', 'fall', 'splat', 'bounce', 'stretch_one', 'stretch_smear', 'stretch_two', 'look_up'] as const;
