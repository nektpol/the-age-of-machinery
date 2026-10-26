package com.TheAgeOfMachinery.json;

import com.TheAgeOfMachinery.quest.CampaignDefinition;
import com.TheAgeOfMachinery.quest.QuestIngredient;
import com.TheAgeOfMachinery.quest.QuestObjective;
import com.TheAgeOfMachinery.reward.QuestRewardDefinition;

import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads the currently active server data packs at server startup.
 * Live definition replacement during /reload is intentionally deferred to 2.13.5.
 */
public final class CampaignDataPackLoader
{
    private static final ResourceLocation MANIFEST_ID = Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("theageofmachinery", "main"));
    private static final ResourceLocation MANIFEST_FILE = Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("theageofmachinery", "campaigns/main.json"));

    private CampaignDataPackLoader()
    {
    }

    public static CampaignDefinition load(ResourceManager resourceManager)
    {
        Objects.requireNonNull(resourceManager, "Server resource manager cannot be null.");

        CampaignManifest manifest = CampaignJsonParser.parseManifest(MANIFEST_ID, readJson(resourceManager, MANIFEST_FILE));
        Map<String, QuestRewardDefinition> definitions = new HashMap<>();

        for (String questId : manifest.quests())
        {
            ResourceLocation definitionId = Objects.requireNonNull(ResourceLocation.parse(Objects.requireNonNull(questId)));
            ResourceLocation resourceFile = Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(Objects.requireNonNull(definitionId.getNamespace(), "Namespace cannot be null."), "quests/" + definitionId.getPath() + ".json"));
            QuestRewardDefinition definition = CampaignJsonParser.parseQuest(definitionId, readJson(resourceManager, resourceFile));

            if (definitions.putIfAbsent(questId, definition) != null) throw new IllegalArgumentException("Duplicate quest entry in campaign manifest: " + questId);
        }

        CampaignDefinition candidate = CampaignDefinitionAssembler.assemble(manifest, definitions);
        validateIngredientTags(candidate);
        return candidate;
    }

    private static String readJson(ResourceManager resourceManager, ResourceLocation fileId)
    {
        try (BufferedReader reader = resourceManager.openAsReader(Objects.requireNonNull(fileId, "File ID cannot be null.")))
        {
            StringWriter contents = new StringWriter();
            reader.transferTo(contents);
            return contents.toString();
        }
        catch (IOException exception)
        {
            throw new IllegalStateException("Missing or unreadable campaign data-pack resource: data/" + fileId.getNamespace() + "/" + fileId.getPath(), exception);
        }
    }

    private static void validateIngredientTags(CampaignDefinition campaign)
    {
        for (QuestRewardDefinition definition : campaign.quests())
        {
            for (QuestObjective objective : definition.quest().objectives())
            {
                for (QuestIngredient ingredient : objective.ingredients())
                {
                    if (!ingredient.tag()) continue;

                    ResourceLocation tagId = Objects.requireNonNull(ResourceLocation.parse(Objects.requireNonNull(ingredient.resourceId())));
                    TagKey<Item> tagKey = TagKey.create(Objects.requireNonNull(Registries.ITEM, "Item registry cannot be null."), tagId);
                    Optional<HolderSet.Named<Item>> tag = BuiltInRegistries.ITEM.getTag(Objects.requireNonNull(tagKey, "Tag key cannot be null."));

                    if (tag.isEmpty() || tag.get().size() == 0)
                    {
                        throw new IllegalArgumentException("[" + definition.quest().id() + "] objectives." + objective.id() + ": Unknown or empty item ingredient tag: #" + ingredient.resourceId());
                    }
                }
            }
        }
    }
}
