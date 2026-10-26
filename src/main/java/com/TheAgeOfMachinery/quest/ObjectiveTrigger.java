
package com.TheAgeOfMachinery.quest;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record ObjectiveTrigger(ObjectiveTriggerType type, String itemId)
{
    public ObjectiveTrigger
    {
        Objects.requireNonNull(type, "Objective trigger type cannot be null.");
        Objects.requireNonNull(itemId, "Objective trigger item ID cannot be null.");

        if (type == ObjectiveTriggerType.MANUAL)
        {
            if (!itemId.isEmpty()) throw new IllegalArgumentException("Manual objectives cannot specify an item.");
        }
        else
        {
            if (itemId.isBlank()) throw new IllegalArgumentException("Item-triggered objectives require an item ID.");

            itemId = ResourceLocation.parse(itemId).toString();
        }
    }

    public static ObjectiveTrigger manual()
    {
        return new ObjectiveTrigger(ObjectiveTriggerType.MANUAL, "");
    }

    public static ObjectiveTrigger craftItem(String itemId)
    {
        return new ObjectiveTrigger(ObjectiveTriggerType.CRAFT_ITEM, itemId);
    }

    public static ObjectiveTrigger pickupItem(String itemId)
    {
        return new ObjectiveTrigger(ObjectiveTriggerType.PICKUP_ITEM, itemId);
    }
}
