package com.TheAgeOfMachinery.quest;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record ObjectiveTrigger(ObjectiveTriggerType type, String itemId, String machineId, float minSpeed)
{
    public ObjectiveTrigger
    {
        Objects.requireNonNull(type, "Objective trigger type cannot be null.");
        Objects.requireNonNull(itemId, "Objective item ID cannot be null.");
        Objects.requireNonNull(machineId, "Objective machine ID cannot be null.");

        if (!Float.isFinite(minSpeed)) throw new IllegalArgumentException("Minimum speed must be finite.");

        switch (type)
        {
            case MANUAL ->
            {
                if (!itemId.isEmpty() || !machineId.isEmpty() || minSpeed != 0f) throw new IllegalArgumentException("Manual objectives cannot specify machinery or items.");
            }

            case CRAFT_ITEM, PICKUP_ITEM ->
            {
                if (!machineId.isEmpty() || minSpeed != 0f) throw new IllegalArgumentException("Item objectives cannot specify machinery properties.");
                itemId = normalizeId(itemId, "Item");
            }

            case KINETIC_ACTIVE ->
            {
                if (!itemId.isEmpty()) throw new IllegalArgumentException("Kinetic objectives cannot specify an output item.");
                if (minSpeed <= 0f) throw new IllegalArgumentException("Kinetic objectives require positive minimum speed.");
                machineId = normalizeId(machineId, "Machine");
            }

            case MACHINE_PROCESS ->
            {
                if (minSpeed != 0f) throw new IllegalArgumentException("Processing objectives cannot specify minimum speed.");
                itemId = normalizeId(itemId, "Output item");
                machineId = normalizeId(machineId, "Machine");
            }
        }
    }

    public ObjectiveTrigger(ObjectiveTriggerType type, String itemId)
    {
        this(type, itemId, "", 0f);
    }

    public static ObjectiveTrigger manual()
    {
        return new ObjectiveTrigger(ObjectiveTriggerType.MANUAL, "", "", 0f);
    }

    public static ObjectiveTrigger craftItem(String itemId)
    {
        return new ObjectiveTrigger(ObjectiveTriggerType.CRAFT_ITEM, itemId, "", 0f);
    }

    public static ObjectiveTrigger pickupItem(String itemId)
    {
        return new ObjectiveTrigger(ObjectiveTriggerType.PICKUP_ITEM, itemId, "", 0f);
    }

    public static ObjectiveTrigger kineticActive(String machineId, float minSpeed)
    {
        return new ObjectiveTrigger(ObjectiveTriggerType.KINETIC_ACTIVE, "", machineId, minSpeed);
    }

    public static ObjectiveTrigger machineProcess(String machineId, String outputItemId)
    {
        return new ObjectiveTrigger(ObjectiveTriggerType.MACHINE_PROCESS, outputItemId, machineId, 0f);
    }

    private static String normalizeId(String value, String description)
    {
        if (value.isBlank()) throw new IllegalArgumentException(description + " ID cannot be empty.");

        return Objects.requireNonNull(ResourceLocation.parse(Objects.requireNonNull(value))).toString();
    }
}
