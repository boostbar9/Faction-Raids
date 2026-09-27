package com.devfarinsky.siegeoverhaul.siege;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.naval.BridgeBuilder;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class RaiderHoleAvoidGoalTest extends MinecraftTestSupport {
    @Test void roofsDoNotTriggerCaveRecoveryAndReachableIndoorPathsArePreserved() {
        var mob = mock(PathfinderMob.class);
        var level = mock(net.minecraft.world.level.Level.class);
        var nav = mock(PathNavigation.class);
        when(mob.getNavigation()).thenReturn(nav);
        when(mob.blockPosition()).thenReturn(new net.minecraft.core.BlockPos(0,64,0));
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getHeight(any(), anyInt(), anyInt())).thenReturn(74);
        when(level.getBlockState(any())).thenReturn(net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState());
        var goal = new RaiderHoleAvoidGoal(mob);
        org.junit.jupiter.api.Assertions.assertFalse(goal.isTrappedUnderground(level));
        when(level.getBlockState(any())).thenAnswer(i -> ((net.minecraft.core.BlockPos)i.getArgument(0)).getY()==73
                ? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()
                : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        org.junit.jupiter.api.Assertions.assertFalse(goal.isTrappedUnderground(level), "thin stone roofs are not caves");
        doReturn(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()).when(level).getBlockState(any());
        org.junit.jupiter.api.Assertions.assertTrue(goal.isTrappedUnderground(level));
        var path = mock(net.minecraft.world.level.pathfinder.Path.class);
        when(path.canReach()).thenReturn(true); when(nav.getPath()).thenReturn(path);
        org.junit.jupiter.api.Assertions.assertFalse(goal.isTrappedUnderground(level), "do not replace a reachable route");
        when(level.hasChunkAt(any())).thenReturn(false);
        org.junit.jupiter.api.Assertions.assertFalse(goal.isTrappedUnderground(level));
    }

    @Test
    void bridgeJobControlsItsOwnSafeEdgeMovement() {
        PathfinderMob mob = mock(PathfinderMob.class);
        PathNavigation navigation = mock(PathNavigation.class);
        when(mob.getNavigation()).thenReturn(navigation);
        try (var builders = mockStatic(BridgeBuilder.class)) {
            builders.when(() -> BridgeBuilder.assigned(mob)).thenReturn(true);
            new RaiderHoleAvoidGoal(mob).tick();
            verify(mob, never()).level();
            verifyNoInteractions(navigation);
        }
    }
}
