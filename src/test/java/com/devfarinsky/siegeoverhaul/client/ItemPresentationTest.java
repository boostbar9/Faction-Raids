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
