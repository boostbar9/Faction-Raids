package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.camp.CampVegetation;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.core.SiegeCore;
import com.devfarinsky.siegeoverhaul.core.WallBuilderAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.BlockEvent;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Opt-in guard around the existing native BuilderWorkGoal tick, never a builder replacement.
 * Public upstream 29d26e1 has no mutation event: every path is checked before
 * dispatching its synchronous tick. No solid clearing or FREE_AREA is enabled.
 */
public final class NativeConstructionGuard {
    private static final String KEY = "SiegeProtectedConstructionV1", STATUS = "SiegeConstructionPause",
            PAID = "SiegeConstructionCommissionPaid", PROTECTED_LINK = "SiegeProtectedAreaReceipt",
            PROTECTED_GENERATION = "SiegeProtectedLedgerGeneration";
    private static final Map<Entity, Snapshot> CACHE = new WeakHashMap<>();
    private static final Set<String> STATES = Set.of("SELECT_WORK_AREA", "MOVE_TO_WORK_AREA", "PREPARE_FREE_AREA",
            "FREE_AREA", "PREPARE_BREAK_BLOCKS", "BREAK_BLOCKS", "PREPARE_PLACE_BLOCKS", "PLACE_BLOCKS",
            "PREPARE_PLACE_MULTIBLOCK", "PLACE_MULTIBLOCK", "DONE", "ERROR");
    private record Snapshot(AcceptedConstructionPlan plan, AcceptedConstructionReservation reservation,
                            Map<BlockPos, BlockState> before,
                            Set<Long> completed, Set<Long> cleared, UUID owner, UUID builder,
                            String coreKey, BlockPos corePos) {}

    private NativeConstructionGuard() {}

    /** All new commission callers must use the sealed protected native subtype. */
    public static String availabilityProblem() { return WorkersConstructionRuntime.problem(); }

    public static boolean commissionPaid(Entity area) {
        return area != null && area.getPersistentData() != null && area.getPersistentData().getBoolean(PAID);
    }

    /** Actual protected cells, including jobs whose physical marker is unloaded. */
    public static boolean reserves(ServerLevel level, java.util.Collection<BlockPos> cells) {
        return level == null || ConstructionEditLedger.get(level).reserves(cells);
    }

    public static boolean hasReservation(ServerLevel level, UUID areaId) {
        return level != null && areaId != null && ConstructionEditLedger.get(level).contains(areaId);
    }

    /**
     * Call after startBlueprint, before assignment/payment. Pass the exact preflight solid + clearance
     * cells (at most 65,536), including all native targets. Reservations never authorize excavation.
     * A false result must abort the handoff. Missing older draft reservation recipes pause on reload.
     */
    public static boolean protect(ServerPlayer owner, Mob builder, Entity area, Collection<BlockPos> reservedCells) {
        if (owner == null || builder == null || area == null || !(area.level() instanceof ServerLevel level)
                || area.getPersistentData().contains(KEY)) return false;
        if (!(area instanceof ProtectedBuildArea))
            return pause(area, "Protected construction requires the sealed native marker type");
        String capabilityProblem = availabilityProblem();
        if (capabilityProblem != null) return pause(area, capabilityProblem);
        try {
            if (!WallBuilderAccess.install(builder)) return pause(area, "Paused: native protection hook unavailable");
            var plan = AcceptedConstructionPlan.capture(area);
            var reservation = AcceptedConstructionReservation.capture(level, plan, reservedCells);
            var point = SiegeCore.point(owner.server, SiegeCore.key(owner));
            if (point == null || !owner.getUUID().equals(WorkersBridge.readOwner(area))
                    || !owner.getUUID().equals(WorkersBridge.readWorkerOwner(builder))) return false;
            Map<BlockPos, BlockState> before = new HashMap<>();
            for (BlockPos pos : plan.cells.keySet()) {
                if (!level.hasChunkAt(pos)) return pause(area, "Paused: the planned site is not fully loaded");
                BlockState state = level.getBlockState(pos);
                if (!initialCellSafe(state, plan.cells.get(pos)) || level.getBlockEntity(pos) != null)
                    return pause(area, "Paused: protected blocks or paired plants need manual clearance");
                before.put(pos, state);
            }
            Snapshot snapshot = new Snapshot(plan, reservation, Map.copyOf(before), new HashSet<>(), new HashSet<>(),
                    owner.getUUID(), builder.getUUID(), SiegeCore.key(owner), point.pos().immutable());
            before.forEach((pos, state) -> { if (state.equals(plan.cells.get(pos))) snapshot.completed.add(pos.asLong()); });
            String problem = worldProblem(level, builder, area, snapshot, snapshot.plan.cells.keySet(), false);
            if (problem != null) return pause(area, problem);
            var ledger = ConstructionEditLedger.get(level);
            var workerData = builder.getPersistentData();
            if (workerData.hasUUID(PROTECTED_LINK)) {
                UUID previous = workerData.getUUID(PROTECTED_LINK);
                if (!ledger.retired(previous))
                    return pause(area, "Paused: the builder still has an active or unverified protected job");
                if (!retireBuilderAssociation(builder, previous))
                    return pause(area, "Paused: the builder's previous canceled job cannot be detached safely");
                ledger.acknowledgeRetirement(previous);
            }
            if (ledger.reserves(reservation.cells))
                return pause(area, "Paused: another protected job reserves this footprint or headroom");
            if (!ledger.register(area.getUUID(), reservation.cells))
                return pause(area, "Paused: protected-site ledger is full or unavailable");
            area.getPersistentData().put(KEY, save(snapshot));
            area.getPersistentData().putBoolean(PAID, false);
            builder.getPersistentData().putUUID(PROTECTED_LINK, area.getUUID());
            builder.getPersistentData().putUUID(PROTECTED_GENERATION, ledger.generation());
            CACHE.put(area, snapshot);
            pause(area, "Paused: commission not completed");
            return true;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return pause(area, "Paused: native construction protection is unavailable");
        }
    }

    /** Call only after successful Treasury payment; retries never charge or consume materials. */
    public static boolean activate(Entity area) {
        if (!protectedArea(area)) return false;
        area.getPersistentData().putBoolean(PAID, true);
        area.getPersistentData().remove(STATUS);
        return true;
    }

    /**
     * Called before the living AI tick. Only inability to install the guard cancels
     * a living tick; a normally guarded pause leaves physics, food and sleep alone.
     * This also catches a second native builder discovering the protected area.
     */
    public static boolean beforeWorkerTick(Mob builder) {
        if (!WorkersBridge.isBuilder(builder) || !(builder.level() instanceof ServerLevel level)) return true;
        var data = builder.getPersistentData();
        if (data.hasUUID(PROTECTED_LINK)) {
            UUID receipt = data.getUUID(PROTECTED_LINK);
            // Protected construction is Overworld-only. A transferred worker
            // in another dimension must consult that original durable index.
            var ledger = ConstructionEditLedger.get(level.getServer().overworld());
            if (ledger.retired(receipt)) {
                if (!retireBuilderAssociation(builder, receipt)) return false;
                ledger.acknowledgeRetirement(receipt);
            }
        }
        Entity area = currentArea(builder);
        if (!protectedArea(area)) return true;
        if (WallBuilderAccess.install(builder)) {
            if (area instanceof ProtectedBuildArea protectedArea && !protectedArea.nativeQueuesReady()
                    && prepareLoadedArea(area)) WallBuilderAccess.prepareProtectedHandoff(builder, area);
            return true;
        }
        return pause(area, "Paused: native protection hook unavailable");
    }

    /** Works for transferred builders too; only exact old references/metadata can be cleared. */
    static boolean retireBuilderAssociation(Mob builder, UUID areaId) {
        if (!WorkersBridge.detachBuildAreaReference(builder, areaId)) return false;
        com.devfarinsky.siegeoverhaul.core.PlayerFortificationJobs.unlink(builder, areaId);
        var data = builder.getPersistentData();
        if (data.hasUUID(PROTECTED_LINK) && areaId.equals(data.getUUID(PROTECTED_LINK))) {
            data.remove(PROTECTED_LINK); data.remove(PROTECTED_GENERATION);
        }
        return true;
    }

    /** Called directly at the wrapper's native tick boundary, including after resupply/reload. */
    public static boolean beforeNativeTick(Mob builder, Goal nativeGoal) {
        Entity area = currentArea(builder);
        if (!protectedArea(area)) return true;
        if (!(builder.level() instanceof ServerLevel level)) return false;
        if (!area.getPersistentData().getBoolean(PAID)) return pause(area, "Paused: commission not completed");
        String capabilityProblem = availabilityProblem();
        if (capabilityProblem != null) return pause(area, capabilityProblem);
        if (area instanceof ProtectedBuildArea protectedArea && !protectedArea.nativeQueuesReady())
            return pause(area, "Paused: load the complete construction footprint to resume");
        try {
            Snapshot snapshot = snapshot(area);
            if (!snapshot.builder.equals(builder.getUUID())) return pause(area, "Paused: another builder selected this reserved job");
            if (builder.tickCount % 5 == 0 && !snapshot.plan.matches(area))
                return pause(area, "Paused: accepted blueprint or marker position changed");
            if (!Boolean.FALSE.equals(AcceptedConstructionPlan.call(area, "getFreeArea")))
                return pause(area, "Paused: whole-area clearing is not permitted");
            Object state = nativeGoal.getClass().getField("state").get(nativeGoal);
            if (state != null && (!(state instanceof Enum<?> e) || !STATES.contains(e.name())))
                return pause(area, "Paused: unsupported native construction state");
            if (state instanceof Enum<?> e && (e.name().equals("FREE_AREA") || e.name().equals("PREPARE_FREE_AREA")))
                return pause(area, "Paused: whole-area clearing is not permitted");
            // The audited native goal can mutate only every fifth tick. Other
            // ticks keep its look/availability updates without an O(plan) scan.
            if (builder.tickCount % 5 != 0) return true;
            String problem = nativeStacksProblem(area, nativeGoal, snapshot.plan);
            Set<BlockPos> candidates = state instanceof Enum<?> e && e.name().equals("DONE")
                    ? snapshot.plan.cells.keySet() : mutationCells(nativeGoal, state);
            if (problem == null) problem = worldProblem(level, builder, area, snapshot, candidates, true);
            if (problem == null && state instanceof Enum<?> e && e.name().equals("PREPARE_BREAK_BLOCKS")
                    && !scanChunksLoaded(level, snapshot.plan))
                problem = "Paused: native scan envelope contains unloaded terrain";
            // Native BREAK_BLOCKS ignores the desired state: a stale scan target
            // that has become the finished wall must never be mined.
            if (problem == null && state instanceof Enum<?> e && e.name().equals("BREAK_BLOCKS")) {
                Object target = nativeGoal.getClass().getField("blockPos").get(nativeGoal);
                if (target instanceof BlockPos pos && !level.getBlockState(pos).isAir()
                        && (!clearablePlant(level.getBlockState(pos))
                            || !level.getBlockState(pos).equals(snapshot.before.get(pos))
                            || snapshot.completed.contains(pos.asLong()) || snapshot.cleared.contains(pos.asLong())))
                    problem = "Paused: a mining target changed after the native scan";
            }
            if (problem == null && state instanceof Enum<?> e && e.name().equals("DONE")
                    && snapshot.plan.cells.entrySet().stream().anyMatch(cell -> !level.getBlockState(cell.getKey()).equals(cell.getValue())))
                problem = "Paused: native job ended before every accepted block was placed";
            if (problem != null) return pause(area, problem);
            if (state instanceof Enum<?> e && e.name().equals("DONE") && area instanceof ProtectedBuildArea protectedArea)
                protectedArea.verifyCompletion();
            area.getPersistentData().remove(STATUS);
            return true;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return pause(area, "Paused: native construction state cannot be verified");
        }
    }

    /** Record exact native progress so later player removal is never treated as fresh empty terrain. */
    public static void afterNativeTick(Mob builder, Entity priorArea, Set<BlockPos> mutationCells) {
        if (!protectedArea(priorArea) || !(builder.level() instanceof ServerLevel level)) return;
        try {
            Snapshot snapshot = snapshot(priorArea);
            boolean changed = false;
            for (BlockPos pos : mutationCells) {
                if (!level.hasChunkAt(pos) || !snapshot.plan.cells.containsKey(pos)) continue;
                BlockState now = level.getBlockState(pos);
                if (now.equals(snapshot.plan.cells.get(pos))) changed |= snapshot.completed.add(pos.asLong());
                else if (now.isAir() && !snapshot.before.get(pos).isAir()) changed |= snapshot.cleared.add(pos.asLong());
            }
            if (priorArea instanceof ProtectedBuildArea protectedArea && protectedArea.isDone())
                retireBuilderAssociation(builder, priorArea.getUUID());
            if (changed) {
                CompoundTag tag = priorArea.getPersistentData().getCompound(KEY);
                tag.putLongArray("Completed", snapshot.completed.stream().mapToLong(Long::longValue).toArray());
                tag.putLongArray("Cleared", snapshot.cleared.stream().mapToLong(Long::longValue).toArray());
            }
        } catch (RuntimeException unavailable) { pause(priorArea, "Paused: native progress cannot be verified"); }
    }

    public static String status(Entity area) {
        if (area == null) return "";
        String value = area.getPersistentData().getString(STATUS);
        return value.substring(0, Math.min(160, value.length()));
    }

    public static Entity currentArea(Mob builder) {
        try {
            Object value = builder.getClass().getField("currentBuildArea").get(builder);
            return value instanceof Entity entity ? entity : null;
        } catch (ReflectiveOperationException | RuntimeException unavailable) { return null; }
    }

    private static boolean protectedArea(Entity area) {
        return area instanceof ProtectedBuildArea || area != null && area.getPersistentData() != null
                && area.getPersistentData().contains(KEY);
    }

    private static boolean pause(Entity area, String reason) {
        if (area != null && area.getPersistentData() != null) area.getPersistentData().putString(STATUS, reason);
        return false;
    }

    static boolean initialCellSafe(BlockState current, BlockState target) {
        return current != null && target != null && !current.hasBlockEntity() && current.getFluidState().isEmpty()
                && (current.isAir() || current.equals(target) || clearablePlant(current));
    }

    private static boolean clearablePlant(BlockState state) {
        // Paired plants can destroy another cell through neighbor updates. No such
        // indirect clearing is authorized by a one-cell construction snapshot.
        return CampVegetation.plant(state) && !(state.getBlock() instanceof DoublePlantBlock)
                && !state.is(Blocks.WITHER_ROSE)
                && "minecraft".equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace());
    }

    static boolean currentCellSafe(BlockState initial, BlockState target, BlockState current,
                                   boolean completed, boolean cleared) {
        if (initial == null || !initialCellSafe(current, target)) return false;
        if (completed) return current.equals(target);
        if (current.equals(target) || current.isAir()) return true;
        return !cleared && clearablePlant(initial) && current.equals(initial);
    }

    private static String worldProblem(ServerLevel level, Mob builder, Entity area, Snapshot snapshot,
                                       Set<BlockPos> candidates, boolean requireLedger) {
        String permissions = NativeConstructionPolicy.problem(level, builder, area, snapshot.owner,
                snapshot.coreKey, snapshot.corePos, snapshot.reservation.cells);
        if (permissions != null) return permissions;
        String clearance = snapshot.reservation.problem(level);
        if (clearance != null) return clearance;
        if (requireLedger) {
            var ledger = ConstructionEditLedger.get(level);
            if (!ledger.matches(area.getUUID(), snapshot.reservation.cells)) return "Paused: protected-site history is unavailable";
            if (ledger.edited(area.getUUID())) return "Paused: this site was edited; commission a new reviewed plan";
        }
        for (BlockPos pos : candidates) {
            BlockState target = snapshot.plan.cells.get(pos);
            if (target == null) return "Paused: native target is outside the accepted plan";
            BlockState current = level.getBlockState(pos);
            if (level.getBlockEntity(pos) != null || !currentCellSafe(snapshot.before.get(pos), target, current,
                    snapshot.completed.contains(pos.asLong()), snapshot.cleared.contains(pos.asLong())))
                return "Paused: existing or changed blocks are protected at " + pos.toShortString();
            if (!current.equals(target)) {
                String neighborhood = neighborhoodProblem(level, pos);
                if (neighborhood != null) return neighborhood;
            }
            if (!current.equals(target) && !level.getEntities((Entity) null, new AABB(pos),
                    entity -> blocksPlacement(entity) && entity != area).isEmpty())
                return "Paused: move entities out of the planned blocks";
        }
        return null;
    }

    static String neighborhoodProblem(ServerLevel level, BlockPos target) {
        // Conductors can query a second neighbor ring while resolving power.
        // Validate that bounded envelope before any state/signal read.
        java.util.List<BlockPos> neighbors = new java.util.ArrayList<>(24);
        for (int x = -2; x <= 2; x++) for (int y = -2; y <= 2; y++) for (int z = -2; z <= 2; z++) {
            int distance = Math.abs(x) + Math.abs(y) + Math.abs(z);
            if (distance == 0 || distance > 2) continue;
            BlockPos pos = target.offset(x, y, z);
            if (!level.hasChunkAt(pos)) return "Paused: placement neighbors must be loaded";
            neighbors.add(pos);
        }
        for (BlockPos neighbor : neighbors) {
            if (!stableNeighbor(level.getBlockState(neighbor)) || level.getBlockEntity(neighbor) != null)
                return "Paused: a reactive or protected neighboring block needs manual review";
        }
        if (level.hasNeighborSignal(target)) return "Paused: powered construction sites need manual review";
        return null;
    }

    static boolean stableNeighbor(BlockState state) {
        if (state == null || state.hasBlockEntity() || !state.getFluidState().isEmpty()) return false;
        if (state.isAir()) return true;
        // Closed vanilla allowlist. No material tag or generic modded block is
        // accepted as proof that neighbor updates are harmless.
        return state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.STONE_BRICKS)
                || state.is(Blocks.OAK_PLANKS) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL)
                || state.is(Blocks.MYCELIUM) || state.is(Blocks.GRANITE) || state.is(Blocks.DIORITE)
                || state.is(Blocks.ANDESITE) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.TUFF)
                || state.is(Blocks.CALCITE) || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE)
                || state.is(Blocks.BEDROCK) || clearablePlant(state);
    }

    static boolean blocksPlacement(Entity entity) {
        return entity.isAlive() && !(entity instanceof net.minecraft.world.entity.item.ItemEntity)
                && !(entity instanceof net.minecraft.world.entity.ExperienceOrb);
    }

    /** The public source mutates at most one primary full-block cell in a tick. */
    public static Set<BlockPos> mutationCells(Goal nativeGoal) {
        try { return mutationCells(nativeGoal, nativeGoal.getClass().getField("state").get(nativeGoal)); }
        catch (ReflectiveOperationException | RuntimeException unavailable) { return Set.of(); }
    }

    private static Set<BlockPos> mutationCells(Object nativeGoal, Object state) throws ReflectiveOperationException {
        if (!(state instanceof Enum<?> e) || (!e.name().equals("BREAK_BLOCKS") && !e.name().equals("PLACE_BLOCKS")))
            return Set.of();
        Object target = nativeGoal.getClass().getField("blockPos").get(nativeGoal);
        if (target instanceof BlockPos pos) return Set.of(pos);
        if (e.name().equals("PLACE_BLOCKS")) {
            Object queue = nativeGoal.getClass().getField("stackToPlace").get(nativeGoal);
            if (queue instanceof java.util.Stack<?> stack && !stack.isEmpty()) {
                if (!(stack.peek() instanceof BlockPos pos)) throw new IllegalArgumentException("Unknown target");
                return Set.of(pos);
            }
        }
        return Set.of();
    }

    private static boolean scanChunksLoaded(ServerLevel level, AcceptedConstructionPlan plan) {
        BlockPos end = plan.origin.relative(plan.facing, plan.depth - 1)
                .relative(plan.facing.getClockWise(), plan.width - 1);
        for (int x = Math.min(plan.origin.getX(), end.getX()) >> 4; x <= Math.max(plan.origin.getX(), end.getX()) >> 4; x++)
            for (int z = Math.min(plan.origin.getZ(), end.getZ()) >> 4; z <= Math.max(plan.origin.getZ(), end.getZ()) >> 4; z++)
                if (!level.hasChunkAt(new BlockPos(x << 4, plan.origin.getY(), z << 4))) return false;
        return true;
    }

    /** Check every native target source, not just its first break scan. */
    static String nativeStacksProblem(Object area, Object nativeGoal, AcceptedConstructionPlan plan)
            throws ReflectiveOperationException {
        for (String name : new String[]{"stackToPlace", "stackToPlaceMultiBlock"}) {
            Object value = area.getClass().getField(name).get(area);
            if (!(value instanceof Collection<?> cells) || cells.size() > plan.cells.size())
                return "Paused: unsupported native placement queue";
            // Current approved full-block templates must never acquire a secondary write path.
            if (name.equals("stackToPlaceMultiBlock") && !cells.isEmpty()) return "Paused: multipart placement is unsupported";
            for (Object cell : cells) {
                Object pos = AcceptedConstructionPlan.call(cell, "getPos");
                Object state = AcceptedConstructionPlan.call(cell, "getState");
                if (!(pos instanceof BlockPos p) || !state.equals(plan.cells.get(p)))
                    return "Paused: native placement queue changed";
            }
        }
        for (String name : new String[]{"stackToBreak", "stackToFree"}) {
            Object value = area.getClass().getField(name).get(area);
            if (!(value instanceof Collection<?> cells) || cells.size() > plan.cells.size())
                return "Paused: unsupported native clearing queue";
            if (name.equals("stackToFree") && !cells.isEmpty()) return "Paused: whole-area clearing is not permitted";
            for (Object cell : cells) if (!(cell instanceof BlockPos pos) || !plan.cells.containsKey(pos))
                return "Paused: native clearing queue escaped the accepted plan";
        }
        for (String name : new String[]{"stackToPlace", "stackToBreak", "stackToFree"}) {
            Object value = nativeGoal.getClass().getField(name).get(nativeGoal);
            if (value == null) continue;
            if (!(value instanceof java.util.Stack<?> cells) || cells.size() > plan.cells.size())
                return "Paused: unsupported native job queue";
            if (name.equals("stackToFree") && !cells.isEmpty()) return "Paused: whole-area clearing is not permitted";
            for (Object cell : cells) if (!(cell instanceof BlockPos pos) || !plan.cells.containsKey(pos))
                return "Paused: native job target escaped the accepted plan";
        }
        Object target = nativeGoal.getClass().getField("blockPos").get(nativeGoal);
        if (target != null && (!(target instanceof BlockPos pos) || !plan.cells.containsKey(pos)))
            return "Paused: native job target escaped the accepted plan";
        return null;
    }

    private static CompoundTag save(Snapshot snapshot) {
        CompoundTag tag = snapshot.plan.save();
        tag.putUUID("Owner", snapshot.owner); tag.putUUID("Builder", snapshot.builder);
        tag.putString("CoreKey", snapshot.coreKey); tag.putLong("CorePos", snapshot.corePos.asLong());
        ListTag initial = new ListTag();
        snapshot.before.forEach((pos, state) -> {
            CompoundTag cell = new CompoundTag(); cell.putLong("Pos", pos.asLong());
            cell.put("State", NbtUtils.writeBlockState(state)); initial.add(cell);
        });
        tag.put("Before", initial);
        tag.put("Reservation", snapshot.reservation.save());
        tag.putLongArray("Completed", snapshot.completed.stream().mapToLong(Long::longValue).toArray());
        tag.putLongArray("Cleared", snapshot.cleared.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }

    private static Snapshot snapshot(Entity area) {
        Snapshot cached = CACHE.get(area);
        if (cached != null) return cached;
        CompoundTag tag = area.getPersistentData().getCompound(KEY);
        var plan = AcceptedConstructionPlan.load(tag);
        var reservation = AcceptedConstructionReservation.load(plan, tag.getCompound("Reservation"));
        if (!tag.hasUUID("Owner") || !tag.hasUUID("Builder") || tag.getString("CoreKey").isBlank()
                || !tag.contains("Completed", Tag.TAG_LONG_ARRAY) || !tag.contains("Cleared", Tag.TAG_LONG_ARRAY))
            throw new IllegalArgumentException("Incomplete protection context");
        Map<BlockPos, BlockState> before = new HashMap<>();
        ListTag list = tag.getList("Before", Tag.TAG_COMPOUND);
        if (list.size() != plan.cells.size()) throw new IllegalArgumentException("Incomplete initial state");
        for (Tag entry : list) {
            CompoundTag cell = (CompoundTag) entry;
            BlockPos pos = BlockPos.of(cell.getLong("Pos"));
            BlockState state = NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),
                    cell.getCompound("State"));
            if (!initialCellSafe(state, plan.cells.get(pos)) || before.putIfAbsent(pos, state) != null)
                throw new IllegalArgumentException("Invalid initial state");
        }
        Set<Long> completed = readPositions(tag.getLongArray("Completed"), plan),
                cleared = readPositions(tag.getLongArray("Cleared"), plan);
        before.forEach((pos, state) -> { if (state.equals(plan.cells.get(pos))) completed.add(pos.asLong()); });
        Snapshot result = new Snapshot(plan, reservation, Map.copyOf(before), completed, cleared, tag.getUUID("Owner"),
                tag.getUUID("Builder"), tag.getString("CoreKey"), BlockPos.of(tag.getLong("CorePos")));
        CACHE.put(area, result);
        return result;
    }

    private static Set<Long> readPositions(long[] cells, AcceptedConstructionPlan plan) {
        if (cells.length > plan.cells.size()) throw new IllegalArgumentException("Oversized progress history");
        Set<Long> result = new HashSet<>();
        for (long cell : cells) {
            if (!plan.cells.containsKey(BlockPos.of(cell)) || !result.add(cell))
                throw new IllegalArgumentException("Invalid progress history");
        }
        return result;
    }

    /** Read-only readiness check, then noncreative queue rebuild with no chunk loading. */
    public static boolean prepareLoadedArea(Entity area) {
        if (!(area instanceof ProtectedBuildArea protectedArea) || !(area.level() instanceof ServerLevel level)) return false;
        String capabilityProblem = availabilityProblem();
        if (capabilityProblem != null) return pause(area, capabilityProblem);
        if (protectedArea.nativeQueuesReady()) return true;
        try {
            Snapshot snapshot = snapshot(area);
            if (!snapshot.plan.matches(area) || !ConstructionEditLedger.get(level).matches(area.getUUID(), snapshot.reservation.cells)
                    || !Boolean.FALSE.equals(AcceptedConstructionPlan.call(area, "getFreeArea")))
                return pause(area, "Paused: saved construction needs a new reviewed plan");
            if (!scanChunksLoaded(level, snapshot.plan)) return pause(area, "Paused: load the complete construction footprint to resume");
            String clearance = snapshot.reservation.problem(level);
            if (clearance != null) return pause(area, clearance);
            protectedArea.rebuildAcceptedQueues();
            return true;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return pause(area, "Paused: saved construction cannot be verified");
        }
    }

    public static void areaJoined(EntityJoinLevelEvent event) {
        Entity area = event.getEntity();
        if (event.loadedFromDisk() && event.getLevel() instanceof ServerLevel && protectedArea(area))
            prepareLoadedArea(area);
    }

    public static void blockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var ledger = ConstructionEditLedger.get(level);
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multi)
            multi.getReplacedBlockSnapshots().forEach(snapshot -> ledger.record(snapshot.getPos()));
        else ledger.record(event.getPos());
    }

    public static void blockBroken(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) ConstructionEditLedger.get(level).record(event.getPos());
    }

    public static void areaRemoved(EntityLeaveLevelEvent event) {
        Entity area = event.getEntity();
        CACHE.remove(area);
        if (!(event.getLevel() instanceof ServerLevel level) || area.getRemovalReason() == null
                || !area.getRemovalReason().shouldDestroy()) return;
        if (area instanceof Mob worker && worker.getPersistentData().hasUUID(PROTECTED_LINK)) {
            ConstructionEditLedger.get(level.getServer().overworld())
                    .builderDestroyed(worker.getPersistentData().getUUID(PROTECTED_LINK));
        }
        if (area instanceof ProtectedBuildArea protectedArea && protectedArea.retirementHandled()) return;
        if (protectedArea(area)) ConstructionEditLedger.get(level).retire(area.getUUID(),
                area instanceof ProtectedBuildArea protectedArea && protectedArea.isDone());
    }
}
