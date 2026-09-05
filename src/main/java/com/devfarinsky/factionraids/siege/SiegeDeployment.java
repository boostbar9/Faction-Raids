package com.devfarinsky.factionraids.siege;

import com.devfarinsky.factionraids.RaidConfig;
import com.devfarinsky.factionraids.RaidSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Ticks alive siege engines forward — steering unmanned rams / towers
 * toward the objective, purging entries whose entity has been destroyed,
 * and cleaning up survivors at raid end.
 *
 * <p>Ranged engines (catapult, ballista) do not need help here because
 * their operating {@code SiegeEngineerEntity} pathfinds and fires on its
 * own once mounted (see
 * {@code com.talhanation.recruits.entities.ai.controller.siegeengineer.SiegeWeaponCatapultController}).
 * Non-ranged engines get a light nudge toward the objective every tick
 * so an unmanned ram or tower still drifts into position.</p>
 */
public final class SiegeDeployment {

    /** Persistent-data key that ties an operator to a specific raid. */
    public static final String TEAM_TAG = "FactionRaidsSiegeTeam";

    /** How hard to nudge non-ranged engines per tick, in blocks/sec. */
    private static final double DRIFT_SPEED = 0.05D;

    private SiegeDeployment() {}

    /**
     * Called every raid tick from {@code RaidEvents.processRaid}. Iterates
     * registered engines, drops any whose entity vanished, and applies a
     * gentle steering vector to non-ranged engines that lost their driver.
     * @return number of engines removed from the registry this tick
     * (destroyed by defenders, despawned, or removed by the level).
     */
    public static int tick(ServerLevel level, RaidSavedData.RaidState state, BlockPos objective) {
        if (state.siegeEngines == null || state.siegeEngines.isEmpty()) return 0;
        int removed = 0;
        Iterator<Map.Entry<UUID, String>> it = state.siegeEngines.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, String> entry = it.next();
            Entity vehicle = level.getEntity(entry.getKey());
            if (vehicle == null || !vehicle.isAlive() || vehicle.isRemoved()) {
                it.remove();
                removed++;
                continue;
            }
            SiegeEngineType type = SiegeEngineType.parse(entry.getValue());
            if (type == null || type.ranged()) continue;
            // If an operator is aboard (e.g. a Recruit siege engineer or a
            // player who took the wheel), don't shove the engine \u2014 we'd be
            // fighting whoever is steering. Only auto-drift when the vehicle
            // is genuinely unmanned.
            if (!vehicle.getPassengers().isEmpty()) continue;
            // Non-ranged: nudge toward objective if we've stalled.
            if (vehicle.getDeltaMovement().lengthSqr() < 0.005D) {
                Vec3 dir = new Vec3(objective.getX() - vehicle.getX(), 0,
                        objective.getZ() - vehicle.getZ()).normalize();
                if (!(Double.isNaN(dir.x) || Double.isNaN(dir.z))) {
                    vehicle.setDeltaMovement(vehicle.getDeltaMovement().add(dir.scale(DRIFT_SPEED)));
                    vehicle.hurtMarked = true;
                }
            }
        }
        return removed;
    }

    /**
     * Called from {@code RaidEvents.finishRaid}. When the config flag is
     * on, all surviving siege engines are discarded. Otherwise they are
     * left in place as loot / rubble for the defenders to reclaim.
     */
    public static void cleanup(ServerLevel level, RaidSavedData.RaidState state) {
        if (state.siegeEngines == null || state.siegeEngines.isEmpty()) return;
        if (!RaidConfig.CLEANUP_SURVIVING_ENGINES.get()) {
            state.siegeEngines.clear();
            return;
        }
        for (UUID id : state.siegeEngines.keySet()) {
            Entity vehicle = level.getEntity(id);
            if (vehicle != null && vehicle.isAlive()) vehicle.discard();
        }
        state.siegeEngines.clear();
    }
}
