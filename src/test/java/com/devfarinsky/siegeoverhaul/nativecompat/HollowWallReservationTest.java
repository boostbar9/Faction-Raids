package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.core.DefenseBlueprint;
import com.devfarinsky.siegeoverhaul.core.DefenseStructures;
import com.devfarinsky.siegeoverhaul.core.PerimeterBlueprint;
import com.devfarinsky.siegeoverhaul.core.PerimeterConstruction;
import com.devfarinsky.siegeoverhaul.core.TerritoryFortification;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HollowWallReservationTest extends MinecraftTestSupport {
    @Test void automaticCavitiesSurviveActualReservationPathNativeDecodeSaveAndReload() throws Exception {
        var plan = PerimeterBlueprint.create(Set.of(new ChunkPos(-2, -3)),
                (x, z) -> PerimeterBlueprint.Surface.ready(64), PerimeterBlueprint.Palette.COBBLESTONE);
        Set<BlockPos> cavity = new HashSet<>();
        for (var column : plan.columns()) if (!column.parapet())
            for (int y = 0; y < 3; y++) cavity.add(column.base().above(y));
        assertEquals(396, cavity.size());
        verifyReservation(plan.blocks(), plan.min(), plan.max(),
                reserved(PerimeterConstruction.class, plan), cavity);
    }

    @Test void manualCavitiesSurviveActualReservationPathForBothVariantsAndEveryRotation() throws Exception {
        BlockPos origin = new BlockPos(-20, 64, -30);
        for (var kind : List.of(DefenseBlueprint.Kind.WALL, DefenseBlueprint.Kind.CORNER))
            for (var facing : Direction.Plane.HORIZONTAL) {
                var plan = DefenseBlueprint.create(kind, origin, facing);
                Set<BlockPos> cavity = new HashSet<>();
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = 0; y < 3; y++)
                    cavity.add(origin.relative(facing.getClockWise(), x).relative(facing, z).above(y));
                assertEquals(27, cavity.size());
                verifyReservation(plan.blocks(), plan.min(), plan.max(), reserved(DefenseStructures.class, plan), cavity);
            }
    }

    @Test void independentLegacyNativeSolidWallAndCornerRetainSavedRecipeExactly() {
        BlockPos min = new BlockPos(-22, 64, -32), max = min.offset(4, 5, 4);
        for (boolean corner : List.of(false, true)) {
            // Frozen pre-hollow recipe, independent of DefenseBlueprint.create and its material method.
            var oldTargets = new java.util.LinkedHashMap<Long, String>(); Set<BlockPos> reserved = new HashSet<>();
            for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) for (int y = 0; y < 6; y++) {
                BlockPos cell = min.offset(x, y, z); reserved.add(cell);
                boolean rail = corner ? x == 0 || z == 4 || x == 4 && z == 0 : x == 0 || x == 4;
                if (y < 3 || y == 4 && rail) oldTargets.put(cell.asLong(), "minecraft:cobblestone");
                else if (y == 3) oldTargets.put(cell.asLong(), "minecraft:oak_planks");
            }
            assertEquals(110, oldTargets.size());
            var nbt = TerritoryFortification.blueprint(oldTargets, min, max);
            var original = AcceptedConstructionPlan.decode(new BlockPos(max.getX(), min.getY(), min.getZ()),
                    Direction.SOUTH, 5, 5, 6, nbt);
            var level = mock(ServerLevel.class); when(level.hasChunkAt(any())).thenReturn(true);
            when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
            var reservation = AcceptedConstructionReservation.capture(level, original, reserved);
            CompoundTag saved = original.save(); var restored = AcceptedConstructionPlan.load(saved.copy());
            var restoredReservation = AcceptedConstructionReservation.load(restored, reservation.save());
            assertEquals(saved, restored.save()); assertEquals(original.cells, restored.cells);
            assertEquals(nbt, restored.save().getCompound("Structure"));
            assertEquals(110, restored.cells.size()); assertEquals(40, restoredReservation.clearance.size());
            BlockPos solidInterior = min.offset(2, 1, 2);
            assertEquals(Blocks.COBBLESTONE.defaultBlockState(), restored.cells.get(solidInterior));
            assertFalse(restoredReservation.clearance.containsKey(solidInterior));
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<BlockPos> reserved(Class<?> owner, Object plan) throws Exception {
        // Exercise the production handoff helper rather than reconstructing a headroom-only substitute.
        var method = owner.getDeclaredMethod("reservedCells", plan.getClass()); method.setAccessible(true);
        return (Set<BlockPos>) method.invoke(null, plan);
    }

    private static void verifyReservation(Map<Long, String> targets, BlockPos min, BlockPos max,
                                          Set<BlockPos> reserved, Set<BlockPos> cavity) throws ReflectiveOperationException {
        var nativePlan = AcceptedConstructionPlan.decode(new BlockPos(max.getX(), min.getY(), min.getZ()), Direction.SOUTH,
                max.getX() - min.getX() + 1, max.getZ() - min.getZ() + 1, max.getY() - min.getY() + 1,
                TerritoryFortification.blueprint(targets, min, max));
        assertEquals(targets.size(), nativePlan.cells.size());
        assertTrue(nativePlan.cells.values().stream().noneMatch(s -> s.isAir()));
        assertTrue(reserved.containsAll(cavity)); assertTrue(java.util.Collections.disjoint(nativePlan.cells.keySet(), cavity));
        var level = mock(ServerLevel.class);
        when(level.hasChunkAt(any())).thenReturn(true); when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        var accepted = AcceptedConstructionReservation.capture(level, nativePlan, reserved);
        var loadedPlan = AcceptedConstructionPlan.load(nativePlan.save());
        var loaded = AcceptedConstructionReservation.load(loadedPlan, accepted.save());
        assertEquals(reserved, loaded.cells); assertTrue(loaded.clearance.keySet().containsAll(cavity));
        var ledger = new ConstructionEditLedger(); UUID area = UUID.randomUUID(); assertTrue(ledger.register(area, loaded.cells));
        var restoredLedger = ConstructionEditLedger.load(ledger.save(new CompoundTag()));
        for (BlockPos cell : cavity) {
            assertTrue(restoredLedger.reserves(Set.of(cell)));
            assertFalse(loadedPlan.cells.containsKey(cell), "A protected cavity never authorizes mining or placement");
        }
        BlockPos changed = cavity.iterator().next();
        assertNull(loaded.problem(level));
        for (var block : List.of(Blocks.COBBLESTONE, Blocks.OAK_PLANKS, Blocks.CHEST, Blocks.WATER)) {
            when(level.getBlockState(changed)).thenReturn(block.defaultBlockState());
            assertNotNull(loaded.problem(level), "Player blocks added after acceptance pause the job");
            assertThrows(IllegalArgumentException.class, () -> AcceptedConstructionReservation.capture(level, nativePlan, reserved));
        }
        var goal = new NativeConstructionGuardTest.NativeGoal(); goal.blockPos = changed;
        assertNotNull(NativeConstructionGuard.nativeStacksProblem(new NativeConstructionGuardTest.Area(), goal, loadedPlan));
        verify(level, never()).setBlock(any(), any(), anyInt(), anyInt());
    }
}
