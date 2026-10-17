package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksManifest;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Explicitly unregistered goal for a future new-area lease. Installation must replace only that new
 * job's native goal at its audited priority; an old accepted/manual goal is never converted here.
 * MOVE/LOOK exclusion and genuine native eligibility are retained, including rechecks on mutation ticks.
 */
final class LocalEarthworksGoal extends Goal {
    private final PerimeterEarthworksManifest manifest;
    private final NativeEarthworksAdapter.Store store;
    private final WorkersEarthworksPort port;
    private final NativeEarthworksAdapter adapter;
    private boolean started, failed;
    private String lifecycleProblem = "";

    LocalEarthworksGoal(PerimeterEarthworksManifest manifest, NativeEarthworksAdapter.Store store, WorkersEarthworksPort port) {
        this.manifest = manifest; this.store = store; this.port = port;
        this.adapter = new NativeEarthworksAdapter(manifest, store, port);
        setFlags(port.nativeGoal().getFlags());
    }
    @Override public boolean canUse() {
        if (failed) return false;
        try {
            var saved = store.read();
            return store.recoveryAdmissionEstablished() && saved.journal().manifestHash().equals(manifest.hash())
                    && saved.inFlight() == null && port.nativeEligible() && port.leaseProblem(manifest, saved.journal()) == null
                    && (saved.journal().state() == com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksJournal.State.READY
                        || saved.journal().state() == com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksJournal.State.PENDING);
        } catch (RuntimeException | LinkageError unavailable) { fail("New earthworks lease/store is unavailable"); return false; }
    }
    @Override public boolean canContinueToUse() {
        if (!started || !canUse()) return false;
        try { return port.nativeGoal().canContinueToUse(); }
        catch (RuntimeException | LinkageError unavailable) { fail("Native earthworks eligibility is unavailable"); return false; }
    }
    @Override public boolean isInterruptable() { return true; }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void start() {
        if (!canUse()) return;
        try { port.nativeGoal().start(); started = true; }
        catch (RuntimeException | LinkageError unavailable) { fail("Native earthworks start is uncertain; it was not replayed"); }
    }
    @Override public void stop() {
        boolean stop = started && !failed; started = false;
        if (stop) try { port.nativeGoal().stop(); }
        catch (RuntimeException | LinkageError unavailable) { fail("Native earthworks stop is uncertain; cleanup was not replayed"); }
    }
    @Override public void tick() {
        if (!started || failed || !canContinueToUse()) return;
        try {
            var saved = store.read(); port.navigate(manifest, saved.journal());
            adapter.tick(); // Rechecks the actual first callback target and every live admission/eligibility gate.
        } catch (RuntimeException | LinkageError unavailable) { fail("Earthworks route/store is unavailable; work was not dispatched"); }
    }
    private void fail(String reason) { failed = true; started = false; lifecycleProblem = reason; }
    boolean cleanupKnown(){return !failed;}
    String status() { return lifecycleProblem.isEmpty() ? adapter.blocker() : lifecycleProblem; }
}
