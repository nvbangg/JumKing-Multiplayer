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

@EnabledIfEnvironmentVariable(named = "E2E_BROWSER_PATH", matches = ".+")
@SpringBootTest(classes = JumpKingApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountBrowserIT {
    private static final int VITE_PORT = freePort();
    @LocalServerPort int port;
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
            var builder = new ProcessBuilder("node", "node_modules/@playwright/test/cli.js", "test", "e2e/account-online.spec.ts", "--workers=1").directory(frontend);
            builder.environment().put("E2E_BASE_URL", origin); builder.environment().put("E2E_AUTH_ONLINE", "true");
            browser = builder.redirectErrorStream(true).redirectOutput(Path.of("target/auth-browser-test.log").toFile()).start();
            assertThat(browser.waitFor(120, TimeUnit.SECONDS)).as("Browser test timeout; inspect target/auth-browser-test.log").isTrue();
            assertThat(browser.exitValue()).as("Inspect target/auth-browser-test.log and frontend/test-results").isZero();
        } finally {
            if (browser != null && browser.isAlive()) { browser.destroyForcibly(); browser.waitFor(5, TimeUnit.SECONDS); }
            vite.destroy(); if (!vite.waitFor(10, TimeUnit.SECONDS)) { vite.destroyForcibly(); vite.waitFor(5, TimeUnit.SECONDS); }
        }
    }
}
