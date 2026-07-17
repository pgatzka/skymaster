package io.github.pgatzka.skymaster.data;

import java.util.List;

public record CollectionMilestoneData(int milestone, String name, int countRequirement, int skyBlockXp, List<String> recipes, List<String> trades, List<String> discounts, SkillXpGain skillXpGain, String minionRecipe) {

}
