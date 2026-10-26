package com.TheAgeOfMachinery.command;

import com.TheAgeOfMachinery.runtime.CampaignRuntime;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

public final class CampaignRuntimeTestCommand
{
    private CampaignRuntimeTestCommand()
    {
    }

    
    public static void register(RegisterCommandsEvent event)
    {
        event.getDispatcher().register(
            Commands.literal("tamruntime")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("campaign").executes(context -> campaign(context.getSource())))
        );
    }


    private static int status(CommandSourceStack source)
    {
        CampaignRuntime runtime = CampaignRuntime.get(source.getServer());

        success(source, "Campaign runtime session: " + runtime.getSessionId());
        success(source, "Advancement service initialized: " + (runtime.getAdvancementService() != null));
        success(source, "Reward manager initialized: " + (runtime.getRewardManager() != null));

        return 1;
    }

    private static void success(CommandSourceStack source, @Nonnull String text)
    {
        Component message = Objects.requireNonNull(Component.literal(text), "Runtime status message cannot be null.");

        source.sendSuccess(() -> message, false);
    }

    
    private static int campaign(CommandSourceStack source)
    {
        CampaignRuntime runtime = CampaignRuntime.get(source.getServer());
        List<String> questIds = runtime.getCampaignQuestIds();

        success(source, "Registered campaign quests: " + runtime.getCampaignQuestCount());

        for (int index = 0; index < questIds.size(); index++)
        {
            success(source, "Quest " + (index + 1) + ": " + questIds.get(index));
        }

        return 1;
    }

}
