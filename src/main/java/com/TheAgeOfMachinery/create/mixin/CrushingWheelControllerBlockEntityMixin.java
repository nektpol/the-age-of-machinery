package com.TheAgeOfMachinery.create.mixin;

import com.TheAgeOfMachinery.create.MachineProcessingBridge;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CrushingWheelControllerBlockEntity.class, remap = false)
public abstract class CrushingWheelControllerBlockEntityMixin
{
    @Inject(method = "applyRecipe", at = @At("RETURN"), remap = false)
    private void tam$onRecipeApplied(CallbackInfo callback)
    {
        MachineProcessingBridge.onCrushingCompleted((CrushingWheelControllerBlockEntity) (Object) this);
    }
}
