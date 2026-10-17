package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksJournal;
import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksManifest;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.entities.workarea.BuildArea;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Real controller authority for a separately accepted local job; no user-facing commissioning caller yet. */
final class EarthworksExecution implements WorkersEarthworksPort.Authority {
    private final ServerLevel level;
    private final EarthworksJobLedger.Job job;
    private final BuilderEntity worker;
    private final EarthworksBuildArea area;
    EarthworksExecution(ServerLevel level,EarthworksJobLedger.Job job,BuilderEntity worker,EarthworksBuildArea area){
        this.level=level;this.job=job;this.worker=worker;this.area=area;
    }
    @Override public String newLeaseProblem(PerimeterEarthworksManifest manifest,PerimeterEarthworksJournal journal,BuilderEntity builder,BuildArea marker){
        return builder==worker && marker==area && manifest==job.manifest && journal==job.read().journal()
                && NativeEarthworksJobs.authenticated(worker,true)==job && area.matches(job)
                ? null : "Paused: exact local grading authority or marker changed";
    }
    @Override public long editRevision(BlockPos pos){
        var observed=job.manifest.observations().get(pos.asLong());
        if(observed==null||ConstructionEditLedger.get(level).edited(job.area))throw new IllegalStateException("Grading original/edit authority changed");
        return observed.editRevision();
    }
    @Override public String nativeInventoryProblem(PerimeterEarthworksManifest manifest,PerimeterEarthworksJournal journal,BuilderEntity worker){
        String problem=NativeEarthworksJobs.inventoryProblem(worker,java.util.Set.of());if(problem!=null)return problem;
        return ProtectedStorageAccess.runningProblem(worker);
    }
    @Override public String nativeDropConfigurationProblem(PerimeterEarthworksManifest manifest,PerimeterEarthworksJournal journal){
        return "Paused: dirt loot/global-modifier/drop-event configuration still needs pinned runtime admission";
    }
    @Override public boolean exactRemovalReviewed(String hash,long pos,long revision){
        // No live cut activation until the version-audited drop configuration and exact-removal review are installed.
        return false;
    }
    @Override public String standingAndEscapeProblem(PerimeterEarthworksManifest manifest,PerimeterEarthworksJournal journal,BuilderEntity worker){
        return EarthworksStandingAccess.problem(level,worker,area,job);
    }
    @Override public void navigateReviewedRoute(PerimeterEarthworksManifest manifest,PerimeterEarthworksJournal journal,BuilderEntity worker){
        // First actual slice starts at already-proved local standing. It cannot borrow the legacy horizontal/heightmap route.
        // Autonomous approach/ascent is gated until a connected, loaded, post-mutation route has been independently proved.
    }
}
