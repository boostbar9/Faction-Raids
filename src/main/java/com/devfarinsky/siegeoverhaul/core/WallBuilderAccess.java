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
import java.lang.reflect.Method;
import net.minecraft.world.level.pathfinder.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/** Keep commissioned jobs assigned and repair failed approaches; Workers performs construction. */
public final class WallBuilderAccess extends Goal {
    private final Mob worker;
    private final Goal delegate;
    private final Field areaField, blockField, stateField, workDoneField;
    private Path pendingPath;
    private Set<BlockPos> pendingSites = Set.of();
    private long pendingUntil;
    private static final ClassValue<java.util.Optional<Method>> PATH_READY = new ClassValue<>() {
        @Override protected java.util.Optional<Method> computeValue(Class<?> type) {
            try { return java.util.Optional.of(type.getMethod("isProcessed")); }
            catch (NoSuchMethodException ignored) { return java.util.Optional.empty(); }
        }
    };
    private long nextSearch, nextRoute;
    private BlockPos lastTarget, destination;
    private Entity reservedArea;
    private Set<Long> reservedColumns = Set.of();

    WallBuilderAccess(Mob worker, Goal delegate) throws ReflectiveOperationException {
        this.worker = worker;
        this.delegate = delegate;
        areaField = worker.getClass().getField("currentBuildArea");
        blockField = delegate.getClass().getField("blockPos");
        stateField = delegate.getClass().getField("state");
        workDoneField = findWorkDone(delegate.getClass());
        setFlags(delegate.getFlags());
    }

    private static Field findWorkDone(Class<?> type) throws ReflectiveOperationException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField("workDone");
                if (field.getType() != boolean.class) throw new NoSuchFieldException("workDone boolean");
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException("workDone");
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
    @Override public void stop() { delegate.stop(); destination = null; lastTarget = null; pendingPath = null; pendingSites = Set.of(); }
    @Override public void tick() {
        retainCommission();
        delegate.tick();
        if (!(worker.level() instanceof ServerLevel level) || worker.isPassenger()
                || worker.isLeashed() || worker.getTarget() != null) return;
        try {
            Object current = areaField.get(worker);
            if (current != reservedArea) {
                reservedArea = null; reservedColumns = Set.of();
                pendingPath = null; pendingSites = Set.of(); destination = null; lastTarget = null;
            }
            if (!(current instanceof Entity area) || !isCommission(area)) return;
            Object state = stateField.get(delegate);
            BlockPos target = blockField.get(delegate) instanceof BlockPos p ? p : null;
            if (state instanceof Enum<?> e && e.name().equals("MOVE_TO_WORK_AREA")) target = area.getOnPos();
            if (target == null) return;
            if (reservedArea != area) {
                var columns = new java.util.HashSet<Long>();
                for (String fieldName : new String[]{"stackToPlace", "stackToPlaceMultiBlock"}) {
                    Object cells = area.getClass().getField(fieldName).get(area);
                    if (!(cells instanceof Iterable<?> iterable)) return;
                    for (Object cell : iterable) {
                        BlockPos pos = (BlockPos) cell.getClass().getMethod("getPos").invoke(cell);
                        columns.add(pos.atY(0).asLong());
                    }
                }
                reservedColumns = columns;
                reservedArea = area;
            }
            route(level, target, state instanceof Enum<?> e && e.name().equals("MOVE_TO_WORK_AREA") ? 20 : 40);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Keep native behavior when a companion changes its public job state.
        }
    }

    private boolean isCommission(Entity area) {
        var data = worker.getPersistentData();
        return area.isAlive() && !area.isRemoved()
                && data.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID)
                && area.getUUID().equals(data.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID))
                && data.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_OWNER)
                && data.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_OWNER).equals(WorkersBridge.readOwner(area))
                && data.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_OWNER).equals(WorkersBridge.readWorkerOwner(worker))
                && WorkersBridge.workingOn(worker, area);
    }

    /** Native SELECT_WORK_AREA otherwise replaces currentBuildArea with a competing nearby job. */
    private void retainCommission() {
        if (!(worker.level() instanceof ServerLevel)) return;
        try {
            Object state = stateField.get(delegate);
            if (!(state instanceof Enum<?> selection) || !selection.name().equals("SELECT_WORK_AREA")) return;
            if (!(areaField.get(worker) instanceof Entity area) || !isCommission(area)
                    || Boolean.TRUE.equals(area.getClass().getMethod("isDone").invoke(area))) return;
            Method eligible = java.util.Arrays.stream(area.getClass().getMethods())
                    .filter(method -> method.getName().equals("canWorkHere") && method.getParameterCount() == 1
                            && method.getParameterTypes()[0].isInstance(worker)).findFirst().orElseThrow();
            if (!Boolean.TRUE.equals(eligible.invoke(area, worker))) return;
            Object move = java.util.Arrays.stream(selection.getDeclaringClass().getEnumConstants())
                    .filter(value -> value.name().equals("MOVE_TO_WORK_AREA")).findFirst().orElseThrow();
            // Resolve the complete native selection contract before changing anything.
            Method active = area.getClass().getMethod("setBeingWorkedOn", boolean.class);
            Method time = area.getClass().getMethod("setTime", int.class);
            active.invoke(area, true);
            time.invoke(area, 0);
            workDoneField.setBoolean(delegate, false);
            blockField.set(delegate, null); // A supply interruption may leave the previous block target.
            stateField.set(delegate, move);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Unsupported companion versions retain their own selection behavior.
        }
    }

    private static boolean pathReady(Path path) {
        try {
            var ready = PATH_READY.get(path.getClass());
            return ready.isEmpty() || Boolean.TRUE.equals(ready.get().invoke(path));
        } catch (ReflectiveOperationException | RuntimeException ignored) { return false; }
    }

    void route(ServerLevel level, BlockPos target, int nativeReachSquared) {
        double dx = worker.getX() - (target.getX() + 0.5), dz = worker.getZ() - (target.getZ() + 0.5);
        if (dx * dx + dz * dz < nativeReachSquared) return;
        if (!target.equals(lastTarget)) {
            pendingPath = null;
            pendingSites = Set.of();
            destination = null;
        }
        var nav = worker.getNavigation();
        var existing = nav.getPath();
        if (existing != null && (!pathReady(existing) || (!existing.isDone() && existing.canReach()))) return;
        long now = level.getGameTime();
        if (now < nextRoute && now >= nextRoute - 10) return;
        nextRoute = now + 10;
        if (pendingPath != null) {
            if (now <= pendingUntil && !pathReady(pendingPath)) return;
            Path ready = pendingPath;
            Set<BlockPos> sites = pendingSites;
            pendingPath = null;
            pendingSites = Set.of();
            if (now <= pendingUntil && pathReady(ready) && ready.canReach()
                    && sites.contains(ready.getTarget())
                    && standingSites(level, worker, target).contains(ready.getTarget())
                    && moveToSite(ready.getTarget())) destination = ready.getTarget();
            return;
        }
        if (now < nextSearch && now >= nextSearch - 40) {
            if (target.equals(lastTarget) && destination != null) moveToSite(destination);
            return;
        }
        nextSearch = now + 40;
        lastTarget = target.immutable();
        destination = null;
        Set<BlockPos> candidates = standingSites(level, worker, target);
        candidates.removeIf(p -> reservedColumns.contains(p.atY(0).asLong()));
        if (candidates.isEmpty()) return;
        var path = nav.createPath(candidates, 0);
        if (path == null) return;
        if (!pathReady(path)) {
            pendingPath = path;
            pendingSites = Set.copyOf(candidates);
            pendingUntil = now + 100;
        } else if (path.canReach() && candidates.contains(path.getTarget()) && moveToSite(path.getTarget())) {
            destination = path.getTarget();
        }
    }

    private boolean moveToSite(BlockPos site) {
        // The multi-target path is a reachability probe. A late-installed AsyncPath misses
        // native target/reach-range callbacks; let native moveTo own its movement path.
        // Integer coordinates also avoid upstream truncation of negative half-coordinates.
        return worker.getNavigation().moveTo(site.getX(), site.getY(), site.getZ(), 0.8);
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
