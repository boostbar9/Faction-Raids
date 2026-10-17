package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksManifest;
import com.talhanation.workers.entities.BuilderEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;

import java.util.Set;

/** New-job inventory routing. The old paid-area authority is never reused as an earthworks lease. */
final class EarthworksInventoryAccess {
    private EarthworksInventoryAccess() {}

    static boolean selected(Mob worker) { return NativeEarthworksJobs.selected(worker); }
    static boolean guarded(Mob worker) {
        return selected(worker) || NativeConstructionGuard.hasProtectedReceipt(worker);
    }
    static String problem(Mob worker, Set<BlockPos> sources) {
        if (!selected(worker)) return NativeConstructionGuard.storageProblem(worker, sources);
        if (NativeConstructionGuard.hasProtectedReceipt(worker)) return "Paused: incompatible construction inventory authorities";
        return NativeEarthworksJobs.inventoryProblem(worker, sources);
    }
    static boolean sharedStorageFactionMatches(Mob worker) {
        return selected(worker) ? NativeEarthworksJobs.sharedStorageFactionMatches(worker)
                : NativeConstructionGuard.sharedStorageFactionMatches(worker);
    }
    /** Bind a running native inventory lifecycle to its original immutable job, including active step. */
    static Object identity(BuilderEntity worker) {
        if (!selected(worker)) return null;
        var lease = NativeEarthworksJobs.inventoryLease(worker);
        if (lease == null) throw new IllegalStateException("Earthworks inventory lease unavailable");
        return EarthworksSupplyDemand.Scope.from(lease);
    }
    static boolean suppliesReady(BuilderEntity worker, PerimeterEarthworksManifest.Step step) {
        String authority = problem(worker, Set.of());
        if (!selected(worker) || authority != null) throw new IllegalStateException("Earthworks supply authority unavailable: " + authority);
        return EarthworksSupplyDemand.prepare(worker, step);
    }
}
