package io.github.pgatzka.skymaster;

import io.github.pgatzka.skymaster.module.DataCollectionModule;
import net.fabricmc.api.ClientModInitializer;

import java.util.HashMap;
import java.util.Map;

public class SkyMasterClientMod implements ClientModInitializer {

    private final Map<Integer, IModule> modules = new HashMap<>();

    public SkyMasterClientMod() {
        modules.put(0, new DataCollectionModule());
    }

    @Override
    public void onInitializeClient() {
        modules.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).forEach(IModule::onInitializeClient);
    }

}
