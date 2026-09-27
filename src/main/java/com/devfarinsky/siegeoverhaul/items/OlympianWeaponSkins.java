package com.devfarinsky.siegeoverhaul.items;

import com.devfarinsky.siegeoverhaul.core.*;
import net.minecraft.world.item.*;
import java.util.*;

/** Appearance identity only. No item replacement, stats, enchantments or CustomModelData takeover. */
public final class OlympianWeaponSkins {
    public static final String HERO_ROLE="SiegeHeroWeaponRole";
    public static final List<String> PATRONS=List.of("ares","athena","artemis","hephaestus","poseidon","zeus","apollo","demeter");
    public static final List<String> SHAPES=List.of("blade","spear","staff","bow","bow_1","bow_2","bow_3","crossbow","crossbow_1","crossbow_2","crossbow_3","crossbow_loaded","crossbow_rocket","pick","hoe","hook");
    private OlympianWeaponSkins() {}
    public static void identifyHero(ItemStack stack,int role) {
        if(stack!=null && !stack.isEmpty() && CoreHiring.isHero(role))stack.getOrCreateTag().putInt(HERO_ROLE,role);
    }
    public static String patron(ItemStack stack) {
        if(stack==null || !stack.hasTag() || stack.getTag().contains("CustomModelData"))return "";
        var tag=stack.getTag();String patron=tag.getString("SiegeOlympianPatron");
        if(tag.contains(HERO_ROLE)) {
            int role=tag.getInt(HERO_ROLE);
            patron=switch(role) {
                case 10,14,18,27 -> "ares";
                case 11,21,23,25 -> "athena";
                case 13,19,26,28 -> "artemis";
                case 15,16,20,24 -> "hephaestus";
                case 12,17,22,29 -> "poseidon";
                default -> "";
            };
        }
        return PATRONS.contains(patron)?patron:"";
    }
    public static String shape(ItemStack stack) {
        var item=stack.getItem();
        if(item instanceof SwordItem)return stack.hasTag() && stack.getTag().getInt(HERO_ROLE)==10?"spear":"blade";
        if(item==Items.BLAZE_ROD)return "staff";
        if(item instanceof BowItem)return "bow";
        if(item instanceof CrossbowItem)return "crossbow";
        if(item instanceof PickaxeItem)return "pick";
        if(item instanceof HoeItem)return "hoe";
        if(item instanceof FishingRodItem)return "hook";
        return "";
    }
}
