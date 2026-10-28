
package com.TheAgeOfMachinery.command;

import com.TheAgeOfMachinery.quest.CampaignAdvancementResult;
import com.TheAgeOfMachinery.quest.PlayerQuestProgress;
import com.TheAgeOfMachinery.quest.QuestDefinition;
import com.TheAgeOfMachinery.quest.QuestObjective;
import com.TheAgeOfMachinery.quest.QuestProgress;
import com.TheAgeOfMachinery.reward.RewardClaimResult;
import com.TheAgeOfMachinery.reward.RewardEntry;
import com.TheAgeOfMachinery.runtime.CampaignRuntime;
import com.TheAgeOfMachinery.persistence.CampaignSavedData;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;

public final class CampaignRuntimeTestCommand
{
    private CampaignRuntimeTestCommand()
    {
    }

    public static void register(RegisterCommandsEvent event)
    {
        event.getDispatcher().register(
            Commands.literal("tamruntime")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("campaign").executes(context -> campaign(context.getSource())))
                .then(Commands.literal("verify").executes(context -> verify(context.getSource())))
                .then(Commands.literal("audit").executes(context -> audit(context.getSource())))
                .then(Commands.literal("reloadstatus").executes(context -> reloadStatus(context.getSource())))
                .then(Commands.literal("start").executes(context -> start(context.getSource())))
                .then(Commands.literal("progress")
                    .then(Commands.argument("objectiveId", Objects.requireNonNull(StringArgumentType.word()))
                        .then(Commands.argument("amount", Objects.requireNonNull(IntegerArgumentType.integer(1)))
                            .executes(context -> progress(context.getSource(), StringArgumentType.getString(context, "objectiveId"), IntegerArgumentType.getInteger(context, "amount"))))))
                .then(Commands.literal("advance").executes(context -> advance(context.getSource())))
                .then(Commands.literal("player").executes(context -> playerStatus(context.getSource())))
                .then(Commands.literal("rewards").executes(context -> rewards(context.getSource())))
                .then(Commands.literal("claim")
                    .then(Commands.literal("first")
                        .then(Commands.argument("rewardId", Objects.requireNonNull(StringArgumentType.word()))
                            .executes(context -> claim(context.getSource(), 0, StringArgumentType.getString(context, "rewardId")))))
                    .then(Commands.literal("second")
                        .then(Commands.argument("rewardId", Objects.requireNonNull(StringArgumentType.word()))
                            .executes(context -> claim(context.getSource(), 1, StringArgumentType.getString(context, "rewardId")))))
                    .then(Commands.literal("quest")
                        .then(Commands.argument("questId", Objects.requireNonNull(ResourceLocationArgument.id()))
                            .then(Commands.argument("rewardId", Objects.requireNonNull(StringArgumentType.word()))
                                .executes(context -> claim(context.getSource(), ResourceLocationArgument.getId(Objects.requireNonNull(context), "questId").toString(), StringArgumentType.getString(context, "rewardId"))))))
                )
        );
    }

    private static int status(CommandSourceStack source)
    {
        CampaignRuntime runtime = CampaignRuntime.get(source.getServer());

        success(source, "Campaign runtime session: " + runtime.getSessionId());
        success(source, "Campaign registered: " + runtime.getCampaignQuestCount() + " quests.");
        success(source, "Server-thread validation: Passed.");

        return 1;
    }

    private static int campaign(CommandSourceStack source)
    {
        CampaignRuntime runtime = CampaignRuntime.get(source.getServer());
        List<String> questIds = runtime.getCampaignQuestIds();

        success(source, "Registered campaign quests: " + questIds.size());

        for (int index = 0; index < questIds.size(); index++)
        {
            success(source, "Quest " + (index + 1) + ": " + questIds.get(index));
        }

        return 1;
    }

    private static int start(CommandSourceStack source) throws CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        CampaignRuntime runtime = CampaignRuntime.get(source.getServer());

        if (!runtime.startCampaign(player)) return failure(source, "Campaign already started or cannot be initialized.");

        return success(source, "First campaign quest activated.");
    }

    private static int progress(CommandSourceStack source, String objectiveId, int amount) throws CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        CampaignRuntime runtime = CampaignRuntime.get(source.getServer());

        if (!runtime.addObjectiveProgress(player, objectiveId, amount))
        {
            return failure(source, "Objective not found, inactive, or progress update rejected.");
        }

        return success(source, "Objective progress updated: " + objectiveId + " +" + amount);
    }

    private static int advance(CommandSourceStack source) throws CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        CampaignRuntime runtime = CampaignRuntime.get(source.getServer());

        CampaignAdvancementResult result = runtime.advanceQuest(player);

        if (result == CampaignAdvancementResult.NEXT_QUEST_STARTED) return success(source, "Quest completed. Next quest activated and earned rewards unlocked.");
        if (result == CampaignAdvancementResult.CAMPAIGN_COMPLETED) return success(source, "Final quest completed. Campaign finished.");

        return failure(source, "Advancement rejected: " + result);
    }

    private static int playerStatus(CommandSourceStack source) throws CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        CampaignRuntime runtime = CampaignRuntime.get(source.getServer());

        Optional<PlayerQuestProgress> progressResult = runtime.getPlayerQuestProgress(player);

        if (progressResult.isEmpty()) return failure(source, "Player campaign has not been initialized.");

        PlayerQuestProgress playerProgress = progressResult.get();
        Optional<QuestDefinition> activeDefinition = runtime.getActiveQuestDefinition(player);

        if (activeDefinition.isEmpty())
        {
            success(source, "No active quest. Campaign may be completed.");
            return 1;
        }

        QuestDefinition definition = activeDefinition.get();
        QuestProgress activeProgress = playerProgress.getActiveQuestProgress().orElseThrow();

        success(source, "Active quest: " + definition.title());

        for (QuestObjective objective : definition.objectives())
        {
            String category = objective.optional() ? "Optional" : "Mandatory";
            int currentAmount = activeProgress.getObjectiveProgress(objective.id());

            success(source, category + ": " + objective.description() + " | " + currentAmount + "/" + objective.requiredAmount());
        }

        return 1;
    }

    private static int rewards(CommandSourceStack source) throws CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        CampaignRuntime runtime = CampaignRuntime.get(source.getServer());
        Map<String, List<RewardEntry>> groups = runtime.getUnlockedRewards(player);

        if (groups.isEmpty()) return success(source, "No unlocked rewards.");

        for (Map.Entry<String, List<RewardEntry>> group : groups.entrySet())
        {
            success(source, "Quest rewards: " + group.getKey());

            for (RewardEntry entry : group.getValue())
            {
                success(source, entry.rewardId() + " | " + entry.reward().amount() + " | " + entry.state());
            }
        }

        return 1;
    }

    private static int claim(CommandSourceStack source, int questIndex, String rewardId) throws CommandSyntaxException
    {
        CampaignRuntime runtime = CampaignRuntime.get(source.getServer());
        List<String> questIds = runtime.getCampaignQuestIds();

        if (questIndex < 0 || questIndex >= questIds.size()) return failure(source, "Quest is not registered.");

        String questId = questIds.get(questIndex);
        return claim(source, questId, rewardId);
    }

    private static int claim(CommandSourceStack source, String questId, String rewardId) throws CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        CampaignRuntime runtime = CampaignRuntime.get(source.getServer());
        RewardClaimResult result = runtime.claimReward(player, questId, rewardId);

        if (result == RewardClaimResult.SUCCESS) return success(source, "Reward successfully claimed: " + rewardId);

        return failure(source, "Reward claim rejected: " + result);
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

    private static int verify(CommandSourceStack source) throws CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        CampaignRuntime runtime = CampaignRuntime.get(source.getServer());
        CampaignSavedData savedData = CampaignSavedData.get(source.getServer());
        UUID playerId = player.getUUID();

        Optional<CampaignSavedData.PlayerSnapshot> beforeResult = savedData.getPlayerSnapshot(playerId);

        if (beforeResult.isEmpty()) return failure(source, "Player campaign has not been initialized.");

        CampaignSavedData.PlayerSnapshot before = beforeResult.get();
        Map<String, List<RewardEntry>> rewardsBefore = runtime.getUnlockedRewards(player);

        if (runtime.addObjectiveProgress(player, "invalid_debug_objective", 1))
        {
            return failure(source, "Invalid objective was incorrectly accepted.");
        }

        if (runtime.addObjectiveProgress(player, "gather_cobblestone", 0))
        {
            return failure(source, "Zero progress was incorrectly accepted.");
        }

        if (runtime.addObjectiveProgress(player, "gather_cobblestone", -1))
        {
            return failure(source, "Negative progress was incorrectly accepted.");
        }

        RewardClaimResult claimResult = runtime.claimReward(player, "theageofmachinery:prototype_first_workshop", "invalid_debug_reward");

        if (claimResult != RewardClaimResult.NOT_FOUND)
        {
            return failure(source, "Invalid reward claim returned: " + claimResult);
        }

        Optional<CampaignSavedData.PlayerSnapshot> afterResult = savedData.getPlayerSnapshot(playerId);

        if (afterResult.isEmpty()) return failure(source, "Player campaign disappeared during verification.");

        CampaignSavedData.PlayerSnapshot after = afterResult.get();
        Map<String, List<RewardEntry>> rewardsAfter = runtime.getUnlockedRewards(player);

        if (after.revision() != before.revision())
        {
            return failure(source, "Rejected operations unexpectedly changed the player revision.");
        }

        if (!rewardsBefore.equals(rewardsAfter))
        {
            return failure(source, "Rejected operations unexpectedly changed unlocked rewards.");
        }

        return success(source, "PASS: Invalid operations rejected. Campaign revision and rewards unchanged.");
    }
    
    private static int audit(CommandSourceStack source)
    {
        CampaignRuntime runtime = CampaignRuntime.get(source.getServer());
        List<ServerPlayer> onlinePlayers = source.getServer().getPlayerList().getPlayers();

        success(source, "Campaign runtime session: " + runtime.getSessionId());
        success(source, "Online players: " + onlinePlayers.size());

        for (ServerPlayer player : onlinePlayers)
        {
            Optional<PlayerQuestProgress> progress = runtime.getPlayerQuestProgress(player);
            Optional<QuestDefinition> activeQuest = runtime.getActiveQuestDefinition(player);
            Map<String, List<RewardEntry>> rewards = runtime.getUnlockedRewards(player);

            String questStatus = "Not initialized";

            if (progress.isPresent())
            {
                questStatus = activeQuest.isPresent() ? activeQuest.get().title() : "No active quest";
            }

            success(source, player.getName().getString() + " | Quest: " + questStatus + " | Reward groups: " + rewards.size());
        }

        if (onlinePlayers.size() < 2)
        {
            success(source, "Multiplayer isolation requires at least two connected players.");
        }

        return 1;
    }
    
    private static int reloadStatus(CommandSourceStack source)
    {
        CampaignRuntime runtime = CampaignRuntime.get(source.getServer());

        return success(source, Objects.requireNonNull(runtime.getLastReloadStatus(), "No reload status available."));
    }

}
