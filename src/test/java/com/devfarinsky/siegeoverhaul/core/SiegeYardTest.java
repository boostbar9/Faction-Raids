package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.siege.SiegeIntegration;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SiegeYardTest extends MinecraftTestSupport {
    @Test
    void purchaseGuidanceUsesSelectedVehicleFootprint() {
        String guidance = SiegeYard.deploymentAreaGuidance(new SiegeIntegration.Footprint(2, 5));
        assertEquals(5, SiegeYard.deploymentDiameter(new SiegeIntegration.Footprint(2, 5)));
        assertTrue(guidance.contains("5x5"));
        assertTrue(guidance.contains("5 blocks of headroom"));
    }

    @Test
    void clearanceChecksTopLayerIntersectedByCenteredSpawn() {
        BlockPos center = new BlockPos(0, 64, 0);
        ServerLevel level = clearDeploymentLevel(center);
        BlockPos obstruction = center.above(4);
        when(level.getBlockState(any())).thenAnswer(call -> {
            BlockPos pos = call.getArgument(0);
            if (pos.equals(obstruction)) return Blocks.STONE.defaultBlockState();
            return pos.getY() < center.getY()
                    ? Blocks.STONE.defaultBlockState()
                    : Blocks.AIR.defaultBlockState();
        });

        String issue = SiegeYard.describeClearance(level, center, 2, 5);
        assertTrue(issue.contains("0, 68, 0"));
    }

    @Test
    void validVehicleSizedVolumePassesClearance() {
        BlockPos center = new BlockPos(0, 64, 0);
        ServerLevel level = clearDeploymentLevel(center);
        when(level.getBlockState(any())).thenAnswer(call -> {
            BlockPos pos = call.getArgument(0);
            return pos.getY() < center.getY()
                    ? Blocks.STONE.defaultBlockState()
                    : Blocks.AIR.defaultBlockState();
        });

        assertNull(SiegeYard.describeClearance(level, center, 2, 5));
    }

    private static ServerLevel clearDeploymentLevel(BlockPos center) {
        ServerLevel level = mock(ServerLevel.class);
        WorldBorder border = mock(WorldBorder.class);
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getWorldBorder()).thenReturn(border);
        when(border.isWithinBounds(any(BlockPos.class))).thenReturn(true);
        when(level.getFluidState(any())).thenReturn(Fluids.EMPTY.defaultFluidState());
        return level;
    }

    @Test void creativeKitFeedbackDoesNotClaimThatEmeraldsWereSpent() {
        String feedback = SiegeYard.kitPurchaseMessage("Catapult Crew", 480, true, true, false,
                new SiegeIntegration.Footprint(2, 5));
        assertTrue(feedback.contains("No Treasury emeralds charged in Creative."));
        org.junit.jupiter.api.Assertions.assertFalse(feedback.contains("480"));
        assertTrue(feedback.contains("5x5 area with 5 blocks of headroom"));
        org.junit.jupiter.api.Assertions.assertFalse(feedback.contains("dropped"));
    }

    @Test void paidKitFeedbackNamesTreasuryAndFullInventoryDrop() {
        String feedback = SiegeYard.kitPurchaseMessage("Ballista Crew", 400, false, false, true,
                new SiegeIntegration.Footprint(1, 3));
        assertTrue(feedback.contains("400 emeralds charged to the faction Treasury."));
        assertTrue(feedback.contains("3x3 area with 3 blocks of headroom"));
        assertTrue(feedback.contains("kit was dropped at your feet"));
        org.junit.jupiter.api.Assertions.assertFalse(feedback.contains("Creative"));
    }

    @Test void unconfirmedDeliveryNeverClaimsThatAKitIsReadyOrDropped() {
        var footprint = new SiegeIntegration.Footprint(1, 3);
        for (boolean creative : new boolean[]{false, true}) {
            String message = SiegeYard.kitPurchaseMessage("Ballista Crew", 400, creative, false, false, footprint);
            assertTrue(message.contains("delivery could not be confirmed"));
            org.junit.jupiter.api.Assertions.assertFalse(message.contains("kit ready"));
            org.junit.jupiter.api.Assertions.assertFalse(message.contains("dropped at your feet"));
        }
    }

    @Test void inventoryReceiptCountsExistingKitsAcrossEveryInventorySlot() {
        var inventory = mock(net.minecraft.world.entity.player.Inventory.class);
        var item = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER);
        when(inventory.getContainerSize()).thenReturn(41);
        when(inventory.getItem(org.mockito.ArgumentMatchers.anyInt())).thenReturn(net.minecraft.world.item.ItemStack.EMPTY);
        when(inventory.getItem(35)).thenReturn(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER, 2));
        when(inventory.getItem(40)).thenReturn(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER, 3));
        assertEquals(5L, SiegeYard.inventoryCount(inventory, item));
        // A discarded Creative insertion leaves exactly the same count, never a receipt.
        assertEquals(5L, SiegeYard.inventoryCount(inventory, item));
        when(inventory.getItem(35)).thenReturn(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER, 3));
        assertEquals(6L, SiegeYard.inventoryCount(inventory, item));
    }

}
