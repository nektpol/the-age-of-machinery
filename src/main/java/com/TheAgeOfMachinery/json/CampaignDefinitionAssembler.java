package com.TheAgeOfMachinery.json;

import com.TheAgeOfMachinery.quest.CampaignDefinition;
import com.TheAgeOfMachinery.quest.ObjectiveTriggerType;
import com.TheAgeOfMachinery.quest.QuestIngredient;
import com.TheAgeOfMachinery.quest.QuestObjective;
import com.TheAgeOfMachinery.reward.ItemReward;
import com.TheAgeOfMachinery.reward.QuestRewardDefinition;
import com.TheAgeOfMachinery.reward.RewardDefinition;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CampaignDefinitionAssembler
{
    private CampaignDefinitionAssembler()
    {
    }

    public static CampaignDefinition assemble(CampaignManifest manifest, Map<String, QuestRewardDefinition> definitions)
    {
        Objects.requireNonNull(manifest, "Campaign manifest cannot be null.");
        Objects.requireNonNull(definitions, "Quest definitions cannot be null.");

        Map<String, QuestRewardDefinition> available = Map.copyOf(definitions);
        List<QuestRewardDefinition> ordered = new ArrayList<>();

        for (String questId : manifest.quests())
        {
            QuestRewardDefinition definition = available.get(questId);

            if (definition == null) throw new IllegalArgumentException("[" + manifest.id() + "] Missing quest definition: " + questId);
            if (!questId.equals(definition.quest().id())) throw new IllegalArgumentException("[" + manifest.id() + "] Quest resource key " + questId + " does not match embedded quest ID " + definition.quest().id());

            validateQuest(definition);
            ordered.add(definition);
        }

        return new CampaignDefinition(ordered);
    }

    private static void validateQuest(QuestRewardDefinition definition)
    {
        String questId = definition.quest().id();

        for (QuestObjective objective : definition.quest().objectives())
        {
            String path = "[" + questId + "] objectives." + objective.id();

            if (objective.trigger().type() != ObjectiveTriggerType.MANUAL)
            {
                requireRegisteredItem(objective.trigger().itemId(), path + ".trigger.item");
            }

            for (int index = 0; index < objective.ingredients().size(); index++)
            {
                QuestIngredient ingredient = objective.ingredients().get(index);

                // Tags are validated against active data-pack tags at the later loader stage.
                if (!ingredient.tag()) requireRegisteredItem(ingredient.resourceId(), path + ".ingredients[" + index + "].item");
            }
        }

        validateRewards(definition.mainRewards(), questId, "rewards.main");

        for (Map.Entry<String, List<RewardDefinition>> entry : definition.optionalRewards().entrySet())
        {
            validateRewards(entry.getValue(), questId, "rewards.optional." + entry.getKey());
        }
    }

    private static void validateRewards(List<RewardDefinition> rewards, String questId, String path)
    {
        for (RewardDefinition reward : rewards)
        {
            if (reward instanceof ItemReward itemReward)
            {
                requireRegisteredItem(itemReward.itemId(), "[" + questId + "] " + path + "." + itemReward.id() + ".item");
            }
        }
    }

    private static void requireRegisteredItem(String itemId, String path)
    {
        ResourceLocation id = Objects.requireNonNull(ResourceLocation.parse(Objects.requireNonNull(itemId)));

        if (!BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalArgumentException(path + ": Unregistered item: " + itemId);
    }
}
