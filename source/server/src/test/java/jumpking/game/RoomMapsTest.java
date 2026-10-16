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
            new GameMap.Finish(5, 288, true, 2048),
            new GameMap.Finish(9, 248, true, 3480),
            new GameMap.Finish(13, 216, true, 4912),
            new GameMap.Finish(18, 128, true, 6752),
            new GameMap.Finish(24, 88, true, 8912),
            new GameMap.Finish(25, 192, true, 9232),
            new GameMap.Finish(31, 16, true, 11392),
            new GameMap.Finish(35, 256, true, 12816),
            new GameMap.Finish(38, 248, true, 13912),
            new GameMap.Finish(42, 336, true, 0));
    assertThat(shared.roomMap(10).finish()).isEqualTo(shared.geometry().finish());
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
      state.y =
          goal.finish().minProgress() > 0
              ? map.progressOriginY() - physics.bodyHeight - goal.finish().minProgress()
              : -(double) state.screen * map.screenHeight() + map.screenHeight() / 2.0;
      sim.restore(state);
      assertThat(sim.finished()).as("level %s at boundary", goal.level()).isTrue();

      state.x -= 0.25;
      sim.restore(state);
      assertThat(sim.finished()).as("level %s below minCenterX", goal.level()).isFalse();
      state.x += 0.25;

      // Progress bị chặn ở 0 nên Map 10 không có trạng thái dưới minProgress.
      if (goal.finish().minProgress() > 0) {
        state.y += 0.25;
        sim.restore(state);
        assertThat(sim.finished()).as("level %s below minProgress", goal.level()).isFalse();
        state.y -= 0.25;
      }

      state.grounded = false;
      sim.restore(state);
      assertThat(sim.finished()).as("level %s while airborne", goal.level()).isFalse();
    }
  }

  @Test
  void redcrownWoodsFinishesOnTheRightPlatformInsteadOfTheHigherLeftPlatform() throws Exception {
    var json = new ObjectMapper();
    var shared = new SharedData(json);
    var sim = new Simulation(shared.roomMap(1), shared.tuning(), false, false);
    sim.restore(checkpoint(json, 2064));
    assertThat(sim.progress()).isEqualTo(2056);
    assertThat(sim.snapshot().grounded).isTrue();
    assertThat(sim.finished()).isFalse();

    sim.restore(checkpoint(json, 2217));
    assertThat(sim.snapshot().screen).isEqualTo(5);
    assertThat(sim.snapshot().x + shared.tuning().bodyWidth / 2.0).isGreaterThanOrEqualTo(288);
    assertThat(sim.progress()).isEqualTo(2048);
    assertThat(sim.finished()).isTrue();
  }

  @Test
  void greatFrontierAcceptsTheCanonicalLandingAtCenter91Point5() throws Exception {
    var json = new ObjectMapper();
    var shared = new SharedData(json);
    var sim = new Simulation(shared.roomMap(5), shared.tuning(), false, false);
    sim.restore(checkpoint(json, 9469));
    assertThat(sim.snapshot().x + shared.tuning().bodyWidth / 2.0).isEqualTo(91.5);
    assertThat(sim.progress()).isEqualTo(8912);
    assertThat(sim.finished()).isTrue();
  }

  @Test
  void chapelPerilousFinishesOnTheRightPlatformOnly() throws Exception {
    var json = new ObjectMapper();
    var shared = new SharedData(json);
    var sim = new Simulation(shared.roomMap(8), shared.tuning(), false, false);
    var rightLanding = checkpoint(json, 14236);
    var leftLanding = rightLanding.copy();
    leftLanding.x = 200;
    sim.restore(leftLanding);
    assertThat(sim.progress()).isEqualTo(12816);
    assertThat(sim.snapshot().grounded).isTrue();
    assertThat(sim.finished()).isFalse();

    sim.restore(rightLanding);
    assertThat(sim.snapshot().x + shared.tuning().bodyWidth / 2.0).isGreaterThanOrEqualTo(256);
    assertThat(sim.finished()).isTrue();
  }

  @Test
  void allTenGoalsAreReachableFromTheNativeSpawnWithoutFlyOrSplat() throws Exception {
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
      java.util.function.Consumer<PlayerInput> step =
          input -> {
            if (!game.finished()) game.tick(input);
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
        if (game.finished()) break;
      }
      var state = game.physics().snapshot();
      assertThat(game.finished()).as("level %s", goal.level()).isTrue();
      assertThat(state.screen).isEqualTo(goal.finish().screen());
      assertThat(state.falls).isZero();
      assertThat(state.freeFlight).isFalse();
      assertThat(game.adventure().completed()).isEqualTo(goal.level() == 10);
      state.grounded = false;
      game.physics().restore(state);
      assertThat(game.finished()).isFalse();
    }
    assertThatThrownBy(() -> shared.roomMap(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> shared.roomMap(11)).isInstanceOf(IllegalArgumentException.class);
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
