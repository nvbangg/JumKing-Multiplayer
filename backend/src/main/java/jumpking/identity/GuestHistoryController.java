package jumpking.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import jumpking.network.ApiException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
public class GuestHistoryController {
    private final GuestHistoryService history;
    private final IdentityContext identity;
    private final GuestIdentity guests;
    public GuestHistoryController(GuestHistoryService history, IdentityContext identity, GuestIdentity guests) {
        this.history = history; this.identity = identity; this.guests = guests;
    }
    @PostMapping("/api/auth/guest-history/merge") public Map<String, Boolean> merge(HttpServletRequest request, HttpServletResponse response,
            @RequestBody(required = false) com.fasterxml.jackson.databind.JsonNode body) {
        if (body != null) throw new ApiException(400, "VALIDATION", "This request does not accept a body");
        long account = Long.parseLong(identity.requireAccount().id()); var session = request.getSession(false);
        Object candidate = session == null ? null : session.getAttribute(GuestHistoryService.CANDIDATE);
        if (!(candidate instanceof GuestHistoryService.Candidate source) || source.target() != account) {
            throw new ApiException(409, "CONFLICT", "No Guest Room history is available to merge");
        }
        history.merge(source.source(), account); guests.clear(response);
        return Map.of("merged", true);
    }
}
