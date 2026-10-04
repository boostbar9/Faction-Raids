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
import java.util.UUID;
import com.devfarinsky.siegeoverhaul.nativecompat.ProtectedConstructionActions;

/** Bounded owner-filtered whole-project summaries and nearby legacy/native jobs. */
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
        player.sendSystemMessage(Component.literal("Construction report — your core’s perimeters and loaded jobs within 128 blocks").withStyle(ChatFormatting.GOLD));
        if (jobs.isEmpty()) player.sendSystemMessage(Component.literal("No loaded commissioned jobs found nearby."));
        for (Job job : jobs) {
            player.sendSystemMessage(Component.literal(job.label() + " — " + job.progressText()).withStyle(ChatFormatting.AQUA));
            player.sendSystemMessage(Component.literal(job.location()));
            player.sendSystemMessage(Component.literal(job.activity()));
            if (!job.sectionText().isBlank()) player.sendSystemMessage(Component.literal(job.sectionText()));
            if (!job.supplies().isEmpty()) player.sendSystemMessage(Component.literal("Requested now: " + job.supplies()));
        }
        player.sendSystemMessage(Component.literal("Native Workers controls work and supplies. Right-click the marker for its blueprint and the builder for orders."));
        return 1;
    }

    public record Job(String label, int percent, String progressText, String location, String activity, String supplies,
                      UUID projectId, long generation, String sectionText, boolean cancelable, boolean complete) {
        public Job(String label, int percent, String progressText, String location, String activity, String supplies) {
            this(label, percent, progressText, location, activity, supplies, null, 0, "", false, false);
        }
        public Job {
            label = bounded(label); progressText = bounded(progressText); location = bounded(location);
            activity = bounded(activity); supplies = bounded(supplies); sectionText = bounded(sectionText);
            percent = Math.max(-1, Math.min(100, percent));
            if (projectId == null ? generation != 0 || !sectionText.isEmpty() || cancelable || complete
                    : projectId.equals(new UUID(0, 0)) || generation < 1 || complete && (cancelable || percent != 100))
                throw new IllegalArgumentException("Invalid construction project summary identity");
        }
        private static String bounded(String text) {
            if (text == null || text.length() <= 256) return text == null ? "" : text;
            int end = 256;
            if (Character.isHighSurrogate(text.charAt(end - 1)) && Character.isLowSurrogate(text.charAt(end))) end--;
            return text.substring(0, end);
        }
    }

    /** Pure projection: unknown native counts never become zero remaining or terminal completion. */
    static Job projectSummary(PerimeterProject project, WorkersConstructionView.Progress available,
                              String nativeActivity, String supplies) {
        int total = project.targets().size(), verified = project.completedTargetCount();
        boolean complete = project.state() == PerimeterProject.State.COMPLETE;
        boolean canceled = project.state() == PerimeterProject.State.CANCELED;
        boolean finalVerification = project.state() == PerimeterProject.State.VERIFYING_COMPLETE
                || project.state() == PerimeterProject.State.RECOVERY_BLOCKED
                && project.recoveryState() == PerimeterProject.State.VERIFYING_COMPLETE;
        boolean alreadyVerified = project.active() == null || project.receipts().size() > project.activeStage();
        boolean availableCount = project.state() == PerimeterProject.State.RUNNING && project.payment() != null && project.active() != null && !alreadyVerified && available != null
                && available.total() == project.active().layout().targets().size()
                && available.remaining() >= 0 && available.remaining() <= available.total();
        int placed = verified + (availableCount ? available.total() - available.remaining() : 0);
        boolean exact = complete || finalVerification || alreadyVerified || availableCount;
        String amount = complete ? total + " / " + total + " blocks verified"
                : finalVerification ? total + " / " + total + " placed; verifying"
                : exact && !canceled ? placed + " / " + total + " blocks placed"
                : "At least " + verified + " / " + total + " verified; rest unknown";
        int percent = exact && !canceled ? (int) ((long) placed * 100 / total) : -1;
        String activity = complete ? "Complete: every approved wall block verified"
                : canceled ? "Canceled. Placed blocks stay; no refund."
                : finalVerification ? "Awaiting final verification" + (project.blocker().isBlank() ? "" : ": " + project.blocker())
                : !project.blocker().isBlank() ? project.blocker()
                : project.state() == PerimeterProject.State.PREPARED_UNPAID ? "Not paid; commission acceptance is incomplete"
                : project.state() == PerimeterProject.State.WAITING_FOR_NEXT_STAGE ? "Waiting for the next section’s safety checks"
                : project.state() == PerimeterProject.State.STAGE_VERIFIED ? "Section verified; preparing the next section"
                : availableCount && nativeActivity != null && !nativeActivity.isBlank() ? nativeActivity
                : "Current section unavailable; load its marker and builder";
        String section = project.receipts().size() + " / " + project.stages().size() + " sections verified"
                + (project.active() == null ? "" : " · current " + (project.activeStage() + 1))
                + "\n" + fee(project.payment());
        return new Job("Territory perimeter", percent, amount,
                "Whole territory · " + project.header().territory().size() + " claimed chunks", activity, supplies,
                project.header().projectId(), project.header().generation(), section, !complete && !canceled, complete);
    }

    static Job terminalSummary(PerimeterTerminalReceipt terminal) {
        boolean complete = terminal.state() == PerimeterProject.State.COMPLETE;
        return new Job("Territory perimeter", complete ? 100 : -1,
                complete ? terminal.totalTargetCount() + " / " + terminal.totalTargetCount() + " blocks verified"
                        : "At least " + terminal.completedTargetCount() + " / " + terminal.totalTargetCount() + " verified before cancel",
                "Whole territory · " + terminal.claimChunkCount() + " claimed chunks",
                complete ? "Complete: every approved wall block verified" : "Canceled. Placed blocks stay; no refund.", "",
                terminal.projectId(), terminal.generation(), terminal.verifiedStages() + " / " + terminal.totalStageCount()
                        + " sections verified\n" + fee(terminal.payment()), false, complete);
    }

    private static String fee(PerimeterProject.PaymentReceipt payment) {
        return payment == null ? "64 emeralds quoted; not paid · materials separate"
                : payment.creative() ? "Creative: no emeralds charged · materials separate"
                : "64 emeralds paid once · materials separate";
    }

    /** No chunk loads, payments, orders or mutations; at most twelve owner-filtered project/nearby-job summaries. */
    public static java.util.List<Job> snapshot(ServerPlayer player) {
        var result = new java.util.ArrayList<Job>();
        var level = player.serverLevel(); var box = player.getBoundingBox().inflate(128);
        PerimeterProjectStore.Snapshot projects = null;
        var projectAreas = new HashSet<UUID>();
        if (player.getServer() != null && !SiegeCore.key(player).isBlank()) {
            try {
                projects = ProtectedConstructionActions.projectsForReport(player);
                var current = projects.projects();
                for (int i = current.size() - 1; i >= 0; i--) {
                    var project = current.get(i);
                    if (!project.header().owner().equals(player.getUUID())
                            || !project.header().coreKey().equals(SiegeCore.key(player))) continue;
                    project.stages().forEach(stage -> projectAreas.add(stage.areaId()));
                    if (result.size() >= 12) continue;
                    var area = project.active() == null ? null : level.getEntity(project.active().areaId());
                    var candidate = level.getEntity(project.header().builder());
                    Mob builder = candidate instanceof Mob mob ? mob : null;
                    WorkersConstructionView.Progress progress = null;
                    String activity = "", supplies = "";
                    if (ProtectedConstructionActions.validProjectProgress(area, builder, project)) {
                        progress = WorkersConstructionView.progress(area, project.active().layout().targets().size());
                        activity = builder.isSleeping() ? "Sleeping" : builder.getTarget() != null ? "Responding to danger"
                                : WorkersConstructionView.activity(builder, area);
                        supplies = String.join(", ", WorkersConstructionView.requests(builder));
                    }
                    result.add(projectSummary(project, progress, activity, supplies));
                }
            } catch (RuntimeException | LinkageError unknown) {
                if (result.size() < 12) result.add(new Job("Perimeter report unavailable", -1, "Saved project status could not be verified", "",
                        "Recovery review is needed; no completion is inferred", ""));
            }
        }
        var areas = new java.util.ArrayList<>(level.getEntitiesOfClass(Entity.class, box, e -> e.isAlive()
                && e.getPersistentData().getBoolean(ModConstants.Tags.PLAYER_FORTIFICATION_AREA)
                && WorkersBridge.isBuildArea(e) && player.getUUID().equals(WorkersBridge.readOwner(e))));
        areas.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
        var shown = new HashSet<java.util.UUID>();
        for (Entity area : areas) {
            if (result.size() >= 12) break;
            if (projectAreas.contains(area.getUUID()) || ProtectedConstructionActions.projectScoped(area)) continue;
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
            String protection = com.devfarinsky.siegeoverhaul.nativecompat.NativeConstructionGuard.status(area);
            if (!protection.isBlank()) activity = protection;
            result.add(new Job(label, progress.percent(), amount, location, activity,
                    String.join(", ", WorkersConstructionView.requests(builder))));
        }
        // A loaded builder can still tell us where its currently unloaded marker belongs.
        for (Mob builder : level.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && WorkersBridge.isBuilder(m)
                && player.getUUID().equals(WorkersBridge.readWorkerOwner(m)))) {
            var tag = builder.getPersistentData();
            if (result.size() >= 12 || PerimeterProjectLink.reserved(builder) || !tag.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID)
                    || !shown.add(tag.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID))) continue;
            if (projectAreas.contains(tag.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID))) continue;
            result.add(new Job("Builder job", -1, "progress unavailable",
                    "Builder " + builder.blockPosition().toShortString() + " | Marker "
                            + net.minecraft.core.BlockPos.of(tag.getLong(ModConstants.Tags.PLAYER_FORTIFICATION_POS)).toShortString(),
                    "Marker unavailable. Load the site or check whether the job finished.", ""));
        }
        if (projects != null) {
            var terminals = projects.terminals(); int added = 0;
            for (int i = terminals.size() - 1; i >= 0 && result.size() < 12 && added < 3; i--) {
                var terminal = terminals.get(i);
                if (!terminal.owner().equals(player.getUUID()) || !terminal.coreKey().equals(SiegeCore.key(player))) continue;
                result.add(terminalSummary(terminal)); added++;
            }
        }
        return java.util.List.copyOf(result);
    }
}
