package com.TheAgeOfMachinery.command;

import com.TheAgeOfMachinery.integration.MinecraftRewardDelivery;
import com.TheAgeOfMachinery.persistence.CampaignSavedData;
import com.TheAgeOfMachinery.reward.ExperienceReward;
import com.TheAgeOfMachinery.reward.PlayerRewardInventory;
import com.TheAgeOfMachinery.reward.RewardClaimResult;
import com.TheAgeOfMachinery.reward.RewardDelivery;
import com.TheAgeOfMachinery.reward.RewardEntry;
import com.TheAgeOfMachinery.reward.RewardManager;

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

public final class RewardRevisionTestCommand
{
    private static final String QUEST_ID = "theageofmachinery:debug_reward_revision";
    private static final String EXTRA_QUEST_ID = "theageofmachinery:debug_reward_revision_extra";
    private static final String MAIN_REWARD_ID = "main_xp";
    private static final String NESTED_REWARD_ID = "nested_xp";

    private RewardRevisionTestCommand()
    {
    }

    public static void register(RegisterCommandsEvent event)
    {
        event.getDispatcher().register(
            Commands.literal("tamrewardrevision")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("init").executes(context -> initialize(context.getSource())))
                .then(Commands.literal("stale").executes(context -> testStaleWrite(context.getSource())))
                .then(Commands.literal("fail").executes(context -> testFailedDelivery(context.getSource())))
                .then(Commands.literal("claim").executes(context -> claim(context.getSource())))
                .then(Commands.literal("nested").executes(context -> testNestedClaim(context.getSource())))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
        );
    }

    private static UUID getTestPlayerId(CommandSourceStack source) throws CommandSyntaxException
    {
        UUID playerId = Objects.requireNonNull(source.getPlayerOrException().getUUID(), "Player UUID cannot be null.");
        String key = "theageofmachinery:reward_revision_test:" + playerId;

        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private static int initialize(CommandSourceStack source) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());
        RewardManager manager = new RewardManager(data);
        UUID playerId = getTestPlayerId(source);

        boolean unlocked = manager.unlockQuestRewards(
            playerId,
            QUEST_ID,
            List.of(new ExperienceReward(MAIN_REWARD_ID, 10), new ExperienceReward(NESTED_REWARD_ID, 1))
        );

        if (!unlocked) return failure(source, "Test rewards already initialized.");

        return success(source, "Reward revision test initialized.");
    }

    private static int testStaleWrite(CommandSourceStack source) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());
        RewardManager manager = new RewardManager(data);
        UUID playerId = getTestPlayerId(source);

        Optional<CampaignSavedData.PlayerSnapshot> snapshotResult = data.getPlayerSnapshot(playerId);

        if (snapshotResult.isEmpty() || snapshotResult.get().rewards() == null) return failure(source, "Initialize the test first.");

        CampaignSavedData.PlayerSnapshot oldSnapshot = snapshotResult.get();

        if (!manager.unlockQuestRewards(playerId, EXTRA_QUEST_ID, List.of(new ExperienceReward("extra_xp", 2))))
        {
            return failure(source, "Extra rewards already unlocked.");
        }

        PlayerRewardInventory staleInventory = oldSnapshot.rewards();
        boolean accepted = data.tryStorePlayerRewardInventory(playerId, oldSnapshot.revision(), staleInventory);

        if (accepted) return failure(source, "Stale reward write was incorrectly accepted.");

        PlayerRewardInventory latest = data.getPlayerRewardInventory(playerId).orElseThrow();

        if (!latest.hasUnlockedRewardsFromQuest(EXTRA_QUEST_ID)) return failure(source, "Newer reward group was lost.");

        return success(source, "Stale reward write rejected and newer rewards preserved.");
    }

    private static int testFailedDelivery(CommandSourceStack source) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());
        RewardManager manager = new RewardManager(data);
        UUID playerId = getTestPlayerId(source);

        Optional<CampaignSavedData.PlayerSnapshot> beforeResult = data.getPlayerSnapshot(playerId);

        if (beforeResult.isEmpty() || beforeResult.get().rewards() == null) return failure(source, "Initialize the test first.");

        long originalRevision = beforeResult.get().revision();

        RewardClaimResult result = manager.claimReward(playerId, QUEST_ID, MAIN_REWARD_ID, reward -> false);

        if (result != RewardClaimResult.DELIVERY_FAILED) return failure(source, "Expected delivery failure, got: " + result);

        CampaignSavedData.PlayerSnapshot after = data.getPlayerSnapshot(playerId).orElseThrow();

        if (after.revision() != originalRevision) return failure(source, "Failed delivery unexpectedly changed the revision.");

        RewardEntry entry = manager.getReward(playerId, QUEST_ID, MAIN_REWARD_ID).orElseThrow();

        if (entry.isClaimed()) return failure(source, "Failed delivery incorrectly claimed the reward.");

        return success(source, "Failed delivery preserved reward availability and revision.");
    }

    private static int claim(CommandSourceStack source) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());
        RewardManager manager = new RewardManager(data);
        UUID playerId = getTestPlayerId(source);

        ServerPlayer player = source.getPlayerOrException();
        RewardClaimResult result = manager.claimReward(playerId, QUEST_ID, MAIN_REWARD_ID, new MinecraftRewardDelivery(player));

        if (result == RewardClaimResult.SUCCESS) return success(source, "10 XP delivered and claim persisted.");

        return failure(source, "Reward claim rejected: " + result);
    }

    private static int testNestedClaim(CommandSourceStack source) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());
        RewardManager manager = new RewardManager(data);
        UUID playerId = getTestPlayerId(source);
        ServerPlayer player = source.getPlayerOrException();

        boolean[] nestedRejected = {false};

        RewardDelivery delivery = reward ->
        {
            RewardClaimResult nested = manager.claimReward(playerId, QUEST_ID, NESTED_REWARD_ID, ignored -> true);
            nestedRejected[0] = nested == RewardClaimResult.CLAIM_IN_PROGRESS;

            if (!nestedRejected[0]) return false;

            return new MinecraftRewardDelivery(player).deliver(reward);
        };

        RewardClaimResult result = manager.claimReward(playerId, QUEST_ID, NESTED_REWARD_ID, delivery);

        if (!nestedRejected[0]) return failure(source, "Reentrant claim was not blocked.");
        if (result != RewardClaimResult.SUCCESS) return failure(source, "Outer claim failed: " + result);

        return success(source, "Reentrant claim blocked; outer claim delivered 1 XP once.");
    }

    private static int status(CommandSourceStack source) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());
        RewardManager manager = new RewardManager(data);
        UUID playerId = getTestPlayerId(source);

        Optional<CampaignSavedData.PlayerSnapshot> snapshotResult = data.getPlayerSnapshot(playerId);

        if (snapshotResult.isEmpty() || snapshotResult.get().rewards() == null) return failure(source, "Test not initialized.");

        CampaignSavedData.PlayerSnapshot snapshot = snapshotResult.get();

        success(source, "Player revision: " + snapshot.revision());

        for (RewardEntry entry : manager.getPlayerRewardsSnapshot(playerId).getOrDefault(QUEST_ID, List.of()))
        {
            success(source, "Reward: " + entry.rewardId() + " | " + entry.state());
        }

        success(source, "Extra reward group exists: " + snapshot.rewards().hasUnlockedRewardsFromQuest(EXTRA_QUEST_ID));

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
