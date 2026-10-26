
package com.TheAgeOfMachinery.reward;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class PlayerRewardInventory
{
    private final UUID playerId;
    private final Map<String, List<RewardEntry>> rewardsByQuest = new LinkedHashMap<>();

    public PlayerRewardInventory(UUID playerId)
    {
        this.playerId = Objects.requireNonNull(playerId, "Player ID cannot be null.");
    }

    public UUID getPlayerId()
    {
        return playerId;
    }

    public boolean unlockRewards(String questId, List<RewardDefinition> rewards)
    {
        if (questId == null || questId.isBlank()) throw new IllegalArgumentException("Quest ID cannot be empty.");

        Objects.requireNonNull(rewards, "Rewards cannot be null.");

        if (rewardsByQuest.containsKey(questId)) return false;

        List<RewardDefinition> validatedRewards = List.copyOf(rewards);
        Set<String> rewardIds = new HashSet<>();
        List<RewardEntry> entries = new ArrayList<>();

        for (RewardDefinition reward : validatedRewards)
        {
            if (!rewardIds.add(reward.id())) throw new IllegalArgumentException("Duplicate reward ID: " + reward.id());

            entries.add(new RewardEntry(questId, reward, RewardClaimState.AVAILABLE));
        }

        rewardsByQuest.put(questId, List.copyOf(entries));
        return true;
    }

    public Optional<RewardEntry> getReward(String questId, String rewardId)
    {
        List<RewardEntry> entries = rewardsByQuest.get(questId);

        if (entries == null) return Optional.empty();

        for (RewardEntry entry : entries)
        {
            if (entry.rewardId().equals(rewardId)) return Optional.of(entry);
        }

        return Optional.empty();
    }

    public boolean hasUnlockedRewardsFromQuest(String questId)
    {
        return rewardsByQuest.containsKey(questId);
    }

    public Map<String, List<RewardEntry>> getRewardGroupsSnapshot()
    {
        Map<String, List<RewardEntry>> snapshot = new LinkedHashMap<>(rewardsByQuest);

        return Collections.unmodifiableMap(snapshot);
    }

    
    RewardClaimResult claimReward(String questId, String rewardId, RewardDelivery delivery)
    {
        Objects.requireNonNull(delivery, "Reward delivery cannot be null.");

        List<RewardEntry> entries = rewardsByQuest.get(questId);

        if (entries == null) return RewardClaimResult.NOT_FOUND;

        for (int index = 0; index < entries.size(); index++)
        {
            RewardEntry entry = entries.get(index);

            if (!entry.rewardId().equals(rewardId)) continue;
            if (entry.isClaimed()) return RewardClaimResult.ALREADY_CLAIMED;
            if (!delivery.deliver(entry.reward())) return RewardClaimResult.DELIVERY_FAILED;

            List<RewardEntry> updatedEntries = new ArrayList<>(entries);
            updatedEntries.set(index, new RewardEntry(questId, entry.reward(), RewardClaimState.CLAIMED));

            rewardsByQuest.put(questId, List.copyOf(updatedEntries));

            return RewardClaimResult.SUCCESS;
        }

        return RewardClaimResult.NOT_FOUND;
    }

    public static PlayerRewardInventory restore(UUID playerId, Map<String, List<RewardEntry>> savedRewards)
    {
        Objects.requireNonNull(savedRewards, "Saved rewards cannot be null.");

        PlayerRewardInventory inventory = new PlayerRewardInventory(playerId);

        for (Map.Entry<String, List<RewardEntry>> group : savedRewards.entrySet())
        {
            String questId = group.getKey();
            List<RewardEntry> entries = List.copyOf(group.getValue());

            if (questId == null || questId.isBlank()) throw new IllegalArgumentException("Saved quest ID cannot be empty.");

            Set<String> rewardIds = new HashSet<>();

            for (RewardEntry entry : entries)
            {
                if (!entry.questId().equals(questId)) throw new IllegalArgumentException("Reward belongs to a different quest.");
                if (!rewardIds.add(entry.rewardId())) throw new IllegalArgumentException("Duplicate reward ID: " + entry.rewardId());
            }

            inventory.rewardsByQuest.put(questId, entries);
        }

        return inventory;
    }


}

