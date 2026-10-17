package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksJournal;
import com.talhanation.workers.entities.BuilderEntity;

/** Cleanup-only retained authority. No requests, work, transfers, refunds or receipt/selector removal. */
final class EarthworksCanceledCleanup {
    private EarthworksCanceledCleanup() {}
    static boolean beforeWorkerTick(BuilderEntity worker,EarthworksJobLedger.Job job) {
        try {
            if(job==null||NativeEarthworksJobs.authenticated(worker,false)!=job||!job.recoveryAdmissionEstablished()
                    ||job.read().journal().state()!=PerimeterEarthworksJournal.State.CANCELED||job.read().inFlight()!=null
                    ||!NativeEarthworksJobs.matchesArea(worker,job)||WorkersConstructionRuntime.problem()!=null
                    ||NativeConstructionGuard.hasProtectedReceipt(worker)||NativeEarthworksJobs.handLifecycleProblem(worker)!=null
                    ||!EarthworksSupplyDemand.canceledEvidenceMatches(worker,job))return false;
            // A previous callback exception must never be downgraded to a normal close obligation.
            if(ProtectedInventoryCleanup.read(worker.getPersistentData()).containsValue(ProtectedInventoryCleanup.REVIEW))return false;
            if(job.runtimeGoal!=null&&job.runtimeWorker!=worker || !ProtectedStorageAccess.retainedEarthworksWrappers(worker,job))return false;
            if(!EarthworksWorkGoal.quiesce(worker,job.runtimeGoal))return false;
            // Existing live wrappers own their native close, dirty, flag/timer and earned-payment finalization.
            // Their source/identity guards and callback fence still run; no new start/tick is invoked here.
            return ProtectedStorageAccess.drainCleanup(worker);
        }catch(RuntimeException|LinkageError unavailable){return false;}
    }
}
