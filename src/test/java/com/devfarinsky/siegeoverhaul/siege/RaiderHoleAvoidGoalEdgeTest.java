package com.devfarinsky.siegeoverhaul.siege;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.naval.BridgeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

class RaiderHoleAvoidGoalEdgeTest extends MinecraftTestSupport {
    private final Map<BlockPos, BlockState> blocks = new HashMap<>();
    private final CompoundTag tags = new CompoundTag();
    private PathfinderMob mob;
    private Level level;
    private PathNavigation navigation;
    private MoveControl moveControl;

    @BeforeEach
    void setup() {
        level = mock(Level.class);
        when(level.getBlockState(any())).thenAnswer(call ->
                blocks.getOrDefault(call.getArgument(0), Blocks.AIR.defaultBlockState()));
        mob = mock(PathfinderMob.class);
        navigation = mock(PathNavigation.class);
        moveControl = mock(MoveControl.class);
        when(mob.level()).thenReturn(level);
        when(mob.getNavigation()).thenReturn(navigation);
        when(mob.getMoveControl()).thenReturn(moveControl);
        when(mob.getPersistentData()).thenReturn(tags);
        when(mob.onGround()).thenReturn(true);
        when(mob.getX()).thenReturn(0.5);
        when(mob.getY()).thenReturn(64.0);
        when(mob.getZ()).thenReturn(0.5);
        when(mob.getBlockY()).thenReturn(64);
        when(mob.getDeltaMovement()).thenReturn(new Vec3(0.2, 0.0, 0.0));
        // Solid ledge under the raider, open air east of it.
        blocks.put(new BlockPos(0, 63, 0), Blocks.STONE.defaultBlockState());
    }

    private void tick() {
        new RaiderHoleAvoidGoal(mob).tick();
    }

    @Test
    void lethalDropAheadStopsTheMarchAndHoldsTheRaiderBack() {
        tick();
        verify(navigation).stop();
        verify(mob).setDeltaMovement(0.0, 0.0, 0.0);
        verify(moveControl).setWantedPosition(anyDouble(), anyDouble(), anyDouble(), anyDouble());
        verify(navigation).moveTo(anyDouble(), anyDouble(), anyDouble(), anyDouble());
        assertTrue(RaiderHoleAvoidGoal.holdingEdge(mob));
    }

    @Test
    void survivableStepAndWaterLandingsStayOnTheMarch() {
        for (int x = 1; x <= 4; x++) blocks.put(new BlockPos(x, 61, 0), Blocks.STONE.defaultBlockState());
        tick();
        verifyNoInteractions(navigation);
        assertFalse(RaiderHoleAvoidGoal.holdingEdge(mob));

        blocks.clear();
        blocks.put(new BlockPos(0, 63, 0), Blocks.STONE.defaultBlockState());
        for (int x = 1; x <= 4; x++) blocks.put(new BlockPos(x, 61, 0), Blocks.WATER.defaultBlockState());
        tick();
        verifyNoInteractions(navigation);
        assertFalse(RaiderHoleAvoidGoal.holdingEdge(mob));
    }

    @Test
    void lavaBelowTheLedgeIsNeverTreatedAsASafeLanding() {
        for (int x = 1; x <= 4; x++) blocks.put(new BlockPos(x, 62, 0), Blocks.LAVA.defaultBlockState());
        tick();
        verify(navigation).stop();
        assertTrue(RaiderHoleAvoidGoal.holdingEdge(mob));
    }

    @Test
    void aStationaryRaiderWithAnOrderTowardTheDropIsStillHeld() {
        when(mob.getDeltaMovement()).thenReturn(Vec3.ZERO);
        var path = mock(net.minecraft.world.level.pathfinder.Path.class);
        when(path.isDone()).thenReturn(false);
        when(path.getNextEntityPos(mob)).thenReturn(new Vec3(8.5, 64.0, 0.5));
        when(navigation.getPath()).thenReturn(path);
        tick();
        verify(navigation).stop();
        assertTrue(RaiderHoleAvoidGoal.holdingEdge(mob));
    }

    @Test
    void theHoldExpiresSoTheAssaultResumes() {
        tick();
        assertTrue(RaiderHoleAvoidGoal.holdingEdge(mob));
        when(level.getGameTime()).thenReturn(1000L);
        assertFalse(RaiderHoleAvoidGoal.holdingEdge(mob));
    }

    @Test
    void bridgeBuildersKeepOwningTheirOwnEdgeMovement() {
        try (var builders = mockStatic(BridgeBuilder.class)) {
            builders.when(() -> BridgeBuilder.assigned(mob)).thenReturn(true);
            tick();
            verifyNoInteractions(navigation);
            assertFalse(RaiderHoleAvoidGoal.holdingEdge(mob));
        }
    }
}
