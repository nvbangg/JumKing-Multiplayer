package jumpking.game;

import java.io.IOException;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class SharedData {
    private final JsonNode map;
    private final JsonNode physics;
    private final JsonNode rules;
    private final JsonNode adventure;

    public SharedData(ObjectMapper mapper) throws IOException {
        map = read(mapper, "maps/main-babe.json");
        physics = read(mapper, "physics.json");
        rules = read(mapper, "rules.json");
        adventure = read(mapper, "adventure.json");
        if (map.path("width").asInt() != 480 || map.path("screenHeight").asInt() != 360 || map.path("tileSize").asInt() != 8
            || map.path("screens").size() != 43 || map.path("finish").path("screen").asInt() != 42) throw new IOException("Invalid Main Babe geometry");
        for (JsonNode screen : map.path("screens")) {
            if (screen.path("tiles").size() != 45 || !screen.path("wind").isBoolean()) throw new IOException("Invalid Main Babe screen");
            screen.fieldNames().forEachRemaining(key -> { if (!Set.of("tiles", "wind").contains(key)) throw new IllegalArgumentException("Presentation does not belong in simulation data"); });
            for (JsonNode row : screen.path("tiles")) if (!row.isTextual() || row.textValue().length() != 60 || !row.textValue().matches("[ #INabcd]+")) throw new IOException("Invalid terrain");
        }
    }
    private JsonNode read(ObjectMapper mapper, String name) throws IOException {
        try (var stream = new ClassPathResource("shared/" + name).getInputStream()) { return mapper.readTree(stream); }
    }
    public JsonNode map() { return map.deepCopy(); }
    public JsonNode physics() { return physics.deepCopy(); }
    public JsonNode rules() { return rules.deepCopy(); }
    public JsonNode adventure() { return adventure.deepCopy(); }
}
