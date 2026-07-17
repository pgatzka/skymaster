package io.github.pgatzka.skymaster;

import com.google.gson.Gson;
import io.github.pgatzka.skymaster.data.CollectionData;
import io.github.pgatzka.skymaster.data.CollectionMilestoneData;
import io.github.pgatzka.skymaster.data.SkillXpGain;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.github.pgatzka.skymaster.SkymasterMod.log;

public class SkymasterClientMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
    }

    private Screen lastOpenScreen = null;

    private void onEndTick(Minecraft client) {
        if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
            return;
        }

        if (lastOpenScreen == screen) {
            // Only parse a screen when opening it
            return;
        }
        lastOpenScreen = screen;

        String title = screen.getTitle().getString();

        if (title.endsWith(" Collection")) {
            // We are in a collection screen
            parseCollection(title, screen.getMenu());
        }

    }

    private void parseCollection(String title, AbstractContainerMenu menu) {
        int containerSize = menu.slots.size() - 36;

        List<String> relevantStacks = List.of("block.minecraft.lime_stained_glass_pane", "block.minecraft.yellow_stained_glass_pane", "block.minecraft.red_stained_glass_pane");

        String collectionName = title.replace(" Collection", "");
        log.info("Parsing the {} collection", collectionName);

        List<CollectionMilestoneData> milestones = new ArrayList<>();
        for (int i = 0; i < containerSize; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (stack.isEmpty()) continue;

            String itemName = stack.getItem().getDescriptionId();

            if (!relevantStacks.contains(itemName)) {
                log.debug("Ignoring stack: {}", stack);
                continue;
            }

            try {
                CollectionMilestoneData milestone = parseMilestone(stack);

                if (milestone != null) {
                    milestones.add(milestone);
                }
            } catch (Exception e) {
                log.warn("Error parsing milestone {}", stack, e);
            }
        }

        CollectionData collectionData = new CollectionData(
                collectionName,
                milestones
        );
        Gson gson = new Gson();
        String json = gson.toJson(collectionData);

        // log.info("CollectionData\n{}", json);
    }

    private static final Pattern progressPattern = Pattern.compile("^([0-9,]+)/([0-9.]+)([kM]?)$");

    private static final Pattern skyBlockXpPattern = Pattern.compile("^\\+([0-9]+) SkyBlock XP$", Pattern.CASE_INSENSITIVE);

    private static final Pattern recipePattern = Pattern.compile("^([a-z \\-0-9\\[\\]]+) Recipe$", Pattern.CASE_INSENSITIVE);

    private static final Pattern discountPattern = Pattern.compile("^([a-z ]+) Exp Discount \\(-[0-9]+%\\)$", Pattern.CASE_INSENSITIVE);

    private static final Pattern skillXpPattern = Pattern.compile("^\\+([0-9,]+) ([a-z]+) Experience$", Pattern.CASE_INSENSITIVE);

    private static final Pattern minionRecipePattern = Pattern.compile("^([a-z ]+ Minion) Recipes$", Pattern.CASE_INSENSITIVE);

    private static final Pattern tradePattern = Pattern.compile("^([a-z ]+) Trade$", Pattern.CASE_INSENSITIVE);

    private static final List<String> itemNameTrash = List.of(
            "¯ÇÉ",
            "¯üô",
            "¯Çâ",
            "¯Çê",
            "¯Çò",
            "¯Ç£",
            "¯Çì",
            "¯Çº",
            "¯Çç",
            "¯üö",
            "¯Çî"
    );

    private CollectionMilestoneData parseMilestone(ItemStack stack) throws IllegalStateException {
        int milestoneNumber = stack.getCount();

        ItemLore lore = stack.get(DataComponents.LORE);

        if (lore == null) {
            log.warn("No lore found for stack {}", stack);
            return null;
        }

        List<String> lines = lore.lines().stream().map(Component::getString).map(line -> {
            for (String nameTrash : itemNameTrash) {
                line = line.replace(nameTrash, "");
            }
            return line;
        }).map(String::trim).filter(line -> !line.isEmpty()).toList();

        List<String> lineList = new ArrayList<>(lines);
        lineList.remove("Click to view rewards!");
        lineList.remove("Rewards:");
        lineList.removeIf(line -> line.startsWith("Progress: "));

        int countRequirement = parseCountRequirement(lines, lineList);
        int skyBlockXp = parseSkyBlockXp(lines, lineList);
        List<String> recipes = parseRecipes(lines, lineList);
        List<String> trades = parseTrades(lines, lineList);
        List<String> discounts = parseDiscounts(lines, lineList);
        SkillXpGain skillXpGain = parseSkillXp(lines, lineList);
        String minionRecipe = parseMinionRecipe(lines, lineList);

        if (!lineList.isEmpty()) {
            log.warn("Found {} unprocessed lines", lineList.size());
            lineList.forEach(line -> log.info("\t'{}'", line));
        }

        return new CollectionMilestoneData(milestoneNumber, stack.getDisplayName().getString(), countRequirement, skyBlockXp, recipes, discounts, trades, skillXpGain, minionRecipe);
    }

    private @Nullable String parseMinionRecipe(List<String> lines, List<String> lineList) {
        Matcher minionRecipeLineMatcher = lines.stream().map(line -> {
            Matcher matcher = minionRecipePattern.matcher(line);
            if (matcher.matches()) {
                lineList.remove(line);
                return matcher;
            }
            return null;
        }).filter(Objects::nonNull).reduce((_, _) -> {
            throw new IllegalStateException("Found multiple minion recipe lines");
        }).orElse(null);
        return parseMinionRecipeLine(minionRecipeLineMatcher);
    }

    private @Nullable SkillXpGain parseSkillXp(List<String> lines, List<String> lineList) {
        Matcher skillXpLineMatcher = lines.stream().map(line -> {
            Matcher matcher = skillXpPattern.matcher(line);
            if (matcher.matches()) {
                lineList.remove(line);
                return matcher;
            }
            return null;
        }).filter(Objects::nonNull).reduce((_, _) -> {
            throw new IllegalStateException("Found multiple skill xp lines");
        }).orElse(null);
        return parseSkillXpGainLine(skillXpLineMatcher);
    }

    private @NonNull List<String> parseDiscounts(List<String> lines, List<String> lineList) {
        List<Matcher> discountLineMatchers = lines.stream().map(line -> {
            Matcher matcher = discountPattern.matcher(line);
            if (matcher.matches()) {
                lineList.remove(line);
                return matcher;
            }
            return null;
        }).filter(Objects::nonNull).toList();
        return parseDiscountLines(discountLineMatchers);
    }

    private @NonNull List<String> parseRecipes(List<String> lines, List<String> lineList) {
        List<Matcher> recipeLineMatchers = lines.stream().map(line -> {
            Matcher matcher = recipePattern.matcher(line);
            if (matcher.matches()) {
                lineList.remove(line);
                return matcher;
            }
            return null;
        }).filter(Objects::nonNull).toList();
        return parseRecipeLines(recipeLineMatchers);
    }

    private @NonNull List<String> parseTrades(List<String> lines, List<String> lineList) {
        List<Matcher> recipeLineMatchers = lines.stream().map(line -> {
            Matcher matcher = tradePattern.matcher(line);
            if (matcher.matches()) {
                lineList.remove(line);
                return matcher;
            }
            return null;
        }).filter(Objects::nonNull).toList();
        return parseTradeLines(recipeLineMatchers);
    }

    private int parseSkyBlockXp(List<String> lines, List<String> lineList) {
        Matcher skyBlockXpLineMatcher = lines.stream().map(line -> {
            Matcher matcher = skyBlockXpPattern.matcher(line);
            if (matcher.matches()) {
                lineList.remove(line);
                return matcher;
            }
            return null;
        }).filter(Objects::nonNull).reduce((_, _) -> {
            throw new IllegalStateException("Found multiple SkyBlock xp lines");
        }).orElseThrow(() -> new IllegalStateException("Could not find SkyBlock XP line"));
        return parseSkyBlockXp(skyBlockXpLineMatcher);
    }

    private int parseCountRequirement(List<String> lines, List<String> lineList) {
        Matcher progressLineMatcher = lines.stream().map(line -> {
            Matcher matcher = progressPattern.matcher(line);
            if (matcher.matches()) {
                lineList.remove(line);
                return matcher;
            }
            return null;
        }).filter(Objects::nonNull).reduce((_, _) -> {
            throw new IllegalStateException("Found multiple progress lines");
        }).orElseThrow(() -> new IllegalStateException("Could not find progress line"));
        return parseCountRequirement(progressLineMatcher);
    }

    private String parseMinionRecipeLine(Matcher minionRecipeLineMatcher) {
        if (minionRecipeLineMatcher == null) return null;
        return minionRecipeLineMatcher.group(1);
    }

    private SkillXpGain parseSkillXpGainLine(Matcher skillXpLineMatcher) {
        if (skillXpLineMatcher == null) return null;
        return new SkillXpGain(skillXpLineMatcher.group(2), Integer.parseInt(skillXpLineMatcher.group(1).replace(",", "")));
    }

    private List<String> parseDiscountLines(List<Matcher> discountLineMatchers) {
        return discountLineMatchers.stream().map(matcher -> matcher.group(1)).toList();
    }

    private List<String> parseRecipeLines(List<Matcher> recipeLineMatchers) {
        return recipeLineMatchers.stream().map(matcher -> matcher.group(1)).toList();
    }

    private List<String> parseTradeLines(List<Matcher> tradeLineMatchers) {
        return tradeLineMatchers.stream().map(matcher -> matcher.group(1)).toList();
    }

    private int parseSkyBlockXp(Matcher skyBlockXpLineMatcher) {
        return Integer.parseInt(skyBlockXpLineMatcher.group(1));
    }

    private int parseCountRequirement(Matcher progressLineMatcher) {
        double requiredCount = Double.parseDouble(progressLineMatcher.group(2));

        String unit = progressLineMatcher.group(3);
        int countRequirement;
        if (!unit.isEmpty()) {
            countRequirement = switch (unit) {
                case "k" -> (int) (requiredCount * 1_000);
                case "M" -> (int) (requiredCount * 1_000_000);
                default -> throw new IllegalArgumentException("Unknown unit: " + unit);
            };
        } else {
            countRequirement = (int) requiredCount;
        }
        return countRequirement;
    }


}
