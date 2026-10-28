package com.TheAgeOfMachinery.create.mixin;

import com.TheAgeOfMachinery.create.MachineProcessingBridge;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Map;

@Mixin(value = MillstoneBlockEntity.class, remap = false)
public abstract class MillstoneBlockEntityMixin
{
    @WrapMethod(method = "process")
    private void tam$observeProcessing(Operation<Void> original)
    {
        MillstoneBlockEntity millstone = (MillstoneBlockEntity) (Object) this;

        if (!(millstone.getLevel() instanceof ServerLevel))
        {
            original.call();
            return;
        }

        Map<ResourceLocation, Integer> before = MachineProcessingBridge.countInventory(millstone.outputInv, 0);
        original.call();
        MachineProcessingBridge.publishInventoryIncrease(millstone, millstone.outputInv, before);
    }
}
