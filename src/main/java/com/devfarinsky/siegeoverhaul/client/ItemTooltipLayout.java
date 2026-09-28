package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** Wrap Siege descriptions without flattening colors, translated text or vanilla attributes. */
@Mod.EventBusSubscriber(modid=SiegeOverhaul.MOD_ID,value=Dist.CLIENT)
public final class ItemTooltipLayout {
    private ItemTooltipLayout() {}
    static boolean applies(ItemStack stack) {
        var id=ForgeRegistries.ITEMS.getKey(stack.getItem());
        if(id!=null && id.getNamespace().equals(SiegeOverhaul.MOD_ID))return true;
        var tag=stack.getTag();
        return tag!=null && (tag.contains("SiegeOlympianPatron") || tag.contains("SiegeHeroWeaponRole")
                || tag.contains("SiegeSupplyPower"));
    }
    @SubscribeEvent public static void tooltip(ItemTooltipEvent event) {
        if(!applies(event.getItemStack()))return;
        var mc=Minecraft.getInstance();if(mc.font==null)return;
        wrap(event.getToolTip(),mc.font.getSplitter(),Math.max(80,Math.min(260,mc.getWindow().getGuiScaledWidth()-32)));
    }
    static void wrap(List<Component> lines,StringSplitter splitter,int width) {
        var output=new ArrayList<Component>();
        for(int i=0;i<lines.size();i++) {
            var line=lines.get(i);
            // Let Minecraft retain the item-name/rarity heading and empty section separators.
            if(i==0 || line.getString().isEmpty() || splitter.stringWidth(line)<=Math.max(40,width)) {output.add(line);continue;}
            for(var part:splitter.splitLines(line,Math.max(40,width),Style.EMPTY)) {
                var wrapped=Component.empty();
                part.visit((style,text)->{if(!text.isEmpty())wrapped.append(Component.literal(text).setStyle(style));return Optional.empty();},Style.EMPTY);
                output.add(wrapped);
            }
        }
        lines.clear();lines.addAll(output);
    }
}
