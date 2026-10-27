package com.TheAgeOfMachinery.create.mixin;

import com.TheAgeOfMachinery.create.MechanicalPressProcessingBridge;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = BasinBlockEntity.class, remap = false)
public abstract class BasinBlockEntityMixin
{
    @Inject(method = "acceptOutputs", at = @At("RETURN"), remap = false)
    private void tam$onOutputsAccepted(List<ItemStack> outputItems, List<FluidStack> outputFluids, boolean simulate, CallbackInfoReturnable<Boolean> callback)
    {
        if (simulate || !callback.getReturnValueZ()) return;

        BasinBlockEntity basin = (BasinBlockEntity) (Object) this;

        MechanicalPressProcessingBridge.onBasinOutputsAccepted(basin, outputItems);
    }
}
