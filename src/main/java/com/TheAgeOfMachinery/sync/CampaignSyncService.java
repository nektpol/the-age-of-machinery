package com.TheAgeOfMachinery.sync;

import com.TheAgeOfMachinery.TheAgeofMachinery;
import com.TheAgeOfMachinery.runtime.CampaignRuntime;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class CampaignSyncService
{
    private static final Map<MinecraftServer, CampaignDeliveryQueue> QUEUES = new IdentityHashMap<>();

    private CampaignSyncService()
    {
    }

    public static void queue(ServerPlayer player)
    {
        MinecraftServer server = Objects.requireNonNull(player.getServer());
        if (!server.isSameThread()) throw new IllegalStateException("Campaign sync must be queued on the server thread.");
        QUEUES.computeIfAbsent(server, ignored -> new CampaignDeliveryQueue()).mark(player.getUUID());
    }

    public static void queueAll(MinecraftServer server)
    {
        if (!server.isSameThread()) throw new IllegalStateException("Campaign sync must be queued on the server thread.");

        for (ServerPlayer player : server.getPlayerList().getPlayers())
        {
            queue(player);
        }
    }

    public static void onServerTick(ServerTickEvent.Post event)
    {
        MinecraftServer server = event.getServer();
        CampaignDeliveryQueue queue = QUEUES.get(server);
        if (queue == null) return;
        var runtime = CampaignRuntime.findIfStarted(server);
        if (runtime.isEmpty()) return;

        for (UUID playerId : queue.drain())
        {
            ServerPlayer player = server.getPlayerList().getPlayer(Objects.requireNonNull(playerId));

            if (player == null)
            {
                queue.forget(playerId);
                continue;
            }

            try
            {
                PlayerCampaignView view = runtime.get().getPlayerView(player);
                long sequence = queue.nextSequence(playerId, player.connection);
                PacketDistributor.sendToPlayer(player, new PlayerCampaignPayload(sequence, view));
            }
            catch (RuntimeException exception)
            {
                TheAgeofMachinery.LOGGER.error("Unable to synchronize campaign state for player {}", playerId, exception);
            }
        }
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CampaignDeliveryQueue queue = QUEUES.get(player.getServer());
        if (queue != null) queue.forget(player.getUUID());
    }

    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player) queue(player);
    }

    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player) queue(player);
    }

    public static void onServerStopped(ServerStoppedEvent event)
    {
        QUEUES.remove(event.getServer());
    }
}
