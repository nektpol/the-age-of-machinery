package com.TheAgeOfMachinery.command;

import com.TheAgeOfMachinery.persistence.CampaignSavedData;
import com.TheAgeOfMachinery.quest.PlayerQuestProgress;
import com.TheAgeOfMachinery.quest.QuestDefinition;
import com.TheAgeOfMachinery.quest.QuestManager;
import com.TheAgeOfMachinery.quest.QuestObjective;
import com.TheAgeOfMachinery.quest.QuestProgress;
import com.TheAgeOfMachinery.reward.ExperienceReward;
import com.TheAgeOfMachinery.reward.PlayerRewardInventory;
import com.TheAgeOfMachinery.reward.RewardManager;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;

public final class StateIsolationTestCommand
{
    private static final String QUEST_ID = "theageofmachinery:debug_state_isolation";
    private static final String OBJECTIVE_ID = "main";
    private static final String REWARD_QUEST_ID = "theageofmachinery:debug_isolation_rewards";
    private static final String LOCAL_ONLY_ID = "theageofmachinery:debug_uncommitted_rewards";

    private static final QuestDefinition TEST_QUEST = new QuestDefinition(
        QUEST_ID,
        "State Isolation Test",
        "Verify detached player state and revision checks.",
        List.of(new QuestObjective(OBJECTIVE_ID, "Complete three actions", 3, false))
    );

    private StateIsolationTestCommand()
    {
    }

    public static void register(RegisterCommandsEvent event)
    {
        event.getDispatcher().register(
            Commands.literal("tamstatetest")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("init").executes(context -> initialize(context.getSource())))
                .then(Commands.literal("detached").executes(context -> testDetachedQuestState(context.getSource())))
                .then(Commands.literal("stale").executes(context -> testStaleCommit(context.getSource())))
                .then(Commands.literal("rewards").executes(context -> testRewardIsolation(context.getSource())))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
        );
    }

    private static UUID getTestPlayerId(CommandSourceStack source) throws CommandSyntaxException
    {
        UUID playerId = Objects.requireNonNull(source.getPlayerOrException().getUUID(), "Player UUID cannot be null.");
        String key = "theageofmachinery:state_isolation_test:" + playerId;

        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private static QuestManager createQuestManager(CampaignSavedData data)
    {
        QuestManager manager = new QuestManager(data);
        manager.registerQuest(TEST_QUEST);

        return manager;
    }

    private static int getMainProgress(PlayerQuestProgress player)
    {
        Optional<QuestProgress> quest = player.getQuestProgress(QUEST_ID);

        if (quest.isEmpty()) throw new IllegalStateException("Isolation test quest is missing.");

        return quest.get().getObjectiveProgress(OBJECTIVE_ID);
    }

    private static int initialize(CommandSourceStack source) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());
        QuestManager manager = createQuestManager(data);

        if (!manager.startFirstQuest(getTestPlayerId(source))) return failure(source, "Test already initialized.");

        return success(source, "State isolation test initialized.");
    }

    private static int testDetachedQuestState(CommandSourceStack source) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());
        UUID playerId = getTestPlayerId(source);

        Optional<PlayerQuestProgress> result = data.getPlayerQuestProgress(playerId);

        if (result.isEmpty()) return failure(source, "Initialize the test first.");

        PlayerQuestProgress detached = result.get();
        int originalValue = getMainProgress(detached);

        if (originalValue != 0) return failure(source, "Expected fresh progress of 0/3.");

        if (!detached.addObjectiveProgress(TEST_QUEST, OBJECTIVE_ID, 1)) return failure(source, "Could not modify detached quest progress.");

        int persistedValue = getMainProgress(data.getPlayerQuestProgress(playerId).orElseThrow());

        if (persistedValue != originalValue) return failure(source, "Unsafe mutation detected: detached changes affected saved state.");

        data.storePlayerQuestProgress(detached);

        if (!detached.addObjectiveProgress(TEST_QUEST, OBJECTIVE_ID, 1)) return failure(source, "Could not modify the original input after storing.");

        persistedValue = getMainProgress(data.getPlayerQuestProgress(playerId).orElseThrow());

        if (persistedValue != 1) return failure(source, "Unsafe mutation detected: storage retained the caller's mutable object.");

        return success(source, "Copy-on-read and copy-on-write passed. Persisted progress: 1/3.");
    }

    private static int testStaleCommit(CommandSourceStack source) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());
        UUID playerId = getTestPlayerId(source);

        Optional<CampaignSavedData.PlayerSnapshot> result = data.getPlayerSnapshot(playerId);

        if (result.isEmpty() || result.get().quests() == null) return failure(source, "Initialize the test first.");

        CampaignSavedData.PlayerSnapshot staleSnapshot = result.get();

        if (getMainProgress(staleSnapshot.quests()) != 1) return failure(source, "Run the detached test before the stale test.");

        QuestManager manager = createQuestManager(data);

        if (!manager.addObjectiveProgress(playerId, OBJECTIVE_ID, 1)) return failure(source, "Could not perform the newer progression update.");

        PlayerRewardInventory stagedRewards = staleSnapshot.rewards() == null ? new PlayerRewardInventory(playerId) : staleSnapshot.rewards();

        boolean committed = data.commitAdvancement(playerId, staleSnapshot.revision(), staleSnapshot.quests(), stagedRewards);

        if (committed) return failure(source, "Unsafe commit detected: a stale revision was accepted.");

        int persistedValue = getMainProgress(data.getPlayerQuestProgress(playerId).orElseThrow());

        if (persistedValue != 2) return failure(source, "The newer progression update was lost.");

        return success(source, "Stale commit rejected. Latest progress preserved: 2/3.");
    }

    private static int testRewardIsolation(CommandSourceStack source) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());
        UUID playerId = getTestPlayerId(source);

        if (data.getPlayerQuestProgress(playerId).isEmpty()) return failure(source, "Initialize the test first.");

        RewardManager manager = new RewardManager(data);

        if (!manager.unlockQuestRewards(playerId, REWARD_QUEST_ID, List.of(new ExperienceReward("test_xp", 10))))
        {
            return failure(source, "Test rewards already unlocked.");
        }

        PlayerRewardInventory detached = data.getPlayerRewardInventory(playerId).orElseThrow();

        if (!detached.unlockRewards(LOCAL_ONLY_ID, List.of(new ExperienceReward("local_xp", 1))))
        {
            return failure(source, "Could not modify detached reward inventory.");
        }

        PlayerRewardInventory persisted = data.getPlayerRewardInventory(playerId).orElseThrow();

        if (persisted.hasUnlockedRewardsFromQuest(LOCAL_ONLY_ID)) return failure(source, "Unsafe reward mutation detected.");

        data.storePlayerRewardInventory(detached);

        if (!detached.unlockRewards("theageofmachinery:debug_after_store", List.of(new ExperienceReward("after_store_xp", 1))))
        {
            return failure(source, "Could not modify the original reward inventory after storing.");
        }

        persisted = data.getPlayerRewardInventory(playerId).orElseThrow();

        if (persisted.hasUnlockedRewardsFromQuest("theageofmachinery:debug_after_store")) return failure(source, "Unsafe reward reference retained after storing.");

        return success(source, "Reward inventory copy-on-read and copy-on-write passed.");
    }

    private static int status(CommandSourceStack source) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());
        UUID playerId = getTestPlayerId(source);

        Optional<CampaignSavedData.PlayerSnapshot> result = data.getPlayerSnapshot(playerId);

        if (result.isEmpty()) return failure(source, "Test not initialized.");

        CampaignSavedData.PlayerSnapshot snapshot = result.get();

        success(source, "Revision: " + snapshot.revision());

        if (snapshot.quests() != null)
        {
            success(source, "Quest progress: " + getMainProgress(snapshot.quests()) + "/3");
        }

        if (snapshot.rewards() != null)
        {
            success(source, "Test reward group exists: " + snapshot.rewards().hasUnlockedRewardsFromQuest(REWARD_QUEST_ID));
            success(source, "Uncommitted reward group exists: " + snapshot.rewards().hasUnlockedRewardsFromQuest(LOCAL_ONLY_ID));
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

