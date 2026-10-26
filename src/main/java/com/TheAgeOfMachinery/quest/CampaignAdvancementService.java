
package com.TheAgeOfMachinery.quest;

import com.TheAgeOfMachinery.reward.QuestRewardDefinition;
import com.TheAgeOfMachinery.reward.RewardDefinition;
import com.TheAgeOfMachinery.reward.RewardManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class CampaignAdvancementService
{
    private final QuestManager questManager;
    private final RewardManager rewardManager;
    private final Map<String, QuestRewardDefinition> questRewards = new HashMap<>();

    public CampaignAdvancementService(QuestManager questManager, RewardManager rewardManager)
    {
        this.questManager = Objects.requireNonNull(questManager, "Quest manager cannot be null.");
        this.rewardManager = Objects.requireNonNull(rewardManager, "Reward manager cannot be null.");
    }

    public void registerQuest(QuestRewardDefinition definition)
    {
        Objects.requireNonNull(definition, "Quest reward definition cannot be null.");

        String questId = definition.quest().id();

        if (questRewards.containsKey(questId)) throw new IllegalArgumentException("Duplicate quest reward definition: " + questId);

        questManager.registerQuest(definition.quest());
        questRewards.put(questId, definition);
    }

    public boolean startFirstQuest(UUID playerId)
    {
        Objects.requireNonNull(playerId, "Player ID cannot be null.");

        return questManager.startFirstQuest(playerId);
    }

    public CampaignAdvancementResult advanceQuest(UUID playerId)
    {
        Objects.requireNonNull(playerId, "Player ID cannot be null.");

        Optional<QuestDefinition> activeQuestResult = questManager.getActiveQuestDefinition(playerId);

        if (activeQuestResult.isEmpty()) return CampaignAdvancementResult.NO_ACTIVE_QUEST;

        QuestDefinition activeQuest = activeQuestResult.get();
        String questId = activeQuest.id();

        if (!questManager.canAdvanceQuest(playerId)) return CampaignAdvancementResult.MAIN_OBJECTIVES_INCOMPLETE;

        QuestRewardDefinition rewardConfiguration = questRewards.get(questId);

        if (rewardConfiguration == null) throw new IllegalStateException("Missing reward configuration for quest: " + questId);

        PlayerQuestProgress playerProgress = questManager.getOrCreatePlayerProgress(playerId);
        Optional<QuestProgress> progressResult = playerProgress.getActiveQuestProgress();

        if (progressResult.isEmpty()) return CampaignAdvancementResult.INVALID_CAMPAIGN_STATE;

        QuestProgress questProgress = progressResult.get();

        if (rewardManager.getPlayerRewardsSnapshot(playerId).containsKey(questId))
        {
            return CampaignAdvancementResult.REWARDS_ALREADY_UNLOCKED;
        }

        Optional<QuestDefinition> nextQuestResult = questManager.getNextQuestDefinition(questId);

        if (nextQuestResult.isPresent())
        {
            String nextQuestId = nextQuestResult.get().id();

            if (playerProgress.getQuestProgress(nextQuestId).isPresent())
            {
                return CampaignAdvancementResult.INVALID_CAMPAIGN_STATE;
            }
        }

        // Determine the rewards before changing the current quest state.
        List<RewardDefinition> earnedRewards = rewardConfiguration.calculateEarnedRewards(questProgress);

        // Unlock the complete reward group before finalizing the quest.
        if (!rewardManager.unlockQuestRewards(playerId, questId, earnedRewards))
        {
            return CampaignAdvancementResult.REWARDS_ALREADY_UNLOCKED;
        }

        // These operations were validated above. Unexpected failures indicate an inconsistent state.
        if (!questManager.completeActiveQuest(playerId))
        {
            throw new IllegalStateException("Quest completion failed after rewards were unlocked: " + questId);
        }

        if (nextQuestResult.isPresent())
        {
            if (!questManager.activateNextQuest(playerId, questId))
            {
                throw new IllegalStateException("Next quest activation failed after completing: " + questId);
            }

            return CampaignAdvancementResult.NEXT_QUEST_STARTED;
        }

        return CampaignAdvancementResult.CAMPAIGN_COMPLETED;
    }
}
