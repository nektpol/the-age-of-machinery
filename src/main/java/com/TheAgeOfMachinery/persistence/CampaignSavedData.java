package com.TheAgeOfMachinery.persistence;

import com.TheAgeOfMachinery.quest.PlayerQuestProgress;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class CampaignSavedData extends SavedData
{
    private static final String FILE_NAME = "theageofmachinery_campaign";
    private static final String TAG_VERSION = "FormatVersion";
    private static final String TAG_TEST_VALUE = "TestValue";
    private static final String TAG_PLAYERS = "Players";
    private static final int FORMAT_VERSION = 2;

    private int testValue = 0;
    private final Map<UUID, PlayerQuestProgress> playerQuests = new HashMap<>();

    public static CampaignSavedData get(MinecraftServer server)
    {
        Objects.requireNonNull(server, "Minecraft server cannot be null.");

        SavedData.Factory<CampaignSavedData> factory = new SavedData.Factory<>(CampaignSavedData::new, CampaignSavedData::load);

        return server.overworld().getDataStorage().computeIfAbsent(factory, FILE_NAME);
    }

    public static CampaignSavedData load(CompoundTag tag, HolderLookup.Provider registries)
    {
        Objects.requireNonNull(tag, "Saved campaign tag cannot be null.");

        CampaignSavedData data = new CampaignSavedData();

        int version = tag.contains(TAG_VERSION, Tag.TAG_INT) ? tag.getInt(TAG_VERSION) : 1;

        if (version < 1 || version > FORMAT_VERSION) throw new IllegalArgumentException("Unsupported campaign save version: " + version);

        data.testValue = tag.getInt(TAG_TEST_VALUE);

        if (version >= 2)
        {
            if (!tag.contains(TAG_PLAYERS, Tag.TAG_LIST)) throw new IllegalArgumentException("Saved player list is missing.");

            ListTag playersTag = tag.getList(TAG_PLAYERS, Tag.TAG_COMPOUND);

            for (int index = 0; index < playersTag.size(); index++)
            {
                CompoundTag playerTag = playersTag.getCompound(index);
                PlayerQuestProgress progress = PlayerQuestProgressCodec.read(playerTag);

                if (data.playerQuests.putIfAbsent(progress.getPlayerId(), progress) != null)
                {
                    throw new IllegalArgumentException("Duplicate saved player UUID: " + progress.getPlayerId());
                }
            }
        }

        return data;
    }

    @Override
    public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries)
    {
        tag.putInt(TAG_VERSION, FORMAT_VERSION);
        tag.putInt(TAG_TEST_VALUE, testValue);

        ListTag playersTag = new ListTag();

        for (PlayerQuestProgress progress : playerQuests.values())
        {
            CompoundTag playerTag = PlayerQuestProgressCodec.write(progress);
            playersTag.add(playerTag);
        }

        tag.put(TAG_PLAYERS, playersTag);

        return tag;
    }

    public Optional<PlayerQuestProgress> getPlayerQuestProgress(UUID playerId)
    {
        Objects.requireNonNull(playerId, "Player ID cannot be null.");

        return Optional.ofNullable(playerQuests.get(playerId));
    }

    public void storePlayerQuestProgress(PlayerQuestProgress progress)
    {
        Objects.requireNonNull(progress, "Player progress cannot be null.");

        playerQuests.put(progress.getPlayerId(), progress);
        setDirty();
    }

    public int getTestValue()
    {
        return testValue;
    }

    public void incrementTestValue()
    {
        testValue++;
        setDirty();
    }
}