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
    public static final int[] PRICES = { 560, 480 };
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
        player.sendSystemMessage(Component.literal(
                "Purchased a " + LABELS[index] + " deployment kit for " + price
                        + " emeralds. Right-click the top of a clear flat 3x3 area to deploy it."
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
        // 2x2 and fits inside 3x3 already. We ceil the width so a 2.5-wide
        // vehicle still gets a full 3-column pad on each side.
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
        Optional<Mob> engineerOpt = spawnFriendlyEngineer(player, level, spawn, vehicle, type);
        if (engineerOpt.isEmpty()) {
            vehicle.discard();
            player.sendSystemMessage(Component.literal(
                    "Could not summon a Siege Engineer. Check that the Recruits mod is fully loaded."));
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

    /**
     * Footprint-aware clearance check.
     *
     * <p>Verifies that the square from {@code center-radius} to
     * {@code center+radius} on each horizontal axis is free of solid blocks
     * and fluids for {@code height} vertical blocks starting at
     * {@code center}, and that the ring of ground blocks immediately below
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
    private static Optional<Mob> spawnFriendlyEngineer(ServerPlayer player, ServerLevel level,
                                                       Vec3 pos, Entity vehicle, SiegeEngineType type) {
        try {
            ResourceLocation id = new ResourceLocation("recruits", "siege_engineer");
            if (!ForgeRegistries.ENTITY_TYPES.containsKey(id)) return Optional.empty();
            EntityType<?> et = ForgeRegistries.ENTITY_TYPES.getValue(id);
            if (et == null) return Optional.empty();
            Entity entity = et.create(level);
            if (!(entity instanceof Mob mob)) return Optional.empty();
            mob.moveTo(pos.x, pos.y, pos.z, vehicle.getYRot(), 0F);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()),
                    MobSpawnType.EVENT, null, null);
            // Cost setter so the hire event doesn't refuse.
            try { mob.getClass().getMethod("setCost", int.class).invoke(mob, 0); }
            catch (ReflectiveOperationException ignored) {}
            mob.setPersistenceRequired();
            if (!level.addFreshEntity(mob)) return Optional.empty();
            // Hire the recruit under the player's ownership.
            try {
                Class<?> group = Class.forName("com.talhanation.recruits.world.RecruitsGroup");
                var hire = mob.getClass().getMethod("hire", Player.class, group, boolean.class);
                if (!Boolean.TRUE.equals(hire.invoke(mob, player, null, true))) {
                    mob.discard();
                    return Optional.empty();
                }
            } catch (ReflectiveOperationException e) {
                mob.discard();
                return Optional.empty();
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
            return Optional.of(mob);
        } catch (RuntimeException ex) {
            FactionLogger.LOG.warn("Friendly siege crew spawn failed", ex);
            return Optional.empty();
        }
    }
}
