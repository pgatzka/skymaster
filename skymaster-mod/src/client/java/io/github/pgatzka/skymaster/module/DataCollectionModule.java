package io.github.pgatzka.skymaster.module;

import static io.github.pgatzka.skymaster.SkyMasterMod.MOD_ID;
import static io.github.pgatzka.skymaster.SkyMasterMod.log;

import io.github.pgatzka.skymaster.API;
import io.github.pgatzka.skymaster.IModule;
import io.github.pgatzka.skymaster.SkyMasterClientMod;
import java.time.Duration;
import java.time.LocalDateTime;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.openapitools.client.model.HandshakeRequest;

public class DataCollectionModule implements IModule {

    private API api;

    public void onInitializeClient() {
        api = new API(
                SkyMasterClientMod.getConfig().dataCollection.dataCollectionHost.host,
                SkyMasterClientMod.getConfig().dataCollection.dataCollectionHost.port);
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
    }

    private LocalDateTime lastHandshakeAt;

    private Boolean lastHandshakeSuccessful;

    private void onEndTick(Minecraft minecraft) {
        if (!SkyMasterClientMod.getConfig().dataCollection.enabled) {
            return;
        }

        // perform handshake every n seconds
        if (lastHandshakeAt == null
                || lastHandshakeAt.isBefore(LocalDateTime.now()
                        .minus(Duration.ofSeconds(
                                SkyMasterClientMod.getConfig().dataCollection.handshakeIntervalSeconds)))) {
            doHandshake(minecraft);
        }

        if (!lastHandshakeSuccessful) {
            return;
        }

        // TODO: Collect data
    }

    private void doHandshake(Minecraft minecraft) {
        // Set lastHandshakeAt always because we do not want to spam the server if the handshake failed
        lastHandshakeAt = LocalDateTime.now();

        User user = minecraft.getUser();

        try {
            String version = FabricLoader.getInstance()
                    .getModContainer(MOD_ID)
                    .map(container -> container.getMetadata().getVersion().getFriendlyString())
                    .orElse("unknown");

            HandshakeRequest request = new HandshakeRequest();
            request.setUsername(user.getName());
            request.setUuid(user.getProfileId().toString());
            request.setVersion(version);

            api.handshake(request);
            lastHandshakeSuccessful = true;
        } catch (Exception exception) {
            lastHandshakeSuccessful = false;
            log.error("Handshake failed: {}", exception.getMessage());
            log.debug(exception.getMessage(), exception);
        }
    }
}
