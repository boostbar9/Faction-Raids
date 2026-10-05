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
    private boolean started;

    LocalEarthworksGoal(PerimeterEarthworksManifest manifest, NativeEarthworksAdapter.Store store, WorkersEarthworksPort port) {
        this.manifest = manifest; this.store = store; this.port = port;
        this.adapter = new NativeEarthworksAdapter(manifest, store, port);
        setFlags(port.nativeGoal().getFlags());
    }
    @Override public boolean canUse() {
        var saved = store.read();
        return store.recoveryAdmissionEstablished() && saved.journal().manifestHash().equals(manifest.hash())
                && saved.inFlight() == null && port.nativeEligible() && port.leaseProblem(manifest, saved.journal()) == null
                && (saved.journal().state() == com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksJournal.State.READY
                    || saved.journal().state() == com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksJournal.State.PENDING);
    }
    @Override public boolean canContinueToUse() { return started && canUse() && port.nativeGoal().canContinueToUse(); }
    @Override public boolean isInterruptable() { return port.nativeGoal().isInterruptable(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void start() { started = canUse(); if (started) port.nativeGoal().start(); }
    @Override public void stop() { if (started) port.nativeGoal().stop(); started = false; }
    @Override public void tick() {
        if (!started || !canContinueToUse()) return;
        var saved = store.read(); port.navigate(manifest, saved.journal());
        adapter.tick(); // The actual first-cell callback rechecks standing/escape, target, ledger and native eligibility.
    }
    String status() { return adapter.blocker(); }
}
