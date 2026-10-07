package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PerimeterBuilderAssignmentsTest extends MinecraftTestSupport {
    private PerimeterProject running() { var paid=PerimeterProjectTest.project().paid(false); return paid.activate(paid.check()); }
    @Test void replacementAndReloadPreservePaidManifestAndCompletedStageHistory() {
        var project=running();var original=project.save();var assignments=new PerimeterBuilderAssignments();
        UUID next=UUID.randomUUID();
        assignments.replace(project,project.header().builder(),next,project.active().areaId(),"a".repeat(64));
        assertEquals(next,assignments.builder(project));assertEquals(original,project.save());
        var loaded=PerimeterBuilderAssignments.load(assignments.save());
        assertEquals(next,loaded.builder(PerimeterProject.load(original)));
        assertTrue(loaded.canRebind(project,project.header().builder(),project.active().areaId()));
        assertFalse(loaded.canRebind(project,UUID.randomUUID(),project.active().areaId()));
        assertFalse(loaded.canRebind(project,project.header().builder(),UUID.randomUUID()));
        assertEquals(next,loaded.builder(project.header().projectId(),project.header().generation(),project.manifestHash(),project.header().builder()));
    }
    @Test void repeatedDeathsRejectStaleAndPreviouslyRetiredWorkers() {
        var p=running();var a=new PerimeterBuilderAssignments();UUID b=UUID.randomUUID(),c=UUID.randomUUID();
        a.replace(p,p.header().builder(),b,p.active().areaId(),"a".repeat(64));
        assertThrows(IllegalArgumentException.class,()->a.replace(p,p.header().builder(),c,p.active().areaId(),"b".repeat(64)));
        a.replace(p,b,c,p.active().areaId(),"b".repeat(64));
        assertEquals(c,PerimeterBuilderAssignments.load(a.save()).builder(p));
        assertThrows(IllegalArgumentException.class,()->a.replace(p,c,b,p.active().areaId(),"c".repeat(64)));
        assertThrows(IllegalArgumentException.class,()->a.replace(p,c,p.header().builder(),p.active().areaId(),"c".repeat(64)));
    }
    @Test void unpaidCanceledWrongSectionAndMalformedDeathNeverGrantAssignment() {
        var p=running();var a=new PerimeterBuilderAssignments();UUID next=UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,()->a.replace(PerimeterProjectTest.project(),p.header().builder(),next,p.active().areaId(),"a".repeat(64)));
        assertThrows(IllegalArgumentException.class,()->a.replace(p.cancel(p.check(),"cancel"),p.header().builder(),next,p.active().areaId(),"a".repeat(64)));
        assertThrows(IllegalArgumentException.class,()->a.replace(p,p.header().builder(),next,UUID.randomUUID(),"a".repeat(64)));
        assertThrows(IllegalArgumentException.class,()->a.replace(p,p.header().builder(),next,p.active().areaId(),"missing"));
        assertEquals(p.header().builder(),a.builder(p));
    }
    @Test void corruptedChainAndConflictingPaidProjectFailClosed() {
        var p=running();var a=new PerimeterBuilderAssignments();a.replace(p,p.header().builder(),UUID.randomUUID(),p.active().areaId(),"a".repeat(64));
        var saved=a.save();saved.getList("Projects",Tag.TAG_COMPOUND).getCompound(0).getList("History",Tag.TAG_COMPOUND).getCompound(0).putUUID("From",UUID.randomUUID());
        assertThrows(IllegalArgumentException.class,()->PerimeterBuilderAssignments.load(saved));
        assertThrows(IllegalArgumentException.class,()->a.builder(p.header().projectId(),p.header().generation()+1,p.manifestHash(),p.header().builder()));
    }
}
