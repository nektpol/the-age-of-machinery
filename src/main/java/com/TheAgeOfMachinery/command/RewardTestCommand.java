
package com.TheAgeOfMachinery.command;

import java.util.Objects;

import com.TheAgeOfMachinery.integration.MinecraftRewardDelivery;
import com.TheAgeOfMachinery.reward.ExperienceReward;
import com.TheAgeOfMachinery.reward.ItemReward;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

public class RewardTestCommand
{
    public static void register(RegisterCommandsEvent event)
    {
        event.getDispatcher().register(
            Commands.literal("tamtest")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("xp").executes(context -> testExperience(context.getSource())))
                .then(Commands.literal("item").executes(context -> testItem(context.getSource())))
        );
    }

    private static int testExperience(CommandSourceStack source) throws CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftRewardDelivery delivery = new MinecraftRewardDelivery(player);

        boolean success = delivery.deliver(new ExperienceReward("debug_xp", 100));

        if (success)
        {
            source.sendSuccess(() -> Component.literal("Delivered 100 XP."), false);
            return 1;
        }

        Component message = Objects.requireNonNull(Component.literal("XP delivery failed."), "XP delivery message cannot be null.");
        source.sendFailure(message);
        return 0;
    }

    private static int testItem(CommandSourceStack source) throws CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftRewardDelivery delivery = new MinecraftRewardDelivery(player);

        boolean success = delivery.deliver(new ItemReward("debug_iron", "minecraft:iron_ingot", 5));

        if (success)
        {
            source.sendSuccess(() -> Component.literal("Delivered 5 Iron Ingots."), false);
            return 1;
        }

        Component message = Objects.requireNonNull(Component.literal("Item delivery failed: insufficient space or invalid item."), "Item delivery message cannot be null.");
        source.sendFailure(message);
        return 0;
    }
}


// ItemStack plannedStack = Objects.requireNonNull(plannedStacks[slot], "Planned item stack cannot be null.");
