package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.entities.ai.BuilderWorkGoal;
import com.talhanation.workers.world.BuildBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual pinned Workers goal/placement methods over mocked world and native worker entities. */
class NativeCrewWorkClaimsTest extends MinecraftTestSupport {
    private static final BlockPos FIRST = new BlockPos(0, 64, 0), SECOND = FIRST.east(), THIRD = SECOND.east();
    private static final BlockState WALL = Blocks.COBBLESTONE.defaultBlockState(), AIR = Blocks.AIR.defaultBlockState();
    private final List<ProtectedBuildArea> opened = new ArrayList<>();

    @AfterEach void closeMarkers() { opened.forEach(NativeCrewWorkClaims::close); }

    @Test void twoNativeWorkersReserveDistinctTargetsWithoutEditingSharedOrOtherPrivateQueues() {
        var f = new Fixture(FIRST, SECOND, THIRD);
        var one = f.worker(FIRST, SECOND, THIRD);
        var two = f.worker(FIRST, SECOND, THIRD);
        Stack<BlockPos> oneOriginal = one.goal.stackToPlace, twoOriginal = two.goal.stackToPlace;
        var shared = f.area.stackToPlace;
        f.area.stackToBreak.push(FIRST);
        one.goal.stackToBreak = f.area.stackToBreak;
        two.goal.stackToBreak = f.area.stackToBreak;

        assertTrue(f.prepare(one));
        assertTrue(f.prepare(two));
        assertEquals(THIRD, one.goal.blockPos);
        assertEquals(SECOND, two.goal.blockPos);
        assertEquals(List.of(FIRST, SECOND), one.goal.stackToPlace);
        assertEquals(List.of(FIRST, THIRD), two.goal.stackToPlace);
        assertEquals(List.of(FIRST, SECOND, THIRD), oneOriginal);
        assertEquals(List.of(FIRST, SECOND, THIRD), twoOriginal);
        assertSame(shared, f.area.stackToPlace);
        assertEquals(3, shared.size());
        assertSame(f.area.stackToBreak, one.goal.stackToBreak);
        assertSame(f.area.stackToBreak, two.goal.stackToBreak);
        assertEquals(List.of(FIRST), f.area.stackToBreak);
        verify(f.level, never()).setBlockAndUpdate(any(), any());
        assertEquals(8, one.material.getCount());
        assertEquals(8, two.material.getCount());
    }

    @Test void actualNativeDispatchSpendsOneItemPerDistinctClaimAndStaleTargetCostsNothingExtra() {
        var f = new Fixture(FIRST, SECOND);
        var one = f.worker(FIRST, SECOND);
        var two = f.worker(FIRST, SECOND);
        assertTrue(f.prepare(one));
        assertTrue(f.prepare(two));
        assertEquals(SECOND, one.goal.blockPos);
        assertEquals(FIRST, two.goal.blockPos);

        f.dispatch(one);
        assertEquals(7, one.material.getCount());
        assertEquals(1, f.area.stackToPlace.size());
        f.dispatch(two);
        assertEquals(7, two.material.getCount());
        assertTrue(f.area.stackToPlace.isEmpty());

        // Both Workers goals retain a stale blockPos after successful native placement.
        assertEquals(SECOND, one.goal.blockPos);
        assertEquals(FIRST, two.goal.blockPos);
        assertTrue(f.prepare(one));
        assertTrue(f.prepare(two));
        assertNull(one.goal.blockPos);
        assertNull(two.goal.blockPos);
        assertTrue(one.goal.stackToPlace.isEmpty());
        assertTrue(two.goal.stackToPlace.isEmpty());
        one.goal.tick();
        two.goal.tick();
        assertEquals(BuilderWorkGoal.State.PREPARE_PLACE_BLOCKS, one.goal.state);
        assertEquals(BuilderWorkGoal.State.PREPARE_PLACE_BLOCKS, two.goal.state);
        verify(f.level, times(1)).setBlockAndUpdate(FIRST, WALL);
        verify(f.level, times(1)).setBlockAndUpdate(SECOND, WALL);
        assertEquals(7, one.material.getCount());
        assertEquals(7, two.material.getCount());
        verify(one.builder, never()).mineBlock(any());
        verify(two.builder, never()).mineBlock(any());
    }

    @Test void removedCompletedLowerEntryFailsClosedBeforeAnySafeTopPruning() {
        var f = new Fixture(FIRST, SECOND);
        var worker = f.worker(FIRST, SECOND);
        f.completed.add(FIRST.asLong());
        f.completed.add(SECOND.asLong());
        f.world.put(SECOND, WALL);
        Stack<BlockPos> original = worker.goal.stackToPlace;
        assertFalse(f.prepare(worker));
        assertSame(original, worker.goal.stackToPlace);
        assertEquals(List.of(FIRST, SECOND), original);
        assertNull(worker.goal.blockPos);
        assertEquals(2, f.area.stackToPlace.size());
        assertEquals(Set.of(FIRST.asLong(), SECOND.asLong()), f.completed);
    }

    @Test void changedCompletedCurrentTargetIsNeverDiscardedEvenWithAnotherAvailableCell() {
        var f = new Fixture(FIRST, SECOND);
        var worker = f.worker(SECOND);
        worker.goal.blockPos = FIRST;
        f.completed.add(FIRST.asLong());
        f.world.put(FIRST, Blocks.OAK_PLANKS.defaultBlockState());
        assertFalse(f.prepare(worker));
        assertEquals(FIRST, worker.goal.blockPos);
        assertEquals(List.of(SECOND), worker.goal.stackToPlace);
        verify(f.level, never()).setBlockAndUpdate(any(), any());
    }

    @Test void missingSharedPendingCellRequiresExactLiveAcceptedStateBeforeSkipping() {
        var f = new Fixture(FIRST, SECOND);
        var worker = f.worker(FIRST, SECOND);
        f.area.stackToPlace.removeIf(cell -> cell.getPos().equals(SECOND));
        assertFalse(f.prepare(worker));
        assertEquals(List.of(FIRST, SECOND), worker.goal.stackToPlace);
        f.world.put(SECOND, WALL);
        assertTrue(f.prepare(worker));
        assertEquals(FIRST, worker.goal.blockPos);
        assertTrue(worker.goal.stackToPlace.isEmpty());
        assertEquals(1, f.area.stackToPlace.size());
        assertTrue(f.completed.isEmpty(), "The scheduler cannot create durable completion receipts");
    }

    @Test void unloadedCompletedCellIsNotReadOrPruned() {
        var f = new Fixture(FIRST);
        var worker = f.worker(FIRST);
        f.completed.add(FIRST.asLong());
        f.world.put(FIRST, WALL);
        when(f.level.hasChunkAt(FIRST)).thenReturn(false);
        assertFalse(f.prepare(worker));
        assertEquals(List.of(FIRST), worker.goal.stackToPlace);
        verify(f.level, never()).getBlockState(FIRST);
    }

    @Test void aliasedMiningQueuesAreRejectedWithoutMutation() {
        var f = new Fixture(FIRST);
        var worker = f.worker(FIRST);
        f.area.stackToBreak.push(FIRST);
        worker.goal.stackToPlace = f.area.stackToBreak;
        assertFalse(f.prepare(worker));
        assertSame(f.area.stackToBreak, worker.goal.stackToPlace);
        assertEquals(List.of(FIRST), f.area.stackToBreak);
        worker.goal.stackToPlace = stack(FIRST);
        worker.goal.stackToBreak = worker.goal.stackToPlace;
        assertFalse(f.prepare(worker));
        assertSame(worker.goal.stackToPlace, worker.goal.stackToBreak);
        assertEquals(List.of(FIRST), worker.goal.stackToBreak);
    }

    @Test void invalidWorkerAreaGoalAndChangedRecipeCannotGainClaims() {
        var f = new Fixture(FIRST);
        var worker = f.worker(FIRST);
        worker.builder.currentBuildArea = null;
        assertFalse(f.prepare(worker));
        worker.builder.currentBuildArea = f.area;
        var other = f.worker(FIRST);
        assertFalse(NativeCrewWorkClaims.prepare(other.builder, worker.goal, f.area, f.accepted, f.completed));
        assertTrue(f.prepare(worker));
        Map<BlockPos, BlockState> changed = Map.of(FIRST, Blocks.OAK_PLANKS.defaultBlockState());
        assertFalse(NativeCrewWorkClaims.prepare(worker.builder, worker.goal, f.area, changed, f.completed));
        assertEquals(FIRST, worker.goal.blockPos);
        assertTrue(f.prepare(other), "The rejected old holder releases its token");
    }

    @Test void unknownQueueTargetsAndModifiedSharedStatesCannotBeSilentlyFiltered() {
        var f = new Fixture(FIRST);
        var worker = f.worker(FIRST, SECOND);
        assertFalse(f.prepare(worker));
        assertEquals(List.of(FIRST, SECOND), worker.goal.stackToPlace);
        worker.goal.stackToPlace = stack(FIRST);
        f.area.stackToPlace.set(0, new BuildBlock(FIRST, Blocks.OAK_PLANKS.defaultBlockState()));
        assertFalse(f.prepare(worker));
        assertEquals(List.of(FIRST), worker.goal.stackToPlace);
    }

    @Test void renewalsRetainAClaimAndExactTtlExpiryAllowsAReplacementWorker() {
        var f = new Fixture(FIRST);
        var one = f.worker(FIRST);
        var two = f.worker(FIRST);
        assertTrue(f.prepare(one));
        f.tick = 199;
        assertTrue(f.prepare(one));
        f.tick = 200;
        assertFalse(f.prepare(two));
        assertNull(two.goal.blockPos);
        assertEquals(List.of(FIRST), two.goal.stackToPlace);
        f.tick = 399;
        assertTrue(f.prepare(two));
        assertFalse(f.prepare(one), "An expired token cannot displace the replacement generation");
        assertNull(one.goal.blockPos);
        assertEquals(List.of(FIRST), one.goal.stackToPlace);
        assertEquals(FIRST, two.goal.blockPos);
    }

    @Test void explicitInterruptionReleasesTargetWithoutEditingNativeState() {
        var f = new Fixture(FIRST);
        var one = f.worker(FIRST);
        var two = f.worker(FIRST);
        assertTrue(f.prepare(one));
        NativeCrewWorkClaims.release(one.builder);
        assertEquals(FIRST, one.goal.blockPos);
        assertTrue(one.goal.stackToPlace.isEmpty());
        assertTrue(f.prepare(two));
        assertFalse(f.prepare(one));
    }

    @Test void everyOtherNativeStateReleasesOnlyTheClaimAndLeavesNativeFieldsIntact() {
        for (BuilderWorkGoal.State state : BuilderWorkGoal.State.values()) {
            if (state == BuilderWorkGoal.State.PLACE_BLOCKS) continue;
            var f = new Fixture(FIRST);
            var one = f.worker(FIRST);
            var two = f.worker(FIRST);
            assertTrue(f.prepare(one));
            one.goal.state = state;
            var queue = one.goal.stackToPlace;
            assertTrue(f.prepare(one), state.name());
            assertSame(queue, one.goal.stackToPlace);
            assertEquals(FIRST, one.goal.blockPos);
            assertEquals(state, one.goal.state);
            assertTrue(f.prepare(two));
        }
    }

    @Test void unrelatedGoalNeverReceivesPlacementFieldsOrRetainsAClaim() {
        var f = new Fixture(FIRST);
        var one = f.worker(FIRST);
        var two = f.worker(FIRST);
        assertTrue(f.prepare(one));
        Goal unrelated = mock(Goal.class);
        assertTrue(NativeCrewWorkClaims.prepare(one.builder, unrelated, f.area, f.accepted, f.completed));
        verifyNoInteractions(unrelated);
        assertTrue(f.prepare(two));
    }

    @Test void atMostFourWorkersMayHoldClaimsAndReleaseReturnsCapacity() {
        var f = new Fixture(FIRST, SECOND, THIRD, THIRD.east(), THIRD.east(2));
        var workers = new ArrayList<Worker>();
        for (BlockPos pos : f.accepted.keySet()) workers.add(f.worker(pos));
        for (int i = 0; i < 4; i++) assertTrue(f.prepare(workers.get(i)));
        assertFalse(f.prepare(workers.get(4)));
        NativeCrewWorkClaims.release(workers.get(0).builder);
        assertTrue(f.prepare(workers.get(4)));
    }

    @Test void markerReloadUsesFreshCoordinatorAndOldCallbacksCannotReleaseNewTokens() {
        var old = new Fixture(FIRST);
        var worker = old.worker(FIRST);
        assertTrue(old.prepare(worker));
        NativeCrewWorkClaims.close(old.area);
        assertFalse(old.prepare(worker), "The same closed entity object cannot revive");

        var reloaded = new Fixture(FIRST);
        when(reloaded.area.getUUID()).thenReturn(old.area.getUUID());
        worker.builder.currentBuildArea = reloaded.area;
        when(worker.builder.level()).thenReturn(reloaded.level);
        when(worker.builder.getCommandSenderWorld()).thenReturn(reloaded.level);
        worker.goal.stackToPlace = stack(FIRST);
        assertTrue(reloaded.prepare(worker));
        NativeCrewWorkClaims.after(worker.builder, old.area);
        NativeCrewWorkClaims.close(old.area);
        assertFalse(reloaded.prepare(reloaded.worker(FIRST)), "Stale close/after cannot touch the fresh claim");
    }

    @Test void nativeStateChangeAfterDispatchReleasesAnUnfinishedTarget() {
        var f = new Fixture(FIRST);
        var one = f.worker(FIRST);
        var two = f.worker(FIRST);
        assertTrue(f.prepare(one));
        one.goal.state = BuilderWorkGoal.State.PREPARE_PLACE_BLOCKS;
        NativeCrewWorkClaims.after(one.builder, f.area);
        assertTrue(f.prepare(two));
        assertEquals(8, one.material.getCount());
    }

    @Test void regressedWorldTimeInvalidatesOldTokensAndFailsTheDiscoveringDispatch() {
        var f = new Fixture(FIRST);
        var one = f.worker(FIRST);
        var two = f.worker(FIRST);
        f.tick = 20;
        assertTrue(f.prepare(one));
        f.tick = 10;
        assertFalse(f.prepare(one));
        assertTrue(f.prepare(two));
    }

    private static Stack<BlockPos> stack(BlockPos... positions) {
        var result = new Stack<BlockPos>();
        result.addAll(List.of(positions));
        return result;
    }

    private static final class Worker {
        final BuilderEntity builder = mock(BuilderEntity.class);
        final BuilderWorkGoal goal = new BuilderWorkGoal(builder);
        final ItemStack material = new ItemStack(Items.COBBLESTONE, 8);
    }

    private final class Fixture {
        final ProtectedBuildArea area = mock(ProtectedBuildArea.class);
        final ServerLevel level = mock(ServerLevel.class);
        final Map<BlockPos, BlockState> accepted = new HashMap<>();
        final Map<BlockPos, BlockState> world = new HashMap<>();
        final Set<Long> completed = new HashSet<>();
        long tick;

        Fixture(BlockPos... targets) {
            opened.add(area);
            area.stackToPlace = new Stack<>();
            area.stackToPlaceMultiBlock = new Stack<>();
            area.stackToBreak = new Stack<>();
            area.stackToFree = new Stack<>();
            for (BlockPos pos : targets) {
                accepted.put(pos, WALL);
                area.stackToPlace.push(new BuildBlock(pos, WALL));
            }
            when(area.level()).thenReturn(level);
            when(area.getUUID()).thenReturn(UUID.randomUUID());
            when(level.getGameTime()).thenAnswer(call -> tick);
            when(level.hasChunkAt(any())).thenReturn(true);
            when(level.getBlockState(any())).thenAnswer(call -> world.getOrDefault(call.getArgument(0), AIR));
            when(level.setBlockAndUpdate(any(), any())).thenAnswer(call -> {
                world.put(call.getArgument(0), call.getArgument(1));
                return true;
            });
            doCallRealMethod().when(area).getStateFromPos(any());
            doCallRealMethod().when(area).statesMatch(any(), any());
            doCallRealMethod().when(area).removeBuildBlockToPlace(any());
        }

        Worker worker(BlockPos... queue) {
            Worker worker = new Worker();
            worker.builder.currentBuildArea = area;
            worker.builder.tickCount = 5;
            when(worker.builder.getUUID()).thenReturn(UUID.randomUUID());
            when(worker.builder.level()).thenReturn(level);
            when(worker.builder.getCommandSenderWorld()).thenReturn(level);
            when(worker.builder.getLookControl()).thenReturn(mock(LookControl.class));
            when(worker.builder.getMainHandItem()).thenReturn(worker.material);
            when(worker.builder.getMatchingItem(any())).thenAnswer(call -> worker.material.isEmpty() ? null : worker.material);
            worker.goal.state = BuilderWorkGoal.State.PLACE_BLOCKS;
            worker.goal.stackToPlace = stack(queue);
            return worker;
        }

        boolean prepare(Worker worker) {
            return NativeCrewWorkClaims.prepare(worker.builder, worker.goal, area, accepted, completed);
        }

        void dispatch(Worker worker) {
            BlockPos selected = worker.goal.blockPos;
            worker.goal.tick();
            // Stand-in for the existing guard's separately authenticated completion receipt.
            if (WALL.equals(world.get(selected))) completed.add(selected.asLong());
            NativeCrewWorkClaims.after(worker.builder, area);
        }
    }
}
