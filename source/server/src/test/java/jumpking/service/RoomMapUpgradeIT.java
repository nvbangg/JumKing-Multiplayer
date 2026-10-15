package jumpking.service;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import jumpking.support.TestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class RoomMapUpgradeIT {
    @Test void existingMatchesDefaultToFullMapAndNewBoundsAreEnforced() throws Exception {
        var db = TestDatabase.get(); String table = "map_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try {
            db.jdbc.execute("CREATE TABLE " + table + " LIKE matches");
            for (var name : db.jdbc.queryForList("SELECT CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND CONSTRAINT_TYPE='CHECK'", String.class, table)) db.jdbc.execute("ALTER TABLE " + table + " DROP CHECK `" + name + "`");
            db.jdbc.execute("ALTER TABLE " + table + " DROP COLUMN map_level");
            String id = UUID.randomUUID().toString();
            db.jdbc.update("INSERT INTO " + table + "(id,room_code,mode,started_at,duration_ticks) VALUES (?,'ABCDEF','RACE',UTC_TIMESTAMP(3),123)", id);
            String sql = Files.readString(Path.of("../../scripts/upgrade-room-maps.sql")).replace("USE jumpking;", "")
                .replaceAll("\\bmatches\\b", table).replace("ck_matches_map_level", "ck_" + table);
            try (var connection = db.dataSource.getConnection()) { ScriptUtils.executeSqlScript(connection, new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8))); }
            assertThat(db.jdbc.queryForObject("SELECT map_level FROM " + table + " WHERE id=?", Integer.class, id)).isEqualTo(10);
            assertThat(db.jdbc.queryForObject("SELECT duration_ticks FROM " + table + " WHERE id=?", Long.class, id)).isEqualTo(123);
            assertThatThrownBy(() -> db.jdbc.update("UPDATE " + table + " SET map_level=0")).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(() -> db.jdbc.update("UPDATE " + table + " SET map_level=11")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        } finally { db.jdbc.execute("DROP TABLE IF EXISTS " + table); }
    }
}
