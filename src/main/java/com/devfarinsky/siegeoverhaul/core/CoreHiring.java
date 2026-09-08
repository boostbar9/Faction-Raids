package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.FactionLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.ForgeRegistries;

/** Uses native costs, currency, hiring events, ownership, faction and unit-limit checks. */
public final class CoreHiring {
    public static final String[] IDS = {"recruit", "recruit_shieldman", "bowman", "crossbowman", "farmer", "lumberjack", "miner", "builder", "cook", "courier"};
    public static final String[] NAMES = {"Recruit", "Shieldman", "Archer", "Crossbowman", "Farmer", "Lumberjack", "Miner", "Builder", "Cook", "Courier", "Kael Bloodthorn", "Branna Dawnwarden", "Sylva Stormbow", "Orin Frostbinder"};
    private static final String[] COSTS = {"RecruitCost", "ShieldmanCost", "BowmanCost", "CrossbowmanCost", "FarmerCost", "LumberjackCost", "MinerCost", "BuilderCost", "CookCost", "CourierCost"};
    private CoreHiring() {}
    private static Object config(String name) throws ReflectiveOperationException {
        return ((ForgeConfigSpec.ConfigValue<?>) Class.forName("com.talhanation.recruits.config.RecruitsServerConfig").getField(name).get(null)).get();
    }
    public static int cost(int role) throws ReflectiveOperationException {
        if (role >= 10 && role <= 13) return (int)Math.min(32767L,Math.max(256L,(long)cost(role-10)*12));
        if (role < CoreOffers.WORKER_START) return (Integer) config(COSTS[role]);
        return (Integer) ((ForgeConfigSpec.ConfigValue<?>) Class.forName("com.talhanation.workers.config.WorkersServerConfig").getField(COSTS[role]).get(null)).get();
    }
    public static Item icon(int role) {
        if (role >= 10 && role <= 13) return icon(role-10);
        return switch (role) {
            case 0 -> Items.IRON_SWORD; case 1 -> Items.SHIELD; case 2 -> Items.BOW;
            case 3 -> Items.CROSSBOW; case 4 -> Items.WHEAT; case 5 -> Items.IRON_AXE;
            case 6 -> Items.IRON_PICKAXE; case 7 -> Items.BRICKS; case 8 -> Items.COOKED_BEEF;
            case 9 -> Items.CHEST; default -> Items.BARRIER;
        };
    }
    public static int weight(int role) {
        if (role >= 10 && role <= 13) return CoreOffers.HERO_WEIGHTS[role-10];
        return role < CoreOffers.WORKER_START ? CoreOffers.RECRUIT_WEIGHTS[role]
                : CoreOffers.WORKER_WEIGHTS[role - CoreOffers.WORKER_START];
    }
    public static String rarity(int role) {
        if (role >= 10 && role <= 13) return "Hero";
        int weight = weight(role);
        return weight >= 25 ? "Common" : weight >= 15 ? "Uncommon" : weight >= 10 ? "Rare" : "Very rare";
    }
    public static Item currency() throws ReflectiveOperationException {
        ResourceLocation id = ResourceLocation.tryParse((String) config("RecruitCurrency"));
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        return item == null || item == Items.AIR ? Items.EMERALD : item;
    }
    private static void prepareHero(Mob recruit,int role) throws ReflectiveOperationException {
        recruit.getClass().getMethod("setXpLevel",int.class).invoke(recruit,10);
        var health=recruit.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if(health!=null) health.setBaseValue(Math.max(health.getBaseValue(),60));
        recruit.setHealth(recruit.getMaxHealth());
        var attack=recruit.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if(attack!=null) attack.setBaseValue(attack.getBaseValue()+4);
        Object inventory=recruit.getClass().getMethod("getInventory").invoke(recruit);
        if(!(inventory instanceof net.minecraft.world.SimpleContainer container)) throw new IllegalStateException("Hero inventory missing");
        HeroTraits.equip(recruit,role,container);
        container.addItem(new ItemStack(Items.BREAD,32));
        if(role>=12) container.addItem(new ItemStack(Items.ARROW,64));
        recruit.getPersistentData().putBoolean("SiegeHiredHero",true);
    }
    public static boolean hire(ServerPlayer player, BlockPos core, int role) {
        if (role < 0 || role >= NAMES.length) return false;
        int typeRole=role>=10?role-10:role;
        Mob mob = null;
        try {
            var type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(typeRole < CoreOffers.WORKER_START ? "recruits" : "workers", IDS[typeRole]));
            if (type == null || !(type.create(player.serverLevel()) instanceof Mob recruit)) throw new IllegalStateException("Missing recruit type: " + IDS[typeRole]);
            mob = recruit;
            boolean found = false;
            for (int radius = 2; radius <= 4 && !found; radius++) {
                for (int dx = -radius; dx <= radius && !found; dx++) for (int dz = -radius; dz <= radius && !found; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    for (int dy = -1; dy <= 1 && !found; dy++) {
                        BlockPos pos = core.offset(dx, dy, dz);
                        var level = player.serverLevel();
                        if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
                                || !level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()
                                || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)) continue;
                        recruit.moveTo(pos.getX()+.5, pos.getY(), pos.getZ()+.5, player.getYRot(), 0);
                        found = level.noCollision(recruit) && level.getEntities(recruit, recruit.getBoundingBox()).isEmpty();
                    }
                }
            }
            if (!found) { recruit.discard(); player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Clear a safe space beside the core for your new unit.")); return false; }
            recruit.finalizeSpawn(player.serverLevel(), player.serverLevel().getCurrentDifficultyAt(recruit.blockPosition()), MobSpawnType.EVENT, null, null);
            if (role>=10) prepareHero(recruit,role);
            // Use the same configured price as native villager hiring trades. Some workers
            // still hard-code their spawn cost, so align the entity with the displayed trade.
            int price = Math.max(0, cost(role));
            recruit.getClass().getMethod("setCost", int.class).invoke(recruit, price);
            Item currency = currency();
            int available = player.getInventory().countItem(currency);
            if (!player.isCreative() && available < price) {
                recruit.discard();
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("You need " + price + " ").append(currency.getDescription()));
                return false;
            }
            Class<?> group = Class.forName("com.talhanation.recruits.world.RecruitsGroup");
            var hire = recruit.getClass().getMethod("hire", Player.class, group, boolean.class);
            var faction = Class.forName("com.talhanation.recruits.FactionEvents").getMethod("addNPCToData", net.minecraft.server.level.ServerLevel.class, String.class, int.class);
            recruit.setPersistenceRequired();
            if (!player.serverLevel().addFreshEntity(recruit)) { recruit.discard(); return false; }
            if (!Boolean.TRUE.equals(hire.invoke(recruit, player, null, true))) { recruit.discard(); return false; }
            // Remove only the price, preserving all other stacks and their NBT.
            if (!player.isCreative()) {
                int remaining = price;
                for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (stack.is(currency)) { int take = Math.min(remaining, stack.getCount()); stack.shrink(take); remaining -= take; }
                }
                player.getInventory().setChanged();
            }
            // Hire already assigned owner, scoreboard team and the player's unit count.
            try { faction.invoke(null, player.serverLevel(), player.getTeam().getName(), 1); }
            catch (ReflectiveOperationException ex) { FactionLogger.LOG.warn("Core hire faction statistics could not update", ex); }
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            if (mob != null) mob.discard();
            FactionLogger.LOG.warn("Siege Core hiring unavailable", ex);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Unit hiring is unavailable; check the server log."));
            return false;
        }
    }
}
