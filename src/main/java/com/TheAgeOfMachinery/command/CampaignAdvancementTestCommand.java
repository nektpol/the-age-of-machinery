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
import com.TheAgeOfMachinery.reward.ExperienceReward;
import com.TheAgeOfMachinery.reward.ItemReward;
import com.TheAgeOfMachinery.reward.QuestRewardDefinition;
import com.TheAgeOfMachinery.reward.RewardClaimResult;
import com.TheAgeOfMachinery.reward.RewardEntry;
import com.TheAgeOfMachinery.reward.RewardManager;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;

public final class CampaignAdvancementTestCommand
{
    private static final String FIRST_QUEST_ID = "theageofmachinery:debug_advancement_first";
    private static final String SECOND_QUEST_ID = "theageofmachinery:debug_advancement_second";

    private static final String MAIN_ID = "main";
    private static final String OPTIONAL_ID = "optional";
    private static final String SECOND_ID = "second";

    private static final QuestDefinition FIRST_QUEST = new QuestDefinition(
        FIRST_QUEST_ID,
        "Advancement Test One",
        "Verify automatic reward unlocking and campaign advancement.",
        List.of(
            new QuestObjective(MAIN_ID, "Complete two actions", 2, false),
            new QuestObjective(OPTIONAL_ID, "Complete an optional action", 1, true)
        )
    );

    private static final QuestDefinition SECOND_QUEST = new QuestDefinition(
        SECOND_QUEST_ID,
        "Advancement Test Two",
        "Verify completion of the final quest.",
        List.of(new QuestObjective(SECOND_ID, "Complete one action", 1, false))
    );

    private static final QuestRewardDefinition FIRST_REWARDS = new QuestRewardDefinition(
        FIRST_QUEST,
        List.of(
            new ExperienceReward("main_xp", 100),
            new ItemReward("main_iron", "minecraft:iron_ingot", 5)
        ),
        Map.of(
            OPTIONAL_ID, List.of(new ExperienceReward("bonus_xp", 50))
        )
    );

    private static final QuestRewardDefinition SECOND_REWARDS = new QuestRewardDefinition(
        SECOND_QUEST,
        List.of(),
        Map.of()
    );

    private CampaignAdvancementTestCommand()
    {
    }

    public static void register(RegisterCommandsEvent event)
    {
        event.getDispatcher().register(
            Commands.literal("tamadvancetest")
                .requires(source -> source.hasPermission(2))
                .then(createScenarioCommands("basic"))
                .then(createScenarioCommands("bonus"))
                .then(createScenarioCommands("staged"))
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createScenarioCommands(@Nonnull String scenario)
    {
        LiteralArgumentBuilder<CommandSourceStack> commands = Commands.literal(scenario)
            .then(Commands.literal("init").executes(context -> initialize(context.getSource(), scenario)))
            .then(Commands.literal("main").executes(context -> addProgress(context.getSource(), scenario, MAIN_ID)))
            .then(Commands.literal("second").executes(context -> addProgress(context.getSource(), scenario, SECOND_ID)))
            .then(Commands.literal("advance").executes(context -> advance(context.getSource(), scenario)))
            .then(Commands.literal("status").executes(context -> status(context.getSource(), scenario)))
            .then(Commands.literal("claim")
                .then(Commands.literal("xp").executes(context -> claim(context.getSource(), scenario, "main_xp")))
                .then(Commands.literal("iron").executes(context -> claim(context.getSource(), scenario, "main_iron")))
                .then(Commands.literal("bonus").executes(context -> claim(context.getSource(), scenario, "bonus_xp")))
            );

        if (scenario.equals("bonus") || scenario.equals("staged"))
        {
            commands.then(Commands.literal("optional").executes(context -> addProgress(context.getSource(), scenario, OPTIONAL_ID)));
        }
        return commands;
    }

    private static UUID getTestPlayerId(CommandSourceStack source, String scenario) throws CommandSyntaxException
    {
        UUID playerId = Objects.requireNonNull(source.getPlayerOrException().getUUID(), "Player UUID cannot be null.");
        String key = "theageofmachinery:advancement_test:" + scenario + ":" + playerId;

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

    private static int initialize(CommandSourceStack source, String scenario) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());
        CampaignAdvancementService service = createAdvancementService(data);

        if (!service.startFirstQuest(getTestPlayerId(source, scenario))) return failure(source, "Test campaign already started.");

        return success(source, "Test campaign initialized: " + scenario);
    }

    private static int addProgress(CommandSourceStack source, String scenario, String objectiveId) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());
        QuestManager manager = createQuestManager(data);

        if (!manager.addObjectiveProgress(getTestPlayerId(source, scenario), objectiveId, 1))
        {
            return failure(source, "Cannot update objective: " + objectiveId);
        }

        return success(source, "Objective progress updated: " + objectiveId);
    }

    private static int advance(CommandSourceStack source, String scenario) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());
        CampaignAdvancementService service = createAdvancementService(data);

        CampaignAdvancementResult result = service.advanceQuest(getTestPlayerId(source, scenario));

        if (result == CampaignAdvancementResult.NEXT_QUEST_STARTED) return success(source, "Quest advanced. Next quest started and earned rewards unlocked.");
        if (result == CampaignAdvancementResult.CAMPAIGN_COMPLETED) return success(source, "Final quest completed. Campaign finished.");

        return failure(source, "Advancement rejected: " + result);
    }

    private static int claim(CommandSourceStack source, String scenario, String rewardId) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());
        ServerPlayer player = source.getPlayerOrException();

        RewardManager manager = new RewardManager(data);
        MinecraftRewardDelivery delivery = new MinecraftRewardDelivery(player);

        RewardClaimResult result = manager.claimReward(getTestPlayerId(source, scenario), FIRST_QUEST_ID, rewardId, delivery);

        if (result == RewardClaimResult.SUCCESS) return success(source, "Reward claimed: " + rewardId);

        return failure(source, "Reward claim rejected: " + result);
    }

    private static int status(CommandSourceStack source, String scenario) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());
        UUID playerId = getTestPlayerId(source, scenario);

        Optional<PlayerQuestProgress> result = data.getPlayerQuestProgress(playerId);

        if (result.isEmpty()) return failure(source, "Test campaign not initialized.");

        PlayerQuestProgress progress = result.get();

        success(source, "Active quest: " + (progress.hasActiveQuest() ? progress.getActiveQuestId() : "None"));

        Optional<QuestProgress> first = progress.getQuestProgress(FIRST_QUEST_ID);

        if (first.isPresent())
        {
            QuestProgress quest = first.get();
            success(source, "Quest One: " + quest.getState() + " | Main: " + quest.getObjectiveProgress(MAIN_ID) + "/2 | Optional: " + quest.getObjectiveProgress(OPTIONAL_ID) + "/1");
        }

        Optional<QuestProgress> second = progress.getQuestProgress(SECOND_QUEST_ID);

        if (second.isPresent())
        {
            QuestProgress quest = second.get();
            success(source, "Quest Two: " + quest.getState() + " | Main: " + quest.getObjectiveProgress(SECOND_ID) + "/1");
        }

        RewardManager manager = new RewardManager(data);
        List<RewardEntry> rewards = manager.getPlayerRewardsSnapshot(playerId).get(FIRST_QUEST_ID);

        if (rewards == null)
        {
            success(source, "First quest rewards: Not unlocked.");
            return 1;
        }

        for (RewardEntry entry : rewards)
        {
            success(source, "Reward: " + entry.rewardId() + " | Amount: " + entry.reward().amount() + " | State: " + entry.state());
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

