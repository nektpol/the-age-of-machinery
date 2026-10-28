package com.TheAgeOfMachinery.runtime;

import com.TheAgeOfMachinery.quest.CampaignDefinition;
import com.TheAgeOfMachinery.quest.PlayerQuestProgress;
import com.TheAgeOfMachinery.quest.QuestDefinition;
import com.TheAgeOfMachinery.quest.QuestProgress;
import com.TheAgeOfMachinery.quest.QuestState;
import com.TheAgeOfMachinery.reward.ExperienceReward;
import com.TheAgeOfMachinery.reward.ItemReward;
import com.TheAgeOfMachinery.reward.PlayerRewardInventory;
import com.TheAgeOfMachinery.reward.RewardEntry;
import com.TheAgeOfMachinery.sync.PlayerCampaignView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// Server-side projection only. Runtime captures one detached save snapshot before calling this factory.
final class PlayerCampaignViewFactory
{
    private PlayerCampaignViewFactory()
    {
    }

    static PlayerCampaignView create(UUID sessionId, UUID playerId, long revision, CampaignDefinition campaign, Optional<PlayerQuestProgress> quests, Optional<PlayerRewardInventory> inventory)
    {
        Objects.requireNonNull(playerId, "Player ID cannot be null.");
        Objects.requireNonNull(campaign, "Campaign cannot be null.");
        Objects.requireNonNull(quests, "Quest state optional cannot be null.");
        Objects.requireNonNull(inventory, "Reward inventory optional cannot be null.");

        if (quests.isPresent() && !playerId.equals(quests.get().getPlayerId())) throw new IllegalArgumentException("Quest snapshot belongs to another player.");
        if (inventory.isPresent() && !playerId.equals(inventory.get().getPlayerId())) throw new IllegalArgumentException("Reward snapshot belongs to another player.");

        if (quests.isEmpty() || quests.get().getQuestProgressSnapshot().isEmpty()) return new PlayerCampaignView(sessionId, revision, false, false, Optional.empty(), List.of(), List.of());

        PlayerQuestProgress player = quests.get();
        Map<String, QuestDefinition> definitions = new LinkedHashMap<>();

        for (var definition : campaign.quests())
        {
            definitions.put(definition.quest().id(), definition.quest());
        }

        Optional<PlayerCampaignView.ActiveQuest> active = Optional.empty();
        Optional<QuestProgress> activeProgress = player.getActiveQuestProgress();

        if (activeProgress.isPresent())
        {
            QuestProgress progress = activeProgress.get();
            QuestDefinition definition = definitions.get(progress.getQuestId());

            if (definition == null) throw new IllegalStateException("Active quest definition is unavailable.");

            active = Optional.of(new PlayerCampaignView.ActiveQuest(definition.id(), definition.title(), definition.description(), objectives(definition, progress), progress.canAdvance(definition)));
        }

        List<PlayerCampaignView.CompletedQuest> history = new ArrayList<>();
        Set<String> included = new HashSet<>();

        // Presentation order follows the manifest; the save does not contain completion timestamps.
        for (QuestDefinition definition : definitions.values())
        {
            if (!player.hasCompletedQuest(definition.id())) continue;

            QuestProgress progress = player.getQuestProgress(definition.id()).orElseThrow();
            history.add(new PlayerCampaignView.CompletedQuest(definition.id(), definition.title(), definition.description(), objectives(definition, progress)));
            included.add(definition.id());
        }

        // Keep already completed historical IDs even when their definition is absent in this campaign.
        List<QuestProgress> historical = new ArrayList<>(player.getQuestProgressSnapshot().values());
        historical.sort(Comparator.comparing(progress -> Objects.requireNonNull(progress).getQuestId()));

        for (QuestProgress progress : historical)
        {
            if (progress.getState() != QuestState.COMPLETED || included.contains(progress.getQuestId())) continue;

            history.add(new PlayerCampaignView.CompletedQuest(progress.getQuestId(), progress.getQuestId(), "Completed quest; its original description is unavailable.", List.of()));
        }

        Map<String, List<RewardEntry>> earned = inventory.isPresent() ? inventory.get().getRewardGroupsSnapshot() : Map.of();
        List<PlayerCampaignView.RewardGroup> rewards = new ArrayList<>();

        for (PlayerCampaignView.CompletedQuest completed : history)
        {
            List<RewardEntry> entries = earned.getOrDefault(completed.id(), List.of());

            if (entries.isEmpty()) continue;

            List<PlayerCampaignView.Reward> visible = new ArrayList<>();

            for (RewardEntry entry : entries)
            {
                if (!entry.questId().equals(completed.id())) throw new IllegalStateException("Earned reward belongs to another quest.");

                // Use the earned ledger, never today's reward definition or optional reward catalogue.
                if (entry.reward() instanceof ExperienceReward experience)
                {
                    visible.add(new PlayerCampaignView.Reward(entry.rewardId(), PlayerCampaignView.RewardKind.EXPERIENCE, experience.amount(), Optional.empty(), entry.isClaimed()));
                }
                else if (entry.reward() instanceof ItemReward item)
                {
                    visible.add(new PlayerCampaignView.Reward(entry.rewardId(), PlayerCampaignView.RewardKind.ITEM, item.amount(), Optional.of(item.itemId()), entry.isClaimed()));
                }
            }

            rewards.add(new PlayerCampaignView.RewardGroup(completed.id(), completed.title(), visible));
        }

        boolean complete = active.isEmpty() && definitions.keySet().stream().allMatch(player::hasCompletedQuest);
        return new PlayerCampaignView(sessionId, revision, true, complete, active, history, rewards);
    }

    private static List<PlayerCampaignView.Objective> objectives(QuestDefinition definition, QuestProgress progress)
    {
        List<PlayerCampaignView.Objective> result = new ArrayList<>();

        for (var objective : definition.objectives())
        {
            List<PlayerCampaignView.Ingredient> ingredients = new ArrayList<>();

            for (var ingredient : objective.ingredients())
            {
                ingredients.add(new PlayerCampaignView.Ingredient(ingredient.resourceId(), ingredient.tag(), ingredient.amount()));
            }

            result.add(new PlayerCampaignView.Objective(objective.id(), objective.description(), progress.getObjectiveProgress(objective.id()), objective.requiredAmount(), objective.optional(), ingredients));
        }

        return result;
    }
}
