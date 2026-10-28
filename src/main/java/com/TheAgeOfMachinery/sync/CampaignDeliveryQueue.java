package com.TheAgeOfMachinery.sync;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class CampaignDeliveryQueue
{
    private final Set<UUID> pending = new LinkedHashSet<>();
    private final Map<UUID, Cursor> cursors = new HashMap<>();

    private record Cursor(Object connection, long sequence)
    {
    }

    public void mark(UUID playerId)
    {
        pending.add(Objects.requireNonNull(playerId));
    }

    public List<UUID> drain()
    {
        List<UUID> result = List.copyOf(pending);
        pending.clear();
        return result;
    }

    public long nextSequence(UUID playerId, Object connection)
    {
        Objects.requireNonNull(connection);
        Cursor previous = cursors.get(playerId);
        long sequence = previous == null || previous.connection() != connection ? 1 : Math.incrementExact(previous.sequence());
        cursors.put(Objects.requireNonNull(playerId), new Cursor(connection, sequence));
        return sequence;
    }

    public void forget(UUID playerId)
    {
        pending.remove(playerId);
        cursors.remove(playerId);
    }
}
