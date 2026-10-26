package com.TheAgeOfMachinery.command;

import com.TheAgeOfMachinery.persistence.CampaignSavedData;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Objects;

import javax.annotation.Nonnull;

public class SaveTestCommand
{
    public static void register(RegisterCommandsEvent event)
    {
        event.getDispatcher().register(
            Commands.literal("tamsavetest")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("increment").executes(context -> increment(context.getSource())))
                .then(Commands.literal("value").executes(context -> value(context.getSource())))
        );
    }

    private static int increment(CommandSourceStack source)
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());

        data.incrementTestValue();

        return success(source, "Saved counter increased to: " + data.getTestValue());
    }

    private static int value(CommandSourceStack source)
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());

        return success(source, "Current saved counter: " + data.getTestValue());
    }

    private static int success(CommandSourceStack source, @Nonnull String text)
    {
        Component message = Objects.requireNonNull(Component.literal(text), "Command message cannot be null.");

        source.sendSuccess(() -> message, false);
        return 1;
    }
}

