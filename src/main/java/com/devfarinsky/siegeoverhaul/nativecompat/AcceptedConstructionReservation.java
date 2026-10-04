package com.devfarinsky.siegeoverhaul.nativecompat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Separate immutable occupancy contract; reservation never authorizes native block mutation. */
final class AcceptedConstructionReservation {
    static final int VERSION = 1, MAX_CELLS = AcceptedConstructionPlan.MAX_CELLS;
    final Set<BlockPos> cells;
    final Map<BlockPos, BlockState> clearance;

    private AcceptedConstructionReservation(Set<BlockPos> cells, Map<BlockPos, BlockState> clearance) {
        this.cells = Set.copyOf(cells);
        this.clearance = Map.copyOf(clearance);
    }

    static AcceptedConstructionReservation capture(ServerLevel level, AcceptedConstructionPlan plan,
                                                     Collection<BlockPos> reservedCells) {
        Set<BlockPos> cells = validate(plan, reservedCells);
        // Check every chunk before reading any clearance state. No reservation scan may force loads.
        if (cells.stream().anyMatch(pos -> !level.hasChunkAt(pos)))
            throw new IllegalArgumentException("Reserved footprint is not fully loaded");
        Map<BlockPos, BlockState> clearance = new HashMap<>();
        for (BlockPos pos : cells) if (!plan.cells.containsKey(pos)) {
            BlockState state = level.getBlockState(pos);
            if (!initialClearanceSafe(state) || level.getBlockEntity(pos) != null)
                throw new IllegalArgumentException("Reserved clearance is obstructed");
            clearance.put(pos, state);
        }
        return new AcceptedConstructionReservation(cells, clearance);
    }

    static Set<BlockPos> validate(AcceptedConstructionPlan plan, Collection<BlockPos> positions) {
        if (plan == null || positions == null || positions.isEmpty() || positions.size() > MAX_CELLS)
            throw new IllegalArgumentException("Unsupported reservation size");
        Set<BlockPos> cells = new HashSet<>();
        for (BlockPos pos : positions) {
            if (pos == null || !plan.withinEnvelope(pos))
                throw new IllegalArgumentException("Reservation escaped accepted envelope");
            cells.add(pos.immutable());
        }
        if (!cells.containsAll(plan.cells.keySet()))
            throw new IllegalArgumentException("Reservation omits planned structural cells");
        return Set.copyOf(cells);
    }

    static boolean initialClearanceSafe(BlockState state) {
        return NativeConstructionGuard.initialCellSafe(state, Blocks.AIR.defaultBlockState());
    }

    String problem(ServerLevel level) {
        if (cells.stream().anyMatch(pos -> !level.hasChunkAt(pos)))
            return "Paused: load the complete reserved footprint and headroom";
        for (var entry : clearance.entrySet()) {
            if (!entry.getValue().equals(level.getBlockState(entry.getKey())) || level.getBlockEntity(entry.getKey()) != null)
                return "Paused: reserved walkway or headroom changed at " + entry.getKey().toShortString();
        }
        return null;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", VERSION);
        tag.putLongArray("Cells", cells.stream().mapToLong(BlockPos::asLong).toArray());
        ListTag initial = new ListTag();
        clearance.forEach((pos, state) -> {
            CompoundTag cell = new CompoundTag(); cell.putLong("Pos", pos.asLong());
            cell.put("State", NbtUtils.writeBlockState(state)); initial.add(cell);
        });
        tag.put("Clearance", initial);
        return tag;
    }

    static AcceptedConstructionReservation load(AcceptedConstructionPlan plan, CompoundTag tag) {
        if (tag.getInt("Version") != VERSION || !tag.contains("Cells", Tag.TAG_LONG_ARRAY)
                || !tag.contains("Clearance", Tag.TAG_LIST))
            throw new IllegalArgumentException("Explicit reservation contract is missing");
        long[] saved = tag.getLongArray("Cells");
        if (saved.length == 0 || saved.length > MAX_CELLS)
            throw new IllegalArgumentException("Unsupported saved reservation size");
        Set<BlockPos> positions = new HashSet<>();
        for (long pos : saved) if (!positions.add(BlockPos.of(pos)))
            throw new IllegalArgumentException("Duplicate reservation cell");
        Set<BlockPos> cells = validate(plan, positions);
        ListTag initial = tag.getList("Clearance", Tag.TAG_COMPOUND);
        if (initial.size() != cells.size() - plan.cells.size())
            throw new IllegalArgumentException("Incomplete reserved clearance snapshot");
        Map<BlockPos, BlockState> clearance = new HashMap<>();
        for (Tag entry : initial) {
            CompoundTag cell = (CompoundTag) entry;
            if (!cell.contains("Pos", Tag.TAG_LONG) || !cell.contains("State", Tag.TAG_COMPOUND))
                throw new IllegalArgumentException("Missing reserved clearance state");
            BlockPos pos = BlockPos.of(cell.getLong("Pos"));
            CompoundTag stateTag = cell.getCompound("State");
            BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), stateTag);
            // NbtUtils defaults unknown/missing block IDs to air. Do not accept that as a valid receipt.
            if (!NbtUtils.writeBlockState(state).equals(stateTag) || !initialClearanceSafe(state)
                    || !cells.contains(pos) || plan.cells.containsKey(pos) || clearance.putIfAbsent(pos, state) != null)
                throw new IllegalArgumentException("Invalid reserved clearance state");
        }
        return new AcceptedConstructionReservation(cells, clearance);
    }
}
