package jumpking.game;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class RoomMapsTest {
  @Test
  void allTenGoalsUseTheApprovedFinishBoundaries() throws Exception {
    var shared = new SharedData(new ObjectMapper());
    assertThat(shared.roomMaps())
        .extracting(SharedData.RoomMap::finish)
        .containsExactly(
            new GameMap.Finish(3, 136, true, 1088),
            new GameMap.Finish(5, 152, true, 1800),
            new GameMap.Finish(9, 8, true, 3384),
            new GameMap.Finish(13, 216, true, 4912),
            new GameMap.Finish(18, 240, true, 6584),
            new GameMap.Finish(21, 272, true, 7648),
            new GameMap.Finish(24, 208, true, 8744),
            new GameMap.Finish(31, 264, true, 11304),
            new GameMap.Finish(35, 192, true, 12816),
            new GameMap.Finish(42, 112, true, 15304));
    assertThat(shared.geometry().finish()).isEqualTo(new GameMap.Finish(42, 336, true, 0));
  }

  @Test
  void eachGoalRequiresItsInclusiveHorizontalAndProgressBoundariesAndGrounded() throws Exception {
    var shared = new SharedData(new ObjectMapper());
    var physics = shared.tuning();
    for (var goal : shared.roomMaps()) {
      var map = shared.roomMap(goal.level());
      var sim = new Simulation(map, physics, false, false);
      var state = sim.snapshot();
      state.screen = goal.finish().screen();
      state.x = goal.finish().minCenterX() - physics.bodyWidth / 2.0;
      state.y = map.progressOriginY() - physics.bodyHeight - goal.finish().minProgress();
      sim.restore(state);
      assertThat(sim.finished()).as("level %s at boundary", goal.level()).isTrue();

      state.x -= 0.25;
      sim.restore(state);
      assertThat(sim.finished()).as("level %s below minCenterX", goal.level()).isFalse();
      state.x += 0.25;

      state.y += 0.25;
      sim.restore(state);
      assertThat(sim.finished()).as("level %s below minProgress", goal.level()).isFalse();
      state.y -= 0.5;
      sim.restore(state);
      assertThat(sim.finished()).as("level %s above the selected floor", goal.level()).isFalse();
      state.y += 0.25;

      state.grounded = false;
      sim.restore(state);
      assertThat(sim.finished()).as("level %s while airborne", goal.level()).isFalse();
    }
  }

  @Test
  void selectedFloorsRejectOtherLandingsThatAlsoMeetTheOldLowerBounds() throws Exception {
    var json = new ObjectMapper();
    var shared = new SharedData(json);
    int[][] alternatives = {{6, 8489}, {9, 14236}, {10, 16957}};
    for (var alternative : alternatives) {
      var goal = shared.roomMap(alternative[0]);
      var sim = new Simulation(goal, shared.tuning(), false, false);
      sim.restore(checkpoint(json, alternative[1]));
      assertThat(sim.snapshot().grounded).isTrue();
      assertThat(sim.snapshot().screen).isEqualTo(goal.finish().screen());
      assertThat(sim.snapshot().x + shared.tuning().bodyWidth / 2.0)
          .isGreaterThanOrEqualTo(goal.finish().minCenterX());
      assertThat(sim.progress()).isGreaterThanOrEqualTo(goal.finish().minProgress());
      assertThat(sim.finished()).as("level %s alternate floor", alternative[0]).isFalse();
    }
  }

  @Test
  void allTenSelectedFloorsAreReachableFromTheNativeSpawnWithoutFly() throws Exception {
    var json = new ObjectMapper();
    var shared = new SharedData(json);
    var physics =
        new Physics(
            json.convertValue(
                shared.physics(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Number>>() {}));
    com.fasterxml.jackson.databind.JsonNode route;
    try (var input = new ClassPathResource("fixtures/main-babe-route.json").getInputStream()) {
      route = json.readTree(input);
    }
    for (var goal : shared.roomMaps()) {
      var map = shared.roomMap(goal.level());
      var game = new GameSimulation(map, physics, shared.adventure(), false, false);
      long branchTick = goal.level() == 6 ? 8489 : goal.level() == 9 ? 14391 : Long.MAX_VALUE;
      java.util.function.Consumer<PlayerInput> step =
          input -> {
            if (!game.finished() && game.physics().snapshot().ticks < branchTick) game.tick(input);
          };
      for (int i = 0; i < 30; i++) step.accept(PlayerInput.NEUTRAL);
      for (var action : route) {
        if (action.has("walk")) {
          for (int i = 0; i < action.path("ticks").asInt(); i++)
            step.accept(
                new PlayerInput(
                    action.path("walk").asInt() < 0, action.path("walk").asInt() > 0, false));
        } else {
          step.accept(PlayerInput.NEUTRAL);
          int charge = action.path("charge").asInt(), direction = action.path("direction").asInt();
          for (int i = 0; i < charge; i++)
            step.accept(
                new PlayerInput(
                    charge == 36 && direction < 0, charge == 36 && direction > 0, true));
          if (charge != 36) step.accept(new PlayerInput(direction < 0, direction > 0, false));
          for (int i = 0; i < 180; i++) {
            step.accept(PlayerInput.NEUTRAL);
            if (game.physics().snapshot().grounded && i > 0) break;
          }
        }
        if (game.finished() || game.physics().snapshot().ticks >= branchTick) break;
      }
      if (goal.level() == 6 || goal.level() == 9) {
        assertThat(game.finished()).as("level %s before the branch", goal.level()).isFalse();
        assertThat(game.physics().snapshot().ticks).isEqualTo(branchTick);
        assertThat(game.physics().snapshot())
            .usingRecursiveComparison()
            .isEqualTo(checkpoint(json, (int) branchTick));
        if (goal.level() == 6) {
          repeat(game, new PlayerInput(false, true, false), 48);
          repeat(game, PlayerInput.NEUTRAL, 1);
          repeat(game, new PlayerInput(false, false, true), 18);
          repeat(game, new PlayerInput(true, false, false), 1);
          repeat(game, PlayerInput.NEUTRAL, 31);
        } else {
          repeat(game, new PlayerInput(true, false, false), 2);
          repeat(game, PlayerInput.NEUTRAL, 1);
          repeat(game, new PlayerInput(false, false, true), 4);
          repeat(game, new PlayerInput(true, false, false), 1);
          repeat(game, PlayerInput.NEUTRAL, 46);
        }
      }
      var state = game.physics().snapshot();
      assertThat(game.finished()).as("level %s", goal.level()).isTrue();
      assertThat(state.screen).isEqualTo(goal.finish().screen());
      assertThat(state.y + physics.bodyHeight)
          .isEqualTo(map.progressOriginY() - goal.finish().minProgress());
      // Nhánh Map 9 rơi từ màn trên xuống đúng nền đã chọn và có một lần splat hợp lệ.
      assertThat(state.falls).isEqualTo(goal.level() == 9 ? 1 : 0);
      assertThat(state.freeFlight).isFalse();
      assertThat(game.adventure().completed()).isEqualTo(goal.level() == 10);
      state.grounded = false;
      game.physics().restore(state);
      assertThat(game.finished()).isFalse();
    }
    assertThatThrownBy(() -> shared.roomMap(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> shared.roomMap(11)).isInstanceOf(IllegalArgumentException.class);
  }

  private void repeat(GameSimulation game, PlayerInput input, int ticks) {
    for (int tick = 0; tick < ticks && !game.finished(); tick++) game.tick(input);
  }

  private PlayerState checkpoint(ObjectMapper json, int ticks) throws Exception {
    try (var input =
        new ClassPathResource("fixtures/main-babe-checkpoints.json").getInputStream()) {
      for (var checkpoint : json.readTree(input)) {
        if (checkpoint.path("ticks").asInt() == ticks)
          return json.treeToValue(checkpoint, PlayerState.class);
      }
    }
    throw new IllegalArgumentException("Missing route checkpoint: " + ticks);
  }
}
