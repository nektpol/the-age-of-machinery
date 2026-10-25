
package com.TheAgeOfMachinery.quest;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

public record QuestDefinition(String id, String title, String description, List<QuestObjective> objectives) 
{
    public QuestDefinition 
    {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Quest ID cannot be empty.");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Quest title cannot be empty.");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("Quest description cannot be empty.");
        objectives = List.copyOf(objectives);

        if (objectives.isEmpty()) throw new IllegalArgumentException("A quest must contain objectives.");

        Set<String> ids = new HashSet<>();
        boolean hasMainObjective = false;

        for (QuestObjective objective : objectives) 
        {
            if (!ids.add(objective.id())) throw new IllegalArgumentException("Duplicate objective ID: " + objective.id());
            if (!objective.optional()) hasMainObjective = true;
        }

        if (!hasMainObjective) throw new IllegalArgumentException("A quest must have at least one main objective.");
    }
}
