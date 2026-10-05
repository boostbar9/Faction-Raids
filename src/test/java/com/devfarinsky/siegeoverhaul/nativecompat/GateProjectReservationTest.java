package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.core.PerimeterGateProjectFixture;
import com.devfarinsky.siegeoverhaul.core.PerimeterProject;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GateProjectReservationTest extends MinecraftTestSupport {
    @Test void approachReservationsSurviveReloadAndSameStateEditsInvalidateActiveChild() {
        var project = PerimeterGateProjectFixture.project(); var ledger = new ConstructionEditLedger();
        assertTrue(ledger.registerProject(project)); assertTrue(ledger.leaseProjectStage(project));
        long approach = project.observations().keySet().iterator().next();
        assertTrue(ledger.reserves(Set.of(BlockPos.of(approach))));
        assertTrue(ledger.matchesProjectLease(project));
        assertFalse(project.active().reservation().contains(approach));
        Set<BlockPos> injected = new HashSet<>(); project.active().reservation().forEach(cell -> injected.add(BlockPos.of(cell)));
        injected.add(BlockPos.of(approach));
        assertFalse(ledger.matches(project.active().areaId(), injected), "Global observations must not widen native lease authority");
        ledger = ConstructionEditLedger.load(ledger.save(new CompoundTag()));
        assertTrue(ledger.matchesProjectLease(project));
        ledger.record(BlockPos.of(approach)); // Same-state edit evidence is independent of current block state.
        assertFalse(ledger.matchesProjectLease(project)); assertFalse(ledger.matchesProjectReservation(project));
        assertFalse(ledger.leaseProjectStage(project));
        var restored = ConstructionEditLedger.load(ledger.save(new CompoundTag()));
        assertFalse(restored.matchesProjectReservation(project)); assertTrue(restored.edited(project.active().areaId()));
    }

    @Test void cancellationRetiresCompleteObservationReservationAndKeepsChildTombstones() {
        var project = PerimeterGateProjectFixture.project(); var ledger = new ConstructionEditLedger();
        assertTrue(ledger.registerProject(project)); assertTrue(ledger.leaseProjectStage(project));
        var canceled = project.cancel(project.check(), "Cancel staged gate review");
        var saved = PerimeterProject.load(canceled.save());
        assertEquals(project.observations(), saved.observations()); assertNull(saved.payment());
        ledger.retire(saved.header().projectId(), false);
        var restored = ConstructionEditLedger.load(ledger.save(new CompoundTag()));
        assertTrue(restored.retired(saved.active().areaId()));
        Set<BlockPos> cells = new HashSet<>(); saved.reservation().forEach(p -> cells.add(BlockPos.of(p)));
        assertFalse(restored.reserves(cells)); assertFalse(restored.leaseProjectStage(saved));
        assertFalse(PerimeterProjectAuthority.workState(saved, true, true));
    }

    @Test void persistedGateContractUsesOrdinaryProjectWorkStatesAfterObservationRuntimeSupport() {
        var project = PerimeterGateProjectFixture.project();
        assertTrue(project.executionSupported());
        assertTrue(PerimeterProjectAuthority.workState(project, true, true));
        assertFalse(PerimeterProjectAuthority.workState(project, false, false));
        var running = PerimeterGateProjectFixture.paidRunning();
        assertTrue(running.executionSupported());
        assertTrue(PerimeterProjectAuthority.workState(running, false, false));
        var verified = running.verifyStage(running.check(), running.expectedStageReceipt());
        assertFalse(PerimeterProjectAuthority.workState(verified, false, false));
        assertTrue(PerimeterProjectAuthority.workState(verified, true, true));
        var canceled = running.cancel(running.check(), "Owner canceled");
        assertFalse(PerimeterProjectAuthority.workState(canceled, true, true));
    }
}
