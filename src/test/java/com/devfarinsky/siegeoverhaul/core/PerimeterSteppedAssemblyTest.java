package com.devfarinsky.siegeoverhaul.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static com.devfarinsky.siegeoverhaul.core.PerimeterSteppedAssembly.*;
import static com.devfarinsky.siegeoverhaul.core.PerimeterSteppedModelFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class PerimeterSteppedAssemblyTest {
    @Test void exactTwoPhaseMembershipDependencyReservationAndDistinctAreaIds() {
        var a = assembly(); assertEquals(2, a.phases().size());
        var fill = a.phases().get(0); var structure = a.phases().get(1);
        assertEquals(Kind.FILL, fill.kind()); assertEquals(Kind.STRUCTURE, structure.kind());
        assertEquals(Set.of(FILL), fill.targets().keySet()); assertEquals(Set.of(WALL), structure.targets().keySet());
        assertTrue(Collections.disjoint(fill.targets().keySet(), structure.targets().keySet()));
        assertEquals(java.util.List.of(), fill.dependencies()); assertEquals(java.util.List.of(0), structure.dependencies());
        assertEquals(Set.of(FILL, WALL, CLEAR, SUPPORT), a.reservation());
        assertEquals(Set.of(FILL, CLEAR, SUPPORT), fill.reservation());
        assertEquals(Map.of("minecraft:dirt", 1), fill.materialBill());
        assertNotEquals(fill.areaId(), structure.areaId()); assertEquals(fill.areaId(), assembly().phases().get(0).areaId());
        assertFalse(a.executionSupported());
    }
    @Test void inputAndOutputCollectionsAreImmutable() {
        var targets = targets(); var observations = observations(); var bill = bill();
        var a = assemble(header(), 1, GEOMETRY, targets, observations, bill);
        targets.clear(); observations.clear(); bill.clear(); assertEquals(2, a.targets().size()); assertEquals(2, a.observations().size());
        assertThrows(UnsupportedOperationException.class, () -> a.targets().clear());
        assertThrows(UnsupportedOperationException.class, () -> a.header().claims().clear());
        assertThrows(UnsupportedOperationException.class, () -> a.phases().get(0).reservation().clear());
        assertThrows(UnsupportedOperationException.class, () -> a.phases().get(0).materialBill().clear());
        assertThrows(UnsupportedOperationException.class, () -> a.observations().get(SUPPORT).state().properties().clear());
    }
    @Test void moreThanOneFillCellIsSupportedWithoutPerPhaseFee() {
        var targets = targets(); targets.put(SECOND_FILL, fill()); var bill = bill(); bill.put("minecraft:dirt", 2);
        var a = assemble(header(), 1, GEOMETRY, targets, observations(), bill);
        assertEquals(2, a.phases().get(0).targets().size()); assertEquals(64, a.header().quotedPrice());
    }
    @Test void emptyFillIsExplicitlyRefusedRatherThanInventingARetirement() {
        var targets = targets(); targets.remove(FILL);
        var e = assertThrows(IllegalArgumentException.class, () -> assemble(header(), 1, GEOMETRY, targets, observations(), Map.of("minecraft:cobblestone", 1)));
        assertTrue(e.getMessage().contains("Empty fill"));
    }
    @Test void emptyStructureIsRefused() {
        var targets = targets(); targets.remove(WALL);
        assertThrows(IllegalArgumentException.class, () -> assemble(header(), 1, GEOMETRY, targets, observations(), Map.of("minecraft:dirt", 1)));
    }
    @Test void readonlyMutationOverlapAndOutOfClaimTargetsAreRefused() {
        var o = observations(); o.put(FILL, new Observation(ObservationRole.CLEARANCE, StateValue.AIR, 0));
        assertThrows(IllegalArgumentException.class, () -> assemble(header(), 1, GEOMETRY, targets(), o, bill()));
        var t = targets(); t.put(new Position(16, 63, 0), fill());
        assertThrows(IllegalArgumentException.class, () -> assemble(header(), 1, GEOMETRY, t, observations(), Map.of("minecraft:dirt", 2, "minecraft:cobblestone", 1)));
    }
    @Test void readonlyExteriorObservationsAreRetainedWithoutMutationPermission() {
        var o = observations(); var exterior = new Position(-1, 64, 0); o.put(exterior, new Observation(ObservationRole.CLEARANCE, StateValue.AIR, 1));
        var a = assemble(header(), 1, GEOMETRY, targets(), o, bill()); assertTrue(a.reservation().contains(exterior)); assertFalse(a.targets().containsKey(exterior));
    }
    @Test void materialBillMustBeExactAndExcludeKeptTargets() {
        assertThrows(IllegalArgumentException.class, () -> assemble(header(), 1, GEOMETRY, targets(), observations(), Map.of("minecraft:dirt", 1)));
        var t = targets(); t.put(WALL, new Target(Kind.STRUCTURE, 0, COBBLE, COBBLE, Edit.KEEP, 7));
        assertThrows(IllegalArgumentException.class, () -> assemble(header(), 1, GEOMETRY, t, observations(), bill()));
        var a = assemble(header(), 1, GEOMETRY, t, observations(), Map.of("minecraft:dirt", 1)); assertTrue(a.phases().get(1).materialBill().isEmpty());
    }
    @Test void unsupportedMutationAndStateShapesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Target(Kind.FILL, 0, COBBLE, StateValue.DIRT, Edit.PLACE, 1));
        assertThrows(IllegalArgumentException.class, () -> new Target(Kind.FILL, 0, StateValue.AIR, StateValue.DIRT, Edit.KEEP, 1));
        assertThrows(IllegalArgumentException.class, () -> new Target(Kind.STRUCTURE, 0, COBBLE, COBBLE, Edit.PLACE, 1));
        assertThrows(IllegalArgumentException.class, () -> new Target(Kind.STRUCTURE, 0, StateValue.AIR, StateValue.DIRT, Edit.PLACE, 1));
        assertThrows(IllegalArgumentException.class, () -> new Target(Kind.STRUCTURE, 0, StateValue.AIR, new StateValue("minecraft:oak_planks", Map.of("shape", "full")), Edit.PLACE, 1));
        assertThrows(IllegalArgumentException.class, () -> new Observation(ObservationRole.CLEARANCE, COBBLE, 1));
        assertThrows(IllegalArgumentException.class, () -> new Observation(ObservationRole.SUPPORT, StateValue.AIR, 1));
        assertThrows(IllegalArgumentException.class, () -> new Observation(ObservationRole.SUPPORT, COBBLE, -1));
    }
    @Test void canonicalHashIgnoresMapInsertionOrder() {
        var reversed = new LinkedHashMap<Position, Target>(); reversed.put(WALL, targets().get(WALL)); reversed.put(FILL, fill());
        assertEquals(assembly().digest(), assemble(header(), 1, GEOMETRY, reversed, observations(), bill()).digest());
    }
    @Test void observationPropertiesAndEditRevisionsAreLosslesslyBound() {
        var a = assembly(); var o = observations();
        o.put(SUPPORT, new Observation(ObservationRole.SUPPORT, new StateValue("minecraft:grass_block", Map.of("snowy", "true")), 5));
        assertNotEquals(a.digest(), assemble(header(), 1, GEOMETRY, targets(), o, bill()).digest());
        o = observations(); o.put(SUPPORT, new Observation(ObservationRole.SUPPORT, o.get(SUPPORT).state(), 6));
        assertNotEquals(a.digest(), assemble(header(), 1, GEOMETRY, targets(), o, bill()).digest());
        var t = targets(); t.put(WALL, new Target(Kind.STRUCTURE, 0, StateValue.AIR, COBBLE, Edit.PLACE, 8));
        assertNotEquals(a.digest(), assemble(header(), 1, GEOMETRY, t, observations(), bill()).digest());
    }
    @Test void unpairedSurrogatesAndPropertyDelimitersCannotAlias() {
        var first = observations(); var second = observations();
        first.put(SUPPORT, new Observation(ObservationRole.SUPPORT, new StateValue("test:block", Map.of("a", "\uD800")), 5));
        second.put(SUPPORT, new Observation(ObservationRole.SUPPORT, new StateValue("test:block", Map.of("a", "\uD801")), 5));
        assertNotEquals(assemble(header(), 1, GEOMETRY, targets(), first, bill()).digest(), assemble(header(), 1, GEOMETRY, targets(), second, bill()).digest());
        first.put(SUPPORT, new Observation(ObservationRole.SUPPORT, new StateValue("test:block", Map.of("a=b", "c")), 5));
        second.put(SUPPORT, new Observation(ObservationRole.SUPPORT, new StateValue("test:block", Map.of("a", "b=c")), 5));
        assertNotEquals(assemble(header(), 1, GEOMETRY, targets(), first, bill()).digest(), assemble(header(), 1, GEOMETRY, targets(), second, bill()).digest());
    }
    @ParameterizedTest @ValueSource(ints = {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14})
    void everyMutableIdentityInputChangesTheDigest(int field) {
        Header h = header();
        Header changed = new Header(field == 0 ? id(9) : h.projectId(), field == 1 ? 2 : h.generation(), field == 2 ? id(9) : h.owner(),
                field == 3 ? id(9) : h.builder(), field == 4 ? "other" : h.faction(), field == 4 ? "team:other" : h.coreKey(),
                field == 5 ? new Position(2, 64, 1) : h.originalCore(), field == 6 ? "test:dimension" : h.dimension(),
                field == 7 ? id(9) : h.ledgerId(), field == 8 ? 2 : h.ledgerGeneration(),
                field == 9 ? Set.of(new Chunk(0, 0), new Chunk(1, 0)) : h.claims(), field == 10 ? -63 : h.minY(), field == 11 ? 319 : h.maxY(),
                field == 12 ? "d".repeat(64) : h.reviewedFingerprint(), h.feeVersion(), h.quotedPrice());
        var a = assemble(changed, field == 13 ? 2 : 1, field == 14 ? "d".repeat(64) : GEOMETRY, targets(), observations(), bill());
        assertNotEquals(assembly().digest(), a.digest()); assertNotEquals(assembly().phases().get(0).areaId(), a.phases().get(0).areaId());
    }
    @Test void malformedIdentityVersionsQuotesAndPositionsAreRefused() {
        var h = header();
        assertThrows(IllegalArgumentException.class, () -> new Header(h.projectId(), 1, id(0), h.builder(), h.faction(), h.coreKey(), h.originalCore(), h.dimension(), h.ledgerId(), 1, h.claims(), -64, 320, h.reviewedFingerprint(), 1, 64));
        assertThrows(IllegalArgumentException.class, () -> new Header(h.projectId(), 1, h.owner(), h.builder(), h.faction(), h.coreKey(), h.originalCore(), h.dimension(), h.ledgerId(), 1, h.claims(), -64, 320, h.reviewedFingerprint(), 1, 128));
        assertThrows(IllegalArgumentException.class, () -> assemble(h, 0, GEOMETRY, targets(), observations(), bill()));
        assertThrows(IllegalArgumentException.class, () -> assemble(h, 1, "A".repeat(64), targets(), observations(), bill()));
        assertThrows(IllegalArgumentException.class, () -> new Position(30_000_000, 64, 0));
        assertThrows(IllegalArgumentException.class, () -> new Position(0, 2048, 0));
    }
    @Test void aggregateObservationAndStateAndEnvelopeBudgetsAreRefused() {
        var o = new HashMap<Position, Observation>();
        for (int i = 0; i < MAX_OBSERVATIONS; i++) o.put(new Position(i % 256, 66, i / 256), new Observation(ObservationRole.CLEARANCE, StateValue.AIR, 0));
        assertThrows(IllegalArgumentException.class, () -> assemble(header(), 1, GEOMETRY, targets(), o, bill()));
        var properties = new HashMap<String, String>(); for (int i = 0; i < 32; i++) properties.put("p" + i, "v".repeat(64));
        var large = new StateValue("test:block", properties); o.clear();
        for (int i = 0; i < 2100; i++) o.put(new Position(i % 64, 66, i / 64), new Observation(ObservationRole.SUPPORT, large, 0));
        assertThrows(IllegalArgumentException.class, () -> assemble(header(), 1, GEOMETRY, targets(), o, bill()));
        o.clear(); o.put(new Position(29_999_999, 66, 29_999_999), new Observation(ObservationRole.CLEARANCE, StateValue.AIR, 0));
        assertThrows(IllegalArgumentException.class, () -> assemble(header(), 1, GEOMETRY, targets(), o, bill()));
    }
}
