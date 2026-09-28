package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.items.OlympianWeaponSkins;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.*;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ItemPresentationTest extends MinecraftTestSupport {
    private static final StringSplitter SPLITTER=new StringSplitter((character,style)->6f);
    private static String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/resources/assets/siegeoverhaul/models/item/olympian",name+".json"));
    }
    @Test void everyActualGripMeetsTheSamePalmInBothHands() throws Exception {
        for(var patron:OlympianWeaponSkins.PATRONS)for(var shape:OlympianWeaponSkins.SHAPES) {
            String name=patron+"_"+shape,source=source(name);
            var model=BlockModel.fromString(source);
            var elements=JsonParser.parseString(source).getAsJsonObject().getAsJsonArray("elements");
            // Bows are held at their central riser; other models use the first handle cuboid.
            var grip=elements.get(0).getAsJsonObject();
            if(shape.startsWith("bow")) {
                for(var element:elements) {
                    var box=element.getAsJsonObject();
                    if(box.getAsJsonArray("from").toString().equals("[7,6,7]")
                            && box.getAsJsonArray("to").toString().equals("[9,10,9]"))grip=box;
                }
            }
            for(boolean left:new boolean[]{false,true}) {
                var point=new Vector4f();
                float[] coords=new float[3];
                for(int axis=0;axis<3;axis++)coords[axis]=(grip.getAsJsonArray("from").get(axis).getAsFloat()
                        +grip.getAsJsonArray("to").get(axis).getAsFloat())/32f-.5f;
                point.set(coords[0],coords[1],coords[2],1);
                var pose=new PoseStack();
                model.getTransforms().getTransform(left?ItemDisplayContext.THIRD_PERSON_LEFT_HAND:ItemDisplayContext.THIRD_PERSON_RIGHT_HAND).apply(left,pose);
                point.mul(pose.last().pose());
                assertEquals(0,point.x,0.00001,name+" left="+left);
                assertEquals(2f/16,point.y,0.00001,name+" left="+left);
                assertEquals(0,point.z,0.00001,name+" left="+left);
            }
        }
    }
    @Test void drawAndLoadedStatesDoNotJumpInsideEitherHand() throws Exception {
        for(var patron:OlympianWeaponSkins.PATRONS)for(var family:List.of("bow","crossbow")) {
            var idle=JsonParser.parseString(source(patron+"_"+family)).getAsJsonObject().getAsJsonObject("display");
            for(var shape:OlympianWeaponSkins.SHAPES)if(shape.startsWith(family+"_")) {
                var display=JsonParser.parseString(source(patron+"_"+shape)).getAsJsonObject().getAsJsonObject("display");
                for(var hand:List.of("thirdperson_righthand","thirdperson_lefthand"))assertEquals(idle.get(hand),display.get(hand));
            }
        }
    }
    @Test void longDescriptionsFitWidthAndKeepAllWords() {
        String text="Hold use to cast a storm that strikes visible siege enemies. Cooldown: 15s.";
        var lines=new ArrayList<Component>(List.of(Component.literal("Tempest"),Component.literal(text)));
        ItemTooltipLayout.wrap(lines,SPLITTER,90);
        assertEquals("Tempest",lines.get(0).getString());
        assertTrue(lines.size()>3);
        for(var line:lines.subList(1,lines.size()))assertTrue(SPLITTER.stringWidth(line)<=90);
        assertEquals(text,String.join(" ",lines.subList(1,lines.size()).stream().map(Component::getString).map(String::trim).toList()));
    }
    @Test void wrappingPreservesSectionBreaksColorsAndAttributes() {
        var heading=Component.literal("Ares' Blade").withStyle(ChatFormatting.GOLD);
        var detail=Component.literal("Damage ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("+5 Attack Damage").withStyle(ChatFormatting.BLUE));
        var lines=new ArrayList<Component>(List.of(heading,Component.empty(),detail));
        ItemTooltipLayout.wrap(lines,SPLITTER,60);
        assertSame(heading,lines.get(0));assertEquals("",lines.get(1).getString());
        var colors=new HashSet<Integer>();
        for(var line:lines.subList(2,lines.size()))line.visit((style,text)->{
            if(!text.isEmpty() && style.getColor()!=null)colors.add(style.getColor().getValue());return Optional.empty();
        },Style.EMPTY);
        assertEquals(Set.of(ChatFormatting.GRAY.getColor(),ChatFormatting.BLUE.getColor()),colors);
        assertTrue(String.join(" ",lines.stream().map(Component::getString).toList()).contains("+5"));
    }
    @Test void wrappingIsStableAcrossRepeatedTooltipEvents() {
        var lines=new ArrayList<Component>(List.of(Component.literal("Relic"),Component.literal("Use to reveal nearby siege enemies through walls.")));
        ItemTooltipLayout.wrap(lines,SPLITTER,96);
        var first=List.copyOf(lines);
        ItemTooltipLayout.wrap(lines,SPLITTER,96);
        assertEquals(first,lines);
    }
    @Test void ordinaryRenamedItemsDoNotUseSiegeFormatting() {
        var ordinary=new ItemStack(Items.DIAMOND_SWORD);ordinary.setHoverName(Component.literal("Olympian"));
        assertFalse(ItemTooltipLayout.applies(ordinary));
        for(var marker:List.of("SiegeOlympianPatron","SiegeHeroWeaponRole","SiegeSupplyPower")) {
            var siege=ordinary.copy();siege.getOrCreateTag().putString(marker,"test");
            assertTrue(ItemTooltipLayout.applies(siege));
        }
    }
}
