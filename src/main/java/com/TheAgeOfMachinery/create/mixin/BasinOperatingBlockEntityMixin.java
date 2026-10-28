package com.TheAgeOfMachinery.create.mixin;

import com.TheAgeOfMachinery.create.BasinProcessingBridge;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinOperatingBlockEntity;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = BasinOperatingBlockEntity.class, remap = false)
public abstract class BasinOperatingBlockEntityMixin
{
    @WrapOperation(method = "applyBasinRecipe", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/processing/basin/BasinRecipe;apply(Lcom/simibubi/create/content/processing/basin/BasinBlockEntity;Lnet/minecraft/world/item/crafting/Recipe;)Z"))
    private boolean tam$applyWithMachine(BasinBlockEntity basin, Recipe<?> recipe, Operation<Boolean> original)
    {
        BasinOperatingBlockEntity machine = (BasinOperatingBlockEntity) (Object) this;

        return BasinProcessingBridge.apply(machine, basin, () -> original.call(basin, recipe));
    }
}
