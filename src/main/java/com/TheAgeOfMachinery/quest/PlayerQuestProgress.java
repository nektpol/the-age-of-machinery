
package com.TheAgeOfMachinery.quest;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class PlayerQuestProgress {

    private final UUID playerId;
    private final Map<String, QuestProgress> quests = new HashMap<>();
    private String activeQuestId;

    public PlayerQuestProgress(UUID playerId) 
    {
        this.playerId = Objects.requireNonNull(playerId, "Player ID cannot be null.");
    }

    public UUID getPlayerId() 
    {
        return playerId;
    }

    public String getActiveQuestId() 
    {
        return activeQuestId;
    }

    
    public boolean activateQuest(QuestDefinition definition)
    {
        Objects.requireNonNull(definition, "Quest definition cannot be null.");

        if (activeQuestId != null) return false;
        if (quests.containsKey(definition.id())) return false;

        QuestProgress progress = new QuestProgress(definition.id());
        progress.activate();

        quests.put(definition.id(), progress);
        activeQuestId = definition.id();

        return true;
    }

    public Optional<QuestProgress> getActiveQuestProgress()
    {
        if (activeQuestId == null) return Optional.empty();

        return Optional.ofNullable(quests.get(activeQuestId));
    }

    public Optional<QuestProgress> getQuestProgress(String questId)
    {
        return Optional.ofNullable(quests.get(questId));
    }

    public Map<String, QuestProgress> getQuestProgressSnapshot()
    {
        return Map.copyOf(quests);
    }

    public boolean canAdvanceActiveQuest(QuestDefinition definition)
    {
        if (activeQuestId == null) return false;
        if (!activeQuestId.equals(definition.id())) return false;

        QuestProgress progress = quests.get(activeQuestId);
        if (progress == null) return false;

        return progress.canAdvance(definition);
    }

    public boolean completeActiveQuest(QuestDefinition definition)
    {
        if (!canAdvanceActiveQuest(definition)) return false;

        QuestProgress progress = quests.get(activeQuestId);
        if (!progress.complete(definition)) return false;

        activeQuestId = null;
        return true;
    }

    public boolean hasCompletedQuest(String questId)
    {
        QuestProgress progress = quests.get(questId);

        if (progress == null) return false;

        return progress.getState() == QuestState.COMPLETED;
    }

    
    public boolean addObjectiveProgress(QuestDefinition definition, String objectiveId, int amount)
    {
        if (activeQuestId == null || !activeQuestId.equals(definition.id())) return false;
        if (amount <= 0) return false;

        QuestProgress progress = quests.get(activeQuestId);
        if (progress == null) return false;

        for (QuestObjective objective : definition.objectives())
        {
            if (!objective.id().equals(objectiveId)) continue;

            return progress.addProgress(objective, amount);
        }

        return false;
    }

    public boolean hasActiveQuest()
    {
        return activeQuestId != null;
    }

    
    public static PlayerQuestProgress restore(UUID playerId, Map<String, QuestProgress> savedQuests)
    {
        Objects.requireNonNull(savedQuests, "Saved quests cannot be null.");

        PlayerQuestProgress playerProgress = new PlayerQuestProgress(playerId);

        for (Map.Entry<String, QuestProgress> entry : savedQuests.entrySet())
        {
            String questId = entry.getKey();
            QuestProgress progress = Objects.requireNonNull(entry.getValue(), "Saved quest progress cannot be null.");

            if (!progress.getQuestId().equals(questId)) throw new IllegalArgumentException("Quest ID does not match saved progress.");
            if (progress.getState() == QuestState.LOCKED) throw new IllegalArgumentException("Locked quests must not exist in saved progress.");

            if (progress.getState() == QuestState.ACTIVE)
            {
                if (playerProgress.activeQuestId != null) throw new IllegalArgumentException("Player cannot have multiple active quests.");

                playerProgress.activeQuestId = questId;
            }

            playerProgress.quests.put(questId, progress);
        }

        return playerProgress;
    }


}
