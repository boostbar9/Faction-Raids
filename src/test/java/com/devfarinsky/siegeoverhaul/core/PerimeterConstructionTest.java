package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PerimeterConstructionTest extends MinecraftTestSupport {
    private PerimeterBlueprint.Plan plan() { return PerimeterBlueprint.create(Set.of(new ChunkPos(0,0)),
            (x,z)->PerimeterBlueprint.Surface.ready(64),PerimeterBlueprint.Palette.COBBLESTONE); }
    private ServerLevel level() {
        var level=mock(ServerLevel.class);when(level.getMinBuildHeight()).thenReturn(-64);
        when(level.getAllEntities()).thenReturn(List.of());
        var storage=mock(net.minecraft.world.level.storage.DimensionDataStorage.class);
        when(level.getDataStorage()).thenReturn(storage);
        when(storage.computeIfAbsent(any(),any(),anyString())).thenAnswer(call -> ((java.util.function.Supplier<?>)call.getArgument(1)).get());
        when(level.getMaxBuildHeight()).thenReturn(320);when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getWorldBorder()).thenReturn(new WorldBorder());
        when(level.getBlockState(any())).thenAnswer(call->((BlockPos)call.getArgument(0)).getY()<64
                ?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
        return level;
    }
    @Test void completeDryOwnedFootprintIsAccepted() {assertNull(PerimeterConstruction.siteProblem(level(),plan(),p->true));}
    @Test void reservationRetainsWalkwayHeadroomWithoutClaimingTheHollowCourtyard() {
        var plan = plan();
        var reserved = PerimeterConstruction.reservedCells(plan);
        var headroom = new BlockPos(2, 69, 8);
        assertTrue(plan.clearance().contains(headroom.asLong()));
        assertFalse(plan.blocks().containsKey(headroom.asLong()));
        assertTrue(reserved.contains(headroom));
        assertTrue(plan.blocks().keySet().stream().allMatch(cell -> reserved.contains(BlockPos.of(cell))));
        assertEquals(1320, reserved.size());
        assertFalse(reserved.contains(new BlockPos(8, 64, 8)));
        assertFalse(reserved.contains(new BlockPos(8, 69, 8)));
        assertThrows(UnsupportedOperationException.class, reserved::clear);
    }
    @Test void unclaimedInnerFootprintOrHeadroomFailsEntirePlan() {
        assertNotNull(PerimeterConstruction.siteProblem(level(),plan(),p->p.getX()!=4));
        assertNotNull(PerimeterConstruction.siteProblem(level(),plan(),p->p.getY()!=69));
    }
    @Test void allChunksAreCheckedBeforeReadingTerrain() {
        var level=level();when(level.hasChunkAt(any())).thenReturn(false);
        assertNotNull(PerimeterConstruction.siteProblem(level,plan(),p->true));verify(level,never()).getBlockState(any());
    }
    @Test void housesInventoriesFluidAndHazardsAreNeverSilentlyOmitted() {
        for(var block:List.of(Blocks.OAK_PLANKS,Blocks.CHEST,Blocks.WATER,Blocks.LAVA,Blocks.NETHER_PORTAL)) {
            var level=level();when(level.getBlockState(new BlockPos(2,68,2))).thenReturn(block.defaultBlockState());
            assertNotNull(PerimeterConstruction.siteProblem(level,plan(),p->true),block.toString());
        }
    }
    @Test void occupiedBuildCellsAndUnsafeGroundRejectCommission() {
        var level=level();when(level.getEntities(isNull(Entity.class),any(AABB.class),any())).thenReturn(List.of(mock(Entity.class)));
        assertNotNull(PerimeterConstruction.siteProblem(level,plan(),p->true));
        level=level();when(level.getBlockState(new BlockPos(0,63,0))).thenReturn(Blocks.WATER.defaultBlockState());
        assertNotNull(PerimeterConstruction.siteProblem(level,plan(),p->true));
    }
    @Test void anotherReservedNativeAreaBlocksTheWholeOverlappingPlan() {
        var level=level();var area=mock(Entity.class);var tag=new CompoundTag();
        tag.putLong(PerimeterConstruction.SITE_MIN,new BlockPos(4,64,4).asLong());
        tag.putLong(PerimeterConstruction.SITE_MAX,new BlockPos(8,69,8).asLong());
        when(area.getPersistentData()).thenReturn(tag);when(level.getEntitiesOfClass(eq(Entity.class),any(),any())).thenReturn(List.of(area));
        when(level.getAllEntities()).thenReturn(List.of(area)); when(area.isAlive()).thenReturn(true);
        try(var bridge=mockStatic(WorkersBridge.class)) {bridge.when(()->WorkersBridge.isBuildArea(area)).thenReturn(true);
            assertNotNull(PerimeterConstruction.siteProblem(level,plan(),p->true));}
    }
    @Test void nativeScanWorkIsBoundedBeforeHandingOffLargeSparseRings() {
        assertTrue(PerimeterConstruction.nativeScanWithinBudget(plan()));
        var chunks=new java.util.HashSet<ChunkPos>();
        for(int x=0;x<4;x++)for(int z=0;z<4;z++)chunks.add(new ChunkPos(x,z));
        var large=PerimeterBlueprint.create(chunks,(x,z)->PerimeterBlueprint.Surface.ready(64),PerimeterBlueprint.Palette.COBBLESTONE);
        assertTrue(large.valid());assertFalse(PerimeterConstruction.nativeScanWithinBudget(large));
    }
    @Test void materialsCountSharedCornerCellsOnlyOnce() {
        assertEquals(968,plan().blocks().size());assertTrue(PerimeterConstruction.materials(plan()).contains("748 cobblestone"));
        assertTrue(PerimeterConstruction.materials(plan()).contains("220 oak planks"));
    }
}
