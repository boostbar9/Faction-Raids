package com.devfarinsky.siegeoverhaul.core;
import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Mob;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OlympianPolishTest extends MinecraftTestSupport {
    @Test void nativeSpawnNamesDoNotMaskHeroIdentityAndPlayerRenamesSurvive() {
        for(int role=10;role<=29;role++) {
            var mob=mock(Mob.class);var tag=new CompoundTag();when(mob.getPersistentData()).thenReturn(tag);
            when(mob.getCustomName()).thenReturn(Component.literal("Recruit"));
            HeroTraits.equip(mob,role,new SimpleContainer(8));
            final String name=CoreHiring.NAMES[role];
            verify(mob,atLeastOnce()).setCustomName(argThat(c->c.getString().equals(name)));
            clearInvocations(mob);when(mob.getCustomName()).thenReturn(Component.literal("My Champion"));
            HeroTraits.ensureOlympianIdentity(mob,role);verify(mob,never()).setCustomName(any());
            when(mob.getCustomName()).thenReturn(Component.literal("Bowman"));
            HeroTraits.ensureOlympianIdentity(mob,role);verify(mob).setCustomName(argThat(c->c.getString().equals(name)));
        }
    }
    @Test void factionDesignsUseDistinctVanillaBannerLayersEvenWithoutCustomRegistry() {
        var designs=new java.util.HashSet<String>();
        var valid=java.util.Set.of("bs","ts","mc","bo","sc","mr","bt","bts","cs","hh");
        for(var faction:com.devfarinsky.siegeoverhaul.items.FactionBanners.FactionId.values()) {
            var tag=new CompoundTag();com.devfarinsky.siegeoverhaul.items.FactionBanners.applyToBlockEntityTag(tag,faction);
            var layers=tag.getList("Patterns",10);assertTrue(layers.size()>=3);
            for(int i=0;i<layers.size();i++)assertTrue(valid.contains(layers.getCompound(i).getString("Pattern")));
            assertTrue(designs.add(tag.toString()));
        }
    }
}
