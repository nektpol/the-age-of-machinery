package com.TheAgeOfMachinery.runtime;

import com.TheAgeOfMachinery.quest.ObjectiveTriggerType;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class CampaignCraftingEvents
{
    private CampaignCraftingEvents()
    {
    }

    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack craftedStack = event.getCrafting();

        if (craftedStack.isEmpty()) return;

        CampaignObjectiveRouter.routeItemEvent(player, ObjectiveTriggerType.CRAFT_ITEM, craftedStack, craftedStack.getCount());
    }
}
