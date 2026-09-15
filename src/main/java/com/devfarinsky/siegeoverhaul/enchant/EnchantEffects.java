package com.devfarinsky.siegeoverhaul.enchant;

import com.devfarinsky.siegeoverhaul.ModConstants;
import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Behaviour for the signature enchantments in {@link ModEnchantments}.
 *
 * <p>All three effects are decided server-side inside combat events, so a
 * client cannot influence them. Each effect is bounded so a stacked loadout
 * cannot trivialise a siege.
 */
@Mod.EventBusSubscriber(modid = SiegeOverhaul.MOD_ID)
public final class EnchantEffects {

    /** Extra damage per Siegebreaker level against an enemy raider. */
    public static final float SIEGEBREAKER_DAMAGE_PER_LEVEL = 1.5F;
    /** Damage reduction per Bulwark level, per nearby enemy beyond the first. */
    public static final float BULWARK_REDUCTION_PER_LEVEL = 0.05F;
    /** Bulwark can never remove more than this share of an incoming hit. */
    public static final float BULWARK_MAX_REDUCTION = 0.40F;
    /** Enemies counted around the victim when scaling Bulwark. */
    public static final double BULWARK_RADIUS = 8.0D;

    private EnchantEffects() {}

    /** True when {@code entity} is an enemy raider belonging to some raid. */
    public static boolean isRaider(Entity entity) {
        return entity != null
                && !entity.getPersistentData().getString(ModConstants.Tags.RAID_TEAM).isBlank();
    }

    /** Damage bonus a Siegebreaker weapon adds against {@code victim}. */
    public static float siegebreakerBonus(int level, LivingEntity victim) {
        if (level <= 0 || !isRaider(victim)) return 0F;
        return level * SIEGEBREAKER_DAMAGE_PER_LEVEL;
    }

    /**
     * Fraction of an incoming hit Bulwark removes. The wearer gets nothing for
     * the first attacker, then a bounded bonus for each additional enemy.
     */
    public static float bulwarkReduction(int level, int nearbyEnemies) {
        if (level <= 0) return 0F;
        int outnumberedBy = Math.max(0, nearbyEnemies - 1);
        return Math.min(BULWARK_MAX_REDUCTION,
                level * BULWARK_REDUCTION_PER_LEVEL * outnumberedBy);
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;

        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            int level = EnchantmentHelper.getItemEnchantmentLevel(
                    ModEnchantments.SIEGEBREAKER.get(), attacker.getMainHandItem());
            float bonus = siegebreakerBonus(level, victim);
            if (bonus > 0F) event.setAmount(event.getAmount() + bonus);
        }

        int bulwark = EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.BULWARK.get(), victim.getItemBySlot(EquipmentSlot.CHEST));
        if (bulwark > 0) {
            int nearby = victim.level().getEntitiesOfClass(LivingEntity.class,
                    victim.getBoundingBox().inflate(BULWARK_RADIUS),
                    other -> other != victim && other.isAlive() && isRaider(other)).size();
            float reduction = bulwarkReduction(bulwark, nearby);
            if (reduction > 0F) event.setAmount(event.getAmount() * (1F - reduction));
        }
    }

    /** Extra war-key progress a Plunderer weapon grants on a kill. */
    public static int plundererBonus(ServerPlayer killer) {
        return EnchantmentHelper.getItemEnchantmentLevel(
                ModEnchantments.PLUNDERER.get(), killer.getMainHandItem());
    }
}
