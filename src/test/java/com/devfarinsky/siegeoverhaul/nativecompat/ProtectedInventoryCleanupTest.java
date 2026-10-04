package com.devfarinsky.siegeoverhaul.nativecompat;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProtectedInventoryCleanupTest {
    @Test void fourBoundedObligationsRoundTripAndCompletingOnePreservesOthers() {
        var data=new CompoundTag();
        for(var kind:ProtectedStorageAccess.Kind.values())ProtectedInventoryCleanup.record(data,kind,ProtectedInventoryCleanup.CLEANUP);
        assertEquals(4,ProtectedInventoryCleanup.read(data.copy()).size());
        assertEquals(5,data.getCompound(ProtectedInventoryCleanup.KEY).getAllKeys().size());
        ProtectedInventoryCleanup.record(data,ProtectedStorageAccess.Kind.POSITION_UPKEEP,ProtectedInventoryCleanup.REVIEW);
        ProtectedInventoryCleanup.complete(data,ProtectedStorageAccess.Kind.NEEDED);
        assertEquals(3,ProtectedInventoryCleanup.read(data).size());
        assertEquals(ProtectedInventoryCleanup.REVIEW,ProtectedInventoryCleanup.read(data).get(ProtectedStorageAccess.Kind.POSITION_UPKEEP));
        for(var kind:ProtectedStorageAccess.Kind.values())ProtectedInventoryCleanup.complete(data,kind);
        assertFalse(data.contains(ProtectedInventoryCleanup.KEY));assertFalse(ProtectedInventoryCleanup.outstanding(data));
    }

    @Test void unknownKindsAndStatesStayIntactAndFailClosed() {
        for(boolean unknownKind:new boolean[]{false,true}) {
            var data=new CompoundTag();var tag=new CompoundTag();tag.putInt("Version",1);
            tag.putInt(unknownKind?"UNKNOWN":ProtectedStorageAccess.Kind.NEEDED.name(),unknownKind?1:7);
            data.put(ProtectedInventoryCleanup.KEY,tag);var before=data.copy();
            assertTrue(ProtectedInventoryCleanup.outstanding(data));
            assertThrows(RuntimeException.class,()->ProtectedInventoryCleanup.complete(data,ProtectedStorageAccess.Kind.NEEDED));
            assertThrows(RuntimeException.class,()->ProtectedInventoryCleanup.record(data,ProtectedStorageAccess.Kind.NEEDED,1));
            assertEquals(before,data);
        }
    }
}
