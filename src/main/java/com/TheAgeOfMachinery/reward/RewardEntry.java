package com.TheAgeOfMachinery.reward;

import java.util.Objects;

public record RewardEntry(String questId, RewardDefinition reward, RewardClaimState state)
{
    public RewardEntry
    {
        if (questId == null || questId.isBlank()) throw new IllegalArgumentException("Quest ID cannot be empty.");

        Objects.requireNonNull(reward, "Reward definition cannot be null.");
        Objects.requireNonNull(state, "Reward claim state cannot be null.");
    }

    public String rewardId()
    {
        return reward.id();
    }

    public boolean isClaimed()
    {
        return state == RewardClaimState.CLAIMED;
    }
}

