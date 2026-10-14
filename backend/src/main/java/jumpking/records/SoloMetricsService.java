package jumpking.records;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import jumpking.network.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SoloMetricsService {
    public record Ack(long revision, String syncStatus) {}
    private final JdbcTemplate jdbc;
    private final RunOwners owners;
    private final Clock clock;
    public SoloMetricsService(JdbcTemplate jdbc, RunOwners owners, Clock clock) { this.jdbc = jdbc; this.owners = owners; this.clock = clock; }
    public Ack save(long account, String id, SoloMetrics incoming) {
        try { if (!UUID.fromString(id).toString().equals(id)) throw new IllegalArgumentException(); }
        catch (IllegalArgumentException invalid) { throw new ApiException(400, "VALIDATION", "Invalid run ID"); }
        incoming.validate();
        return owners.write(account, owner -> {
            var stored = jdbc.query("SELECT user_id,kind,revision,status,play_ticks,jumps,falls,current_progress,max_progress,peak_reached_ticks,finish_ticks,started_at,ended_at FROM play_runs WHERE id=? FOR UPDATE",
                (row, index) -> {
                    if (row.getLong("user_id") != owner || !row.getString("kind").equals("SOLO")) throw new ApiException(404, "NOT_FOUND", "Run not found");
                    return new SoloMetrics(row.getLong("revision"), SoloMetrics.Status.valueOf(row.getString("status")),
                    row.getLong("play_ticks"), row.getInt("jumps"), row.getInt("falls"), row.getDouble("current_progress"), row.getDouble("max_progress"),
                    row.getLong("peak_reached_ticks"), row.getObject("finish_ticks", Long.class), row.getTimestamp("started_at").toInstant(),
                    row.getTimestamp("ended_at") == null ? null : row.getTimestamp("ended_at").toInstant()); }, id);
            if (!stored.isEmpty()) {
                var old = stored.getFirst();
                if (incoming.revision() < old.revision()) return new Ack(incoming.revision(), "SAVED");
                if (incoming.revision().equals(old.revision())) {
                    if (!incoming.sameContent(old)) throw new ApiException(409, "CONFLICT", "Solo revision already has different metrics");
                    return new Ack(incoming.revision(), "SAVED");
                }
                incoming.validateSuccessor(old);
                jdbc.update("UPDATE play_runs SET revision=?,status=?,play_ticks=?,jumps=?,falls=?,current_progress=?,max_progress=?,peak_reached_ticks=?,finish_ticks=?,ended_at=?,updated_at=? WHERE id=?",
                    incoming.revision(), incoming.status().name(), incoming.playTicks(), incoming.jumps(), incoming.falls(), incoming.currentProgress(), incoming.maxProgress(),
                    incoming.peakReachedTicks(), incoming.finishTicks(), timestamp(incoming.endedAt()), Timestamp.from(clock.instant()), id);
            } else jdbc.update("INSERT INTO play_runs(id,user_id,kind,revision,status,play_ticks,jumps,falls,current_progress,max_progress,peak_reached_ticks,finish_ticks,started_at,updated_at,ended_at) VALUES (?,?,'SOLO',?,?,?,?,?,?,?,?,?,?,?,?)",
                id, owner, incoming.revision(), incoming.status().name(), incoming.playTicks(), incoming.jumps(), incoming.falls(), incoming.currentProgress(), incoming.maxProgress(),
                incoming.peakReachedTicks(), incoming.finishTicks(), timestamp(incoming.startedAt()), Timestamp.from(clock.instant()), timestamp(incoming.endedAt()));
            return new Ack(incoming.revision(), "SAVED");
        });
    }
    private static Timestamp timestamp(java.time.Instant instant) { return instant == null ? null : Timestamp.from(instant); }
}
