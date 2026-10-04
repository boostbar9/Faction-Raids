package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import java.util.UUID;

/** Authenticated replacements for native controls whose packets do not carry trusted permissions. */
public final class ProtectedConstructionActions {
    public static final int HIDE = 0, SHOW = 1, CANCEL = 2;
    private ProtectedConstructionActions() {}

    /** Called only from the server-side Siege packet handler with context.getSender(). */
    public static void handle(ServerPlayer sender, UUID areaId, int action) {
        if (sender == null || areaId == null || action < HIDE || action > CANCEL) return;
        if (!(sender.serverLevel().getEntity(areaId) instanceof ProtectedBuildArea area)
                || !area.isAlive() || !authorized(sender.getUUID(), area.getPlayerUUID(),
                        sender.distanceToSqr(area) <= 16 * 16) || !sender.isAlive() || sender.isSpectator()) {
            fail(sender, "That protected construction area is unavailable or is not yours.");
            return;
        }
        if (action != CANCEL) {
            area.projectionAuthorized(action == SHOW);
            return;
        }
        // A native section is only one lease of the whole commission. Never retire it independently.
        try {
            if (PerimeterProjectAuthority.tracked(area)) {
                var scope = PerimeterProjectAuthority.read(area.getPersistentData());
                var project = PerimeterProjectAuthority.project(sender.serverLevel(), scope);
                if (scope.stage() >= project.stages().size()
                        || !project.stages().get(scope.stage()).areaId().equals(area.getUUID())
                        || !project.stages().get(scope.stage()).digest().equals(scope.stageDigest())
                        || !project.header().owner().equals(sender.getUUID())) {
                    fail(sender, "The whole-perimeter cancellation identity could not be verified.");
                    return;
                }
                if (!NativePerimeterProjects.cancelFromMarker(sender, area))
                    fail(sender, "That whole perimeter could not be canceled from this marker.");
                return;
            }
        } catch (RuntimeException | LinkageError unavailable) {
            fail(sender, "Perimeter cancellation history is unavailable; no section was released.");
            return;
        }
        // Resolve durable state first. Failure must not detach a worker and then
        // discover that the reservation cannot be retired.
        ConstructionEditLedger ledger;
        try { ledger = ConstructionEditLedger.get(sender.serverLevel()); }
        catch (RuntimeException unavailable) {
            fail(sender, "The construction reservation could not be read; no job state changed.");
            return;
        }
        if (!ledger.canRetire(area.getUUID())) {
            fail(sender, "Cancellation history is unavailable; no job state changed.");
            return;
        }
        Mob builder = loadedReservedBuilder(sender, area.reservedBuilderId());
        if (builder == null && ledger.handLifecycle(area.reservedBuilderId()) == null) {
            fail(sender, "Older protected job cleanup needs original builder hand provenance. Load that builder if available; otherwise recovery review is required.");
            return;
        }
        if (builder != null && !NativeConstructionGuard.retireBuilderAssociation(builder, area.getUUID())) {
            fail(sender, "The native builder's exact job reference could not be verified; the job was kept.");
            return;
        }
        // Unavailable/dead workers are untouched. Their single saved protected
        // UUID receipt is retired on load against this durable active index.
        // A loaded transferred worker loses only an exact old reference, never
        // ownership, new assignments, navigation orders or inventory.
        area.removeAuthorized();
        ledger.retire(area.getUUID(), builder != null);
        sender.sendSystemMessage(Component.literal(
                "Construction canceled. Placed blocks stay in the world; supplied materials and the commission are not refunded."));
    }

    /** Read-only cached authority for summaries; callers still filter every record by owner. */
    public static com.devfarinsky.siegeoverhaul.core.PerimeterProjectStore.Snapshot projectsForReport(ServerPlayer sender) {
        return PerimeterProjectAuthority.snapshot(sender.serverLevel(),
                com.devfarinsky.siegeoverhaul.core.SiegeCore.key(sender));
    }

    /** Missing/malformed stage evidence must never fall back to an unrelated legacy job. */
    public static boolean projectScoped(net.minecraft.world.entity.Entity area) {
        try { return PerimeterProjectAuthority.tracked(area); }
        catch (RuntimeException | LinkageError unavailable) { return true; }
    }

    /** Native queue counts are displayable only after exact current project/lease/snapshot validation. */
    public static boolean validProjectProgress(net.minecraft.world.entity.Entity area, Mob builder,
            com.devfarinsky.siegeoverhaul.core.PerimeterProject project) {
        if (area == null || builder == null || project == null || project.active() == null
                || !(area instanceof ProtectedBuildArea) || !area.isAlive() || !builder.isAlive()
                || !project.active().areaId().equals(area.getUUID())
                || !(area.level() instanceof net.minecraft.server.level.ServerLevel level)) return false;
        try {
            var scope = PerimeterProjectAuthority.read(area.getPersistentData());
            return scope.projectId().equals(project.header().projectId())
                    && scope.generation() == project.header().generation()
                    && PerimeterProjectAuthority.project(level, scope).check().equals(project.check())
                    && PerimeterProjectAuthority.problem(level, builder, area, false, true) == null;
        } catch (RuntimeException | LinkageError unavailable) { return false; }
    }

    private static Mob loadedReservedBuilder(ServerPlayer sender, UUID builderId) {
        if (builderId == null) return null;
        int inspected = 0;
        for (var level : sender.getServer().getAllLevels()) {
            if (++inspected > 32) break;
            var entity = level.getEntity(builderId);
            if (entity instanceof Mob mob && WorkersBridge.isBuilder(mob)) return mob;
        }
        return null;
    }

    static boolean authorized(UUID sender, UUID owner, boolean nearby) {
        return nearby && sender != null && sender.equals(owner);
    }

    private static void fail(ServerPlayer sender, String message) {
        sender.sendSystemMessage(Component.literal(message));
    }
}
