package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.ModConstants;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/** Adjust only failed native movement for a commissioned wall; Workers still performs the job. */
public final class WallBuilderAccess extends Goal {
    private final Mob worker;
    private final Goal delegate;
    private final Field areaField, blockField, stateField;
    private long nextSearch, nextRoute;
    private BlockPos lastTarget, destination;

    WallBuilderAccess(Mob worker, Goal delegate) throws ReflectiveOperationException {
        this.worker = worker;
        this.delegate = delegate;
        areaField = worker.getClass().getField("currentBuildArea");
        blockField = delegate.getClass().getField("blockPos");
        stateField = delegate.getClass().getField("state");
        setFlags(delegate.getFlags());
    }

    public static void install(Mob worker) {
        if (worker.goalSelector == null) return;
        var goals = new ArrayList<>(worker.goalSelector.getAvailableGoals());
        if (goals.stream().anyMatch(g -> g.getGoal() instanceof WallBuilderAccess)) return;
        for (var wrapped : goals) {
            if (!wrapped.getGoal().getClass().getName().equals("com.talhanation.workers.entities.ai.BuilderWorkGoal")) continue;
            try {
                var replacement = new WallBuilderAccess(worker, wrapped.getGoal());
                worker.goalSelector.removeGoal(wrapped.getGoal());
                worker.goalSelector.addGoal(wrapped.getPriority(), replacement);
            } catch (ReflectiveOperationException ex) {
                com.devfarinsky.siegeoverhaul.FactionLogger.LOG.debug("Wall access API unavailable: {}", ex.getMessage());
            }
            return;
        }
    }

    @Override public boolean canUse() { return delegate.canUse(); }
    @Override public boolean canContinueToUse() { return delegate.canContinueToUse(); }
    @Override public boolean isInterruptable() { return delegate.isInterruptable(); }
    @Override public boolean requiresUpdateEveryTick() { return delegate.requiresUpdateEveryTick(); }
    @Override public void start() { delegate.start(); }
    @Override public void stop() { delegate.stop(); destination = null; lastTarget = null; }
    @Override public void tick() {
        delegate.tick();
        if (!(worker.level() instanceof ServerLevel level) || worker.isPassenger()
                || worker.isLeashed() || worker.getTarget() != null) return;
        try {
            Object current = areaField.get(worker);
            var data = worker.getPersistentData();
            if (!(current instanceof Entity area) || !area.isAlive()
                    || !data.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID)
                    || !area.getUUID().equals(data.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID))
                    || !data.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_OWNER)
                    || !data.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_OWNER).equals(WorkersBridge.readOwner(area))
                    || !data.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_OWNER).equals(WorkersBridge.readWorkerOwner(worker))
                    || !WorkersBridge.workingOn(worker, area)) return;
            Object state = stateField.get(delegate);
            BlockPos target = blockField.get(delegate) instanceof BlockPos p ? p : null;
            if (state instanceof Enum<?> e && e.name().equals("MOVE_TO_WORK_AREA")) target = area.getOnPos();
            if (target == null) return;
            route(level, target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Keep native behavior when a companion changes its public job state.
        }
    }

    void route(ServerLevel level, BlockPos target) {
        var nav = worker.getNavigation();
        var existing = nav.getPath();
        if (existing != null && !existing.isDone() && existing.canReach()) return;
        double dx = worker.getX() - (target.getX() + 0.5), dz = worker.getZ() - (target.getZ() + 0.5);
        if (dx * dx + dz * dz < 16) return; // Already inside the native marker's squared reach (20).
        long now = level.getGameTime();
        if (now < nextRoute && now >= nextRoute - 10) return;
        nextRoute = now + 10;
        if (now < nextSearch && now >= nextSearch - 40) {
            if (target.equals(lastTarget) && destination != null) nav.moveTo(destination.getX()+0.5,
                    destination.getY(), destination.getZ()+0.5, 0.8);
            return;
        }
        nextSearch = now + 40;
        lastTarget = target.immutable();
        destination = null;
        Set<BlockPos> candidates = standingSites(level, worker, target);
        if (candidates.isEmpty()) return;
        var path = nav.createPath(candidates, 0);
        if (path != null && path.canReach() && nav.moveTo(path, 0.8)) destination = path.getTarget();
    }

    /** At most 49 columns, inside native horizontal reach; never dig or move the blueprint. */
    static Set<BlockPos> standingSites(ServerLevel level, Mob worker, BlockPos target) {
        Set<BlockPos> sites = new LinkedHashSet<>();
        for (int dx=-3; dx<=3; dx++) for (int dz=-3; dz<=3; dz++) {
            BlockPos column = target.offset(dx, 0, dz);
            if (dx*dx+dz*dz >= 16 || !level.hasChunkAt(column)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
            if (Math.abs(y-target.getY()) > 12 || y <= level.getMinBuildHeight() || y+2 >= level.getMaxBuildHeight()) continue;
            BlockPos feet = column.atY(y), floor = feet.below();
            var support = level.getBlockState(floor);
            if (!support.isFaceSturdy(level, floor, Direction.UP) || !support.getFluidState().isEmpty()
                    || support.is(Blocks.MAGMA_BLOCK) || support.is(Blocks.CAMPFIRE) || support.is(Blocks.SOUL_CAMPFIRE)
                    || support.is(Blocks.CACTUS) || !level.getWorldBorder().isWithinBounds(feet)
                    || !level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()) continue;
            var body = worker.getBoundingBox().move(Vec3.atBottomCenterOf(feet).subtract(worker.position()));
            if (level.getWorldBorder().isWithinBounds(body) && level.noCollision(worker, body)) sites.add(feet);
        }
        return sites;
    }
}
