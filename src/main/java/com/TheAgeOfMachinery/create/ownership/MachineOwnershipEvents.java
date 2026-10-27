package com.TheAgeOfMachinery.create.ownership;

import com.TheAgeOfMachinery.create.MachineLocation;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MachineOwnershipEvents 
{

    private static final Map<MinecraftServer, Map<MachineLocation, PendingChange>> PENDING = new IdentityHashMap<>();

    private MachineOwnershipEvents() 
    {
    }

    private record PendingPlacement(UUID builderId, ResourceLocation blockId) 
    {
    }

    private record PendingRemoval(UUID installationId, BlockEntity originalBlockEntity) 
    {
    }

    private static final class PendingChange 
    {
        PendingPlacement placement;
        PendingRemoval removal;
    }

    public static void onPlacement(BlockEvent.EntityPlaceEvent event) 
    {
        if (event.isCanceled()) return;
        if (event instanceof BlockEvent.EntityMultiPlaceEvent) return;
        if (!(event.getEntity() instanceof ServerPlayer builder)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getPlacedBlock().getBlock() instanceof KineticBlock)) return;

        BlockPos position = event.getPos();

        // This phase handles newly placed blocks, not same-block state replacements.
        BlockState previousState = event.getBlockSnapshot().getState();
        if (previousState.getBlock() == event.getPlacedBlock().getBlock()) return;

        ResourceLocation blockId = Objects.requireNonNull(BuiltInRegistries.BLOCK.getKey(Objects.requireNonNull(event.getPlacedBlock().getBlock())));
        MachineLocation location = new MachineLocation(level.dimension(), position);

        pendingChange(level.getServer(), location).placement = new PendingPlacement(builder.getUUID(), blockId);
    }

    public static void onBreak(BlockEvent.BreakEvent event) 
    {
        if (event.isCanceled()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        BlockPos position = event.getPos();
        Optional<MachineOwnership> ownership = MachineOwnershipRegistry.find(level, position);

        if (ownership.isEmpty()) return;

        BlockEntity originalBlockEntity = level.getBlockEntity(Objects.requireNonNull(position, "position"));

        if (!(originalBlockEntity instanceof KineticBlockEntity)) return;

        MachineLocation location = new MachineLocation(level.dimension(), position);
        pendingChange(level.getServer(), location).removal = new PendingRemoval(ownership.get().installationId(), originalBlockEntity);
    }

    public static void onServerTick(ServerTickEvent.Post event) 
    {
        MinecraftServer server = event.getServer();
        Map<MachineLocation, PendingChange> changes = PENDING.remove(server);

        if (changes == null || changes.isEmpty()) return;

        for (Map.Entry<MachineLocation, PendingChange> entry : changes.entrySet()) 
        {
            MachineLocation location = entry.getKey();
            PendingChange change = entry.getValue();

            ServerLevel level = server.getLevel(Objects.requireNonNull(location.dimension(), "location.dimension"));
            if (level == null) continue;

            BlockPos position = location.position();

            if (!level.getChunkSource().hasChunk(position.getX() >> 4, position.getZ() >> 4)) continue;

            BlockState currentState = level.getBlockState(position);
            BlockEntity currentBlockEntity = level.getBlockEntity(position);

            // A cancelled break normally preserves the original block entity.
            // A completed break removes or replaces it.
            if (change.removal != null && currentBlockEntity != change.removal.originalBlockEntity()) 
            {
                MachineOwnershipRegistry.unregister(level, position, change.removal.installationId());
            }

            if (change.placement == null) continue;
            if (!(currentState.getBlock() instanceof KineticBlock)) continue;
            if (!(currentBlockEntity instanceof KineticBlockEntity)) continue;

            ResourceLocation currentBlockId = Objects.requireNonNull(BuiltInRegistries.BLOCK.getKey(Objects.requireNonNull(currentState.getBlock())));

            if (!currentBlockId.equals(change.placement.blockId())) continue;

            MachineOwnershipRegistry.tryRegisterPlacement(level, position, change.placement.builderId());
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) 
    {
        PENDING.remove(event.getServer());
    }

    private static PendingChange pendingChange(MinecraftServer server, MachineLocation location) 
    {
        Map<MachineLocation, PendingChange> changes = PENDING.computeIfAbsent(server, ignored -> new HashMap<>());
        return changes.computeIfAbsent(location, ignored -> new PendingChange());
    }
}
