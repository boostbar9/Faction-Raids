package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Fresh, read-only checks for the literal empty passages and exact observed approach cells.
 * Does not load chunks, reserve cells, clear vegetation, create targets, or authorize construction.
 * Supplied permissions must be fresh read-only predicates, including access/claim protection and
 * reservation policy appropriate to the call. Outside-claim access is not building permission.
 * These checks complement, never replace, the native target contract and project edit ledger.
 */
public final class PerimeterGateAccess {
    private static final Set<Block> STABLE_FLOORS = Set.of(
            Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT,
            Blocks.PODZOL, Blocks.MYCELIUM, Blocks.STONE, Blocks.GRANITE, Blocks.DIORITE,
            Blocks.ANDESITE, Blocks.DEEPSLATE, Blocks.TUFF, Blocks.CALCITE, Blocks.BEDROCK,
            Blocks.SNOW_BLOCK, Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.OAK_PLANKS);
    private static final String UNAVAILABLE = "Entrance access could not be verified; review the loaded terrain again.";

    private PerimeterGateAccess() {}

    /** No partially verified contract escapes a failed snapshot. */
    public record Snapshot(PerimeterGateContract contract, String problem) {
        public boolean ready() { return contract != null && problem == null; }
    }

    /** Indexes immutable plan metadata only. Every invocation reads current terrain and permissions. */
    public static PerimeterGateLayout.PassageValidator validator(ServerLevel level, ServerPlayer player,
            PerimeterBlueprint.Plan originalWall, Predicate<BlockPos> permitted) {
        final Map<Long, PerimeterBlueprint.Column> columns;
        try { columns = columns(originalWall); }
        catch (RuntimeException invalid) { return (feet, region) -> UNAVAILABLE; }
        return (feet, region) -> columnProblem(level, player, originalWall, columns, feet, region, permitted);
    }

    /** One fresh column, including all three headroom cells and its exact floor. */
    public static String columnProblem(ServerLevel level, ServerPlayer player, PerimeterBlueprint.Plan originalWall,
            BlockPos feet, PerimeterGateLayout.Region region, Predicate<BlockPos> permitted) {
        return validator(level, player, originalWall, permitted).problem(feet, region);
    }

    private static String columnProblem(ServerLevel level, ServerPlayer player, PerimeterBlueprint.Plan wall,
            Map<Long, PerimeterBlueprint.Column> columns, BlockPos feet, PerimeterGateLayout.Region region,
            Predicate<BlockPos> permitted) {
        try {
            actor(level, player, permitted);
            checkColumn(wall, columns, feet, region);
            for (int y = 0; y < PerimeterGateLayout.PASSAGE_HEIGHT; y++) {
                BlockPos cell = feet.above(y);
                air(read(level, player, cell, permitted), cell);
            }
            BlockPos floor = feet.below();
            BlockState state = read(level, player, floor, permitted);
            if (plannedFloor(wall, columns, feet, region)) foundation(state, floor);
            else floor(state, floor);
            return null;
        } catch (Blocked blocked) { return blocked.getMessage(); }
        catch (RuntimeException unavailable) { return UNAVAILABLE; }
    }

    /**
     * Rechecks selected wall passages, then captures exact approach air/floors and natural passage
     * floors. Public layout records undergo the full pure contract validation before any world read.
     * Planned dirt floors remain ordinary native targets and are never added to observations.
     */
    public static Snapshot snapshot(ServerLevel level, ServerPlayer player, Set<ChunkPos> territory,
            PerimeterBlueprint.Plan originalWall, PerimeterGateLayout.Layout layout, Predicate<BlockPos> permitted) {
        try {
            actor(level, player, permitted);
            Map<Long, PerimeterBlueprint.Column> columns = columns(originalWall);
            if (layout == null || !layout.valid() || layout.gates().size() > PerimeterGateContract.MAX_GATES
                    || layout.approachClearance().size() > PerimeterGateContract.MAX_OBSERVATIONS
                    || layout.approachFooting().size() > PerimeterGateContract.MAX_OBSERVATIONS
                    || layout.passageClearance().size() > PerimeterStageLayout.MAX_RESERVED)
                throw new Blocked("A complete bounded entrance layout is required.");
            // Dummy states establish only exact roles/geometry. They are never returned or accepted
            // as terrain evidence; the real contract below contains only freshly read states.
            Map<Long, BlockState> roles = new HashMap<>();
            layout.approachClearance().forEach(cell -> roles.put(cell, Blocks.AIR.defaultBlockState()));
            layout.approachFooting().forEach(cell -> roles.put(cell, Blocks.DIRT.defaultBlockState()));
            for (var column : columns.values())
                if (column.supportDepth() == 0 && layout.passageClearance().contains(column.base().asLong()))
                    roles.put(column.base().below().asLong(), Blocks.DIRT.defaultBlockState());
            PerimeterGateContract geometry = PerimeterGateContract.create(territory, originalWall, layout, roles);
            Map<Long, BlockState> before = new LinkedHashMap<>();
            for (long cell : geometry.observations().keySet()) {
                BlockPos pos = BlockPos.of(cell);
                BlockState state = read(level, player, pos, permitted);
                if (geometry.airObservations().contains(cell)) air(state, pos); else floor(state, pos);
                before.put(cell, state);
            }
            // Observed natural floors were read above; only new planned floors need another check.
            // The passage headroom must be literal air even at an omitted, newly planned wall skin.
            for (var gate : geometry.gates()) for (int lane = -1; lane <= 1; lane++) for (int depth = -4; depth <= 0; depth++) {
                BlockPos feet = gate.outerCenter().relative(gate.facing().getClockWise(), lane).relative(gate.facing(), depth);
                for (int y = 0; y < PerimeterGateLayout.PASSAGE_HEIGHT; y++) {
                    BlockPos pos = feet.above(y);
                    air(read(level, player, pos, permitted), pos);
                }
                if (plannedFloor(originalWall, columns, feet, PerimeterGateLayout.Region.WALL)) {
                    BlockPos pos = feet.below();
                    foundation(read(level, player, pos, permitted), pos);
                }
            }
            return new Snapshot(PerimeterGateContract.create(territory, originalWall, layout, before), null);
        } catch (Blocked blocked) { return new Snapshot(null, blocked.getMessage()); }
        catch (RuntimeException unavailable) { return new Snapshot(null, UNAVAILABLE); }
    }

    /** Required at initial acceptance and final verification; never checks just the current stage. */
    public static String observationProblem(ServerLevel level, ServerPlayer player, PerimeterGateContract contract,
            Predicate<BlockPos> permitted) {
        try {
            if (contract == null) return UNAVAILABLE;
            return observationsProblem(level, player, contract, contract.observations(), permitted,
                    PerimeterGateContract.MAX_OBSERVATIONS);
        } catch (RuntimeException unavailable) { return UNAVAILABLE; }
    }

    /**
     * Active-stage optimization only: call for the stage's verified single component, including
     * before every activation/handoff. Future components are checked when activated. Initial and
     * final acceptance still require observationProblem for the whole project.
     */
    public static String componentObservationProblem(ServerLevel level, ServerPlayer player,
            PerimeterGateContract contract, int componentId, Predicate<BlockPos> permitted) {
        try {
            if (contract == null) return UNAVAILABLE;
            return observationsProblem(level, player, contract, contract.componentObservations(componentId),
                    permitted, PerimeterGateContract.MAX_COMPONENT_OBSERVATIONS);
        } catch (RuntimeException unavailable) { return UNAVAILABLE; }
    }

    private static String observationsProblem(ServerLevel level, ServerPlayer player, PerimeterGateContract contract,
            Map<Long, BlockState> expected, Predicate<BlockPos> permitted, int limit) {
        try {
            actor(level, player, permitted);
            if (expected.isEmpty() || expected.size() > limit) throw new Blocked("Invalid entrance observation budget.");
            for (var entry : expected.entrySet()) {
                BlockPos pos = BlockPos.of(entry.getKey());
                BlockState state = read(level, player, pos, permitted);
                if (!entry.getValue().equals(state)) throw blocked("Entrance terrain changed; a fresh review is required", pos);
                if (contract.airObservations().contains(entry.getKey())) air(state, pos); else floor(state, pos);
            }
            return null;
        } catch (Blocked blocked) { return blocked.getMessage(); }
        catch (RuntimeException unavailable) { return UNAVAILABLE; }
    }

    private static void actor(ServerLevel level, ServerPlayer player, Predicate<BlockPos> permitted) {
        if (level == null || player == null || permitted == null || player.serverLevel() != level
                || !player.isAlive() || player.isSpectator() || !player.mayBuild())
            throw new Blocked("Entrance access needs the authorized player in this world.");
    }

    private static void available(ServerLevel level, ServerPlayer player, BlockPos pos, Predicate<BlockPos> permitted) {
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(pos) || !level.hasChunkAt(pos))
            throw blocked("Entrance terrain must be loaded and inside the build height and world border", pos);
        if (!level.mayInteract(player, pos) || !permitted.test(pos)) throw blocked("Entrance access is protected or reserved", pos);
    }

    private static BlockState read(ServerLevel level, ServerPlayer player, BlockPos pos, Predicate<BlockPos> permitted) {
        available(level, player, pos, permitted);
        BlockState state = level.getBlockState(pos);
        if (state == null || state.hasBlockEntity() || !state.getFluidState().isEmpty() || HirePlacement.dangerous(state))
            throw blocked("Entrances need dry, safe cells without block entities", pos);
        // A mismatched/stale block entity must not pass just because its BlockState appears safe.
        available(level, player, pos, permitted);
        if (level.getBlockEntity(pos) != null) throw blocked("A block entity occupies the entrance", pos);
        return state;
    }

    private static void air(BlockState state, BlockPos pos) {
        if (!(state.is(Blocks.AIR) || state.is(Blocks.CAVE_AIR) || state.is(Blocks.VOID_AIR))
                || !state.getCollisionShape(EmptyBlockGetter.INSTANCE, pos).isEmpty())
            throw blocked("Entrance headroom must already be empty; existing blocks and plants are preserved", pos);
    }

    private static void floor(BlockState state, BlockPos pos) {
        // Identity whitelist prevents modded, falling, openable, powered, support-dependent or
        // neighbor-dependent shapes from becoming unobserved dependencies. No neighbor reads.
        if (!STABLE_FLOORS.contains(state.getBlock()) || !state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, pos))
            throw blocked("Entrances need existing stable, full-block soil, stone or wall-material footing", pos);
    }

    private static void foundation(BlockState state, BlockPos pos) {
        // Native construction still checks the complete reviewed support/target contract later.
        // Do not infer permission to mine an existing block or clear a plant for this dirt target.
        if (!state.is(Blocks.DIRT)) air(state, pos);
    }

    private static Map<Long, PerimeterBlueprint.Column> columns(PerimeterBlueprint.Plan wall) {
        if (wall == null || !wall.valid() || wall.columns().isEmpty() || wall.columns().size() > PerimeterStageLayout.MAX_COLUMNS
                || wall.blocks().size() > PerimeterStageLayout.MAX_TARGETS
                || (long) wall.blocks().size() + wall.clearance().size() > PerimeterStageLayout.MAX_RESERVED)
            throw new Blocked("A complete bounded original wall is required.");
        Map<Long, PerimeterBlueprint.Column> result = new HashMap<>();
        for (var column : wall.columns()) {
            if (column == null || column.base() == null || column.supportDepth() < 0 || column.supportDepth() > 64
                    || result.putIfAbsent(PerimeterStageLayout.column(column.base().asLong()), column) != null)
                throw new Blocked("Invalid entrance wall columns.");
        }
        return Map.copyOf(result);
    }

    private static void checkColumn(PerimeterBlueprint.Plan wall, Map<Long, PerimeterBlueprint.Column> columns,
            BlockPos feet, PerimeterGateLayout.Region region) {
        if (feet == null || region == null || feet.getY() < -2047 || feet.getY() > 2045
                || Math.abs((long) feet.getX()) >= 30_000_000 || Math.abs((long) feet.getZ()) >= 30_000_000)
            throw new Blocked("Invalid entrance passage column.");
        var column = columns.get(PerimeterStageLayout.column(feet.asLong()));
        if (region == PerimeterGateLayout.Region.WALL) {
            if (column == null || !column.base().equals(feet)) throw blocked("Entrance is outside the reviewed wall", feet);
            for (int y = 0; y < PerimeterGateLayout.PASSAGE_HEIGHT; y++) {
                long cell = feet.above(y).asLong();
                if (!wall.blocks().containsKey(cell) && !wall.clearance().contains(cell))
                    throw blocked("Entrance is missing reviewed wall cells", feet);
            }
        } else if (column != null) throw blocked("An approach intersects a wall column", feet);
    }

    private static boolean plannedFloor(PerimeterBlueprint.Plan wall, Map<Long, PerimeterBlueprint.Column> columns,
            BlockPos feet, PerimeterGateLayout.Region region) {
        if (region != PerimeterGateLayout.Region.WALL) return false;
        var column = columns.get(PerimeterStageLayout.column(feet.asLong()));
        if (column == null || !column.base().equals(feet)) throw blocked("Missing reviewed passage column", feet);
        String target = wall.blocks().get(feet.below().asLong());
        if (column.supportDepth() > 0) {
            if (!"minecraft:dirt".equals(target)) throw blocked("Missing reviewed passage foundation", feet);
            return true;
        }
        if (target != null) throw blocked("Unexpected natural passage floor target", feet);
        return false;
    }

    private static Blocked blocked(String message, BlockPos pos) { return new Blocked(message + " at " + pos.toShortString() + "."); }
    private static final class Blocked extends IllegalArgumentException {
        private Blocked(String message) { super(message); }
    }
}
