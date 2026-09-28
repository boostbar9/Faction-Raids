package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** Shares the native model parts, preserving its geometry, armor copies and held-item transforms. */
@Mod.EventBusSubscriber(modid=SiegeOverhaul.MOD_ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class HeroCastingModel<T extends LivingEntity> extends HumanoidModel<T> {
    private final HumanoidModel<T> original;
    public HeroCastingModel(HumanoidModel<T> original) {
        super(new ModelPart(List.of(),Map.of("head",original.head,"hat",original.hat,"body",original.body,
                "right_arm",original.rightArm,"left_arm",original.leftArm,"right_leg",original.rightLeg,"left_leg",original.leftLeg)));
        this.original=original;
    }
    @SubscribeEvent @SuppressWarnings({"rawtypes","unchecked"})
    public static void install(EntityRenderersEvent.AddLayers event) {
        try {
            var field=ObfuscationReflectionHelper.findField(LivingEntityRenderer.class,"f_115290_");
            Set<LivingEntityRenderer> seen=Collections.newSetFromMap(new IdentityHashMap<>());
            for(var type:ForgeRegistries.ENTITY_TYPES.getValues()) {
                var id=ForgeRegistries.ENTITY_TYPES.getKey(type);
                if(id==null || !id.getNamespace().equals("recruits"))continue;
                LivingEntityRenderer renderer=event.getRenderer((EntityType)type);
                if(renderer!=null && seen.add(renderer) && renderer.getModel() instanceof HumanoidModel model
                        && !(model instanceof HeroCastingModel))field.set(renderer,new HeroCastingModel(model));
            }
        } catch(ReflectiveOperationException | RuntimeException ex) {
            FactionLogger.LOG.warn("Hero casting poses unavailable; native models and ability effects remain active",ex);
        }
    }
    @Override public void prepareMobModel(T entity,float swing,float amount,float partialTick) {
        copyPropertiesTo(original);original.prepareMobModel(entity,swing,amount,partialTick);
        original.copyPropertiesTo(this);
    }
    @Override public void setupAnim(T entity,float swing,float amount,float age,float yaw,float pitch) {
        copyPropertiesTo(original);original.setupAnim(entity,swing,amount,age,yaw,pitch);
        var cast=HeroCastVisuals.current(entity);
        if(cast!=null && HeroVisualConfig.ANIMATIONS.get() && !entity.isPassenger() && !entity.isSleeping()) {
            float elapsed=entity.level().getGameTime()-cast.start()+(age-entity.tickCount);
            applyPose(this,cast.role(),cast.phase(),HeroCastVisuals.poseWeight(cast.phase(),elapsed));
        }
    }
    static void applyPose(HumanoidModel<?> model,int phase,float weight) {
        float arm=phase==0?-2.35F:-.65F;
        model.rightArm.xRot+=(arm-model.rightArm.xRot)*weight;
        model.leftArm.xRot+=(arm-model.leftArm.xRot)*weight;
        model.rightArm.zRot+=(-.35F-model.rightArm.zRot)*weight;
        model.leftArm.zRot+=(.35F-model.leftArm.zRot)*weight;
    }
    static void applyPose(HumanoidModel<?> model,int role,int phase,float weight) {
        if(role==22) { // Raise the weapon to call the storm; the free arm braces the release.
            blend(model.rightArm,phase==0?-2.7F:-1.4F,-.2F,weight);
            blend(model.leftArm,phase==0?-.7F:-1.1F,.55F,weight);
        } else if(role==27) { // Draw the fire inward, then thrust both hands forward.
            blend(model.rightArm,phase==0?-1.2F:-1.75F,-.65F,weight);
            blend(model.leftArm,phase==0?-1.2F:-1.75F,.65F,weight);
        } else if(role==29) { // Wide arms sweep the tide together.
            blend(model.rightArm,-1.05F,phase==0?-1.1F:-.25F,weight);
            blend(model.leftArm,-1.05F,phase==0?1.1F:.25F,weight);
        }
    }
    private static void blend(ModelPart arm,float x,float z,float weight) {
        float w=Math.max(0,Math.min(1,weight));
        arm.xRot+=(x-arm.xRot)*w;arm.zRot+=(z-arm.zRot)*w;
    }
    @Override public void renderToBuffer(PoseStack pose,VertexConsumer vertices,int light,int overlay,float r,float g,float b,float a) {
        copyPropertiesTo(original);original.renderToBuffer(pose,vertices,light,overlay,r,g,b,a);
    }
    @Override public void translateToHand(HumanoidArm hand,PoseStack pose) {original.translateToHand(hand,pose);}
}
