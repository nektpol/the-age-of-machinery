
package com.TheAgeOfMachinery.quest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class QuestManager {

    private final Map<String, QuestDefinition> questDefinitions = new HashMap<>();
    private final List<String> questOrder = new ArrayList<>();
    private final Map<UUID, PlayerQuestProgress> playerProgress = new HashMap<>();

    public void registerQuest(QuestDefinition definition)
    {
        Objects.requireNonNull(definition, "Quest definition cannot be null.");

        if (questDefinitions.containsKey(definition.id()))
        {
            throw new IllegalArgumentException("Duplicate quest ID: " + definition.id());
        }

        questDefinitions.put(definition.id(), definition);
        questOrder.add(definition.id());
    }

    public Optional<QuestDefinition> getQuestDefinition(String questId)
    {
        return Optional.ofNullable(questDefinitions.get(questId));
    }

    public PlayerQuestProgress getOrCreatePlayerProgress(UUID playerId)
    {
        Objects.requireNonNull(playerId, "Player ID cannot be null.");

        PlayerQuestProgress progress = playerProgress.get(playerId);

        if (progress == null)
        {
            progress = new PlayerQuestProgress(playerId);
            playerProgress.put(playerId, progress);
        }

        return progress;
    }

    public boolean startFirstQuest(UUID playerId)
    {
        if (questOrder.isEmpty()) return false;

        PlayerQuestProgress progress = getOrCreatePlayerProgress(playerId);

        if (!progress.getQuestProgressSnapshot().isEmpty()) return false;

        QuestDefinition firstQuest = questDefinitions.get(questOrder.get(0));

        return progress.activateQuest(firstQuest);
    }

    public Optional<QuestDefinition> getActiveQuestDefinition(UUID playerId)
    {
        PlayerQuestProgress progress = getOrCreatePlayerProgress(playerId);

        if (!progress.hasActiveQuest()) return Optional.empty();

        return getQuestDefinition(progress.getActiveQuestId());
    }

    public boolean addObjectiveProgress(UUID playerId, String objectiveId, int amount)
    {
        PlayerQuestProgress progress = getOrCreatePlayerProgress(playerId);

        if (!progress.hasActiveQuest()) return false;

        QuestDefinition definition = questDefinitions.get(progress.getActiveQuestId());

        if (definition == null) return false;

        return progress.addObjectiveProgress(definition, objectiveId, amount);
    }

    public boolean canAdvanceQuest(UUID playerId)
    {
        PlayerQuestProgress progress = getOrCreatePlayerProgress(playerId);

        if (!progress.hasActiveQuest()) return false;

        QuestDefinition definition = questDefinitions.get(progress.getActiveQuestId());

        if (definition == null) return false;

        return progress.canAdvanceActiveQuest(definition);
    }

    public boolean completeActiveQuest(UUID playerId)
    {
        PlayerQuestProgress progress = getOrCreatePlayerProgress(playerId);

        if (!progress.hasActiveQuest()) return false;

        QuestDefinition definition = questDefinitions.get(progress.getActiveQuestId());

        if (definition == null) return false;

        return progress.completeActiveQuest(definition);
    }

    public Optional<QuestDefinition> getNextQuestDefinition(String completedQuestId)
    {
        int currentIndex = questOrder.indexOf(completedQuestId);

        if (currentIndex == -1) return Optional.empty();

        int nextIndex = currentIndex + 1;

        if (nextIndex >= questOrder.size()) return Optional.empty();

        String nextQuestId = questOrder.get(nextIndex);

        return getQuestDefinition(nextQuestId);
    }

    public boolean activateNextQuest(UUID playerId, String completedQuestId)
    {
        PlayerQuestProgress progress = getOrCreatePlayerProgress(playerId);

        if (!progress.hasCompletedQuest(completedQuestId)) return false;
        if (progress.hasActiveQuest()) return false;

        Optional<QuestDefinition> nextQuest = getNextQuestDefinition(completedQuestId);

        if (nextQuest.isEmpty()) return false;

        return progress.activateQuest(nextQuest.get());
    }

    public List<String> getQuestOrderSnapshot()
    {
        return List.copyOf(questOrder);
    }
}
