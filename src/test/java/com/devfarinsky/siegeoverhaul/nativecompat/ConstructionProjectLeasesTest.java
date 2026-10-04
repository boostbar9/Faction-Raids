package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConstructionProjectLeasesTest extends MinecraftTestSupport {
    private final UUID project = UUID.randomUUID(), first = UUID.randomUUID(), second = UUID.randomUUID(), third = UUID.randomUUID();
    private final String hash = "a".repeat(64);
    private final Set<Long> global = Set.of(1L, 2L, 3L, 4L);
    private ConstructionProjectLeases registered() {
        var leases = new ConstructionProjectLeases();
        assertTrue(leases.register(project, 1, hash, List.of(first, second, third), Set.of())); return leases;
    }
    @Test void exactActiveChildIsTheOnlyLeaseAndRetirementNeverDropsTheParent() {
        var leases = registered();
        assertFalse(leases.active(first)); assertFalse(leases.retired(first));
        assertFalse(leases.lease(project, 1, hash, 1, second, Set.of(2L), global));
        assertTrue(leases.lease(project, 1, hash, 0, first, Set.of(1L, 2L), global));
        assertTrue(leases.matches(first, Set.of(2L, 1L))); assertFalse(leases.matches(first, global));
        assertFalse(leases.active(second)); assertFalse(leases.retire(second));
        assertTrue(leases.retire(first)); assertTrue(leases.retire(first));
        assertEquals(project, leases.parent(first)); assertEquals(Set.of(project), leases.projectIds());
        assertFalse(leases.active(first)); assertTrue(leases.retired(first));
        assertFalse(leases.lease(project, 1, hash, 2, third, Set.of(4L), global));
        assertTrue(leases.lease(project, 1, hash, 1, second, Set.of(3L), global));
        assertTrue(leases.lease(project, 1, hash, 1, second, Set.of(3L), global));
        assertFalse(leases.lease(project, 1, hash, 1, second, Set.of(3L, 4L), global));
        assertTrue(leases.retired(first)); assertFalse(leases.retired(second));
    }
    @Test void identityGenerationScopeAndForeignIdentifiersCannotBeAdopted() {
        var leases = registered();
        assertFalse(leases.lease(project, 2, hash, 0, first, Set.of(1L), global));
        assertFalse(leases.lease(project, 1, "b".repeat(64), 0, first, Set.of(1L), global));
        assertFalse(leases.lease(project, 1, hash, 0, first, Set.of(99L), global));
        assertFalse(leases.lease(project, 1, hash, 0, second, Set.of(1L), global));
        assertFalse(leases.register(UUID.randomUUID(), 1, hash, List.of(first), Set.of()));
        assertFalse(leases.register(first, 1, hash, List.of(UUID.randomUUID()), Set.of()));
        assertFalse(new ConstructionProjectLeases().register(project, 1, hash, List.of(first), Set.of(first)));
        assertFalse(new ConstructionProjectLeases().register(project, 1, hash, List.of(first, first), Set.of()));
        assertFalse(new ConstructionProjectLeases().register(new UUID(0,0), 1, hash, List.of(first), Set.of()));
        assertFalse(new ConstructionProjectLeases().register(project, 0, hash, List.of(first), Set.of()));
    }
    @Test void restartPreservesActiveSubsetAndEveryRetiredPrefixWithoutInventingFutureAuthority() {
        var leases = registered();
        assertTrue(leases.lease(project, 1, hash, 0, first, Set.of(1L), global)); assertTrue(leases.retire(first));
        assertTrue(leases.lease(project, 1, hash, 1, second, Set.of(2L, 3L), global));
        for (int reload = 0; reload < 3; reload++) {
            CompoundTag saved = leases.save();
            leases = ConstructionProjectLeases.load(saved, Map.of(project, global), Set.of());
            assertTrue(leases.retired(first)); assertTrue(leases.matches(second, Set.of(2L,3L)));
            assertFalse(leases.active(third)); assertFalse(leases.retired(third));
            assertEquals(saved, leases.save());
        }
    }
    @Test void corruptOrMissingGlobalHistoryCannotBecomeAValidChildLease() {
        var leases = registered(); assertTrue(leases.lease(project, 1, hash, 0, first, Set.of(1L), global));
        assertThrows(IllegalArgumentException.class, () -> ConstructionProjectLeases.load(leases.save(), Map.of(), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> ConstructionProjectLeases.load(leases.save(), Map.of(project, global), Set.of(first)));
        CompoundTag escaped = leases.save(); escaped.getList("Projects", 10).getCompound(0).putLongArray("Cells", new long[]{99});
        assertThrows(IllegalArgumentException.class, () -> ConstructionProjectLeases.load(escaped, Map.of(project,global),Set.of()));
        CompoundTag duplicate = leases.save(); duplicate.getList("Projects",10).add(duplicate.getList("Projects",10).getCompound(0).copy());
        assertThrows(IllegalArgumentException.class, () -> ConstructionProjectLeases.load(duplicate,Map.of(project,global),Set.of()));
        CompoundTag wrongVersion = leases.save(); wrongVersion.putDouble("Version",1);
        assertThrows(IllegalArgumentException.class, () -> ConstructionProjectLeases.load(wrongVersion,Map.of(project,global),Set.of()));
        CompoundTag unknown = leases.save(); unknown.putString("Unexpected","not normalized");
        assertThrows(IllegalArgumentException.class, () -> ConstructionProjectLeases.load(unknown,Map.of(project,global),Set.of()));
        CompoundTag wrongType = leases.save(); wrongType.putString("Projects", "not a list");
        assertThrows(IllegalArgumentException.class, () -> ConstructionProjectLeases.load(wrongType,Map.of(project,global),Set.of()));
        CompoundTag malformedBoolean = leases.save(); malformedBoolean.getList("Projects",10).getCompound(0).putByte("Retired",(byte)2);
        assertThrows(IllegalArgumentException.class, () -> ConstructionProjectLeases.load(malformedBoolean,Map.of(project,global),Set.of()));
        CompoundTag omitted = leases.save(); omitted.getList("Projects",10).getCompound(0).remove("Retired");
        assertThrows(IllegalArgumentException.class, () -> ConstructionProjectLeases.load(omitted,Map.of(project,global),Set.of()));
    }
    @Test void saveAndInputCopiesCannotMutateTheReservationAuthority() {
        var leases = registered(); var input = new java.util.HashSet<>(Set.of(1L));
        assertTrue(leases.lease(project,1,hash,0,first,input,global)); input.add(4L);
        var saved = leases.save(); saved.getList("Projects",10).getCompound(0).putLongArray("Cells",new long[]{4L});
        assertTrue(leases.matches(first,Set.of(1L))); assertFalse(leases.matches(first,Set.of(1L,4L)));
    }
}
