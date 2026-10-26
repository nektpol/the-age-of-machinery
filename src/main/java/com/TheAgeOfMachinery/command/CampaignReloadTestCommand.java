package com.TheAgeOfMachinery.command;

import com.TheAgeOfMachinery.json.CampaignDefinitionAssembler;
import com.TheAgeOfMachinery.json.CampaignJsonParser;
import com.TheAgeOfMachinery.json.CampaignManifest;
import com.TheAgeOfMachinery.json.CampaignReloadCompatibility;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CampaignReloadTestCommand
{
    private static final ResourceLocation MANIFEST_ID = Objects.requireNonNull(ResourceLocation.parse("theageofmachinery:main"));
    private static final ResourceLocation WORKSHOP_ID = Objects.requireNonNull(ResourceLocation.parse("theageofmachinery:prototype_first_workshop"));
    private static final ResourceLocation TOOLS_ID = Objects.requireNonNull(ResourceLocation.parse("theageofmachinery:prototype_first_tools"));

    private CampaignReloadTestCommand()
    {
    }

    public static void register(RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal("tamreloadtest").requires(source -> source.hasPermission(2)).executes(context -> test(context.getSource())));
    }

    private static int test(CommandSourceStack source)
    {
        try
        {
            String manifestJson = readResource("data/theageofmachinery/campaigns/main.json");
            String workshopJson = readResource("data/theageofmachinery/quests/prototype_first_workshop.json");
            String toolsJson = readResource("data/theageofmachinery/quests/prototype_first_tools.json");
            CampaignDefinition original = build(manifestJson, workshopJson, toolsJson);

            String cosmeticWorkshop = workshopJson.replace("The First Workshop", "The First Workshop [Reloaded]").replace("Obtain 16 Cobblestone", "Gather sixteen Cobblestone");
            if (cosmeticWorkshop.equals(workshopJson)) throw new IllegalStateException("Cosmetic fixture replacement failed.");
            CampaignReloadCompatibility.requireCompatible(original, build(manifestJson, cosmeticWorkshop, toolsJson));

            CampaignDefinition reordered = new CampaignDefinition(List.of(original.quests().get(1), original.quests().get(0)));
            expectRejected(original, reordered, "quest order");
            expectRejected(original, build(manifestJson, workshopJson.replace("\"requiredAmount\": 16", "\"requiredAmount\": 17"), toolsJson), "required quantity");
            expectRejected(original, build(manifestJson, workshopJson.replace("\"item\": \"minecraft:cobblestone\"", "\"item\": \"minecraft:stone\""), toolsJson), "trigger item");
            expectRejected(original, build(manifestJson, workshopJson, toolsJson.replace("\"amount\": 4", "\"amount\": 5")), "ingredient quantity");
            expectRejected(original, build(manifestJson, workshopJson.replace("\"amount\": 25", "\"amount\": 26"), toolsJson), "reward quantity");

            source.sendSuccess(() -> Objects.requireNonNull(Component.literal("PASS: Cosmetic reload accepted; 5 structural changes rejected. Runtime unchanged.")), false);
            return 1;
        }
        catch (Exception exception)
        {
            source.sendFailure(Objects.requireNonNull(Component.literal("Campaign reload policy test failed: " + exception.getMessage())));
            return 0;
        }
    }

    private static CampaignDefinition build(String manifestJson, String workshopJson, String toolsJson)
    {
        CampaignManifest manifest = CampaignJsonParser.parseManifest(MANIFEST_ID, manifestJson);
        QuestRewardDefinition workshop = CampaignJsonParser.parseQuest(WORKSHOP_ID, workshopJson);
        QuestRewardDefinition tools = CampaignJsonParser.parseQuest(TOOLS_ID, toolsJson);
        return CampaignDefinitionAssembler.assemble(manifest, Map.of(workshop.quest().id(), workshop, tools.quest().id(), tools));
    }

    private static void expectRejected(CampaignDefinition original, CampaignDefinition modified, String scenario)
    {
        try
        {
            CampaignReloadCompatibility.requireCompatible(original, modified);
        }
        catch (IllegalArgumentException expected)
        {
            return;
        }

        throw new IllegalStateException("Unsafe live reload accepted: " + scenario);
    }

    private static String readResource(String path) throws IOException
    {
        ClassLoader loader = CampaignReloadTestCommand.class.getClassLoader();

        try (InputStream stream = loader.getResourceAsStream(path))
        {
            if (stream == null) throw new IOException("Bundled test resource not found: " + path);

            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
