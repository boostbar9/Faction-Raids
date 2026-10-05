package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksManifest.*;
import static org.junit.jupiter.api.Assertions.*;

class PerimeterEarthworksManifestTest extends MinecraftTestSupport {
    private static BlockState AIR, DIRT, STONE, WALL;
    @BeforeAll static void initializeStatesAfterMinecraftBootstrap() {
        AIR = Blocks.AIR.defaultBlockState(); DIRT = Blocks.DIRT.defaultBlockState();
        STONE = Blocks.STONE.defaultBlockState(); WALL = Blocks.COBBLESTONE.defaultBlockState();
    }
    private static final long CUT = pos(-4, 64, -7), FILL = pos(-3, 63, -7), GATE = pos(-6, 64, -7);

    @Test void exactCutFillBuildAndReadOnlyStateRoundTripWithoutLosingProperties() {
        var manifest = manifest(); var loaded = load(manifest.save());
        assertEquals(manifest.header(), loaded.header()); assertEquals(manifest.observations(), loaded.observations());
        assertEquals(manifest.steps(), loaded.steps()); assertEquals(manifest.hash(), loaded.hash());
        assertEquals(manifest.stageDigests(), loaded.stageDigests()); assertEquals(manifest.save(), loaded.save());
        assertEquals(3, loaded.stageDigests().size()); assertEquals(1, loaded.cutCount()); assertEquals(1, loaded.fillCount());
        assertEquals(5, loaded.observations().get(pos(-7, 63, -7)).original().getValue(BlockStateProperties.LAYERS));
        assertThrows(UnsupportedOperationException.class, () -> loaded.observations().clear());
        assertThrows(UnsupportedOperationException.class, () -> loaded.steps().clear());
        assertThrows(UnsupportedOperationException.class, () -> loaded.materialCounts().clear());
    }

    @Test void clearingNeverCreditsPotentialMiningDropsAsConstructionSupply() {
        var manifest = manifest();
        assertEquals(Map.of("minecraft:dirt", 1, "minecraft:cobblestone", 1), manifest.materialCounts());
        assertFalse(manifest.materialCounts().containsKey("minecraft:stone"));
        assertTrue(manifest.requiresSpecificRemovalReview());
    }

    @Test void observedGateFloorHeadroomAndDependenciesNeverAuthorizeMutation() {
        for (Role role : List.of(Role.GATE_CLEARANCE, Role.GATE_FLOOR, Role.PROTECTED_OCCUPANCY, Role.DEPENDENCY)) {
            var observations = new ArrayList<>(manifest().observations().values());
            observations.replaceAll(cell -> cell.pos() == CUT ? new Observation(CUT, STONE, role, 0) : cell);
            assertThrows(IllegalArgumentException.class, () -> new PerimeterEarthworksManifest(header(), observations, steps()));
        }
    }

    @Test void originIsNotInferredFromOrdinarySoilStoneOrLogStatesAndKnownPlayerEditsFail() {
        for (Family family : Family.values()) {
            var removal = new Removal(Origin.UNKNOWN, family, "fixture:single_cell", "1", "a".repeat(64));
            var changed = new ArrayList<>(steps()); changed.set(0, new Step(0, Kind.CUT, CUT, STONE, AIR, removal));
            assertTrue(new PerimeterEarthworksManifest(header(), observations(), changed).requiresSpecificRemovalReview());
        }
        assertThrows(IllegalArgumentException.class, () -> new Removal(Origin.KNOWN_PLAYER_EDIT, Family.SOIL,
                "fixture:single_cell", "1", "a".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> new Removal(Origin.UNKNOWN, Family.MODDED,
                "fixture:single_cell", "", "a".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> new Removal(Origin.UNKNOWN, Family.MODDED,
                "fixture:single_cell", "1", "unversioned"));
    }

    @Test void fullOriginalPropertiesEditRevisionAdapterAndStageMembershipBindTheDigest() {
        var original = manifest(); var reordered = new ArrayList<>(observations()); Collections.reverse(reordered);
        assertEquals(original.hash(), new PerimeterEarthworksManifest(header(), reordered, steps()).hash());
        var changed = new ArrayList<>(observations());
        changed.replaceAll(cell -> cell.pos() == CUT ? new Observation(CUT, STONE, Role.WORK, 1) : cell);
        assertNotEquals(original.hash(), new PerimeterEarthworksManifest(header(), changed, steps()).hash());
        var altered = new ArrayList<>(steps()); altered.set(0, new Step(0, Kind.CUT, CUT, STONE, AIR,
                new Removal(Origin.UNKNOWN, Family.STONE, "fixture:single_cell", "2", "a".repeat(64))));
        assertNotEquals(original.hash(), new PerimeterEarthworksManifest(header(), observations(), altered).hash());
        var properties = new ArrayList<>(observations()); properties.replaceAll(cell -> cell.role() == Role.GATE_FLOOR
                ? new Observation(cell.pos(), cell.original().setValue(BlockStateProperties.LAYERS, 6), cell.role(), 0) : cell);
        assertNotEquals(original.hash(), new PerimeterEarthworksManifest(header(), properties, steps()).hash());
    }

    @Test void missingWrongTypedUnknownAndTamperedSavedMetadataFailClosed() {
        corrupt(tag -> tag.putInt("EarthworksVersion", 2)); corrupt(tag -> tag.putString("Hash", "0".repeat(64)));
        corrupt(tag -> tag.putString("Version", "project-v1")); corrupt(tag -> tag.remove("Steps"));
        corrupt(tag -> tag.getCompound("Header").putInt("Price", 65));
        corrupt(tag -> tag.getCompound("Header").putString("Generation", "1"));
        corrupt(tag -> tag.getCompound("Header").putString("Dimension", "not a dimension"));
        corrupt(tag -> tag.getList("Observations", Tag.TAG_COMPOUND).getCompound(0).putLong("EditRevision", 500));
        corrupt(tag -> tag.getList("Stages", Tag.TAG_COMPOUND).getCompound(0).putString("Hash", "0".repeat(64)));
        corrupt(tag -> tag.getList("Steps", Tag.TAG_COMPOUND).getCompound(0).putInt("Stage", 1));
        corrupt(tag -> tag.getList("Steps", Tag.TAG_COMPOUND).getCompound(0).getCompound("Removal").putString("Origin", "NATURAL_TAG"));
        corrupt(tag -> tag.getList("Steps", Tag.TAG_COMPOUND).getCompound(0).getCompound("Removal").putLongArray("DependentCells", new long[]{CUT, GATE}));
        corrupt(tag -> { ListTag list = new ListTag(); list.add(StringTag.valueOf("not a cell")); tag.put("Observations", list); });
    }

    @Test void duplicateUnusedUnknownOrLossyPaletteStatesAreRejected() {
        corrupt(tag -> { var palette = tag.getList("Palette", Tag.TAG_COMPOUND); palette.add(palette.get(0).copy()); });
        corrupt(tag -> tag.getList("Palette", Tag.TAG_COMPOUND).getCompound(0).putString("Name", "fixture:unknown_block"));
        corrupt(tag -> {
            var palette = tag.getList("Palette", Tag.TAG_COMPOUND);
            for (Tag raw : palette) if (((CompoundTag) raw).getString("Name").equals("minecraft:snow"))
                ((CompoundTag) raw).getCompound("Properties").putString("layers", "9");
        });
        corrupt(tag -> tag.getList("Steps", Tag.TAG_COMPOUND).getCompound(0).putInt("Before", Integer.MAX_VALUE));
        corrupt(tag -> { var cells = tag.getList("Observations", Tag.TAG_COMPOUND); cells.add(cells.get(0).copy()); });
    }

    @Test void cutsCannotBeRepresentedByAirPlacementAndChangedOriginalCannotBeSkipped() {
        assertThrows(IllegalArgumentException.class, () -> new Step(0, Kind.BUILD, CUT, STONE, AIR, null));
        assertThrows(IllegalArgumentException.class, () -> new Step(0, Kind.CUT, CUT, STONE, DIRT, removal()));
        assertThrows(IllegalArgumentException.class, () -> new Step(0, Kind.CUT, CUT, AIR, AIR, removal()));
        var changed = new ArrayList<>(steps()); changed.set(0, new Step(0, Kind.CUT, CUT, DIRT, AIR, removal()));
        assertThrows(IllegalArgumentException.class, () -> new PerimeterEarthworksManifest(header(), observations(), changed));
    }

    @Test void blockEntitiesFluidsAndUnsupportedPlacementAreRejectedBeforeAnyPlanExists() {
        for (BlockState unsafe : List.of(Blocks.CHEST.defaultBlockState(), Blocks.WATER.defaultBlockState(),
                Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true))) {
            assertThrows(IllegalArgumentException.class, () -> new Step(0, Kind.CUT, CUT, unsafe, AIR, removal()),
                    "Unsafe original must never be cut: " + unsafe);
        }
        assertThrows(IllegalArgumentException.class, () -> new Step(0, Kind.FILL, FILL, AIR, WALL, null));
        assertThrows(IllegalArgumentException.class, () -> new Step(0, Kind.BUILD, CUT, AIR, Blocks.CHEST.defaultBlockState(), null));
    }

    @Test void topDownFourCutAndBottomUpEightFillBoundsAreExact() {
        var observations = new ArrayList<Observation>(); var steps = new ArrayList<Step>();
        for (int y = 67; y >= 64; y--) {
            long pos = pos(-4, y, -7); observations.add(new Observation(pos, STONE, Role.WORK, 0));
            steps.add(new Step(0, Kind.CUT, pos, STONE, AIR, removal()));
        }
        for (int y = 56; y <= 63; y++) {
            long pos = pos(-3, y, -7); observations.add(new Observation(pos, AIR, Role.WORK, 0));
            steps.add(new Step(1, Kind.FILL, pos, AIR, DIRT, null));
        }
        var valid = new PerimeterEarthworksManifest(header(), observations, steps);
        assertEquals(4, valid.cutCount()); assertEquals(8, valid.fillCount());
        Collections.swap(steps, 0, 1);
        assertThrows(IllegalArgumentException.class, () -> new PerimeterEarthworksManifest(header(), observations, steps));
        Collections.swap(steps, 0, 1); steps.remove(2);
        assertThrows(IllegalArgumentException.class, () -> new PerimeterEarthworksManifest(header(), observations, steps));
    }

    @Test void fifthCutNinthFillAndIncompletePlaneColumnsAreRejected() {
        for (int y : List.of(68, 65)) {
            long cell = pos(0, y, 0);
            assertThrows(IllegalArgumentException.class, () -> new PerimeterEarthworksManifest(header(),
                    List.of(new Observation(cell, STONE, Role.WORK, 0)), List.of(new Step(0, Kind.CUT, cell, STONE, AIR, removal()))));
        }
        for (int y : List.of(55, 62)) {
            long cell = pos(0, y, 0);
            assertThrows(IllegalArgumentException.class, () -> new PerimeterEarthworksManifest(header(),
                    List.of(new Observation(cell, AIR, Role.WORK, 0)), List.of(new Step(0, Kind.FILL, cell, AIR, DIRT, null))));
        }
    }

    @Test void stageGapsPhaseRewindAndDuplicatePlacementAreRejected() {
        var changed = new ArrayList<>(steps()); var build = changed.get(2);
        changed.set(2, new Step(3, build.kind(), build.pos(), build.before(), build.after(), null));
        assertThrows(IllegalArgumentException.class, () -> new PerimeterEarthworksManifest(header(), observations(), changed));
        var mixed = new ArrayList<>(steps()); mixed.set(2, new Step(1, build.kind(), build.pos(), build.before(), build.after(), null));
        assertThrows(IllegalArgumentException.class, () -> new PerimeterEarthworksManifest(header(), observations(), mixed));
        var duplicate = new ArrayList<>(steps()); duplicate.add(new Step(2, Kind.BUILD, CUT, AIR, WALL, null));
        assertThrows(IllegalArgumentException.class, () -> new PerimeterEarthworksManifest(header(), observations(), duplicate));
        var rewind = List.of(steps().get(0), new Step(1, Kind.BUILD, CUT, AIR, WALL, null), new Step(2, Kind.FILL, FILL, AIR, DIRT, null));
        assertThrows(IllegalArgumentException.class, () -> new PerimeterEarthworksManifest(header(), observations(), rewind));
    }

    @Test void aggregateCutAndObservationBoundsRejectTheWholeProposal() {
        var observations = new ArrayList<Observation>(); var steps = new ArrayList<Step>();
        for (int x = 0; x <= MAX_CUTS; x++) {
            long pos = pos(x, 64, 0); observations.add(new Observation(pos, STONE, Role.WORK, 0));
            steps.add(new Step(0, Kind.CUT, pos, STONE, AIR, removal()));
        }
        assertThrows(IllegalArgumentException.class, () -> new PerimeterEarthworksManifest(header(), observations, steps));
        assertThrows(IllegalArgumentException.class, () -> new PerimeterEarthworksManifest(header(),
                Collections.nCopies(MAX_OBSERVATIONS + 1, observations.get(0)), steps()));
    }

    @Test void invalidWorldHeightAndUnassignedWorkObservationsAreRejected() {
        for (long cell : List.of(pos(30_000_000, 64, 0), pos(0, -65, 0), pos(0, 320, 0))) {
            var changed = new ArrayList<>(observations()); changed.add(new Observation(cell, AIR, Role.DEPENDENCY, 0));
            assertThrows(IllegalArgumentException.class, () -> new PerimeterEarthworksManifest(header(), changed, steps()));
        }
        var changed = new ArrayList<>(observations()); changed.add(new Observation(pos(10, 64, 0), AIR, Role.WORK, 0));
        assertThrows(IllegalArgumentException.class, () -> new PerimeterEarthworksManifest(header(), changed, steps()));
    }

    @Test void existingAcceptedPaidVersionOneProjectRoundTripsThroughItsOriginalCodecOnly() {
        var legacy = PerimeterProjectTest.project().paid(false); CompoundTag saved = legacy.save();
        assertEquals(saved, PerimeterProject.load(saved.copy()).save());
        assertThrows(IllegalArgumentException.class, () -> load(saved));
        assertThrows(IllegalArgumentException.class, () -> PerimeterProject.load(manifest().save()));
    }

    private static PerimeterEarthworksManifest manifest() { return new PerimeterEarthworksManifest(header(), observations(), steps()); }
    private static Header header() {
        return new Header(UUID.fromString("11111111-1111-1111-1111-111111111111"), 1,
                UUID.fromString("22222222-2222-2222-2222-222222222222"), UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "minecraft:overworld", "test", "b".repeat(64), "c".repeat(64), "reviewed-cut-fill-proposal-v1", 0, 64, -64, 320, 1, 64);
    }
    private static Removal removal() { return new Removal(Origin.UNKNOWN, Family.STONE, "fixture:single_cell", "1", "a".repeat(64)); }
    private static List<Observation> observations() {
        return List.of(new Observation(CUT, STONE, Role.WORK, 0), new Observation(FILL, AIR, Role.WORK, 0),
                new Observation(GATE, AIR, Role.GATE_CLEARANCE, 0), new Observation(pos(-7, 63, -7),
                Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, 5), Role.GATE_FLOOR, 0));
    }
    private static List<Step> steps() {
        return List.of(new Step(0, Kind.CUT, CUT, STONE, AIR, removal()), new Step(1, Kind.FILL, FILL, AIR, DIRT, null),
                new Step(2, Kind.BUILD, CUT, AIR, WALL, null));
    }
    private static long pos(int x, int y, int z) { return new BlockPos(x, y, z).asLong(); }
    private static void corrupt(Consumer<CompoundTag> edit) {
        CompoundTag saved = manifest().save(); edit.accept(saved); assertThrows(IllegalArgumentException.class, () -> load(saved));
    }
}
