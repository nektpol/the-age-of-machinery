package com.TheAgeOfMachinery.command;

import com.TheAgeOfMachinery.json.CampaignJsonParser;
import com.TheAgeOfMachinery.json.CampaignManifest;
import com.TheAgeOfMachinery.quest.ObjectiveTriggerType;
import com.TheAgeOfMachinery.quest.QuestObjective;
import com.TheAgeOfMachinery.reward.QuestRewardDefinition;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class CampaignJsonParserTestCommand
{
    private CampaignJsonParserTestCommand()
    {
    }

    public static void register(RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal("tamjson").requires(source -> source.hasPermission(2)).then(Commands.literal("test").executes(context -> test(context.getSource()))));
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

            if (manifest.quests().size() != 2) throw new IllegalStateException("Expected two campaign quest IDs.");
            if (!manifest.quests().get(0).equals(workshop.quest().id())) throw new IllegalStateException("First quest ID mismatch.");
            if (!manifest.quests().get(1).equals(tools.quest().id())) throw new IllegalStateException("Second quest ID mismatch.");
            if (workshop.quest().objectives().size() != 2) throw new IllegalStateException("Workshop objectives did not parse.");
            if (tools.quest().objectives().size() != 2) throw new IllegalStateException("Tools objectives did not parse.");

            QuestObjective craftingObjective = tools.quest().objectives().get(0);

            if (craftingObjective.trigger().type() != ObjectiveTriggerType.CRAFT_ITEM) throw new IllegalStateException("Crafting trigger missing.");
            if (craftingObjective.ingredients().size() != 1) throw new IllegalStateException("Crafting ingredients missing.");
            if (!craftingObjective.ingredients().get(0).tag()) throw new IllegalStateException("Planks should be defined by item tag.");
            if (craftingObjective.ingredients().get(0).amount() != 4) throw new IllegalStateException("Ingredient quantity mismatch.");

            String invalidAmount = workshopJson.replace("\"requiredAmount\": 16", "\"requiredAmount\": 0");
            String invalidTrigger = toolsJson.replace("\"type\": \"CRAFT_ITEM\"", "\"type\": \"UNKNOWN\"");
            String unexpectedField = workshopJson.replace("\"formatVersion\": 1,", "\"formatVersion\": 1, \"typoField\": true,");

            if (invalidAmount.equals(workshopJson) || invalidTrigger.equals(toolsJson) || unexpectedField.equals(workshopJson)) throw new IllegalStateException("Negative fixture substitution failed.");

            assertRejected(workshopId, invalidAmount);
            assertRejected(toolsId, invalidTrigger);
            assertRejected(workshopId, unexpectedField);
            assertRejected(Objects.requireNonNull(ResourceLocation.parse("theageofmachinery:wrong_file")), workshopJson);

            source.sendSuccess(() -> Objects.requireNonNull(Component.literal("PASS: JSON manifest, quests, ingredients, IDs, rewards, and 4 negative cases.")), false);
            return 1;
        }
        catch (Exception exception)
        {
            source.sendFailure(Objects.requireNonNull(Component.literal("JSON parser test failed: " + exception.getMessage())));
            return 0;
        }
    }

    private static void assertRejected(ResourceLocation sourceId, String json)
    {
        try
        {
            CampaignJsonParser.parseQuest(sourceId, json);
        }
        catch (IllegalArgumentException expected)
        {
            return;
        }

        throw new IllegalStateException("Invalid JSON was accepted for " + sourceId + ".");
    }

    private static String readResource(String path) throws IOException
    {
        ClassLoader loader = CampaignJsonParserTestCommand.class.getClassLoader();

        try (InputStream stream = loader.getResourceAsStream(path))
        {
            if (stream == null) throw new IOException("Bundled JSON resource not found: " + path);

            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
