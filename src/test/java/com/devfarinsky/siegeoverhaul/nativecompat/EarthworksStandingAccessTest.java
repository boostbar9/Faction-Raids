package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EarthworksStandingAccessTest extends MinecraftTestSupport {
    private static final AABB START=new AABB(.2,64,.2,.8,65.8,.8),ESCAPE=START.move(1,0,0);
    @Test void damagingAndUnauditedFloorsAreNeverSafeFullBlockFooting(){
        for(var block:new net.minecraft.world.level.block.Block[]{Blocks.MAGMA_BLOCK,Blocks.CAMPFIRE,Blocks.SOUL_CAMPFIRE,Blocks.CACTUS,Blocks.SAND,Blocks.POWDER_SNOW})
            assertFalse(EarthworksStandingAccess.safeFloor(block.defaultBlockState()));
        assertTrue(EarthworksStandingAccess.safeFloor(Blocks.STONE.defaultBlockState()));
    }
    @Test void magmaFloorAndCollisionlessFireFeetAreRefused(){
        var level=level();when(level.getBlockState(any())).thenAnswer(call->{BlockPos pos=call.getArgument(0);return pos.getY()==63?Blocks.MAGMA_BLOCK.defaultBlockState():Blocks.AIR.defaultBlockState();});
        assertFalse(EarthworksStandingAccess.clearSafePrism(level,START));
        var safe=floor(level);when(level.getBlockState(any())).thenAnswer(call->{BlockPos pos=call.getArgument(0);return pos.getY()==63?safe:pos.getY()==64?Blocks.FIRE.defaultBlockState():Blocks.AIR.defaultBlockState();});
        assertFalse(EarthworksStandingAccess.clearSafePrism(level,START));
    }
    @Test void safeStartCannotBorrowAHazardousEscapeLane(){
        var level=level();var safe=floor(level);
        when(level.getBlockState(any())).thenAnswer(call->{BlockPos pos=call.getArgument(0);return pos.getY()==63?(pos.getX()==1?Blocks.MAGMA_BLOCK.defaultBlockState():safe):Blocks.AIR.defaultBlockState();});
        assertTrue(EarthworksStandingAccess.clearSafePrism(level,START));
        assertFalse(EarthworksStandingAccess.clearSafePrism(level,ESCAPE));
        assertFalse(EarthworksStandingAccess.clearSafePrism(level,START.minmax(ESCAPE)));
    }
    @Test void unloadedSweepRefusesBeforeBlockOrFluidReads(){
        var level=level();when(level.hasChunkAt(any())).thenReturn(false);
        assertFalse(EarthworksStandingAccess.clearSafePrism(level,START.minmax(ESCAPE)));
        verify(level,never()).getBlockState(any());verify(level,never()).getFluidState(any());
    }
    private static ServerLevel level(){
        var level=mock(ServerLevel.class);var border=mock(WorldBorder.class);when(level.getWorldBorder()).thenReturn(border);when(border.isWithinBounds(any(BlockPos.class))).thenReturn(true);
        when(level.getMinBuildHeight()).thenReturn(-64);when(level.getMaxBuildHeight()).thenReturn(320);when(level.hasChunkAt(any())).thenReturn(true);when(level.getFluidState(any())).thenReturn(Fluids.EMPTY.defaultFluidState());return level;
    }
    private static BlockState floor(ServerLevel level){var state=mock(BlockState.class);when(state.is(Blocks.STONE)).thenReturn(true);when(state.isFaceSturdy(eq(level),any(BlockPos.class),eq(Direction.UP))).thenReturn(true);return state;}
}
