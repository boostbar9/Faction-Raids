package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData.RaidState;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CampBuilderAccessTest extends MinecraftTestSupport {
    private static final class Site {
        final ServerLevel level = mock(ServerLevel.class);
        final RaidState raid = new RaidState("team:test", "siege_core", 0);
        final Mob worker = mock(Mob.class);
        final PathNavigation navigation = mock(PathNavigation.class);
        final WorldBorder border = mock(WorldBorder.class);
        final Map<BlockPos, BlockState> world = new HashMap<>();
        final BlockPos center = new BlockPos(0, 64, 0);
        Site() {
            raid.campPos = center;
            when(level.hasChunkAt(any())).thenReturn(true);
            when(level.getMinBuildHeight()).thenReturn(-64);
            when(level.getMaxBuildHeight()).thenReturn(320);
            when(level.getWorldBorder()).thenReturn(border);
            when(border.isWithinBounds(any(BlockPos.class))).thenReturn(true);
            when(level.getHeight(any(), anyInt(), anyInt())).thenReturn(64);
            when(level.getBlockState(any())).thenAnswer(c -> {
                BlockPos p = c.getArgument(0);
                return world.getOrDefault(p, (p.getY() < 64 ? Blocks.STONE : Blocks.AIR).defaultBlockState());
            });
            when(level.noCollision(nullable(net.minecraft.world.entity.Entity.class), any(AABB.class))).thenReturn(true);
            when(worker.position()).thenReturn(new Vec3(0.5, 64, 0.5));
            when(worker.getBoundingBox()).thenReturn(new AABB(0.2, 64, 0.2, 0.8, 65.95, 0.8));
            when(worker.getNavigation()).thenReturn(navigation);
        }
        AABB body(BlockPos p) { return new AABB(p.getX()+0.2,p.getY(),p.getZ()+0.2,p.getX()+0.8,p.getY()+1.95,p.getZ()+0.8); }
    }

    @Test void spawnUsesSurfaceInsteadOfCloserUndergroundPocket() {
        var s = new Site();
        s.raid.campPos = new BlockPos(0, 62, 0);
        for (int x=-3;x<=3;x++) for (int z=-3;z<=3;z++) {
            s.world.put(new BlockPos(x,61,z),Blocks.AIR.defaultBlockState());
            s.world.put(new BlockPos(x,62,z),Blocks.AIR.defaultBlockState());
        }
        BlockPos spawn = CampBuilderAccess.spawnPosition(s.level,s.raid,s.center);
        assertNotNull(spawn); assertEquals(64,spawn.getY());
        verify(s.level,never()).setBlock(any(),any(),anyInt());
    }

    @Test void unloadedSpawnColumnsNeverReadHeightOrBlocks() {
        var s=new Site();when(s.level.hasChunkAt(any())).thenReturn(false);
        assertNull(CampBuilderAccess.spawnPosition(s.level,s.raid,s.center));
        verify(s.level,never()).getHeight(any(),anyInt(),anyInt());
        verify(s.level,never()).getBlockState(any());
    }

    @Test void steepSurfaceAndOccupiedBodiesDoNotSpawn() {
        var s=new Site();when(s.level.getHeight(any(),anyInt(),anyInt())).thenReturn(68);
        assertNull(CampBuilderAccess.spawnPosition(s.level,s.raid,s.center));
        when(s.level.getHeight(any(),anyInt(),anyInt())).thenReturn(64);
        when(s.level.noCollision(nullable(net.minecraft.world.entity.Entity.class),any(AABB.class))).thenReturn(false);
        assertNull(CampBuilderAccess.spawnPosition(s.level,s.raid,s.center));
    }

    @Test void rejectsHazardsHeadroomAndFutureBodyPlacements() {
        var s=new Site();BlockPos p=s.center;
        for(var hazard:List.of(Blocks.MAGMA_BLOCK,Blocks.CAMPFIRE,Blocks.SOUL_CAMPFIRE,Blocks.CACTUS,Blocks.WATER,Blocks.LAVA)) {
            s.world.put(p.below(),hazard.defaultBlockState());
            assertFalse(CampBuilderAccess.safe(s.level,s.raid,p,s.body(p),s.worker),hazard.toString());
        }
        s.world.clear();
        for(var hazard:List.of(Blocks.COBWEB,Blocks.WITHER_ROSE,Blocks.FIRE,Blocks.NETHER_PORTAL,Blocks.WATER,Blocks.LAVA,Blocks.STONE)) {
            s.world.put(p.above(),hazard.defaultBlockState());
            assertFalse(CampBuilderAccess.safe(s.level,s.raid,p,s.body(p),s.worker),hazard.toString());
        }
        s.world.clear();assertTrue(CampBuilderAccess.safe(s.level,s.raid,p,s.body(p),s.worker));
        s.raid.pendingCampBlocks.put(p.above().asLong(),"minecraft:stone");
        assertFalse(CampBuilderAccess.safe(s.level,s.raid,p,s.body(p),s.worker));
        s.raid.pendingCampBlocks.clear();s.raid.pendingFortifications.put(p.asLong(),"minecraft:oak_fence");
        assertFalse(CampBuilderAccess.safe(s.level,s.raid,p,s.body(p),s.worker));
    }

    @Test void wholeBodyMustBeLoadedAndWithinBorder() {
        var s=new Site();AABB wide=new AABB(-0.2,64,-0.2,1.2,66,1.2);
        when(s.border.isWithinBounds(new BlockPos(-1,64,0))).thenReturn(false);
        assertFalse(CampBuilderAccess.safe(s.level,s.raid,s.center,wide,s.worker));
        verify(s.level,never()).getBlockState(any());
        when(s.border.isWithinBounds(any(BlockPos.class))).thenReturn(true);
        when(s.level.hasChunkAt(new BlockPos(1,65,1))).thenReturn(false);
        assertFalse(CampBuilderAccess.safe(s.level,s.raid,s.center,wide,s.worker));
        verify(s.level,never()).getBlockState(any());
    }

    @Test void oneMultiTargetSearchCanChooseFartherReachablePosition() {
        var s=new Site();BlockPos reachable=s.center.east(3);Path path=mock(Path.class);
        when(path.canReach()).thenReturn(true);when(path.getTarget()).thenReturn(reachable);
        when(s.navigation.createPath(anySet(),eq(0))).thenAnswer(c->{
            Set<BlockPos> choices=c.getArgument(0);
            assertTrue(choices.contains(s.center.east()));assertTrue(choices.contains(reachable));
            assertTrue(choices.size()<=84);return path;
        });
        assertEquals(reachable,CampBuilderAccess.workPosition(s.level,s.raid,s.worker,s.center));
        verify(s.navigation,times(1)).createPath(anySet(),eq(0));
        verify(s.navigation,never()).moveTo(any(Path.class),anyDouble());
    }

    @Test void partialMissingOrUnvalidatedPathsCannotSetWorkPosition() {
        var s=new Site();Path path=mock(Path.class);
        when(s.navigation.createPath(anySet(),eq(0))).thenReturn(path);
        when(path.getTarget()).thenReturn(s.center.east());
        assertNull(CampBuilderAccess.workPosition(s.level,s.raid,s.worker,s.center));
        when(path.canReach()).thenReturn(true);when(path.getTarget()).thenReturn(s.center.east(20));
        assertNull(CampBuilderAccess.workPosition(s.level,s.raid,s.worker,s.center));
        when(s.navigation.createPath(anySet(),eq(0))).thenReturn(null);
        assertNull(CampBuilderAccess.workPosition(s.level,s.raid,s.worker,s.center));
    }

    @Test void fallbackTickDoesNotRedirectOrBuildOnAnUnreachableJob() {
        var s=new Site();UUID id=UUID.randomUUID();s.raid.campWorkers.add(id);s.raid.campUsesWorkers=true;
        s.raid.pendingCampBlocks.put(s.center.asLong(),"minecraft:stone_bricks");
        when(s.level.getEntity(id)).thenReturn(s.worker);when(s.worker.isAlive()).thenReturn(true);
        try(var claims=mockStatic(com.devfarinsky.siegeoverhaul.compat.CampClaims.class);
            var bridge=mockStatic(WorkersBridge.class)) {
            claims.when(()->com.devfarinsky.siegeoverhaul.compat.CampClaims.owns(s.level,s.raid)).thenReturn(true);
            bridge.when(WorkersBridge::available).thenReturn(true);
            CampBuilder.tick(s.level,s.raid,(p,b)->fail("Unreachable work must wait"));
            bridge.verify(()->WorkersBridge.moveBuilder(eq(s.worker),any()),never());
        }
        assertEquals(1,s.raid.pendingCampBlocks.size());
        assertEquals(s.raid.pendingCampBlocks,RaidState.load(s.raid.save()).pendingCampBlocks);
    }
}
