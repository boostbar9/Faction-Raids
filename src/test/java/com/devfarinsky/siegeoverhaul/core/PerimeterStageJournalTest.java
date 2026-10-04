package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class PerimeterStageJournalTest extends MinecraftTestSupport {
    private final BlockPos marker=new BlockPos(8,64,8);
    @Test void oneTransientCreationPermitAndReloadedIntentNeverGrantAnotherSpawn() {
        var project=PerimeterProjectTest.project();var core=new CompoundTag();var dirty=new AtomicInteger();
        PerimeterProjectStore.prepare(core,project,()->{});
        PerimeterStageJournal.prepare(core,project,dirty::incrementAndGet);
        var permit=PerimeterStageJournal.begin(core,project,marker,dirty::incrementAndGet);
        assertNotNull(permit);assertTrue(permit.claim(core,project));assertFalse(permit.claim(core,project));
        assertNull(PerimeterStageJournal.begin(core,project,marker,dirty::incrementAndGet));
        var loaded=core.copy();assertNull(PerimeterStageJournal.begin(loaded,PerimeterProjectTest.roundtrip(project),marker,dirty::incrementAndGet));
        assertEquals(PerimeterStageJournal.State.INTENT,PerimeterStageJournal.get(loaded,project).at(0).state());
        assertThrows(IllegalArgumentException.class,()->PerimeterStageJournal.begin(loaded,project,marker.east(),()->{}));
        assertTrue(dirty.get()>=4);
    }
    @Test void exactExistingMarkerCanBecomeLiveAndOnlyVerifiedCleanupAllowsNextStage() {
        var project=PerimeterProjectTest.project();var core=new CompoundTag();
        PerimeterProjectStore.prepare(core,project,()->{});
        PerimeterStageJournal.prepare(core,project,()->{});PerimeterStageJournal.begin(core,project,marker,()->{});
        PerimeterStageJournal.live(core,project,()->{});PerimeterStageJournal.live(core,project,()->{});
        assertThrows(IllegalArgumentException.class,()->PerimeterStageJournal.retired(core,project,()->{}));
        var paid=PerimeterProjectStore.consumeOnce(core,project.header().projectId(),project.manifestHash(),64,true,()->{}).project();
        var running=store(core,paid,paid.activate(paid.check()));
        var verified=store(core,running,running.verifyStage(running.check(),running.expectedStageReceipt()));
        var next=verified.retireVerifiedStage(verified.check());assertNotNull(next.active());
        var premature=core.copy();store(premature,verified,next);
        assertThrows(IllegalArgumentException.class,()->PerimeterStageJournal.begin(premature,next,marker,()->{}));
        PerimeterStageJournal.retired(core,verified,()->{});store(core,verified,next);
        assertNotNull(PerimeterStageJournal.begin(core,next,marker,()->{}));
        assertEquals(PerimeterStageJournal.State.RETIRED,PerimeterStageJournal.get(core,next).at(0).state());
        assertThrows(IllegalArgumentException.class,()->PerimeterStageJournal.live(core,verified,()->{}));
    }
    @Test void malformedOrReorderedRecoveryDataIsNotNormalizedIntoFreshWork() {
        var project=PerimeterProjectTest.project();var core=new CompoundTag();
        PerimeterProjectStore.prepare(core,project,()->{});
        PerimeterStageJournal.prepare(core,project,()->{});PerimeterStageJournal.begin(core,project,marker,()->{});
        var missing=core.copy();missing.getCompound(PerimeterStageJournal.KEY).remove("Entries");
        assertThrows(IllegalArgumentException.class,()->PerimeterStageJournal.get(missing,project));
        var wrong=core.copy();wrong.getCompound(PerimeterStageJournal.KEY).putLong("Version",1);
        assertThrows(IllegalArgumentException.class,()->PerimeterStageJournal.get(wrong,project));
        var attempt=core.copy();attempt.getCompound(PerimeterStageJournal.KEY).getList("Entries",Tag.TAG_COMPOUND).getCompound(0)
                .getList("Attempts",Tag.TAG_COMPOUND).getCompound(0).putInt("Index",1);
        assertThrows(IllegalArgumentException.class,()->PerimeterStageJournal.get(attempt,project));
        var state=core.copy();state.getCompound(PerimeterStageJournal.KEY).getList("Entries",Tag.TAG_COMPOUND).getCompound(0)
                .getList("Attempts",Tag.TAG_COMPOUND).getCompound(0).putString("State","UNKNOWN");
        assertThrows(IllegalArgumentException.class,()->PerimeterStageJournal.get(state,project));
    }
    @Test void failedDirtySignalLeavesAnIntentButNoRetryCanMintAPermit() {
        var project=PerimeterProjectTest.project();var core=new CompoundTag();
        PerimeterProjectStore.prepare(core,project,()->{});
        PerimeterStageJournal.prepare(core,project,()->{});
        assertThrows(IllegalStateException.class,()->PerimeterStageJournal.begin(core,project,marker,()->{throw new IllegalStateException("disk signal");}));
        assertNull(PerimeterStageJournal.begin(core,project,marker,()->{}));
        var canceled=store(core,project,project.cancel(project.check(),"Canceled before acceptance"));
        PerimeterStageJournal.retired(core,canceled,()->{});
        assertThrows(IllegalArgumentException.class,()->PerimeterStageJournal.begin(core,canceled,marker,()->{}));
    }

    @Test void allMutationsRequireTheExactAuthoritativeStoreSnapshot() {
        var project=PerimeterProjectTest.project();var core=new CompoundTag();
        assertThrows(IllegalArgumentException.class,()->PerimeterStageJournal.prepare(core,project,()->{}));
        prepare(core,project);var permit=PerimeterStageJournal.begin(core,project,marker,()->{});
        var newer=store(core,project,project.waitFor(project.check(),"Waiting for owner"));
        assertFalse(permit.claim(core,project));assertFalse(permit.claim(core,newer));
        assertThrows(IllegalArgumentException.class,()->PerimeterStageJournal.prepare(core,project,()->{}));
        assertThrows(IllegalArgumentException.class,()->PerimeterStageJournal.begin(core,project,marker,()->{}));
        assertThrows(IllegalArgumentException.class,()->PerimeterStageJournal.live(core,project,()->{}));
        assertThrows(IllegalArgumentException.class,()->PerimeterStageJournal.retired(core,project,()->{}));
        assertNull(PerimeterStageJournal.begin(core,newer,marker,()->{}));
    }
    @Test void cancellationAndRecoveryCannotUseAnOldPermitOrMarkTheIntentLive() {
        var project=PerimeterProjectTest.project();var core=new CompoundTag();prepare(core,project);
        var permit=PerimeterStageJournal.begin(core,project,marker,()->{});
        var canceled=store(core,project,project.cancel(project.check(),"Canceled"));
        assertFalse(permit.claim(core,canceled));assertFalse(permit.claim(core,project));
        assertThrows(IllegalArgumentException.class,()->PerimeterStageJournal.live(core,canceled,()->{}));
        assertThrows(IllegalArgumentException.class,()->PerimeterStageJournal.begin(core,project,marker,()->{}));
        assertThrows(IllegalArgumentException.class,()->PerimeterStageJournal.prepare(core,project,()->{}));
        PerimeterStageJournal.retired(core,canceled,()->{});
        var recovering=new CompoundTag();prepare(recovering,project);
        var recoveryPermit=PerimeterStageJournal.begin(recovering,project,marker,()->{});
        var blocked=store(recovering,project,project.blockRecovery(project.check(),"Unknown entity save"));
        assertFalse(recoveryPermit.claim(recovering,blocked));assertFalse(recoveryPermit.claim(recovering,project));
        assertThrows(IllegalArgumentException.class,()->PerimeterStageJournal.live(recovering,blocked,()->{}));
        var restored=store(recovering,blocked,blocked.reconciled(blocked.check(),PerimeterProject.State.PREPARED_UNPAID));
        assertFalse(recoveryPermit.claim(recovering,restored));
        assertNull(PerimeterStageJournal.begin(recovering,restored,marker,()->{}));
    }
    @Test void aNewSpawnPermitCannotBecomeAnActiveOrPaidStageCapability() {
        var project=PerimeterProjectTest.project();var core=new CompoundTag();prepare(core,project);
        var permit=PerimeterStageJournal.begin(core,project,marker,()->{});
        var paid=PerimeterProjectStore.consumeOnce(core,project.header().projectId(),project.manifestHash(),64,true,()->{}).project();
        assertFalse(permit.claim(core,paid));assertFalse(permit.claim(core,project));
        var running=store(core,paid,paid.activate(paid.check()));
        assertFalse(permit.claim(core,running));
        assertThrows(IllegalArgumentException.class,()->PerimeterStageJournal.begin(core,running,marker,()->{}));
    }
    @Test void permitBindsItsOriginalCoreAndExactMarkerIntentAndPositionsRoundtrip() {
        var project=PerimeterProjectTest.project();var core=new CompoundTag();prepare(core,project);
        assertThrows(IllegalArgumentException.class,()->PerimeterStageJournal.begin(core,project,new BlockPos(8,2048,8),()->{}));
        var permit=PerimeterStageJournal.begin(core,project,marker,()->{});
        assertFalse(permit.claim(core.copy(),project));
        attempts(core).getCompound(0).putLong("Marker",marker.east().asLong());
        assertFalse(permit.claim(core,project));
    }
    @Test void terminalCompactionCannotEraseEvidenceWithoutTheExactCommittedReceipt() {
        var project=PerimeterProjectTest.project();var core=new CompoundTag();prepare(core,project);
        PerimeterStageJournal.begin(core,project,marker,()->{});
        var canceled=store(core,project,project.cancel(project.check(),"Canceled"));
        PerimeterStageJournal.retired(core,canceled,()->{});
        var terminal=PerimeterTerminalReceipt.compact(canceled,proof(canceled));
        assertThrows(IllegalArgumentException.class,()->PerimeterStageJournal.compacted(core,terminal,()->{}));
        var committed=PerimeterProjectStore.compact(core,canceled.check(),proof(canceled),()->{});
        assertNotEquals(terminal.receiptHash(),committed.receiptHash());
        var before=core.copy();
        assertThrows(IllegalArgumentException.class,()->PerimeterStageJournal.compacted(core,terminal,()->{}));assertEquals(before,core);
        var wrongArea=core.copy();attempts(wrongArea).getCompound(0).putUUID("Area",UUID.randomUUID());
        assertThrows(IllegalArgumentException.class,()->PerimeterStageJournal.compacted(wrongArea,committed,()->{}));
        PerimeterStageJournal.compacted(core,committed,()->{});
        assertNull(PerimeterStageJournal.get(core,project));
        PerimeterStageJournal.compacted(core,committed,()->{});
        assertThrows(IllegalArgumentException.class,()->PerimeterStageJournal.compacted(core,terminal,()->{}));
        assertThrows(IllegalArgumentException.class,()->PerimeterStageJournal.prepare(core,project,()->{}));
        assertThrows(IllegalArgumentException.class,()->PerimeterStageJournal.begin(core,project,marker,()->{}));
    }
    @Test void terminalCleanupRequiresEveryVerifiedAttemptAndItsExactOrderedPrefix() {
        var project=PerimeterProjectTest.project();var core=new CompoundTag();prepare(core,project);
        PerimeterStageJournal.begin(core,project,marker,()->{});PerimeterStageJournal.live(core,project,()->{});
        var paid=PerimeterProjectStore.consumeOnce(core,project.header().projectId(),project.manifestHash(),64,true,()->{}).project();
        var running=store(core,paid,paid.activate(paid.check()));
        var verified=store(core,running,running.verifyStage(running.check(),running.expectedStageReceipt()));
        PerimeterStageJournal.retired(core,verified,()->{});
        var canceled=store(core,verified,verified.cancel(verified.check(),"Canceled after stage verification"));
        var terminal=PerimeterProjectStore.compact(core,canceled.check(),proof(canceled),()->{});
        var missing=core.copy();attempts(missing).clear();
        assertThrows(IllegalArgumentException.class,()->PerimeterStageJournal.compacted(missing,terminal,()->{}));
        var live=core.copy();attempts(live).getCompound(0).putString("State","LIVE");
        assertThrows(IllegalArgumentException.class,()->PerimeterStageJournal.compacted(live,terminal,()->{}));
        PerimeterStageJournal.compacted(core,terminal,()->{});
    }
    private static void prepare(CompoundTag core,PerimeterProject project) {
        PerimeterProjectStore.prepare(core,project,()->{});PerimeterStageJournal.prepare(core,project,()->{});
    }
    private static PerimeterProject store(CompoundTag core,PerimeterProject previous,PerimeterProject next) {
        return PerimeterProjectStore.replace(core,previous.check(),next,()->{});
    }
    private static net.minecraft.nbt.ListTag attempts(CompoundTag core) {
        return core.getCompound(PerimeterStageJournal.KEY).getList("Entries",Tag.TAG_COMPOUND).getCompound(0)
                .getList("Attempts",Tag.TAG_COMPOUND);
    }
    // Synthetic model witnesses only; production callers must independently prove world cleanup.
    private static PerimeterTerminalReceipt.CleanupProof proof(PerimeterProject terminal) {
        var h=terminal.header();return new PerimeterTerminalReceipt.CleanupProof(h.projectId(),h.generation(),terminal.manifestHash(),terminal.revision(),
                terminal.state(),h.owner(),h.builder(),UUID.randomUUID(),terminal.stages().stream().map(PerimeterProject.Stage::areaId).toList(),
                "1".repeat(64),"2".repeat(64),"3".repeat(64));
    }
}
