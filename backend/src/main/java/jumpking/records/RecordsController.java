package jumpking.records;

import jakarta.servlet.http.HttpServletRequest;
import jumpking.identity.IdentityContext;
import jumpking.network.ApiException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RecordsController {
    public record Profile(String username, RecordsService.Statistics total) {}
    private final IdentityContext identity;
    private final RecordsService records;
    public RecordsController(IdentityContext identity, RecordsService records) { this.identity = identity; this.records = records; }
    private long owner(HttpServletRequest request) {
        Long owner = identity.owner(request); if (owner == null) throw new ApiException(401, "UNAUTHENTICATED", "A player identity is required"); return owner;
    }
    @GetMapping("/api/profile/me") public Profile profile(HttpServletRequest request) {
        long owner = owner(request); var account = identity.account();
        return new Profile(account == null ? null : account.username(), records.statistics(owner));
    }
    @GetMapping("/api/history") public RecordsService.Page history(HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return records.history(owner(request), page, size); }
    @GetMapping("/api/history/{kind}/{id}") public com.fasterxml.jackson.databind.JsonNode detail(HttpServletRequest request,
            @PathVariable String kind, @PathVariable String id) { return records.detail(owner(request), kind, id); }
}
