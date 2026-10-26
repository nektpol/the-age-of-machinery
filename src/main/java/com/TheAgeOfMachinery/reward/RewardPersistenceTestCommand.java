package com.TheAgeOfMachinery.reward;

import com.TheAgeOfMachinery.integration.MinecraftRewardDelivery;
import com.TheAgeOfMachinery.persistence.CampaignSavedData;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;

public final class RewardPersistenceTestCommand
{
    private static final String TEST_QUEST_ID = "theageofmachinery:debug_reward_persistence";

    private RewardPersistenceTestCommand()
    {
    }

    public static void register(RegisterCommandsEvent event)
    {
        event.getDispatcher().register(
            Commands.literal("tamrewardsave")
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

    private static UUID getTestPlayerId(CommandSourceStack source) throws CommandSyntaxException
    {
        UUID playerId = Objects.requireNonNull(source.getPlayerOrException().getUUID(), "Player UUID cannot be null.");
        String testKey = "theageofmachinery:reward_persistence_test:" + playerId;

        return UUID.nameUUIDFromBytes(testKey.getBytes(StandardCharsets.UTF_8));
    }

    private static int unlock(CommandSourceStack source) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());
        UUID testPlayerId = getTestPlayerId(source);

        if (data.getPlayerRewardInventory(testPlayerId).isPresent()) return failure(source, "Test reward inventory already exists.");

        PlayerRewardInventory inventory = new PlayerRewardInventory(testPlayerId);

        List<RewardDefinition> rewards = List.of(
            new ExperienceReward("main_xp", 100),
            new ItemReward("main_iron", "minecraft:iron_ingot", 5),
            new ExperienceReward("bonus_xp", 50)
        );

        if (!inventory.unlockRewards(TEST_QUEST_ID, rewards)) return failure(source, "Could not unlock test rewards.");

        data.storePlayerRewardInventory(inventory);

        return success(source, "Three persistent test rewards unlocked.");
    }

    private static int claim(CommandSourceStack source, String rewardId) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());
        Optional<PlayerRewardInventory> result = data.getPlayerRewardInventory(getTestPlayerId(source));

        if (result.isEmpty()) return failure(source, "Test rewards have not been unlocked.");

        ServerPlayer player = source.getPlayerOrException();
        PlayerRewardInventory inventory = result.get();
        MinecraftRewardDelivery delivery = new MinecraftRewardDelivery(player);

        RewardClaimResult claimResult = inventory.claimReward(TEST_QUEST_ID, rewardId, delivery);

        if (claimResult == RewardClaimResult.SUCCESS)
        {
            data.storePlayerRewardInventory(inventory);
            return success(source, "Reward claimed successfully: " + rewardId);
        }

        if (claimResult == RewardClaimResult.ALREADY_CLAIMED) return failure(source, "Reward has already been claimed.");
        if (claimResult == RewardClaimResult.DELIVERY_FAILED) return failure(source, "Reward delivery failed.");

        return failure(source, "Reward not found.");
    }

    private static int status(CommandSourceStack source) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());
        Optional<PlayerRewardInventory> result = data.getPlayerRewardInventory(getTestPlayerId(source));

        if (result.isEmpty()) return failure(source, "No persistent reward inventory found.");

        List<RewardEntry> entries = result.get().getRewardGroupsSnapshot().get(TEST_QUEST_ID);

        if (entries == null) return failure(source, "Test reward group not found.");

        for (RewardEntry entry : entries)
        {
            String message = entry.rewardId() + " | Amount: " + entry.reward().amount() + " | State: " + entry.state();
            success(source, message);
        }

        return 1;
    }

    private static int success(CommandSourceStack source, @Nonnull String text)
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
