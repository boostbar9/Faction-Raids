package com.devfarinsky.siegeoverhaul.items;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreativeCatalogTest extends MinecraftTestSupport {
    private OlympianRelicRegistryFixture relicRegistry;
    @org.junit.jupiter.api.BeforeEach void bindRelicRegistry() { relicRegistry = new OlympianRelicRegistryFixture(); }
    @org.junit.jupiter.api.AfterEach void closeRelicRegistry() { relicRegistry.close(); }

    @Test void catalogHasAllRewardVariantsAndOnlyFreshSingleUniqueStacks() {
        var entries = CreativeCatalog.entries(List.of(Items.BOOK, Items.BOOK));
        assertFalse(entries.isEmpty());
        for (int i=0;i<entries.size();i++) {
            assertEquals(1,entries.get(i).getCount());
            for(int j=0;j<i;j++) assertFalse(ItemStack.isSameItemSameTags(entries.get(i),entries.get(j)));
        }
        for(var tier:LootBoxItem.Tier.values()) {
            for(int i:OlympianLoot.availableArmory(tier)) assertPresent(entries,OlympianLoot.armory(tier,i));
            assertPresent(entries,OlympianLoot.provisions(tier));
            assertPresent(entries,OlympianLoot.supplies(tier,OlympianLoot.AMMUNITION,OlympianLoot.armory(tier,4)));
            for(int i:OlympianLoot.AVAILABLE_SUPPLIES) assertPresent(entries,OlympianLoot.supplies(tier,i));
        }
        entries.get(0).getOrCreateTag().putBoolean("mutated",true);
        assertFalse(CreativeCatalog.entries(List.of(Items.BOOK)).get(0).hasTag());
    }
    @Test void catalogEquipmentMatchesWhatEveryHeroActuallyReceives() {
        var entries=CreativeCatalog.entries(List.of());
        for(int role=10;role<=29;role++) {
            var mob=mock(Mob.class);when(mob.getPersistentData()).thenReturn(new CompoundTag());
            var inventory=new SimpleContainer(8);HeroTraits.equip(mob,role,inventory);
            for(int slot=0;slot<6;slot++) if(!inventory.getItem(slot).isEmpty()) assertPresent(entries,inventory.getItem(slot));
        }
    }
    @Test void referenceBookCoversEveryHeroWithValidReadablePages() {
        var book=CreativeCatalog.abilityGuide();
        assertTrue(book.is(Items.WRITTEN_BOOK));
        assertTrue(WrittenBookItem.makeSureTagIsValid(book.getTag()));
        var pages=book.getTag().getList("pages",8);
        assertEquals(21,pages.size());
        for(int role=10;role<=29;role++) {
            String text=Component.Serializer.fromJson(pages.getString(role-9)).getString();
            assertTrue(text.contains(CoreHiring.NAMES[role]));assertTrue(text.contains(HeroTraits.description(role)));
        }
    }
    private static void assertPresent(List<ItemStack> entries,ItemStack stack) {
        assertTrue(entries.stream().anyMatch(e->ItemStack.isSameItemSameTags(e,stack)),stack.getHoverName().getString());
    }
}
