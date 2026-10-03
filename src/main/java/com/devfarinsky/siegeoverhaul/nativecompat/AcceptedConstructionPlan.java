package com.devfarinsky.siegeoverhaul.nativecompat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable exact world-space contract for the public Workers 2 blueprint transform. */
final class AcceptedConstructionPlan {
    static final int MAX_CELLS = 65536;
    private static final ClassValue<Map<String, java.lang.reflect.Method>> METHODS = new ClassValue<>() {
        @Override protected Map<String, java.lang.reflect.Method> computeValue(Class<?> type) {
            return new java.util.concurrent.ConcurrentHashMap<>();
        }
    };
    final BlockPos origin;
    final Direction facing;
    final int width, depth, height;
    private final CompoundTag structure;
    final Map<BlockPos, BlockState> cells;

    private AcceptedConstructionPlan(BlockPos origin, Direction facing, int width, int depth, int height,
                                     CompoundTag structure, Map<BlockPos, BlockState> cells) {
        this.origin = origin.immutable(); this.facing = facing;
        this.width = width; this.depth = depth; this.height = height;
        this.structure = structure.copy(); this.cells = Map.copyOf(cells);
    }

    static AcceptedConstructionPlan capture(Object area) throws ReflectiveOperationException {
        return decode((BlockPos) call(area, "getOriginPos"), (Direction) call(area, "getFacing"),
                (Integer) call(area, "getWidthSize"), (Integer) call(area, "getDepthSize"),
                (Integer) call(area, "getHeightSize"), (CompoundTag) call(area, "getStructureNBT"));
    }

    static AcceptedConstructionPlan decode(BlockPos origin, Direction facing, int width, int depth, int height,
                                            CompoundTag structure) {
        if (origin == null || facing == null || facing.getAxis().isVertical() || width < 1 || depth < 1
                || height < 1 || width > 512 || depth > 512 || height > 384 || structure == null
                || structure.getInt("width") != width || structure.contains("entities")
                && !structure.getList("entities", Tag.TAG_COMPOUND).isEmpty())
            throw new IllegalArgumentException("Unsupported native blueprint envelope");
        Direction scan = Direction.byName(structure.getString("facing"));
        if (scan == null || scan.getAxis().isVertical()) throw new IllegalArgumentException("Unknown scan facing");
        ListTag list = structure.getList("blocks", Tag.TAG_COMPOUND);
        if (list.isEmpty() || list.size() > MAX_CELLS) throw new IllegalArgumentException("Unsupported plan size");
        int turns = Math.floorMod(facing.get2DDataValue() - scan.get2DDataValue(), 4);
        Rotation rotation = switch (turns) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
        Map<BlockPos, BlockState> cells = new LinkedHashMap<>();
        for (Tag entry : list) {
            CompoundTag block = (CompoundTag) entry;
            int x = block.getInt("x"), y = block.getInt("y"), z = block.getInt("z");
            if (x < 0 || x >= width || y < 0 || y >= height || z < 0 || z >= depth)
                throw new IllegalArgumentException("Blueprint lies outside native scan envelope");
            BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), block.getCompound("state"))
                    .rotate(rotation);
            // Current commissioned templates are full-block structures. Reject any new
            // multipart/inventory/physics-capable material until its mutation path is audited.
            if (!supportedMaterial(state)) throw new IllegalArgumentException("Unsupported construction material");
            BlockPos pos = origin.relative(facing, z).relative(facing.getClockWise(), width - 1 - x).above(y);
            if (cells.putIfAbsent(pos, state) != null) throw new IllegalArgumentException("Duplicate blueprint cell");
        }
        return new AcceptedConstructionPlan(origin, facing, width, depth, height, structure, cells);
    }

    static boolean supportedMaterial(BlockState state) {
        return state.is(Blocks.COBBLESTONE) || state.is(Blocks.STONE_BRICKS)
                || state.is(Blocks.OAK_PLANKS) || state.is(Blocks.DIRT);
    }

    boolean matches(Object area) throws ReflectiveOperationException {
        return origin.equals(call(area, "getOriginPos")) && facing == call(area, "getFacing")
                && width == (Integer) call(area, "getWidthSize") && depth == (Integer) call(area, "getDepthSize")
                && height == (Integer) call(area, "getHeightSize") && structure.equals(call(area, "getStructureNBT"));
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Origin", origin.asLong()); tag.putString("Facing", facing.getName());
        tag.putInt("Width", width); tag.putInt("Depth", depth); tag.putInt("Height", height);
        tag.put("Structure", structure.copy());
        return tag;
    }

    static AcceptedConstructionPlan load(CompoundTag tag) {
        return decode(BlockPos.of(tag.getLong("Origin")), Direction.byName(tag.getString("Facing")),
                tag.getInt("Width"), tag.getInt("Depth"), tag.getInt("Height"), tag.getCompound("Structure"));
    }

    static Object call(Object value, String method) throws ReflectiveOperationException {
        var methods = METHODS.get(value.getClass());
        var resolved = methods.get(method);
        if (resolved == null) {
            resolved = value.getClass().getMethod(method);
            methods.put(method, resolved);
        }
        return resolved.invoke(value);
    }
}
