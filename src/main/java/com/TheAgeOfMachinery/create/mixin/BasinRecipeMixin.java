package com.TheAgeOfMachinery.create.mixin;

import com.TheAgeOfMachinery.create.BasinProcessingBridge;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(value = BasinRecipe.class, remap = false)
public abstract class BasinRecipeMixin
{
    @WrapOperation(method = "apply(Lcom/simibubi/create/content/processing/basin/BasinBlockEntity;Lnet/minecraft/world/item/crafting/Recipe;Z)Z", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/processing/basin/BasinBlockEntity;acceptOutputs(Ljava/util/List;Ljava/util/List;Z)Z"))
    private static boolean tam$captureRecipeOutputs(BasinBlockEntity basin, List<ItemStack> items, List<FluidStack> fluids, boolean simulate, Operation<Boolean> original)
    {
        return BasinProcessingBridge.acceptOutputs(basin, items, simulate, () -> original.call(basin, items, fluids, simulate));
    }
}
