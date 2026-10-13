import { readFile, writeFile, mkdir, copyFile, realpath, unlink } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const source = process.argv[2];
if (!source) throw new Error('Usage: node scripts/import-content.mjs <manifest.json> <details.json> [route.json]');
const manifestPath = await realpath(source);
const sourceRoot = path.dirname(manifestPath);
const manifest = JSON.parse(await readFile(manifestPath, 'utf8'));
const details = JSON.parse(await readFile(process.argv[3], 'utf8'));
const screens = manifest.screens.slice(0, 43);
if (screens.length !== 43) throw new Error('Main Babe requires 43 screens');
for (const screen of screens) {
  if (screen.tiles.length !== 45 || screen.tiles.some(row => row.length !== 60 || /[^ #INabcd]/.test(row))) throw new Error('Invalid Main Babe geometry');
}
const world = {
  width: 480, screenHeight: 360, tileSize: 8, progressOriginY: 328,
  spawn: { x: 231, y: 302 }, finish: { screen: 42, minCenterX: 336, requiresGrounded: true },
  screens: screens.map(({ tiles, wind }) => ({ tiles, wind })),
};
const inCampaign = entry => Number.isInteger(entry.screen) && entry.screen >= 0 && entry.screen < 43;
const main = details.adventure;
const npcs = details.npcs.filter(inCampaign).map(({ achievements, quotes, ...npc }) => ({
  ...npc,
  screenQuotes: (npc.screenQuotes || []).filter(inCampaign),
  flagQuotes: (npc.flagQuotes || []).filter(quote => quote.flag === 'CompletedOriginal'),
}));
const items = main.items.filter(inCampaign);
const merchants = main.merchants.filter(inCampaign).map(({ achievements, ...merchant }) => merchant);
const birds = main.birds.map(bird => ({ ...bird, positions: bird.positions.filter(inCampaign) })).filter(bird => bird.positions.length);
const messages = main.messages.filter(inCampaign);
const validItems = new Set(['Crown', 'Cap', 'Shoes', ...items.map(item => item.item), ...birds.map(bird => bird.item)]);
const skins = main.skins.filter(skin => validItems.has(skin.item));
const adventure = {
  items: items.map(({ id, item, screen, x, y, width, height, displayOnly }) => ({ id, item, screen, x, y, width, height, displayOnly })),
  skins: skins.map(({ item, layer }) => ({ item, layer })),
  merchants: merchants.map(({ name, screen, item, currency, basePrice, increase, trigger, delay }) => ({ id: name, screen, item, currency, basePrice, increase, trigger, delayTicks: Math.round(delay * 60) })),
  birds: birds.map(({ name, item, positions }) => ({ id: name, item, positions })),
  triggers: [
    ...npcs.filter(npc => npc.trigger?.screen_rect).map(npc => {
      const r = npc.trigger.screen_rect;
      return { id: npc.name, kind: 'NPC', screen: npc.screen, rect: { x: r.X, y: r.Y, width: r.Width, height: r.Height } };
    }),
    ...messages.map(({ id, screen, trigger }) => ({ id, kind: 'READABLE', screen, rect: trigger })),
  ],
};
const textureKeys = new Set(['title_logo', 'king/base', 'gui/frame', 'gui/cursor', 'gui/back', 'gui/checkbox', 'ending/ending_animations', 'ending/imagecrown', 'ending/imageshoes']);
const soundKeys = new Set(['audio/music/menu loop/menu_intro', 'audio/music/menu loop/menu_loop', 'audio/music/ending', 'audio/music/event_music/ending_jingle', 'audio/music/event_music/Elude1', 'audio/music/event_music/Elude2', 'audio/music/event_music/Moon', 'audio/talking', 'audio/plink', 'audio/new_location', 'audio/event_sfx/bird_fly']);
for (const key of Object.keys(manifest.sounds)) if (/^audio\/(jump_king\/(ice\/|snow\/)?king_|gui_sfx\/|babe\/)/.test(key)) soundKeys.add(key);
for (const screen of screens) {
  for (const layer of screen.layers || []) textureKeys.add(layer);
  for (const prop of screen.props || []) textureKeys.add(prop.texture);
  for (const ambient of screen.ambience || []) soundKeys.add('audio/background/' + ambient.name);
  for (const weather of screen.weather || []) {
    if (weather.mask) textureKeys.add(weather.mask);
    for (const key of Object.keys(manifest.textures)) if (key.startsWith('particles/' + weather.name + '/')) textureKeys.add(key);
  }
  const scrolling = screen.scrolling?.ScrollingBGdata?.layers?.Layer;
  for (const layer of scrolling ? (Array.isArray(scrolling) ? scrolling : [scrolling]) : []) textureKeys.add('screens/scrolling/textures/' + layer.texture);
}
for (const entry of [...npcs, ...items, ...skins, ...birds]) if (entry.texture) textureKeys.add(entry.texture);
textureKeys.add('particles/jump_particle');
textureKeys.add('particles/snow_jump_particle');
const fonts = Object.fromEntries(Object.entries(manifest.fonts).filter(([key]) => ['font/sf_pixolde', 'font/sf_litter_lover2_bold'].includes(key)));
const textures = Object.fromEntries([...textureKeys].filter(key => manifest.textures[key]).map(key => [key, manifest.textures[key]]));
const sounds = Object.fromEntries([...soundKeys].filter(key => manifest.sounds[key]).map(key => [key, manifest.sounds[key]]));
const output = path.join(root, 'frontend/public/assets');
await mkdir(output, { recursive: true });
let previousHashes = {};
try { previousHashes = JSON.parse(await readFile(path.join(output, 'manifest.json'), 'utf8')).hashes || {}; }
catch (error) { if (error.code !== 'ENOENT') throw error; }
const files = new Set([...Object.values(textures).map(t => t.file), ...Object.values(sounds).map(s => s.file), ...Object.values(fonts).flatMap(f => [f.texture, f.data]), 'font/ttf_litter_lover2_bold.ttf']);
const hashes = {};
for (const [file, hash] of Object.entries(previousHashes)) {
  if (files.has(file)) continue;
  const owned = path.resolve(output, file);
  if (!owned.startsWith(output + path.sep)) throw new Error('Previous manifest escapes asset directory');
  try {
    if (createHash('sha256').update(await readFile(owned)).digest('hex') !== hash) throw new Error('Refusing to remove modified asset: ' + file);
    await unlink(owned);
  } catch (error) { if (error.code !== 'ENOENT') throw error; }
}
for (const file of files) {
  const resolved = await realpath(path.join(sourceRoot, file));
  if (!resolved.startsWith(sourceRoot + path.sep)) throw new Error('Asset path escapes source directory');
  const destination = path.resolve(output, file);
  if (!destination.startsWith(output + path.sep)) throw new Error('Asset path escapes output directory');
  await mkdir(path.dirname(destination), { recursive: true });
  await copyFile(resolved, destination);
  hashes[file] = createHash('sha256').update(await readFile(resolved)).digest('hex');
}
const presentation = { screens: screens.map(({ tiles, wind, teleport, ...display }) => display), npcs, items, skins, birds, messages, merchants, fonts, textures, sounds, hashes };
for (const [file, data] of [
  ['shared/maps/main-babe.json', world],
  ['shared/adventure.json', adventure],
  ['frontend/public/assets/manifest.json', presentation],
]) {
  const destination = path.join(root, file);
  await mkdir(path.dirname(destination), { recursive: true });
  await writeFile(destination, JSON.stringify(data, null, 2) + '\n');
}
if (process.argv[4]) {
  await mkdir(path.join(root, 'shared/fixtures'), { recursive: true });
  await writeFile(path.join(root, 'shared/fixtures/main-babe-route.json'), JSON.stringify(JSON.parse(await readFile(process.argv[4], 'utf8')), null, 2) + '\n');
}
console.log(JSON.stringify({ screens: world.screens.length, textures: Object.keys(textures).length, sounds: Object.keys(sounds).length, npcs: npcs.length, files: files.size }));
