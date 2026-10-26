package com.TheAgeOfMachinery.persistence;

import com.TheAgeOfMachinery.quest.PlayerQuestProgress;
import com.TheAgeOfMachinery.reward.PlayerRewardInventory;

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
    private static final String TAG_REWARD_INVENTORIES = "RewardInventories";

    private static final int FORMAT_VERSION = 3;

    private int testValue = 0;

    private final Map<UUID, PlayerQuestProgress> playerQuests = new HashMap<>();
    private final Map<UUID, PlayerRewardInventory> playerRewards = new HashMap<>();

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

        // Version 2 introduced player quest progression.
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

        // Version 3 introduced player reward inventories.
        if (version >= 3)
        {
            if (!tag.contains(TAG_REWARD_INVENTORIES, Tag.TAG_LIST)) throw new IllegalArgumentException("Saved reward inventories list is missing.");

            ListTag inventoriesTag = tag.getList(TAG_REWARD_INVENTORIES, Tag.TAG_COMPOUND);

            for (int index = 0; index < inventoriesTag.size(); index++)
            {
                CompoundTag inventoryTag = inventoriesTag.getCompound(index);
                PlayerRewardInventory inventory = PlayerRewardInventoryCodec.read(inventoryTag);

                if (data.playerRewards.putIfAbsent(inventory.getPlayerId(), inventory) != null)
                {
                    throw new IllegalArgumentException("Duplicate saved reward inventory UUID: " + inventory.getPlayerId());
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

        // Save player quest progress.
        ListTag playersTag = new ListTag();

        for (PlayerQuestProgress progress : playerQuests.values())
        {
            CompoundTag playerTag = PlayerQuestProgressCodec.write(progress);
            playersTag.add(playerTag);
        }

        tag.put(TAG_PLAYERS, playersTag);

        // Save player reward inventories.
        ListTag inventoriesTag = new ListTag();

        for (PlayerRewardInventory inventory : playerRewards.values())
        {
            CompoundTag inventoryTag = PlayerRewardInventoryCodec.write(inventory);
            inventoriesTag.add(inventoryTag);
        }

        tag.put(TAG_REWARD_INVENTORIES, inventoriesTag);

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

    public Optional<PlayerRewardInventory> getPlayerRewardInventory(UUID playerId)
    {
        Objects.requireNonNull(playerId, "Player ID cannot be null.");

        return Optional.ofNullable(playerRewards.get(playerId));
    }

    public void storePlayerRewardInventory(PlayerRewardInventory inventory)
    {
        Objects.requireNonNull(inventory, "Reward inventory cannot be null.");

        playerRewards.put(inventory.getPlayerId(), inventory);
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
