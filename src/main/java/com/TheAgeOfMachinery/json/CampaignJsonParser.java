package com.TheAgeOfMachinery.json;

import com.TheAgeOfMachinery.quest.ObjectiveTrigger;
import com.TheAgeOfMachinery.quest.ObjectiveTriggerType;
import com.TheAgeOfMachinery.quest.QuestDefinition;
import com.TheAgeOfMachinery.quest.QuestIngredient;
import com.TheAgeOfMachinery.quest.QuestObjective;
import com.TheAgeOfMachinery.reward.ExperienceReward;
import com.TheAgeOfMachinery.reward.ItemReward;
import com.TheAgeOfMachinery.reward.QuestRewardDefinition;
import com.TheAgeOfMachinery.reward.RewardDefinition;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import net.minecraft.resources.ResourceLocation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class CampaignJsonParser
{
    private static final int FORMAT_VERSION = 1;
    private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern LOCAL_ID = Pattern.compile("[a-z0-9_./-]+");

    private CampaignJsonParser()
    {
    }

    public static QuestRewardDefinition parseQuest(ResourceLocation sourceId, String json)
    {
        Objects.requireNonNull(sourceId, "Quest resource ID cannot be null.");
        JsonObject root = object(parseJson(sourceId, json), sourceId, "$");
        keys(root, sourceId, "$", Set.of("formatVersion", "id", "title", "description", "objectives", "rewards"));
        version(root, sourceId);

        String id = resourceId(root, "id", sourceId, "$.id");
        requireMatchingResourceId(sourceId, id);
        String title = string(root, "title", sourceId, "$.title");
        String description = string(root, "description", sourceId, "$.description");
        JsonArray objectivesJson = array(required(root, "objectives", sourceId, "$.objectives"), sourceId, "$.objectives");

        if (objectivesJson.size() == 0) throw error(sourceId, "$.objectives", "At least one objective is required.");

        List<QuestObjective> objectives = new ArrayList<>();

        for (int i = 0; i < objectivesJson.size(); i++)
        {
            objectives.add(parseObjective(objectivesJson.get(i), sourceId, "$.objectives[" + i + "]"));
        }

        QuestDefinition quest;

        try
        {
            quest = new QuestDefinition(id, title, description, objectives);
        }
        catch (IllegalArgumentException exception)
        {
            throw error(sourceId, "$.objectives", exception.getMessage());
        }

        JsonObject rewardsJson = object(required(root, "rewards", sourceId, "$.rewards"), sourceId, "$.rewards");
        keys(rewardsJson, sourceId, "$.rewards", Set.of("main", "optional"));
        List<RewardDefinition> mainRewards = parseRewards(required(rewardsJson, "main", sourceId, "$.rewards.main"), sourceId, "$.rewards.main");
        Map<String, List<RewardDefinition>> optionalRewards = new LinkedHashMap<>();

        if (rewardsJson.has("optional"))
        {
            JsonObject optionalJson = object(rewardsJson.get("optional"), sourceId, "$.rewards.optional");

            for (Map.Entry<String, JsonElement> entry : optionalJson.entrySet())
            {
                String objectiveId = entry.getKey();
                localId(objectiveId, sourceId, "$.rewards.optional." + objectiveId);
                optionalRewards.put(objectiveId, parseRewards(entry.getValue(), sourceId, "$.rewards.optional." + objectiveId));
            }
        }

        try
        {
            return new QuestRewardDefinition(quest, mainRewards, optionalRewards);
        }
        catch (IllegalArgumentException exception)
        {
            throw error(sourceId, "$.rewards", exception.getMessage());
        }
    }

    public static CampaignManifest parseManifest(ResourceLocation sourceId, String json)
    {
        Objects.requireNonNull(sourceId, "Manifest resource ID cannot be null.");
        JsonObject root = object(parseJson(sourceId, json), sourceId, "$");
        keys(root, sourceId, "$", Set.of("formatVersion", "id", "quests"));
        version(root, sourceId);

        String id = resourceId(root, "id", sourceId, "$.id");
        requireMatchingResourceId(sourceId, id);
        JsonArray entries = array(required(root, "quests", sourceId, "$.quests"), sourceId, "$.quests");
        List<String> questIds = new ArrayList<>();

        for (int i = 0; i < entries.size(); i++)
        {
            String path = "$.quests[" + i + "]";
            questIds.add(validateResourceId(text(entries.get(i), sourceId, path), sourceId, path));
        }

        try
        {
            return new CampaignManifest(id, questIds);
        }
        catch (IllegalArgumentException exception)
        {
            throw error(sourceId, "$.quests", exception.getMessage());
        }
    }

    private static QuestObjective parseObjective(JsonElement element, ResourceLocation sourceId, String path)
    {
        JsonObject json = object(element, sourceId, path);
        keys(json, sourceId, path, Set.of("id", "description", "requiredAmount", "optional", "trigger", "ingredients"));

        String id = localId(string(json, "id", sourceId, path + ".id"), sourceId, path + ".id");
        String description = string(json, "description", sourceId, path + ".description");
        int requiredAmount = positiveInt(json, "requiredAmount", sourceId, path + ".requiredAmount");
        boolean optional = bool(json, "optional", sourceId, path + ".optional");
        ObjectiveTrigger trigger = parseTrigger(required(json, "trigger", sourceId, path + ".trigger"), sourceId, path + ".trigger");
        List<QuestIngredient> ingredients = new ArrayList<>();

        boolean crafting = trigger.type() == ObjectiveTriggerType.CRAFT_ITEM;
        boolean kineticChecklist = trigger.type() == ObjectiveTriggerType.KINETIC_ACTIVE && json.has("ingredients");

        if (crafting || kineticChecklist)
        {
            JsonArray list = array(required(json, "ingredients", sourceId, path + ".ingredients"), sourceId, path + ".ingredients");

            if (list.size() == 0) throw error(sourceId, path + ".ingredients", "Ingredient checklists need at least one ingredient.");

            for (int i = 0; i < list.size(); i++)
            {
                ingredients.add(parseIngredient(list.get(i), sourceId, path + ".ingredients[" + i + "]"));
            }
        }
        else if (json.has("ingredients"))
        {
            throw error(sourceId, path + ".ingredients", "Only crafting and kinetic activation objectives can specify ingredients in format v1.");
        }

        try
        {
            return new QuestObjective(id, description, requiredAmount, optional, trigger, ingredients);
        }
        catch (IllegalArgumentException exception)
        {
            throw error(sourceId, path, exception.getMessage());
        }
    }


    private static ObjectiveTrigger parseTrigger(JsonElement element, ResourceLocation sourceId, String path)
    {
        JsonObject json = object(element, sourceId, path);
        String typeName = string(json, "type", sourceId, path + ".type");
        ObjectiveTriggerType type;

        try
        {
            type = ObjectiveTriggerType.valueOf(typeName);
        }
        catch (IllegalArgumentException exception)
        {
            throw error(sourceId, path + ".type", "Unsupported trigger: " + typeName);
        }

        switch (type)
        {
            case MANUAL ->
            {
                keys(json, sourceId, path, Set.of("type"));
                return ObjectiveTrigger.manual();
            }

            case CRAFT_ITEM ->
            {
                keys(json, sourceId, path, Set.of("type", "item"));
                String itemId = resourceId(json, "item", sourceId, path + ".item");
                return ObjectiveTrigger.craftItem(itemId);
            }

            case PICKUP_ITEM ->
            {
                keys(json, sourceId, path, Set.of("type", "item"));
                String itemId = resourceId(json, "item", sourceId, path + ".item");
                return ObjectiveTrigger.pickupItem(itemId);
            }

            case KINETIC_ACTIVE ->
            {
                keys(json, sourceId, path, Set.of("type", "machine", "minSpeed"));

                String machineId = resourceId(json, "machine", sourceId, path + ".machine");
                float minSpeed = positiveFloat(json, "minSpeed", sourceId, path + ".minSpeed");

                return ObjectiveTrigger.kineticActive(machineId, minSpeed);
            }

            case MACHINE_PROCESS ->
            {
                keys(json, sourceId, path, Set.of("type", "machine", "output"));

                String machineId = resourceId(json, "machine", sourceId, path + ".machine");
                String outputId = resourceId(json, "output", sourceId, path + ".output");

                return ObjectiveTrigger.machineProcess(machineId, outputId);
            }
        }

        throw error(sourceId, path + ".type", "Unsupported trigger: " + typeName);
    }

    
    private static float positiveFloat(JsonObject json, String key, ResourceLocation sourceId, String path)
    {
        JsonElement element = required(json, key, sourceId, path);

        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) throw error(sourceId, path, "Expected a positive number.");

        try
        {
            float value = new BigDecimal(element.getAsString()).floatValue();

            if (!Float.isFinite(value) || value <= 0f) throw error(sourceId, path, "Value must be finite and greater than zero.");

            return value;
        }
        catch (NumberFormatException exception)
        {
            throw error(sourceId, path, "Invalid floating-point value.");
        }
    }


    private static QuestIngredient parseIngredient(JsonElement element, ResourceLocation sourceId, String path)
    {
        JsonObject json = object(element, sourceId, path);
        keys(json, sourceId, path, Set.of("item", "tag", "amount"));

        if (json.has("item") == json.has("tag")) throw error(sourceId, path, "Exactly one of item or tag is required.");

        int amount = positiveInt(json, "amount", sourceId, path + ".amount");

        if (json.has("item")) return QuestIngredient.item(resourceId(json, "item", sourceId, path + ".item"), amount);

        return QuestIngredient.tag(resourceId(json, "tag", sourceId, path + ".tag"), amount);
    }

    private static List<RewardDefinition> parseRewards(JsonElement element, ResourceLocation sourceId, String path)
    {
        JsonArray rewardsJson = array(element, sourceId, path);
        List<RewardDefinition> rewards = new ArrayList<>();

        for (int i = 0; i < rewardsJson.size(); i++)
        {
            String rewardPath = path + "[" + i + "]";
            JsonObject json = object(rewardsJson.get(i), sourceId, rewardPath);
            String id = localId(string(json, "id", sourceId, rewardPath + ".id"), sourceId, rewardPath + ".id");
            String type = string(json, "type", sourceId, rewardPath + ".type");
            int amount = positiveInt(json, "amount", sourceId, rewardPath + ".amount");

            if ("EXPERIENCE".equals(type))
            {
                keys(json, sourceId, rewardPath, Set.of("id", "type", "amount"));
                rewards.add(new ExperienceReward(id, amount));
            }
            else if ("ITEM".equals(type))
            {
                keys(json, sourceId, rewardPath, Set.of("id", "type", "amount", "item"));
                String itemId = resourceId(json, "item", sourceId, rewardPath + ".item");
                rewards.add(new ItemReward(id, itemId, amount));
            }
            else
            {
                throw error(sourceId, rewardPath + ".type", "Unsupported reward type: " + type);
            }
        }

        return List.copyOf(rewards);
    }

    private static void version(JsonObject json, ResourceLocation sourceId)
    {
        int version = positiveInt(json, "formatVersion", sourceId, "$.formatVersion");

        if (version != FORMAT_VERSION) throw error(sourceId, "$.formatVersion", "Only formatVersion 1 is supported, found " + version + ".");
    }

    private static JsonElement parseJson(ResourceLocation sourceId, String json)
    {
        Objects.requireNonNull(json, "JSON content cannot be null.");

        try
        {
            return JsonParser.parseString(json);
        }
        catch (JsonParseException exception)
        {
            throw error(sourceId, "$", "Malformed JSON: " + exception.getMessage());
        }
    }

    private static JsonElement required(JsonObject json, String key, ResourceLocation sourceId, String path)
    {
        JsonElement value = json.get(key);

        if (value == null || value.isJsonNull()) throw error(sourceId, path, "Required field is missing or null.");

        return value;
    }

    private static JsonObject object(JsonElement element, ResourceLocation sourceId, String path)
    {
        if (element == null || !element.isJsonObject()) throw error(sourceId, path, "Expected an object.");

        return element.getAsJsonObject();
    }

    private static JsonArray array(JsonElement element, ResourceLocation sourceId, String path)
    {
        if (element == null || !element.isJsonArray()) throw error(sourceId, path, "Expected an array.");

        return element.getAsJsonArray();
    }

    private static String string(JsonObject json, String key, ResourceLocation sourceId, String path)
    {
        String value = text(required(json, key, sourceId, path), sourceId, path);

        if (value.isBlank()) throw error(sourceId, path, "String must not be blank.");

        return value;
    }

    private static String text(JsonElement element, ResourceLocation sourceId, String path)
    {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) throw error(sourceId, path, "Expected a string.");

        return element.getAsString();
    }

    private static boolean bool(JsonObject json, String key, ResourceLocation sourceId, String path)
    {
        JsonElement element = required(json, key, sourceId, path);

        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) throw error(sourceId, path, "Expected a boolean.");

        return element.getAsBoolean();
    }

    private static int positiveInt(JsonObject json, String key, ResourceLocation sourceId, String path)
    {
        JsonElement element = required(json, key, sourceId, path);

        if (!element.isJsonPrimitive()) throw error(sourceId, path, "Expected a positive integer.");

        JsonPrimitive primitive = element.getAsJsonPrimitive();

        if (!primitive.isNumber()) throw error(sourceId, path, "Expected a positive integer.");

        try
        {
            int value = new BigDecimal(primitive.getAsString()).intValueExact();

            if (value <= 0) throw error(sourceId, path, "Value must be greater than zero.");

            return value;
        }
        catch (ArithmeticException | NumberFormatException exception)
        {
            throw error(sourceId, path, "Value must be a 32-bit positive integer.");
        }
    }

    private static String resourceId(JsonObject json, String key, ResourceLocation sourceId, String path)
    {
        return validateResourceId(string(json, key, sourceId, path), sourceId, path);
    }

    private static String validateResourceId(String value, ResourceLocation sourceId, String path)
    {
        if (!RESOURCE_ID.matcher(value).matches()) throw error(sourceId, path, "Expected explicit namespace:path identifier: " + value);

        try
        {
            return Objects.requireNonNull(ResourceLocation.parse(Objects.requireNonNull(value))).toString();
        }
        catch (IllegalArgumentException exception)
        {
            throw error(sourceId, path, "Invalid resource identifier: " + value);
        }
    }

    private static String localId(String value, ResourceLocation sourceId, String path)
    {
        if (!LOCAL_ID.matcher(value).matches()) throw error(sourceId, path, "Invalid local identifier: " + value);

        return value;
    }

    private static void requireMatchingResourceId(ResourceLocation sourceId, String documentId)
    {
        if (!sourceId.toString().equals(documentId)) throw error(sourceId, "$.id", "Document ID " + documentId + " does not match file resource ID " + sourceId + ".");
    }

    private static void keys(JsonObject json, ResourceLocation sourceId, String path, Set<String> permitted)
    {
        for (String key : json.keySet())
        {
            if (!permitted.contains(key)) throw error(sourceId, path + "." + key, "Unexpected field.");
        }
    }

    private static IllegalArgumentException error(ResourceLocation sourceId, String path, String message)
    {
        return new IllegalArgumentException("[" + sourceId + "] " + path + ": " + message);
    }
}
