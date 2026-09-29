package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import com.devfarinsky.siegeoverhaul.items.OlympianWeaponSkins;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.world.item.ItemDisplayContext;
import com.mojang.blaze3d.vertex.PoseStack;
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
                models.put(loc,new SkinnedModel(entry.getValue(),skins,item));
        }
    }
    static final class SkinnedModel extends BakedModelWrapper<BakedModel> {
        private final ItemOverrides overrides;
        SkinnedModel(BakedModel original,Map<String,BakedModel> skins) {this(original,skins,"");}
        SkinnedModel(BakedModel original,Map<String,BakedModel> skins,String item) {
            super(original);
            // An override model's own transform is normally used after resolution.
            // Keep the native bow/crossbow hand transforms even when a skin is selected.
            Map<String,BakedModel> heldSkins=skins;
            if(item.equals("bow") || item.equals("crossbow")) {
                heldSkins=new HashMap<>(skins);
                for(String patron:OlympianWeaponSkins.PATRONS)for(String shape:OlympianWeaponSkins.SHAPES) {
                    if(shape.equals(item) || shape.startsWith(item+"_")) {
                        String key=patron+"_"+shape;
                        BakedModel skin=skins.get(key);
                        if(skin!=null)heldSkins.put(key,new NativeGripModel(skin,original));
                    }
                }
            }
            Map<String,BakedModel> resolvedSkins=heldSkins;
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
                            if(shape.equals("bow") || shape.equals("crossbow")) {
                                float pull = Math.max(0, elapsed) / (float) Math.max(1, duration);
                                // Match the 1.20.1 vanilla model predicates, including Quick Charge.
                                int stage = shape.equals("crossbow")
                                        ? (pull >= 1.0F ? 3 : pull >= 0.58F ? 2 : 1)
                                        : (pull >= 0.9F ? 3 : pull >= 0.65F ? 2 : 1);
                                suffix = "_" + stage;
                            }
                        }
                        var selected=resolvedSkins.get(patron+"_"+shape+suffix);
                        if(selected!=null)return selected;
                    }
                    return original.getOverrides().resolve(original,stack,level,entity,seed);
                }
            };
        }
        @Override public ItemOverrides getOverrides(){return overrides;}
    }

    /** Render the custom art using the real item's baked third-person and first-person pose. */
    static final class NativeGripModel extends BakedModelWrapper<BakedModel> {
        private final BakedModel nativeModel;
        NativeGripModel(BakedModel skin,BakedModel nativeModel) {super(skin);this.nativeModel=nativeModel;}
        @Override public ItemTransforms getTransforms() {return nativeModel.getTransforms();}
        @Override public BakedModel applyTransform(ItemDisplayContext context,PoseStack pose,boolean leftHand) {
            nativeModel.applyTransform(context,pose,leftHand);
            return this;
        }
    }
}
