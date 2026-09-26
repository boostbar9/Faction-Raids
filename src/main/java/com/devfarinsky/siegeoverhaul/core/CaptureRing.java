package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Geometry of the Siege Core contest ring.
 *
 * <p>The ring is a cylinder, not a sphere: combatants must stand near the core
 * on roughly its own floor. A separate sight check rejects anyone who is inside
 * the cylinder but separated from the core by blocks - standing on the roof
 * directly above the core no longer counts as holding it.
 */
public final class CaptureRing {
    private CaptureRing() {}

    /** True when the offsets from the core block center fall inside the contest cylinder. */
    public static boolean inside(double dx, double dy, double dz, int radius, int vertical) {
        if (radius <= 0 || vertical < 0) return false;
        return dx * dx + dz * dz <= (double) radius * radius && Math.abs(dy) <= vertical;
    }

    /** True when nothing solid sits between the core block and the contestant. */
    public static boolean visible(BlockGetter level, BlockPos core, Vec3 position) {
        Vec3 from = Vec3.atCenterOf(core);
        // Aim at chest height so a one-block lip in front of the core does not
        // hide a defender who is genuinely standing at it.
        Vec3 to = new Vec3(position.x, position.y + 1.0D, position.z);
        // Cast toward the core: a ray starting inside its solid shape hits
        // the core immediately, hiding every contestant. Only the endpoint
        // core may occlude this ray; walls along the approach still block it.
        HitResult hit = level.clip(new ClipContext(to, from, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, null));
        return hit != null && (hit.getType() == HitResult.Type.MISS
                || hit instanceof BlockHitResult block && block.getBlockPos().equals(core));
    }
}
