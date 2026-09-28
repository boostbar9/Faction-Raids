package com.devfarinsky.siegeoverhaul.items;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.core.HeroTraits;
import com.google.gson.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OlympianWeaponSkinsTest extends MinecraftTestSupport {
    @Test void everyHeroMainWeaponHasAValidSkinWithoutChangingItsItem() {
        for(int role=10;role<=29;role++) {
            var hero=mock(Mob.class);when(hero.getPersistentData()).thenReturn(new CompoundTag());
            var inventory=new SimpleContainer(8);HeroTraits.equip(hero,role,inventory);
            var stack=inventory.getItem(5);
            assertFalse(OlympianWeaponSkins.patron(stack).isEmpty(),"role "+role);
            assertFalse(OlympianWeaponSkins.shape(stack).isEmpty(),"role "+role);
            assertEquals(HeroTraits.weaponName(role),stack.getHoverName().getString());
            assertTrue(stack.isEnchanted());
        }
    }
    @Test void identityPreservesSavedLootAndResourcePackOverrides() {
        var loot=new ItemStack(Items.DIAMOND_SWORD);loot.setDamageValue(42);
        loot.enchant(Enchantments.SHARPNESS,4);loot.setHoverName(Component.literal("Ares' Blade"));
        loot.getOrCreateTag().putString("SiegeOlympianPatron","ares");
        var saved=loot.save(new CompoundTag());var loaded=ItemStack.of(saved);
        assertEquals("ares",OlympianWeaponSkins.patron(loaded));
        assertEquals(saved,loaded.save(new CompoundTag()));
        OlympianWeaponSkins.identifyHero(loaded,11);
        assertEquals("athena",OlympianWeaponSkins.patron(loaded));
        assertEquals(42,loaded.getDamageValue());assertTrue(loaded.isEnchanted());
        loaded.getOrCreateTag().putInt("CustomModelData",7);
        assertEquals("",OlympianWeaponSkins.patron(loaded));
        assertEquals("",OlympianWeaponSkins.patron(new ItemStack(Items.DIAMOND_SWORD)));
        loaded.getTag().remove("CustomModelData");loaded.getTag().putInt(OlympianWeaponSkins.HERO_ROLE,1000);
        assertEquals("",OlympianWeaponSkins.patron(loaded));
    }
    @Test void skinsUseVanillaParentsAndReadableOriginalTextures() throws Exception {
        for(var patron:OlympianWeaponSkins.PATRONS)for(var shape:OlympianWeaponSkins.SHAPES) {
            var path=Path.of("src/main/resources/assets/siegeoverhaul/models/item/olympian",patron+"_"+shape+".json");
            var json=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            assertNotNull(net.minecraft.client.renderer.block.model.BlockModel.fromString(Files.readString(path)));
            String family=shape.startsWith("crossbow")?"crossbow":shape.startsWith("bow")?"bow":"handheld";
            assertEquals("minecraft:item/"+family,json.get("parent").getAsString());
            assertFalse(json.has("elements"));assertFalse(json.has("display"),"Use vanilla grip, scale and both hand poses");
            if(!family.equals("handheld"))assertTrue(json.getAsJsonArray("overrides").isEmpty());
            var texture=javax.imageio.ImageIO.read(Path.of("src/main/resources/assets/siegeoverhaul/textures/item/olympian",patron+"_"+shape+".png").toFile());
            assertEquals(32,texture.getWidth());assertEquals(32,texture.getHeight());
            int visible=0;for(int x=0;x<32;x++)for(int y=0;y<32;y++)if((texture.getRGB(x,y)>>>24)>0)visible++;
            assertTrue(visible>35 && visible<600,path.toString());
        }
    }
}
