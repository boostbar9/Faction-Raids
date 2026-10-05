package com.devfarinsky.siegeoverhaul.naval.prototype.mixin;

import com.devfarinsky.siegeoverhaul.naval.prototype.FinalShipsTurnCommands;
import com.talhanation.recruits.entities.CaptainEntity;
import com.talhanation.recruits.pathfinding.AsyncPath;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** QA-only lifecycle adapter: hand back an already-produced native node, never synthesize a route. */
@Pseudo
@Mixin(targets = "com.talhanation.recruits.entities.ai.controller.SmallShipsController", remap = false)
public abstract class FinalCaptainPathCompletionMixin {
    @Shadow @Final private CaptainEntity captain;
    @Shadow private Path path;
    @Shadow private Node currentNode;

    @Inject(method = "tick", at = @At("HEAD"), require = 1, remap = false)
    private void siegeQa$acceptFinishedNativePath(CallbackInfo callback) {
        if (captain.level().isClientSide || currentNode != null || captain.getSailPos() == null
                || !(captain.getVehicle() instanceof Boat ship)
                || !(ship instanceof FinalShipsTurnCommands.ShipHook)
                || FinalShipsTurnCommands.helmsman(ship) != captain
                || !(path instanceof AsyncPath async) || !async.isProcessed() || path.getNodeCount() == 0) return;
        // The original controller uses the native end node without requiring canReach,
        // including a nearest reachable endpoint. Preserve that policy, but never
        // accept completion for a target the captain has since replaced.
        Node end = path.getEndNode();
        if (end != null && captain.getSailPos().equals(path.getTarget())) currentNode = end;
    }
}
