package jumpking.config;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class FeatureFlagsTest {
    @Test void flyRequiresLocalAndExplicitFlag() {
        var env = new MockEnvironment().withProperty("FLY_ENABLED", "true");
        assertThat(new FeatureFlags(env).flyEnabled()).isFalse();
        env.setActiveProfiles("local");
        assertThat(new FeatureFlags(env).flyEnabled()).isTrue();
        env.setActiveProfiles("local", "prod");
        assertThat(new FeatureFlags(env).flyEnabled()).isFalse();
        env.setActiveProfiles("prod");
        assertThat(new FeatureFlags(env).flyEnabled()).isFalse();
    }
    @Test void localDefaultIsOff() {
        var env = new MockEnvironment();
        env.setActiveProfiles("local");
        assertThat(new FeatureFlags(env).flyEnabled()).isFalse();
    }
}
