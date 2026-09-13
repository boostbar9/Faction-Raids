package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.FactionLogger;
import com.devfarinsky.siegeoverhaul.items.ModItems;
import com.devfarinsky.siegeoverhaul.siege.SiegeEngineType;
import com.devfarinsky.siegeoverhaul.siege.SiegeIntegration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

/**
 * Army-tab Siege Yard: buys a placement kit for a friendly Recruits Siege
 * Engineer pre-mounted on a fresh Siege Weapons vehicle (catapult or
 * ballista). Charged like a normal recruit: bank first, then the player's
 * inventory. The actual crew is deployed where the player uses the kit, so
 * an indoor or crowded Core can no longer block the purchase.
 * <p>Both mods must be present. When either is missing, the button rejects
 * with an ingame message and no emeralds are consumed.</p>
 */
public final class SiegeYard {
    /** Index 0 = catapult crew, 1 = ballista crew. */
    public static final int[] PRICES = { 480, 400 };
    public static final String[] LABELS = { "Catapult Crew", "Ballista Crew" };
    public static final SiegeEngineType[] TYPES = { SiegeEngineType.CATAPULT, SiegeEngineType.BALLISTA };

    private SiegeYard() {}

    public static int price(int index) {
        if (index < 0 || index >= PRICES.length) return -1;
        return PRICES[index];
    }

    public static String label(int index) {
        if (index < 0 || index >= LABELS.length) return "";
        return LABELS[index];
    }

    public static boolean available() {
        return SiegeIntegration.isSiegeEngineerAvailable();
    }

    public static boolean hire(ServerPlayer player, BlockPos core, int index) {
        if (player == null || core == null) return false;
        if (index < 0 || index >= PRICES.length) return false;
        if (!available()) {
            player.sendSystemMessage(Component.literal(
                    "Requires Recruits and Siege Weapons mods to hire a siege crew."));
            return false;
        }
        int price = PRICES[index];
        long combined = PaymentSource.available(player, price);
        if (!player.isCreative() && combined < price) {
            player.sendSystemMessage(Component.literal(
                    "You need " + price + " emeralds (bank + inventory) for a " + LABELS[index] + "."));
            return false;
        }

        ItemStack kit = new ItemStack(index == 0
                ? ModItems.CATAPULT_CREW_KIT.get()
                : ModItems.BALLISTA_CREW_KIT.get());
        if (!player.isCreative() && !PaymentSource.consume(player, price)) return false;

        boolean stored = player.getInventory().add(kit);
        if (!stored && !kit.isEmpty()) player.drop(kit, false);
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        SiegeIntegration.Footprint footprint = SiegeIntegration.footprintOf(TYPES[index]);
        player.sendSystemMessage(Component.literal(
                "Purchased a " + LABELS[index] + " deployment kit for " + price
                        + " emeralds. Right-click the top of a clear flat "
                        + deploymentAreaGuidance(footprint) + " to deploy it."
                        + (stored ? "" : " Your inventory was full, so the kit was dropped at your feet.")));
        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 0.7F, 1.15F);
        return true;
    }

    /**
     * Deploy a previously purchased kit at the player's chosen position.
     * Payment is intentionally not handled here; failed deployment leaves the
     * item in hand so the player can select a different area without paying
     * twice.
     */
    public static boolean deploy(ServerPlayer player, BlockPos deployPos, int index) {
        if (player == null || deployPos == null) return false;
        if (index < 0 || index >= PRICES.length) return false;
        if (!available()) {
            player.sendSystemMessage(Component.literal(
                    "Requires Recruits and Siege Weapons mods to deploy this siege crew."));
            return false;
        }

        ServerLevel level = player.serverLevel();

        // Size the ground-clearance check to the actual vehicle. The catapult
        // is 4x4 blocks so a fixed 3x3 clearance was too small: the vehicle's
        // corners fell outside the checked columns, level.noCollision saw a
        // block inside the bbox, and the deploy failed with the misleading
        // "Siege Weapons rejected the deployment spot" message. Ballista is
        // 2x2 and fits inside 3x3 already. footprintOf converts the centered
        // bounding box to exact occupied columns and also accounts for the
        // half-block vertical spawn offset.
        SiegeEngineType type = TYPES[index];
        SiegeIntegration.Footprint fp = SiegeIntegration.footprintOf(type);
        String flatIssue = describeClearance(level, deployPos, fp.horizontalRadius(), fp.blockHeight());
        if (flatIssue != null) {
            player.sendSystemMessage(Component.literal("Deployment blocked: " + flatIssue));
            return false;
        }

        Vec3 spawn = Vec3.atCenterOf(deployPos);
        float yaw = player.getYRot();
        Optional<Entity> vehicleOpt = SiegeIntegration.spawnSiegeVehicle(level, type, spawn, yaw);
        if (vehicleOpt.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    "Siege Weapons rejected the deployment spot. Try again from a clearer angle."));
            return false;
        }
        Entity vehicle = vehicleOpt.get();
        EngineerSpawn engineerResult = spawnFriendlyEngineer(player, level, spawn, vehicle, type);
        if (engineerResult.mob == null) {
            vehicle.discard();
            player.sendSystemMessage(Component.literal(
                    "Could not summon a Siege Engineer: " + engineerResult.reason));
            return false;
        }
        level.playSound(null, deployPos, SoundEvents.ANVIL_LAND,
                SoundSource.PLAYERS, 0.65F, 1.35F);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                spawn.x, spawn.y + 0.6D, spawn.z,
                10, 1.0D, 0.35D, 1.0D, 0.02D);
        player.sendSystemMessage(Component.literal(
                "Deployed your " + LABELS[index] + ". The crew is ready for orders."));
        return true;
    }

    static boolean isFlat3x3(ServerLevel level, BlockPos center) {
        return describeFlat3x3(level, center) == null;
    }

    public static int deploymentDiameter(SiegeIntegration.Footprint footprint) {
        return 2 * Math.max(1, footprint.horizontalRadius()) + 1;
    }

    public static String deploymentAreaGuidance(SiegeIntegration.Footprint footprint) {
        int diameter = deploymentDiameter(footprint);
        int height = Math.max(1, footprint.blockHeight());
        return diameter + "x" + diameter + " area with " + height + " blocks of headroom";
    }

    /**
     * Footprint-aware clearance check.
     *
     * <p>Verifies that the square from {@code center-radius} to
     * {@code center+radius} on each horizontal axis is free of solid blocks
     * and fluids for {@code height} vertical blocks starting at
     * {@code center}, and that the ground blocks immediately below
     * that square is sturdy. This matches the actual footprint the spawned
     * vehicle will occupy, so vanilla noCollision won't reject the spawn
     * because of a block outside the previously fixed 3x3 window.</p>
     *
     * @return a human-readable reason the site is unsuitable, or null when
     *         it is fine.
     */
    static String describeClearance(ServerLevel level, BlockPos center, int radius, int height) {
        int h = Math.max(1, height);
        int r = Math.max(1, radius);
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            BlockPos p = center.offset(dx, 0, dz);
            if (!level.hasChunkAt(p) || !level.getWorldBorder().isWithinBounds(p)) {
                return "Deployment target is outside the loaded world.";
            }
            for (int dy = 0; dy < h; dy++) {
                BlockPos q = p.above(dy);
                if (!level.getFluidState(q).isEmpty()) {
                    return "There is fluid at " + coord(q) + ".";
                }
                BlockState state = level.getBlockState(q);
                if (!state.isAir() && !state.canBeReplaced()) {
                    return "A block is in the way at " + coord(q) + " (" + blockName(state) + "). Clear a "
                            + (2 * r + 1) + "x" + (2 * r + 1) + " space " + h + " blocks tall.";
                }
            }
            // The whole footprint needs sturdy ground: a catapult that
            // straddles a 1-block hole spawns fine but immediately rolls
            // into it, so we require every column below to be solid.
            BlockState below = level.getBlockState(p.below());
            if (!below.isFaceSturdy(level, p.below(), Direction.UP)) {
                return "The ground under " + coord(p) + " is not solid. Fill it with any full block.";
            }
        }
        return null;
    }

    /**
     * Human-readable reason why a 3x3 deployment area at {@code center} is
     * unsuitable, or null when it is fine. Rules were relaxed: replaceable
     * blocks like grass and snow layers are treated as clear space, and
     * sturdy ground is only required at the center and four corners.
     */
    static String describeFlat3x3(ServerLevel level, BlockPos center) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos p = center.offset(dx, 0, dz);
            if (!level.hasChunkAt(p) || !level.getWorldBorder().isWithinBounds(p)) {
                return "Deployment target is outside the loaded world.";
            }
            if (!level.getFluidState(p).isEmpty()) return "There is fluid where the crew should stand at " + coord(p) + ".";
            if (!level.getFluidState(p.above()).isEmpty()) return "There is fluid above the crew at " + coord(p.above()) + ".";
            BlockState ground = level.getBlockState(p);
            if (!ground.isAir() && !ground.canBeReplaced()) {
                return "A solid block is in the way at " + coord(p) + " (" + blockName(ground) + "). Clear a 3x3 space.";
            }
            BlockState above1 = level.getBlockState(p.above());
            if (!above1.isAir() && !above1.canBeReplaced()) {
                return "Not enough headroom at " + coord(p.above()) + " (" + blockName(above1) + ").";
            }
            BlockState above2 = level.getBlockState(p.above(2));
            if (!above2.isAir() && !above2.canBeReplaced()) {
                return "Not enough headroom at " + coord(p.above(2)) + " (" + blockName(above2) + ").";
            }
        }
        BlockPos[] anchors = {
                center,
                center.offset(-1, 0, -1), center.offset(1, 0, -1),
                center.offset(-1, 0,  1), center.offset(1, 0,  1)
        };
        for (BlockPos a : anchors) {
            if (!level.getBlockState(a.below()).isFaceSturdy(level, a.below(), Direction.UP)) {
                return "The ground under " + coord(a) + " is not solid. Fill it with any full block.";
            }
        }
        return null;
    }

    private static String coord(BlockPos p) { return p.getX() + ", " + p.getY() + ", " + p.getZ(); }

    private static String blockName(BlockState state) {
        net.minecraft.resources.ResourceLocation id =
                net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return id == null ? "unknown" : id.getPath();
    }

    /**
     * Spawn a friendly siege engineer owned by the player and mount them
     * onto the freshly spawned vehicle. Uses reflection to call the same
     * hire path the CoreHiring class uses so ownership, faction, and unit
     * count all stay consistent with a normal recruit hire.
     */
    /** Small result carrier so the caller can surface which step failed. */
    private static final class EngineerSpawn {
        final Mob mob;
        final String reason;
        EngineerSpawn(Mob mob, String reason) { this.mob = mob; this.reason = reason; }
        static EngineerSpawn ok(Mob m) { return new EngineerSpawn(m, ""); }
        static EngineerSpawn fail(String r) { return new EngineerSpawn(null, r); }
    }

    private static EngineerSpawn spawnFriendlyEngineer(ServerPlayer player, ServerLevel level,
                                                       Vec3 pos, Entity vehicle, SiegeEngineType type) {
        try {
            ResourceLocation id = new ResourceLocation("recruits", "siege_engineer");
            if (!ForgeRegistries.ENTITY_TYPES.containsKey(id))
                return EngineerSpawn.fail("Recruits entity type 'siege_engineer' is not registered. Update or reinstall Recruits.");
            EntityType<?> et = ForgeRegistries.ENTITY_TYPES.getValue(id);
            if (et == null)
                return EngineerSpawn.fail("Recruits siege_engineer entity type is registered but returned null.");
            Entity entity;
            try {
                entity = et.create(level);
            } catch (RuntimeException ex) {
                FactionLogger.LOG.warn("siege_engineer create() threw", ex);
                return EngineerSpawn.fail("Recruits siege_engineer constructor threw: " + ex.getClass().getSimpleName());
            }
            if (entity == null)
                return EngineerSpawn.fail("Recruits siege_engineer create() returned null.");
            if (!(entity instanceof Mob mob))
                return EngineerSpawn.fail("Recruits siege_engineer is not a Mob (class: " + entity.getClass().getSimpleName() + ").");
            mob.moveTo(pos.x, pos.y, pos.z, vehicle.getYRot(), 0F);
            // We do not call mob.finalizeSpawn here. Recruits' SiegeEngineerEntity
            // overrides finalizeSpawn with a hard cast:
            //   ((GroundPathNavigation) this.getNavigation()).setCanOpenDoors(true);
            // but AbstractRecruitEntity's createNavigation returns a
            // RecruitPathNavigation, which does not extend GroundPathNavigation,
            // so the cast throws ClassCastException on every non-hire spawn.
            // Their in-game hire flow avoids the issue only because hire()
            // routes through a different code path. We do the useful side
            // effects of finalizeSpawn manually instead.
            try {
                net.minecraft.world.entity.ai.navigation.PathNavigation nav = mob.getNavigation();
                if (nav instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation ground) {
                    ground.setCanOpenDoors(true);
                } else {
                    try {
                        nav.getClass().getMethod("setCanOpenDoors", boolean.class).invoke(nav, true);
                    } catch (ReflectiveOperationException ignored) {}
                }
            } catch (RuntimeException ignored) {}
            try { mob.getClass().getMethod("initSpawn").invoke(mob); }
            catch (ReflectiveOperationException ignored) {}
            // Cost setter so the hire event doesn't refuse.
            try { mob.getClass().getMethod("setCost", int.class).invoke(mob, 0); }
            catch (ReflectiveOperationException ignored) {}
            mob.setPersistenceRequired();
            if (!level.addFreshEntity(mob))
                return EngineerSpawn.fail("Level rejected addFreshEntity for siege_engineer (spot may be blocked).");
            // Hand ownership directly rather than going through Recruits' hire()
            // path. hire() enforces the player's global recruit cap and returns
            // false with an "INFO_RECRUITING_MAX" message when the player is at
            // the limit, which surfaced as a misleading "make sure Recruits is
            // fully loaded" error from the SiegeYard. Siege engineers are
            // bought via the SiegeYard, not the vanilla Recruits menu, so they
            // do not need to count against that cap.
            try {
                mob.getClass().getMethod("setOwnerUUID", Optional.class)
                        .invoke(mob, Optional.of(player.getUUID()));
                mob.getClass().getMethod("setIsOwned", boolean.class).invoke(mob, true);
                try { mob.getClass().getMethod("setFollowState", int.class).invoke(mob, 2); }
                catch (ReflectiveOperationException ignored) {}
                try { mob.getClass().getMethod("setAggroState", int.class).invoke(mob, 0); }
                catch (ReflectiveOperationException ignored) {}
                try { mob.getClass().getMethod("resetPaymentTimer").invoke(mob); }
                catch (ReflectiveOperationException ignored) {}
                // Assign to the player's scoreboard team if they have one, so
                // the engineer inherits faction ownership visuals and doesn't
                // get shot by friendly recruits.
                try {
                    net.minecraft.world.scores.Team team = player.getTeam();
                    if (team instanceof net.minecraft.world.scores.PlayerTeam pt) {
                        level.getScoreboard().addPlayerToTeam(mob.getStringUUID(), pt);
                    }
                } catch (RuntimeException ignored) {}
            } catch (ReflectiveOperationException e) {
                mob.discard();
                FactionLogger.LOG.warn("siege_engineer ownership reflection failed", e);
                return EngineerSpawn.fail("Ownership setters missing on Recruits siege_engineer (" + e.getClass().getSimpleName() + "). Recruits API may have changed.");
            }
            // Give the engineer their ammunition so they actually fire.
            try {
                Object inventory = mob.getClass().getMethod("getInventory").invoke(mob);
                if (inventory instanceof net.minecraft.world.SimpleContainer container) {
                    net.minecraft.world.item.Item ammo = type == SiegeEngineType.BALLISTA
                            ? ForgeRegistries.ITEMS.getValue(new ResourceLocation("siegeweapons", "ballista_projectile_item"))
                            : net.minecraft.world.item.Items.COBBLESTONE;
                    if (ammo != null && ammo != net.minecraft.world.item.Items.AIR) {
                        container.addItem(new net.minecraft.world.item.ItemStack(ammo, 64));
                    }
                    container.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 16));
                }
            } catch (ReflectiveOperationException e) {
                FactionLogger.LOG.debug("Could not stock friendly siege engineer: {}", e.toString());
            }
            if (!SiegeIntegration.assignSiegeEngineer(mob, vehicle)) {
                // Even if the controller mount failed, keep the crew; the player
                // can still ride the vehicle themselves. Ammo is loaded, so this
                // is a soft degradation rather than a failure.
                FactionLogger.LOG.debug("Friendly siege engineer could not mount natively; standing by beside the vehicle");
            }
            return EngineerSpawn.ok(mob);
        } catch (RuntimeException ex) {
            FactionLogger.LOG.warn("Friendly siege crew spawn failed", ex);
            return EngineerSpawn.fail("Unexpected " + ex.getClass().getSimpleName() + " during spawn. See latest.log for stack trace.");
        }
    }
}
