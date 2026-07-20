package io.github.pgatzka.skymaster;

import io.github.notenoughupdates.moulconfig.common.IMinecraft;
import io.github.notenoughupdates.moulconfig.managed.ManagedConfig;
import io.github.pgatzka.skymaster.config.SkyMasterConfig;
import io.github.pgatzka.skymaster.module.DataCollectionModule;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

public class SkyMasterClientMod implements ClientModInitializer {

    private final Map<Integer, IModule> modules = new HashMap<>();

    public SkyMasterClientMod() {
        modules.put(0, new DataCollectionModule());
    }

    private static ManagedConfig<SkyMasterConfig> config;

    @Override
    public void onInitializeClient() {
        setupConfig();

        modules.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .forEach(IModule::onInitializeClient);
    }

    private void setupConfig() {
        File configFile = FabricLoader.getInstance()
                .getConfigDir()
                .resolve("SkyMaster/config.json")
                .toFile();

        config = ManagedConfig.create(configFile, SkyMasterConfig.class);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, _) ->
                dispatcher.register(ClientCommands.literal("skymaster").executes(_ -> {
                    Minecraft.getInstance().execute(SkyMasterClientMod::openConfigScreen);
                    return 1;
                })));
    }

    public static SkyMasterConfig getConfig() {
        return config.getInstance();
    }

    private static void openConfigScreen() {
        IMinecraft.INSTANCE.openWrappedScreen(config.getEditor());
    }
}
