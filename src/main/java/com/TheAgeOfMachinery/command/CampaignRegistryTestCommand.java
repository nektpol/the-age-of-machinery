package com.TheAgeOfMachinery.command;

import com.TheAgeOfMachinery.json.CampaignDefinitionAssembler;
import com.TheAgeOfMachinery.json.CampaignJsonParser;
import com.TheAgeOfMachinery.json.CampaignManifest;
import com.TheAgeOfMachinery.quest.CampaignDefinition;
import com.TheAgeOfMachinery.reward.QuestRewardDefinition;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

public final class CampaignRegistryTestCommand
{
    private CampaignRegistryTestCommand()
    {
    }

    public static void register(RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal("tamregistry").requires(source -> source.hasPermission(2)).then(Commands.literal("test").executes(context -> test(context.getSource()))));
    }

    private static int test(CommandSourceStack source)
    {
        try
        {
            ResourceLocation manifestId = Objects.requireNonNull(ResourceLocation.parse("theageofmachinery:main"));
            ResourceLocation workshopId = Objects.requireNonNull(ResourceLocation.parse("theageofmachinery:prototype_first_workshop"));
            ResourceLocation toolsId = Objects.requireNonNull(ResourceLocation.parse("theageofmachinery:prototype_first_tools"));

            String manifestJson = readResource("data/theageofmachinery/campaigns/main.json");
            String workshopJson = readResource("data/theageofmachinery/quests/prototype_first_workshop.json");
            String toolsJson = readResource("data/theageofmachinery/quests/prototype_first_tools.json");

            CampaignManifest manifest = CampaignJsonParser.parseManifest(manifestId, manifestJson);
            QuestRewardDefinition workshop = CampaignJsonParser.parseQuest(workshopId, workshopJson);
            QuestRewardDefinition tools = CampaignJsonParser.parseQuest(toolsId, toolsJson);
            Map<String, QuestRewardDefinition> validDefinitions = Map.of(workshop.quest().id(), workshop, tools.quest().id(), tools);
            CampaignDefinition campaign = CampaignDefinitionAssembler.assemble(manifest, validDefinitions);

            if (campaign.questCount() != 2) throw new IllegalStateException("Expected exactly two campaign quests.");
            if (!campaign.getQuestIds().equals(manifest.quests())) throw new IllegalStateException("Manifest quest ordering was not preserved.");

            assertRejected(() -> CampaignDefinitionAssembler.assemble(manifest, Map.of(workshop.quest().id(), workshop)), "missing quest");
            assertRejected(() -> CampaignDefinitionAssembler.assemble(manifest, Map.of(workshop.quest().id(), tools, tools.quest().id(), workshop)), "mismatched file key");

            String invalidTriggerJson = toolsJson.replace("minecraft:crafting_table", "minecraft:missing_test_item");
            String invalidRewardJson = workshopJson.replace("minecraft:iron_ingot", "minecraft:missing_test_reward");
            String invalidIngredientJson = toolsJson.replace("minecraft:cobblestone", "minecraft:missing_test_ingredient");

            if (invalidTriggerJson.equals(toolsJson) || invalidRewardJson.equals(workshopJson) || invalidIngredientJson.equals(toolsJson)) throw new IllegalStateException("Negative fixture substitution failed.");

            QuestRewardDefinition invalidTrigger = CampaignJsonParser.parseQuest(toolsId, invalidTriggerJson);
            QuestRewardDefinition invalidReward = CampaignJsonParser.parseQuest(workshopId, invalidRewardJson);
            QuestRewardDefinition invalidIngredient = CampaignJsonParser.parseQuest(toolsId, invalidIngredientJson);

            assertRejected(() -> CampaignDefinitionAssembler.assemble(manifest, Map.of(workshop.quest().id(), workshop, tools.quest().id(), invalidTrigger)), "unregistered trigger item");
            assertRejected(() -> CampaignDefinitionAssembler.assemble(manifest, Map.of(workshop.quest().id(), invalidReward, tools.quest().id(), tools)), "unregistered reward item");
            assertRejected(() -> CampaignDefinitionAssembler.assemble(manifest, Map.of(workshop.quest().id(), workshop, tools.quest().id(), invalidIngredient)), "unregistered ingredient item");

            source.sendSuccess(() -> Objects.requireNonNull(Component.literal("PASS: Assembled 2 ordered quests; 5 invalid campaign candidates rejected. Runtime unchanged.")), false);
            return 1;
        }
        catch (Exception exception)
        {
            source.sendFailure(Objects.requireNonNull(Component.literal("Campaign registry test failed: " + exception.getMessage())));
            return 0;
        }
    }

    private static void assertRejected(Runnable action, String scenario)
    {
        try
        {
            action.run();
        }
        catch (IllegalArgumentException expected)
        {
            return;
        }

        throw new IllegalStateException("Invalid campaign was accepted: " + scenario);
    }

    private static String readResource(String path) throws IOException
    {
        ClassLoader loader = CampaignRegistryTestCommand.class.getClassLoader();

        try (InputStream stream = loader.getResourceAsStream(path))
        {
            if (stream == null) throw new IOException("Bundled JSON resource not found: " + path);

            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
