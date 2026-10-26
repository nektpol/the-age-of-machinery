
package com.TheAgeOfMachinery.quest;

import java.util.Objects;

public record QuestObjective(String id, String description, int requiredAmount, boolean optional, ObjectiveTrigger trigger)
{
    public QuestObjective 
    {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Objective ID cannot be empty.");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("Objective description cannot be empty.");
        if (requiredAmount <= 0) throw new IllegalArgumentException("Required amount must be positive.");
        Objects.requireNonNull(trigger, "Objective trigger cannot be null.");
    }

    
    public QuestObjective(String id, String description, int requiredAmount, boolean optional)
    {
        this(id, description, requiredAmount, optional, ObjectiveTrigger.manual());
    }

}
