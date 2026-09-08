package com.devfarinsky.siegeoverhaul.raid;
import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class FlankRoutesTest extends MinecraftTestSupport {
    @Test void acceptsReachableRouteAndThrottlesRepeatedSearches() {
        var level=mock(ServerLevel.class);var mob=mock(net.minecraft.world.entity.PathfinderMob.class);
        var nav=mock(net.minecraft.world.entity.ai.navigation.PathNavigation.class);var path=mock(net.minecraft.world.level.pathfinder.Path.class);
        var tag=new net.minecraft.nbt.CompoundTag();when(mob.getPersistentData()).thenReturn(tag);when(mob.getUUID()).thenReturn(new java.util.UUID(0,2));
        when(mob.position()).thenReturn(new Vec3(0,64,0));when(mob.getY()).thenReturn(64D);when(mob.getNavigation()).thenReturn(nav);
        when(mob.getBoundingBox()).thenReturn(new net.minecraft.world.phys.AABB(0,64,0,0.6,66,0.6));
        when(level.getGameTime()).thenReturn(100L);when(level.hasChunkAt(any())).thenReturn(true);
        var border=mock(net.minecraft.world.level.border.WorldBorder.class);when(level.getWorldBorder()).thenReturn(border);when(border.isWithinBounds(any(BlockPos.class))).thenReturn(true);
        when(level.getMinBuildHeight()).thenReturn(-64);when(level.getMaxBuildHeight()).thenReturn(320);
        when(level.getBlockState(any())).thenAnswer(a->((BlockPos)a.getArgument(0)).getY()<64?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
        when(level.getFluidState(any())).thenReturn(Fluids.EMPTY.defaultFluidState());when(level.noCollision(eq(mob),any())).thenReturn(true);
        when(nav.createPath(any(BlockPos.class),eq(0))).thenReturn(path);when(path.canReach()).thenReturn(true);
        assertNotNull(FlankRoutes.find(level,mob,new Vec3(100,64,0)));assertTrue(FlankRoutes.active(mob,100));
        assertNull(FlankRoutes.find(level,mob,new Vec3(100,64,0)));verify(nav,times(1)).createPath(any(BlockPos.class),eq(0));
        assertFalse(FlankRoutes.active(mob,301));
    }

    @Test void searchesBothFlanksElevationNeutralAndShortRetreatsWithinBoundedRadius() {
        var from=new Vec3(0,64,0);var points=FlankRoutes.candidates(from,new Vec3(100,64,0),true);
        assertEquals(12,points.size());assertTrue(points.stream().anyMatch(p->p.z>1));assertTrue(points.stream().anyMatch(p->p.z< -1));
        assertTrue(points.stream().anyMatch(p->p.x< -1));assertTrue(points.stream().allMatch(p->p.distanceTo(from)<=16.001 && p.y==64));
        assertEquals(-points.get(0).z,FlankRoutes.candidates(from,new Vec3(100,64,0),false).get(0).z,0.001);
    }
    @Test void avoidsWaterMagmaAndUnsupportedLandings() {
        var level=mock(ServerLevel.class);var feet=new BlockPos(0,64,0);
        when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        when(level.getBlockState(feet.below())).thenReturn(Blocks.STONE.defaultBlockState());
        when(level.getFluidState(any())).thenReturn(Fluids.EMPTY.defaultFluidState());
        assertTrue(FlankRoutes.safeGround(level,feet));
        when(level.getFluidState(feet)).thenReturn(Fluids.WATER.defaultFluidState());assertFalse(FlankRoutes.safeGround(level,feet));
        when(level.getFluidState(feet)).thenReturn(Fluids.EMPTY.defaultFluidState());
        when(level.getBlockState(feet.below())).thenReturn(Blocks.MAGMA_BLOCK.defaultBlockState());assertFalse(FlankRoutes.safeGround(level,feet));
        when(level.getBlockState(feet.below())).thenReturn(Blocks.AIR.defaultBlockState());assertFalse(FlankRoutes.safeGround(level,feet));
    }
}
