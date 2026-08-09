package io.github.pgatzka.skymaster.api.service;

import static io.github.pgatzka.skymaster.SkyMasterMod.MOD_ID;
import static io.github.pgatzka.skymaster.SkyMasterMod.log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pgatzka.skymaster.SkyMasterClientMod;
import io.github.pgatzka.skymaster.api.API;
import io.github.pgatzka.skymaster.api.client.ChatMessageSink;
import io.github.pgatzka.skymaster.api.client.HandshakeClient;
import io.github.pgatzka.skymaster.api.pojo.HandshakeIdentity;
import io.github.pgatzka.skymaster.generated.openapi.ApiException;
import io.github.pgatzka.skymaster.generated.openapi.model.HandshakeRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.LongSupplier;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class HandshakeService {

    private static final int HTTP_UPGRADE_REQUIRED = 426;

    private static final String CODE_NOT_ONLINE = "not-online";

    private static final String NOT_ONLINE_CHAT_MESSAGE =
            "[SkyMaster] Hypixel reports you as offline, so no data is being collected. If you are"
                    + " playing on Hypixel, your session is most likely hidden in Hypixel's API"
                    + " settings.";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HandshakeClient handshakeClient;

    private final ChatMessageSink chatMessageSink;

    private final LongSupplier intervalSeconds;

    private final String modVersion;

    private final Clock clock;

    private boolean handshakesDisabled = false;

    private boolean lastHandshakeSuccessful = false;

    private boolean notOnlineNotified = false;

    private Instant lastHandshakeAt = Instant.MIN;

    public static HandshakeService create() {
        return new HandshakeService(
                request -> API.getInstance().handshake(request),
                HandshakeService::sendChatMessage,
                () -> SkyMasterClientMod.getConfig().getDataCollection().getHandshakeIntervalSeconds(),
                resolveModVersion(),
                Clock.systemUTC());
    }

    HandshakeService(
            HandshakeClient handshakeClient,
            ChatMessageSink chatMessageSink,
            LongSupplier intervalSeconds,
            String modVersion,
            Clock clock) {
        this.handshakeClient = handshakeClient;
        this.chatMessageSink = chatMessageSink;
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
            notOnlineNotified = false;
            return true;
        } catch (ApiException exception) {
            if (exception.getCode() == HTTP_UPGRADE_REQUIRED) {
                handshakesDisabled = true;
                log.error(
                        "Handshake failed with a version mismatch, no more handshakes will be performed: {}",
                        exception.getResponseBody());
                return false;
            }
            trackNotOnlineEpisode(extractCode(exception.getResponseBody()));
            log.error(
                    "Handshake failed: {}, next attempt at {}", exception.getMessage(), lastHandshakeAt.plus(interval));
            log.debug("Handshake failed", exception);
            return false;
        }
    }

    /**
     * Sends the not-online chat message only on the transition into that state: handshakes repeat
     * every interval and would otherwise spam chat. A rejection with a different code ends the
     * episode; a failure without a code (e.g. the server was unreachable) keeps the current state,
     * since it says nothing about whether the player's Hypixel session changed.
     */
    private void trackNotOnlineEpisode(String code) {
        if (CODE_NOT_ONLINE.equals(code)) {
            if (!notOnlineNotified) {
                notOnlineNotified = true;
                chatMessageSink.sendChatMessage(NOT_ONLINE_CHAT_MESSAGE);
            }
        } else if (code != null) {
            notOnlineNotified = false;
        }
    }

    private static String extractCode(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonNode code = OBJECT_MAPPER.readTree(responseBody).path("code");
            return code.isTextual() ? code.asText() : null;
        } catch (Exception exception) {
            log.debug("Failed to parse handshake failure response body", exception);
            return null;
        }
    }

    private HandshakeRequest buildRequest(HandshakeIdentity identity) {
        HandshakeRequest request = new HandshakeRequest();
        request.setUsername(identity.username());
        request.setUuid(identity.uuid());
        request.setVersion(modVersion);
        return request;
    }

    private static void sendChatMessage(String message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.gui.getChat().addClientSystemMessage(Component.literal(message));
        }
    }

    private static String resolveModVersion() {
        return FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
