package com.TheAgeOfMachinery.reward;

public record ItemReward(String id, String itemId, int amount) implements RewardDefinition {

    public ItemReward 
    {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Reward ID cannot be empty.");
        if (itemId == null || itemId.isBlank()) throw new IllegalArgumentException("Item ID cannot be empty.");
        if (amount <= 0) throw new IllegalArgumentException("Item amount must be positive.");
    }
}

