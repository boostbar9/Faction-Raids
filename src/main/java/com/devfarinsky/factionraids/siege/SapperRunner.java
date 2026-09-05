package com.devfarinsky.factionraids.siege;

import com.devfarinsky.factionraids.RaidConfig;
import com.devfarinsky.factionraids.RaidSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Non-griefing sapper runner. Raiders tagged with the {@code SapperCharge}
 * persistent-data flag path to the objective on the standard raid AI, and
 * when they arrive within {@link #DETONATION_RADIUS} of the anchor they
 * trigger a scripted breach that removes doors, fences, trapdoors, and
 * iron bars in a 3-block radius. Server admins can switch to real vanilla
 * TNT via {@link RaidConfig#SAPPER_MODE_VANILLA_TNT}.
 *
 * <p>Only breachable "gate-like" blocks are removed by default; walls,
 * player buildings, and terrain are untouched. This matches the safe
 * default the user asked for.</p>
 */
public final class SapperRunner {

    public static final String CHARGE_TAG = "FactionRaidsSapperCharge";
    private static final int DETONATION_RADIUS = 3;
    /** Vertical band around the target y to sweep for gate-like blocks. */
    private static final int VERTICAL_SWEEP = 4;

    private SapperRunner() {}

    /** Tag a raider as carrying a demolition charge. */
    public static void arm(Mob raider) {
        raider.getPersistentData().putBoolean(CHARGE_TAG, true);
    }

    public static boolean isArmed(Mob raider) {
        return raider.getPersistentData().getBoolean(CHARGE_TAG);
    }

    /**
     * Called once per second per active raid. Scans active raiders for
     * armed sappers that reached the objective and triggers the configured
     * charge behavior. Returns the number of charges detonated this tick,
     * for optional announce chatter.
     */
    public static int tick(ServerLevel level, RaidSavedData.RaidState state, BlockPos objective,
                           Set<UUID> raiderIds) {
        if (raiderIds == null || raiderIds.isEmpty()) return 0;
        int detonations = 0;
        for (UUID id : raiderIds) {
            var entity = level.getEntity(id);
            if (!(entity instanceof Mob mob) || !mob.isAlive()) continue;
            if (!isArmed(mob)) continue;
            if (mob.blockPosition().closerThan(objective, 4.5D)) {
                detonate(level, mob.blockPosition());
                mob.getPersistentData().remove(CHARGE_TAG);
                state.totalSpawned = state.totalSpawned; // no-op, hint that state changed
                detonations++;
            }
        }
        return detonations;
    }

    private static void detonate(ServerLevel level, BlockPos center) {
        if (RaidConfig.SAPPER_MODE_VANILLA_TNT.get()) {
            // Real vanilla TNT: 4.0F is the classic block-of-TNT power.
            PrimedTnt tnt = new PrimedTnt(level, center.getX() + 0.5, center.getY() + 0.5,
                    center.getZ() + 0.5, null);
            tnt.setFuse(20); // 1 second so nearby defenders have a beat to react
            level.addFreshEntity(tnt);
            return;
        }
        // Non-griefing default: cosmetic blast + remove gate-like blocks
        // in a small sphere around the sapper.
        level.explode(null, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5,
                0.0F, false, ServerLevel.ExplosionInteraction.NONE);
        Set<BlockPos> removed = new HashSet<>();
        int r = DETONATION_RADIUS;
        for (int dy = -1; dy <= VERTICAL_SWEEP; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz > r * r) continue;
                    BlockPos p = center.offset(dx, dy, dz);
                    BlockState bs = level.getBlockState(p);
                    if (isBreachable(bs)) {
                        level.destroyBlock(p, false); // drop nothing; wall opened, not looted
                        removed.add(p);
                    }
                }
            }
        }
        // Emit smoke + campfire particles for feedback.
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5,
                30, 0.6, 0.5, 0.6, 0.02);
        level.playSound(null, center, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,
                net.minecraft.sounds.SoundSource.HOSTILE, 3.0F, 0.9F);
    }

    /** @return true when the block should be blown open by a non-griefing charge. */
    private static boolean isBreachable(BlockState bs) {
        if (bs.isAir()) return false;
        return bs.getBlock() instanceof DoorBlock
                || bs.getBlock() instanceof TrapDoorBlock
                || bs.getBlock() instanceof FenceBlock
                || bs.getBlock() instanceof FenceGateBlock
                || bs.getBlock() instanceof IronBarsBlock;
    }

    /** Copy the charge tag when replicating raider NBT (unused today, reserved for future). */
    @SuppressWarnings("unused")
    public static void copyChargeTag(CompoundTag from, CompoundTag to) {
        if (from.getBoolean(CHARGE_TAG)) to.putBoolean(CHARGE_TAG, true);
    }

    @SuppressWarnings("unused")
    private static Direction dummyKeepImportsFromWarn() {
        return Direction.NORTH;
    }
}
