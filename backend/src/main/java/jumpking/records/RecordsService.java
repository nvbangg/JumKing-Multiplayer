package jumpking.records;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.math.BigInteger;
import java.util.List;
import jumpking.game.SharedData;
import jumpking.network.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RecordsService {
    public record Statistics(BigInteger totalPlayTimeMs, long totalJumps, long totalFalls, long finishes, long matchesPlayed, long wins, Long bestFinishTimeMs) {}
    public record Room(String mode, String roomCode, long durationMs, Integer rank, boolean isWinner) {}
    public record History(String id, String kind, Instant startedAt, Instant endedAt, String status, long playTimeMs, int jumps, int falls,
        double currentProgress, double maxProgress, Long finishTimeMs, Room room) {}
    public record Page(List<History> items, int page, int size, boolean hasNext) {}
    public record Participant(String runId, String nickname, String status, Integer rank, boolean isWinner, double currentProgress,
        double maxProgress, Long finishTimeMs, long playTimeMs, int jumps, int falls) {}
    private static final String HISTORY = """
        WITH owned_rooms AS (
          SELECT p.status,p.play_ticks,p.jumps,p.falls,p.current_progress,p.max_progress,p.finish_ticks,p.`rank`,p.is_winner,
            m.id,m.started_at,m.ended_at,m.mode,m.room_code,m.duration_ticks,
            ROW_NUMBER() OVER (PARTITION BY m.id ORDER BY p.`rank`,p.id) AS choice
          FROM play_runs p JOIN matches m ON m.id=p.match_id WHERE p.user_id=? AND p.kind='ROOM' AND m.ended_at IS NOT NULL
        ), history AS (
          SELECT id,kind,status,started_at,ended_at,play_ticks,jumps,falls,current_progress,max_progress,finish_ticks,
            NULL AS mode,NULL AS room_code,NULL AS duration_ticks,NULL AS `rank`,FALSE AS is_winner
          FROM play_runs WHERE user_id=? AND kind IN ('SOLO','COMMUNITY') AND status<>'ACTIVE'
          UNION ALL
          SELECT id,'ROOM',status,started_at,ended_at,play_ticks,jumps,falls,current_progress,max_progress,finish_ticks,
            mode,room_code,duration_ticks,`rank`,is_winner FROM owned_rooms WHERE choice=1
        ) SELECT * FROM history
        """;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final int tickRate;
    public RecordsService(JdbcTemplate jdbc, ObjectMapper json, SharedData shared) { this.jdbc = jdbc; this.json = json; tickRate = shared.physics().path("tickRate").asInt(); }
    public Statistics statistics(long owner) {
        return jdbc.queryForObject("""
            SELECT COALESCE(SUM(play_ticks),0) AS ticks,COALESCE(SUM(jumps),0) AS jumps,COALESCE(SUM(falls),0) AS falls,
              COALESCE(SUM(status='FINISHED'),0) AS finishes,MIN(finish_ticks) AS best,
              COUNT(DISTINCT CASE WHEN kind='ROOM' THEN match_id END) AS matches,
              COUNT(DISTINCT CASE WHEN kind='ROOM' AND is_winner=TRUE THEN match_id END) AS wins
            FROM play_runs WHERE user_id=?
            """, (row, index) -> new Statistics(row.getBigDecimal("ticks").toBigIntegerExact().multiply(BigInteger.valueOf(1000)).divide(BigInteger.valueOf(tickRate)), row.getLong("jumps"), row.getLong("falls"), row.getLong("finishes"),
                row.getLong("matches"), row.getLong("wins"), optionalMs(row.getObject("best", Long.class))), owner);
    }
    public Page history(long owner, int page, int size) {
        if (page < 0 || page > 100_000 || size < 1 || size > 100) throw new ApiException(400, "VALIDATION", "Invalid page bounds");
        var rows = jdbc.query(HISTORY + " ORDER BY ended_at DESC,kind,id DESC LIMIT ? OFFSET ?", (row, index) -> historyRow(row), owner, owner, size + 1, page * size);
        return new Page(List.copyOf(rows.subList(0, Math.min(rows.size(), size))), page, size, rows.size() > size);
    }
    public ObjectNode detail(long owner, String kind, String id) {
        if (!List.of("SOLO", "COMMUNITY", "ROOM").contains(kind)) throw new ApiException(400, "VALIDATION", "Invalid history kind");
        var records = jdbc.query(HISTORY + " WHERE kind=? AND id=?", (row, index) -> historyRow(row), owner, owner, kind, id);
        if (records.isEmpty()) throw new ApiException(404, "NOT_FOUND", "History not found");
        ObjectNode detail = json.valueToTree(records.getFirst());
        if (kind.equals("ROOM")) {
            jdbc.query("SELECT time_limit_minutes,end_reason FROM matches WHERE id=?", row -> {
                Integer minutes = row.getObject("time_limit_minutes", Integer.class);
                if (minutes == null) detail.putNull("timeLimitMinutes"); else detail.put("timeLimitMinutes", minutes);
                detail.put("endReason", row.getString("end_reason"));
            }, id);
            var participants = jdbc.query("""
                SELECT id,nickname,status,`rank`,is_winner,current_progress,max_progress,finish_ticks,play_ticks,jumps,falls
                FROM play_runs WHERE match_id=? ORDER BY `rank`,id
                """, (row, index) -> new Participant(row.getString("id"), row.getString("nickname"), row.getString("status"), row.getObject("rank", Integer.class),
                    row.getBoolean("is_winner"), row.getDouble("current_progress"), row.getDouble("max_progress"), optionalMs(row.getObject("finish_ticks", Long.class)),
                    ms(row.getLong("play_ticks")), row.getInt("jumps"), row.getInt("falls")), id);
            detail.set("participants", json.valueToTree(participants));
        }
        return detail;
    }
    private History historyRow(ResultSet row) throws SQLException {
        boolean room = row.getString("kind").equals("ROOM");
        return new History(row.getString("id"), row.getString("kind"), row.getTimestamp("started_at").toInstant(), row.getTimestamp("ended_at").toInstant(),
            row.getString("status"), ms(row.getLong("play_ticks")), row.getInt("jumps"), row.getInt("falls"), row.getDouble("current_progress"), row.getDouble("max_progress"),
            optionalMs(row.getObject("finish_ticks", Long.class)), room ? new Room(row.getString("mode"), row.getString("room_code"), ms(row.getLong("duration_ticks")),
                row.getObject("rank", Integer.class), row.getBoolean("is_winner")) : null);
    }
    private long ms(long ticks) { return Math.addExact(Math.multiplyExact(ticks / tickRate, 1000), ticks % tickRate * 1000 / tickRate); }
    private Long optionalMs(Long ticks) { return ticks == null ? null : ms(ticks); }
}
