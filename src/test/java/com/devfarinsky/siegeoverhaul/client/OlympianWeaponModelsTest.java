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
        var wrapper=new OlympianWeaponModels.SkinnedModel(original,Map.of("artemis_bow",idle,"artemis_bow_3",drawn,"artemis_crossbow_loaded",drawn));
        var bow=new ItemStack(Items.BOW);bow.getOrCreateTag().putString("SiegeOlympianPatron","artemis");
        assertSame(idle,wrapper.getOverrides().resolve(wrapper,bow,null,null,0));
        var user=mock(LivingEntity.class);when(user.isUsingItem()).thenReturn(true);when(user.getUseItem()).thenReturn(bow);
        when(user.getUseItemRemainingTicks()).thenReturn(bow.getUseDuration()-20);
        assertSame(drawn,wrapper.getOverrides().resolve(wrapper,bow,null,user,0));
        var crossbow=new ItemStack(Items.CROSSBOW);crossbow.getOrCreateTag().putString("SiegeOlympianPatron","artemis");CrossbowItem.setCharged(crossbow,true);
        assertSame(drawn,wrapper.getOverrides().resolve(wrapper,crossbow,null,null,0));
    }
    @Test void loadingArrowAndFireworkStatesAreDifferentAndDoNotModifyAmmo() {
        var idle=mock(BakedModel.class);var drawing=mock(BakedModel.class);
        var arrow=mock(BakedModel.class);var rocket=mock(BakedModel.class);
        var wrapper=new OlympianWeaponModels.SkinnedModel(mock(BakedModel.class),Map.of(
                "zeus_crossbow",idle,"zeus_crossbow_3",drawing,
                "zeus_crossbow_loaded",arrow,"zeus_crossbow_rocket",rocket));
        var crossbow=new ItemStack(Items.CROSSBOW);
        crossbow.getOrCreateTag().putString("SiegeOlympianPatron","zeus");
        crossbow.enchant(net.minecraft.world.item.enchantment.Enchantments.QUICK_CHARGE,2);
        var user=mock(LivingEntity.class);when(user.isUsingItem()).thenReturn(true);when(user.getUseItem()).thenReturn(crossbow);
        when(user.getUseItemRemainingTicks()).thenReturn(crossbow.getUseDuration()-CrossbowItem.getChargeDuration(crossbow));
        assertSame(drawing,wrapper.getOverrides().resolve(wrapper,crossbow,null,user,0));
        for(var projectile:new Item[]{Items.ARROW,Items.SPECTRAL_ARROW,Items.TIPPED_ARROW,Items.FIREWORK_ROCKET}) {
            var ammo=new net.minecraft.nbt.ListTag();ammo.add(new ItemStack(projectile).save(new net.minecraft.nbt.CompoundTag()));
            crossbow.getOrCreateTag().put("ChargedProjectiles",ammo);CrossbowItem.setCharged(crossbow,true);
            var saved=crossbow.save(new net.minecraft.nbt.CompoundTag());
            var loaded=ItemStack.of(saved);
            assertSame(projectile==Items.FIREWORK_ROCKET?rocket:arrow,wrapper.getOverrides().resolve(wrapper,loaded,null,null,0));
            assertEquals(saved,loaded.save(new net.minecraft.nbt.CompoundTag()));
        }
        CrossbowItem.setCharged(crossbow,false);crossbow.getTag().remove("ChargedProjectiles");
        assertSame(idle,wrapper.getOverrides().resolve(wrapper,crossbow,null,null,0));
    }
}
