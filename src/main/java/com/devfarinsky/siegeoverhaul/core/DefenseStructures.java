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
        var search = TerritoryFortification.findNearbyBuilder(level, player, origin, true);
        Mob builder = search.builder();
        if (builder == null) return Preparation.failed(search.reason().replace("core", "placement"));
        Entity storage = TerritoryFortification.findPlayerStorageArea(level, player, builder.blockPosition(), nativeClaim.chunks());
        if (storage == null || !WorkersBridge.hasBuilderStorage(storage))
            return Preparation.failed("Set up your own Workers storage area inside this claim, within 64 blocks of the builder, with Builders enabled.");
        if (TerritoryFortification.withinStorageRange(plan.footprint(), storage.blockPosition()).size() != plan.footprint().size())
            return Preparation.failed("Move your storage area within 64 blocks of the whole structure.");
        if (!player.isCreative() && PaymentSource.available(player, kind.price) < kind.price)
            return Preparation.failed("You need " + kind.price + " emeralds in the faction Treasury.");
        return new Preparation(builder, plan, null);
    }

    /** Checks empty access space too, so the passage and stepped approach cannot start obstructed. */
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
                var state = level.getBlockState(new BlockPos(base.getX(), y, base.getZ()));
                if (HirePlacement.dangerous(state) || !TerritoryFortification.safeWallReplacement(state))
                    return "Clear the whole structure's footprint and headroom first; existing blocks and fluids are protected.";
            }
        }
        for (long cell : plan.blocks().keySet()) {
            if (!level.getEntities((Entity) null, new AABB(BlockPos.of(cell)), Entity::isAlive).isEmpty())
                return "Move players, creatures and vehicles out of the planned blocks, then try again.";
        }
        AABB bounds = new AABB(plan.min(), plan.max().offset(1, 1, 1));
        // Another builder can reserve the same still-empty site before its first block is placed.
        for (Entity area : level.getEntitiesOfClass(Entity.class, bounds.inflate(16), Entity::isAlive)) {
            var tag = area.getPersistentData();
            if (WorkersBridge.isBuildArea(area) && tag.contains(SITE_MIN) && tag.contains(SITE_MAX)
                    && bounds.intersects(new AABB(BlockPos.of(tag.getLong(SITE_MIN)),
                    BlockPos.of(tag.getLong(SITE_MAX)).offset(1, 1, 1))))
                return "Another defense job already reserves this site. Finish or remove its build marker first.";
        }
        return null;
    }

    static boolean startJob(ServerPlayer player, Mob builder, DefenseBlueprint.Plan plan, DefenseBlueprint.Kind kind) {
        Entity build = null;
        boolean committed = false;
        try {
            BlockPos min = plan.min(), max = plan.max();
            build = WorkersBridge.createPlayerArea(player.serverLevel(), "buildarea",
                    new BlockPos(max.getX(), min.getY(), min.getZ()), player.getUUID(),
                    player.getGameProfile().getName(), max.getX() - min.getX() + 1,
                    max.getZ() - min.getZ() + 1, max.getY() - min.getY() + 1);
            build.getPersistentData().putLong(SITE_MIN, min.asLong());
            build.getPersistentData().putLong(SITE_MAX, max.asLong());
            ConstructionReport.remember(build, kind.label, plan.blocks().size());
            PlayerFortificationJobs.link(builder, build, player.getUUID());
            if (!player.serverLevel().addFreshEntity(build)) throw new IllegalStateException("Build area rejected");
            WorkersBridge.startBlueprint(build, TerritoryFortification.blueprint(plan.blocks(), min, max));
            if (!com.devfarinsky.siegeoverhaul.nativecompat.NativeConstructionGuard.protect(player, builder, build))
                throw new IllegalStateException("The native job could not be safely protected");
            WorkersBridge.enableWallProjection(build, plan.blocks().size());
            WorkersBridge.enablePlayerJob(builder, player.getUUID());
            WallBuilderAccess.install(builder);
            if (!WorkersBridge.assignBuildAreaDirectly(builder, build)) throw new IllegalStateException("Builder refused the plan");
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
            if (build != null && WorkersBridge.releasePlayerJob(builder, build)) {
                PlayerFortificationJobs.unlink(builder, build.getUUID());
                build.discard();
            }
            FactionLogger.LOG.warn("[SiegeOverhaul] Defense commission failed", ex);
            return fail(player, "The builder could not start this defense. No payment was taken; your plan is kept.");
        }
    }

    private static boolean fail(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message));
        return false;
    }
}
