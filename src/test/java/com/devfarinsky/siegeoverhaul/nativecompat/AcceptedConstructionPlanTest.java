package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AcceptedConstructionPlanTest extends MinecraftTestSupport {
    static CompoundTag blueprint(int width, int x, int y, int z, BlockState state) {
        CompoundTag structure = new CompoundTag(), cell = new CompoundTag();
        structure.putInt("width", width); structure.putString("facing", "south");
        cell.putInt("x", x); cell.putInt("y", y); cell.putInt("z", z);
        cell.put("state", NbtUtils.writeBlockState(state));
        ListTag cells = new ListTag(); cells.add(cell); structure.put("blocks", cells);
        return structure;
    }

    @Test void nativeMirrorAndFacingRoundTripAtNegativeCoordinates() {
        BlockPos origin = new BlockPos(-29, 62, -48);
        for (Direction facing : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
            var plan = AcceptedConstructionPlan.decode(origin, facing, 5, 9, 7,
                    blueprint(5, 1, 3, 6, Blocks.COBBLESTONE.defaultBlockState()));
            BlockPos expected = origin.relative(facing, 6).relative(facing.getClockWise(), 3).above(3);
            assertEquals(java.util.Set.of(expected), plan.cells.keySet());
            var loaded = AcceptedConstructionPlan.load(plan.save());
            assertEquals(plan.cells, loaded.cells);
            assertEquals(origin, loaded.origin);
            assertEquals(facing, loaded.facing);
        }
    }

    @Test void acceptedGeometryAndSerializedCopiesCannotBeChangedThroughInputAliases() {
        CompoundTag nbt = blueprint(5, 1, 0, 0, Blocks.OAK_PLANKS.defaultBlockState());
        var plan = AcceptedConstructionPlan.decode(BlockPos.ZERO, Direction.SOUTH, 5, 5, 5, nbt);
        nbt.putInt("width", 900);
        CompoundTag saved = plan.save(); saved.getCompound("Structure").putInt("width", 900);
        assertEquals(5, plan.save().getCompound("Structure").getInt("width"));
        assertThrows(UnsupportedOperationException.class, () -> plan.cells.clear());
    }

    @Test void negativeYRebaseAndEveryOutOfEnvelopeAxisAreRejected() {
        for (int[] xyz : List.of(new int[]{0,-1,0}, new int[]{-1,0,0}, new int[]{0,0,-1},
                new int[]{5,0,0}, new int[]{0,5,0}, new int[]{0,0,5})) {
            var nbt = blueprint(5, xyz[0], xyz[1], xyz[2], Blocks.COBBLESTONE.defaultBlockState());
            assertThrows(IllegalArgumentException.class,
                    () -> AcceptedConstructionPlan.decode(BlockPos.ZERO, Direction.SOUTH, 5, 5, 5, nbt));
        }
    }

    @Test void inventoriesMultipartAndEntitySpawningNeverEnterGuardedJobs() {
        for (var block : List.of(Blocks.CHEST, Blocks.OAK_DOOR, Blocks.WHITE_BED, Blocks.WATER, Blocks.SAND)) {
            assertThrows(IllegalArgumentException.class, () -> AcceptedConstructionPlan.decode(BlockPos.ZERO,
                    Direction.SOUTH, 5, 5, 5, blueprint(5, 0, 0, 0, block.defaultBlockState())));
        }
        var nbt = blueprint(5, 0, 0, 0, Blocks.COBBLESTONE.defaultBlockState());
        ListTag entities = new ListTag(); entities.add(new CompoundTag()); nbt.put("entities", entities);
        assertThrows(IllegalArgumentException.class,
                () -> AcceptedConstructionPlan.decode(BlockPos.ZERO, Direction.SOUTH, 5, 5, 5, nbt));
    }

    @Test void geometryMutationAndDuplicateCellsAreRejected() throws Exception {
        Area area = new Area();
        var plan = AcceptedConstructionPlan.capture(area);
        assertTrue(plan.matches(area));
        area.origin = area.origin.above(); assertFalse(plan.matches(area));
        area.origin = BlockPos.ZERO; area.facing = Direction.NORTH; assertFalse(plan.matches(area));
        area.facing = Direction.SOUTH;
        ListTag blocks = area.structure.getList("blocks", 10); blocks.add(blocks.get(0).copy());
        assertThrows(IllegalArgumentException.class, () -> AcceptedConstructionPlan.capture(area));
    }

    public static class Area {
        BlockPos origin = BlockPos.ZERO;
        Direction facing = Direction.SOUTH;
        CompoundTag structure = blueprint(5, 0, 0, 0, Blocks.COBBLESTONE.defaultBlockState());
        public BlockPos getOriginPos() { return origin; }
        public Direction getFacing() { return facing; }
        public int getWidthSize() { return 5; }
        public int getDepthSize() { return 5; }
        public int getHeightSize() { return 5; }
        public CompoundTag getStructureNBT() { return structure; }
    }
}
