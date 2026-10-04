package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class NativePerimeterRecoveryOrderTest extends MinecraftTestSupport {
    private final BlockPos marker=new BlockPos(8,64,8);
    private PerimeterStageJournal.Entry journal(PerimeterProject project,List<PerimeterStageJournal.Attempt> attempts) {
        return new PerimeterStageJournal.Entry(project.header().projectId(),project.header().generation(),project.manifestHash(),attempts);
    }
    @Test void firstCreationRequiresUntouchedLeaseAndNoRecordedAttempt() {
        var p=ConstructionProjectLedgerTest.project();var empty=journal(p,List.of());
        assertTrue(NativePerimeterProjects.creationHistoryMatches(p,empty,-1,true));
        assertFalse(NativePerimeterProjects.creationHistoryMatches(p,empty,0,true));
        assertFalse(NativePerimeterProjects.creationHistoryMatches(p,empty,-2,true));
        var intent=new PerimeterStageJournal.Attempt(0,p.active().areaId(),marker,PerimeterStageJournal.State.INTENT);
        assertFalse(NativePerimeterProjects.creationHistoryMatches(p,journal(p,List.of(intent)),-1,true));
        assertFalse(NativePerimeterProjects.creationHistoryMatches(p,null,-1,true));
    }
    @Test void canceledBlockedAndPaidSnapshotsNeverMintNewCreationAuthority() {
        var p=ConstructionProjectLedgerTest.project();var empty=journal(p,List.of());
        assertFalse(NativePerimeterProjects.creationHistoryMatches(p.cancel(p.check(),"canceled"),empty,-1,true));
        assertFalse(NativePerimeterProjects.creationHistoryMatches(p.blockRecovery(p.check(),"unknown save"),empty,-1,true));
        var core=new CompoundTag();core.putLong("BankEmeralds",1000);PerimeterProjectStore.prepare(core,p,()->{});
        var paid=PerimeterProjectStore.consumeOnce(core,p.header().projectId(),p.manifestHash(),64,false,()->{}).project();
        assertFalse(NativePerimeterProjects.creationHistoryMatches(paid,empty,-1,true));
        assertFalse(NativePerimeterProjects.creationHistoryMatches(paid.activate(paid.check()),empty,-1,true));
    }
    @Test void nextSectionRequiresExactRetiredPrefixAndRejectsAheadOfCoreLedgerOrSavedIntent() {
        var initial=ConstructionProjectLedgerTest.project();var core=new CompoundTag();core.putLong("BankEmeralds",1000);
        PerimeterProjectStore.prepare(core,initial,()->{});
        var p=PerimeterProjectStore.consumeOnce(core,initial.header().projectId(),initial.manifestHash(),64,false,()->{}).project();
        p=p.activate(p.check());p=p.verifyStage(p.check(),p.expectedStageReceipt());p=p.retireVerifiedStage(p.check());
        assertNotNull(p.active());assertEquals(1,p.activeStage());
        var retired=new PerimeterStageJournal.Attempt(0,p.stages().get(0).areaId(),marker,PerimeterStageJournal.State.RETIRED);
        var exact=journal(p,List.of(retired));assertTrue(NativePerimeterProjects.creationHistoryMatches(p,exact,0,true));
        assertFalse(NativePerimeterProjects.creationHistoryMatches(p,exact,1,true));
        assertFalse(NativePerimeterProjects.creationHistoryMatches(p,exact,0,false));
        assertFalse(NativePerimeterProjects.creationHistoryMatches(p,journal(p,List.of()),0,true));
        var pending=new PerimeterStageJournal.Attempt(1,p.active().areaId(),marker,PerimeterStageJournal.State.INTENT);
        assertFalse(NativePerimeterProjects.creationHistoryMatches(p,journal(p,List.of(retired,pending)),1,true));
        var live=new PerimeterStageJournal.Attempt(0,retired.area(),marker,PerimeterStageJournal.State.LIVE);
        assertFalse(NativePerimeterProjects.creationHistoryMatches(p,journal(p,List.of(live)),0,true));
        var wrong=new PerimeterStageJournal.Entry(UUID.randomUUID(),p.header().generation(),p.manifestHash(),List.of(retired));
        assertFalse(NativePerimeterProjects.creationHistoryMatches(p,wrong,0,true));
        var generation=new PerimeterStageJournal.Entry(p.header().projectId(),p.header().generation()+1,p.manifestHash(),List.of(retired));
        assertFalse(NativePerimeterProjects.creationHistoryMatches(p,generation,0,true));
    }
}
