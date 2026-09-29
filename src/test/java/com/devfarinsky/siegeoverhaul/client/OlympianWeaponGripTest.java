package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OlympianWeaponGripTest extends MinecraftTestSupport {
    @Test void skinRetainsItsArtButUsesVanillaHandPoses() {
        BakedModel skin=mock(BakedModel.class),nativeBow=mock(BakedModel.class);
        var model=new OlympianWeaponModels.NativeGripModel(skin,nativeBow);
        PoseStack pose=new PoseStack();
        assertSame(model,model.applyTransform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,pose,false));
        assertSame(model,model.applyTransform(ItemDisplayContext.THIRD_PERSON_LEFT_HAND,pose,true));
        assertSame(model,model.applyTransform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,pose,false));
        verify(nativeBow).applyTransform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,pose,false);
        verify(nativeBow).applyTransform(ItemDisplayContext.THIRD_PERSON_LEFT_HAND,pose,true);
        verify(nativeBow).applyTransform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,pose,false);
        verify(skin,never()).applyTransform(any(),any(),anyBoolean());
        model.getQuads(null,null,net.minecraft.util.RandomSource.create());
        verify(skin).getQuads(isNull(),isNull(),any());
    }
}
