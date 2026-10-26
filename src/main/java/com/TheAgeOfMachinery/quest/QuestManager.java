package com.TheAgeOfMachinery.quest;

import com.TheAgeOfMachinery.persistence.CampaignSavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class QuestManager
{
    private final CampaignSavedData savedData;

    private final Map<String, QuestDefinition> questDefinitions = new HashMap<>();
    private final List<String> questOrder = new ArrayList<>();

    public QuestManager(CampaignSavedData savedData)
    {
        this.savedData = Objects.requireNonNull(savedData, "Campaign saved data cannot be null.");
    }

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

        Optional<PlayerQuestProgress> existing = savedData.getPlayerQuestProgress(playerId);

        if (existing.isPresent()) return existing.get();

        PlayerQuestProgress progress = new PlayerQuestProgress(playerId);
        savedData.storePlayerQuestProgress(progress);

        return progress;
    }

    public boolean startFirstQuest(UUID playerId)
    {
        if (questOrder.isEmpty()) return false;

        PlayerQuestProgress progress = getOrCreatePlayerProgress(playerId);

        if (!progress.getQuestProgressSnapshot().isEmpty()) return false;

        QuestDefinition firstQuest = questDefinitions.get(questOrder.get(0));

        if (!progress.activateQuest(firstQuest)) return false;

        savedData.storePlayerQuestProgress(progress);
        return true;
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
        if (!progress.addObjectiveProgress(definition, objectiveId, amount)) return false;

        savedData.storePlayerQuestProgress(progress);
        return true;
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
        if (!progress.completeActiveQuest(definition)) return false;

        savedData.storePlayerQuestProgress(progress);
        return true;
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
        if (!progress.activateQuest(nextQuest.get())) return false;

        savedData.storePlayerQuestProgress(progress);
        return true;
    }

    public List<String> getQuestOrderSnapshot()
    {
        return List.copyOf(questOrder);
    }
}
