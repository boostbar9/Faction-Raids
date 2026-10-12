package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.talhanation.workers.entities.BuilderEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BuilderGroundRecoveryTest extends MinecraftTestSupport {
    private final ServerLevel level=mock(ServerLevel.class);
    private final BuilderEntity worker=mock(BuilderEntity.class);
    private final CompoundTag data=new CompoundTag();
    private void setup() {
        when(worker.isAlive()).thenReturn(true);
        when(worker.shouldWork()).thenReturn(true);
        when(worker.needsToSleep()).thenReturn(false);
        when(worker.needsToGetToChest()).thenReturn(false);
        when(worker.position()).thenReturn(new Vec3(0.5,60,0.5));
        when(worker.blockPosition()).thenReturn(new BlockPos(0,60,0));
        when(worker.getX()).thenReturn(0.5);when(worker.getY()).thenReturn(60.0);when(worker.getZ()).thenReturn(0.5);
        when(worker.getBoundingBox()).thenReturn(new AABB(0.2,60,0.2,0.8,61.9,0.8));
        when(worker.getNavigation()).thenReturn(mock(PathNavigation.class));
        when(worker.getPersistentData()).thenReturn(data);
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.canSeeSky(any())).thenReturn(true);
        when(level.getMinBuildHeight()).thenReturn(-64);when(level.getMaxBuildHeight()).thenReturn(320);
        WorldBorder border=new WorldBorder();border.setSize(1000);when(level.getWorldBorder()).thenReturn(border);
        when(level.getHeight(any(),anyInt(),anyInt())).thenReturn(64);
        doAnswer(i -> ((BlockPos)i.getArgument(0)).getY()<64
                ? Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState()).when(level).getBlockState(any());
        when(level.getFluidState(any())).thenReturn(Fluids.EMPTY.defaultFluidState());
        when(level.noCollision(eq(worker),any(AABB.class))).thenReturn(true);
    }
    private void pass(long time) {when(level.getGameTime()).thenReturn(time);BuilderGroundRecovery.tick(level,worker);}
    @Test void stationaryUndergroundWorkerWaitsThenRecoversWithoutEditingWorld() {
        setup();pass(0);pass(40);pass(80);
        verify(worker,never()).teleportTo(anyDouble(),anyDouble(),anyDouble());
        pass(120);
        verify(worker).teleportTo(0.5,64,0.5);
        verify(worker).setDeltaMovement(Vec3.ZERO);
        assertFalse(data.contains("SiegeWallGroundRecovery"));
        verify(level,never()).setBlock(any(),any(),anyInt());
    }
    @Test void movingOrNormallyStandingWorkersAreNotTeleported() {
        setup();pass(0);pass(40);
        when(worker.position()).thenReturn(new Vec3(1.5,60,0.5));pass(80);pass(120);
        verify(worker,never()).teleportTo(anyDouble(),anyDouble(),anyDouble());
        when(level.getHeight(any(),anyInt(),anyInt())).thenReturn(60);
        pass(160);pass(200);pass(240);
        verify(worker,never()).teleportTo(anyDouble(),anyDouble(),anyDouble());
    }
    @Test void openPitIsDetectedWithoutCallingEverySlopeAPit() {
        setup();when(level.getHeight(any(),eq(0),eq(0))).thenReturn(60);
        pass(0);pass(40);pass(80);pass(120);
        verify(worker).teleportTo(anyDouble(),eq(64.0),anyDouble());
        reset(worker);setup();when(level.getHeight(any(),eq(0),eq(0))).thenReturn(60);
        when(level.getHeight(any(),eq(1),eq(0))).thenReturn(60);
        BuilderGroundRecovery.reset(worker);pass(200);pass(240);pass(280);pass(320);
        verify(worker,never()).teleportTo(anyDouble(),anyDouble(),anyDouble());
    }
    @Test void unloadedWaterBlockedAndOutOfBorderDestinationsAreRejected() {
        setup();when(level.hasChunkAt(any())).thenReturn(false);
        assertNull(BuilderGroundRecovery.findSurface(level,worker));
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getFluidState(any())).thenReturn(Fluids.WATER.defaultFluidState());
        assertNull(BuilderGroundRecovery.findSurface(level,worker));
        when(level.getFluidState(any())).thenReturn(Fluids.EMPTY.defaultFluidState());
        when(level.noCollision(eq(worker),any(AABB.class))).thenReturn(false);
        assertNull(BuilderGroundRecovery.findSurface(level,worker));
        when(level.noCollision(eq(worker),any(AABB.class))).thenReturn(true);
        level.getWorldBorder().setSize(0.5);
        assertNull(BuilderGroundRecovery.findSurface(level,worker));
    }
    @Test void highCliffsAndDangerousFootingAreNotRecoverySites() {
        setup();when(level.getHeight(any(),anyInt(),anyInt())).thenReturn(80);
        assertNull(BuilderGroundRecovery.findSurface(level,worker));
        when(level.getHeight(any(),anyInt(),anyInt())).thenReturn(64);
        doReturn(Blocks.MAGMA_BLOCK.defaultBlockState()).when(level).getBlockState(any());
        assertNull(BuilderGroundRecovery.findSurface(level,worker));
    }
    @Test void collisionFreeWitherRosesAreNeverRecoveryDestinations() {
        setup();
        assertTrue(Blocks.WITHER_ROSE.defaultBlockState().getCollisionShape(
                net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty());
        doAnswer(i -> ((BlockPos)i.getArgument(0)).getY()<64
                ? Blocks.STONE.defaultBlockState():Blocks.WITHER_ROSE.defaultBlockState())
                .when(level).getBlockState(any());
        assertNull(BuilderGroundRecovery.findSurface(level,worker));
        pass(0);pass(40);pass(80);pass(120);
        verify(worker,never()).teleportTo(anyDouble(),anyDouble(),anyDouble());
        verify(worker.getNavigation(),never()).stop();
        verify(level,never()).setBlock(any(),any(),anyInt());
    }
    @Test void witherRoseInAnyOccupiedBodyCellRejectsTheClosestColumn() {
        setup();
        doAnswer(i -> {
            BlockPos p=i.getArgument(0);
            if (p.getY()<64) return Blocks.STONE.defaultBlockState();
            return p.equals(new BlockPos(0,65,0))
                    ? Blocks.WITHER_ROSE.defaultBlockState():Blocks.AIR.defaultBlockState();
        }).when(level).getBlockState(any());
        BlockPos destination=BuilderGroundRecovery.findSurface(level,worker);
        assertNotNull(destination);
        assertNotEquals(new BlockPos(0,64,0),destination);
        assertEquals(64,destination.getY());
        assertEquals(1,Math.abs(destination.getX())+Math.abs(destination.getZ()));
    }
    @Test void harmlessSingleAndDoubleCellVegetationStillAllowsSurfaceRecovery() {
        setup();
        for (var plant : java.util.List.of(Blocks.GRASS,Blocks.FERN,Blocks.DEAD_BUSH,
                Blocks.POPPY,Blocks.DANDELION,Blocks.TALL_GRASS,Blocks.LARGE_FERN,Blocks.SUNFLOWER)) {
            var lower=plant.defaultBlockState();
            var upper=lower.hasProperty(net.minecraft.world.level.block.DoublePlantBlock.HALF)
                    ? lower.setValue(net.minecraft.world.level.block.DoublePlantBlock.HALF,
                            net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER)
                    : Blocks.AIR.defaultBlockState();
            doAnswer(i -> {
                int y=((BlockPos)i.getArgument(0)).getY();
                return y<64 ? Blocks.STONE.defaultBlockState():y==64 ? lower:upper;
            }).when(level).getBlockState(any());
            assertEquals(new BlockPos(0,64,0),BuilderGroundRecovery.findSurface(level,worker),plant.toString());
        }
    }
    @Test void leashedCombatAndMountedWorkersStayWhereTheyAre() {
        setup();when(worker.isLeashed()).thenReturn(true);pass(0);pass(40);pass(80);pass(120);
        verify(worker,never()).teleportTo(anyDouble(),anyDouble(),anyDouble());
        when(worker.isLeashed()).thenReturn(false);when(worker.isPassenger()).thenReturn(true);pass(160);
        assertFalse(data.contains("SiegeWallGroundRecovery"));
        when(worker.isPassenger()).thenReturn(false);when(worker.getTarget()).thenReturn(mock(Mob.class));pass(200);
        assertFalse(data.contains("SiegeWallGroundRecovery"));
    }
    @Test void reloadOrTimeResetRequiresANewObservationPeriod() {
        setup();pass(1000);pass(1040);pass(1080);pass(20);pass(60);
        verify(worker,never()).teleportTo(anyDouble(),anyDouble(),anyDouble());
    }
    @Test void nativeSleepStorageAndItemUseResetTheWholeStallObservationWindow() {
        for (int interruption=0; interruption<5; interruption++) {
            reset(worker,level); data.remove("SiegeWallGroundRecovery"); setup();
            pass(0); pass(40); pass(80);
            switch (interruption) {
                case 0 -> when(worker.shouldWork()).thenReturn(false);
                case 1 -> when(worker.needsToSleep()).thenReturn(true);
                case 2 -> when(worker.needsToGetToChest()).thenReturn(true);
                case 3 -> when(worker.isSleeping()).thenReturn(true);
                case 4 -> when(worker.isUsingItem()).thenReturn(true);
            }
            pass(120); pass(160); pass(200); pass(240);
            assertFalse(data.contains("SiegeWallGroundRecovery"));
            verify(worker,never()).teleportTo(anyDouble(),anyDouble(),anyDouble());
            verify(worker.getNavigation(),never()).stop();
            verify(level,never()).getHeight(any(),anyInt(),anyInt());
            verify(worker,never()).stopSleeping();
            verify(worker,never()).stopUsingItem();

            when(worker.shouldWork()).thenReturn(true);
            when(worker.needsToSleep()).thenReturn(false);
            when(worker.needsToGetToChest()).thenReturn(false);
            when(worker.isSleeping()).thenReturn(false);
            when(worker.isUsingItem()).thenReturn(false);
            pass(280); pass(320); pass(360);
            verify(worker,never()).teleportTo(anyDouble(),anyDouble(),anyDouble());
            pass(400);
            verify(worker).teleportTo(0.5,64,0.5);
            verify(level,never()).setBlock(any(),any(),anyInt());
        }
    }
    @Test void unreadableNativeSchedulingClearsStaleRecoveryWithoutMovingTheWorker() {
        setup(); pass(0); pass(40); pass(80);
        when(worker.needsToGetToChest()).thenThrow(new IllegalStateException("native API unavailable"));
        pass(120);
        assertFalse(data.contains("SiegeWallGroundRecovery"));
        verify(worker,never()).teleportTo(anyDouble(),anyDouble(),anyDouble());
        verify(worker.getNavigation(),never()).stop();
    }
}
