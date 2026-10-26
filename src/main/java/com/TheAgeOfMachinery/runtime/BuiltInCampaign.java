package com.TheAgeOfMachinery.runtime;

import com.TheAgeOfMachinery.quest.CampaignDefinition;
import com.TheAgeOfMachinery.quest.ObjectiveTrigger;
import com.TheAgeOfMachinery.quest.QuestDefinition;
import com.TheAgeOfMachinery.quest.QuestObjective;
import com.TheAgeOfMachinery.reward.ExperienceReward;
import com.TheAgeOfMachinery.reward.ItemReward;
import com.TheAgeOfMachinery.reward.QuestRewardDefinition;

import java.util.List;
import java.util.Map;

public final class BuiltInCampaign
{
    private BuiltInCampaign()
    {
    }

    public static CampaignDefinition create()
    {
        QuestObjective firstMain = new QuestObjective("gather_cobblestone", "Obtain 16 Cobblestone", 16, false, ObjectiveTrigger.pickupItem("minecraft:cobblestone"));
        QuestObjective firstOptional = new QuestObjective("gather_logs", "Obtain 8 Oak Logs", 8, true, ObjectiveTrigger.pickupItem("minecraft:oak_log"));

        QuestDefinition firstQuest = new QuestDefinition(
            "theageofmachinery:prototype_first_workshop",
            "The First Workshop",
            "Gather the materials needed to establish your first workshop.",
            List.of(firstMain, firstOptional)
        );

        QuestRewardDefinition firstRewards = new QuestRewardDefinition(
            firstQuest,
            List.of(new ExperienceReward("main_xp", 25), new ItemReward("main_iron", "minecraft:iron_ingot", 2)),
            Map.of("gather_logs", List.of(new ExperienceReward("optional_xp", 10)))
        );
        
        QuestObjective secondMain = new QuestObjective("craft_crafting_table", "Craft a Crafting Table (4 Planks)", 1, false, ObjectiveTrigger.craftItem("minecraft:crafting_table"));
        QuestObjective secondOptional = new QuestObjective("craft_furnace", "Craft a Furnace (8 Cobblestone)", 1, true, ObjectiveTrigger.craftItem("minecraft:furnace"));

        QuestDefinition secondQuest = new QuestDefinition(
            "theageofmachinery:prototype_first_tools",
            "The First Tools",
            "Prepare the basic tools required before working with machinery.",
            List.of(secondMain, secondOptional)
        );

        QuestRewardDefinition secondRewards = new QuestRewardDefinition(
            secondQuest,
            List.of(new ExperienceReward("main_xp", 50)),
            Map.of("craft_furnace", List.of(new ExperienceReward("optional_xp", 15)))
        );

        return new CampaignDefinition(List.of(firstRewards, secondRewards));
    }
}
