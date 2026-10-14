package jumpking.identity;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import jumpking.config.SecuritySettings;
import jumpking.network.ApiException;
import org.springframework.stereotype.Component;

@Component
public class AuthRateLimiter {
    private record Bucket(long window, int count) {}
    private final Map<String, Bucket> buckets = new HashMap<>();
    private final Clock clock;
    private final SecuritySettings settings;
    public AuthRateLimiter(Clock clock, SecuritySettings settings) { this.clock = clock; this.settings = settings; }
    public synchronized void check(String ip, String identity) {
        long window = Math.floorDiv(clock.millis(), 60_000);
        buckets.entrySet().removeIf(entry -> entry.getValue().window != window);
        consume("ip:" + ip, window, settings.authRatePerMinute());
        if (identity != null) consume("identity:" + identity, window, settings.authRatePerMinute());
    }
    public synchronized void solo(String account) {
        long window = Math.floorDiv(clock.millis(), 60_000);
        buckets.entrySet().removeIf(entry -> entry.getValue().window != window);
        consume("solo:" + account, window, settings.soloSyncRatePerMinute());
    }
    private void consume(String key, long window, int limit) {
        Bucket old = buckets.get(key);
        if ((old == null && buckets.size() >= settings.rateBucketLimit()) || (old != null && old.count >= limit)) {
            throw new ApiException(429, "RATE_LIMIT", "Too many requests; try again later");
        }
        buckets.put(key, new Bucket(window, old == null ? 1 : old.count + 1));
    }
}
