package com.TheAgeOfMachinery.runtime;

import com.TheAgeOfMachinery.quest.ObjectiveTrigger;
import com.TheAgeOfMachinery.quest.ObjectiveTriggerType;
import com.TheAgeOfMachinery.quest.PlayerQuestProgress;
import com.TheAgeOfMachinery.quest.QuestDefinition;
import com.TheAgeOfMachinery.quest.QuestObjective;
import com.TheAgeOfMachinery.quest.QuestProgress;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.Optional;

public final class CampaignObjectiveRouter
{
    private CampaignObjectiveRouter()
    {
    }

    public static void routeItemEvent(ServerPlayer player, ObjectiveTriggerType eventType, ItemStack itemStack, int amount)
    {
        Objects.requireNonNull(player, "Player cannot be null.");
        Objects.requireNonNull(eventType, "Event type cannot be null.");
        Objects.requireNonNull(itemStack, "Item stack cannot be null.");

        if (eventType == ObjectiveTriggerType.MANUAL) return;
        if (itemStack.isEmpty() || amount <= 0) return;

        MinecraftServer server = player.getServer();

        if (server == null) return;

        CampaignRuntime runtime = CampaignRuntime.get(server);
        Optional<QuestDefinition> activeQuestResult = runtime.getActiveQuestDefinition(player);

        if (activeQuestResult.isEmpty()) return;

        Optional<PlayerQuestProgress> playerProgressResult = runtime.getPlayerQuestProgress(player);

        if (playerProgressResult.isEmpty()) return;

        Optional<QuestProgress> activeProgressResult = playerProgressResult.get().getActiveQuestProgress();

        if (activeProgressResult.isEmpty()) return;

        QuestDefinition activeQuest = activeQuestResult.get();
        QuestProgress activeProgress = activeProgressResult.get();
        String itemId = Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(itemStack.getItem()))).toString();

        for (QuestObjective objective : activeQuest.objectives())
        {
            ObjectiveTrigger trigger = objective.trigger();

            if (trigger.type() != eventType) continue;
            if (!trigger.itemId().equals(itemId)) continue;

            int currentAmount = activeProgress.getObjectiveProgress(objective.id());
            int remainingAmount = objective.requiredAmount() - currentAmount;

            if (remainingAmount <= 0) continue;

            int progressAmount = Math.min(amount, remainingAmount);

            if (!runtime.addObjectiveProgress(player, objective.id(), progressAmount)) continue;

            int updatedAmount = currentAmount + progressAmount;
            Component message = Objects.requireNonNull(Component.literal("Quest objective updated: " + objective.description() + " (" + updatedAmount + "/" + objective.requiredAmount() + ")"));

            player.sendSystemMessage(message);
        }
    }
}
