package com.TheAgeOfMachinery.json;

import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public record CampaignManifest(String id, List<String> quests)
{
    private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public CampaignManifest
    {
        Objects.requireNonNull(id, "Campaign ID cannot be null.");
        Objects.requireNonNull(quests, "Campaign quest IDs cannot be null.");

        if (!RESOURCE_ID.matcher(id).matches()) throw new IllegalArgumentException("Invalid campaign ID: " + id);
        if (quests.isEmpty()) throw new IllegalArgumentException("Campaign must list at least one quest.");

        id = Objects.requireNonNull(ResourceLocation.parse(id)).toString();
        quests = List.copyOf(quests);

        Set<String> seen = new HashSet<>();

        for (String questId : quests)
        {
            if (!RESOURCE_ID.matcher(questId).matches()) throw new IllegalArgumentException("Invalid campaign quest ID: " + questId);
            if (!seen.add(questId)) throw new IllegalArgumentException("Duplicate campaign quest ID: " + questId);
        }
    }
}
