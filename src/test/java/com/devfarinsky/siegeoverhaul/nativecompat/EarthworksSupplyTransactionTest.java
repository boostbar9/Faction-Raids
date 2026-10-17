package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksManifest;
import com.talhanation.recruits.inventory.RecruitSimpleContainer;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.entities.ai.GetNeededItemsFromStorage;
import com.talhanation.workers.world.NeededItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real native transfer method with mocked world/lease authority. This is NOT a Minecraft gameplay fixture. */
class EarthworksSupplyTransactionTest extends MinecraftTestSupport {
    @Test void exactActiveMaterialIsRequestedEvenWhenAnotherNativeQueuedMaterialExists() {
        try (var fixture = new Fixture()) {
            fixture.inventory.setItem(6, new ItemStack(Items.COBBLESTONE, 32));
            assertFalse(EarthworksSupplyDemand.prepare(fixture.worker, fixture.step));
            assertEquals(1, fixture.worker.neededItems.size());
            var request = fixture.worker.neededItems.get(0);
            assertTrue(request.matches(new ItemStack(Items.DIRT)));
            assertFalse(request.matches(new ItemStack(Items.COBBLESTONE)));
            assertFalse(EarthworksSupplyDemand.prepare(fixture.worker, fixture.step));
            assertSame(request, fixture.worker.neededItems.get(0));
        }
    }
    @Test void unknownPredicatesAndCopiedNativeRequestObjectsAreNeverTrusted() {
        try (var fixture = new Fixture()) {
            var probed = new AtomicBoolean();
            fixture.worker.neededItems.add(new NeededItem(stack -> { probed.set(true); return true; }, 1, true));
            assertNotNull(EarthworksSupplyDemand.requestsProblem(fixture.worker)); assertFalse(probed.get());
            fixture.worker.neededItems.clear(); EarthworksSupplyDemand.prepare(fixture.worker, fixture.step);
            var original = fixture.worker.neededItems.get(0);
            fixture.worker.neededItems.set(0, new NeededItem(original.matcher, 1, true));
            assertNotNull(EarthworksSupplyDemand.requestsProblem(fixture.worker));
        }
    }
    @Test void nativeErrorsMayClearTheRequestButCannotCreateImmediateDuplicateDemand() {
        try (var fixture = new Fixture()) {
            EarthworksSupplyDemand.prepare(fixture.worker, fixture.step);
            UUID request = fixture.data.getCompound(EarthworksSupplyDemand.KEY).getUUID("Request");
            fixture.worker.neededItems.clear();
            assertFalse(EarthworksSupplyDemand.prepare(fixture.worker, fixture.step)); assertTrue(fixture.worker.neededItems.isEmpty());
            fixture.time.addAndGet(1200);
            assertFalse(EarthworksSupplyDemand.prepare(fixture.worker, fixture.step)); assertEquals(1, fixture.worker.neededItems.size());
            assertEquals(request, fixture.data.getCompound(EarthworksSupplyDemand.KEY).getUUID("Request"));
        }
    }
    @Test void genuineNativeTransferMovesExactlyOneAndRetainsObservedReceipt() {
        try (var fixture = new Fixture()) {
            EarthworksSupplyDemand.prepare(fixture.worker, fixture.step);
            var supplied = new ItemStack(Items.DIRT, 32); var source = new SimpleContainer(supplied);
            var transfer = EarthworksSupplyDemand.beforeTransfer(fixture.worker, source, Set.of(BlockPos.ZERO));
            assertNotNull(EarthworksSupplyDemand.requestsProblem(fixture.worker)); // Durable unresolved callback fence.
            fixture.nativeTransfer(source);
            EarthworksSupplyDemand.afterTransfer(fixture.worker, transfer);
            assertEquals(31, supplied.getCount()); assertEquals(1, fixture.inventory.getItem(6).getCount());
            assertTrue(fixture.worker.neededItems.isEmpty());
            var saved = fixture.data.getCompound(EarthworksSupplyDemand.KEY);
            assertEquals("DELIVERED", saved.getString("State")); assertEquals(1, saved.getList("Receipts", 10).size());
            assertTrue(EarthworksSupplyDemand.prepare(fixture.worker, fixture.step));
            fixture.inventory.removeItemNoUpdate(6);
            assertThrows(IllegalStateException.class, () -> EarthworksSupplyDemand.prepare(fixture.worker, fixture.step));
            assertTrue(fixture.worker.neededItems.isEmpty()); assertEquals(31, supplied.getCount());
        }
    }
    @Test void interruptedTransferAndCrossFileSnapshotMismatchCannotReplay() {
        try (var fixture = new Fixture()) {
            EarthworksSupplyDemand.prepare(fixture.worker, fixture.step);
            var source = new SimpleContainer(new ItemStack(Items.DIRT, 2));
            EarthworksSupplyDemand.beforeTransfer(fixture.worker, source, Set.of(BlockPos.ZERO));
            fixture.worker.neededItems.clear();
            assertNotNull(EarthworksSupplyDemand.requestsProblem(fixture.worker));
            assertThrows(IllegalStateException.class, () -> EarthworksSupplyDemand.prepare(fixture.worker, fixture.step));
            assertEquals(2, source.getItem(0).getCount());
            fixture.worldDigest.set("0".repeat(64));
            assertNotNull(EarthworksSupplyDemand.requestsProblem(fixture.worker));
        }
    }
    @Test void contradictoryTransferLeavesFenceAndNeverRefundsOrGrantsStock() {
        try (var fixture = new Fixture()) {
            EarthworksSupplyDemand.prepare(fixture.worker, fixture.step);
            var source = new SimpleContainer(new ItemStack(Items.DIRT, 2));
            var transfer = EarthworksSupplyDemand.beforeTransfer(fixture.worker, source, Set.of(BlockPos.ZERO));
            fixture.nativeTransfer(source); source.getItem(0).shrink(1); // Explicit fault injection, not native gameplay.
            assertThrows(IllegalStateException.class, () -> EarthworksSupplyDemand.afterTransfer(fixture.worker, transfer));
            assertEquals("TRANSFER", fixture.data.getCompound(EarthworksSupplyDemand.KEY).getString("State"));
            assertEquals(1, fixture.inventory.getItem(6).getCount()); assertTrue(source.getItem(0).isEmpty());
        }
    }
    @Test void actualOwnerDeliveryDuringTravelSettlesOnlyItsAuthenticatedRequest() {
        try (var fixture = new Fixture()) {
            EarthworksSupplyDemand.prepare(fixture.worker, fixture.step);
            fixture.inventory.setItem(6, new ItemStack(Items.DIRT)); // Labeled owner edit before native transfer.
            assertTrue(EarthworksSupplyDemand.reconcileAvailable(fixture.worker));
            assertTrue(fixture.worker.neededItems.isEmpty());
            assertEquals("AVAILABLE", fixture.data.getCompound(EarthworksSupplyDemand.KEY).getString("State"));
            assertTrue(fixture.data.getCompound(EarthworksSupplyDemand.KEY).getList("Receipts", 10).isEmpty());
            assertTrue(EarthworksSupplyDemand.prepare(fixture.worker, fixture.step));
        }
    }
    @Test void fullInventoryCannotPartiallyExtractOrFabricateAReplacementSlot() {
        try (var fixture = new Fixture()) {
            EarthworksSupplyDemand.prepare(fixture.worker, fixture.step);
            for (int i = 6; i < 15; i++) fixture.inventory.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            var source = new SimpleContainer(new ItemStack(Items.DIRT, 2));
            assertNotNull(ProtectedTransferCapacity.problem(fixture.worker, source));
            assertThrows(IllegalStateException.class, () -> EarthworksSupplyDemand.beforeTransfer(fixture.worker, source, Set.of(BlockPos.ZERO)));
            assertEquals(2, source.getItem(0).getCount());
        }
    }

    private static final class Fixture implements AutoCloseable {
        final BuilderEntity worker = mock(BuilderEntity.class);
        final RecruitSimpleContainer inventory = new RecruitSimpleContainer(15, worker);
        final CompoundTag data = new CompoundTag();
        final AtomicReference<String> worldDigest = new AtomicReference<>("");
        final AtomicLong time = new AtomicLong(5000);
        final PerimeterEarthworksManifest.Step step = new PerimeterEarthworksManifest.Step(0, PerimeterEarthworksManifest.Kind.FILL,
                BlockPos.ZERO.asLong(), Blocks.AIR.defaultBlockState(), Blocks.DIRT.defaultBlockState(), null);
        final MockedStatic<NativeEarthworksJobs> jobs = mockStatic(NativeEarthworksJobs.class);
        Fixture() {
            var lease = new NativeEarthworksJobs.InventoryLease(UUID.randomUUID(), 1, UUID.randomUUID(), "a".repeat(64), "b".repeat(64),
                    0, step, UUID.randomUUID(), "qa", BlockPos.ZERO);
            worker.neededItems = new ArrayList<>();
            when(worker.getInventory()).thenReturn(inventory); when(worker.getPersistentData()).thenReturn(data);
            when(worker.getUseItem()).thenReturn(ItemStack.EMPTY); when(worker.getMainHandItem()).thenAnswer(ignored -> inventory.getItem(5));
            var level = mock(Level.class); when(level.getGameTime()).thenAnswer(ignored -> time.get());
            when(worker.level()).thenReturn(level); when(worker.getCommandSenderWorld()).thenReturn(level);
            when(worker.getLookControl()).thenReturn(mock(LookControl.class));
            jobs.when(() -> NativeEarthworksJobs.selected(worker)).thenReturn(true);
            jobs.when(() -> NativeEarthworksJobs.inventoryLease(worker)).thenReturn(lease);
            jobs.when(() -> NativeEarthworksJobs.supplyDigest(worker)).thenAnswer(ignored -> worldDigest.get());
            jobs.when(() -> NativeEarthworksJobs.compareSupplyDigest(eq(worker), anyString(), anyString()))
                    .thenAnswer(invocation -> {
                        if (!worldDigest.get().equals(invocation.getArgument(1))) return false;
                        worldDigest.set(invocation.getArgument(2)); return true;
                    });
        }
        void nativeTransfer(SimpleContainer source) {
            var goal = new GetNeededItemsFromStorage(worker); goal.chestPos = BlockPos.ZERO; goal.container = source;
            goal.setState(GetNeededItemsFromStorage.State.TAKE_NEEDED_ITEMS); goal.tick();
            assertEquals(GetNeededItemsFromStorage.State.CLOSE_CHEST_DONE, goal.state);
        }
        @Override public void close() { jobs.close(); }
    }
}
