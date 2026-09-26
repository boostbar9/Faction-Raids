package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CaptureRingTest extends MinecraftTestSupport {
    private BlockGetter world(Set<BlockPos> walls) {
        var level=mock(BlockGetter.class,CALLS_REAL_METHODS);
        // A solid endpoint exercises the same self-occlusion without trying
        // to register a new mod block after the plain JUnit registry freezes.
        var core=Blocks.STONE.defaultBlockState();
        when(level.getBlockState(any())).thenAnswer(c -> {
            BlockPos pos=c.getArgument(0);
            return pos.equals(BlockPos.ZERO)?core:walls.contains(pos)?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState();
        });
        when(level.getFluidState(any())).thenReturn(net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState());
        return level;
    }
    @Test void solidCoreDoesNotBlockItsOwnContestantsInAnyDirection() {
        var level=world(Set.of());
        for(Vec3 pos:List.of(new Vec3(3.5,0,.5),new Vec3(-2.5,0,.5),new Vec3(.5,0,3.5),new Vec3(.5,0,-2.5)))
            assertTrue(CaptureRing.visible(level,BlockPos.ZERO,pos),pos.toString());
    }
    @Test void interveningWallsAndRoofsStillBlockCapture() {
        var level=world(Set.of(new BlockPos(1,0,0),new BlockPos(1,1,0),new BlockPos(0,1,0)));
        assertFalse(CaptureRing.visible(level,BlockPos.ZERO,new Vec3(3.5,0,.5)));
        assertFalse(CaptureRing.visible(level,BlockPos.ZERO,new Vec3(.5,2,.5)));
    }
}
