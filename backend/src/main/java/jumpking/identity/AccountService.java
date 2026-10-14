package jumpking.identity;

import java.time.Clock;
import java.util.function.Consumer;
import jumpking.network.ApiException;
import jumpking.room.Memberships;
import org.springframework.dao.DataIntegrityViolationException;
import jumpking.records.PlayRunRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AccountService {
    private final UserRepository users;
    private final PlayRunRepository runs;
    private final PasswordEncoder passwords;
    private final AuthenticationManager authentication;
    private final TransactionTemplate transactions;
    private final Memberships memberships;
    private final Clock clock;
    public AccountService(UserRepository users, PlayRunRepository runs, PasswordEncoder passwords, AuthenticationManager authentication,
            TransactionTemplate transactions, Memberships memberships, Clock clock) {
        this.users = users; this.runs = runs; this.passwords = passwords; this.authentication = authentication;
        this.transactions = transactions; this.memberships = memberships; this.clock = clock;
    }
    public IdentityContext.Account register(AccountCredentials.Registration form, Long guest, Consumer<IdentityContext.Account> authenticated) {
        form.validate(); String encoded = passwords.encode(form.password());
        return memberships.changingIdentity(guest, null, () -> {
            try {
                var account = transactions.execute(status -> {
                    if (guest == null) users.saveAndFlush(User.account(form.username(), form.email(), encoded, clock.instant()));
                    else {
                        var user = users.lockById(guest).orElseThrow(() -> new ApiException(409, "CONFLICT", "This Guest identity has changed"));
                        if (!"GUEST".equals(user.getType()) || user.getMergedIntoUserId() != null) throw new ApiException(409, "CONFLICT", "This Guest identity has changed");
                        user.setType("ACCOUNT"); user.setUsername(form.username()); user.setEmail(form.email());
                        user.setPasswordHash(encoded); user.setGuestTokenHash(null); users.saveAndFlush(user);
                    }
                    return account(form.email());
                });
                authenticated.accept(account); return account;
            } catch (DataIntegrityViolationException duplicate) { throw new ApiException(409, "CONFLICT", "Username or email is unavailable"); }
        });
    }
    public IdentityContext.Account login(AccountCredentials.Login form, Long current, Consumer<IdentityContext.Account> authenticated) {
        if (!AccountCredentials.passwordWithinBcrypt(form.password())) throw invalidCredentials();
        try { authentication.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(form.email(), form.password())); }
        catch (AuthenticationException invalid) { throw invalidCredentials(); }
        var account = account(form.email());
        return memberships.changingIdentity(current, Long.valueOf(account.id()), () -> { authenticated.accept(account); return account; });
    }
    public boolean guestHistory(Long guest) {
        return guest != null && runs.existsByUserIdAndKind(guest, "ROOM");
    }
    private IdentityContext.Account account(String email) {
        return users.findByTypeAndEmail("ACCOUNT", email).map(IdentityContext::accountView).orElseThrow(AccountService::invalidCredentials);
    }
    private static ApiException invalidCredentials() { return new ApiException(401, "INVALID_CREDENTIALS", "Email or password is incorrect"); }
}
