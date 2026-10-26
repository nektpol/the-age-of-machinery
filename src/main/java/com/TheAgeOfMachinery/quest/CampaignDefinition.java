package com.TheAgeOfMachinery.quest;

import com.TheAgeOfMachinery.reward.QuestRewardDefinition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record CampaignDefinition(List<QuestRewardDefinition> quests)
{
    public CampaignDefinition
    {
        Objects.requireNonNull(quests, "Campaign quests cannot be null.");

        quests = List.copyOf(quests);

        if (quests.isEmpty()) throw new IllegalArgumentException("A campaign must contain at least one quest.");

        Set<String> registeredQuestIds = new HashSet<>();

        for (QuestRewardDefinition definition : quests)
        {
            String questId = definition.quest().id();

            if (!registeredQuestIds.add(questId)) throw new IllegalArgumentException("Duplicate campaign quest ID: " + questId);
        }
    }

    public int questCount()
    {
        return quests.size();
    }

    public List<String> getQuestIds()
    {
        List<String> questIds = new ArrayList<>();

        for (QuestRewardDefinition definition : quests)
        {
            questIds.add(definition.quest().id());
        }

        return List.copyOf(questIds);
    }
}
