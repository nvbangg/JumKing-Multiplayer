package jumpking.room;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import jumpking.config.FeatureFlags;
import jumpking.config.NetworkSettings;
import jumpking.game.*;
import jumpking.identity.SessionAuthority;
import jumpking.network.*;
import jumpking.records.CommunityRuns;
import jumpking.records.CheckpointWriter;
import jumpking.records.PersistenceWorkers;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CommunityTower implements GameCommandHandler, AutoCloseable {
    private static final Set<String> COMMANDS = Set.of("JOIN_COMMUNITY", "JOIN_ROOM", "GET_ROOM_STATE", "LEAVE_ROOM", "PLAYER_INPUT", "CANCEL_INPUT", "RECONNECT", "SET_COMMUNITY_PAUSED", "EQUIP_ITEM", "NEW_COMMUNITY_RUN");
    private final GameWorkers workers;
    private final RoomDirectory directory;
    private final Memberships memberships;
    private final CommunityRuns records;
    private final ObjectMapper json;
    private final NetworkSettings limits;
    private final SessionAuthority sessions;
    private final Clock clock;
    private final MonotonicTime time;
    private final FeatureFlags flags;
    private final GameMap map;
    private final Physics physics;
    private final JsonNode adventure, rules;
    private final Map<String, Tower> towers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("world-clock").factory());
    private final PersistenceWorkers database;
    private final long checkpointMs;
    public CommunityTower(GameWorkers workers, PersistenceWorkers database, RoomDirectory directory, Memberships memberships, CommunityRuns records,
            ObjectMapper json, NetworkSettings limits, SharedData shared, FeatureFlags flags, SessionAuthority sessions, Clock clock, MonotonicTime time,
            @Value("${app.checkpoint-interval-ms:1000}") long checkpointMs) {
        this.workers = workers; this.database = database; this.directory = directory; this.memberships = memberships; this.records = records; this.json = json;
        this.limits = limits; this.flags = flags; this.sessions = sessions; this.clock = clock; this.time = time; this.checkpointMs = checkpointMs;
        if (checkpointMs < 1) throw new IllegalArgumentException("Checkpoint interval must be positive");
        map = json.convertValue(shared.map(), GameMap.class); physics = new Physics(json.convertValue(shared.physics(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Number>>() {}));
        adventure = shared.adventure(); rules = shared.rules();
    }
    private final class Member {
        final GameConnection.Identity identity;
        final RoomDirectory.Slot slot;
        final String id = UUID.randomUUID().toString(), token = token();
        final InputFrames input = new InputFrames(limits.inputFrames(), limits.inputTimeoutMs());
        final CheckpointWriter<CommunityRuns.Write> writer = database.writer(records::save);
        GameConnection connection;
        GameSimulation game;
        CommunityRuns.Stored stored;
        long generation = 1, revision, savedRevision, peakTicks, disconnectedAt, pausedAt, lastPhysical = time.nanos(), lastSave = time.nanos();
        double maximum;
        boolean loading = true, paused, pausePending, leaving, finished, saving, terminalQueued, operation;
        final List<Runnable> terminalActions = new ArrayList<>();
        java.time.Instant finishedAt;
        int pendingWrites;
        String saveStatus = "SAVED";
        PlayerInput lastPhysicalInput = new PlayerInput(false, false, false, false, false, false);
        GameEnvelope leaveCommand;
        GameEnvelope operationCommand;
        Member(GameConnection connection, RoomDirectory.Slot slot) { this.connection = connection; identity = connection.identity(); this.slot = slot; }
    }
    private final class Tower {
        final String id;
        final LinkedHashMap<String, Member> players = new LinkedHashMap<>();
        final SerialExecutor actor;
        final AtomicBoolean tickQueued = new AtomicBoolean();
        long serverTick, nextTick = time.nanos();
        final ScheduledFuture<?> timer;
        Tower(String id) {
            this.id = id; actor = new SerialExecutor(workers.executor(), limits.commandQueue(), error -> players.values().forEach(player -> { player.paused = true; player.saveStatus = "FAILED"; }));
            timer = scheduler.scheduleAtFixedRate(() -> {
                if (!tickQueued.compareAndSet(false, true)) return;
                try { actor.execute(() -> { try { advance(); } finally { tickQueued.set(false); } }); }
                catch (RejectedExecutionException busy) { tickQueued.set(false); }
            }, 0, 1_000_000_000L / physics.tickRate, TimeUnit.NANOSECONDS);
        }
        void advance() {
            long now = time.nanos(); int steps = 0;
            boolean changed = false;
            for (Member player : List.copyOf(players.values())) {
                if (player.loading || player.operation || player.leaving) continue;
                if ((player.connection == null && now - player.disconnectedAt >= rules.path("reconnectMs").asLong() * 1_000_000L)
                        || (player.connection != null && player.paused && now - player.pausedAt >= rules.path("communityPauseMs").asLong() * 1_000_000L)) {
                    requestLeave(this, player, null); changed = true;
                } else if (!player.finished && !player.paused && !player.pausePending && now - player.lastPhysical >= rules.path("afkMs").asLong() * 1_000_000L) {
                    pause(player); changed = true;
                    if (player.paused) save(this, player, true, () -> runState(player)); else runState(player);
                }
            }
            while (now >= nextTick && steps++ < 5) { nextTick += 1_000_000_000L / physics.tickRate; tick(now); }
            if (now >= nextTick) nextTick = now + 1_000_000_000L / physics.tickRate;
            if (changed) { broadcast(this); snapshots(this); }
        }
        void tick(long now) {
            serverTick++;
            for (Member player : List.copyOf(players.values())) {
                if (player.loading || player.operation) continue;
                if (player.finished || player.leaving && player.paused) continue;
                if (!player.paused) {
                    var frame = player.input.poll(now);
                    if (frame.cancel() || player.pausePending) player.game.physics().cancelInput();
                    var input = player.pausePending ? PlayerInput.NEUTRAL : frame.input();
                    player.game.tick(input); double progress = player.game.physics().progress();
                    if (progress > player.maximum) { player.maximum = progress; player.peakTicks = player.game.physics().snapshot().ticks; }
                    if (player.game.finished()) { player.finished = true; player.finishedAt = clock.instant(); player.paused = true; player.pausedAt = now; player.pausePending = false; save(this, player, true, () -> {
                        if (player.connection != null) player.connection.send("PLAYER_FINISHED", Map.of("playerId", player.id, "playId", player.stored.id(), "result", summary(player), "showEnding", true));
                    }); }
                    else if (player.pausePending && stable(player)) {
                        player.paused = true; player.pausePending = false; player.pausedAt = now;
                        save(this, player, true, () -> { if (player.leaving) remove(this, player); else runState(player); });
                    }
                }
                if (!player.loading && !player.finished && !player.saving && !player.paused && !player.pausePending && !player.saveStatus.equals("FAILED") && now - player.lastSave >= checkpointMs * 1_000_000L) save(this, player, false, () -> {});
            }
            if (serverTick % (physics.tickRate / rules.path("snapshotRate").asInt()) == 0) snapshots(this);
        }
    }
    @Override public boolean supports(GameConnection connection, GameEnvelope command) {
        if (!COMMANDS.contains(command.type())) return false;
        if (command.type().equals("JOIN_COMMUNITY")) return true;
        if (command.type().equals("JOIN_ROOM") || command.type().equals("RECONNECT")) {
            var info = directory.find(command.payload().path("roomId").asText()); return info != null && info.kind().equals("COMMUNITY");
        }
        var membership = memberships.find(connection.identity().owner()); return membership != null && towers.containsKey(membership.roomId());
    }
    @Override public void handle(GameConnection connection, GameEnvelope command) {
        if (command.type().equals("JOIN_COMMUNITY") || command.type().equals("JOIN_ROOM")) { join(connection, command); return; }
        var membership = memberships.find(connection.identity().owner());
        var tower = membership == null ? null : towers.get(membership.roomId());
        if (tower == null) throw new ApiException(404, "ROOM_NOT_FOUND", "Community membership not found");
        tower.actor.execute(() -> safely(connection, command, () -> {
            Member player = tower.players.get(membership.playerId());
            if (player == null) throw new ApiException(404, "ROOM_NOT_FOUND", "Community membership has ended");
            if (command.type().equals("RECONNECT")) { reconnect(tower, player, connection, command); return; }
            if (player.connection != connection) throw new ApiException(409, "INVALID_RECONNECT", "Reconnect with your saved token");
            if (player.loading) throw new ApiException(409, "CONFLICT", "Community save is still loading");
            if (player.operation && !command.type().equals("GET_ROOM_STATE")) throw new ApiException(409, "CONFLICT", "Community save is changing");
            switch (command.type()) {
                case "GET_ROOM_STATE" -> { GameEnvelope.fields(command.payload(), Set.of()); connection.reply(command, "ROOM_STATE", state(tower)); }
                case "LEAVE_ROOM" -> { GameEnvelope.fields(command.payload(), Set.of()); requestLeave(tower, player, command); }
                case "PLAYER_INPUT", "CANCEL_INPUT" -> {
                    player.input.offer(command, time.nanos(), flags.flyEnabled());
                    if (player.paused || player.pausePending || player.finished) player.input.clear();
                    else {
                        var payload = command.payload();
                        if (command.type().equals("CANCEL_INPUT")) player.game.physics().cancelInput();
                        else {
                            var current = new PlayerInput(payload.path("left").asBoolean(), payload.path("right").asBoolean(), payload.path("jump").asBoolean(), payload.path("up").asBoolean(), payload.path("down").asBoolean(), payload.path("freeFlight").asBoolean());
                            if (!current.equals(player.lastPhysicalInput)) { player.lastPhysical = time.nanos(); player.lastPhysicalInput = current; }
                        }
                    }
                }
                case "SET_COMMUNITY_PAUSED" -> {
                    GameEnvelope.fields(command.payload(), Set.of("paused")); boolean paused = GameEnvelope.bool(command.payload(), "paused", false);
                    if (!paused && player.saveStatus.equals("FAILED")) {
                        player.operation = true; player.operationCommand = command;
                        save(tower, player, true, () -> {
                            player.operation = false; player.operationCommand = null; resume(player);
                            if (player.connection != null) player.connection.reply(command, "COMMUNITY_RUN_STATE", runPayload(player)); snapshots(tower);
                        });
                        return;
                    }
                    if (paused) pause(player);
                    else resume(player);
                    if (paused && player.paused) save(tower, player, true, () -> runState(player));
                    connection.reply(command, "COMMUNITY_RUN_STATE", runPayload(player));
                    snapshots(tower);
                }
                case "EQUIP_ITEM" -> {
                    GameEnvelope.fields(command.payload(), Set.of("itemId", "equipped"));
                    if (!player.game.adventure().equip(GameEnvelope.text(command.payload(), "itemId"), GameEnvelope.bool(command.payload(), "equipped", false))) throw new ApiException(409, "CONFLICT", "Item is not owned or cannot be equipped");
                    connection.reply(command, "COMMUNITY_RUN_STATE", runPayload(player)); save(tower, player, true, () -> runState(player));
                }
                case "NEW_COMMUNITY_RUN" -> newRun(tower, player, command);
                default -> throw GameEnvelope.invalid();
            }
        }));
    }
    private void resume(Member player) {
        if (!player.leaving && !player.finished && (player.paused || player.pausePending)) {
            player.paused = player.pausePending = false; player.lastPhysical = time.nanos(); player.generation++; player.input.reset(player.stored.id(), player.generation);
        }
    }
    private void newRun(Tower tower, Member player, GameEnvelope command) {
        GameEnvelope.fields(command.payload(), Set.of("expectedRunId"));
        if (!command.payload().has("expectedRunId")) throw GameEnvelope.invalid();
        String expected = command.payload().path("expectedRunId").isNull() ? null : GameEnvelope.text(command.payload(), "expectedRunId");
        if (!Objects.equals(expected, player.finished ? null : player.stored.id())) throw new ApiException(409, "CONFLICT", "Community save changed; refresh before starting a new game");
        if (player.leaving) throw new ApiException(409, "CONFLICT", "Save & Exit is in progress");
        player.operation = true; player.operationCommand = command; player.input.clear(); player.game.physics().cancelInput();
        save(tower, player, true, () -> {
            try { database.execute(() -> {
                try {
                    var game = new GameSimulation(map, physics, adventure, flags.flyEnabled(), false, List.of());
                    var stored = records.open(player.identity.owner(), true, expected, game.snapshot());
                    tower.actor.execute(() -> {
                        player.game = game; player.stored = stored; player.revision = player.savedRevision = stored.revision(); player.maximum = 0; player.peakTicks = 0;
                        player.finished = player.terminalQueued = player.operation = player.pausePending = player.paused = false; player.finishedAt = null; player.operationCommand = null;
                        player.generation++; player.input.reset(stored.id(), player.generation); player.lastPhysical = player.lastSave = time.nanos(); player.saveStatus = "SAVED";
                        if (player.connection == null) pause(player);
                        if (player.connection != null) player.connection.reply(command, "COMMUNITY_RUN_STATE", runPayload(player));
                        broadcast(tower); snapshots(tower);
                    });
                } catch (Exception error) { tower.actor.execute(() -> { player.operation = false; player.operationCommand = null; pause(player); if (player.connection != null) player.connection.error(command, error instanceof ApiException api ? api : saveError()); }); }
            }); } catch (RejectedExecutionException busy) { player.operation = false; player.operationCommand = null; pause(player); if (player.connection != null) player.connection.error(command, saveError()); }
        });
    }
    private synchronized void join(GameConnection connection, GameEnvelope command) {
        if (!connection.identity().account()) throw new ApiException(403, "ACCOUNT_REQUIRED", "Sign in to play Community Tower");
        boolean direct = command.type().equals("JOIN_ROOM");
        GameEnvelope.fields(command.payload(), direct ? Set.of("roomId", "nickname") : Set.of("newRun", "expectedRunId"));
        String requested = direct ? GameEnvelope.text(command.payload(), "roomId") : null;
        boolean replace = !direct && GameEnvelope.bool(command.payload(), "newRun", true);
        if (replace && !command.payload().has("expectedRunId")) throw GameEnvelope.invalid();
        String expected = command.payload().path("expectedRunId").isNull() || !command.payload().has("expectedRunId") ? null : GameEnvelope.text(command.payload(), "expectedRunId");
        if (memberships.find(connection.identity().owner()) != null) throw new ApiException(409, "ALREADY_IN_ROOM", "Leave your current room first");
        var slot = directory.community(requested); var player = new Member(connection, slot);
        try { memberships.claim(player.identity.owner(), new Memberships.Membership(slot.roomId(), player.id)); }
        catch (RuntimeException error) { directory.release(slot); throw error; }
        var tower = towers.computeIfAbsent(slot.roomId(), Tower::new);
        tower.actor.execute(() -> {
            tower.players.put(player.id, player);
            try {
                database.execute(() -> {
                    try {
                        var game = new GameSimulation(map, physics, adventure, flags.flyEnabled(), false, List.of());
                        var stored = records.open(player.identity.owner(), replace, expected, game.snapshot()); game.restore(stored.snapshot()); game.physics().cancelInput();
                        tower.actor.execute(() -> {
                            player.game = game; player.stored = stored; player.revision = player.savedRevision = stored.revision(); player.maximum = stored.maximum(); player.peakTicks = stored.peak(); player.loading = false;
                            player.input.reset(stored.id(), player.generation); player.lastPhysical = time.nanos();
                            if (player.connection == null) pause(player);
                            connection.reply(command, "ROOM_JOINED", joined(player)); broadcast(tower); runState(player); snapshots(tower);
                        });
                    } catch (Exception error) { tower.actor.execute(() -> { remove(tower, player); connection.error(command, error instanceof ApiException api ? api : saveError()); }); }
                });
            } catch (RejectedExecutionException busy) { remove(tower, player); connection.error(command, saveError()); }
        });
    }
    private void reconnect(Tower tower, Member player, GameConnection connection, GameEnvelope command) {
        GameEnvelope.fields(command.payload(), Set.of("roomId", "playerId", "reconnectToken"));
        String id = GameEnvelope.text(command.payload(), "playerId"), token = GameEnvelope.text(command.payload(), "reconnectToken");
        if (!tower.id.equals(GameEnvelope.text(command.payload(), "roomId")) || !player.id.equals(id)
                || !MessageDigest.isEqual(player.token.getBytes(java.nio.charset.StandardCharsets.UTF_8), token.getBytes(java.nio.charset.StandardCharsets.UTF_8))) throw new ApiException(403, "INVALID_RECONNECT", "Invalid reconnect context");
        if (player.loading || (player.connection == null && time.nanos() - player.disconnectedAt >= rules.path("reconnectMs").asLong() * 1_000_000L)) throw new ApiException(409, "RECONNECT_EXPIRED", "Reconnect is no longer available");
        var old = player.connection; player.connection = connection; player.generation++; player.input.reset(player.stored.id(), player.generation);
        pause(player); player.pausedAt = time.nanos();
        if (old != null && old != connection) old.close(org.springframework.web.socket.CloseStatus.NORMAL);
        connection.reply(command, "RECONNECT_OK", context(player)); broadcast(tower); runState(player); snapshots(tower);
    }
    private void pause(Member player) {
        player.input.clear(); if (player.game == null) return; player.game.physics().cancelInput();
        if (stable(player)) { if (!player.paused) player.pausedAt = time.nanos(); player.paused = true; player.pausePending = false; }
        else player.pausePending = true;
    }
    private boolean stable(Member player) { var state = player.game.physics().snapshot(); return state.freeFlight || (state.grounded && !state.phasingFall); }
    private void requestLeave(Tower tower, Member player, GameEnvelope command) {
        if (player.leaving) { if (command != null) player.connection.error(command, new ApiException(409, "CONFLICT", "Save & Exit is still in progress")); return; }
        player.leaving = true; player.leaveCommand = command; pause(player);
        if (player.paused || player.finished) save(tower, player, true, () -> remove(tower, player));
    }
    private void save(Tower tower, Member player, boolean flush, Runnable committed) {
        if (player.loading) return;
        if (player.finished && player.terminalQueued) {
            if (player.savedRevision == player.revision && !player.saving) committed.run(); else player.terminalActions.add(committed);
            return;
        }
        if (player.finished) player.terminalQueued = true;
        var snapshot = player.game.snapshot(); long revision = ++player.revision; player.saveStatus = "PENDING"; player.saving = true; player.pendingWrites++; player.lastSave = time.nanos();
        var write = new CommunityRuns.Write(player.stored.id(), player.identity.owner(), revision, player.finished ? "FINISHED" : "ACTIVE", snapshot, player.game.physics().progress(), player.maximum, player.peakTicks, player.finished ? player.finishedAt : null);
        runState(player);
        try { player.writer.submit(write, flush, (outcome, error) -> {
            if (outcome == CheckpointWriter.Outcome.RETRYING) { tower.actor.execute(() -> { pause(player); runState(player); }); return; }
            if (outcome == CheckpointWriter.Outcome.SUPERSEDED) { tower.actor.execute(() -> player.saving = --player.pendingWrites > 0); return; }
            if (outcome == CheckpointWriter.Outcome.SAVED) { tower.actor.execute(() -> {
                player.savedRevision = Math.max(player.savedRevision, revision); player.saving = --player.pendingWrites > 0; player.saveStatus = player.savedRevision == player.revision ? "SAVED" : "PENDING";
                if (player.connection != null) player.connection.send("SAVE_STATUS", Map.of("playId", write.id(), "revision", revision, "saveStatus", "SAVED"));
                if (flush) committed.run();
                if (write.status().equals("FINISHED")) { var actions = List.copyOf(player.terminalActions); player.terminalActions.clear(); actions.forEach(Runnable::run); }
            }); }
            else { tower.actor.execute(() -> {
                player.saving = --player.pendingWrites > 0; player.saveStatus = "FAILED"; if (write.status().equals("FINISHED")) player.terminalQueued = false; pause(player); player.leaving = false;
                player.operation = false;
                if (player.operationCommand != null && player.connection != null) player.connection.error(player.operationCommand, saveError()); player.operationCommand = null;
                if (player.connection != null) { if (player.leaveCommand != null) player.connection.error(player.leaveCommand, saveError()); else player.connection.send("ERROR", Map.of("code", "SAVE_FAILED", "message", "Community progress could not be saved")); runState(player); }
                player.leaveCommand = null;
            }); }
        }); } catch (RejectedExecutionException busy) { player.saving = --player.pendingWrites > 0; player.terminalQueued = false; player.saveStatus = "FAILED"; pause(player); player.leaving = false; if (player.connection != null) player.connection.send("ERROR", Map.of("code", "SAVE_FAILED", "message", "Save queue is full")); }
    }
    private void remove(Tower tower, Member player) {
        if (tower.players.remove(player.id) == null) return;
        memberships.release(player.identity.owner(), new Memberships.Membership(tower.id, player.id));
        boolean empty = directory.release(player.slot);
        if (player.connection != null) {
            if (player.leaveCommand != null) player.connection.reply(player.leaveCommand, "ROOM_LEFT", Map.of("roomId", tower.id));
            else player.connection.send("ROOM_LEFT", Map.of("roomId", tower.id));
        }
        if (empty) { towers.remove(tower.id, tower); tower.timer.cancel(false); }
        else broadcast(tower);
    }
    @Override public void disconnected(GameConnection connection) {
        var membership = memberships.find(connection.identity().owner()); var tower = membership == null ? null : towers.get(membership.roomId()); if (tower == null) return;
        try { tower.actor.execute(() -> { var player = tower.players.get(membership.playerId()); if (player == null || player.connection != connection) return;
            player.connection = null; player.disconnectedAt = time.nanos(); pause(player); if (!player.loading && player.paused) save(tower, player, true, () -> {}); broadcast(tower);
        }); } catch (RejectedExecutionException busy) { /* Deadline xử lý khi actor chạy lại. */ }
    }
    private void safely(GameConnection connection, GameEnvelope command, Runnable action) {
        if (!connection.open()) return;
        try { action.run(); if (connection.identity().sessionId() != null) sessions.valid(sessions.find(connection.identity().sessionId()), true); }
        catch (ApiException error) { connection.error(command, error); }
        catch (Exception error) { connection.error(command, new ApiException(500, "INTERNAL_ERROR", "Unable to process Community command")); }
    }
    private ObjectNode state(Tower tower) {
        var value = json.createObjectNode().put("roomId", tower.id).put("kind", "COMMUNITY").put("state", "PLAYING");
        for (String field : List.of("hostPlayerId", "settings", "currentPlay", "result", "pauseExpiresAt")) value.putNull(field);
        var players = value.putArray("members");
        for (Member player : tower.players.values()) if (!player.loading) players.addObject().put("playerId", player.id).put("nickname", player.identity.username()).put("username", player.identity.username())
            .put("colorSlot", player.slot.color()).put("connected", player.connection != null).put("paused", player.paused).putNull("nextMatchConfirmed");
        return value;
    }
    private void broadcast(Tower tower) { var state = state(tower); tower.players.values().forEach(player -> { if (player.connection != null && !player.loading) player.connection.send("ROOM_STATE", state); }); }
    private Map<String, Object> context(Member player) { return Map.of("roomId", player.slot.roomId(), "playerId", player.id, "generation", player.generation, "playId", player.stored.id()); }
    private Map<String, Object> joined(Member player) { var value = new LinkedHashMap<>(context(player)); value.put("kind", "COMMUNITY"); value.put("reconnectToken", player.token); return value; }
    private Map<String, Object> summary(Member player) {
        var state = player.game.physics().snapshot(); var result = new LinkedHashMap<String, Object>();
        result.put("id", player.stored.id()); result.put("status", player.finished ? "FINISHED" : "ACTIVE"); result.put("currentProgress", player.game.physics().progress()); result.put("maxProgress", player.maximum);
        result.put("elapsedTimeMs", ms(state.ticks)); result.put("peakReachedTimeMs", ms(player.peakTicks)); result.put("finishTimeMs", player.finished ? ms(state.ticks) : null); return result;
    }
    private Map<String, Object> runPayload(Member player) { return Map.of("run", summary(player), "generation", player.generation, "paused", player.paused, "pausePending", player.pausePending, "saveStatus", player.saveStatus); }
    private void runState(Member player) { if (player.connection != null && !player.loading) player.connection.send("COMMUNITY_RUN_STATE", runPayload(player)); }
    private void snapshots(Tower tower) {
        for (Member self : tower.players.values()) {
            if (self.loading || self.connection == null) continue;
            var value = json.createObjectNode().put("roomId", tower.id).put("serverTick", tower.serverTick).put("playId", self.stored.id()).put("generation", self.generation).put("elapsedTimeMs", ms(self.game.physics().snapshot().ticks)).putNull("remainingTimeMs");
            var state = value.putObject("self").put("lastProcessedInputSeq", self.input.processed()).put("status", self.finished ? "FINISHED" : "ACTIVE").put("paused", self.paused || self.pausePending);
            state.set("simulation", json.valueToTree(self.game.snapshot())); state.set("metrics", json.valueToTree(summary(self)));
            var peers = value.putArray("peers");
            for (Member peer : tower.players.values()) if (peer != self && !peer.loading) {
                var p = peers.addObject().put("playerId", peer.id).put("nickname", peer.identity.username()).put("colorSlot", peer.slot.color()).put("status", peer.finished ? "FINISHED" : "ACTIVE");
                p.set("state", json.valueToTree(peer.game.physics().snapshot())); p.set("equipment", peer.game.adventure().snapshot().path("equipped"));
            }
            self.connection.snapshot(value);
        }
    }
    private long ms(long ticks) { return ticks * 1000 / physics.tickRate; }
    private static String token() { byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static ApiException saveError() { return new ApiException(503, "SAVE_FAILED", "Community progress could not be saved; try again"); }
    @Override public void close() {
        scheduler.shutdownNow(); var pending = new ArrayList<CompletableFuture<Void>>();
        for (Tower tower : towers.values()) {
            var flushed = new CompletableFuture<Void>(); pending.add(flushed);
            try { tower.actor.execute(() -> {
                var saves = tower.players.values().stream().filter(player -> !player.loading).toList();
                if (saves.isEmpty()) { flushed.complete(null); return; }
                var count = new java.util.concurrent.atomic.AtomicInteger(saves.size());
                for (Member player : saves) { player.game.physics().cancelInput(); save(tower, player, true, () -> { if (count.decrementAndGet() == 0) flushed.complete(null); }); }
            }); } catch (RejectedExecutionException busy) { flushed.completeExceptionally(busy); }
        }
        try { CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS); }
        catch (Exception incomplete) { org.slf4j.LoggerFactory.getLogger(CommunityTower.class).warn("Community shutdown flush did not complete within the deadline"); }
    }
}
