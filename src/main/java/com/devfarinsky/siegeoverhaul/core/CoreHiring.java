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
    /**
     * v4.19.0 hero roster expanded from 4 to 20 across five rarities.
     * Role IDs 10-29 map to the {@link #HERO_BASE} entity role (0=sword,
     * 1=shield, 2=bow, 3=crossbow) that drives spawn type, gear and offer pool.
     * Mage heroes ride on top of role 0 (sword base) but replace their weapon
     * and attack loop through {@link HeroTraits}.
     */
    public static final String[] NAMES = {
        "Recruit", "Shieldman", "Archer", "Crossbowman",
        "Farmer", "Lumberjack", "Miner", "Builder", "Cook", "Courier",
        // Commons (10-11)
        "Garrick Ironoath", "Mira Stonehand",
        // Uncommons (12-15)
        "Sylva Stormbow", "Orin Frostbinder", "Kael Bloodthorn", "Branna Dawnwarden",
        // Rares (16-21)
        "Vex Emberstep", "Nyx Hollowveil", "Roric Warbell", "Elowen Verdant", "Thane Grimwatch", "Zara Wildsong",
        // Epics (22-26)
        "Arcanis Voidweaver", "Lyria Starweaver", "Pyra Ashenheart", "Sable Ironclad", "Talon Skyrender",
        // Legendaries (27-29)
        "Solmyra the Radiant", "Umbros the Nightcaller", "Chronos Timebender"
    };
    /** Underlying entity role each hero uses when spawned. 0=sword,1=shield,2=bow,3=crossbow. */
    public static final int[] HERO_BASE = {
        0, 1,          // Common: Garrick sword, Mira shield
        2, 3, 0, 1,    // Uncommon: bow, crossbow, sword, shield
        0, 2, 1, 2, 3, 0, // Rare
        0, 0, 0, 1, 2, // Epic: three mages + shield + bow
        0, 0, 0        // Legendary: all mages
    };
    /** Rarity tier per hero, 0=Common..4=Legendary. Aligns with {@link #RARITY_NAMES}. */
    public static final int[] HERO_TIER = {
        0, 0,
        1, 1, 1, 1,
        2, 2, 2, 2, 2, 2,
        3, 3, 3, 3, 3,
        4, 4, 4
    };
    /** Cost multiplier applied to base recruit cost, per rarity tier. */
    public static final double[] TIER_COST_MULT = { 8.0, 12.0, 18.0, 26.0, 40.0 };
    public static final String[] RARITY_NAMES = { "Common", "Uncommon", "Rare", "Epic", "Legendary" };
    public static final int HERO_ID_MIN = 10;
    public static final int HERO_ID_MAX = 29;
    public static boolean isHero(int role) { return role >= HERO_ID_MIN && role <= HERO_ID_MAX; }
    public static int heroTier(int role) { return isHero(role) ? HERO_TIER[role - HERO_ID_MIN] : -1; }
    public static int heroBase(int role) { return isHero(role) ? HERO_BASE[role - HERO_ID_MIN] : role; }
    private static final String[] COSTS = {"RecruitCost", "ShieldmanCost", "BowmanCost", "CrossbowmanCost", "FarmerCost", "LumberjackCost", "MinerCost", "BuilderCost", "CookCost", "CourierCost"};
    private CoreHiring() {}
    private static Object config(String name) throws ReflectiveOperationException {
        return ((ForgeConfigSpec.ConfigValue<?>) Class.forName("com.talhanation.recruits.config.RecruitsServerConfig").getField(name).get(null)).get();
    }
    /**
     * v4.18.0 price uplift so Faction-tab hires feel meaningful vs. bank
     * income. Applied to combat recruits (roles 0-3) and workers (4-9);
     * heroes derive their price from the base recruit price so the uplift
     * flows through automatically. Never lowers the mod-configured price.
     */
    private static int applyUplift(int base, int role) {
        if (base <= 0) return base;
        // Combat recruits +50%, workers +25%. Heroes already amplify (x12).
        double factor = role < CoreOffers.WORKER_START ? 1.50 : 1.25;
        long lifted = Math.max(base, (long) Math.ceil(base * factor));
        return (int) Math.min(Integer.MAX_VALUE, lifted);
    }
    public static int cost(int role) throws ReflectiveOperationException {
        if (isHero(role)) {
            int base = cost(heroBase(role));
            double mult = TIER_COST_MULT[heroTier(role)];
            // Floor at the old 256e minimum so heroes never feel disposable
            // on servers that lowered base recruit cost.
            return (int) Math.min(32767L, Math.max(256L, Math.round(base * mult)));
        }
        if (role < CoreOffers.WORKER_START) return applyUplift((Integer) config(COSTS[role]), role);
        int workerBase = (Integer) ((ForgeConfigSpec.ConfigValue<?>) Class.forName("com.talhanation.workers.config.WorkersServerConfig").getField(COSTS[role]).get(null)).get();
        return applyUplift(workerBase, role);
    }
    public static Item icon(int role) {
        if (isHero(role)) return icon(heroBase(role));
        return switch (role) {
            case 0 -> Items.IRON_SWORD; case 1 -> Items.SHIELD; case 2 -> Items.BOW;
            case 3 -> Items.CROSSBOW; case 4 -> Items.WHEAT; case 5 -> Items.IRON_AXE;
            case 6 -> Items.IRON_PICKAXE; case 7 -> Items.BRICKS; case 8 -> Items.COOKED_BEEF;
            case 9 -> Items.CHEST; default -> Items.BARRIER;
        };
    }
    public static int weight(int role) {
        if (isHero(role)) return CoreOffers.HERO_WEIGHTS[role - HERO_ID_MIN];
        return role < CoreOffers.WORKER_START ? CoreOffers.RECRUIT_WEIGHTS[role]
                : CoreOffers.WORKER_WEIGHTS[role - CoreOffers.WORKER_START];
    }
    public static String rarity(int role) {
        if (isHero(role)) return RARITY_NAMES[heroTier(role)];
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
        int typeRole = isHero(role) ? heroBase(role) : role;
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
            // v4.18.0 Iron Levy territory buff: extra 4 HP (2 hearts) on all fresh hires.
            if (TerritoryBuffs.has(player.server.overworld() == null ? null
                    : com.devfarinsky.siegeoverhaul.RaidSavedData.get(player.server).siegeCores.get(SiegeCore.key(player)),
                    3)) {
                var health = recruit.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                if (health != null) health.setBaseValue(health.getBaseValue() + 4);
                recruit.setHealth(recruit.getMaxHealth());
            }
            if (role>=10) prepareHero(recruit,role);
            else if (role < CoreOffers.WORKER_START) {
                Object inventory = recruit.getClass().getMethod("getInventory").invoke(recruit);
                if (!(inventory instanceof net.minecraft.world.SimpleContainer container))
                    throw new IllegalStateException("Recruit inventory missing");
                RecruitPersonality.prepare(recruit, role, container);
            }
            if (role >= CoreOffers.WORKER_START && role < 10) {
                Object inventory = recruit.getClass().getMethod("getInventory").invoke(recruit);
                if (!(inventory instanceof net.minecraft.world.SimpleContainer container))
                    throw new IllegalStateException("Worker inventory missing");
                WorkerStartingKit.prepare(recruit, role, container);
            }
            // Use the same configured price as native villager hiring trades. Some workers
            // still hard-code their spawn cost, so align the entity with the displayed trade.
            int price = Math.max(0, cost(role));
            recruit.getClass().getMethod("setCost", int.class).invoke(recruit, price);
            Item currency = currency();
            // Bank-first payment: check combined bank + inventory funds.
            long combined = PaymentSource.available(player, price);
            if (!player.isCreative() && combined < price) {
                recruit.discard();
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("You need " + price + " ").append(currency.getDescription()).append(net.minecraft.network.chat.Component.literal(" (bank + inventory)")));
                return false;
            }
            Class<?> group = Class.forName("com.talhanation.recruits.world.RecruitsGroup");
            var hire = recruit.getClass().getMethod("hire", Player.class, group, boolean.class);
            var faction = Class.forName("com.talhanation.recruits.FactionEvents").getMethod("addNPCToData", net.minecraft.server.level.ServerLevel.class, String.class, int.class);
            recruit.setPersistenceRequired();
            if (!player.serverLevel().addFreshEntity(recruit)) { recruit.discard(); return false; }
            if (!Boolean.TRUE.equals(hire.invoke(recruit, player, null, true))) { recruit.discard(); return false; }
            // Bank-first debit; PaymentSource handles the shared-treasury draw
            // before touching the player's own emeralds.
            if (!PaymentSource.consume(player, price)) {
                recruit.discard();
                return false;
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
