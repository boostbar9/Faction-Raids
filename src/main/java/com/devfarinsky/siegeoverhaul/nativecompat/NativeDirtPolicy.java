package com.devfarinsky.siegeoverhaul.nativecompat;

import java.util.List;
import java.util.Objects;

/**
 * Read-only, closed-baseline dirt admission. Not installed in EarthworksExecution.
 * A census is evidence, never its own approval. There is deliberately no shipped reviewed profile.
 * This does not establish ownership, natural provenance, worker/tool/claim/escape safety or a receipt.
 */
public final class NativeDirtPolicy {
    public static final String VERSION = "minecraft-1.20.1-forge-47.4.16-closed-dirt-v1";
    public static final String VANILLA_DIRT_SHA256 = "9222e5df0ffbb258af7ad3c42a563b432d46bdaf398bf505eccd1df89db24d25";
    public static final String FORGE_EMPTY_GLM_SHA256 = "ed72002040acf4aa51ce8d92dc9591bbf423f9be9860022e36060eaabb0ca4f3";
    public static final int MAX_RESOURCE_BYTES = 8_192, MAX_RESOURCE_LAYERS = 16;
    public static final int MAX_LISTENERS = 256, MAX_OWNERS = 512, MAX_OWNER_LISTENERS = 128;
    public static final int MAX_SECTIONS = 27, MAX_CHUNKS = 9;

    public enum Reason {
        READY, WRONG_THREAD, WRONG_DIMENSION, OUTSIDE_WORLD, UNLOADED,
        NOT_EXACT_DIRT, DROPS_DISABLED, SNAPSHOT_ACTIVE, RESOURCE_UNAVAILABLE,
        RESOURCE_CHANGED, LOOT_GRAPH_CHANGED, MODIFIERS_ACTIVE, API_UNAVAILABLE,
        LISTENER_UNRESOLVED, LISTENER_CHANGED, GAME_EVENT_LISTENER, GAME_EVENT_BUSY,
        READ_LIMIT, RELOADING, STALE_GENERATION, STALE_OBSERVATION, IDENTITY_CHANGED, PROFILE_UNREVIEWED,
        RUNTIME_UNPROVEN, PROFILE_CHANGED
    }
    public record Check(Reason reason, String detail) {
        public Check { Objects.requireNonNull(reason); Objects.requireNonNull(detail); }
        public boolean ready() { return reason == Reason.READY; }
        public String problem() { return ready() ? null : "Paused: " + detail; }
    }
    public record ResourceProof(String pack, boolean builtin, String sha256, int bytes) {
        public ResourceProof { Objects.requireNonNull(pack); Objects.requireNonNull(sha256); }
    }
    /** Class-resource bytes and code source are provenance, NOT proof of post-transform executable bytes. */
    public record CodeOrigin(String type, String module, String source, String classResourceSha256) {
        public CodeOrigin {
            Objects.requireNonNull(type); Objects.requireNonNull(module);
            Objects.requireNonNull(source); Objects.requireNonNull(classResourceSha256);
        }
    }
    public record LogicalOrigin(String type, String module, String classResourceSha256) {}
    static LogicalOrigin logical(CodeOrigin origin, NativeDirtRuntime.FirstParty firstParty) {
        boolean trusted = firstParty != null && origin.module().equals(firstParty.module()) && firstParty.trustedClasses().contains(origin.type());
        return new LogicalOrigin(origin.type(), origin.module(), trusted ? "trusted-first-party" : origin.classResourceSha256());
    }
    public record ListenerProof(String event, String callback, String priority, boolean receiveCanceled,
                                String genericFilter, CodeOrigin owner, CodeOrigin declaringClass,
                                String wrapper) {
        public ListenerProof {
            Objects.requireNonNull(event); Objects.requireNonNull(callback); Objects.requireNonNull(priority);
            Objects.requireNonNull(genericFilter); Objects.requireNonNull(owner);
            Objects.requireNonNull(declaringClass); Objects.requireNonNull(wrapper);
        }
    }
    public record Profile(String version, ResourceProof dirt, List<ResourceProof> modifierLayers,
                          List<ListenerProof> listeners, List<CodeOrigin> implementation, NativeDirtRuntime.Catalog runtime) {
        public Profile {
            Objects.requireNonNull(version); Objects.requireNonNull(dirt); Objects.requireNonNull(runtime);
            modifierLayers = List.copyOf(modifierLayers); listeners = List.copyOf(listeners);
            implementation = List.copyOf(implementation);
            if (modifierLayers.size() > MAX_RESOURCE_LAYERS || listeners.size() > MAX_LISTENERS * 6
                    || implementation.size() > 128) throw new IllegalArgumentException("Unbounded reviewed profile");
        }
    }
    /** Exact dispatcher coordinates, including vertical keys outside terrain build height. */
    public enum RegistryKind { ABSENT, NOOP, NATIVE, UNKNOWN }
    public record SectionProof(int chunkX, int sectionY, int chunkZ, RegistryKind kind, Check check) {
        public SectionProof { Objects.requireNonNull(kind); Objects.requireNonNull(check); }
    }
    /** Immutable exported evidence; never serialize the live session identity as approval. */
    public record Census(long generation, long gameTime, long target, ResourceProof dirt,
                         List<ResourceProof> modifierLayers, List<ListenerProof> listeners,
                         List<CodeOrigin> implementation, NativeDirtRuntime.Catalog runtime, int chunks, int sections,
                         List<SectionProof> registries, Check lootGraph, Integer activeModifiers, Check observation) {
        public Census {
            modifierLayers = List.copyOf(modifierLayers); listeners = List.copyOf(listeners);
            implementation = List.copyOf(implementation); registries = List.copyOf(registries);
            if (registries.size() > MAX_SECTIONS) throw new IllegalArgumentException("Unbounded section evidence");
            Objects.requireNonNull(observation);
        }
    }

    /**
     * Per-live-world epoch. The owner must invalidate at reload BEGIN, and complete only after the
     * corresponding successful future/ServerStarted on the server thread. Invalidation may arrive
     * from a reload thread and is synchronized; completion/inspection remain server-thread only. Player datapack sync is
     * not a completion signal. No event registration or reload completion is inferred by this helper.
     */
    public static final class Epoch {
        private final Object world;
        private final Thread thread;
        private long generation;
        private boolean complete;
        private Object resources, table, modifiers;
        public Epoch(Object world) { this.world = Objects.requireNonNull(world); thread = Thread.currentThread(); }
        public synchronized long beginReload() { complete = false; resources = table = modifiers = null; return ++generation; }
        public synchronized boolean completeReload(long expectedGeneration, Object resources, Object table, Object modifiers) {
            requireThread();
            if (expectedGeneration != generation || complete || resources == null || table == null || modifiers == null) return false;
            this.resources = resources; this.table = table; this.modifiers = modifiers; complete = true; return true;
        }
        public synchronized long generation() { return generation; }
        synchronized Check current(Object world, Object resources, Object table, Object modifiers) {
            if (Thread.currentThread() != thread) return denied(Reason.WRONG_THREAD, "dirt census requires the owning server thread");
            if (!complete) return denied(Reason.RELOADING, "resource generation has no verified successful completion");
            if (this.world != world || this.resources != resources || this.table != table || this.modifiers != modifiers)
                return denied(Reason.IDENTITY_CHANGED, "live world/resource/table/modifier identity changed");
            return readyCheck();
        }
        private void requireThread() {
            if (Thread.currentThread() != thread) throw new IllegalStateException("Resource epoch may only change on its owner thread");
        }
    }

    /** Opaque exact live identity. Deliberately has no disk codec and is not recreated from Census. */
    public static final class Identity {
        final Epoch epoch;
        final long generation, gameTime, target;
        final Object world, resources, table, modifiers;
        final List<Object> listeners;
        final List<CodeOrigin> localOrigins;
        final NativeDirtRuntime.Catalog localRuntime;
        final Object localAudit;
        Identity(Epoch epoch, Object world, Object resources, Object table, Object modifiers, List<Object> listeners, long gameTime, long target, List<CodeOrigin> localOrigins, NativeDirtRuntime.Catalog localRuntime, Object localAudit) {
            this.localAudit = localAudit;
            this.gameTime = gameTime; this.target = target; this.localRuntime = localRuntime;
            this.epoch = epoch; generation = epoch.generation(); this.world = world; this.resources = resources;
            this.table = table; this.modifiers = modifiers; this.listeners = List.copyOf(listeners); this.localOrigins = List.copyOf(localOrigins);
        }
        Check current() {
            if (generation != epoch.generation()) return denied(Reason.STALE_GENERATION, "dirt policy belongs to an earlier reload generation");
            Check current = epoch.current(world, resources, table, modifiers);
            if (!current.ready()) return current;
            if (world instanceof net.minecraft.server.level.ServerLevel level && level.getGameTime() != gameTime)
                return denied(Reason.STALE_OBSERVATION, "dirt world census belongs to an earlier game tick");
            return current;
        }
        boolean same(Identity next) {
            if (next == null || epoch != next.epoch || generation != next.generation || world != next.world
                    || resources != next.resources || table != next.table || modifiers != next.modifiers
                    || !Objects.equals(localAudit, next.localAudit) || !Objects.equals(localRuntime, next.localRuntime) || !localOrigins.equals(next.localOrigins) || listeners.size() != next.listeners.size()) return false;
            for (int i = 0; i < listeners.size(); i++) if (listeners.get(i) != next.listeners.get(i)) return false;
            return true;
        }
    }
    public record Observation(Census census, Identity identity) { public Observation { Objects.requireNonNull(census); } }
    public record Decision(Check check, Identity identity) { public String problem() { return check.problem(); } }

    /**
     * The reviewed profile pins concrete mod/module content and actual launch/transformation metadata.
     * It is installed only after independent census/native review; observations never approve themselves.
     * Passing this method alone never activates a CUT or supersedes the controller's other gates.
     */
    static Decision evaluate(Observation observed, Profile reviewed, Identity previous) {
        Census c = observed.census();
        if (!c.observation().ready()) return new Decision(c.observation(), null);
        if (c.chunks() != MAX_CHUNKS || c.sections() != MAX_SECTIONS || c.dirt() == null
                || !c.dirt().builtin() || c.dirt().bytes() != 376 || !VANILLA_DIRT_SHA256.equals(c.dirt().sha256()))
            return refuse(Reason.API_UNAVAILABLE, "exact dirt resource or complete 27-section evidence is unavailable");
        if (observed.identity() == null) return refuse(Reason.API_UNAVAILABLE, "live dirt policy identity is unavailable");
        Check epoch = observed.identity().current();
        if (!epoch.ready()) return new Decision(epoch, null);
        if (c.gameTime() != observed.identity().gameTime || c.target() != observed.identity().target)
            return refuse(Reason.STALE_OBSERVATION, "dirt census target/time differs from its live identity");
        if (c.generation() != observed.identity().generation) return refuse(Reason.STALE_GENERATION, "census and live policy generations differ");
        if (reviewed == null) return refuse(Reason.PROFILE_UNREVIEWED, "dirt runtime census has no independently reviewed native baseline");
        if (c.runtime() == null || !reviewed.runtime().reviewedShape().equals(c.runtime().reviewedShape()))
            return refuse(Reason.RUNTIME_UNPROVEN, "loaded artifacts, modules or relevant transformations differ from the reviewed runtime");
        if (!VERSION.equals(reviewed.version()) || !reviewed.dirt().equals(c.dirt())
                || !reviewed.modifierLayers().equals(c.modifierLayers()) || !logicalOrigins(reviewed.implementation(), reviewed.runtime().firstParty()).equals(logicalOrigins(c.implementation(), c.runtime().firstParty())))
            return refuse(Reason.PROFILE_CHANGED, "dirt resource or loaded-code provenance differs from the reviewed runtime");
        if (!sameListeners(reviewed.listeners(), c.listeners(), c.runtime().firstParty())) return refuse(Reason.LISTENER_CHANGED, "inherited drop/listener census changed");
        if (previous != null && !previous.same(observed.identity()))
            return refuse(Reason.IDENTITY_CHANGED, "dirt policy identity changed since the prior native pulse");
        return new Decision(readyCheck(), observed.identity());
    }
    private static List<LogicalOrigin> logicalOrigins(List<CodeOrigin> origins, NativeDirtRuntime.FirstParty firstParty) { return origins.stream().map(o -> logical(o, firstParty)).toList(); }
    private static boolean sameListeners(List<ListenerProof> a, List<ListenerProof> b, NativeDirtRuntime.FirstParty firstParty) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            ListenerProof x = a.get(i), y = b.get(i);
            if (!x.event().equals(y.event()) || !x.callback().equals(y.callback()) || !x.priority().equals(y.priority())
                    || x.receiveCanceled() != y.receiveCanceled() || !x.genericFilter().equals(y.genericFilter())
                    || !x.wrapper().equals(y.wrapper()) || !logical(x.owner(), firstParty).equals(logical(y.owner(), firstParty))
                    || !logical(x.declaringClass(), firstParty).equals(logical(y.declaringClass(), firstParty))) return false;
        }
        return true;
    }
    static Check readyCheck() { return new Check(Reason.READY, "Read-only dirt baseline matched; all other native gates remain required"); }
    static Check denied(Reason reason, String detail) { return new Check(reason, detail); }
    private static Decision refuse(Reason reason, String detail) { return new Decision(denied(reason, detail), null); }
    private NativeDirtPolicy() {}
}
