package com.TheAgeOfMachinery.create;

import com.TheAgeOfMachinery.create.ownership.MachineOwnership;
import com.TheAgeOfMachinery.create.ownership.MachineOwnershipRegistry;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;
import java.util.Optional;

public final class CreateEventAttribution 
{

    private CreateEventAttribution() 
    {
    }

    public static Optional<AttributedMachineEvent> resolve(ServerLevel level, CreateMachineEvent event) 
    {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(event, "event");

        if (!level.getServer().isSameThread()) throw new IllegalStateException("Create event attribution must run on the server thread");

        if (!level.dimension().equals(event.location().dimension())) return Optional.empty();

        Optional<MachineOwnership> ownershipResult = MachineOwnershipRegistry.find(level, event.location().position());

        if (ownershipResult.isEmpty()) return Optional.empty();

        MachineOwnership ownership = ownershipResult.get();

        if (!ownership.blockId().equals(event.machineBlockId())) return Optional.empty();

        return Optional.of(new AttributedMachineEvent(event, ownership.installationId(), ownership.owners()));
    }
}

