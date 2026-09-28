package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.items.OlympianWeaponSkins;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.HashSet;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class OlympianWeaponPresentationTest extends com.devfarinsky.siegeoverhaul.MinecraftTestSupport {
    private static JsonObject model(String patron, String shape) throws Exception {
        return JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/assets/siegeoverhaul/models/item/olympian", patron+"_"+shape+".json"))).getAsJsonObject();
    }
    private static BufferedImage texture(String patron,String shape) throws Exception {
        return ImageIO.read(Path.of("src/main/resources/assets/siegeoverhaul/textures/item/olympian",patron+"_"+shape+".png").toFile());
    }
    @Test void inventorySilhouettesRetainATransparentSlotMargin() throws Exception {
        for(var patron:OlympianWeaponSkins.PATRONS)for(var shape:OlympianWeaponSkins.SHAPES) {
            var image=texture(patron,shape);
            for(int i=0;i<32;i++) {
                assertEquals(0,image.getRGB(i,0)>>>24);assertEquals(0,image.getRGB(i,31)>>>24);
                assertEquals(0,image.getRGB(0,i)>>>24);assertEquals(0,image.getRGB(31,i)>>>24);
            }
        }
    }
    @Test void animationStatesInheritTheSameVanillaHandAlignment() throws Exception {
        for(var patron:OlympianWeaponSkins.PATRONS)for(var family:new String[]{"bow","crossbow"}) {
            var parent=model(patron,family).get("parent");
            for(var shape:OlympianWeaponSkins.SHAPES)if(shape.startsWith(family+"_")) {
                var state=model(patron,shape);
                assertEquals(parent,state.get("parent"));assertFalse(state.has("display"));
                assertTrue(state.getAsJsonArray("overrides").isEmpty(),"Do not redirect drawn skins to vanilla models");
            }
        }
    }
    @Test void pullingAndLoadedStatesHaveVisibleChanges() throws Exception {
        for(var patron:OlympianWeaponSkins.PATRONS)for(var family:new String[]{"bow","crossbow"}) {
            var stages=new HashSet<String>();
            for(var shape:OlympianWeaponSkins.SHAPES)if(shape.equals(family)||shape.startsWith(family+"_")) {
                var image=texture(patron,shape);
                assertTrue(stages.add(java.util.Arrays.toString(image.getRGB(0,0,32,32,null,0,32))),patron+"_"+shape);
            }
        }
    }
    @Test void deityPalettesRemainDistinctAndTexturesUseABoundedResolution() throws Exception {
        for(var shape:OlympianWeaponSkins.SHAPES) {
            var skins=new HashSet<String>();
            for(var patron:OlympianWeaponSkins.PATRONS) {
                var image=texture(patron,shape);
                assertEquals(32,image.getWidth());assertEquals(32,image.getHeight());
                assertTrue(skins.add(java.util.Arrays.toString(image.getRGB(0,0,32,32,null,0,32))),patron+"_"+shape);
            }
        }
    }
}
