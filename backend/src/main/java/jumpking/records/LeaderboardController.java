package jumpking.records;

import java.util.List;
import java.util.Map;
import jumpking.game.SharedData;
import jumpking.network.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {
    public record Entry(String id, long rank, String username, String status, double currentProgress, double maxProgress,
        long elapsedTimeMs, long peakReachedTimeMs, Long finishTimeMs) {}
    private final JdbcTemplate jdbc;
    private final int tickRate;
    public LeaderboardController(JdbcTemplate jdbc, SharedData shared) { this.jdbc = jdbc; tickRate = shared.physics().path("tickRate").asInt(); }
    @GetMapping Map<String, List<Entry>> leaderboard(@RequestParam(defaultValue = "100") int limit) {
        if (limit < 1 || limit > 100) throw new ApiException(400, "VALIDATION", "Invalid leaderboard limit");
        var rows = jdbc.query("""
            WITH ordered_runs AS (
              SELECT r.id,u.username,r.status,r.current_progress,r.max_progress,r.play_ticks,r.peak_reached_ticks,r.finish_ticks,
                ROW_NUMBER() OVER (PARTITION BY r.user_id ORDER BY (r.status='FINISHED') DESC,
                  CASE WHEN r.status='FINISHED' THEN r.finish_ticks END,
                  CASE WHEN r.status<>'FINISHED' THEN r.max_progress END DESC,
                  CASE WHEN r.status<>'FINISHED' THEN r.peak_reached_ticks END,r.id) AS best
              FROM play_runs r JOIN users u ON u.id=r.user_id WHERE r.kind='COMMUNITY' AND u.type='ACCOUNT'
            ), ranked AS (
              SELECT id,username,status,current_progress,max_progress,play_ticks,peak_reached_ticks,finish_ticks,
                RANK() OVER (ORDER BY (status='FINISHED') DESC,CASE WHEN status='FINISHED' THEN finish_ticks END,
                  CASE WHEN status<>'FINISHED' THEN max_progress END DESC,
                  CASE WHEN status<>'FINISHED' THEN peak_reached_ticks END) AS position
              FROM ordered_runs WHERE best=1
            ) SELECT * FROM ranked ORDER BY position,id LIMIT ?
            """, (row, index) -> {
                Long finish = row.getObject("finish_ticks", Long.class);
                return new Entry(row.getString("id"), row.getLong("position"), row.getString("username"), row.getString("status"), row.getDouble("current_progress"),
                    row.getDouble("max_progress"), ms(row.getLong("play_ticks")), ms(row.getLong("peak_reached_ticks")), finish == null ? null : ms(finish));
            }, limit);
        return Map.of("entries", rows);
    }
    private long ms(long ticks) { return Math.addExact(Math.multiplyExact(ticks / tickRate, 1000), ticks % tickRate * 1000 / tickRate); }
}
