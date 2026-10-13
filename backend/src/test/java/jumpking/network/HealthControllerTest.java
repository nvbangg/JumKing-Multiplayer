package jumpking.network;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthControllerTest {
    @Autowired private TestRestTemplate http;

    @Test
    void servesHealthThroughRealHttp() {
        var response = http.getForEntity("/api/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void unknownApiReturnsNotFound() {
        assertThat(http.getForEntity("/api/missing", String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
