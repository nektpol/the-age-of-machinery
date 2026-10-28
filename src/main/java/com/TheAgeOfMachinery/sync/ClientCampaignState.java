package com.TheAgeOfMachinery.sync;

import java.util.Objects;
import java.util.Optional;

// Accessed on the client main thread. Object tokens compare connection identity without loading client classes.
public final class ClientCampaignState
{
    private static Object connection;
    private static PlayerCampaignPayload latest;

    private ClientCampaignState()
    {
    }

    public static void connected(Object token)
    {
        connection = Objects.requireNonNull(token);
        latest = null;
    }

    public static void disconnected(Object token)
    {
        if (token != null && token != connection) return;
        connection = null;
        latest = null;
    }

    public static boolean receive(Object token, PlayerCampaignPayload payload)
    {
        Objects.requireNonNull(payload);
        if (connection == null || token != connection) return false;

        if (latest != null)
        {
            if (!latest.view().sessionId().equals(payload.view().sessionId())) return false;
            if (payload.sequence() <= latest.sequence()) return false;
        }

        latest = payload;
        return true;
    }

    public static Optional<PlayerCampaignPayload> snapshot()
    {
        return Optional.ofNullable(latest);
    }
}
