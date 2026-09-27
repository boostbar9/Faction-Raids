package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.world.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeroCastingModelTest extends MinecraftTestSupport {
    @Test void nativeGeometryAndArmorCopiesShareTheCastPose() {
        var root=LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE,0),64,64).bakeRoot();
        var nativeModel=new HumanoidModel<LivingEntity>(root);var adapter=new HeroCastingModel<>(nativeModel);
        assertSame(nativeModel.head,adapter.head);assertSame(nativeModel.rightArm,adapter.rightArm);
        nativeModel.rightArm.xRot=.4F;
        HeroCastingModel.applyPose(adapter,0,1);
        assertEquals(-2.35F,nativeModel.rightArm.xRot,.001F);
        var armor=new HumanoidModel<LivingEntity>(LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE,0),64,64).bakeRoot());
        adapter.copyPropertiesTo(armor);assertEquals(adapter.rightArm.xRot,armor.rightArm.xRot);
        // A native setupAnim reset is immediately visible in the wrapper; it owns no separate pose.
        nativeModel.rightArm.xRot=.4F;HeroCastingModel.applyPose(adapter,1,0);
        assertEquals(.4F,adapter.rightArm.xRot);
    }
    @Test void nativePreparationStateIsNotLostBeforeSetup() {
        var root=LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE,0),64,64).bakeRoot();
        var nativeModel=new HumanoidModel<LivingEntity>(root) {
            @Override public void prepareMobModel(LivingEntity entity,float swing,float amount,float partial) {crouching=true;}
        };
        var adapter=new HeroCastingModel<>(nativeModel);
        adapter.prepareMobModel(null,0,0,0);
        assertTrue(adapter.crouching);
    }
    @Test void poseBlendsInAndReleaseFadesOut() {
        assertEquals(0,HeroCastVisuals.poseWeight(0,0));assertEquals(.5F,HeroCastVisuals.poseWeight(0,4));
        assertEquals(1,HeroCastVisuals.poseWeight(0,15));assertEquals(1,HeroCastVisuals.poseWeight(1,0));
        assertEquals(.5F,HeroCastVisuals.poseWeight(1,6));assertEquals(0,HeroCastVisuals.poseWeight(1,12));
        assertEquals(0,HeroCastVisuals.poseWeight(2,0));
    }
    @Test void heldWeaponTransformsFollowNativeHandsDuringCasting() {
        var root=LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE,0),64,64).bakeRoot();
        var nativeModel=new HumanoidModel<LivingEntity>(root);
        var adapter=new HeroCastingModel<>(nativeModel);
        for (int phase : new int[]{0,1}) {
            HeroCastingModel.applyPose(adapter,phase,1);
            for (var hand : net.minecraft.world.entity.HumanoidArm.values()) {
                var expected=new com.mojang.blaze3d.vertex.PoseStack();
                var actual=new com.mojang.blaze3d.vertex.PoseStack();
                nativeModel.translateToHand(hand,expected);
                adapter.translateToHand(hand,actual);
                assertTrue(expected.last().pose().equals(actual.last().pose(),0.00001F),hand+" phase "+phase);
                assertFalse(actual.last().pose().equals(new org.joml.Matrix4f(),0.00001F));
            }
        }
    }
}
