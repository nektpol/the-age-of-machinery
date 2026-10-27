package com.TheAgeOfMachinery.create;

import net.minecraft.resources.ResourceLocation;

public sealed interface CreateMachineEvent permits KineticPowerEvent, ProcessingCompletedEvent {

    MachineLocation location();

    ResourceLocation machineBlockId();
}
