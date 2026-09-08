package com.devfarinsky.siegeoverhaul.core;
import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.items.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class FactionUniformsTest extends MinecraftTestSupport {
    @Test void shieldHeraldryPreservesDurabilityAndEnchantments() {
        var shield=new net.minecraft.world.item.ItemStack(Items.SHIELD);shield.setDamageValue(97);
        shield.enchant(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING,2);
        for(var faction:FactionBanners.FactionId.values()) {
            FactionUniforms.decorateShield(shield,faction.id);
            assertEquals(faction.baseColor.getId(),shield.getTag().getCompound("BlockEntityTag").getInt("Base"));
            assertEquals(97,shield.getDamageValue());assertTrue(shield.isEnchanted());
        }
    }
    @Test void allFactionsHaveDistinctChestColorsAndAllRanksDistinctHelmetColors() {
        Set<String> colors=new HashSet<>();
        for(var faction:FactionBanners.FactionId.values()) {
            var chest=FactionUniforms.armor(faction.id,"captain",EquipmentSlot.CHEST);
            colors.add(chest.getTag().getCompound("Trim").getString("material"));
            assertEquals(faction.id,chest.getTag().getString(FactionUniforms.FACTION));
        }
        assertEquals(5,colors.size()); colors.clear();
        for(String role:List.of("breacher","guard","captain","commander"))
            colors.add(FactionUniforms.armor("blackbay_reavers",role,EquipmentSlot.HEAD).getTag().getCompound("Trim").getString("material"));
        assertEquals(4,colors.size());
    }
    @Test void bannersDoNotReplaceHelmetsAndWildsHaveDyedArmor() {
        assertEquals(Items.DIAMOND_HELMET,FactionUniforms.armor("blackbay_reavers","commander",EquipmentSlot.HEAD).getItem());
        var wild=FactionUniforms.armor("wilds_marauders","marksman",EquipmentSlot.CHEST);
        assertEquals(Items.LEATHER_CHESTPLATE,wild.getItem()); assertTrue(wild.getTag().getCompound("display").contains("color"));
    }
}
