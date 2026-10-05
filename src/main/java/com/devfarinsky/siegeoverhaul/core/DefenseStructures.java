package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.FactionLogger;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.compat.ClaimBridge;
import com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.items.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.function.Predicate;

/** Server-side plan delivery, whole-site validation and native Workers commission. */
public final class DefenseStructures {
    private static final String SITE_MIN = "SiegeDefenseSiteMin", SITE_MAX = "SiegeDefenseSiteMax";
    private enum CommissionStage {
        BLUEPRINT("blueprint preparation"), MARKER("build marker creation"),
        REGISTRATION("build marker registration"), QUEUES("native blueprint setup"),
        PROTECTION("site protection"), ASSIGNMENT("builder assignment"), PAYMENT("Treasury payment");
        final String label;
        CommissionStage(String label) { this.label = label; }
    }
    // Only these locally defined marker refusals may be copied from an exception to chat.
    // Other exception messages can contain implementation details and remain server-log only.
    private static final java.util.Set<String> MARKER_REFUSALS = java.util.Set.of(
            "Move onto clear ground inside your claim near the build site; no accessible native marker position is available.",
            "The complete plan is too far from an accessible native marker; use a smaller construction job.",
            "Native marker entity is unavailable");
    private DefenseStructures() {}

    public static boolean givePlan(ServerPlayer player, int index) {
        if (index < 0 || index >= DefenseBlueprint.Kind.values().length) return false;
        var kind = DefenseBlueprint.Kind.values()[index];
        ItemStack plan = new ItemStack(ModItems.defensePlan(kind).get());
        if (player.getInventory().contains(plan)) return fail(player, "You already have this defense plan.");
        // Plans are free, have no crafting uses, and never drop repeatedly from a full inventory.
        if (!player.getInventory().add(plan)) return fail(player, "Make room in your inventory for the plan.");
        player.sendSystemMessage(Component.literal(kind.label + " plan collected. Use it to preview, sneak-use to rotate, then use the same ground anchor to confirm. "
                + kind.price + " Treasury emeralds are charged only when your builder accepts the job."));
        return true;
    }

    public record Preparation(Mob builder, DefenseBlueprint.Plan plan, String problem) {
        static Preparation failed(String problem) { return new Preparation(null, null, problem); }
    }

    public static boolean commission(ServerPlayer player, BlockPos origin, Direction facing, DefenseBlueprint.Kind kind) {
        Preparation result = prepare(player, origin, facing, kind);
        if (result.problem() != null) return fail(player, result.problem());
        return startJob(player, result.builder(), result.plan(), kind);
    }

    /** Same read-only checks for preview and final confirmation; no payment or worker changes. */
    public static Preparation prepare(ServerPlayer player, BlockPos origin, Direction facing, DefenseBlueprint.Kind kind) {
        ServerLevel level = player.serverLevel();
        String key = SiegeCore.key(player);
        if (!player.isAlive() || player.isSpectator() || !player.mayBuild()
                || !level.dimension().equals(Level.OVERWORLD) || SiegeCore.point(player.server, key) == null)
            return Preparation.failed("You need an active Siege Core in your faction's Overworld claim.");
        if (!WorkersBridge.available() || !RecruitsClaimsBridge.available())
            return Preparation.failed("Defense construction requires Villager Recruits and Workers 2.");
        var anchor = RaidSavedData.get(player.server).anchors.get(key);
        var claim = anchor == null ? java.util.Optional.<RecruitsClaimsBridge.ClaimSnapshot>empty()
                : RecruitsClaimsBridge.resolveDefendingClaim(level, anchor);
        if (claim.isEmpty()) return Preparation.failed("Your core needs a valid faction claim.");
        var nativeClaim = claim.get();
        var identity = anchor.withIdentity(nativeClaim.ownerFactionStringId(), anchor.teamDisplay());
        var plan = DefenseBlueprint.create(kind, origin, facing);
        var permissions = new java.util.HashMap<net.minecraft.world.level.ChunkPos, Boolean>();
        String problem = siteProblem(level, plan, p -> permissions.computeIfAbsent(new net.minecraft.world.level.ChunkPos(p),
                chunk -> nativeClaim.chunks().contains(chunk) && !ClaimBridge.isForeignClaim(level, chunk, identity))
                && level.mayInteract(player, p));
        if (problem != null) return Preparation.failed(problem);

        String nativeProblem = com.devfarinsky.siegeoverhaul.nativecompat.NativeConstructionGuard.availabilityProblem();
        if (nativeProblem != null && !nativeProblem.isBlank()) return Preparation.failed(nativeProblem);
        var supplySites = plan.footprint().stream().flatMap(base -> java.util.stream.Stream.of(base, base.atY(plan.max().getY()))).toList();
        var resources = ConstructionResources.find(level, player, origin, nativeClaim.chunks(), supplySites);
        if (resources.problem() != null) return Preparation.failed(resources.problem());
        Mob builder = resources.builder();
        if (!player.isCreative() && PaymentSource.available(player, kind.price) < kind.price)
            return Preparation.failed("You need " + kind.price + " emeralds in the faction Treasury.");
        return new Preparation(builder, plan, null);
    }

    /** Checks empty access space too, so hollow wall cavities, passages and stepped approaches cannot start obstructed. */
    static String siteProblem(ServerLevel level, DefenseBlueprint.Plan plan, Predicate<BlockPos> permitted) {
        if (plan.min().getY() - 1 < level.getMinBuildHeight() || plan.max().getY() >= level.getMaxBuildHeight())
            return "The structure would exceed the world's build height.";
        for (BlockPos base : plan.footprint()) {
            if (!level.hasChunkAt(base) || !level.getWorldBorder().isWithinBounds(base))
                return "The whole structure must be in loaded terrain inside the world border.";
        }
        for (BlockPos base : plan.footprint()) {
            if (!permitted.test(base)) return "The whole structure must be inside your core's claim and allow building.";
            BlockPos ground = base.below();
            var support = level.getBlockState(ground);
            if (support.hasBlockEntity() || HirePlacement.dangerous(support) || !support.getFluidState().isEmpty()
                    || !support.isFaceSturdy(level, ground, Direction.UP))
                return "Choose dry, solid, level ground under the whole structure. Flatten the site first.";
            for (int y = base.getY(); y <= plan.max().getY(); y++) {
                BlockPos cell = base.atY(y);
                if (!permitted.test(cell)) return "The whole structure's footprint and headroom must allow building.";
                var state = level.getBlockState(cell);
                String problem = com.devfarinsky.siegeoverhaul.nativecompat.NativeConstructionGuard.initialPlacementProblem(
                        level, cell, state, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                if (problem != null) return problem;
            }
        }
        for (long cell : plan.blocks().keySet()) {
            if (!level.getEntities((Entity) null, new AABB(BlockPos.of(cell)), Entity::isAlive).isEmpty())
                return "Move players, creatures and vehicles out of the planned blocks, then try again.";
        }
        String neighborhood = com.devfarinsky.siegeoverhaul.nativecompat.NativeConstructionGuard.placementNeighborhoodProblem(
                level, plan.blocks().keySet().stream().map(BlockPos::of).toList());
        if (neighborhood != null) return neighborhood;
        String reservation = ConstructionReservations.problem(level, reservedCells(plan));
        if (reservation != null) return reservation;
        return null;
    }

    /** The same complete footprint, cavity and headroom contract is checked before and retained after handoff. */
    static java.util.Set<BlockPos> reservedCells(DefenseBlueprint.Plan plan) {
        var cells = new java.util.HashSet<BlockPos>();
        for (BlockPos base : plan.footprint()) for (int y = base.getY(); y <= plan.max().getY(); y++)
            cells.add(base.atY(y));
        return java.util.Set.copyOf(cells);
    }

    static boolean startJob(ServerPlayer player, Mob builder, DefenseBlueprint.Plan plan, DefenseBlueprint.Kind kind) {
        Entity build = null;
        boolean committed = false;
        CommissionStage stage = CommissionStage.BLUEPRINT;
        String refusal = "";
        try {
            BlockPos min = plan.min(), max = plan.max();
            var blueprint = TerritoryFortification.blueprint(plan.blocks(), min, max);
            stage = CommissionStage.MARKER;
            build = WorkersBridge.createProtectedPlayerArea(player, builder,
                    new BlockPos(max.getX(), min.getY(), min.getZ()), max.getX() - min.getX() + 1,
                    max.getZ() - min.getZ() + 1, max.getY() - min.getY() + 1, blueprint);
            stage = CommissionStage.REGISTRATION;
            build.getPersistentData().putLong(SITE_MIN, min.asLong());
            build.getPersistentData().putLong(SITE_MAX, max.asLong());
            ConstructionReport.remember(build, kind.label, plan.blocks().size());
            PlayerFortificationJobs.link(builder, build, player.getUUID());
            if (!player.serverLevel().addFreshEntity(build)) throw new IllegalStateException("Build area rejected");
            stage = CommissionStage.QUEUES;
            WorkersBridge.startBlueprint(build, blueprint);
            stage = CommissionStage.PROTECTION;
            if (!com.devfarinsky.siegeoverhaul.nativecompat.NativeConstructionGuard.protect(player, builder, build, reservedCells(plan))) {
                // Capture the guard's bounded, locally generated refusal before rollback removes its marker.
                refusal = boundedRefusal(com.devfarinsky.siegeoverhaul.nativecompat.NativeConstructionGuard.status(build));
                throw new IllegalStateException("The native job could not be safely protected");
            }
            stage = CommissionStage.ASSIGNMENT;
            WorkersBridge.enableWallProjection(build, plan.blocks().size());
            WorkersBridge.enablePlayerJob(builder, player.getUUID());
            WallBuilderAccess.install(builder);
            if (!WorkersBridge.assignBuildAreaDirectly(builder, build)) throw new IllegalStateException("Builder refused the plan");
            stage = CommissionStage.PAYMENT;
            if (!PaymentSource.consume(player, kind.price)) throw new IllegalStateException("Treasury payment rejected");
            committed = true;
            com.devfarinsky.siegeoverhaul.nativecompat.NativeConstructionGuard.activate(build);
            player.sendSystemMessage(Component.literal(kind.label + " commissioned. Supply " + plan.materials()
                    + " through your Workers storage area. The builder waits when supplies run out and follows normal work hours."));
            player.sendSystemMessage(Component.literal("Build marker: " + build.blockPosition().toShortString()
                    + ". Right-click its shovel to inspect the blueprint. Use normal recruit commands to station defenders after construction."));
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            if (committed) {
                FactionLogger.LOG.warn("[SiegeOverhaul] Defense job started, but feedback failed", ex);
                return true;
            }
            if (stage == CommissionStage.MARKER && ex.getMessage() != null && MARKER_REFUSALS.contains(ex.getMessage()))
                refusal = boundedRefusal(ex.getMessage());
            // Preserve the original failure even if cleanup itself cannot finish.
            FactionLogger.LOG.warn("[SiegeOverhaul] Defense commission failed during {} (refusal: {})",
                    stage.label, refusal.isEmpty() ? "see exception" : refusal, ex);
            if (build != null && WorkersBridge.releasePlayerJob(builder, build)) {
                PlayerFortificationJobs.unlink(builder, build.getUUID());
                WorkersBridge.discardPlayerArea(build);
            }
            String detail = refusal.isEmpty() ? "See the server log for details. "
                    : refusal + (refusal.endsWith(".") ? " " : ". ");
            return fail(player, "The builder could not start this defense (" + stage.label + "). " + detail
                    + "No payment was taken; your plan is kept.");
        }
    }

    private static String boundedRefusal(String reason) {
        if (reason == null) return "";
        String singleLine = reason.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
        return singleLine.substring(0, Math.min(160, singleLine.length()));
    }

    private static boolean fail(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message));
        return false;
    }
}
