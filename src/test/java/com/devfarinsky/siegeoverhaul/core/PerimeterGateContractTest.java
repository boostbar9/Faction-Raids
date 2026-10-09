package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class PerimeterGateContractTest extends MinecraftTestSupport {
    private static final Set<ChunkPos> ONE = Set.of(new ChunkPos(0, 0));

    @Test void completeContractRoundTripsAndValidatesWithoutSavedRunsOrReselectingGates() {
        var original = flat(ONE);
        var selected = PerimeterGateLayout.create(ONE, original, (feet, region) ->
                region == PerimeterGateLayout.Region.OUTSIDE_APPROACH && feet.getZ() < 0 && feet.getX() == 8 ? "Blocked lane" : null);
        assertEquals(new BlockPos(6, 64, 0), selected.gates().get(0).outerCenter());
        var observations = observations(original, selected);
        var contract = PerimeterGateContract.create(ONE, original, selected, observations);
        var gated = contract.applyOpenings(original);
        var restored = PerimeterGateContract.load(contract.save().copy());
        assertEquals(contract.save(), restored.save()); assertEquals(contract.digest(), restored.digest());
        assertEquals(contract.gates(), restored.gates()); assertEquals(observations, restored.observations());
        assertDoesNotThrow(() -> restored.validateAgainst(ONE, withoutRuns(gated)));
        assertEquals(selected.approachClearance(), contract.airObservations());
        assertEquals(observations.size(), PerimeterGateContract.encodedObservationCount(contract.save()));
        assertTrue(restored.floorObservations().stream().map(restored.observations()::get)
                .allMatch(state -> state.getValue(BlockStateProperties.AXIS) == Direction.Axis.Z));
    }

    @Test void observationsAreImmutableAndNeverBecomeNativeTargetsOrClearance() {
        var original = flat(ONE); var selected = selection(ONE, original);
        var before = observations(original, selected);
        var contract = PerimeterGateContract.create(ONE, original, selected, before);
        var savedBefore = contract.save(); before.clear();
        assertEquals(savedBefore, contract.save());
        var gated = contract.applyOpenings(original);
        var stages = PerimeterStageLayout.partition(gated, stage -> null);
        assertEquals(original.blocks().size() - 72, gated.blocks().size());
        assertEquals(original.clearance().size() + 72, gated.clearance().size());
        assertEquals(original.columns(), gated.columns()); assertEquals(original.runs(), gated.runs());
        assertEquals(original.min(), gated.min()); assertEquals(original.max(), gated.max());
        assertEquals(572, original.blocks().size(), "Original legacy wall must remain untouched");
        for (var stage : stages.stages()) assertTrue(Collections.disjoint(stage.reservation(), contract.observations().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> contract.observations().clear());
        assertThrows(UnsupportedOperationException.class, () -> contract.gates().clear());
        assertThrows(UnsupportedOperationException.class, () -> contract.airObservations().clear());
        assertThrows(UnsupportedOperationException.class, () -> contract.floorObservations().clear());
        assertThrows(IllegalArgumentException.class, () -> contract.applyOpenings(gated));
    }

    @Test void everyUnplannedPassageFloorIsObservedAndPlannedFoundationsRemainTargets() {
        var original = PerimeterBlueprint.create(ONE, (x, z) -> PerimeterBlueprint.Surface.ready(x < 8 ? 62 : 64),
                PerimeterBlueprint.Palette.COBBLESTONE);
        var selected = selection(ONE, original); var contract = PerimeterGateContract.create(ONE, original, selected, observations(original, selected));
        var gated = contract.applyOpenings(original);
        Map<Long, PerimeterBlueprint.Column> columns = new HashMap<>();
        original.columns().forEach(column -> columns.put(PerimeterStageLayout.column(column.base().asLong()), column));
        int natural = 0, built = 0;
        for (var gate : selected.gates()) for (long cell : gate.passage()) {
            BlockPos feet = BlockPos.of(cell); if (feet.getY() != gate.outerCenter().getY()) continue;
            var column = columns.get(PerimeterStageLayout.column(cell)); long floor = feet.below().asLong();
            if (column.supportDepth() == 0) {
                natural++; assertTrue(contract.floorObservations().contains(floor)); assertFalse(gated.blocks().containsKey(floor));
            } else {
                built++; assertFalse(contract.observations().containsKey(floor)); assertEquals("minecraft:dirt", gated.blocks().get(floor));
            }
        }
        assertTrue(natural > 0); assertTrue(built > 0);
        var missing = new HashMap<>(contract.observations());
        long naturalFloor = contract.floorObservations().stream().filter(cell -> !selected.approachFooting().contains(cell)).findFirst().orElseThrow();
        missing.remove(naturalFloor);
        assertThrows(IllegalArgumentException.class, () -> PerimeterGateContract.create(ONE, original, selected, missing));
        var extra = new HashMap<>(contract.observations());
        long foundation = gated.blocks().entrySet().stream().filter(entry -> entry.getValue().equals("minecraft:dirt")).findFirst().orElseThrow().getKey();
        extra.put(foundation, Blocks.DIRT.defaultBlockState());
        assertThrows(IllegalArgumentException.class, () -> PerimeterGateContract.create(ONE, original, selected, extra));
    }

    @Test void shuffledInputsSerializeDeterministicallyAndStateChangesBindTheDigest() {
        var original = flat(ONE); var selected = selection(ONE, original); var states = observations(original, selected);
        var first = PerimeterGateContract.create(ONE, original, selected, states);
        var entries = new ArrayList<>(states.entrySet()); Collections.reverse(entries);
        Map<Long, BlockState> reversed = new LinkedHashMap<>(); entries.forEach(e -> reversed.put(e.getKey(), e.getValue()));
        var gates = new ArrayList<>(selected.gates()); Collections.reverse(gates);
        var shuffled = new PerimeterGateLayout.Layout(gates, selected.wallOpenings(), selected.passageClearance(),
                selected.approachClearance(), selected.approachFooting(), List.of());
        var second = PerimeterGateContract.create(ONE, original, shuffled, reversed);
        assertEquals(first.save(), second.save());
        long floor = first.floorObservations().iterator().next();
        reversed.put(floor, Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        assertNotEquals(first.digest(), PerimeterGateContract.create(ONE, original, selected, reversed).digest());
        var changedWall = PerimeterBlueprint.create(ONE, (x, z) -> PerimeterBlueprint.Surface.ready(64), PerimeterBlueprint.Palette.STONE_BRICKS);
        assertThrows(IllegalArgumentException.class, () -> first.applyOpenings(changedWall));
    }

    @Test void independentAndDiagonalComponentsHaveFourExactGatesAtTheirOwnHeights() {
        for (var claim : List.of(Set.of(new ChunkPos(-1, -1), new ChunkPos(0, 0)),
                Set.of(new ChunkPos(0, 0), new ChunkPos(2, 0)))) {
            var original = PerimeterBlueprint.create(claim, (x, z) -> PerimeterBlueprint.Surface.ready(x < 16 ? 64 : 72),
                    PerimeterBlueprint.Palette.COBBLESTONE);
            var selected = selection(claim, original);
            var contract = PerimeterGateContract.create(claim, original, selected, observations(original, selected));
            assertEquals(8, contract.gates().size());
            var restored = PerimeterGateContract.load(contract.save());
            assertDoesNotThrow(() -> restored.validateAgainst(claim, withoutRuns(contract.applyOpenings(original))));
        }
    }

    @Test void derivedComponentObservationsAreExactImmutableBoundedAndAbsentFromTheSaveFormat() {
        var territory = Set.of(new ChunkPos(0, 0), new ChunkPos(2, 0));
        var original = flat(territory); var selected = selection(territory, original);
        var contract = PerimeterGateContract.create(territory, original, selected, observations(original, selected));
        var saved = contract.save(); var restored = PerimeterGateContract.load(saved.copy());
        Set<Long> union = new HashSet<>();
        for (int component = 0; component < 2; component++) {
            Map<Long, BlockState> subset = contract.componentObservations(component);
            assertEquals(312, subset.size());
            assertTrue(subset.size() <= PerimeterGateContract.MAX_COMPONENT_OBSERVATIONS);
            assertEquals(subset, restored.componentObservations(component));
            subset.forEach((cell, state) -> assertEquals(contract.observations().get(cell), state));
            union.addAll(subset.keySet());
            assertThrows(UnsupportedOperationException.class, subset::clear);
        }
        assertEquals(contract.observations().keySet(), union);
        assertThrows(IllegalArgumentException.class, () -> contract.componentObservations(-1));
        assertThrows(IllegalArgumentException.class, () -> contract.componentObservations(2));
        assertEquals(Set.of("Version", "Wall", "Digest", "Gates", "Palette", "Observations"), saved.getAllKeys());
        assertEquals(saved, contract.save()); assertEquals(saved, restored.save());
        assertEquals(contract.digest(), restored.digest());
    }

    @Test void publicLayoutRecordsCannotForgeMissingDuplicateOrExtraGateCells() {
        var original = flat(ONE); var selected = selection(ONE, original); var before = observations(original, selected);
        var missing = new HashSet<>(selected.approachClearance()); missing.remove(missing.iterator().next());
        var changed = new PerimeterGateLayout.Layout(selected.gates(), selected.wallOpenings(), selected.passageClearance(),
                missing, selected.approachFooting(), List.of());
        assertThrows(IllegalArgumentException.class, () -> PerimeterGateContract.create(ONE, original, changed, before));
        var duplicate = new ArrayList<>(selected.gates()); duplicate.set(3, duplicate.get(0));
        var duplicateLayout = new PerimeterGateLayout.Layout(duplicate, selected.wallOpenings(), selected.passageClearance(),
                selected.approachClearance(), selected.approachFooting(), List.of());
        assertThrows(IllegalArgumentException.class, () -> PerimeterGateContract.create(ONE, original, duplicateLayout, before));
        var first = selected.gates().get(0); var bogus = new HashSet<>(first.passage()); bogus.add(new BlockPos(7, 67, 0).asLong());
        var altered = new ArrayList<>(selected.gates()); altered.set(0, new PerimeterGateLayout.Gate(first.componentId(), first.facing(),
                first.outerCenter(), bogus, first.insideApproach(), first.outsideApproach(), first.approachFooting()));
        var forged = new PerimeterGateLayout.Layout(altered, selected.wallOpenings(), selected.passageClearance(),
                selected.approachClearance(), selected.approachFooting(), List.of());
        assertThrows(IllegalArgumentException.class, () -> PerimeterGateContract.create(ONE, original, forged, before));
    }

    @Test void exactLookingDescriptorStillCannotCutCornersUseHolesOrRelabelComponents() {
        var original = flat(ONE); var selected = selection(ONE, original);
        var corner = replaceGate(selected, 0, new BlockPos(5, 64, 0), 0);
        assertThrows(IllegalArgumentException.class, () -> PerimeterGateContract.create(ONE, original, corner, observations(original, corner)));
        var wrongComponent = replaceGate(selected, 0, selected.gates().get(0).outerCenter(), 1);
        assertThrows(IllegalArgumentException.class, () -> PerimeterGateContract.create(ONE, original, wrongComponent, observations(original, wrongComponent)));
        Set<ChunkPos> ring = new HashSet<>();
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) if (x != 1 || z != 1) ring.add(new ChunkPos(x, z));
        var ringWall = flat(ring); var ringSelection = selection(ring, ringWall);
        var hole = replaceGate(ringSelection, 0, new BlockPos(24, 64, 32), 0);
        assertThrows(IllegalArgumentException.class, () -> PerimeterGateContract.create(ring, ringWall, hole, observations(ringWall, hole)));
    }

    @Test void savedColumnMetadataCannotAuthorizeExtraNativeTargetsOrMissingWallCells() {
        var original = flat(ONE); var selected = selection(ONE, original);
        var contract = PerimeterGateContract.create(ONE, original, selected, observations(original, selected));
        var gated = contract.applyOpenings(original);
        Map<Long, String> targets = new HashMap<>(gated.blocks()); targets.put(contract.observations().keySet().iterator().next(), "minecraft:dirt");
        var expanded = changed(gated, targets, gated.columns(), gated.clearance());
        assertThrows(IllegalArgumentException.class, () -> contract.validateAgainst(ONE, expanded));
        targets = new HashMap<>(gated.blocks()); targets.remove(targets.keySet().iterator().next());
        var missing = changed(gated, targets, gated.columns(), gated.clearance());
        assertThrows(IllegalArgumentException.class, () -> contract.validateAgainst(ONE, missing));
        var columns = new ArrayList<>(gated.columns()); var first = columns.get(0);
        columns.set(0, new PerimeterBlueprint.Column(first.base(), first.supportDepth(), 3, first.componentId()));
        assertThrows(IllegalArgumentException.class, () -> contract.validateAgainst(ONE, changed(gated, gated.blocks(), columns, gated.clearance())));
        assertThrows(IllegalArgumentException.class, () -> contract.validateAgainst(Set.of(new ChunkPos(1, 0)), gated));
    }

    @Test void codecRejectsUnknownMissingWrongTypedDuplicateAndChangedFields() {
        var contract = contract();
        corrupt(contract, tag -> tag.putInt("Version", 2));
        corrupt(contract, tag -> tag.putLong("Version", 1));
        corrupt(contract, tag -> tag.putString("Extra", "unknown"));
        corrupt(contract, tag -> tag.remove("Observations"));
        corrupt(contract, tag -> tag.putString("Digest", "0".repeat(64)));
        corrupt(contract, tag -> tag.putString("Wall", "0".repeat(64)));
        corrupt(contract, tag -> tag.getList("Gates", Tag.TAG_COMPOUND).getCompound(0).putString("Facing", "up"));
        corrupt(contract, tag -> tag.getList("Gates", Tag.TAG_COMPOUND).getCompound(0).putLong("Center", new BlockPos(30_000_001, 64, 0).asLong()));
        corrupt(contract, tag -> tag.getList("Gates", Tag.TAG_COMPOUND).getCompound(0).putLong("Center", new BlockPos(7, 2047, 0).asLong()));
        corrupt(contract, tag -> tag.getList("Gates", Tag.TAG_COMPOUND).getCompound(0).putInt("Component", -1));
        corrupt(contract, tag -> tag.getList("Gates", Tag.TAG_COMPOUND).add(tag.getList("Gates", Tag.TAG_COMPOUND).get(0).copy()));
        corrupt(contract, tag -> tag.getList("Observations", Tag.TAG_COMPOUND).remove(0));
        corrupt(contract, tag -> tag.getList("Observations", Tag.TAG_COMPOUND).add(tag.getList("Observations", Tag.TAG_COMPOUND).get(0).copy()));
        corrupt(contract, tag -> tag.getList("Observations", Tag.TAG_COMPOUND).getCompound(0).putInt("Role", 2));
        corrupt(contract, tag -> { var first = tag.getList("Observations", Tag.TAG_COMPOUND).getCompound(0); first.putInt("Role", 1 - first.getInt("Role")); });
        corrupt(contract, tag -> tag.getList("Observations", Tag.TAG_COMPOUND).getCompound(0).putInt("Before", -1));
        corrupt(contract, tag -> { var list = new ListTag(); list.add(StringTag.valueOf("wrong")); tag.put("Observations", list); });
        corrupt(contract, tag -> tag.getList("Palette", Tag.TAG_COMPOUND).add(tag.getList("Palette", Tag.TAG_COMPOUND).get(0).copy()));
        corrupt(contract, tag -> tag.getList("Palette", Tag.TAG_COMPOUND).getCompound(0).putString("Name", "unknown:block"));
        corrupt(contract, tag -> tag.getList("Palette", Tag.TAG_COMPOUND).getCompound(0).putInt("Properties", 1));
    }

    @Test void cheapStorePreflightRejectsOversizeCollectionsBeforeReadingStatesOrGeometry() {
        CompoundTag valid = contract().save();
        valid.getList("Palette", Tag.TAG_COMPOUND).getCompound(0).putString("Name", "unknown:block");
        assertDoesNotThrow(() -> PerimeterGateContract.encodedObservationCount(valid));
        assertThrows(IllegalArgumentException.class, () -> PerimeterGateContract.load(valid));
        CompoundTag tooMany = contract().save(); ListTag large = new ListTag();
        for (int i = 0; i <= PerimeterGateContract.MAX_OBSERVATIONS; i++) large.add(new CompoundTag());
        tooMany.put("Observations", large);
        assertThrows(IllegalArgumentException.class, () -> PerimeterGateContract.encodedObservationCount(tooMany));
        CompoundTag tooManyGates = contract().save(); ListTag gates = new ListTag();
        for (int i = 0; i <= PerimeterGateContract.MAX_GATES; i++) gates.add(new CompoundTag());
        tooManyGates.put("Gates", gates);
        assertThrows(IllegalArgumentException.class, () -> PerimeterGateContract.encodedObservationCount(tooManyGates));
    }

    private static PerimeterGateContract contract() {
        var original = flat(ONE); var selected = selection(ONE, original);
        return PerimeterGateContract.create(ONE, original, selected, observations(original, selected));
    }
    private static PerimeterBlueprint.Plan flat(Set<ChunkPos> territory) {
        return PerimeterBlueprint.create(territory, (x, z) -> PerimeterBlueprint.Surface.ready(64), PerimeterBlueprint.Palette.COBBLESTONE);
    }
    private static PerimeterGateLayout.Layout selection(Set<ChunkPos> territory, PerimeterBlueprint.Plan original) {
        var selected = PerimeterGateLayout.create(territory, original, (feet, region) -> null);
        assertTrue(selected.valid(), selected.problemSummary()); return selected;
    }
    static Map<Long, BlockState> observations(PerimeterBlueprint.Plan original, PerimeterGateLayout.Layout selected) {
        Map<Long, BlockState> result = new LinkedHashMap<>();
        selected.approachClearance().forEach(cell -> result.put(cell, Blocks.CAVE_AIR.defaultBlockState()));
        var floorState = Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
        selected.approachFooting().forEach(cell -> result.put(cell, floorState));
        for (var column : original.columns()) if (column.supportDepth() == 0 && selected.passageClearance().contains(column.base().asLong()))
            result.put(column.base().below().asLong(), floorState);
        return result;
    }
    private static PerimeterBlueprint.Plan withoutRuns(PerimeterBlueprint.Plan plan) {
        return new PerimeterBlueprint.Plan(plan.blocks(), plan.columns(), plan.clearance(), plan.min(), plan.max(), List.of(), List.of(), plan.materialCounts(), List.of());
    }
    private static PerimeterBlueprint.Plan changed(PerimeterBlueprint.Plan plan, Map<Long, String> targets,
                                                   List<PerimeterBlueprint.Column> columns, Set<Long> clearance) {
        Map<String, Integer> counts = new HashMap<>(); targets.values().forEach(material -> counts.merge(material, 1, Integer::sum));
        return new PerimeterBlueprint.Plan(targets, columns, clearance, plan.min(), plan.max(), List.of(), List.of(), counts, List.of());
    }
    private static void corrupt(PerimeterGateContract contract, Consumer<CompoundTag> change) {
        CompoundTag tag = contract.save(); change.accept(tag);
        assertThrows(IllegalArgumentException.class, () -> PerimeterGateContract.load(tag));
    }
    /** Independent descriptor expansion used to test that plausible public records are not trusted. */
    private static PerimeterGateLayout.Layout replaceGate(PerimeterGateLayout.Layout original, int index, BlockPos center, int component) {
        var gates = new ArrayList<>(original.gates()); Direction facing = gates.get(index).facing();
        Set<Long> passage = new HashSet<>(), inside = new HashSet<>(), outside = new HashSet<>(), footing = new HashSet<>();
        for (int lane = -1; lane <= 1; lane++) for (int depth = -7; depth <= 3; depth++) {
            BlockPos feet = center.relative(facing.getClockWise(), lane).relative(facing, depth);
            Set<Long> destination = depth < -4 ? inside : depth > 0 ? outside : passage;
            for (int y = 0; y < 3; y++) destination.add(feet.above(y).asLong());
            if (depth < -4 || depth > 0) footing.add(feet.below().asLong());
        }
        gates.set(index, new PerimeterGateLayout.Gate(component, facing, center, passage, inside, outside, footing));
        Set<Long> allPassage = new LinkedHashSet<>(), allAir = new LinkedHashSet<>(), allFooting = new LinkedHashSet<>(), openings = new LinkedHashSet<>();
        for (var gate : gates) {
            allPassage.addAll(gate.passage()); allAir.addAll(gate.insideApproach()); allAir.addAll(gate.outsideApproach()); allFooting.addAll(gate.approachFooting());
            for (int lane = -1; lane <= 1; lane++) for (int depth : List.of(0, -4)) for (int y = 0; y < 3; y++)
                openings.add(gate.outerCenter().relative(gate.facing().getClockWise(), lane).relative(gate.facing(), depth).above(y).asLong());
        }
        return new PerimeterGateLayout.Layout(gates, openings, allPassage, allAir, allFooting, List.of());
    }
}
