package com.TheAgeOfMachinery.reward;

public record ExperienceReward(String id, int amount) implements RewardDefinition 
{

    public ExperienceReward 
    {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Reward ID cannot be empty.");
        if (amount <= 0) throw new IllegalArgumentException("Experience amount must be positive.");
    }
}

