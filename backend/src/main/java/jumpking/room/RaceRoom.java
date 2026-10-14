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
import jumpking.game.SharedData;
import jumpking.identity.SessionAuthority;
import jumpking.network.*;
import jumpking.records.CheckpointWriter;
import jumpking.records.PersistenceWorkers;
import jumpking.records.RoomRuns;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Mỗi Room sở hữu một actor; registry chỉ giữ chỗ và không thực hiện I/O. */
@Component
public class RaceRoom implements GameCommandHandler, AutoCloseable {
    private static final Set<String> COMMANDS = Set.of("CREATE_ROOM", "JOIN_ROOM", "GET_ROOM_STATE", "LEAVE_ROOM", "UPDATE_ROOM_SETTINGS", "RECONNECT", "CHAT_SEND", "START_GAME", "END_MATCH", "SET_ROOM_PAUSED", "PLAY_AGAIN", "PLAYER_INPUT", "CANCEL_INPUT", "EQUIP_ITEM");
    private final RoomDirectory directory;
    private final Memberships memberships;
    private final GameWorkers workers;
    private final NetworkSettings limits;
    private final SessionAuthority sessions;
    private final MonotonicTime time;
    private final ObjectMapper json;
    private final JsonNode rules;
    private final Clock clock;
    private final SharedData shared;
    private final FeatureFlags flags;
    private final PersistenceWorkers database;
    private final RoomRuns records;
    private final long saveIntervalNs;
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("room-clock").factory());
    public RaceRoom(RoomDirectory directory, Memberships memberships, GameWorkers workers, NetworkSettings limits, SessionAuthority sessions, MonotonicTime time, ObjectMapper json, SharedData shared, Clock clock,
            FeatureFlags flags, PersistenceWorkers database, RoomRuns records, @Value("${app.checkpoint-interval-ms:1000}") long saveIntervalMs) {
        this.directory = directory; this.memberships = memberships; this.workers = workers; this.limits = limits; this.sessions = sessions; this.time = time; this.json = json; rules = shared.rules(); this.clock = clock;
        this.shared = shared; this.flags = flags; this.database = database; this.records = records; saveIntervalNs = saveIntervalMs * 1_000_000L;
        if (saveIntervalMs < 1) throw new IllegalArgumentException("Room save interval must be positive");
    }
    private final class Member {
        final String id = UUID.randomUUID().toString(), token = token();
        final GameConnection.Identity identity;
        final RoomDirectory.Slot slot;
        final String nickname;
        final long order;
        GameConnection connection;
        boolean confirmed = true;
        long disconnectedAt, generation;
        Member(GameConnection connection, RoomDirectory.Slot slot, String nickname, long order) { this.connection = connection; identity = connection.identity(); this.slot = slot; this.nickname = nickname; this.order = order; }
    }
    private final class Room {
        final String id;
        final LinkedHashMap<String, Member> players = new LinkedHashMap<>();
        final ChatLog chat = new ChatLog(clock, time, limits.chatCodePoints(), limits.chatHistory(), limits.chatIntervalMs());
        final SerialExecutor actor;
        final ScheduledFuture<?> timer;
        RoomSettings settings = RoomSettings.defaults();
        String state = "WAITING";
        RoomMatch match;
        CheckpointWriter<RoomRuns.Write> writer;
        long countdownAt, nextTick, serverTick, lastSnapshotTick, eventSeq, lastSave, pauseDeadline;
        java.time.Instant pauseExpiresAt;
        final AtomicBoolean tickQueued = new AtomicBoolean();
        String host;
        long joinOrder;
        Room(String id) {
            this.id = id; actor = new SerialExecutor(workers.executor(), limits.commandQueue(), error -> players.values().forEach(player -> { if (player.connection != null) player.connection.close(org.springframework.web.socket.CloseStatus.SERVER_ERROR); }));
            timer = scheduler.scheduleAtFixedRate(() -> {
                if (!tickQueued.compareAndSet(false, true)) return;
                try { actor.execute(() -> { try { advance(this); } finally { tickQueued.set(false); } }); }
                catch (RejectedExecutionException busy) { tickQueued.set(false); }
            }, 0, 1_000_000_000L / shared.physics().path("tickRate").asInt(), TimeUnit.NANOSECONDS);
        }
    }
    @Override public boolean supports(GameConnection connection, GameEnvelope command) {
        if (!COMMANDS.contains(command.type())) return false;
        if (command.type().equals("CREATE_ROOM")) return true;
        if (command.type().equals("JOIN_ROOM") || command.type().equals("RECONNECT")) { var info = directory.find(command.payload().path("roomId").asText()); return info != null && info.kind().equals("ROOM"); }
        var membership = memberships.find(connection.identity().owner()); return membership != null && rooms.containsKey(membership.roomId());
    }
    @Override public void handle(GameConnection connection, GameEnvelope command) {
        if (command.type().equals("CREATE_ROOM") || command.type().equals("JOIN_ROOM")) { join(connection, command); return; }
        var membership = memberships.find(connection.identity().owner());
        var room = membership == null ? null : rooms.get(membership.roomId());
        if (room == null) throw new ApiException(404, "ROOM_NOT_FOUND", "Room not found");
        room.actor.execute(() -> safely(connection, command, () -> {
            expirePause(room, time.nanos());
            var player = room.players.get(membership.playerId());
            if (player == null) throw new ApiException(404, "ROOM_NOT_FOUND", "Membership has ended");
            if (command.type().equals("RECONNECT")) { reconnect(room, player, connection, command); return; }
            if (player.connection != connection) throw new ApiException(409, "INVALID_RECONNECT", "Reconnect with your saved token");
            switch (command.type()) {
                case "GET_ROOM_STATE" -> { GameEnvelope.fields(command.payload(), Set.of()); connection.reply(command, "ROOM_STATE", state(room)); }
                case "LEAVE_ROOM" -> { GameEnvelope.fields(command.payload(), Set.of()); remove(room, player, command); }
                case "UPDATE_ROOM_SETTINGS" -> {
                    if (!player.id.equals(room.host)) throw new ApiException(403, "FORBIDDEN", "Only the Host can change settings");
                    if (!room.state.equals("WAITING")) throw new ApiException(409, "CONFLICT", "Settings can only change in the Lobby");
                    room.settings = RoomSettings.parse(command.payload(), rules); connection.reply(command, "ROOM_STATE", state(room)); broadcast(room);
                }
                case "START_GAME" -> { GameEnvelope.fields(command.payload(), Set.of()); start(room, player); connection.reply(command, "ROOM_STATE", state(room)); }
                case "PLAY_AGAIN" -> {
                    GameEnvelope.fields(command.payload(), Set.of("matchId"));
                    if (room.match == null || !room.match.id.equals(GameEnvelope.text(command.payload(), "matchId"))) throw new ApiException(409, "STALE_PLAY", "Confirmation belongs to an old match");
                    if (!Set.of("FINISHED", "WAITING").contains(room.state) || room.match.endedAt == null) throw new ApiException(409, "CONFLICT", "The match has not ended");
                    if (room.match.saveStatus.equals("FAILED")) save(room, true);
                    if (room.match.saveStatus.equals("SAVED")) { player.confirmed = true; room.state = "WAITING"; directory.joinable(room.id, true); }
                    connection.reply(command, "ROOM_STATE", state(room)); broadcast(room);
                }
                case "END_MATCH" -> {
                    GameEnvelope.fields(command.payload(), Set.of()); requireHost(room, player);
                    if (!Set.of("PLAYING", "PAUSED").contains(room.state)) throw new ApiException(409, "CONFLICT", "No running match to end");
                    room.match.finish("HOST_ENDED", clock.instant()); finish(room); connection.reply(command, "GAME_FINISHED", room.match.results());
                }
                case "SET_ROOM_PAUSED" -> {
                    GameEnvelope.fields(command.payload(), Set.of("playId", "paused")); requireHost(room, player);
                    if (room.match == null || !room.match.id.equals(GameEnvelope.text(command.payload(), "playId"))) throw new ApiException(409, "STALE_PLAY", "Pause belongs to an old match");
                    setPaused(room, GameEnvelope.bool(command.payload(), "paused", false)); connection.reply(command, "ROOM_STATE", state(room));
                }
                case "PLAYER_INPUT", "CANCEL_INPUT" -> {
                    var racer = room.match == null ? null : room.match.players.get(player.id);
                    if (racer == null) throw new ApiException(409, "STALE_PLAY", "No current play context");
                    racer.input.offer(command, time.nanos(), flags.flyEnabled());
                    if (!room.state.equals("PLAYING") || !racer.status.equals("ACTIVE")) racer.input.clear();
                    else if (command.type().equals("CANCEL_INPUT")) racer.game.physics().cancelInput();
                }
                case "EQUIP_ITEM" -> {
                    GameEnvelope.fields(command.payload(), Set.of("itemId", "equipped"));
                    var racer = room.match == null ? null : room.match.players.get(player.id);
                    if (racer == null || !room.state.equals("PLAYING") || !racer.status.equals("ACTIVE") || !racer.game.adventure().equip(GameEnvelope.text(command.payload(), "itemId"), GameEnvelope.bool(command.payload(), "equipped", false))) throw new ApiException(409, "CONFLICT", "Item is not owned or cannot be equipped");
                    connection.reply(command, "ROOM_STATE", state(room)); snapshots(room);
                }
                case "CHAT_SEND" -> {
                    GameEnvelope.fields(command.payload(), Set.of("text"));
                    var message = room.chat.send(player.id, player.nickname, player.slot.color(), GameEnvelope.text(command.payload(), "text"));
                    var payload = Map.of("roomId", room.id, "messages", List.of(message)); connection.reply(command, "CHAT_MESSAGE", payload);
                    for (var peer : room.players.values()) if (peer != player && peer.connection != null) peer.connection.send("CHAT_MESSAGE", payload);
                }
                default -> throw GameEnvelope.invalid();
            }
        }));
    }
    private synchronized void join(GameConnection connection, GameEnvelope command) {
        boolean create = command.type().equals("CREATE_ROOM");
        GameEnvelope.fields(command.payload(), create ? Set.of("nickname", "settings") : Set.of("roomId", "nickname"));
        String nickname = GameEnvelope.text(command.payload(), "nickname").strip();
        if (nickname.codePointCount(0, nickname.length()) > 16 || !nickname.matches("[\\p{L}\\p{M}\\p{N} _-]+")) throw new ApiException(400, "VALIDATION", "Nickname must contain 1–16 letters, numbers, spaces, underscores or hyphens");
        var settings = create && command.payload().has("settings") ? RoomSettings.parse(command.payload().get("settings"), rules) : RoomSettings.defaults();
        if (memberships.find(connection.identity().owner()) != null) throw new ApiException(409, "ALREADY_IN_ROOM", "Leave your current room first");
        var slot = create ? directory.createRoom() : directory.room(GameEnvelope.text(command.payload(), "roomId"));
        try {
            var room = rooms.computeIfAbsent(slot.roomId(), Room::new);
            var player = new Member(connection, slot, nickname, ++room.joinOrder);
            memberships.claim(player.identity.owner(), new Memberships.Membership(room.id, player.id));
            try { room.actor.execute(() -> {
                if (rooms.get(room.id) != room || !room.state.equals("WAITING")) {
                    memberships.release(player.identity.owner(), new Memberships.Membership(room.id, player.id)); directory.release(slot);
                    connection.error(command, new ApiException(409, "ROOM_NOT_JOINABLE", "This room is not accepting new players")); return;
                }
                if (create) room.settings = settings;
                room.players.put(player.id, player); if (!connection.open()) { player.connection = null; player.disconnectedAt = time.nanos(); }
                chooseHost(room); connection.reply(command, "ROOM_JOINED", joined(room, player)); broadcast(room); chatHistory(room, player);
            }); } catch (RuntimeException error) { memberships.release(player.identity.owner(), new Memberships.Membership(room.id, player.id)); throw error; }
        } catch (RuntimeException error) { if (directory.release(slot)) { var room = rooms.remove(slot.roomId()); if (room != null) room.timer.cancel(false); } throw error; }
    }
    private void reconnect(Room room, Member player, GameConnection connection, GameEnvelope command) {
        GameEnvelope.fields(command.payload(), Set.of("roomId", "playerId", "reconnectToken"));
        if (!room.id.equals(GameEnvelope.text(command.payload(), "roomId")) || !player.id.equals(GameEnvelope.text(command.payload(), "playerId"))
                || !MessageDigest.isEqual(player.token.getBytes(java.nio.charset.StandardCharsets.UTF_8), GameEnvelope.text(command.payload(), "reconnectToken").getBytes(java.nio.charset.StandardCharsets.UTF_8))) throw new ApiException(403, "INVALID_RECONNECT", "Invalid reconnect context");
        if (!room.state.equals("PAUSED") && player.connection == null && time.nanos() - player.disconnectedAt >= rules.path("reconnectMs").asLong() * 1_000_000L) throw new ApiException(409, "RECONNECT_EXPIRED", "Reconnect is no longer available");
        var old = player.connection; player.connection = connection; player.generation++; chooseHost(room);
        if (old != null && old != connection) old.close(org.springframework.web.socket.CloseStatus.NORMAL);
        if (room.match != null && room.match.players.containsKey(player.id)) room.match.players.get(player.id).input.reset(room.match.id, player.generation);
        room.nextTick = time.nanos(); connection.reply(command, "RECONNECT_OK", context(room, player)); broadcast(room); chatHistory(room, player); snapshots(room);
    }
    private void chooseHost(Room room) {
        var old = room.host == null ? null : room.players.get(room.host);
        if (old != null && old.connection != null) return;
        room.host = room.players.values().stream().filter(player -> player.connection != null).min(Comparator.comparingLong(player -> player.order)).map(player -> player.id).orElse(null);
    }
    private void remove(Room room, Member player, GameEnvelope command) {
        if (room.state.equals("COUNTDOWN")) cancelCountdown(room);
        if (room.match != null && room.match.startedAt != null && room.match.endedAt == null) room.match.abandon(player.id, clock.instant());
        if (room.players.remove(player.id) == null) return;
        room.chat.leave(player.id);
        memberships.release(player.identity.owner(), new Memberships.Membership(room.id, player.id));
        if (player.connection != null) { if (command == null) player.connection.send("ROOM_LEFT", Map.of("roomId", room.id)); else player.connection.reply(command, "ROOM_LEFT", Map.of("roomId", room.id)); }
        boolean empty = directory.release(player.slot);
        if (room.match != null && room.match.startedAt != null && room.match.endedAt == null) {
            if (empty) { room.match.finish("ALL_DISCONNECTED", clock.instant()); finish(room); }
            else save(room, true);
        }
        if (empty) { rooms.remove(room.id, room); room.timer.cancel(false); }
        else { chooseHost(room); broadcast(room); snapshots(room); }
    }
    private ObjectNode context(Room room, Member player) { var result = json.createObjectNode().put("roomId", room.id).put("playerId", player.id).put("generation", player.generation); if (room.match == null) result.putNull("playId"); else result.put("playId", room.match.id); return result; }
    private ObjectNode joined(Room room, Member player) { return context(room, player).put("kind", "ROOM").put("reconnectToken", player.token); }
    private ObjectNode state(Room room) {
        var state = json.createObjectNode().put("roomId", room.id).put("kind", "ROOM").put("state", room.state);
        if (room.host == null) state.putNull("hostPlayerId"); else state.put("hostPlayerId", room.host);
        state.set("settings", json.valueToTree(room.settings)); for (String field : List.of("currentPlay", "result", "pauseExpiresAt")) state.putNull(field);
        if (room.pauseExpiresAt != null) state.put("pauseExpiresAt", room.pauseExpiresAt.toString());
        if (room.match != null) { state.putObject("currentPlay").put("id", room.match.id).put("elapsedTimeMs", room.match.ms(room.match.ticks)).put("countdownRemainingMs", room.state.equals("COUNTDOWN") ? Math.max(0, (room.countdownAt - time.nanos()) / 1_000_000L) : 0); if (room.match.endedAt != null) state.set("result", json.valueToTree(room.match.results())); }
        var members = state.putArray("members");
        for (var player : room.players.values()) {
            var member = members.addObject().put("playerId", player.id).put("nickname", player.nickname).put("colorSlot", player.slot.color()).put("connected", player.connection != null).putNull("paused").put("nextMatchConfirmed", player.confirmed);
            if (player.identity.username() == null) member.putNull("username"); else member.put("username", player.identity.username());
        }
        return state;
    }
    private void broadcast(Room room) { var payload = state(room); for (var player : room.players.values()) if (player.connection != null) player.connection.send("ROOM_STATE", payload); }
    private void chatHistory(Room room, Member player) { if (player.connection != null) player.connection.send("CHAT_HISTORY", Map.of("roomId", room.id, "messages", room.chat.history())); }
    private void safely(GameConnection connection, GameEnvelope command, Runnable action) {
        if (!connection.open()) return;
        try { action.run(); if (connection.identity().sessionId() != null) sessions.valid(sessions.find(connection.identity().sessionId()), true); }
        catch (ApiException error) { connection.error(command, error); }
        catch (Exception error) { connection.error(command, new ApiException(500, "INTERNAL_ERROR", "Unable to process Room command")); }
    }
    @Override public void disconnected(GameConnection connection) {
        var membership = memberships.find(connection.identity().owner()); var room = membership == null ? null : rooms.get(membership.roomId()); if (room == null) return;
        try { room.actor.execute(() -> {
            var player = room.players.get(membership.playerId()); if (player == null || player.connection != connection) return;
            if (room.state.equals("COUNTDOWN")) cancelCountdown(room);
            if (room.match != null && room.match.players.containsKey(player.id)) { var racer = room.match.players.get(player.id); racer.input.clear(); racer.game.physics().cancelInput(); }
            player.connection = null; player.disconnectedAt = time.nanos(); chooseHost(room); broadcast(room);
            if (room.state.equals("PLAYING") && room.players.values().stream().noneMatch(member -> member.connection != null)) save(room, true);
        }); } catch (RejectedExecutionException busy) { connection.close(org.springframework.web.socket.CloseStatus.SERVICE_OVERLOAD); }
    }
    private void requireHost(Room room, Member player) { if (!player.id.equals(room.host)) throw new ApiException(403, "FORBIDDEN", "Only the Host can perform this action"); }
    private void start(Room room, Member host) {
        requireHost(room, host);
        if (!room.state.equals("WAITING")) throw new ApiException(409, "CONFLICT", "The Room is not waiting for a match");
        var roster = room.players.values().stream().filter(player -> player.connection != null && player.confirmed).toList();
        if (!host.confirmed || roster.size() < rules.path("minimumRoomPlayers").asInt()) throw new ApiException(409, "NOT_ENOUGH_PLAYERS", "The Host and another connected player must confirm");
        if (room.match != null && room.match.endedAt != null && !room.match.saveStatus.equals("SAVED")) throw new ApiException(409, "SAVE_PENDING", "Wait for the previous results to be saved");
        for (var player : List.copyOf(room.players.values())) if (!roster.contains(player)) remove(room, player, null);
        var specs = roster.stream().map(player -> new RoomMatch.Spec(player.id, player.identity.owner(), player.nickname, player.slot.color(), ++player.generation)).toList();
        room.match = new RoomMatch(room.id, room.settings, specs, shared, json, limits, flags.flyEnabled()); room.writer = database.writer(records::save);
        room.state = "COUNTDOWN"; room.countdownAt = time.nanos() + rules.path("countdownMs").asLong() * 1_000_000L; directory.joinable(room.id, false);
        for (var player : roster) player.connection.send("GAME_STARTED", Map.of("playId", room.match.id, "generation", player.generation, "countdownMs", rules.path("countdownMs").asLong()));
        broadcast(room);
    }
    private void cancelCountdown(Room room) {
        if (!room.state.equals("COUNTDOWN")) return;
        room.state = "WAITING"; room.match = null; room.writer = null; directory.joinable(room.id, true);
    }
    private void setPaused(Room room, boolean paused) {
        if (!Set.of("PLAYING", "PAUSED").contains(room.state)) throw new ApiException(409, "CONFLICT", "No running match to pause");
        if (paused == room.state.equals("PAUSED")) return;
        long now = time.nanos();
        for (var racer : room.match.players.values()) { racer.input.clear(); racer.game.physics().cancelInput(); }
        if (paused) {
            room.state = "PAUSED"; room.pauseDeadline = now + rules.path("hostPauseMs").asLong() * 1_000_000L;
            room.pauseExpiresAt = clock.instant().plusMillis(rules.path("hostPauseMs").asLong()); save(room, true);
        } else {
            room.state = "PLAYING"; room.pauseDeadline = 0; room.pauseExpiresAt = null; room.nextTick = now;
            for (var member : room.players.values()) if (member.connection == null) member.disconnectedAt = now;
        }
        broadcast(room); snapshots(room);
    }
    private void expirePause(Room room, long now) {
        if (!room.state.equals("PAUSED") || now < room.pauseDeadline) return;
        room.match.finish("PAUSE_TIMEOUT", clock.instant()); finish(room);
        for (var member : List.copyOf(room.players.values())) remove(room, member, null);
    }
    private void advance(Room room) {
        long now = time.nanos();
        expirePause(room, now);
        if (!room.state.equals("PAUSED")) for (var player : List.copyOf(room.players.values())) if (player.connection == null && now - player.disconnectedAt >= rules.path("reconnectMs").asLong() * 1_000_000L) remove(room, player, null);
        if (room.state.equals("COUNTDOWN") && now >= room.countdownAt) {
            room.match.start(clock.instant()); room.state = "PLAYING"; room.nextTick = now; save(room, true); broadcast(room); snapshots(room);
        }
        if (!room.state.equals("PLAYING")) return;
        long step = 1_000_000_000L / room.match.tickRate;
        if (room.players.values().stream().noneMatch(player -> player.connection != null)) { room.nextTick = now + step; return; }
        int steps = 0;
        while (now >= room.nextTick && steps++ < 5 && room.state.equals("PLAYING")) {
            room.nextTick += step; room.serverTick++;
            emit(room, room.match.tick(now, clock.instant()));
            if (room.match.endedAt != null) { finish(room); break; }
            if (room.match.ticks % (room.match.tickRate / rules.path("snapshotRate").asInt()) == 0) snapshots(room);
        }
        if (now >= room.nextTick) room.nextTick = now + step;
        if (room.state.equals("PLAYING") && now - room.lastSave >= saveIntervalNs) save(room, false);
    }
    private void finish(Room room) {
        room.players.values().forEach(player -> player.confirmed = false);
        room.pauseDeadline = 0; room.pauseExpiresAt = null;
        room.state = "FINISHED"; directory.joinable(room.id, false); save(room, true);
        for (var player : room.players.values()) if (player.connection != null) player.connection.send("GAME_FINISHED", room.match.results());
        broadcast(room); snapshots(room);
    }
    private void save(Room room, boolean barrier) {
        var match = room.match; if (match == null || match.startedAt == null) return;
        if (match.terminalWrite != null && !match.saveStatus.equals("FAILED")) return;
        var value = match.terminalWrite == null ? match.write(++match.revision) : match.terminalWrite;
        if (match.endedAt != null) match.terminalWrite = value;
        match.saveStatus = "PENDING"; match.pendingWrites++; room.lastSave = time.nanos();
        try { room.writer.submit(value, barrier, (outcome, error) -> room.actor.execute(() -> {
            if (outcome == CheckpointWriter.Outcome.RETRYING) return;
            match.pendingWrites--;
            if (outcome == CheckpointWriter.Outcome.SAVED) match.savedRevision = Math.max(match.savedRevision, value.revision());
            match.saveStatus = outcome == CheckpointWriter.Outcome.FAILED ? "FAILED" : match.savedRevision >= match.revision ? "SAVED" : "PENDING";
            var status = json.createObjectNode().put("playId", match.id).putNull("revision").put("saveStatus", match.saveStatus);
            for (var player : room.players.values()) if (player.connection != null) player.connection.send("SAVE_STATUS", status);
            if (room.match == match) broadcast(room);
        })); } catch (RejectedExecutionException busy) {
            match.pendingWrites--; match.saveStatus = "FAILED";
            for (var player : room.players.values()) if (player.connection != null) player.connection.send("ERROR", Map.of("code", "SAVE_FAILED", "message", "Room results could not be saved"));
        }
    }
    private void emit(Room room, List<RoomMatch.Events> events) {
        var effects = json.createArrayNode();
        for (var event : events) {
            var player = event.player(); var pose = player.game.physics().snapshot();
            for (var effect : event.effects().movement()) effects.addObject().put("eventSeq", ++room.eventSeq).put("playerId", player.spec.playerId()).put("playId", room.match.id).put("screen", pose.screen).put("effect", effect).put("terrain", pose.snow ? "SNOW" : pose.ice ? "ICE" : "NORMAL");
            var member = room.players.get(player.spec.playerId());
            if (member != null && member.connection != null && (!event.effects().adventure().isEmpty() || !event.effects().world().isEmpty())) {
                var cues = json.createArrayNode();
                for (var cue : event.effects().adventure()) cues.addObject().put("eventSeq", ++room.eventSeq).put("kind", "ADVENTURE").put("type", cue.type()).put("id", cue.id());
                for (var cue : event.effects().world()) cues.addObject().put("eventSeq", ++room.eventSeq).put("kind", "WORLD").put("type", cue.type()).put("id", cue.id());
                member.connection.send("ADVENTURE_EVENTS", Map.of("roomId", room.id, "playId", room.match.id, "events", cues));
            }
        }
        if (!effects.isEmpty()) for (var member : room.players.values()) if (member.connection != null && room.match.players.containsKey(member.id)) {
            int screen = room.match.players.get(member.id).game.physics().snapshot().screen;
            var visible = json.createArrayNode(); for (var effect : effects) if (effect.path("screen").asInt() == screen || effect.path("playerId").asText().equals(member.id)) visible.add(effect);
            if (!visible.isEmpty()) member.connection.send("PLAYER_EFFECTS", Map.of("roomId", room.id, "events", visible));
        }
    }
    private void snapshots(Room room) {
        var match = room.match; if (match == null || match.startedAt == null) return;
        // Commands có thể đổi state mà không chạy physics; snapshot mới vẫn phải có thứ tự riêng.
        room.serverTick = Math.max(room.serverTick, room.lastSnapshotTick + 1); room.lastSnapshotTick = room.serverTick;
        for (var member : room.players.values()) {
            var self = match.players.get(member.id); if (member.connection == null || self == null) continue;
            var snapshot = json.createObjectNode().put("roomId", room.id).put("serverTick", room.serverTick).put("playId", match.id).put("generation", member.generation).put("elapsedTimeMs", match.ms(match.ticks)).putNull("remainingTimeMs");
            var own = snapshot.putObject("self").put("lastProcessedInputSeq", self.input.processed()).put("status", self.status).put("paused", !room.state.equals("PLAYING"));
            own.set("simulation", json.valueToTree(self.game.snapshot())); own.putObject("metrics").put("currentProgress", self.game.physics().progress()).put("maxProgress", self.maximum);
            var peers = snapshot.putArray("peers");
            for (var other : room.players.values()) {
                var peer = match.players.get(other.id); if (peer == null || other == member) continue;
                var publicState = peers.addObject().put("playerId", other.id).put("nickname", other.nickname).put("colorSlot", other.slot.color()).put("status", peer.status);
                ObjectNode pose = json.valueToTree(peer.game.physics().snapshot()); pose.retain(List.of("x", "y", "vx", "vy", "grounded", "charge", "direction", "splat", "knocked", "ice", "snow", "wind", "screen", "ticks", "jumps", "falls"));
                publicState.set("state", pose); publicState.set("equipment", peer.game.adventure().snapshot().path("equipped"));
            }
            member.connection.snapshot(snapshot);
        }
    }
    private static String token() { byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    @Override public void close() { scheduler.shutdownNow(); }
}
