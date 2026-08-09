package io.github.pgatzka.skymaster.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pgatzka.skymaster.generated.hypixel.api.PlayerDataApi;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class HypixelConfigurationTest {

    @Test
    void buildsClientAgainstConfiguredBasePath() {
        HypixelProperties properties =
                new HypixelProperties("test-key", "https://hypixel.test", Duration.ofSeconds(1), Duration.ofSeconds(1));

        PlayerDataApi playerDataApi = new HypixelConfiguration().playerDataApi(properties);

        assertThat(playerDataApi.getApiClient().getBasePath()).isEqualTo("https://hypixel.test");
    }
}
