package jumpking.records;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import jumpking.game.SharedData;
import jumpking.support.TestDatabase;
import org.junit.jupiter.api.Test;

class LeaderboardIT {
    @Test void choosesOneWholeRunPerAccountAndComputesTiesBeforeLimitUsingCommunityOnly() throws Exception {
        var jdbc = TestDatabase.get().jdbc; var owners = new ArrayList<Long>(); var ids = new ArrayList<String>();
        var controller = new LeaderboardController(jdbc, new SharedData(new ObjectMapper()));
        try {
            for (int index = 0; index < 4; index++) {
                String name = "ranking_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                jdbc.update("INSERT INTO users(type,username,email,password_hash,created_at) VALUES ('ACCOUNT',?,?,'unused-test-hash',UTC_TIMESTAMP(3))", name, name + "@example.test");
                owners.add(jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, name));
            }
            for (int index = 0; index < 3; index++) {
                String id = UUID.randomUUID().toString(); ids.add(id); long ticks = index < 2 ? 600 : 1200;
                jdbc.update("INSERT INTO play_runs(id,user_id,kind,status,play_ticks,current_progress,max_progress,peak_reached_ticks,finish_ticks,started_at,ended_at,updated_at) VALUES (?,?,'COMMUNITY','FINISHED',?,15000,15000,?,?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))", id, owners.get(index), ticks, ticks, ticks);
            }
            String bestUnfinished = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO play_runs(id,user_id,kind,status,play_ticks,current_progress,max_progress,peak_reached_ticks,started_at,ended_at,updated_at) VALUES (?,?,'COMMUNITY','ABANDONED',200,300,10000,100,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))", bestUnfinished, owners.get(3));
            jdbc.update("INSERT INTO play_runs(id,user_id,kind,status,play_ticks,current_progress,max_progress,peak_reached_ticks,started_at,ended_at,updated_at) VALUES (?,?,'COMMUNITY','ABANDONED',50,300,9000,1,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))", UUID.randomUUID().toString(), owners.get(3));
            jdbc.update("INSERT INTO play_runs(id,user_id,kind,status,play_ticks,current_progress,max_progress,peak_reached_ticks,finish_ticks,started_at,ended_at,updated_at) VALUES (?,?,'SOLO','FINISHED',1,15000,15000,1,1,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))", UUID.randomUUID().toString(), owners.get(3));
            var entries = controller.leaderboard(100).get("entries");
            assertThat(entries.subList(0, 3)).extracting(LeaderboardController.Entry::rank).containsExactly(1L, 1L, 3L);
            assertThat(entries.subList(0, 3)).extracting(LeaderboardController.Entry::id).containsExactlyInAnyOrderElementsOf(ids);
            var peak = entries.stream().filter(entry -> entry.id().equals(bestUnfinished)).findFirst().orElseThrow();
            assertThat(peak.maxProgress()).isEqualTo(10000); assertThat(peak.peakReachedTimeMs()).isEqualTo(1666); assertThat(peak.finishTimeMs()).isNull();
            assertThat(entries.stream().filter(entry -> entry.username().equals(peak.username())).count()).isEqualTo(1);
            assertThat(controller.leaderboard(2).get("entries")).extracting(LeaderboardController.Entry::rank).containsExactly(1L, 1L);
            assertThatThrownBy(() -> controller.leaderboard(0)).isInstanceOf(jumpking.network.ApiException.class);
        } finally { for (long owner : owners) { jdbc.update("DELETE FROM play_runs WHERE user_id=?", owner); jdbc.update("DELETE FROM users WHERE id=?", owner); } }
    }
}
