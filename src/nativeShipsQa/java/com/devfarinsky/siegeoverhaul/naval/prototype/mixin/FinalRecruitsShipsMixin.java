package com.devfarinsky.siegeoverhaul.naval.prototype.mixin;

import com.devfarinsky.siegeoverhaul.naval.prototype.FinalShipsTurnCommands;

import com.talhanation.recruits.entities.CaptainEntity;
import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.talhanation.recruits.compat.smallships.SmallShips", remap = false)
public abstract class FinalRecruitsShipsMixin implements FinalShipsTurnCommands.RecruitsHook {
    @Shadow @Final private Boat boat;
    @Shadow @Final private CaptainEntity captain;

    @Inject(method = "isCaptainDriver", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void siegeQa$actualHelm(CallbackInfoReturnable<Boolean> result) {
        if (boat instanceof FinalShipsTurnCommands.ShipHook) result.setReturnValue(captain == FinalShipsTurnCommands.helmsman(boat));
    }
    @Inject(method = "rotateShip", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void siegeQa$nativeTurnOnly(boolean left, boolean right, CallbackInfo callback) {
        if (!(boat instanceof FinalShipsTurnCommands.ShipHook)) return;
        FinalShipsTurnCommands.issue(boat, captain, left, right);
        callback.cancel();
    }
    @Inject(method = "setSailState", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void siegeQa$protectHelmSails(int state, CallbackInfo callback) {
        if (boat instanceof FinalShipsTurnCommands.ShipHook
                && (boat.level().isClientSide || captain != FinalShipsTurnCommands.helmsman(boat))) callback.cancel();
    }
}
