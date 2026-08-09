package io.github.pgatzka.skymaster.config;

import io.github.pgatzka.skymaster.generated.hypixel.ApiClient;
import io.github.pgatzka.skymaster.generated.hypixel.api.PlayerDataApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(HypixelProperties.class)
public class HypixelConfiguration {

    @Bean
    public PlayerDataApi playerDataApi(HypixelProperties properties) {
        // Explicit timeouts: an unbounded upstream call in the request path would
        // pin request threads for the whole duration of a Hypixel outage.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        RestClient restClient = ApiClient.buildRestClientBuilder()
                .requestFactory(requestFactory)
                .build();

        ApiClient apiClient = new ApiClient(restClient);
        apiClient.setBasePath(properties.baseUrl());
        apiClient.setApiKey(properties.apiKey());

        return new PlayerDataApi(apiClient);
    }
}
