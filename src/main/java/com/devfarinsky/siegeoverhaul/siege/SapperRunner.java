package com.devfarinsky.siegeoverhaul.siege;

import com.devfarinsky.siegeoverhaul.RaidConfig;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import java.util.Comparator;
import java.util.LinkedHashSet;
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
                // v3.1.0: pass the RaidState so detonate() can register every
                // block it removes in raid.breachedBlocks. Without this, sapper
                // holes never restored after the raid — a real bug.
                detonate(level, state, mob.blockPosition());
                mob.getPersistentData().remove(CHARGE_TAG);
                // The caller marks RaidSavedData dirty after this siege pass;
                // both the breach ledger and the mob's own tag are persisted.
                detonations++;
            }
        }
        return detonations;
    }

    /**
     * Trigger a sapper charge. v3.1.0 behavior:
     * <ul>
     *   <li>Cosmetic blast + a scan-and-remove sweep through the detonation
     *       volume, gated by the {@link BlockRestoration#isBreachable} whitelist.</li>
     *   <li>Every removed block is snapshotted into {@code state.breachedBlocks}
     *       through the same ledger the physical-breach path uses, so the
     *       end-of-raid restore call brings them back exactly like a
     *       breacher-broken door — including tile-entity NBT.</li>
     *   <li>The vanilla-TNT sapper mode is still respected, but the primed
     *       TNT entity now runs {@link ServerLevel.ExplosionInteraction#NONE}
     *       through the same scan pass instead of a real destructive blast:
     *       previously it flattened dirt, stone, chests, and player builds in
     *       radius, which contradicted the mod's non-griefing promise.</li>
     * </ul>
     */
    private static void detonate(ServerLevel level, RaidSavedData.RaidState state, BlockPos center) {
        // Cosmetic explosion (no block damage) so defenders see and hear the
        // charge going off regardless of mode. NONE interaction is critical:
        // BLOCK_DESTROY would grief random terrain.
        level.explode(null, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5,
                0.0F, false, ServerLevel.ExplosionInteraction.NONE);
        int r = DETONATION_RADIUS;
        if (RaidConfig.SAPPER_MODE_VANILLA_TNT.get()) {
            // v3.1.0: TNT mode gets a slightly larger scan volume + faster feel
            // to preserve the old "boom" character without the griefing behavior.
            // The vertical band and radius are still whitelist-gated so nothing
            // outside the breachable set is touched.
            r = DETONATION_RADIUS + 1;
        }
        breachBlocks(level, state, center, r);
        // Emit smoke + explosion audio for feedback.
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5,
                30, 0.6, 0.5, 0.6, 0.02);
        level.playSound(null, center, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,
                net.minecraft.sounds.SoundSource.HOSTILE, 3.0F, 0.9F);
    }

    /** Record the whole blast volume before triggering any block updates. */
    static void breachBlocks(ServerLevel level, RaidSavedData.RaidState state, BlockPos center, int r) {
        Set<BlockPos> affected = new LinkedHashSet<>();
        for (int dy = -1; dy <= VERTICAL_SWEEP; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz > r * r) continue;
                    BlockPos p = center.offset(dx, dy, dz);
                    affected.addAll(BlockRestoration.snapshotBreach(level, state.breachedBlocks,
                            p, RaidConfig.MAX_RESTORABLE_BLOCKS.get()));
                }
            }
        }
        affected.stream().sorted(Comparator.comparingInt(BlockPos::getY).reversed()).forEach(pos -> {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            state.blockBreachProgress.remove(pos.asLong());
        });
    }

    // v3.1.0: the local isBreachable helper was removed. Sapper detonations
    // now consult the shared BlockRestoration.isBreachable whitelist so the
    // breacher path and the sapper path agree on what counts as a defense
    // and what counts as untouchable player terrain.

    /** Copy the charge tag when replicating raider NBT (unused today, reserved for future). */
    @SuppressWarnings("unused")
    public static void copyChargeTag(CompoundTag from, CompoundTag to) {
        if (from.getBoolean(CHARGE_TAG)) to.putBoolean(CHARGE_TAG, true);
    }

}
