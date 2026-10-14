package jumpking.identity;

import jumpking.network.ApiException;
import jumpking.records.RunOwners;
import jumpking.room.Memberships;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.context.ApplicationEventPublisher;

@Service
public class GuestHistoryService {
    public static final String CANDIDATE = GuestHistoryService.class.getName() + ".candidate";
    public record Candidate(long source, long target) implements java.io.Serializable {}
    public record Merged(long source, long target) {}
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final RunOwners owners;
    private final Memberships memberships;
    private final ApplicationEventPublisher events;
    public GuestHistoryService(JdbcTemplate jdbc, TransactionTemplate transactions, RunOwners owners, Memberships memberships, ApplicationEventPublisher events) {
        this.jdbc = jdbc; this.transactions = transactions; this.owners = owners; this.memberships = memberships; this.events = events;
    }
    public void merge(long source, long target) {
        if (source == target) throw new ApiException(409, "CONFLICT", "Guest history cannot be merged into itself");
        boolean changed = memberships.vacantOwners(source, target, () -> transactions.execute(status -> {
            owners.lockPair(source, target); var guest = owners.read(source); var account = owners.read(target);
            if (!"GUEST".equals(guest.type()) || !"ACCOUNT".equals(account.type())) throw new ApiException(409, "CONFLICT", "Invalid history ownership");
            if (guest.mergedInto() != null) {
                if (guest.mergedInto() != target) throw new ApiException(409, "CONFLICT", "This Guest history belongs to another account");
                return false;
            }
            jdbc.update("UPDATE play_runs SET user_id=? WHERE user_id=? AND kind='ROOM'", target, source);
            jdbc.update("UPDATE users SET guest_token_hash=NULL,merged_into_user_id=? WHERE id=?", target, source);
            return true;
        }));
        if (changed) events.publishEvent(new Merged(source, target));
    }
}
