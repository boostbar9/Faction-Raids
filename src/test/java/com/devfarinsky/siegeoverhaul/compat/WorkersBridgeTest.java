package com.devfarinsky.siegeoverhaul.compat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidConfig;
import com.devfarinsky.siegeoverhaul.RecruitsBridge;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkersBridgeTest extends MinecraftTestSupport {
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
