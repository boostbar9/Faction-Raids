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

    @Test void nearHorizontalButTooHighAndUnloadedStandingGroundCannotAuthorizeWork() throws Exception {
        for (boolean unloaded : new boolean[]{false, true}) {
            var f = new Fixture();
            f.at(new Vec3(77.5, unloaded ? 64 : 54, .5));
            when(f.level.getHeight(any(), anyInt(), anyInt())).thenReturn(unloaded ? 64 : 54);
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

    private static final class Fixture {
        final BuilderEntity builder = mock(BuilderEntity.class);
        final BuildArea area = mock(BuildArea.class);
        final ServerLevel level = mock(ServerLevel.class);
        final PathNavigation navigation = mock(PathNavigation.class);
        final BuilderWorkGoal nativeGoal = new BuilderWorkGoal(builder);
        final BlockPos target = new BlockPos(80, 64, 0);
        final ItemStack material = new ItemStack(Items.COBBLESTONE, 8);
        Fixture() {
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
            when(level.getHeight(any(), anyInt(), anyInt())).thenReturn(64);
            when(level.getWorldBorder()).thenReturn(new WorldBorder());
            when(level.noCollision(eq(builder), any(AABB.class))).thenReturn(true);
            when(level.getBlockState(any())).thenAnswer(call -> ((BlockPos) call.getArgument(0)).getY() < 64
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
