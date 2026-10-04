package com.devfarinsky.siegeoverhaul.nativecompat;

import com.talhanation.workers.entities.BuilderEntity;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;

/** Shared cleanup ordering around original native goals; never substitutes transfer/payment logic. */
abstract class ProtectedInventoryGoal extends Goal {
    static final class Session {
        final BuilderEntity worker;
        final LinkedHashSet<ProtectedInventoryGoal> pending = new LinkedHashSet<>();
        private boolean draining;
        private ProtectedInventoryGoal upkeepOwner;
        private final EnumMap<ProtectedStorageAccess.Kind,ProtectedInventoryGoal> owners =
                new EnumMap<>(ProtectedStorageAccess.Kind.class);
        Session(BuilderEntity worker) { this.worker = worker; }
        boolean admitted(ProtectedInventoryGoal goal) {
            return (!owners.containsKey(goal.kind()) || owners.get(goal.kind())==goal)
                    && (!goal.upkeep() || upkeepOwner==null || upkeepOwner==goal);
        }
        void claim(ProtectedInventoryGoal goal) { owners.put(goal.kind(),goal);if(goal.upkeep())upkeepOwner=goal; }
        void release(ProtectedInventoryGoal goal) { owners.remove(goal.kind(),goal);if(upkeepOwner==goal)upkeepOwner=null; }
        boolean owns(ProtectedInventoryGoal goal) { return owners.get(goal.kind())==goal; }
        boolean orphanedCleanup() {
            try {
                for(var kind:ProtectedInventoryCleanup.read(worker.getPersistentData()).keySet())
                    if(!owners.containsKey(kind))return true;
                return false;
            } catch(RuntimeException | LinkageError unavailable) { return true; }
        }
        boolean cleanupComplete() {
            return !draining && pending.isEmpty() && owners.isEmpty() && upkeepOwner==null
                    && !ProtectedInventoryCleanup.outstanding(worker.getPersistentData());
        }
        boolean ready() { return ready(null); }
        boolean ready(ProtectedInventoryGoal caller) {
            if (draining) return false;
            try {
                if(orphanedCleanup() || ProtectedInventoryCleanup.read(worker.getPersistentData()).containsValue(ProtectedInventoryCleanup.REVIEW)) {
                    NativeConstructionGuard.pauseStorage(worker,"Paused: interrupted native inventory cleanup needs review; callbacks were not replayed");
                    return false;
                }
            } catch(RuntimeException | LinkageError unavailable) { return false; }
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
    abstract ProtectedStorageAccess.Kind kind();
    abstract String beforeStart();
    abstract String beforeTick();
    abstract String cleanup();
    void afterStart() {}
    void afterTick() {}
    void stopped() {}
    boolean upkeep() { return false; }
    /** Only audited stable checkpoints may omit a post-reload cleanup obligation. */
    boolean cleanupObligation() { return true; }
    private void checkpoint() {
        if(cleanupObligation())ProtectedInventoryCleanup.record(worker.getPersistentData(),kind(),ProtectedInventoryCleanup.CLEANUP);
        else ProtectedInventoryCleanup.complete(worker.getPersistentData(),kind());
    }
    private void callbackFence() {
        ProtectedInventoryCleanup.record(worker.getPersistentData(),kind(),ProtectedInventoryCleanup.REVIEW);
    }
    private void failedCallback() {
        started=false;stopFailed=true;session.pending.add(this);
        // start/tick may call the native stop internally. No callback may be replayed
        // after an exception, even when there were no selected transfer writes.
        try { callbackFence(); }
        catch(RuntimeException | LinkageError unavailable) { /* Preserve unknown durable data verbatim. */ }
    }
    /** Cleanup-only inspection; callers cannot rewrite or discard the lifecycle. */
    final boolean legacyLifecycleActive() { return started && !protectedLifecycle; }
    final boolean cleanupComplete() { return !started && !protectedLifecycle && !stopFailed; }

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
        if(started || !session.ready(this)||!session.admitted(this))return;
        if(NativeConstructionGuard.hasProtectedReceipt(worker)&&!check(true))return;
        protectedLifecycle=NativeConstructionGuard.hasProtectedReceipt(worker);
        started=true;
        if(protectedLifecycle)session.claim(this);
        if(!protectedLifecycle){delegate.start();return;}
        try { callbackFence();delegate.start();afterStart();checkpoint(); }
        catch(RuntimeException|LinkageError unavailable) {
            failedCallback();
            NativeConstructionGuard.pauseStorage(worker,"Paused: native inventory start/cleanup needs review; callbacks were not replayed");
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
        try {
            if(protectedLifecycle)ProtectedInventoryCleanup.record(worker.getPersistentData(),kind(),ProtectedInventoryCleanup.CLEANUP);
            String problem=protectedLifecycle?safeCleanup():null;
            if(problem!=null){NativeConstructionGuard.pauseStorage(worker,problem);return false;}
            callbackFence();delegate.stop();stopped();
            ProtectedInventoryCleanup.complete(worker.getPersistentData(),kind());
            protectedLifecycle=false;session.release(this);return true;
        }
        catch(RuntimeException|LinkageError unavailable) {
            failedCallback(); // A partly completed payment/timer callback must never be replayed blindly.
            NativeConstructionGuard.pauseStorage(worker,"Paused: native inventory cleanup needs review");return false;
        }
    }
    @Override public void tick() {
        if(!started)return;
        // Cancellation can remove the receipt before GoalSelector calls stop.
        // A lifecycle admitted under protection must finish through guarded
        // cleanup, never turn into a legacy transfer halfway through its work.
        if(protectedLifecycle && !NativeConstructionGuard.hasProtectedReceipt(worker)) {
            stop();return;
        }
        if(!session.ready(this)||!session.admitted(this))return; // Shared across all adapters: no late old stop can finalize a new transfer.
        if(!NativeConstructionGuard.hasProtectedReceipt(worker)){delegate.tick();return;}
        writes=List.of();
        if(!check(false))return;
        try {
            callbackFence();
            try { delegate.tick(); }
            finally {
                Throwable failure=null;
                for(Container source:writes)try {
                    source.setChanged();ProtectedStorageAccess.recordDirtyNotification(worker);
                } catch(RuntimeException|LinkageError unavailable){if(failure==null)failure=unavailable;}
                if(failure!=null)throw new IllegalStateException("Native source dirty notification failed",failure);
            }
            afterTick();checkpoint();
        } catch(RuntimeException|LinkageError unavailable) {
            failedCallback();
            if(!writes.isEmpty())ProtectedBuilderHandMirror.requireInventoryReview(worker.getPersistentData());
            NativeConstructionGuard.pauseStorage(worker,"Paused: native inventory operation needs review; no amounts were selected");
        }
    }
}
