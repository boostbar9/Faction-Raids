package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.ModConstants;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.nativecompat.NativeConstructionGuard;
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
    private BlockPos approachTarget;
    private BlockPos lastSelfObstruction;

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

    public static boolean install(Mob worker) {
        if (worker.goalSelector == null) return false;
        var goals = new ArrayList<>(worker.goalSelector.getAvailableGoals());
        if (goals.stream().anyMatch(g -> g.getGoal() instanceof WallBuilderAccess)) return true;
        for (var wrapped : goals) {
            if (!wrapped.getGoal().getClass().getName().equals("com.talhanation.workers.entities.ai.BuilderWorkGoal")) continue;
            try {
                var replacement = new WallBuilderAccess(worker, wrapped.getGoal());
                worker.goalSelector.removeGoal(wrapped.getGoal());
                worker.goalSelector.addGoal(wrapped.getPriority(), replacement);
                return true;
            } catch (ReflectiveOperationException ex) {
                com.devfarinsky.siegeoverhaul.FactionLogger.LOG.debug("Wall access API unavailable: {}", ex.getMessage());
            }
            return false;
        }
        return false;
    }

    /** Reset only stale transient native goal state after the new area's assignment succeeds. */
    public static boolean prepareProtectedHandoff(Mob worker, Entity expectedArea) {
        if (worker.goalSelector == null || NativeConstructionGuard.currentArea(worker) != expectedArea) return false;
        for (var goal : worker.goalSelector.getAvailableGoals())
            if (goal.getGoal() instanceof WallBuilderAccess access) return access.resetTransientState();
        return false;
    }

    private boolean resetTransientState() {
        java.util.List<Field> fields = new ArrayList<>();
        java.util.List<Object> previous = new ArrayList<>();
        try {
            for (String name : new String[]{"stackToBreak", "stackToPlace", "stackToFree"}) {
                Field field = delegate.getClass().getField(name);
                if (!field.getType().isAssignableFrom(java.util.Stack.class)) return false;
                fields.add(field); previous.add(field.get(delegate));
            }
            Object selection = java.util.Arrays.stream(stateField.getType().getEnumConstants())
                    .filter(value -> ((Enum<?>)value).name().equals("SELECT_WORK_AREA")).findFirst().orElseThrow();
            fields.add(stateField); previous.add(stateField.get(delegate));
            fields.add(blockField); previous.add(blockField.get(delegate));
            fields.add(workDoneField); previous.add(workDoneField.get(delegate));
            for (int i = 0; i < 3; i++) fields.get(i).set(delegate, new java.util.Stack<>());
            stateField.set(delegate, selection); blockField.set(delegate, null); workDoneField.setBoolean(delegate, false);
            reservedArea = null; reservedColumns = Set.of(); approachTarget = null; lastSelfObstruction = null;
            pendingPath = null; pendingSites = Set.of(); destination = null; lastTarget = null;
            return true;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            for (int i = 0; i < fields.size(); i++) try { fields.get(i).set(delegate, previous.get(i)); }
            catch (ReflectiveOperationException | RuntimeException ignored) { }
            return false;
        }
    }

    @Override public boolean canUse() { return delegate.canUse(); }
    @Override public boolean canContinueToUse() { return delegate.canContinueToUse(); }
    @Override public boolean isInterruptable() { return delegate.isInterruptable(); }
    @Override public boolean requiresUpdateEveryTick() { return delegate.requiresUpdateEveryTick(); }
    @Override public void start() { reservedArea = null; approachTarget = null; lastSelfObstruction = null; delegate.start(); }
    @Override public void stop() { delegate.stop(); reservedArea = null; approachTarget = null; lastSelfObstruction = null; destination = null; lastTarget = null; pendingPath = null; pendingSites = Set.of(); }
    @Override public void tick() {
        retainCommission();
        if (approachCommission()) return;
        if (recoverBuriedApproach()) return;
        // Access helpers can advance MOVE_TO_WORK_AREA to PREPARE_BREAK_BLOCKS.
        // Validate after those transitions, at the actual native dispatch boundary.
        if (!NativeConstructionGuard.beforeNativeTick(worker, delegate)) {
            recoverGuardedSelfObstruction();
            return;
        }
        Entity guardedArea = NativeConstructionGuard.currentArea(worker);
        var mutationCells = NativeConstructionGuard.mutationCells(delegate);
        // Workers checks movement every tenth tick but may place every fifth tick,
        // including the first target popped from its stack. Route before dispatch.
        if (awaitMutationArrival(mutationCells)) return;
        delegate.tick();
        NativeConstructionGuard.afterNativeTick(worker, guardedArea, mutationCells);
        if (!(worker.level() instanceof ServerLevel level) || worker.isPassenger()
                || worker.isLeashed() || worker.getTarget() != null) return;
        try {
            Object current = areaField.get(worker);
            if (current != reservedArea) {
                reservedArea = null; reservedColumns = Set.of(); lastSelfObstruction = null;
                pendingPath = null; pendingSites = Set.of(); destination = null; lastTarget = null;
            }
            if (!(current instanceof Entity area) || !isCommission(area)) return;
            Object state = stateField.get(delegate);
            BlockPos target = blockField.get(delegate) instanceof BlockPos p ? p : null;
            if (state instanceof Enum<?> e && e.name().equals("MOVE_TO_WORK_AREA")) target = area.getOnPos();
            if (target == null) return;
            if (!reserveColumns(area)) return;
            route(level, target, state instanceof Enum<?> e && e.name().equals("MOVE_TO_WORK_AREA") ? 20 : 40);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Keep native behavior when a companion changes its public job state.
        }
    }

    private boolean awaitMutationArrival(Set<BlockPos> cells) {
        if (cells.isEmpty() || !(worker.level() instanceof ServerLevel level)) return false;
        try {
            if (!(areaField.get(worker) instanceof Entity area) || !isCommission(area)) return false;
            if (!reserveColumns(area)) return true;
            for (BlockPos target : cells) {
                if (workStandingSite(level, target, BlockPos.containing(worker.position()))) continue;
                route(level, target, 40);
                return true;
            }
            return false;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return true; // Never dispatch an unverified commissioned mutation.
        }
    }

    private boolean workStandingSite(ServerLevel level, BlockPos target, BlockPos feet) {
        // Keep the native horizontal limit, and bound vertical reach from hand height.
        double dx = worker.getX() - (target.getX() + .5);
        double dz = worker.getZ() - (target.getZ() + .5);
        double dy = worker.position().y + 1.0 - (target.getY() + .5);
        return dx * dx + dy * dy + dz * dz < 40
                && !reservedColumns.contains(feet.atY(0).asLong())
                && safeStandingSite(level, worker, feet);
    }

    /** Keep collision protection, but let the existing pathfinder move this builder out of its own target. */
    private void recoverGuardedSelfObstruction() {
        if (!(worker.level() instanceof ServerLevel level) || worker.isPassenger()
                || worker.isLeashed() || worker.getTarget() != null) return;
        try {
            if (!(areaField.get(worker) instanceof Entity area) || !isCommission(area)
                    || !"Paused: move entities out of the planned blocks".equals(NativeConstructionGuard.status(area))) return;
            var cells = NativeConstructionGuard.mutationCells(delegate);
            if (cells.size() != 1) return;
            BlockPos target = cells.iterator().next();
            if (!worker.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(target)) || !reserveColumns(area)) return;
            if (!target.equals(lastSelfObstruction)) {
                lastSelfObstruction = target.immutable();
                var diagnostic = worker.getPersistentData();
                diagnostic.putInt("SiegeSelfClearanceRequests", (int)Math.min(Integer.MAX_VALUE,
                        Math.max(0L, diagnostic.getInt("SiegeSelfClearanceRequests")) + 1));
                diagnostic.putLong("SiegeSelfClearanceTarget", target.asLong());
                diagnostic.putString("SiegeSelfClearanceBounds", worker.getBoundingBox().toString());
            }
            route(level, target, 40, true);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            // No unverified movement fallback, native queue changes, teleport or placement.
        }
    }

    /** The blueprint corner is a coordinate origin, not necessarily a reachable workplace. */
    private boolean approachCommission() {
        if (!(worker.level() instanceof ServerLevel level) || worker.isPassenger()
                || worker.isLeashed() || worker.getTarget() != null) return false;
        try {
            if (!(stateField.get(delegate) instanceof Enum<?> state)
                    || !state.name().equals("MOVE_TO_WORK_AREA")
                    || !(areaField.get(worker) instanceof Entity area) || !isCommission(area)
                    || !Boolean.FALSE.equals(area.getClass().getMethod("getFreeArea").invoke(area))) return false;
            Object prepare = java.util.Arrays.stream(state.getDeclaringClass().getEnumConstants())
                    .filter(value -> value.name().equals("PREPARE_BREAK_BLOCKS")).findFirst().orElseThrow();
            if (!reserveColumns(area) || approachTarget == null) return false;
            double dx = worker.getX() - (approachTarget.getX()+0.5);
            double dz = worker.getZ() - (approachTarget.getZ()+0.5);
            BlockPos feet=BlockPos.containing(worker.position());
            if (dx*dx+dz*dz < 20 && !reservedColumns.contains(feet.atY(0).asLong())
                    && safeStandingSite(level,worker,feet)) {
                worker.getNavigation().stop();
                blockField.set(delegate,null);
                stateField.set(delegate,prepare);
                return false; // Workers scans the plan, requests supplies and places every block.
            }
            route(level,approachTarget,20);
            return true; // Do not let the delegate replace this route with the corner marker.
        } catch (ReflectiveOperationException | RuntimeException ignored) { return false; }
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

    /** Do not let native horizontal reach advance construction while below the marker. */
    private boolean recoverBuriedApproach() {
        if (!(worker.level() instanceof ServerLevel level) || worker.isPassenger()
                || worker.isLeashed() || worker.getTarget() != null) return false;
        try {
            if (!(stateField.get(delegate) instanceof Enum<?> state)
                    || !state.name().equals("MOVE_TO_WORK_AREA")
                    || !(areaField.get(worker) instanceof Entity area) || !isCommission(area)) return false;
            BlockPos target=area.getOnPos();
            double dx=worker.getX()-(target.getX()+0.5), dz=worker.getZ()-(target.getZ()+0.5);
            if (dx*dx+dz*dz >= 20 || safeStandingSite(level,worker,BlockPos.containing(worker.position()))) return false;
            if (!reserveColumns(area)) return false;
            route(level,target,20);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) { return false; }
    }

    private boolean reserveColumns(Entity area) throws ReflectiveOperationException {
        if (reservedArea == area) return true;
        reservedArea=null;reservedColumns=Set.of();approachTarget=null;lastSelfObstruction=null;
        pendingPath=null;pendingSites=Set.of();destination=null;lastTarget=null;
        var columns=new java.util.HashSet<Long>();
        for (String name : new String[]{"stackToPlace", "stackToPlaceMultiBlock"}) {
            Object cells=area.getClass().getField(name).get(area);
            if (!(cells instanceof Iterable<?> iterable)) return false;
            for (Object cell : iterable) {
                BlockPos pos=(BlockPos)cell.getClass().getMethod("getPos").invoke(cell);
                columns.add(pos.atY(0).asLong());
                if (approachTarget == null || horizontalDistance(pos) < horizontalDistance(approachTarget))
                    approachTarget = pos.immutable();
            }
        }
        reservedColumns=columns;reservedArea=area;
        return true;
    }

    private double horizontalDistance(BlockPos pos) {
        double dx=worker.getX()-(pos.getX()+0.5), dz=worker.getZ()-(pos.getZ()+0.5);
        return dx*dx+dz*dz;
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
        route(level, target, nativeReachSquared, false);
    }

    void route(ServerLevel level, BlockPos target, int nativeReachSquared, boolean selfRecovery) {
        double dx = worker.getX() - (target.getX() + 0.5), dz = worker.getZ() - (target.getZ() + 0.5);
        // Native reach is horizontal only. Being directly below the job is
        // not a safe work position, even when its distance check passes.
        if (!selfRecovery && dx * dx + dz * dz < nativeReachSquared
                && !reservedColumns.contains(BlockPos.containing(worker.position()).atY(0).asLong())
                && safeStandingSite(level, worker, BlockPos.containing(worker.position()))
                && (nativeReachSquared != 40 || workStandingSite(level, target,
                        BlockPos.containing(worker.position())))) return;
        if (!target.equals(lastTarget)) {
            pendingPath = null;
            pendingSites = Set.of();
            destination = null;
        }
        var nav = worker.getNavigation();
        var existing = nav.getPath();
        if (existing != null && !pathReady(existing)) return;
        if (existing != null && !existing.isDone() && existing.canReach()) {
            var end = existing.getEndNode();
            BlockPos feet = end == null ? null : new BlockPos(end.x,end.y,end.z);
            if (feet != null && (feet.distSqr(target.atY(feet.getY())) < nativeReachSquared
                    || !selfRecovery && target.equals(lastTarget) && feet.equals(destination))
                    && !reservedColumns.contains(feet.atY(0).asLong())
                    && (!selfRecovery || recoveryMargin(feet, reservedColumns, worker.getBbWidth()))
                    && safeStandingSite(level,worker,feet)) return;
            // A reachable cave endpoint still sends the builder underground.
            // Stop that route before probing loaded surface standing space.
            nav.stop();
        }
        long now = level.getGameTime();
        if (now < nextRoute && now >= nextRoute - 10) return;
        nextRoute = now + 10;
        if (pendingPath != null) {
            if (now <= pendingUntil && !pathReady(pendingPath)) return;
            Path ready = pendingPath;
            Set<BlockPos> sites = pendingSites;
            pendingPath = null;
            pendingSites = Set.of();
            BlockPos reached = now <= pendingUntil
                    ? routeEndpoint(level, target, ready, sites, nativeReachSquared, selfRecovery) : null;
            if (reached != null && moveToSite(reached)) destination = reached;
            return;
        }
        if (now < nextSearch && now >= nextSearch - 40) {
            if (target.equals(lastTarget) && destination != null
                    && (!selfRecovery || recoveryMargin(destination, reservedColumns, worker.getBbWidth()))
                    && safeStandingSite(level,worker,destination)) moveToSite(destination);
            return;
        }
        nextSearch = now + 40;
        lastTarget = target.immutable();
        destination = null;
        Set<BlockPos> candidates = routeSites(level, target, nativeReachSquared, selfRecovery);
        if (candidates.isEmpty()) return;
        var path = nav.createPath(candidates, 0);
        if (path == null) return;
        if (!pathReady(path)) {
            pendingPath = path;
            pendingSites = Set.copyOf(candidates);
            pendingUntil = now + 100;
        } else {
            BlockPos reached = routeEndpoint(level, target, path, candidates, nativeReachSquared, selfRecovery);
            if (reached != null && moveToSite(reached)) destination = reached;
        }
    }

    private Set<BlockPos> routeSites(ServerLevel level, BlockPos target, int nativeReachSquared, boolean selfRecovery) {
        Set<BlockPos> sites = standingSites(level, worker, target, selfRecovery ? 6 : 3,
                selfRecovery ? nativeReachSquared : 16);
        sites.removeIf(p -> reservedColumns.contains(p.atY(0).asLong())
                || nativeReachSquared == 40 && Vec3.atBottomCenterOf(p).add(0, 1, 0)
                        .distanceToSqr(Vec3.atCenterOf(target)) >= 40
                || selfRecovery && !recoveryMargin(p, reservedColumns, worker.getBbWidth()));
        return sites;
    }

    private BlockPos routeEndpoint(ServerLevel level, BlockPos target, Path path,
                                   Set<BlockPos> sites, int nativeReachSquared, boolean selfRecovery) {
        BlockPos reached = reachedSite(path, sites);
        if (reached != null)
            return routeSites(level, target, nativeReachSquared, selfRecovery).contains(reached) ? reached : null;
        if (selfRecovery || !pathReady(path) || path.canReach() || path.getNodeCount() < 2) return null;
        var end = path.getEndNode();
        if (end == null) return null;
        BlockPos progress = new BlockPos(end.x, end.y, end.z);
        // A native budget-limited route is an approach, never proof of work reach.
        // Reissue through native coordinate movement to retain async callbacks.
        if (reservedColumns.contains(progress.atY(0).asLong()) || !safeStandingSite(level, worker, progress)
                || progress.distSqr(target) + 1 >= worker.position().distanceToSqr(Vec3.atBottomCenterOf(target)))
            return null;
        return progress;
    }

    private static BlockPos reachedSite(Path path, Set<BlockPos> sites) {
        if (!pathReady(path) || !path.canReach()) return null;
        // Workers 2.0.3 labels a successful multi-target path with its FIRST target,
        // even when it reached a different candidate. Only its actual end node
        // proves which safe standing site native movement can reach.
        var end = path.getEndNode();
        if (end == null) return null;
        BlockPos reached = new BlockPos(end.x, end.y, end.z);
        return sites.contains(reached) ? reached : null;
    }

    private boolean moveToSite(BlockPos site) {
        // The multi-target path is a reachability probe. A late-installed AsyncPath misses
        // native target/reach-range callbacks; let native moveTo own its movement path.
        // Integer coordinates also avoid upstream truncation of negative half-coordinates.
        return worker.getNavigation().moveTo(site.getX(), site.getY(), site.getZ(), 0.8);
    }

    /** At most 49 columns, inside native horizontal reach; never dig or move the blueprint. */
    static Set<BlockPos> standingSites(ServerLevel level, Mob worker, BlockPos target) {
        return standingSites(level, worker, target, 3, 16);
    }

    /** Recovery-only search: at most 169 loaded columns, still inside the native reach. */
    private static Set<BlockPos> standingSites(ServerLevel level, Mob worker, BlockPos target, int radius, int reachSquared) {
        Set<BlockPos> sites = new LinkedHashSet<>();
        for (int dx=-radius; dx<=radius; dx++) for (int dz=-radius; dz<=radius; dz++) {
            BlockPos column = target.offset(dx, 0, dz);
            if (dx*dx+dz*dz >= reachSquared || !level.hasChunkAt(column)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
            if (Math.abs(y-target.getY()) > 12 || y <= level.getMinBuildHeight() || y+2 >= level.getMaxBuildHeight()) continue;
            BlockPos feet = column.atY(y);
            if (safeStandingSite(level,worker,feet)) sites.add(feet);
        }
        return sites;
    }

    /** Arrival tolerance must not leave the worker body straddling a neighboring wall column. */
    static boolean recoveryMargin(BlockPos feet, Set<Long> reservedColumns, float width) {
        if (!Float.isFinite(width) || width < 0 || width > 4) return false;
        int margin = Math.max(1, (int)Math.ceil(width / 2.0 + .5));
        for (int x = -margin; x <= margin; x++) for (int z = -margin; z <= margin; z++)
            if (reservedColumns.contains(feet.offset(x, 0, z).atY(0).asLong())) return false;
        return true;
    }

    private static boolean safeStandingSite(ServerLevel level, Mob worker, BlockPos feet) {
        if (!level.hasChunkAt(feet) || feet.getY() <= level.getMinBuildHeight()
                || feet.getY()+2 >= level.getMaxBuildHeight()
                || level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,feet.getX(),feet.getZ()) != feet.getY()) return false;
        BlockPos floor=feet.below();
        var support=level.getBlockState(floor);
        if (!support.isFaceSturdy(level,floor,Direction.UP) || !support.getFluidState().isEmpty()
                || support.is(Blocks.MAGMA_BLOCK) || support.is(Blocks.CAMPFIRE) || support.is(Blocks.SOUL_CAMPFIRE)
                || support.is(Blocks.CACTUS) || !level.getBlockState(feet).isAir()
                || !level.getBlockState(feet.above()).isAir()) return false;
        var body=worker.getBoundingBox().move(Vec3.atBottomCenterOf(feet).subtract(worker.position()));
        return level.getWorldBorder().isWithinBounds(body) && level.noCollision(worker,body);
    }
}
