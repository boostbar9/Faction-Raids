package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConstructionEditLedgerTest extends MinecraftTestSupport {
    @Test void onlyProtectedCellsAreTrackedAcrossUnloadedMarkerAndReload() {
        var ledger = new ConstructionEditLedger(); UUID id = UUID.randomUUID();
        Set<BlockPos> cells = Set.of(new BlockPos(-30, 65, 40), new BlockPos(10, 65, 40));
        assertTrue(ledger.register(id, cells));
        ledger.record(BlockPos.ZERO); assertFalse(ledger.edited(id));
        var loaded = ConstructionEditLedger.load(ledger.save(new CompoundTag()));
        assertTrue(loaded.matches(id, cells));
        loaded.record(new BlockPos(10, 65, 40));
        loaded = ConstructionEditLedger.load(loaded.save(new CompoundTag()));
        assertTrue(loaded.edited(id));
        assertFalse(loaded.matches(id, Set.of(BlockPos.ZERO)));
    }

    @Test void sameStateReplacementHistoryCannotBeResetByRegisteringAgain() {
        var ledger = new ConstructionEditLedger(); UUID id = UUID.randomUUID();
        assertTrue(ledger.register(id, Set.of(BlockPos.ZERO)));
        ledger.record(BlockPos.ZERO);
        assertFalse(ledger.register(id, Set.of(BlockPos.ZERO)));
        assertTrue(ledger.edited(id));
    }

    @Test void destroyedJobsReleaseBoundedIndexButOverlappingJobsKeepTheirHistory() {
        var ledger = new ConstructionEditLedger(); UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertTrue(ledger.register(a, Set.of(BlockPos.ZERO)));
        assertTrue(ledger.register(b, Set.of(BlockPos.ZERO)));
        ledger.remove(a); ledger.record(BlockPos.ZERO);
        assertFalse(ledger.contains(a)); assertTrue(ledger.edited(b));
        ledger.remove(b); assertFalse(ledger.contains(b));
        assertTrue(ledger.register(b, Set.of(BlockPos.ZERO))); assertFalse(ledger.edited(b));
    }

    @Test void malformedAndMissingLedgersFailClosed() {
        CompoundTag root = new CompoundTag(); root.putBoolean("Invalid", true);
        var invalid = ConstructionEditLedger.load(root);
        UUID id = UUID.randomUUID();
        assertFalse(invalid.register(id, Set.of(BlockPos.ZERO)));
        assertFalse(invalid.contains(id)); assertTrue(invalid.edited(id));
        assertTrue(new ConstructionEditLedger().edited(id));
    }

    @Test void registrationBudgetIsEnforcedAndRecoverableAfterJobRemoval() {
        var ledger = new ConstructionEditLedger(); UUID first = UUID.randomUUID();
        assertTrue(ledger.register(first, Set.of(BlockPos.ZERO)));
        for (int i = 1; i < ConstructionEditLedger.MAX_JOBS; i++)
            assertTrue(ledger.register(UUID.randomUUID(), Set.of(new BlockPos(i, 64, 0))));
        assertFalse(ledger.register(UUID.randomUUID(), Set.of(new BlockPos(99, 64, 0))));
        ledger.remove(first);
        assertTrue(ledger.register(UUID.randomUUID(), Set.of(new BlockPos(99, 64, 0))));
    }
}
