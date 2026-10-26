package com.TheAgeOfMachinery.reward;

import com.TheAgeOfMachinery.persistence.CampaignSavedData;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class RewardManager
{
    private final CampaignSavedData savedData;

    public RewardManager(CampaignSavedData savedData)
    {
        this.savedData = Objects.requireNonNull(savedData, "Campaign saved data cannot be null.");
    }

    public boolean unlockQuestRewards(UUID playerId, String questId, List<RewardDefinition> rewards)
    {
        PlayerRewardInventory inventory = getOrCreateInventory(playerId);

        if (!inventory.unlockRewards(questId, rewards)) return false;

        savedData.storePlayerRewardInventory(inventory);
        return true;
    }

    public RewardClaimResult claimReward(UUID playerId, String questId, String rewardId, RewardDelivery delivery)
    {
        PlayerRewardInventory inventory = getOrCreateInventory(playerId);

        RewardClaimResult result = inventory.claimReward(questId, rewardId, delivery);

        if (result == RewardClaimResult.SUCCESS)
        {
            savedData.storePlayerRewardInventory(inventory);
        }

        return result;
    }

    public Optional<RewardEntry> getReward(UUID playerId, String questId, String rewardId)
    {
        PlayerRewardInventory inventory = getOrCreateInventory(playerId);

        return inventory.getReward(questId, rewardId);
    }

    public Map<String, List<RewardEntry>> getPlayerRewardsSnapshot(UUID playerId)
    {
        PlayerRewardInventory inventory = getOrCreateInventory(playerId);

        return inventory.getRewardGroupsSnapshot();
    }

    private PlayerRewardInventory getOrCreateInventory(UUID playerId)
    {
        Objects.requireNonNull(playerId, "Player ID cannot be null.");

        Optional<PlayerRewardInventory> existing = savedData.getPlayerRewardInventory(playerId);

        if (existing.isPresent()) return existing.get();

        PlayerRewardInventory inventory = new PlayerRewardInventory(playerId);
        savedData.storePlayerRewardInventory(inventory);

        return inventory;
    }
}
