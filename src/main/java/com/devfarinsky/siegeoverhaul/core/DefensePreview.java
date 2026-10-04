package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;

/** Owner-bound, expiring placement intent. Vanilla inventory sync carries it to the client. */
public final class DefensePreview {
    public static final String TAG = "SiegeDefensePreview";
    public static final int LIFETIME = 2400, CONFIRM_DELAY = 10, RANGE = 16;
    public record Selection(BlockPos origin, Direction facing, long created, String problem) {
        public boolean ready() { return problem.isEmpty(); }
        public boolean canConfirm(BlockPos clickedOrigin, long now) {
            return origin.equals(clickedOrigin) && now - created >= CONFIRM_DELAY;
        }
    }
    private DefensePreview() {}

    public static void set(ItemStack stack, DefenseBlueprint.Kind kind, BlockPos origin, Direction facing, ResourceLocation dimension,
                           UUID owner, long now, String problem) {
        if (facing.getAxis().isVertical()) throw new IllegalArgumentException("Horizontal preview required");
        CompoundTag tag = new CompoundTag();
        tag.putLong("Origin", origin.asLong()); tag.putInt("Facing", facing.get2DDataValue());
        tag.putString("Dimension", dimension.toString()); tag.putUUID("Owner", owner);
        tag.putLong("Created", now); tag.putString("Problem", bounded(problem));
        tag.putString("Geometry", geometry(kind));
        stack.getOrCreateTag().put(TAG, tag);
    }
    public static Selection read(ItemStack stack, DefenseBlueprint.Kind kind, ResourceLocation dimension, UUID owner, long now) {
        if (!stack.hasTag() || !stack.getTag().contains(TAG)) return null;
        CompoundTag tag = stack.getTag().getCompound(TAG);
        // Pre-version previews are safe only for variants whose geometry did not change.
        // Never replace their old reviewed shape in place and leave its confirmation delay satisfied.
        if (tag.contains("Geometry") ? !tag.contains("Geometry", Tag.TAG_STRING)
                || !geometry(kind).equals(tag.getString("Geometry")) : kind.previewGeometryVersion() != 1) return null;
        long age = now - tag.getLong("Created"); int facing = tag.getInt("Facing");
        if (!tag.contains("Origin") || !tag.hasUUID("Owner") || !tag.getUUID("Owner").equals(owner)
                || !dimension.toString().equals(tag.getString("Dimension")) || age < 0 || age > LIFETIME
                || facing < 0 || facing > 3) return null;
        return new Selection(BlockPos.of(tag.getLong("Origin")), Direction.from2DDataValue(facing),
                tag.getLong("Created"), bounded(tag.getString("Problem")));
    }
    public static void updateProblem(ItemStack stack, String problem) {
        if (stack.hasTag() && stack.getTag().contains(TAG))
            stack.getTag().getCompound(TAG).putString("Problem", bounded(problem));
    }
    public static void clear(ItemStack stack) { stack.removeTagKey(TAG); }
    private static String geometry(DefenseBlueprint.Kind kind) {
        return kind.name() + ":" + kind.previewGeometryVersion();
    }
    private static String bounded(String text) {
        if (text == null) return "";
        return text.substring(0, Math.min(text.length(), 256));
    }
}
