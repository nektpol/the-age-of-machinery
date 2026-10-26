package com.TheAgeOfMachinery.runtime;

import com.TheAgeOfMachinery.json.CampaignDataPackLoader;
import com.TheAgeOfMachinery.quest.CampaignDefinition;

import com.mojang.logging.LogUtils;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;

/**
 * Applies a replacement only after Minecraft has successfully reloaded
 * its server data packs. This event also fires for individual player joins;
 * those must NEVER trigger campaign replacement.
 */
public final class CampaignReloadEvents
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private CampaignReloadEvents()
    {
    }

    public static void onDatapackSync(OnDatapackSyncEvent event)
    {
        if (event.getPlayer() != null) return;

        MinecraftServer server = Objects.requireNonNull(event.getPlayerList().getServer(), "Reload event has no Minecraft server.");
        Optional<CampaignRuntime> runtimeResult = CampaignRuntime.findIfStarted(server);

        // Initial data-pack loading can precede ServerStartedEvent.
        if (runtimeResult.isEmpty()) return;

        CampaignRuntime runtime = runtimeResult.get();

        try
        {
            CampaignDefinition candidate = CampaignDataPackLoader.load(server.getResourceManager());
            runtime.replaceCompatibleCampaign(candidate);
            LOGGER.info("The Age of Machinery: Applied compatible JSON campaign text changes.");
        }
        catch (RuntimeException exception)
        {
            runtime.recordReloadFailure(exception.getMessage());
            LOGGER.error("The Age of Machinery: Rejected campaign replacement; the previous in-memory campaign remains active. {}", exception.getMessage(), exception);
        }
    }
}
