package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProtectedStorageContextTest extends MinecraftTestSupport {
    private ServerLevel level() {
        var level=mock(ServerLevel.class);var border=mock(WorldBorder.class);
        when(level.getMinBuildHeight()).thenReturn(-64);when(level.getMaxBuildHeight()).thenReturn(320);
        when(level.getWorldBorder()).thenReturn(border);when(border.isWithinBounds(any(BlockPos.class))).thenReturn(true);
        when(level.hasChunkAt(any(BlockPos.class))).thenReturn(true);return level;
    }
    @Test void scanReadNeighborhoodIsLoadedBeforeAnyBlockOrContainerRead() {
        var level=level();var pos=new BlockPos(15,65,0);
        when(level.hasChunkAt(new BlockPos(16,66,0))).thenReturn(false);
        assertThrows(IllegalStateException.class,()->ProtectedStorageContext.envelope(level,new AABB(pos,pos),1,1));
        verify(level,never()).getBlockState(any());verify(level,never()).getBlockEntity(any());
    }
    @Test void oldCompoundCellsRemainRequiredWhenTheNewClosePositionIsLoaded() {
        var level=level();var old=new BlockPos(33,65,0);var current=new BlockPos(1,65,0);
        var container=new CompoundContainer(new SimpleContainer(27),new SimpleContainer(27));
        when(level.hasChunkAt(old)).thenReturn(false);
        assertThrows(IllegalStateException.class,()->ProtectedStorageContext.cleanup(level,container,current,Set.of(old,old.east())));
        verify(level,never()).getBlockState(any());verify(level,never()).getBlockEntity(any());
        when(level.hasChunkAt(old)).thenReturn(true);
        assertDoesNotThrow(()->ProtectedStorageContext.cleanup(level,container,current,Set.of(old,old.east())));
    }
    @Test void clearingPositionStillRequiresOnlyKnownOldCompoundHalvesToBeLoaded() {
        var level=level();var old=new BlockPos(33,65,0);
        var container=new CompoundContainer(new SimpleContainer(27),new SimpleContainer(27));
        assertThrows(IllegalStateException.class,()->ProtectedStorageContext.cleanup(level,container,null));
        assertDoesNotThrow(()->ProtectedStorageContext.cleanup(level,container,null,Set.of(old,old.east())));
        when(level.hasChunkAt(old)).thenReturn(false);
        assertThrows(IllegalStateException.class,()->ProtectedStorageContext.cleanup(level,container,null,Set.of(old,old.east())));
        verify(level,never()).getBlockState(any());verify(level,never()).getBlockEntity(any());
    }
    @Test void dirtyNotificationCannotReadAcrossAnUnloadedSecondNeighborConductor() {
        var level=level();var chest=new BlockPos(14,65,0);var second=chest.offset(2,0,0);
        when(level.hasChunkAt(second)).thenReturn(false);
        assertThrows(IllegalStateException.class,()->ProtectedStorageContext.dirtyReadPositions(level,Set.of(chest)));
        verify(level,never()).getBlockState(any());verify(level,never()).getBlockEntity(any());
        var compound=new CompoundContainer(new SimpleContainer(27),new SimpleContainer(27));
        assertThrows(IllegalStateException.class,()->ProtectedStorageContext.cleanup(level,compound,null,Set.of(chest,chest.west())));
        when(level.hasChunkAt(second)).thenReturn(true);
        var reads=ProtectedStorageContext.dirtyReadPositions(level,Set.of(chest));
        assertTrue(reads.contains(chest.above(2)));assertTrue(reads.contains(chest.below(2)));
        assertTrue(reads.contains(chest.north(2)));assertTrue(reads.contains(chest.east(2)));
        assertEquals(25,reads.size());
    }

    @Test void excessiveEnvelopeFailsBeforeLoadedOrStateQueries() {
        var level=level();
        assertThrows(IllegalStateException.class,()->ProtectedStorageContext.envelope(level,new AABB(0,65,0,100,100,100),1,1));
        verify(level,never()).hasChunkAt(any());verify(level,never()).getBlockState(any());
    }
}
