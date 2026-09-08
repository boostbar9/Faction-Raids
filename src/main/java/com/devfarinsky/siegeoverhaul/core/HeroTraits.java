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
            case 10 -> "Vanguard: 5s combat speed burst / 20s cooldown";
            case 11 -> "Bulwark: 5s Resistance I for up to 8 nearby allies / 25s";
            case 12 -> "Ranger: Power II bow; 5s evasive speed / 20s";
            case 13 -> "Arbalist: Quick Charge II; a hit slows for 3s / 20s";
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
        for(int i=0;i<4;i++){ItemStack stack=armor(role,armor[i]);inventory.setItem(i,stack);mob.setItemSlot(slots[i],stack);}
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
    @SubscribeEvent
    public static void tick(LivingEvent.LivingTickEvent event) {
        if(!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)
                || mob.tickCount%20!=0 || !mob.isAlive() || mob.isNoAi())return;
        int role=role(mob);if(role<10 || role>12 || mob.getTarget()==null || !mob.getTarget().isAlive())return;
        long now=level.getGameTime();var tag=mob.getPersistentData();
        if(!ready(now,tag.getLong("SiegeHeroNext")))return;
        if(role==12 && mob.distanceToSqr(mob.getTarget())>64)return;
        if(role==11) {
            var owner=RecruitsBridge.ownerUuid(mob);if(owner.isEmpty())return;
            var allies=level.getEntitiesOfClass(LivingEntity.class,mob.getBoundingBox().inflate(6),other->other.isAlive()
                    && !EnemyHiringProtection.enemy(other) && mob.hasLineOfSight(other)
                    && (other.getUUID().equals(owner.get()) || RecruitsBridge.ownerUuid(other).equals(owner)));
            allies.sort(java.util.Comparator.comparingDouble(mob::distanceToSqr));
            allies.stream().limit(8).forEach(other->other.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,100,0)));
            mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,100,0));
        } else mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,100,role==10?0:1));
        tag.putLong("SiegeHeroNext",now+(role==11?500:400));
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,mob.getX(),mob.getY()+1,mob.getZ(),16,.5,.6,.5,.05);
    }
    @SubscribeEvent
    public static void hit(LivingDamageEvent event) {
        if(event.getAmount()<=0 || !(event.getSource().getDirectEntity() instanceof Projectile projectile)
                || !(projectile.getOwner() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)
                || !mob.isAlive() || role(mob)!=13 || mob.isAlliedTo(event.getEntity())
                || !EnemyHiringProtection.enemy(event.getEntity()))return;
        long now=level.getGameTime();var tag=mob.getPersistentData();
        if(!ready(now,tag.getLong("SiegeHeroNext")))return;
        event.getEntity().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,60,0));
        tag.putLong("SiegeHeroNext",now+400);
    }
}
