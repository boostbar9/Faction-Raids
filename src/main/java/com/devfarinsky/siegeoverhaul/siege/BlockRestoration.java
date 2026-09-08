package com.devfarinsky.siegeoverhaul.siege;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * v3.1.0: shared block snapshot/restoration utility.
 *
 * <p>Before v3.1.0 the mod had three parallel block-mutation paths that
 * each carried their own private serialize/deserialize helpers or ignored
 * persistence entirely:</p>
 * <ul>
 *   <li>Physical breaching in {@code RaidEvents.processPhysicalBreaching}
 *       captured BlockState properties only, throwing away tile-entity NBT
 *       (chest contents, sign text, banner patterns, lectern books).</li>
 *   <li>Sapper detonations in {@code SapperRunner.detonate} called
 *       {@code level.destroyBlock} directly, so anything a sapper blew open
 *       was never queued for post-raid restoration.</li>
 *   <li>Camp placement in {@code RaidEvents.placeCampBlock} recorded only
 *       the placed-block id, so the terrain the mod overwrote was never
 *       restored on cleanup.</li>
 * </ul>
 *
 * <p>This utility centralizes:</p>
 * <ul>
 *   <li>{@link #serialize(ServerLevel, BlockPos)} \u2014 captures both BlockState
 *       properties AND tile-entity NBT into a single tag.</li>
 *   <li>{@link #applyTo(ServerLevel, BlockPos, CompoundTag)} \u2014 restores a
 *       captured block, including tile-entity data, without overwriting
 *       player-placed blocks that occupy the space now.</li>
 *   <li>{@link #isBreachable(BlockState)} \u2014 the single whitelist consulted
 *       by breachers, sappers, and future block-damage code so the "what
 *       can this mod break?" question has exactly one answer.</li>
 * </ul>
 *
 * <p>The tag shape is: <code>{Name: "minecraft:oak_door", Properties: {...},
 * BlockEntity: {...optional...}}</code>. An empty tag or an air-name tag
 * both round-trip to air.</p>
 */
public final class BlockRestoration {

    private static final String NAME_KEY = "Name";
    private static final String PROPERTIES_KEY = "Properties";
    private static final String BLOCK_ENTITY_KEY = "BlockEntity";

    private BlockRestoration() {}

    /**
     * Snapshot a breachable block and its matching door half as one operation.
     * No world mutations occur here: callers must record their entire removal
     * batch before neighbor updates can erase another block. Refuse a whole
     * door if the ledger cannot hold both halves, even at a scan boundary.
     */
    public static List<BlockPos> snapshotBreach(ServerLevel level, Map<Long, CompoundTag> ledger,
                                               BlockPos target, int maxBlocks) {
        BlockState initial = level.getBlockState(target);
        if (!isBreachable(initial)) return List.of();
        List<BlockPos> affected = new ArrayList<>();
        affected.add(target.immutable());
        if (initial.getBlock() instanceof DoorBlock) {
            DoubleBlockHalf half = initial.getValue(DoorBlock.HALF);
            BlockPos otherPos = half == DoubleBlockHalf.LOWER ? target.above() : target.below();
            BlockState other = level.getBlockState(otherPos);
            if (other.is(initial.getBlock()) && other.getValue(DoorBlock.HALF) != half) {
                affected.add(otherPos.immutable());
            }
        }
        long newEntries = affected.stream().filter(pos -> !ledger.containsKey(pos.asLong())).count();
        if (newEntries > Math.max(0, maxBlocks - ledger.size())) return List.of();
        for (BlockPos pos : affected) {
            if (!ledger.containsKey(pos.asLong())) ledger.put(pos.asLong(), serialize(level, pos));
        }
        return affected;
    }

    /**
     * Serialize the block at {@code pos} into a self-describing tag suitable
     * for storage in RaidState. Returns an empty tag when the block cannot be
     * identified (defensive; loader migrations sometimes drop registrations).
     * Captures full tile-entity NBT when present so chest contents, sign
     * text, banner patterns, and similar are preserved across breach/repair.
     */
    public static CompoundTag serialize(ServerLevel level, BlockPos pos) {
        return serializeState(level, pos, level.getBlockState(pos));
    }

    /**
     * Variant that lets the caller supply the state (e.g., a snapshot captured
     * before an in-flight replacement). The tile-entity NBT is still pulled from
     * the current world state; callers that need pre-replacement NBT must call
     * this before performing the world mutation.
     */
    public static CompoundTag serializeState(ServerLevel level, BlockPos pos, BlockState state) {
        CompoundTag tag = new CompoundTag();
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null) return tag;
        tag.putString(NAME_KEY, id.toString());
        CompoundTag properties = new CompoundTag();
        for (Property<?> property : state.getProperties()) {
            properties.putString(property.getName(), propertyValueName(state, property));
        }
        tag.put(PROPERTIES_KEY, properties);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            // saveWithFullMetadata includes the block-entity id + xyz so we can
            // rehydrate it without hand-rolling the type dispatch. On restoration
            // we clear the id + xyz keys so vanilla setBlockEntity accepts them
            // regardless of the target position.
            CompoundTag beTag = blockEntity.saveWithFullMetadata();
            tag.put(BLOCK_ENTITY_KEY, beTag);
        }
        return tag;
    }

    /**
     * Deserialize a stored state into a BlockState (without tile-entity NBT
     * \u2014 use {@link #applyTo} to also restore the tile entity). Returns air
     * when the tag is empty or references an unknown block.
     */
    public static BlockState deserializeState(CompoundTag tag) {
        if (tag == null || tag.isEmpty() || !tag.contains(NAME_KEY)) return Blocks.AIR.defaultBlockState();
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(NAME_KEY));
        Block block = id == null ? null : ForgeRegistries.BLOCKS.getValue(id);
        if (block == null || block == Blocks.AIR) return Blocks.AIR.defaultBlockState();
        BlockState state = block.defaultBlockState();
        CompoundTag properties = tag.getCompound(PROPERTIES_KEY);
        for (Property<?> property : state.getProperties()) {
            if (properties.contains(property.getName())) {
                state = applyProperty(state, property, properties.getString(property.getName()));
            }
        }
        return state;
    }

    /**
     * Restore a captured block at {@code pos}. Returns true when a real block
     * (non-air) was placed and any tile-entity NBT successfully attached.
     * Does NOT overwrite a non-air block already at the position \u2014 callers
     * are expected to check for player replacements before invoking this.
     */
    public static boolean applyTo(ServerLevel level, BlockPos pos, CompoundTag tag) {
        BlockState state = deserializeState(tag);
        if (state.isAir()) return false;
        // flags=3: BLOCK_UPDATE | NOTIFY_NEIGHBORS. Same as vanilla setBlock().
        if (!level.setBlock(pos, state, 3)) return false;
        if (tag != null && tag.contains(BLOCK_ENTITY_KEY, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity != null) {
                CompoundTag beTag = tag.getCompound(BLOCK_ENTITY_KEY).copy();
                // Strip location + type-id keys so the target position wins;
                // load() then re-hydrates fields (Items list, sign text, etc.).
                beTag.remove("x");
                beTag.remove("y");
                beTag.remove("z");
                beTag.remove("id");
                blockEntity.load(beTag);
                blockEntity.setChanged();
            }
        }
        return true;
    }

    /**
     * The mod's canonical "can this be breached?" whitelist. Consulted by
     * breachers, sappers, and any future block-damage code so the answer is
     * unambiguous. Includes:
     * <ul>
     *   <li>Doors, trapdoors, fence gates, fences, iron bars \u2014 the original
     *       v2.x breach set.</li>
     *   <li>Walls (cobblestone, stone brick, etc.) \u2014 v3.1.0 addition so a
     *       cobblestone wall in front of a gate is not a permanent shield.</li>
     * </ul>
     * Open doors, trapdoors, and fence gates return false: no point burning
     * breach time on something already passable. Wood/plank walls are NOT
     * included here \u2014 that would let breachers eat player builds.
     */
    public static boolean isBreachable(BlockState state) {
        if (state.isAir()) return false;
        // Open state is on both DoorBlock and TrapDoorBlock via BlockStateProperties.OPEN.
        if (state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN)) return false;
        Block block = state.getBlock();
        return block instanceof DoorBlock
                || block instanceof FenceGateBlock
                || block instanceof TrapDoorBlock
                || block instanceof FenceBlock
                || block instanceof IronBarsBlock
                || block instanceof WallBlock;
    }

    /**
     * Reinforced defenses cost extra breach time. Iron doors/trapdoors/bars
     * were already reinforced in v2.x; walls join the reinforced tier in
     * v3.1.0 (a cobblestone wall is materially harder than a door).
     */
    public static boolean isReinforced(BlockState state) {
        Block block = state.getBlock();
        if (block == Blocks.IRON_DOOR || block == Blocks.IRON_TRAPDOOR || block == Blocks.IRON_BARS) return true;
        if (block instanceof WallBlock) {
            // A wooden-material wall would be odd, but future modded walls
            // could ship one. Treat non-wooden-map-color walls as reinforced.
            MapColor color = state.getMapColor(null, null);
            return color != MapColor.WOOD;
        }
        return false;
    }

    private static <T extends Comparable<T>> String propertyValueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static <T extends Comparable<T>> BlockState applyProperty(BlockState state,
                                                                       Property<T> property,
                                                                       String value) {
        return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(state);
    }
}
