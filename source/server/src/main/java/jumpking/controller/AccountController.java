package jumpking.controller;

import static jumpking.dto.AccountDto.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import jumpking.dto.AccountCredentials;
import jumpking.dto.AccountDto;
import jumpking.exception.ApiException;
import jumpking.security.GuestIdentity;
import jumpking.security.IdentityContext;
import jumpking.service.AccountService;
import jumpking.service.GuestHistoryService;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AccountController {
    private final AccountService accounts;
    private final IdentityContext identity;
    private final GuestIdentity guests;
    private final HttpSessionSecurityContextRepository contexts;
    private final HttpSessionCsrfTokenRepository csrf;
    private final ServerProperties server;
    public AccountController(AccountService accounts, IdentityContext identity, GuestIdentity guests,
            HttpSessionSecurityContextRepository contexts, HttpSessionCsrfTokenRepository csrf, ServerProperties server) {
        this.accounts = accounts; this.identity = identity; this.guests = guests; this.contexts = contexts; this.csrf = csrf; this.server = server;
    }
    @PostMapping("/register") public AccountDto.Me register(@Valid @RequestBody AccountCredentials.Registration form,
            HttpServletRequest request, HttpServletResponse response) {
        if (identity.account() != null) throw new ApiException(409, "CONFLICT", "Sign out before creating another account");
        var account = accounts.register(form, guests.resolve(request), value -> signIn(value, request, response));
        guests.clear(response);
        return new AccountDto.Me(true, false, account, account.crownUnlocked());
    }
    @PostMapping("/login") public LoginResponse login(@Valid @RequestBody AccountCredentials.Login form,
            HttpServletRequest request, HttpServletResponse response) {
        Long guest = guests.resolve(request); boolean history = accounts.guestHistory(guest);
        var account = accounts.login(form, identity.owner(request), value -> signIn(value, request, response));
        long target = Long.parseLong(account.id()); var session = request.getSession(false);
        if (guest != null) session.setAttribute(GuestHistoryService.CANDIDATE, new GuestHistoryService.Candidate(guest, target));
        else if (!(session.getAttribute(GuestHistoryService.CANDIDATE) instanceof GuestHistoryService.Candidate candidate) || candidate.target() != target) {
            session.removeAttribute(GuestHistoryService.CANDIDATE);
        }
        return new LoginResponse(true, false, account, history);
    }
    @PostMapping("/logout") public Map<String, Boolean> logout(HttpServletRequest request, HttpServletResponse response) {
        var session = request.getSession(false); if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        var cookie = server.getServlet().getSession().getCookie(); String name = cookie.getName() == null ? "JSESSIONID" : cookie.getName();
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, "").path("/").httpOnly(true)
            .sameSite("Lax").secure(Boolean.TRUE.equals(cookie.getSecure())).maxAge(0).build().toString());
        return Map.of("ok", true);
    }
    private void signIn(AccountDto.Account account, HttpServletRequest request, HttpServletResponse response) {
        request.getSession(true); request.changeSessionId();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(account.email(), null, List.of(new SimpleGrantedAuthority("ROLE_ACCOUNT"))));
        SecurityContextHolder.setContext(context); contexts.saveContext(context, request, response);
        csrf.saveToken(null, request, response);
    }
}
