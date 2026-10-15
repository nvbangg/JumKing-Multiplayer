package jumpking.service;

import jumpking.exception.ApiException;
import jumpking.repository.PlayRunRepository;
import jumpking.repository.UserRepository;
import jumpking.room.Memberships;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class GuestHistoryService {
  public static final String CANDIDATE = GuestHistoryService.class.getName() + ".candidate";

  public record Candidate(long source, long target) implements java.io.Serializable {}

  public record Merged(long source, long target) {}

  private final UserRepository users;
  private final PlayRunRepository runs;
  private final TransactionTemplate transactions;
  private final RunOwners owners;
  private final Memberships memberships;
  private final ApplicationEventPublisher events;

  public GuestHistoryService(
      UserRepository users,
      PlayRunRepository runs,
      TransactionTemplate transactions,
      RunOwners owners,
      Memberships memberships,
      ApplicationEventPublisher events) {
    this.users = users;
    this.runs = runs;
    this.transactions = transactions;
    this.owners = owners;
    this.memberships = memberships;
    this.events = events;
  }

  public void merge(long source, long target) {
    if (source == target)
      throw new ApiException(409, "CONFLICT", "Guest history cannot be merged into itself");
    boolean changed =
        memberships.vacantOwners(
            source,
            target,
            () ->
                transactions.execute(
                    status -> {
                      owners.lockPair(source, target);
                      var guest = owners.read(source);
                      var account = owners.read(target);
                      if (!"GUEST".equals(guest.type()) || !"ACCOUNT".equals(account.type()))
                        throw new ApiException(409, "CONFLICT", "Invalid history ownership");
                      if (guest.mergedInto() != null) {
                        if (guest.mergedInto() != target)
                          throw new ApiException(
                              409, "CONFLICT", "This Guest history belongs to another account");
                        return false;
                      }
                      runs.transferRoomRuns(source, target);
                      var user = users.findById(source).orElseThrow();
                      if (user.isCrownUnlocked())
                        users.findById(target).orElseThrow().setCrownUnlocked(true);
                      user.setGuestTokenHash(null);
                      user.setMergedIntoUserId(target);
                      return true;
                    }));
    if (changed) events.publishEvent(new Merged(source, target));
  }
}
