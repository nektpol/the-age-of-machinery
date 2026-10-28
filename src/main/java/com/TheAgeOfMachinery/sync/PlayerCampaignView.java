package com.TheAgeOfMachinery.sync;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

// Shared immutable presentation data. This type has no server, registry or mutable domain references.
public record PlayerCampaignView(UUID sessionId, long playerRevision, boolean initialized, boolean campaignComplete, Optional<ActiveQuest> activeQuest, List<CompletedQuest> history, List<RewardGroup> rewards)
{
    public PlayerCampaignView
    {
        Objects.requireNonNull(sessionId, "Session ID cannot be null.");
        if (playerRevision < 0) throw new IllegalArgumentException("Player revision cannot be negative.");
        Objects.requireNonNull(activeQuest, "Active quest optional cannot be null.");
        history = List.copyOf(history);
        rewards = List.copyOf(rewards);
        if (campaignComplete && activeQuest.isPresent()) throw new IllegalArgumentException("A completed campaign cannot have an active quest.");
        if (!initialized && (campaignComplete || activeQuest.isPresent() || !history.isEmpty() || !rewards.isEmpty())) throw new IllegalArgumentException("An uninitialized view cannot contain progression.");
    }

    public record ActiveQuest(String id, String title, String description, List<Objective> objectives, boolean canAdvance)
    {
        public ActiveQuest
        {
            requireText(id);
            requireText(title);
            requireText(description);
            objectives = List.copyOf(objectives);
        }
    }

    public record CompletedQuest(String id, String title, String description, List<Objective> objectives)
    {
        public CompletedQuest
        {
            requireText(id);
            requireText(title);
            requireText(description);
            objectives = List.copyOf(objectives);
        }
    }

    public record Objective(String id, String description, int currentAmount, int requiredAmount, boolean optional, List<Ingredient> ingredients)
    {
        public Objective
        {
            requireText(id);
            requireText(description);
            if (requiredAmount <= 0 || currentAmount < 0 || currentAmount > requiredAmount) throw new IllegalArgumentException("Objective amounts are outside their supported range.");
            ingredients = List.copyOf(ingredients);
        }

        public boolean complete()
        {
            return currentAmount == requiredAmount;
        }
    }

    public record Ingredient(String resourceId, boolean tag, int amount)
    {
        public Ingredient
        {
            requireText(resourceId);
            if (amount <= 0) throw new IllegalArgumentException("Ingredient amount must be positive.");
        }
    }

    public record RewardGroup(String questId, String questTitle, List<Reward> entries)
    {
        public RewardGroup
        {
            requireText(questId);
            requireText(questTitle);
            entries = List.copyOf(entries);
        }
    }

    public enum RewardKind
    {
        EXPERIENCE,
        ITEM
    }

    public record Reward(String id, RewardKind kind, int amount, Optional<String> itemId, boolean claimed)
    {
        public Reward
        {
            requireText(id);
            Objects.requireNonNull(kind, "Reward kind cannot be null.");
            Objects.requireNonNull(itemId, "Reward item optional cannot be null.");
            if (amount <= 0) throw new IllegalArgumentException("Reward amount must be positive.");
            if ((kind == RewardKind.ITEM) != itemId.isPresent()) throw new IllegalArgumentException("Only item rewards can specify an item ID.");
            itemId.ifPresent(PlayerCampaignView::requireText);
        }
    }

    private static void requireText(String text)
    {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Presentation text cannot be empty.");
    }
}
