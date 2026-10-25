
package com.TheAgeOfMachinery.quest;

public record QuestObjective(String id, String description, int requiredAmount, boolean optional) 
{
    public QuestObjective 
    {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Objective ID cannot be empty.");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("Objective description cannot be empty.");
        if (requiredAmount <= 0) throw new IllegalArgumentException("Required amount must be positive.");
    }
}
