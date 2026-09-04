package com.devfarinsky.factionraids.camp;

import com.devfarinsky.factionraids.FactionLogger;
import com.devfarinsky.factionraids.RaidConfig;
import com.devfarinsky.factionraids.compat.WorkersBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates the camp construction phase of a raid.
 *
 * <p>Lifecycle:
 * <ol>
 *     <li>{@link #startCamp(ServerLevel, BlockPos, double, int, UUID, String)}
 *         picks a site via {@link CampSite}, spawns a {@link WorkersBridge}
 *         LumberArea + BuildArea(s) + one Lumberjack per two placements
 *         + one Builder per placement, all owned by the raider faction.</li>
 *     <li>{@link CampState#tick(ServerLevel)} polls the work areas each
 *         tick and updates {@link CampState#phase} → {@link Phase#IN_PROGRESS}
 *         → {@link Phase#COMPLETE}.</li>
 *     <li>{@link CampState#cleanup(ServerLevel)} removes spawned workers and
 *         work-area entities when the raid ends. Placed blocks stay standing
 *         so defenders can loot/dismantle them.</li>
 * </ol>
 *
 * <p>When Workers is not installed, {@link #startCamp} returns an empty
 * {@link Optional} — callers should treat this as "camp phase skipped, jump
 * straight to the assault." No crash, no fallback prefab (that's future
 * work if we ever want a Workers-free build to still get a visible camp).
 */
public final class CampBuilder {

    private CampBuilder() {}

    /** Phase state exposed to the siege lifecycle. */
    public enum Phase {
        /** Site chosen and workers spawned but nothing built yet. */
        SPAWNED,
        /** Workers are actively building. */
        IN_PROGRESS,
        /** All work areas report done — advance to next siege phase. */
        COMPLETE,
        /** Something failed — cleanup and skip to assault. */
        FAILED
    }

    /**
     * Attempts to start a camp for the given raid. Returns empty when
     * Workers is missing, no camp site can be chosen, or the target is in
     * an unsupported dimension (the End).
     */
    public static Optional<CampState> startCamp(ServerLevel level, BlockPos targetAnchor,
                                                 double approachAngle, int factionSize,
                                                 UUID raiderOwner, String raiderTeamKey) {
        if (!RaidConfig.ENABLE_CAMP_CONSTRUCTION.get()) {
            FactionLogger.LOG.debug("Camp construction disabled by config; skipping.");
            return Optional.empty();
        }
        if (!WorkersBridge.available()) {
            FactionLogger.LOG.debug("Workers mod absent; skipping camp construction phase.");
            return Optional.empty();
        }
        if (!CampSite.canHostCamp(level)) {
            FactionLogger.LOG.debug("Dimension {} cannot host a raider camp.", level.dimension().location());
            return Optional.empty();
        }

        CampBlueprint blueprint = CampBlueprintRegistry.chooseFor(factionSize);
        Optional<BlockPos> center = CampSite.choose(level, targetAnchor, approachAngle, blueprint.size());
        if (center.isEmpty()) {
            FactionLogger.LOG.info("No suitable camp site found near {}; skipping camp phase.", targetAnchor);
            return Optional.empty();
        }

        BlockPos campCenter = center.get();
        List<Entity> spawned = new ArrayList<>();
        List<Entity> workAreas = new ArrayList<>();

        // 1. Lumber area for tree-cutting around the camp
        WorkersBridge.spawnLumberArea(level, campCenter,
                        blueprint.lumberRadius() * 2 + 1,
                        blueprint.lumberRadius() * 2 + 1,
                        blueprint.lumberHeight(),
                        raiderOwner, raiderTeamKey)
                .ifPresent(entity -> { spawned.add(entity); workAreas.add(entity); });

        // 2. One BuildArea per placement
        for (CampBlueprint.Placement placement : blueprint.placements()) {
            BlockPos anchor = placement.anchorAt(campCenter);
            WorkersBridge.spawnBuildArea(level, anchor, placement.facing(),
                            placement.structureNbt(), raiderOwner, raiderTeamKey)
                    .ifPresent(entity -> { spawned.add(entity); workAreas.add(entity); });
        }

        // 3. Workers to do the work. Sized to the placement count so bigger
        //    camps get more hands. Lumberjacks scale slower — trees regrow slow.
        int placementCount = Math.max(1, blueprint.placements().size());
        int builderCount = Math.min(RaidConfig.CAMP_BUILDER_MAX.get(), placementCount);
        int lumberjackCount = Math.min(RaidConfig.CAMP_LUMBERJACK_MAX.get(),
                Math.max(1, placementCount / 2));

        for (int i = 0; i < builderCount; i++) {
            WorkersBridge.spawnBuilder(level, campCenter, raiderOwner, raiderTeamKey)
                    .ifPresent(spawned::add);
        }
        for (int i = 0; i < lumberjackCount; i++) {
            WorkersBridge.spawnLumberjack(level, campCenter, raiderOwner, raiderTeamKey)
                    .ifPresent(spawned::add);
        }

        if (spawned.isEmpty()) {
            FactionLogger.LOG.info("Camp phase spawned no entities at {}; skipping.", campCenter);
            return Optional.empty();
        }

        FactionLogger.LOG.info("Raider camp started at {} for faction {} — {} entities, {} work areas.",
                campCenter, raiderTeamKey, spawned.size(), workAreas.size());
        return Optional.of(new CampState(campCenter, blueprint.id(), spawned, workAreas));
    }

    /** Live state for one raider camp. Owned by the raid, ticked from the siege lifecycle. */
    public static final class CampState {
        public final BlockPos center;
        public final String blueprintId;
        public final List<Entity> spawnedEntities;
        public final List<Entity> workAreas;
        public Phase phase = Phase.SPAWNED;
        private int ticksSinceStart;

        CampState(BlockPos center, String blueprintId, List<Entity> spawnedEntities, List<Entity> workAreas) {
            this.center = center;
            this.blueprintId = blueprintId;
            this.spawnedEntities = spawnedEntities;
            this.workAreas = workAreas;
        }

        /** Called from the siege tick — returns true when we transition to COMPLETE. */
        public boolean tick(ServerLevel level) {
            ticksSinceStart++;
            if (phase == Phase.SPAWNED && ticksSinceStart > 20) {
                phase = Phase.IN_PROGRESS;
            }
            if (phase == Phase.IN_PROGRESS) {
                boolean allDone = !workAreas.isEmpty() &&
                        workAreas.stream().allMatch(WorkersBridge::isWorkAreaDone);
                if (allDone) {
                    phase = Phase.COMPLETE;
                    return true;
                }
                // Safety valve: if builders are stuck (no completion within
                // MAX_BUILD_TICKS) fail cleanly so the raid still progresses.
                int maxTicks = RaidConfig.CAMP_MAX_BUILD_SECONDS.get() * 20;
                if (ticksSinceStart > maxTicks) {
                    FactionLogger.LOG.info("Camp {} timed out after {}s; advancing raid.",
                            blueprintId, RaidConfig.CAMP_MAX_BUILD_SECONDS.get());
                    phase = Phase.FAILED;
                    return true;
                }
            }
            return false;
        }

        /**
         * Remove the workers and work-area entities so the camp stops being
         * "active" when the raid ends. Placed blocks (tents, banners) are
         * left behind — that's part of the "raiders were here" feel.
         */
        public void cleanup(ServerLevel level) {
            for (Entity entity : spawnedEntities) {
                if (entity != null && entity.isAlive() && entity.level() == level) {
                    entity.discard();
                }
            }
            spawnedEntities.clear();
            workAreas.clear();
        }
    }
}
