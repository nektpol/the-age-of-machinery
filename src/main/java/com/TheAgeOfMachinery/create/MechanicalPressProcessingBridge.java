package com.TheAgeOfMachinery.create;

import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingBehaviour;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class MechanicalPressProcessingBridge
{
    private static final ThreadLocal<MechanicalPressBlockEntity> ACTIVE_BASIN_PRESS = new ThreadLocal<>();

    private MechanicalPressProcessingBridge()
    {
    }

    public static void onItemPressed(MechanicalPressBlockEntity press, ItemStack result)
    {
        if (result.isEmpty()) return;

        if (press.pressingBehaviour != null && press.pressingBehaviour.mode == PressingBehaviour.Mode.BELT) return;

        publishOutputs(press, List.of(result));
    }

    public static void onBeltProcessed(MechanicalPressBlockEntity press, List<ItemStack> outputs)
    {
        if (outputs == null || outputs.isEmpty()) return;

        publishOutputs(press, outputs);
    }

    public static void beginBasinProcessing(MechanicalPressBlockEntity press)
    {
        ACTIVE_BASIN_PRESS.set(press);
    }

    public static void endBasinProcessing()
    {
        ACTIVE_BASIN_PRESS.remove();
    }

    public static void onBasinOutputsAccepted(BasinBlockEntity basin, List<ItemStack> outputs)
    {
        MechanicalPressBlockEntity press = ACTIVE_BASIN_PRESS.get();

        if (press == null) return;
        if (outputs.isEmpty()) return;
        if (!press.getBlockPos().below(2).equals(basin.getBlockPos())) return;
        if (basin.getLevel() != press.getLevel()) return;

        publishOutputs(press, outputs);
    }

    private static void publishOutputs(MechanicalPressBlockEntity press, List<ItemStack> outputs)
    {
        if (!(press.getLevel() instanceof ServerLevel level)) return;
        if (!level.getServer().isSameThread()) return;

        BlockPos position = Objects.requireNonNull(press.getBlockPos());

        if (level.getBlockEntity(position) != press) return;

        ResourceLocation machineBlockId = Objects.requireNonNull(BuiltInRegistries.BLOCK.getKey(Objects.requireNonNull(level.getBlockState(position).getBlock())));
        Map<ResourceLocation, Integer> producedItems = new LinkedHashMap<>();

        for (ItemStack output : outputs)
        {
            if (output == null || output.isEmpty()) continue;

            ResourceLocation outputItemId = Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(output.getItem())));

            Integer previousCount = producedItems.get(outputItemId);
            int totalCount = previousCount == null ? output.getCount() : previousCount + output.getCount();

            producedItems.put(outputItemId, totalCount);
        }

        if (producedItems.isEmpty()) return;

        MachineLocation location = new MachineLocation(level.dimension(), position);

        for (Map.Entry<ResourceLocation, Integer> entry : producedItems.entrySet())
        {
            ProcessingCompletedEvent event = new ProcessingCompletedEvent(location, machineBlockId, UUID.randomUUID(), entry.getKey(), entry.getValue());

            CreateObjectiveRouter.route(level, event);
        }
    }
}