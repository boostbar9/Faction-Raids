package com.devfarinsky.siegeoverhaul.items;

import com.devfarinsky.siegeoverhaul.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;

/** Vanilla armor and trim NBT: synced by equipment packets and retained by native inventory saves. */
public final class FactionUniforms {
    public static final String FACTION = "SiegeUniformFaction";
    private FactionUniforms() {}
    public static String factionMaterial(String faction) {
        return switch(FactionBanners.FactionId.byIdOrDefault(faction)) {
            case BLACKBAY_REAVERS -> "lapis";
            case HOLLOWFANG_CLAN -> "quartz";
            case EMBERCHANT_ZEALOTS -> "redstone";
            case CROWNFALL_EXILES -> "amethyst";
            case WILDS_MARAUDERS -> "emerald";
        };
    }
    public static String rankMaterial(String role) {
        return switch(role) { case "commander" -> "diamond"; case "captain" -> "gold"; case "guard" -> "iron"; default -> "copper"; };
    }
    public static ItemStack armor(String faction,String role,EquipmentSlot slot) {
        boolean commander="commander".equals(role);
        boolean wild=FactionBanners.FactionId.byIdOrDefault(faction)==FactionBanners.FactionId.WILDS_MARAUDERS && !commander && !"captain".equals(role);
        Item item=switch(slot) {
            case HEAD -> commander?Items.DIAMOND_HELMET:wild?Items.LEATHER_HELMET:Items.IRON_HELMET;
            case CHEST -> commander?Items.DIAMOND_CHESTPLATE:wild?Items.LEATHER_CHESTPLATE:Items.IRON_CHESTPLATE;
            case LEGS -> commander?Items.DIAMOND_LEGGINGS:wild?Items.LEATHER_LEGGINGS:Items.IRON_LEGGINGS;
            case FEET -> commander?Items.DIAMOND_BOOTS:wild?Items.LEATHER_BOOTS:Items.IRON_BOOTS;
            default -> throw new IllegalArgumentException("Armor slot required");
        };
        ItemStack stack=new ItemStack(item);
        boolean rank=slot==EquipmentSlot.HEAD || slot==EquipmentSlot.FEET;
        CompoundTag trim=new CompoundTag();
        trim.putString("material","minecraft:"+(rank?rankMaterial(role):factionMaterial(faction)));
        trim.putString("pattern","minecraft:"+("commander".equals(role)?"spire":"captain".equals(role)?"ward":"guard".equals(role)?"sentry":"coast"));
        stack.getOrCreateTag().put("Trim",trim);
        stack.getOrCreateTag().putString(FACTION,FactionBanners.FactionId.byIdOrDefault(faction).id);
        if(item instanceof DyeableLeatherItem leather) leather.setColor(stack,0x52663D);
        return stack;
    }
    public static void decorateShield(ItemStack shield,String faction) {
        if(!shield.is(Items.SHIELD))return;
        var id=FactionBanners.FactionId.byIdOrDefault(faction);
        CompoundTag tag=new CompoundTag(); FactionBanners.applyToBlockEntityTag(tag,id);
        tag.putInt("Base",id.baseColor.getId());
        shield.getOrCreateTag().put("BlockEntityTag",tag);
        shield.getOrCreateTag().putString(FACTION,id.id);
    }
    private static void applyShields(Mob mob,String faction) {
        try {
            if(mob.getClass().getMethod("getInventory").invoke(mob) instanceof SimpleContainer inventory) {
                for(int i=0;i<inventory.getContainerSize();i++) {
                    ItemStack stack=inventory.getItem(i);
                    if(stack.is(Items.SHIELD) && !faction.equals(stack.getOrCreateTag().getString(FACTION))) {
                        decorateShield(stack,faction); inventory.setChanged();
                    }
                }
            }
            for(EquipmentSlot slot:new EquipmentSlot[]{EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND}) {
                ItemStack stack=mob.getItemBySlot(slot);
                if(stack.is(Items.SHIELD) && !faction.equals(stack.getOrCreateTag().getString(FACTION))) decorateShield(stack,faction);
            }
        } catch(ReflectiveOperationException ex) { FactionLogger.LOG.debug("Shield inventory unavailable",ex); }
    }
    public static void apply(Mob mob,String faction,String role) {
        if (!RecruitsBridge.isRecruitSoldier(mob)) return;
        // Apply once to each siege soldier, never replenish broken armor or touch hired player units.
        applyShields(mob,faction);
        if(mob.getPersistentData().getBoolean("SiegeUniformApplied")) return;
        try {
            Object inventory=mob.getClass().getMethod("getInventory").invoke(mob);
            if(!(inventory instanceof SimpleContainer container) || container.getContainerSize()<4) return;
            RecruitsBridge.assignToRaidersFaction(mob); // Repair legacy role-glow teams that broke native diplomacy.
            EquipmentSlot[] slots={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
            for(int i=0;i<slots.length;i++) {
                ItemStack stack=armor(faction,role,slots[i]);
                container.setItem(i,stack);
                mob.setItemSlot(slots[i],stack);
                mob.setDropChance(slots[i],0.0F);
            }
            mob.setCanPickUpLoot(false);
            mob.getPersistentData().putBoolean("SiegeUniformApplied",true);
        } catch(ReflectiveOperationException | RuntimeException ex) {
            FactionLogger.LOG.warn("Could not equip faction uniform for {}",mob.getUUID(),ex);
        }
    }
}
