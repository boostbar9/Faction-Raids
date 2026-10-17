package com.devfarinsky.siegeoverhaul.naval.prototype.mixin;

import com.devfarinsky.siegeoverhaul.naval.prototype.FinalShipsTurnCommands;

import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.talhanation.smallships.world.entity.ship.Ship", remap = false)
public abstract class FinalShipInputsMixin implements FinalShipsTurnCommands.ShipHook {
    @Inject(method = "isLeft", at = @At("RETURN"), cancellable = true, require = 1, remap = false)
    private void siegeQa$captainLeft(CallbackInfoReturnable<Boolean> result) {
        if (!result.getReturnValueZ() && FinalShipsTurnCommands.requested((Boat) (Object) this, true)) result.setReturnValue(true);
    }
    @Inject(method = "isRight", at = @At("RETURN"), cancellable = true, require = 1, remap = false)
    private void siegeQa$captainRight(CallbackInfoReturnable<Boolean> result) {
        if (!result.getReturnValueZ() && FinalShipsTurnCommands.requested((Boat) (Object) this, false)) result.setReturnValue(true);
    }
}
