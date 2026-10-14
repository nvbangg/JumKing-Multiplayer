package jumpking.records;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import jumpking.game.GameSimulation;
import jumpking.network.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CommunityRuns {
    public record Stored(String id, long owner, long revision, GameSimulation.Snapshot snapshot, double maximum, long peak, Instant startedAt) {}
    public record Write(String id, long owner, long revision, String status, GameSimulation.Snapshot snapshot, double progress, double maximum, long peak, Instant endedAt) {
        public Write { if (endedAt != null) endedAt = endedAt.truncatedTo(java.time.temporal.ChronoUnit.MILLIS); }
    }
    private record Existing(String status, long revision, long ticks, int jumps, int falls, double progress, double maximum, long peak, String snapshot, Instant endedAt) {
        boolean same(Write next) {
            var physics = next.snapshot().physics();
            return status.equals(next.status()) && ticks == physics.ticks && jumps == physics.jumps && falls == physics.falls
                && progress == next.progress() && maximum == next.maximum() && peak == next.peak() && java.util.Objects.equals(endedAt, next.endedAt());
        }
    }
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final RunOwners owners;
    private final Clock clock;
    public CommunityRuns(JdbcTemplate jdbc, ObjectMapper json, RunOwners owners, Clock clock) { this.jdbc = jdbc; this.json = json; this.owners = owners; this.clock = clock; }
    public Stored open(long account, boolean replace, String expected, GameSimulation.Snapshot spawn) {
        return owners.write(account, owner -> {
            if (!owners.read(owner).type().equals("ACCOUNT")) throw new ApiException(403, "ACCOUNT_REQUIRED", "Sign in to play Community Tower");
            var active = jdbc.query("SELECT id,revision,snapshot,max_progress,peak_reached_ticks,started_at FROM play_runs WHERE user_id=? AND kind='COMMUNITY' AND status='ACTIVE' FOR UPDATE",
                (row, index) -> new Stored(row.getString("id"), owner, row.getLong("revision"), decode(row.getString("snapshot")), row.getDouble("max_progress"), row.getLong("peak_reached_ticks"), row.getTimestamp("started_at").toInstant()), owner);
            var old = active.isEmpty() ? null : active.getFirst();
            if (!replace && old != null) return old;
            if (replace && !java.util.Objects.equals(expected, old == null ? null : old.id())) throw new ApiException(409, "CONFLICT", "Community save changed; refresh before starting a new game");
            var now = Timestamp.from(clock.instant());
            if (old != null) jdbc.update("UPDATE play_runs SET status='ABANDONED',snapshot=NULL,revision=revision+1,ended_at=?,updated_at=? WHERE id=?", now, now, old.id());
            String id = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO play_runs(id,user_id,kind,status,snapshot,started_at,updated_at) VALUES (?,?,'COMMUNITY','ACTIVE',?,?,?)", id, owner, encode(spawn), now, now);
            return new Stored(id, owner, 1, spawn, 0, 0, now.toInstant());
        });
    }
    public void save(Write next) {
        var state = next.snapshot().physics();
        if (!java.util.Set.of("ACTIVE", "FINISHED", "ABANDONED").contains(next.status()) || next.revision() < 1
                || !Double.isFinite(next.progress()) || !Double.isFinite(next.maximum()) || next.maximum() < next.progress() || next.progress() < 0
                || state.ticks < 0 || state.jumps < 0 || state.falls < 0 || next.peak() < 0 || next.peak() > state.ticks
                || next.snapshot().world().path("lastTick").asLong() != state.ticks
                || (next.status().equals("ACTIVE") != (next.endedAt() == null))) throw new ApiException(409, "CONFLICT", "Invalid Community metrics");
        owners.write(next.owner(), owner -> {
            var old = jdbc.query("SELECT status,revision,play_ticks,jumps,falls,current_progress,max_progress,peak_reached_ticks,snapshot,ended_at FROM play_runs WHERE id=? AND user_id=? AND kind='COMMUNITY' FOR UPDATE",
                (row, index) -> new Existing(row.getString("status"), row.getLong("revision"), row.getLong("play_ticks"), row.getInt("jumps"), row.getInt("falls"), row.getDouble("current_progress"),
                    row.getDouble("max_progress"), row.getLong("peak_reached_ticks"), row.getString("snapshot"), row.getTimestamp("ended_at") == null ? null : row.getTimestamp("ended_at").toInstant()), next.id(), owner);
            if (old.isEmpty()) throw new ApiException(409, "CONFLICT", "Community run no longer exists");
            var value = old.getFirst(); long revision = value.revision();
            if (next.revision() < revision) return null;
            if (next.revision() == revision) {
                if (!value.same(next) || (next.status().equals("ACTIVE") && !tree(value.snapshot()).equals(tree(encode(next.snapshot()))))) throw new ApiException(409, "CONFLICT", "Community revision conflicts");
                return null;
            }
            if (!value.status().equals("ACTIVE")) throw new ApiException(409, "CONFLICT", "Community run has already ended");
            if (state.ticks < value.ticks() || state.jumps < value.jumps() || state.falls < value.falls() || next.maximum() < value.maximum()
                    || next.peak() < value.peak() || (next.maximum() == value.maximum() && next.peak() != value.peak())) throw new ApiException(409, "CONFLICT", "Community metrics cannot decrease or change an existing peak time");
            boolean active = next.status().equals("ACTIVE");
            jdbc.update("UPDATE play_runs SET revision=?,status=?,play_ticks=?,jumps=?,falls=?,current_progress=?,max_progress=?,peak_reached_ticks=?,finish_ticks=?,snapshot=?,ended_at=?,updated_at=? WHERE id=? AND user_id=?",
                next.revision(), next.status(), state.ticks, state.jumps, state.falls, next.progress(), next.maximum(), next.peak(), next.status().equals("FINISHED") ? state.ticks : null,
                active ? encode(next.snapshot()) : null, next.endedAt() == null ? null : Timestamp.from(next.endedAt()), Timestamp.from(clock.instant()), next.id(), owner);
            return null;
        });
    }
    private GameSimulation.Snapshot decode(String text) { try { return json.readValue(text, GameSimulation.Snapshot.class); } catch (Exception error) { throw new IllegalStateException("Invalid stored Community checkpoint", error); } }
    private com.fasterxml.jackson.databind.JsonNode tree(String text) { try { return json.readTree(text); } catch (Exception error) { throw new IllegalStateException("Invalid checkpoint JSON", error); } }
    private String encode(Object value) { try { return json.writeValueAsString(value); } catch (Exception error) { throw new IllegalStateException("Cannot encode Community checkpoint", error); } }
}
