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
    public static boolean needsWaveSupport(RaidSavedData.RaidState state) {
        return RaidConfig.ENABLE_SIEGE_ENGINES.get() && state.campPos != null
                && state.wave > 0 && state.lastSiegeSupportWave < state.wave;
    }
    private static boolean capacity(ServerLevel level, RaidSavedData.RaidState state) {
        if (state.raiders.size() + state.campGuards.size() >= RaidConfig.MAX_ACTIVE_RAIDERS.get()) return false;
        RaidSavedData data = RaidSavedData.get(level.getServer());
        return data.raids.values().stream().mapToInt(r -> r.raiders.size() + r.campGuards.size()).sum()
                < RaidConfig.MAX_GLOBAL_RAIDERS.get();
    }
    private static void ensureEngine(ServerLevel level, RaidSavedData.RaidState state, BlockPos objective) {
        if (!needsWaveSupport(state) || state.preparationTicks > 0 || !capacity(level, state)) return;
        for (UUID id : state.siegeEngines.keySet()) {
            Entity engine = level.getEntity(id);
            if (engine == null) continue;
            if (!engine.isAlive() || engine.isRemoved()) continue;
            int wave = engine.getPersistentData().getInt("SiegeSupportWave");
            if (wave == state.wave) return;
            // Adopt camp prefabs and current pre-upgrade equipment without duplicating a crew.
            if (wave == 0 && !(engine.getPersistentData().getBoolean(OPERATOR_ASSIGNED) && engine.getPassengers().isEmpty())) { engine.getPersistentData().putInt("SiegeSupportWave", state.wave); return; }
        }
        if (level.getGameTime() % 100 != 0) return;
        if (!SiegeConstruction.deployWaveEngine(level, state, objective)) {
            if (level.getGameTime() % 600 == 0)
                com.devfarinsky.siegeoverhaul.FactionLogger.LOG.warn("Wave {} siege support waiting for a clear equipment slot at camp {}", state.wave, state.campPos);
        } else RaidSavedData.get(level.getServer()).setDirty();
    }

    /** Provision each loaded ranged engine at most once after preparation; keep unloaded identities for cleanup. */
    public static int tick(ServerLevel level, RaidSavedData.RaidState state, BlockPos objective) {
        // Infantry must be able to deploy before support can form an active wave on its own.
        if(state.campPos!=null && (!com.devfarinsky.siegeoverhaul.camp.WarGate.ready(level,state) || state.waveStartingCount<=0))return 0;
        ensureEngine(level, state, objective);
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
            if (!vehicle.getPassengers().isEmpty()) {
                for (Entity passenger : vehicle.getPassengers()) if (passenger instanceof net.minecraft.world.entity.Mob operator
                        && state.teamKey.equals(operator.getPersistentData().getString(TEAM_TAG))) {
                    vehicle.getPersistentData().putUUID("SiegeOperatorUuid",operator.getUUID());
                    com.devfarinsky.siegeoverhaul.camp.CampLoading.keep(level, vehicle.blockPosition());
                    SiegeIntegration.advanceEngineer(operator, objective);
                    if (vehicle.getPersistentData().getInt("SiegeSupportWave") == state.wave && state.lastSiegeSupportWave < state.wave) {
                        state.lastSiegeSupportWave = state.wave;
                        RaidSavedData.get(level.getServer()).setDirty();
                    }
                }
                continue;
            }
            if (vehicle.getPersistentData().getBoolean(OPERATOR_ASSIGNED)) {
                // A dismounted living crew walks back to its own engine. Never replace dead crews.
                if(vehicle.getPersistentData().hasUUID("SiegeOperatorUuid")
                        && level.getEntity(vehicle.getPersistentData().getUUID("SiegeOperatorUuid")) instanceof net.minecraft.world.entity.Mob operator
                        && operator.isAlive() && !operator.isPassenger() && state.teamKey.equals(operator.getPersistentData().getString(TEAM_TAG))) {
                    EngineerAdvanceOrders.restore(operator);
                    double distance=operator.distanceToSqr(vehicle);
                    if(distance<=16) {
                        if(SiegeIntegration.assignSiegeEngineer(operator,vehicle))SiegeIntegration.advanceEngineer(operator,objective);
                    } else if(distance<=32*32 && operator.getTarget()==null) {
                        operator.getNavigation().moveTo(vehicle,1.1);
                    }
                }
                continue;
            }
            // Provision once, after the warning period. A killed operator is never replaced.
            if (!capacity(level, state)) continue;
            long now = level.getGameTime();
            if (vehicle.getPersistentData().contains("SiegeOperatorLastAttempt")
                    && now - vehicle.getPersistentData().getLong("SiegeOperatorLastAttempt") < 100) continue;
            vehicle.getPersistentData().putLong("SiegeOperatorLastAttempt", now);
            int attempts = vehicle.getPersistentData().getInt(OPERATOR_ATTEMPTS);
            // Failed initialization may recover after terrain/entity changes. Retry without duplicating successful crews.
            vehicle.getPersistentData().putInt(OPERATOR_ATTEMPTS, attempts + 1);
            SiegeIntegration.spawnSiegeEngineer(level, vehicle.position(), state.teamKey, vehicle, type).ifPresent(operator -> {
                state.raiders.add(operator.getUUID());
                state.totalSpawned++;
                vehicle.getPersistentData().putBoolean(OPERATOR_ASSIGNED, true);
                vehicle.getPersistentData().putUUID("SiegeOperatorUuid",operator.getUUID());
                state.lastSiegeSupportWave = Math.max(state.lastSiegeSupportWave, vehicle.getPersistentData().getInt("SiegeSupportWave"));
                SiegeIntegration.advanceEngineer(operator, objective);
                com.devfarinsky.siegeoverhaul.FactionLogger.LOG.info("Wave {} deployed {} with supplied Siege Engineer for {}", state.wave, type, state.teamKey);
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
