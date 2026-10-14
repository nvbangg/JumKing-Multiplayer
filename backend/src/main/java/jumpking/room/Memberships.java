package jumpking.room;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import jumpking.network.ApiException;
import org.springframework.stereotype.Component;

@Component
public class Memberships {
    public record Membership(String roomId, String playerId) {}
    private final Map<Long, Membership> members = new HashMap<>();
    public synchronized Membership find(long owner) { return members.get(owner); }
    public synchronized void claim(long owner, Membership membership) {
        var existing = members.putIfAbsent(owner, membership);
        if (existing != null && !existing.equals(membership)) throw new ApiException(409, "CONFLICT", "Leave your current room first");
    }
    public synchronized void release(long owner, Membership expected) { members.remove(owner, expected); }
    public synchronized <T> T changingIdentity(Long current, Long target, Supplier<T> action) {
        if (current != null && !current.equals(target) && members.containsKey(current)) {
            throw new ApiException(409, "CONFLICT", "Leave your current room before changing identity");
        }
        return action.get();
    }
}
