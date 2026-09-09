package com.devfarinsky.siegeoverhaul.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.function.Function;

/** Curated coherent outfits; optional item lookup only, no client or Epic Knights classes linked. */
public final class EpicKnightsCompatibility {
    private static final String[][] OUTFITS = {
        {"kettlehat", "brigandine_chestplate", "chainmail_leggings", "chainmail_boots"},
        {"norman_helmet", "chainmail_chestplate", "chainmail_leggings", "chainmail_boots"},
        {"greathelm", "crusader_chestplate", "crusader_leggings", "crusader_boots"},
        {"barbute", "halfarmor_chestplate", "platemail_leggings", "platemail_boots"},
        {"sallet", "gothic_chestplate", "gothic_leggings", "gothic_boots"},
        {"maximilian_helmet", "maximilian_chestplate", "maximilian_leggings", "maximilian_boots"},
        {"coif", "gambeson_chestplate", "pantyhose", "gambeson_boots"}
    };
    private EpicKnightsCompatibility() {}
    public static ItemStack armor(ItemStack fallback, int outfit, EquipmentSlot slot, int color) {
        return armor(fallback,outfit,slot,color,ForgeRegistries.ITEMS::getValue);
    }
    static ItemStack armor(ItemStack fallback, int outfit, EquipmentSlot slot, int color, Function<ResourceLocation,Item> lookup) {
        int index=switch(slot){case HEAD->0;case CHEST->1;case LEGS->2;case FEET->3;default->-1;};
        if(index<0 || outfit<0 || outfit>=OUTFITS.length)return fallback;
        // Fall back as a complete outfit if a version/config disables any piece.
        Item[] pieces=new Item[4];
        EquipmentSlot[] slots={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
        for(int i=0;i<4;i++) {
            pieces[i]=lookup.apply(new ResourceLocation("magistuarmory",OUTFITS[outfit][i]));
            if(!(pieces[i] instanceof ArmorItem armor) || armor.getEquipmentSlot()!=slots[i])return fallback;
        }
        ItemStack result=new ItemStack(pieces[index],fallback.getCount());
        if(fallback.hasTag())result.setTag(fallback.getTag().copy());
        if(result.getItem() instanceof DyeableLeatherItem dyeable)dyeable.setColor(result,color);
        return result;
    }
}
