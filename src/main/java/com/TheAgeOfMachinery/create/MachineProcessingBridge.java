package com.TheAgeOfMachinery.create;

import com.TheAgeOfMachinery.create.ownership.MachineOwnershipRegistry;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelBlockEntity;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlock;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

public final class MachineProcessingBridge
{
    private MachineProcessingBridge()
    {
    }

    public static Map<ResourceLocation, Integer> countOutputs(List<ItemStack> outputs)
    {
        Map<ResourceLocation, Integer> counts = new LinkedHashMap<>();

        for (ItemStack output : outputs)
        {
            if (output.isEmpty()) continue;

            counts.merge(BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(output.getItem())), output.getCount(), (first, second) -> Math.addExact(Objects.requireNonNull(first), Objects.requireNonNull(second)));
        }

        return counts;
    }

    public static Map<ResourceLocation, Integer> countInventory(IItemHandler inventory, int firstSlot)
    {
        Map<ResourceLocation, Integer> counts = new LinkedHashMap<>();

        for (int slot = firstSlot; slot < inventory.getSlots(); slot++)
        {
            ItemStack stack = inventory.getStackInSlot(slot);

            if (stack.isEmpty()) continue;

            counts.merge(BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(stack.getItem())), stack.getCount(), (first, second) -> Math.addExact(Objects.requireNonNull(first), Objects.requireNonNull(second)));
        }

        return counts;
    }

    public static void publishInventoryIncrease(KineticBlockEntity machine, IItemHandler inventory, Map<ResourceLocation, Integer> before)
    {
        Map<ResourceLocation, Integer> produced = countInventory(inventory, 0);
        produced.replaceAll((item, count) -> count - before.getOrDefault(item, 0));
        produced.values().removeIf(count -> count <= 0);
        publish(machine, produced, UUID.randomUUID(), Set.of());
    }

    public static void publish(KineticBlockEntity machine, Map<ResourceLocation, Integer> outputs, UUID operationId, Set<UUID> excludedOwners)
    {
        if (!(machine.getLevel() instanceof ServerLevel level)) return;
        if (!level.getServer().isSameThread()) return;
        if (!isInstalled(level, machine)) return;
        if (!isWorking(machine)) return;

        MachineLocation location = new MachineLocation(level.dimension(), machine.getBlockPos());
        ResourceLocation machineId = BuiltInRegistries.BLOCK.getKey(Objects.requireNonNull(machine.getBlockState().getBlock()));

        for (Map.Entry<ResourceLocation, Integer> output : outputs.entrySet())
        {
            if (output.getValue() <= 0) continue;

            CreateObjectiveRouter.route(level, new ProcessingCompletedEvent(location, machineId, operationId, output.getKey(), output.getValue()), excludedOwners);
        }
    }

    public static void onCrushingCompleted(CrushingWheelControllerBlockEntity controller)
    {
        if (!(controller.getLevel() instanceof ServerLevel level)) return;
        if (!level.getServer().isSameThread() || !isInstalled(level, controller)) return;
        if (!Float.isFinite(controller.crushingspeed) || controller.crushingspeed <= 0) return;
        if (!controller.getBlockState().getValue(Objects.requireNonNull(CrushingWheelControllerBlock.VALID))) return;

        // applyRecipe has already consumed the input. Slot zero is never a result slot.
        Map<ResourceLocation, Integer> outputs = countInventory(controller.inventory, 1);

        if (outputs.isEmpty()) return;

        Direction facing = controller.getBlockState().getValue(Objects.requireNonNull(CrushingWheelControllerBlock.FACING));
        Direction.Axis processingAxis = facing.getAxis();

        // Follow Create's updateSpeed neighbor order, rather than choosing a nearby pair arbitrarily.
        for (Direction candidate : Iterate.directions)
        {
            Direction side = Objects.requireNonNull(candidate);
            CrushingWheelBlockEntity first = findWheel(level, controller.getBlockPos().relative(side));

            if (first == null) continue;

            Direction.Axis wheelAxis = first.getBlockState().getValue(Objects.requireNonNull(BlockStateProperties.AXIS));

            if (wheelAxis == side.getAxis()) continue;
            if (side.getAxis() == processingAxis || wheelAxis == processingAxis) return;

            Direction opposite = Objects.requireNonNull(side.getOpposite());
            CrushingWheelBlockEntity second = findWheel(level, controller.getBlockPos().relative(opposite));

            if (second == null) return;
            if (wheelAxis != second.getBlockState().getValue(Objects.requireNonNull(BlockStateProperties.AXIS))) return;
            if (!isWorking(first) || !isWorking(second)) return;
            if ((first.getSpeed() > 0) == (second.getSpeed() > 0)) return;

            Vec3 rotationAxis = new Vec3(wheelAxis == Direction.Axis.X ? 1 : 0, wheelAxis == Direction.Axis.Y ? 1 : 0, wheelAxis == Direction.Axis.Z ? 1 : 0);
            Vec3 towardController = Objects.requireNonNull(Vec3.atLowerCornerOf(Objects.requireNonNull(opposite.getNormal())));
            Vec3 outputDirection = rotationAxis.cross(towardController).scale(Math.signum(first.getSpeed()));

            if (Direction.getNearest(outputDirection.x, outputDirection.y, outputDirection.z) != facing) return;

            UUID operationId = UUID.randomUUID();
            Set<UUID> creditedOwners = new HashSet<>();
            publish(first, outputs, operationId, Set.of());
            MachineOwnershipRegistry.find(level, first.getBlockPos()).ifPresent(ownership -> creditedOwners.addAll(Objects.requireNonNull(ownership).owners()));
            publish(second, outputs, operationId, creditedOwners);
            return;
        }
    }

    @Nullable
    private static CrushingWheelBlockEntity findWheel(ServerLevel level, BlockPos position)
    {
        if (!level.getChunkSource().hasChunk(position.getX() >> 4, position.getZ() >> 4)) return null;
        if (!AllBlocks.CRUSHING_WHEEL.has(Objects.requireNonNull(level.getBlockState(position)))) return null;

        return level.getBlockEntity(position) instanceof CrushingWheelBlockEntity wheel ? wheel : null;
    }

    private static boolean isInstalled(ServerLevel level, BlockEntity machine)
    {
        BlockPos position = machine.getBlockPos();

        return level.getChunkSource().hasChunk(position.getX() >> 4, position.getZ() >> 4) && !machine.isRemoved() && level.getBlockEntity(position) == machine;
    }

    private static boolean isWorking(KineticBlockEntity machine)
    {
        return Float.isFinite(machine.getSpeed()) && machine.getSpeed() != 0 && !machine.isOverStressed() && machine.isSpeedRequirementFulfilled();
    }
}
