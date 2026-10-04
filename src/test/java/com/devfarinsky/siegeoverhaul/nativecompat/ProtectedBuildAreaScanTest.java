package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.talhanation.workers.world.BuildBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.Stack;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProtectedBuildAreaScanTest extends MinecraftTestSupport {
    private ProtectedBuildArea area(ServerLevel level) {
        var area = mock(ProtectedBuildArea.class, CALLS_REAL_METHODS);
        doReturn(level).when(area).getCommandSenderWorld();
        doReturn(new AABB(0, 64, 0, 2, 64, 0)).when(area).getArea();
        doReturn(3).when(area).getWidthSize(); doReturn(1).when(area).getDepthSize(); doReturn(1).when(area).getHeightSize();
        area.stackToPlace = new Stack<>(); area.stackToPlaceMultiBlock = new Stack<>(); area.stackToBreak = new Stack<>();
        return area;
    }

    @Test void nativeScanRetainsFirstPendingEntryAndNeverUsesCompletedRecipeCells() {
        var level = mock(ServerLevel.class); var area = area(level);
        var a = new BlockPos(0, 64, 0); var b = new BlockPos(1, 64, 0); var c = new BlockPos(2, 64, 0);
        var first = new BuildBlock(a, Blocks.COBBLESTONE.defaultBlockState());
        area.stackToPlace.push(first);
        area.stackToPlace.push(new BuildBlock(a, Blocks.OAK_PLANKS.defaultBlockState()));
        area.stackToPlaceMultiBlock.push(new BuildBlock(a, Blocks.DIRT.defaultBlockState()));
        area.stackToPlaceMultiBlock.push(new BuildBlock(b, Blocks.OAK_PLANKS.defaultBlockState()));
        when(level.getBlockState(a)).thenReturn(Blocks.COBBLESTONE.defaultBlockState());
        when(level.getBlockState(b)).thenReturn(Blocks.STONE.defaultBlockState());
        when(level.getBlockState(c)).thenReturn(Blocks.CHEST.defaultBlockState());
        area.scanBreakArea();
        assertEquals(java.util.List.of(b), area.stackToBreak);
        // No index survives the scan: live pending mutations are reflected immediately.
        area.stackToPlace.clear(); area.stackToPlaceMultiBlock.clear();
        assertNull(area.getStateFromPos(a)); assertNull(area.getStateFromPos(b));
        area.scanBreakArea(); assertTrue(area.stackToBreak.isEmpty());
    }

    @Test void scanExceptionClearsTheIndexAndNativeBudgetRejectsBeforeWorldReads() {
        var level = mock(ServerLevel.class); var area = area(level); var pos = new BlockPos(0, 64, 0);
        area.stackToPlace.push(new BuildBlock(pos, Blocks.COBBLESTONE.defaultBlockState()));
        when(level.getBlockState(pos)).thenThrow(new IllegalStateException("fixture"));
        assertThrows(IllegalStateException.class, area::scanBreakArea);
        area.stackToPlace.clear(); assertNull(area.getStateFromPos(pos));
        clearInvocations(level);
        doReturn(512).when(area).getWidthSize(); doReturn(512).when(area).getDepthSize(); doReturn(384).when(area).getHeightSize();
        assertThrows(IllegalStateException.class, area::scanBreakArea); verifyNoInteractions(level);
    }
}
