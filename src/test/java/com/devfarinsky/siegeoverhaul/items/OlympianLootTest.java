package com.devfarinsky.siegeoverhaul.items;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.core.CoreLoot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OlympianLootTest extends MinecraftTestSupport {
    @ParameterizedTest @EnumSource(LootBoxItem.Tier.class)
    void everyPatronHasCompatibleUsefulEnchantsAndPersistentIdentity(LootBoxItem.Tier tier) {
        var names = new HashSet<String>();
        for (int pick = 0; pick < OlympianLoot.ARMORY_SIZE; pick++) {
            ItemStack item = OlympianLoot.armory(tier, pick);
            assertTrue(names.add(item.getHoverName().getString()));
            assertEquals(1, item.getCount());
            assertTrue(item.isDamageableItem());
            assertEquals(0, item.getDamageValue());
            assertFalse(item.getTag().getString("SiegeOlympianPatron").isBlank());
            assertEquals(2, item.getTagElement("display").getList("Lore", Tag.TAG_STRING).size());
            var enchants = EnchantmentHelper.getEnchantments(item);
            assertFalse(enchants.isEmpty());
            for (var e : enchants.entrySet()) {
                assertTrue(e.getKey().canEnchant(item), item.getHoverName().getString() + ": " + e.getKey());
                assertTrue(e.getValue() >= e.getKey().getMinLevel() && e.getValue() <= e.getKey().getMaxLevel());
                for (Enchantment other : enchants.keySet()) if (other != e.getKey())
                    assertTrue(e.getKey().isCompatibleWith(other), item.getHoverName().getString() + " incompatible enchants");
            }
            assertTrue(ItemStack.matches(item, ItemStack.of(item.save(new CompoundTag()))));
            if (tier.ordinal() >= 2) {
                assertEquals(1, EnchantmentHelper.getItemEnchantmentLevel(
                        pick == 4 ? Enchantments.INFINITY_ARROWS : Enchantments.MENDING, item));
            }
        }
        assertEquals(12, names.size());
    }

    @ParameterizedTest @EnumSource(LootBoxItem.Tier.class)
    void boxRollsAlwaysIncludeEquipmentProvisionsAndDistinctUsefulSupplies(LootBoxItem.Tier tier) {
        for (int seed = 0; seed < 200; seed++) {
            var rewards = LootBoxItem.roll(RandomSource.create(seed), tier);
            var repeat = LootBoxItem.roll(RandomSource.create(seed), tier);
            assertTrue(rewards.size() >= 3 + tier.ordinal() && rewards.size() <= 4 + tier.ordinal());
            assertTrue(rewards.get(0).isEnchanted());
            assertTrue(ItemStack.matches(OlympianLoot.provisions(tier), rewards.get(1)));
            var supplyNames = new HashSet<String>();
            for (int i = 0; i < rewards.size(); i++) {
                ItemStack stack = rewards.get(i);
                assertTrue(ItemStack.matches(stack, repeat.get(i)));
                assertTrue(stack.getCount() > 0 && stack.getCount() <= stack.getMaxStackSize());
                if (i >= 2) assertTrue(supplyNames.add(stack.getHoverName().getString()));
            }
        }
    }

    @ParameterizedTest @EnumSource(LootBoxItem.Tier.class)
    void potionsAndArrowsCarryRealEffects(LootBoxItem.Tier tier) {
        for (int pick : new int[]{4, 5, 6}) {
            ItemStack item = OlympianLoot.supplies(tier, pick);
            assertNotEquals(Potions.EMPTY, PotionUtils.getPotion(item));
            assertFalse(PotionUtils.getMobEffects(item).isEmpty());
        }
        assertEquals(tier.ordinal() >= 2 ? Potions.LONG_SLOWNESS : Potions.SLOWNESS,
                PotionUtils.getPotion(OlympianLoot.supplies(tier, 6)));
    }

    @Test void signaturePowersAndMaterialProgressionMatchTheirNames() {
        var rare = LootBoxItem.Tier.RARE;
        assertEquals(1, EnchantmentHelper.getItemEnchantmentLevel(Enchantments.CHANNELING, OlympianLoot.armory(rare, 0)));
        assertEquals(3, EnchantmentHelper.getItemEnchantmentLevel(Enchantments.LOYALTY, OlympianLoot.armory(rare, 0)));
        assertEquals(2, EnchantmentHelper.getItemEnchantmentLevel(Enchantments.RIPTIDE, OlympianLoot.armory(rare, 1)));
        assertEquals(4, EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FALL_PROTECTION, OlympianLoot.armory(rare, 6)));
        assertTrue(OlympianLoot.armory(LootBoxItem.Tier.COMMON, 3).is(Items.IRON_SWORD));
        assertTrue(OlympianLoot.armory(LootBoxItem.Tier.UNCOMMON, 3).is(Items.DIAMOND_SWORD));
        assertTrue(OlympianLoot.armory(LootBoxItem.Tier.EPIC, 3).is(Items.NETHERITE_SWORD));
        assertTrue(OlympianLoot.provisions(LootBoxItem.Tier.EPIC).is(Items.ENCHANTED_GOLDEN_APPLE));
        assertTrue(OlympianLoot.armory(rare, 9).getTag().contains("Trim"));
    }

    @Test void partialInsertionDropsOnlyTheRemainderAndLeavesOriginalUntouched() {
        var player = player();
        var inv = player.getInventory();
        for (int i = 0; i < 36; i++) inv.items.set(i, new ItemStack(Items.STONE, 64));
        var reward = OlympianLoot.supplies(LootBoxItem.Tier.RARE, 0);
        var partial = reward.copy(); partial.setCount(60);
        inv.items.set(35, partial);
        LootBoxItem.deliver(player, List.of(reward));
        var dropped = ArgumentCaptor.forClass(ItemStack.class);
        verify(player).drop(dropped.capture(), eq(false));
        assertEquals(64, inv.items.get(35).getCount());
        assertEquals(reward.getCount() - 4, dropped.getValue().getCount());
        assertEquals(32, reward.getCount());
        assertTrue(ItemStack.isSameItemSameTags(reward, dropped.getValue()));
    }

    @Test void fullInventoryDropsEquipmentExactlyOnceAndStorageSlotsAreUsed() {
        var player = player();
        var inv = player.getInventory();
        for (int i = 0; i < 36; i++) inv.items.set(i, new ItemStack(Items.STONE, 64));
        var reward = OlympianLoot.armory(LootBoxItem.Tier.EPIC, 9);
        LootBoxItem.deliver(player, List.of(reward));
        var dropped = ArgumentCaptor.forClass(ItemStack.class);
        verify(player).drop(dropped.capture(), eq(false));
        assertTrue(ItemStack.matches(reward, dropped.getValue()));
        clearInvocations(player);
        inv.items.set(35, ItemStack.EMPTY);
        LootBoxItem.deliver(player, List.of(reward));
        verify(player, never()).drop(any(ItemStack.class), anyBoolean());
        assertTrue(ItemStack.matches(reward, inv.items.get(35)));
    }

    @Test void openingLastBoxFreesItsSlotForTheEquipmentBeforeOverflow() {
        var player = player();
        var inv = player.getInventory();
        for (int i = 0; i < 36; i++) inv.items.set(i, new ItemStack(Items.STONE, 64));
        // A vanilla stand-in avoids registering a second loot-box item during plain JUnit.
        var held = new ItemStack(Items.CHEST);
        inv.items.set(0, held);
        when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(held);
        var level = mock(ServerLevel.class);
        when(level.getRandom()).thenReturn(RandomSource.create(42));
        new LootBoxItem(LootBoxItem.Tier.RARE).use(level, player, InteractionHand.MAIN_HAND);
        assertTrue(held.isEmpty());
        assertTrue(inv.items.get(0).isEnchanted());
        var dropped = ArgumentCaptor.forClass(ItemStack.class);
        verify(player, atLeastOnce()).drop(dropped.capture(), eq(false));
        assertTrue(dropped.getAllValues().stream().noneMatch(ItemStack::isEnchanted));
    }

    @Test void pricesFloorsAndSealedTooltipStayHonest() {
        assertEquals(16, CoreLoot.price(0)); assertEquals(48, CoreLoot.price(1)); assertEquals(96, CoreLoot.price(2));
        for (int roll = 0; roll < 100; roll++) {
            assertEquals(Math.max(1, CoreLoot.tier(roll)), CoreLoot.tierForBox(1, roll));
            assertEquals(Math.max(2, CoreLoot.tier(roll)), CoreLoot.tierForBox(2, roll));
        }
        var tooltip = new ArrayList<net.minecraft.network.chat.Component>();
        new LootBoxItem(LootBoxItem.Tier.RARE).appendHoverText(ItemStack.EMPTY, null, tooltip, TooltipFlag.NORMAL);
        assertTrue(tooltip.stream().anyMatch(c -> c.getString().contains("equipment and supplies")));
        assertFalse(tooltip.toString().contains("Zeus"));
        assertFalse(tooltip.toString().contains("Trident"));
    }

    private ServerPlayer player() {
        var player = mock(ServerPlayer.class);
        when(player.getInventory()).thenReturn(new Inventory(player));
        when(player.getAbilities()).thenReturn(new Abilities());
        return player;
    }
}
