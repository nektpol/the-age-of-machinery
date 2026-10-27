package com.TheAgeOfMachinery.create;

import com.TheAgeOfMachinery.create.ownership.MachineOwnership;
import com.TheAgeOfMachinery.create.ownership.MachineOwnershipRegistry;
import com.TheAgeOfMachinery.runtime.CampaignRuntime;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class CreateKineticMonitor
{
    private static final int SAMPLE_INTERVAL_TICKS = 10;

    private CreateKineticMonitor()
    {
    }

    public static void onServerTick(ServerTickEvent.Post event)
    {
        MinecraftServer server = event.getServer();

        if (server.getTickCount() % SAMPLE_INTERVAL_TICKS != 0) return;
        if (CampaignRuntime.findIfStarted(server).isEmpty()) return;

        List<MachineOwnership> installations = MachineOwnershipRegistry.getInstallationsSnapshot(server);

        for (MachineOwnership installation : installations)
        {
            inspectInstallation(server, installation);
        }
    }

    private static void inspectInstallation(MinecraftServer server, MachineOwnership installation)
    {
        MachineLocation location = installation.location();
        ServerLevel level = server.getLevel(Objects.requireNonNull(location.dimension()));

        if (level == null) return;

        BlockPos position = Objects.requireNonNull(location.position());
        int chunkX = position.getX() >> 4;
        int chunkZ = position.getZ() >> 4;

        if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) return;

        Optional<MachineOwnership> currentOwnership = MachineOwnershipRegistry.find(level, position);

        if (currentOwnership.isEmpty()) return;
        if (!currentOwnership.get().installationId().equals(installation.installationId())) return;

        BlockEntity blockEntity = level.getBlockEntity(position);

        if (!(blockEntity instanceof KineticBlockEntity kinetic)) return;
        if (kinetic.isOverStressed()) return;
        if (!kinetic.isSpeedRequirementFulfilled()) return;

        float speed = kinetic.getSpeed();

        if (!Float.isFinite(speed) || speed == 0f) return;

        KineticPowerEvent kineticEvent = new KineticPowerEvent(location, installation.blockId(), speed, false);

        CreateObjectiveRouter.route(level, kineticEvent);
    }
}

