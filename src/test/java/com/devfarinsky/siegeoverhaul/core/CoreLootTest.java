package com.devfarinsky.siegeoverhaul.core;
import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.server.level.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CoreLootTest extends MinecraftTestSupport {
    @Test void purchaseChatDoesNotSpoilAnyRewardBeforeTheReveal() {
        for (int box=0;box<3;box++) for (int roll:new int[]{0,50,80,95}) {
            var player=mock(ServerPlayer.class);var level=mock(ServerLevel.class);var inv=new Inventory(player);
            var random=mock(net.minecraft.util.RandomSource.class);
            when(player.getInventory()).thenReturn(inv);when(player.serverLevel()).thenReturn(level);
            when(player.getPersistentData()).thenReturn(new net.minecraft.nbt.CompoundTag());
            when(player.getRandom()).thenReturn(random);when(random.nextInt(100)).thenReturn(roll);
            inv.items.set(0,new ItemStack(Items.EMERALD,64));inv.items.set(1,new ItemStack(Items.EMERALD,64));
            var receipt=CoreLoot.purchaseWithReceipt(player,box);assertNotNull(receipt);
            var message=org.mockito.ArgumentCaptor.forClass(net.minecraft.network.chat.Component.class);
            verify(player).sendSystemMessage(message.capture());
            assertEquals("Opening "+CoreLoot.NAMES[box]+"... Reward secured in your inventory.",message.getValue().getString());
            assertEquals(128-CoreLoot.price(box),inv.countItem(Items.EMERALD));
            assertTrue(inv.items.stream().anyMatch(s->ItemStack.isSameItemSameTags(s,receipt.prize())));
        }
    }
    @Test void exactAdvertisedOddsAndInvalidRolls() {
        int[] counts=new int[4];
        for(int roll=0;roll<100;roll++) {
            var item=CoreLoot.reward(0,roll).getItem();counts[item==Items.GOLDEN_APPLE?0:item==Items.ARROW?1:item==Items.DIAMOND?2:3]++;
            for(int box=0;box<3;box++)assertFalse(CoreLoot.reward(box,roll).isEmpty());
        }
        assertArrayEquals(new int[]{50,30,15,5},counts);
        assertThrows(IllegalArgumentException.class,()->CoreLoot.reward(3,0));
        assertThrows(IllegalArgumentException.class,()->CoreLoot.reward(0,100));
    }
    @Test void paymentAndDeliveryOccurOnceAndFullInventoryIsNotCharged() {
        var player=mock(ServerPlayer.class);var level=mock(ServerLevel.class);var inv=new Inventory(player);
        when(player.getInventory()).thenReturn(inv);when(player.serverLevel()).thenReturn(level);
        when(player.getPersistentData()).thenReturn(new net.minecraft.nbt.CompoundTag());
        when(player.getRandom()).thenReturn(net.minecraft.util.RandomSource.create(1));
        inv.items.set(0,new ItemStack(Items.EMERALD,64));
        assertTrue(CoreLoot.purchase(player,0));assertEquals(48,inv.countItem(Items.EMERALD));
        assertFalse(CoreLoot.purchase(player,0));assertEquals(48,inv.countItem(Items.EMERALD));
        when(level.getGameTime()).thenReturn(80L);
        for(int i=1;i<36;i++)inv.items.set(i,new ItemStack(Items.STONE,64));
        assertFalse(CoreLoot.purchase(player,0));assertEquals(48,inv.countItem(Items.EMERALD));
        assertFalse(CoreLoot.purchase(player,2));assertEquals(48,inv.countItem(Items.EMERALD));
    }
    @Test void cannotFitEnchantedGearIntoFullInventoryOrFilterBadRolls() {
        var inventory=new java.util.ArrayList<ItemStack>();for(int i=0;i<36;i++)inventory.add(new ItemStack(Items.STONE,64));
        assertFalse(CoreLoot.fits(inventory,CoreLoot.reward(1,95)));
        inventory.set(0,ItemStack.EMPTY);assertTrue(CoreLoot.fits(inventory,CoreLoot.reward(1,95)));
    }
    @Test void receiptMatchesDeliveredRewardAndCannotChargeAgainDuringReveal() {
        var player=mock(ServerPlayer.class);var level=mock(ServerLevel.class);var inv=new Inventory(player);
        when(player.getInventory()).thenReturn(inv);when(player.serverLevel()).thenReturn(level);
        when(player.getPersistentData()).thenReturn(new net.minecraft.nbt.CompoundTag());
        when(player.getRandom()).thenReturn(net.minecraft.util.RandomSource.create(7));
        inv.items.set(0,new ItemStack(Items.EMERALD,64));
        var receipt=CoreLoot.purchaseWithReceipt(player,1);assertNotNull(receipt);
        assertEquals(16,inv.countItem(Items.EMERALD));
        assertEquals(receipt.prize().getCount(),inv.countItem(receipt.prize().getItem()));
        assertTrue(inv.items.stream().anyMatch(s->ItemStack.isSameItemSameTags(s,receipt.prize())));
        assertTrue(receipt.tier()>=0 && receipt.tier()<4);
        when(level.getGameTime()).thenReturn(40L);
        assertNull(CoreLoot.purchaseWithReceipt(player,0));assertEquals(16,inv.countItem(Items.EMERALD));
    }
}
