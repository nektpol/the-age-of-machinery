
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
        Objects.requireNonNull(playerId, "Player ID cannot be null.");

        Optional<CampaignSavedData.PlayerSnapshot> snapshotResult = savedData.getPlayerSnapshot(playerId);

        PlayerRewardInventory inventory = snapshotResult.isPresent() && snapshotResult.get().rewards() != null
            ? snapshotResult.get().rewards()
            : new PlayerRewardInventory(playerId);

        if (!inventory.unlockRewards(questId, rewards)) return false;

        long expectedRevision = snapshotResult.isPresent() ? snapshotResult.get().revision() : -1L;

        return savedData.tryStorePlayerRewardInventory(playerId, expectedRevision, inventory);
    }

    public RewardClaimResult claimReward(UUID playerId, String questId, String rewardId, RewardDelivery delivery)
    {
        return savedData.executeRewardClaim(playerId, questId, rewardId, delivery);
    }

    public Optional<RewardEntry> getReward(UUID playerId, String questId, String rewardId)
    {
        return savedData.getPlayerRewardInventory(playerId).flatMap(inventory -> inventory.getReward(questId, rewardId));
    }

    public Map<String, List<RewardEntry>> getPlayerRewardsSnapshot(UUID playerId)
    {
        Optional<PlayerRewardInventory> inventory = savedData.getPlayerRewardInventory(playerId);

        if (inventory.isEmpty()) return Map.of();

        return inventory.get().getRewardGroupsSnapshot();
    }
}
