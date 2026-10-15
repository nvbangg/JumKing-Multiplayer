package jumpking.service;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import jumpking.support.TestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class CrownUpgradeIT {
    @Test void upgradeKeepsExistingRowsAndBackfillsFinishesIncludingMergedGuests() throws Exception {
        var db = TestDatabase.get();
        String prefix = "crown_" + UUID.randomUUID().toString().replace("-", ""), users = prefix + "_users", runs = prefix + "_runs";
        try {
            db.jdbc.execute("CREATE TABLE " + users + " LIKE users");
            db.jdbc.execute("ALTER TABLE " + users + " DROP COLUMN crown_unlocked");
            db.jdbc.execute("CREATE TABLE " + runs + " LIKE play_runs");
            db.jdbc.update("INSERT INTO " + users + "(id,type,username,email,password_hash,created_at) VALUES (1,'ACCOUNT','existing','existing@example.test','preserved',UTC_TIMESTAMP(3))");
            db.jdbc.update("INSERT INTO " + users + "(id,type,merged_into_user_id,created_at) VALUES (2,'GUEST',1,UTC_TIMESTAMP(3))");
            db.jdbc.update("INSERT INTO " + users + "(id,type,guest_token_hash,created_at) VALUES (3,'GUEST',?,UTC_TIMESTAMP(3))", "a".repeat(64));
            String id = UUID.randomUUID().toString();
            db.jdbc.update("INSERT INTO " + runs + "(id,user_id,kind,status,play_ticks,finish_ticks,jumps,started_at,updated_at,ended_at) VALUES (?,2,'SOLO','FINISHED',120,120,4,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))", id);
            String sql = Files.readString(Path.of("../../scripts/upgrade-crown.sql")).replace("USE jumpking;", "")
                .replaceAll("\\busers\\b", users).replaceAll("\\bplay_runs\\b", runs);
            try (var connection = db.dataSource.getConnection()) {
                ScriptUtils.executeSqlScript(connection, new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)));
            }
            assertThat(db.jdbc.queryForList("SELECT crown_unlocked FROM " + users + " ORDER BY id", Boolean.class)).containsExactly(true, true, false);
            assertThat(db.jdbc.queryForObject("SELECT password_hash FROM " + users + " WHERE id=1", String.class)).isEqualTo("preserved");
            assertThat(db.jdbc.queryForObject("SELECT jumps FROM " + runs + " WHERE id=?", Integer.class, id)).isEqualTo(4);
            assertThat(db.jdbc.queryForObject("SELECT COUNT(*) FROM " + runs, Integer.class)).isEqualTo(1);
        } finally {
            db.jdbc.execute("DROP TABLE IF EXISTS " + runs); db.jdbc.execute("DROP TABLE IF EXISTS " + users);
        }
    }
}
