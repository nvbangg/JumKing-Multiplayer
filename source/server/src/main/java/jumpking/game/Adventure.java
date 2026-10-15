package jumpking.game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class Adventure {
    public record Event(String type, String id, String item) {}
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private final JsonNode data, tuning;
    private final GameMap map;
    private final Physics physics;
    private final Set<String> itemIds = new HashSet<>();
    private ObjectNode state;

    public Adventure(JsonNode definitions, GameMap map, Physics physics, Collection<String> wardrobe) {
        data = definitions.deepCopy(); tuning = data.path("tuning"); this.map = map; this.physics = physics;
        state = JSON.objectNode().put("codecVersion", 1);
        for (String key : List.of("inventory", "birds", "merchants", "dialogue", "readables")) state.putObject(key);
        for (String key : List.of("equipped", "collected", "flags")) state.putArray(key);
        for (String key : List.of("skins", "items", "birds")) for (JsonNode definition : data.path(key)) itemIds.add(definition.path("item").asText());
        for (String item : wardrobe) if (find(data.path("skins"), "item", item) != null) add(item, true);
        if (has("Crown")) flag("CompletedNormalGame");
        for (JsonNode m : data.path("merchants")) object(state, "merchants").set(m.path("id").asText(), JSON.objectNode()
            .put("price", amount(m.path("currency").asText()) + m.path("basePrice").asInt() + m.path("increase").asInt())
            .put("sold", false).put("introduced", false).put("stage", "WAITING").put("line", 0).put("ticks", 0));
        for (JsonNode bird : data.path("birds")) {
            var pos = bird.path("positions").get(0);
            object(state, "birds").set(bird.path("id").asText(), JSON.objectNode().put("index", 0).put("done", false).put("dropped", false).put("flying", false)
                .put("x", number(pos, "x")).put("y", number(pos, "y") - number(pos, "screen") * map.screenHeight()).put("vx", 0).put("vy", 0).put("ticks", 0));
        }
        for (JsonNode npc : data.path("npcs")) {
            var value = JSON.objectNode().put("introduced", npc.path("introLengths").isEmpty()).put("best", 0).put("loop", 0).putNull("quote")
                .put("line", 0).put("ticks", 0).put("wait", 0).put("busy", 0).put("talks", 0);
            value.putArray("pool"); value.putObject("flags"); object(state, "dialogue").set(npc.path("id").asText(), value);
        }
        for (JsonNode readable : data.path("readables")) object(state, "readables").set(readable.path("id").asText(), freshReadable());
    }
    private static ObjectNode object(JsonNode node, String key) { return (ObjectNode) node.get(key); }
    private static ArrayNode array(JsonNode node, String key) { return (ArrayNode) node.get(key); }
    private static double number(JsonNode node, String key) { return node.path(key).asDouble(); }
    private double tuning(String key) { return number(tuning, key); }
    private static JsonNode find(JsonNode entries, String key, String value) {
        for (JsonNode entry : entries) if (entry.path(key).asText().equals(value)) return entry;
        return null;
    }
    private static boolean contains(JsonNode values, String value) { for (JsonNode entry : values) if (entry.asText().equals(value)) return true; return false; }
    private static ObjectNode freshReadable() { return JSON.objectNode().put("wait", 0).put("line", 0).put("ticks", 0).put("done", false); }
    private static int increment(ObjectNode node, String key) { int value = node.path(key).asInt() + 1; node.put(key, value); return value; }
    public ObjectNode snapshot() { return state.deepCopy(); }
    public boolean has(String item) { return amount(item) > 0; }
    private int amount(String item) { return state.path("inventory").path(item).asInt(); }
    private void add(String item, boolean once) {
        if (!itemIds.contains(item) || (once && has(item))) return;
        object(state, "inventory").put(item, Math.min((int) tuning("maxInventory"), amount(item) + 1)); equip(item, true);
    }
    public boolean equip(String item, boolean enabled) {
        var skin = find(data.path("skins"), "item", item);
        if (skin == null || !has(item)) return false;
        var next = JSON.arrayNode();
        for (JsonNode value : state.path("equipped")) {
            var existing = find(data.path("skins"), "item", value.asText());
            if (!value.asText().equals(item) && (!enabled || !existing.path("layer").equals(skin.path("layer")))) next.add(value);
        }
        if (enabled) next.add(item); state.set("equipped", next); return true;
    }
    private void flag(String value) { if (!contains(state.path("flags"), value)) array(state, "flags").add(value); }
    public void complete() { add("Crown", true); flag("CompletedNormalGame"); }
    public List<ObjectNode> items() {
        var result = new ArrayList<ObjectNode>();
        for (JsonNode item : data.path("items")) if (!contains(state.path("collected"), item.path("id").asText()) && !(item.path("displayOnly").asBoolean() && has(item.path("item").asText()))) result.add(item.deepCopy());
        for (JsonNode bird : data.path("birds")) {
            String id = "bird:" + bird.path("id").asText();
            if (!state.path("birds").path(bird.path("id").asText()).path("dropped").asBoolean() || contains(state.path("collected"), id)) continue;
            var pos = bird.path("positions").get(bird.path("positions").size() - 1);
            result.add(JSON.objectNode().put("id", id).put("item", bird.path("item").asText()).put("screen", pos.path("screen").asInt())
                .put("x", number(pos, "x") + tuning("birdDropOffsetX")).put("y", number(pos, "y") + tuning("birdDropOffsetY"))
                .put("width", tuning("birdDropWidth")).put("height", tuning("birdDropHeight")).put("displayOnly", false));
        }
        return result;
    }
    public JsonNode quoteLengths(String id, String quote) {
        var npc = find(data.path("npcs"), "id", id);
        if (npc == null || quote == null) return JSON.arrayNode();
        String[] parts = quote.split(":"); JsonNode lengths = null;
        try {
            if (quote.equals("intro")) lengths = npc.path("introLengths");
            else if (parts[0].equals("progress")) lengths = npc.path("progressQuotes").path(Integer.parseInt(parts[1])).path("lineLengths");
            else if (parts[0].equals("loop")) lengths = npc.path("loopLengths").path(Integer.parseInt(parts[1]));
            else if (parts[0].equals("flag")) lengths = npc.path("flagQuotes").path(Integer.parseInt(parts[1])).path("sequences").path(Integer.parseInt(parts[2]));
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) { }
        return lengths != null && lengths.isArray() ? lengths : JSON.arrayNode();
    }
    private boolean overlap(PlayerState p, double x, double y, double width, double height) {
        return (int) p.x < x + width && (int) p.x + physics.bodyWidth > x && (int) p.y < y + height && (int) p.y + physics.bodyHeight > y;
    }
    private boolean touching(PlayerState p, JsonNode rect, int screen) { return overlap(p, number(rect, "x"), number(rect, "y") - screen * map.screenHeight(), number(rect, "width"), number(rect, "height")); }
    private static void stage(ObjectNode s, String value) { s.put("stage", value).put("line", 0).put("ticks", 0); }
    private boolean canBuy(JsonNode definition, JsonNode s) { return !s.path("sold").asBoolean() && amount(definition.path("currency").asText()) >= s.path("price").asInt(); }

    public List<Event> tick(PlayerState player, PlayerInput input) {
        var events = new ArrayList<Event>();
        for (ObjectNode item : items()) if (!item.path("displayOnly").asBoolean() && overlap(player, number(item, "x") - number(item, "width") / 2, number(item, "y") - number(item, "height") - number(item, "screen") * map.screenHeight(), number(item, "width"), number(item, "height"))) {
            String id = item.path("id").asText(), value = item.path("item").asText(); array(state, "collected").add(id); add(value, false); events.add(new Event("PICKUP", id, value));
        }
        for (JsonNode definition : data.path("merchants")) {
            String id = definition.path("id").asText(); var s = object(state.path("merchants"), id);
            boolean touching = touching(player, definition.path("trigger"), definition.path("screen").asInt());
            String current = s.path("stage").asText();
            if (current.equals("BUSY")) { if (increment(s, "ticks") >= tuning("npcBusyTicks")) stage(s, "WAITING"); continue; }
            if (current.equals("WAITING")) { s.put("ticks", touching ? s.path("ticks").asInt() + 1 : 0); if (s.path("ticks").asInt() >= definition.path("delayTicks").asInt()) stage(s, s.path("sold").asBoolean() ? "SOLD" : !s.path("introduced").asBoolean() ? "INTRO" : "SALE"); continue; }
            var lines = definition.path("stageLengths").path(current); var line = lines.get(s.path("line").asInt());
            // Giữ thứ tự phép toán native để ceil không lệch một tick.
            if (line != null && increment(s, "ticks") < Math.ceil(((number(line, "letters") + number(line, "prices") * String.valueOf(s.path("price").asInt()).length()) * number(definition, "characterSeconds") + tuning("merchantHoldSeconds")) * physics.tickRate)) continue;
            s.put("ticks", 0); if (increment(s, "line") < lines.size()) continue;
            if (current.equals("INTRO") || current.equals("SALE")) { s.put("introduced", true); stage(s, touching && canBuy(definition, s) ? "SELL" : "NO_GOLD"); }
            else if (current.equals("SELL") && canBuy(definition, s)) {
                String currency = definition.path("currency").asText(), item = definition.path("item").asText();
                object(state, "inventory").put(currency, amount(currency) - s.path("price").asInt()); add(item, true); s.put("sold", true); events.add(new Event("SALE", id, item)); stage(s, "SOLD");
            } else stage(s, "BUSY");
        }
        for (JsonNode bird : data.path("birds")) {
            String id = bird.path("id").asText(); var s = object(state.path("birds"), id); var positions = bird.path("positions"); var pos = positions.get(s.path("index").asInt());
            if (s.path("done").asBoolean()) continue;
            double dx = (int) player.x + physics.bodyWidth / 2.0 - number(s, "x"), dy = (int) player.y + physics.bodyHeight / 2.0 - number(s, "y");
            if (!s.path("flying").asBoolean() && player.screen == pos.path("screen").asInt() && dx * dx + dy * dy < tuning("birdFleeRadius") * tuning("birdFleeRadius")) {
                var next = positions.get(s.path("index").asInt() + 1);
                double vy = next != null && next.path("screen").asInt() < pos.path("screen").asInt() ? tuning("birdInitialFall") : tuning("birdInitialRise");
                s.put("flying", true).put("ticks", 0).put("vy", vy).put("vx", vy > 0 ? number(pos, "flyDirection") * tuning("birdFallSpeedX") : 0); events.add(new Event("FLEE", id, null));
            }
            if (!s.path("flying").asBoolean()) continue;
            int ticks = increment(s, "ticks");
            if (ticks % (int) tuning("birdStepDivisor") == 0) { s.put("vx", number(s, "vx") + number(pos, "flyDirection") * tuning(number(s, "vy") < 0 ? "birdAccelerationRise" : "birdAccelerationFall")); s.put("vy", number(s, "vy") * tuning(number(s, "vy") < 0 ? "birdScaleRise" : "birdScaleFall")); }
            s.put("x", number(s, "x") + number(s, "vx") / tuning("birdStepDivisor")).put("y", number(s, "y") + number(s, "vy") / tuning("birdStepDivisor"));
            double localY = number(s, "y") + number(pos, "screen") * map.screenHeight();
            if (number(s, "x") < 0 || number(s, "x") >= map.width() || localY < 0 || localY >= map.screenHeight()) {
                int index = increment(s, "index"); if (index >= positions.size()) { s.put("done", true).put("index", positions.size() - 1); index = positions.size() - 1; }
                var next = positions.get(index); s.put("flying", false).put("x", number(next, "x")).put("y", number(next, "y") - number(next, "screen") * map.screenHeight());
                if (index == positions.size() - 1 && !s.path("dropped").asBoolean()) { s.put("dropped", true); flag("CHASE_BIRD_PAST_SNOWSTORM"); }
            }
        }
        for (JsonNode npc : data.path("npcs")) {
            String id = npc.path("id").asText(); var s = object(state.path("dialogue"), id); var quotes = npc.path("progressQuotes"); var matched = new ArrayList<Integer>();
            for (int i = 0; i < quotes.size(); i++) if (quotes.get(i).path("screen").asInt() == player.screen + 1) matched.add(i);
            if (!matched.isEmpty() && player.screen + 1 > s.path("best").asInt()) { s.put("best", player.screen + 1); for (int i = matched.size() - 1; i >= 0; i--) array(s, "pool").add("progress:" + matched.get(i)); }
            if (s.path("busy").asInt() > 0) { s.put("busy", s.path("busy").asInt() - 1); continue; }
            if (!s.path("quote").isNull()) {
                var lengths = quoteLengths(id, s.path("quote").asText());
                if (increment(s, "ticks") >= lengths.get(s.path("line").asInt()).asInt() * number(npc, "characterSeconds") * physics.tickRate + tuning("speechHoldTicks")) { s.put("ticks", 0); if (increment(s, "line") >= lengths.size()) s.putNull("quote").put("line", 0).put("busy", (int) tuning("npcBusyTicks")); }
                continue;
            }
            s.put("wait", touching(player, npc.path("trigger"), npc.path("screen").asInt()) ? s.path("wait").asInt() + 1 : 0);
            if (s.path("wait").asInt() < npc.path("delayTicks").asInt()) continue; s.put("wait", 0);
            int flagged = -1; var flagQuotes = npc.path("flagQuotes");
            for (int i = 0; i < flagQuotes.size(); i++) { var quote = flagQuotes.get(i); if (contains(state.path("flags"), quote.path("flag").asText()) && s.path("flags").path(quote.path("flag").asText()).asInt() < quote.path("sequences").size()) { flagged = i; break; } }
            boolean consumed = true;
            if (flagged >= 0) { String flag = flagQuotes.get(flagged).path("flag").asText(); int index = s.path("flags").path(flag).asInt(); s.put("quote", "flag:" + flagged + ":" + index).put("introduced", true); object(s, "flags").put(flag, index + 1); }
            else if (!s.path("introduced").asBoolean()) s.put("quote", "intro").put("introduced", true);
            else if (!s.path("pool").isEmpty()) { var pool = array(s, "pool"); s.put("quote", pool.remove(pool.size() - 1).asText()); }
            else { int size = npc.path("loopLengths").size(); if (size > 0) s.put("quote", "loop:" + s.path("loop").asInt() % size); else s.putNull("quote"); increment(s, "loop"); consumed = false; }
            if (consumed && !s.path("quote").isNull()) increment(s, "talks");
        }
        for (JsonNode readable : data.path("readables")) {
            String id = readable.path("id").asText(); var s = object(state.path("readables"), id);
            if (!touching(player, readable.path("trigger"), readable.path("screen").asInt())) { object(state, "readables").set(id, freshReadable()); continue; }
            if (s.path("done").asBoolean()) continue;
            if (s.path("wait").asInt() < tuning("readableWaitTicks")) { s.put("wait", input.left() != input.right() || input.jump() ? 0 : s.path("wait").asInt() + 1); continue; }
            int ticks = increment(s, "ticks"), line = s.path("line").asInt(), length = readable.path("lineLengths").get(line).asInt();
            if (ticks % (int) tuning("readableCharacterTicks") == 0 && ticks <= length * tuning("readableCharacterTicks")) events.add(new Event("SPEECH", id, null));
            if (ticks >= length * tuning("readableCharacterTicks") + tuning("speechHoldTicks")) { s.put("ticks", 0); if (line + 1 < readable.path("lineLengths").size()) s.put("line", line + 1); else s.put("done", true); }
        }
        return List.copyOf(events);
    }

    private boolean count(JsonNode value) { return value.isIntegralNumber() && value.canConvertToInt() && value.asInt() >= 0 && value.asInt() <= tuning("maxInventory"); }
    private void require(boolean valid) { if (!valid) throw new IllegalArgumentException("Invalid adventure snapshot"); }
    private void counts(JsonNode value, String... fields) { for (String field : fields) require(count(value.path(field))); }
    public void restore(JsonNode saved) {
        require(saved.isObject() && saved.path("codecVersion").asInt() == 1 && saved.path("inventory").isObject());
        saved.path("inventory").properties().forEach(entry -> require(itemIds.contains(entry.getKey()) && count(entry.getValue())));
        var layers = new HashSet<String>(); require(saved.path("equipped").isArray());
        for (JsonNode item : saved.path("equipped")) { var skin = find(data.path("skins"), "item", item.asText()); require(item.isTextual() && skin != null && saved.path("inventory").path(item.asText()).asInt() > 0 && layers.add(skin.path("layer").asText())); }
        var collected = new HashSet<String>(); for (JsonNode item : data.path("items")) collected.add(item.path("id").asText()); for (JsonNode bird : data.path("birds")) collected.add("bird:" + bird.path("id").asText());
        require(saved.path("collected").isArray()); var seen = new HashSet<String>(); for (JsonNode item : saved.path("collected")) require(item.isTextual() && collected.contains(item.asText()) && seen.add(item.asText()));
        require(saved.path("flags").isArray()); for (JsonNode flag : saved.path("flags")) require(flag.isTextual() && Set.of("CompletedNormalGame", "CHASE_BIRD_PAST_SNOWSTORM").contains(flag.asText()));
        for (JsonNode bird : data.path("birds")) {
            var s = saved.path("birds").path(bird.path("id").asText()); counts(s, "index", "ticks"); require(s.path("index").asInt() < bird.path("positions").size());
            for (String key : List.of("x", "y", "vx", "vy")) require(s.path(key).isNumber() && Double.isFinite(number(s, key)));
            for (String key : List.of("done", "dropped", "flying")) require(s.path(key).isBoolean());
        }
        for (JsonNode merchant : data.path("merchants")) {
            var s = saved.path("merchants").path(merchant.path("id").asText()); counts(s, "price", "line", "ticks"); String stage = s.path("stage").asText();
            require(Set.of("WAITING", "INTRO", "SALE", "SELL", "NO_GOLD", "SOLD", "BUSY").contains(stage) && s.path("sold").isBoolean() && s.path("introduced").isBoolean());
            if (!Set.of("WAITING", "BUSY").contains(stage)) require(s.path("line").asInt() < merchant.path("stageLengths").path(stage).size());
        }
        for (JsonNode npc : data.path("npcs")) {
            String id = npc.path("id").asText(); var s = saved.path("dialogue").path(id); counts(s, "best", "loop", "line", "ticks", "wait", "busy", "talks"); require(s.path("introduced").isBoolean() && s.path("pool").isArray() && s.path("flags").isObject());
            for (JsonNode quote : s.path("pool")) require(quote.isTextual() && !quoteLengths(id, quote.asText()).isEmpty());
            require(s.path("quote").isNull() || (s.path("quote").isTextual() && s.path("line").asInt() < quoteLengths(id, s.path("quote").asText()).size()));
            s.path("flags").properties().forEach(entry -> require(find(npc.path("flagQuotes"), "flag", entry.getKey()) != null && count(entry.getValue())));
        }
        for (JsonNode readable : data.path("readables")) { var s = saved.path("readables").path(readable.path("id").asText()); counts(s, "wait", "line", "ticks"); require(s.path("wait").asInt() <= tuning("readableWaitTicks") && s.path("line").asInt() < readable.path("lineLengths").size() && s.path("done").isBoolean()); }
        state = saved.deepCopy();
    }
}
