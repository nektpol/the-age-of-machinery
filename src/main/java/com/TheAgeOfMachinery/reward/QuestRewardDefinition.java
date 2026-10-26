package com.TheAgeOfMachinery.reward;

import com.TheAgeOfMachinery.quest.QuestDefinition;
import com.TheAgeOfMachinery.quest.QuestObjective;
import com.TheAgeOfMachinery.quest.QuestProgress;
import com.TheAgeOfMachinery.quest.QuestState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record QuestRewardDefinition(
    QuestDefinition quest,
    List<RewardDefinition> mainRewards,
    Map<String, List<RewardDefinition>> optionalRewards
)
{
    public QuestRewardDefinition
    {
        Objects.requireNonNull(quest, "Quest definition cannot be null.");
        mainRewards = List.copyOf(Objects.requireNonNull(mainRewards, "Main rewards cannot be null."));
        Objects.requireNonNull(optionalRewards, "Optional rewards cannot be null.");

        Set<String> validOptionalIds = new HashSet<>();
        Set<String> usedRewardIds = new HashSet<>();

        for (QuestObjective objective : quest.objectives())
        {
            if (objective.optional()) validOptionalIds.add(objective.id());
        }

        validateRewardIds(mainRewards, usedRewardIds);

        Map<String, List<RewardDefinition>> validatedOptionalRewards = new LinkedHashMap<>();

        for (Map.Entry<String, List<RewardDefinition>> entry : optionalRewards.entrySet())
        {
            String objectiveId = Objects.requireNonNull(entry.getKey(), "Optional objective ID cannot be null.");

            if (!validOptionalIds.contains(objectiveId)) throw new IllegalArgumentException("Unknown or mandatory objective: " + objectiveId);

            List<RewardDefinition> rewards = List.copyOf(entry.getValue());

            validateRewardIds(rewards, usedRewardIds);
            validatedOptionalRewards.put(objectiveId, rewards);
        }

        optionalRewards = Collections.unmodifiableMap(validatedOptionalRewards);
    }

    private static void validateRewardIds(List<RewardDefinition> rewards, Set<String> usedRewardIds)
    {
        for (RewardDefinition reward : rewards)
        {
            if (!usedRewardIds.add(reward.id())) throw new IllegalArgumentException("Duplicate reward ID: " + reward.id());
        }
    }

    public List<RewardDefinition> calculateEarnedRewards(QuestProgress progress)
    {
        Objects.requireNonNull(progress, "Quest progress cannot be null.");

        if (!quest.id().equals(progress.getQuestId())) throw new IllegalArgumentException("Quest progress belongs to a different quest.");
        if (progress.getState() != QuestState.ACTIVE) throw new IllegalStateException("Rewards must be calculated before completing the quest.");

        List<RewardDefinition> earnedRewards = new ArrayList<>(mainRewards);

        for (QuestObjective objective : quest.objectives())
        {
            if (!objective.optional()) continue;
            if (!progress.isObjectiveComplete(objective)) continue;

            List<RewardDefinition> rewards = optionalRewards.get(objective.id());

            if (rewards != null) earnedRewards.addAll(rewards);
        }

        return List.copyOf(earnedRewards);
    }
}
