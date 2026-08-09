package io.github.pgatzka.skymaster.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "skymaster.hypixel")
public record HypixelProperties(String apiKey, String baseUrl, Duration connectTimeout, Duration readTimeout) {}
