package com.TheAgeOfMachinery.persistence;

import com.TheAgeOfMachinery.quest.PlayerQuestProgress;
import com.TheAgeOfMachinery.reward.PlayerRewardInventory;
import com.TheAgeOfMachinery.reward.RewardClaimResult;
import com.TheAgeOfMachinery.reward.RewardDelivery;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class CampaignSavedData extends SavedData
{
    private static final String FILE_NAME = "theageofmachinery_campaign";

    private static final String TAG_VERSION = "FormatVersion";
    private static final String TAG_TEST_VALUE = "TestValue";
    private static final String TAG_PLAYERS = "Players";
    private static final String TAG_REWARD_INVENTORIES = "RewardInventories";

    private static final int FORMAT_VERSION = 3;

    private record PlayerState(PlayerQuestProgress quests, PlayerRewardInventory rewards, long revision)
    {
    }

    public record PlayerSnapshot(long revision, PlayerQuestProgress quests, PlayerRewardInventory rewards)
    {
    }

    private int testValue = 0;
    private final Map<UUID, PlayerState> players = new HashMap<>();
    private final Set<UUID> claimsInProgress = new HashSet<>();

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
                PlayerQuestProgress progress = PlayerQuestProgressCodec.read(playersTag.getCompound(index));
                UUID playerId = progress.getPlayerId();

                PlayerState existing = data.players.get(playerId);

                if (existing != null && existing.quests() != null) throw new IllegalArgumentException("Duplicate saved player UUID: " + playerId);

                PlayerRewardInventory rewards = existing == null ? null : existing.rewards();

                data.players.put(playerId, new PlayerState(progress, rewards, 0));
            }
        }

        if (version >= 3)
        {
            if (!tag.contains(TAG_REWARD_INVENTORIES, Tag.TAG_LIST)) throw new IllegalArgumentException("Saved reward inventories list is missing.");

            ListTag inventoriesTag = tag.getList(TAG_REWARD_INVENTORIES, Tag.TAG_COMPOUND);

            for (int index = 0; index < inventoriesTag.size(); index++)
            {
                PlayerRewardInventory inventory = PlayerRewardInventoryCodec.read(inventoriesTag.getCompound(index));
                UUID playerId = inventory.getPlayerId();

                PlayerState existing = data.players.get(playerId);

                if (existing != null && existing.rewards() != null) throw new IllegalArgumentException("Duplicate saved reward inventory UUID: " + playerId);

                PlayerQuestProgress quests = existing == null ? null : existing.quests();

                data.players.put(playerId, new PlayerState(quests, inventory, 0));
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
                playersTag.add(PlayerQuestProgressCodec.write(state.quests()));
            }

            if (state.rewards() != null)
            {
                inventoriesTag.add(PlayerRewardInventoryCodec.write(state.rewards()));
            }
        }

        tag.put(TAG_PLAYERS, playersTag);
        tag.put(TAG_REWARD_INVENTORIES, inventoriesTag);

        return tag;
    }

    private static PlayerQuestProgress copyQuests(PlayerQuestProgress progress)
    {
        if (progress == null) return null;

        return PlayerQuestProgressCodec.read(PlayerQuestProgressCodec.write(progress));
    }

    private static PlayerRewardInventory copyRewards(PlayerRewardInventory inventory)
    {
        if (inventory == null) return null;

        return PlayerRewardInventoryCodec.read(PlayerRewardInventoryCodec.write(inventory));
    }

    public Optional<PlayerSnapshot> getPlayerSnapshot(UUID playerId)
    {
        Objects.requireNonNull(playerId, "Player ID cannot be null.");

        PlayerState state = players.get(playerId);

        if (state == null) return Optional.empty();

        return Optional.of(new PlayerSnapshot(state.revision(), copyQuests(state.quests()), copyRewards(state.rewards())));
    }

    public Optional<PlayerQuestProgress> getPlayerQuestProgress(UUID playerId)
    {
        Objects.requireNonNull(playerId, "Player ID cannot be null.");

        PlayerState state = players.get(playerId);

        if (state == null) return Optional.empty();

        return Optional.ofNullable(copyQuests(state.quests()));
    }

    public Optional<PlayerRewardInventory> getPlayerRewardInventory(UUID playerId)
    {
        Objects.requireNonNull(playerId, "Player ID cannot be null.");

        PlayerState state = players.get(playerId);

        if (state == null) return Optional.empty();

        return Optional.ofNullable(copyRewards(state.rewards()));
    }

    public boolean commitAdvancement(UUID playerId, long expectedRevision, PlayerQuestProgress updatedQuests, PlayerRewardInventory updatedRewards)
    {
        Objects.requireNonNull(playerId, "Player ID cannot be null.");
        Objects.requireNonNull(updatedQuests, "Updated quest progress cannot be null.");
        Objects.requireNonNull(updatedRewards, "Updated reward inventory cannot be null.");

        if (!playerId.equals(updatedQuests.getPlayerId())) throw new IllegalArgumentException("Updated quests belong to another player.");
        if (!playerId.equals(updatedRewards.getPlayerId())) throw new IllegalArgumentException("Updated rewards belong to another player.");

        if (claimsInProgress.contains(playerId)) return false;
        PlayerState current = players.get(playerId);

        if (current == null) return false;
        if (current.revision() != expectedRevision) return false;
        if (current.quests() == null) return false;

        PlayerQuestProgress committedQuests = copyQuests(updatedQuests);
        PlayerRewardInventory committedRewards = copyRewards(updatedRewards);

        players.put(playerId, new PlayerState(committedQuests, committedRewards, current.revision() + 1));
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
    
    public boolean tryStorePlayerQuestProgress(UUID playerId, long expectedRevision, PlayerQuestProgress progress)
    {
        Objects.requireNonNull(playerId, "Player ID cannot be null.");
        if (claimsInProgress.contains(playerId)) return false;
        Objects.requireNonNull(progress, "Player progress cannot be null.");

        if (!playerId.equals(progress.getPlayerId())) throw new IllegalArgumentException("Quest progress belongs to another player.");
        if (expectedRevision < -1) throw new IllegalArgumentException("Invalid expected revision.");

        PlayerState current = players.get(playerId);

        if (current == null && expectedRevision != -1) return false;
        if (current != null && current.revision() != expectedRevision) return false;

        PlayerQuestProgress committedQuests = copyQuests(progress);
        PlayerRewardInventory rewards = current == null ? null : current.rewards();
        long nextRevision = current == null ? 1 : current.revision() + 1;

        players.put(playerId, new PlayerState(committedQuests, rewards, nextRevision));
        setDirty();

        return true;
    }

    public boolean tryStorePlayerRewardInventory(UUID playerId, long expectedRevision, PlayerRewardInventory inventory)
    {
        Objects.requireNonNull(playerId, "Player ID cannot be null.");
        Objects.requireNonNull(inventory, "Reward inventory cannot be null.");

        if (!playerId.equals(inventory.getPlayerId())) throw new IllegalArgumentException("Reward inventory belongs to another player.");
        if (expectedRevision < -1) throw new IllegalArgumentException("Invalid expected revision.");
        if (claimsInProgress.contains(playerId)) return false;

        PlayerState current = players.get(playerId);

        if (current == null && expectedRevision != -1) return false;
        if (current != null && current.revision() != expectedRevision) return false;

        PlayerRewardInventory committedRewards = copyRewards(inventory);
        PlayerQuestProgress quests = current == null ? null : current.quests();
        long nextRevision = current == null ? 1 : current.revision() + 1;

        players.put(playerId, new PlayerState(quests, committedRewards, nextRevision));
        setDirty();

        return true;
    }

    public RewardClaimResult executeRewardClaim(UUID playerId, String questId, String rewardId, RewardDelivery delivery)
    {
        Objects.requireNonNull(playerId, "Player ID cannot be null.");
        Objects.requireNonNull(questId, "Quest ID cannot be null.");
        Objects.requireNonNull(rewardId, "Reward ID cannot be null.");
        Objects.requireNonNull(delivery, "Reward delivery cannot be null.");

        if (!claimsInProgress.add(playerId)) return RewardClaimResult.CLAIM_IN_PROGRESS;

        try
        {
            PlayerState current = players.get(playerId);

            if (current == null || current.rewards() == null) return RewardClaimResult.NOT_FOUND;

            RewardClaimResult result = current.rewards().claimReward(questId, rewardId, delivery);

            if (result == RewardClaimResult.SUCCESS)
            {
                players.put(playerId, new PlayerState(current.quests(), current.rewards(), current.revision() + 1));
                setDirty();
            }

            return result;
        }
        finally
        {
            claimsInProgress.remove(playerId);
        }
    }

}
