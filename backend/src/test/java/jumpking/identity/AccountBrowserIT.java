package jumpking.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import jumpking.JumpKingApplication;
import jumpking.support.TestDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

@EnabledIfEnvironmentVariable(named = "E2E_BROWSER_PATH", matches = ".+")
@SpringBootTest(classes = JumpKingApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountBrowserIT {
    private static final int VITE_PORT = freePort();
    @LocalServerPort int port;
    @Autowired PasswordEncoder passwords;
    @Autowired jumpking.records.CommunityRuns communityRuns;
    @Autowired jumpking.game.SharedData shared;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    private record FinishFixture(long owner, String email, String password, String run) {}
    private record AdventureFixture(String email, String password) {}
    private AdventureFixture adventureFixture() throws Exception {
        var jdbc = TestDatabase.get().jdbc;
        String name = "adventure_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12), email = name + "@example.test", password = java.util.UUID.randomUUID().toString();
        jdbc.update("INSERT INTO users(type,username,email,password_hash,created_at) VALUES ('ACCOUNT',?,?,?,UTC_TIMESTAMP(3))", name, email, passwords.encode(password));
        long owner = jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
        var game = new jumpking.game.GameSimulation(json.treeToValue(shared.map(), jumpking.game.GameMap.class), new jumpking.game.Physics(json.convertValue(shared.physics(), new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Number>>() {})), shared.adventure(), false, false, java.util.List.of());
        var physics = game.physics().snapshot(); physics.x = 296; physics.y = -1682; physics.screen = 5;
        game.physics().restore(physics); communityRuns.open(owner, false, null, game.snapshot());
        return new AdventureFixture(email, password);
    }
    private FinishFixture finishFixture() throws Exception {
        var jdbc = TestDatabase.get().jdbc;
        String name = "finish_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12), email = name + "@example.test", password = java.util.UUID.randomUUID().toString();
        jdbc.update("INSERT INTO users(type,username,email,password_hash,created_at) VALUES ('ACCOUNT',?,?,?,UTC_TIMESTAMP(3))", name, email, passwords.encode(password));
        long owner = jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
        var before = jumpking.support.MainBabeRoute.beforeFinish(shared, json);
        var stored = communityRuns.open(owner, false, null, before);
        var physics = before.physics(); double progress = Math.max(0, shared.map().path("progressOriginY").asDouble() - physics.y - shared.physics().path("bodyHeight").asDouble());
        communityRuns.save(new jumpking.records.CommunityRuns.Write(stored.id(), owner, 2, "ACTIVE", before, progress, progress, physics.ticks, null));
        return new FinishFixture(owner, email, password, stored.id());
    }
    private record MergeFixture(long guest, long account, String token, String username, String email, String password, String run) {}
    private MergeFixture mergeFixture() {
        var jdbc = TestDatabase.get().jdbc; String name = "merge_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String email = name + "@example.test", password = java.util.UUID.randomUUID().toString();
        byte[] bytes = new byte[32]; new java.security.SecureRandom().nextBytes(bytes);
        String token = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes), hash = GuestIdentity.hash(token);
        jdbc.update("INSERT INTO users(type,guest_token_hash,created_at) VALUES ('GUEST',?,UTC_TIMESTAMP(3))", hash);
        long guest = jdbc.queryForObject("SELECT id FROM users WHERE guest_token_hash=?", Long.class, hash);
        jdbc.update("INSERT INTO users(type,username,email,password_hash,created_at) VALUES ('ACCOUNT',?,?,?,UTC_TIMESTAMP(3))", name, email, passwords.encode(password));
        long account = jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
        String match = java.util.UUID.randomUUID().toString(), run = java.util.UUID.randomUUID().toString();
        jdbc.update("INSERT INTO matches(id,room_code,mode,started_at,ended_at,end_reason) VALUES (?,'ABCDEF','RACE',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),'HOST_ENDED')", match);
        jdbc.update("INSERT INTO play_runs(id,user_id,kind,match_id,status,nickname,started_at,updated_at,ended_at) VALUES (?,?,'ROOM',?,'UNFINISHED','Guest Player',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))", run, guest, match);
        return new MergeFixture(guest, account, token, name, email, password, run);
    }
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        var db = TestDatabase.get().dataSource;
        properties.add("spring.datasource.url", db::getUrl); properties.add("spring.datasource.username", db::getUsername); properties.add("spring.datasource.password", db::getPassword);
        properties.add("app.allowed-origins", () -> "http://127.0.0.1:" + VITE_PORT);
    }
    private static int freePort() {
        try (var socket = new ServerSocket(0)) { return socket.getLocalPort(); }
        catch (java.io.IOException error) { throw new IllegalStateException(error); }
    }
    @Test void accountFormsUseRealBackendAndMySql() throws Exception {
        var frontend = Path.of("../frontend").toFile();
        var viteBuilder = new ProcessBuilder("node", "node_modules/vite/bin/vite.js", "--host", "127.0.0.1", "--port", Integer.toString(VITE_PORT), "--strictPort").directory(frontend);
        viteBuilder.environment().put("BACKEND_URL", "http://127.0.0.1:" + port);
        Process vite = viteBuilder.redirectErrorStream(true).redirectOutput(Path.of("target/auth-vite-test.log").toFile()).start();
        Process browser = null;
        try (var http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(1)).build()) {
            String origin = "http://127.0.0.1:" + VITE_PORT; boolean ready = false;
            for (int attempt = 0; attempt < 80; attempt++) {
                assertThat(vite.isAlive()).as("Inspect target/auth-vite-test.log").isTrue();
                try {
                    if (http.send(HttpRequest.newBuilder(URI.create(origin)).timeout(Duration.ofSeconds(1)).build(), HttpResponse.BodyHandlers.discarding()).statusCode() == 200) { ready = true; break; }
                } catch (java.io.IOException notReady) { }
                Thread.sleep(250);
            }
            assertThat(ready).isTrue();
            var builder = new ProcessBuilder("node", "node_modules/@playwright/test/cli.js", "test", "e2e/.*-online.spec.ts", "--workers=1").directory(frontend);
            builder.environment().put("E2E_BASE_URL", origin); builder.environment().put("E2E_AUTH_ONLINE", "true");
            builder.environment().put("E2E_OUTPUT_DIR", "../.local/browser-online-tests");
            var merge = mergeFixture();
            builder.environment().put("E2E_MERGE_USERNAME", merge.username); builder.environment().put("E2E_MERGE_EMAIL", merge.email);
            builder.environment().put("E2E_MERGE_PASSWORD", merge.password); builder.environment().put("E2E_MERGE_GUEST", merge.token);
            var finish = finishFixture();
            builder.environment().put("E2E_FINISH_EMAIL", finish.email); builder.environment().put("E2E_FINISH_PASSWORD", finish.password); builder.environment().put("E2E_FINISH_RUN", finish.run);
            var adventure = adventureFixture();
            builder.environment().put("E2E_ADVENTURE_EMAIL", adventure.email); builder.environment().put("E2E_ADVENTURE_PASSWORD", adventure.password);
            browser = builder.redirectErrorStream(true).redirectOutput(Path.of("target/auth-browser-test.log").toFile()).start();
            assertThat(browser.waitFor(300, TimeUnit.SECONDS)).as("Browser test timeout; inspect target/auth-browser-test.log").isTrue();
            assertThat(browser.exitValue()).as("Inspect target/auth-browser-test.log and frontend/test-results").isZero();
            var jdbc = TestDatabase.get().jdbc;
            assertThat(jdbc.queryForObject("SELECT user_id FROM play_runs WHERE id=?", Long.class, merge.run)).isEqualTo(merge.account);
            assertThat(jdbc.queryForObject("SELECT merged_into_user_id FROM users WHERE id=?", Long.class, merge.guest)).isEqualTo(merge.account);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM play_runs WHERE id=? AND status='FINISHED' AND snapshot IS NULL AND finish_ticks=16957", Integer.class, finish.run)).isEqualTo(1);
        } finally {
            if (browser != null && browser.isAlive()) { browser.destroyForcibly(); browser.waitFor(5, TimeUnit.SECONDS); }
            vite.destroy(); if (!vite.waitFor(10, TimeUnit.SECONDS)) { vite.destroyForcibly(); vite.waitFor(5, TimeUnit.SECONDS); }
        }
    }
}
