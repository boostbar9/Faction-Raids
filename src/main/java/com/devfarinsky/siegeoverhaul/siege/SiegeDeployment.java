package com.devfarinsky.siegeoverhaul.siege;

import com.devfarinsky.siegeoverhaul.RaidConfig;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Deploys supplied native operators after warmup and retains engine identities through chunk unloads. */
public final class SiegeDeployment {

    /** Persistent-data key that ties an operator to a specific raid. */
    public static final String TEAM_TAG = "FactionRaidsSiegeTeam";

    /** Provisioning survives saves so defeated crews cannot respawn. */
    public static final String OPERATOR_ASSIGNED = "SiegeOperatorAssigned";
    public static final String OPERATOR_ATTEMPTS = "SiegeOperatorAttempts";

    private SiegeDeployment() {}

    /** Provision each loaded ranged engine at most once after preparation; keep unloaded identities for cleanup. */
    public static int tick(ServerLevel level, RaidSavedData.RaidState state, BlockPos objective) {
        if (state.siegeEngines == null || state.siegeEngines.isEmpty()) return 0;
        int removed = 0;
        Iterator<Map.Entry<UUID, String>> it = state.siegeEngines.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, String> entry = it.next();
            Entity vehicle = level.getEntity(entry.getKey());
            // Missing entities may simply be in unloaded chunks; keep their cleanup identity.
            if (vehicle == null) continue;
            if (!vehicle.isAlive() || vehicle.isRemoved()) {
                it.remove();
                removed++;
                continue;
            }
            SiegeEngineType type = SiegeEngineType.parse(entry.getValue());
            if (type == null || !type.ranged() || state.wave <= 0 || state.preparationTicks > 0) continue;
            if (!vehicle.getPassengers().isEmpty() || vehicle.getPersistentData().getBoolean(OPERATOR_ASSIGNED)) continue;
            // Provision once, after the warning period. A killed operator is never replaced.
            if (state.raiders.size() >= RaidConfig.MAX_ACTIVE_RAIDERS.get()) continue;
            long now = level.getGameTime();
            if (vehicle.getPersistentData().contains("SiegeOperatorLastAttempt")
                    && now - vehicle.getPersistentData().getLong("SiegeOperatorLastAttempt") < 100) continue;
            vehicle.getPersistentData().putLong("SiegeOperatorLastAttempt", now);
            int attempts = vehicle.getPersistentData().getInt(OPERATOR_ATTEMPTS);
            if (attempts >= 3) continue;
            vehicle.getPersistentData().putInt(OPERATOR_ATTEMPTS, attempts + 1);
            SiegeIntegration.spawnSiegeEngineer(level, vehicle.position(), state.teamKey, vehicle, type).ifPresent(operator -> {
                state.raiders.add(operator.getUUID());
                state.totalSpawned++;
                vehicle.getPersistentData().putBoolean(OPERATOR_ASSIGNED, true);
                RaidSavedData.get(level.getServer()).setDirty();
            });
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
