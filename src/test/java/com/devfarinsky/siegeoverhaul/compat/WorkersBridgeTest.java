package com.devfarinsky.siegeoverhaul.compat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidConfig;
import com.devfarinsky.siegeoverhaul.RecruitsBridge;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.entities.ai.BuilderWorkGoal;
import com.talhanation.workers.entities.ai.GetNeededItemsFromStorage;
import com.talhanation.workers.entities.ai.WorkerGoHomeGoal;
import com.talhanation.workers.entities.workarea.BuildArea;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkersBridgeTest extends MinecraftTestSupport {
    public static class WorkStateApi {
        public Object currentBuildArea;
        public boolean isFleeing;
        public int state=6;
        public int getFollowState() {return state;}
    }
    @Test void recoveryRequiresTheExactJobAndRespectsOwnerCommands() {
        var worker=new WorkStateApi();var area=new Object();worker.currentBuildArea=area;
        assertTrue(WorkersBridge.workingOnApi(worker,area));
        assertFalse(WorkersBridge.workingOnApi(worker,new Object()));
        worker.state=3;assertFalse(WorkersBridge.workingOnApi(worker,area));
        worker.state=6;worker.isFleeing=true;assertFalse(WorkersBridge.workingOnApi(worker,area));
        assertFalse(WorkersBridge.workingOnApi(new Object(),area));
    }
    public static class RecoveryScheduleApi {
        boolean work = true, sleep, chest;
        public boolean shouldWork() { return work; }
        public boolean needsToSleep() { return sleep; }
        public boolean needsToGetToChest() { return chest; }
    }
    public static class ThrowingRecoveryScheduleApi extends RecoveryScheduleApi {
        @Override public boolean needsToGetToChest() { throw new IllegalStateException("unavailable"); }
    }
    @Test void groundRecoveryFailsClosedForUnavailableNativeScheduling() {
        var worker = new RecoveryScheduleApi();
        assertTrue(WorkersBridge.readyForGroundRecoveryApi(worker));
        worker.work = false;
        assertFalse(WorkersBridge.readyForGroundRecoveryApi(worker));
        worker.work = true; worker.sleep = true;
        assertFalse(WorkersBridge.readyForGroundRecoveryApi(worker));
        worker.sleep = false; worker.chest = true;
        assertFalse(WorkersBridge.readyForGroundRecoveryApi(worker));
        assertFalse(WorkersBridge.readyForGroundRecoveryApi(new Object()));
        assertFalse(WorkersBridge.readyForGroundRecoveryApi(new WorkStateApi()));
        assertFalse(WorkersBridge.readyForGroundRecoveryApi(new ThrowingRecoveryScheduleApi()));
        assertFalse(WorkersBridge.readyForGroundRecoveryApi(null));
        assertFalse(WorkersBridge.readyForGroundRecovery(null));
    }
    @Test void recoveryAgreesWithPinnedNativeConstructionSchedulingAndAddsItemUseExclusion() {
        var worker = mock(BuilderEntity.class);
        worker.currentBuildArea = mock(BuildArea.class);
        var nativeWork = new BuilderWorkGoal(worker);
        when(worker.isOwned()).thenReturn(true);
        when(worker.getFollowState()).thenReturn(6);
        doCallRealMethod().when(worker).shouldWork();
        assertTrue(nativeWork.canUse());
        assertTrue(WorkersBridge.readyForGroundRecovery(worker));

        when(worker.needsToSleep()).thenReturn(true);
        assertFalse(nativeWork.canUse());
        assertFalse(WorkersBridge.readyForGroundRecovery(worker));
        when(worker.needsToSleep()).thenReturn(false);
        when(worker.needsToGetToChest()).thenReturn(true);
        assertFalse(nativeWork.canUse());
        assertFalse(WorkersBridge.readyForGroundRecovery(worker));
        when(worker.needsToGetToChest()).thenReturn(false);
        when(worker.isOwned()).thenReturn(false);
        assertFalse(nativeWork.canUse());
        assertFalse(WorkersBridge.readyForGroundRecovery(worker));
        when(worker.isOwned()).thenReturn(true);
        for (int command : new int[]{1, 2, 3, 5}) {
            when(worker.getFollowState()).thenReturn(command);
            assertFalse(nativeWork.canUse());
            assertFalse(WorkersBridge.readyForGroundRecovery(worker));
        }
        when(worker.getFollowState()).thenReturn(6);
        when(worker.isSleeping()).thenReturn(true);
        assertTrue(nativeWork.canUse(), "An unusual daytime sleep still needs the explicit recovery exclusion");
        assertFalse(WorkersBridge.readyForGroundRecovery(worker));
        when(worker.isSleeping()).thenReturn(false);
        when(worker.isUsingItem()).thenReturn(true);
        assertTrue(nativeWork.canUse(), "Native build scheduling does not itself reject an active item use");
        assertFalse(WorkersBridge.readyForGroundRecovery(worker));
        verify(worker, never()).stopUsingItem();
        verify(worker, never()).stopSleeping();
    }
    @Test void nativeHomeAndStorageKeepStateSixAndJobButCannotTriggerGroundRecovery() {
        var worker = mock(BuilderEntity.class);
        var area = mock(BuildArea.class);
        worker.currentBuildArea = area;
        var state = new java.util.concurrent.atomic.AtomicInteger(0);
        when(worker.getFollowState()).thenAnswer(call -> state.get());
        doAnswer(call -> { state.set(call.getArgument(0)); return null; }).when(worker).setFollowState(anyInt());
        when(worker.shouldWork()).thenReturn(true);
        when(worker.needsToSleep()).thenReturn(true);
        new WorkerGoHomeGoal(worker).start();
        assertTrue(WorkersBridge.workingOn(worker, area), "Native home starts state 6 without clearing the build area");
        assertFalse(WorkersBridge.readyForGroundRecovery(worker));

        state.set(0);
        when(worker.needsToSleep()).thenReturn(false);
        when(worker.needsToGetToChest()).thenReturn(true);
        when(worker.position()).thenReturn(Vec3.ZERO);
        when(worker.getNavigation()).thenReturn(mock(net.minecraft.world.entity.ai.navigation.PathNavigation.class));
        when(worker.getLookControl()).thenReturn(mock(net.minecraft.world.entity.ai.control.LookControl.class));
        assertTrue(new GetNeededItemsFromStorage(worker).moveToPosition(new BlockPos(100, 64, 100)));
        assertTrue(WorkersBridge.workingOn(worker, area), "Native storage travel also retains state 6 and the exact job");
        assertFalse(WorkersBridge.readyForGroundRecovery(worker));
        assertSame(area, worker.currentBuildArea);
    }
    @Test
    void workers2ApiReceivesRaiderOwnerAndNonWorkingHoldMode() throws Exception {
        Workers2Api worker = new Workers2Api();
        WorkersBridge.configureBuilder(worker, Vec3.ZERO);
        assertEquals(Optional.of(RecruitsBridge.RAIDERS_LEADER_UUID), worker.owner);
        assertTrue(worker.owned);
        assertFalse(worker.listen);
        assertEquals(3, worker.followState);
        assertEquals(Vec3.ZERO, worker.hold);
    }

    @Test
    void unsupportedApiFailsBeforeCallerCanRegisterWorker() {
        assertThrows(ReflectiveOperationException.class,
                () -> WorkersBridge.configureBuilder(new Object(), Vec3.ZERO));
    }

    @Test
    void disabledCompatibilityNeverRequiresModLoader() {
        RaidConfig.ENABLE_WORKERS_COMPAT.set(false);
        assertFalse(WorkersBridge.available());
    }

    @Test
    void builderEnabledStorageIsAcceptedWithoutLinkingWorkersEnum() {
        assertTrue(WorkersBridge.hasBuilderStorageApi(
                new StorageAreaApi(EnumSet.of(StorageType.BUILDERS, StorageType.FARMERS))));
    }

    @Test
    void readableStorageWithoutBuilderTypeIsRejected() {
        assertFalse(WorkersBridge.hasBuilderStorageApi(
                new StorageAreaApi(EnumSet.of(StorageType.FARMERS))));
        assertFalse(WorkersBridge.hasBuilderStorageApi(null));
    }

    @Test
    void unreadableOptionalStorageApiFailsOpen() {
        assertTrue(WorkersBridge.hasBuilderStorageApi(new Object()));
        assertTrue(WorkersBridge.hasBuilderStorageApi(new UnexpectedStorageAreaApi(null)));
        assertTrue(WorkersBridge.hasBuilderStorageApi(
                new UnexpectedStorageAreaApi(java.util.List.of(StorageType.FARMERS))));
    }

    @Test
    void areaTeamMarkerSeparatesPlayerJobsFromEnemyCampJobs() {
        assertEquals("", WorkersBridge.readAreaTeamApi(new AreaApi("")));
        assertEquals(RecruitsBridge.RAIDERS_FACTION_ID,
                WorkersBridge.readAreaTeamApi(new AreaApi(RecruitsBridge.RAIDERS_FACTION_ID)));
        assertNull(WorkersBridge.readAreaTeamApi(new Object()));
    }

    @Test
    void ownedBusyAndFleeingWorkersAreRecognisedThroughTheOptionalApi() {
        BuilderApi free = new BuilderApi();
        UUID player = UUID.randomUUID();
        free.owner = Optional.of(player);
        assertEquals(player, WorkersBridge.readWorkerOwnerApi(free));
        assertFalse(WorkersBridge.hasActiveBuildAreaApi(free));
        assertFalse(WorkersBridge.isFleeingApi(free));

        free.isFleeing = true;
        assertTrue(WorkersBridge.isFleeingApi(free));
    }

    @Test
    void unreadableWorkerApiNeverClaimsOwnershipOrWork() {
        assertNull(WorkersBridge.readWorkerOwnerApi(new Object()));
        assertNull(WorkersBridge.readWorkerOwnerApi(null));
        assertFalse(WorkersBridge.hasActiveBuildAreaApi(new Object()));
        assertFalse(WorkersBridge.hasActiveBuildAreaApi(null));
        assertFalse(WorkersBridge.isFleeingApi(new Object()));
        assertFalse(WorkersBridge.isFleeingApi(null));
    }

    @Test
    void buildAreaHandoffAndRollbackAffectOnlyTheExpectedJob() {
        BuilderApi worker = new BuilderApi();
        Object first = new Object(), second = new Object();

        assertTrue(WorkersBridge.assignBuildAreaApi(worker, first));
        assertSame(first, worker.currentBuildArea);
        assertEquals(6, worker.followState);
        assertEquals(WorkersBridge.PlayerJobRelease.NOT_ATTACHED,
                WorkersBridge.releasePlayerJobApi(worker, second));
        assertSame(first, worker.currentBuildArea);
        assertEquals(WorkersBridge.PlayerJobRelease.RELEASED,
                WorkersBridge.releasePlayerJobApi(worker, first));
        assertNull(worker.currentBuildArea);
        assertEquals(0, worker.followState);
    }

    @Test
    void failedReflectiveHandoffRestoresThePreviousNativeJob() {
        Object previous = new Object();
        BuilderWithoutFollowState worker = new BuilderWithoutFollowState();
        worker.currentBuildArea = previous;

        assertFalse(WorkersBridge.assignBuildAreaApi(worker, new Object()));

        assertSame(previous, worker.currentBuildArea);
    }

    @Test
    void rollbackStillDetachesAreaWhenFollowStateResetIsIncompatible() {
        Object failedArea = new Object();
        BuilderWithBrokenFollowState worker = new BuilderWithBrokenFollowState();
        worker.currentBuildArea = failedArea;

        assertEquals(WorkersBridge.PlayerJobRelease.RELEASED,
                WorkersBridge.releasePlayerJobApi(worker, failedArea));

        assertNull(worker.currentBuildArea);
        assertEquals(1, worker.resetAttempts);
    }

    @Test
    void rollbackReportsFailureWhenAreaCannotBeDetached() {
        BuilderWithReadOnlyBuildArea worker = new BuilderWithReadOnlyBuildArea();

        assertEquals(WorkersBridge.PlayerJobRelease.FAILED,
                WorkersBridge.releasePlayerJobApi(worker, BuilderWithReadOnlyBuildArea.currentBuildArea));

        assertNotNull(BuilderWithReadOnlyBuildArea.currentBuildArea);
        assertEquals(0, worker.resetAttempts);
        assertEquals(6, worker.followState);
    }

    @Test
    void distantBuilderArrivalFinishesPlayerLevelSearchBeforeConsideringRoof() {
        ServerLevel level=mock(ServerLevel.class);
        Mob worker=mock(Mob.class);
        WorldBorder border=mock(WorldBorder.class);
        BlockPos anchor=new BlockPos(10,64,10);
        BlockPos clearFloor=anchor.offset(4,0,0);
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getMinBuildHeight()).thenReturn(-64);
        when(level.getMaxBuildHeight()).thenReturn(320);
        when(level.getWorldBorder()).thenReturn(border);
        when(border.isWithinBounds(any(AABB.class))).thenReturn(true);
        when(level.getHeight(any(Heightmap.Types.class),anyInt(),anyInt())).thenReturn(67);
        when(level.getBlockState(any())).thenAnswer(call -> {
            BlockPos pos=call.getArgument(0);
            return (pos.getY()<64 || pos.getY()==66 ? Blocks.STONE : Blocks.AIR).defaultBlockState();
        });
        when(worker.position()).thenReturn(new Vec3(100.5D,64,100.5D));
        when(worker.getBoundingBox()).thenReturn(new AABB(100.2D,64,100.2D,100.8D,65.95D,100.8D));
        when(level.noCollision(eq(worker),any(AABB.class))).thenAnswer(call -> {
            AABB body=call.getArgument(1);
            return body.minY >= 67 || (Math.abs(body.minX-(clearFloor.getX()+0.2D))<0.01D
                    && Math.abs(body.minZ-(clearFloor.getZ()+0.2D))<0.01D);
        });

        assertEquals(clearFloor,WorkersBridge.playerBuilderArrival(level,worker,anchor));
    }

    @Test
    void commissionedBuilderArrivalRejectsWaterAndUsesNearbyDryFooting() {
        ServerLevel level=mock(ServerLevel.class);
        Mob worker=mock(Mob.class);
        WorldBorder border=mock(WorldBorder.class);
        BlockPos anchor=new BlockPos(0,64,0);
        BlockPos dry=anchor.east();
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getMinBuildHeight()).thenReturn(-64);
        when(level.getMaxBuildHeight()).thenReturn(320);
        when(level.getWorldBorder()).thenReturn(border);
        when(border.isWithinBounds(any(AABB.class))).thenReturn(true);
        when(level.getHeight(any(Heightmap.Types.class),anyInt(),anyInt())).thenReturn(64);
        when(level.getBlockState(any())).thenAnswer(call -> {
            BlockPos pos=call.getArgument(0);
            if (pos.getY()==63) return (pos.getX()==dry.getX() && pos.getZ()==dry.getZ()
                    ? Blocks.STONE : Blocks.WATER).defaultBlockState();
            return Blocks.AIR.defaultBlockState();
        });
        when(worker.position()).thenReturn(new Vec3(100.5D,64,100.5D));
        when(worker.getBoundingBox()).thenReturn(new AABB(100.2D,64,100.2D,100.8D,65.95D,100.8D));
        when(level.noCollision(eq(worker),any(AABB.class))).thenReturn(true);

        assertEquals(dry,WorkersBridge.playerBuilderArrival(level,worker,anchor));
    }

    @Test
    void commissionedBuilderArrivalRejectsHazardsInBothBodyCells() {
        ServerLevel level=mock(ServerLevel.class);
        Mob worker=mock(Mob.class);
        WorldBorder border=mock(WorldBorder.class);
        BlockPos anchor=new BlockPos(0,64,0);
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getMinBuildHeight()).thenReturn(-64);
        when(level.getMaxBuildHeight()).thenReturn(320);
        when(level.getWorldBorder()).thenReturn(border);
        when(border.isWithinBounds(any(AABB.class))).thenReturn(true);
        when(level.getHeight(any(Heightmap.Types.class),anyInt(),anyInt())).thenReturn(64);
        when(worker.position()).thenReturn(new Vec3(100.5D,64,100.5D));
        when(worker.getBoundingBox()).thenReturn(new AABB(100.2D,64,100.2D,100.8D,65.95D,100.8D));
        when(level.noCollision(eq(worker),any(AABB.class))).thenReturn(true);
        boolean[] headHazard={false};
        when(level.getBlockState(any())).thenAnswer(call -> {
            BlockPos pos=call.getArgument(0);
            BlockPos hazard=headHazard[0] ? anchor.above() : anchor;
            if (pos.equals(hazard)) return (headHazard[0] ? Blocks.POWDER_SNOW : Blocks.FIRE).defaultBlockState();
            return (pos.getY()<64 ? Blocks.STONE : Blocks.AIR).defaultBlockState();
        });
        assertNotEquals(anchor,WorkersBridge.playerBuilderArrival(level,worker,anchor));

        headHazard[0]=true;
        assertNotEquals(anchor,WorkersBridge.playerBuilderArrival(level,worker,anchor));
    }

    @Test
    void commissionedBuilderArrivalNeverReadsUnloadedColumns() {
        ServerLevel level=mock(ServerLevel.class);
        Mob worker=mock(Mob.class);
        when(level.hasChunkAt(any())).thenReturn(false);

        assertNull(WorkersBridge.playerBuilderArrival(level,worker,new BlockPos(0,64,0)));
        verify(level,never()).getHeight(any(Heightmap.Types.class),anyInt(),anyInt());
        verify(level,never()).getBlockState(any());
    }

    @Test
    void canceledAreaCleanupOnlyDetachesExactPointerWithoutChangingWorkerOrders() {
        BuilderApi worker = new BuilderApi(); worker.followState = 6;
        Entity canceled = mock(Entity.class), replacement = mock(Entity.class);
        UUID id = UUID.randomUUID(), next = UUID.randomUUID();
        when(canceled.getUUID()).thenReturn(id); when(replacement.getUUID()).thenReturn(next);
        worker.currentBuildArea = replacement;
        assertTrue(WorkersBridge.detachBuildAreaReferenceApi(worker, id));
        assertSame(replacement, worker.currentBuildArea); assertEquals(6, worker.followState);
        worker.currentBuildArea = canceled;
        assertTrue(WorkersBridge.detachBuildAreaReferenceApi(worker, id));
        assertNull(worker.currentBuildArea); assertEquals(6, worker.followState);
        assertTrue(WorkersBridge.detachBuildAreaReferenceApi(worker, id));
        assertFalse(WorkersBridge.detachBuildAreaReferenceApi(new Object(), id));
    }

    /** Public signatures verified against Workers 2 / Recruits upstream. */
    public static class BuilderApi {
        public Object currentBuildArea;
        public boolean isFleeing;
        public int followState;
        Optional<UUID> owner = Optional.empty();
        public Optional<UUID> getOwnerUUID() { return owner; }
        public void setFollowState(int state) { followState = state; }
    }

    public static class BuilderWithoutFollowState { public Object currentBuildArea; }

    public static class BuilderWithBrokenFollowState {
        public Object currentBuildArea;
        int resetAttempts;
        public void setFollowState(int state) {
            resetAttempts++;
            throw new IllegalStateException("incompatible optional API");
        }
    }

    public static class BuilderWithReadOnlyBuildArea {
        public static final Object currentBuildArea = new Object();
        int followState = 6;
        int resetAttempts;
        public void setFollowState(int state) {
            resetAttempts++;
            followState = state;
        }
    }

    /** Public signatures verified against Workers 2 / Recruits upstream. */
    public static class Workers2Api {
        Optional<UUID> owner;
        boolean owned, listen = true;
        int followState;
        Vec3 hold;
        public void setOwnerUUID(Optional<UUID> owner) { this.owner = owner; }
        public void setIsOwned(boolean owned) { this.owned = owned; }
        public void setListen(boolean listen) { this.listen = listen; }
        public void setFollowState(int state) { followState = state; }
        public void setHoldPos(Vec3 pos) { hold = pos; }
    }

    public enum StorageType { BUILDERS, FARMERS }

    public static class StorageAreaApi {
        private final EnumSet<StorageType> types;
        StorageAreaApi(EnumSet<StorageType> types) { this.types = types; }
        public EnumSet<StorageType> getStorageTypes() { return types; }
    }

    public static class UnexpectedStorageAreaApi {
        private final Object types;
        UnexpectedStorageAreaApi(Object types) { this.types = types; }
        public Object getStorageTypes() { return types; }
    }

    public record AreaApi(String team) {
        public String getTeamStringID() { return team; }
    }
}
