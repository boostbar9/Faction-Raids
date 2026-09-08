package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;

/** One finite veteran kit per enemy worker; upgrading never repeatedly repairs gear or heals damage. */
public final class BuilderSupport {
    private BuilderSupport() {}
    public static void provision(Mob worker) throws ReflectiveOperationException {
        var tag=worker.getPersistentData();if(tag.getBoolean("SiegeBuilderKit"))return;
        if(!(worker.getClass().getMethod("getInventory").invoke(worker) instanceof SimpleContainer inventory))return;
        equip(worker,inventory);
        worker.getClass().getMethod("setMoral",float.class).invoke(worker,90F);
    }
    public static void equip(Mob worker,SimpleContainer inventory) {
        var tag=worker.getPersistentData();if(tag.getBoolean("SiegeBuilderKit"))return;
        Item[] items={Items.DIAMOND_HELMET,Items.DIAMOND_CHESTPLATE,Items.DIAMOND_LEGGINGS,Items.DIAMOND_BOOTS};
        EquipmentSlot[] slots={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
        for(int i=0;i<4;i++) {
            ItemStack armor=new ItemStack(items[i]);armor.enchant(Enchantments.ALL_DAMAGE_PROTECTION,3);armor.enchant(Enchantments.UNBREAKING,3);
            CompoundTag trim=new CompoundTag();trim.putString("material","minecraft:gold");trim.putString("pattern","minecraft:sentry");armor.getOrCreateTag().put("Trim",trim);
            inventory.setItem(i,armor);worker.setItemSlot(slots[i],armor);worker.setDropChance(slots[i],0);
        }
        java.util.List<ItemStack> supplies=new java.util.ArrayList<>();supplies.add(new ItemStack(Items.GOLDEN_APPLE,4));
        for(Item tool:new Item[]{Items.DIAMOND_PICKAXE,Items.DIAMOND_AXE,Items.DIAMOND_SHOVEL}) {
            ItemStack stack=new ItemStack(tool);stack.enchant(Enchantments.BLOCK_EFFICIENCY,3);stack.enchant(Enchantments.UNBREAKING,2);
            if(tool==Items.DIAMOND_PICKAXE){inventory.setItem(5,stack);worker.setItemSlot(EquipmentSlot.MAINHAND,stack);worker.setDropChance(EquipmentSlot.MAINHAND,0);}
            else supplies.add(stack);
        }
        supplies.add(new ItemStack(Items.BREAD,16));
        tag.put("SiegeBuilderSupplies",com.devfarinsky.siegeoverhaul.items.StarterBagItem.save(supplies));
        deliver(worker,inventory);
        float oldMax=worker.getMaxHealth(),health=worker.getHealth();var max=worker.getAttribute(Attributes.MAX_HEALTH);
        if(max!=null)max.setBaseValue(Math.max(60,max.getBaseValue()));
        if(oldMax>0)worker.setHealth(worker.getMaxHealth()*health/oldMax);
        var speed=worker.getAttribute(Attributes.MOVEMENT_SPEED);if(speed!=null)speed.setBaseValue(speed.getBaseValue()*1.2);
        var knockback=worker.getAttribute(Attributes.KNOCKBACK_RESISTANCE);if(knockback!=null)knockback.setBaseValue(Math.max(.4,knockback.getBaseValue()));
        tag.putBoolean("SiegeBuilderKit",true);inventory.setChanged();
    }
    private static void deliver(Mob worker,SimpleContainer inventory) {
        var tag=worker.getPersistentData();java.util.List<ItemStack> remaining=new java.util.ArrayList<>();
        for(var entry:tag.getList("SiegeBuilderSupplies",net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            ItemStack item=ItemStack.of((CompoundTag)entry);
            for(int i=6;i<inventory.getContainerSize() && !item.isEmpty();i++) {
                ItemStack current=inventory.getItem(i);
                if(current.isEmpty()){inventory.setItem(i,item.copy());item.setCount(0);}
                else if(ItemStack.isSameItemSameTags(current,item)) {
                    int n=Math.min(item.getCount(),current.getMaxStackSize()-current.getCount());current.grow(n);item.shrink(n);
                }
            }
            if(!item.isEmpty())remaining.add(item);
        }
        tag.put("SiegeBuilderSupplies",com.devfarinsky.siegeoverhaul.items.StarterBagItem.save(remaining));inventory.setChanged();
    }
    private static void requestSpecialMaterials(ServerLevel level,Mob worker,RaidSavedData.RaidState raid) throws ReflectiveOperationException {
        if (!NativeCampConstruction.active(raid) || raid.pendingCampBlocks.isEmpty()
                || Boolean.TRUE.equals(worker.getClass().getMethod("needsToGetToChest").invoke(worker))) return;
        // One live crew member fetches these ingredients, avoiding competing requests
        // for the same finite stock. Native storage AI performs the actual trip.
        for (var id:raid.campWorkers) if(level.getEntity(id) instanceof Mob candidate && candidate.isAlive()) {
            if(candidate!=worker)return;
            break;
        }
        if(!(worker.getClass().getMethod("getInventory").invoke(worker) instanceof SimpleContainer inventory))return;
        for(var stack:NativeCampConstruction.materials(level,raid.pendingCampBlocks)) {
            if(stack.getItem() instanceof net.minecraft.world.item.BlockItem || inventory.hasAnyMatching(s -> s.is(stack.getItem())))continue;
            Class<?> needed=Class.forName("com.talhanation.workers.world.NeededItem");
            java.util.function.Predicate<ItemStack> match=s -> s.is(stack.getItem());
            Object request=needed.getConstructor(java.util.function.Predicate.class,int.class,boolean.class).newInstance(match,1,true);
            worker.getClass().getMethod("addNeededItem",needed).invoke(worker,request);
            return;
        }
    }

    public static void tick(ServerLevel level,Mob worker,RaidSavedData.RaidState raid) {
        if(worker.tickCount%20!=0 || worker.isNoAi() || !worker.isAlive())return;
        try {
            provision(worker);BuilderWorkShift.install(worker);
            requestSpecialMaterials(level,worker,raid);
            if(worker.getClass().getMethod("getInventory").invoke(worker) instanceof SimpleContainer inventory)deliver(worker,inventory);
            if(NativeCampConstruction.active(raid) && !worker.getClass().getField("isFleeing").getBoolean(worker)
                    && level.getGameTime()%400==0) {
                float morale=((Number)worker.getClass().getMethod("getMorale").invoke(worker)).floatValue();
                if(morale<90)worker.getClass().getMethod("setMoral",float.class).invoke(worker,Math.min(90,morale+2));
            }
        } catch(ReflectiveOperationException ex) { FactionLogger.LOG.debug("Builder support API unavailable",ex); }
    }
}
