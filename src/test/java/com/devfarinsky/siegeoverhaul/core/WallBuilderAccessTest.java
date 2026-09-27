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
    public abstract static class Builder extends Mob {
        public Entity currentBuildArea;
        protected Builder(EntityType<? extends Mob> type, Level level) { super(type, level); }
    }
    public static class NativeGoal extends Goal {
        public BlockPos blockPos;
        public Object state;
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
        when(level.getBlockState(any())).thenAnswer(i -> ((BlockPos)i.getArgument(0)).getY()<64
                ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
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
        when(nav.createPath(anySet(),eq(0))).thenReturn(path); when(nav.moveTo(path,0.8)).thenReturn(true);
        goal.route(level,marker); verify(nav).moveTo(path,0.8);
        when(level.getGameTime()).thenReturn(10L);goal.route(level,marker);
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
        when(level.getBlockState(any())).thenReturn(Blocks.WATER.defaultBlockState());
        assertTrue(WallBuilderAccess.standingSites(level,worker,marker).isEmpty());
        terrain(); Path unreachable=mock(Path.class);when(nav.createPath(anySet(),eq(0))).thenReturn(unreachable);
        goal.route(level,marker); verify(nav,never()).moveTo(any(Path.class),anyDouble());
    }
    @Test void nativeSleepSupplyAndOwnerCommandsRemainAuthoritative() throws Exception {
        NativeGoal original=mock(NativeGoal.class);when(original.getFlags()).thenReturn(EnumSet.of(Goal.Flag.MOVE));
        var goal=new WallBuilderAccess(worker,original);
        assertFalse(goal.canUse());assertFalse(goal.canContinueToUse());
        when(original.canUse()).thenReturn(true);when(original.canContinueToUse()).thenReturn(true);
        assertTrue(goal.canUse());assertTrue(goal.canContinueToUse());goal.start();goal.tick();goal.stop();
        verify(original).start();verify(original).tick();verify(original).stop();verifyNoInteractions(level);
    }
}
