package io.github.pgatzka.skymaster;

import static io.github.pgatzka.skymaster.SkyMasterMod.log;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import org.openapitools.client.model.Collector;
import org.openapitools.client.model.ItemStackData;
import org.openapitools.client.model.ScreenDataRequest;

public class SkyMasterClientMod implements ClientModInitializer {

    private final List<ScreenDataRequest> requests = new ArrayList<>();

    private final API api;

    public SkyMasterClientMod() {
        this.api = new API("localhost");
    }

    @Override
    public void onInitializeClient() {
        try {
            log.info("Server ping answered with: {}", api.getPing().getMessage());
        } catch (Exception e) {
            log.error("Could not ping server, skipping event registration", e);
            return;
        }

        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
    }

    private boolean isInSkyBlock(Minecraft minecraft) {
        if (minecraft.level != null) {
            Objective objective = minecraft.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
            if (objective != null) {
                Component displayName = objective.getDisplayName();
                return displayName.getString().trim().toLowerCase().startsWith("skyblock");
            }
        }
        return false;
    }

    private Screen lastOpenScreen = null;

    private void onEndTick(Minecraft minecraft) {
        pushScreenData();

        if (isInSkyBlock(minecraft)) {
            if (!(minecraft.screen instanceof AbstractContainerScreen<?> screen)) {
                return;
            }

            if (lastOpenScreen == screen) {
                return;
            }
            lastOpenScreen = screen;

            User user = minecraft.getUser();

            Collector collector = new Collector();
            collector.setId(user.getProfileId().toString());
            collector.setName(user.getName());

            String title = screen.getTitle().getString();

            ScreenDataRequest request = new ScreenDataRequest();
            request.setTitle(title);
            request.setCollectedAt(OffsetDateTime.now());
            request.setCollector(collector);

            List<ItemStackData> itemStackData = parseMenuSlots(screen.getMenu());
            if (itemStackData == null) {
                return;
            }
            request.setItemStackDataList(itemStackData);

            requests.add(request);
            log.info("Added screen {} to push queue", title);
        }
    }

    private LocalDateTime lastPush;

    private void pushScreenData() {
        if (lastPush != null && LocalDateTime.now().isBefore(lastPush.plusSeconds(1))) {
            log.debug("Skipping push, rate limit active (last push at {})", lastPush);
            return;
        }
        if (requests.isEmpty()) {
            log.debug("Skipping push, no requests queued");
            return;
        }

        lastPush = LocalDateTime.now();
        try {
            log.info("Pushing screen data ({} request(s) remaining in queue)", requests.size() - 1);
            api.pushScreenData(requests.removeFirst());
            log.info("Successfully pushed screen data");
        } catch (Exception e) {
            log.error("Could not push screen data to server", e);
        }
    }

    private List<ItemStackData> parseMenuSlots(AbstractContainerMenu menu) {
        int containerSize = menu.slots.size() - 36;

        List<ItemStackData> data = new ArrayList<>();

        for (int i = 0; i < containerSize; i++) {
            ItemStack itemStack = menu.getSlot(i).getItem();

            ItemStackData itemStackData = new ItemStackData();
            itemStackData.setSlot(i);
            if (itemStack.isEmpty()) {
                data.add(itemStackData);
                continue;
            }

            itemStackData.setDisplayName(itemStack.getDisplayName().getString());
            itemStackData.setItemName(itemStack.getItemName().getString());
            itemStackData.setItemCount(itemStack.count());

            ItemLore lore = itemStack.get(DataComponents.LORE);
            if (lore == null) {
                log.warn("Could not get lore from slot {}", i);
                return null;
            }
            List<String> lines = lore.lines().stream().map(Component::getString).toList();
            itemStackData.setLines(lines);

            data.add(itemStackData);
        }

        return data;
    }
}
