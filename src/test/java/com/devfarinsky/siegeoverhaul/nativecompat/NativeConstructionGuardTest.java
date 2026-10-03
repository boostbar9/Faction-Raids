package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.Stack;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NativeConstructionGuardTest extends MinecraftTestSupport {
    private final BlockState AIR = Blocks.AIR.defaultBlockState(), WALL = Blocks.COBBLESTONE.defaultBlockState(),
            GRASS = Blocks.GRASS.defaultBlockState();

    @Test void ordinaryNativeAreasCannotAccidentallyEnableProtectedCommissions() {
        var owner = mock(net.minecraft.server.level.ServerPlayer.class);
        var worker = mock(Mob.class); var area = mock(Entity.class);
        when(area.level()).thenReturn(mock(net.minecraft.server.level.ServerLevel.class));
        when(area.getPersistentData()).thenReturn(new CompoundTag());
        assertFalse(NativeConstructionGuard.protect(owner, worker, area));
        assertTrue(NativeConstructionGuard.status(area).contains("sealed native marker type"));
    }

    @Test void ambiguousLegacySolidsNeverBecomeClearableThroughAMaterialWhitelist() {
        for (var block : java.util.List.of(Blocks.DIRT, Blocks.OAK_LOG, Blocks.STONE, Blocks.CHEST,
                Blocks.WATER, Blocks.LAVA, Blocks.WITHER_ROSE, Blocks.TALL_GRASS, Blocks.LARGE_FERN))
            assertFalse(NativeConstructionGuard.initialCellSafe(block.defaultBlockState(), WALL), block.toString());
        assertTrue(NativeConstructionGuard.initialCellSafe(AIR, WALL));
        assertTrue(NativeConstructionGuard.initialCellSafe(WALL, WALL));
        assertTrue(NativeConstructionGuard.initialCellSafe(GRASS, WALL));
    }

    @Test void onlyOriginalUnchangedSingleCellVegetationCanBeMined() {
        assertTrue(NativeConstructionGuard.currentCellSafe(GRASS, WALL, GRASS, false, false));
        assertFalse(NativeConstructionGuard.currentCellSafe(AIR, WALL, GRASS, false, false));
        assertFalse(NativeConstructionGuard.currentCellSafe(GRASS, WALL, Blocks.POPPY.defaultBlockState(), false, false));
        assertFalse(NativeConstructionGuard.currentCellSafe(GRASS, WALL, GRASS, false, true));
        assertFalse(NativeConstructionGuard.currentCellSafe(GRASS, WALL, Blocks.STONE.defaultBlockState(), false, false));
        assertTrue(NativeConstructionGuard.currentCellSafe(GRASS, WALL, AIR, false, true));
    }

    @Test void vegetationDropsDoNotBlockTheNextPlacementButLivingAndHangingEntitiesDo() {
        var item = mock(net.minecraft.world.entity.item.ItemEntity.class);
        var xp = mock(net.minecraft.world.entity.ExperienceOrb.class);
        var mob = mock(Mob.class);
        var hanging = mock(net.minecraft.world.entity.decoration.HangingEntity.class);
        when(item.isAlive()).thenReturn(true); when(xp.isAlive()).thenReturn(true);
        when(mob.isAlive()).thenReturn(true); when(hanging.isAlive()).thenReturn(true);
        assertFalse(NativeConstructionGuard.blocksPlacement(item));
        assertFalse(NativeConstructionGuard.blocksPlacement(xp));
        assertTrue(NativeConstructionGuard.blocksPlacement(mob));
        assertTrue(NativeConstructionGuard.blocksPlacement(hanging));
    }

    @Test void finishedBlocksAreNeverRebuiltAfterLaterRemovalEvenAfterReload() {
        assertTrue(NativeConstructionGuard.currentCellSafe(AIR, WALL, WALL, true, false));
        assertFalse(NativeConstructionGuard.currentCellSafe(AIR, WALL, AIR, true, false));
        assertFalse(NativeConstructionGuard.currentCellSafe(AIR, WALL, GRASS, true, false));
        assertFalse(NativeConstructionGuard.currentCellSafe(WALL, WALL, AIR, true, false));
    }

    @Test void everyNativeQueueMustStayInsideTheAcceptedImmutablePlan() throws Exception {
        var plan = plan(); var area = new Area(); var goal = new NativeGoal();
        area.stackToPlace.push(new Cell(BlockPos.ZERO, WALL));
        assertNull(NativeConstructionGuard.nativeStacksProblem(area, goal, plan));
        goal.stackToBreak.push(BlockPos.ZERO.east());
        assertNotNull(NativeConstructionGuard.nativeStacksProblem(area, goal, plan));
        goal.stackToBreak.clear(); goal.stackToFree.push(BlockPos.ZERO);
        assertNotNull(NativeConstructionGuard.nativeStacksProblem(area, goal, plan));
        goal.stackToFree.clear(); area.stackToFree.push(BlockPos.ZERO);
        assertNotNull(NativeConstructionGuard.nativeStacksProblem(area, goal, plan));
        area.stackToFree.clear(); goal.blockPos = BlockPos.ZERO.above();
        assertNotNull(NativeConstructionGuard.nativeStacksProblem(area, goal, plan));
    }

    @Test void directSecondaryPlacementAndModifiedPlannedStatesFailClosed() throws Exception {
        var plan = plan(); var area = new Area(); var goal = new NativeGoal();
        area.stackToPlaceMultiBlock.push(new Cell(BlockPos.ZERO, WALL));
        assertNotNull(NativeConstructionGuard.nativeStacksProblem(area, goal, plan));
        area.stackToPlaceMultiBlock.clear();
        area.stackToPlace.push(new Cell(BlockPos.ZERO, Blocks.OAK_PLANKS.defaultBlockState()));
        assertNotNull(NativeConstructionGuard.nativeStacksProblem(area, goal, plan));
        assertThrows(ReflectiveOperationException.class,
                () -> NativeConstructionGuard.nativeStacksProblem(new Object(), goal, plan));
    }

    @Test void nextMutationTargetMatchesNativePopAndStaleTargetBranches() {
        var goal = new NativeGoal();
        goal.state = State.PLACE_BLOCKS; goal.stackToPlace.push(BlockPos.ZERO); goal.stackToPlace.push(BlockPos.ZERO.above());
        assertEquals(Set.of(BlockPos.ZERO.above()), NativeConstructionGuard.mutationCells(goal));
        assertEquals(2, goal.stackToPlace.size(), "Inspection never pops native work");
        goal.blockPos = BlockPos.ZERO.east();
        assertEquals(Set.of(BlockPos.ZERO.east()), NativeConstructionGuard.mutationCells(goal));
        goal.state = State.BREAK_BLOCKS;
        assertEquals(Set.of(BlockPos.ZERO.east()), NativeConstructionGuard.mutationCells(goal));
        goal.blockPos = null;
        assertTrue(NativeConstructionGuard.mutationCells(goal).isEmpty(), "Mining selects its target one tick before mining");
        for (State state : java.util.List.of(State.SELECT_WORK_AREA, State.MOVE_TO_WORK_AREA,
                State.PREPARE_BREAK_BLOCKS, State.PREPARE_PLACE_BLOCKS, State.DONE)) {
            goal.state = state; assertTrue(NativeConstructionGuard.mutationCells(goal).isEmpty());
        }
    }

    @Test void unguardedOldJobsAreNotMigratedAndActivationIsExplicit() {
        Entity area = mock(Entity.class); CompoundTag data = new CompoundTag();
        when(area.getPersistentData()).thenReturn(data);
        assertFalse(NativeConstructionGuard.activate(area));
        assertEquals("", NativeConstructionGuard.status(area));
        Mob worker = mock(Mob.class);
        assertTrue(NativeConstructionGuard.beforeNativeTick(worker, new NativeGoal()));
        assertFalse(data.contains("SiegeProtectedConstructionV1"));
    }

    @Test void retiredReceiptCleansOnlyAnExactOldAssociationAfterTransferOrReload() {
        var worker = mock(Builder.class); var canceled = mock(Entity.class); var replacement = mock(Entity.class);
        var data = new CompoundTag(); when(worker.getPersistentData()).thenReturn(data);
        var old = java.util.UUID.randomUUID(); var next = java.util.UUID.randomUUID();
        when(canceled.getUUID()).thenReturn(old); when(replacement.getUUID()).thenReturn(next);
        data.putUUID(com.devfarinsky.siegeoverhaul.ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID, old);
        data.putUUID("SiegeProtectedAreaReceipt", old); worker.currentBuildArea = canceled;
        assertTrue(NativeConstructionGuard.retireBuilderAssociation(worker, old));
        assertNull(worker.currentBuildArea); assertFalse(data.hasUUID("SiegeProtectedAreaReceipt"));
        data.putUUID(com.devfarinsky.siegeoverhaul.ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID, next);
        data.putUUID("SiegeProtectedAreaReceipt", next); worker.currentBuildArea = replacement;
        assertTrue(NativeConstructionGuard.retireBuilderAssociation(worker, old));
        assertSame(replacement, worker.currentBuildArea);
        assertEquals(next, data.getUUID(com.devfarinsky.siegeoverhaul.ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID));
        assertEquals(next, data.getUUID("SiegeProtectedAreaReceipt"));
        verify(worker, never()).getNavigation();
        verify(worker, never()).setItemSlot(any(), any());
    }

    @Test void pendingCommissionNeverDispatchesNativeWorkUntilActivated() {
        Entity area = mock(Entity.class); CompoundTag data = new CompoundTag();
        data.put("SiegeProtectedConstructionV1", new CompoundTag());
        when(area.getPersistentData()).thenReturn(data);
        var worker = mock(Builder.class); worker.currentBuildArea = area;
        when(worker.level()).thenReturn(mock(net.minecraft.server.level.ServerLevel.class));
        assertFalse(NativeConstructionGuard.beforeNativeTick(worker, new NativeGoal()));
        assertTrue(NativeConstructionGuard.status(area).contains("commission not completed"));
        assertTrue(NativeConstructionGuard.activate(area));
        assertEquals("", NativeConstructionGuard.status(area));
        assertFalse(NativeConstructionGuard.beforeNativeTick(worker, new NativeGoal()), "An invalid saved recipe still fails closed");
    }

    private AcceptedConstructionPlan plan() {
        return AcceptedConstructionPlan.decode(BlockPos.ZERO, Direction.SOUTH, 1, 1, 1,
                AcceptedConstructionPlanTest.blueprint(1, 0, 0, 0, WALL));
    }
    public record Cell(BlockPos pos, BlockState state) {
        public BlockPos getPos() { return pos; }
        public BlockState getState() { return state; }
    }
    public static class Area {
        public Stack<Cell> stackToPlace = new Stack<>(), stackToPlaceMultiBlock = new Stack<>();
        public Stack<BlockPos> stackToBreak = new Stack<>(), stackToFree = new Stack<>();
    }
    public enum State { SELECT_WORK_AREA, MOVE_TO_WORK_AREA, PREPARE_BREAK_BLOCKS, BREAK_BLOCKS,
        PREPARE_PLACE_BLOCKS, PLACE_BLOCKS, PLACE_MULTIBLOCK, DONE }
    public static class NativeGoal extends Goal {
        public Stack<BlockPos> stackToPlace = new Stack<>(), stackToBreak = new Stack<>(), stackToFree = new Stack<>();
        public BlockPos blockPos;
        public State state;
        @Override public boolean canUse() { return true; }
    }
    public abstract static class Builder extends Mob {
        public Entity currentBuildArea;
        protected Builder(net.minecraft.world.entity.EntityType<? extends Mob> type, net.minecraft.world.level.Level level) {
            super(type, level);
        }
    }
}
