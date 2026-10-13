package jumpking.identity;

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
}
