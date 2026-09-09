package com.devfarinsky.siegeoverhaul.compat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

class EpicKnightsCompatibilityTest extends MinecraftTestSupport {
    private Function<ResourceLocation,Item> outfit() {
        Map<String,Item> items = Map.of("kettlehat", Items.LEATHER_HELMET,
                "brigandine_chestplate", Items.LEATHER_CHESTPLATE,
                "chainmail_leggings", Items.LEATHER_LEGGINGS, "chainmail_boots", Items.LEATHER_BOOTS);
        return key -> key.getNamespace().equals("magistuarmory") ? items.get(key.getPath()) : null;
    }
    @Test void completeOutfitUsesCorrectSlotsAndPreservesIndependentTagsAndColor() {
        ItemStack fallback = new ItemStack(Items.IRON_HELMET);
        fallback.getOrCreateTag().putString("SiegeFaction", "iron");
        fallback.enchant(net.minecraft.world.item.enchantment.Enchantments.ALL_DAMAGE_PROTECTION, 1);
        var result = EpicKnightsCompatibility.armor(fallback, 0, EquipmentSlot.HEAD, 0x476B86, outfit());
        assertTrue(result.is(Items.LEATHER_HELMET));
        assertEquals("iron", result.getTag().getString("SiegeFaction")); assertTrue(result.isEnchanted());
        assertEquals(0x476B86, ((DyeableLeatherItem) result.getItem()).getColor(result));
        result.getTag().putString("SiegeFaction", "changed");
        assertEquals("iron", fallback.getTag().getString("SiegeFaction"));
    }
    @Test void missingOrWrongSlotPieceFallsBackAsWholeOutfit() {
        ItemStack fallback = new ItemStack(Items.IRON_HELMET);
        for (Item missing : new Item[]{null, Items.AIR, Items.IRON_SWORD, Items.IRON_HELMET}) {
            Function<ResourceLocation,Item> items = key -> key.getPath().equals("chainmail_boots") ? missing : outfit().apply(key);
            assertSame(fallback, EpicKnightsCompatibility.armor(fallback, 0, EquipmentSlot.HEAD, 1, items));
        }
    }
    @Test void absentModAndNonArmorSlotsKeepOriginalEquipment() {
        ItemStack fallback = new ItemStack(Items.IRON_HELMET);
        assertSame(fallback, EpicKnightsCompatibility.armor(fallback, 0, EquipmentSlot.HEAD, 1, key -> null));
        assertSame(fallback, EpicKnightsCompatibility.armor(fallback, 0, EquipmentSlot.MAINHAND, 1, outfit()));
        assertSame(fallback, EpicKnightsCompatibility.armor(fallback, 99, EquipmentSlot.HEAD, 1, outfit()));
    }
}
