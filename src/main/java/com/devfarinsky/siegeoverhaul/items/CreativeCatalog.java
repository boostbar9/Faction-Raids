package com.devfarinsky.siegeoverhaul.items;

import com.devfarinsky.siegeoverhaul.core.CoreHiring;
import com.devfarinsky.siegeoverhaul.core.HeroTraits;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import java.util.*;

/** Rebuildable catalog: no shared mutable stacks and no duplicate creative entries. */
public final class CreativeCatalog {
    private CreativeCatalog() {}

    public static List<ItemStack> entries(Collection<Item> registered) {
        var result = new ArrayList<ItemStack>();
        for (var item : registered) add(result, new ItemStack(item));
        add(result, abilityGuide());
        for (int role = CoreHiring.HERO_ID_MIN; role <= CoreHiring.HERO_ID_MAX; role++) {
            var equipment = HeroTraits.equipment(role);
            for (int slot = 0; slot < equipment.getContainerSize(); slot++) add(result, equipment.getItem(slot));
        }
        for (var tier : LootBoxItem.Tier.values()) {
            for (int entry = 0; entry < OlympianLoot.ARMORY_SIZE; entry++) add(result, OlympianLoot.armory(tier, entry));
            add(result, OlympianLoot.provisions(tier));
            add(result, OlympianLoot.supplies(tier, OlympianLoot.AMMUNITION, OlympianLoot.armory(tier, 4)));
            for (int entry = 0; entry < OlympianLoot.SUPPLY_TYPES; entry++) add(result, OlympianLoot.supplies(tier, entry));
        }
        for (var faction : FactionBanners.FactionId.values()) add(result, FactionBanners.itemStackFor(faction));
        return result;
    }

    private static void add(List<ItemStack> entries, ItemStack source) {
        if (source == null || source.isEmpty()) return;
        var stack = source.copy();
        stack.setCount(1); // CreativeModeTab rejects stacks with any other count.
        if (entries.stream().noneMatch(existing -> ItemStack.isSameItemSameTags(existing, stack))) entries.add(stack);
    }

    public static ItemStack abilityGuide() {
        var book = new ItemStack(Items.WRITTEN_BOOK);
        var tag = book.getOrCreateTag();
        tag.putString("title", "The Olympian Arts");
        tag.putString("author", "Siege Overhaul");
        var pages = new ListTag();
        page(pages, "The Olympian Arts\n\nHero powers belong to the hero, not the weapon. Their equipment retains its enchantments when used by a player.\n\nCreative spellbooks let you cast Tempest, Inferno and Undertow against siege enemies. Hold use for one second; release early to cancel.");
        for (int role = CoreHiring.HERO_ID_MIN; role <= CoreHiring.HERO_ID_MAX; role++)
            page(pages, CoreHiring.NAMES[role] + "\n\n" + HeroTraits.description(role) + ".\n\nWeapon: " + HeroTraits.weaponName(role) + ".\n\nUse the matching spawn egg to summon this hero. Hire and command them through Villager Recruits.");
        tag.put("pages", pages);
        return book;
    }

    private static void page(ListTag pages, String text) {
        pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(text))));
    }
}
