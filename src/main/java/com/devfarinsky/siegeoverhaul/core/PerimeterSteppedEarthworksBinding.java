package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Pure bridge from the stepped physical proposal to the earthworks/wall assembly contracts.
 * It records exact observed states and material demand; it does not grant payment, claim, route
 * or Workers authority.
 */
public final class PerimeterSteppedEarthworksBinding {
    private PerimeterSteppedEarthworksBinding() {}

    public record Original(BlockState state, long editRevision) {
        public Original {
            Objects.requireNonNull(state);
            if (editRevision < 0) throw invalid("Unknown stepped observation edit revision");
        }
    }

    public interface Snapshot {
        Original original(PerimeterSteppedGeometry.Pos position);
    }

    public record Binding(List<PerimeterEarthworksManifest> fillRegions,
                          List<PerimeterEarthworksAssembly.WallCell> wallCells,
                          List<PerimeterEarthworksManifest.Observation> readOnly,
                          PerimeterEarthworksAssembly.Assembly assembly) {
        public Binding {
            fillRegions = List.copyOf(fillRegions);
            wallCells = List.copyOf(wallCells);
            readOnly = List.copyOf(readOnly);
            Objects.requireNonNull(assembly);
        }
    }

    public static Binding bind(PerimeterEarthworksAssembly.Scope scope, PerimeterSteppedGeometry.Draft draft,
                               Snapshot snapshot, int wallNativeStages) {
        Objects.requireNonNull(scope); Objects.requireNonNull(draft); Objects.requireNonNull(snapshot);
        if (!draft.feasible() || !PerimeterProject.digest(draft.digest()))
            throw invalid("Only a complete stepped geometry draft can be bound");
        Map<PerimeterSteppedGeometry.Pos, Original> originals = new HashMap<>();
        Map<FillColumn, List<PerimeterSteppedGeometry.Pos>> fills = new TreeMap<>();
        List<PerimeterEarthworksAssembly.WallCell> wall = new ArrayList<>();
        draft.targets().forEach((pos, target) -> {
            Original original = observed(scope, snapshot, originals, pos);
            BlockState targetState = state(target.block());
            if (target.phase() == PerimeterSteppedGeometry.Phase.FILL) {
                if (!targetState.equals(Blocks.DIRT.defaultBlockState()) || !original.state().isAir())
                    throw invalid("Stepped fill must bind exact observed air to dirt");
                fills.computeIfAbsent(new FillColumn(pos.x(), pos.z(), target.component()), ignored -> new ArrayList<>()).add(pos);
            } else {
                wall.add(new PerimeterEarthworksAssembly.WallCell(pos(pos), original.state(), targetState, original.editRevision()));
            }
        });
        List<PerimeterEarthworksManifest> regions = new ArrayList<>();
        for (FillGroup group : fillGroups(fills)) regions.add(fillRegion(scope, draft, snapshot, originals, group));
        List<PerimeterEarthworksManifest.Observation> readOnly = readOnly(scope, draft, snapshot, originals);
        var assembly = PerimeterEarthworksAssembly.assemble(scope, regions, wall, readOnly, wallNativeStages);
        return new Binding(regions, wall, readOnly, assembly);
    }

    private static List<PerimeterEarthworksManifest.Observation> readOnly(PerimeterEarthworksAssembly.Scope scope,
            PerimeterSteppedGeometry.Draft draft, Snapshot snapshot, Map<PerimeterSteppedGeometry.Pos, Original> originals) {
        Map<Long, PerimeterEarthworksManifest.Observation> result = new TreeMap<>();
        for (PerimeterSteppedGeometry.Pos pos : draft.clearance()) {
            if (draft.targets().containsKey(pos)) throw invalid("Clearance cannot overlap stepped mutation targets");
            Original original = observed(scope, snapshot, originals, pos);
            if (!original.state().isAir()) throw invalid("Stepped clearance must still be observed as air");
            put(result, pos, original, PerimeterEarthworksManifest.Role.PROTECTED_OCCUPANCY);
        }
        for (PerimeterSteppedGeometry.Pos pos : draft.readOnlyFooting()) {
            if (draft.targets().containsKey(pos)) throw invalid("Footing cannot overlap stepped mutation targets");
            put(result, pos, observed(scope, snapshot, originals, pos), PerimeterEarthworksManifest.Role.DEPENDENCY);
        }
        return List.copyOf(result.values());
    }

    private static PerimeterEarthworksManifest fillRegion(PerimeterEarthworksAssembly.Scope scope,
            PerimeterSteppedGeometry.Draft draft, Snapshot snapshot, Map<PerimeterSteppedGeometry.Pos, Original> originals,
            FillGroup group) {
        List<PerimeterSteppedGeometry.Pos> cells = new ArrayList<>(group.cells());
        cells.sort(java.util.Comparator.comparingInt(PerimeterSteppedGeometry.Pos::y)
                .thenComparing(PerimeterSteppedGeometry.Pos::compareTo));
        var header = new PerimeterEarthworksManifest.Header(scope.project(), scope.generation(), scope.owner(), scope.builder(),
                scope.dimension(), scope.faction(), scope.claimsDigest(), draft.digest(), "stepped-short-fill-v1",
                group.component(), group.planeY(), scope.minY(), scope.maxY(), scope.feeVersion(), scope.price());
        Map<Long, PerimeterEarthworksManifest.Observation> observations = new TreeMap<>();
        for (PerimeterSteppedGeometry.Pos fill : cells) {
            Original original = observed(scope, snapshot, originals, fill);
            if (!original.state().isAir()) throw invalid("Fill target changed before binding");
            put(observations, fill, original, PerimeterEarthworksManifest.Role.WORK);
        }
        for (PerimeterSteppedGeometry.Pos fill : cells) {
            for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 2; dy++) for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > 2) continue;
                PerimeterSteppedGeometry.Pos neighbor = new PerimeterSteppedGeometry.Pos(fill.x() + dx, fill.y() + dy, fill.z() + dz);
                put(observations, neighbor, observed(scope, snapshot, originals, neighbor), PerimeterEarthworksManifest.Role.DEPENDENCY);
            }
        }
        for (FillColumn column : group.columns()) {
            PerimeterSteppedGeometry.Pos support = new PerimeterSteppedGeometry.Pos(column.x(), column.lowestY() - 1, column.z());
            Original supportOriginal = observed(scope, snapshot, originals, support);
            if (supportOriginal.state().isAir()) throw invalid("Fill support must be an exact observed non-air footing");
            put(observations, support, supportOriginal, PerimeterEarthworksManifest.Role.DEPENDENCY);
        }
        List<PerimeterEarthworksManifest.Step> steps = new ArrayList<>();
        for (PerimeterSteppedGeometry.Pos fill : cells) steps.add(new PerimeterEarthworksManifest.Step(0,
                PerimeterEarthworksManifest.Kind.FILL, pos(fill), Blocks.AIR.defaultBlockState(),
                Blocks.DIRT.defaultBlockState(), null));
        return new PerimeterEarthworksManifest(header, new ArrayList<>(observations.values()), steps);
    }

    private static List<FillGroup> fillGroups(Map<FillColumn, List<PerimeterSteppedGeometry.Pos>> columns) {
        List<FillColumn> ordered = new ArrayList<>();
        Map<PerimeterSteppedGeometry.Pos, Integer> owner = new HashMap<>();
        for (var entry : columns.entrySet()) {
            List<PerimeterSteppedGeometry.Pos> cells = new ArrayList<>(entry.getValue());
            Collections.sort(cells);
            for (int i = 1; i < cells.size(); i++)
                if (cells.get(i).y() != cells.get(i - 1).y() + 1) throw invalid("Stepped fill column is not contiguous");
            FillColumn column = entry.getKey().withVertical(cells.get(0).y(), cells.get(cells.size() - 1).y() + 1, List.copyOf(cells));
            int index = ordered.size(); ordered.add(column);
            for (PerimeterSteppedGeometry.Pos cell : cells) owner.put(cell, index);
        }
        Disjoint groups = new Disjoint(ordered.size());
        for (int i = 0; i < ordered.size(); i++) for (PerimeterSteppedGeometry.Pos fill : ordered.get(i).cells()) {
            for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 2; dy++) for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > 2) continue;
                Integer other = owner.get(new PerimeterSteppedGeometry.Pos(fill.x() + dx, fill.y() + dy, fill.z() + dz));
                if (other == null || other == i) continue;
                FillColumn a = ordered.get(i), b = ordered.get(other);
                if (a.component() != b.component() || a.planeY() != b.planeY())
                    throw invalid("Nearby stepped fills need a separately scheduled transition manifest");
                groups.union(i, other);
            }
        }
        Map<Integer, List<FillColumn>> byRoot = new TreeMap<>();
        for (int i = 0; i < ordered.size(); i++) byRoot.computeIfAbsent(groups.find(i), ignored -> new ArrayList<>()).add(ordered.get(i));
        List<FillGroup> result = new ArrayList<>();
        for (List<FillColumn> group : byRoot.values()) {
            List<PerimeterSteppedGeometry.Pos> cells = new ArrayList<>();
            group.forEach(column -> cells.addAll(column.cells()));
            result.add(new FillGroup(group.get(0).component(), group.get(0).planeY(), List.copyOf(cells), List.copyOf(group)));
        }
        return result;
    }

    private static void put(Map<Long, PerimeterEarthworksManifest.Observation> observations,
            PerimeterSteppedGeometry.Pos pos, Original original, PerimeterEarthworksManifest.Role role) {
        long packed = pos(pos);
        var next = new PerimeterEarthworksManifest.Observation(packed, original.state(), role, original.editRevision());
        var old = observations.putIfAbsent(packed, next);
        if (old != null && (!old.original().equals(next.original()) || old.editRevision() != next.editRevision()))
            throw invalid("Conflicting exact stepped observation");
    }

    private static Original observed(PerimeterEarthworksAssembly.Scope scope, Snapshot snapshot,
            Map<PerimeterSteppedGeometry.Pos, Original> originals, PerimeterSteppedGeometry.Pos pos) {
        if (pos.y() < scope.minY() || pos.y() >= scope.maxY()) throw invalid("Stepped observation outside reviewed height bounds");
        return originals.computeIfAbsent(pos, key -> {
            Original original = snapshot.original(key);
            if (original == null) throw invalid("Missing exact stepped observation");
            return original;
        });
    }

    private static long pos(PerimeterSteppedGeometry.Pos pos) {
        return new BlockPos(pos.x(), pos.y(), pos.z()).asLong();
    }

    private static BlockState state(PerimeterSteppedGeometry.Block block) {
        return switch (block) {
            case COBBLESTONE -> Blocks.COBBLESTONE.defaultBlockState();
            case STONE_BRICKS -> Blocks.STONE_BRICKS.defaultBlockState();
            case OAK_PLANKS -> Blocks.OAK_PLANKS.defaultBlockState();
            case DIRT -> Blocks.DIRT.defaultBlockState();
        };
    }

    private record FillGroup(int component, int planeY, List<PerimeterSteppedGeometry.Pos> cells, List<FillColumn> columns) {}

    private record FillColumn(int x, int z, int component, int lowestY, int planeY,
                              List<PerimeterSteppedGeometry.Pos> cells) implements Comparable<FillColumn> {
        FillColumn(int x, int z, int component) { this(x, z, component, Integer.MIN_VALUE, Integer.MIN_VALUE, List.of()); }
        FillColumn withVertical(int lowestY, int planeY, List<PerimeterSteppedGeometry.Pos> cells) {
            return new FillColumn(x, z, component, lowestY, planeY, cells);
        }
        @Override public int compareTo(FillColumn other) {
            int c = Integer.compare(x, other.x);
            if (c == 0) c = Integer.compare(z, other.z);
            return c == 0 ? Integer.compare(component, other.component) : c;
        }
    }

    private static final class Disjoint {
        private final int[] parent;
        Disjoint(int size) { parent = new int[size]; for (int i = 0; i < size; i++) parent[i] = i; }
        int find(int value) { return parent[value] == value ? value : (parent[value] = find(parent[value])); }
        void union(int a, int b) { parent[find(a)] = find(b); }
    }

    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
