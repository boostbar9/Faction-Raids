package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CivilianReportTest extends MinecraftTestSupport {
    @Test void watchRequiresCurrentValidOwnerAndMonotonicRequestWithoutResettingTheThrottle() throws Exception {
        var menu = mock(CoreHireMenu.class, CALLS_REAL_METHODS);
        var owner = mock(net.minecraft.server.level.ServerPlayer.class);
        var ownerField = CoreHireMenu.class.getDeclaredField("owner"); ownerField.setAccessible(true); ownerField.set(menu, owner);
        var request = CoreHireMenu.class.getDeclaredField("civilianRequest"); request.setAccessible(true);
        var at = CoreHireMenu.class.getDeclaredField("civiliansAt"); at.setAccessible(true);
        owner.containerMenu = menu;
        doReturn(false).when(menu).stillValid(owner);
        menu.watchCivilians(owner, 1, true); assertEquals(0L, request.getLong(menu));
        doReturn(true).when(menu).stillValid(owner);
        menu.watchCivilians(mock(net.minecraft.server.level.ServerPlayer.class), 1, true);
        assertEquals(0L, request.getLong(menu));
        owner.containerMenu = mock(net.minecraft.world.inventory.AbstractContainerMenu.class);
        menu.watchCivilians(owner, 1, true); assertEquals(0L, request.getLong(menu));
        owner.containerMenu = menu;
        at.setLong(menu, Long.MIN_VALUE);
        menu.watchCivilians(owner, 1, true); assertEquals(1L, request.getLong(menu));
        assertEquals(Long.MIN_VALUE, at.getLong(menu));
        at.setLong(menu, 120L); menu.watchCivilians(owner, 2, true); assertEquals(120L, at.getLong(menu));
        menu.watchCivilians(owner, 1, false); assertEquals(2L, request.getLong(menu));
        menu.watchCivilians(owner, 3, false); menu.watchCivilians(owner, 4, true);
        assertEquals(120L, at.getLong(menu), "Stop/start must not reset the scan budget");
    }

    @Test void closingChangingTabsAndReopeningRejectOldReportEpochsAndClearNames() {
        var menu = mock(CoreHireMenu.class, CALLS_REAL_METHODS);
        var snapshot = new CivilianReport.Snapshot(List.of(CivilianReport.Resident.unavailable(UUID.randomUUID(), false)), true);
        menu.expectCivilianReport(1, true); assertTrue(menu.civilianReport(1, snapshot)); assertSame(snapshot, menu.civilianReport());
        menu.expectCivilianReport(2, false); assertNull(menu.civilianReport()); assertFalse(menu.civilianReport(1, snapshot));
        menu.expectCivilianReport(3, true); assertFalse(menu.civilianReport(1, snapshot)); assertNull(menu.civilianReport());
        assertTrue(menu.civilianReport(3, snapshot));
        var reopened = mock(CoreHireMenu.class, CALLS_REAL_METHODS);
        reopened.expectCivilianReport(4, true); assertFalse(reopened.civilianReport(3, snapshot)); assertNull(reopened.civilianReport());
    }

    @Test void unloadedResidentsKeepOnlySavedIdentityAndPausedStatusWithoutMutatingLedger() {
        var ledger = new CompoundTag(); UUID id = UUID.randomUUID();
        CivilianLedger.register(ledger, id, 100); CivilianLedger.pause(ledger, id, true, 200);
        var before = ledger.copy(); var lookedUp = new ArrayList<UUID>();
        var report = CoreCivilians.snapshot(ledger, "team:test", value -> { lookedUp.add(value); return null; }, false);
        assertEquals(before, ledger); assertEquals(List.of(id), lookedUp);
        assertFalse(report.taxEligible()); assertEquals(0, report.loaded());
        var resident = report.residents().get(0);
        assertFalse(resident.loaded()); assertTrue(resident.paused()); assertEquals("", resident.name());
        assertFalse(resident.bed()); assertFalse(resident.workstation());
        assertEquals("Details unavailable", resident.status());
    }

    @Test @SuppressWarnings("unchecked") void loadedDetailsComeFromOwnedNativeVillagerOnly() {
        var ledger = new CompoundTag(); UUID id = UUID.randomUUID(); CivilianLedger.register(ledger, id, 0);
        var nativeVillager = mock(Villager.class); var tag = new CompoundTag();
        tag.putString("SiegeCivilianFaction", "team:test"); tag.putString("SiegeCivilianName", "Mira");
        when(nativeVillager.getPersistentData()).thenReturn(tag); when(nativeVillager.isAlive()).thenReturn(true);
        when(nativeVillager.getVillagerData()).thenReturn(new VillagerData(VillagerType.DESERT, VillagerProfession.LIBRARIAN, 3));
        when(nativeVillager.isBaby()).thenReturn(true);
        Brain<Villager> brain = mock(Brain.class); when(nativeVillager.getBrain()).thenReturn(brain);
        when(brain.getMemory(MemoryModuleType.HOME)).thenReturn(Optional.empty());
        when(brain.getMemory(MemoryModuleType.JOB_SITE)).thenReturn(Optional.of(net.minecraft.core.GlobalPos.of(
                net.minecraft.world.level.Level.OVERWORLD, net.minecraft.core.BlockPos.ZERO)));
        var report = CoreCivilians.snapshot(ledger, "team:test", ignored -> nativeVillager, true);
        var resident = report.residents().get(0);
        assertEquals(1, report.loaded()); assertEquals("Mira", resident.label()); assertTrue(resident.baby());
        assertEquals(new ResourceLocation("minecraft", "librarian"), resident.profession());
        assertEquals(new ResourceLocation("minecraft", "desert"), resident.type());
        assertEquals(3, resident.level()); assertFalse(resident.bed()); assertTrue(resident.workstation());
        tag.putString("SiegeCivilianFaction", "team:other");
        var foreign = CoreCivilians.snapshot(ledger, "team:test", ignored -> nativeVillager, true).residents().get(0);
        assertFalse(foreign.loaded()); assertEquals("Details unavailable", foreign.status());
        assertEquals("", foreign.name());
        tag.putString("SiegeCivilianFaction", "team:test"); when(nativeVillager.isAlive()).thenReturn(false);
        var removed = CoreCivilians.snapshot(ledger, "team:test", ignored -> nativeVillager, true).residents().get(0);
        assertFalse(removed.loaded()); assertEquals("Details unavailable", removed.status());
        assertEquals("", removed.name());
    }

    @Test void emptyReportsDoNotCreateResidentsAndCorruptExtraEntriesStayBounded() {
        var empty = new CompoundTag();
        assertTrue(CoreCivilians.snapshot(empty, "team:test", ignored -> { fail("Empty ledger must not lookup"); return null; }, true).residents().isEmpty());
        assertTrue(empty.isEmpty());
        var residents = new CompoundTag();
        for (int i = 0; i < 100; i++) residents.putLong(new UUID(0, i + 1).toString(), i);
        empty.put("Residents", residents); var calls = new java.util.concurrent.atomic.AtomicInteger();
        assertEquals(64, CoreCivilians.snapshot(empty, "team:test", ignored -> { calls.incrementAndGet(); return null; }, true).residents().size());
        assertEquals(64, calls.get());
    }

    @Test void boundedWireRoundTripsAndRejectsDuplicateIdentitiesTrailingDataAndInvalidCounts() {
        var resident = CivilianReport.Resident.unavailable(UUID.randomUUID(), true);
        var snapshot = new CivilianReport.Snapshot(List.of(resident), false);
        var b = new FriendlyByteBuf(Unpooled.buffer());
        try { snapshot.write(b); assertEquals(snapshot, CivilianReport.Snapshot.read(b)); }
        finally { b.release(); }
        var extra = new FriendlyByteBuf(Unpooled.buffer());
        try { snapshot.write(extra); extra.writeByte(1); assertThrows(IllegalArgumentException.class, () -> CivilianReport.Snapshot.read(extra)); }
        finally { extra.release(); }
        for (int count : new int[]{-1, 65, Integer.MAX_VALUE}) {
            var bad = new FriendlyByteBuf(Unpooled.buffer());
            try { bad.writeBoolean(true); bad.writeVarInt(count); assertThrows(IllegalArgumentException.class, () -> CivilianReport.Snapshot.read(bad)); }
            finally { bad.release(); }
        }
        assertThrows(IllegalArgumentException.class, () -> new CivilianReport.Snapshot(List.of(resident, resident), true));
        assertThrows(IllegalArgumentException.class, () -> new CivilianReport.Resident(resident.id(), "Invented",
                resident.profession(), resident.type(), 1, false, false, false, false, false));
    }
}
