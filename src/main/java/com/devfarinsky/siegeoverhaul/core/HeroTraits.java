package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.compat.EnemyHiringProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Four readable specializations. Native AI, finite equipment, visible effects and bounded cooldowns. */
@Mod.EventBusSubscriber(modid=SiegeOverhaul.MOD_ID)
public final class HeroTraits {
    private HeroTraits() {}
    public static String description(int role) {
        return switch(role) {
            case 10 -> "Bloodthorn: every third melee hit heals 1 heart; full health grants a short shield (2s cooldown)";
            case 11 -> "Dawnwarden: shields one ally below 30% health for 5s (30s cooldown)";
            case 12 -> "Stormbow: every fourth arrow chains 2 damage hearts to up to two enemies (3s cooldown)";
            case 13 -> "Frostbinder: a bolt slows up to three enemies for 3s (10s cooldown)";
            default -> "";
        };
    }
    public static ItemStack armor(int role,Item item) {
        ItemStack stack=new ItemStack(item);CompoundTag trim=new CompoundTag();
        trim.putString("material","minecraft:"+switch(role){case 10->"redstone";case 11->"gold";case 12->"emerald";default->"amethyst";});
        trim.putString("pattern","minecraft:"+switch(role){case 10->"rib";case 11->"ward";case 12->"wild";default->"eye";});
        stack.getOrCreateTag().put("Trim",trim);
        stack.enchant(Enchantments.UNBREAKING,2);
        return stack;
    }
    public static void equip(Mob mob,int role,SimpleContainer inventory) {
        Item[] armor={Items.DIAMOND_HELMET,Items.DIAMOND_CHESTPLATE,Items.DIAMOND_LEGGINGS,Items.DIAMOND_BOOTS};
        EquipmentSlot[] slots={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
        for(int i=0;i<4;i++){ItemStack stack=armor(role,armor[i]);stack=com.devfarinsky.siegeoverhaul.compat.EpicKnightsCompatibility.armor(stack,4+(role%2),slots[i],new int[]{0x963F3F,0x476B86,0x738062,0x78548B}[role-10]);inventory.setItem(i,stack);mob.setItemSlot(slots[i],stack);}
        ItemStack weapon=new ItemStack(switch(role){case 12->Items.BOW;case 13->Items.CROSSBOW;default->Items.DIAMOND_SWORD;});
        if(role==12)weapon.enchant(Enchantments.POWER_ARROWS,2);
        else if(role==13){weapon.enchant(Enchantments.QUICK_CHARGE,2);weapon.enchant(Enchantments.PIERCING,1);}
        else weapon.enchant(Enchantments.SHARPNESS,2);
        weapon.setHoverName(Component.literal(switch(role){case 10->"Cinderfang";case 11->"Oathkeeper";case 12->"Thornsong";default->"Stormbolt";}).withStyle(ChatFormatting.GOLD));
        inventory.setItem(5,weapon);mob.setItemSlot(EquipmentSlot.MAINHAND,weapon);
        if(role<=11) {
            ItemStack shield=new ItemStack(Items.SHIELD);CompoundTag be=new CompoundTag();
            be.putInt("Base",role==10?DyeColor.RED.getId():DyeColor.BLUE.getId());
            var patterns=new net.minecraft.nbt.ListTag();CompoundTag pattern=new CompoundTag();
            pattern.putString("Pattern",role==10?"cs":"bo");pattern.putInt("Color",DyeColor.YELLOW.getId());patterns.add(pattern);be.put("Patterns",patterns);
            shield.getOrCreateTag().put("BlockEntityTag",be);shield.enchant(Enchantments.UNBREAKING,2);
            inventory.setItem(4,shield);mob.setItemSlot(EquipmentSlot.OFFHAND,shield);
        }
        mob.getPersistentData().putInt("SiegeHeroRole",role);
        mob.setCustomName(Component.literal(CoreHiring.NAMES[role]).withStyle(ChatFormatting.GOLD));
    }
    static boolean ready(long now,long next) { return next<=now || next>now+1200; }
    private static int role(Mob mob) {
        var tag=mob.getPersistentData();
        if(!tag.getBoolean("SiegeHiredHero") || EnemyHiringProtection.enemy(mob))return -1;
        if(!tag.contains("SiegeHeroRole")) {
            var id=net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
            if(id==null)return -1;
            int role=switch(id.getPath()){case "recruit"->10;case "recruit_shieldman"->11;case "bowman"->12;case "crossbowman"->13;default->-1;};
            tag.putInt("SiegeHeroRole",role);
        }
        return tag.getInt("SiegeHeroRole");
    }
    private static boolean hostile(Mob hero,LivingEntity target) {
        return target.isAlive() && EnemyHiringProtection.enemy(target) && !hero.isAlliedTo(target) && hero.hasLineOfSight(target);
    }
    static boolean charged(CompoundTag tag,String key,int interval) {
        int hits=Math.min(interval,Math.max(0,tag.getInt(key))+1);
        tag.putInt(key,hits);return hits>=interval;
    }
    private static void sparkle(ServerLevel level,LivingEntity target,net.minecraft.core.particles.SimpleParticleType particle) {
        level.sendParticles(particle,target.getX(),target.getY()+1,target.getZ(),16,.4,.6,.4,.02);
    }
    @SubscribeEvent
    public static void tick(LivingEvent.LivingTickEvent event) {
        if(!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)
                || mob.tickCount%20!=0 || !mob.isAlive() || mob.isNoAi() || role(mob)!=11)return;
        long now=level.getGameTime();var tag=mob.getPersistentData();
        if(!ready(now,tag.getLong("SiegeHeroNext")))return;
        var owner=RecruitsBridge.ownerUuid(mob);if(owner.isEmpty())return;
        var allies=level.getEntitiesOfClass(LivingEntity.class,mob.getBoundingBox().inflate(6),other->other.isAlive()
                && other.getHealth()<=other.getMaxHealth()*.3F && !other.hasEffect(MobEffects.ABSORPTION)
                && ready(now,other.getPersistentData().getLong("SiegeLastLight"))
                && !EnemyHiringProtection.enemy(other) && mob.hasLineOfSight(other)
                && (other.getUUID().equals(owner.get()) || RecruitsBridge.ownerUuid(other).equals(owner)));
        allies.sort(java.util.Comparator.comparingDouble(other->other.getHealth()/other.getMaxHealth()));
        if(allies.isEmpty())return;
        var ally=allies.get(0);ally.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,100,1));
        ally.getPersistentData().putLong("SiegeLastLight",now+600);tag.putLong("SiegeHeroNext",now+600);
        sparkle(level,ally,net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING);
        level.playSound(null,ally.blockPosition(),net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,net.minecraft.sounds.SoundSource.NEUTRAL,0.7F,1.2F);
    }
    @SubscribeEvent
    public static void hit(LivingDamageEvent event) {
        if(event.getAmount()<=0 || !(event.getSource().getEntity() instanceof Mob mob)
                || !(mob.level() instanceof ServerLevel level) || !mob.isAlive() || mob.isNoAi()
                || !hostile(mob,event.getEntity()))return;
        int role=role(mob);long now=level.getGameTime();var tag=mob.getPersistentData();
        if(role==10 && event.getSource().getDirectEntity()==mob) {
            if(!charged(tag,"SiegeHeroHits",3) || !ready(now,tag.getLong("SiegeHeroNext")))return;
            tag.putInt("SiegeHeroHits",0);tag.putLong("SiegeHeroNext",now+40);
            if(mob.getHealth()<mob.getMaxHealth())mob.heal(2);
            else if(!mob.hasEffect(MobEffects.ABSORPTION))mob.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,100,0));
            sparkle(level,mob,net.minecraft.core.particles.ParticleTypes.HEART);return;
        }
        // Only real projectile hits trigger ranged magic. Secondary chain damage
        // has the hero as its direct source, so it cannot recursively chain.
        if(!(event.getSource().getDirectEntity() instanceof Projectile projectile) || projectile.getOwner()!=mob)return;
        if(role==12) {
            if(!charged(tag,"SiegeHeroHits",4)) {sparkle(level,mob,net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK);return;}
            if(!ready(now,tag.getLong("SiegeHeroNext")))return;
            tag.putInt("SiegeHeroHits",0);tag.putLong("SiegeHeroNext",now+60);
            var primary=event.getEntity();
            var enemies=level.getEntitiesOfClass(LivingEntity.class,primary.getBoundingBox().inflate(6),e->e!=primary && hostile(mob,e) && primary.hasLineOfSight(e));
            enemies.sort(java.util.Comparator.comparingDouble(primary::distanceToSqr));
            for(var enemy:enemies.stream().limit(2).toList()) {
                if(enemy.hurt(level.damageSources().indirectMagic(mob,mob),4))sparkle(level,enemy,net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK);
            }
            sparkle(level,primary,net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK);
        } else if(role==13 && ready(now,tag.getLong("SiegeHeroNext"))) {
            tag.putLong("SiegeHeroNext",now+200);var primary=event.getEntity();
            var enemies=level.getEntitiesOfClass(LivingEntity.class,primary.getBoundingBox().inflate(3),e->hostile(mob,e) && primary.hasLineOfSight(e));
            enemies.sort(java.util.Comparator.comparingDouble(primary::distanceToSqr));
            for(var enemy:enemies.stream().limit(3).toList()) {
                if(!ready(now,enemy.getPersistentData().getLong("SiegeFrostNext")))continue;
                enemy.getPersistentData().putLong("SiegeFrostNext",now+100);
                enemy.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,60,1));
                sparkle(level,enemy,net.minecraft.core.particles.ParticleTypes.SNOWFLAKE);
            }
        }
    }
}
