package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.core.PlayerFortificationJobs;
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
        UUID reserved = area.reservedBuilderId();
        var raw = reserved == null ? null : sender.serverLevel().getEntity(reserved);
        if (!(raw instanceof Mob builder) || !WorkersBridge.isBuilder(builder)
                || !sender.getUUID().equals(WorkersBridge.readWorkerOwner(builder))) {
            fail(sender, "Load your reserved builder nearby before changing or canceling this job; no job state changed.");
            return;
        }
        if (action != CANCEL) {
            area.projectionAuthorized(action == SHOW);
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
        if (!WorkersBridge.releasePlayerJob(builder, area)) {
            fail(sender, "The native builder could not be safely detached; the job was kept.");
            return;
        }
        PlayerFortificationJobs.unlink(builder, area.getUUID());
        area.removeAuthorized();
        ledger.remove(area.getUUID());
        sender.sendSystemMessage(Component.literal(
                "Construction canceled. Placed blocks stay in the world; supplied materials and the commission are not refunded."));
    }

    static boolean authorized(UUID sender, UUID owner, boolean nearby) {
        return nearby && sender != null && sender.equals(owner);
    }

    private static void fail(ServerPlayer sender, String message) {
        sender.sendSystemMessage(Component.literal(message));
    }
}
