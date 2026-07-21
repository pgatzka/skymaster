package io.github.pgatzka.skymaster.module;

import io.github.pgatzka.skymaster.IModule;
import io.github.pgatzka.skymaster.SkyMasterClientMod;
import io.github.pgatzka.skymaster.api.pojo.HandshakeIdentity;
import io.github.pgatzka.skymaster.api.service.HandshakeService;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

public class DataCollectionModule implements IModule {

    private final HandshakeService handshakeService = HandshakeService.create();

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
    }

    private void onEndTick(Minecraft minecraft) {
        if (!SkyMasterClientMod.getConfig().getDataCollection().isEnabled()) {
            return;
        }

        HandshakeIdentity identity = new HandshakeIdentity(
                minecraft.getUser().getName(), minecraft.getUser().getProfileId());

        if (handshakeService.isConnected(identity)) {
            // TODO: Collect data
        }
    }
}
