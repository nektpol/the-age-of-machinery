package com.TheAgeOfMachinery.reward;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class RewardManager
{
    private final Map<UUID, PlayerRewardInventory> playerInventories = new HashMap<>();

    public boolean unlockQuestRewards(UUID playerId, String questId, List<RewardDefinition> rewards)
    {
        PlayerRewardInventory inventory = getOrCreateInventory(playerId);

        return inventory.unlockRewards(questId, rewards);
    }

    public RewardClaimResult claimReward(UUID playerId, String questId, String rewardId, RewardDelivery delivery)
    {
        PlayerRewardInventory inventory = getOrCreateInventory(playerId);

        return inventory.claimReward(questId, rewardId, delivery);
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

        return playerInventories.computeIfAbsent(playerId, PlayerRewardInventory::new);
    }
}

