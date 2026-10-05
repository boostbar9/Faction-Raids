package com.devfarinsky.siegeoverhaul.naval.prototype.mixin;

import com.devfarinsky.siegeoverhaul.naval.prototype.FinalShipsTurnCommands;
import com.talhanation.recruits.entities.CaptainEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** QA-only named-runtime experiment. Production mapping/packaging remains a separate gate. */
@Pseudo
@Mixin(targets = "com.talhanation.recruits.entities.ai.navigation.SailorNodeEvaluator", remap = false)
public abstract class FinalCaptainWaterlineMixin {
    @Redirect(method = "getStart", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;getY()D", remap = false), require = 1, remap = false)
    private double siegeQa$startAtVesselWaterline(Mob mob) {
        if (!mob.level().isClientSide && mob instanceof CaptainEntity captain && mob.getVehicle() instanceof Boat ship
                && ship instanceof FinalShipsTurnCommands.ShipHook
                && ship.isInWater() && FinalShipsTurnCommands.helmsman(ship) == captain) return ship.getY();
        return mob.getY();
    }
}
