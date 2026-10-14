package jumpking.room;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import jumpking.config.NetworkSettings;
import jumpking.game.SharedData;
import jumpking.identity.SessionAuthority;
import jumpking.network.*;
import org.springframework.stereotype.Component;

/** Mỗi Room sở hữu một actor; registry chỉ giữ chỗ và không thực hiện I/O. */
@Component
public class RaceRoom implements GameCommandHandler, AutoCloseable {
    private static final Set<String> COMMANDS = Set.of("CREATE_ROOM", "JOIN_ROOM", "GET_ROOM_STATE", "LEAVE_ROOM", "UPDATE_ROOM_SETTINGS", "RECONNECT");
    private final RoomDirectory directory;
    private final Memberships memberships;
    private final GameWorkers workers;
    private final NetworkSettings limits;
    private final SessionAuthority sessions;
    private final MonotonicTime time;
    private final ObjectMapper json;
    private final JsonNode rules;
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("room-clock").factory());
    public RaceRoom(RoomDirectory directory, Memberships memberships, GameWorkers workers, NetworkSettings limits, SessionAuthority sessions, MonotonicTime time, ObjectMapper json, SharedData shared) {
        this.directory = directory; this.memberships = memberships; this.workers = workers; this.limits = limits; this.sessions = sessions; this.time = time; this.json = json; rules = shared.rules();
    }
    private final class Member {
        final String id = UUID.randomUUID().toString(), token = token();
        final GameConnection.Identity identity;
        final RoomDirectory.Slot slot;
        final String nickname;
        final long order;
        GameConnection connection;
        long disconnectedAt, generation;
        Member(GameConnection connection, RoomDirectory.Slot slot, String nickname, long order) { this.connection = connection; identity = connection.identity(); this.slot = slot; this.nickname = nickname; this.order = order; }
    }
    private final class Room {
        final String id;
        final LinkedHashMap<String, Member> players = new LinkedHashMap<>();
        final SerialExecutor actor;
        final ScheduledFuture<?> timer;
        RoomSettings settings = RoomSettings.defaults();
        String host;
        long joinOrder;
        Room(String id) {
            this.id = id; actor = new SerialExecutor(workers.executor(), limits.commandQueue(), error -> players.values().forEach(player -> { if (player.connection != null) player.connection.close(org.springframework.web.socket.CloseStatus.SERVER_ERROR); }));
            timer = scheduler.scheduleAtFixedRate(() -> {
                try { actor.execute(() -> {
                    long now = time.nanos();
                    for (var player : List.copyOf(players.values())) if (player.connection == null && now - player.disconnectedAt >= rules.path("reconnectMs").asLong() * 1_000_000L) remove(this, player, null);
                }); } catch (RejectedExecutionException busy) { /* Kiểm tra lại ở lần clock kế tiếp. */ }
            }, 1, 1, TimeUnit.SECONDS);
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
            var player = room.players.get(membership.playerId());
            if (player == null) throw new ApiException(404, "ROOM_NOT_FOUND", "Membership has ended");
            if (command.type().equals("RECONNECT")) { reconnect(room, player, connection, command); return; }
            if (player.connection != connection) throw new ApiException(409, "INVALID_RECONNECT", "Reconnect with your saved token");
            switch (command.type()) {
                case "GET_ROOM_STATE" -> { GameEnvelope.fields(command.payload(), Set.of()); connection.reply(command, "ROOM_STATE", state(room)); }
                case "LEAVE_ROOM" -> { GameEnvelope.fields(command.payload(), Set.of()); remove(room, player, command); }
                case "UPDATE_ROOM_SETTINGS" -> {
                    if (!player.id.equals(room.host)) throw new ApiException(403, "FORBIDDEN", "Only the Host can change settings");
                    room.settings = RoomSettings.parse(command.payload(), rules); connection.reply(command, "ROOM_STATE", state(room)); broadcast(room);
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
                if (create) room.settings = settings;
                room.players.put(player.id, player); if (!connection.open()) { player.connection = null; player.disconnectedAt = time.nanos(); }
                chooseHost(room); connection.reply(command, "ROOM_JOINED", joined(room, player)); broadcast(room);
            }); } catch (RuntimeException error) { memberships.release(player.identity.owner(), new Memberships.Membership(room.id, player.id)); throw error; }
        } catch (RuntimeException error) { if (directory.release(slot)) { var room = rooms.remove(slot.roomId()); if (room != null) room.timer.cancel(false); } throw error; }
    }
    private void reconnect(Room room, Member player, GameConnection connection, GameEnvelope command) {
        GameEnvelope.fields(command.payload(), Set.of("roomId", "playerId", "reconnectToken"));
        if (!room.id.equals(GameEnvelope.text(command.payload(), "roomId")) || !player.id.equals(GameEnvelope.text(command.payload(), "playerId"))
                || !MessageDigest.isEqual(player.token.getBytes(java.nio.charset.StandardCharsets.UTF_8), GameEnvelope.text(command.payload(), "reconnectToken").getBytes(java.nio.charset.StandardCharsets.UTF_8))) throw new ApiException(403, "INVALID_RECONNECT", "Invalid reconnect context");
        if (player.connection == null && time.nanos() - player.disconnectedAt >= rules.path("reconnectMs").asLong() * 1_000_000L) throw new ApiException(409, "RECONNECT_EXPIRED", "Reconnect is no longer available");
        var old = player.connection; player.connection = connection; player.generation++; chooseHost(room);
        if (old != null && old != connection) old.close(org.springframework.web.socket.CloseStatus.NORMAL);
        connection.reply(command, "RECONNECT_OK", context(room, player)); broadcast(room);
    }
    private void chooseHost(Room room) {
        var old = room.host == null ? null : room.players.get(room.host);
        if (old != null && old.connection != null) return;
        room.host = room.players.values().stream().filter(player -> player.connection != null).min(Comparator.comparingLong(player -> player.order)).map(player -> player.id).orElse(null);
    }
    private void remove(Room room, Member player, GameEnvelope command) {
        if (room.players.remove(player.id) == null) return;
        memberships.release(player.identity.owner(), new Memberships.Membership(room.id, player.id));
        if (player.connection != null) { if (command == null) player.connection.send("ROOM_LEFT", Map.of("roomId", room.id)); else player.connection.reply(command, "ROOM_LEFT", Map.of("roomId", room.id)); }
        if (directory.release(player.slot)) { rooms.remove(room.id, room); room.timer.cancel(false); }
        else { chooseHost(room); broadcast(room); }
    }
    private ObjectNode context(Room room, Member player) { return json.createObjectNode().put("roomId", room.id).put("playerId", player.id).put("generation", player.generation).putNull("playId"); }
    private ObjectNode joined(Room room, Member player) { return context(room, player).put("kind", "ROOM").put("reconnectToken", player.token); }
    private ObjectNode state(Room room) {
        var state = json.createObjectNode().put("roomId", room.id).put("kind", "ROOM").put("state", "WAITING");
        if (room.host == null) state.putNull("hostPlayerId"); else state.put("hostPlayerId", room.host);
        state.set("settings", json.valueToTree(room.settings)); for (String field : List.of("currentPlay", "result", "pauseExpiresAt")) state.putNull(field);
        var members = state.putArray("members");
        for (var player : room.players.values()) {
            var member = members.addObject().put("playerId", player.id).put("nickname", player.nickname).put("colorSlot", player.slot.color()).put("connected", player.connection != null).putNull("paused").put("nextMatchConfirmed", true);
            if (player.identity.username() == null) member.putNull("username"); else member.put("username", player.identity.username());
        }
        return state;
    }
    private void broadcast(Room room) { var payload = state(room); for (var player : room.players.values()) if (player.connection != null) player.connection.send("ROOM_STATE", payload); }
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
            player.connection = null; player.disconnectedAt = time.nanos(); chooseHost(room); broadcast(room);
        }); } catch (RejectedExecutionException busy) { connection.close(org.springframework.web.socket.CloseStatus.SERVICE_OVERLOAD); }
    }
    private static String token() { byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    @Override public void close() { scheduler.shutdownNow(); }
}
