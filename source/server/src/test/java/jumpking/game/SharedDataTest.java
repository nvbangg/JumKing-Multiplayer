package jumpking.game;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class SharedDataTest {
    @Test void loadsSharedFromClasspathAndDoesNotExposeMutableDefinitions() throws Exception {
        var data = new SharedData(new ObjectMapper());
        assertThat(data.map().path("screens").size()).isEqualTo(43);
        assertThat(data.physics().path("chargeTicks").asInt()).isEqualTo(36);
        var copy = (com.fasterxml.jackson.databind.node.ObjectNode) data.map();
        copy.put("width", 0);
        assertThat(data.map().path("width").asInt()).isEqualTo(480);
        for (String field : new String[]{"texture", "font", "sound", "intro", "quotes", "lines"})
            assertThat(data.adventure().toString()).doesNotContain("\"" + field + "\"");
    }
}
