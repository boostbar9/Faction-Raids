package com.devfarinsky.siegeoverhaul.camp;
import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class CampOccupancyTest extends MinecraftTestSupport {
    @Test void guardsAndVisitorsBlockConstructionUntilTheyLeave() {
        var level=mock(ServerLevel.class);var raid=new RaidSavedData.RaidState("team:test","siege_core",0);
        var cell=new BlockPos(0,65,0);raid.pendingCampBlocks.put(cell.asLong(),"minecraft:stone_bricks");
        var mob=mock(LivingEntity.class);when(mob.isAlive()).thenReturn(true);
        // Feet can be outside the cell while the head/body intersects the planned wall.
        when(mob.getBoundingBox()).thenReturn(new AABB(-.2,64,.2,.4,66,.8));
        when(level.getEntitiesOfClass(eq(LivingEntity.class),any(AABB.class))).thenReturn(List.of(mob));
        when(level.hasChunkAt(cell)).thenReturn(true);when(level.getBlockState(cell)).thenReturn(Blocks.AIR.defaultBlockState());
        var saved=raid.save();assertTrue(NativeCampConstruction.occupiedBlueprint(level,raid));
        assertEquals(saved,raid.save());verify(level,times(1)).getEntitiesOfClass(eq(LivingEntity.class),any(AABB.class));
        when(mob.getBoundingBox()).thenReturn(new AABB(1.1,64,.2,1.7,66,.8));
        assertFalse(NativeCampConstruction.occupiedBlueprint(level,raid));
        verify(level,never()).setBlock(any(),any(),anyInt());
    }
    @Test void completedCellsSpectatorsAndDeadEntitiesDoNotPauseJobs() {
        var level=mock(ServerLevel.class);var raid=new RaidSavedData.RaidState("team:test","siege_core",0);
        raid.pendingCampBlocks.put(BlockPos.ZERO.asLong(),"minecraft:stone_bricks");
        var mob=mock(LivingEntity.class);when(mob.isAlive()).thenReturn(true);when(mob.getBoundingBox()).thenReturn(new AABB(BlockPos.ZERO));
        when(level.getEntitiesOfClass(eq(LivingEntity.class),any(AABB.class))).thenReturn(List.of(mob));
        when(level.hasChunkAt(any())).thenReturn(true);when(level.getBlockState(any())).thenReturn(Blocks.STONE_BRICKS.defaultBlockState());
        assertFalse(NativeCampConstruction.occupiedBlueprint(level,raid));
        when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());when(mob.isSpectator()).thenReturn(true);
        assertFalse(NativeCampConstruction.occupiedBlueprint(level,raid));
        when(mob.isSpectator()).thenReturn(false);when(mob.isAlive()).thenReturn(false);
        assertFalse(NativeCampConstruction.occupiedBlueprint(level,raid));
        raid.pendingCampBlocks.clear();clearInvocations(level);
        assertFalse(NativeCampConstruction.occupiedBlueprint(level,raid));verifyNoInteractions(level);
    }
    @Test void nativeCrewDoesNotFreezeItsOwnEscapeMovement() {
        var level=mock(ServerLevel.class);var raid=new RaidSavedData.RaidState("team:test","siege_core",0);
        raid.pendingCampBlocks.put(BlockPos.ZERO.asLong(),"minecraft:stone_bricks");
        var worker=mock(LivingEntity.class);var id=java.util.UUID.randomUUID();
        when(worker.getUUID()).thenReturn(id);when(worker.isAlive()).thenReturn(true);raid.campWorkers.add(id);
        when(level.getEntitiesOfClass(eq(LivingEntity.class),any(AABB.class))).thenReturn(List.of(worker));
        assertFalse(NativeCampConstruction.occupiedBlueprint(level,raid));
        verify(worker,never()).getBoundingBox();
    }
}
