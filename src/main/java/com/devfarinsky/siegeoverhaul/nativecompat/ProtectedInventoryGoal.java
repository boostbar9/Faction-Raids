package com.devfarinsky.siegeoverhaul.nativecompat;

import com.talhanation.workers.entities.BuilderEntity;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Shared cleanup ordering around original native goals; never substitutes transfer/payment logic. */
abstract class ProtectedInventoryGoal extends Goal {
    static final class Session {
        final BuilderEntity worker;
        final LinkedHashSet<ProtectedInventoryGoal> pending = new LinkedHashSet<>();
        private boolean draining;
        private ProtectedInventoryGoal upkeepOwner;
        Session(BuilderEntity worker) { this.worker = worker; }
        boolean admitted(ProtectedInventoryGoal goal) { return !goal.upkeep() || upkeepOwner==null || upkeepOwner==goal; }
        void claim(ProtectedInventoryGoal goal) { if(goal.upkeep())upkeepOwner=goal; }
        void release(ProtectedInventoryGoal goal) { if(upkeepOwner==goal)upkeepOwner=null; }
        boolean ready() { return ready(null); }
        boolean ready(ProtectedInventoryGoal caller) {
            if (draining) return false;
            draining = true;
            try {
                boolean blocked=false;
                for (var goal : new ArrayList<>(pending)) {
                    if (!goal.finishStop()) {
                        if(caller==null || caller==goal || caller.upkeep()&&goal.upkeep())blocked=true;
                    } else pending.remove(goal);
                }
                return !blocked;
            } finally { draining = false; }
        }
    }

    final BuilderEntity worker;
    final Goal delegate;
    final Session session;
    private boolean started, protectedLifecycle, stopFailed;
    protected List<Container> writes = List.of();

    ProtectedInventoryGoal(BuilderEntity worker, Goal delegate, Session session) {
        this.worker=worker; this.delegate=delegate; this.session=session; setFlags(delegate.getFlags());
    }
    abstract String beforeStart();
    abstract String beforeTick();
    abstract String cleanup();
    void afterStart() {}
    void afterTick() {}
    void stopped() {}
    boolean upkeep() { return false; }

    private String safeCleanup() {
        try { return cleanup(); }
        catch (RuntimeException | LinkageError unavailable) { return "Paused: load native inventory cleanup sources to resume"; }
    }
    private boolean check(boolean start) {
        try {
            String context=NativeConstructionGuard.storageProblem(worker, java.util.Set.of());
            if(context==null)context=start?beforeStart():beforeTick();
            if(context!=null){NativeConstructionGuard.pauseStorage(worker,context);return false;}
            return true;
        } catch(RuntimeException|LinkageError unavailable) {
            NativeConstructionGuard.pauseStorage(worker,"Paused: native inventory source or state cannot be verified");return false;
        }
    }
    @Override public boolean canUse() {
        if(!session.ready(this)||!session.admitted(this))return false;
        if(!delegate.canUse())return false;
        return !NativeConstructionGuard.hasProtectedReceipt(worker)||check(true);
    }
    @Override public boolean canContinueToUse() { return started && delegate.canContinueToUse(); }
    @Override public boolean isInterruptable() { return delegate.isInterruptable(); }
    @Override public boolean requiresUpdateEveryTick() { return delegate.requiresUpdateEveryTick(); }
    @Override public void start() {
        if(!session.ready(this)||!session.admitted(this))return;
        if(NativeConstructionGuard.hasProtectedReceipt(worker)&&!check(true))return;
        protectedLifecycle=NativeConstructionGuard.hasProtectedReceipt(worker);
        started=true;
        if(protectedLifecycle)session.claim(this);
        if(!protectedLifecycle){delegate.start();return;}
        try { delegate.start(); afterStart(); }
        catch(RuntimeException|LinkageError unavailable) {
            started=false; session.pending.add(this);
            NativeConstructionGuard.pauseStorage(worker,"Paused: native inventory start could not be verified");
        }
    }
    @Override public void stop() {
        if(!started)return;
        started=false;
        if(!protectedLifecycle){delegate.stop();stopped();return;}
        if(!finishStop())session.pending.add(this);
    }
    private boolean finishStop() {
        if(stopFailed) {
            NativeConstructionGuard.pauseStorage(worker,"Paused: native inventory cleanup needs review");return false;
        }
        // A protected lifecycle keeps its bounded close/dirty obligation after owner cancellation.
        // This checks loading/known read locations only; it does not resurrect transfer authority.
        String problem=protectedLifecycle?safeCleanup():null;
        if(problem!=null){NativeConstructionGuard.pauseStorage(worker,problem);return false;}
        try { delegate.stop(); protectedLifecycle=false; session.release(this); stopped(); return true; }
        catch(RuntimeException|LinkageError unavailable) {
            stopFailed=true; // A partly completed payment/timer callback must never be replayed blindly.
            NativeConstructionGuard.pauseStorage(worker,"Paused: native inventory cleanup needs review");return false;
        }
    }
    @Override public void tick() {
        if(!started)return;
        if(!session.ready(this)||!session.admitted(this))return; // Shared across all adapters: no late old stop can finalize a new transfer.
        if(!NativeConstructionGuard.hasProtectedReceipt(worker)){delegate.tick();return;}
        writes=List.of();
        if(!check(false))return;
        try {
            try { delegate.tick(); }
            finally {
                Throwable failure=null;
                for(Container source:writes)try {
                    source.setChanged();ProtectedStorageAccess.recordDirtyNotification(worker);
                } catch(RuntimeException|LinkageError unavailable){if(failure==null)failure=unavailable;}
                if(failure!=null)throw new IllegalStateException("Native source dirty notification failed",failure);
            }
            afterTick();
        } catch(RuntimeException|LinkageError unavailable) {
            if(!writes.isEmpty())ProtectedBuilderHandMirror.requireInventoryReview(worker.getPersistentData());
            NativeConstructionGuard.pauseStorage(worker,"Paused: native inventory operation needs review; no amounts were selected");
        }
    }
}
