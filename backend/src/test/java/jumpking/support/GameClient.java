package jumpking.support;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** Client HTTP/WS thật, mỗi instance có cookies riêng. */
public final class GameClient implements AutoCloseable {
    private final ObjectMapper json = new ObjectMapper();
    private final String origin;
    public final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
        .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(3)).build();
    public final List<JsonNode> messages = new CopyOnWriteArrayList<>();
    public WebSocket socket;
    public long owner;
    private String csrf;
    public GameClient(int port) { origin = "http://127.0.0.1:" + port; }
    public JsonNode request(String path, String method, Object body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(origin + "/api" + path)).timeout(Duration.ofSeconds(8));
        if (!method.equals("GET")) request.header("X-CSRF-TOKEN", csrf);
        if (body != null) request.header("Content-Type", "application/json");
        var response = http.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(path + " " + response.body()).isEqualTo(200); return json.readTree(response.body());
    }
    public void register() throws Exception {
        csrf = request("/auth/csrf", "GET", null).path("token").asText();
        String name = "online_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        owner = request("/auth/register", "POST", Map.of("username", name, "email", name + "@example.test", "password", "A secure server integration password")).path("account").path("id").asLong();
        csrf = request("/auth/csrf", "GET", null).path("token").asText();
    }
    public void guest() throws Exception {
        csrf = request("/auth/csrf", "GET", null).path("token").asText(); request("/auth/guest", "POST", null);
        var cookies = (CookieManager) http.cookieHandler().orElseThrow();
        String token = cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("JK_GUEST")).findFirst().orElseThrow().getValue();
        owner = TestDatabase.get().jdbc.queryForObject("SELECT id FROM users WHERE guest_token_hash=?", Long.class, jumpking.identity.GuestIdentity.hash(token));
    }
    public void connect() {
        socket = http.newWebSocketBuilder().header("Origin", "http://allowed.test").connectTimeout(Duration.ofSeconds(3))
            .buildAsync(URI.create(origin.replace("http", "ws") + "/ws/game"), new WebSocket.Listener() {
                final StringBuilder partial = new StringBuilder();
                @Override public void onOpen(WebSocket socket) { socket.request(1); }
                @Override public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
                    partial.append(data);
                    if (last) { try { messages.add(json.readTree(partial.toString())); } catch (Exception error) { throw new IllegalStateException(error); } partial.setLength(0); }
                    socket.request(1); return null;
                }
            }).join();
    }
    public JsonNode command(String type, Map<String, ?> payload) throws Exception {
        String id = UUID.randomUUID().toString();
        socket.sendText(json.writeValueAsString(Map.of("v", 1, "type", type, "requestId", id, "payload", payload)), true).join();
        return await(message -> id.equals(message.path("requestId").asText()));
    }
    public JsonNode ok(String type, Map<String, ?> payload) throws Exception {
        var result = command(type, payload); assertThat(result.path("type").asText()).as(result.toString()).isNotEqualTo("ERROR"); return result.path("payload");
    }
    public void input(Map<String, ?> payload) throws Exception { socket.sendText(json.writeValueAsString(Map.of("v", 1, "type", "PLAYER_INPUT", "payload", payload)), true).join(); }
    public JsonNode await(Predicate<JsonNode> predicate) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        do { var result = messages.stream().filter(predicate).reduce((first, second) -> second); if (result.isPresent()) return result.get(); Thread.sleep(10); } while (System.nanoTime() < deadline);
        throw new AssertionError("Timed out waiting for game message; last messages: " + messages.stream().skip(Math.max(0, messages.size() - 5)).toList());
    }
    public JsonNode snapshot() { return messages.stream().filter(message -> message.path("type").asText().equals("GAME_SNAPSHOT")).reduce((first, second) -> second).orElseThrow().path("payload"); }
    public static void until(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(condition.getAsBoolean()).as("Condition before deadline").isTrue();
    }
    public void disconnect() { if (socket != null) { socket.sendClose(WebSocket.NORMAL_CLOSURE, "").join(); socket = null; } }
    @Override public void close() { disconnect(); http.close(); }
}
