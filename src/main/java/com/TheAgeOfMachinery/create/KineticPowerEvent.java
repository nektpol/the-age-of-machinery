package com.TheAgeOfMachinery.create;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record KineticPowerEvent( MachineLocation location, ResourceLocation machineBlockId, float speed, boolean overstressed) implements CreateMachineEvent 
{
    public KineticPowerEvent 
    {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(machineBlockId, "machineBlockId");

        if (!Float.isFinite(speed)) throw new IllegalArgumentException("Kinetic speed must be finite");
    }
}