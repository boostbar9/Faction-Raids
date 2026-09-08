package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import com.devfarinsky.siegeoverhaul.items.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** Cosmetic standard attached to the torso, leaving both hands and the helmet equipped. */
@Mod.EventBusSubscriber(modid=SiegeOverhaul.MOD_ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class FactionBackBannerLayer<T extends LivingEntity,M extends EntityModel<T>> extends RenderLayer<T,M> {
    private final Map<String,ItemStack> banners=new HashMap<>();
    public FactionBackBannerLayer(RenderLayerParent<T,M> parent) { super(parent); }
    @SubscribeEvent @SuppressWarnings({"rawtypes","unchecked"})
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        Set<LivingEntityRenderer> attached=Collections.newSetFromMap(new IdentityHashMap<>());
        for(var type:ForgeRegistries.ENTITY_TYPES.getValues()) {
            var id=ForgeRegistries.ENTITY_TYPES.getKey(type);
            if(id==null || !id.getNamespace().equals("recruits")) continue;
            var renderer=event.getRenderer(type);
            if(renderer instanceof LivingEntityRenderer living && attached.add(living))
                living.addLayer(new FactionBackBannerLayer(living));
        }
    }
    @Override public void render(PoseStack pose,MultiBufferSource buffers,int light,T entity,
            float limbSwing,float limbAmount,float partialTick,float age,float yaw,float pitch) {
        if(entity.isInvisible() || !(getParentModel() instanceof HumanoidModel<?> body)) return;
        var chest=entity.getItemBySlot(EquipmentSlot.CHEST);
        if(!chest.hasTag() || !chest.getTag().contains(FactionUniforms.FACTION)) return;
        String faction=chest.getTag().getString(FactionUniforms.FACTION);
        ItemStack banner=banners.computeIfAbsent(faction,id->FactionBanners.itemStackFor(FactionBanners.FactionId.byIdOrDefault(id)));
        pose.pushPose();
        body.body.translateAndRotate(pose);
        pose.translate(0,-0.65,0.32);
        pose.mulPose(Axis.ZP.rotationDegrees(180));
        pose.scale(0.65F,0.65F,0.65F);
        Minecraft.getInstance().getItemRenderer().renderStatic(banner,ItemDisplayContext.FIXED,light,
                OverlayTexture.NO_OVERLAY,pose,buffers,entity.level(),entity.getId());
        pose.popPose();
    }
}
