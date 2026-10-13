package jumpking.records;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SchemaValidator implements ApplicationRunner {
    private static final Map<String, Set<String>> COLUMNS = Map.of(
        "users", Set.of("id", "type", "username", "email", "password_hash", "guest_token_hash", "merged_into_user_id", "created_at"),
        "matches", Set.of("id", "room_code", "mode", "time_limit_minutes", "started_at", "ended_at", "duration_ticks", "end_reason"),
        "play_runs", Set.of("id", "user_id", "kind", "match_id", "status", "play_ticks", "jumps", "falls", "current_progress", "max_progress", "peak_reached_ticks", "finish_ticks", "nickname", "rank", "is_winner", "snapshot", "revision", "active_community_user_id", "started_at", "updated_at", "ended_at")
    );
    private static final Set<String> CHECKS = Set.of("ck_users_identity", "ck_matches_mode", "ck_matches_ticks", "ck_matches_end", "ck_runs_kind", "ck_runs_counters", "ck_runs_end", "ck_runs_finish", "ck_runs_snapshot", "ck_runs_rank", "ck_runs_winner");
    private final JdbcTemplate jdbc;

    public SchemaValidator(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void run(ApplicationArguments args) { validate(); }

    public void validate() {
        var tables = jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE'", String.class);
        require(Set.copyOf(tables).equals(COLUMNS.keySet()), "Expected exactly users, matches, play_runs");
        for (var table : COLUMNS.entrySet()) {
            var rows = jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=?", String.class, table.getKey());
            require(Set.copyOf(rows).equals(table.getValue()), "Column contract differs: " + table.getKey());
        }
        var checks = jdbc.queryForList("SELECT constraint_name FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND constraint_type='CHECK' AND enforced='YES'", String.class);
        require(checks.containsAll(CHECKS), "Required enforced CHECK constraints missing");
        var unique = jdbc.queryForList("SELECT index_name FROM information_schema.statistics WHERE table_schema=DATABASE() AND non_unique=0", String.class);
        require(unique.containsAll(List.of("uq_users_username", "uq_users_email", "uq_users_guest_token", "uq_active_community_user")), "Required unique indexes missing");
        var foreign = jdbc.queryForList("SELECT constraint_name FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND delete_rule IN ('RESTRICT','NO ACTION')", String.class);
        require(foreign.containsAll(List.of("fk_users_merged_into", "fk_runs_user", "fk_runs_match")), "Required restrictive foreign keys missing");
        var engines = jdbc.queryForList("SELECT engine FROM information_schema.tables WHERE table_schema=DATABASE()", String.class);
        require(engines.stream().allMatch("InnoDB"::equals), "InnoDB required");
        var generated = jdbc.queryForMap("SELECT extra,generation_expression FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='play_runs' AND column_name='active_community_user_id'");
        require(generated.get("EXTRA").toString().contains("GENERATED") && generated.get("GENERATION_EXPRESSION").toString().contains("COMMUNITY"), "Community uniqueness must use a generated column");
        var indexes = jdbc.queryForList("SELECT index_name,column_name,seq_in_index FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='play_runs' ORDER BY index_name,seq_in_index");
        var ordered = indexes.stream().collect(Collectors.groupingBy(row -> row.get("INDEX_NAME").toString(), Collectors.mapping(row -> row.get("COLUMN_NAME").toString(), Collectors.toList())));
        require(List.of("user_id", "ended_at", "id").equals(ordered.get("ix_runs_owner_history")), "History index differs");
        require(List.of("match_id").equals(ordered.get("ix_runs_match")) && List.of("kind", "status").equals(ordered.get("ix_runs_leaderboard")), "Records indexes differ");
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalStateException("Database schema is incompatible: " + message + ". Review scripts/schema.sql; no automatic changes were applied.");
    }
}
