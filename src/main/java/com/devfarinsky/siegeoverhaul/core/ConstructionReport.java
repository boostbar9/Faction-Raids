package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.ModConstants;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.compat.WorkersConstructionView;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import java.util.Comparator;
import java.util.HashSet;

/** Explicit, bounded reports for the requesting player's loaded commissioned jobs. */
public final class ConstructionReport {
    private static final String COUNT = "SiegeCommissionBlocks", LABEL = "SiegeCommissionLabel";
    private static final String LAST_REPORT = "SiegeConstructionReportAt";
    private ConstructionReport() {}
    public static void remember(Entity area, String label, int count) {
        area.getPersistentData().putString(LABEL, label);
        area.getPersistentData().putInt(COUNT, count);
    }
    public static int report(ServerPlayer player) {
        long now = player.level().getGameTime(); var playerTag = player.getPersistentData();
        if (playerTag.contains(LAST_REPORT) && now >= playerTag.getLong(LAST_REPORT) && now - playerTag.getLong(LAST_REPORT) < 20) return 0;
        playerTag.putLong(LAST_REPORT, now);
        var jobs = snapshot(player);
        player.sendSystemMessage(Component.literal("Construction report — your loaded jobs within 128 blocks").withStyle(ChatFormatting.GOLD));
        if (jobs.isEmpty()) player.sendSystemMessage(Component.literal("No loaded commissioned jobs found nearby."));
        for (Job job : jobs) {
            player.sendSystemMessage(Component.literal(job.label() + " — " + job.progressText()).withStyle(ChatFormatting.AQUA));
            player.sendSystemMessage(Component.literal(job.location()));
            player.sendSystemMessage(Component.literal(job.activity()));
            if (!job.supplies().isEmpty()) player.sendSystemMessage(Component.literal("Requested now: " + job.supplies()));
        }
        player.sendSystemMessage(Component.literal("Native Workers controls work and supplies. Right-click the marker for its blueprint and the builder for orders."));
        return 1;
    }

    public record Job(String label, int percent, String progressText, String location, String activity, String supplies) {
        public Job {
            label = bounded(label); progressText = bounded(progressText); location = bounded(location);
            activity = bounded(activity); supplies = bounded(supplies);
            percent = Math.max(-1, Math.min(100, percent));
        }
        private static String bounded(String text) { return text == null ? "" : text.substring(0, Math.min(256, text.length())); }
    }

    /** No chunk loads, payments, orders or mutations; at most twelve owned nearby jobs. */
    public static java.util.List<Job> snapshot(ServerPlayer player) {
        var result = new java.util.ArrayList<Job>();
        var level = player.serverLevel(); var box = player.getBoundingBox().inflate(128);
        var areas = new java.util.ArrayList<>(level.getEntitiesOfClass(Entity.class, box, e -> e.isAlive()
                && e.getPersistentData().getBoolean(ModConstants.Tags.PLAYER_FORTIFICATION_AREA)
                && WorkersBridge.isBuildArea(e) && player.getUUID().equals(WorkersBridge.readOwner(e))));
        areas.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
        var shown = new HashSet<java.util.UUID>(); int count = 0;
        for (Entity area : areas) {
            if (count++ >= 12) break;
            shown.add(area.getUUID()); var tag = area.getPersistentData();
            Entity candidate = tag.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_BUILDER)
                    ? level.getEntity(tag.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_BUILDER)) : null;
            Mob builder = candidate instanceof Mob mob && mob.isAlive()
                    && player.getUUID().equals(WorkersBridge.readWorkerOwner(mob)) ? mob : null;
            var progress = WorkersConstructionView.progress(area, tag.getInt(COUNT));
            String label = tag.getString(LABEL); if (label.isBlank()) label = "Builder job";
            String amount = progress.percent() < 0 ? "progress unavailable" : progress.percent() + "% placed; "
                    + progress.remaining() + " of " + progress.total() + " blocks left";
            String location = "Marker " + area.blockPosition().toShortString()
                    + (builder == null ? "" : " | " + builder.getName().getString() + " " + builder.blockPosition().toShortString());
            String activity = builder != null && builder.isSleeping() ? "Sleeping"
                    : builder != null && builder.getTarget() != null ? "Responding to danger"
                    : WorkersConstructionView.activity(builder, area);
            result.add(new Job(label, progress.percent(), amount, location, activity,
                    String.join(", ", WorkersConstructionView.requests(builder))));
        }
        // A loaded builder can still tell us where its currently unloaded marker belongs.
        for (Mob builder : level.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && WorkersBridge.isBuilder(m)
                && player.getUUID().equals(WorkersBridge.readWorkerOwner(m)))) {
            var tag = builder.getPersistentData();
            if (count >= 12 || !tag.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID)
                    || !shown.add(tag.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID))) continue;
            count++;
            result.add(new Job("Builder job", -1, "progress unavailable",
                    "Builder " + builder.blockPosition().toShortString() + " | Marker "
                            + net.minecraft.core.BlockPos.of(tag.getLong(ModConstants.Tags.PLAYER_FORTIFICATION_POS)).toShortString(),
                    "Marker unavailable. Load the site or check whether the job finished.", ""));
        }
        return java.util.List.copyOf(result);
    }
}
