
package com.TheAgeOfMachinery.quest;

import java.util.List;
import java.util.Objects;

public record QuestObjective(String id, String description, int requiredAmount, boolean optional, ObjectiveTrigger trigger, List<QuestIngredient> ingredients)
{
    public QuestObjective 
    {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Objective ID cannot be empty.");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("Objective description cannot be empty.");
        if (requiredAmount <= 0) throw new IllegalArgumentException("Required amount must be positive.");
        Objects.requireNonNull(trigger, "Objective trigger cannot be null.");
        Objects.requireNonNull(ingredients, "Objective ingredients cannot be null.");
        ingredients = List.copyOf(ingredients);
    }
    
    public QuestObjective(String id, String description, int requiredAmount, boolean optional)
    {
        this(id, description, requiredAmount, optional, ObjectiveTrigger.manual(), List.of());
    }

    public QuestObjective(String id, String description, int requiredAmount, boolean optional, ObjectiveTrigger trigger)
    {
        this(id, description, requiredAmount, optional, trigger, List.of());
    }


}
