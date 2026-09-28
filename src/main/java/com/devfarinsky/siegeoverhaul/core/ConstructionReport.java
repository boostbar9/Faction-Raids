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
        var level = player.serverLevel(); var box = player.getBoundingBox().inflate(128);
        var areas = new java.util.ArrayList<>(level.getEntitiesOfClass(Entity.class, box, e -> e.isAlive()
                && e.getPersistentData().getBoolean(ModConstants.Tags.PLAYER_FORTIFICATION_AREA)
                && WorkersBridge.isBuildArea(e) && player.getUUID().equals(WorkersBridge.readOwner(e))));
        areas.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
        player.sendSystemMessage(Component.literal("Construction report — your loaded jobs within 128 blocks")
                .withStyle(ChatFormatting.GOLD));
        var shown = new HashSet<java.util.UUID>(); int count = 0;
        for (Entity area : areas) {
            if (count++ >= 12) { player.sendSystemMessage(Component.literal("More jobs nearby; move closer to inspect another group.")); break; }
            shown.add(area.getUUID()); var tag = area.getPersistentData();
            Entity candidate = tag.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_BUILDER)
                    ? level.getEntity(tag.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_BUILDER)) : null;
            Mob builder = candidate instanceof Mob mob && mob.isAlive()
                    && player.getUUID().equals(WorkersBridge.readWorkerOwner(mob)) ? mob : null;
            var progress = WorkersConstructionView.progress(area, tag.getInt(COUNT));
            String label = tag.getString(LABEL); if (label.isBlank()) label = "Builder job";
            String amount = progress.percent() < 0 ? "progress unavailable" : progress.percent() + "% placed; "
                    + progress.remaining() + " of " + progress.total() + " blocks left";
            player.sendSystemMessage(Component.literal(label + " — " + amount).withStyle(ChatFormatting.AQUA));
            player.sendSystemMessage(Component.literal("Marker " + area.blockPosition().toShortString()
                    + (builder == null ? "" : " | Builder " + builder.blockPosition().toShortString())));
            player.sendSystemMessage(Component.literal(builder != null && builder.isSleeping() ? "Sleeping"
                    : builder != null && builder.getTarget() != null ? "Responding to danger"
                    : WorkersConstructionView.activity(builder, area)));
            var requests = WorkersConstructionView.requests(builder);
            if (!requests.isEmpty()) player.sendSystemMessage(Component.literal("Requested now: " + String.join(", ", requests)));
        }
        // A loaded builder can still tell us where its currently unloaded marker belongs.
        for (Mob builder : level.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && WorkersBridge.isBuilder(m)
                && player.getUUID().equals(WorkersBridge.readWorkerOwner(m)))) {
            var tag = builder.getPersistentData();
            if (count >= 12 || !tag.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID)
                    || !shown.add(tag.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID))) continue;
            count++;
            player.sendSystemMessage(Component.literal("Builder " + builder.blockPosition().toShortString()
                    + " | Marker unavailable at " + net.minecraft.core.BlockPos.of(tag.getLong(ModConstants.Tags.PLAYER_FORTIFICATION_POS)).toShortString()
                    + ". Load the site or check whether the job finished."));
        }
        if (count == 0) player.sendSystemMessage(Component.literal("No loaded commissioned jobs found nearby. Move closer to your builder or site and run /siegeoverhaul builds."));
        player.sendSystemMessage(Component.literal("Native Workers controls work and supplies. Right-click the marker for its blueprint and the builder for orders.")
                .withStyle(ChatFormatting.GRAY));
        return 1;
    }
}
