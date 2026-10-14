package jumpking.identity;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.function.Consumer;
import jumpking.network.ApiException;
import jumpking.room.Memberships;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AccountService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final AuthenticationManager authentication;
    private final TransactionTemplate transactions;
    private final Memberships memberships;
    private final Clock clock;
    public AccountService(JdbcTemplate jdbc, PasswordEncoder passwords, AuthenticationManager authentication,
            TransactionTemplate transactions, Memberships memberships, Clock clock) {
        this.jdbc = jdbc; this.passwords = passwords; this.authentication = authentication;
        this.transactions = transactions; this.memberships = memberships; this.clock = clock;
    }
    public IdentityContext.Account register(AccountCredentials.Registration form, Long guest, Consumer<IdentityContext.Account> authenticated) {
        form.validate(); String encoded = passwords.encode(form.password());
        return memberships.changingIdentity(guest, null, () -> {
            try {
                var account = transactions.execute(status -> {
                    if (guest == null) jdbc.update("INSERT INTO users(type,username,email,password_hash,created_at) VALUES ('ACCOUNT',?,?,?,?)",
                        form.username(), form.email(), encoded, Timestamp.from(clock.instant()));
                    else {
                        var type = jdbc.queryForObject("SELECT type FROM users WHERE id=? FOR UPDATE", String.class, guest);
                        if (!"GUEST".equals(type) || jdbc.update("UPDATE users SET type='ACCOUNT',username=?,email=?,password_hash=?,guest_token_hash=NULL WHERE id=? AND merged_into_user_id IS NULL",
                                form.username(), form.email(), encoded, guest) != 1) throw new ApiException(409, "CONFLICT", "This Guest identity has changed");
                    }
                    return account(form.email());
                });
                authenticated.accept(account); return account;
            } catch (DuplicateKeyException duplicate) { throw new ApiException(409, "CONFLICT", "Username or email is unavailable"); }
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
        return guest != null && jdbc.queryForObject("SELECT COUNT(*) FROM play_runs WHERE user_id=? AND kind='ROOM'", Long.class, guest) > 0;
    }
    private IdentityContext.Account account(String email) {
        return jdbc.queryForObject("SELECT id,username,email FROM users WHERE type='ACCOUNT' AND email=?",
            (row, index) -> new IdentityContext.Account(Long.toString(row.getLong("id")), row.getString("username"), row.getString("email")), email);
    }
    private static ApiException invalidCredentials() { return new ApiException(401, "INVALID_CREDENTIALS", "Email or password is incorrect"); }
}
