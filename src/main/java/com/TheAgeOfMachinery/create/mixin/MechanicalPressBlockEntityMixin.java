package com.TheAgeOfMachinery.create.mixin;

import com.TheAgeOfMachinery.create.MechanicalPressProcessingBridge;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = MechanicalPressBlockEntity.class, remap = false)
public abstract class MechanicalPressBlockEntityMixin
{
    @Inject(method = "onItemPressed", at = @At("TAIL"), remap = false)
    private void tam$onItemPressed(ItemStack result, CallbackInfo callback)
    {
        MechanicalPressBlockEntity press = (MechanicalPressBlockEntity) (Object) this;

        MechanicalPressProcessingBridge.onItemPressed(press, result);
    }

    @Inject(method = "tryProcessOnBelt", at = @At("RETURN"), remap = false)
    private void tam$onBeltProcessed(TransportedItemStack input, List<ItemStack> outputList, boolean simulate, CallbackInfoReturnable<Boolean> callback)
    {
        if (simulate || !callback.getReturnValueZ()) return;

        MechanicalPressBlockEntity press = (MechanicalPressBlockEntity) (Object) this;

        MechanicalPressProcessingBridge.onBeltProcessed(press, outputList);
    }

}
