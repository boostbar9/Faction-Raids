package com.devfarinsky.siegeoverhaul.naval;

import com.devfarinsky.siegeoverhaul.ModConstants;
import com.devfarinsky.siegeoverhaul.RaidConfig;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.camp.CampGuards;
import com.devfarinsky.siegeoverhaul.compat.ClaimBridge;
import com.devfarinsky.siegeoverhaul.formations.RecruitsFormationBridge;
import com.devfarinsky.siegeoverhaul.siege.BlockRestoration;
import com.devfarinsky.siegeoverhaul.siege.RaiderLadderGoal;
import com.devfarinsky.siegeoverhaul.siege.SiegeDeployment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** A finite, physical construction job for an existing wave member, never an extra spawned crew. */
public final class BridgeBuilder {
    public static final String SPECIALIST_TAG = "SiegeBridgeBuilder";
    private static final int SEARCH_INTERVAL = 400;
    private static final int APPROACH_SCAN = 6;
    private static final int MAX_CANDIDATES = 12;
    /**
     * Rank-and-file roles that may be pulled onto a crossing job. These are the
     * values {@code RaidEvents.assignSiegeRole} actually writes for ordinary
     * line troops; the legacy codex ids are kept so raiders saved by older
     * versions stay eligible after a reload.
     */
    private static final Set<String> ORDINARY_ROLES = Set.of("marksman", "breacher",
            "shieldman", "bowman", "crossbowman");

    private BridgeBuilder() {}

    /** Restore movement ownership before other systems run; report each completed bridge once. */
    public static boolean tick(ServerLevel level, RaidSavedData.RaidState state, BlockPos objective) {
        var data = RaidSavedData.get(level.getServer());
        if (!running(data, state)) return false;
        var anchor = data.anchors.get(state.teamKey);
        boolean completed = state.bridgeCompletionPending;
        if (completed) {
            state.bridgeCompletionPending = false;
            data.setDirty();
        }
        if (state.bridgePlan != null) {
            BridgePlan plan = state.bridgePlan;
            var entity = level.getEntity(plan.builder);
            if (!RaidConfig.ENABLE_BRIDGE_BUILDING.get() || anchor == null
                    || !plan.objective.equals(objective) || level.getGameTime() >= plan.deadline
                    || !state.raiders.contains(plan.builder)
                    || (entity != null && (!(entity instanceof Mob mob) || !eligible(mob, state)))) {
                cancel(level, state);
            } else if (entity instanceof Mob mob) {
                install(level, state, mob);
            }
            // An unloaded builder gets no substitute and no remote construction; the saved deadline still runs.
            return completed;
        }
        if (!RaidConfig.ENABLE_BRIDGE_BUILDING.get() || anchor == null
                || state.bridgeAttempts >= RaidConfig.MAX_BRIDGES_PER_RAID.get()
                || state.bridgeBlocksSpent >= RaidConfig.MAX_BRIDGE_BLOCKS_PER_RAID.get()
                || level.getGameTime() < state.bridgeNextAttempt) return completed;
        state.bridgeNextAttempt = level.getGameTime() + SEARCH_INTERVAL;
        data.setDirty();
        int candidates = 0;
        Map<ChunkPos, Boolean> claims = new HashMap<>();
        for (var id : state.raiders) {
            if (!(level.getEntity(id) instanceof Mob mob) || !eligible(mob, state)
                    || mob.distanceToSqr(Vec3.atCenterOf(objective)) > 192 * 192
                    || RaiderLadderGoal.assigned(mob)) continue;
            if (++candidates > MAX_CANDIDATES) break;
            if (hasObjectiveRoute(mob, objective)) continue;
            BridgePlan plan = discover(level, mob, objective, anchor, claims, RaidConfig.MAX_BRIDGE_SPAN.get(),
                    RaidConfig.MAX_BRIDGE_BLOCKS_PER_RAID.get() - state.bridgeBlocksSpent);
            if (plan == null) continue;
            state.bridgeAttempts++;
            state.bridgePlan = plan;
            mob.getPersistentData().putBoolean(SPECIALIST_TAG, true);
            mob.setCustomName(Component.literal("Enemy Bridge Builder"));
            com.devfarinsky.siegeoverhaul.FactionLogger.LOG.info(
                    "[SiegeOverhaul] Bridge crossing started for {}: {} blocks {} from {}",
                    state.teamKey, plan.span, plan.direction, plan.start);
            // Keep the original combat role, equipment and native enemy ownership/unhireable setup.
            install(level, state, mob);
            data.setDirty();
            break;
        }
        return completed;
    }

    static boolean eligible(Mob mob, RaidSavedData.RaidState state) {
        var tag = mob.getPersistentData();
        return mob.isAlive() && !mob.isPassenger() && !mob.isVehicle() && mob.getBbWidth() <= 1.0F
                && state.raiders.contains(mob.getUUID()) && !state.campGuards.contains(mob.getUUID())
                && !mob.getUUID().equals(state.commanderUuid)
                && state.teamKey.equals(tag.getString(ModConstants.Tags.RAID_TEAM))
                && ORDINARY_ROLES.contains(tag.getString(ModConstants.Tags.RAID_ROLE))
                && !tag.getBoolean("SiegeEnemyHero") && !tag.contains("SiegeHeroRole")
                && !tag.contains(CampGuards.TEAM_TAG) && !tag.contains(ModConstants.Tags.CAMP_WORKER_TEAM)
                && !tag.getBoolean(ModConstants.Tags.SCOUT) && !tag.contains(SiegeDeployment.OPERATOR_ASSIGNED)
                && !tag.contains(SiegeDeployment.TEAM_TAG);
    }

    private static boolean running(RaidSavedData data, RaidSavedData.RaidState state) {
        return RaidConfig.ENABLED.get() && data.raids.get(state.teamKey) == state
                && state.preparationTicks <= 0 && !state.offlinePauseAnnounced;
    }

    static boolean hasObjectiveRoute(Mob mob, BlockPos objective) {
        var path = mob.getNavigation().createPath(objective, 1);
        return path != null && path.canReach();
    }

    static BridgePlan discover(ServerLevel level, Mob mob, BlockPos objective, RaidSavedData.Anchor anchor,
                               Map<ChunkPos, Boolean> claims, int maxSpan, int materialLeft) {
        BlockPos from = mob.blockPosition();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (Vec3.atCenterOf(objective).subtract(Vec3.atCenterOf(from))
                    .dot(Vec3.atLowerCornerOf(direction.getNormal())) <= 0) continue;
            BlockPos start = from;
            for (int approach = 0; approach <= APPROACH_SCAN; approach++, start = start.relative(direction)) {
                if (!safeShore(level, start)) break;
                int span = scanGap(level, start, direction, Math.min(maxSpan, materialLeft), anchor, claims);
                if (span == 0) continue;
                BlockPos end = start.relative(direction, span + 1);
                if (end.distSqr(objective) >= start.distSqr(objective)) break;
                var path = mob.getNavigation().createPath(start, 0);
                if (path == null || !path.canReach()) break;
                return new BridgePlan(mob.getUUID(), start, objective, direction, span,
                        level.getGameTime() + 600L + 100L * span);
            }
        }
        return null;
    }

    /** Start/shore are feet positions; only the blocks one level below become the walking deck. */
    static int scanGap(ServerLevel level, BlockPos start, Direction direction, int maxSpan,
                       RaidSavedData.Anchor anchor, Map<ChunkPos, Boolean> claims) {
        if (maxSpan < 1 || maxSpan > 64 || !safeShore(level, start)
                || foreign(level, start, anchor, claims)) return 0;
        for (int i = 1; i <= maxSpan + 1; i++) {
            BlockPos feet = start.relative(direction, i);
            if (!safeColumn(level, feet) || foreign(level, feet, anchor, claims)) return 0;
            if (safeShore(level, feet)) return i > 1 ? i - 1 : 0;
            if (i > maxSpan || !replaceable(level.getBlockState(feet.below()))
                    || !clearOfEntities(level, feet.below())) return 0;
        }
        return 0;
    }

    private static boolean foreign(ServerLevel level, BlockPos pos, RaidSavedData.Anchor anchor,
                                   Map<ChunkPos, Boolean> claims) {
        return claims.computeIfAbsent(new ChunkPos(pos), chunk -> ClaimBridge.isForeignClaim(level, chunk, anchor));
    }

    static boolean replaceable(BlockState block) {
        // Fluid-state checks alone also accept player-built waterlogged stairs, slabs and fences.
        return block.isAir() || block.is(Blocks.WATER);
    }

    private static boolean safeColumn(ServerLevel level, BlockPos feet) {
        for (int dy = -2; dy <= 1; dy++) {
            BlockPos pos = feet.above(dy);
            if (!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos)
                    || !level.getWorldBorder().isWithinBounds(pos)) return false;
            BlockState block = level.getBlockState(pos);
            if (block.is(Blocks.LAVA) || block.getFluidState().is(FluidTags.LAVA)) return false;
        }
        return level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir();
    }

    private static boolean safeShore(ServerLevel level, BlockPos feet) {
        if (!safeColumn(level, feet)) return false;
        BlockPos floor = feet.below();
        BlockState block = level.getBlockState(floor);
        return block.getFluidState().isEmpty() && block.isFaceSturdy(level, floor, Direction.UP)
                && !block.is(Blocks.MAGMA_BLOCK) && !block.is(Blocks.CAMPFIRE)
                && !block.is(Blocks.SOUL_CAMPFIRE) && !block.is(Blocks.CACTUS);
    }

    private static boolean clearOfEntities(ServerLevel level, BlockPos floor) {
        AABB space = new AABB(floor).expandTowards(0, 2, 0);
        return level.noCollision(null, space) && level.getEntities((Entity) null, space,
                entity -> !entity.isSpectator() && entity.isAlive()).isEmpty();
    }

    public static boolean assigned(Mob mob) {
        BuilderGoal goal = find(mob);
        return goal != null && goal.canUse();
    }

    private static BuilderGoal find(Mob mob) {
        if (mob.goalSelector == null) return null;
        for (var wrapped : mob.goalSelector.getAvailableGoals())
            if (wrapped.getGoal() instanceof BuilderGoal goal) return goal;
        return null;
    }

    private static void install(ServerLevel level, RaidSavedData.RaidState state, Mob mob) {
        BuilderGoal old = find(mob);
        if (old != null && old.plan == state.bridgePlan) return;
        if (old != null) mob.goalSelector.removeGoal(old);
        RecruitsFormationBridge.release(mob);
        mob.getNavigation().stop();
        mob.goalSelector.addGoal(0, new BuilderGoal(level, state, mob));
    }

    public static void cancel(ServerLevel level, RaidSavedData.RaidState state) {
        BridgePlan plan = state.bridgePlan;
        state.bridgePlan = null;
        state.bridgeNextAttempt = level.getGameTime() + SEARCH_INTERVAL;
        if (plan != null && level.getEntity(plan.builder) instanceof Mob mob) {
            BuilderGoal goal = find(mob);
            if (goal != null) mob.goalSelector.removeGoal(goal);
            mob.getNavigation().stop();
        }
        RaidSavedData.get(level.getServer()).setDirty();
    }

    /** Budgets now belong to RaidState; removing a finished raid releases them without a JVM cache. */
    public static void forget(String teamKey) {}

    static final class BuilderGoal extends Goal {
        private final ServerLevel level;
        private final RaidSavedData.RaidState state;
        private final Mob mob;
        private final BridgePlan plan;
        private int ticks;

        BuilderGoal(ServerLevel level, RaidSavedData.RaidState state, Mob mob) {
            this.level = level;
            this.state = state;
            this.mob = mob;
            this.plan = state.bridgePlan;
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
        }

        @Override public boolean canUse() {
            return state.bridgePlan == plan && mob.isAlive() && !mob.isNoAi()
                    && RaidConfig.ENABLE_BRIDGE_BUILDING.get()
                    && running(RaidSavedData.get(level.getServer()), state)
                    && state.raiders.contains(mob.getUUID());
        }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void stop() { mob.getNavigation().stop(); }

        @Override public void tick() {
            if (state.bridgePlan != plan) return;
            if (!canUse()) {
                mob.getNavigation().stop();
                return;
            }
            if (!eligible(mob, state) || !RaidConfig.ENABLE_BRIDGE_BUILDING.get()
                    || level.getGameTime() >= plan.deadline) {
                cancel(level, state);
                return;
            }
            if (++ticks % 10 != 1) return;
            var data = RaidSavedData.get(level.getServer());
            var anchor = data.anchors.get(state.teamKey);
            // Recheck the complete remaining route with a fresh per-chunk claim cache. A player's
            // changed support, obstruction or claim must never be silently built through after planning.
            Map<ChunkPos, Boolean> claims = new HashMap<>();
            if (anchor == null || !safeShore(level, plan.start) || !safeShore(level, plan.shore())) {
                cancel(level, state);
                return;
            }
            if (foreign(level, plan.start, anchor, claims) || foreign(level, plan.shore(), anchor, claims)) {
                cancel(level, state);
                return;
            }
            for (int i = 1; i <= plan.span; i++) {
                BlockPos floor = plan.floor(i);
                if (!safeColumn(level, floor.above()) || foreign(level, floor, anchor, claims)
                        || (i < plan.next ? !ownedPlank(state, level, floor)
                                         : !replaceable(level.getBlockState(floor)))) {
                    cancel(level, state);
                    return;
                }
            }
            BlockPos stand = plan.floor(plan.next - 1).above();
            Vec3 standAt = Vec3.atBottomCenterOf(stand);
            mob.setTarget(null);
            Vec3 look = Vec3.atCenterOf(plan.floor(Math.min(plan.next, plan.span)));
            mob.getLookControl().setLookAt(look.x, look.y, look.z);
            if (mob.distanceToSqr(standAt) > .64 || !mob.onGround()) {
                var path = mob.getNavigation().createPath(stand, 0);
                if (path != null && path.canReach()) mob.getNavigation().moveTo(path, 1.0);
                return;
            }
            mob.getNavigation().stop();
            if (plan.next > plan.span) {
                cancel(level, state);
                return;
            }
            if (state.bridgeBlocksSpent >= RaidConfig.MAX_BRIDGE_BLOCKS_PER_RAID.get()) {
                cancel(level, state);
                return;
            }
            if (level.getGameTime() < plan.nextPlacement) return;
            if (hasObjectiveRoute(mob, plan.objective)) {
                cancel(level, state);
                return;
            }
            BlockPos floor = plan.floor(plan.next);
            if (!clearOfEntities(level, floor)) return;
            BlockState original = level.getBlockState(floor);
            CompoundTag snapshot = original.isAir() ? new CompoundTag()
                    : BlockRestoration.serializeState(level, floor, original);
            if (!level.setBlockAndUpdate(floor, Blocks.OAK_PLANKS.defaultBlockState())) return;
            state.recordCampBlock(floor.asLong(), "minecraft:oak_planks", snapshot);
            state.bridgeBlocksSpent++;
            plan.next++;
            if (plan.next > plan.span) state.bridgeCompletionPending = true;
            plan.nextPlacement = level.getGameTime() + 20;
            mob.swing(InteractionHand.MAIN_HAND);
            data.setDirty();
        }
    }

    private static boolean ownedPlank(RaidSavedData.RaidState state, ServerLevel level, BlockPos floor) {
        var record = state.campBlocks.get(floor.asLong());
        return record != null && "minecraft:oak_planks".equals(record.getString("Placed"))
                && level.getBlockState(floor).is(Blocks.OAK_PLANKS);
    }
}
