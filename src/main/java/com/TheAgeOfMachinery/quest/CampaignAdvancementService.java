package com.TheAgeOfMachinery.quest;

import com.TheAgeOfMachinery.persistence.CampaignSavedData;
import com.TheAgeOfMachinery.persistence.PlayerQuestProgressCodec;
import com.TheAgeOfMachinery.persistence.PlayerRewardInventoryCodec;
import com.TheAgeOfMachinery.reward.PlayerRewardInventory;
import com.TheAgeOfMachinery.reward.QuestRewardDefinition;
import com.TheAgeOfMachinery.reward.RewardDefinition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class CampaignAdvancementService
{
    private final CampaignSavedData savedData;
    private final QuestManager questManager;
    private final Map<String, QuestRewardDefinition> questRewards = new HashMap<>();

    public CampaignAdvancementService(CampaignSavedData savedData)
    {
        this.savedData = Objects.requireNonNull(savedData, "Campaign saved data cannot be null.");
        this.questManager = new QuestManager(savedData);
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

        Optional<CampaignSavedData.PlayerSnapshot> snapshotResult = savedData.getPlayerSnapshot(playerId);

        if (snapshotResult.isEmpty()) return CampaignAdvancementResult.NO_ACTIVE_QUEST;

        CampaignSavedData.PlayerSnapshot snapshot = snapshotResult.get();
        PlayerQuestProgress originalQuests = snapshot.quests();

        if (originalQuests == null || !originalQuests.hasActiveQuest()) return CampaignAdvancementResult.NO_ACTIVE_QUEST;

        Optional<QuestProgress> activeProgressResult = originalQuests.getActiveQuestProgress();

        if (activeProgressResult.isEmpty()) return CampaignAdvancementResult.INVALID_CAMPAIGN_STATE;

        QuestProgress activeProgress = activeProgressResult.get();
        String questId = activeProgress.getQuestId();

        Optional<QuestDefinition> definitionResult = questManager.getQuestDefinition(questId);

        if (definitionResult.isEmpty()) return CampaignAdvancementResult.INVALID_CAMPAIGN_STATE;

        QuestDefinition activeQuest = definitionResult.get();
        QuestRewardDefinition rewardConfiguration = questRewards.get(questId);

        if (rewardConfiguration == null) return CampaignAdvancementResult.INVALID_CAMPAIGN_STATE;
        if (!originalQuests.canAdvanceActiveQuest(activeQuest)) return CampaignAdvancementResult.MAIN_OBJECTIVES_INCOMPLETE;

        PlayerRewardInventory originalRewards = snapshot.rewards();

        if (originalRewards != null && originalRewards.hasUnlockedRewardsFromQuest(questId))
        {
            return CampaignAdvancementResult.REWARDS_ALREADY_UNLOCKED;
        }

        Optional<QuestDefinition> nextQuestResult = questManager.getNextQuestDefinition(questId);

        if (nextQuestResult.isPresent() && originalQuests.getQuestProgress(nextQuestResult.get().id()).isPresent())
        {
            return CampaignAdvancementResult.INVALID_CAMPAIGN_STATE;
        }

        List<RewardDefinition> earnedRewards = rewardConfiguration.calculateEarnedRewards(activeProgress);

        PlayerQuestProgress stagedQuests = PlayerQuestProgressCodec.read(PlayerQuestProgressCodec.write(originalQuests));
        PlayerRewardInventory stagedRewards = originalRewards == null ? new PlayerRewardInventory(playerId) : PlayerRewardInventoryCodec.read(PlayerRewardInventoryCodec.write(originalRewards));

        if (!stagedRewards.unlockRewards(questId, earnedRewards))
        {
            return CampaignAdvancementResult.REWARDS_ALREADY_UNLOCKED;
        }

        if (!stagedQuests.completeActiveQuest(activeQuest))
        {
            return CampaignAdvancementResult.INVALID_CAMPAIGN_STATE;
        }

        if (nextQuestResult.isPresent() && !stagedQuests.activateQuest(nextQuestResult.get()))
        {
            return CampaignAdvancementResult.INVALID_CAMPAIGN_STATE;
        }

        if (!savedData.commitAdvancement(playerId, snapshot.revision(), stagedQuests, stagedRewards))
        {
            return CampaignAdvancementResult.INVALID_CAMPAIGN_STATE;
        }

        if (nextQuestResult.isPresent()) return CampaignAdvancementResult.NEXT_QUEST_STARTED;

        return CampaignAdvancementResult.CAMPAIGN_COMPLETED;
    }
    
    public boolean addObjectiveProgress(UUID playerId, String objectiveId, int amount)
    {
        Objects.requireNonNull(playerId, "Player ID cannot be null.");
        Objects.requireNonNull(objectiveId, "Objective ID cannot be null.");

        if (amount <= 0) return false;

        return questManager.addObjectiveProgress(playerId, objectiveId, amount);
    }

    public Optional<QuestDefinition> getActiveQuestDefinition(UUID playerId)
    {
        Objects.requireNonNull(playerId, "Player ID cannot be null.");

        return questManager.getActiveQuestDefinition(playerId);
    }

}
