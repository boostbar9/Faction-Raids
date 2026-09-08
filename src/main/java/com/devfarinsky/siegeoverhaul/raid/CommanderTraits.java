package com.devfarinsky.siegeoverhaul.raid;

import com.devfarinsky.siegeoverhaul.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=SiegeOverhaul.MOD_ID)
public final class CommanderTraits {
    private CommanderTraits() {}
    private static boolean commander(LivingEntity mob) {
        return "commander".equals(mob.getPersistentData().getString(ModConstants.Tags.RAID_ROLE));
    }
    public static void equip(Mob mob,String faction) {
        try {
            if(!(mob.getClass().getMethod("getInventory").invoke(mob) instanceof net.minecraft.world.SimpleContainer inventory))return;
            ItemStack axe=new ItemStack(Items.DIAMOND_AXE);
            axe.setHoverName(net.minecraft.network.chat.Component.literal("Siegebreaker"));
            inventory.setItem(5,axe);mob.setItemSlot(EquipmentSlot.MAINHAND,axe);mob.setDropChance(EquipmentSlot.MAINHAND,0);
            ItemStack shield=new ItemStack(Items.SHIELD);
            com.devfarinsky.siegeoverhaul.items.FactionUniforms.decorateShield(shield,faction);
            inventory.setItem(4,shield);mob.setItemSlot(EquipmentSlot.OFFHAND,shield);mob.setDropChance(EquipmentSlot.OFFHAND,0);
        } catch(ReflectiveOperationException ex) { FactionLogger.LOG.warn("Commander equipment unavailable",ex); }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void hurt(LivingDamageEvent event) {
        if(event.getAmount()>0 && commander(event.getEntity()) && event.getEntity().level() instanceof ServerLevel level)
            event.getEntity().getPersistentData().putLong("SiegeBossHurtAt",event.getEntity().getPersistentData().getLong("SiegeBossHurtAt")+1);
    }
    @SubscribeEvent
    public static void tick(LivingEvent.LivingTickEvent event) {
        if(!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)
                || !commander(mob) || mob.tickCount%20!=0 || mob.isNoAi() || !mob.isAlive()
                || mob.getHealth()>mob.getMaxHealth()*.5F || mob.getPersistentData().getBoolean("SiegeBossLastStand"))return;
        String team=mob.getPersistentData().getString(ModConstants.Tags.RAID_TEAM);
        var raid=RaidSavedData.get(level.getServer()).raids.get(team);if(raid==null || raid.preparationTicks>0)return;
        mob.getPersistentData().putBoolean("SiegeBossLastStand",true);
        var allies=level.getEntitiesOfClass(Mob.class,mob.getBoundingBox().inflate(8),other->other.isAlive()
                && team.equals(other.getPersistentData().getString(ModConstants.Tags.RAID_TEAM)) && mob.hasLineOfSight(other));
        allies.sort(java.util.Comparator.comparingDouble(mob::distanceToSqr));
        allies.stream().limit(6).forEach(other->other.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,100,0)));
        level.playSound(null,mob.blockPosition(),SoundEvents.RAID_HORN.value(),SoundSource.HOSTILE,1,1);
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER,mob.getX(),mob.getY()+2,mob.getZ(),16,1,.5,1,0);
        for(var player:level.players())if(player.distanceToSqr(mob)<1024)player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("Commander: Last Stand! Nearby troops gain strength for 5 seconds."),true);
    }
}
