package jumpking.records;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import jumpking.support.TestDatabase;

class SchemaIT {
    private static JdbcTemplate jdbc;
    private static DriverManagerDataSource dataSource;

    @BeforeAll static void startDatabase() throws Exception {
        var database = TestDatabase.get(); dataSource = database.dataSource; jdbc = database.jdbc;
    }

    private long account() {
        String token = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO users(type,username,email,password_hash,created_at) VALUES ('ACCOUNT',?,?,?,UTC_TIMESTAMP(3))", token.substring(0, 24), token + "@example.test", "hash");
        return jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, token + "@example.test");
    }

    private void community(long userId) {
        jdbc.update("INSERT INTO play_runs(id,user_id,kind,status,snapshot,started_at,updated_at) VALUES (?,?,'COMMUNITY','ACTIVE',JSON_OBJECT('codecVersion',1),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))", UUID.randomUUID().toString(), userId);
    }

    @Test void validatesActualSchemaAndIndexes() { new SchemaValidator(jdbc).validate(); }

    @Test void packagedBackendStartsWithValidatedSchema() throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        Path log = Path.of("target", "artifact-test.log");
        var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-jar", "target/jumpking-server.jar");
        var environment = builder.environment();
        environment.put("SPRING_DATASOURCE_URL", dataSource.getUrl());
        environment.put("SPRING_DATASOURCE_USERNAME", dataSource.getUsername());
        environment.put("SPRING_DATASOURCE_PASSWORD", dataSource.getPassword());
        environment.put("SERVER_PORT", Integer.toString(port));
        environment.put("SPRING_PROFILES_ACTIVE", "local");
        Process process = builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try (var http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(1)).build()) {
            String origin = "http://127.0.0.1:" + port;
            HttpResponse<String> response = null;
            for (int attempt = 0; attempt < 80; attempt++) {
                assertThat(process.isAlive()).as("Packaged process; inspect target/artifact-test.log on failure").isTrue();
                try {
                    response = http.send(HttpRequest.newBuilder(URI.create(origin + "/api/health")).timeout(Duration.ofSeconds(1)).build(), HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) break;
                } catch (java.io.IOException ignored) { }
                Thread.sleep(250);
            }
            assertThat(response).isNotNull();
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"status\":\"UP\"");
            try (var archive = new ZipFile("target/jumpking-server.jar")) {
                for (String shared : new String[]{"maps/main-babe.json", "physics.json", "rules.json", "adventure.json"}) {
                    var entry = archive.getEntry("BOOT-INF/classes/shared/" + shared);
                    assertThat(entry).as(shared).isNotNull();
                    try (var stream = archive.getInputStream(entry)) {
                        assertThat(stream.readAllBytes()).isEqualTo(Files.readAllBytes(Path.of("../shared", shared)));
                    }
                }
                assertThat(archive.getEntry("BOOT-INF/classes/shared/fixtures/physics-traces.json")).isNull();
                if (archive.getEntry("BOOT-INF/classes/static/index.html") != null) {
                    for (String path : new String[]{"/", "/history", "/community"}) {
                        var page = http.send(HttpRequest.newBuilder(URI.create(origin + path)).build(), HttpResponse.BodyHandlers.ofString());
                        assertThat(page.statusCode()).as(path).isEqualTo(200);
                        assertThat(page.body()).contains("<div id=\"root\"></div>").doesNotContain("SPA routing fixture");
                    }
                    verifyViteProxy(http, origin);
                }
            }
            for (String path : new String[]{"/api/missing", "/assets/missing.png", "/ws/game"}) {
                assertThat(http.send(HttpRequest.newBuilder(URI.create(origin + path)).header("Origin", "http://127.0.0.1:5173").build(), HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(404);
            }
        } finally {
            process.destroy();
            if (!process.waitFor(15, TimeUnit.SECONDS)) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); }
        }
    }

    private void verifyViteProxy(HttpClient http, String backend) throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        var builder = new ProcessBuilder("node", "../frontend/node_modules/vite/bin/vite.js", "--host", "127.0.0.1", "--port", Integer.toString(port), "--strictPort");
        builder.directory(Path.of("../frontend").toFile());
        builder.environment().put("BACKEND_URL", backend);
        var vite = builder.redirectErrorStream(true).redirectOutput(Path.of("target/vite-proxy-test.log").toFile()).start();
        try {
            HttpResponse<String> response = null;
            for (int attempt = 0; attempt < 80; attempt++) {
                assertThat(vite.isAlive()).as("Vite proxy; inspect target/vite-proxy-test.log").isTrue();
                try {
                    response = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/health")).timeout(Duration.ofSeconds(1)).build(), HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) break;
                } catch (java.io.IOException ignored) { }
                Thread.sleep(250);
            }
            assertThat(response).isNotNull();
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"status\":\"UP\"");
        } finally {
            vite.destroy();
            if (!vite.waitFor(10, TimeUnit.SECONDS)) { vite.destroyForcibly(); vite.waitFor(5, TimeUnit.SECONDS); }
        }
    }

    @Test void rejectsMissingConstraints() {
        jdbc.execute("ALTER TABLE matches DROP CHECK ck_matches_ticks");
        try { assertThatThrownBy(() -> new SchemaValidator(jdbc).validate()).hasMessageContaining("CHECK"); }
        finally { jdbc.execute("ALTER TABLE matches ADD CONSTRAINT ck_matches_ticks CHECK (duration_ticks BETWEEN 0 AND 9007199254740991)"); }
    }

    @Test void oneActiveCommunityAndRollback() {
        long owner = account();
        community(owner);
        assertThatThrownBy(() -> community(owner)).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        assertThatThrownBy(() -> tx.execute(status -> {
            jdbc.update("UPDATE play_runs SET status='ABANDONED',snapshot=NULL,ended_at=UTC_TIMESTAMP(3) WHERE user_id=?", owner);
            throw new IllegalStateException("rollback");
        })).hasMessage("rollback");
        assertThat(jdbc.queryForObject("SELECT status FROM play_runs WHERE user_id=?", String.class, owner)).isEqualTo("ACTIVE");
    }

    @Test void rejectsInvalidIdentityAndNullTimeLimit() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO users(type,created_at) VALUES ('ACCOUNT',UTC_TIMESTAMP(3))")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO matches(id,room_code,mode,started_at) VALUES (?,'ABCDEF','TIME_LIMIT',UTC_TIMESTAMP(3))", UUID.randomUUID().toString())).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test void rejectsFinishWithoutTimeAndInvalidSnapshot() {
        long owner = account();
        assertThatThrownBy(() -> jdbc.update("INSERT INTO play_runs(id,user_id,kind,status,started_at,updated_at,ended_at) VALUES (?,?,'SOLO','FINISHED',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))", UUID.randomUUID().toString(), owner)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO play_runs(id,user_id,kind,status,snapshot,started_at,updated_at) VALUES (?,?,'COMMUNITY','ACTIVE',JSON_ARRAY(),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))", UUID.randomUUID().toString(), owner)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        community(owner);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM users WHERE id=?", owner)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
