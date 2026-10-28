package com.TheAgeOfMachinery.create;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

// Session-only replay protection. One operation can legitimately produce several item types.
final class ProcessingOperationLedger
{
    private final int capacity;
    private final Set<OutputKey> outputs = new LinkedHashSet<>();

    private record OutputKey(UUID installationId, UUID operationId, String outputItemId)
    {
    }

    ProcessingOperationLedger(int capacity)
    {
        if (capacity <= 0) throw new IllegalArgumentException("Ledger capacity must be positive.");

        this.capacity = capacity;
    }

    boolean remember(UUID installationId, UUID operationId, String outputItemId)
    {
        OutputKey key = new OutputKey(Objects.requireNonNull(installationId), Objects.requireNonNull(operationId), Objects.requireNonNull(outputItemId));

        if (!outputs.add(key)) return false;

        if (outputs.size() > capacity)
        {
            Iterator<OutputKey> iterator = outputs.iterator();
            iterator.next();
            iterator.remove();
        }

        return true;
    }
}
