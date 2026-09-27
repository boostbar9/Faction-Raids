package com.devfarinsky.siegeoverhaul.client;
import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OlympianWeaponModelsTest extends MinecraftTestSupport {
    @Test void ordinaryAndCustomModelDataItemsKeepTheirOriginalOverride() {
        var original=mock(BakedModel.class);var nativeOverrides=mock(ItemOverrides.class);var nativeResult=mock(BakedModel.class);
        when(original.getOverrides()).thenReturn(nativeOverrides);
        var stack=new ItemStack(Items.DIAMOND_SWORD);
        when(nativeOverrides.resolve(original,stack,null,null,0)).thenReturn(nativeResult);
        var wrapper=new OlympianWeaponModels.SkinnedModel(original,Map.of());
        assertSame(nativeResult,wrapper.getOverrides().resolve(wrapper,stack,null,null,0));
        stack.getOrCreateTag().putString("SiegeOlympianPatron","ares");stack.getTag().putInt("CustomModelData",8);
        assertSame(nativeResult,wrapper.getOverrides().resolve(wrapper,stack,null,null,0));
    }
    @Test void bowDrawAndChargedCrossbowSelectDistinctModels() {
        var original=mock(BakedModel.class);var idle=mock(BakedModel.class);var drawn=mock(BakedModel.class);
        var wrapper=new OlympianWeaponModels.SkinnedModel(original,Map.of("artemis_bow",idle,"artemis_bow_3",drawn,"artemis_crossbow_3",drawn));
        var bow=new ItemStack(Items.BOW);bow.getOrCreateTag().putString("SiegeOlympianPatron","artemis");
        assertSame(idle,wrapper.getOverrides().resolve(wrapper,bow,null,null,0));
        var user=mock(LivingEntity.class);when(user.isUsingItem()).thenReturn(true);when(user.getUseItem()).thenReturn(bow);
        when(user.getUseItemRemainingTicks()).thenReturn(bow.getUseDuration()-20);
        assertSame(drawn,wrapper.getOverrides().resolve(wrapper,bow,null,user,0));
        var crossbow=new ItemStack(Items.CROSSBOW);crossbow.getOrCreateTag().putString("SiegeOlympianPatron","artemis");CrossbowItem.setCharged(crossbow,true);
        assertSame(drawn,wrapper.getOverrides().resolve(wrapper,crossbow,null,null,0));
    }
}
