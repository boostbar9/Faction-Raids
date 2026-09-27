package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import com.devfarinsky.siegeoverhaul.items.OlympianWeaponSkins;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import java.util.*;

/** Compose with existing item overrides instead of replacing Minecraft's model JSON files. */
@Mod.EventBusSubscriber(modid=SiegeOverhaul.MOD_ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class OlympianWeaponModels {
    private OlympianWeaponModels() {}
    static ResourceLocation id(String patron,String shape) {
        return new ResourceLocation(SiegeOverhaul.MOD_ID,"item/olympian/"+patron+"_"+shape);
    }
    @SubscribeEvent public static void register(ModelEvent.RegisterAdditional event) {
        for(String patron:OlympianWeaponSkins.PATRONS)for(String shape:OlympianWeaponSkins.SHAPES)event.register(id(patron,shape));
    }
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event) {
        var models=event.getModels();
        Map<String,BakedModel> skins=new HashMap<>();
        for(String patron:OlympianWeaponSkins.PATRONS)for(String shape:OlympianWeaponSkins.SHAPES) {
            BakedModel model=models.get(id(patron,shape));if(model!=null)skins.put(patron+"_"+shape,model);
        }
        for(var entry:new ArrayList<>(models.entrySet())) {
            if(!(entry.getKey() instanceof ModelResourceLocation loc) || !loc.getNamespace().equals("minecraft") || !loc.getVariant().equals("inventory"))continue;
            String item=loc.getPath();
            if(item.endsWith("_sword") || item.endsWith("_pickaxe") || item.endsWith("_hoe")
                    || Set.of("bow","crossbow","blaze_rod","fishing_rod").contains(item))
                models.put(loc,new SkinnedModel(entry.getValue(),skins));
        }
    }
    static final class SkinnedModel extends BakedModelWrapper<BakedModel> {
        private final ItemOverrides overrides;
        SkinnedModel(BakedModel original,Map<String,BakedModel> skins) {
            super(original);
            overrides=new ItemOverrides() {
                @Override public BakedModel resolve(BakedModel model,ItemStack stack,@Nullable ClientLevel level,@Nullable LivingEntity entity,int seed) {
                    String patron=OlympianWeaponSkins.patron(stack),shape=OlympianWeaponSkins.shape(stack);
                    if(!patron.isEmpty() && !shape.isEmpty()) {
                        String suffix="";
                        if(stack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(stack)) {
                            suffix=CrossbowItem.containsChargedProjectile(stack,Items.FIREWORK_ROCKET)?"_rocket":"_loaded";
                        }
                        else if(entity!=null && entity.isUsingItem() && entity.getUseItem()==stack) {
                            int elapsed=stack.getUseDuration()-entity.getUseItemRemainingTicks();
                            int duration=stack.getItem() instanceof CrossbowItem?CrossbowItem.getChargeDuration(stack):20;
                            if(shape.equals("bow") || shape.equals("crossbow"))suffix="_"+Math.min(3,Math.max(1,elapsed*3/Math.max(1,duration)+1));
                        }
                        var selected=skins.get(patron+"_"+shape+suffix);
                        if(selected!=null)return selected;
                    }
                    return original.getOverrides().resolve(original,stack,level,entity,seed);
                }
            };
        }
        @Override public ItemOverrides getOverrides(){return overrides;}
    }
}
