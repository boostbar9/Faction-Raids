package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class ModularWallsTest extends MinecraftTestSupport {
    @Test void snappingIsStableAcrossNegativeCoordinatesAndAdjacentCells() {
        for (int x = -17; x <= 17; x++) for (int z = -17; z <= 17; z++) {
            var click = new BlockPos(x, 64, z);
            var anchor = DefenseBlueprint.Kind.WALL.anchor(click);
            assertEquals(0, Math.floorMod(anchor.getX(), 5));
            assertEquals(0, Math.floorMod(anchor.getZ(), 5));
            assertTrue(Math.abs(x - anchor.getX()) <= 2);
            assertTrue(Math.abs(z - anchor.getZ()) <= 2);
            assertEquals(anchor, DefenseBlueprint.Kind.CORNER.anchor(anchor));
            assertEquals(64, anchor.getY());
            assertEquals(click, DefenseBlueprint.Kind.WATCHTOWER.anchor(click));
        }
    }
    @Test void adjacentStraightAndCornerExitsHaveContinuousDecksAndHeadroom() {
        var origin = new BlockPos(0, 64, 0);
        for (var facing : Direction.Plane.HORIZONTAL) {
            var straight = DefenseBlueprint.create(DefenseBlueprint.Kind.WALL, origin, facing);
            var corner = DefenseBlueprint.create(DefenseBlueprint.Kind.CORNER, origin.relative(facing, 5), facing);
            var outgoing = DefenseBlueprint.create(DefenseBlueprint.Kind.WALL,
                    origin.relative(facing, 5).relative(facing.getClockWise(), 5), facing.getClockWise());
            var sites = new HashSet<>(straight.footprint());
            assertTrue(corner.footprint().stream().noneMatch(sites::contains));
            sites.addAll(corner.footprint());
            assertTrue(outgoing.footprint().stream().noneMatch(sites::contains));
            var blocks = new HashSet<>(straight.blocks().keySet());
            blocks.addAll(corner.blocks().keySet()); blocks.addAll(outgoing.blocks().keySet());
            for (int lane = -1; lane <= 1; lane++) {
                for (int step = 0; step <= 5; step++) {
                    var cell = origin.relative(facing, step).relative(facing.getClockWise(), lane).above(3);
                    assertTrue(blocks.contains(cell.asLong()));
                    assertFalse(blocks.contains(cell.above().asLong()));
                    assertFalse(blocks.contains(cell.above(2).asLong()));
                    cell = origin.relative(facing, 5 + lane).relative(facing.getClockWise(), step).above(3);
                    assertTrue(blocks.contains(cell.asLong()));
                    assertFalse(blocks.contains(cell.above().asLong()));
                    assertFalse(blocks.contains(cell.above(2).asLong()));
                }
            }
        }
    }
    @Test void stairsReachTheSameDeckWithoutHeadObstructions() {
        for (var facing : Direction.Plane.HORIZONTAL) {
            var plan = DefenseBlueprint.create(DefenseBlueprint.Kind.STAIRS, BlockPos.ZERO, facing);
            for (int lane = -1; lane <= 1; lane++) for (int step = 0; step <= 4; step++) {
                var cell = BlockPos.ZERO.relative(facing, step - 2).relative(facing.getClockWise(), lane).above(Math.min(step, 3));
                assertTrue(plan.blocks().containsKey(cell.asLong()));
                assertFalse(plan.blocks().containsKey(cell.above().asLong()));
                assertFalse(plan.blocks().containsKey(cell.above(2).asLong()));
            }
        }
    }
}
