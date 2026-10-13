package jumpking.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class IdentityController {
    private final IdentityContext identity;
    private final GuestIdentity guests;
    public IdentityController(IdentityContext identity, GuestIdentity guests) { this.identity = identity; this.guests = guests; }
    @GetMapping("/csrf") public Map<String, String> csrf(CsrfToken token) { return Map.of("token", token.getToken(), "headerName", token.getHeaderName()); }
    @GetMapping("/me") public IdentityContext.Me me(HttpServletRequest request) { return identity.me(request); }
    @PostMapping("/guest") public IdentityContext.Me guest(HttpServletRequest request, HttpServletResponse response) {
        var account = identity.account();
        if (account != null) return new IdentityContext.Me(true, false, account);
        guests.ensure(request, response); return new IdentityContext.Me(false, true, null);
    }
}
