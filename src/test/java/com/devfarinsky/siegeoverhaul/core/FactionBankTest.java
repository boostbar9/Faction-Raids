package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FactionBankTest extends MinecraftTestSupport {
    @Test void interestRequiresWholeRealDaysAndCannotRepeatAfterReloadOrClockRollback() {
        var core = new CompoundTag();
        FactionBank.credit(core, 10000);
        FactionBank.settle(core, 1000, 100);
        assertFalse(FactionBank.settle(core, 1000 + FactionBank.DAY - 1, 100));
        FactionBank.settle(core, 1000 + FactionBank.DAY, 100);
        assertEquals(10100, FactionBank.balance(core));
        var data = new RaidSavedData(); data.siegeCores.put("team:test", core);
        core = RaidSavedData.load(data.save(new CompoundTag())).siegeCores.get("team:test");
        assertFalse(FactionBank.settle(core, 1000 + FactionBank.DAY, 100));
        assertFalse(FactionBank.settle(core, 0, 100));
        FactionBank.settle(core, 1000 + 2 * FactionBank.DAY, 100);
        assertEquals(10201, FactionBank.balance(core));
    }
    @Test void fractionalInterestAccumulatesForSmallBalances() {
        var core = new CompoundTag(); FactionBank.credit(core, 10);
        FactionBank.settle(core, 1000, 100);
        FactionBank.settle(core, 1000 + 10 * FactionBank.DAY, 100);
        assertEquals(11, FactionBank.balance(core));
    }
    @Test void ledgerRejectsNegativePaymentsOverdraftsAndOverflow() {
        var core = new CompoundTag();
        assertEquals(0, FactionBank.credit(core, -1));
        assertEquals(FactionBank.LIMIT, FactionBank.credit(core, Long.MAX_VALUE));
        assertEquals(0, FactionBank.credit(core, 1));
        assertFalse(FactionBank.debit(core, 0)); assertFalse(FactionBank.debit(core, -1));
        assertFalse(FactionBank.debit(core, FactionBank.LIMIT + 1));
        assertTrue(FactionBank.debit(core, FactionBank.LIMIT));
        assertEquals(0, FactionBank.balance(core));
    }
    @Test void transactionsRespectInventoryRoomAndLeaderPermission() throws Exception {
        var player=org.mockito.Mockito.mock(net.minecraft.server.level.ServerPlayer.class);
        var inventory=new net.minecraft.world.entity.player.Inventory(player);
        org.mockito.Mockito.when(player.getInventory()).thenReturn(inventory);
        var field=net.minecraft.world.entity.player.Player.class.getField("inventoryMenu");
        field.setAccessible(true); field.set(player,org.mockito.Mockito.mock(net.minecraft.world.inventory.InventoryMenu.class));
        var saved=new RaidSavedData(); var core=new CompoundTag(); saved.siegeCores.put("team:test",core);
        try(var saves=org.mockito.Mockito.mockStatic(RaidSavedData.class);
            var keys=org.mockito.Mockito.mockStatic(SiegeCore.class);
            var bank=org.mockito.Mockito.mockStatic(FactionBank.class,org.mockito.Mockito.CALLS_REAL_METHODS)) {
            saves.when(()->RaidSavedData.get(null)).thenReturn(saved);
            keys.when(()->SiegeCore.key(player)).thenReturn("team:test");
            bank.when(()->FactionBank.canWithdraw(player)).thenReturn(false);
            inventory.items.set(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD,12));
            assertTrue(FactionBank.transact(player,64)); assertEquals(12,FactionBank.balance(core));
            assertTrue(inventory.items.get(0).isEmpty());
            assertFalse(FactionBank.transact(player,-8)); assertEquals(12,FactionBank.balance(core));
            bank.when(()->FactionBank.canWithdraw(player)).thenReturn(true);
            for(int n=0;n<inventory.items.size();n++)inventory.items.set(n,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE,64));
            assertFalse(FactionBank.transact(player,-8)); assertEquals(12,FactionBank.balance(core));
            inventory.items.set(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD,62));
            assertTrue(FactionBank.transact(player,-8)); assertEquals(10,FactionBank.balance(core));
            assertEquals(64,inventory.items.get(0).getCount());
        }
    }
}
