package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.core.PerimeterGateProjectFixture;
import com.devfarinsky.siegeoverhaul.core.PerimeterProject;
import com.devfarinsky.siegeoverhaul.core.PerimeterProjectLink;
import com.talhanation.workers.entities.BuilderEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Controller boundary tests use mocked world/hooks; no native construction evidence is implied. */
class NativePerimeterCrewTest extends MinecraftTestSupport {
    private static final class Fixture implements AutoCloseable {
        final PerimeterProject project = PerimeterGateProjectFixture.paidRunning();
        final ServerLevel level = mock(ServerLevel.class);
        final ServerPlayer owner = mock(ServerPlayer.class);
        final ProtectedBuildArea area = mock(ProtectedBuildArea.class);
        final BuilderEntity helper = mock(BuilderEntity.class);
        final UUID helperId = UUID.randomUUID();
        final CompoundTag helperData = new CompoundTag();
        final ConstructionEditLedger ledger = mock(ConstructionEditLedger.class);
        final Set<UUID> reserved = new HashSet<>();
        final MockedStatic<ConstructionEditLedger> ledgers = mockStatic(ConstructionEditLedger.class);
        final MockedStatic<WorkersBridge> workers = mockStatic(WorkersBridge.class);
        final MockedStatic<NativeConstructionGuard> guards = mockStatic(NativeConstructionGuard.class);
        final MockedStatic<NativePerimeterProjects> projects = mockStatic(NativePerimeterProjects.class);

        Fixture() {
            when(owner.getUUID()).thenReturn(project.header().owner());
            when(helper.getUUID()).thenReturn(helperId); when(helper.getPersistentData()).thenReturn(helperData);
            when(helper.level()).thenReturn(level); when(helper.isAlive()).thenReturn(true);
            when(level.getEntity(helperId)).thenReturn(helper);
            when(area.getUUID()).thenReturn(project.active().areaId());
            when(ledger.matchesProjectLease(project)).thenReturn(true);
            when(ledger.reservedCrewMembers(project)).thenAnswer(call -> Set.copyOf(reserved));
            when(ledger.canRetainHandLifecycle(helperData, helperId)).thenReturn(true);
            when(ledger.enlistCrew(project, helperId)).thenAnswer(call -> reserved.add(helperId));
            ledgers.when(() -> ConstructionEditLedger.get(level)).thenReturn(ledger);
            workers.when(() -> WorkersBridge.isBuilder(helper)).thenReturn(true);
            workers.when(() -> WorkersBridge.readWorkerOwner(helper)).thenReturn(project.header().owner());
            guards.when(() -> NativeConstructionGuard.matchesProjectSnapshot(area, project)).thenReturn(true);
            guards.when(() -> NativeConstructionGuard.retireBuilderAssociation(eq(helper), any())).thenReturn(true);
            projects.when(() -> NativePerimeterProjects.idleReplacement(helper)).thenReturn(true);
            when(level.getEntitiesOfClass(eq(Mob.class), any(AABB.class), any())).thenAnswer(call -> {
                Predicate<Mob> filter = call.getArgument(2);
                return filter.test(helper) ? List.<Mob>of(helper) : List.<Mob>of();
            });
        }
        @Override public void close() { projects.close(); guards.close(); workers.close(); ledgers.close(); }
    }

    @Test void admissionUsesSavedIntentAndWorkerOnlyResumeWithoutAnotherProjectPayment() {
        try (var f = new Fixture()) {
            var frozen = f.project.save();
            NativePerimeterCrew.advance(f.level, f.owner, f.project, f.area);
            verify(f.ledger).enlistCrew(f.project, f.helperId);
            f.guards.verify(() -> NativeConstructionGuard.resumeCrewMember(f.owner, f.helper, f.area, f.project));
            assertEquals(frozen, f.project.save());
            verify(f.ledger, never()).registerProject(any()); verify(f.ledger, never()).leaseProjectStage(any());
        }
    }

    @Test void foreignOwnerAndCompetingWorkerReservationCannotEnroll() {
        try (var f = new Fixture()) {
            f.workers.when(() -> WorkersBridge.readWorkerOwner(f.helper)).thenReturn(UUID.randomUUID());
            NativePerimeterCrew.advance(f.level, f.owner, f.project, f.area);
            verify(f.ledger, never()).enlistCrew(any(), any());
            f.workers.when(() -> WorkersBridge.readWorkerOwner(f.helper)).thenReturn(f.project.header().owner());
            PerimeterProjectLink.set(f.helper, f.project, f.helperId);
            NativePerimeterCrew.advance(f.level, f.owner, f.project, f.area);
            verify(f.ledger, never()).enlistCrew(any(), any());
        }
    }

    @Test void unloadedHelperRevokesOnlyWorkAuthorityAndNeverBecomesDeathEvidence() {
        try (var f = new Fixture()) {
            f.reserved.add(f.helperId); when(f.level.getEntity(f.helperId)).thenReturn(null);
            when(f.ledger.knownCrewMember(f.project, f.helperId)).thenReturn(true);
            NativePerimeterCrew.advance(f.level, f.owner, f.project, f.area);
            verify(f.ledger).retireCrew(f.project, f.helperId);
            verify(f.ledger, never()).markCrewBuilderDestroyed(any(), any(), any(), any());
            verify(f.ledger, never()).projectBuilderDestroyed(any(), any(), any(), any());
        }
    }

    @Test void cancellationWithdrawsNeverLinkedPendingIntentOnlyAfterLoadedInspection() {
        try (var f = new Fixture()) {
            f.reserved.add(f.helperId); when(f.ledger.pendingCrewMember(f.project, f.helperId)).thenReturn(true);
            when(f.ledger.cancelPendingCrewMember(f.project, f.helperId)).thenReturn(true);
            assertTrue(NativePerimeterCrew.retireStage(f.level, f.project, f.project.active().areaId()));
            verify(f.ledger).cancelPendingCrewMember(f.project, f.helperId);
            f.guards.verify(() -> NativeConstructionGuard.readyForRetirement(f.helper), never());
        }
    }

    @Test void cancellationRecoversProjectLinkOnlyCrashWithoutInventingInventoryCleanup() {
        try (var f = new Fixture()) {
            f.reserved.add(f.helperId); when(f.ledger.pendingCrewMember(f.project, f.helperId)).thenReturn(true);
            when(f.ledger.cancelPendingCrewMember(f.project, f.helperId)).thenReturn(true);
            PerimeterProjectLink.set(f.helper, f.project, f.helperId);
            assertTrue(NativePerimeterCrew.retireStage(f.level, f.project, f.project.active().areaId()));
            assertFalse(PerimeterProjectLink.reserved(f.helper));
            verify(f.ledger).cancelPendingCrewMember(f.project, f.helperId);
        }
    }

    @Test void pendingWorkerUnloadPreservesEveryPartialAdmissionForReplay() {
        for (int boundary = 0; boundary < 3; boundary++) try (var f = new Fixture()) {
            f.reserved.add(f.helperId); when(f.ledger.pendingCrewMember(f.project, f.helperId)).thenReturn(true);
            when(f.ledger.knownCrewMember(f.project, f.helperId)).thenReturn(true);
            if (boundary >= 1) PerimeterProjectLink.set(f.helper, f.project, f.helperId);
            if (boundary >= 2) assertTrue(PerimeterProjectLink.bindLedgerGeneration(f.helper, UUID.randomUUID()));
            CompoundTag frozen = f.helperData.copy();
            when(f.level.getEntity(f.helperId)).thenReturn(null);
            NativePerimeterCrew.advance(f.level, f.owner, f.project, f.area);
            verify(f.ledger, never()).retireCrew(f.project, f.helperId);
            verify(f.ledger, never()).cancelPendingCrewMember(any(), any());
            assertEquals(frozen, f.helperData);
            when(f.level.getEntity(f.helperId)).thenReturn(f.helper);
            NativePerimeterCrew.advance(f.level, f.owner, f.project, f.area);
            f.guards.verify(() -> NativeConstructionGuard.resumeCrewMember(f.owner, f.helper, f.area, f.project));
        }
    }

    @Test void missingPendingWorkerDoesNotProveAdmissionOrInventoryCleanup() {
        try (var f = new Fixture()) {
            f.reserved.add(f.helperId); when(f.ledger.pendingCrewMember(f.project, f.helperId)).thenReturn(true);
            when(f.level.getEntity(f.helperId)).thenReturn(null);
            assertFalse(NativePerimeterCrew.retireStage(f.level, f.project, f.project.active().areaId()));
            verify(f.ledger, never()).cancelPendingCrewMember(any(), any());
        }
    }
}
