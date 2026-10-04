package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TerritoryBuffAvailabilityTest extends TreasuryTestSupport {
    @Test void unavailableNewAndOwnedPurchasesNeverChargeOrAlterSavedOwnership() {
        for (int mask : new int[]{0, 1, 2, 3, 15, 255}) for (int index : new int[]{0, 1}) {
            var player = mock(ServerPlayer.class);
            CompoundTag core = fund(player, 2000);
            core.putInt("TerritoryBuffs", mask);
            var before = core.copy();
            assertFalse(TerritoryBuffs.purchase(player, BlockPos.ZERO, index));
            assertFalse(TerritoryBuffs.purchase(player, BlockPos.ZERO, index));
            assertEquals(before, core);
            verify(player, never()).getInventory();
            when(player.isCreative()).thenReturn(true);
            assertFalse(TerritoryBuffs.purchase(player, BlockPos.ZERO, index));
            assertEquals(before, core);
        }
    }

    @Test void retainedOwnershipSurvivesSerializationAndDoesNotCountAsActive() throws Exception {
        var savedCore = new CompoundTag(); savedCore.putInt("TerritoryBuffs", 15);
        var bytes = new java.io.ByteArrayOutputStream();
        net.minecraft.nbt.NbtIo.write(savedCore, new java.io.DataOutputStream(bytes));
        var restored = net.minecraft.nbt.NbtIo.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())));
        assertTrue(TerritoryBuffs.has(restored, 0)); assertTrue(TerritoryBuffs.has(restored, 1));
        assertEquals(2, TerritoryBuffs.activeCount(TerritoryBuffs.mask(restored)));
        assertEquals(2, TerritoryBuffs.retainedCount(TerritoryBuffs.mask(restored)));
        assertEquals(2, TerritoryBuffs.availableCount());
        assertFalse(TerritoryBuffs.available(-1)); assertFalse(TerritoryBuffs.available(4));
    }

    @Test void workingUpgradeStillChargesOnceAtItsUnchangedPrice() {
        for (int index : new int[]{2, 3}) {
            var player = mock(ServerPlayer.class); var core = fund(player, 2000);
            core.putInt("TerritoryBuffs", 3);
            assertTrue(TerritoryBuffs.purchase(player, BlockPos.ZERO, index));
            assertEquals(2000 - TerritoryBuffs.price(index), FactionBank.balance(core));
            assertEquals(3 | (1 << index), TerritoryBuffs.mask(core));
            assertFalse(TerritoryBuffs.purchase(player, BlockPos.ZERO, index));
            assertEquals(2000 - TerritoryBuffs.price(index), FactionBank.balance(core));
            assertArrayEquals(new int[]{-TerritoryBuffs.price(index)}, FactionBank.ledgerDeltas(core));
        }
    }

    @Test void disabledCopyExplainsNoChargeAndPreservedOwnershipWithoutPromisingAnEffect() {
        assertTrue(TerritoryBuffs.unavailableDescription(false, false).contains("No emeralds will be charged"));
        assertTrue(TerritoryBuffs.unavailableDescription(true, false).contains("ownership is retained"));
        assertEquals("Unavailable; ownership saved", TerritoryBuffs.unavailableDescription(true, true));
        assertEquals("Unavailable; no charge", TerritoryBuffs.unavailableDescription(false, true));
    }
}
