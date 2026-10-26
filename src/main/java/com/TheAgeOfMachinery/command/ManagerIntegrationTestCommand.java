package com.TheAgeOfMachinery.command;

import com.TheAgeOfMachinery.integration.MinecraftRewardDelivery;
import com.TheAgeOfMachinery.persistence.CampaignSavedData;
import com.TheAgeOfMachinery.quest.PlayerQuestProgress;
import com.TheAgeOfMachinery.quest.QuestDefinition;
import com.TheAgeOfMachinery.quest.QuestManager;
import com.TheAgeOfMachinery.quest.QuestObjective;
import com.TheAgeOfMachinery.quest.QuestProgress;
import com.TheAgeOfMachinery.reward.ExperienceReward;
import com.TheAgeOfMachinery.reward.ItemReward;
import com.TheAgeOfMachinery.reward.RewardClaimResult;
import com.TheAgeOfMachinery.reward.RewardDefinition;
import com.TheAgeOfMachinery.reward.RewardEntry;
import com.TheAgeOfMachinery.reward.RewardManager;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ManagerIntegrationTestCommand
{
    private static final String FIRST_QUEST_ID = "theageofmachinery:debug_manager_first";
    private static final String SECOND_QUEST_ID = "theageofmachinery:debug_manager_second";

    private static final String MAIN_OBJECTIVE_ID = "main";
    private static final String OPTIONAL_OBJECTIVE_ID = "optional";
    private static final String SECOND_OBJECTIVE_ID = "second_main";

    private static final QuestObjective MAIN_OBJECTIVE = new QuestObjective(MAIN_OBJECTIVE_ID, "Perform three test actions", 3, false);
    private static final QuestObjective OPTIONAL_OBJECTIVE = new QuestObjective(OPTIONAL_OBJECTIVE_ID, "Perform two optional actions", 2, true);

    private static final QuestDefinition FIRST_QUEST = new QuestDefinition(FIRST_QUEST_ID, "Manager Test One", "Verify persistent manager coordination.", List.of(MAIN_OBJECTIVE, OPTIONAL_OBJECTIVE));
    private static final QuestDefinition SECOND_QUEST = new QuestDefinition(SECOND_QUEST_ID, "Manager Test Two", "Verify activation of the next quest.", List.of(new QuestObjective(SECOND_OBJECTIVE_ID, "Perform one test action", 1, false)));

    private ManagerIntegrationTestCommand()
    {
    }

    public static void register(RegisterCommandsEvent event)
    {
        event.getDispatcher().register(
            Commands.literal("tammanagertest")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("init").executes(context -> initialize(context.getSource())))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("progress")
                    .then(Commands.literal("main").executes(context -> addProgress(context.getSource(), MAIN_OBJECTIVE_ID)))
                    .then(Commands.literal("optional").executes(context -> addProgress(context.getSource(), OPTIONAL_OBJECTIVE_ID)))
                    .then(Commands.literal("second").executes(context -> addProgress(context.getSource(), SECOND_OBJECTIVE_ID)))
                )
                .then(Commands.literal("complete").executes(context -> complete(context.getSource())))
                .then(Commands.literal("unlock").executes(context -> unlock(context.getSource())))
                .then(Commands.literal("next").executes(context -> activateNext(context.getSource())))
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
        String testKey = "theageofmachinery:manager_integration_test:" + playerId;

        return UUID.nameUUIDFromBytes(testKey.getBytes(StandardCharsets.UTF_8));
    }

    private static QuestManager createQuestManager(CampaignSavedData data)
    {
        QuestManager manager = new QuestManager(data);
        manager.registerQuest(FIRST_QUEST);
        manager.registerQuest(SECOND_QUEST);

        return manager;
    }

    private static int initialize(CommandSourceStack source) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());
        QuestManager questManager = createQuestManager(data);

        if (!questManager.startFirstQuest(getTestPlayerId(source))) return failure(source, "Could not start the first test quest.");

        return success(source, "First test quest activated.");
    }

    private static int addProgress(CommandSourceStack source, String objectiveId) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());
        QuestManager questManager = createQuestManager(data);

        if (!questManager.addObjectiveProgress(getTestPlayerId(source), objectiveId, 1)) return failure(source, "Could not update objective: " + objectiveId);

        return success(source, "Objective updated: " + objectiveId);
    }

    private static int complete(CommandSourceStack source) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());
        QuestManager questManager = createQuestManager(data);

        if (!questManager.completeActiveQuest(getTestPlayerId(source))) return failure(source, "Active quest is not ready for completion.");

        return success(source, "Active quest completed.");
    }

    private static int unlock(CommandSourceStack source) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());
        UUID playerId = getTestPlayerId(source);

        Optional<PlayerQuestProgress> playerResult = data.getPlayerQuestProgress(playerId);

        if (playerResult.isEmpty()) return failure(source, "Test player progress not found.");

        PlayerQuestProgress playerProgress = playerResult.get();

        if (!playerProgress.hasCompletedQuest(FIRST_QUEST_ID)) return failure(source, "Complete the first test quest before unlocking rewards.");

        Optional<QuestProgress> questResult = playerProgress.getQuestProgress(FIRST_QUEST_ID);

        if (questResult.isEmpty()) return failure(source, "First test quest progress not found.");

        QuestProgress questProgress = questResult.get();

        List<RewardDefinition> rewards = new ArrayList<>();
        rewards.add(new ExperienceReward("main_xp", 100));
        rewards.add(new ItemReward("main_iron", "minecraft:iron_ingot", 5));

        if (questProgress.isObjectiveComplete(OPTIONAL_OBJECTIVE))
        {
            rewards.add(new ExperienceReward("bonus_xp", 50));
        }

        RewardManager rewardManager = new RewardManager(data);

        if (!rewardManager.unlockQuestRewards(playerId, FIRST_QUEST_ID, rewards)) return failure(source, "Rewards have already been unlocked.");

        return success(source, "Earned rewards unlocked through RewardManager.");
    }

    private static int activateNext(CommandSourceStack source) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());
        QuestManager questManager = createQuestManager(data);

        if (!questManager.activateNextQuest(getTestPlayerId(source), FIRST_QUEST_ID)) return failure(source, "Could not activate the second quest.");

        return success(source, "Second test quest activated.");
    }

    private static int claim(CommandSourceStack source, String rewardId) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());
        UUID playerId = getTestPlayerId(source);
        ServerPlayer player = source.getPlayerOrException();

        RewardManager rewardManager = new RewardManager(data);
        MinecraftRewardDelivery delivery = new MinecraftRewardDelivery(player);

        RewardClaimResult result = rewardManager.claimReward(playerId, FIRST_QUEST_ID, rewardId, delivery);

        if (result == RewardClaimResult.SUCCESS) return success(source, "Reward claimed: " + rewardId);
        if (result == RewardClaimResult.ALREADY_CLAIMED) return failure(source, "Reward already claimed: " + rewardId);
        if (result == RewardClaimResult.DELIVERY_FAILED) return failure(source, "Reward delivery failed.");

        return failure(source, "Reward not found or not earned: " + rewardId);
    }

    private static int status(CommandSourceStack source) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());
        UUID playerId = getTestPlayerId(source);

        Optional<PlayerQuestProgress> playerResult = data.getPlayerQuestProgress(playerId);

        if (playerResult.isEmpty()) return failure(source, "No test player progression found.");

        PlayerQuestProgress progress = playerResult.get();

        String activeQuestId = progress.getActiveQuestId();
        success(source, "Active quest: " + (activeQuestId == null ? "None" : activeQuestId));

        Optional<QuestProgress> firstQuest = progress.getQuestProgress(FIRST_QUEST_ID);

        if (firstQuest.isPresent())
        {
            QuestProgress quest = firstQuest.get();
            success(source, "Quest One: " + quest.getState() + " | Main: " + quest.getObjectiveProgress(MAIN_OBJECTIVE_ID) + "/3 | Optional: " + quest.getObjectiveProgress(OPTIONAL_OBJECTIVE_ID) + "/2");
        }

        Optional<QuestProgress> secondQuest = progress.getQuestProgress(SECOND_QUEST_ID);

        if (secondQuest.isPresent())
        {
            QuestProgress quest = secondQuest.get();
            success(source, "Quest Two: " + quest.getState() + " | Main: " + quest.getObjectiveProgress(SECOND_OBJECTIVE_ID) + "/1");
        }

        RewardManager rewardManager = new RewardManager(data);
        Map<String, List<RewardEntry>> groups = rewardManager.getPlayerRewardsSnapshot(playerId);
        List<RewardEntry> rewards = groups.get(FIRST_QUEST_ID);

        if (rewards == null)
        {
            success(source, "Rewards: Not unlocked.");
            return 1;
        }

        for (RewardEntry entry : rewards)
        {
            success(source, "Reward: " + entry.rewardId() + " | " + entry.reward().amount() + " | " + entry.state());
        }

        return 1;
    }

    private static int success(CommandSourceStack source, String text)
    {
        Component message = Objects.requireNonNull(Component.literal(text), "Success message cannot be null.");

        source.sendSuccess(() -> message, false);
        return 1;
    }

    private static int failure(CommandSourceStack source, String text)
    {
        Component message = Objects.requireNonNull(Component.literal(text), "Failure message cannot be null.");

        source.sendFailure(message);
        return 0;
    }
}
