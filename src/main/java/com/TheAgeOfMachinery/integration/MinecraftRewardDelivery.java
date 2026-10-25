package com.TheAgeOfMachinery.integration;

import com.TheAgeOfMachinery.reward.ExperienceReward;
import com.TheAgeOfMachinery.reward.RewardDefinition;
import com.TheAgeOfMachinery.reward.RewardDelivery;

import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

public class MinecraftRewardDelivery implements RewardDelivery
{
    private final ServerPlayer player;

    public MinecraftRewardDelivery(ServerPlayer player)
    {
        this.player = Objects.requireNonNull(player, "Player cannot be null.");
    }

    @Override
    public boolean deliver(RewardDefinition reward)
    {
        Objects.requireNonNull(reward, "Reward cannot be null.");

        if (reward instanceof ExperienceReward xp)
        {
            player.giveExperiencePoints(xp.amount());
            return true;
        }

        return false;
    }
}

