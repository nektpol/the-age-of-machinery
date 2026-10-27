package com.TheAgeOfMachinery.create.ownership;

import com.TheAgeOfMachinery.create.MachineLocation;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record MachineOwnership(UUID installationId, MachineLocation location, ResourceLocation blockId, Set<UUID> owners) 
{
    public MachineOwnership 
    {
        Objects.requireNonNull(installationId, "installationId");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(blockId, "blockId");

        owners = Set.copyOf(Objects.requireNonNull(owners, "owners"));

        if (owners.isEmpty()) throw new IllegalArgumentException("A machine installation must have at least one owner");
    }

    public boolean isOwner(UUID playerId) 
    {
        return owners.contains(playerId);
    }
}

