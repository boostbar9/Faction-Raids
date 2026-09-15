package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraft.world.effect.MobEffects;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentSourceTest extends TreasuryTestSupport {
    @Test void inventoryCannotCoverTreasuryShortfallOrCausePartialDebit() {
        var player = mock(ServerPlayer.class);
        var core = fund(player, 15);
        var inventory = new Inventory(player);
        inventory.items.set(35, new ItemStack(Items.EMERALD, 64));
        when(player.getInventory()).thenReturn(inventory);
        assertEquals(15, PaymentSource.available(player, 16));
        assertFalse(PaymentSource.consume(player, 16));
        assertEquals(15, FactionBank.balance(core));
        assertEquals(64, inventory.countItem(Items.EMERALD));
        assertEquals(0, FactionBank.ledgerDeltas(core).length);
    }
    @Test void exactTreasuryPaymentDoesNotAccessInventoryAndRecordsOneDebit() {
        var player = mock(ServerPlayer.class); var core = fund(player, 48);
        assertTrue(PaymentSource.consume(player, 48));
        assertEquals(0, FactionBank.balance(core));
        assertFalse(PaymentSource.consume(player, 48));
        verify(player, never()).getInventory();
        assertTrue(saved.isDirty());
        assertEquals(-48, java.util.Arrays.stream(FactionBank.ledgerDeltas(core)).sum());
    }
    @Test void missingFactionCannotSpendOtherTreasury() {
        var player = mock(ServerPlayer.class); var core = fund(player, 100);
        when(player.getTeam()).thenReturn(null);
        assertEquals(0, PaymentSource.available(player, 24));
        assertFalse(PaymentSource.consume(player, 24));
        assertEquals(100, FactionBank.balance(core));
    }
    @Test void creativeRemainsFreeAndNegativePriceIsRejected() {
        var player = mock(ServerPlayer.class); var core = fund(player, 10);
        when(player.isCreative()).thenReturn(true);
        assertTrue(PaymentSource.consume(player, 100));
        assertFalse(PaymentSource.consume(player, -1));
        assertEquals(10, FactionBank.balance(core));
    }
    @Test void insufficientTreasuryNeverAppliesBlessing() {
        var player = mock(ServerPlayer.class); var core = fund(player, 23);
        when(player.isAlive()).thenReturn(true);
        assertFalse(CoreBuffs.purchase(player, 0));
        verify(player, never()).addEffect(any());
        assertEquals(23, FactionBank.balance(core));
    }
    @Test void rejectedPaymentRollsBackNewBlessing() {
        var player = mock(ServerPlayer.class); var core = fund(player, 24);
        when(player.isAlive()).thenReturn(true);
        when(player.addEffect(any())).thenAnswer(call -> { core.putLong("BankEmeralds", 0); return true; });
        assertFalse(CoreBuffs.purchase(player, 0));
        verify(player).removeEffect(MobEffects.MOVEMENT_SPEED);
    }
}
