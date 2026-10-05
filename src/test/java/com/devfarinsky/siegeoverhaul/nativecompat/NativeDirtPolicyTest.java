package com.devfarinsky.siegeoverhaul.nativecompat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.devfarinsky.siegeoverhaul.nativecompat.NativeDirtPolicy.*;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic admission/epoch contracts. These do not constitute actual mod census or native CUT acceptance. */
class NativeDirtPolicyTest {
    @Test void explicitlyReviewedSyntheticBaselineCanMatchWithoutDispatchingAnyGameplay() {
        Fixture f = new Fixture();
        Decision result = evaluate(f.observed(), f.profile(), null);
        assertTrue(result.check().ready()); assertNull(result.problem()); assertSame(f.identity(), result.identity());
    }
    @Test void censusCannotApproveItselfOrAcceptUnknownActualRuntimeCatalog() {
        Fixture f = new Fixture();
        assertEquals(Reason.PROFILE_UNREVIEWED, evaluate(f.observed(), null, null).check().reason());
        var profile = f.profile(); f.runtime = null;
        assertEquals(Reason.RUNTIME_UNPROVEN, evaluate(f.observed(), profile, null).check().reason());
        f.runtime = catalog("unknown-mod");
        assertEquals(Reason.RUNTIME_UNPROVEN, evaluate(f.observed(), profile, null).check().reason());
    }
    @ParameterizedTest @EnumSource(value = Reason.class, names = "READY", mode = EnumSource.Mode.EXCLUDE)
    void everyObservedFailureRemainsFailClosed(Reason reason) {
        Fixture f = new Fixture(); f.check = denied(reason, "fixture refusal");
        Decision decision = evaluate(f.observed(), f.profile(), null);
        assertEquals(reason, decision.check().reason()); assertNull(decision.identity()); assertNotNull(decision.problem());
    }
    @Test void resourceListenerAndCodeChangesInvalidateReviewedBaseline() {
        Fixture f = new Fixture(); var profile = f.profile();
        f.dirt = new ResourceProof("changed-pack", true, VANILLA_DIRT_SHA256, 376);
        assertEquals(Reason.PROFILE_CHANGED, evaluate(f.observed(), profile, null).check().reason());
        f = new Fixture(); profile = f.profile(); f.layers.add(new ResourceProof("world", false, "d".repeat(64), 32));
        assertEquals(Reason.PROFILE_CHANGED, evaluate(f.observed(), profile, null).check().reason());
        f = new Fixture(); profile = f.profile(); f.listeners.add(listener("EntityEvent#unreviewed"));
        assertEquals(Reason.LISTENER_CHANGED, evaluate(f.observed(), profile, null).check().reason());
        f = new Fixture(); profile = f.profile(); f.code.add(new CodeOrigin("changed", "runtime", "fixture", "e".repeat(64)));
        assertEquals(Reason.PROFILE_CHANGED, evaluate(f.observed(), profile, null).check().reason());
    }
    @Test void profileVersionAndSameDescriptorsWithReplacedListenerIdentityRefuse() {
        Fixture f = new Fixture(); var p = f.profile();
        Profile wrong = new Profile("unknown", p.dirt(), p.modifierLayers(), p.listeners(), p.implementation(), p.runtime());
        assertEquals(Reason.PROFILE_CHANGED, evaluate(f.observed(), wrong, null).check().reason());
        Identity original = f.identity();
        f.listenerObjects = List.of(new Object()); f.identity = null;
        assertEquals(Reason.IDENTITY_CHANGED, evaluate(f.observed(), p, original).check().reason());
    }
    @Test void reloadBeginImmediatelyInvalidatesPreviouslyIssuedIdentityAndApproval() {
        Fixture f = new Fixture(); var before = f.observed();
        long second = f.epoch.beginReload();
        assertEquals(Reason.STALE_GENERATION, evaluate(before, f.profile(), null).check().reason());
        assertFalse(f.epoch.completeReload(second - 1, f.resources, f.table, f.modifiers));
        assertEquals(Reason.RELOADING, f.epoch.current(f.world, f.resources, f.table, f.modifiers).reason());
        assertTrue(f.epoch.completeReload(second, f.resources, f.table, f.modifiers));
        assertEquals(Reason.STALE_GENERATION, evaluate(before, f.profile(), null).check().reason());
    }
    @Test void replacedResourcesTableManagerOrWorldAndUnsynchronizedReloadRefuse() {
        Fixture f = new Fixture();
        assertEquals(Reason.IDENTITY_CHANGED, f.epoch.current(new Object(), f.resources, f.table, f.modifiers).reason());
        assertEquals(Reason.IDENTITY_CHANGED, f.epoch.current(f.world, new Object(), f.table, f.modifiers).reason());
        assertEquals(Reason.IDENTITY_CHANGED, f.epoch.current(f.world, f.resources, new Object(), f.modifiers).reason());
        assertEquals(Reason.IDENTITY_CHANGED, f.epoch.current(f.world, f.resources, f.table, new Object()).reason());
        Epoch reopened = new Epoch(f.world);
        assertEquals(Reason.RELOADING, reopened.current(f.world, f.resources, f.table, f.modifiers).reason());
        assertFalse(reopened.completeReload(0, null, f.table, f.modifiers));
    }
    @Test void anotherThreadCannotReadOrCompleteButMayInvalidateTheEpoch() throws Exception {
        Fixture f = new Fixture(); var observed = f.observed();
        AtomicReference<Reason> reason = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
            reason.set(evaluate(observed, f.profile(), null).check().reason());
            assertThrows(IllegalStateException.class, () -> f.epoch.completeReload(f.epoch.generation(), f.resources, f.table, f.modifiers));
            f.epoch.beginReload();
            } catch (Throwable error) { failure.set(error); }
        });
        thread.start(); thread.join(); assertNull(failure.get()); assertEquals(Reason.WRONG_THREAD, reason.get());
        assertEquals(Reason.STALE_GENERATION, evaluate(observed, f.profile(), null).check().reason());
    }
    @Test void identicalInstallationAtAnotherPathUsesPortableProfileButNotPriorLocalIdentity() {
        Fixture first = new Fixture();
        var a = new CodeOrigin("same.Type", "same.module", "file:/first/mod.jar", "a".repeat(64));
        var b = new CodeOrigin("same.Type", "same.module", "file:/other/mod.jar", "a".repeat(64));
        first.code.add(a); Profile reviewed = first.profile(); Identity local = first.identity();
        first.code.clear(); first.code.add(b); first.identity = null;
        assertTrue(evaluate(first.observed(), reviewed, null).check().ready());
        assertEquals(Reason.IDENTITY_CHANGED, evaluate(first.observed(), reviewed, local).check().reason());
        first.code.clear(); first.code.add(new CodeOrigin("same.Type", "other.module", "file:/other/mod.jar", "a".repeat(64))); first.identity = null;
        assertEquals(Reason.PROFILE_CHANGED, evaluate(first.observed(), reviewed, null).check().reason());
    }
    @Test void exportedProfilesAndCensusCollectionsAreImmutable() {
        Fixture f = new Fixture(); var profile = f.profile(); var census = f.observed().census();
        f.listeners.add(listener("later")); f.layers.add(new ResourceProof("later", false, "d".repeat(64), 1));
        assertTrue(profile.listeners().isEmpty()); assertTrue(census.listeners().isEmpty());
        assertTrue(profile.modifierLayers().isEmpty()); assertTrue(census.modifierLayers().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> census.listeners().clear());
    }
    private static NativeDirtRuntime.Catalog catalog(String mod) {
        return new NativeDirtRuntime.Catalog("fixture", "fixture", "fixture", List.of(new NativeDirtRuntime.Artifact(mod, "1", "fixture", "a".repeat(64), 1)),
                List.of(), List.of(), List.of(), List.of(), null);
    }
    private static ListenerProof listener(String callback) {
        var origin = new CodeOrigin("fixture", "fixture", "fixture", "d".repeat(64));
        return new ListenerProof("fixture", callback, "NORMAL", false, "*", origin, origin, "fixture");
    }
    private static final class Fixture {
        final Object world = new Object(), resources = new Object(), table = new Object(), modifiers = new Object();
        final Epoch epoch = new Epoch(world);
        NativeDirtRuntime.Catalog runtime = catalog("fixture");
        ResourceProof dirt = new ResourceProof("vanilla", true, VANILLA_DIRT_SHA256, 376);
        final List<ResourceProof> layers = new ArrayList<>();
        final List<ListenerProof> listeners = new ArrayList<>();
        final List<CodeOrigin> code = new ArrayList<>();
        List<Object> listenerObjects = List.of(new Object());
        Check check = readyCheck(); Identity identity;
        Fixture() { assertTrue(epoch.completeReload(epoch.beginReload(), resources, table, modifiers)); }
        Identity identity() { if (identity == null) identity = new Identity(epoch, world, resources, table, modifiers, listenerObjects, 50, 0, code, runtime, null); return identity; }
        Observation observed() { return new Observation(new Census(epoch.generation(), 50, 0, dirt, layers, listeners, code, runtime, 9, 27, List.of(), readyCheck(), 0, check), identity()); }
        Profile profile() { return new Profile(VERSION, dirt, layers, listeners, code, runtime); }
    }
}
