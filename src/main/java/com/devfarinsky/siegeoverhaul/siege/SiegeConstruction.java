package com.devfarinsky.siegeoverhaul.siege;

import com.devfarinsky.siegeoverhaul.ModConstants;
import com.devfarinsky.siegeoverhaul.RaidConfig;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
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
 * war camp, oriented toward the objective. Each assault wave then receives a supplied native operator and, if needed,
 * a new ranged engine. Failed placement is retried without rerolling a chance.</p>
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
            if (state.siegeEngines.size() >= 4) break;
            SiegeEngineType type = SiegeEngineType.parse(raw);
            if (type == null || !type.requiresSiegeWeapons()) continue;
            if (deployEngine(level, state, objective, teamKey, type)) placed++;
        }
        return placed;
    }

    /** One dedicated ranged engine per wave; retries are controlled by SiegeDeployment. */
    public static boolean deployWaveEngine(ServerLevel level, RaidSavedData.RaidState state, BlockPos objective) {
        if (!RaidConfig.ENABLE_SIEGE_ENGINES.get() || state.campPos == null || !SiegeIntegration.isSiegeWeaponsPresent()) return false;
        // Bounded by configured wave count plus up to three extra camp prefabs, not a four-wave lifetime limit.
        if (state.siegeEngines.size() >= Math.max(4, RaidConfig.WAVES.get() + 3)) return false;
        return deployEngine(level, state, objective, state.teamKey,
                state.wave % 2 == 0 ? SiegeEngineType.CATAPULT : SiegeEngineType.BALLISTA);
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
        type = automaticType(type);
        float yaw = (float) (Math.toDegrees(Math.atan2(-dir.x, dir.z)));
        // Artillery needs a clear firing lane outside the palisade, with separate slots.
        Vec3 forward = new Vec3(dir.x, 0, dir.z).normalize();
        Vec3 side = new Vec3(-forward.z, 0, forward.x);
        for (int distance : new int[]{14, 18, 22}) {
            for (int offset : new int[]{0, 5, -5, 10, -10}) {
                Vec3 candidate = camp.add(forward.scale(distance)).add(side.scale(offset));
                if(!state.warGate.isEmpty()) {
                    BlockPos gate=com.devfarinsky.siegeoverhaul.camp.WarGate.center(state);
                    if(Math.abs(candidate.x-(gate.getX()+.5))<8 && Math.abs(candidate.z-(gate.getZ()+.5))<8)continue;
                }
                int x = (int) Math.floor(candidate.x), z = (int) Math.floor(candidate.z);
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (Math.abs(y - camp.y) > 3) continue;
                Optional<Entity> vehicle = SiegeIntegration.spawnSiegeVehicle(level, type, new Vec3(x + 0.5, y, z + 0.5), yaw);
                if (vehicle.isEmpty()) continue;
                vehicle.get().getPersistentData().putString(SiegeDeployment.TEAM_TAG, teamKey);
                vehicle.get().getPersistentData().putInt(SiegeFleet.SUPPORT_WAVE, state.wave);
                state.siegeEngines.put(vehicle.get().getUUID(), type.name());
                return true;
            }
        }
        com.devfarinsky.siegeoverhaul.FactionLogger.LOG.warn("No clear siege engine slot for {} at {}", teamKey, state.campPos);
        return false;
    }

    /** Native Recruits operators support ranged engines; legacy unmanned choices get a working ballista. */
    static SiegeEngineType automaticType(SiegeEngineType type) {
        return type.ranged() ? type : SiegeEngineType.BALLISTA;
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
