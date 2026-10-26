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

    private record PlayerState(PlayerQuestProgress quests, PlayerRewardInventory rewards)
    {
    }

    private int testValue = 0;
    private final Map<UUID, PlayerState> players = new HashMap<>();

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

                UUID playerId = progress.getPlayerId();
                PlayerState existing = data.players.get(playerId);

                if (existing != null && existing.quests() != null) throw new IllegalArgumentException("Duplicate saved player UUID: " + playerId);

                PlayerRewardInventory rewards = existing == null ? null : existing.rewards();

                data.players.put(playerId, new PlayerState(progress, rewards));
            }
        }

        if (version >= 3)
        {
            if (!tag.contains(TAG_REWARD_INVENTORIES, Tag.TAG_LIST)) throw new IllegalArgumentException("Saved reward inventories list is missing.");

            ListTag inventoriesTag = tag.getList(TAG_REWARD_INVENTORIES, Tag.TAG_COMPOUND);

            for (int index = 0; index < inventoriesTag.size(); index++)
            {
                CompoundTag inventoryTag = inventoriesTag.getCompound(index);
                PlayerRewardInventory inventory = PlayerRewardInventoryCodec.read(inventoryTag);

                UUID playerId = inventory.getPlayerId();
                PlayerState existing = data.players.get(playerId);

                if (existing != null && existing.rewards() != null) throw new IllegalArgumentException("Duplicate saved reward inventory UUID: " + playerId);

                PlayerQuestProgress quests = existing == null ? null : existing.quests();

                data.players.put(playerId, new PlayerState(quests, inventory));
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
        ListTag inventoriesTag = new ListTag();

        for (PlayerState state : players.values())
        {
            if (state.quests() != null)
            {
                CompoundTag playerTag = PlayerQuestProgressCodec.write(state.quests());
                playersTag.add(playerTag);
            }

            if (state.rewards() != null)
            {
                CompoundTag inventoryTag = PlayerRewardInventoryCodec.write(state.rewards());
                inventoriesTag.add(inventoryTag);
            }
        }

        tag.put(TAG_PLAYERS, playersTag);
        tag.put(TAG_REWARD_INVENTORIES, inventoriesTag);

        return tag;
    }

    public Optional<PlayerQuestProgress> getPlayerQuestProgress(UUID playerId)
    {
        Objects.requireNonNull(playerId, "Player ID cannot be null.");

        PlayerState state = players.get(playerId);

        if (state == null) return Optional.empty();

        return Optional.ofNullable(state.quests());
    }

    public void storePlayerQuestProgress(PlayerQuestProgress progress)
    {
        Objects.requireNonNull(progress, "Player progress cannot be null.");

        UUID playerId = progress.getPlayerId();
        PlayerState existing = players.get(playerId);

        PlayerRewardInventory rewards = existing == null ? null : existing.rewards();

        players.put(playerId, new PlayerState(progress, rewards));
        setDirty();
    }

    public Optional<PlayerRewardInventory> getPlayerRewardInventory(UUID playerId)
    {
        Objects.requireNonNull(playerId, "Player ID cannot be null.");

        PlayerState state = players.get(playerId);

        if (state == null) return Optional.empty();

        return Optional.ofNullable(state.rewards());
    }

    public void storePlayerRewardInventory(PlayerRewardInventory inventory)
    {
        Objects.requireNonNull(inventory, "Reward inventory cannot be null.");

        UUID playerId = inventory.getPlayerId();
        PlayerState existing = players.get(playerId);

        PlayerQuestProgress quests = existing == null ? null : existing.quests();

        players.put(playerId, new PlayerState(quests, inventory));
        setDirty();
    }

    public boolean commitAdvancement(UUID playerId, PlayerQuestProgress expectedQuests, PlayerRewardInventory expectedRewards, PlayerQuestProgress updatedQuests, PlayerRewardInventory updatedRewards)
    {
        Objects.requireNonNull(playerId, "Player ID cannot be null.");
        Objects.requireNonNull(expectedQuests, "Expected quest progress cannot be null.");
        Objects.requireNonNull(updatedQuests, "Updated quest progress cannot be null.");
        Objects.requireNonNull(updatedRewards, "Updated reward inventory cannot be null.");

        if (!playerId.equals(updatedQuests.getPlayerId())) throw new IllegalArgumentException("Updated quests belong to another player.");
        if (!playerId.equals(updatedRewards.getPlayerId())) throw new IllegalArgumentException("Updated rewards belong to another player.");

        PlayerState current = players.get(playerId);

        if (current == null) return false;
        if (current.quests() != expectedQuests) return false;
        if (current.rewards() != expectedRewards) return false;

        players.put(playerId, new PlayerState(updatedQuests, updatedRewards));
        setDirty();

        return true;
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
