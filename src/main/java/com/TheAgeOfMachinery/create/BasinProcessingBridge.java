package com.TheAgeOfMachinery.create;

import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinOperatingBlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public final class BasinProcessingBridge
{
    private static final ThreadLocal<ProcessingScope> ACTIVE = new ThreadLocal<>();

    private record ProcessingScope(BasinBlockEntity basin, Map<ResourceLocation, Integer> outputs)
    {
    }

    private BasinProcessingBridge()
    {
    }

    public static boolean apply(BasinOperatingBlockEntity machine, BasinBlockEntity basin, BooleanSupplier original)
    {
        if (!(machine.getLevel() instanceof ServerLevel level)) return original.getAsBoolean();
        if (!level.getServer().isSameThread()) return original.getAsBoolean();
        if (basin.getLevel() != level || !machine.getBlockPos().below(2).equals(basin.getBlockPos())) return original.getAsBoolean();

        boolean supported = machine instanceof MechanicalPressBlockEntity || machine instanceof MechanicalMixerBlockEntity;
        ProcessingScope previous = ACTIVE.get();
        ProcessingScope scope = new ProcessingScope(basin, new LinkedHashMap<>());
        boolean successful;
        ACTIVE.set(scope);

        try
        {
            successful = original.getAsBoolean();
        }
        finally
        {
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
        }

        // Only publish after the entire recipe application returned successfully.
        if (successful && supported) MachineProcessingBridge.publish(machine, scope.outputs(), UUID.randomUUID(), Set.of());

        return successful;
    }

    public static boolean acceptOutputs(BasinBlockEntity basin, List<ItemStack> outputs, boolean simulate, BooleanSupplier original)
    {
        ProcessingScope scope = ACTIVE.get();

        if (simulate || scope == null || scope.basin() != basin) return original.getAsBoolean();

        // Snapshot before acceptance: addons may mutate the supplied stacks.
        Map<ResourceLocation, Integer> produced = MachineProcessingBridge.countOutputs(outputs);
        boolean accepted = original.getAsBoolean();

        if (accepted)
        {
            produced.forEach((item, count) -> scope.outputs().merge(item, count, (first, second) -> Math.addExact(Objects.requireNonNull(first), Objects.requireNonNull(second))));
        }

        return accepted;
    }
}
