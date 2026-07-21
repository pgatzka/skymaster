package io.github.pgatzka.skymaster.api.service;

import static io.github.pgatzka.skymaster.SkyMasterMod.MOD_ID;
import static io.github.pgatzka.skymaster.SkyMasterMod.log;

import io.github.pgatzka.skymaster.SkyMasterClientMod;
import io.github.pgatzka.skymaster.api.API;
import io.github.pgatzka.skymaster.api.client.HandshakeClient;
import io.github.pgatzka.skymaster.api.pojo.HandshakeIdentity;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.LongSupplier;
import net.fabricmc.loader.api.FabricLoader;
import org.openapitools.client.ApiException;
import org.openapitools.client.model.HandshakeRequest;

public class HandshakeService {

    private static final int HTTP_UPGRADE_REQUIRED = 426;

    private final HandshakeClient handshakeClient;

    private final LongSupplier intervalSeconds;

    private final String modVersion;

    private final Clock clock;

    private boolean handshakesDisabled = false;

    private boolean lastHandshakeSuccessful = false;

    private Instant lastHandshakeAt = Instant.MIN;

    public static HandshakeService create() {
        return new HandshakeService(
                request -> API.getInstance().handshake(request),
                () -> SkyMasterClientMod.getConfig().getDataCollection().getHandshakeIntervalSeconds(),
                resolveModVersion(),
                Clock.systemUTC());
    }

    HandshakeService(HandshakeClient handshakeClient, LongSupplier intervalSeconds, String modVersion, Clock clock) {
        this.handshakeClient = handshakeClient;
        this.intervalSeconds = intervalSeconds;
        this.modVersion = modVersion;
        this.clock = clock;
    }

    public boolean isConnected(HandshakeIdentity identity) {
        if (handshakesDisabled) {
            return false;
        }
        Duration interval = Duration.ofSeconds(intervalSeconds.getAsLong());
        if (Duration.between(lastHandshakeAt, clock.instant()).compareTo(interval) < 0) {
            return lastHandshakeSuccessful;
        }
        lastHandshakeAt = clock.instant();
        lastHandshakeSuccessful = performHandshake(identity, interval);
        return lastHandshakeSuccessful;
    }

    private boolean performHandshake(HandshakeIdentity identity, Duration interval) {
        try {
            handshakeClient.handshake(buildRequest(identity));
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

    private HandshakeRequest buildRequest(HandshakeIdentity identity) {
        HandshakeRequest request = new HandshakeRequest();
        request.setUsername(identity.username());
        request.setUuid(identity.uuid());
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
