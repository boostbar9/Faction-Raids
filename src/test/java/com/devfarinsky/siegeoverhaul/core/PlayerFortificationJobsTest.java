package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.ModConstants;
import com.devfarinsky.siegeoverhaul.RecruitsBridge;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerFortificationJobsTest extends MinecraftTestSupport {
    @Test void areaJoinCannotLinkTransferredAreaToFormerOwner() {
        try (JobFixture f = new JobFixture()) {
            when(f.level.getEntity(f.builder.getUUID())).thenReturn(f.builder);
            f.bridge.when(() -> WorkersBridge.readOwner(f.area)).thenReturn(UUID.randomUUID());

            PlayerFortificationJobs.handleAreaJoin(f.level, f.area, true);

            assertFalse(f.workerTag.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID));
            f.bridge.when(() -> WorkersBridge.readOwner(f.area)).thenReturn(f.owner);
            PlayerFortificationJobs.tick(f.level, f.builder);
            assertFalse(f.workerTag.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID),
                    "A confirmed transfer must not leave a pending reservation");
        }
    }

    @Test void areaJoinWaitsForReadableOwnerThenRecovers() {
        try (JobFixture f = new JobFixture()) {
            when(f.level.getEntity(f.builder.getUUID())).thenReturn(f.builder);
            f.bridge.when(() -> WorkersBridge.readOwner(f.area)).thenReturn(null);

            PlayerFortificationJobs.handleAreaJoin(f.level, f.area, true);
            assertFalse(f.workerTag.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID));
            f.bridge.when(() -> WorkersBridge.readOwner(f.area)).thenReturn(f.owner);
            PlayerFortificationJobs.tick(f.level, f.builder);
            assertEquals(f.area.getUUID(), f.workerTag.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID));
        }
    }

    @Test void pendingAreaCannotLinkAfterOwnershipTransfer() {
        try (JobFixture f = new JobFixture()) {
            PlayerFortificationJobs.handleAreaJoin(f.level, f.area, true);
            f.bridge.when(() -> WorkersBridge.readOwner(f.area)).thenReturn(UUID.randomUUID());

            PlayerFortificationJobs.tick(f.level, f.builder);

            assertFalse(f.workerTag.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID));
            f.bridge.when(() -> WorkersBridge.readOwner(f.area)).thenReturn(f.owner);
            PlayerFortificationJobs.tick(f.level, f.builder);
            assertFalse(f.workerTag.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID),
                    "A transferred pending area must be removed from the old owner's scan");
        }
    }

    @Test void pendingAreaWaitsForReadableOwnerThenRecovers() {
        try (JobFixture f = new JobFixture()) {
            PlayerFortificationJobs.handleAreaJoin(f.level, f.area, true);
            f.bridge.when(() -> WorkersBridge.readOwner(f.area)).thenReturn(null);
            PlayerFortificationJobs.tick(f.level, f.builder);
            assertFalse(f.workerTag.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID));
            f.bridge.when(() -> WorkersBridge.readOwner(f.area)).thenReturn(f.owner);
            PlayerFortificationJobs.tick(f.level, f.builder);
            assertEquals(f.area.getUUID(), f.workerTag.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID));
        }
    }

    @Test void newCommissionWaitsForSavedJobToFinish() {
        try (JobFixture f = new JobFixture()) {
            net.minecraft.server.level.ServerPlayer player = mock(net.minecraft.server.level.ServerPlayer.class);
            when(player.getUUID()).thenReturn(f.owner);
            when(f.level.getEntitiesOfClass(eq(Mob.class), any(), any()))
                    .thenReturn(java.util.List.of(f.builder));
            PlayerFortificationJobs.link(f.builder, f.area, f.owner);

            assertNull(TerritoryFortification.findNearbyBuilder(f.level, player, BlockPos.ZERO).builder());

            PlayerFortificationJobs.unlink(f.builder, f.area.getUUID());
            assertSame(f.builder, TerritoryFortification.findNearbyBuilder(f.level, player, BlockPos.ZERO).builder());
        }
    }

    @Test void secondReloadedAreaCannotOverwriteReservedJob() {
        try (JobFixture f = new JobFixture()) {
            PlayerFortificationJobs.link(f.builder, f.area, f.owner);
            UUID firstJob = f.area.getUUID();
            Entity second = f.newArea(true);
            when(f.level.getEntity(f.builder.getUUID())).thenReturn(f.builder);

            PlayerFortificationJobs.handleAreaJoin(f.level, second, true);

            assertEquals(firstJob, f.workerTag.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID));
        }
    }

    @Test void nearbyBuilderWithReservedJobIsExcludedFromLegacySearch() {
        try (JobFixture f = new JobFixture()) {
            PlayerFortificationJobs.link(f.builder, f.area, f.owner);
            Entity second = f.newArea(false);
            when(second.getBoundingBox()).thenReturn(new net.minecraft.world.phys.AABB(0, 0, 0, 1, 1, 1));
            when(f.level.getEntitiesOfClass(eq(Mob.class), any(), any())).thenAnswer(invocation -> {
                java.util.function.Predicate<Mob> filter = invocation.getArgument(2);
                return filter.test(f.builder) ? java.util.List.of(f.builder) : java.util.List.of();
            });

            PlayerFortificationJobs.handleAreaJoin(f.level, second, true);

            assertEquals(f.area.getUUID(), f.workerTag.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID));
            assertFalse(second.getPersistentData().hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_BUILDER));
        }
    }

    @Test void areaLoadedBeforeBuilderRecoversThroughRealTickPath() throws Exception {
        try (JobFixture f = new JobFixture()) {
            PlayerFortificationJobs.handleAreaJoin(f.level, f.area, true);
            assertFalse(f.workerTag.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID));
            PlayerFortificationJobs.tick(f.level, f.builder);
            assertEquals(f.area.getUUID(), f.workerTag.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID));
            PlayerFortificationJobs.tick(f.level, f.builder);
            f.bridge.verify(() -> WorkersBridge.enablePlayerJob(f.builder, f.owner));
            f.bridge.verify(() -> WorkersBridge.assignBuildAreaDirectly(f.builder, f.area));
        }
    }

    @Test void busyBuilderWaitsThenRecoversPendingJob() {
        try (JobFixture f = new JobFixture()) {
            PlayerFortificationJobs.handleAreaJoin(f.level, f.area, true);
            f.bridge.when(() -> WorkersBridge.hasActiveBuildArea(f.builder)).thenReturn(true);
            PlayerFortificationJobs.tick(f.level, f.builder);
            assertFalse(f.workerTag.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID));
            f.bridge.when(() -> WorkersBridge.hasActiveBuildArea(f.builder)).thenReturn(false);
            PlayerFortificationJobs.tick(f.level, f.builder);
            assertEquals(f.area.getUUID(), f.workerTag.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID));
        }
    }

    @Test void transferredBuilderIsNeverReownedByRecovery() throws Exception {
        try (JobFixture f = new JobFixture()) {
            PlayerFortificationJobs.link(f.builder, f.area, f.owner);
            f.bridge.when(() -> WorkersBridge.readWorkerOwner(f.builder)).thenReturn(UUID.randomUUID());
            PlayerFortificationJobs.tick(f.level, f.builder);
            assertFalse(f.workerTag.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID));
            f.bridge.verify(() -> WorkersBridge.enablePlayerJob(any(), any()), never());
            f.bridge.verify(() -> WorkersBridge.assignBuildAreaDirectly(any(), any()), never());
        }
    }

    @Test void transferredAreaCannotBeWorkedUsingStaleOwnerTags() throws Exception {
        try (JobFixture f = new JobFixture()) {
            PlayerFortificationJobs.link(f.builder, f.area, f.owner);
            f.bridge.when(() -> WorkersBridge.readOwner(f.area)).thenReturn(UUID.randomUUID());
            PlayerFortificationJobs.tick(f.level, f.builder);
            assertFalse(f.workerTag.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID));
            f.bridge.verify(() -> WorkersBridge.enablePlayerJob(any(), any()), never());
        }
    }

    @Test void unreadableOwnershipRetainsJobForLaterRetry() throws Exception {
        try (JobFixture f = new JobFixture()) {
            PlayerFortificationJobs.link(f.builder, f.area, f.owner);
            f.bridge.when(() -> WorkersBridge.readOwner(f.area)).thenReturn(null);
            PlayerFortificationJobs.tick(f.level, f.builder);
            assertEquals(f.area.getUUID(), f.workerTag.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID));
            f.bridge.verify(() -> WorkersBridge.enablePlayerJob(any(), any()), never());
            f.bridge.when(() -> WorkersBridge.readOwner(f.area)).thenReturn(f.owner);
            PlayerFortificationJobs.tick(f.level, f.builder);
            f.bridge.verify(() -> WorkersBridge.assignBuildAreaDirectly(f.builder, f.area));
        }
    }

    @Test void campWorkerCannotReconnectToPendingPlayerJob() {
        try (JobFixture f = new JobFixture()) {
            PlayerFortificationJobs.handleAreaJoin(f.level, f.area, true);
            f.workerTag.putString(ModConstants.Tags.CAMP_WORKER_TEAM, "team:enemy");
            PlayerFortificationJobs.tick(f.level, f.builder);
            assertFalse(f.workerTag.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID));
        }
    }

    @Test void unloadedPendingAreaIsForgottenAndRegistersAgainOnJoin() {
        try (JobFixture f = new JobFixture()) {
            PlayerFortificationJobs.handleAreaJoin(f.level, f.area, true);
            when(f.level.getEntity(f.area.getUUID())).thenReturn(null);
            PlayerFortificationJobs.tick(f.level, f.builder);
            when(f.level.getEntity(f.area.getUUID())).thenReturn(f.area);
            PlayerFortificationJobs.tick(f.level, f.builder);
            assertFalse(f.workerTag.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID));
            PlayerFortificationJobs.handleAreaJoin(f.level, f.area, true);
            PlayerFortificationJobs.tick(f.level, f.builder);
            assertEquals(f.area.getUUID(), f.workerTag.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID));
        }
    }

    private static final class JobFixture implements AutoCloseable {
        final ServerLevel level = mock(ServerLevel.class);
        final Mob builder = mock(Mob.class);
        final UUID owner = UUID.randomUUID();
        final CompoundTag workerTag = new CompoundTag();
        final MockedStatic<WorkersBridge> bridge = mockStatic(WorkersBridge.class);
        final Entity area;

        JobFixture() {
            when(builder.getUUID()).thenReturn(UUID.randomUUID());
            when(builder.getPersistentData()).thenReturn(workerTag);
            when(builder.isAlive()).thenReturn(true);
            bridge.when(() -> WorkersBridge.isBuilder(builder)).thenReturn(true);
            bridge.when(() -> WorkersBridge.readWorkerOwner(builder)).thenReturn(owner);
            area = newArea(true);
            bridge.when(() -> WorkersBridge.assignBuildAreaDirectly(builder, area)).thenReturn(true);
        }

        Entity newArea(boolean reserved) {
            Entity result = mock(Entity.class);
            UUID id = UUID.randomUUID();
            CompoundTag tag = new CompoundTag();
            tag.putBoolean(ModConstants.Tags.PLAYER_FORTIFICATION_AREA, true);
            tag.putUUID(ModConstants.Tags.PLAYER_FORTIFICATION_OWNER, owner);
            if (reserved) tag.putUUID(ModConstants.Tags.PLAYER_FORTIFICATION_BUILDER, builder.getUUID());
            when(result.getPersistentData()).thenReturn(tag);
            when(result.getUUID()).thenReturn(id);
            when(result.blockPosition()).thenReturn(new BlockPos(80, 70, -16));
            when(result.isAlive()).thenReturn(true);
            when(level.getEntity(id)).thenReturn(result);
            bridge.when(() -> WorkersBridge.readOwner(result)).thenReturn(owner);
            return result;
        }

        public void close() { bridge.close(); }
    }

    @Test void commissionedWallUsesDurablePlayerLinkInsteadOfEnemyCampTag() {
        Mob builder=mock(Mob.class); Entity area=mock(Entity.class);
        CompoundTag workerTag=new CompoundTag(),areaTag=new CompoundTag();
        UUID builderId=UUID.randomUUID(),areaId=UUID.randomUUID(),owner=UUID.randomUUID();
        when(builder.getPersistentData()).thenReturn(workerTag);
        when(area.getPersistentData()).thenReturn(areaTag);
        when(builder.getUUID()).thenReturn(builderId); when(area.getUUID()).thenReturn(areaId);
        when(area.blockPosition()).thenReturn(new BlockPos(80,70,-16));
        areaTag.putString(ModConstants.Tags.CAMP_AREA_TEAM,"team:legacy");

        PlayerFortificationJobs.link(builder,area,owner);

        assertFalse(areaTag.contains(ModConstants.Tags.CAMP_AREA_TEAM));
        assertTrue(areaTag.getBoolean(ModConstants.Tags.PLAYER_FORTIFICATION_AREA));
        assertEquals(builderId,areaTag.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_BUILDER));
        assertEquals(areaId,workerTag.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID));
        assertEquals(owner,workerTag.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_OWNER));
        assertEquals(area.blockPosition(),BlockPos.of(workerTag.getLong(ModConstants.Tags.PLAYER_FORTIFICATION_POS)));
    }

    @Test void failedCommissionOnlyClearsItsOwnSavedAssociation() {
        Mob builder=mock(Mob.class); CompoundTag tag=new CompoundTag();
        when(builder.getPersistentData()).thenReturn(tag);
        UUID active=UUID.randomUUID(); tag.putUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID,active);
        PlayerFortificationJobs.unlink(builder,UUID.randomUUID());
        assertEquals(active,tag.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID));
        PlayerFortificationJobs.unlink(builder,active);
        assertFalse(tag.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID));
    }

    @Test void legacyMigrationRequiresPositivePlayerBuildAreaEvidence() {
        UUID player=UUID.randomUUID();
        assertTrue(PlayerFortificationJobs.legacyPlayerCommission(
                true,"team:claimed-core",player,true));

        assertFalse(PlayerFortificationJobs.legacyPlayerCommission(
                false,"team:claimed-core",player,true));
        assertFalse(PlayerFortificationJobs.legacyPlayerCommission(
                true,"",player,true));
        assertFalse(PlayerFortificationJobs.legacyPlayerCommission(
                true,"team:claimed-core",null,true));
        assertFalse(PlayerFortificationJobs.legacyPlayerCommission(
                true,"team:claimed-core",player,false));
        assertFalse(PlayerFortificationJobs.legacyPlayerCommission(
                true,"team:claimed-core",RecruitsBridge.RAIDERS_LEADER_UUID,true));
    }

    @Test void lateBuilderReconnectRequiresOwnerAndReservationMatch() {
        UUID owner=UUID.randomUUID(),builder=UUID.randomUUID(),other=UUID.randomUUID();
        assertTrue(PlayerFortificationJobs.pendingBuilderMatches(
                owner,owner,null,builder,true,false));
        assertTrue(PlayerFortificationJobs.pendingBuilderMatches(
                owner,owner,builder,builder,true,false));

        assertFalse(PlayerFortificationJobs.pendingBuilderMatches(
                owner,other,null,builder,true,false));
        assertFalse(PlayerFortificationJobs.pendingBuilderMatches(
                owner,owner,other,builder,true,false));
        assertFalse(PlayerFortificationJobs.pendingBuilderMatches(
                owner,owner,null,builder,false,false));
        assertFalse(PlayerFortificationJobs.pendingBuilderMatches(
                owner,owner,null,builder,true,true));
    }
}
