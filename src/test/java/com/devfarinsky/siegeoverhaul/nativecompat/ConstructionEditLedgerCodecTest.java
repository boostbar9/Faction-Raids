package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class ConstructionEditLedgerCodecTest extends MinecraftTestSupport {
    private final UUID site = UUID.randomUUID();

    private ConstructionEditLedger ledger() {
        var ledger = new ConstructionEditLedger();
        assertTrue(ledger.register(site, Set.of(BlockPos.ZERO, BlockPos.ZERO.above())));
        ledger.record(BlockPos.ZERO);
        return ledger;
    }

    private void rejects(Consumer<CompoundTag> change) {
        var original = ledger();
        CompoundTag saved = original.save(new CompoundTag());
        change.accept(saved);
        var loaded = ConstructionEditLedger.load(saved);
        assertFalse(loaded.sameGeneration(original.generation()), "Malformed history cannot retain authority");
        assertFalse(loaded.matches(site, Set.of(BlockPos.ZERO, BlockPos.ZERO.above())));
        assertFalse(loaded.canRetire(site), "Malformed history cannot manufacture cleanup evidence");
        assertTrue(loaded.edited(site));
        assertTrue(loaded.reserves(Set.of(new BlockPos(500, 65, 500))));
        assertFalse(loaded.register(UUID.randomUUID(), Set.of(new BlockPos(500, 65, 500))));
    }

    private static CompoundTag job(CompoundTag root) { return root.getList("Sites", 10).getCompound(0); }

    @Test void missingWronglyTypedOrUnknownRootFieldsCannotEraseHistory() {
        for (String key : Set.of("Sites", "Retired", "Generation", "Invalid")) {
            rejects(root -> root.remove(key));
            rejects(root -> root.putString(key, "not the recorded type"));
        }
        rejects(root -> root.putInt("Invalid", 0));
        rejects(root -> root.putByte("Invalid", (byte) 2));
        rejects(root -> root.putByte("Invalid", (byte) -1));
        rejects(root -> root.putUUID("Generation", new UUID(0, 0)));
        rejects(root -> root.putInt("Version", 99));
        rejects(root -> root.putString("Unexpected", "unknown schema"));
        for (String key : Set.of("Sites", "Retired")) rejects(root -> {
            ListTag list = new ListTag(); list.add(StringTag.valueOf("not a compound")); root.put(key, list);
        });
    }

    @Test void missingWronglyTypedOrUnknownSiteFieldsCannotClearEditsOrDestruction() {
        for (String key : Set.of("Id", "Cells", "Edited", "BuilderDestroyed")) {
            rejects(root -> job(root).remove(key));
            rejects(root -> job(root).putString(key, "not the recorded type"));
        }
        rejects(root -> job(root).putUUID("Id", new UUID(0, 0)));
        rejects(root -> job(root).putInt("BuilderDestroyed", 0));
        rejects(root -> job(root).putByte("BuilderDestroyed", (byte) 2));
        rejects(root -> job(root).putByte("BuilderDestroyed", (byte) -1));
        rejects(root -> job(root).putLongArray("Edited", new long[]{BlockPos.ZERO.asLong(), BlockPos.ZERO.asLong()}));
        rejects(root -> job(root).putLongArray("Edited", new long[]{new BlockPos(99, 65, 99).asLong()}));
        rejects(root -> job(root).putString("ReservationVersion", "1"));
        rejects(root -> job(root).putDouble("ReservationVersion", 1));
        rejects(root -> job(root).putInt("ReservationVersion", 2));
        rejects(root -> job(root).putInt("ReservationVersion", -1));
        rejects(root -> job(root).putString("Unexpected", "unknown schema"));
        rejects(root -> job(root).putString("BuilderDestruction", "not a destruction proof"));
        rejects(root -> job(root).put("BuilderDestruction", new CompoundTag()));
        rejects(root -> {
            CompoundTag proof = new CompoundTag(); proof.putUUID("Area", UUID.randomUUID()); proof.putString("Receipt", "a".repeat(64));
            job(root).put("BuilderDestruction", proof); // A receipt cannot contradict BuilderDestroyed=false.
        });
    }

    @Test void malformedRetirementEntriesInvalidateTheWholeLedger() {
        rejects(root -> {
            CompoundTag receipt = new CompoundTag(); receipt.putString("Id", "missing UUID");
            root.getList("Retired", 10).add(receipt);
        });
        rejects(root -> {
            CompoundTag receipt = new CompoundTag(); receipt.putUUID("Id", new UUID(0, 0));
            root.getList("Retired", 10).add(receipt);
        });
        rejects(root -> {
            CompoundTag receipt = new CompoundTag(); receipt.putUUID("Id", UUID.randomUUID()); receipt.putBoolean("Unknown", false);
            root.getList("Retired", 10).add(receipt);
        });
    }

    @Test void supportedLegacyShapePreservesEditsAndOnlyExplicitCancellation() {
        for (boolean omitVersion : new boolean[]{true, false}) {
            var original = ledger(); CompoundTag saved = original.save(new CompoundTag());
            saved.remove("ProjectLeases");
            if (omitVersion) job(saved).remove("ReservationVersion");
            else job(saved).putInt("ReservationVersion", 0);
            var loaded = ConstructionEditLedger.load(saved);
            assertTrue(loaded.sameGeneration(original.generation()));
            assertTrue(loaded.edited(site)); assertTrue(loaded.canRetire(site));
            assertFalse(loaded.completeReservation(site));
            assertTrue(loaded.reserves(Set.of(new BlockPos(500, 65, 500))));
            loaded = ConstructionEditLedger.load(loaded.save(new CompoundTag()));
            assertTrue(loaded.edited(site)); assertFalse(loaded.completeReservation(site));
            loaded.retire(site, true);
            assertFalse(loaded.reserves(Set.of(new BlockPos(500, 65, 500))));
        }
    }

    @Test void malformedWholeProjectEditHistoryCannotReauthorizeItsActiveLease() {
        var project = ConstructionProjectLedgerTest.project(); var ledger = new ConstructionEditLedger();
        assertTrue(ledger.registerProject(project)); assertTrue(ledger.leaseProjectStage(project));
        ledger.record(BlockPos.of(project.stages().get(1).reservation().iterator().next()));
        assertFalse(ledger.matchesProjectLease(project));
        for (boolean omit : new boolean[]{true, false}) {
            CompoundTag saved = ledger.save(new CompoundTag());
            if (omit) job(saved).remove("Edited"); else job(saved).putString("Edited", "damaged history");
            var loaded = ConstructionEditLedger.load(saved);
            assertFalse(loaded.sameGeneration(ledger.generation()));
            assertFalse(loaded.matchesProjectLease(project));
            assertFalse(loaded.completeReservation(project.active().areaId()));
            assertTrue(loaded.reserves(Set.of(new BlockPos(500, 65, 500))));
        }
    }
}
