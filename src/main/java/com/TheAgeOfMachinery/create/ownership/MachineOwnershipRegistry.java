
package com.TheAgeOfMachinery.create.ownership;

import com.TheAgeOfMachinery.create.MachineLocation;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
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
    private MachineOwnershipRegistry() 
    {
    }

    public static MachineOwnership registerPlacement(ServerPlayer builder, BlockPos position) 
    {
        Objects.requireNonNull(builder, "builder");
        Objects.requireNonNull(position, "position");

        return tryRegisterPlacement(builder.serverLevel(), position, builder.getUUID())
                .orElseThrow(() -> new IllegalArgumentException("Machine already registered at " + position));
    }

    static Optional<MachineOwnership> tryRegisterPlacement(ServerLevel level, BlockPos position, UUID builderId) 
    {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(builderId, "builderId");

        if (!level.getServer().isSameThread()) throw new IllegalStateException("Machine registration must run on the server thread");

        if (!level.getChunkSource().hasChunk(position.getX() >> 4, position.getZ() >> 4)) throw new IllegalArgumentException("Cannot register a machine in an unloaded chunk");

        BlockState state = level.getBlockState(position);

        if (!(state.getBlock() instanceof KineticBlock) || !(level.getBlockEntity(position) instanceof KineticBlockEntity)) throw new IllegalArgumentException("The selected block is not a supported Create kinetic machine");

        MachineLocation location = new MachineLocation(level.dimension(), position);
        MachineOwnershipSavedData savedData = MachineOwnershipSavedData.get(level.getServer());

        if (savedData.find(location).isPresent()) return Optional.empty();

        ResourceLocation blockId = Objects.requireNonNull(BuiltInRegistries.BLOCK.getKey(Objects.requireNonNull(state.getBlock())));
        return Optional.of(savedData.registerInstallation(location, blockId, builderId));
    }

    public static Optional<MachineOwnership> find(ServerLevel level, BlockPos position) 
    {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");

        MachineLocation location = new MachineLocation(level.dimension(), position);
        Optional<MachineOwnership> ownership = MachineOwnershipSavedData.get(level.getServer()).find(location);

        if (ownership.isEmpty()) return Optional.empty();
        if (!level.getChunkSource().hasChunk(position.getX() >> 4, position.getZ() >> 4)) return Optional.empty();

        BlockState state = level.getBlockState(position);

        if (!(state.getBlock() instanceof KineticBlock) || !(level.getBlockEntity(position) instanceof KineticBlockEntity)) return Optional.empty();

        ResourceLocation currentBlockId = Objects.requireNonNull(BuiltInRegistries.BLOCK.getKey(Objects.requireNonNull(state.getBlock())));
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
