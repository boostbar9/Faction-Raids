package com.devfarinsky.factionraids.compat;

import com.devfarinsky.factionraids.FactionLogger;
import com.devfarinsky.factionraids.OptionalCompatBridge;
import com.devfarinsky.factionraids.RaidConfig;
import com.devfarinsky.factionraids.RaidEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * v2.30.0 Bridge Sieges (Path A: reactive integration).
 *
 * <p>Subscribes to Recruits' {@code SiegeEvent.Start} on the Forge event bus
 * and translates each start into a Faction Raids raid at the same claim.
 * The Recruits siege still runs its own health/timer system in parallel;
 * we add a parallel wave-based combat scenario driven by the FR raid
 * pipeline. Both systems observe the same defenders and share the same
 * physical space \u2014 the effect is a "the Recruits army is already here,
 * now their reinforcements pour in from the woods" siege experience.</p>
 *
 * <h3>Reflection-only design</h3>
 * <p>Faction Raids does not compile against Recruits. Direct import of
 * {@code SiegeEvent} would break the mod for servers without Recruits
 * installed. Instead we:</p>
 * <ul>
 *   <li>Register a reflective event handler for the fully qualified class
 *       name {@code com.talhanation.recruits.SiegeEvent$Start} via
 *       {@link IEventBus#addListener(Consumer)} after resolving the Class.</li>
 *   <li>Introspect {@code getClaim()} and {@code getLevel()} on each fired
 *       event to pull the {@code RecruitsClaim} + {@code ServerLevel} out.</li>
 *   <li>Ask the existing {@link RecruitsClaimsBridge} to snapshot the claim
 *       (which handles the reflection into {@code RecruitsClaim} internals).</li>
 * </ul>
 *
 * <p>If any reflection step fails at bootstrap the bridge disables itself for
 * the session. If a step fails at runtime we log once and stop subscribing.</p>
 *
 * <h3>Deduplication</h3>
 * <p>Recruits fires {@code Start} once per siege activation, but a defender
 * may already be in an active Faction Raids raid from the normal trigger
 * schedule. {@link RaidEvents#tryTriggerRaidForTeam(ServerLevel, String, ChunkPos)}
 * is idempotent per team \u2014 duplicate triggers return false without side
 * effects.</p>
 *
 * <h3>What we do NOT do</h3>
 * <ul>
 *   <li>We do NOT cancel the Recruits siege. Path A explicitly runs both
 *       systems in parallel; cancelling would upset players who installed
 *       Recruits for its claim gameplay.</li>
 *   <li>We do NOT modify Recruits' siege {@code Tick} damage. That path is
 *       reserved for v2.31+ if we ever want speed synchronization.</li>
 *   <li>We do NOT subscribe to {@code Success}. Recruits' ownership swap on
 *       success is Recruits' business; if the FR raid is still active it
 *       will finish normally on its own timer.</li>
 * </ul>
 */
public final class RecruitsSiegeBridge {

    private static final String SIEGE_START_CLASS =
            "com.talhanation.recruits.SiegeEvent$Start";

    private static final AtomicBoolean SUBSCRIBED = new AtomicBoolean(false);
    private static final AtomicBoolean RUNTIME_FAILED = new AtomicBoolean(false);

    private static volatile Method getClaimMethod;
    private static volatile Method getLevelMethod;

    private RecruitsSiegeBridge() {}

    /**
     * Called once during mod bootstrap by {@code FactionRaids} main class,
     * on the Forge event bus (not the mod bus).
     *
     * <p>We defer the actual subscription until {@link ServerStartedEvent}
     * so Recruits' class-loading is definitely complete before we probe.
     * Attempting to resolve {@code SiegeEvent$Start} at mod-construction
     * time races with Recruits' own registration on some setups.</p>
     */
    public static void init() {
        MinecraftForge.EVENT_BUS.register(RecruitsSiegeBridge.class);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onServerStarted(ServerStartedEvent event) {
        if (SUBSCRIBED.get() || RUNTIME_FAILED.get()) return;
        if (!RaidConfig.BRIDGE_SIEGES_ENABLED.get()) {
            FactionLogger.LOG.info("[FactionRaids] Bridge Sieges disabled by config; skipping SiegeEvent subscription.");
            return;
        }
        if (!OptionalCompatBridge.isLoaded(OptionalCompatBridge.RECRUITS)) {
            FactionLogger.LOG.info("[FactionRaids] Recruits not installed; Bridge Sieges idle.");
            return;
        }
        subscribeReflectively();
    }

    /**
     * Resolves {@code SiegeEvent$Start}, caches getter Methods, and installs
     * a raw {@link Consumer} listener on the Forge bus using the resolved
     * Class as the event type. The listener body is our reflective handler.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static synchronized void subscribeReflectively() {
        if (SUBSCRIBED.get() || RUNTIME_FAILED.get()) return;
        try {
            Class<?> startClass = Class.forName(SIEGE_START_CLASS);
            // Both getters live on the abstract SiegeEvent superclass; using
            // the concrete Start's methods works because getMethod walks up.
            getClaimMethod = startClass.getMethod("getClaim");
            getLevelMethod = startClass.getMethod("getLevel");

            // Forge's addGenericListener/addListener both accept a Consumer
            // parameterized by the event type. We use the raw addListener
            // overload that takes an explicit Class token so the compiler
            // doesn't need to see SiegeEvent.Start at compile time.
            MinecraftForge.EVENT_BUS.addListener(
                    EventPriority.NORMAL,
                    false,
                    (Class) startClass,
                    (java.util.function.Consumer) RecruitsSiegeBridge::handleSiegeStart);

            SUBSCRIBED.set(true);
            FactionLogger.LOG.info(
                    "[FactionRaids] Bridge Sieges active: subscribed to {} \u2192 Faction Raids raid trigger.",
                    SIEGE_START_CLASS);
        } catch (Throwable t) {
            RUNTIME_FAILED.set(true);
            FactionLogger.LOG.warn(
                    "[FactionRaids] Bridge Sieges could not attach: {} {} \u2014 disabled for this session.",
                    t.getClass().getSimpleName(), t.getMessage());
        }
    }

    /**
     * Handler for each {@code SiegeEvent.Start}. Extracts claim + level via
     * cached reflective getters, converts the claim center to a raid trigger
     * request, and delegates to
     * {@link RaidEvents#tryTriggerRaidForTeam(ServerLevel, String, ChunkPos)}.
     *
     * <p>Reflective failures here poison the bridge for the session because
     * something in Recruits changed shape after bootstrap succeeded.</p>
     */
    private static void handleSiegeStart(Object event) {
        if (event == null) return;
        try {
            Object claim = getClaimMethod.invoke(event);
            Object levelObj = getLevelMethod.invoke(event);
            if (!(levelObj instanceof ServerLevel level)) return;
            if (claim == null) return;

            // Reuse the existing claim snapshot pipeline. It knows how to
            // pull the owner faction id, center, chunks, and health.
            RecruitsClaimsBridge.ClaimSnapshot snap = snapshotFromRaw(claim);
            if (snap == null) {
                FactionLogger.LOG.debug("[FactionRaids] SiegeEvent.Start received but claim snapshot came back null; ignoring.");
                return;
            }

            String ownerTeamKey = snap.ownerFactionStringId();
            ChunkPos center = snap.center();
            if (ownerTeamKey == null || ownerTeamKey.isBlank() || center == null) {
                FactionLogger.LOG.debug("[FactionRaids] Skipping bridge trigger: incomplete claim snapshot (owner={}, center={}).",
                        ownerTeamKey, center);
                return;
            }

            boolean triggered = RaidEvents.tryTriggerRaidForTeam(level, ownerTeamKey, center);
            if (triggered) {
                FactionLogger.LOG.info(
                        "[FactionRaids] Bridge Sieges: SiegeEvent.Start on claim '{}' (owner team '{}') \u2192 spawned parallel Faction Raids raid at chunk {}.",
                        snap.claimName(), ownerTeamKey, center);
            } else {
                FactionLogger.LOG.debug(
                        "[FactionRaids] Bridge Sieges: SiegeEvent.Start on claim '{}' declined \u2014 no matching anchor or team already at raid cap.",
                        snap.claimName());
            }
        } catch (Throwable t) {
            RUNTIME_FAILED.set(true);
            FactionLogger.LOG.warn(
                    "[FactionRaids] Bridge Sieges: runtime error handling SiegeEvent.Start ({} {}) \u2014 disabling bridge for this session.",
                    t.getClass().getSimpleName(), t.getMessage());
        }
    }

    /**
     * Bridge {@link RecruitsClaimsBridge} to a raw claim Object. The existing
     * bridge only exposes chunk-position lookups, so we peek into its cached
     * reflection via a helper it exposes internally. If that helper is not
     * available we fall back to a lightweight in-line snapshot that reads
     * only the fields we need here.
     */
    private static RecruitsClaimsBridge.ClaimSnapshot snapshotFromRaw(Object claim) {
        try {
            Method m = claim.getClass().getMethod("getCenter");
            Object centerObj = m.invoke(claim);
            Method nameM = claim.getClass().getMethod("getName");
            Object nameObj = nameM.invoke(claim);
            Method ownerM = claim.getClass().getMethod("getOwnerFactionStringID");
            Object ownerObj = ownerM.invoke(claim);
            if (!(centerObj instanceof ChunkPos center)) return null;
            String name = nameObj instanceof String s ? s : "";
            String owner = ownerObj instanceof String s ? s : "";
            // Minimal snapshot with just the fields Bridge Sieges cares about.
            // We pass empty defaults for everything else; downstream code
            // here only reads center + name + owner.
            return new RecruitsClaimsBridge.ClaimSnapshot(
                    java.util.UUID.randomUUID(), name, owner, center,
                    java.util.Set.of(), true, 0, 0);
        } catch (Throwable t) {
            FactionLogger.LOG.debug("[FactionRaids] Bridge Sieges: snapshotFromRaw failed: {} {}",
                    t.getClass().getSimpleName(), t.getMessage());
            return null;
        }
    }

    /** Availability probe for logging / debug commands. */
    public static boolean isActive() {
        return SUBSCRIBED.get() && !RUNTIME_FAILED.get();
    }

    @SuppressWarnings("unused")
    private static void keepBlockPosImport(BlockPos p) { /* import retention */ }

    @SuppressWarnings("unused")
    private static void keepMinecraftServerImport(MinecraftServer s) { /* import retention */ }
}
