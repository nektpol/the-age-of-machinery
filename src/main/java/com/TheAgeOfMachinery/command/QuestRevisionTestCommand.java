package com.TheAgeOfMachinery.command;

import com.TheAgeOfMachinery.persistence.CampaignSavedData;
import com.TheAgeOfMachinery.quest.PlayerQuestProgress;
import com.TheAgeOfMachinery.quest.QuestDefinition;
import com.TheAgeOfMachinery.quest.QuestManager;
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

public final class QuestRevisionTestCommand
{
    private static final String QUEST_ID = "theageofmachinery:debug_quest_revision";
    private static final String OBJECTIVE_ID = "main";

    private static final QuestDefinition TEST_QUEST = new QuestDefinition(
        QUEST_ID,
        "Quest Revision Test",
        "Verify revision-checked quest mutations.",
        List.of(new QuestObjective(OBJECTIVE_ID, "Complete three actions", 3, false))
    );

    private QuestRevisionTestCommand()
    {
    }

    public static void register(RegisterCommandsEvent event)
    {
        event.getDispatcher().register(
            Commands.literal("tamquestrevision")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("init").executes(context -> initialize(context.getSource())))
                .then(Commands.literal("stale").executes(context -> testStaleWrite(context.getSource())))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
        );
    }

    private static UUID getTestPlayerId(CommandSourceStack source) throws CommandSyntaxException
    {
        UUID playerId = Objects.requireNonNull(source.getPlayerOrException().getUUID(), "Player UUID cannot be null.");
        String key = "theageofmachinery:quest_revision_test:" + playerId;

        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private static QuestManager createQuestManager(CampaignSavedData data)
    {
        QuestManager manager = new QuestManager(data);
        manager.registerQuest(TEST_QUEST);

        return manager;
    }

    private static int initialize(CommandSourceStack source) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());
        QuestManager manager = createQuestManager(data);

        if (!manager.startFirstQuest(getTestPlayerId(source))) return failure(source, "Test already initialized.");

        return success(source, "Quest revision test initialized.");
    }

    private static int testStaleWrite(CommandSourceStack source) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());
        UUID playerId = getTestPlayerId(source);

        Optional<CampaignSavedData.PlayerSnapshot> snapshotResult = data.getPlayerSnapshot(playerId);

        if (snapshotResult.isEmpty() || snapshotResult.get().quests() == null) return failure(source, "Initialize the test first.");

        CampaignSavedData.PlayerSnapshot oldSnapshot = snapshotResult.get();
        QuestManager manager = createQuestManager(data);

        if (!manager.addObjectiveProgress(playerId, OBJECTIVE_ID, 1)) return failure(source, "Manager could not update the objective.");

        PlayerQuestProgress staleProgress = oldSnapshot.quests();

        if (!staleProgress.addObjectiveProgress(TEST_QUEST, OBJECTIVE_ID, 1)) return failure(source, "Could not prepare stale progress.");

        boolean accepted = data.tryStorePlayerQuestProgress(playerId, oldSnapshot.revision(), staleProgress);

        if (accepted) return failure(source, "Stale quest mutation was incorrectly accepted.");

        CampaignSavedData.PlayerSnapshot latest = data.getPlayerSnapshot(playerId).orElseThrow();
        QuestProgress quest = latest.quests().getQuestProgress(QUEST_ID).orElseThrow();

        if (quest.getObjectiveProgress(OBJECTIVE_ID) != 1) return failure(source, "Latest objective progress was not preserved.");

        return success(source, "Stale quest write rejected; latest progress preserved at 1/3.");
    }

    private static int status(CommandSourceStack source) throws CommandSyntaxException
    {
        CampaignSavedData data = CampaignSavedData.get(source.getServer());
        UUID playerId = getTestPlayerId(source);

        Optional<CampaignSavedData.PlayerSnapshot> result = data.getPlayerSnapshot(playerId);

        if (result.isEmpty() || result.get().quests() == null) return failure(source, "Test is not initialized.");

        CampaignSavedData.PlayerSnapshot snapshot = result.get();
        QuestProgress quest = snapshot.quests().getQuestProgress(QUEST_ID).orElseThrow();

        return success(source, "Revision: " + snapshot.revision() + " | Objective: " + quest.getObjectiveProgress(OBJECTIVE_ID) + "/3");
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
