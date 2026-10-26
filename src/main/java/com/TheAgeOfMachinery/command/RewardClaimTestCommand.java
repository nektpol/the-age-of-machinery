
package com.TheAgeOfMachinery.command;

import com.TheAgeOfMachinery.integration.MinecraftRewardDelivery;
import com.TheAgeOfMachinery.reward.ExperienceReward;
import com.TheAgeOfMachinery.reward.ItemReward;
import com.TheAgeOfMachinery.reward.RewardClaimResult;
import com.TheAgeOfMachinery.reward.RewardDefinition;
import com.TheAgeOfMachinery.reward.RewardEntry;
import com.TheAgeOfMachinery.reward.RewardManager;
import com.TheAgeOfMachinery.persistence.CampaignSavedData;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;

public class RewardClaimTestCommand
{
    private static final String TEST_QUEST_ID = "debug:reward_claim_test";

    private static RewardManager rewardManager = new RewardManager(new CampaignSavedData());

    public static void register(RegisterCommandsEvent event)
    {
        event.getDispatcher().register(
            Commands.literal("tamrewardtest")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("unlock").executes(context -> unlock(context.getSource())))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("claim")
                    .then(Commands.literal("xp").executes(context -> claim(context.getSource(), "main_xp")))
                    .then(Commands.literal("iron").executes(context -> claim(context.getSource(), "main_iron")))
                    .then(Commands.literal("bonus").executes(context -> claim(context.getSource(), "bonus_xp")))
                )
        );
    }

    public static void reset(ServerStoppedEvent event)
    {
        rewardManager = new RewardManager(new CampaignSavedData());
    }

    private static int unlock(CommandSourceStack source) throws CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();

        List<RewardDefinition> rewards = List.of(
            new ExperienceReward("main_xp", 100),
            new ItemReward("main_iron", "minecraft:iron_ingot", 5),
            new ExperienceReward("bonus_xp", 50)
        );

        boolean unlocked = rewardManager.unlockQuestRewards(player.getUUID(), TEST_QUEST_ID, rewards);

        if (!unlocked) return failure(source, "Test rewards have already been unlocked.");

        return success(source, "Three test rewards unlocked.");
    }

    private static int claim(CommandSourceStack source, String rewardId) throws CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();

        MinecraftRewardDelivery delivery = new MinecraftRewardDelivery(player);

        RewardClaimResult result = rewardManager.claimReward(player.getUUID(), TEST_QUEST_ID, rewardId, delivery);

        if (result == RewardClaimResult.SUCCESS) return success(source, "Reward claimed successfully.");
        if (result == RewardClaimResult.ALREADY_CLAIMED) return failure(source, "This reward has already been claimed.");
        if (result == RewardClaimResult.DELIVERY_FAILED) return failure(source, "Reward delivery failed.");

        return failure(source, "Reward not found. Unlock the test rewards first.");
    }

    private static int status(CommandSourceStack source) throws CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();

        Map<String, List<RewardEntry>> groups = rewardManager.getPlayerRewardsSnapshot(player.getUUID());
        List<RewardEntry> entries = groups.get(TEST_QUEST_ID);

        if (entries == null) return failure(source, "No test rewards unlocked.");

        for (RewardEntry entry : entries)
        {
            success(source, entry.rewardId() + ": " + entry.state());
        }

        return 1;
    }

    private static int success(CommandSourceStack source,@Nonnull  String text)
    {
        Component message = Objects.requireNonNull(Component.literal(text), "Success message cannot be null.");

        source.sendSuccess(() -> message, false);
        return 1;
    }

    private static int failure(CommandSourceStack source, @Nonnull String text)
    {
        Component message = Objects.requireNonNull(Component.literal(text), "Failure message cannot be null.");

        source.sendFailure(message);
        return 0;
    }
}
