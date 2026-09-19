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
            var host=com.devfarinsky.siegeoverhaul.narrative.OlympianHostIdentity.forFaction(faction);
            if(RaidConfig.RAIDER_LABEL_MODE.get()!=RaidConfig.LabelMode.OFF)
                mob.setCustomName(net.minecraft.network.chat.Component.literal(
                                host.factionId().isBlank()?"Siege Commander":host.hostName()+" Strategos")
                        .withStyle(net.minecraft.ChatFormatting.DARK_RED));
            axe.setHoverName(net.minecraft.network.chat.Component.literal(switch(host.commanderPower()) {
                case TIDAL_ADVANCE -> "Tempest Cleaver";
                case WAR_CRY -> "Bronze Siegebreaker";
                case FORGE_WARD -> "Forgehammer";
                case AEGIS_ORDER -> "Strategos' Edge";
                case HUNTERS_MARK -> "Moonlit Axe";
                default -> "Siegebreaker";
            }));
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
        var host=com.devfarinsky.siegeoverhaul.narrative.OlympianHostIdentity.forFaction(raid.factionId);
        applyPower(mob,allies.stream().limit(6).toList(),host.commanderPower(),team);
        playSignature(level,mob,host.commanderPower());
        for(var player:level.players())if(player.distanceToSqr(mob)<1024)player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(host.hostName()+" commander: "+host.commanderMessage()),true);
    }

    static void applyPower(Mob commander,java.util.List<Mob> allies,
                           com.devfarinsky.siegeoverhaul.narrative.OlympianHostIdentity.CommanderPower power,
                           String team) {
        for(Mob ally:allies) switch(power) {
            case TIDAL_ADVANCE -> {
                ally.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,100,1));
                ally.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING,100,0));
            }
            case WAR_CRY, LAST_STAND -> ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,100,0));
            case FORGE_WARD -> {
                ally.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,100,0));
                ally.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,100,0));
            }
            case AEGIS_ORDER -> ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,100,0));
            case HUNTERS_MARK -> {
                ally.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,100,0));
                ally.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,120,0));
            }
        }
        if(power==com.devfarinsky.siegeoverhaul.narrative.OlympianHostIdentity.CommanderPower.HUNTERS_MARK) {
            LivingEntity target=commander.getTarget();
            if(target==null || !target.isAlive()) for(Mob ally:allies)
                if(ally.getTarget()!=null && ally.getTarget().isAlive()) { target=ally.getTarget();break; }
            if(target!=null && target.isAlive()
                    && !team.equals(target.getPersistentData().getString(ModConstants.Tags.RAID_TEAM)))
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING,100,0));
        }
    }

    private static void playSignature(ServerLevel level,Mob mob,
                                      com.devfarinsky.siegeoverhaul.narrative.OlympianHostIdentity.CommanderPower power) {
        switch(power) {
            case TIDAL_ADVANCE -> {
                level.playSound(null,mob.blockPosition(),SoundEvents.TRIDENT_RIPTIDE_3,SoundSource.HOSTILE,1,.8F);
                level.sendParticles(ParticleTypes.SPLASH,mob.getX(),mob.getY()+1,mob.getZ(),24,1,.8,1,.1);
            }
            case WAR_CRY, LAST_STAND -> {
                level.playSound(null,mob.blockPosition(),SoundEvents.RAID_HORN.value(),SoundSource.HOSTILE,1,1);
                level.sendParticles(ParticleTypes.ANGRY_VILLAGER,mob.getX(),mob.getY()+2,mob.getZ(),16,1,.5,1,0);
            }
            case FORGE_WARD -> {
                level.playSound(null,mob.blockPosition(),SoundEvents.ANVIL_USE,SoundSource.HOSTILE,.9F,.8F);
                level.sendParticles(ParticleTypes.FLAME,mob.getX(),mob.getY()+1,mob.getZ(),20,1,.8,1,.03);
            }
            case AEGIS_ORDER -> {
                level.playSound(null,mob.blockPosition(),SoundEvents.SHIELD_BLOCK,SoundSource.HOSTILE,1,.7F);
                level.sendParticles(ParticleTypes.END_ROD,mob.getX(),mob.getY()+1,mob.getZ(),18,1,.8,1,.02);
            }
            case HUNTERS_MARK -> {
                level.playSound(null,mob.blockPosition(),SoundEvents.CROSSBOW_SHOOT,SoundSource.HOSTILE,.9F,1.2F);
                level.sendParticles(ParticleTypes.CRIT,mob.getX(),mob.getY()+1,mob.getZ(),24,1,.8,1,.1);
            }
        }
    }
}
