package com.TheAgeOfMachinery.runtime;

import com.TheAgeOfMachinery.quest.CampaignDefinition;
import com.TheAgeOfMachinery.quest.ObjectiveTrigger;
import com.TheAgeOfMachinery.quest.PlayerQuestProgress;
import com.TheAgeOfMachinery.quest.QuestDefinition;
import com.TheAgeOfMachinery.quest.QuestIngredient;
import com.TheAgeOfMachinery.quest.QuestObjective;
import com.TheAgeOfMachinery.quest.QuestState;
import com.TheAgeOfMachinery.reward.ExperienceReward;
import com.TheAgeOfMachinery.reward.ItemReward;
import com.TheAgeOfMachinery.reward.PlayerRewardInventory;
import com.TheAgeOfMachinery.reward.QuestRewardDefinition;
import com.TheAgeOfMachinery.reward.RewardClaimState;
import com.TheAgeOfMachinery.reward.RewardEntry;
import com.TheAgeOfMachinery.sync.PlayerCampaignView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PlayerCampaignViewRegression
{
    public static void main(String[] arguments)
    {
        UUID session = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        QuestObjective craft = new QuestObjective("craft", "Craft a table", 1, false, ObjectiveTrigger.craftItem("minecraft:crafting_table"), List.of(QuestIngredient.tag("minecraft:planks", 4)));
        QuestObjective bonus = new QuestObjective("bonus", "Gather extra stone", 2, true, ObjectiveTrigger.pickupItem("minecraft:cobblestone"));
        QuestDefinition first = new QuestDefinition("test:first", "First workshop", "Only this quest is initially visible", List.of(craft, bonus));
        QuestDefinition second = new QuestDefinition("test:second_secret", "SECOND_TITLE_SECRET", "SECOND_DESCRIPTION_SECRET", List.of(new QuestObjective("second_objective_secret", "SECOND_OBJECTIVE_SECRET", 1, false)));
        QuestDefinition third = new QuestDefinition("test:third_secret", "THIRD_TITLE_SECRET", "THIRD_DESCRIPTION_SECRET", List.of(new QuestObjective("third_objective_secret", "THIRD_OBJECTIVE_SECRET", 1, false)));
        QuestRewardDefinition firstRewards = new QuestRewardDefinition(first, List.of(new ExperienceReward("FIRST_REWARD_SECRET", 999)), Map.of("bonus", List.of(new ItemReward("OPTIONAL_REWARD_SECRET", "minecraft:diamond", 64))));
        QuestRewardDefinition secondRewards = new QuestRewardDefinition(second, List.of(new ExperienceReward("SECOND_REWARD_SECRET", 50)), Map.of());
        QuestRewardDefinition thirdRewards = new QuestRewardDefinition(third, List.of(new ExperienceReward("THIRD_REWARD_SECRET", 75)), Map.of());
        CampaignDefinition campaign = new CampaignDefinition(List.of(firstRewards, secondRewards, thirdRewards));
        PlayerQuestProgress player = new PlayerQuestProgress(playerId);
        PlayerRewardInventory inventory = new PlayerRewardInventory(playerId);
        player.activateQuest(first);

        PlayerCampaignView initial = view(session, playerId, campaign, player, inventory);
        require(initial.initialized() && !initial.campaignComplete(), "Active player status was lost.");
        require(initial.activeQuest().orElseThrow().id().equals(first.id()), "Wrong active quest.");
        require(!initial.activeQuest().orElseThrow().canAdvance(), "Empty mandatory progress allowed advancement.");
        require(initial.activeQuest().orElseThrow().objectives().getFirst().ingredients().getFirst().amount() == 4, "Ingredient quantity was lost.");
        require(initial.activeQuest().orElseThrow().objectives().getFirst().ingredients().getFirst().tag(), "Ingredient tag was lost.");
        require(initial.history().isEmpty() && initial.rewards().isEmpty(), "Initial view exposed history or rewards.");
        require(!initial.toString().contains("SECRET") && !initial.toString().contains("test:second") && !initial.toString().contains("test:third"), "Future quest or unearned reward leaked.");

        CampaignDefinition changedSecrets = new CampaignDefinition(List.of(new QuestRewardDefinition(first, List.of(new ExperienceReward("CHANGED_REWARD_SECRET", 12345)), Map.of()), thirdRewards));
        require(initial.equals(view(session, playerId, changedSecrets, player, inventory)), "Changing hidden definitions changed the visible active view.");

        // Even inconsistent ledger groups must not disclose the active/future quest's rewards.
        inventory.unlockRewards(first.id(), firstRewards.mainRewards());
        inventory.unlockRewards(second.id(), secondRewards.mainRewards());
        require(view(session, playerId, campaign, player, inventory).rewards().isEmpty(), "An uncompleted quest's ledger entry leaked.");

        player.addObjectiveProgress(first, craft.id(), 1);
        PlayerCampaignView ready = view(session, playerId, campaign, player, inventory);
        require(ready.activeQuest().orElseThrow().canAdvance(), "Optional objective blocked readiness.");
        require(ready.activeQuest().orElseThrow().objectives().getFirst().complete(), "Completed objective was not marked complete.");
        require(!ready.activeQuest().orElseThrow().objectives().get(1).complete(), "Unfinished optional objective was marked complete.");
        require(initial.activeQuest().orElseThrow().objectives().getFirst().currentAmount() == 0, "A later domain mutation changed an existing view.");

        player.completeActiveQuest(first);
        player.activateQuest(second);
        PlayerRewardInventory earned = PlayerRewardInventory.restore(playerId, Map.of(first.id(), List.of(new RewardEntry(first.id(), new ExperienceReward("earned_xp", 25), RewardClaimState.CLAIMED), new RewardEntry(first.id(), new ItemReward("earned_iron", "minecraft:iron_ingot", 2), RewardClaimState.AVAILABLE))));
        PlayerCampaignView advanced = view(session, playerId, campaign, player, earned);
        require(advanced.history().size() == 1 && advanced.history().getFirst().id().equals(first.id()), "Completed history was lost.");
        require(advanced.activeQuest().orElseThrow().id().equals(second.id()), "New active quest missing.");
        require(advanced.rewards().size() == 1 && advanced.rewards().getFirst().entries().size() == 2, "Earned rewards were lost.");
        var entries = advanced.rewards().getFirst().entries();
        require(entries.getFirst().amount() == 25 && entries.getFirst().claimed(), "Earned XP changed to the new definition or lost its claim state.");
        require(entries.get(1).itemId().orElseThrow().equals("minecraft:iron_ingot") && !entries.get(1).claimed(), "Available item reward was corrupted.");
        require(!advanced.toString().contains("THIRD_") && !advanced.toString().contains("test:third"), "Unreached third quest leaked.");
        require(!advanced.toString().contains("OPTIONAL_REWARD_SECRET") && !advanced.toString().contains("SECOND_REWARD_SECRET"), "Unearned/current rewards leaked after advancement.");

        CampaignDefinition removedHistory = new CampaignDefinition(List.of(secondRewards, thirdRewards));
        PlayerCampaignView historical = view(session, playerId, removedHistory, player, earned);
        require(historical.history().getFirst().id().equals(first.id()) && historical.rewards().getFirst().entries().size() == 2, "Missing historical definitions discarded history or earned rewards.");

        expectFailure(() -> initial.activeQuest().orElseThrow().objectives().clear(), UnsupportedOperationException.class);
        expectFailure(() -> advanced.history().clear(), UnsupportedOperationException.class);
        expectFailure(() -> advanced.rewards().getFirst().entries().clear(), UnsupportedOperationException.class);
        expectFailure(() -> initial.activeQuest().orElseThrow().objectives().getFirst().ingredients().clear(), UnsupportedOperationException.class);
        List<PlayerCampaignView.CompletedQuest> mutableHistory = new ArrayList<>(advanced.history());
        PlayerCampaignView copied = new PlayerCampaignView(session, 7, true, false, advanced.activeQuest(), mutableHistory, advanced.rewards());
        mutableHistory.clear();
        require(copied.history().size() == 1 && copied.playerRevision() == 7, "View retained a mutable list or lost revision.");

        PlayerCampaignView empty = PlayerCampaignViewFactory.create(session, playerId, 0, campaign, Optional.empty(), Optional.empty());
        require(!empty.initialized() && !empty.campaignComplete() && empty.activeQuest().isEmpty(), "Uninitialized state was misrepresented.");
        require(!empty.toString().contains("SECRET"), "Uninitialized view leaked campaign content.");
        expectFailure(() -> view(session, UUID.randomUUID(), campaign, player, earned), IllegalArgumentException.class);
        expectFailure(() -> view(session, playerId, campaign, player, new PlayerRewardInventory(UUID.randomUUID())), IllegalArgumentException.class);

        player.addObjectiveProgress(second, "second_objective_secret", 1);
        player.completeActiveQuest(second);
        player.activateQuest(third);
        player.addObjectiveProgress(third, "third_objective_secret", 1);
        player.completeActiveQuest(third);
        PlayerCampaignView finished = view(session, playerId, campaign, player, earned);
        require(finished.campaignComplete() && finished.activeQuest().isEmpty() && finished.history().size() == 3, "Completed campaign was not represented correctly.");
        require(finished.history().getFirst().id().equals(first.id()) && finished.history().get(1).id().equals(second.id()), "History did not follow manifest order.");
        require(player.getQuestProgress(first.id()).orElseThrow().getState() == QuestState.COMPLETED, "Projection altered saved history.");
        expectFailure(() -> new PlayerCampaignView.Objective("invalid", "Invalid", 2, 1, false, List.of()), IllegalArgumentException.class);

        System.out.println("PASS: authorized player views exclude future/unearned content; detached immutable data, player ownership, earned reward values/claim states, ingredients, optional readiness, history and finished/uninitialized states verified.");
    }

    private static PlayerCampaignView view(UUID session, UUID playerId, CampaignDefinition campaign, PlayerQuestProgress player, PlayerRewardInventory inventory)
    {
        return PlayerCampaignViewFactory.create(session, playerId, 7, campaign, Optional.of(player), Optional.of(inventory));
    }

    private static void expectFailure(Runnable action, Class<? extends Exception> expected)
    {
        try
        {
            action.run();
        }
        catch (Exception exception)
        {
            if (expected.isInstance(exception)) return;

            throw new AssertionError("Unexpected exception", exception);
        }

        throw new AssertionError("Expected " + expected.getSimpleName());
    }

    private static void require(boolean condition, String message)
    {
        if (!condition) throw new AssertionError(message);
    }
}
