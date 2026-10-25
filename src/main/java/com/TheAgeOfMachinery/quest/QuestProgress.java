
package com.TheAgeOfMachinery.quest;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class QuestProgress 
{

    private final String questId;
    private final Map<String, Integer> objectiveProgress = new HashMap<>();
    private QuestState state;

    public QuestProgress(String questId) 
    {
        if (questId == null || questId.isBlank()) throw new IllegalArgumentException("Quest ID cannot be empty.");

        this.questId = questId;
        this.state = QuestState.LOCKED;
    }

    public String getQuestId() 
    {
        return questId;
    }

    public QuestState getState() 
    {
        return state;
    }

    public void activate() 
    {
        if (state != QuestState.LOCKED) throw new IllegalStateException("Quest cannot be activated.");
        state = QuestState.ACTIVE;
    }

    public int getObjectiveProgress(String objectiveId) 
    {
        return objectiveProgress.getOrDefault(objectiveId, 0);
    }

    public boolean addProgress(QuestObjective objective, int amount) 
    {
        Objects.requireNonNull(objective, "Objective cannot be null.");

        if (state != QuestState.ACTIVE || amount <= 0) return false;

        int current = getObjectiveProgress(objective.id());
        int remaining = objective.requiredAmount() - current;
        int updated = current + Math.min(amount, remaining);

        if (updated == current) return false;

        objectiveProgress.put(objective.id(), updated);
        return true;
    }

    public boolean isObjectiveComplete(QuestObjective objective) 
    {
        return getObjectiveProgress(objective.id()) >= objective.requiredAmount();
    }

    public boolean canAdvance(QuestDefinition definition) 
    {
        if (state != QuestState.ACTIVE || !questId.equals(definition.id())) return false;

        for (QuestObjective objective : definition.objectives())
        {
            if (objective.optional()) continue;
            if (!isObjectiveComplete(objective)) return false;
        }

        return true;
    }

    public boolean complete(QuestDefinition definition) 
    {
        if (!canAdvance(definition)) return false;

        state = QuestState.COMPLETED;
        return true;
    }

    public Map<String, Integer> getObjectiveProgressSnapshot() 
    {
        return Map.copyOf(objectiveProgress);
    }
}
