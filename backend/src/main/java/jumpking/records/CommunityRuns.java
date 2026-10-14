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
    public record Write(String id, long owner, long revision, String status, GameSimulation.Snapshot snapshot, double progress, double maximum, long peak, Instant endedAt) {}
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
        owners.write(next.owner(), owner -> {
            var old = jdbc.query("SELECT status,revision,play_ticks,snapshot FROM play_runs WHERE id=? AND user_id=? AND kind='COMMUNITY' FOR UPDATE",
                (row, index) -> new Object[]{row.getString("status"), row.getLong("revision"), row.getLong("play_ticks"), row.getString("snapshot")}, next.id(), owner);
            if (old.isEmpty()) throw new ApiException(409, "CONFLICT", "Community run no longer exists");
            Object[] value = old.getFirst(); long revision = (Long) value[1];
            if (next.revision() < revision) return null;
            if (next.revision() == revision) {
                if (!value[0].equals(next.status()) || (Long) value[2] != next.snapshot().physics().ticks
                        || (next.status().equals("ACTIVE") && !tree((String) value[3]).equals(json.valueToTree(next.snapshot())))) throw new ApiException(409, "CONFLICT", "Community revision conflicts");
                return null;
            }
            if (!value[0].equals("ACTIVE")) throw new ApiException(409, "CONFLICT", "Community run has already ended");
            var state = next.snapshot().physics(); boolean active = next.status().equals("ACTIVE");
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
