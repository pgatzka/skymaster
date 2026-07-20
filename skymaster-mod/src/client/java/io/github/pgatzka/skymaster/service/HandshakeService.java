package io.github.pgatzka.skymaster.service;

import static io.github.pgatzka.skymaster.SkyMasterMod.MOD_ID;
import static io.github.pgatzka.skymaster.SkyMasterMod.log;

import io.github.pgatzka.skymaster.API;
import io.github.pgatzka.skymaster.SkyMasterClientMod;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.LongSupplier;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.User;
import org.openapitools.client.ApiException;
import org.openapitools.client.model.HandshakeRequest;

public class HandshakeService {

    private static final int HTTP_UPGRADE_REQUIRED = 426;

    private final API api;

    private final LongSupplier intervalSeconds;

    private final String modVersion;

    private final Clock clock;

    private boolean handshakesDisabled = false;

    private boolean lastHandshakeSuccessful = false;

    private Instant lastHandshakeAt = Instant.MIN;

    public static HandshakeService create() {
        return new HandshakeService(
                API.getInstance(),
                () -> SkyMasterClientMod.getConfig().getDataCollection().getHandshakeIntervalSeconds(),
                resolveModVersion(),
                Clock.systemUTC());
    }

    HandshakeService(API api, LongSupplier intervalSeconds, String modVersion, Clock clock) {
        this.api = api;
        this.intervalSeconds = intervalSeconds;
        this.modVersion = modVersion;
        this.clock = clock;
    }

    public boolean isConnected(User user) {
        if (handshakesDisabled) {
            return false;
        }
        Duration interval = Duration.ofSeconds(intervalSeconds.getAsLong());
        if (Duration.between(lastHandshakeAt, clock.instant()).compareTo(interval) < 0) {
            return lastHandshakeSuccessful;
        }
        lastHandshakeAt = clock.instant();
        lastHandshakeSuccessful = performHandshake(user, interval);
        return lastHandshakeSuccessful;
    }

    private boolean performHandshake(User user, Duration interval) {
        try {
            api.handshake(buildRequest(user));
            return true;
        } catch (ApiException exception) {
            if (exception.getCode() == HTTP_UPGRADE_REQUIRED) {
                handshakesDisabled = true;
                log.error(
                        "Handshake failed with a version mismatch, no more handshakes will be performed: {}",
                        exception.getResponseBody());
                return false;
            }
            log.error(
                    "Handshake failed: {}, next attempt at {}", exception.getMessage(), lastHandshakeAt.plus(interval));
            log.debug("Handshake failed", exception);
            return false;
        }
    }

    private HandshakeRequest buildRequest(User user) {
        HandshakeRequest request = new HandshakeRequest();
        request.setUsername(user.getName());
        request.setUuid(user.getProfileId().toString());
        request.setVersion(modVersion);
        return request;
    }

    private static String resolveModVersion() {
        return FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
