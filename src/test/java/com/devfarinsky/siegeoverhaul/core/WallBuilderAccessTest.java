package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Target;
import net.minecraft.world.phys.*;
import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WallBuilderAccessTest extends MinecraftTestSupport {
    @Test void reachableNativeCaveEndpointIsStoppedAndReplacedWithSurfaceApproach() throws Exception {
        terrain();var goal=new WallBuilderAccess(worker,new NativeGoal());
        var cave=mock(Path.class);when(cave.canReach()).thenReturn(true);
        when(cave.getEndNode()).thenReturn(new net.minecraft.world.level.pathfinder.Node(0,60,0));
        when(nav.getPath()).thenReturn(cave);
        var surface=mock(Path.class);when(surface.canReach()).thenReturn(true);
        when(surface.getTarget()).thenReturn(new BlockPos(1,64,0));when(surface.getEndNode()).thenReturn(new net.minecraft.world.level.pathfinder.Node(1,64,0));
        when(nav.createPath(anySet(),eq(0))).thenReturn(surface);
        goal.route(level,new BlockPos(0,60,0),20);
        verify(nav).stop();verify(nav).moveTo(1,64,0,0.8);
        verify(level,never()).setBlock(any(),any(),anyInt());
    }
    @Test void reachableSurfaceEndpointKeepsItsNativeMovementPath() throws Exception {
        terrain();var goal=new WallBuilderAccess(worker,new NativeGoal());
        var path=mock(Path.class);when(path.canReach()).thenReturn(true);
        when(path.getEndNode()).thenReturn(new net.minecraft.world.level.pathfinder.Node(1,64,0));
        when(nav.getPath()).thenReturn(path);
        goal.route(level,new BlockPos(0,60,0),20);
        verify(nav,never()).stop();verify(nav,never()).createPath(anySet(),anyInt());
    }
    @Test void horizontalReachDoesNotKeepWorkerDirectlyBelowBuriedJob() throws Exception {
        terrain();var goal=new WallBuilderAccess(worker,new NativeGoal());
        when(worker.getX()).thenReturn(0.5);when(worker.getZ()).thenReturn(0.5);
        when(worker.position()).thenReturn(new Vec3(0.5,60,0.5));
        when(worker.getBoundingBox()).thenReturn(new AABB(0.2,60,0.2,0.8,61.8,0.8));
        var path=mock(Path.class);when(path.canReach()).thenReturn(true);
        when(path.getTarget()).thenReturn(new BlockPos(1,64,0));when(path.getEndNode()).thenReturn(new net.minecraft.world.level.pathfinder.Node(1,64,0));
        when(nav.createPath(anySet(),eq(0))).thenReturn(path);
        goal.route(level,new BlockPos(0,60,0),20);
        verify(nav).moveTo(1,64,0,0.8);
        verify(worker,never()).teleportTo(anyDouble(),anyDouble(),anyDouble());
    }
    @Test void cachedStandingSpaceIsNotReusedAfterItsFloorBecomesHazardous() throws Exception {
        terrain();var goal=new WallBuilderAccess(worker,new NativeGoal());
        var path=mock(Path.class);when(path.canReach()).thenReturn(true);
        when(path.getTarget()).thenReturn(new BlockPos(1,64,0));when(path.getEndNode()).thenReturn(new net.minecraft.world.level.pathfinder.Node(1,64,0));
        when(nav.createPath(anySet(),eq(0))).thenReturn(path);
        when(nav.moveTo(1,64,0,0.8)).thenReturn(true);
        goal.route(level,new BlockPos(0,60,0),20);
        clearInvocations(nav);
        doReturn(Blocks.MAGMA_BLOCK.defaultBlockState()).when(level).getBlockState(new BlockPos(1,63,0));
        when(level.getGameTime()).thenReturn(10L);
        goal.route(level,new BlockPos(0,60,0),20);
        verify(nav,never()).moveTo(anyDouble(),anyDouble(),anyDouble(),anyDouble());
    }
    @Test void selfRecoveryDoesNotMistakeAnUnreservedFootCellForClearBodySpace() throws Exception {
        terrain();var goal=new WallBuilderAccess(worker,new NativeGoal());
        when(worker.getX()).thenReturn(-.04);when(worker.getZ()).thenReturn(-.02);
        when(worker.position()).thenReturn(new Vec3(-.04,64,-.02));
        when(worker.getBbWidth()).thenReturn(.6f);
        when(worker.getBoundingBox()).thenReturn(new AABB(-.34,64,-.32,.26,65.95,.28));
        var target=new BlockPos(0,64,0);
        assertTrue(worker.getBoundingBox().intersects(new AABB(target)));
        // The old non-recovery path accepts this floored neighboring standing cell.
        goal.route(level,target,40);verify(nav,never()).createPath(anySet(),eq(0));
        var field=WallBuilderAccess.class.getDeclaredField("reservedColumns");field.setAccessible(true);
        field.set(goal,java.util.Set.of(target.atY(0).asLong()));
        Path clear=mock(Path.class);when(clear.canReach()).thenReturn(true);when(clear.getTarget()).thenReturn(new BlockPos(-2,64,0));when(clear.getEndNode()).thenReturn(new net.minecraft.world.level.pathfinder.Node(-2,64,0));
        when(nav.createPath(anySet(),eq(0))).thenReturn(clear);
        goal.route(level,target,40,true);
        verify(nav).createPath(argThat((java.util.Set<BlockPos> sites)->!sites.isEmpty()
                && sites.stream().allMatch(p->WallBuilderAccess.recoveryMargin(p,java.util.Set.of(target.atY(0).asLong()),.6f))
                && sites.stream().allMatch(p->p.distSqr(target.atY(p.getY()))<40)),eq(0));
        verify(nav).moveTo(-2,64,0,0.8);
        verify(worker,never()).teleportTo(anyDouble(),anyDouble(),anyDouble());
        verify(level,never()).setBlock(any(),any(),anyInt());
    }
    @Test void onlyTheGuardsOwnBodyObstructionAllowsRecoveryWhileNativeWorkStaysPaused() throws Exception {
        var area=commission();var original=spy(new NativeGoal()); original.state=State.PLACE_BLOCKS;
        var target=new BlockPos(0,64,0);original.blockPos=target;
        area.stackToPlace=java.util.List.of(new Cell(target));
        var goal=new WallBuilderAccess(worker,original);
        when(worker.getX()).thenReturn(-.04);when(worker.getZ()).thenReturn(-.02);
        when(worker.position()).thenReturn(new Vec3(-.04,64,-.02));when(worker.getBbWidth()).thenReturn(.6f);
        when(worker.getBoundingBox()).thenReturn(new AABB(-.34,64,-.32,.26,65.95,.28));
        var path=mock(Path.class);when(path.canReach()).thenReturn(true);when(path.getTarget()).thenReturn(new BlockPos(-2,64,0));when(path.getEndNode()).thenReturn(new net.minecraft.world.level.pathfinder.Node(-2,64,0));
        when(nav.createPath(anySet(),eq(0))).thenReturn(path);
        try(var guard=mockStatic(com.devfarinsky.siegeoverhaul.nativecompat.NativeConstructionGuard.class,CALLS_REAL_METHODS)) {
            guard.when(()->com.devfarinsky.siegeoverhaul.nativecompat.NativeConstructionGuard.beforeNativeTick(worker,original)).thenReturn(false);
            guard.when(()->com.devfarinsky.siegeoverhaul.nativecompat.NativeConstructionGuard.mutationCells(original)).thenReturn(java.util.Set.of(target));
            guard.when(()->com.devfarinsky.siegeoverhaul.nativecompat.NativeConstructionGuard.status(area)).thenReturn("Paused: faction territory changed");
            goal.tick();verifyNoInteractions(nav);verify(original,never()).tick();
            guard.when(()->com.devfarinsky.siegeoverhaul.nativecompat.NativeConstructionGuard.status(area)).thenReturn("Paused: move entities out of the planned blocks");
            goal.tick();verify(nav).moveTo(-2,64,0,0.8);verify(original,never()).tick();
            assertEquals(target,original.blockPos);assertEquals(State.PLACE_BLOCKS,original.state);
            assertEquals(1,worker.getPersistentData().getInt("SiegeSelfClearanceRequests"));
            clearInvocations(nav);when(worker.getBoundingBox()).thenReturn(new AABB(4.2,64,4.2,4.8,65.95,4.8));
            goal.tick();verifyNoInteractions(nav); // A different occupant is never moved or ignored.
        }
        verify(worker,never()).teleportTo(anyDouble(),anyDouble(),anyDouble());
        verify(level,never()).setBlock(any(),any(),anyInt());
    }
    @Test void recoveryMarginRejectsBoundaryStraddlingAndUnsupportedWidths() {
        var reserved=java.util.Set.of(new BlockPos(142,0,11).asLong());
        assertFalse(WallBuilderAccess.recoveryMargin(new BlockPos(141,65,10),reserved,.6f));
        assertTrue(WallBuilderAccess.recoveryMargin(new BlockPos(140,65,10),reserved,.6f));
        assertFalse(WallBuilderAccess.recoveryMargin(BlockPos.ZERO,reserved,Float.NaN));
        assertFalse(WallBuilderAccess.recoveryMargin(BlockPos.ZERO,reserved,5));
    }
    @Test void guardedRecoveryNeverProbesUnloadedOrUnsafeTerrain() throws Exception {
        terrain();var goal=new WallBuilderAccess(worker,new NativeGoal());
        when(level.hasChunkAt(any())).thenReturn(false);
        goal.route(level,new BlockPos(0,64,0),40,true);
        verify(level,never()).getHeight(any(),anyInt(),anyInt());verify(nav,never()).moveTo(anyDouble(),anyDouble(),anyDouble(),anyDouble());
        terrain();doReturn(Blocks.LAVA.defaultBlockState()).when(level).getBlockState(any());
        when(level.getGameTime()).thenReturn(100L);goal.route(level,new BlockPos(0,64,0),40,true);
        verify(nav,never()).moveTo(anyDouble(),anyDouble(),anyDouble(),anyDouble());
    }
    public abstract static class Builder extends Mob {
        public Entity currentBuildArea;
        public boolean isFleeing;
        public java.util.UUID getOwnerUUID() { return null; }
        public int getFollowState() { return 6; }
        protected Builder(EntityType<? extends Mob> type, Level level) { super(type, level); }
    }
    public abstract static class Area extends Entity {
        public java.util.List<Cell> stackToPlace;
        public java.util.List<Cell> stackToPlaceMultiBlock;
        protected Area(EntityType<?> type, Level level) { super(type,level); }
        public java.util.UUID getPlayerUUID() { return null; }
        public boolean isDone() { return false; }
        public boolean getFreeArea() { return false; }
        public boolean canWorkHere(Builder builder) { return false; }
        public void setBeingWorkedOn(boolean value) { }
        public void setTime(int value) { }
    }
    public record Cell(BlockPos pos) { public BlockPos getPos() { return pos; } }
    public enum State { SELECT_WORK_AREA, MOVE_TO_WORK_AREA, PREPARE_BREAK_BLOCKS, PLACE_BLOCKS, BREAK_BLOCKS, DONE }
    public static class NativeGoal extends Goal {
        public BlockPos blockPos;
        public Object state;
        boolean workDone;
        @Override public boolean canUse() { return false; }
    }
    private final Builder worker = mock(Builder.class);
    private final ServerLevel level = mock(ServerLevel.class);
    private final PathNavigation nav = mock(PathNavigation.class);
    private void terrain() {
        when(worker.getNavigation()).thenReturn(nav);
        when(worker.getX()).thenReturn(20.5);
        when(worker.position()).thenReturn(new Vec3(20.5,64,0.5));
        when(worker.getBoundingBox()).thenReturn(new AABB(20.2,64,0.2,20.8,65.8,0.8));
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getMinBuildHeight()).thenReturn(-64); when(level.getMaxBuildHeight()).thenReturn(320);
        when(level.getHeight(any(),anyInt(),anyInt())).thenReturn(64);
        when(level.getWorldBorder()).thenReturn(new WorldBorder());
        doAnswer(i -> ((BlockPos)i.getArgument(0)).getY()<64
                ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState()).when(level).getBlockState(any());
        when(level.noCollision(eq(worker),any(AABB.class))).thenReturn(true);
    }
    @Test void buriedMarkerUsesReachableSurfaceWithoutMovingBlueprintOrPlacingBlocks() throws Exception {
        terrain(); var nativeGoal = mock(NativeGoal.class);
        when(nativeGoal.getFlags()).thenReturn(EnumSet.of(Goal.Flag.MOVE));
        var goal = new WallBuilderAccess(worker,nativeGoal);
        BlockPos marker = new BlockPos(0,60,0);
        var sites = WallBuilderAccess.standingSites(level,worker,marker);
        assertFalse(sites.isEmpty());
        assertTrue(sites.stream().allMatch(p -> p.getY()==64 && p.distSqr(new BlockPos(0,64,0))<16));
        Path path=mock(Path.class); when(path.canReach()).thenReturn(true);
        when(path.getTarget()).thenReturn(new BlockPos(1,64,0));when(path.getEndNode()).thenReturn(new net.minecraft.world.level.pathfinder.Node(1,64,0));
        when(nav.createPath(anySet(),eq(0))).thenReturn(path); when(nav.moveTo(1,64,0,0.8)).thenReturn(true);
        goal.route(level,marker,20); verify(nav).moveTo(1,64,0,0.8);
        when(level.getGameTime()).thenReturn(10L);goal.route(level,marker,20);
        verify(nav,times(1)).createPath(anySet(),eq(0));
        verify(level,never()).setBlock(any(),any(),anyInt());
        verify(worker,never()).teleportTo(anyDouble(),anyDouble(),anyDouble());
    }
    @Test void loadedSafeSpaceAndReachabilityAreRequired() throws Exception {
        terrain(); var goal=new WallBuilderAccess(worker,new NativeGoal());
        var marker=new BlockPos(0,60,0);
        when(level.hasChunkAt(any())).thenReturn(false);
        assertTrue(WallBuilderAccess.standingSites(level,worker,marker).isEmpty());
        when(level.hasChunkAt(any())).thenReturn(true);
        doReturn(Blocks.WATER.defaultBlockState()).when(level).getBlockState(any());
        assertTrue(WallBuilderAccess.standingSites(level,worker,marker).isEmpty());
        terrain(); Path unreachable=mock(Path.class);when(nav.createPath(anySet(),eq(0))).thenReturn(unreachable);
        goal.route(level,marker,20); verify(nav,never()).moveTo(any(Path.class),anyDouble());
            verify(nav,never()).moveTo(anyDouble(),anyDouble(),anyDouble(),anyDouble());
    }
    @Test void correctionRunsOnlyForTheLinkedOwnedActiveWallJob() throws Exception {
        terrain(); var original=new NativeGoal(); original.state=State.MOVE_TO_WORK_AREA;
        var goal=new WallBuilderAccess(worker,original); var area=mock(Area.class);
        var data=new net.minecraft.nbt.CompoundTag(); var owner=java.util.UUID.randomUUID();
        var id=java.util.UUID.randomUUID();worker.currentBuildArea=area;
        area.stackToPlace=java.util.List.of(new Cell(new BlockPos(0,64,0)));
        area.stackToPlaceMultiBlock=java.util.List.of();
        when(worker.level()).thenReturn(level);when(worker.getPersistentData()).thenReturn(data);
        when(area.isAlive()).thenReturn(true);when(area.getUUID()).thenReturn(id);
        when(area.getOnPos()).thenReturn(new BlockPos(0,60,0));
        when(area.getPlayerUUID()).thenReturn(owner);when(worker.getOwnerUUID()).thenReturn(owner);
        when(worker.getFollowState()).thenReturn(6);
        goal.tick();verifyNoInteractions(nav);
        data.putUUID(com.devfarinsky.siegeoverhaul.ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID,id);
        data.putUUID(com.devfarinsky.siegeoverhaul.ModConstants.Tags.PLAYER_FORTIFICATION_OWNER,owner);
        goal.tick();verify(nav).createPath(argThat((java.util.Set<BlockPos> sites) -> !sites.isEmpty()
                && !sites.contains(new BlockPos(0,64,0))),eq(0));
        clearInvocations(nav);when(worker.getFollowState()).thenReturn(1);
        when(level.getGameTime()).thenReturn(80L);goal.tick();verifyNoInteractions(nav);
        when(worker.getFollowState()).thenReturn(6);when(worker.getOwnerUUID()).thenReturn(java.util.UUID.randomUUID());
        goal.tick();verifyNoInteractions(nav);
    }
    @Test void normalBlockWorkKeepsTheNativeFortySquaredReach() throws Exception {
        terrain();var goal=new WallBuilderAccess(worker,new NativeGoal());
        when(worker.getX()).thenReturn(5.5);when(worker.getZ()).thenReturn(0.5);
        var target=new BlockPos(0,64,0);
        goal.route(level,target,40);
        verifyNoInteractions(nav);
        goal.route(level,target,20);
        verify(nav).createPath(anySet(),eq(0));
    }
    @Test void nativeSleepSupplyAndOwnerCommandsRemainAuthoritative() throws Exception {
        NativeGoal original=mock(NativeGoal.class);when(original.getFlags()).thenReturn(EnumSet.of(Goal.Flag.MOVE));
        var goal=new WallBuilderAccess(worker,original);
        assertFalse(goal.canUse());assertFalse(goal.canContinueToUse());
        when(original.canUse()).thenReturn(true);when(original.canContinueToUse()).thenReturn(true);
        assertTrue(goal.canUse());assertTrue(goal.canContinueToUse());goal.start();goal.tick();goal.stop();
        verify(original).start();verify(original).tick();verify(original).stop();verifyNoInteractions(level);
    }
    /** Contract fixture: native selection replaces an existing area with the next eligible one. */
    public static class SelectingGoal extends NativeGoal {
        final Builder builder;
        Entity competing;
        int selections;
        SelectingGoal(Builder builder, Entity competing) { this.builder = builder; this.competing = competing; }
        @Override public void start() { state = State.SELECT_WORK_AREA; }
        @Override public void tick() {
            if (state == State.SELECT_WORK_AREA) {
                selections++;
                if (competing != null) builder.currentBuildArea = competing;
                workDone = false;
                state = State.MOVE_TO_WORK_AREA;
            }
        }
    }
    private Area commission() {
        terrain();
        var area = mock(Area.class);
        var owner = java.util.UUID.randomUUID(); var id = java.util.UUID.randomUUID();
        var data = new net.minecraft.nbt.CompoundTag();
        data.putUUID(com.devfarinsky.siegeoverhaul.ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID, id);
        data.putUUID(com.devfarinsky.siegeoverhaul.ModConstants.Tags.PLAYER_FORTIFICATION_OWNER, owner);
        when(worker.level()).thenReturn(level); when(worker.getPersistentData()).thenReturn(data);
        when(worker.getOwnerUUID()).thenReturn(owner); when(worker.getFollowState()).thenReturn(6);
        when(area.getPlayerUUID()).thenReturn(owner); when(area.getUUID()).thenReturn(id);
        when(area.canWorkHere(worker)).thenReturn(true);
        when(area.isAlive()).thenReturn(true); when(area.getOnPos()).thenReturn(new BlockPos(0,60,0));
        area.stackToPlace = java.util.List.of(); area.stackToPlaceMultiBlock = java.util.List.of();
        worker.currentBuildArea = area;
        return area;
    }

    public static class ApproachingGoal extends NativeGoal {
        int ticks;
        @Override public void tick() { ticks++; state=State.DONE; }
    }
    @Test void nearWallStartsNativePreparationWithoutVisitingFarBlueprintCorner() throws Exception {
        var area=commission();when(worker.getZ()).thenReturn(0.5);
        when(area.getOnPos()).thenReturn(new BlockPos(200,20,200));
        area.stackToPlace=java.util.List.of(new Cell(new BlockPos(21,64,0)),new Cell(new BlockPos(200,20,200)));
        var nativeGoal=new NativeGoal();nativeGoal.state=State.MOVE_TO_WORK_AREA;
        nativeGoal.blockPos=new BlockPos(200,20,200);
        new WallBuilderAccess(worker,nativeGoal).tick();
        assertEquals(State.PREPARE_BREAK_BLOCKS,nativeGoal.state);assertNull(nativeGoal.blockPos);
        verify(nav).stop();verify(nav,never()).createPath(anySet(),anyInt());
        verify(level,never()).setBlock(any(),any(),anyInt());
        assertEquals(2,area.stackToPlace.size(),"Workers must still consume supplies and construct the plan");
    }
    @Test void farWorkerRoutesToNearestRemainingWallInsteadOfItsBlueprintCorner() throws Exception {
        var area=commission();when(worker.getZ()).thenReturn(0.5);
        when(area.getOnPos()).thenReturn(new BlockPos(200,20,200));
        area.stackToPlace=java.util.List.of(new Cell(new BlockPos(0,64,0)),new Cell(new BlockPos(200,20,200)));
        var nativeGoal=new ApproachingGoal();nativeGoal.state=State.MOVE_TO_WORK_AREA;
        new WallBuilderAccess(worker,nativeGoal).tick();
        assertEquals(0,nativeGoal.ticks);
        verify(nav).createPath(argThat((java.util.Set<BlockPos> sites)->!sites.isEmpty()
                && sites.stream().allMatch(p->Math.abs(p.getX())<=3 && Math.abs(p.getZ())<=3)
                && !sites.contains(new BlockPos(0,64,0))),eq(0));
    }
    @Test void workerStandingInQueuedWallColumnMovesAsideBeforeNativeConstruction() throws Exception {
        var area=commission();when(worker.getZ()).thenReturn(0.5);
        area.stackToPlace=java.util.List.of(new Cell(new BlockPos(20,64,0)));
        var nativeGoal=new ApproachingGoal();nativeGoal.state=State.MOVE_TO_WORK_AREA;
        new WallBuilderAccess(worker,nativeGoal).tick();
        assertEquals(0,nativeGoal.ticks);assertEquals(State.MOVE_TO_WORK_AREA,nativeGoal.state);
        verify(nav).createPath(argThat((java.util.Set<BlockPos> sites)->!sites.isEmpty()
                && sites.stream().noneMatch(p->p.getX()==20 && p.getZ()==0)),eq(0));
        verify(level,never()).setBlock(any(),any(),anyInt());
    }
    @Test void manualClearingPhaseRemainsNativeEvenWithNearWall() throws Exception {
        var area=commission();when(area.getFreeArea()).thenReturn(true);when(worker.getZ()).thenReturn(0.5);
        area.stackToPlace=java.util.List.of(new Cell(new BlockPos(21,64,0)));
        var nativeGoal=new NativeGoal();nativeGoal.state=State.MOVE_TO_WORK_AREA;
        new WallBuilderAccess(worker,nativeGoal).tick();
        assertEquals(State.MOVE_TO_WORK_AREA,nativeGoal.state);
    }
    @Test void supplyRestartReevaluatesRemainingWorkInsteadOfCompletedWallSection() throws Exception {
        var area=commission();when(worker.getZ()).thenReturn(0.5);
        area.stackToPlace=java.util.List.of(new Cell(new BlockPos(21,64,0)));
        var nativeGoal=new NativeGoal();nativeGoal.state=State.MOVE_TO_WORK_AREA;
        var goal=new WallBuilderAccess(worker,nativeGoal);goal.tick();
        assertEquals(State.PREPARE_BREAK_BLOCKS,nativeGoal.state);
        goal.stop();goal.start();nativeGoal.state=State.MOVE_TO_WORK_AREA;
        area.stackToPlace=java.util.List.of(new Cell(new BlockPos(0,64,0)));
        clearInvocations(nav);goal.tick();
        assertEquals(State.MOVE_TO_WORK_AREA,nativeGoal.state);
        verify(nav).createPath(anySet(),eq(0));
    }
    @Test void nativePhaseCannotAdvanceFromDirectlyUnderneathTheOwnedMarker() throws Exception {
        var area=commission();area.stackToPlace=java.util.List.of(new Cell(new BlockPos(0,64,0)));
        when(worker.getX()).thenReturn(0.5);when(worker.getZ()).thenReturn(0.5);
        when(worker.position()).thenReturn(new Vec3(0.5,60,0.5));
        when(worker.getBoundingBox()).thenReturn(new AABB(0.2,60,0.2,0.8,61.8,0.8));
        var nativeGoal=new ApproachingGoal();nativeGoal.state=State.MOVE_TO_WORK_AREA;
        var goal=new WallBuilderAccess(worker,nativeGoal);
        var path=mock(Path.class);when(path.canReach()).thenReturn(true);
        when(path.getTarget()).thenReturn(new BlockPos(1,64,0));when(path.getEndNode()).thenReturn(new net.minecraft.world.level.pathfinder.Node(1,64,0));
        when(nav.createPath(anySet(),eq(0))).thenReturn(path);
        goal.tick();assertEquals(0,nativeGoal.ticks);assertEquals(State.MOVE_TO_WORK_AREA,nativeGoal.state);
        verify(nav).moveTo(1,64,0,0.8);
        when(worker.position()).thenReturn(new Vec3(1.5,64,0.5));
        when(worker.getBoundingBox()).thenReturn(new AABB(1.2,64,0.2,1.8,65.8,0.8));
        goal.tick();assertEquals(1,nativeGoal.ticks);
    }
    @Test void assignedWallSurvivesCompetingBlueprintAndSupplyRestart() throws Exception {
        var wall = commission(); var other = mock(Area.class);
        var original = new SelectingGoal(worker, other);
        var goal = new WallBuilderAccess(worker, original);
        for (int trip = 0; trip < 3; trip++) {
            original.workDone = true;
            original.blockPos = new BlockPos(50,20,50);
            goal.start(); goal.tick();
            assertSame(wall, worker.currentBuildArea);
            assertEquals(State.MOVE_TO_WORK_AREA, original.state);
            assertFalse(original.workDone, "Native completion latch must reset for resumed/new jobs");
            assertNull(original.blockPos, "Discard the previous job's movement target");
            goal.stop();
        }
        assertEquals(0, original.selections);
        verify(wall, times(3)).setBeingWorkedOn(true);
        verify(wall, times(3)).setTime(0);
        verify(other, never()).setBeingWorkedOn(anyBoolean());
        verify(worker, never()).teleportTo(anyDouble(),anyDouble(),anyDouble());
        verify(level, never()).setBlock(any(),any(),anyInt());
    }
    @Test void unlinkedManualTransferredRemovedAndCompletedAreasKeepNativeSelection() throws Exception {
        for (int scenario = 0; scenario < 6; scenario++) {
            var wall = commission(); var other = mock(Area.class);
            switch (scenario) {
                case 0 -> worker.getPersistentData().remove(com.devfarinsky.siegeoverhaul.ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID);
                case 1 -> worker.currentBuildArea = other;
                case 2 -> when(worker.getOwnerUUID()).thenReturn(java.util.UUID.randomUUID());
                case 3 -> when(wall.isRemoved()).thenReturn(true);
                case 4 -> when(wall.isDone()).thenReturn(true);
                case 5 -> when(wall.canWorkHere(worker)).thenReturn(false);
            }
            var original = new SelectingGoal(worker, other); var goal = new WallBuilderAccess(worker, original);
            goal.start(); goal.tick();
            assertEquals(1, original.selections);
            assertSame(other, worker.currentBuildArea);
            verify(wall, never()).setBeingWorkedOn(anyBoolean());
        }
    }
    public abstract static class DelayedPath extends Path {
        protected DelayedPath() { super(java.util.List.of(), BlockPos.ZERO, false); }
        public abstract boolean isProcessed();
    }
    @Test void delayedNativePathIsRetainedUntilReadyWithoutRepeatedSearch() throws Exception {
        terrain(); var goal = new WallBuilderAccess(worker, new NativeGoal());
        var target = new BlockPos(0,60,0); var endpoint = new BlockPos(1,64,0);
        var path = mock(DelayedPath.class);
        when(nav.createPath(anySet(),eq(0))).thenReturn(path);
        goal.route(level,target,20);
        when(level.getGameTime()).thenReturn(40L); goal.route(level,target,20);
        verify(nav, never()).moveTo(any(Path.class),anyDouble());
        verify(nav, never()).moveTo(anyDouble(),anyDouble(),anyDouble(),anyDouble());
        verify(path, never()).getTarget();
        when(path.isProcessed()).thenReturn(true); when(path.canReach()).thenReturn(true);
        when(path.getTarget()).thenReturn(endpoint);when(path.getEndNode()).thenReturn(new net.minecraft.world.level.pathfinder.Node(1,64,0)); when(nav.moveTo(1,64,0,0.8)).thenReturn(true);
        when(level.getGameTime()).thenReturn(50L); goal.route(level,target,20);
        verify(nav).moveTo(1,64,0,0.8); verify(nav,times(1)).createPath(anySet(),eq(0));
        verify(nav,never()).moveTo(any(Path.class),anyDouble());
    }
    @Test void staleStoppedTimedOutAndUnsafeDelayedRoutesCannotBeInstalled() throws Exception {
        for (int scenario = 0; scenario < 5; scenario++) {
            reset(nav); terrain();
            var goal = new WallBuilderAccess(worker,new NativeGoal());
            var target = new BlockPos(0,60,0); var path = mock(DelayedPath.class);
            when(nav.createPath(anySet(),eq(0))).thenReturn(path);
            when(level.getGameTime()).thenReturn(0L); goal.route(level,target,20);
            when(nav.createPath(anySet(),eq(0))).thenReturn(null);
            when(path.isProcessed()).thenReturn(true); when(path.canReach()).thenReturn(true);
            when(path.getTarget()).thenReturn(new BlockPos(1,64,0));when(path.getEndNode()).thenReturn(new net.minecraft.world.level.pathfinder.Node(1,64,0));
            when(level.getGameTime()).thenReturn(10L);
            switch (scenario) {
                case 0 -> target = new BlockPos(3,60,0);
                case 1 -> goal.stop();
                case 2 -> when(level.getGameTime()).thenReturn(110L);
                case 3 -> when(level.noCollision(eq(worker),any(AABB.class))).thenReturn(false);
                case 4 -> when(path.canReach()).thenReturn(false);
            }
            goal.route(level,target,20);
            verify(nav,never()).moveTo(any(Path.class),anyDouble());
            verify(nav,never()).moveTo(anyDouble(),anyDouble(),anyDouble(),anyDouble());
        }
    }
    @Test void existingAsyncNavigationIsLeftAloneWhileItProcesses() throws Exception {
        terrain(); var goal = new WallBuilderAccess(worker,new NativeGoal());
        when(nav.getPath()).thenReturn(mock(DelayedPath.class));
        goal.route(level,new BlockPos(0,60,0),20);
        verify(nav,never()).createPath(anySet(),anyInt());
    }

    @Test void negativeEndpointUsesNativeMovementWithoutTruncatingIntoAnotherColumn() throws Exception {
        terrain(); var goal = new WallBuilderAccess(worker,new NativeGoal());
        var path = mock(Path.class); when(path.canReach()).thenReturn(true);
        when(path.getTarget()).thenReturn(new BlockPos(-1,64,-2));when(path.getEndNode()).thenReturn(new net.minecraft.world.level.pathfinder.Node(-1,64,-2));
        when(nav.createPath(anySet(),eq(0))).thenReturn(path);
        goal.route(level,new BlockPos(-2,60,-2),20);
        verify(nav).moveTo(-1,64,-2,0.8);
        verify(nav,never()).moveTo(any(Path.class),anyDouble());
    }

    @Test void nativeMultiTargetProbeUsesReachedGroundInsteadOfItsUnreachableRoofLabel() throws Exception {
        for (boolean delayed : new boolean[]{false,true}) {
            reset(nav);terrain();
            when(worker.getX()).thenReturn(129.94);when(worker.getZ()).thenReturn(3.92);
            when(worker.position()).thenReturn(new Vec3(129.94,65,3.92));
            when(worker.getBoundingBox()).thenReturn(new AABB(129.64,65,3.62,130.24,66.95,4.22));
            when(level.getHeight(any(),anyInt(),anyInt())).thenAnswer(i ->
                    (int)i.getArgument(1)==128 ? 70 : (int)i.getArgument(1)==129 ? 69 : 65);
            doAnswer(i -> {
                BlockPos p=i.getArgument(0);
                int top=p.getX()==128 ? 70 : p.getX()==129 ? 69 : 65;
                return p.getY()<top ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
            }).when(level).getBlockState(any());
            var roof=new BlockPos(128,70,3);var ground=new BlockPos(132,65,3);
            Path nativePath=nativeMultiTargetProbe(roof,ground);
            assertTrue(nativePath.canReach());assertEquals(roof,nativePath.getTarget(),
                    "Pinned Workers 2.0.3 labels the probe with its first candidate");
            assertEquals(new Node(132,65,3),nativePath.getEndNode(),
                    "The path really reached a different, ground-level candidate");
            var goal=new WallBuilderAccess(worker,new NativeGoal());
            Path probe=nativePath;
            if (delayed) {
                var pending=mock(DelayedPath.class);probe=pending;
                when(pending.canReach()).thenReturn(true);when(pending.getTarget()).thenReturn(roof);
                when(pending.getEndNode()).thenReturn(nativePath.getEndNode());
            }
            when(nav.createPath(anySet(),eq(0))).thenReturn(probe);
            when(level.getGameTime()).thenReturn(0L);goal.route(level,new BlockPos(130,68,3),20);
            if (delayed) {
                verify(probe,never()).getEndNode();
                verify(nav,never()).moveTo(anyDouble(),anyDouble(),anyDouble(),anyDouble());
                when(((DelayedPath)probe).isProcessed()).thenReturn(true);
                when(level.getGameTime()).thenReturn(10L);goal.route(level,new BlockPos(130,68,3),20);
            }
            verify(nav).createPath(argThat((java.util.Set<BlockPos> sites)->sites.contains(roof)&&sites.contains(ground)),eq(0));
            verify(nav).moveTo(132,65,3,0.8);
            verify(nav,never()).moveTo(128,70,3,0.8);
            verify(nav,never()).moveTo(any(Path.class),anyDouble());
            verify(level,never()).setBlock(any(),any(),anyInt());
            verify(worker,never()).teleportTo(anyDouble(),anyDouble(),anyDouble());
        }
    }

    /** Execute the supported companion's actual multi-target reconstruction, with a bounded node graph. */
    private Path nativeMultiTargetProbe(BlockPos roof,BlockPos ground) throws Exception {
        var evaluator=mock(NodeEvaluator.class);
        doAnswer(i -> {
            Node[] neighbors=i.getArgument(0);Node node=i.getArgument(1);
            if (node.x>=ground.getX()) return 0;
            neighbors[0]=new Node(node.x+1,ground.getY(),ground.getZ());return 1;
        }).when(evaluator).getNeighbors(any(Node[].class),any(Node.class));
        var type=com.talhanation.workers.entities.ai.navigation.WorkersAsyncPathfinder.class;
        var method=type.getDeclaredMethod("processPath",NodeEvaluator.class,Node.class,java.util.List.class,
                float.class,int.class,float.class,int.class);
        method.setAccessible(true);
        return (Path)method.invoke(new com.talhanation.workers.entities.ai.navigation.WorkersAsyncPathfinder(evaluator,16),
                evaluator,new Node(129,65,3),java.util.List.of(
                        java.util.Map.entry(new Target(roof.getX(),roof.getY(),roof.getZ()),roof),
                        java.util.Map.entry(new Target(ground.getX(),ground.getY(),ground.getZ()),ground)),32f,0,1f,1);
    }

    @Test void reachableProbeRequiresAnActualCandidateEndpointNotOnlyAValidTargetLabel() throws Exception {
        for (Node end : new Node[]{null,new Node(1,65,0),new Node(10,64,0)}) {
            reset(nav);terrain();var goal=new WallBuilderAccess(worker,new NativeGoal());
            Path probe=mock(Path.class);when(probe.canReach()).thenReturn(true);
            when(probe.getTarget()).thenReturn(new BlockPos(1,64,0));when(probe.getEndNode()).thenReturn(end);
            when(nav.createPath(anySet(),eq(0))).thenReturn(probe);
            goal.route(level,new BlockPos(0,64,0),20);
            verify(nav,never()).moveTo(anyDouble(),anyDouble(),anyDouble(),anyDouble());
        }
    }

    @Test void delayedActualEndpointMustStillBeLoadedSafeAndUnreserved() throws Exception {
        for (int scenario=0;scenario<4;scenario++) {
            reset(nav);terrain();var goal=new WallBuilderAccess(worker,new NativeGoal());
            var site=new BlockPos(1,64,0);var probe=mock(DelayedPath.class);
            when(probe.canReach()).thenReturn(true);when(probe.getTarget()).thenReturn(new BlockPos(-1,64,0));
            when(probe.getEndNode()).thenReturn(new Node(1,64,0));
            when(nav.createPath(anySet(),eq(0))).thenReturn(probe);
            when(level.getGameTime()).thenReturn(0L);goal.route(level,BlockPos.ZERO.atY(64),20);
            when(probe.isProcessed()).thenReturn(true);
            switch (scenario) {
                case 0 -> when(level.hasChunkAt(site)).thenReturn(false);
                case 1 -> doReturn(Blocks.MAGMA_BLOCK.defaultBlockState()).when(level).getBlockState(site.below());
                case 2 -> {
                    var field=WallBuilderAccess.class.getDeclaredField("reservedColumns");field.setAccessible(true);
                    field.set(goal,java.util.Set.of(site.atY(0).asLong()));
                }
                case 3 -> when(probe.getEndNode()).thenReturn(new Node(10,64,0));
            }
            when(level.getGameTime()).thenReturn(10L);goal.route(level,BlockPos.ZERO.atY(64),20);
            verify(nav,never()).moveTo(anyDouble(),anyDouble(),anyDouble(),anyDouble());
        }
    }

    @Test void budgetLimitedNativePathCanWalkCloserWithoutBeingTreatedAsWorkArrival() throws Exception {
        for (boolean delayed : new boolean[]{false, true}) {
            reset(nav); terrain();
            var target = new BlockPos(80, 64, 0);
            var endpoint = new BlockPos(26, 64, 0);
            Path partial = nativeLimitedProbe();
            assertFalse(partial.canReach());
            assertTrue(partial.getNodeCount() > 1);
            assertEquals(new Node(26, 64, 0), partial.getEndNode());
            Path probe = partial;
            if (delayed) {
                var pending = mock(DelayedPath.class); probe = pending;
                when(pending.getNodeCount()).thenReturn(partial.getNodeCount());
                when(pending.getEndNode()).thenReturn(partial.getEndNode());
            }
            when(nav.createPath(anySet(), eq(0))).thenReturn(probe);
            when(nav.moveTo(26, 64, 0, .8)).thenReturn(true);
            var goal = new WallBuilderAccess(worker, new NativeGoal());
            when(level.getGameTime()).thenReturn(0L); goal.route(level, target, 40);
            if (delayed) {
                verify(nav, never()).moveTo(anyDouble(), anyDouble(), anyDouble(), anyDouble());
                when(((DelayedPath) probe).isProcessed()).thenReturn(true);
                when(level.getGameTime()).thenReturn(10L); goal.route(level, target, 40);
            }
            verify(nav).moveTo(endpoint.getX(), endpoint.getY(), endpoint.getZ(), .8);
            verify(nav, never()).moveTo(any(Path.class), anyDouble());
            var movement = mock(Path.class);
            when(movement.canReach()).thenReturn(true); when(movement.getEndNode()).thenReturn(partial.getEndNode());
            when(nav.getPath()).thenReturn(movement);
            clearInvocations(nav);
            when(level.getGameTime()).thenReturn(20L); goal.route(level, target, 40);
            verify(nav, never()).stop(); verify(nav, never()).createPath(anySet(), anyInt());
            verify(worker, never()).teleportTo(anyDouble(), anyDouble(), anyDouble());
        }
    }

    private Path nativeLimitedProbe() throws Exception {
        var evaluator = mock(NodeEvaluator.class);
        doAnswer(i -> {
            Node[] neighbors = i.getArgument(0); Node node = i.getArgument(1);
            neighbors[0] = new Node(node.x + 1, 64, 0); return 1;
        }).when(evaluator).getNeighbors(any(Node[].class), any(Node.class));
        var type = com.talhanation.workers.entities.ai.navigation.WorkersAsyncPathfinder.class;
        var method = type.getDeclaredMethod("processPath", NodeEvaluator.class, Node.class, java.util.List.class,
                float.class, int.class, float.class, int.class);
        method.setAccessible(true);
        return (Path) method.invoke(new com.talhanation.workers.entities.ai.navigation.WorkersAsyncPathfinder(evaluator, 1),
                evaluator, new Node(20, 64, 0), java.util.List.of(java.util.Map.entry(new Target(80, 64, 0),
                        new BlockPos(80, 64, 0))), 32f, 0, 1f, 1);
    }

    @Test void partialApproachRejectsUnsafeUnloadedReservedAndNonprogressingEndpoints() throws Exception {
        for (int scenario = 0; scenario < 6; scenario++) {
            reset(nav); terrain();
            var goal = new WallBuilderAccess(worker, new NativeGoal());
            BlockPos endpoint = scenario == 0 ? new BlockPos(19, 64, 0) : new BlockPos(26, 64, 0);
            var partial = mock(Path.class);
            when(partial.getNodeCount()).thenReturn(2);
            when(partial.getEndNode()).thenReturn(new Node(endpoint.getX(), endpoint.getY(), endpoint.getZ()));
            when(nav.createPath(anySet(), eq(0))).thenReturn(partial);
            switch (scenario) {
                case 1 -> when(level.hasChunkAt(endpoint)).thenReturn(false);
                case 2 -> doReturn(Blocks.MAGMA_BLOCK.defaultBlockState()).when(level).getBlockState(endpoint.below());
                case 3 -> {
                    var field = WallBuilderAccess.class.getDeclaredField("reservedColumns"); field.setAccessible(true);
                    field.set(goal, java.util.Set.of(endpoint.atY(0).asLong()));
                }
                case 4 -> when(level.noCollision(eq(worker), any(AABB.class))).thenReturn(false);
                case 5 -> when(partial.getNodeCount()).thenReturn(1);
            }
            goal.route(level, new BlockPos(80, 64, 0), 40);
            verify(nav, never()).moveTo(anyDouble(), anyDouble(), anyDouble(), anyDouble());
        }
    }

    @Test void changedTargetAndRecoveryNeverInstallAFormerPartialApproach() throws Exception {
        for (boolean recovery : new boolean[]{false, true}) {
            reset(nav); terrain();
            var goal = new WallBuilderAccess(worker, new NativeGoal());
            var partial = mock(DelayedPath.class);
            when(partial.getNodeCount()).thenReturn(2); when(partial.getEndNode()).thenReturn(new Node(26, 64, 0));
            when(nav.createPath(anySet(), eq(0))).thenReturn(partial);
            goal.route(level, new BlockPos(80, 64, 0), 40, recovery);
            when(partial.isProcessed()).thenReturn(true);
            when(level.getGameTime()).thenReturn(10L);
            if (!recovery) when(nav.createPath(anySet(), eq(0))).thenReturn(null);
            goal.route(level, new BlockPos(recovery ? 80 : 0, 64, 0), 40, recovery);
            verify(nav, never()).moveTo(anyDouble(), anyDouble(), anyDouble(), anyDouble());
        }
    }

}
