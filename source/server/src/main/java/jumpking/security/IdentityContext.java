package jumpking.security;

import static jumpking.dto.AccountDto.*;

import jakarta.servlet.http.HttpServletRequest;
import jumpking.entity.User;
import jumpking.exception.ApiException;
import jumpking.repository.UserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class IdentityContext {
  private final UserRepository users;
  private final GuestIdentity guests;

  public IdentityContext(UserRepository users, GuestIdentity guests) {
    this.users = users;
    this.guests = guests;
  }

  public Account account() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || authentication instanceof AnonymousAuthenticationToken) return null;
    return users
        .findByTypeAndEmail("ACCOUNT", authentication.getName())
        .map(IdentityContext::accountView)
        .orElse(null);
  }

  public static Account accountView(User user) {
    return new Account(
        user.getId().toString(), user.getUsername(), user.getEmail(), user.isCrownUnlocked());
  }

  public Account requireAccount() {
    Account account = account();
    if (account == null) throw new ApiException(401, "UNAUTHENTICATED", "Sign in to continue");
    return account;
  }

  public Long owner(HttpServletRequest request) {
    Account account = account();
    return account == null ? guests.resolve(request) : Long.valueOf(account.id());
  }

  public Me me(HttpServletRequest request) {
    Account account = account();
    Long guest = account == null ? guests.resolve(request) : null;
    boolean crown =
        account != null
            ? account.crownUnlocked()
            : guest != null && users.findById(guest).map(User::isCrownUnlocked).orElse(false);
    return new Me(account != null, guest != null, account, crown);
  }
}
