package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.registries.ForgeRegistries;

/** Server-generated offer equipment; the portrait and delivered unit share these saved bytes. */
public final class CoreOfferEquipment {
    public static final EquipmentSlot[] SLOTS={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,
            EquipmentSlot.FEET,EquipmentSlot.OFFHAND,EquipmentSlot.MAINHAND};
    private CoreOfferEquipment() {}
    public static CompoundTag ensure(CompoundTag core,int index,int role,ServerLevel level) {
        String key="OfferEquipment"+index;
        CompoundTag saved=core.getCompound(key);
        if(saved.getLong("Rotation")==core.getLong("RefreshAt") && saved.getInt("Role")==role && saved.contains("Items",9))return saved;
        core.remove(key); // never leave a stale kit purchasable after regeneration fails
        Mob mob=null;
        try {
            int base=CoreHiring.isHero(role)?CoreHiring.heroBase(role):role;
            var type=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(base<4?"recruits":"workers",CoreHiring.IDS[base]));
            if(type==null || !(type.create(level) instanceof Mob created))return new CompoundTag();
            mob=created;
            mob.finalizeSpawn(level,level.getCurrentDifficultyAt(mob.blockPosition()),MobSpawnType.EVENT,null,null);
            Object value=mob.getClass().getMethod("getInventory").invoke(mob);
            if(!(value instanceof SimpleContainer inventory))return new CompoundTag();
            if(CoreHiring.isHero(role)) CoreHiring.prepareHero(mob,role,true);
            else if(role<4)RecruitPersonality.prepare(mob,role,inventory);
            else {WorkerStartingKit.prepare(mob,role,inventory);mob.setCustomName(Component.literal(RecruitPersonality.name(mob.getRandom())));}
            saved=capture(mob,inventory);saved.putInt("Role",role);saved.putLong("Rotation",core.getLong("RefreshAt"));
            core.put(key,saved);return saved;
        } catch(ReflectiveOperationException | RuntimeException ex) {
            return new CompoundTag();
        } finally {if(mob!=null)mob.discard();}
    }
    static CompoundTag capture(Mob mob,SimpleContainer inventory) {
        CompoundTag result=new CompoundTag();ListTag items=new ListTag();
        for(int i=0;i<inventory.getContainerSize();i++)items.add(inventory.getItem(i).save(new CompoundTag()));
        result.put("Items",items);
        if(mob.hasCustomName())result.putString("Name",Component.Serializer.toJson(mob.getCustomName()));
        return result;
    }
    public static boolean matches(CompoundTag kit,int role,long rotation) {
        return kit.getList("Items",10).size()>=6 && kit.contains("Role",3) && kit.getInt("Role")==role
                && kit.contains("Rotation",4) && kit.getLong("Rotation")==rotation;
    }
    public static ItemStack item(CompoundTag kit,int index) {
        var items=kit.getList("Items",10);return index<items.size()?ItemStack.of(items.getCompound(index)):ItemStack.EMPTY;
    }
    static void apply(Mob mob,SimpleContainer inventory,CompoundTag kit) {
        if(!kit.contains("Items",9))throw new IllegalArgumentException("Missing offer equipment");
        for(int i=0;i<inventory.getContainerSize();i++)inventory.setItem(i,item(kit,i));
        for(int i=0;i<SLOTS.length;i++)mob.setItemSlot(SLOTS[i],inventory.getItem(i));
        if(kit.contains("Name"))mob.setCustomName(Component.Serializer.fromJson(kit.getString("Name")));
        inventory.setChanged();
    }
}
