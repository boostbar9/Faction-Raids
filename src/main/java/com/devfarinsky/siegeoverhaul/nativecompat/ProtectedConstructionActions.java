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
