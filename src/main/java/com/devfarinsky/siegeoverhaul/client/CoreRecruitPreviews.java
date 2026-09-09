package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.core.CoreHiring;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.HashMap;
import java.util.Map;

/** Unticked, unspawned role portraits. These are not the later randomized hire's exact loadout. */
final class CoreRecruitPreviews {
    private final Map<Integer,LivingEntity> portraits=new HashMap<>();
    private final java.util.Set<Integer> unavailable=new java.util.HashSet<>();
    void draw(GuiGraphics g,int role,int x,int y,int scale){
        if(role<0 || role>=CoreHiring.NAMES.length || unavailable.contains(role))return;
        try {
            if(!portraits.containsKey(role)){
                int typeRole=role>=10?role-10:role;
                var id=new ResourceLocation(typeRole<4?"recruits":"workers",CoreHiring.IDS[typeRole]);
                var mc=Minecraft.getInstance();
                if(mc.level==null || !ForgeRegistries.ENTITY_TYPES.containsKey(id)){unavailable.add(role);return;}
                var entity=ForgeRegistries.ENTITY_TYPES.getValue(id).create(mc.level);
                if(!(entity instanceof LivingEntity living)){unavailable.add(role);return;}
                portraits.put(role,living);
            }
            InventoryScreen.renderEntityInInventoryFollowsMouse(g,x,y,scale,0,0,portraits.get(role));
        } catch(RuntimeException | LinkageError ex){
            unavailable.add(role);portraits.remove(role);
            com.devfarinsky.siegeoverhaul.FactionLogger.LOG.debug("Role portrait unavailable",ex);
        }
    }
    void clear(){portraits.clear();unavailable.clear();}
}
