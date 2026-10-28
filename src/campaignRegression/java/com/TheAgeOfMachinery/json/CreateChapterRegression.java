package com.TheAgeOfMachinery.json;

import com.TheAgeOfMachinery.quest.ObjectiveTriggerType;
import com.TheAgeOfMachinery.quest.QuestProgress;
import com.TheAgeOfMachinery.quest.QuestState;
import com.TheAgeOfMachinery.reward.QuestRewardDefinition;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class CreateChapterRegression
{
    public static void main(String[] arguments) throws Exception
    {
        Path resources = Path.of(arguments[0], "datapacks", "tam_create_workshop", "data", "theageofmachinery");
        CampaignManifest manifest = CampaignJsonParser.parseManifest(ResourceLocation.parse("theageofmachinery:main"), Files.readString(resources.resolve("campaigns/main.json")));

        require(manifest.quests().size() == 8, "Expected the original prelude plus six Create quests.");
        require(manifest.quests().getFirst().equals("theageofmachinery:prototype_first_workshop"), "First saved quest ID changed.");
        require(manifest.quests().get(1).equals("theageofmachinery:prototype_first_tools"), "Second saved quest ID changed.");

        for (String questId : manifest.quests())
        {
            String name = questId.substring(questId.indexOf(':') + 1);
            QuestRewardDefinition definition = CampaignJsonParser.parseQuest(ResourceLocation.parse(questId), Files.readString(resources.resolve("quests/" + name + ".json")));
            require(definition.quest().objectives().stream().anyMatch(objective -> !objective.optional()), "Quest has no mandatory objective: " + name);
            QuestProgress progress = new QuestProgress(questId);
            progress.activate();
            require(!progress.canAdvance(definition.quest()), "An empty quest allowed advancement: " + name);

            for (var objective : definition.quest().objectives())
            {
                if (!objective.optional()) progress.addProgress(objective, objective.requiredAmount());
            }

            require(progress.canAdvance(definition.quest()), "Optional objectives incorrectly blocked advancement: " + name);
            require(progress.getState() == QuestState.ACTIVE, "Progress automatically finalized the quest: " + name);
            require(definition.calculateEarnedRewards(progress).equals(definition.mainRewards()), "An unfinished optional bonus was earned: " + name);

            for (var objective : definition.quest().objectives())
            {
                if (objective.optional()) progress.addProgress(objective, objective.requiredAmount());
            }

            int totalRewards = definition.mainRewards().size() + definition.optionalRewards().values().stream().mapToInt(rewards -> Objects.requireNonNull(rewards).size()).sum();
            require(definition.calculateEarnedRewards(progress).size() == totalRewards, "A completed optional bonus was lost: " + name);
            require(progress.complete(definition.quest()), "Eligible manual completion failed: " + name);
        }

        ResourceLocation crushingId = ResourceLocation.parse("theageofmachinery:workshop_crushing");
        String crushing = Files.readString(resources.resolve("quests/workshop_crushing.json"));
        QuestRewardDefinition parsed = CampaignJsonParser.parseQuest(crushingId, crushing);
        var machine = parsed.quest().objectives().stream().filter(objective -> objective.trigger().type() == ObjectiveTriggerType.KINETIC_ACTIVE).findFirst().orElseThrow();
        require(machine.ingredients().size() == 3, "Crushing Wheel build checklist was lost.");
        require(machine.ingredients().getFirst().amount() == 16, "Wheel pair alloy quantity changed.");

        String missingCraftingChecklist = "{\"formatVersion\":1,\"id\":\"theageofmachinery:negative\",\"title\":\"Test\",\"description\":\"Test\",\"objectives\":[{\"id\":\"craft\",\"description\":\"Test\",\"requiredAmount\":1,\"optional\":false,\"trigger\":{\"type\":\"CRAFT_ITEM\",\"item\":\"minecraft:stick\"}}],\"rewards\":{\"main\":[]}}";
        assertRejected(ResourceLocation.parse("theageofmachinery:negative"), missingCraftingChecklist);
        String pickupChecklist = missingCraftingChecklist.replace("CRAFT_ITEM", "PICKUP_ITEM").replace("\"item\":\"minecraft:stick\"}}", "\"item\":\"minecraft:stick\"},\"ingredients\":[{\"item\":\"minecraft:oak_planks\",\"amount\":1}]}");
        assertRejected(ResourceLocation.parse("theageofmachinery:negative"), pickupChecklist);
        String zero = crushing.replace("\"amount\": 16", "\"amount\": 0");
        require(!zero.equals(crushing), "Negative quantity fixture did not change.");
        assertRejected(crushingId, zero);

        System.out.println("PASS: 8 quests parsed; mandatory gates, manual completion and earned optional rewards checked; kinetic build ingredients accepted and 3 invalid checklist cases rejected.");
    }

    private static void assertRejected(ResourceLocation id, String json)
    {
        try
        {
            CampaignJsonParser.parseQuest(Objects.requireNonNull(id), json);
        }
        catch (IllegalArgumentException expected)
        {
            return;
        }

        throw new AssertionError("Invalid checklist was accepted: " + id);
    }

    private static void require(boolean condition, String message)
    {
        if (!condition) throw new AssertionError(message);
    }
}
