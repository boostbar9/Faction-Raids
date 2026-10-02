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
        when(surface.getTarget()).thenReturn(new BlockPos(1,64,0));
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
        when(path.getTarget()).thenReturn(new BlockPos(1,64,0));
        when(nav.createPath(anySet(),eq(0))).thenReturn(path);
        goal.route(level,new BlockPos(0,60,0),20);
        verify(nav).moveTo(1,64,0,0.8);
        verify(worker,never()).teleportTo(anyDouble(),anyDouble(),anyDouble());
    }
    @Test void cachedStandingSpaceIsNotReusedAfterItsFloorBecomesHazardous() throws Exception {
        terrain();var goal=new WallBuilderAccess(worker,new NativeGoal());
        var path=mock(Path.class);when(path.canReach()).thenReturn(true);
        when(path.getTarget()).thenReturn(new BlockPos(1,64,0));
        when(nav.createPath(anySet(),eq(0))).thenReturn(path);
        when(nav.moveTo(1,64,0,0.8)).thenReturn(true);
        goal.route(level,new BlockPos(0,60,0),20);
        clearInvocations(nav);
        doReturn(Blocks.MAGMA_BLOCK.defaultBlockState()).when(level).getBlockState(new BlockPos(1,63,0));
        when(level.getGameTime()).thenReturn(10L);
        goal.route(level,new BlockPos(0,60,0),20);
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
    public enum State { SELECT_WORK_AREA, MOVE_TO_WORK_AREA, PREPARE_BREAK_BLOCKS, DONE }
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
        when(path.getTarget()).thenReturn(new BlockPos(1,64,0));
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
        when(path.getTarget()).thenReturn(new BlockPos(1,64,0));
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
        when(path.getTarget()).thenReturn(endpoint); when(nav.moveTo(1,64,0,0.8)).thenReturn(true);
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
            when(path.getTarget()).thenReturn(new BlockPos(1,64,0));
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
        when(path.getTarget()).thenReturn(new BlockPos(-1,64,-2));
        when(nav.createPath(anySet(),eq(0))).thenReturn(path);
        goal.route(level,new BlockPos(-2,60,-2),20);
        verify(nav).moveTo(-1,64,-2,0.8);
        verify(nav,never()).moveTo(any(Path.class),anyDouble());
    }

}
