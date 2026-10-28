package com.TheAgeOfMachinery.create;

import com.TheAgeOfMachinery.quest.ObjectiveTrigger;
import com.TheAgeOfMachinery.quest.ObjectiveTriggerType;
import com.TheAgeOfMachinery.quest.PlayerQuestProgress;
import com.TheAgeOfMachinery.quest.QuestDefinition;
import com.TheAgeOfMachinery.quest.QuestObjective;
import com.TheAgeOfMachinery.quest.QuestProgress;
import com.TheAgeOfMachinery.runtime.CampaignRuntime;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class CreateObjectiveRouter
{
    private static final int MAX_RECORDED_OPERATIONS = 4096;

    private static final Map<MinecraftServer, ProcessingOperationLedger> PROCESSED_OPERATIONS = new IdentityHashMap<>();

    private CreateObjectiveRouter()
    {
    }

    public static int route(ServerLevel level, CreateMachineEvent event)
    {
        return route(level, event, Set.of());
    }

    // A physical wheel pair shares one production run; owners of both wheels receive it once.
    static int route(ServerLevel level, CreateMachineEvent event, Set<UUID> excludedOwners)
    {
        Objects.requireNonNull(level, "Level cannot be null.");
        Objects.requireNonNull(event, "Machine event cannot be null.");

        MinecraftServer server = level.getServer();

        if (!server.isSameThread()) throw new IllegalStateException("Create objectives must be routed on the server thread.");

        Optional<AttributedMachineEvent> result = CreateEventAttribution.resolve(level, event);

        if (result.isEmpty()) return 0;

        AttributedMachineEvent attributed = result.get();

        if (event instanceof ProcessingCompletedEvent processing)
        {
            if (!rememberOperation(server, attributed.installationId(), processing.operationId(), processing.outputItemId())) return 0;
        }

        Optional<CampaignRuntime> runtimeResult = CampaignRuntime.findIfStarted(server);

        if (runtimeResult.isEmpty()) return 0;

        CampaignRuntime runtime = runtimeResult.get();
        int updatedObjectives = 0;

        for (UUID ownerId : attributed.owners())
        {
            if (excludedOwners.contains(ownerId)) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(Objects.requireNonNull(ownerId));

            if (player == null) continue;

            Optional<QuestDefinition> definitionResult = runtime.getActiveQuestDefinition(player);
            Optional<PlayerQuestProgress> progressResult = runtime.getPlayerQuestProgress(player);

            if (definitionResult.isEmpty() || progressResult.isEmpty()) continue;

            Optional<QuestProgress> activeProgressResult = progressResult.get().getActiveQuestProgress();

            if (activeProgressResult.isEmpty()) continue;

            QuestDefinition definition = definitionResult.get();
            QuestProgress activeProgress = activeProgressResult.get();

            if (!definition.id().equals(activeProgress.getQuestId())) continue;

            for (QuestObjective objective : definition.objectives())
            {
                if (!matches(objective.trigger(), event)) continue;

                int current = activeProgress.getObjectiveProgress(objective.id());
                int remaining = objective.requiredAmount() - current;

                if (remaining <= 0) continue;

                int amount = event instanceof ProcessingCompletedEvent processing ? Math.min(processing.outputAmount(), remaining) : 1;

                if (runtime.addObjectiveProgress(player, objective.id(), amount))
                {
                    updatedObjectives++;
                }
            }
        }

        return updatedObjectives;
    }

    private static boolean matches(ObjectiveTrigger trigger, CreateMachineEvent event)
    {
        if (!trigger.machineId().equals(event.machineBlockId().toString())) return false;

        if (event instanceof KineticPowerEvent kinetic)
        {
            if (trigger.type() != ObjectiveTriggerType.KINETIC_ACTIVE) return false;
            if (kinetic.overstressed()) return false;

            return Math.abs(kinetic.speed()) >= trigger.minSpeed();
        }

        if (event instanceof ProcessingCompletedEvent processing)
        {
            if (trigger.type() != ObjectiveTriggerType.MACHINE_PROCESS) return false;

            return trigger.itemId().equals(processing.outputItemId().toString());
        }

        return false;
    }

    private static boolean rememberOperation(MinecraftServer server, UUID installationId, UUID operationId, ResourceLocation outputItemId)
    {
        ProcessingOperationLedger ledger = PROCESSED_OPERATIONS.computeIfAbsent(server, ignored -> new ProcessingOperationLedger(MAX_RECORDED_OPERATIONS));

        return ledger.remember(installationId, operationId, outputItemId.toString());
    }

    public static void onServerStopped(ServerStoppedEvent event)
    {
        PROCESSED_OPERATIONS.remove(event.getServer());
    }
}
