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

        Optional<CampaignSavedData.PlayerSnapshot> snapshotResult = savedData.getPlayerSnapshot(playerId);

        if (snapshotResult.isPresent() && snapshotResult.get().quests() != null)
        {
            return snapshotResult.get().quests();
        }

        PlayerQuestProgress progress = new PlayerQuestProgress(playerId);
        long expectedRevision = snapshotResult.isPresent() ? snapshotResult.get().revision() : -1L;

        if (savedData.tryStorePlayerQuestProgress(playerId, expectedRevision, progress)) return progress;

        return savedData.getPlayerQuestProgress(playerId).orElseThrow(() -> new IllegalStateException("Player quest initialization failed."));
    }

    public boolean startFirstQuest(UUID playerId)
    {
        Objects.requireNonNull(playerId, "Player ID cannot be null.");

        if (questOrder.isEmpty()) return false;

        Optional<CampaignSavedData.PlayerSnapshot> snapshotResult = savedData.getPlayerSnapshot(playerId);

        PlayerQuestProgress progress = snapshotResult.isPresent() && snapshotResult.get().quests() != null
            ? snapshotResult.get().quests()
            : new PlayerQuestProgress(playerId);

        if (!progress.getQuestProgressSnapshot().isEmpty()) return false;

        QuestDefinition firstQuest = questDefinitions.get(questOrder.get(0));

        if (!progress.activateQuest(firstQuest)) return false;

        long expectedRevision = snapshotResult.isPresent() ? snapshotResult.get().revision() : -1L;

        return savedData.tryStorePlayerQuestProgress(playerId, expectedRevision, progress);
    }

    public Optional<QuestDefinition> getActiveQuestDefinition(UUID playerId)
    {
        Optional<PlayerQuestProgress> result = savedData.getPlayerQuestProgress(playerId);

        if (result.isEmpty() || !result.get().hasActiveQuest()) return Optional.empty();

        return getQuestDefinition(result.get().getActiveQuestId());
    }

    public boolean addObjectiveProgress(UUID playerId, String objectiveId, int amount)
    {
        Optional<CampaignSavedData.PlayerSnapshot> snapshotResult = savedData.getPlayerSnapshot(playerId);

        if (snapshotResult.isEmpty()) return false;

        CampaignSavedData.PlayerSnapshot snapshot = snapshotResult.get();
        PlayerQuestProgress progress = snapshot.quests();

        if (progress == null || !progress.hasActiveQuest()) return false;

        QuestDefinition definition = questDefinitions.get(progress.getActiveQuestId());

        if (definition == null) return false;
        if (!progress.addObjectiveProgress(definition, objectiveId, amount)) return false;

        return savedData.tryStorePlayerQuestProgress(playerId, snapshot.revision(), progress);
    }

    public boolean canAdvanceQuest(UUID playerId)
    {
        Optional<PlayerQuestProgress> result = savedData.getPlayerQuestProgress(playerId);

        if (result.isEmpty()) return false;

        PlayerQuestProgress progress = result.get();

        if (!progress.hasActiveQuest()) return false;

        QuestDefinition definition = questDefinitions.get(progress.getActiveQuestId());

        if (definition == null) return false;

        return progress.canAdvanceActiveQuest(definition);
    }

    public boolean completeActiveQuest(UUID playerId)
    {
        Optional<CampaignSavedData.PlayerSnapshot> snapshotResult = savedData.getPlayerSnapshot(playerId);

        if (snapshotResult.isEmpty()) return false;

        CampaignSavedData.PlayerSnapshot snapshot = snapshotResult.get();
        PlayerQuestProgress progress = snapshot.quests();

        if (progress == null || !progress.hasActiveQuest()) return false;

        QuestDefinition definition = questDefinitions.get(progress.getActiveQuestId());

        if (definition == null) return false;
        if (!progress.completeActiveQuest(definition)) return false;

        return savedData.tryStorePlayerQuestProgress(playerId, snapshot.revision(), progress);
    }

    public Optional<QuestDefinition> getNextQuestDefinition(String completedQuestId)
    {
        int currentIndex = questOrder.indexOf(completedQuestId);

        if (currentIndex == -1) return Optional.empty();

        int nextIndex = currentIndex + 1;

        if (nextIndex >= questOrder.size()) return Optional.empty();

        return getQuestDefinition(questOrder.get(nextIndex));
    }

    public boolean activateNextQuest(UUID playerId, String completedQuestId)
    {
        Optional<CampaignSavedData.PlayerSnapshot> snapshotResult = savedData.getPlayerSnapshot(playerId);

        if (snapshotResult.isEmpty()) return false;

        CampaignSavedData.PlayerSnapshot snapshot = snapshotResult.get();
        PlayerQuestProgress progress = snapshot.quests();

        if (progress == null) return false;
        if (!progress.hasCompletedQuest(completedQuestId)) return false;
        if (progress.hasActiveQuest()) return false;

        Optional<QuestDefinition> nextQuest = getNextQuestDefinition(completedQuestId);

        if (nextQuest.isEmpty()) return false;
        if (!progress.activateQuest(nextQuest.get())) return false;

        return savedData.tryStorePlayerQuestProgress(playerId, snapshot.revision(), progress);
    }

    public List<String> getQuestOrderSnapshot()
    {
        return List.copyOf(questOrder);
    }
}
