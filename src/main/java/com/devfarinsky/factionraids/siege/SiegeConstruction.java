package com.devfarinsky.factionraids.siege;

import com.devfarinsky.factionraids.RaidConfig;
import com.devfarinsky.factionraids.RaidSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Handles siege engine placement across the raid lifecycle.
 *
 * <p>Wave 1 uses the <b>prefab</b> path: engines listed in
 * {@link RaidConfig#FIRST_WAVE_ENGINES} spawn fully assembled next to the
 * war camp, oriented toward the objective. On subsequent waves the
 * <b>combo</b> path fires: a per-wave roll (see
 * {@link RaidConfig#LATER_WAVE_ENGINE_CHANCE}) can start an on-site build
 * that ticks toward completion, so defenders see the engine assembled in
 * real time rather than teleported in.</p>
 *
 * <p>All entity operations are gated by {@link SiegeIntegration}: when the
 * Siege Weapons mod is not installed, engine construction becomes a no-op
 * (only sapper charges still spawn via the sapper role in RaidEvents).</p>
 */
public final class SiegeConstruction {

    /** How long an on-site build takes before the engine appears, in ticks. */
    private static final int BUILD_TICKS = 20 * 45; // 45 s
    /** Direction offset from campPos toward the objective for engine facing. */
    private static final double DEPLOY_OFFSET = 3.0D;

    private SiegeConstruction() {}

    /**
     * Called once when wave 1 kicks off. Spawns the configured prefab
     * engines next to {@code campPos}. Missing entity types are skipped
     * with a debug-log line, not a fatal error.
     * @return count of engines actually placed.
     */
    public static int spawnPrefabEngines(ServerLevel level, RaidSavedData.RaidState state,
                                         BlockPos objective, String teamKey) {
        if (!RaidConfig.ENABLE_SIEGE_ENGINES.get()) return 0;
        if (state.campPos == null) return 0;
        if (!SiegeIntegration.isSiegeWeaponsPresent()) return 0;
        int placed = 0;
        List<? extends String> configured = RaidConfig.FIRST_WAVE_ENGINES.get();
        for (String raw : configured) {
            SiegeEngineType type = SiegeEngineType.parse(raw);
            if (type == null || !type.requiresSiegeWeapons()) continue;
            if (deployEngine(level, state, objective, teamKey, type)) placed++;
        }
        return placed;
    }

    /**
     * Called at the top of each wave after wave 1. Rolls
     * {@link RaidConfig#LATER_WAVE_ENGINE_CHANCE} to decide whether to
     * begin an on-site build. Currently a placeholder that spawns the
     * engine directly rather than animating construction — the interpolated
     * build-timer is intentionally deferred to a follow-up PR to keep
     * this one focused on getting engines onto the battlefield.
     */
    public static boolean maybeStartLaterWaveBuild(ServerLevel level, RaidSavedData.RaidState state,
                                                    BlockPos objective, String teamKey) {
        if (!RaidConfig.ENABLE_SIEGE_ENGINES.get()) return false;
        if (state.campPos == null) return false;
        if (!SiegeIntegration.isSiegeWeaponsPresent()) return false;
        int chance = RaidConfig.LATER_WAVE_ENGINE_CHANCE.get();
        if (chance <= 0) return false;
        if (level.random.nextInt(100) >= chance) return false;
        // Pick a random engine type that requires Siege Weapons.
        SiegeEngineType[] options = new SiegeEngineType[]{
                SiegeEngineType.BATTERING_RAM, SiegeEngineType.CATAPULT,
                SiegeEngineType.BALLISTA, SiegeEngineType.SIEGE_TOWER};
        SiegeEngineType pick = options[level.random.nextInt(options.length)];
        return deployEngine(level, state, objective, teamKey, pick);
    }

    /**
     * Physically spawn an engine at a computed slot near the war camp,
     * facing the objective, and register it in {@code state.siegeEngines}.
     * When Recruits + Siege Weapons are both present, an accompanying
     * SiegeEngineer is spawned and mounted on ranged engines.
     */
    private static boolean deployEngine(ServerLevel level, RaidSavedData.RaidState state,
                                        BlockPos objective, String teamKey, SiegeEngineType type) {
        Vec3 target = new Vec3(objective.getX() + 0.5, objective.getY(), objective.getZ() + 0.5);
        Vec3 camp = new Vec3(state.campPos.getX() + 0.5, state.campPos.getY(), state.campPos.getZ() + 0.5);
        Vec3 dir = target.subtract(camp).normalize();
        if (Double.isNaN(dir.x) || Double.isNaN(dir.z)) dir = new Vec3(1.0, 0.0, 0.0);
        Vec3 deploy = camp.add(dir.scale(DEPLOY_OFFSET));
        float yaw = (float) (Math.toDegrees(Math.atan2(-dir.x, dir.z)));
        Optional<Entity> vehicle = SiegeIntegration.spawnSiegeVehicle(level, type, deploy, yaw);
        if (vehicle.isEmpty()) return false;
        state.siegeEngines.put(vehicle.get().getUUID(), type.name());
        // Assign a siege engineer for ranged engines when Recruits is present.
        if (type.ranged() && SiegeIntegration.isSiegeEngineerAvailable()) {
            SiegeIntegration.spawnSiegeEngineer(level, deploy).ifPresent(engineer -> {
                engineer.getPersistentData().putString(SiegeDeployment.TEAM_TAG, teamKey);
                SiegeIntegration.assignSiegeEngineer(engineer, vehicle.get());
            });
        }
        return true;
    }

    /**
     * Utility: given a raider count, decide if we should promote one of
     * them into a sapper this squad. Currently a fixed cap at
     * {@link RaidConfig#SAPPER_MAX_PER_RAID}.
     */
    public static boolean canPromoteSapper(RaidSavedData.RaidState state) {
        if (!RaidConfig.ENABLE_SAPPER.get()) return false;
        return state.sappersDispatched < RaidConfig.SAPPER_MAX_PER_RAID.get();
    }

    /** Mark a raider as this raid's sapper and bump the counter. */
    public static void assignSapper(RaidSavedData.RaidState state, Mob raider) {
        SapperRunner.arm(raider);
        state.sappersDispatched++;
    }

    /** Ticks in one full on-site build. Exposed for potential future UI. */
    public static int buildTicks() { return BUILD_TICKS; }
}
