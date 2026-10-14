package jumpking.room;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import jumpking.JumpKingApplication;
import jumpking.support.GameClient;
import jumpking.support.TestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = {JumpKingApplication.class, RoomRaceIT.TimeConfiguration.class}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.allowed-origins=http://allowed.test,http://pause.test", "app.checkpoint-interval-ms=25"})
class RoomPauseIT {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        var db = TestDatabase.get().dataSource;
        properties.add("spring.datasource.url", db::getUrl); properties.add("spring.datasource.username", db::getUsername); properties.add("spring.datasource.password", db::getPassword);
    }
    @Autowired RoomRaceIT.TestTime time;
    @Autowired Memberships memberships;
    @Autowired jumpking.identity.SessionAuthority sessions;
    @LocalServerPort int port;
    private record Started(String room, String play, JsonNode host, JsonNode peer) {}
    private Started start(GameClient a, GameClient b) throws Exception {
        if (a.owner == 0) a.guest(); if (b.owner == 0) b.guest(); a.connect(); b.connect();
        var host = a.ok("CREATE_ROOM", Map.of("nickname", "Host")); String room = host.path("roomId").asText();
        var peer = b.ok("JOIN_ROOM", Map.of("roomId", room, "nickname", "Peer")); a.ok("START_GAME", Map.of());
        String play = a.await(message -> message.path("type").asText().equals("GAME_STARTED")).path("payload").path("playId").asText();
        time.advance(3000); a.await(message -> message.path("type").asText().equals("GAME_SNAPSHOT")); return new Started(room, play, host, peer);
    }
    private Map<String, Object> pause(String play, boolean value) { return Map.of("playId", play, "paused", value); }
    private Map<String, String> reconnect(Started game, JsonNode member) { return Map.of("roomId", game.room, "playerId", member.path("playerId").asText(), "reconnectToken", member.path("reconnectToken").asText()); }
    @Test void hostPauseFreezesSimulationAndClockCancelsChargeAndCannotExtendItsDeadline() throws Exception {
        try (var a = new GameClient(port); var b = new GameClient(port)) {
            var game = start(a, b);
            assertThat(b.command("SET_ROOM_PAUSED", pause(game.play, true)).path("payload").path("code").asText()).isEqualTo("FORBIDDEN");
            assertThat(a.command("SET_ROOM_PAUSED", pause("old-match", true)).path("payload").path("code").asText()).isEqualTo("STALE_PLAY");
            a.input(Map.of("playId", game.play, "generation", 1, "seq", 1, "left", false, "right", true, "jump", true));
            a.ok("GET_ROOM_STATE", Map.of());
            time.advance(50); a.await(message -> message.path("type").asText().equals("GAME_SNAPSHOT") && message.path("payload").path("self").path("simulation").path("physics").path("charge").asInt() > 0);
            var paused = a.ok("SET_ROOM_PAUSED", pause(game.play, true));
            assertThat(paused.path("state").asText()).isEqualTo("PAUSED"); String expires = paused.path("pauseExpiresAt").asText(); assertThat(expires).endsWith("Z");
            var frozen = a.await(message -> message.path("type").asText().equals("GAME_SNAPSHOT") && message.path("payload").path("self").path("paused").asBoolean()).path("payload");
            assertThat(frozen.path("self").path("simulation").path("physics").path("charge").asInt()).isZero();
            time.advance(60_000); Thread.sleep(50);
            assertThat(a.ok("SET_ROOM_PAUSED", pause(game.play, true)).path("pauseExpiresAt").asText()).isEqualTo(expires);
            assertThat(a.ok("GET_ROOM_STATE", Map.of()).path("currentPlay").path("elapsedTimeMs")).isEqualTo(frozen.path("elapsedTimeMs"));
            a.ok("CHAT_SEND", Map.of("text", "Paused chat still works"));
            a.ok("SET_ROOM_PAUSED", pause(game.play, false)); time.advance(50);
            var resumed = a.await(message -> message.path("type").asText().equals("GAME_SNAPSHOT") && message.path("payload").path("elapsedTimeMs").asLong() > frozen.path("elapsedTimeMs").asLong()).path("payload");
            assertThat(resumed.path("elapsedTimeMs").asLong() - frozen.path("elapsedTimeMs").asLong()).isLessThan(150);
            assertThat(resumed.path("self").path("simulation").path("physics").path("jumps").asInt()).isZero();
            a.ok("SET_ROOM_PAUSED", pause(game.play, true)); a.ok("END_MATCH", Map.of());
            a.await(message -> message.path("type").asText().equals("ROOM_STATE") && message.path("payload").path("result").path("saveStatus").asText().equals("SAVED"));
            a.ok("LEAVE_ROOM", Map.of()); b.ok("LEAVE_ROOM", Map.of());
        }
    }
    @Test void pausedRosterSurvivesDisconnectAndResumeStartsANewFiveMinuteGrace() throws Exception {
        try (var a = new GameClient(port); var b = new GameClient(port)) {
            a.register(); String email = a.request("/auth/me", "GET", null).path("account").path("email").asText();
            var game = start(a, b); a.ok("SET_ROOM_PAUSED", pause(game.play, true));
            var cookies = (java.net.CookieManager) a.http.cookieHandler().orElseThrow();
            String session = cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("JSESSIONID")).findFirst().orElseThrow().getValue();
            sessions.find(session).invalidate(); a.disconnect();
            b.await(message -> message.path("type").asText().equals("ROOM_STATE") && message.path("payload").path("hostPlayerId").asText().equals(game.peer.path("playerId").asText()));
            time.advance(600_000); Thread.sleep(50); assertThat(memberships.find(a.owner)).isNotNull();
            a.login(email, "A secure server integration password"); a.connect(); a.ok("RECONNECT", reconnect(game, game.host));
            var state = a.ok("GET_ROOM_STATE", Map.of()); assertThat(state.path("state").asText()).isEqualTo("PAUSED");
            assertThat(state.path("hostPlayerId").asText()).isEqualTo(game.peer.path("playerId").asText()); b.messages.clear(); a.disconnect();
            b.await(message -> message.path("type").asText().equals("ROOM_STATE") && !message.path("payload").path("members").get(0).path("connected").asBoolean());
            b.disconnect(); time.advance(600_000); Thread.sleep(50);
            assertThat(memberships.find(a.owner)).isNotNull(); assertThat(memberships.find(b.owner)).isNotNull();
            b.connect(); b.ok("RECONNECT", reconnect(game, game.peer));
            assertThat(b.ok("GET_ROOM_STATE", Map.of()).path("hostPlayerId").asText()).isEqualTo(game.peer.path("playerId").asText());
            b.ok("SET_ROOM_PAUSED", pause(game.play, false)); time.advance(299_999); Thread.sleep(50); assertThat(memberships.find(a.owner)).isNotNull();
            time.advance(1); GameClient.until(() -> memberships.find(a.owner) == null);
            b.ok("END_MATCH", Map.of()); b.ok("LEAVE_ROOM", Map.of());
        }
    }
    @Test void aFullDayTimeoutFinalizesWithoutWinnersAndReleasesTheRoomExactlyOnce() throws Exception {
        try (var a = new GameClient(port); var b = new GameClient(port)) {
            var game = start(a, b); var paused = a.ok("SET_ROOM_PAUSED", pause(game.play, true)); b.disconnect();
            time.advance(86_399_999); Thread.sleep(50); assertThat(memberships.find(b.owner)).isNotNull();
            assertThat(a.ok("GET_ROOM_STATE", Map.of()).path("pauseExpiresAt")).isEqualTo(paused.path("pauseExpiresAt"));
            time.advance(1); GameClient.until(() -> memberships.find(a.owner) == null && memberships.find(b.owner) == null);
            var jdbc = TestDatabase.get().jdbc;
            GameClient.until(() -> jdbc.queryForObject("SELECT COUNT(*) FROM matches WHERE id=? AND end_reason='PAUSE_TIMEOUT'", Integer.class, game.play) == 1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM play_runs WHERE match_id=? AND status='UNFINISHED' AND is_winner=FALSE", Integer.class, game.play)).isEqualTo(2);
            assertThat(a.command("JOIN_ROOM", Map.of("roomId", game.room, "nickname", "Late")).path("type").asText()).isEqualTo("ERROR");
        }
    }
}
