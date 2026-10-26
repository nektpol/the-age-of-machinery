package com.TheAgeOfMachinery.runtime;

import com.TheAgeOfMachinery.quest.QuestDefinition;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Objects;
import java.util.Optional;

public final class CampaignPlayerEvents
{
    private CampaignPlayerEvents()
    {
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        MinecraftServer server = Objects.requireNonNull(player.getServer(), "Player's Minecraft server cannot be null.");
        CampaignRuntime runtime = CampaignRuntime.get(server);

        if (!runtime.initializePlayerCampaign(player)) return;

        Optional<QuestDefinition> activeQuest = runtime.getActiveQuestDefinition(player.getUUID());

        if (activeQuest.isPresent())
        {
            Component message = Objects.requireNonNull(Component.literal("The Age of Machinery - New quest: " + activeQuest.get().title()));
            player.sendSystemMessage(message);
        }
    }
}

