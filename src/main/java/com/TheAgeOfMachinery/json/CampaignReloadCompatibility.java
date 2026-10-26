package com.TheAgeOfMachinery.json;

import com.TheAgeOfMachinery.quest.CampaignDefinition;
import com.TheAgeOfMachinery.quest.QuestObjective;
import com.TheAgeOfMachinery.reward.ExperienceReward;
import com.TheAgeOfMachinery.reward.ItemReward;
import com.TheAgeOfMachinery.reward.QuestRewardDefinition;
import com.TheAgeOfMachinery.reward.RewardDefinition;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Format-v1 live reload policy: only quest titles/descriptions and objective
 * descriptions may change. Everything affecting progression or entitlements
 * remains identical until explicit save migrations are implemented.
 */
public final class CampaignReloadCompatibility
{
    private CampaignReloadCompatibility()
    {
    }

    public static void requireCompatible(CampaignDefinition current, CampaignDefinition candidate)
    {
        Objects.requireNonNull(current, "Current campaign cannot be null.");
        Objects.requireNonNull(candidate, "Candidate campaign cannot be null.");

        if (!current.getQuestIds().equals(candidate.getQuestIds())) throw new IllegalArgumentException("Campaign quest IDs or ordering changed. Live reload permits text-only changes.");

        for (int questIndex = 0; questIndex < current.quests().size(); questIndex++)
        {
            QuestRewardDefinition oldDefinition = current.quests().get(questIndex);
            QuestRewardDefinition newDefinition = candidate.quests().get(questIndex);
            String questId = oldDefinition.quest().id();
            List<QuestObjective> oldObjectives = oldDefinition.quest().objectives();
            List<QuestObjective> newObjectives = newDefinition.quest().objectives();

            if (oldObjectives.size() != newObjectives.size()) reject(questId, "Objective count changed");

            for (int objectiveIndex = 0; objectiveIndex < oldObjectives.size(); objectiveIndex++)
            {
                QuestObjective oldObjective = oldObjectives.get(objectiveIndex);
                QuestObjective newObjective = newObjectives.get(objectiveIndex);
                String location = questId + "/" + oldObjective.id();

                if (!oldObjective.id().equals(newObjective.id())) reject(location, "Objective ID or order changed");
                if (oldObjective.requiredAmount() != newObjective.requiredAmount()) reject(location, "Required quantity changed");
                if (oldObjective.optional() != newObjective.optional()) reject(location, "Mandatory/optional flag changed");
                if (!oldObjective.trigger().equals(newObjective.trigger())) reject(location, "Gameplay trigger changed");
                if (!oldObjective.ingredients().equals(newObjective.ingredients())) reject(location, "Ingredient checklist changed");
            }

            requireSameRewards(questId + "/rewards.main", oldDefinition.mainRewards(), newDefinition.mainRewards());
            Map<String, List<RewardDefinition>> oldOptional = oldDefinition.optionalRewards();
            Map<String, List<RewardDefinition>> newOptional = newDefinition.optionalRewards();

            if (!oldOptional.keySet().equals(newOptional.keySet())) reject(questId, "Optional reward objective keys changed");

            for (String objectiveId : oldOptional.keySet())
            {
                requireSameRewards(questId + "/rewards.optional." + objectiveId, oldOptional.get(objectiveId), newOptional.get(objectiveId));
            }
        }
    }

    private static void requireSameRewards(String location, List<RewardDefinition> current, List<RewardDefinition> candidate)
    {
        if (current.size() != candidate.size()) reject(location, "Reward count changed");

        for (int index = 0; index < current.size(); index++)
        {
            RewardDefinition oldReward = current.get(index);
            RewardDefinition newReward = candidate.get(index);
            String rewardLocation = location + "[" + index + "]";

            if (oldReward instanceof ExperienceReward oldXp && newReward instanceof ExperienceReward newXp)
            {
                if (!oldXp.id().equals(newXp.id()) || oldXp.amount() != newXp.amount()) reject(rewardLocation, "Experience reward changed");
            }
            else if (oldReward instanceof ItemReward oldItem && newReward instanceof ItemReward newItem)
            {
                if (!oldItem.id().equals(newItem.id()) || oldItem.amount() != newItem.amount() || !oldItem.itemId().equals(newItem.itemId())) reject(rewardLocation, "Item reward changed");
            }
            else
            {
                reject(rewardLocation, "Reward type changed or is unsupported by live reload");
            }
        }
    }

    private static void reject(String location, String reason)
    {
        throw new IllegalArgumentException("[" + location + "] " + reason + ". Use a versioned migration for structural changes.");
    }
}
