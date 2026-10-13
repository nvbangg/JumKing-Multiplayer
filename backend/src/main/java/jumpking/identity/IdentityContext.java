package jumpking.identity;

import jakarta.servlet.http.HttpServletRequest;
import jumpking.network.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class IdentityContext {
    public record Account(String id, String username, String email) {}
    public record Me(boolean authenticated, boolean guest, Account account) {}
    private final JdbcTemplate jdbc;
    private final GuestIdentity guests;
    public IdentityContext(JdbcTemplate jdbc, GuestIdentity guests) { this.jdbc = jdbc; this.guests = guests; }
    public Account account() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) return null;
        return jdbc.query("SELECT id,username,email FROM users WHERE type='ACCOUNT' AND email=?",
            (row, index) -> new Account(Long.toString(row.getLong("id")), row.getString("username"), row.getString("email")), authentication.getName())
            .stream().findFirst().orElse(null);
    }
    public Account requireAccount() {
        Account account = account(); if (account == null) throw new ApiException(401, "UNAUTHENTICATED", "Sign in to continue"); return account;
    }
    public Long owner(HttpServletRequest request) {
        Account account = account(); return account == null ? guests.resolve(request) : Long.valueOf(account.id);
    }
    public Me me(HttpServletRequest request) {
        Account account = account(); return new Me(account != null, account == null && guests.resolve(request) != null, account);
    }
}
