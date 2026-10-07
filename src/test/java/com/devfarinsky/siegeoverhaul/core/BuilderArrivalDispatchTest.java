package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.ModConstants;
import com.devfarinsky.siegeoverhaul.nativecompat.NativeConstructionGuard;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.entities.ai.BuilderWorkGoal;
import com.talhanation.workers.entities.workarea.BuildArea;
import com.talhanation.workers.world.BuildBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Stack;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual pinned Workers 2.0.3 goal; terrain/entity mocks isolate its dispatch contract. */
class BuilderArrivalDispatchTest extends MinecraftTestSupport {
    @Test void depletedPlankTargetCannotOverrideTheNewNativeCobblestoneBatch() throws Exception {
        var f = new Fixture();
        var plank = new BlockPos(2, 64, 0);
        f.area.stackToPlace.push(new BuildBlock(plank, Blocks.OAK_PLANKS.defaultBlockState()));
        var inventory = new net.minecraft.world.SimpleContainer(36);
        inventory.setItem(0, f.material);
        when(f.builder.getInventory()).thenReturn(inventory);
        when(f.area.getArea()).thenReturn(new AABB(0, 64, 0, 81, 65, 1));
        when(f.area.getStateFromPos(plank)).thenReturn(Blocks.OAK_PLANKS.defaultBlockState());
        when(f.builder.getMatchingItem(any())).thenAnswer(call -> {
            java.util.function.Predicate<ItemStack> predicate = call.getArgument(0);
            return predicate.test(f.material) && !f.material.isEmpty() ? f.material : null;
        });
        f.nativeGoal.state = BuilderWorkGoal.State.PREPARE_PLACE_BLOCKS;
        f.nativeGoal.blockPos = plank;
        var wrapper = new WallBuilderAccess(f.builder, f.nativeGoal);
        try (var guard = mockStatic(NativeConstructionGuard.class, CALLS_REAL_METHODS)) {
            guard.when(() -> NativeConstructionGuard.beforeNativeTick(f.builder, f.nativeGoal)).thenReturn(true);
            wrapper.tick();
            assertNull(f.nativeGoal.blockPos);
            assertEquals(BuilderWorkGoal.State.PLACE_BLOCKS, f.nativeGoal.state);
            assertEquals(java.util.List.of(f.target), f.nativeGoal.stackToPlace);
            assertEquals(2, f.area.stackToPlace.size());
            assertEquals(8, f.material.getCount());
            wrapper.tick(); // Far from the newly selected cobblestone: still no dispatch.
            assertEquals(1, f.nativeGoal.stackToPlace.size());
            verify(f.level, never()).setBlockAndUpdate(any(), any());
            f.at(new Vec3(77.5, 64, .5));
            wrapper.tick();
            verify(f.level).setBlockAndUpdate(f.target, Blocks.COBBLESTONE.defaultBlockState());
            verify(f.level, never()).setBlockAndUpdate(eq(plank), any());
            assertEquals(7, f.material.getCount());
            verify(f.builder, never()).addNeededItem(any());
        }
    }

    @Test void emptyNativeBatchStillRequestsFiniteMaterialsThroughWorkers() throws Exception {
        var f = new Fixture();
        when(f.builder.getInventory()).thenReturn(new net.minecraft.world.SimpleContainer(36));
        when(f.area.getArea()).thenReturn(new AABB(0, 64, 0, 81, 65, 1));
        when(f.area.getRequiredMaterials()).thenReturn(new java.util.ArrayList<>(
                java.util.List.of(new ItemStack(Items.COBBLESTONE, 128))));
        f.nativeGoal.state = BuilderWorkGoal.State.PREPARE_PLACE_BLOCKS;
        f.nativeGoal.blockPos = f.target;
        var wrapper = new WallBuilderAccess(f.builder, f.nativeGoal);
        try (var guard = mockStatic(NativeConstructionGuard.class, CALLS_REAL_METHODS)) {
            guard.when(() -> NativeConstructionGuard.beforeNativeTick(f.builder, f.nativeGoal)).thenReturn(true);
            wrapper.tick();
            assertNull(f.nativeGoal.blockPos);
            assertTrue(f.nativeGoal.stackToPlace.isEmpty());
            verify(f.builder).addNeededItem(argThat(need -> need.count == 64 && need.required
                    && need.matcher.test(new ItemStack(Items.COBBLESTONE))
                    && !need.matcher.test(new ItemStack(Items.OAK_PLANKS))));
            assertEquals(1, f.area.stackToPlace.size());
            verify(f.level, never()).setBlockAndUpdate(any(), any());
        }
    }

    @Test void blockedOrUnlinkedPreparationKeepsItsNativeTarget() throws Exception {
        for (boolean blocked : new boolean[]{false, true}) {
            var f = new Fixture();
            when(f.builder.getInventory()).thenReturn(new net.minecraft.world.SimpleContainer(36));
            when(f.area.getArea()).thenReturn(new AABB(0, 64, 0, 81, 65, 1));
            when(f.area.getRequiredMaterials()).thenReturn(new java.util.ArrayList<>());
            if (!blocked) f.builder.getPersistentData().remove(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID);
            f.nativeGoal.state = BuilderWorkGoal.State.PREPARE_PLACE_BLOCKS;
            f.nativeGoal.blockPos = f.target;
            var wrapper = new WallBuilderAccess(f.builder, f.nativeGoal);
            try (var guard = mockStatic(NativeConstructionGuard.class, CALLS_REAL_METHODS)) {
                guard.when(() -> NativeConstructionGuard.beforeNativeTick(f.builder, f.nativeGoal)).thenReturn(!blocked);
                wrapper.tick();
                assertEquals(f.target, f.nativeGoal.blockPos);
                verify(f.level, never()).setBlockAndUpdate(any(), any());
            }
        }
    }

    @Test void nativeHorizontalReachUsesTheSameStrictSquaredBoundaryRegardlessOfHeight() {
        var f = new Fixture();
        doCallRealMethod().when(f.builder).getHorizontalDistanceTo(any());
        assertEquals(36, f.builder.getHorizontalDistanceTo(new BlockPos(6, 100, 0).getCenter()));
        assertFalse(f.nativeGoal.moveToPosition(new BlockPos(6, 100, 0), 40));
        assertEquals(40, f.builder.getHorizontalDistanceTo(new BlockPos(6, 100, 2).getCenter()));
        assertTrue(f.nativeGoal.moveToPosition(new BlockPos(6, 100, 2), 40));
        verify(f.navigation).moveTo(6, 100, 2, (double) .8F);
    }

    @Test void pinnedNativeFifthTickCanPlaceAFirstTargetBeforeCallingMovement() {
        var f = new Fixture();
        f.nativeGoal.tick();
        verify(f.level).setBlockAndUpdate(f.target, Blocks.COBBLESTONE.defaultBlockState());
        verify(f.navigation, never()).moveTo(anyDouble(), anyDouble(), anyDouble(), anyDouble());
        assertEquals(7, f.material.getCount());
    }

    @Test void commissionedFirstTargetWaitsForRealArrivalWithoutPoppingOrSpending() throws Exception {
        var f = new Fixture();
        var wrapper = new WallBuilderAccess(f.builder, f.nativeGoal);
        try (var guard = mockStatic(NativeConstructionGuard.class, CALLS_REAL_METHODS)) {
            guard.when(() -> NativeConstructionGuard.beforeNativeTick(f.builder, f.nativeGoal)).thenReturn(true);
            wrapper.tick();
            assertNull(f.nativeGoal.blockPos);
            assertEquals(1, f.nativeGoal.stackToPlace.size());
            assertEquals(8, f.material.getCount());
            verify(f.level, never()).setBlockAndUpdate(any(), any());
            verify(f.navigation).createPath(anySet(), eq(0));
            f.at(new Vec3(77.5, 64, .5));
            wrapper.tick();
            verify(f.level).setBlockAndUpdate(f.target, Blocks.COBBLESTONE.defaultBlockState());
            assertEquals(7, f.material.getCount());
        }
        verify(f.builder, never()).teleportTo(anyDouble(), anyDouble(), anyDouble());
    }

    @Test void currentMiningAndPlacementTargetsCannotDispatchAtDistanceOnEitherCadence() throws Exception {
        for (var state : new BuilderWorkGoal.State[]{BuilderWorkGoal.State.BREAK_BLOCKS, BuilderWorkGoal.State.PLACE_BLOCKS}) {
            for (int tick : new int[]{5, 10}) {
                var f = new Fixture();
                f.builder.tickCount = tick;
                f.nativeGoal.state = state;
                f.nativeGoal.blockPos = f.target;
                var wrapper = new WallBuilderAccess(f.builder, f.nativeGoal);
                try (var guard = mockStatic(NativeConstructionGuard.class, CALLS_REAL_METHODS)) {
                    guard.when(() -> NativeConstructionGuard.beforeNativeTick(f.builder, f.nativeGoal)).thenReturn(true);
                    wrapper.tick();
                    assertEquals(f.target, f.nativeGoal.blockPos);
                    assertEquals(state, f.nativeGoal.state);
                    assertEquals(8, f.material.getCount());
                    verify(f.level, never()).setBlockAndUpdate(any(), any());
                    verify(f.builder, never()).mineBlock(any());
                    verify(f.navigation).createPath(anySet(), eq(0));
                }
            }
        }
    }

    @Test void nearHorizontalButBuriedOrUnloadedStandingGroundCannotAuthorizeWork() throws Exception {
        for (boolean unloaded : new boolean[]{false, true}) {
            var f = new Fixture();
            f.at(new Vec3(77.5, unloaded ? 64 : 54, .5));
            if (unloaded) when(f.level.hasChunkAt(any())).thenReturn(false);
            var wrapper = new WallBuilderAccess(f.builder, f.nativeGoal);
            try (var guard = mockStatic(NativeConstructionGuard.class, CALLS_REAL_METHODS)) {
                guard.when(() -> NativeConstructionGuard.beforeNativeTick(f.builder, f.nativeGoal)).thenReturn(true);
                wrapper.tick();
                verify(f.level, never()).setBlockAndUpdate(any(), any());
                assertEquals(8, f.material.getCount());
                assertEquals(1, f.nativeGoal.stackToPlace.size());
            }
        }
    }

    @Test void existingWorldGuardStillRunsBeforeAnyArrivalRoute() throws Exception {
        var f = new Fixture();
        var wrapper = new WallBuilderAccess(f.builder, f.nativeGoal);
        try (var guard = mockStatic(NativeConstructionGuard.class, CALLS_REAL_METHODS)) {
            guard.when(() -> NativeConstructionGuard.beforeNativeTick(f.builder, f.nativeGoal)).thenReturn(false);
            wrapper.tick();
            verifyNoInteractions(f.navigation);
            verify(f.level, never()).setBlockAndUpdate(any(), any());
            assertEquals(8, f.material.getCount());
        }
    }

    @Test void auditedCollisionFreeSingleCellPlantsPermitStandingWithoutBeingCleared() throws Exception {
        for (var plant : new net.minecraft.world.level.block.Block[]{Blocks.GRASS, Blocks.FERN, Blocks.DANDELION}) {
            var f = new Fixture(); f.at(new Vec3(77.5, 64, .5));
            doAnswer(call -> {
                BlockPos pos = call.getArgument(0);
                return pos.getY() < 64 ? Blocks.STONE.defaultBlockState()
                        : pos.getY() == 64 && !pos.equals(f.target) ? plant.defaultBlockState() : Blocks.AIR.defaultBlockState();
            }).when(f.level).getBlockState(any());
            var wrapper = new WallBuilderAccess(f.builder, f.nativeGoal);
            try (var guard = mockStatic(NativeConstructionGuard.class, CALLS_REAL_METHODS)) {
                guard.when(() -> NativeConstructionGuard.beforeNativeTick(f.builder, f.nativeGoal)).thenReturn(true);
                wrapper.tick();
                verify(f.level).setBlockAndUpdate(f.target, Blocks.COBBLESTONE.defaultBlockState());
                verify(f.level, never()).destroyBlock(any(), anyBoolean());
                verify(f.builder, never()).mineBlock(any());
                assertEquals(7, f.material.getCount());
            }
        }
    }

    @Test void hazardousPairedAndFluidOccupiedStandingCellsCannotAuthorizeWork() throws Exception {
        for (var obstacle : new net.minecraft.world.level.block.Block[]{Blocks.WITHER_ROSE, Blocks.TALL_GRASS,
                Blocks.WATER, Blocks.FIRE, Blocks.POWDER_SNOW}) {
            var f = new Fixture(); f.at(new Vec3(77.5, 64, .5));
            doAnswer(call -> {
                BlockPos pos = call.getArgument(0);
                return pos.getY() < 64 ? Blocks.STONE.defaultBlockState()
                        : pos.getY() == 64 ? obstacle.defaultBlockState() : Blocks.AIR.defaultBlockState();
            }).when(f.level).getBlockState(any());
            var wrapper = new WallBuilderAccess(f.builder, f.nativeGoal);
            try (var guard = mockStatic(NativeConstructionGuard.class, CALLS_REAL_METHODS)) {
                guard.when(() -> NativeConstructionGuard.beforeNativeTick(f.builder, f.nativeGoal)).thenReturn(true);
                wrapper.tick();
                verify(f.level, never()).setBlockAndUpdate(any(), any());
                verify(f.navigation, never()).moveTo(anyDouble(), anyDouble(), anyDouble(), anyDouble());
                assertEquals(8, f.material.getCount());
            }
        }
    }

    @Test void mountLeashOrCombatNeverForcesAConstructionApproach() throws Exception {
        for (int scenario = 0; scenario < 3; scenario++) {
            var f = new Fixture();
            switch (scenario) {
                case 0 -> when(f.builder.isPassenger()).thenReturn(true);
                case 1 -> when(f.builder.isLeashed()).thenReturn(true);
                case 2 -> when(f.builder.getTarget()).thenReturn(mock(net.minecraft.world.entity.LivingEntity.class));
            }
            var wrapper = new WallBuilderAccess(f.builder, f.nativeGoal);
            try (var guard = mockStatic(NativeConstructionGuard.class, CALLS_REAL_METHODS)) {
                guard.when(() -> NativeConstructionGuard.beforeNativeTick(f.builder, f.nativeGoal)).thenReturn(true);
                wrapper.tick();
                verifyNoInteractions(f.navigation);
                verify(f.level, never()).setBlockAndUpdate(any(), any());
                assertEquals(8, f.material.getCount());
                assertEquals(1, f.nativeGoal.stackToPlace.size());
            }
        }
    }

    @Test void acceptedFourAndEightBlockFoundationsRetainNativeVerticalSemantics() throws Exception {
        for (int relief : new int[]{4, 8}) {
            int groundY = 64 - relief;
            var plan = PerimeterBlueprint.create(java.util.Set.of(new net.minecraft.world.level.ChunkPos(0, 0)),
                    (x, z) -> PerimeterBlueprint.Surface.ready(x < 8 ? groundY : 64),
                    PerimeterBlueprint.Palette.COBBLESTONE);
            assertTrue(plan.valid(), plan.problemSummary());
            var target = new BlockPos(0, 68, 7);
            assertEquals("minecraft:cobblestone", plan.blocks().get(target.asLong()));
            var f = new Fixture(target, groundY);
            f.at(new Vec3(-2.5, groundY, 7.5));
            var wrapper = new WallBuilderAccess(f.builder, f.nativeGoal);
            try (var guard = mockStatic(NativeConstructionGuard.class, CALLS_REAL_METHODS)) {
                guard.when(() -> NativeConstructionGuard.beforeNativeTick(f.builder, f.nativeGoal)).thenReturn(true);
                wrapper.tick();
                verify(f.level).setBlockAndUpdate(target, Blocks.COBBLESTONE.defaultBlockState());
                assertEquals(7, f.material.getCount());
            }
            verify(f.builder, never()).teleportTo(anyDouble(), anyDouble(), anyDouble());
        }
    }

    private static final class Fixture {
        final BuilderEntity builder = mock(BuilderEntity.class);
        final BuildArea area = mock(BuildArea.class);
        final ServerLevel level = mock(ServerLevel.class);
        final PathNavigation navigation = mock(PathNavigation.class);
        final BuilderWorkGoal nativeGoal = new BuilderWorkGoal(builder);
        final BlockPos target;
        final ItemStack material = new ItemStack(Items.COBBLESTONE, 8);
        Fixture() { this(new BlockPos(80, 64, 0), 64); }
        Fixture(BlockPos target, int groundY) {
            this.target = target;
            UUID owner = UUID.randomUUID(), areaId = UUID.randomUUID();
            var data = new CompoundTag();
            data.putUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID, areaId);
            data.putUUID(ModConstants.Tags.PLAYER_FORTIFICATION_OWNER, owner);
            builder.currentBuildArea = area;
            builder.tickCount = 5;
            when(builder.getPersistentData()).thenReturn(data);
            when(builder.getOwnerUUID()).thenReturn(owner);
            when(builder.getFollowState()).thenReturn(6);
            when(builder.level()).thenReturn(level);
            when(builder.getCommandSenderWorld()).thenReturn(level);
            when(builder.getNavigation()).thenReturn(navigation);
            when(builder.getLookControl()).thenReturn(mock(LookControl.class));
            when(builder.getMainHandItem()).thenReturn(material);
            when(builder.getMatchingItem(any())).thenReturn(material);
            when(area.getUUID()).thenReturn(areaId);
            when(area.getPlayerUUID()).thenReturn(owner);
            when(area.isAlive()).thenReturn(true);
            when(area.getPersistentData()).thenReturn(new CompoundTag());
            when(area.getStateFromPos(target)).thenReturn(Blocks.COBBLESTONE.defaultBlockState());
            area.stackToPlace = new Stack<>();
            area.stackToPlace.push(new BuildBlock(target, Blocks.COBBLESTONE.defaultBlockState()));
            area.stackToPlaceMultiBlock = new Stack<>();
            nativeGoal.state = BuilderWorkGoal.State.PLACE_BLOCKS;
            nativeGoal.stackToPlace = new Stack<>(); nativeGoal.stackToPlace.push(target);
            when(level.hasChunkAt(any())).thenReturn(true);
            when(level.getMinBuildHeight()).thenReturn(-64);
            when(level.getMaxBuildHeight()).thenReturn(320);
            when(level.getHeight(any(), anyInt(), anyInt())).thenReturn(groundY);
            when(level.getWorldBorder()).thenReturn(new WorldBorder());
            when(level.noCollision(eq(builder), any(AABB.class))).thenReturn(true);
            when(level.getBlockState(any())).thenAnswer(call -> ((BlockPos) call.getArgument(0)).getY() < groundY
                    ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            at(new Vec3(.5, 64, .5));
        }
        void at(Vec3 position) {
            when(builder.position()).thenReturn(position);
            when(builder.getX()).thenReturn(position.x); when(builder.getZ()).thenReturn(position.z);
            when(builder.getBoundingBox()).thenReturn(new AABB(position.x - .3, position.y, position.z - .3,
                    position.x + .3, position.y + 1.8, position.z + .3));
        }
    }
}
