import data from '../../../../shared/adventure.json';
import { PHYSICS as P } from './tuning';
import type { GameMap, PlayerInput, PlayerState } from './types';

export const ADVENTURE = data;
type Rect = { x: number; y: number; width: number; height: number };
export type MerchantStage = 'WAITING' | 'INTRO' | 'SALE' | 'SELL' | 'NO_GOLD' | 'SOLD' | 'BUSY';
export interface BirdState { index: number; done: boolean; dropped: boolean; flying: boolean; x: number; y: number; vx: number; vy: number; ticks: number }
export interface MerchantState { price: number; sold: boolean; introduced: boolean; stage: MerchantStage; line: number; ticks: number }
export interface DialogueState { introduced: boolean; best: number; pool: string[]; loop: number; flags: Record<string, number>; quote: string | null; line: number; ticks: number; wait: number; busy: number; talks: number }
export interface ReadableState { wait: number; line: number; ticks: number; done: boolean }
export interface AdventureState {
  codecVersion: 1; inventory: Record<string, number>; equipped: string[]; collected: string[]; flags: string[];
  birds: Record<string, BirdState>; merchants: Record<string, MerchantState>;
  dialogue: Record<string, DialogueState>; readables: Record<string, ReadableState>;
}
export interface AdventureEvent { type: 'PICKUP' | 'SALE' | 'FLEE' | 'SPEECH'; id: string; item?: string }
const T = data.tuning;
const itemIds = new Set([...data.skins.map(skin => skin.item), ...data.items.map(item => item.item), ...data.birds.map(bird => bird.item)]);
const overlap = (a: Rect, b: Rect) => a.x < b.x + b.width && a.x + a.width > b.x && a.y < b.y + b.height && a.y + a.height > b.y;
const box = (s: PlayerState): Rect => ({ x: Math.trunc(s.x), y: Math.trunc(s.y), width: P.bodyWidth, height: P.bodyHeight });
const freshReadable = (): ReadableState => ({ wait: 0, line: 0, ticks: 0, done: false });
export function availableItems(state: AdventureState) {
  const result = data.items.filter(item => !state.collected.includes(item.id) && !(item.displayOnly && (state.inventory[item.item] || 0) > 0)).map(item => ({ ...item }));
  for (const bird of data.birds) if (state.birds[bird.id]!.dropped && !state.collected.includes('bird:' + bird.id)) {
    const pos = bird.positions.at(-1)!;
    result.push({ id: 'bird:' + bird.id, item: bird.item, screen: pos.screen, x: pos.x + T.birdDropOffsetX, y: pos.y + T.birdDropOffsetY, width: T.birdDropWidth, height: T.birdDropHeight, displayOnly: false });
  }
  return result;
}

export class Adventure {
  private state: AdventureState;
  constructor(readonly map: GameMap, wardrobe: readonly string[] = []) {
    this.state = { codecVersion: 1, inventory: {}, equipped: [], collected: [], flags: [], birds: {}, merchants: {}, dialogue: {}, readables: {} };
    for (const item of wardrobe) if (data.skins.some(skin => skin.item === item)) this.add(item, true);
    if (this.has('Crown')) this.flag('CompletedNormalGame');
    for (const merchant of data.merchants) this.state.merchants[merchant.id] = { price: (this.state.inventory[merchant.currency] || 0) + merchant.basePrice + merchant.increase, sold: false, introduced: false, stage: 'WAITING', line: 0, ticks: 0 };
    for (const bird of data.birds) {
      const pos = bird.positions[0]!;
      this.state.birds[bird.id] = { index: 0, done: false, dropped: false, flying: false, x: pos.x, y: pos.y - pos.screen * map.screenHeight, vx: 0, vy: 0, ticks: 0 };
    }
    for (const npc of data.npcs) this.state.dialogue[npc.id] = { introduced: !npc.introLengths.length, best: 0, pool: [], loop: 0, flags: {}, quote: null, line: 0, ticks: 0, wait: 0, busy: 0, talks: 0 };
    for (const readable of data.readables) this.state.readables[readable.id] = freshReadable();
  }
  snapshot(): AdventureState { return structuredClone(this.state); }
  private worldRect(rect: Rect, screen: number): Rect { return { ...rect, y: rect.y - screen * this.map.screenHeight }; }
  has(item: string): boolean { return (this.state.inventory[item] || 0) > 0; }
  private add(item: string, once = false): void {
    if (!itemIds.has(item) || (once && this.has(item))) return;
    this.state.inventory[item] = Math.min(T.maxInventory, (this.state.inventory[item] || 0) + 1); this.equip(item, true);
  }
  equip(item: string, enabled: boolean): boolean {
    const skin = data.skins.find(value => value.item === item);
    if (!skin || !this.has(item)) return false;
    this.state.equipped = this.state.equipped.filter(value => value !== item && (!enabled || data.skins.find(entry => entry.item === value)?.layer !== skin.layer));
    if (enabled) this.state.equipped.push(item); return true;
  }
  private flag(value: string): void { if (!this.state.flags.includes(value)) this.state.flags.push(value); }
  complete(): void { this.add('Crown', true); this.flag('CompletedNormalGame'); }
  items() { return availableItems(this.state); }
  quoteLengths(id: string, quote: string): number[] {
    const npc = data.npcs.find(value => value.id === id);
    if (!npc) return [];
    const parts = quote.split(':');
    if (quote === 'intro') return npc.introLengths;
    if (parts[0] === 'progress') return npc.progressQuotes[Number(parts[1])]?.lineLengths || [];
    if (parts[0] === 'loop') return npc.loopLengths[Number(parts[1])] || [];
    if (parts[0] === 'flag') return npc.flagQuotes[Number(parts[1])]?.sequences[Number(parts[2])] || [];
    return [];
  }
  tick(player: PlayerState, input: PlayerInput): AdventureEvent[] {
    const events: AdventureEvent[] = [], hitbox = box(player);
    for (const item of this.items()) if (!item.displayOnly && overlap(hitbox, this.worldRect({ x: item.x - item.width / 2, y: item.y - item.height, width: item.width, height: item.height }, item.screen))) {
      this.state.collected.push(item.id); this.add(item.item); events.push({ type: 'PICKUP', id: item.id, item: item.item });
    }
    for (const definition of data.merchants) {
      const s = this.state.merchants[definition.id]!, touching = overlap(hitbox, this.worldRect(definition.trigger, definition.screen));
      const stage = (value: MerchantStage) => { s.stage = value; s.line = 0; s.ticks = 0; };
      const canBuy = () => !s.sold && (this.state.inventory[definition.currency] || 0) >= s.price;
      if (s.stage === 'BUSY') { if (++s.ticks >= T.npcBusyTicks) stage('WAITING'); continue; }
      if (s.stage === 'WAITING') { s.ticks = touching ? s.ticks + 1 : 0; if (s.ticks >= definition.delayTicks) stage(s.sold ? 'SOLD' : !s.introduced ? 'INTRO' : 'SALE'); continue; }
      const lines = definition.stageLengths[s.stage], line = lines[s.line];
      // Giữ thứ tự phép toán native để ceil không lệch một tick.
      if (line && ++s.ticks < Math.ceil(((line.letters + line.prices * String(s.price).length) * definition.characterSeconds + T.merchantHoldSeconds) * P.tickRate)) continue;
      s.ticks = 0; if (++s.line < lines.length) continue;
      if (s.stage === 'INTRO' || s.stage === 'SALE') { s.introduced = true; stage(touching && canBuy() ? 'SELL' : 'NO_GOLD'); }
      else if (s.stage === 'SELL' && canBuy()) { this.state.inventory[definition.currency] = (this.state.inventory[definition.currency] || 0) - s.price; this.add(definition.item, true); s.sold = true; events.push({ type: 'SALE', id: definition.id, item: definition.item }); stage('SOLD'); }
      else stage('BUSY');
    }
    for (const bird of data.birds) {
      const s = this.state.birds[bird.id]!, pos = bird.positions[s.index]!;
      if (s.done) continue;
      if (!s.flying && player.screen === pos.screen && (hitbox.x + P.bodyWidth / 2 - s.x) ** 2 + (hitbox.y + P.bodyHeight / 2 - s.y) ** 2 < T.birdFleeRadius ** 2) {
        s.flying = true; s.ticks = 0; const next = bird.positions[s.index + 1];
        s.vy = next && next.screen < pos.screen ? T.birdInitialFall : T.birdInitialRise;
        s.vx = s.vy > 0 ? pos.flyDirection * T.birdFallSpeedX : 0; events.push({ type: 'FLEE', id: bird.id });
      }
      if (!s.flying) continue;
      s.ticks++;
      if (s.ticks % T.birdStepDivisor === 0) { s.vx += pos.flyDirection * (s.vy < 0 ? T.birdAccelerationRise : T.birdAccelerationFall); s.vy *= s.vy < 0 ? T.birdScaleRise : T.birdScaleFall; }
      s.x += s.vx / T.birdStepDivisor; s.y += s.vy / T.birdStepDivisor;
      const localY = s.y + pos.screen * this.map.screenHeight;
      if (s.x < 0 || s.x >= this.map.width || localY < 0 || localY >= this.map.screenHeight) {
        if (++s.index >= bird.positions.length) { s.done = true; s.index = bird.positions.length - 1; }
        s.flying = false; const next = bird.positions[s.index]!; s.x = next.x; s.y = next.y - next.screen * this.map.screenHeight;
        if (s.index === bird.positions.length - 1 && !s.dropped) { s.dropped = true; this.flag('CHASE_BIRD_PAST_SNOWSTORM'); }
      }
    }
    for (const npc of data.npcs) {
      const s = this.state.dialogue[npc.id]!;
      const quotes = npc.progressQuotes.map((quote, i) => ({ ...quote, i })).filter(quote => quote.screen === player.screen + 1);
      if (quotes.length && player.screen + 1 > s.best) { s.best = player.screen + 1; s.pool.push(...quotes.map(quote => 'progress:' + quote.i).reverse()); }
      if (s.busy > 0) { s.busy--; continue; }
      if (s.quote !== null) {
        const lengths = this.quoteLengths(npc.id, s.quote);
        if (++s.ticks >= lengths[s.line]! * npc.characterSeconds * P.tickRate + T.speechHoldTicks) { s.ticks = 0; if (++s.line >= lengths.length) { s.quote = null; s.line = 0; s.busy = T.npcBusyTicks; } }
        continue;
      }
      s.wait = overlap(hitbox, this.worldRect(npc.trigger, npc.screen)) ? s.wait + 1 : 0;
      if (s.wait < npc.delayTicks) continue;
      s.wait = 0;
      const flagged = npc.flagQuotes.findIndex(quote => this.state.flags.includes(quote.flag) && (s.flags[quote.flag] || 0) < quote.sequences.length);
      let consumed = true;
      if (flagged >= 0) { const flag = npc.flagQuotes[flagged]!.flag, index = s.flags[flag] || 0; s.quote = 'flag:' + flagged + ':' + index; s.flags[flag] = index + 1; s.introduced = true; }
      else if (!s.introduced) { s.quote = 'intro'; s.introduced = true; }
      else if (s.pool.length) s.quote = s.pool.pop()!;
      else { s.quote = npc.loopLengths.length ? 'loop:' + s.loop % npc.loopLengths.length : null; s.loop++; consumed = false; }
      if (consumed && s.quote !== null) s.talks++;
    }
    for (const readable of data.readables) {
      const s = this.state.readables[readable.id]!;
      if (!overlap(hitbox, this.worldRect(readable.trigger, readable.screen))) { this.state.readables[readable.id] = freshReadable(); continue; }
      if (s.done) continue;
      if (s.wait < T.readableWaitTicks) { s.wait = input.left !== input.right || input.jump ? 0 : s.wait + 1; continue; }
      s.ticks++; const length = readable.lineLengths[s.line]!;
      if (s.ticks % T.readableCharacterTicks === 0 && s.ticks <= length * T.readableCharacterTicks) events.push({ type: 'SPEECH', id: readable.id });
      if (s.ticks >= length * T.readableCharacterTicks + T.speechHoldTicks) { s.ticks = 0; if (s.line + 1 < readable.lineLengths.length) s.line++; else s.done = true; }
    }
    return events;
  }
  restore(saved: AdventureState): void {
    const count = (value: unknown) => Number.isSafeInteger(value) && (value as number) >= 0 && (value as number) <= T.maxInventory;
    const knownCollected = new Set([...data.items.map(item => item.id), ...data.birds.map(bird => 'bird:' + bird.id)]);
    if (saved.codecVersion !== 1 || !saved.inventory || Object.entries(saved.inventory).some(([item, amount]) => !itemIds.has(item) || !count(amount))
      || !Array.isArray(saved.equipped) || new Set(saved.equipped).size !== saved.equipped.length || saved.equipped.some(item => !data.skins.some(skin => skin.item === item) || !(saved.inventory[item]! > 0))
      || !Array.isArray(saved.collected) || new Set(saved.collected).size !== saved.collected.length || saved.collected.some(id => !knownCollected.has(id))
      || !Array.isArray(saved.flags) || saved.flags.some(flag => !['CompletedNormalGame', 'CHASE_BIRD_PAST_SNOWSTORM'].includes(flag))) throw new Error('Invalid adventure snapshot');
    if (new Set(saved.equipped.map(item => data.skins.find(skin => skin.item === item)!.layer)).size !== saved.equipped.length) throw new Error('Invalid equipment layers');
    for (const definition of data.birds) {
      const s = saved.birds?.[definition.id];
      if (!s || !count(s.index) || s.index >= definition.positions.length || !count(s.ticks) || [s.x, s.y, s.vx, s.vy].some(value => !Number.isFinite(value)) || [s.done, s.dropped, s.flying].some(value => typeof value !== 'boolean')) throw new Error('Invalid bird snapshot');
    }
    for (const definition of data.merchants) {
      const s = saved.merchants?.[definition.id];
      if (!s || !['WAITING', 'INTRO', 'SALE', 'SELL', 'NO_GOLD', 'SOLD', 'BUSY'].includes(s.stage) || !count(s.price) || !count(s.line) || !count(s.ticks) || typeof s.sold !== 'boolean' || typeof s.introduced !== 'boolean' || (!['WAITING', 'BUSY'].includes(s.stage) && s.line >= definition.stageLengths[s.stage as keyof typeof definition.stageLengths].length)) throw new Error('Invalid merchant snapshot');
    }
    for (const definition of data.npcs) {
      const s = saved.dialogue?.[definition.id];
      if (!s || typeof s.introduced !== 'boolean' || [s.best, s.loop, s.line, s.ticks, s.wait, s.busy, s.talks].some(value => !count(value)) || !Array.isArray(s.pool) || s.pool.some(quote => !this.quoteLengths(definition.id, quote).length) || (s.quote !== null && (!this.quoteLengths(definition.id, s.quote).length || s.line >= this.quoteLengths(definition.id, s.quote).length)) || !s.flags || Object.entries(s.flags).some(([flag, value]) => !definition.flagQuotes.some(quote => quote.flag === flag) || !count(value))) throw new Error('Invalid dialogue snapshot');
    }
    for (const definition of data.readables) {
      const s = saved.readables?.[definition.id];
      if (!s || [s.wait, s.line, s.ticks].some(value => !count(value)) || s.wait > T.readableWaitTicks || s.line >= definition.lineLengths.length || typeof s.done !== 'boolean') throw new Error('Invalid readable snapshot');
    }
    this.state = structuredClone(saved);
  }
}
