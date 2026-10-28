package com.TheAgeOfMachinery.create;

import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingBehaviour;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class MechanicalPressProcessingBridge
{
    private MechanicalPressProcessingBridge()
    {
    }

    public static void onItemPressed(MechanicalPressBlockEntity press, ItemStack result)
    {
        if (result.isEmpty()) return;
        if (press.pressingBehaviour != null && press.pressingBehaviour.mode == PressingBehaviour.Mode.BELT) return;

        MachineProcessingBridge.publish(press, MachineProcessingBridge.countOutputs(List.of(result)), UUID.randomUUID(), Set.of());
    }

    public static void onBeltProcessed(MechanicalPressBlockEntity press, List<ItemStack> outputs)
    {
        if (outputs == null || outputs.isEmpty()) return;

        MachineProcessingBridge.publish(press, MachineProcessingBridge.countOutputs(outputs), UUID.randomUUID(), Set.of());
    }
}
