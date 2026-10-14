package jumpking.records;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.util.UUID;
import jumpking.game.SharedData;
import jumpking.support.TestDatabase;
import org.junit.jupiter.api.Test;

class RecordsServiceIT {
    @Test void totalsAllKindsOnceAndKeepsHistoryOwnerScopedWithMatchDeduplication() throws Exception {
        var jdbc = TestDatabase.get().jdbc; var json = new ObjectMapper().findAndRegisterModules();
        var records = new RecordsService(jdbc, json, new SharedData(json));
        String name = "totals_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12), match = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO users(type,username,email,password_hash,created_at) VALUES ('ACCOUNT',?,?,'test-hash',UTC_TIMESTAMP(3))", name, name + "@example.test");
        long owner = jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, name);
        try {
            jdbc.update("INSERT INTO matches(id,room_code,mode,started_at,ended_at,duration_ticks,end_reason) VALUES (?,'ABCDEF','RACE',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),180,'FINISH')", match);
            for (String kind : java.util.List.of("SOLO", "COMMUNITY", "ROOM", "ROOM")) {
                boolean room = kind.equals("ROOM"); long ticks = room ? 180 : 61;
                jdbc.update("INSERT INTO play_runs(id,user_id,kind,match_id,nickname,status,play_ticks,jumps,falls,current_progress,max_progress,peak_reached_ticks,finish_ticks,`rank`,is_winner,started_at,ended_at,updated_at) VALUES (?,?,?,?,?,'FINISHED',?,2,1,100,100,?,?,?, ?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
                    UUID.randomUUID().toString(), owner, kind, room ? match : null, room ? "A player" : null, ticks, ticks, ticks, room ? 1 : null, room);
            }
            var total = records.statistics(owner);
            assertThat(total.totalPlayTimeMs()).isEqualTo(BigInteger.valueOf(8033));
            assertThat(total.totalJumps()).isEqualTo(8); assertThat(total.totalFalls()).isEqualTo(4);
            assertThat(total.finishes()).isEqualTo(4); assertThat(total.bestFinishTimeMs()).isEqualTo(1016);
            assertThat(total.matchesPlayed()).isEqualTo(1); assertThat(total.wins()).isEqualTo(1);
            assertThat(records.statistics(Long.MAX_VALUE).totalPlayTimeMs()).isEqualTo(BigInteger.ZERO);
            var first = records.history(owner, 0, 2); var second = records.history(owner, 1, 2);
            assertThat(first.items()).hasSize(2); assertThat(first.hasNext()).isTrue();
            assertThat(second.items()).hasSize(1); assertThat(second.hasNext()).isFalse();
            assertThat(java.util.stream.Stream.concat(first.items().stream(), second.items().stream()).map(RecordsService.History::kind)).containsExactlyInAnyOrder("SOLO", "COMMUNITY", "ROOM");
            var detail = records.detail(owner, "ROOM", match);
            assertThat(detail.path("participants").size()).isEqualTo(2); assertThat(detail.has("snapshot")).isFalse();
            assertThatThrownBy(() -> records.detail(Long.MAX_VALUE, "ROOM", match)).isInstanceOf(jumpking.network.ApiException.class);
        } finally { jdbc.update("DELETE FROM play_runs WHERE user_id=?", owner); jdbc.update("DELETE FROM matches WHERE id=?", match); jdbc.update("DELETE FROM users WHERE id=?", owner); }
    }
}
