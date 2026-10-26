package com.TheAgeOfMachinery.persistence;

import com.TheAgeOfMachinery.quest.PlayerQuestProgress;
import com.TheAgeOfMachinery.quest.QuestProgress;
import com.TheAgeOfMachinery.quest.QuestState;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class PlayerQuestProgressCodec
{
    private static final String TAG_PLAYER_ID = "PlayerId";
    private static final String TAG_QUESTS = "Quests";
    private static final String TAG_QUEST_ID = "Id";
    private static final String TAG_STATE = "State";
    private static final String TAG_OBJECTIVES = "Objectives";

    private PlayerQuestProgressCodec()
    {
    }

    public static CompoundTag write(PlayerQuestProgress playerProgress)
    {
        Objects.requireNonNull(playerProgress, "Player progress cannot be null.");

        CompoundTag tag = new CompoundTag();

        UUID playerId = Objects.requireNonNull(playerProgress.getPlayerId(), "Player ID cannot be null.");
        tag.putUUID(TAG_PLAYER_ID, playerId);

        ListTag questsTag = new ListTag();

        for (QuestProgress progress : playerProgress.getQuestProgressSnapshot().values())
        {
            CompoundTag questTag = new CompoundTag();
            String questId = Objects.requireNonNull(progress.getQuestId(), "Quest ID cannot be null.");
            questTag.putString(TAG_QUEST_ID, questId);
            String stateName = Objects.requireNonNull(progress.getState().name(), "Quest state cannot be null.");
            questTag.putString(TAG_STATE, stateName);

            CompoundTag objectivesTag = new CompoundTag();

            for (Map.Entry<String, Integer> entry : progress.getObjectiveProgressSnapshot().entrySet())
            {
                String objectiveId = Objects.requireNonNull(entry.getKey(), "Objective ID cannot be null.");
                objectivesTag.putInt(objectiveId, entry.getValue());
            }

            questTag.put(TAG_OBJECTIVES, objectivesTag);
            questsTag.add(questTag);
        }

        tag.put(TAG_QUESTS, questsTag);
        return tag;
    }

    public static PlayerQuestProgress read(CompoundTag tag)
    {
        Objects.requireNonNull(tag, "Player progress tag cannot be null.");

        if (!tag.hasUUID(TAG_PLAYER_ID)) throw new IllegalArgumentException("Saved player UUID is missing.");
        if (!tag.contains(TAG_QUESTS, Tag.TAG_LIST)) throw new IllegalArgumentException("Saved quests list is missing.");

        UUID playerId = tag.getUUID(TAG_PLAYER_ID);
        Map<String, QuestProgress> savedQuests = new HashMap<>();

        ListTag questsTag = tag.getList(TAG_QUESTS, Tag.TAG_COMPOUND);

        for (int index = 0; index < questsTag.size(); index++)
        {
            CompoundTag questTag = questsTag.getCompound(index);

            String questId = questTag.getString(TAG_QUEST_ID);
            QuestState state = QuestState.valueOf(questTag.getString(TAG_STATE));

            if (!questTag.contains(TAG_OBJECTIVES, Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Saved objective progress is missing.");

            CompoundTag objectivesTag = questTag.getCompound(TAG_OBJECTIVES);
            Map<String, Integer> objectiveProgress = new HashMap<>();

            for (String objectiveId : objectivesTag.getAllKeys())
            {
                objectiveId = Objects.requireNonNull(objectiveId, "Objective ID cannot be null.");
                if (!objectivesTag.contains(objectiveId, Tag.TAG_INT)) throw new IllegalArgumentException("Invalid objective progress type: " + objectiveId);

                objectiveProgress.put(objectiveId, objectivesTag.getInt(objectiveId));
            }

            QuestProgress progress = QuestProgress.restore(questId, state, objectiveProgress);

            if (savedQuests.putIfAbsent(questId, progress) != null)
            {
                throw new IllegalArgumentException("Duplicate saved quest ID: " + questId);
            }
        }

        return PlayerQuestProgress.restore(playerId, savedQuests);
    }
}
