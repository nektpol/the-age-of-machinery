package com.TheAgeOfMachinery.create;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record AttributedMachineEvent(CreateMachineEvent event, UUID installationId, Set<UUID> owners) 
{
    public AttributedMachineEvent 
    {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(installationId, "installationId");

        owners = Set.copyOf(Objects.requireNonNull(owners, "owners"));

        if (owners.isEmpty()) throw new IllegalArgumentException("An attributed machine event must have at least one owner");
    }

    public boolean belongsTo(UUID playerId) {return owners.contains(playerId);}
}
