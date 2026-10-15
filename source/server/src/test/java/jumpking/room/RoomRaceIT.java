package jumpking.room;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import jumpking.JumpKingApplication;
import jumpking.support.GameClient;
import jumpking.support.TestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = {JumpKingApplication.class, RoomRaceIT.TimeConfiguration.class}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.allowed-origins=http://allowed.test", "app.checkpoint-interval-ms=25"})
class RoomRaceIT {
    static class TestTime extends MonotonicTime {
        private final AtomicLong value = new AtomicLong(1_000_000_000L);
        @Override public long nanos() { return value.get(); }
        void advance(long milliseconds) { value.addAndGet(TimeUnit.MILLISECONDS.toNanos(milliseconds)); }
    }
    @TestConfiguration static class TimeConfiguration { @Bean @Primary TestTime testTime() { return new TestTime(); } }
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        var db = TestDatabase.get().dataSource;
        properties.add("spring.datasource.url", db::getUrl); properties.add("spring.datasource.username", db::getUsername); properties.add("spring.datasource.password", db::getPassword);
    }
    @Autowired TestTime time;
    @Autowired RaceRoom server;
    @Autowired jumpking.game.SharedData shared;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    @LocalServerPort int port;
    private void step(GameClient client, long milliseconds) throws Exception {
        long previous = client.ok("GET_ROOM_STATE", Map.of()).path("currentPlay").path("elapsedTimeMs").asLong(); time.advance(milliseconds);
        client.await(message -> message.path("type").asText().equals("GAME_SNAPSHOT") && message.path("payload").path("elapsedTimeMs").asLong() > previous);
    }
    @Test void minimumPlayersPermissionsAndCountdownCancellationDoNotCreateHistory() throws Exception {
        try (var a = new GameClient(port); var b = new GameClient(port); var extra = new GameClient(port)) {
            for (var client : List.of(a, b, extra)) { client.guest(); client.connect(); }
            String id = a.ok("CREATE_ROOM", Map.of("nickname", "Host")).path("roomId").asText();
            assertThat(a.command("START_GAME", Map.of()).path("payload").path("code").asText()).isEqualTo("NOT_ENOUGH_PLAYERS");
            b.ok("JOIN_ROOM", Map.of("roomId", id, "nickname", "Peer"));
            assertThat(b.command("START_GAME", Map.of()).path("payload").path("code").asText()).isEqualTo("FORBIDDEN");
            assertThat(a.ok("START_GAME", Map.of()).path("state").asText()).isEqualTo("COUNTDOWN");
            assertThat(extra.command("JOIN_ROOM", Map.of("roomId", id, "nickname", "Late")).path("payload").path("code").asText()).isEqualTo("ROOM_NOT_JOINABLE");
            var settings = new HashMap<String, Object>(); settings.put("mode", "RACE"); settings.put("timeLimitMinutes", null);
            assertThat(a.command("UPDATE_ROOM_SETTINGS", settings).path("payload").path("code").asText()).isEqualTo("CONFLICT");
            b.ok("LEAVE_ROOM", Map.of()); assertThat(a.ok("GET_ROOM_STATE", Map.of()).path("state").asText()).isEqualTo("WAITING");
            time.advance(3000); Thread.sleep(50);
            assertThat(TestDatabase.get().jdbc.queryForObject("SELECT COUNT(*) FROM matches WHERE room_code=?", Integer.class, id)).isZero();
            a.ok("LEAVE_ROOM", Map.of());
        }
    }
    @Test void twoRealClientsJumpAndEndTheMatchWithCommittedGuestAndAccountHistory() throws Exception {
        try (var a = new GameClient(port); var b = new GameClient(port)) {
            a.guest(); b.register(); a.connect(); b.connect();
            String room = a.ok("CREATE_ROOM", Map.of("nickname", "Guest")).path("roomId").asText(); b.ok("JOIN_ROOM", Map.of("roomId", room, "nickname", "Account"));
            a.ok("START_GAME", Map.of()); String play = a.await(message -> message.path("type").asText().equals("GAME_STARTED")).path("payload").path("playId").asText();
            time.advance(3000); a.await(message -> message.path("type").asText().equals("GAME_SNAPSHOT")); step(a, 50);
            assertThat(a.ok("GET_ROOM_STATE", Map.of()).path("state").asText()).isEqualTo("PLAYING");
            a.input(Map.of("playId", play, "generation", 1, "seq", 1, "left", false, "right", false, "jump", true)); a.ok("GET_ROOM_STATE", Map.of()); step(a, 100);
            a.input(Map.of("playId", play, "generation", 1, "seq", 2, "left", false, "right", false, "jump", false)); a.ok("GET_ROOM_STATE", Map.of()); step(a, 50);
            assertThat(a.snapshot().path("self").path("simulation").path("physics").path("jumps").asInt()).isEqualTo(1);
            GameClient.until(() -> TestDatabase.get().jdbc.queryForObject("SELECT COUNT(*) FROM play_runs WHERE match_id=? AND status='ACTIVE' AND jumps=1", Integer.class, play) == 1);
            b.await(message -> message.path("type").asText().equals("GAME_SNAPSHOT") && message.path("payload").path("peers").get(0).path("state").path("jumps").asInt() == 1);
            assertThat(b.command("END_MATCH", Map.of()).path("payload").path("code").asText()).isEqualTo("FORBIDDEN");
            var result = a.ok("END_MATCH", Map.of()); assertThat(result.path("reason").asText()).isEqualTo("HOST_ENDED"); assertThat(result.path("results").size()).isEqualTo(2);
            a.await(message -> message.path("type").asText().equals("ROOM_STATE") && message.path("payload").path("state").asText().equals("FINISHED") && message.path("payload").path("result").path("saveStatus").asText().equals("SAVED"));
            assertThat(a.request("/history", "GET", null).path("items").get(0).path("id").asText()).isEqualTo(play);
            assertThat(b.request("/history/ROOM/" + play, "GET", null).path("participants").size()).isEqualTo(2);
            var total = a.request("/profile/me", "GET", null).path("total"); assertThat(total.path("totalJumps").asInt()).isEqualTo(1); assertThat(total.path("matchesPlayed").asInt()).isEqualTo(1); assertThat(total.path("wins").asInt()).isZero();
            assertThat(TestDatabase.get().jdbc.queryForObject("SELECT COUNT(*) FROM play_runs WHERE match_id=?", Integer.class, play)).isEqualTo(2);
            a.ok("LEAVE_ROOM", Map.of()); b.ok("LEAVE_ROOM", Map.of());
        }
    }
    @Test void nativeRouteCheckpointFinishesBothPlayersInOneServerTickAndFinalizesOnce() throws Exception {
        try (var a = new GameClient(port); var b = new GameClient(port)) {
            a.guest(); b.guest(); a.connect(); b.connect();
            String id = a.ok("CREATE_ROOM", Map.of("nickname", "First")).path("roomId").asText(); b.ok("JOIN_ROOM", Map.of("roomId", id, "nickname", "Second"));
            a.ok("START_GAME", Map.of()); time.advance(3000); a.await(message -> message.path("type").asText().equals("GAME_SNAPSHOT"));
            var before = jumpking.support.MainBabeRoute.beforeFinish(shared, json);
            var room = ((Map<?, ?>) org.springframework.test.util.ReflectionTestUtils.getField(server, "rooms")).get(id);
            var actor = (jumpking.network.SerialExecutor) org.springframework.test.util.ReflectionTestUtils.getField(room, "actor");
            var restored = new java.util.concurrent.CompletableFuture<Void>();
            actor.execute(() -> {
                try {
                    var match = (RoomMatch) org.springframework.test.util.ReflectionTestUtils.getField(room, "match"); match.ticks = before.physics().ticks;
                    for (var player : match.players.values()) { player.game.restore(before); player.maximum = player.game.physics().progress(); player.peak = before.physics().ticks; }
                    restored.complete(null);
                } catch (Throwable error) { restored.completeExceptionally(error); }
            }); restored.get(8, TimeUnit.SECONDS);
            time.advance(50);
            var finished = a.await(message -> message.path("type").asText().equals("GAME_FINISHED")).path("payload");
            assertThat(finished.path("reason").asText()).isEqualTo("FINISH"); assertThat(finished.path("durationMs").asLong()).isEqualTo(282616);
            for (var row : finished.path("results")) { assertThat(row.path("rank").asInt()).isEqualTo(1); assertThat(row.path("isWinner").asBoolean()).isTrue(); }
            a.await(message -> message.path("type").asText().equals("ROOM_STATE") && message.path("payload").path("result").path("saveStatus").asText().equals("SAVED"));
            String play = finished.path("playId").asText();
            assertThat(TestDatabase.get().jdbc.queryForObject("SELECT COUNT(*) FROM play_runs WHERE match_id=? AND status='FINISHED' AND is_winner=TRUE AND finish_ticks=16957", Integer.class, play)).isEqualTo(2);
            assertThat(a.command("END_MATCH", Map.of()).path("type").asText()).isEqualTo("ERROR");
            assertThat(TestDatabase.get().jdbc.queryForObject("SELECT COUNT(*) FROM matches WHERE id=?", Integer.class, play)).isEqualTo(1);
            a.ok("LEAVE_ROOM", Map.of()); b.ok("LEAVE_ROOM", Map.of());
        }
    }
}
