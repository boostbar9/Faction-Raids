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
    @Test void allDeclaredModelsParseAndHaveBoundedGeometryAndTextureReferences() throws Exception {
        for(var patron:OlympianWeaponSkins.PATRONS)for(var shape:OlympianWeaponSkins.SHAPES) {
            var path=Path.of("src/main/resources/assets/siegeoverhaul/models/item/olympian",patron+"_"+shape+".json");
            assertNotNull(net.minecraft.client.renderer.block.model.BlockModel.fromString(Files.readString(path)));
            var json=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            var textures=json.getAsJsonObject("textures");
            for(var element:json.getAsJsonArray("elements")) {
                var box=element.getAsJsonObject();
                for(int axis=0;axis<3;axis++) {
                    double from=box.getAsJsonArray("from").get(axis).getAsDouble(),to=box.getAsJsonArray("to").get(axis).getAsDouble();
                    assertTrue(from>=-16 && to<=32 && to>from,path.toString());
                }
                for(var face:box.getAsJsonObject("faces").entrySet())
                    assertTrue(textures.has(face.getValue().getAsJsonObject().get("texture").getAsString().substring(1)));
            }
            for(var texture:textures.entrySet())assertTrue(texture.getValue().getAsString().startsWith("minecraft:block/"));
            assertTrue(json.getAsJsonObject("display").has("firstperson_righthand"));
        }
    }
    @Test void crossbowStringsStayAttachedThroughoutLoading() throws Exception {
        for (var patron : OlympianWeaponSkins.PATRONS) {
            for (var suffix : new String[]{"", "_1", "_2", "_3", "_loaded", "_rocket"}) {
                var path = Path.of("src/main/resources/assets/siegeoverhaul/models/item/olympian",
                        patron + "_crossbow" + suffix + ".json");
                var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                double angle = suffix.equals("_2") ? 22.5
                        : suffix.equals("_3") || suffix.equals("_loaded") || suffix.equals("_rocket") ? 45 : 0;
                double joinZ = 9 - 6 * Math.tan(Math.toRadians(angle));
                int strings = 0;
                for (var element : json.getAsJsonArray("elements")) {
                    var box = element.getAsJsonObject();
                    if (!box.has("rotation") || !box.getAsJsonObject("rotation").get("axis").getAsString().equals("y")
                            || !box.getAsJsonObject("faces").getAsJsonObject("north").get("texture").getAsString().equals("#string")) continue;
                    var rotation = box.getAsJsonObject("rotation");
                    var origin = rotation.getAsJsonArray("origin");
                    double ox = origin.get(0).getAsDouble(), oz = origin.get(2).getAsDouble();
                    double radians = Math.toRadians(rotation.get("angle").getAsDouble());
                    boolean left = ox < 8;
                    for (int end = 0; end < 2; end++) {
                        double x = box.getAsJsonArray(end == 0 ? "from" : "to").get(0).getAsDouble();
                        double z = (box.getAsJsonArray("from").get(2).getAsDouble()
                                + box.getAsJsonArray("to").get(2).getAsDouble()) / 2;
                        double worldX = ox + Math.cos(radians) * (x - ox) + Math.sin(radians) * (z - oz);
                        double worldZ = oz - Math.sin(radians) * (x - ox) + Math.cos(radians) * (z - oz);
                        boolean tip = left ? end == 0 : end == 1;
                        assertEquals(tip ? (left ? 2 : 14) : 8, worldX, 0.00001, path.toString());
                        assertEquals(tip ? 9 : joinZ, worldZ, 0.00001, path.toString());
                    }
                    strings++;
                }
                assertEquals(2, strings, path.toString());
            }
        }
    }

}
