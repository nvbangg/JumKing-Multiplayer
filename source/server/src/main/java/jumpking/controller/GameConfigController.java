package jumpking.controller;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import jumpking.config.FeatureFlags;
import jumpking.game.SharedData;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GameConfigController {
  public record Appearance(int colorSlot, String color) {}

  public record GameConfig(
      JsonNode physics,
      JsonNode rules,
      List<Appearance> appearances,
      Map<String, Boolean> features) {}

  private final SharedData shared;
  private final FeatureFlags flags;

  public GameConfigController(SharedData shared, FeatureFlags flags) {
    this.shared = shared;
    this.flags = flags;
  }

  @GetMapping("/api/map")
  public JsonNode map() {
    return shared.map();
  }

  @GetMapping("/api/game-config")
  public GameConfig config() {
    var rules = shared.rules();
    var colors = rules.path("appearanceColors");
    var appearances =
        IntStream.range(0, colors.size())
            .mapToObj(slot -> new Appearance(slot, colors.get(slot).asText()))
            .toList();
    return new GameConfig(
        shared.physics(), rules, appearances, Map.of("flyEnabled", flags.flyEnabled()));
  }
}
