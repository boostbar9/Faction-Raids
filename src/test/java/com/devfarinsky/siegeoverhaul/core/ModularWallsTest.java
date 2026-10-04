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
    @Test void unrelatedVariantsKeepTheirPreHollowGeometryExactly() throws Exception {
        // Canonical coordinate/material digests from the solid-profile baseline 864b698.
        var expected = java.util.Map.of(
                DefenseBlueprint.Kind.BARRICADE, "b67b5514ea9709538ec02236993024336233c1621d4ff77066430ec27aa665c1",
                DefenseBlueprint.Kind.WATCHTOWER, "59b0a156024a562ed3027e24ce19c50220cba8df2ce68f12210060b308270340",
                DefenseBlueprint.Kind.GATEHOUSE, "eed9d6684b253b34d2559107278a20e9a0dd2dd063f8bfbab0cd17122bfba0b4",
                DefenseBlueprint.Kind.STAIRS, "8641d9bad0994b597b77b047d3e7c1389b4d5c5b3a5b705058f773aa9396676d");
        for (var entry : expected.entrySet()) {
            var plan = DefenseBlueprint.create(entry.getKey(), BlockPos.ZERO, Direction.SOUTH);
            String canonical = plan.blocks().entrySet().stream().map(e -> {
                BlockPos pos = BlockPos.of(e.getKey());
                return pos.getX() + "," + pos.getY() + "," + pos.getZ() + "=" + e.getValue() + "\n";
            }).sorted().collect(java.util.stream.Collectors.joining());
            assertEquals(entry.getValue(), java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8))), entry.getKey().label);
        }
    }

    @Test void hollowWallAndCornerKeepEveryExposedEndcapInAllRotations() {
        BlockPos origin = new BlockPos(-20, 64, -30);
        for (var kind : java.util.List.of(DefenseBlueprint.Kind.WALL, DefenseBlueprint.Kind.CORNER))
            for (var facing : Direction.Plane.HORIZONTAL) {
                var plan = DefenseBlueprint.create(kind, origin, facing);
                assertEquals(83, plan.blocks().size());
                assertEquals("58 cobblestone + 25 oak planks", plan.materials());
                for (int x = -2; x <= 2; x++) for (int z = 0; z < 5; z++) for (int y = 0; y < 6; y++) {
                    long cell = origin.relative(facing.getClockWise(), x).relative(facing, z - 2).above(y).asLong();
                    String expected = null;
                    if (y < 3 && (Math.abs(x) == 2 || z == 0 || z == 4)) expected = "minecraft:cobblestone";
                    if (y == 3) expected = "minecraft:oak_planks";
                    if (y == 4 && (kind == DefenseBlueprint.Kind.WALL ? Math.abs(x) == 2
                            : x == -2 || z == 4 || x == 2 && z == 0)) expected = "minecraft:cobblestone";
                    assertEquals(expected, plan.blocks().get(cell), kind + " " + facing + " " + BlockPos.of(cell));
                }
                assertFalse(plan.blocks().containsValue("minecraft:air"));
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
