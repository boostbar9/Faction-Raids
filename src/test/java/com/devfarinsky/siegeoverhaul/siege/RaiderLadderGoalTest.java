package com.devfarinsky.siegeoverhaul.siege;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RaiderLadderGoalTest extends MinecraftTestSupport {
    private final BlockPos base = new BlockPos(0,64,0);
    private final Map<BlockPos, BlockState> blocks = new HashMap<>();
    private ServerLevel wall(Direction into, int height) {
        var level = mock(ServerLevel.class);
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getBlockState(any())).thenAnswer(call -> blocks.getOrDefault(call.getArgument(0), Blocks.AIR.defaultBlockState()));
        for (int y=0;y<height;y++) {
            blocks.put(base.above(y), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, into.getOpposite()));
            blocks.put(base.relative(into).above(y), Blocks.STONE.defaultBlockState());
        }
        return level;
    }
    @Test void crossingFindsInsideGroundBeyondThickWallAndRejectsDeepDrops() {
        var level=wall(Direction.EAST,4);
        when(level.getFluidState(any())).thenReturn(net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState());
        var route=RaiderLadderGoal.readRoute(level,base);
        // Two-block-thick wall, then ground at original approach height.
        for(int y=0;y<4;y++)blocks.put(base.east(2).above(y),Blocks.STONE.defaultBlockState());
        blocks.put(base.east(3).below(),Blocks.DIRT.defaultBlockState());
        assertEquals(base.east(3),RaiderLadderGoal.findLanding(level,route));
        blocks.remove(base.east(3).below());assertNull(RaiderLadderGoal.findLanding(level,route));
        blocks.put(base.east(3).below(),Blocks.MAGMA_BLOCK.defaultBlockState());assertNull(RaiderLadderGoal.findLanding(level,route));
    }

    @Test void findsExitAboveWallForEveryApproachDirection() {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            blocks.clear();
            var level = wall(dir,4);
            var route = RaiderLadderGoal.readRoute(level,base);
            assertNotNull(route);
            assertEquals(base.relative(dir).above(4), route.exit());
            assertEquals(dir, route.intoWall());
        }
    }
    @Test void rejectsBrokenRungsBlockedExitAndUnloadedRoutes() {
        var level = wall(Direction.EAST,4);
        blocks.remove(base.above(2));
        assertNull(RaiderLadderGoal.readRoute(level,base));
        wall(Direction.EAST,4);
        blocks.put(base.east().above(5), Blocks.STONE.defaultBlockState());
        assertNull(RaiderLadderGoal.readRoute(level,base));
        blocks.remove(base.east().above(5));
        when(level.hasChunkAt(any())).thenReturn(false);
        assertNull(RaiderLadderGoal.readRoute(level,base));
    }
    @Test void supportsTallFortressWalls() {
        var level=wall(Direction.WEST,12);
        var route=RaiderLadderGoal.readRoute(level,base);
        assertNotNull(route);assertEquals(base.west().above(12),route.exit());
    }
    @Test void rejectsColumnsTallerThanSupportedAndNonBaseRungs() {
        var level = wall(Direction.SOUTH,LadderBuilder.MAX_WALL_HEIGHT+1);
        assertNull(RaiderLadderGoal.readRoute(level,base));
        assertNull(RaiderLadderGoal.readRoute(level,base.above()));
    }
}
