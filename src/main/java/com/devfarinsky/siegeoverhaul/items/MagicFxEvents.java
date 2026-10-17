package com.devfarinsky.siegeoverhaul.items;

import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Impact visuals for patron arrows and melee hits. Visual only: damage and drops are untouched. */
@Mod.EventBusSubscriber(modid = SiegeOverhaul.MOD_ID)
public final class MagicFxEvents {
    static final String THEME = "SiegeMagicTheme";
    private MagicFxEvents() {}

    /** Remember the shooter's patron on the arrow; the bow may change before it lands. */
    @SubscribeEvent
    public static void arrowSpawned(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel) || event.loadedFromDisk()
                || !(event.getEntity() instanceof AbstractArrow arrow) || !(arrow.getOwner() instanceof LivingEntity owner)) return;
        var theme = ranged(owner);
        if (theme != null) arrow.getPersistentData().putInt(THEME, theme.ordinal() + 1);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void arrowLanded(ProjectileImpactEvent event) {
        if (event.isCanceled() || !(event.getProjectile() instanceof AbstractArrow arrow)
                || !(arrow.level() instanceof ServerLevel level)) return;
        var theme = MagicTheme.byId(arrow.getPersistentData().getInt(THEME) - 1);
        if (theme == null || !MagicFx.allow(arrow)) return;
        var at = event.getRayTraceResult().getLocation();
        boolean entity = event.getRayTraceResult() instanceof EntityHitResult;
        MagicFx.burst(level, at, theme, entity ? .9F : .6F);
        if (entity) MagicFx.ring(level, at, theme, 1.4F, 8);
        MagicFx.cue(level, at, theme, true);
        // Piercing or bouncing arrows would repeat the flourish on every block they touch.
        arrow.getPersistentData().remove(THEME);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void meleeHit(LivingHurtEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0 || !(event.getEntity().level() instanceof ServerLevel level)
                || !(event.getSource().getDirectEntity() instanceof LivingEntity attacker)) return;
        var weapon = attacker.getMainHandItem();
        if (weapon.getItem() instanceof BowItem || weapon.getItem() instanceof CrossbowItem) return;
        var theme = MagicTheme.of(weapon);
        if (theme == null || !MagicFx.allow(attacker)) return;
        var target = event.getEntity();
        if (target.position() == null) return;
        MagicFx.burst(level, target.position().add(0, target.getBbHeight() * .6, 0), theme, .55F);
    }

    private static MagicTheme ranged(LivingEntity owner) {
        for (var stack : new net.minecraft.world.item.ItemStack[]{owner.getMainHandItem(), owner.getOffhandItem()}) {
            if (stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem) {
                var theme = MagicTheme.of(stack);
                if (theme != null) return theme;
            }
        }
        return null;
    }
}
