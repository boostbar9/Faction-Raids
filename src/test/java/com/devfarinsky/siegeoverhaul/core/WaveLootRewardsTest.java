package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.items.LootBoxItem.Tier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WaveLootRewardsTest extends MinecraftTestSupport {
    private RaidSavedData.RaidState raid(int wave) {
        var state = new RaidSavedData.RaidState("team:test", "siege_core", 0);
        state.wave = wave;
        return state;
    }

    private ServerPlayer player() {
        var player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(UUID.randomUUID());
        var inventory = mock(Inventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.add(any(ItemStack.class))).thenAnswer(call -> {
            ((ItemStack) call.getArgument(0)).setCount(0);
            return true;
        });
        return player;
    }

    private void award(RaidSavedData data, RaidSavedData.RaidState state, ServerPlayer... members) {
        WaveLootRewards.awardClearedWave(data, state, List.of(members), p -> Tier.RARE,
                tier -> new ItemStack(Items.CHEST));
    }

    @Test void newRaidReceiptSurvivesPreWaveSaveAndPaysFirstClearedWave() {
        var data = new RaidSavedData();
        var state = RaidSavedData.RaidState.load(raid(0).save());
        var player = player();
        award(data, state, player);
        verify(player.getInventory(), never()).add(any());
        state.wave = 1;
        award(data, state, player);
        verify(player.getInventory()).add(any());
        assertTrue(data.isDirty());
    }

    @Test void eligibleWavePaysEachFrozenOnlineDefenderOnlyOnceAcrossFullSavedDataReload() {
        var data = new RaidSavedData();
        var state = raid(3);
        var first = player(); var second = player(); var late = player();
        data.raids.put(state.teamKey, state);
        award(data, state, first, second, first);
        award(data, state, first, second, late);
        data = RaidSavedData.load(data.save(new CompoundTag()));
        state = data.raids.get("team:test");
        award(data, state, first, second, late);
        verify(first.getInventory(), times(1)).add(any());
        verify(second.getInventory(), times(1)).add(any());
        verify(late.getInventory(), never()).add(any());
        state.wave = 4;
        award(data, state, first, late);
        verify(first.getInventory(), times(2)).add(any());
        verify(late.getInventory(), times(1)).add(any());
        assertEquals(2, state.waveLootRewards.save().getList("Recipients", Tag.TAG_COMPOUND).size());
    }

    @Test void practiceWaveAndSpectatorsDoNotRollCreateOrReceiveBoxes() {
        var data = new RaidSavedData(); var state = raid(1); var player = player();
        state.rewardEligible = false;
        WaveLootRewards.awardClearedWave(data, state, List.of(player), p -> { fail("practice roll"); return null; },
                tier -> { fail("practice item"); return null; });
        state.rewardEligible = true;
        award(data, state, player);
        verify(player.getInventory(), never()).add(any());
        state.wave = 2;
        when(player.isSpectator()).thenReturn(true);
        award(data, state, player);
        when(player.isSpectator()).thenReturn(false);
        award(data, state, player);
        verify(player.getInventory(), never()).add(any());
    }

    @Test void incompleteAndOccupiedWavesDoNotConsumeAReceipt() {
        List<Consumer<RaidSavedData.RaidState>> incomplete = List.of(
                state -> state.pendingWaveSpawns = 1,
                state -> state.raiders.add(UUID.randomUUID()),
                state -> state.preparationTicks = 20,
                state -> state.coreCaptured = true);
        var player = player();
        for (var block : incomplete) {
            var data = new RaidSavedData(); var state = raid(1);
            block.accept(state);
            award(data, state, player);
            assertEquals(0, state.waveLootRewards.save().getInt("Wave"));
            assertFalse(data.isDirty());
        }
        verify(player.getInventory(), never()).add(any());
        award(new RaidSavedData(), raid(1), player);
        verify(player.getInventory()).add(any());
    }

    @Test void checkpointClearPaysBeforeRetreatAndRepeatedVotePassesNeverPayAgain() {
        var data = new RaidSavedData(); var state = raid(5); var player = player();
        // Settlement has no next-wave/countdown prerequisite, including checkpoint vote passes.
        state.ticksToNextWave = 40;
        award(data, state, player);
        EndlessSiege.begin(state.campaign, 5, List.of(player.getUUID()), "checkpoint");
        state = RaidSavedData.RaidState.load(state.save());
        award(data, state, player);
        assertTrue(EndlessSiege.cast(state.campaign, player.getUUID(), "checkpoint", true));
        assertEquals(EndlessSiege.Decision.RETREAT, EndlessSiege.tick(state.campaign));
        award(data, state, player);
        verify(player.getInventory(), times(1)).add(any());
    }

    @Test void finiteFinalWaveAlsoPaysWithoutWaitingForAnotherWave() {
        var state = raid(5); state.defensePointName = "legacy";
        var player = player();
        award(new RaidSavedData(), state, player);
        verify(player.getInventory()).add(any());
    }

    @Test void rolledTierIsSavedBeforeItemCreationAndReusedAfterRetryAndReload() {
        var data = new RaidSavedData(); var state = raid(5); var player = player();
        var rolls = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> WaveLootRewards.awardClearedWave(data, state,
                List.of(player), p -> { rolls.incrementAndGet(); return Tier.EPIC; },
                tier -> { throw new IllegalStateException("registry unavailable"); }));
        var loaded = RaidSavedData.RaidState.load(state.save());
        WaveLootRewards.awardClearedWave(data, loaded, List.of(player),
                p -> { fail("must reuse the saved tier"); return null; }, tier -> {
                    assertEquals(Tier.EPIC, tier);
                    return new ItemStack(Items.CHEST);
                });
        assertEquals(1, rolls.get());
        verify(player.getInventory()).add(any());
    }

    @Test void uncertainPartialDeliveryIsConsumedBeforeSideEffectsAndNeverReplayed() {
        var data = new RaidSavedData(); var state = raid(2);
        var first = player(); var second = player();
        doAnswer(call -> {
            assertTrue(state.waveLootRewards.save().getList("Recipients", Tag.TAG_COMPOUND)
                    .getCompound(0).getBoolean("Attempted"));
            throw new IllegalStateException("inventory listener failed after mutation");
        }).when(first.getInventory()).add(any(ItemStack.class));
        assertThrows(IllegalStateException.class, () -> award(data, state, first, second));
        var loaded = RaidSavedData.RaidState.load(state.save());
        award(data, loaded, first, second);
        verify(first.getInventory(), times(1)).add(any());
        verify(second.getInventory(), times(1)).add(any());
    }

    @Test void partialInventoryInsertionDropsOnlyTheMutatedRemainderRegardlessOfBooleanResult() {
        for (boolean result : List.of(false, true)) {
            var player = player();
            doAnswer(call -> {
                ((ItemStack) call.getArgument(0)).shrink(2);
                return result;
            }).when(player.getInventory()).add(any(ItemStack.class));
            when(player.drop(any(ItemStack.class), eq(false))).thenReturn(mock(ItemEntity.class));
            ItemStack original = new ItemStack(Items.CHEST, 5);
            assertTrue(WaveLootRewards.deliver(player, original));
            verify(player).drop(argThat(stack -> stack.getCount() == 3), eq(false));
            assertEquals(5, original.getCount());
            verify(player.getInventory()).setChanged();
        }
    }

    @Test void failedOverflowDropIsNotReportedAsSuccessOrRetried() {
        var data = new RaidSavedData(); var state = raid(2); var player = player();
        doReturn(false).when(player.getInventory()).add(any(ItemStack.class));
        award(data, state, player);
        var loaded = RaidSavedData.RaidState.load(state.save());
        award(data, loaded, player);
        verify(player).drop(any(ItemStack.class), eq(false));
        verify(player, never()).displayClientMessage(any(), anyBoolean());
        verify(player.getInventory(), times(1)).add(any());
    }

    @Test void legacyMissingReceiptSuppressesAmbiguousCurrentWaveButAllowsTheNext() {
        for (int currentWave : List.of(0, 1, 5, 10)) {
            var saved = raid(currentWave).save(); saved.remove("WaveLootRewards");
            var state = RaidSavedData.RaidState.load(saved); var player = player();
            award(new RaidSavedData(), state, player);
            verify(player.getInventory(), never()).add(any());
            state.wave = currentWave + 1;
            award(new RaidSavedData(), state, player);
            verify(player.getInventory()).add(any());
        }
    }

    @Test void invalidReceiptsFailClosedWithoutSynthesizingHistoricalPayouts() {
        List<Consumer<CompoundTag>> corruptions = List.of(
                tag -> tag.remove("Version"), tag -> tag.putInt("Version", 99),
                tag -> tag.putString("Wave", "5"), tag -> tag.putInt("Wave", -1),
                tag -> tag.putInt("Wave", 6), tag -> tag.remove("Recipients"),
                tag -> { var wrong = new ListTag(); wrong.add(StringTag.valueOf("bad")); tag.put("Recipients", wrong); },
                tag -> tag.getList("Recipients", Tag.TAG_COMPOUND).getCompound(0).putInt("Tier", 99),
                tag -> tag.getList("Recipients", Tag.TAG_COMPOUND).getCompound(0).remove("Player"),
                tag -> tag.getList("Recipients", Tag.TAG_COMPOUND).getCompound(0).remove("Attempted"),
                tag -> tag.getList("Recipients", Tag.TAG_COMPOUND).getCompound(0).putByte("Attempted", (byte) 2),
                tag -> tag.getList("Recipients", Tag.TAG_COMPOUND).getCompound(0).putInt("Tier", -1),
                tag -> { var list = tag.getList("Recipients", Tag.TAG_COMPOUND); list.add(list.getCompound(0).copy()); });
        for (var corrupt : corruptions) {
            var state = raid(5); var player = player();
            award(new RaidSavedData(), state, player);
            var saved = state.save(); corrupt.accept(saved.getCompound("WaveLootRewards"));
            state = RaidSavedData.RaidState.load(saved);
            award(new RaidSavedData(), state, player);
            verify(player.getInventory(), times(1)).add(any());
            assertEquals(5, state.waveLootRewards.save().getInt("Wave"));
            assertTrue(state.waveLootRewards.save().getList("Recipients", Tag.TAG_COMPOUND).isEmpty());
            state.wave = 6;
            award(new RaidSavedData(), state, player);
            verify(player.getInventory(), times(2)).add(any());
        }
    }

    @Test void receiptSizeIsBoundedByOneWaveAndRejectsOversizedSavedLists() {
        var state = raid(1); var player = player();
        for (int wave = 1; wave <= 100; wave++) {
            state.wave = wave;
            award(new RaidSavedData(), state, player);
            assertEquals(1, state.waveLootRewards.save().getList("Recipients", Tag.TAG_COMPOUND).size());
        }
        var saved = state.save(); var rewards = saved.getCompound("WaveLootRewards");
        var list = rewards.getList("Recipients", Tag.TAG_COMPOUND);
        var entry = list.getCompound(0);
        while (list.size() <= WaveLootRewards.MAX_RECIPIENTS) list.add(entry.copy());
        var loaded = RaidSavedData.RaidState.load(saved);
        assertTrue(loaded.waveLootRewards.save().getList("Recipients", Tag.TAG_COMPOUND).isEmpty());
        award(new RaidSavedData(), loaded, player);
        verify(player.getInventory(), times(100)).add(any());
    }
}
