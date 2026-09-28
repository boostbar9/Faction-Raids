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
        player.sendSystemMessage(Component.literal(kind.label + " plan collected. Use it on level ground in your claim. "
                + kind.price + " Treasury emeralds are charged only when your builder accepts the job."));
        return true;
    }

    public static boolean commission(ServerPlayer player, BlockPos origin, Direction facing, DefenseBlueprint.Kind kind) {
        ServerLevel level = player.serverLevel();
        String key = SiegeCore.key(player);
        if (!player.isAlive() || player.isSpectator() || !player.mayBuild()
                || !level.dimension().equals(Level.OVERWORLD) || SiegeCore.point(player.server, key) == null)
            return fail(player, "You need an active Siege Core in your faction's Overworld claim.");
        if (!WorkersBridge.available() || !RecruitsClaimsBridge.available())
            return fail(player, "Defense construction requires Villager Recruits and Workers 2.");
        var anchor = RaidSavedData.get(player.server).anchors.get(key);
        var claim = anchor == null ? java.util.Optional.<RecruitsClaimsBridge.ClaimSnapshot>empty()
                : RecruitsClaimsBridge.resolveDefendingClaim(level, anchor);
        if (claim.isEmpty()) return fail(player, "Your core needs a valid faction claim.");
        var nativeClaim = claim.get();
        var identity = anchor.withIdentity(nativeClaim.ownerFactionStringId(), anchor.teamDisplay());
        var plan = DefenseBlueprint.create(kind, origin, facing);
        String problem = siteProblem(level, plan, p -> nativeClaim.chunks().contains(new net.minecraft.world.level.ChunkPos(p))
                && !ClaimBridge.isForeignClaim(level, p, identity) && level.mayInteract(player, p));
        if (problem != null) return fail(player, problem);

        var search = TerritoryFortification.findNearbyBuilder(level, player, origin, true);
        Mob builder = search.builder();
        if (builder == null) return fail(player, search.reason().replace("core", "placement"));
        Entity storage = TerritoryFortification.findPlayerStorageArea(level, player, builder.blockPosition(), nativeClaim.chunks());
        if (storage == null || !WorkersBridge.hasBuilderStorage(storage))
            return fail(player, "Set up your own Workers storage area inside this claim, within 64 blocks of the builder, with Builders enabled.");
        if (TerritoryFortification.withinStorageRange(plan.footprint(), storage.blockPosition()).size() != plan.footprint().size())
            return fail(player, "Move your storage area within 64 blocks of the whole structure.");
        if (!player.isCreative() && PaymentSource.available(player, kind.price) < kind.price)
            return fail(player, "You need " + kind.price + " emeralds in the faction Treasury.");
        return startJob(player, builder, plan, kind);
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
            PlayerFortificationJobs.link(builder, build, player.getUUID());
            if (!player.serverLevel().addFreshEntity(build)) throw new IllegalStateException("Build area rejected");
            WorkersBridge.startBlueprint(build, TerritoryFortification.blueprint(plan.blocks(), min, max));
            WorkersBridge.enableWallProjection(build, plan.blocks().size());
            WorkersBridge.enablePlayerJob(builder, player.getUUID());
            WallBuilderAccess.install(builder);
            if (!WorkersBridge.assignBuildAreaDirectly(builder, build)) throw new IllegalStateException("Builder refused the plan");
            if (!PaymentSource.consume(player, kind.price)) throw new IllegalStateException("Treasury payment rejected");
            committed = true;
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
