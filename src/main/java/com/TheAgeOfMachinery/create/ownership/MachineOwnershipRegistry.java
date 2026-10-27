package com.TheAgeOfMachinery.create.ownership;

import com.TheAgeOfMachinery.create.MachineLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MachineOwnershipRegistry 
{

    private MachineOwnershipRegistry() {
    }

    public static MachineOwnership registerPlacement(ServerPlayer builder, BlockPos position) 
    {
        Objects.requireNonNull(builder, "builder");
        Objects.requireNonNull(position, "position");

        ServerLevel level = builder.serverLevel();

        if (!level.getChunkSource().hasChunk(position.getX() >> 4, position.getZ() >> 4)) throw new IllegalArgumentException("Cannot register a machine in an unloaded chunk");

        BlockState state = level.getBlockState(position);

        if (!state.hasBlockEntity()) throw new IllegalArgumentException("The selected block does not have a block entity");

        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(Objects.requireNonNull(state.getBlock()));
        MachineLocation location = new MachineLocation(level.dimension(), position);

        return MachineOwnershipSavedData.get(level.getServer()).registerInstallation(location, blockId, builder.getUUID());
    }

    public static Optional<MachineOwnership> find(ServerLevel level, BlockPos position) 
    {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");

        MachineLocation location = new MachineLocation(level.dimension(), position);
        Optional<MachineOwnership> ownership = MachineOwnershipSavedData.get(level.getServer()).find(location);

        if (ownership.isEmpty() || !level.getChunkSource().hasChunk(position.getX() >> 4, position.getZ() >> 4)) return Optional.empty();

        BlockState state = level.getBlockState(position);

        if (!state.hasBlockEntity()) return Optional.empty();

        ResourceLocation currentBlockId = BuiltInRegistries.BLOCK.getKey(Objects.requireNonNull(state.getBlock()));

        return ownership.filter(entry -> entry.blockId().equals(currentBlockId));
    }

    public static boolean unregister(ServerLevel level, BlockPos position, UUID expectedInstallationId) 
    {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(expectedInstallationId, "expectedInstallationId");

        MachineLocation location = new MachineLocation(level.dimension(), position);

        return MachineOwnershipSavedData.get(level.getServer()).unregisterInstallation(location, expectedInstallationId);
    }
}

