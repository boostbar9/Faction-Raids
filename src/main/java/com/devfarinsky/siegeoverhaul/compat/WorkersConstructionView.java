package com.devfarinsky.siegeoverhaul.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Read-only Workers 2 diagnostics. An unavailable API is unknown, never a completed job. */
public final class WorkersConstructionView {
    public record Progress(int total, int remaining) {
        public int percent() { return total <= 0 || remaining < 0 ? -1 : Math.max(0, Math.min(100, (total - remaining) * 100 / total)); }
    }
    private WorkersConstructionView() {}

    public static Progress progress(Object area, int originalCount) {
        try {
            Object first = area.getClass().getField("stackToPlace").get(area);
            Object second = area.getClass().getField("stackToPlaceMultiBlock").get(area);
            if (!(first instanceof Collection<?> a) || !(second instanceof Collection<?> b)) return new Progress(0, -1);
            int remaining = Math.addExact(a.size(), b.size());
            int total = originalCount;
            if (total <= 0) {
                Object nbt = area.getClass().getMethod("getStructureNBT").invoke(area);
                if (nbt instanceof CompoundTag tag) total = tag.getList("blocks", Tag.TAG_COMPOUND).size();
            }
            if (total <= 0 || total > 100000 || remaining > total) return new Progress(0, -1);
            return new Progress(total, remaining);
        } catch (ReflectiveOperationException | RuntimeException ex) { return new Progress(0, -1); }
    }

    public static String activity(Object builder, Object area) {
        if (builder == null) return "Builder unavailable; check its saved location or load its chunk";
        try {
            if (builder.getClass().getField("isFleeing").getBoolean(builder)) return "Fleeing danger";
            Object assigned = builder.getClass().getField("currentBuildArea").get(builder);
            if (assigned != null && assigned != area) return "Assigned to another work area";
            int order = ((Number) builder.getClass().getMethod("getFollowState").invoke(builder)).intValue();
            if (order != 0 && order != 6) return "Following an owner command; select Work to resume";
            if (Boolean.TRUE.equals(builder.getClass().getMethod("needsToSleep").invoke(builder))) return "Night/rest period";
            if (Boolean.TRUE.equals(builder.getClass().getMethod("needsToGetItems").invoke(builder)))
                return "Collecting requested supplies; check stock and Builders access in storage";
            return assigned == area ? "Working / travelling" : "Waiting for job assignment";
        } catch (ReflectiveOperationException | RuntimeException ex) { return "Native worker status unavailable"; }
    }

    public static List<String> requests(Object builder) {
        if (builder == null) return List.of();
        try {
            Object raw = builder.getClass().getField("neededItems").get(builder);
            if (!(raw instanceof List<?> list)) return List.of();
            List<String> result = new ArrayList<>();
            // Inspect a bounded prefix, including tool/food requests from native goals.
            for (Object request : list.subList(0, Math.min(8, list.size()))) {
                int count = request.getClass().getField("count").getInt(request);
                if (count <= 0 || !request.getClass().getField("required").getBoolean(request)) continue;
                Object key = request.getClass().getMethod("getMatchKey").invoke(request);
                String label = key instanceof Item item ? new ItemStack(item).getHoverName().getString() : "native requested item";
                result.add(Math.min(count, 100000) + " x " + label);
            }
            return List.copyOf(result);
        } catch (ReflectiveOperationException | RuntimeException ex) { return List.of("Open the builder's native menu for requested supplies"); }
    }
}
