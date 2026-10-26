
package com.TheAgeOfMachinery.integration;

import com.TheAgeOfMachinery.reward.ExperienceReward;
import com.TheAgeOfMachinery.reward.ItemReward;
import com.TheAgeOfMachinery.reward.RewardDefinition;
import com.TheAgeOfMachinery.reward.RewardDelivery;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

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

        if (reward instanceof ItemReward item)
        {
            return deliverItem(item);
        }

        return false;
    }

    private boolean deliverItem(ItemReward reward)
    {
        String itemIdString = Objects.requireNonNull(reward.itemId(), "Reward item ID cannot be null.");
        ResourceLocation itemId = ResourceLocation.tryParse(itemIdString);

        if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) return false;

        Item item = BuiltInRegistries.ITEM.get(itemId);
        ItemStack template = new ItemStack(item);
        Inventory inventory = player.getInventory();

        ItemStack[] plannedStacks = new ItemStack[Inventory.INVENTORY_SIZE];
        boolean[] modifiedSlots = new boolean[Inventory.INVENTORY_SIZE];

        for (int slot = 0; slot < plannedStacks.length; slot++)
        {
            plannedStacks[slot] = inventory.getItem(slot).copy();
        }

        int remaining = reward.amount();

        // Phase 1A: Fill existing compatible stacks.
        for (int slot = 0; slot < plannedStacks.length && remaining > 0; slot++)
        {
            ItemStack stack = plannedStacks[slot];

            if (stack.isEmpty()) continue;
            if (!ItemStack.isSameItemSameComponents(stack, template)) continue;

            int maxStackSize = inventory.getMaxStackSize(stack);
            int availableSpace = Math.max(0, maxStackSize - stack.getCount());
            int toAdd = Math.min(remaining, availableSpace);

            if (toAdd <= 0) continue;

            stack.grow(toAdd);
            remaining -= toAdd;
            modifiedSlots[slot] = true;
        }

        // Phase 1B: Use empty inventory slots.
        for (int slot = 0; slot < plannedStacks.length && remaining > 0; slot++)
        {
            if (!plannedStacks[slot].isEmpty()) continue;

            int toAdd = Math.min(remaining, inventory.getMaxStackSize(template));

            if (toAdd <= 0) return false;

            plannedStacks[slot] = template.copyWithCount(toAdd);
            remaining -= toAdd;
            modifiedSlots[slot] = true;
        }

        // Cancel the delivery if the complete reward does not fit.
        if (remaining > 0) return false;

        // Phase 2: Apply the complete delivery plan.
        for (int slot = 0; slot < plannedStacks.length; slot++)
        {
            if (!modifiedSlots[slot]) continue;

            ItemStack plannedStack = Objects.requireNonNull(plannedStacks[slot], "Planned item stack cannot be null.");
            inventory.setItem(slot, plannedStack);
        }

        inventory.setChanged();
        return true;
    }
}


