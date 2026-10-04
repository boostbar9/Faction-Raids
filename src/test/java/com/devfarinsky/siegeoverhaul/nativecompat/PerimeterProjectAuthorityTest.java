package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge;
import com.devfarinsky.siegeoverhaul.core.PerimeterProjectStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PerimeterProjectAuthorityTest extends MinecraftTestSupport {
    @Test void fullTerritoryComparisonCannotBeReplacedByActiveStageOrEntitySelectors() {
        var project=ConstructionProjectLedgerTest.project();var header=project.header();
        assertTrue(PerimeterProjectAuthority.territoryMatches(project,
                new RecruitsClaimsBridge.TerritorySnapshot(header.faction(),header.territory(),null)));
        var expanded=new HashSet<>(header.territory());expanded.add(new ChunkPos(20,20));
        assertFalse(PerimeterProjectAuthority.territoryMatches(project,
                new RecruitsClaimsBridge.TerritorySnapshot(header.faction(),expanded,null)));
        assertFalse(PerimeterProjectAuthority.territoryMatches(project,
                new RecruitsClaimsBridge.TerritorySnapshot("foreign",header.territory(),null)));
        assertFalse(PerimeterProjectAuthority.territoryMatches(project,
                new RecruitsClaimsBridge.TerritorySnapshot(header.faction(),header.territory(),"unavailable")));
    }
    @Test void selectorIsBoundAndMalformedRequiredVersionOrStageCannotFallBackToLegacy() {
        var project=ConstructionProjectLedgerTest.project();var area=mock(Entity.class);var data=new CompoundTag();
        when(area.getPersistentData()).thenReturn(data);when(area.getUUID()).thenReturn(project.active().areaId());
        PerimeterProjectAuthority.stamp(area,project);var selector=PerimeterProjectAuthority.read(data);
        assertEquals(project.header().projectId(),selector.projectId());assertEquals(project.manifestHash(),selector.manifestHash());
        assertEquals(project.active().digest(),selector.stageDigest());
        assertThrows(IllegalArgumentException.class,()->PerimeterProjectAuthority.stamp(area,project));
        var missing=data.copy();missing.remove(PerimeterProjectAuthority.REQUIRED);
        assertThrows(IllegalArgumentException.class,()->PerimeterProjectAuthority.read(missing));
        var coerced=data.copy();coerced.getCompound(PerimeterProjectAuthority.KEY).putDouble("Version",1);
        assertThrows(IllegalArgumentException.class,()->PerimeterProjectAuthority.read(coerced));
        var future=data.copy();future.getCompound(PerimeterProjectAuthority.KEY).putInt("Stage",256);
        assertThrows(IllegalArgumentException.class,()->PerimeterProjectAuthority.read(future));
        var zero=data.copy();zero.getCompound(PerimeterProjectAuthority.KEY).putUUID("Project",new UUID(0,0));
        assertThrows(IllegalArgumentException.class,()->PerimeterProjectAuthority.read(zero));
    }
    @Test void paidFlagAloneDoesNotAuthorizePreparedRecoveryOrTerminalStates() {
        var project=ConstructionProjectLedgerTest.project();var core=new CompoundTag();core.putLong("BankEmeralds",64);
        assertFalse(PerimeterProjectAuthority.workState(project,false,false));
        assertTrue(PerimeterProjectAuthority.workState(project,true,false));
        PerimeterProjectStore.prepare(core,project,()->{});
        project=PerimeterProjectStore.consumeOnce(core,project.header().projectId(),project.manifestHash(),64,false,()->{}).project();
        assertFalse(PerimeterProjectAuthority.workState(project,false,false));
        project=project.activate(project.check());assertTrue(PerimeterProjectAuthority.workState(project,false,false));
        var verified=project.verifyStage(project.check(),project.expectedStageReceipt());
        assertFalse(PerimeterProjectAuthority.workState(verified,false,false));
        assertTrue(PerimeterProjectAuthority.workState(verified,false,true));
        var canceled=project.cancel(project.check(),"Owner canceled");
        assertFalse(PerimeterProjectAuthority.workState(canceled,true,true));
        var blocked=project.blockRecovery(project.check(),"Missing marker");
        assertFalse(PerimeterProjectAuthority.workState(blocked,true,true));
    }
}
