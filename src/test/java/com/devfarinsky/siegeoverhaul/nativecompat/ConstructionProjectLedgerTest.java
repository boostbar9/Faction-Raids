package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConstructionProjectLedgerTest extends MinecraftTestSupport {
    static PerimeterProject project() {
        Set<ChunkPos> territory = Set.of(new ChunkPos(0, 0));
        var plan = PerimeterBlueprint.create(territory, (x,z) -> PerimeterBlueprint.Surface.ready(65), PerimeterBlueprint.Palette.COBBLESTONE);
        var layout = PerimeterStageLayout.partition(plan, stage -> stage.targets().size() <= 500 ? null : "Fixture stage capacity");
        var header = PerimeterProject.Header.newCommission(UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(),
                "team:ledger", new BlockPos(8,65,8), "ledger", 1, "a".repeat(64), territory);
        Map<Long, BlockState> before = new HashMap<>(), clearance = new HashMap<>();
        plan.blocks().keySet().forEach(cell -> before.put(cell, Blocks.AIR.defaultBlockState()));
        plan.clearance().forEach(cell -> clearance.put(cell, Blocks.AIR.defaultBlockState()));
        return PerimeterProject.prepare(header, plan, layout, before, clearance);
    }
    private Set<BlockPos> positions(Set<Long> cells) {
        Set<BlockPos> result = new HashSet<>(); cells.forEach(cell -> result.add(BlockPos.of(cell))); return result;
    }
    private PerimeterProject paid(PerimeterProject project) {
        var core = new CompoundTag(); core.putLong("BankEmeralds",64);
        PerimeterProjectStore.prepare(core,project,()->{});
        return PerimeterProjectStore.consumeOnce(core,project.header().projectId(),project.manifestHash(),64,false,()->{}).project();
    }
    @Test void exactDeathOpensReplacementWithoutReleasingReservationOrChangingPayment() {
        var prepared=project();var ledger=new ConstructionEditLedger();
        assertTrue(ledger.registerProject(prepared));assertTrue(ledger.leaseProjectStage(prepared));
        var paid=paid(prepared);var running=paid.activate(paid.check());var original=running.save();
        UUID next=UUID.randomUUID();assertFalse(ledger.replaceDeadBuilder(running,next));
        assertFalse(ledger.projectBuilderDestroyed(running,UUID.randomUUID(),running.active().areaId(),ledger.generation()));
        assertTrue(ledger.projectBuilderDestroyed(running,running.header().builder(),running.active().areaId(),ledger.generation()));
        assertFalse(ledger.matchesProjectReservation(running));assertTrue(ledger.replaceDeadBuilder(running,next));
        assertEquals(next,ledger.assignedBuilder(running));assertTrue(ledger.matchesProjectLease(running));
        assertTrue(ledger.canRebindBuilder(running,running.header().builder(),running.active().areaId()));
        assertEquals(original,running.save());assertNull(ledger.projectBuilderDestructionReceipt(running));
        var reload=ConstructionEditLedger.load(ledger.save(new CompoundTag()));
        assertEquals(next,reload.assignedBuilder(running));assertTrue(reload.matchesProjectLease(running));
        assertFalse(reload.projectBuilderDestroyed(running,running.header().builder(),running.active().areaId(),reload.generation()));
        assertTrue(reload.projectBuilderDestroyed(running,next,running.active().areaId(),reload.generation()));
        assertTrue(reload.replaceDeadBuilder(running,UUID.randomUUID()));
    }
    @Test void deathBetweenSectionsRetainsVerifiedPrefixAndLeasesNextSectionAfterReload() {
        var p=project();var ledger=new ConstructionEditLedger();assertTrue(ledger.registerProject(p));assertTrue(ledger.leaseProjectStage(p));
        var paid=paid(p);p=paid.activate(paid.check());UUID previous=p.active().areaId();
        p=p.verifyStage(p.check(),p.expectedStageReceipt());ledger.retire(previous,true);p=p.retireVerifiedStage(p.check());
        assertEquals(PerimeterProject.State.WAITING_FOR_NEXT_STAGE,p.state());var original=p.save();UUID replacement=UUID.randomUUID();
        assertTrue(ledger.projectBuilderDestroyed(p,p.header().builder(),previous,ledger.generation()));
        assertTrue(ledger.replaceDeadBuilder(p,replacement));assertEquals(original,p.save());
        var reloaded=ConstructionEditLedger.load(ledger.save(new CompoundTag()));
        assertEquals(replacement,reloaded.assignedBuilder(p));assertTrue(reloaded.matchesProjectReservation(p));
        assertTrue(reloaded.leaseProjectStage(p));assertTrue(reloaded.matchesProjectLease(p));
        assertTrue(reloaded.retired(previous));assertEquals(1,p.receipts().size());
    }
    @Test void editedSiteCannotBeTransferredEvenWithConfirmedDeath() {
        var p=project();var ledger=new ConstructionEditLedger();assertTrue(ledger.registerProject(p));
        assertTrue(ledger.leaseProjectStage(p));var paid=paid(p);var running=paid.activate(paid.check());
        assertTrue(ledger.projectBuilderDestroyed(running,running.header().builder(),running.active().areaId(),ledger.generation()));
        ledger.record(BlockPos.of(running.targets().keySet().iterator().next()));
        assertFalse(ledger.replaceDeadBuilder(running,UUID.randomUUID()));assertEquals(running.header().builder(),ledger.assignedBuilder(running));
    }
    @Test void oneGlobalReservationSurvivesEveryChildRetirementAndRestart() {
        var project = project(); var ledger = new ConstructionEditLedger();
        assertTrue(project.stages().size() > 1); assertTrue(ledger.registerProject(project));
        assertTrue(ledger.leaseProjectStage(project)); project = paid(project);
        while (project.active() != null) {
            project = project.activate(project.check());
            assertTrue(ledger.matchesProjectLease(project));
            UUID area = project.active().areaId();
            assertTrue(ledger.matches(area,positions(project.active().layout().reservation())));
            assertTrue(ledger.completeReservation(area)); assertTrue(ledger.reserves(positions(project.reservation())));
            project = project.verifyStage(project.check(),project.expectedStageReceipt()); // Model proof only.
            ledger.retire(area,true); assertTrue(ledger.retired(area));
            assertTrue(ledger.reserves(positions(project.reservation())));
            project = project.retireVerifiedStage(project.check());
            ledger = ConstructionEditLedger.load(ledger.save(new CompoundTag()));
            assertTrue(ledger.retired(area));
            assertTrue(ledger.matches(project.header().projectId(),positions(project.reservation())));
            if (project.active() != null) assertTrue(ledger.leaseProjectStage(project));
        }
        assertEquals(PerimeterProject.State.VERIFYING_COMPLETE,project.state());
        ledger.retire(project.header().projectId(),true);
        assertFalse(ledger.reserves(positions(project.reservation())));
    }
    @Test void editInFutureStageInvalidatesTheActiveChildAndPreventsNextLease() {
        var project = project(); var ledger = new ConstructionEditLedger();
        assertTrue(ledger.registerProject(project)); assertTrue(ledger.leaseProjectStage(project));
        var future = project.stages().get(1).layout().reservation().iterator().next();
        assertTrue(ledger.matchesProjectLease(project)); // Warm the exact immutable-geometry cache.
        ledger.record(BlockPos.of(future));
        assertFalse(ledger.matchesProjectLease(project)); // Storage authority uses this same global edit gate.
        assertTrue(ledger.edited(project.active().areaId()));
        project = paid(project); project = project.activate(project.check());
        project = project.verifyStage(project.check(),project.expectedStageReceipt());
        ledger.retire(project.active().areaId(),true); project = project.retireVerifiedStage(project.check());
        assertFalse(ledger.leaseProjectStage(project));
        assertTrue(ConstructionEditLedger.load(ledger.save(new CompoundTag())).edited(project.header().projectId()));
    }
    @Test void cancellationReleasesWholeSiteAndKeepsExactUnloadedWorkerTombstone() {
        var project = project(); var ledger = new ConstructionEditLedger(); UUID area = project.active().areaId();
        assertTrue(ledger.registerProject(project)); assertTrue(ledger.leaseProjectStage(project));
        ledger.retire(project.header().projectId(),false);
        ledger = ConstructionEditLedger.load(ledger.save(new CompoundTag()));
        assertTrue(ledger.retired(area)); assertFalse(ledger.completeReservation(area));
        assertFalse(ledger.reserves(positions(project.reservation())));
        assertFalse(ledger.leaseProjectStage(project));
        ledger.acknowledgeRetirement(area); assertFalse(ledger.retired(area));
    }
    @Test void unknownOrCorruptProjectHistoryNeverFallsBackToOrdinaryChildAuthority() {
        var project = project(); var ledger = new ConstructionEditLedger(); UUID area = project.active().areaId();
        assertTrue(ledger.registerProject(project)); assertTrue(ledger.leaseProjectStage(project));
        assertFalse(ledger.register(area,Set.of(BlockPos.ZERO)));
        var missing = ledger.save(new CompoundTag()); missing.remove("ProjectLeases");
        var loaded = ConstructionEditLedger.load(missing);
        assertFalse(loaded.completeReservation(area)); assertFalse(loaded.matches(area,positions(project.active().layout().reservation())));
        var corrupt = ledger.save(new CompoundTag()); corrupt.putString("ProjectLeases","unknown");
        loaded = ConstructionEditLedger.load(corrupt);
        assertFalse(loaded.sameGeneration(ledger.generation())); assertTrue(loaded.reserves(Set.of(BlockPos.ZERO)));
    }
}
