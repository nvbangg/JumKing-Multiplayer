package jumpking.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import jumpking.dto.AccountDto;
import jumpking.security.GuestIdentity;
import jumpking.security.IdentityContext;
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
  private final jumpking.service.CrownService crowns;

  public IdentityController(
      IdentityContext identity, GuestIdentity guests, jumpking.service.CrownService crowns) {
    this.identity = identity;
    this.guests = guests;
    this.crowns = crowns;
  }

  @GetMapping("/csrf")
  public Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  @GetMapping("/me")
  public AccountDto.Me me(HttpServletRequest request) {
    return identity.me(request);
  }

  @PostMapping("/guest")
  public AccountDto.Me guest(HttpServletRequest request, HttpServletResponse response) {
    var account = identity.account();
    if (account != null) return new AccountDto.Me(true, false, account, account.crownUnlocked());
    long owner = guests.ensure(request, response);
    return new AccountDto.Me(false, true, null, crowns.unlocked(owner));
  }
}
