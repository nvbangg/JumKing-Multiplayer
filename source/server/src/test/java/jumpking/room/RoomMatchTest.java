package jumpking.room;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import jumpking.config.NetworkSettings;
import jumpking.game.*;
import jumpking.support.MainBabeRoute;
import org.junit.jupiter.api.Test;

class RoomMatchTest {
    private final ObjectMapper json = new ObjectMapper();
    private final SharedData shared;
    RoomMatchTest() throws Exception { shared = new SharedData(json); }
    private RoomMatch match() {
        var limits = new NetworkSettings(30, 2, 32, 256, 64, 20, 16_384, 120, 120, 64, 240, 4, 8, 5000, 30000, 500, 60, 200, 50, 1000);
        return new RoomMatch("ABCDEF", RoomSettings.defaults(), List.of(new RoomMatch.Spec("first", 1, "First", 0, 1), new RoomMatch.Spec("second", 2, "Second", 1, 1)), shared, json, limits, false);
    }
    @Test void roomClockAndDisconnectedPhysicsAreAuthoritativeWhileDnfMetricsFreeze() {
        var match = match(); var start = Instant.parse("2026-01-01T00:00:00Z"); match.start(start);
        for (int index = 0; index < 4; index++) match.tick(1_000_000_000L, start);
        match.abandon("second", start.plusSeconds(1));
        for (int index = 0; index < 3; index++) match.tick(2_000_000_000L, start.plusSeconds(1));
        assertThat(match.ticks).isEqualTo(7); assertThat(match.players.get("second").game.physics().snapshot().ticks).isEqualTo(4);
        match.finish("HOST_ENDED", start.plusSeconds(2));
        assertThat(match.players.get("first").status).isEqualTo("UNFINISHED"); assertThat(match.players.values()).noneMatch(player -> player.winner);
        var write = match.write(1); assertThat(write.duration()).isEqualTo(7); assertThat(write.runs().get(1).status()).isEqualTo("DNF"); assertThat(write.runs().get(1).endedAt()).isEqualTo(start.plusSeconds(1));
        match.tick(3_000_000_000L, start.plusSeconds(3)); assertThat(match.ticks).isEqualTo(7);
    }
    @Test void allPlayersOfTheFinishingTickAreProcessedBeforeResultsAndTies() throws Exception {
        var match = match(); var before = MainBabeRoute.beforeFinish(shared, json); var start = Instant.parse("2026-01-01T00:00:00Z");
        match.start(start); match.ticks = before.physics().ticks;
        for (var player : match.players.values()) { player.game.restore(before); player.maximum = player.game.physics().progress(); player.peak = before.physics().ticks; }
        match.tick(1_000_000_000L, start.plusSeconds(283));
        assertThat(match.ticks).isEqualTo(16957); assertThat(match.reason).isEqualTo("FINISH");
        assertThat(match.players.values()).allMatch(player -> player.winner && player.rank == 1 && player.finish == 16957);
        assertThat(match.write(1).runs()).allMatch(run -> run.status().equals("FINISHED") && run.ticks() == 16957 && run.rank() == 1);
    }
}
