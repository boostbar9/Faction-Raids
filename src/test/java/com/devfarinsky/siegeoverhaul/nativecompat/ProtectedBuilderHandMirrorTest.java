package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProtectedBuilderHandMirrorTest extends MinecraftTestSupport {
    @Test void equalPostLoadCopiesRebindOnceWithoutChangingAnyInventoryValue() {
        var data = pending(); var inventory = inventory();
        var slot = new ItemStack(Items.COBBLESTONE, 30); inventory.setItem(5, slot);
        var hand = new AtomicReference<>(slot.copy()); var calls = new AtomicInteger();
        var food = inventory.getItem(6).copy(); var offhand = inventory.getItem(4);
        assertNotSame(slot, hand.get());
        assertNull(ProtectedBuilderHandMirror.restore(data, inventory, hand::get,
                next -> { calls.incrementAndGet(); hand.set(next); inventory.setItem(5, next); }));
        assertSame(slot, hand.get()); assertSame(slot, inventory.getItem(5));
        assertEquals(30, inventory.countItem(Items.COBBLESTONE));
        assertTrue(ProtectedBuilderHandMirror.sameValue(food, inventory.getItem(6)));
        assertSame(offhand, inventory.getItem(4));
        assertFalse(ProtectedBuilderHandMirror.pending(data)); assertFalse(ProtectedBuilderHandMirror.reviewNeeded(data));
        assertEquals(1, ProtectedBuilderHandMirror.rebindCount(data));
        assertNull(ProtectedBuilderHandMirror.restore(data, inventory, hand::get, next -> calls.incrementAndGet()));
        assertEquals(1, calls.get(), "Only the armed post-load check may invoke native binding");
        slot.shrink(1);
        assertEquals(29, hand.get().getCount(), "Native consumption now changes the same mirror object");
    }

    @Test void alreadySharedPairNeedsNoSetterAndDoesNotTouchOffhand() {
        var data = pending(); var inventory = inventory(); var slot = new ItemStack(Items.COBBLESTONE, 30);
        inventory.setItem(5, slot);
        assertNull(ProtectedBuilderHandMirror.restore(data, inventory, () -> slot, next -> fail("Already aliased")));
        assertFalse(ProtectedBuilderHandMirror.pending(data)); assertEquals(0, ProtectedBuilderHandMirror.rebindCount(data));
    }

    @Test void ordinaryUnarmedInventoryNeverTriggersARepair() {
        var data = new CompoundTag();
        assertNull(ProtectedBuilderHandMirror.restore(data, null, () -> { throw new AssertionError(); }, next -> fail()));
        assertTrue(data.isEmpty());
    }

    @Test void unequalCountsItemsOrTagsPauseBeforeAnySetterAndRemainBlockedAcrossReload() {
        for (int kind = 0; kind < 3; kind++) {
            var data = pending(); var inventory = inventory(); var slot = new ItemStack(Items.COBBLESTONE, 30);
            inventory.setItem(5, slot); ItemStack different = slot.copy();
            if (kind == 0) different.setCount(29);
            if (kind == 1) different = new ItemStack(Items.OAK_PLANKS, 30);
            if (kind == 2) different.getOrCreateTag().putString("custom", "different");
            var hand = new AtomicReference<>(different); ItemStack before = different.copy();
            assertNotNull(ProtectedBuilderHandMirror.restore(data, inventory, hand::get, next -> fail("Unequal pair")));
            assertTrue(ProtectedBuilderHandMirror.reviewNeeded(data));
            assertEquals(30, slot.getCount()); assertTrue(ProtectedBuilderHandMirror.sameValue(before, hand.get()));
            CompoundTag reloaded = data.copy(); ProtectedBuilderHandMirror.arm(reloaded);
            hand.set(slot.copy()); // Native reload could conceal the prior mismatch by recreating equal copies.
            assertNotNull(ProtectedBuilderHandMirror.restore(reloaded, inventory, hand::get, next -> fail("Review receipt survives reload")));
            assertEquals(0, ProtectedBuilderHandMirror.rebindCount(reloaded));
        }
    }

    @Test void equalOverstacksAreRejectedBeforeTheNativeContainerCanClampTheirAmounts() {
        var data = pending(); var inventory = inventory(); var slot = new ItemStack(Items.COBBLESTONE, 30);
        inventory.setItem(5, slot); slot.setCount(127); // Do not pre-clamp through SimpleContainer.setItem.
        var hand = slot.copy();
        assertNotNull(ProtectedBuilderHandMirror.restore(data, inventory, () -> hand,
                next -> fail("Native setItem would clamp this equal 127-stack")));
        assertEquals(127, slot.getCount()); assertEquals(127, hand.getCount());
        assertTrue(ProtectedBuilderHandMirror.reviewNeeded(data)); assertEquals(0, ProtectedBuilderHandMirror.rebindCount(data));
        var tools = pending(); var tool = new ItemStack(Items.DIAMOND_PICKAXE, 2); inventory.setItem(5, tool);
        var heldTool = tool.copy();
        assertNotNull(ProtectedBuilderHandMirror.restore(tools, inventory, () -> heldTool,
                next -> fail("Item stack maximum is also part of the precondition")));
        assertEquals(2, tool.getCount()); assertEquals(2, heldTool.getCount());
    }

    @Test void differentSerializedForgeCapabilitiesRejectEqualItemPlainTagAndCountBeforeSetter() {
        var data = pending(); var inventory = inventory();
        var slot = serializedCapabilityStack(30, 12); var hand = serializedCapabilityStack(30, 99);
        inventory.setItem(5, slot);
        assertEquals(slot.getItem(), hand.getItem()); assertEquals(slot.getCount(), hand.getCount());
        assertEquals(slot.getTag(), hand.getTag());
        assertFalse(ProtectedBuilderHandMirror.sameValue(slot, hand));
        assertNotNull(ProtectedBuilderHandMirror.restore(data, inventory, () -> hand,
                next -> fail("Full ForgeCaps mismatch must not choose the slot capability state")));
        assertTrue(ProtectedBuilderHandMirror.reviewNeeded(data));
        verify(slot, never()).copy(); verify(hand, never()).copy();
    }

    @Test void changingCapabilitySerializationOrSerializationFailureCannotPassPostconditions() {
        var data = pending(); var inventory = inventory();
        var charge = new AtomicInteger(12); var slot = serializedCapabilityStack(30, charge);
        var hand = new AtomicReference<>(serializedCapabilityStack(30, 12)); inventory.setItem(5, slot);
        assertNotNull(ProtectedBuilderHandMirror.restore(data, inventory, hand::get, next -> {
            hand.set(next); charge.set(13); // Simulated unreviewed capability side effect.
        }));
        assertTrue(ProtectedBuilderHandMirror.reviewNeeded(data)); assertEquals(0, ProtectedBuilderHandMirror.rebindCount(data));
        var broken = pending(); var unavailable = serializedCapabilityStack(30, 12);
        when(unavailable.save(any(CompoundTag.class))).thenThrow(new IllegalStateException("Cannot serialize caps"));
        assertNotNull(ProtectedBuilderHandMirror.restore(broken, inventory, () -> unavailable, next -> fail()));
        assertTrue(ProtectedBuilderHandMirror.reviewNeeded(broken));
    }

    @Test void sharedNonemptyCargoAliasIsNotMistakenForTheAuditedHandMirrorDefect() {
        var data = pending(); var inventory = inventory(); var slot = new ItemStack(Items.COBBLESTONE, 30);
        inventory.setItem(5, slot); inventory.setItem(10, slot);
        assertNotNull(ProtectedBuilderHandMirror.restore(data, inventory, slot::copy, next -> fail("Ambiguous cargo alias")));
        assertTrue(ProtectedBuilderHandMirror.reviewNeeded(data));
        var alreadyShared = pending();
        assertNotNull(ProtectedBuilderHandMirror.restore(alreadyShared, inventory, () -> slot,
                next -> fail("Already-shared hand plus extra cargo alias is still ambiguous")));
        assertTrue(ProtectedBuilderHandMirror.reviewNeeded(alreadyShared));
    }

    @Test void nativeSetterMustProveBothIdentityAndUnchangedValuesWithoutRetryOrRollback() {
        var data = pending(); var inventory = inventory(); var slot = new ItemStack(Items.COBBLESTONE, 30);
        inventory.setItem(5, slot); var hand = new AtomicReference<>(slot.copy());
        assertNotNull(ProtectedBuilderHandMirror.restore(data, inventory, hand::get, next -> hand.set(next.copy())));
        assertTrue(ProtectedBuilderHandMirror.reviewNeeded(data));
        assertEquals(30, slot.getCount()); assertEquals(30, hand.get().getCount());
        assertNotNull(ProtectedBuilderHandMirror.restore(data, inventory, hand::get, next -> fail("No speculative retry")));
        var modified = pending();
        assertNotNull(ProtectedBuilderHandMirror.restore(modified, inventory, hand::get, next -> {
            hand.set(next); inventory.getItem(6).shrink(1); // Simulated unsupported callback, never the production setter.
        }));
        assertTrue(ProtectedBuilderHandMirror.reviewNeeded(modified));
        assertEquals(15, inventory.getItem(6).getCount(), "Do not conceal unsupported mutation by inventing rollback amounts");
    }

    @Test void reviewMarkerPreventsAReentrantNativeSetterFromRebindingAgain() {
        var data = pending(); var inventory = inventory(); var slot = new ItemStack(Items.COBBLESTONE, 30);
        inventory.setItem(5, slot); var hand = new AtomicReference<>(slot.copy()); var calls = new AtomicInteger();
        assertNull(ProtectedBuilderHandMirror.restore(data, inventory, hand::get, next -> {
            calls.incrementAndGet();
            assertNotNull(ProtectedBuilderHandMirror.restore(data, inventory, hand::get,
                    nested -> fail("Reentrant binding must be denied")));
            hand.set(next);
        }));
        assertEquals(1, calls.get()); assertEquals(1, ProtectedBuilderHandMirror.rebindCount(data));
        assertEquals(30, slot.getCount()); assertSame(slot, hand.get());
    }

    @Test void nativeSetterExceptionLeavesADurableReviewReceipt() {
        var data = pending(); var inventory = inventory(); var slot = new ItemStack(Items.COBBLESTONE, 30);
        inventory.setItem(5, slot);
        assertNotNull(ProtectedBuilderHandMirror.restore(data, inventory, slot::copy, next -> {throw new IllegalStateException("unsupported");}));
        assertTrue(ProtectedBuilderHandMirror.reviewNeeded(data.copy()));
        assertEquals(30, inventory.countItem(Items.COBBLESTONE));
    }

    @Test void onlyCurrentCompleteActiveReceiptWithMatchingGenerationAuthorizesTheNativeHook() {
        var ledger = new ConstructionEditLedger(); UUID job = UUID.randomUUID(); var data = new CompoundTag();
        assertFalse(NativeConstructionGuard.validHandReceipt(data, ledger));
        data.putUUID("SiegeProtectedAreaReceipt", job); data.putUUID("SiegeProtectedLedgerGeneration", ledger.generation());
        assertFalse(NativeConstructionGuard.validHandReceipt(data, ledger));
        assertTrue(ledger.register(job, Set.of(BlockPos.ZERO)));
        assertTrue(NativeConstructionGuard.validHandReceipt(data, ledger));
        assertFalse(NativeConstructionGuard.validHandReceipt(data, new ConstructionEditLedger()));
        var saved = ledger.save(new CompoundTag());
        saved.getList("Sites", Tag.TAG_COMPOUND).getCompound(0).remove("ReservationVersion");
        assertFalse(NativeConstructionGuard.validHandReceipt(data, ConstructionEditLedger.load(saved)));
        ledger.retire(job, false);
        assertFalse(NativeConstructionGuard.validHandReceipt(data, ledger), "Canceled receipt takes cleanup path, never unrelated native repair");
    }

    @Test void normalUseCanBeObservedAsATransientPreCommissionBlockWithoutWritingReviewState() {
        var worker = mock(com.talhanation.workers.entities.BuilderEntity.class); var data = new CompoundTag();
        when(worker.getPersistentData()).thenReturn(data); when(worker.getUseItem()).thenReturn(new ItemStack(Items.BREAD));
        when(worker.isUsingItem()).thenReturn(true); when(worker.getUseItemRemainingTicks()).thenReturn(12);
        assertTrue(ProtectedBuilderHandMirror.activeUse(worker));
        assertNotNull(NativeConstructionGuard.commissionProblem(worker));
        assertTrue(data.isEmpty(), "Pre-payment observation does not arm or mark a healthy eating worker");
        when(worker.isUsingItem()).thenReturn(false); when(worker.getUseItem()).thenReturn(ItemStack.EMPTY);
        when(worker.getUseItemRemainingTicks()).thenReturn(0);
        assertFalse(ProtectedBuilderHandMirror.activeUse(worker));
        assertNull(NativeConstructionGuard.commissionProblem(worker));
        assertTrue(data.isEmpty()); verify(worker, never()).stopUsingItem();
    }

    @Test void anyLiveUseStateRejectsTheNativeSetterWithoutStoppingUse() {
        for (int active = 0; active < 3; active++) {
            var worker = mock(com.talhanation.workers.entities.BuilderEntity.class); var data = pending();
            when(worker.getPersistentData()).thenReturn(data);
            when(worker.isUsingItem()).thenReturn(active == 0);
            when(worker.getUseItem()).thenReturn(active == 1 ? new ItemStack(Items.BOW) : ItemStack.EMPTY);
            when(worker.getUseItemRemainingTicks()).thenReturn(active == 2 ? 20 : 0);
            assertNotNull(ProtectedBuilderHandMirror.restore(worker));
            assertTrue(ProtectedBuilderHandMirror.reviewNeeded(data));
            verify(worker, never()).setItemInHand(any(), any());
            verify(worker, never()).stopUsingItem();
            verify(worker, never()).getInventory();
        }
    }

    @Test void tooSmallOrUnboundedInventoriesFailClosed() {
        for (int size : new int[]{5, 129}) {
            var data = pending();
            assertNotNull(ProtectedBuilderHandMirror.restore(data, new SimpleContainer(size), () -> ItemStack.EMPTY, next -> fail()));
            assertTrue(ProtectedBuilderHandMirror.reviewNeeded(data));
        }
    }

    // Pure serialized-state contract, not a claim that Mockito runs a live Forge capability provider.
    private ItemStack serializedCapabilityStack(int count, int charge) {
        return serializedCapabilityStack(count, new AtomicInteger(charge));
    }
    private ItemStack serializedCapabilityStack(int count, AtomicInteger charge) {
        var stack = mock(ItemStack.class);
        when(stack.getItem()).thenReturn(Items.COBBLESTONE); when(stack.getCount()).thenReturn(count);
        when(stack.getMaxStackSize()).thenReturn(64); when(stack.isEmpty()).thenReturn(false);
        when(stack.save(any(CompoundTag.class))).thenAnswer(call -> {
            CompoundTag nbt = call.getArgument(0); nbt.putString("id", "minecraft:cobblestone");
            nbt.putByte("Count", (byte)count);
            CompoundTag caps = new CompoundTag(); caps.putInt("qa:charge", charge.get()); nbt.put("ForgeCaps", caps);
            return nbt;
        });
        return stack;
    }

    private CompoundTag pending() { var data = new CompoundTag(); ProtectedBuilderHandMirror.arm(data); return data; }
    private SimpleContainer inventory() {
        var inventory = new SimpleContainer(15); inventory.setItem(6, new ItemStack(Items.BREAD, 16));
        inventory.setItem(4, new ItemStack(Items.SHIELD)); return inventory;
    }
}
