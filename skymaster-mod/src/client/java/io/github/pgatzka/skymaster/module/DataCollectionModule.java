package io.github.pgatzka.skymaster.module;

import static io.github.pgatzka.skymaster.SkyMasterMod.MOD_ID;
import static io.github.pgatzka.skymaster.SkyMasterMod.log;

import io.github.pgatzka.skymaster.API;
import io.github.pgatzka.skymaster.IModule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.openapitools.client.model.HandshakeRequest;

public class DataCollectionModule implements IModule {

    private final API api = new API("localhost", 8080);

    private Boolean handshakeSuccessful;

    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
    }

    private void onEndTick(Minecraft minecraft) {
        if (handshakeSuccessful == null) {
            doHandshake(minecraft);
        } else if (!handshakeSuccessful) {
            return;
        }
        // TODO: Collect data
    }

    private void doHandshake(Minecraft minecraft) {
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
            handshakeSuccessful = true;
        } catch (Exception exception) {
            handshakeSuccessful = false;
            log.error("Handshake failed", exception);
        }
    }
}
