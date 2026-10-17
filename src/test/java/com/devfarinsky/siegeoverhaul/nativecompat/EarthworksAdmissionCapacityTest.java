package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksJournal;
import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksManifest;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class EarthworksAdmissionCapacityTest extends MinecraftTestSupport {
    @Test void fullJobLedgerRefusesBeforeAnySpatialReservationAndRetainsAllHistory() {
        var jobs=new EarthworksJobLedger();var edits=new ConstructionEditLedger();
        for(int i=0;i<EarthworksJobLedger.MAX_JOBS;i++)jobs.prepare(manifest(),binding(),UUID.randomUUID(),BlockPos.ZERO);
        var candidate=manifest();var binding=binding();UUID area=UUID.randomUUID();var reservation=cells(candidate);
        var beforeJobs=jobs.save(new CompoundTag());var beforeEdits=edits.save(new CompoundTag());
        assertFalse(jobs.canPrepare(candidate,binding,area,BlockPos.ZERO));
        assertThrows(IllegalStateException.class,()->EarthworksCommission.reservePrepared(jobs,edits,candidate,binding,area,BlockPos.ZERO,reservation));
        assertEquals(beforeJobs,jobs.save(new CompoundTag()));assertEquals(beforeEdits,edits.save(new CompoundTag()));
        assertFalse(edits.reserves(reservation));assertFalse(edits.matches(area,reservation));assertNull(jobs.job(candidate.header().project()));
    }
    @Test void duplicateProjectBuilderOrAreaNeverCreatesAnAdditionalSpatialReservation() {
        var jobs=new EarthworksJobLedger();var first=manifest();UUID firstArea=UUID.randomUUID();jobs.prepare(first,binding(),firstArea,BlockPos.ZERO);
        for(int conflict=0;conflict<3;conflict++){
            var next=manifest();
            if(conflict==0)next=first;
            if(conflict==1){var h=next.header();next=new PerimeterEarthworksManifest(new PerimeterEarthworksManifest.Header(h.project(),h.generation(),h.owner(),first.header().builder(),h.dimension(),h.faction(),h.claimsDigest(),h.layoutDigest(),h.policy(),h.component(),h.planeY(),h.minY(),h.maxY(),h.feeVersion(),h.price()),new ArrayList<>(next.observations().values()),next.steps());}
            UUID area=conflict==2?firstArea:UUID.randomUUID();var edits=new ConstructionEditLedger();var before=edits.save(new CompoundTag());
            var candidate=next;var binding=binding();var reservation=cells(candidate);
            assertThrows(IllegalStateException.class,()->EarthworksCommission.reservePrepared(jobs,edits,candidate,binding,area,BlockPos.ZERO,reservation));
            assertEquals(before,edits.save(new CompoundTag()));
        }
    }
    @Test void readOnlyPreflightDoesNotConsumeAJobSlotOrSpatialLease() {
        var jobs=new EarthworksJobLedger();var manifest=manifest();var binding=binding();UUID area=UUID.randomUUID();
        var before=jobs.save(new CompoundTag());assertTrue(jobs.canPrepare(manifest,binding,area,BlockPos.ZERO));
        assertTrue(jobs.canPrepare(manifest,binding,area,BlockPos.ZERO));assertEquals(before,jobs.save(new CompoundTag()));
        var edits=new ConstructionEditLedger();EarthworksCommission.reservePrepared(jobs,edits,manifest,binding,area,BlockPos.ZERO,cells(manifest));
        assertTrue(edits.matches(area,cells(manifest)));assertNull(jobs.job(manifest.header().project()));
        var admitted=jobs.prepare(manifest,binding,area,BlockPos.ZERO);assertFalse(admitted.paid());
        assertFalse(admitted.recoveryAdmissionEstablished());
    }
    private static Set<BlockPos> cells(PerimeterEarthworksManifest manifest){return manifest.observations().keySet().stream().map(BlockPos::of).collect(Collectors.toSet());}
    private static PerimeterEarthworksJournal.Binding binding(){return new PerimeterEarthworksJournal.Binding(UUID.randomUUID(),"a".repeat(64),"b".repeat(64));}
    private static PerimeterEarthworksManifest manifest(){var full=NativeEarthworksAdapterTest.manifest();return new PerimeterEarthworksManifest(full.header(),new ArrayList<>(full.observations().values()),full.steps().subList(0,2));}
}
