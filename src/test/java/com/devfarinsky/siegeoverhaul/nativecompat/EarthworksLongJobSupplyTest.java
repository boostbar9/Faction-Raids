package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksManifest;
import com.talhanation.recruits.inventory.RecruitSimpleContainer;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.entities.ai.DepositItemsToStorage;
import com.talhanation.workers.entities.ai.GetNeededItemsFromStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Native transfer/swap method tests with mocked world, permissions and hand setter. Not gameplay proof. */
class EarthworksLongJobSupplyTest extends MinecraftTestSupport {
    @Test void observedNativeMaterialReturnAllowsOneNewRequestButDisappearanceDoesNot() {
        try (var f = new Fixture(false)) {
            f.requestAndFetch(new SimpleContainer(new ItemStack(Items.DIRT, 2)));
            UUID original = f.saved().getUUID("Request");
            var destination = new SimpleContainer(2);
            var deposit = EarthworksSupplyDemand.beforeDeposit(f.worker, destination, Set.of(BlockPos.ZERO));
            f.nativeDeposit(destination); EarthworksSupplyDemand.afterDeposit(f.worker, destination, deposit);
            assertEquals("RETURNED", f.saved().getString("State")); assertEquals(1, destination.getItem(0).getCount());
            assertEquals(1, f.saved().getList("Operations", 10).size());
            assertFalse(EarthworksSupplyDemand.prepare(f.worker, f.step)); assertEquals(1, f.worker.neededItems.size());
            assertNotEquals(original, f.saved().getUUID("Request"));
            assertNull(EarthworksSupplyDemand.requestsProblem(f.worker));
        }
    }
    @Test void depositWhileRequestIsStillPendingPreservesThatExactRequest() {
        try (var f = new Fixture(false)) {
            EarthworksSupplyDemand.prepare(f.worker, f.step); var request = f.worker.neededItems.get(0);
            f.inventory.setItem(6, new ItemStack(Items.DIRT)); // Explicit owner-edit setup.
            var destination = new SimpleContainer(2);
            var deposit = EarthworksSupplyDemand.beforeDeposit(f.worker, destination, Set.of(BlockPos.ZERO));
            f.nativeDeposit(destination); EarthworksSupplyDemand.afterDeposit(f.worker, destination, deposit);
            assertEquals("REQUESTED", f.saved().getString("State")); assertSame(request, f.worker.neededItems.get(0));
            assertNull(EarthworksSupplyDemand.requestsProblem(f.worker));
        }
    }
    @Test void lastDurabilityRetrievesFreshSwapsNativelyAndReturnsWornBeforeMiningCanResume() {
        try (var f = new Fixture(true)) {
            var worn = f.putWornInHand(); CompoundTag wornValue = worn.save(new CompoundTag());
            var supply = new SimpleContainer(new ItemStack(Items.IRON_SHOVEL));
            f.requestAndFetch(supply);
            assertEquals(wornValue, worn.save(new CompoundTag())); assertSame(worn, f.worker.getMainHandItem());
            assertFalse(EarthworksSupplyDemand.prepare(f.worker, f.step));
            assertTrue(f.worker.forcedDeposit); assertEquals(0, f.worker.getMainHandItem().getDamageValue());
            assertSame(worn, f.inventory.getItem(6)); assertEquals("RETURN", f.saved().getCompound("Tool").getString("Phase"));
            var destination = new SimpleContainer(2); destination.setItem(0, new ItemStack(Items.IRON_SHOVEL));
            // The existing fresh tool fills its max-stack-one slot; the worn tool must use another actual slot.
            assertNull(ProtectedTransferCapacity.depositProblem(f.worker, destination));
            f.jobs.when(() -> NativeEarthworksJobs.selected(f.worker)).thenReturn(false);
            assertNotNull(ProtectedTransferCapacity.depositProblem(f.worker, destination)); // The v1 guard is unchanged.
            f.jobs.when(() -> NativeEarthworksJobs.selected(f.worker)).thenReturn(true);
            var deposit = EarthworksSupplyDemand.beforeDeposit(f.worker, destination, Set.of(BlockPos.ZERO));
            f.nativeDeposit(destination); EarthworksSupplyDemand.afterDeposit(f.worker, destination, deposit);
            assertEquals("DONE", f.saved().getCompound("Tool").getString("Phase"));
            assertEquals(wornValue, destination.getItem(1).save(new CompoundTag()));
            assertTrue(EarthworksSupplyDemand.prepare(f.worker, f.step));
            assertEquals(2, f.saved().getList("Operations", 10).size());
            verify(f.worker, never()).damageMainHandItem(); // The breaking native path was never called.
            verify(f.worker, never()).tryToReequip(any());
        }
    }
    @Test void fullStorageRetainsBothToolsAndCannotClaimReturnOrCreateAnotherFreshTool() {
        try (var f = new Fixture(true)) {
            var worn = f.putWornInHand(); f.requestAndFetch(new SimpleContainer(new ItemStack(Items.IRON_SHOVEL)));
            EarthworksSupplyDemand.prepare(f.worker, f.step);
            var full = new SimpleContainer(new ItemStack(Items.COBBLESTONE, 64));
            var deposit = EarthworksSupplyDemand.beforeDeposit(f.worker, full, Set.of(BlockPos.ZERO));
            f.nativeDeposit(full); EarthworksSupplyDemand.afterDeposit(f.worker, full, deposit);
            assertSame(worn, f.inventory.getItem(6)); assertEquals("RETURN", f.saved().getCompound("Tool").getString("Phase"));
            assertEquals(1, f.saved().getList("Operations", 10).size()); // Only the actual swap.
            assertFalse(EarthworksSupplyDemand.prepare(f.worker, f.step)); assertTrue(f.worker.neededItems.isEmpty());
            assertEquals(64, full.getItem(0).getCount());
        }
    }
    @Test void aThirdShovelOrEditedWornValueCannotBeDiscardedByReplacement() {
        try (var f = new Fixture(true)) {
            var worn = f.putWornInHand(); f.requestAndFetch(new SimpleContainer(new ItemStack(Items.IRON_SHOVEL)));
            f.inventory.setItem(7, new ItemStack(Items.IRON_SHOVEL));
            assertThrows(IllegalStateException.class, () -> EarthworksSupplyDemand.prepare(f.worker, f.step));
            assertSame(worn, f.worker.getMainHandItem()); assertFalse(f.worker.forcedDeposit);
            f.inventory.setItem(7, ItemStack.EMPTY); worn.getOrCreateTag().putString("OwnerEdit", "keep this value");
            assertThrows(IllegalStateException.class, () -> EarthworksSupplyDemand.prepare(f.worker, f.step));
            assertEquals("keep this value", worn.getTag().getString("OwnerEdit"));
        }
    }
    @Test void sleepAndMountedStatePreventTheNativeSwapWithoutClearingOrBreakingTools() {
        try (var f = new Fixture(true)) {
            var worn = f.putWornInHand(); f.requestAndFetch(new SimpleContainer(new ItemStack(Items.IRON_SHOVEL)));
            when(f.worker.needsToSleep()).thenReturn(true);
            assertFalse(EarthworksSupplyDemand.prepare(f.worker, f.step)); assertSame(worn, f.worker.getMainHandItem());
            when(f.worker.needsToSleep()).thenReturn(false); when(f.worker.isPassenger()).thenReturn(true);
            assertFalse(EarthworksSupplyDemand.prepare(f.worker, f.step)); assertSame(worn, f.worker.getMainHandItem());
            verify(f.worker, never()).switchMainHandItem(any());
        }
    }
    @Test void aFailedNativeSwapRetainsItsFenceAndDoesNotRetryOrCompensate() {
        try (var f = new Fixture(true)) {
            var worn = f.putWornInHand(); f.requestAndFetch(new SimpleContainer(new ItemStack(Items.IRON_SHOVEL)));
            doNothing().when(f.worker).switchMainHandItem(any()); // Explicit fault injection.
            assertThrows(IllegalStateException.class, () -> EarthworksSupplyDemand.prepare(f.worker, f.step));
            assertEquals("SWAP", f.saved().getString("State")); assertSame(worn, f.worker.getMainHandItem());
            assertTrue(ProtectedBuilderHandMirror.reviewNeeded(f.data));
            assertThrows(IllegalStateException.class, () -> EarthworksSupplyDemand.prepare(f.worker, f.step));
            verify(f.worker, times(1)).switchMainHandItem(any());
        }
    }
    @Test void unobservedDepositSaveCannotBeReplayedOrInferredFromInventoryAbsence() {
        try (var f = new Fixture(false)) {
            f.requestAndFetch(new SimpleContainer(new ItemStack(Items.DIRT)));
            var destination = new SimpleContainer(2);
            EarthworksSupplyDemand.beforeDeposit(f.worker, destination, Set.of(BlockPos.ZERO));
            f.nativeDeposit(destination); // Deliberately do not record an outcome, as at an interrupted callback.
            when(f.worker.getPersistentData()).thenReturn(f.data.copy());
            assertNotNull(EarthworksSupplyDemand.requestsProblem(f.worker));
            assertThrows(IllegalStateException.class, () -> EarthworksSupplyDemand.prepare(f.worker, f.step));
            assertEquals(1, destination.getItem(0).getCount()); assertTrue(f.worker.neededItems.isEmpty());
        }
    }
    @Test void unrelatedDepositLossAndWorldDigestRollbackLeaveUnresolvedEvidence() {
        try (var f = new Fixture(false)) {
            f.requestAndFetch(new SimpleContainer(new ItemStack(Items.DIRT)));
            var destination = new SimpleContainer(2);
            var deposit = EarthworksSupplyDemand.beforeDeposit(f.worker, destination, Set.of(BlockPos.ZERO));
            f.nativeDeposit(destination); destination.getItem(0).shrink(1); // Explicit fault injection.
            assertThrows(IllegalStateException.class, () -> EarthworksSupplyDemand.afterDeposit(f.worker, destination, deposit));
            assertEquals("DEPOSIT", f.saved().getString("State"));
            f.worldDigest.set("a".repeat(64));
            assertNotNull(EarthworksSupplyDemand.requestsProblem(f.worker));
        }
    }
    @Test void oldSupplySnapshotCanBeReadAndUpgradedOnlyThroughWorldDigestComparison() {
        try (var f = new Fixture(false)) {
            EarthworksSupplyDemand.prepare(f.worker, f.step);
            var old = f.saved().copy(); old.putInt("Version", 1); old.remove("Operations"); old.remove("Tool"); old.remove("Resume");
            f.data.put(EarthworksSupplyDemand.KEY, old); f.worldDigest.set(EarthworksInventoryEvidence.digest(WorkersEarthworksPort.canonical(old)));
            assertNull(EarthworksSupplyDemand.requestsProblem(f.worker));
            f.inventory.setItem(6, new ItemStack(Items.DIRT)); EarthworksSupplyDemand.reconcileAvailable(f.worker);
            assertEquals(2, f.saved().getInt("Version")); assertTrue(f.saved().getList("Operations", 10).isEmpty());
        }
    }

    @Test void partialNativeDepositRetainsActualCargoAndDoesNotRequestAnotherDelivery() {
        try (var f = new Fixture(false)) {
            f.requestAndFetch(new SimpleContainer(new ItemStack(Items.DIRT)));
            // Native isContainerFull requires an empty slot before its merge loop. Earlier unrelated cargo
            // fills that last slot; the later dirt stack can then merge only one item and retains its remainder.
            f.inventory.setItem(6,new ItemStack(Items.COBBLESTONE,64));
            f.inventory.setItem(7,new ItemStack(Items.DIRT,64)); // Explicit owner-stock setup only.
            var destination = new SimpleContainer(2);destination.setItem(0,new ItemStack(Items.DIRT,63));
            var deposit = EarthworksSupplyDemand.beforeDeposit(f.worker, destination, Set.of(BlockPos.ZERO));
            f.nativeDeposit(destination); EarthworksSupplyDemand.afterDeposit(f.worker, destination, deposit);
            assertEquals(64, destination.getItem(0).getCount()); assertEquals(63, f.inventory.getItem(7).getCount());
            assertTrue(f.inventory.getItem(6).isEmpty());assertEquals(64,destination.getItem(1).getCount());
            assertTrue(EarthworksSupplyDemand.prepare(f.worker, f.step)); assertTrue(f.worker.neededItems.isEmpty());
            assertEquals("DELIVERED", f.saved().getString("State"));
        }
    }
    @Test void nativeOccupiedSlotsGatePreservesMergeRoomWithoutClaimingAnObservedReturn() {
        try(var f=new Fixture(false)) {
            f.requestAndFetch(new SimpleContainer(new ItemStack(Items.DIRT)));
            f.inventory.getItem(6).setCount(64);var destination=new SimpleContainer(new ItemStack(Items.DIRT,63));
            var deposit=EarthworksSupplyDemand.beforeDeposit(f.worker,destination,Set.of(BlockPos.ZERO));
            f.nativeDeposit(destination);EarthworksSupplyDemand.afterDeposit(f.worker,destination,deposit);
            assertEquals(63,destination.getItem(0).getCount());assertEquals(64,f.inventory.getItem(6).getCount());
            assertTrue(f.saved().getList("Operations",10).isEmpty());assertTrue(f.worker.neededItems.isEmpty());
            assertTrue(EarthworksSupplyDemand.prepare(f.worker,f.step));
        }
    }
    @Test void aWornCargoToolCanBeReplacedWhileTheNativeSwapPreservesTheOldHand() {
        try (var f = new Fixture(true)) {
            var oldHand = new ItemStack(Items.COBBLESTONE, 3); f.inventory.setItem(5, oldHand);
            var worn = new ItemStack(Items.IRON_SHOVEL); worn.setDamageValue(worn.getMaxDamage() - 1); f.inventory.setItem(7, worn);
            f.requestAndFetch(new SimpleContainer(new ItemStack(Items.IRON_SHOVEL)));
            assertFalse(EarthworksSupplyDemand.prepare(f.worker, f.step));
            assertSame(oldHand, f.inventory.getItem(6)); assertEquals(3, oldHand.getCount());
            assertSame(worn, f.inventory.getItem(7)); assertEquals(0, f.worker.getMainHandItem().getDamageValue());
        }
    }
    @Test void occupiedDepositMergeRoomWithDifferentFullMetadataIsStillRefused() {
        try (var f = new Fixture(false)) {
            f.requestAndFetch(new SimpleContainer(new ItemStack(Items.DIRT)));
            var named = new ItemStack(Items.DIRT, 1); named.getOrCreateTag().putString("OwnerNote", "do not erase");
            var destination = new SimpleContainer(named);
            assertNotNull(ProtectedTransferCapacity.depositProblem(f.worker, destination));
            assertEquals("do not erase", named.getTag().getString("OwnerNote"));
            assertEquals(1, f.inventory.getItem(6).getCount());
        }
    }
    @Test void sourceInventoryAliasesAreRejectedBeforeTheNativeDepositFence() {
        try (var f = new Fixture(false)) {
            f.requestAndFetch(new SimpleContainer(new ItemStack(Items.DIRT)));
            var shared = f.inventory.getItem(6); var destination = new SimpleContainer(shared);
            assertThrows(IllegalStateException.class, () -> EarthworksSupplyDemand.beforeDeposit(f.worker, destination, Set.of(BlockPos.ZERO)));
            assertEquals("DELIVERED", f.saved().getString("State")); assertEquals(1, shared.getCount());
        }
    }

    @Test void multipleLargeCargoValuesRefuseBeforeAnyNativeDepositWhenCompleteReceiptWouldOverflow() {
        try(var f=new Fixture(false)) {
            f.requestAndFetch(new SimpleContainer(new ItemStack(Items.DIRT)));
            f.inventory.setItem(7,tagged(Items.COBBLESTONE,6950));f.inventory.setItem(8,tagged(Items.OAK_PLANKS,6950));
            var destination=new SimpleContainer(4);var before=f.saved().copy();String digest=f.worldDigest.get();
            var inventory=EarthworksInventoryEvidence.inventory(f.worker);
            int oldEstimate=2048;for(int i=6;i<inventory.slots().size();i++)if(inventory.slots().get(i).count()>0)
                oldEstimate+=WorkersEarthworksPort.canonical(inventory.slots().get(i).data()).length()+64;
            assertTrue(oldEstimate<=EarthworksSupplyOperations.MAX_RECORD_BYTES,"Reproduces the old underestimated preflight");
            assertThrows(IllegalStateException.class,()->EarthworksSupplyDemand.beforeDeposit(f.worker,destination,Set.of(BlockPos.ZERO)));
            assertEquals(before,f.saved());assertEquals(digest,f.worldDigest.get());EarthworksInventoryEvidence.unchanged(inventory);
            for(int i=0;i<destination.getContainerSize();i++)assertTrue(destination.getItem(i).isEmpty());
        }
    }
    @Test void admittedLargeMultiValueNativeDepositFitsTheExactReservedReceiptAndHistory() {
        try(var f=new Fixture(false)) {
            f.requestAndFetch(new SimpleContainer(new ItemStack(Items.DIRT)));
            f.inventory.setItem(7,tagged(Items.COBBLESTONE,5900));f.inventory.setItem(8,tagged(Items.OAK_PLANKS,5900));
            var expected=EarthworksInventoryEvidence.inventory(f.worker).totals();var destination=new SimpleContainer(4);
            var deposit=EarthworksSupplyDemand.beforeDeposit(f.worker,destination,Set.of(BlockPos.ZERO,new BlockPos(1,0,0)));
            f.nativeDeposit(destination);EarthworksSupplyDemand.afterDeposit(f.worker,destination,deposit);
            var operations=f.saved().getList("Operations",10);assertEquals(1,operations.size());
            assertTrue(WorkersEarthworksPort.canonical(operations.getCompound(0)).length()<=EarthworksSupplyOperations.MAX_RECORD_BYTES);
            assertTrue(WorkersEarthworksPort.canonical(operations).length()<=EarthworksSupplyOperations.MAX_HISTORY_BYTES);
            assertEquals(expected,EarthworksInventoryEvidence.capture(destination,new IdentityHashMap<>()).totals());
            assertTrue(EarthworksInventoryEvidence.inventory(f.worker).totals().isEmpty());
        }
    }
    private static ItemStack tagged(net.minecraft.world.item.Item item,int maximumCanonicalLength) {
        var stack=new ItemStack(item);int chars=0;
        while(true){stack.getOrCreateTag().putString("OwnerNote","x".repeat(chars));
            if(WorkersEarthworksPort.canonical(stack.save(new CompoundTag())).length()>maximumCanonicalLength){
                stack.getOrCreateTag().putString("OwnerNote","x".repeat(chars-1));return stack;
            }chars++;
        }
    }

    private static final class Fixture implements AutoCloseable {
        final BuilderEntity worker = mock(BuilderEntity.class);
        final RecruitSimpleContainer inventory = new RecruitSimpleContainer(15, worker);
        final CompoundTag data = new CompoundTag();
        final AtomicReference<String> worldDigest = new AtomicReference<>("");
        final AtomicLong time = new AtomicLong(5000);
        final PerimeterEarthworksManifest.Step step;
        final MockedStatic<NativeEarthworksJobs> jobs = mockStatic(NativeEarthworksJobs.class);
        Fixture(boolean cut) {
            step = cut ? new PerimeterEarthworksManifest.Step(0, PerimeterEarthworksManifest.Kind.CUT, 0,
                    Blocks.DIRT.defaultBlockState(), Blocks.AIR.defaultBlockState(), new PerimeterEarthworksManifest.Removal(
                    PerimeterEarthworksManifest.Origin.UNKNOWN, PerimeterEarthworksManifest.Family.SOIL,
                    NativeEarthworksAdapter.DIRT_ADAPTER, NativeEarthworksAdapter.DIRT_VERSION, NativeEarthworksAdapter.DIRT_SOURCE))
                    : new PerimeterEarthworksManifest.Step(0, PerimeterEarthworksManifest.Kind.FILL, 0,
                    Blocks.AIR.defaultBlockState(), Blocks.DIRT.defaultBlockState(), null);
            var lease = new NativeEarthworksJobs.InventoryLease(UUID.randomUUID(), 1, UUID.randomUUID(), "a".repeat(64), "b".repeat(64),
                    0, step, UUID.randomUUID(), "qa", BlockPos.ZERO);
            worker.neededItems = new ArrayList<>();
            when(worker.getInventory()).thenReturn(inventory); when(worker.getPersistentData()).thenReturn(data);
            when(worker.getUseItem()).thenReturn(ItemStack.EMPTY); when(worker.getMainHandItem()).thenAnswer(ignored -> inventory.getItem(5));
            when(worker.isAlive()).thenReturn(true); when(worker.shouldWork()).thenReturn(true);
            var level = mock(Level.class); when(level.getGameTime()).thenAnswer(ignored -> time.get());
            when(worker.level()).thenReturn(level); when(worker.getCommandSenderWorld()).thenReturn(level);
            when(worker.getLookControl()).thenReturn(mock(LookControl.class));
            // Only the Workers switch body is real here; the native entity hand setter needs genuine-runtime QA separately.
            doCallRealMethod().when(worker).switchMainHandItem(any());
            doAnswer(invocation -> { inventory.setItem(5, invocation.getArgument(1)); return null; })
                    .when(worker).setItemInHand(eq(InteractionHand.MAIN_HAND), any());
            jobs.when(() -> NativeEarthworksJobs.selected(worker)).thenReturn(true);
            jobs.when(() -> NativeEarthworksJobs.inventoryLease(worker)).thenReturn(lease);
            jobs.when(() -> NativeEarthworksJobs.supplyDigest(worker)).thenAnswer(ignored -> worldDigest.get());
            jobs.when(() -> NativeEarthworksJobs.compareSupplyDigest(eq(worker), anyString(), anyString())).thenAnswer(invocation -> {
                if (!worldDigest.get().equals(invocation.getArgument(1))) return false;
                worldDigest.set(invocation.getArgument(2)); return true;
            });
        }
        CompoundTag saved() { return data.getCompound(EarthworksSupplyDemand.KEY); }
        ItemStack putWornInHand() {
            var worn = new ItemStack(Items.IRON_SHOVEL); worn.setDamageValue(worn.getMaxDamage() - 1); inventory.setItem(5, worn); return worn;
        }
        void requestAndFetch(SimpleContainer source) {
            assertFalse(EarthworksSupplyDemand.prepare(worker, step));
            var transfer = EarthworksSupplyDemand.beforeTransfer(worker, source, Set.of(BlockPos.ZERO));
            var goal = new GetNeededItemsFromStorage(worker); goal.chestPos = BlockPos.ZERO; goal.container = source;
            goal.setState(GetNeededItemsFromStorage.State.TAKE_NEEDED_ITEMS); goal.tick();
            EarthworksSupplyDemand.afterTransfer(worker, transfer);
        }
        void nativeDeposit(SimpleContainer destination) {
            var goal = new DepositItemsToStorage(worker); goal.chestPos = BlockPos.ZERO; goal.container = destination;
            goal.setState(DepositItemsToStorage.State.DEPOSIT); goal.tick();
            assertTrue(goal.state.name().startsWith("CLOSE_CHEST_"));
        }
        @Override public void close() { jobs.close(); }
    }
}
