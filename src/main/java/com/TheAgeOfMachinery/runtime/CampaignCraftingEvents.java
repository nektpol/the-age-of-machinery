
package com.TheAgeOfMachinery.runtime;

import com.TheAgeOfMachinery.quest.PlayerQuestProgress;
import com.TheAgeOfMachinery.quest.QuestDefinition;
import com.TheAgeOfMachinery.quest.QuestObjective;
import com.TheAgeOfMachinery.quest.QuestProgress;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Objects;
import java.util.Optional;

public final class CampaignCraftingEvents
{
    private static final String QUEST_ID = "theageofmachinery:prototype_first_tools";
    private static final String OBJECTIVE_ID = "craft_crafting_table";

    private CampaignCraftingEvents()
    {
    }

    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack craftedStack = event.getCrafting();

        if (craftedStack.isEmpty()) return;
        if (!craftedStack.is(Objects.requireNonNull(Items.CRAFTING_TABLE))) return;

        MinecraftServer server = player.getServer();

        if (server == null) return;

        CampaignRuntime runtime = CampaignRuntime.get(server);
        Optional<QuestDefinition> activeQuestResult = runtime.getActiveQuestDefinition(player);

        if (activeQuestResult.isEmpty()) return;

        QuestDefinition activeQuest = activeQuestResult.get();

        if (!QUEST_ID.equals(activeQuest.id())) return;

        QuestObjective targetObjective = null;

        for (QuestObjective objective : activeQuest.objectives())
        {
            if (OBJECTIVE_ID.equals(objective.id()))
            {
                targetObjective = objective;
                break;
            }
        }

        if (targetObjective == null) return;

        Optional<PlayerQuestProgress> playerProgressResult = runtime.getPlayerQuestProgress(player);

        if (playerProgressResult.isEmpty()) return;

        Optional<QuestProgress> activeProgressResult = playerProgressResult.get().getActiveQuestProgress();

        if (activeProgressResult.isEmpty()) return;

        int currentAmount = activeProgressResult.get().getObjectiveProgress(OBJECTIVE_ID);
        int requiredAmount = targetObjective.requiredAmount();

        if (currentAmount >= requiredAmount) return;

        int progressAmount = Math.min(craftedStack.getCount(), requiredAmount - currentAmount);

        if (progressAmount <= 0) return;
        if (!runtime.addObjectiveProgress(player, OBJECTIVE_ID, progressAmount)) return;

        int updatedAmount = currentAmount + progressAmount;
        Component message = Objects.requireNonNull(Component.literal("Quest objective updated: " + targetObjective.description() + " (" + updatedAmount + "/" + requiredAmount + ")"));

        player.sendSystemMessage(message);
    }
}
