
package com.TheAgeOfMachinery.command;

import com.TheAgeOfMachinery.integration.MinecraftRewardDelivery;
import com.TheAgeOfMachinery.persistence.CampaignSavedData;
import com.TheAgeOfMachinery.quest.CampaignAdvancementResult;
import com.TheAgeOfMachinery.quest.CampaignAdvancementService;
import com.TheAgeOfMachinery.quest.PlayerQuestProgress;
import com.TheAgeOfMachinery.quest.QuestDefinition;
import com.TheAgeOfMachinery.quest.QuestManager;
import com.TheAgeOfMachinery.quest.QuestObjective;
import com.TheAgeOfMachinery.quest.QuestProgress;
import com.TheAgeOfMachinery.quest.QuestState;
import com.TheAgeOfMachinery.reward.ExperienceReward;
import com.TheAgeOfMachinery.reward.PlayerRewardInventory;
import com.TheAgeOfMachinery.reward.QuestRewardDefinition;
import com.TheAgeOfMachinery.reward.RewardClaimResult;
import com.TheAgeOfMachinery.reward.RewardEntry;
import com.TheAgeOfMachinery.reward.RewardManager;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;

public final class CampaignFailureTestCommand
{
    private static final String FIRST_QUEST_ID = "theageofmachinery:failure_first";
    private static final String SECOND_QUEST_ID = "theageofmachinery:failure_second";
    private static final String SIDE_QUEST_ID = "theageofmachinery:failure_side_write";
    private static final String OBJECTIVE_ID = "main";
    private static final String DELIVERY_REWARD_ID = "delivery_xp";
    private static final String INJECTED_EXCEPTION = "Injected failure before reward delivery";

    private static final QuestDefinition FIRST_QUEST = new QuestDefinition(
        FIRST_QUEST_ID,
        "Failure Test One",
        "Verify rejected advancement preserves player state.",
        List.of(new QuestObjective(OBJECTIVE_ID, "Complete one action", 1, false))
    );

    private static final QuestDefinition SECOND_QUEST = new QuestDefinition(
        SECOND_QUEST_ID,
        "Failure Test Two",
        "Verify next-quest activation is not partially committed.",
        List.of(new QuestObjective("second_main", "Complete one action", 1, false))
    );

    private static final QuestRewardDefinition FIRST_REWARDS = new QuestRewardDefinition(
        FIRST_QUEST,
        List.of(new ExperienceReward("earned_xp", 10)),
        Map.of()
    );

    private static final QuestRewardDefinition SECOND_REWARDS = new QuestRewardDefinition(
        SECOND_QUEST,
        List.of(),
        Map.of()
    );

    private CampaignFailureTestCommand()
    {
    }

    public static void register(RegisterCommandsEvent event)
    {
        event.getDispatcher().register(
            Commands.literal("tamfailuretest")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("conflict").executes(context -> testRewardConflict(context.getSource())))
                .then(Commands.literal("stale").executes(context -> testStaleCommit(context.getSource())))
                .then(Commands.literal("delivery").executes(context -> testDeliveryException(context.getSource())))
                .then(Commands.literal("delivery-claim").executes(context -> claimAfterException(context.getSource())))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
        );
    }

    private static UUID getTestPlayerId(CommandSourceStack source, String scenario) throws CommandSyntaxException
    {
        UUID realPlayerId = Objects.requireNonNull(source.getPlayerOrException().getUUID(), "Player UUID cannot be null.");
        String key = "theageofmachinery:failure_test:" + scenario + ":" + realPlayerId;

        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private static QuestManager createQuestManager(CampaignSavedData data)
    {
        QuestManager manager = new QuestManager(data);
        manager.registerQuest(FIRST_QUEST);
        manager.registerQuest(SECOND_QUEST);

        return manager;
    }

    private static CampaignAdvancementService createAdvancementService(CampaignSavedData data)
    {
        CampaignAdvancementService service = new CampaignAdvancementService(data);
        service.registerQuest(FIRST_REWARDS);
        service.registerQuest(SECOND_REWARDS);

        return service;
    }

    private static boolean isFirstQuestActive(CampaignSavedData data, UUID playerId)
    {
        Optional<PlayerQuestProgress> result = data.getPlayerQuestProgress(playerId);

        if (result.isEmpty()) return false;

        PlayerQuestProgress progress = result.get();
        Optional<QuestProgress> active = progress.getActiveQuestProgress();

        if (active.isEmpty()) return false;
        if (!FIRST_QUEST_ID.equals(active.get().getQuestId())) return false;
        if (active.get().getState() != QuestState.ACTIVE) return false;
        if (progress.getQuestProgress(SECOND_QUEST_ID).isPresent()) return false;

        return true;
    }

    private static int testRewardConflict(CommandSourceStack source) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());
        UUID playerId = getTestPlayerId(source, "conflict");

        CampaignAdvancementService service = createAdvancementService(data);
        QuestManager questManager = createQuestManager(data);
        RewardManager rewardManager = new RewardManager(data);

        if (!service.startFirstQuest(playerId)) return failure(source, "Conflict test already initialized.");
        if (!questManager.addObjectiveProgress(playerId, OBJECTIVE_ID, 1)) return failure(source, "Could not complete test objective.");

        if (!rewardManager.unlockQuestRewards(playerId, FIRST_QUEST_ID, List.of(new ExperienceReward("injected_xp", 5))))
        {
            return failure(source, "Could not inject conflicting reward group.");
        }

        CampaignSavedData.PlayerSnapshot before = data.getPlayerSnapshot(playerId).orElseThrow();

        CampaignAdvancementResult result = service.advanceQuest(playerId);

        if (result != CampaignAdvancementResult.REWARDS_ALREADY_UNLOCKED) return failure(source, "Unexpected advancement result: " + result);

        CampaignSavedData.PlayerSnapshot after = data.getPlayerSnapshot(playerId).orElseThrow();

        if (after.revision() != before.revision()) return failure(source, "Rejected advancement unexpectedly changed the revision.");
        if (!isFirstQuestActive(data, playerId)) return failure(source, "Rejected advancement changed quest lifecycle state.");
        if (after.rewards() == null || !after.rewards().hasUnlockedRewardsFromQuest(FIRST_QUEST_ID)) return failure(source, "Injected reward group was lost.");

        return success(source, "PASS: Conflicting rewards rejected advancement without changing quest state.");
    }

    private static int testStaleCommit(CommandSourceStack source) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());
        UUID playerId = getTestPlayerId(source, "stale");

        CampaignAdvancementService service = createAdvancementService(data);
        QuestManager questManager = createQuestManager(data);
        RewardManager rewardManager = new RewardManager(data);

        if (!service.startFirstQuest(playerId)) return failure(source, "Stale test already initialized.");
        if (!questManager.addObjectiveProgress(playerId, OBJECTIVE_ID, 1)) return failure(source, "Could not complete test objective.");

        CampaignSavedData.PlayerSnapshot original = data.getPlayerSnapshot(playerId).orElseThrow();

        PlayerQuestProgress stagedQuests = original.quests();
        PlayerRewardInventory stagedRewards = original.rewards() == null ? new PlayerRewardInventory(playerId) : original.rewards();

        if (!stagedRewards.unlockRewards(FIRST_QUEST_ID, List.of(new ExperienceReward("earned_xp", 10))))
        {
            return failure(source, "Could not prepare staged rewards.");
        }

        if (!stagedQuests.completeActiveQuest(FIRST_QUEST)) return failure(source, "Could not prepare staged quest completion.");
        if (!stagedQuests.activateQuest(SECOND_QUEST)) return failure(source, "Could not prepare staged next quest.");

        // Inject an intervening write after preparing the advancement.
        if (!rewardManager.unlockQuestRewards(playerId, SIDE_QUEST_ID, List.of(new ExperienceReward("side_xp", 2))))
        {
            return failure(source, "Could not inject intervening reward write.");
        }

        CampaignSavedData.PlayerSnapshot newer = data.getPlayerSnapshot(playerId).orElseThrow();

        boolean committed = data.commitAdvancement(playerId, original.revision(), stagedQuests, stagedRewards);

        if (committed) return failure(source, "Unsafe: stale advancement commit was accepted.");

        CampaignSavedData.PlayerSnapshot after = data.getPlayerSnapshot(playerId).orElseThrow();

        if (after.revision() != newer.revision()) return failure(source, "Rejected commit unexpectedly changed the revision.");
        if (!isFirstQuestActive(data, playerId)) return failure(source, "Stale commit changed quest lifecycle state.");
        if (after.rewards() == null || !after.rewards().hasUnlockedRewardsFromQuest(SIDE_QUEST_ID)) return failure(source, "Intervening reward write was lost.");
        if (after.rewards().hasUnlockedRewardsFromQuest(FIRST_QUEST_ID)) return failure(source, "Uncommitted advancement rewards became persistent.");

        return success(source, "PASS: Stale advancement rejected; newer reward data preserved.");
    }

    private static int testDeliveryException(CommandSourceStack source) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());
        UUID playerId = getTestPlayerId(source, "delivery");
        RewardManager manager = new RewardManager(data);

        if (!manager.unlockQuestRewards(playerId, FIRST_QUEST_ID, List.of(new ExperienceReward(DELIVERY_REWARD_ID, 1))))
        {
            return failure(source, "Delivery exception test already initialized.");
        }

        long beforeRevision = data.getPlayerSnapshot(playerId).orElseThrow().revision();
        boolean expectedExceptionCaught = false;

        try
        {
            manager.claimReward(playerId, FIRST_QUEST_ID, DELIVERY_REWARD_ID, reward ->
            {
                throw new IllegalStateException(INJECTED_EXCEPTION);
            });
        }
        catch (IllegalStateException exception)
        {
            if (!INJECTED_EXCEPTION.equals(exception.getMessage())) throw exception;

            expectedExceptionCaught = true;
        }

        if (!expectedExceptionCaught) return failure(source, "Expected delivery exception did not occur.");

        // This failed delivery performs no external side effects.
        // A second attempt tests whether the claim guard was released.
        RewardClaimResult retry = manager.claimReward(playerId, FIRST_QUEST_ID, DELIVERY_REWARD_ID, reward -> false);

        if (retry != RewardClaimResult.DELIVERY_FAILED) return failure(source, "Claim guard was not released correctly: " + retry);

        long afterRevision = data.getPlayerSnapshot(playerId).orElseThrow().revision();
        RewardEntry entry = manager.getReward(playerId, FIRST_QUEST_ID, DELIVERY_REWARD_ID).orElseThrow();

        if (afterRevision != beforeRevision) return failure(source, "Failed delivery unexpectedly changed the revision.");
        if (entry.isClaimed()) return failure(source, "Failed delivery incorrectly marked reward as claimed.");

        return success(source, "PASS: Pre-delivery exception released claim guard and preserved reward availability.");
    }

    private static int claimAfterException(CommandSourceStack source) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());
        UUID playerId = getTestPlayerId(source, "delivery");

        RewardManager manager = new RewardManager(data);
        RewardClaimResult result = manager.claimReward(playerId, FIRST_QUEST_ID, DELIVERY_REWARD_ID, new MinecraftRewardDelivery(source.getPlayerOrException()));

        if (result != RewardClaimResult.SUCCESS) return failure(source, "Reward could not be claimed: " + result);

        return success(source, "PASS: Reward successfully claimed after previous delivery exception. Granted 1 XP.");
    }

    private static int status(CommandSourceStack source) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());

        for (String scenario : List.of("conflict", "stale", "delivery"))
        {
            UUID playerId = getTestPlayerId(source, scenario);
            Optional<CampaignSavedData.PlayerSnapshot> result = data.getPlayerSnapshot(playerId);

            if (result.isEmpty())
            {
                success(source, scenario + ": Not initialized.");
                continue;
            }

            CampaignSavedData.PlayerSnapshot snapshot = result.get();
            success(source, scenario + ": Revision " + snapshot.revision());

            if (snapshot.quests() != null && snapshot.quests().getActiveQuestProgress().isPresent())
            {
                success(source, scenario + ": Active quest " + snapshot.quests().getActiveQuestProgress().orElseThrow().getQuestId());
            }

            if (scenario.equals("delivery"))
            {
                RewardManager manager = new RewardManager(data);
                Optional<RewardEntry> entry = manager.getReward(playerId, FIRST_QUEST_ID, DELIVERY_REWARD_ID);

                if (entry.isPresent()) success(source, scenario + ": Reward " + entry.get().state());
            }
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
