package com.TheAgeOfMachinery.quest;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.regex.Pattern;

public record QuestIngredient(String resourceId, boolean tag, int amount)
{
    private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public QuestIngredient
    {
        Objects.requireNonNull(resourceId, "Ingredient identifier cannot be null.");

        if (!RESOURCE_ID.matcher(resourceId).matches()) throw new IllegalArgumentException("Invalid ingredient identifier: " + resourceId);
        if (amount <= 0) throw new IllegalArgumentException("Ingredient quantity must be positive.");

        resourceId = Objects.requireNonNull(ResourceLocation.parse(resourceId)).toString();
    }

    public static QuestIngredient item(String resourceId, int amount)
    {
        return new QuestIngredient(resourceId, false, amount);
    }

    public static QuestIngredient tag(String resourceId, int amount)
    {
        return new QuestIngredient(resourceId, true, amount);
    }
}
