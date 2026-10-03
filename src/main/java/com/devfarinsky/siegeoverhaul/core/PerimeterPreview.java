package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Bounded, owner-bound read-only perimeter selection, carried by normal inventory synchronization. */
public final class PerimeterPreview {
    public static final String TAG = "SiegePerimeterPreview";
    public static final int LIFETIME = 2400, CONFIRM_DELAY = 10, MAX_CELLS = 32768, MAX_BOXES = 8192;
    public record Box(BlockPos min, BlockPos max, int material) {}
    public record Selection(BlockPos core, int material, long created, String fingerprint,
                            String problem, String materials, List<Box> boxes) {
        public Selection { boxes = List.copyOf(boxes); }
        public boolean ready() { return problem.isEmpty() && !fingerprint.isEmpty() && !boxes.isEmpty(); }
        public boolean canConfirm(long now) { return now >= created && now - created >= CONFIRM_DELAY; }
    }
    private record Column(int x, int z) {}
    private record Strip(int z, int low, int high, String material) {}
    private record Run(int x0, int x1, int low, int high, String material) {}
    private PerimeterPreview() {}

    public static void set(ItemStack stack, UUID owner, ResourceLocation dimension, BlockPos core,
                           int material, long now, String fingerprint, String problem,
                           String materials, Map<Long, String> cells) {
        if (material < 0 || material >= TerritoryFortification.MATERIALS.length || cells.size() > MAX_CELLS)
            throw new IllegalArgumentException("Invalid perimeter selection");
        List<Box> boxes = boxes(cells);
        if (boxes.size() > MAX_BOXES) throw new IllegalArgumentException("Perimeter preview is too fragmented");
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Owner", owner); tag.putString("Dimension", dimension.toString());
        tag.putLong("Core", core.asLong()); tag.putInt("Material", material); tag.putLong("Created", now);
        tag.putString("Fingerprint", bounded(fingerprint, 64)); tag.putString("Problem", bounded(problem, 256));
        tag.putString("Materials", bounded(materials, 256));
        long[] encoded = new long[boxes.size() * 3];
        for (int i = 0; i < boxes.size(); i++) {
            Box box = boxes.get(i); encoded[i * 3] = box.min().asLong();
            encoded[i * 3 + 1] = box.max().asLong(); encoded[i * 3 + 2] = box.material();
        }
        tag.putLongArray("Boxes", encoded); stack.getOrCreateTag().put(TAG, tag);
    }

    public static Selection read(ItemStack stack, UUID owner, ResourceLocation dimension, long now) {
        if (!stack.hasTag() || !stack.getTag().contains(TAG, Tag.TAG_COMPOUND)) return null;
        CompoundTag tag = stack.getTag().getCompound(TAG);
        if (!tag.hasUUID("Owner") || !owner.equals(tag.getUUID("Owner"))
                || !dimension.toString().equals(tag.getString("Dimension"))
                || !tag.contains("Created", Tag.TAG_LONG) || !tag.contains("Core", Tag.TAG_LONG)
                || !tag.contains("Material", Tag.TAG_INT) || !tag.contains("Boxes", Tag.TAG_LONG_ARRAY)) return null;
        long created = tag.getLong("Created");
        if (created < 0 || now < created || now - created > LIFETIME) return null;
        int material = tag.getInt("Material");
        if (material < 0 || material >= TerritoryFortification.MATERIALS.length) return null;
        long[] encoded = tag.getLongArray("Boxes");
        if (encoded.length % 3 != 0 || encoded.length > MAX_BOXES * 3) return null;
        List<Box> boxes = new ArrayList<>(); long volume = 0;
        for (int i = 0; i < encoded.length; i += 3) {
            BlockPos min = BlockPos.of(encoded[i]), max = BlockPos.of(encoded[i + 1]);
            long dx = (long) max.getX() - min.getX() + 1, dy = (long) max.getY() - min.getY() + 1;
            long dz = (long) max.getZ() - min.getZ() + 1;
            if (dx <= 0 || dy <= 0 || dz <= 0 || dx > MAX_CELLS || dy > MAX_CELLS || dz > MAX_CELLS
                    || encoded[i + 2] < 0 || encoded[i + 2] > 2) return null;
            volume += dx * dy * dz;
            if (volume > MAX_CELLS) return null;
            boxes.add(new Box(min, max, (int) encoded[i + 2]));
        }
        String hash = bounded(tag.getString("Fingerprint"), 64);
        if (!hash.isEmpty() && !hash.matches("[0-9a-f]{64}")) return null;
        return new Selection(BlockPos.of(tag.getLong("Core")), material, created, hash,
                bounded(tag.getString("Problem"), 256), bounded(tag.getString("Materials"), 256), boxes);
    }

    public static void clear(ItemStack stack) { stack.removeTagKey(TAG); }

    /** Canonical coordinate/material identity; never trusts client-supplied estimates on confirmation. */
    public static String fingerprint(Map<Long, String> cells, BlockPos core, int material, String claimIdentity) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((core.asLong() + ":" + material + ":" + claimIdentity + "\n").getBytes(StandardCharsets.UTF_8));
            new TreeMap<>(cells).forEach((position, block) -> digest.update(
                    (position + ":" + block + "\n").getBytes(StandardCharsets.UTF_8)));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException(unavailable); }
    }

    /** Exact non-overlapping prisms, merged vertically then X/Z; no missing cells or invented bounding fill. */
    static List<Box> boxes(Map<Long, String> cells) {
        if (cells.size() > MAX_CELLS) throw new IllegalArgumentException("Too many perimeter cells");
        Map<Column, TreeMap<Integer, String>> columns = new HashMap<>();
        cells.forEach((packed, material) -> {
            BlockPos p = BlockPos.of(packed);
            columns.computeIfAbsent(new Column(p.getX(), p.getZ()), key -> new TreeMap<>()).put(p.getY(), material);
        });
        Map<Strip, TreeSet<Integer>> strips = new HashMap<>();
        columns.forEach((column, ys) -> {
            int low = Integer.MIN_VALUE, high = Integer.MIN_VALUE; String previous = null;
            for (var cell : ys.entrySet()) {
                if (previous != null && (cell.getKey() != high + 1 || !previous.equals(cell.getValue()))) {
                    strips.computeIfAbsent(new Strip(column.z(), low, high, previous), key -> new TreeSet<>()).add(column.x());
                    previous = null;
                }
                if (previous == null) { low = cell.getKey(); previous = cell.getValue(); }
                high = cell.getKey();
            }
            if (previous != null) strips.computeIfAbsent(new Strip(column.z(), low, high, previous), key -> new TreeSet<>()).add(column.x());
        });
        Map<Run, TreeSet<Integer>> runs = new HashMap<>();
        strips.forEach((strip, xs) -> {
            Integer low = null; int high = 0;
            for (int x : xs) {
                if (low != null && x != high + 1) {
                    runs.computeIfAbsent(new Run(low, high, strip.low(), strip.high(), strip.material()), key -> new TreeSet<>()).add(strip.z());
                    low = null;
                }
                if (low == null) low = x;
                high = x;
            }
            if (low != null) runs.computeIfAbsent(new Run(low, high, strip.low(), strip.high(), strip.material()), key -> new TreeSet<>()).add(strip.z());
        });
        List<Box> result = new ArrayList<>();
        runs.forEach((run, zs) -> {
            Integer low = null; int high = 0;
            for (int z : zs) {
                if (low != null && z != high + 1) { result.add(box(run, low, high)); low = null; }
                if (low == null) low = z;
                high = z;
            }
            if (low != null) result.add(box(run, low, high));
        });
        result.sort(Comparator.comparingLong((Box b) -> b.min().asLong()).thenComparingLong(b -> b.max().asLong()));
        return List.copyOf(result);
    }
    private static Box box(Run run, int z0, int z1) {
        int material = run.material().equals("minecraft:oak_planks") ? 1 : run.material().equals("minecraft:dirt") ? 2 : 0;
        return new Box(new BlockPos(run.x0(), run.low(), z0), new BlockPos(run.x1(), run.high(), z1), material);
    }
    private static String bounded(String value, int max) {
        if (value == null) return "";
        return value.substring(0, Math.min(value.length(), max));
    }
}
