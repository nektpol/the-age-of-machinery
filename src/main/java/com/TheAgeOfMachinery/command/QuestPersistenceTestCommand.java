
package com.TheAgeOfMachinery.command;

import com.TheAgeOfMachinery.persistence.CampaignSavedData;
import com.TheAgeOfMachinery.quest.PlayerQuestProgress;
import com.TheAgeOfMachinery.quest.QuestDefinition;
import com.TheAgeOfMachinery.quest.QuestObjective;
import com.TheAgeOfMachinery.quest.QuestProgress;

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

public final class QuestPersistenceTestCommand
{
    private static final String TEST_QUEST_ID = "theageofmachinery:debug_persistence_test";
    private static final String MAIN_OBJECTIVE_ID = "main";
    private static final String OPTIONAL_OBJECTIVE_ID = "optional";

    private static final QuestDefinition TEST_QUEST = new QuestDefinition(
        TEST_QUEST_ID,
        "Quest Persistence Test",
        "Temporary quest used to verify Minecraft world saving.",
        List.of(
            new QuestObjective(MAIN_OBJECTIVE_ID, "Complete five test actions", 5, false),
            new QuestObjective(OPTIONAL_OBJECTIVE_ID, "Complete two optional actions", 2, true)
        )
    );

    private QuestPersistenceTestCommand()
    {
    }

    public static void register(RegisterCommandsEvent event)
    {
        event.getDispatcher().register(
            Commands.literal("tamquestsave")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("init").executes(context -> initialize(context.getSource())))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("progress")
                    .then(Commands.literal("main").executes(context -> addProgress(context.getSource(), MAIN_OBJECTIVE_ID, 2)))
                    .then(Commands.literal("optional").executes(context -> addProgress(context.getSource(), OPTIONAL_OBJECTIVE_ID, 1)))
                )
                .then(Commands.literal("complete").executes(context -> complete(context.getSource())))
        );
    }

    private static UUID getTestPlayerId(CommandSourceStack source) throws CommandSyntaxException
    {
        UUID playerId = Objects.requireNonNull(source.getPlayerOrException().getUUID(), "Player UUID cannot be null.");
        String testKey = "theageofmachinery:persistence_test:" + playerId;

        return UUID.nameUUIDFromBytes(testKey.getBytes(StandardCharsets.UTF_8));
    }

    private static int initialize(CommandSourceStack source) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());
        UUID testPlayerId = getTestPlayerId(source);

        if (data.getPlayerQuestProgress(testPlayerId).isPresent()) return failure(source, "Test quest data already exists.");

        PlayerQuestProgress progress = new PlayerQuestProgress(testPlayerId);

        if (!progress.activateQuest(TEST_QUEST)) return failure(source, "Could not activate test quest.");

        data.storePlayerQuestProgress(progress);

        return success(source, "Test quest created and activated.");
    }

    private static int addProgress(CommandSourceStack source, String objectiveId, int amount) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());
        Optional<PlayerQuestProgress> result = data.getPlayerQuestProgress(getTestPlayerId(source));

        if (result.isEmpty()) return failure(source, "Test quest not initialized.");

        PlayerQuestProgress progress = result.get();

        if (!progress.addObjectiveProgress(TEST_QUEST, objectiveId, amount)) return failure(source, "Objective progress could not be updated.");

        data.storePlayerQuestProgress(progress);

        return success(source, "Objective progress updated.");
    }

    private static int complete(CommandSourceStack source) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());
        Optional<PlayerQuestProgress> result = data.getPlayerQuestProgress(getTestPlayerId(source));

        if (result.isEmpty()) return failure(source, "Test quest not initialized.");

        PlayerQuestProgress progress = result.get();

        if (!progress.completeActiveQuest(TEST_QUEST)) return failure(source, "Main objective is not complete.");

        data.storePlayerQuestProgress(progress);

        return success(source, "Test quest completed.");
    }

    private static int status(CommandSourceStack source) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());
        Optional<PlayerQuestProgress> result = data.getPlayerQuestProgress(getTestPlayerId(source));

        if (result.isEmpty()) return failure(source, "No saved test quest data found.");

        PlayerQuestProgress playerProgress = result.get();
        Optional<QuestProgress> questResult = playerProgress.getQuestProgress(TEST_QUEST_ID);

        if (questResult.isEmpty()) return failure(source, "Saved test quest is missing.");

        QuestProgress quest = questResult.get();

        String message = "State: " + quest.getState()
            + " | Main: " + quest.getObjectiveProgress(MAIN_OBJECTIVE_ID) + "/5"
            + " | Optional: " + quest.getObjectiveProgress(OPTIONAL_OBJECTIVE_ID) + "/2";

        return success(source, message);
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

