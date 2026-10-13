package jumpking.identity;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class GuestIdentity {
    public static final String COOKIE = "JK_GUEST";
    private static final Duration LIFETIME = Duration.ofDays(365);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final boolean secure;
    public GuestIdentity(JdbcTemplate jdbc, Clock clock, ServerProperties server) {
        this.jdbc = jdbc; this.clock = clock; secure = Boolean.TRUE.equals(server.getServlet().getSession().getCookie().getSecure());
    }
    public static String token(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies(); String result = null;
        if (cookies != null) for (Cookie cookie : cookies) if (COOKIE.equals(cookie.getName())) {
            if (result != null) return null;
            result = cookie.getValue();
        }
        return result != null && result.matches("[A-Za-z0-9_-]{43}") ? result : null;
    }
    public static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public Long resolve(HttpServletRequest request) {
        String token = token(request); if (token == null) return null;
        var matches = jdbc.query("SELECT id,created_at FROM users WHERE type='GUEST' AND guest_token_hash=? AND merged_into_user_id IS NULL",
            (row, index) -> new Guest(row.getLong("id"), row.getTimestamp("created_at").toInstant()), hash(token));
        if (matches.isEmpty() || !clock.instant().isBefore(matches.getFirst().created.plus(LIFETIME))) return null;
        return matches.getFirst().id;
    }
    public long ensure(HttpServletRequest request, HttpServletResponse response) {
        Long existing = resolve(request); if (existing != null) return existing;
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = hash(token);
        jdbc.update("INSERT INTO users(type,guest_token_hash,created_at) VALUES ('GUEST',?,?)", hash, java.sql.Timestamp.from(clock.instant()));
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE, token).httpOnly(true).secure(secure)
            .sameSite("Lax").path("/").maxAge(LIFETIME).build().toString());
        return jdbc.queryForObject("SELECT id FROM users WHERE guest_token_hash=?", Long.class, hash);
    }
    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE, "").httpOnly(true).secure(secure)
            .sameSite("Lax").path("/").maxAge(0).build().toString());
    }
    private record Guest(long id, Instant created) {}
}
