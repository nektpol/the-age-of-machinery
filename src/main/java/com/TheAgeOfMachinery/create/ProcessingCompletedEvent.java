package com.TheAgeOfMachinery.create;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

public record ProcessingCompletedEvent(MachineLocation location, ResourceLocation machineBlockId, UUID operationId, ResourceLocation outputItemId, int outputAmount) implements CreateMachineEvent 
{
    public ProcessingCompletedEvent 
    {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(machineBlockId, "machineBlockId");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(outputItemId, "outputItemId");

        if (outputAmount <= 0) throw new IllegalArgumentException("Processing output amount must be positive");
    }
}
