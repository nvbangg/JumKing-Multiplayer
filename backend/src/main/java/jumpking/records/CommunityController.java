package jumpking.records;

import java.util.LinkedHashMap;
import java.util.Map;
import jumpking.game.SharedData;
import jumpking.identity.IdentityContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/community")
public class CommunityController {
    private final JdbcTemplate jdbc;
    private final IdentityContext identities;
    private final int tickRate;
    public CommunityController(JdbcTemplate jdbc, IdentityContext identities, SharedData shared) { this.jdbc = jdbc; this.identities = identities; tickRate = shared.physics().path("tickRate").asInt(); }
    @GetMapping("/current") Map<String, Object> current() {
        var account = identities.requireAccount();
        var rows = jdbc.query("SELECT id,status,current_progress,max_progress,play_ticks,peak_reached_ticks,updated_at FROM play_runs WHERE user_id=? AND kind='COMMUNITY' AND status='ACTIVE'",
            (row, index) -> {
                var result = new LinkedHashMap<String, Object>(); var summary = new LinkedHashMap<String, Object>();
                summary.put("id", row.getString("id")); summary.put("status", row.getString("status")); summary.put("currentProgress", row.getDouble("current_progress")); summary.put("maxProgress", row.getDouble("max_progress"));
                summary.put("elapsedTimeMs", row.getLong("play_ticks") * 1000 / tickRate); summary.put("peakReachedTimeMs", row.getLong("peak_reached_ticks") * 1000 / tickRate); summary.put("finishTimeMs", null);
                result.put("run", summary); result.put("updatedAt", row.getTimestamp("updated_at").toInstant()); result.put("saveStatus", "SAVED"); return result;
            }, Long.valueOf(account.id()));
        if (!rows.isEmpty()) return rows.getFirst();
        var empty = new LinkedHashMap<String, Object>(); empty.put("run", null); empty.put("updatedAt", null); empty.put("saveStatus", "SAVED"); return empty;
    }
}
