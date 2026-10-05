package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.core.PerimeterGateProjectFixture;
import com.devfarinsky.siegeoverhaul.core.PerimeterProject;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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

    @Test void persistedGateContractCannotCreateAssignOrAuthorizeANativeWorker() {
        var project = PerimeterGateProjectFixture.project();
        var owner = mock(ServerPlayer.class); var builder = mock(Mob.class);
        var rejected = assertThrows(IllegalArgumentException.class, () -> ProtectedConstructionAreas.createStage(owner, builder, project));
        assertEquals(PerimeterProject.GATE_EXECUTION_BLOCKER, rejected.getMessage());
        verifyNoInteractions(owner, builder);
        assertFalse(PerimeterProjectAuthority.workState(project, true, true));
        var running = PerimeterGateProjectFixture.paidRunning();
        assertFalse(PerimeterProjectAuthority.workState(running, false, false));
        var verified = running.verifyStage(running.check(), running.expectedStageReceipt());
        assertFalse(PerimeterProjectAuthority.workState(verified, true, true));
    }
}
