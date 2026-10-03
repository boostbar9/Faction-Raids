package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;

import java.util.Set;

/** QA-only access to the production pure planner and native NBT conversion. */
public final class NativeQaPerimeterTemplate {
    private NativeQaPerimeterTemplate() {}

    public record Template(BlockPos origin, int width, int depth, int height,
                           CompoundTag blueprint, int blockCount) {}

    public static Template create() {
        // This is an input shape, not a registered Recruits claim.
        var plan = PerimeterBlueprint.create(Set.of(new ChunkPos(2, 0)),
                (x, z) -> PerimeterBlueprint.Surface.ready(65), PerimeterBlueprint.Palette.COBBLESTONE);
        if (!plan.valid()) throw new AssertionError("Production perimeter fixture failed: " + plan.problemSummary());
        BlockPos min = plan.min(), max = plan.max();
        return new Template(new BlockPos(max.getX(), min.getY(), min.getZ()),
                max.getX() - min.getX() + 1, max.getZ() - min.getZ() + 1, max.getY() - min.getY() + 1,
                TerritoryFortification.blueprint(plan.blocks(), min, max), plan.blocks().size());
    }
}
