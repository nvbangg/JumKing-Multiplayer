package jumpking.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import jumpking.exception.ApiException;
import jumpking.security.IdentityContext;
import jumpking.service.CrownService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private final IdentityContext identity;
    private final CrownService crowns;
    public ProfileController(IdentityContext identity, CrownService crowns) { this.identity = identity; this.crowns = crowns; }

    @PostMapping("/crown")
    public Map<String, Boolean> crown(HttpServletRequest request) {
        Long owner = identity.owner(request);
        if (owner == null) throw new ApiException(401, "UNAUTHENTICATED", "Create a Guest profile first");
        crowns.claimGuest(owner);
        return Map.of("crownUnlocked", true);
    }
}
