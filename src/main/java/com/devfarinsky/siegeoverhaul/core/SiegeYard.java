package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.FactionLogger;
import com.devfarinsky.siegeoverhaul.siege.SiegeEngineType;
import com.devfarinsky.siegeoverhaul.siege.SiegeIntegration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

/**
 * v4.18.0 Army-tab Siege Yard: hires a friendly Recruits Siege Engineer
 * pre-mounted on a fresh Siege Weapons vehicle (catapult or ballista).
 * Charged like a normal recruit: bank first, then the player's inventory.
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
        ServerLevel level = player.serverLevel();
        BlockPos deployPos = findDeploySpot(level, core);
        if (deployPos == null) {
            player.sendSystemMessage(Component.literal(
                    "Clear an open flat area of at least 3x3 near the core to deploy a siege crew."));
            return false;
        }
        SiegeEngineType type = TYPES[index];
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
        if (!player.isCreative() && !PaymentSource.consume(player, price)) {
            engineerOpt.get().discard();
            vehicle.discard();
            return false;
        }
        player.sendSystemMessage(Component.literal(
                "Hired a " + LABELS[index] + " for " + price + " emeralds."));
        return true;
    }

    private static BlockPos findDeploySpot(ServerLevel level, BlockPos core) {
        // Look outward from the core for an unobstructed 3x3 flat patch.
        for (int radius = 3; radius <= 6; radius++) {
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos p = core.offset(dx, dy, dz);
                    if (isFlat3x3(level, p)) return p;
                }
            }
        }
        return null;
    }

    private static boolean isFlat3x3(ServerLevel level, BlockPos center) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos p = center.offset(dx, 0, dz);
            if (!level.hasChunkAt(p) || !level.getWorldBorder().isWithinBounds(p)) return false;
            if (!level.getFluidState(p).isEmpty()) return false;
            if (!level.getFluidState(p.above()).isEmpty()) return false;
            if (!level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) return false;
            if (!level.getBlockState(p).isAir() && !level.getBlockState(p).canBeReplaced()) return false;
            if (!level.getBlockState(p.above()).isAir() && !level.getBlockState(p.above()).canBeReplaced()) return false;
        }
        return true;
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
