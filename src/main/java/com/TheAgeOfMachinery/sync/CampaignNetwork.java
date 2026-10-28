package com.TheAgeOfMachinery.sync;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.Objects;

public final class CampaignNetwork
{
    private CampaignNetwork()
    {
    }

    public static void register(RegisterPayloadHandlersEvent event)
    {
        event.registrar("1").playToClient(Objects.requireNonNull(PlayerCampaignPayload.TYPE), Objects.requireNonNull(PlayerCampaignPayload.CODEC), (payload, context) -> ClientCampaignState.receive(context.connection(), payload));
    }
}
