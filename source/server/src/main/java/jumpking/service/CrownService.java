package jumpking.service;

import jumpking.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CrownService {
  private final UserRepository users;
  private final RunOwners owners;

  public CrownService(UserRepository users, RunOwners owners) {
    this.users = users;
    this.owners = owners;
  }

  @Transactional(readOnly = true)
  public boolean unlocked(long owner) {
    var user = users.findById(owner).orElseThrow();
    if (user.getMergedIntoUserId() != null)
      user = users.findById(user.getMergedIntoUserId()).orElseThrow();
    return user.isCrownUnlocked();
  }

  // Caller đã khóa owner; crown và kết quả phải commit hoặc rollback cùng nhau.
  @Transactional(propagation = Propagation.MANDATORY)
  public void unlock(long owner) {
    users.findById(owner).orElseThrow().setCrownUnlocked(true);
  }

  public void claimGuest(long guest) {
    owners.write(
        guest,
        owner -> {
          if (owner != guest || !owners.read(owner).type().equals("GUEST"))
            throw new jumpking.exception.ApiException(
                403, "FORBIDDEN", "Only Guest cosmetic progress can be claimed");
          unlock(owner);
          return null;
        });
  }
}
