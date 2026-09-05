package com.devfarinsky.factionraids.camp;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import java.util.List;

/**
 * Describes one raider camp layout: a set of blueprint placements + a
 * lumber-zone footprint. Purely a data record — {@link CampBuilder} consumes
 * it, spawns the corresponding Workers work-areas, and moves on.
 *
 * <p>A blueprint is either bundled (loaded from resources by
 * {@link CampBlueprintRegistry}) or authored at runtime from vanilla
 * {@code /structure save} NBT — the field types match either source.
 *
 * <p>Each {@link Placement} is a structure NBT dropped at an offset from the
 * camp center. Offsets stay in raw block coordinates so blueprints can be
 * mirrored around the approach angle at build time.
 */
public record CampBlueprint(
        String id,
        Size size,
        int lumberRadius,
        int lumberHeight,
        List<Placement> placements) {

    /** Approximate footprint of the whole camp in blocks (used for site scoring). */
    public record Size(int width, int depth) {}

    /**
     * One placed structure inside the camp.
     *
     * @param name          human-readable name for logs and tooltips
     * @param offsetX       offset from camp center along the camp's local X axis
     * @param offsetZ       offset from camp center along the camp's local Z axis
     * @param facing        local facing (rotated at build time to match approach)
     * @param structureNbt  the raw structure NBT (as produced by /structure save)
     */
    public record Placement(
            String name,
            int offsetX,
            int offsetZ,
            Direction facing,
            CompoundTag structureNbt) {

        /** World-space anchor position for this placement given a camp center. */
        public BlockPos anchorAt(BlockPos campCenter) {
            return campCenter.offset(offsetX, 0, offsetZ);
        }
    }
}
