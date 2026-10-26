package com.TheAgeOfMachinery.runtime;

import com.TheAgeOfMachinery.quest.ObjectiveTriggerType;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

public final class CampaignItemPickupEvents
{
    private CampaignItemPickupEvents()
    {
    }

    public static void onItemPickedUp(ItemEntityPickupEvent.Post event)
    {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        ItemStack originalStack = event.getOriginalStack();
        ItemStack remainingStack = event.getCurrentStack();

        if (originalStack.isEmpty()) return;

        int acquiredAmount = originalStack.getCount() - remainingStack.getCount();

        if (acquiredAmount <= 0) return;

        CampaignObjectiveRouter.routeItemEvent(player, ObjectiveTriggerType.PICKUP_ITEM, originalStack, acquiredAmount);
    }
}
