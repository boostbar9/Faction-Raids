package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AcceptedConstructionReservationTest extends MinecraftTestSupport {
    private final BlockPos origin = new BlockPos(10, 64, 10);

    @Test void reservesHeadroomAcrossReloadWithoutAuthorizingItsMutationOrReservingHollowInterior() throws Exception {
        var plan = plan(); var level = level();
        BlockPos headroom = origin.above(5), center = origin.west(2).south(2);
        var accepted = AcceptedConstructionReservation.capture(level, plan, Set.of(origin, headroom));
        var loaded = AcceptedConstructionReservation.load(plan, accepted.save());
        assertEquals(Set.of(origin, headroom), loaded.cells);
        assertEquals(Set.of(headroom), loaded.clearance.keySet());
        assertEquals(Set.of(origin), plan.cells.keySet());
        var ledger = new ConstructionEditLedger(); UUID area = UUID.randomUUID();
        assertTrue(ledger.register(area, loaded.cells));
        ledger = ConstructionEditLedger.load(ledger.save(new CompoundTag()));
        assertTrue(ledger.reserves(Set.of(headroom)));
        assertFalse(ledger.reserves(Set.of(center)), "A hollow ring does not reserve its empty center");
        var goal = new NativeConstructionGuardTest.NativeGoal();
        goal.blockPos = headroom;
        assertNotNull(NativeConstructionGuard.nativeStacksProblem(new NativeConstructionGuardTest.Area(), goal, plan),
                "Reserved clearance never broadens native mutation targets");
        ledger.record(headroom);
        assertTrue(ConstructionEditLedger.load(ledger.save(new CompoundTag())).edited(area),
                "Same-state edits in reserved air survive reload");
    }

    @Test void reservationInputsAndSavedAliasesCannotChangeAcceptedCells() {
        var plan = plan(); var source = new HashSet<>(Set.of(origin, origin.above()));
        var accepted = AcceptedConstructionReservation.capture(level(), plan, source);
        source.clear();
        var saved = accepted.save(); saved.putLongArray("Cells", new long[0]);
        assertEquals(2, accepted.cells.size());
        assertEquals(2, accepted.save().getLongArray("Cells").length);
        assertThrows(UnsupportedOperationException.class, () -> accepted.cells.clear());
        assertThrows(UnsupportedOperationException.class, () -> accepted.clearance.clear());
    }

    @Test void missingStructuralCellsAndOutsideEnvelopeReservationsAreRejected() {
        var plan = plan();
        assertThrows(IllegalArgumentException.class, () -> AcceptedConstructionReservation.validate(plan, Set.of(origin.above())));
        for (BlockPos outside : List.of(origin.above(6), origin.below(), origin.east(), origin.north(), origin.west(5), origin.south(5)))
            assertThrows(IllegalArgumentException.class,
                    () -> AcceptedConstructionReservation.validate(plan, Set.of(origin, outside)), outside.toString());
        assertEquals(Set.of(origin, origin.above(5)),
                AcceptedConstructionReservation.validate(plan, Set.of(origin, origin.above(5))), "Native scan Y endpoint is inclusive");
        assertThrows(IllegalArgumentException.class, () -> AcceptedConstructionReservation.validate(plan,
                java.util.Collections.nCopies(AcceptedConstructionReservation.MAX_CELLS + 1, origin)));
    }

    @Test void reservationEnvelopeHonorsEveryNativeFacingAtNegativeCoordinates() {
        BlockPos negative = new BlockPos(-20, -12, -30);
        for (Direction facing : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
            var plan = AcceptedConstructionPlan.decode(negative, facing, 5, 7, 6,
                    AcceptedConstructionPlanTest.blueprint(5, 4, 0, 0, Blocks.COBBLESTONE.defaultBlockState()));
            BlockPos corner = negative.relative(facing, 6).relative(facing.getClockWise(), 4).above(6);
            assertEquals(Set.of(negative, corner), AcceptedConstructionReservation.validate(plan, Set.of(negative, corner)));
            assertThrows(IllegalArgumentException.class,
                    () -> AcceptedConstructionReservation.validate(plan, Set.of(negative, corner.relative(facing))));
        }
    }

    @Test void reservedAirRejectsLaterSolidsAndPlantsAndOnlyKeepsOriginalPermittedPlants() {
        var level = level(); BlockPos clearance = origin.above();
        var accepted = AcceptedConstructionReservation.capture(level, plan(), Set.of(origin, clearance));
        assertNull(accepted.problem(level));
        for (var block : List.of(Blocks.STONE, Blocks.GRASS, Blocks.WATER, Blocks.CHEST)) {
            when(level.getBlockState(clearance)).thenReturn(block.defaultBlockState());
            assertNotNull(accepted.problem(level));
        }
        when(level.getBlockState(clearance)).thenReturn(Blocks.GRASS.defaultBlockState());
        accepted = AcceptedConstructionReservation.capture(level, plan(), Set.of(origin, clearance));
        assertNull(accepted.problem(level));
        when(level.getBlockState(clearance)).thenReturn(Blocks.AIR.defaultBlockState());
        assertNotNull(accepted.problem(level), "Non-mutating vegetation cannot silently change its accepted state");
        for (var block : List.of(Blocks.TALL_GRASS, Blocks.LARGE_FERN, Blocks.WITHER_ROSE, Blocks.STONE, Blocks.CHEST)) {
            when(level.getBlockState(clearance)).thenReturn(block.defaultBlockState());
            assertThrows(IllegalArgumentException.class,
                    () -> AcceptedConstructionReservation.capture(level, plan(), Set.of(origin, clearance)));
        }
    }

    @Test void noClearanceReadsCanForceLoadChunksDuringCaptureOrRevalidation() {
        var level = level(); var cells = Set.of(origin, origin.above());
        var accepted = AcceptedConstructionReservation.capture(level, plan(), cells);
        when(level.hasChunkAt(origin)).thenReturn(false); clearInvocations(level);
        assertThrows(IllegalArgumentException.class, () -> AcceptedConstructionReservation.capture(level, plan(), cells));
        assertNotNull(accepted.problem(level));
        verify(level, never()).getBlockState(any());
        verify(level, never()).getBlockEntity(any());
    }

    @Test void missingOldDraftMalformedOrAmbiguousSavedReservationFailsClosed() {
        var plan = plan(); var accepted = AcceptedConstructionReservation.capture(level(), plan, Set.of(origin, origin.above()));
        assertThrows(IllegalArgumentException.class, () -> AcceptedConstructionReservation.load(plan, new CompoundTag()));
        var duplicate = accepted.save(); duplicate.putLongArray("Cells", new long[]{origin.asLong(), origin.asLong()});
        assertThrows(IllegalArgumentException.class, () -> AcceptedConstructionReservation.load(plan, duplicate));
        var missing = accepted.save(); missing.remove("Clearance");
        assertThrows(IllegalArgumentException.class, () -> AcceptedConstructionReservation.load(plan, missing));
        var unknown = accepted.save();
        unknown.getList("Clearance", Tag.TAG_COMPOUND).getCompound(0).getCompound("State").putString("Name", "unknown:unregistered");
        assertThrows(IllegalArgumentException.class, () -> AcceptedConstructionReservation.load(plan, unknown));
        var occupied = accepted.save();
        occupied.getList("Clearance", Tag.TAG_COMPOUND).getCompound(0).getCompound("State").putString("Name", "minecraft:stone");
        assertThrows(IllegalArgumentException.class, () -> AcceptedConstructionReservation.load(plan, occupied));
    }

    private AcceptedConstructionPlan plan() {
        return AcceptedConstructionPlan.decode(origin, Direction.SOUTH, 5, 5, 5,
                AcceptedConstructionPlanTest.blueprint(5, 4, 0, 0, Blocks.COBBLESTONE.defaultBlockState()));
    }
    private ServerLevel level() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        return level;
    }
}
