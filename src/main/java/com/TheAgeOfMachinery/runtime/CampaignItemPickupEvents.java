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
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

import java.util.Objects;
import java.util.Optional;

public final class CampaignItemPickupEvents
{
    private static final String QUEST_ID = "theageofmachinery:prototype_first_workshop";
    private static final String COBBLESTONE_OBJECTIVE_ID = "gather_cobblestone";
    private static final String OAK_LOG_OBJECTIVE_ID = "gather_logs";

    private CampaignItemPickupEvents()
    {
    }

    public static void onItemPickedUp(ItemEntityPickupEvent.Post event)
    {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        ItemStack originalStack = event.getOriginalStack();
        ItemStack remainingStack = event.getCurrentStack();

        if (originalStack.isEmpty()) return;

        String objectiveId;

        if (originalStack.is(Objects.requireNonNull(Items.COBBLESTONE)))
        {
            objectiveId = COBBLESTONE_OBJECTIVE_ID;
        }
        else if (originalStack.is(Objects.requireNonNull(Items.OAK_LOG)))
        {
            objectiveId = OAK_LOG_OBJECTIVE_ID;
        }
        else
        {
            return;
        }

        int acquiredAmount = originalStack.getCount() - remainingStack.getCount();

        if (acquiredAmount <= 0) return;

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
            if (objectiveId.equals(objective.id()))
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

        int currentAmount = activeProgressResult.get().getObjectiveProgress(objectiveId);
        int requiredAmount = targetObjective.requiredAmount();

        if (currentAmount >= requiredAmount) return;

        int progressAmount = Math.min(acquiredAmount, requiredAmount - currentAmount);

        if (progressAmount <= 0) return;
        if (!runtime.addObjectiveProgress(player, objectiveId, progressAmount)) return;

        int updatedAmount = currentAmount + progressAmount;
        Component message = Objects.requireNonNull(Component.literal("Quest objective updated: " + targetObjective.description() + " (" + updatedAmount + "/" + requiredAmount + ")"));

        player.sendSystemMessage(message);
    }
}
