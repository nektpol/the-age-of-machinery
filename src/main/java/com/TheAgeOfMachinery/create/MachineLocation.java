package com.TheAgeOfMachinery.create;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;

public record MachineLocation(ResourceKey<Level> dimension, BlockPos position) {

    public MachineLocation {
        Objects.requireNonNull(dimension, "dimension");
        position = Objects.requireNonNull(position, "position").immutable();
    }
}