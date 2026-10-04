package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.talhanation.recruits.inventory.RecruitSimpleContainer;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.world.NeededItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProtectedTransferCapacityBoundaryTest extends MinecraftTestSupport {
    private static final String UNVERIFIABLE = "Paused: native transfer capacity or full item data cannot be verified";
    private static final String UNSUPPORTED_INVENTORY = "Paused: native inventory insertion behavior is unsupported";

    @Test void exactNativeInventoryCanValidateInputsWithoutChangingAnyLiveIdentityOrValue() {
        var fixture = fixture();
        var carried = new ItemStack(Items.BREAD, 12);
        var supplied = new ItemStack(Items.COBBLESTONE, 32);
        supplied.getOrCreateTag().putString("qa", "preserve full source tag");
        fixture.inventory().setItem(6, carried);
        var source = new SimpleContainer(supplied);
        CompoundTag carriedBefore = carried.save(new CompoundTag());
        CompoundTag suppliedBefore = supplied.save(new CompoundTag());
        List<ItemStack> inventoryReferences = slots(fixture.inventory());

        assertTrue(ProtectedTransferCapacity.supportedInventory(fixture.inventory()));
        assertNull(ProtectedTransferCapacity.requestsProblem(fixture.worker()));
        assertNull(ProtectedTransferCapacity.problem(fixture.worker(), source));

        assertSame(fixture.requests(), fixture.worker().neededItems);
        assertTrue(fixture.requests().isEmpty());
        assertSame(fixture.inventory(), fixture.worker().getInventory());
        for (int slot = 0; slot < inventoryReferences.size(); slot++)
            assertSame(inventoryReferences.get(slot), fixture.inventory().getItem(slot));
        assertSame(supplied, source.getItem(0));
        assertEquals(carriedBefore, carried.save(new CompoundTag()));
        assertEquals(suppliedBefore, supplied.save(new CompoundTag()));
    }

    @Test void liveSourceDestinationAliasIsRejectedWithoutSplittingOrShrinkingIt() {
        var fixture = fixture();
        var shared = new ItemStack(Items.COBBLESTONE, 32);
        fixture.inventory().setItem(6, shared);
        var source = new SimpleContainer(shared);
        CompoundTag before = shared.save(new CompoundTag());

        assertEquals(UNVERIFIABLE, ProtectedTransferCapacity.problem(fixture.worker(), source));

        assertSame(shared, fixture.inventory().getItem(6));
        assertSame(shared, source.getItem(0));
        assertEquals(before, shared.save(new CompoundTag()));
        assertSame(fixture.requests(), fixture.worker().neededItems);
    }

    @Test void repeatedNonemptyReferencesWithinEitherInventoryAreAlsoRejected() {
        for (boolean aliasInSource : new boolean[]{true, false}) {
            var fixture = fixture();
            var shared = new ItemStack(Items.COBBLESTONE, 32);
            var source = new SimpleContainer(2);
            SimpleContainer aliased = aliasInSource ? source : fixture.inventory();
            int firstSlot = aliasInSource ? 0 : 6;
            aliased.setItem(firstSlot, shared);
            aliased.setItem(firstSlot + 1, shared);

            assertEquals(UNVERIFIABLE, ProtectedTransferCapacity.problem(fixture.worker(), source));

            assertSame(shared, aliased.getItem(firstSlot));
            assertSame(shared, aliased.getItem(firstSlot + 1));
            assertEquals(32, shared.getCount());
        }
    }

    @Test void liveSourceAndDestinationOverstacksAreRejectedBeforeAnyContainerCanClampThem() {
        for (boolean overstackInSource : new boolean[]{true, false}) {
            var fixture = fixture();
            var source = new SimpleContainer(1);
            var overstack = new ItemStack(Items.COBBLESTONE, 32);
            SimpleContainer target = overstackInSource ? source : fixture.inventory();
            int slot = overstackInSource ? 0 : 6;
            target.setItem(slot, overstack);
            overstack.setCount(127); // Set after insertion: SimpleContainer.setItem would pre-clamp 127.

            assertEquals(127, target.getItem(slot).getCount());
            assertEquals(UNVERIFIABLE, ProtectedTransferCapacity.problem(fixture.worker(), source));

            assertSame(overstack, target.getItem(slot));
            assertEquals(127, overstack.getCount());
        }
    }

    @Test void liveItemSpecificStackLimitsApplyToBothSourceAndDestination() {
        for (boolean overstackInSource : new boolean[]{true, false}) {
            var fixture = fixture();
            var source = new SimpleContainer(1);
            var tools = new ItemStack(Items.DIAMOND_PICKAXE);
            SimpleContainer target = overstackInSource ? source : fixture.inventory();
            int slot = overstackInSource ? 0 : 6;
            target.setItem(slot, tools);
            tools.setCount(2);

            assertEquals(1, tools.getMaxStackSize());
            assertEquals(UNVERIFIABLE, ProtectedTransferCapacity.problem(fixture.worker(), source));

            assertSame(tools, target.getItem(slot));
            assertEquals(2, tools.getCount());
        }
    }

    @Test void sourceCopyThatLosesOrdinaryNbtIsRejectedDespiteEqualItemAndCount() {
        var fixture = fixture();
        var supplied = spy(new ItemStack(Items.COBBLESTONE, 32));
        supplied.getOrCreateTag().putString("qa", "do not discard");
        var lossyCopy = new ItemStack(Items.COBBLESTONE, 32);
        doReturn(lossyCopy).when(supplied).copy();
        var source = new SimpleContainer(supplied);
        CompoundTag before = supplied.save(new CompoundTag());

        assertSame(supplied.getItem(), lossyCopy.getItem());
        assertEquals(supplied.getCount(), lossyCopy.getCount());
        assertNotEquals(before, lossyCopy.save(new CompoundTag()));
        assertEquals(UNVERIFIABLE, ProtectedTransferCapacity.problem(fixture.worker(), source));

        verify(supplied).copy();
        assertSame(supplied, source.getItem(0));
        assertEquals(before, supplied.save(new CompoundTag()));
    }

    @Test void sourceCopyMustPreserveSerializedForgeCapsOutsideTheOrdinaryItemTag() {
        var fixture = fixture();
        var supplied = spy(new ItemStack(Items.COBBLESTONE, 32));
        // Serialization-contract fixture only: this does not install or exercise a live capability provider.
        // The actual ItemStack.copy() remains unstubbed, so its plain copy lacks this serialized state.
        doAnswer(call -> {
            CompoundTag serialized = (CompoundTag) call.callRealMethod();
            var caps = new CompoundTag();
            caps.putInt("qa:charge", 12);
            serialized.put("ForgeCaps", caps);
            return serialized;
        }).when(supplied).save(any(CompoundTag.class));
        var source = new SimpleContainer(supplied);
        CompoundTag before = supplied.save(new CompoundTag());
        var plain = new ItemStack(Items.COBBLESTONE, 32);

        assertSame(supplied.getItem(), plain.getItem());
        assertEquals(supplied.getCount(), plain.getCount());
        assertEquals(supplied.getTag(), plain.getTag());
        assertNotEquals(before, plain.save(new CompoundTag()));
        assertEquals(UNVERIFIABLE, ProtectedTransferCapacity.problem(fixture.worker(), source));

        verify(supplied).copy();
        assertSame(supplied, source.getItem(0));
        assertEquals(before, supplied.save(new CompoundTag()));
    }

    @Test void namedAndForeignAnonymousInsertionSubclassesAreRejectedBeforeInsertion() {
        var fixture = fixture();
        var calls = new AtomicInteger();
        var named = new UnsupportedInventory(fixture.worker(), calls);
        var anonymous = new RecruitSimpleContainer(15, fixture.worker()) {
            @Override public ItemStack addItem(ItemStack stack) {
                calls.incrementAndGet();
                throw new AssertionError("Unknown insertion code must not run");
            }
        };
        var foreignInherited = new RecruitSimpleContainer(15, fixture.worker()) {};
        for (var inventory : List.of(named, anonymous, foreignInherited)) {
            when(fixture.worker().getInventory()).thenReturn(inventory);

            assertFalse(ProtectedTransferCapacity.supportedInventory(inventory));
            assertEquals(UNSUPPORTED_INVENTORY,
                    ProtectedTransferCapacity.problem(fixture.worker(), new SimpleContainer(1)));
        }
        assertEquals(0, calls.get());
        assertSame(fixture.requests(), fixture.worker().neededItems);
    }

    @Test void equalValueReplacementOfALiveDestinationStackDuringCopyIsRejected() {
        var fixture = fixture();
        var carried = new ItemStack(Items.BREAD, 12);
        var replacement = carried.copy();
        fixture.inventory().setItem(6, carried);
        var supplied = spy(new ItemStack(Items.COBBLESTONE, 32));
        doAnswer(call -> {
            fixture.inventory().setItem(6, replacement);
            return call.callRealMethod();
        }).when(supplied).copy();

        assertEquals(UNVERIFIABLE,
                ProtectedTransferCapacity.problem(fixture.worker(), new SimpleContainer(supplied)));

        verify(supplied).copy();
        assertSame(replacement, fixture.inventory().getItem(6), "Do not conceal a callback mutation by rolling it back");
        assertEquals(carried.save(new CompoundTag()), replacement.save(new CompoundTag()));
    }

    @Test void liveDestinationTagMutationDuringCopyIsRejectedEvenWhenIdentityAndCountStayEqual() {
        var fixture = fixture();
        var carried = new ItemStack(Items.BREAD, 12);
        fixture.inventory().setItem(6, carried);
        var supplied = spy(new ItemStack(Items.COBBLESTONE, 32));
        doAnswer(call -> {
            carried.getOrCreateTag().putString("qa", "changed during copy");
            return call.callRealMethod();
        }).when(supplied).copy();

        assertEquals(UNVERIFIABLE,
                ProtectedTransferCapacity.problem(fixture.worker(), new SimpleContainer(supplied)));

        verify(supplied).copy();
        assertSame(carried, fixture.inventory().getItem(6));
        assertEquals(12, carried.getCount());
        assertEquals("changed during copy", carried.getTag().getString("qa"));
    }

    @Test void replacingNativeRequestListWithAnEqualEmptyListDuringCopyIsRejected() {
        var fixture = fixture();
        List<NeededItem> replacement = new ArrayList<>();
        var supplied = spy(new ItemStack(Items.COBBLESTONE, 32));
        doAnswer(call -> {
            fixture.worker().neededItems = replacement;
            return call.callRealMethod();
        }).when(supplied).copy();

        assertNotSame(fixture.requests(), replacement);
        assertEquals(fixture.requests(), replacement);
        assertEquals(UNVERIFIABLE,
                ProtectedTransferCapacity.problem(fixture.worker(), new SimpleContainer(supplied)));

        verify(supplied).copy();
        assertSame(replacement, fixture.worker().neededItems, "Reject changed provenance without replacing native state");
        assertTrue(fixture.requests().isEmpty());
        assertTrue(replacement.isEmpty());
    }

    @Test void requestAddedBySourceSerializationCannotExecuteAnUnverifiedMatcher() {
        var fixture = fixture();
        var matcherCalls = new AtomicInteger();
        Predicate<ItemStack> matcher = stack -> { matcherCalls.incrementAndGet(); return true; };
        var injected = new NeededItem(matcher, 32, true);
        var injectOnce = new AtomicBoolean();
        var supplied = spy(new ItemStack(Items.COBBLESTONE, 32));
        doAnswer(call -> {
            if (injectOnce.compareAndSet(false, true)) fixture.requests().add(injected);
            return call.callRealMethod();
        }).when(supplied).save(any(CompoundTag.class));

        assertFalse(ProtectedTransferCapacity.trustedMatcher(matcher));
        assertEquals(UNVERIFIABLE,
                ProtectedTransferCapacity.problem(fixture.worker(), new SimpleContainer(supplied)));

        assertTrue(injectOnce.get(), "The source serialization boundary must actually be reached");
        assertEquals(0, matcherCalls.get(), "Only requests verified before serialization may be simulated");
        assertSame(fixture.requests(), fixture.worker().neededItems);
        assertEquals(1, fixture.requests().size());
        assertSame(injected, fixture.requests().get(0));
        assertSame(matcher, injected.matcher);
        assertEquals(32, injected.count);
        assertEquals(32, supplied.getCount());
    }

    @Test void rejectingAnUntrustedRequestPreservesItsListObjectMatcherAndCountWithoutExecutingIt() {
        var fixture = fixture();
        var calls = new AtomicInteger();
        Predicate<ItemStack> matcher = stack -> { calls.incrementAndGet(); return true; };
        var request = new NeededItem(matcher, 32, true);
        fixture.requests().add(request);
        var supplied = new ItemStack(Items.COBBLESTONE, 32);
        var source = new SimpleContainer(supplied);

        assertFalse(ProtectedTransferCapacity.trustedMatcher(matcher));
        assertEquals("Paused: native supply request provenance cannot be verified",
                ProtectedTransferCapacity.problem(fixture.worker(), source));

        assertSame(fixture.requests(), fixture.worker().neededItems);
        assertEquals(1, fixture.requests().size());
        assertSame(request, fixture.requests().get(0));
        assertSame(matcher, request.matcher);
        assertEquals(32, request.count);
        assertTrue(request.required);
        assertNull(request.sourceKey);
        assertEquals(0, calls.get());
        assertSame(supplied, source.getItem(0));
        assertEquals(32, supplied.getCount());
        verify(fixture.worker(), never()).getInventory();
    }

    // Empty real native request lists intentionally avoid inventing a trusted BuilderWorkGoal predicate.
    private static Fixture fixture() {
        var worker = mock(BuilderEntity.class);
        List<NeededItem> requests = new ArrayList<>();
        worker.neededItems = requests;
        var inventory = new RecruitSimpleContainer(15, worker);
        when(worker.getInventory()).thenReturn(inventory);
        return new Fixture(worker, inventory, requests);
    }

    private static List<ItemStack> slots(SimpleContainer inventory) {
        List<ItemStack> result = new ArrayList<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) result.add(inventory.getItem(slot));
        return result;
    }

    private record Fixture(BuilderEntity worker, RecruitSimpleContainer inventory, List<NeededItem> requests) {}

    private static final class UnsupportedInventory extends RecruitSimpleContainer {
        private final AtomicInteger calls;

        private UnsupportedInventory(BuilderEntity worker, AtomicInteger calls) {
            super(15, worker);
            this.calls = calls;
        }

        @Override public ItemStack addItem(ItemStack stack) {
            calls.incrementAndGet();
            throw new AssertionError("Unknown insertion code must not run");
        }
    }
}
