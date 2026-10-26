package com.TheAgeOfMachinery.persistence;

import com.TheAgeOfMachinery.reward.ExperienceReward;
import com.TheAgeOfMachinery.reward.ItemReward;
import com.TheAgeOfMachinery.reward.PlayerRewardInventory;
import com.TheAgeOfMachinery.reward.RewardClaimState;
import com.TheAgeOfMachinery.reward.RewardDefinition;
import com.TheAgeOfMachinery.reward.RewardEntry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class PlayerRewardInventoryCodec
{
    private static final String TAG_PLAYER_ID = "PlayerId";
    private static final String TAG_GROUPS = "RewardGroups";
    private static final String TAG_QUEST_ID = "QuestId";
    private static final String TAG_REWARDS = "Rewards";
    private static final String TAG_REWARD_ID = "Id";
    private static final String TAG_TYPE = "Type";
    private static final String TAG_AMOUNT = "Amount";
    private static final String TAG_ITEM_ID = "ItemId";
    private static final String TAG_STATE = "State";

    private static final String TYPE_EXPERIENCE = "experience";
    private static final String TYPE_ITEM = "item";

    private PlayerRewardInventoryCodec()
    {
    }

    public static CompoundTag write(PlayerRewardInventory inventory)
    {
        Objects.requireNonNull(inventory, "Reward inventory cannot be null.");

        CompoundTag tag = new CompoundTag();
        UUID playerId = Objects.requireNonNull(inventory.getPlayerId(), "Player ID cannot be null.");

        tag.putUUID(TAG_PLAYER_ID, playerId);

        ListTag groupsTag = new ListTag();

        for (Map.Entry<String, List<RewardEntry>> group : inventory.getRewardGroupsSnapshot().entrySet())
        {
            CompoundTag groupTag = new CompoundTag();
            String questId = Objects.requireNonNull(group.getKey(), "Quest ID cannot be null.");

            groupTag.putString(TAG_QUEST_ID, questId);

            ListTag rewardsTag = new ListTag();

            for (RewardEntry entry : group.getValue())
            {
                CompoundTag rewardTag = new CompoundTag();
                RewardDefinition reward = entry.reward();

                rewardTag.putString(TAG_REWARD_ID, Objects.requireNonNull(reward.id(), "Reward ID cannot be null."));
                rewardTag.putInt(TAG_AMOUNT, reward.amount());
                rewardTag.putString(TAG_STATE, Objects.requireNonNull(entry.state().name(), "Reward state cannot be null."));

                if (reward instanceof ExperienceReward)
                {
                    rewardTag.putString(TAG_TYPE, TYPE_EXPERIENCE);
                }
                else if (reward instanceof ItemReward item)
                {
                    rewardTag.putString(TAG_TYPE, TYPE_ITEM);
                    rewardTag.putString(TAG_ITEM_ID, Objects.requireNonNull(item.itemId(), "Item ID cannot be null."));
                }
                else
                {
                    throw new IllegalArgumentException("Unsupported reward type: " + reward.getClass().getName());
                }

                rewardsTag.add(rewardTag);
            }

            groupTag.put(TAG_REWARDS, rewardsTag);
            groupsTag.add(groupTag);
        }

        tag.put(TAG_GROUPS, groupsTag);
        return tag;
    }

    public static PlayerRewardInventory read(CompoundTag tag)
    {
        Objects.requireNonNull(tag, "Reward inventory tag cannot be null.");

        if (!tag.hasUUID(TAG_PLAYER_ID)) throw new IllegalArgumentException("Saved player UUID is missing.");
        if (!tag.contains(TAG_GROUPS, Tag.TAG_LIST)) throw new IllegalArgumentException("Saved reward groups are missing.");

        UUID playerId = tag.getUUID(TAG_PLAYER_ID);
        Map<String, List<RewardEntry>> savedRewards = new LinkedHashMap<>();

        ListTag groupsTag = tag.getList(TAG_GROUPS, Tag.TAG_COMPOUND);

        for (int groupIndex = 0; groupIndex < groupsTag.size(); groupIndex++)
        {
            CompoundTag groupTag = groupsTag.getCompound(groupIndex);
            String questId = groupTag.getString(TAG_QUEST_ID);

            if (questId.isBlank()) throw new IllegalArgumentException("Saved quest ID cannot be empty.");
            if (!groupTag.contains(TAG_REWARDS, Tag.TAG_LIST)) throw new IllegalArgumentException("Saved reward list is missing.");

            ListTag rewardsTag = groupTag.getList(TAG_REWARDS, Tag.TAG_COMPOUND);
            List<RewardEntry> entries = new ArrayList<>();

            for (int rewardIndex = 0; rewardIndex < rewardsTag.size(); rewardIndex++)
            {
                CompoundTag rewardTag = rewardsTag.getCompound(rewardIndex);

                String rewardId = rewardTag.getString(TAG_REWARD_ID);
                String rewardType = rewardTag.getString(TAG_TYPE);
                int amount = rewardTag.getInt(TAG_AMOUNT);

                RewardClaimState state = RewardClaimState.valueOf(rewardTag.getString(TAG_STATE));
                RewardDefinition reward;

                if (rewardType.equals(TYPE_EXPERIENCE))
                {
                    reward = new ExperienceReward(rewardId, amount);
                }
                else if (rewardType.equals(TYPE_ITEM))
                {
                    String itemId = rewardTag.getString(TAG_ITEM_ID);
                    reward = new ItemReward(rewardId, itemId, amount);
                }
                else
                {
                    throw new IllegalArgumentException("Unknown reward type: " + rewardType);
                }

                entries.add(new RewardEntry(questId, reward, state));
            }

            if (savedRewards.putIfAbsent(questId, List.copyOf(entries)) != null)
            {
                throw new IllegalArgumentException("Duplicate saved reward group: " + questId);
            }
        }

        return PlayerRewardInventory.restore(playerId, savedRewards);
    }
}
