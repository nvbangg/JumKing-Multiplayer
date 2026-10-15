package jumpking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import jumpking.JumpKingApplication;
import jumpking.dto.SoloMetrics;
import jumpking.security.GuestIdentity;
import jumpking.security.SessionAuthority;
import jumpking.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = {JumpKingApplication.class, IdentityIT.TimeConfiguration.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "app.allowed-origins=http://allowed.test")
class IdentityIT {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        var db = TestDatabase.get().dataSource;
        properties.add("spring.datasource.url", db::getUrl); properties.add("spring.datasource.username", db::getUsername); properties.add("spring.datasource.password", db::getPassword);
    }
    @TestConfiguration static class TimeConfiguration { @Bean @Primary MutableClock testClock() { return new MutableClock(); } }
    static class MutableClock extends Clock {
        private final AtomicLong time = new AtomicLong(System.currentTimeMillis());
        void advance(long milliseconds) { time.addAndGet(milliseconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(time.get()); }
    }
    @LocalServerPort int port;
    @Autowired MutableClock clock;
    @Autowired SessionAuthority sessions;
    @Autowired ObjectMapper json;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    @Autowired jumpking.room.Memberships memberships;
    @BeforeEach void nextWindow() { clock.advance(120_000); }
    private Client client() { return new Client(); }
    private class Client implements AutoCloseable {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).version(HttpClient.Version.HTTP_1_1).build();
        String csrf;
        HttpResponse<String> send(String path, String method, String body, String... headers) throws Exception {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api" + path));
            if (csrf != null && !method.equals("GET")) request.header("X-CSRF-TOKEN", csrf);
            if (body != null) request.header("Content-Type", "application/json");
            for (int index = 0; index < headers.length; index += 2) request.header(headers[index], headers[index + 1]);
            request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
        void token() throws Exception { var response = send("/auth/csrf", "GET", null); assertThat(response.statusCode()).isEqualTo(200); csrf = tree(response).path("token").asText(); }
        String cookie(String name) { return cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals(name)).findFirst().orElseThrow().getValue(); }
        @Override public void close() { http.close(); }
    }
    private JsonNode tree(HttpResponse<String> response) throws Exception { return json.readTree(response.body()); }
    private String unique() { return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 20); }
    private String registration(String username, String email, String password) throws Exception {
        return json.writeValueAsString(java.util.Map.of("username", username, "email", email, "password", password));
    }
    private void roomHistory(long guest) {
        String match = java.util.UUID.randomUUID().toString(); var jdbc = TestDatabase.get().jdbc;
        jdbc.update("INSERT INTO matches(id,room_code,mode,started_at) VALUES (?,'ABCDEF','RACE',UTC_TIMESTAMP(3))", match);
        roomFixtures.add(match);
        jdbc.update("INSERT INTO play_runs(id,user_id,kind,match_id,status,nickname,started_at,updated_at) VALUES (?,?,'ROOM',?,'ACTIVE','Guest Player',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
            java.util.UUID.randomUUID().toString(), guest, match);
    }
    private final java.util.List<String> roomFixtures = new java.util.ArrayList<>();
    @org.junit.jupiter.api.AfterEach void cleanupRoomFixtures() {
        var jdbc = TestDatabase.get().jdbc;
        for (String id : roomFixtures) { jdbc.update("DELETE FROM play_runs WHERE match_id=?", id); jdbc.update("DELETE FROM matches WHERE id=?", id); }
    }
    private String metrics(long revision, long ticks, String status) throws Exception {
        var body = json.createObjectNode().put("revision", revision).put("status", status).put("playTicks", ticks).put("jumps", ticks > 0 ? 1 : 0).put("falls", 0)
            .put("currentProgress", 0).put("maxProgress", 0).put("peakReachedTicks", 0).put("startedAt", "2026-01-01T00:00:00.000Z");
        if (status.equals("FINISHED")) body.put("finishTicks", ticks); else body.putNull("finishTicks");
        if (status.equals("ACTIVE")) body.putNull("endedAt"); else body.put("endedAt", "2026-01-01T00:00:10.000Z");
        return body.toString();
    }
    @Test void soloSyncIsRevisionedOwnerScopedAndStatsHistoryCountCommittedRunsOnce() throws Exception {
        try (var client = client(); var other = client()) {
            client.token(); assertThat(client.send("/auth/register", "POST", registration(unique(), unique() + "@example.test", "A secure solo password")).statusCode()).isEqualTo(200); client.token();
            String account = tree(client.send("/auth/me", "GET", null)).path("account").path("id").asText();
            String id = java.util.UUID.randomUUID().toString(), path = "/solo/runs/" + id;
            assertThat(client.send(path, "PUT", metrics(1, 60, "ACTIVE"), "X-Account-ID", "wrong-account").statusCode()).isEqualTo(409);
            assertThat(client.send(path, "PUT", metrics(1, 60, "ACTIVE"), "X-Account-ID", account).statusCode()).isEqualTo(200);
            assertThat(client.send(path, "PUT", metrics(1, 60, "ACTIVE")).statusCode()).isEqualTo(200);
            assertThat(client.send(path, "PUT", metrics(1, 120, "ACTIVE")).statusCode()).isEqualTo(409);
            assertThat(client.send(path, "PUT", metrics(2, 120, "ACTIVE")).statusCode()).isEqualTo(200);
            assertThat(tree(client.send(path, "PUT", metrics(1, 60, "ACTIVE"))).path("revision").asLong()).isEqualTo(1);
            assertThat(TestDatabase.get().jdbc.queryForObject("SELECT play_ticks FROM play_runs WHERE id=?", Long.class, id)).isEqualTo(120);
            assertThat(client.send(path, "PUT", metrics(3, 120, "FINISHED")).statusCode()).isEqualTo(200);
            assertThat(client.send(path, "PUT", metrics(4, 180, "ACTIVE")).statusCode()).isEqualTo(409);
            String second = java.util.UUID.randomUUID().toString(); assertThat(client.send("/solo/runs/" + second, "PUT", metrics(1, 60, "ABANDONED")).statusCode()).isEqualTo(200);
            var stats = tree(client.send("/profile/me", "GET", null)).path("total");
            assertThat(stats.path("totalPlayTimeMs").asLong()).isEqualTo(3000); assertThat(stats.path("totalJumps").asLong()).isEqualTo(2); assertThat(stats.path("finishes").asLong()).isEqualTo(1); assertThat(stats.path("bestFinishTimeMs").asLong()).isEqualTo(2000);
            var history = tree(client.send("/history?page=0&size=1", "GET", null)); assertThat(history.path("items").size()).isEqualTo(1); assertThat(history.path("hasNext").asBoolean()).isTrue();
            assertThat(tree(client.send("/history/SOLO/" + id, "GET", null)).path("status").asText()).isEqualTo("FINISHED");
            other.token(); assertThat(other.send("/auth/register", "POST", registration(unique(), unique() + "@example.test", "Another solo password")).statusCode()).isEqualTo(200); other.token();
            assertThat(other.send(path, "PUT", metrics(4, 180, "ACTIVE")).statusCode()).isEqualTo(404);
            assertThat(other.send("/history/SOLO/" + id, "GET", null).statusCode()).isEqualTo(404);
            assertThat(client.send("/history?page=invalid", "GET", null).statusCode()).isEqualTo(400);
        }
    }
    @Test void soloSyncRejectsScalarCoercionUnknownFieldsAndForeignKinds() throws Exception {
        try (var client = client()) {
            client.token(); assertThat(client.send("/auth/register", "POST", registration(unique(), unique() + "@example.test", "A strict solo password")).statusCode()).isEqualTo(200); client.token();
            String path = "/solo/runs/" + java.util.UUID.randomUUID();
            for (String body : java.util.List.of(metrics(1, 60, "ACTIVE").replace("\"revision\":1", "\"revision\":\"1\""), metrics(1, 60, "ACTIVE").replace("\"revision\":1", "\"revision\":1.5"),
                    metrics(1, 60, "ACTIVE").replace("\"status\":\"ACTIVE\"", "\"status\":0"), metrics(1, 60, "ACTIVE").replace("\"startedAt\":\"2026-01-01T00:00:00.000Z\"", "\"startedAt\":0"),
                    metrics(1, 60, "ACTIVE").replace("}", ",\"snapshot\":{}}"), metrics(SoloMetrics.MAX_SAFE_INTEGER + 1, 60, "ACTIVE"))) {
                assertThat(client.send(path, "PUT", body).statusCode()).as(body).isEqualTo(400);
            }
            long owner = tree(client.send("/auth/me", "GET", null)).path("account").path("id").asLong(); roomHistory(owner);
            String roomRun = TestDatabase.get().jdbc.queryForObject("SELECT id FROM play_runs WHERE user_id=?", String.class, owner);
            TestDatabase.get().jdbc.update("UPDATE play_runs SET status='DNF',ended_at=UTC_TIMESTAMP(3) WHERE id=?", roomRun);
            assertThat(client.send("/solo/runs/" + roomRun, "PUT", metrics(1, 60, "ACTIVE")).statusCode()).isEqualTo(404);
        }
    }
    @Test void soloQuotaCountsInvalidRequestsAndLargeTickTotalsDoNotOverflow() throws Exception {
        try (var client = client()) {
            client.token(); assertThat(client.send("/auth/register", "POST", registration(unique(), unique() + "@example.test", "A bounded Solo password")).statusCode()).isEqualTo(200); client.token();
            String first = "/solo/runs/" + java.util.UUID.randomUUID(), second = "/solo/runs/" + java.util.UUID.randomUUID();
            assertThat(client.send(first, "PUT", metrics(1, SoloMetrics.MAX_SAFE_INTEGER, "ACTIVE")).statusCode()).isEqualTo(200);
            assertThat(client.send(second, "PUT", metrics(1, SoloMetrics.MAX_SAFE_INTEGER, "ACTIVE")).statusCode()).isEqualTo(200);
            var expected = java.math.BigInteger.valueOf(SoloMetrics.MAX_SAFE_INTEGER).multiply(java.math.BigInteger.valueOf(2000)).divide(java.math.BigInteger.valueOf(60));
            assertThat(tree(client.send("/profile/me", "GET", null)).path("total").path("totalPlayTimeMs").bigIntegerValue()).isEqualTo(expected);
            assertThat(client.send(first, "PUT", "{invalid-json").statusCode()).isEqualTo(400);
            for (int index = 0; index < 57; index++) assertThat(client.send(first, "PUT", metrics(1, SoloMetrics.MAX_SAFE_INTEGER, "ACTIVE")).statusCode()).isEqualTo(200);
            var limited = client.send(first, "PUT", metrics(1, SoloMetrics.MAX_SAFE_INTEGER, "ACTIVE")); assertThat(limited.statusCode()).isEqualTo(429);
            assertThat(limited.headers().firstValue("Retry-After")).contains("60");
        }
    }
    @Test void publicMapAndConfigComeFromPackagedSharedDataAndServerFlags() throws Exception {
        try (var client = client()) {
            var map = client.send("/map", "GET", null); var config = client.send("/game-config", "GET", null);
            assertThat(map.statusCode()).isEqualTo(200); assertThat(config.statusCode()).isEqualTo(200);
            var shared = new jumpking.game.SharedData(json);
            assertThat(tree(map)).isEqualTo(shared.map()); assertThat(tree(config).path("physics")).isEqualTo(shared.physics());
            assertThat(tree(config).path("rules")).isEqualTo(shared.rules()); assertThat(tree(config).path("features").path("flyEnabled").asBoolean()).isFalse();
            assertThat(tree(config).path("appearances").size()).isEqualTo(10);
            assertThat(tree(config).path("appearances").get(0).path("color").asText()).isEqualTo("#ffffff");
            assertThat(config.body()).doesNotContain("password", "guest_token", "allowed-origins");
        }
    }

    @Test void meDoesNotCreateIdentityAndGuestUsesHashedOpaqueCookie() throws Exception {
        try (var client = client()) {
            int before = TestDatabase.get().jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
            var me = client.send("/auth/me", "GET", null);
            assertThat(tree(me).path("authenticated").asBoolean()).isFalse(); assertThat(tree(me).path("guest").asBoolean()).isFalse();
            assertThat(me.headers().allValues("set-cookie")).isEmpty();
            assertThat(TestDatabase.get().jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isEqualTo(before);
            assertThat(client.send("/auth/guest", "POST", null).statusCode()).isEqualTo(403);
            client.token(); var response = client.send("/auth/guest", "POST", null);
            assertThat(response.statusCode()).isEqualTo(200); assertThat(tree(response).path("guest").asBoolean()).isTrue();
            String cookie = response.headers().firstValue("set-cookie").orElseThrow();
            assertThat(cookie).contains("HttpOnly", "SameSite=Lax", "Path=/", "Max-Age=31536000").doesNotContain("Domain=", "Secure");
            String token = client.cookie(GuestIdentity.COOKIE); assertThat(token).matches("[A-Za-z0-9_-]{43}");
            assertThat(TestDatabase.get().jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE guest_token_hash=?", Integer.class, GuestIdentity.hash(token))).isEqualTo(1);
            assertThat(client.send("/auth/guest", "POST", null).headers().allValues("set-cookie")).isEmpty();
            assertThat(TestDatabase.get().jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isEqualTo(before + 1);
            assertThat(tree(client.send("/auth/me", "GET", null)).path("account").isNull()).isTrue();
        }
    }
    @Test void enforcesCsrfCorsPrivateAccessAndSafeJsonErrors() throws Exception {
        try (var client = client()) {
            var response = client.send("/auth/csrf", "GET", null); client.csrf = tree(response).path("token").asText();
            assertThat(response.headers().firstValue("set-cookie").orElseThrow()).contains("HttpOnly", "SameSite=Lax", "Path=/").doesNotContain("Domain=");
            assertThat(client.send("/auth/guest", "POST", null, "Origin", "http://evil.test").statusCode()).isEqualTo(403);
            assertThat(tree(client.send("/auth/guest", "POST", null, "Origin", "null")).path("error").path("code").asText()).isEqualTo("ORIGIN_DENIED");
            var preflight = client.send("/auth/guest", "OPTIONS", null, "Origin", "http://allowed.test", "Access-Control-Request-Method", "POST", "Access-Control-Request-Headers", "x-csrf-token,content-type");
            assertThat(preflight.statusCode()).isEqualTo(200); assertThat(preflight.headers().firstValue("access-control-allow-credentials")).contains("true");
            assertThat(client.send("/community/current", "GET", null).statusCode()).isEqualTo(401);
            client.csrf = "invalid"; assertThat(tree(client.send("/auth/guest", "POST", null)).path("error").path("code").asText()).isEqualTo("CSRF");
            var missing = client.send("/missing", "GET", null); assertThat(missing.statusCode()).isEqualTo(404);
            assertThat(tree(missing).path("error").path("code").asText()).isEqualTo("NOT_FOUND");
            assertThat(missing.headers().firstValue("cache-control")).contains("no-store");
        }
    }
    @Test void expiredGuestCannotReuseItsIdentity() throws Exception {
        try (var client = client()) {
            client.token(); assertThat(client.send("/auth/guest", "POST", null).statusCode()).isEqualTo(200);
            String old = client.cookie(GuestIdentity.COOKIE);
            TestDatabase.get().jdbc.update("UPDATE users SET created_at=? WHERE guest_token_hash=?",
                java.sql.Timestamp.from(clock.instant().minus(java.time.Duration.ofDays(366))), GuestIdentity.hash(old));
            assertThat(tree(client.send("/auth/me", "GET", null)).path("guest").asBoolean()).isFalse();
            assertThat(client.send("/auth/guest", "POST", null).statusCode()).isEqualTo(200);
            assertThat(client.cookie(GuestIdentity.COOKIE)).isNotEqualTo(old);
        }
    }
    @Test void limitsBodyIncludingChunkedRequestsAndKeepsIpBucketAcrossSessionChanges() throws Exception {
        try (var client = client()) {
            client.token(); assertThat(client.send("/auth/guest", "POST", " ".repeat(16385)).statusCode()).isEqualTo(400);
            var chunked = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/guest"))
                .header("Content-Type", "application/json").header("X-CSRF-TOKEN", client.csrf)
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new java.io.ByteArrayInputStream(new byte[16385]))).build();
            assertThat(client.http.send(chunked, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(400);
            for (int index = 0; index < 18; index++) assertThat(client.send("/auth/guest", "POST", null).statusCode()).isEqualTo(200);
            client.cookies.getCookieStore().removeAll(); client.token();
            var denied = client.send("/auth/guest", "POST", null);
            assertThat(denied.statusCode()).isEqualTo(429); assertThat(tree(denied).path("error").path("code").asText()).isEqualTo("RATE_LIMIT");
        }
    }
    @Test void expiresIdleAndAbsoluteSessionsButValidActivityOnlyExtendsIdle() throws Exception {
        try (var client = client()) {
            client.token(); String sessionId = client.cookie("JSESSIONID"); var session = sessions.find(sessionId); assertThat(session).isNotNull();
            clock.advance(29 * 60_000); assertThat(sessions.valid(session, false)).isTrue();
            clock.advance(60_000); var expired = client.send("/auth/me", "GET", null);
            assertThat(expired.statusCode()).isEqualTo(401); assertThat(tree(expired).path("error").path("code").asText()).isEqualTo("SESSION_EXPIRED");
            assertThat(sessions.find(sessionId)).isNull(); client.cookies.getCookieStore().removeAll(); client.token();
            sessionId = client.cookie("JSESSIONID"); session = sessions.find(sessionId);
            for (int index = 0; index < 47; index++) { clock.advance(30 * 60_000 - 1); assertThat(sessions.valid(session, true)).isTrue(); }
            clock.advance(30 * 60_000 - 1); assertThat(sessions.valid(session, true)).isTrue();
            clock.advance(48); assertThat(sessions.valid(session, true)).isFalse();
            assertThat(sessions.find(sessionId)).isNull();
        }
    }
    @Test void rejectsWebSocketWithoutAnAllowedOrigin() throws Exception {
        try (var client = client()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/ws/game")).GET().build();
            var response = client.http.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(403); assertThat(tree(response).path("error").path("code").asText()).isEqualTo("ORIGIN_DENIED");
        }
    }
    @Test void registrationRotatesSessionAndCsrfAndLogoutRevokesSession() throws Exception {
        try (var client = client(); var attacker = client()) {
            client.token(); String previous = client.cookie("JSESSIONID"), oldCsrf = client.csrf;
            String name = "player_" + unique(), email = unique() + "@example.test", password = "A secure test password";
            var response = client.send("/auth/register", "POST", registration(name, email.toUpperCase(java.util.Locale.ROOT), password));
            assertThat(response.statusCode()).isEqualTo(200); assertThat(tree(response).path("authenticated").asBoolean()).isTrue();
            assertThat(tree(response).path("account").path("id").isTextual()).isTrue();
            assertThat(tree(response).path("account").path("email").asText()).isEqualTo(email);
            String id = client.cookie("JSESSIONID"); assertThat(id).isNotEqualTo(previous); assertThat(sessions.find(previous)).isNull();
            String hash = TestDatabase.get().jdbc.queryForObject("SELECT password_hash FROM users WHERE email=?", String.class, email);
            assertThat(hash).startsWith("$2").isNotEqualTo(password); assertThat(passwords.matches(password, hash)).isTrue();
            assertThat(sessions.find(id).getAttribute("SPRING_SECURITY_CONTEXT").toString()).doesNotContain(password, hash);
            assertThat(tree(attacker.send("/auth/me", "GET", null, "Cookie", "JSESSIONID=" + previous)).path("authenticated").asBoolean()).isFalse();
            client.csrf = oldCsrf; assertThat(client.send("/auth/logout", "POST", null).statusCode()).isEqualTo(403);
            client.token(); assertThat(client.send("/auth/logout", "POST", null).statusCode()).isEqualTo(200);
            assertThat(sessions.find(id)).isNull(); assertThat(tree(client.send("/auth/me", "GET", null)).path("authenticated").asBoolean()).isFalse();
        }
    }
    @Test void loginUsesUniformCredentialsErrorsAndKeepsGuestHistoryOptional() throws Exception {
        try (var account = client(); var guest = client()) {
            String name = unique(), email = unique() + "@example.test", password = "Another secure password";
            account.token(); assertThat(account.send("/auth/register", "POST", registration(name, email, password)).statusCode()).isEqualTo(200);
            String accountId = tree(account.send("/auth/me", "GET", null)).path("account").path("id").asText();
            guest.token(); assertThat(guest.send("/auth/guest", "POST", null).statusCode()).isEqualTo(200);
            long guestId = TestDatabase.get().jdbc.queryForObject("SELECT id FROM users WHERE guest_token_hash=?", Long.class, GuestIdentity.hash(guest.cookie(GuestIdentity.COOKIE)));
            roomHistory(guestId);
            var known = guest.send("/auth/login", "POST", json.writeValueAsString(java.util.Map.of("email", email, "password", "incorrect")));
            var unknown = guest.send("/auth/login", "POST", json.writeValueAsString(java.util.Map.of("email", unique() + "@example.test", "password", "incorrect")));
            assertThat(known.statusCode()).isEqualTo(401); assertThat(known.body()).isEqualTo(unknown.body());
            String before = guest.cookie("JSESSIONID");
            var login = guest.send("/auth/login", "POST", json.writeValueAsString(java.util.Map.of("email", email, "password", password)));
            assertThat(login.statusCode()).isEqualTo(200); assertThat(tree(login).path("guestHistoryAvailable").asBoolean()).isTrue();
            assertThat(tree(login).path("account").path("id").asText()).isEqualTo(accountId); assertThat(guest.cookie("JSESSIONID")).isNotEqualTo(before);
            assertThat(TestDatabase.get().jdbc.queryForObject("SELECT type FROM users WHERE id=?", String.class, guestId)).isEqualTo("GUEST");
            assertThat(TestDatabase.get().jdbc.queryForObject("SELECT user_id FROM play_runs WHERE user_id=?", Long.class, guestId)).isEqualTo(guestId);
            guest.token(); assertThat(guest.send("/auth/guest-history/merge", "POST", "{\"source\":1}").statusCode()).isEqualTo(400);
            assertThat(guest.send("/auth/guest-history/merge", "POST", null).statusCode()).isEqualTo(200);
            assertThat(guest.send("/auth/guest-history/merge", "POST", null).statusCode()).isEqualTo(200);
            assertThat(TestDatabase.get().jdbc.queryForObject("SELECT merged_into_user_id FROM users WHERE id=?", Long.class, guestId)).isEqualTo(Long.parseLong(accountId));
            assertThat(TestDatabase.get().jdbc.queryForObject("SELECT COUNT(*) FROM play_runs WHERE user_id=?", Integer.class, guestId)).isZero();
        }
    }
    @Test void registeringGuestPromotesTheSameOwnerWithoutMovingSolo() throws Exception {
        try (var client = client()) {
            client.token(); assertThat(client.send("/auth/guest", "POST", null).statusCode()).isEqualTo(200);
            long owner = TestDatabase.get().jdbc.queryForObject("SELECT id FROM users WHERE guest_token_hash=?", Long.class, GuestIdentity.hash(client.cookie(GuestIdentity.COOKIE)));
            roomHistory(owner);
            var result = client.send("/auth/register", "POST", registration(unique(), unique() + "@example.test", "A secure guest password"));
            assertThat(result.statusCode()).isEqualTo(200); assertThat(tree(result).path("account").path("id").asText()).isEqualTo(Long.toString(owner));
            assertThat(TestDatabase.get().jdbc.queryForObject("SELECT type FROM users WHERE id=?", String.class, owner)).isEqualTo("ACCOUNT");
            assertThat(TestDatabase.get().jdbc.queryForObject("SELECT guest_token_hash FROM users WHERE id=?", String.class, owner)).isNull();
            assertThat(TestDatabase.get().jdbc.queryForObject("SELECT user_id FROM play_runs WHERE user_id=?", Long.class, owner)).isEqualTo(owner);
            assertThat(result.headers().allValues("set-cookie").toString()).contains("JK_GUEST=;", "Max-Age=0");
        }
    }
    @Test void validatesUnicodePasswordsStrictJsonAndUniqueCredentials() throws Exception {
        try (var client = client(); var second = client()) {
            client.token(); String email = unique() + "@example.test", name = unique();
            for (String body : java.util.List.of(registration("ab", email, "password123"), registration("invalid name", email, "password123"),
                    registration(name, "invalid-email", "password123"), registration(name, email, "short"), registration(name, email, "é".repeat(37)),
                    "{\"email\":true,\"username\":\"player\",\"password\":\"password123\"}",
                    registration(name, email, "password123").replace("}", ",\"admin\":true}"))) {
                assertThat(client.send("/auth/register", "POST", body).statusCode()).as(body).isEqualTo(400);
            }
            assertThat(client.send("/auth/register", "POST", registration(name, email, "😀".repeat(18))).statusCode()).isEqualTo(200);
            second.token(); var duplicate = second.send("/auth/register", "POST", registration(name, unique() + "@example.test", "password123"));
            assertThat(duplicate.statusCode()).isEqualTo(409); assertThat(duplicate.body()).doesNotContain("SQL", "uq_users", email);
        }
    }
    @Test void membershipBlocksIdentityChangeButAllowsSameAccountReauthentication() throws Exception {
        try (var account = client(); var guest = client()) {
            String name = unique(), email = unique() + "@example.test", password = "A secure membership password";
            account.token(); assertThat(account.send("/auth/register", "POST", registration(name, email, password)).statusCode()).isEqualTo(200);
            long owner = Long.parseLong(tree(account.send("/auth/me", "GET", null)).path("account").path("id").asText());
            guest.token(); assertThat(guest.send("/auth/guest", "POST", null).statusCode()).isEqualTo(200);
            long guestId = TestDatabase.get().jdbc.queryForObject("SELECT id FROM users WHERE guest_token_hash=?", Long.class, GuestIdentity.hash(guest.cookie(GuestIdentity.COOKIE)));
            var member = new jumpking.room.Memberships.Membership("ABCDEF", unique());
            memberships.claim(guestId, member); memberships.claim(owner, member);
            try {
                assertThat(guest.send("/auth/register", "POST", registration(unique(), unique() + "@example.test", password)).statusCode()).isEqualTo(409);
                String body = json.writeValueAsString(java.util.Map.of("email", email, "password", password));
                assertThat(guest.send("/auth/login", "POST", body).statusCode()).isEqualTo(409);
                account.token(); assertThat(account.send("/auth/login", "POST", body).statusCode()).isEqualTo(200);
                assertThat(memberships.find(owner)).isEqualTo(member); assertThat(memberships.find(guestId)).isEqualTo(member);
            } finally { memberships.release(owner, member); memberships.release(guestId, member); }
        }
    }
}
