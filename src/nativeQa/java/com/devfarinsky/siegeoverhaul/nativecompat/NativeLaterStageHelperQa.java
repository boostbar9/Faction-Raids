package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.RaidConfig;
import com.devfarinsky.siegeoverhaul.RaidNetwork;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import com.devfarinsky.siegeoverhaul.client.CoreHireScreen;
import com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.core.*;
import com.google.gson.*;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.EntityDataSerializer;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import com.mojang.blaze3d.platform.NativeImage;
import com.talhanation.recruits.config.RecruitsServerConfig;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.entities.ai.BuilderWorkGoal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/** Separate, bounded synthetic transit stimulus. Every build placement remains native and paid. */
@Mod.EventBusSubscriber(modid = SiegeOverhaul.MOD_ID, value = Dist.CLIENT)
public final class NativeLaterStageHelperQa {
    private static final String CASE = "later-stage-disk";
    private static final boolean ENABLED = Boolean.getBoolean("siegeoverhaul.nativeQa")
            && Boolean.getBoolean("siegeoverhaul.nativeQa.activeHelper")
            && Boolean.getBoolean("siegeoverhaul.nativeQa.sharedCrew")
            && "staged-handoff".equals(System.getProperty("siegeoverhaul.nativeQa.mode"))
            && CASE.equals(System.getProperty("siegeoverhaul.nativeQa.activeHelperCase"));
    private static final long SECOND = 1_000_000_000L, LIMIT_SECONDS = 600;
    private static final String CONFIG = "[Recruits]\nRecruitsChunkLoading = false\n";
    private static final String PIN = "64d4deeadebea0808cf38cbca69ebf72b68b3e2ed089958e3a41d1a37a15a115";
    private static final String RESOURCE = "/native-helper-partition/cap64-candidate-partition.json";
    private static final int CAP = 64;
    private static final Gson JSON = new GsonBuilder().serializeNulls().create();
    private enum Phase { SETUP, COMMISSION, BOTH_WORK, L_OUT, L_SETTLE, L_EVICT, L_OFFLINE_A0,
        L_OFFLINE_A1, L_WAIT_A2, L_LOAD, L_HOLDING, L_RETURN, L_RETURN_SETTLE, L_REJOIN,
        L_CANCEL_MENU, L_CANCEL_WAIT, L_FINAL_STABLE, FINISHED }
    private enum Action { NONE, OPEN_CORE, CANCEL, DONE }
    private static final Map<String, Object> REPORT = new LinkedHashMap<>();
    private static final List<Map<String, Object>> EVENTS = new ArrayList<>(), WORK = new ArrayList<>(), JOINS = new ArrayList<>(), SETUP_JOINS = new ArrayList<>();
    private static final List<Map<String, Object>> CALLS = new ArrayList<>(), ROUNDTRIPS = new ArrayList<>();
    private static final Map<UUID, BuilderEntity> WORKERS = new LinkedHashMap<>();
    private static final Map<UUID, BuilderWorkGoal> GOALS = new LinkedHashMap<>();
    private static final Map<UUID, Set<Long>> PLACEMENTS = new LinkedHashMap<>();
    private static final Set<UUID> SUPPLIED = new HashSet<>();
    private static final Map<UUID, Integer> GROUNDED = new HashMap<>();
    private static final Map<UUID, NativeActiveHelperTransit.Inventory> DISK_JOINS = new LinkedHashMap<>();
    private static final Map<BuilderEntity, NativeActiveHelperTransit.Inventory> RAW_LOADS = new IdentityHashMap<>();
    private static final Map<BuilderEntity, PendingMirror> PENDING_LOADS = new IdentityHashMap<>();
    private static final Map<Integer, String> VERIFIED = new LinkedHashMap<>();
    private static final Set<Integer> RETIRED = new LinkedHashSet<>();
    private record PendingMirror(NativeActiveHelperTransit.Inventory armed, boolean split, int rebinds) {}
    private static Phase phase = Phase.SETUP;
    private static volatile Action action = Action.NONE;
    private static volatile Throwable failure;
    private static volatile boolean finished, finishCleanupScheduled, finishCleanupReady, evidenceSealed;
    private static long started, phaseAt, sequence, lastEligibility = -1, unavailableAt = -1, finalizedOfflineAt = -1;
    private static long transferredAt = -1, settledAt = -1, canceledAuthorityAt = -1, terminalObservedBrackets;
    private static boolean settledEndPending, cancelSent, helperActiveObserved, helperPlacedA2;
    private static BuilderEntity transferredSource, transferredDestination;
    private static final List<String> SHOTS = new ArrayList<>();
    private static Path directory, evidence;
    private static UUID ownerId, coordinator, helper, projectId, ledgerGeneration;
    private static String coreKey;
    private static NativeStagedHandoffFixture.Fixture fixture;
    private static Set<net.minecraft.world.level.ChunkPos> observationChunks = Set.of();
    private static NativeActiveHelperTransit transit;
    private static PerimeterProject accepted;
    private static PerimeterStageLayout.Layout layout;
    private static NativeQaTreasury treasury;
    private static NativeActiveHelperTransit.Inventory offline, finalizedOffline;
    private static ProtectedBuilderHandLifecycle.Receipt originalHand;
    private static CompoundTag canceledReceipt;
    private static Map<Long, BlockState> canceledWorld;
    private static Map<String, List<Integer>> canceledStock;
    private static Object serializerInstance;
    private static Map<String, Object> serializerContract;
    private static JsonObject candidate;
    private static Observation observation;
    private record Observation(BuilderEntity worker, UUID area, int stage, String digest, long tick, Set<Long> pending,
            int cobble, int oak, int chestCobble, int chestOak, String state, boolean authorized,
            Entity receiptArea, Map<Long, BlockState> canceledTargets, String projectSnapshot,
            Map<String, Object> authority) {}
    private NativeLaterStageHelperQa() {}

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (started == 0) started = System.nanoTime();
            require(System.nanoTime() - started < LIMIT_SECONDS * SECOND, "Later-stage helper exceeded its hard ten-minute cap");
            if (failure != null || finishCleanupScheduled) { finish(mc); return; }
            if (directory == null) {
                if (!(mc.screen instanceof TitleScreen)) return;
                initialize(mc);
                GameRules rules = new GameRules();
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
                mc.options.renderDistance().set(6); mc.options.simulationDistance().set(6); mc.options.pauseOnLostFocus = false;
                mc.createWorldOpenFlows().createFreshLevel(NativeStagedHandoffFixture.WORLD,
                        new LevelSettings(NativeStagedHandoffFixture.WORLD, GameType.SURVIVAL, false, Difficulty.PEACEFUL,
                                false, rules, WorldDataConfiguration.DEFAULT), new WorldOptions(20261007L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions());
                return;
            }
            if (mc.player != null) ownerId = mc.player.getUUID();
            Action next = action;
            if (next == Action.NONE) return;
            if (next == Action.DONE) { action = Action.NONE; finish(mc); return; }
            if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
            switch (next) {
                case OPEN_CORE -> {
                    action = Action.NONE;
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(NativeStagedHandoffFixture.CORE), Direction.UP,
                                    NativeStagedHandoffFixture.CORE, false));
                }
                case CANCEL -> {
                    if (!(mc.screen instanceof CoreHireScreen) || !(mc.player.containerMenu instanceof CoreHireMenu menu)) return;
                    screenshot(mc, "03-authenticated-cancel.png"); action = Action.NONE;
                    RaidNetwork.cancelPerimeterProject(menu.containerId, projectId, accepted.header().generation()); cancelSent = true;
                }
                default -> { }
            }
        } catch (Throwable problem) { fail(problem); finish(mc); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (!ENABLED || !observesNativeLifecycle() || failure != null || event.phase != TickEvent.Phase.END || directory == null || ownerId == null) return;
        try {
            var server = event.getServer(); var level = server.overworld(); long tick = level.getGameTime();
            if (phaseAt == 0) phaseAt = tick;
            require(tick - phaseAt <= (phase == Phase.L_EVICT ? 1800 : 1200), "Bounded later-stage phase timed out: " + phase);
            if (observation != null) { require(footprintReady(level), "Open native bracket lost its resident footprint"); flush(level); }
            if (accepted != null) require(footprintReady(level), "Observation footprint became unavailable");
            if (settledEndPending) {
                require(tick > transferredAt, "Settled custody requires a later ordinary END");
                transit.assertSettled(transferredSource, transferredDestination); settledEndPending = false; settledAt = tick;
                record("ordinary-end-settled-custody", Map.of("worker", helper.toString(), "dimension", transferredDestination.level().dimension().location().toString(),
                        "gameTime", tick, "transferGameTime", transferredAt, "nanoTime", System.nanoTime()));
            }
            ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
            if (owner == null) { require(phase == Phase.SETUP, "Owner disappeared outside initial setup"); return; }
            require(owner.isAlive() && !owner.isCreative() && !owner.isSpectator() && !owner.hasPermissions(2)
                    && owner.mayBuild() && owner.serverLevel() == level, "Lost real non-op Survival authority");
            if (accepted != null) {
                treasury.observe(core(server), bankRate(core(server)), tick);
                observeCanceledAuthority(server, level); verifyImmutable(level); conservation(server, owner); verifyClaims(level);
                if (canceledAuthorityAt >= 0) assertCanceledFreeze(server, level);
                else observeStages(server, level);
            }
            updateGrounded(); step(server, level, owner, tick);
        } catch (Throwable problem) {
            fail(problem);
            try { if (accepted != null && !evidenceSealed && footprintReady(event.getServer().overworld())) boundary(event.getServer(), "failure-passive-state"); }
            catch (Throwable diagnostic) { REPORT.put("failureSnapshotProblem", diagnostic.toString()); }
            try { if (transit != null) transit.cleanup(event.getServer()); }
            catch (Throwable cleanup) { REPORT.put("ticketCleanupFailure", cleanup.toString()); }
        }
    }

    private static void step(MinecraftServer server, ServerLevel level, ServerPlayer owner, long tick) throws Exception {
        PerimeterProject project = accepted == null ? null : project(level);
        switch (phase) {
            case SETUP -> {
                require(!RecruitsServerConfig.RecruitsChunkLoading.get(), "Fresh worker chunk-loading config is not disabled");
                fixture = NativeStagedHandoffFixture.setup(level, owner); observationChunks = observationEnvelope(); coordinator = fixture.builderId();
                remember((BuilderEntity) level.getEntity(coordinator));
                var member = NativeStagedHandoffFixture.spawnSharedHelper(level, owner); helper = member.getUUID(); remember(member);
                require(SETUP_JOINS.size() == 2 && SETUP_JOINS.stream().map(v -> v.get("worker")).collect(java.util.stream.Collectors.toSet())
                        .equals(Set.of(coordinator.toString(), helper.toString())), "Synthetic initial admissions differ from original workers");
                REPORT.put("initialSetupAdmissions", List.copyOf(SETUP_JOINS));
                transit = new NativeActiveHelperTransit(helper, REPORT); transit.setup(level); coreKey = SiegeCore.key(owner);
                REPORT.put("workers", List.of(coordinator.toString(), helper.toString())); REPORT.put("owner", ownerId.toString());
                REPORT.put("auxiliaryIsolationInitial", NativeStagedHandoffFixture.auxiliaryIsolationSnapshot(level, fixture));
                require(FactionBank.balance(core(server)) == 0, "Fresh Treasury was not empty"); move(tick, Phase.COMMISSION);
            }
            case COMMISSION -> {
                if (!core(server).contains("BankInterestAt") || tick == phaseAt) return;
                commission(level, owner); boundary(server, "l-commission"); move(tick, Phase.BOTH_WORK);
            }
            case BOTH_WORK -> {
                requireFirstStage(project);
                if (!bothContributed() || SUPPLIED.size() != 2 || !eligible(WORKERS.get(helper), tick)) return;
                assertActive(level, project);
                originalHand = ProtectedBuilderHandLifecycle.read(WORKERS.get(helper).getPersistentData().getCompound(ProtectedBuilderHandLifecycle.KEY));
                require(originalHand != null && originalHand.area().equals(accepted.stages().get(0).areaId()), "Missing original A0 hand receipt");
                REPORT.put("originalHelperHandReceipt", originalHand.save().toString()); REPORT.put("originalHelperHandReceiptId", originalHand.receipt().toString());
                transit.ensureHoldingTicket(server); move(tick, Phase.L_OUT);
            }
            case L_OUT -> {
                requireFirstStage(project);
                if (!transit.holdingReady(server) || !eligible(WORKERS.get(helper), tick)) return;
                boundary(server, "l-before-outbound"); transfer(WORKERS.get(helper), transit.holdingLevel(server), transit.holdingFeet());
                move(tick, Phase.L_SETTLE);
            }
            case L_SETTLE -> {
                requireFirstStage(project); requireSettledNow(tick); boundary(server, "l-outbound-settled");
                transit.releaseHoldingTicket(server); transit.assertNoTickets(server);
                record("l-outbound-ticket-released", Map.of("gameTime", tick, "transferGameTime", transferredAt, "settledGameTime", settledAt, "nanoTime", System.nanoTime()));
                move(tick, Phase.L_EVICT);
            }
            case L_EVICT -> {
                requireFirstStage(project); transit.assertNoTickets(server);
                require(level.getEntity(helper) == null, "Outbound helper still has build-site custody");
                var holding = transit.holdingLevel(server); var member = WORKERS.get(helper);
                if (holding.hasChunkAt(transit.holdingFeet()) || holding.getEntity(helper) != null) return;
                for (var world : server.getAllLevels()) require(world.getEntity(helper) == null, "Helper remained registered outside unavailable holding chunk");
                if (unavailableAt < 0) unavailableAt = tick;
                require(tick - unavailableAt <= 200, "Later-stage native removal did not finalize");
                if (!member.isRemoved()) return;
                require(member.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK
                        && !member.getRemovalReason().shouldDestroy() && member.getRemovalReason().shouldSave(), "Missing genuine helper disk eviction");
                // This is the FIRST finalized-removal END. Never delay this proof for a controller sweep.
                project = project(level); CaseWindow.removal(project.activeStage(), project.receipts().size(), finalizedOfflineAt);
                requireFirstStage(project);
                finalizedOffline = NativeActiveHelperTransit.inventory(member); offline = finalizedOffline;
                require(finalizedOffline.beforeItemSlot() == -1 && !ProtectedBuilderHandMirror.pending(finalizedOffline.persistent())
                        && originalHand.equals(ProtectedBuilderHandLifecycle.read(finalizedOffline.persistent().getCompound(ProtectedBuilderHandLifecycle.KEY)))
                        && PerimeterProjectLink.matches(member, project, helper), "Finalized actor lost its exact original provenance");
                finalizedOfflineAt = tick;
                REPORT.put("finalizedOfflineInventory", inventoryEvidence(finalizedOffline)); REPORT.put("helperDiskEvictionVerified", true);
                REPORT.put("offlineRemovalProof", Map.of("reason", "UNLOADED_TO_CHUNK", "shouldDestroy", false, "shouldSave", true,
                        "chunkAvailable", false, "registered", false, "allDimensionsAbsent", true, "gameTime", tick, "unavailableAt", unavailableAt));
                REPORT.put("remainingA0AtFinalizedRemoval", stagePending(level, 0));
                boundary(server, "l-finalized-disk-removal"); move(tick, Phase.L_OFFLINE_A0);
            }
            case L_OFFLINE_A0 -> {
                assertHelperOffline(server);
                if (!RETIRED.contains(0)) return;
                assertSuspended(level, project); move(tick, Phase.L_OFFLINE_A1);
            }
            case L_OFFLINE_A1 -> {
                assertHelperOffline(server); assertSuspended(level, project);
                if (!RETIRED.contains(1)) return;
                move(tick, Phase.L_WAIT_A2);
            }
            case L_WAIT_A2 -> {
                assertHelperOffline(server); assertSuspended(level, project);
                if (project.state() != PerimeterProject.State.RUNNING || project.activeStage() != 2 || tick - finalizedOfflineAt < 100) return;
                requireA2(project); CaseWindow.load(project.activeStage(), project.receipts().size(), tick, finalizedOfflineAt);
                REPORT.put("genuineDiskAbsenceTicks", tick - finalizedOfflineAt);
                boundary(server, "l-a2-ready-helper-offline"); transit.ensureHoldingTicket(server); move(tick, Phase.L_LOAD);
            }
            case L_LOAD -> {
                requireA2(project);
                if (!transit.holdingReady(server) || !(transit.holdingLevel(server).getEntity(helper) instanceof BuilderEntity member)) return;
                require(DISK_JOINS.size() == 1 && DISK_JOINS.containsKey(helper) && sameInventory(finalizedOffline, DISK_JOINS.get(helper)),
                        "Later-stage disk admission changed exact finalized cargo or actor order");
                NativeActiveHelperTransit.assertUnique(server, helper, member); WORKERS.put(helper, member); offline = null;
                boundary(server, "l-holding-disk-join"); move(tick, Phase.L_HOLDING);
            }
            case L_HOLDING -> {
                requireA2(project); assertSuspended(level, project);
                var proof = laterProof(WORKERS.get(helper));
                if (!proof.valid() || proof.state() != NativeLaterStageHelperProof.State.DETACHED_SUSPENDED || !eligible(WORKERS.get(helper), tick)) return;
                boundary(server, "l-holding-cleanup-ready"); move(tick, Phase.L_RETURN);
            }
            case L_RETURN -> {
                requireA2(project);
                if (!eligible(WORKERS.get(helper), tick) || !transit.returnReady(level, WORKERS.get(helper))) return;
                require(laterProof(WORKERS.get(helper)).state() == NativeLaterStageHelperProof.State.DETACHED_SUSPENDED, "Holding actor did not detach only A0");
                boundary(server, "l-before-native-return"); transfer(WORKERS.get(helper), level, transit.returnFeet()); move(tick, Phase.L_RETURN_SETTLE);
            }
            case L_RETURN_SETTLE -> {
                requireA2(project); requireSettledNow(tick); boundary(server, "l-return-settled");
                transit.releaseHoldingTicket(server); transit.assertNoTickets(server);
                record("l-return-ticket-released", Map.of("gameTime", tick, "transferGameTime", transferredAt, "settledGameTime", settledAt, "nanoTime", System.nanoTime()));
                move(tick, Phase.L_REJOIN);
            }
            case L_REJOIN -> {
                requireA2(project); transit.assertNoTickets(server);
                var ledger = ConstructionEditLedger.get(level);
                if (!ledger.crewMember(project, helper) || !ledger.crewStageGranted(project, helper)) return;
                assertActive(level, project);
                if (!helperActiveObserved) { helperActiveObserved = true; boundary(server, "l-helper-a2-active"); }
                if (stagePlacements(helper, 2) == 0) return;
                helperPlacedA2 = true; boundary(server, "l-helper-a2-placement"); move(tick, Phase.L_CANCEL_MENU); action = Action.OPEN_CORE;
            }
            case L_CANCEL_MENU -> {
                requireA2(project);
                if (!(owner.containerMenu instanceof CoreHireMenu menu)) return;
                require(menu.stillValid(owner), "Later-stage core menu is not authenticated");
                move(tick, Phase.L_CANCEL_WAIT); action = Action.CANCEL;
            }
            case L_CANCEL_WAIT -> {
                if (!cancelSent || canceledAuthorityAt < 0) return;
                if (PerimeterProjectAuthority.snapshot(level, coreKey).terminal(projectId) == null) return;
                verifyCanceled(level); canceledReceipt = PerimeterProjectAuthority.snapshot(level, coreKey).terminal(projectId).save();
                boundary(server, "l-compacted-loaded"); move(tick, Phase.L_FINAL_STABLE);
            }
            case L_FINAL_STABLE -> {
                verifyCanceled(level); transit.assertNoTickets(server);
                if (tick - phaseAt < 100) return;
                require(helperActiveObserved && helperPlacedA2 && PENDING_LOADS.isEmpty() && RAW_LOADS.isEmpty()
                        && terminalObservedBrackets >= 200 && JOINS.size() == 3 && bothContributed(), "Incomplete later-stage proof");
                boundary(server, "l-final-stable"); REPORT.put("cancellationStableTicks", tick - canceledAuthorityAt);
                REPORT.put("terminalObservedBrackets", terminalObservedBrackets); REPORT.put("laterStageReturnVerified", true);
                REPORT.put("stageSkippedReturnVerified", true); REPORT.put("postDiskHelperPlacementVerified", true); REPORT.put("boundedScenarioVerified", true);
                REPORT.put("placementCounts", counts()); REPORT.put("retainedPlacedBlocks", 572 - pending(level).size());
                REPORT.put("paidFee", 64); REPORT.put("wholePlanCompleted", false); REPORT.put("finalMaterials", materialCensus(server, owner));
                REPORT.put("auxiliaryIsolationFinal", NativeStagedHandoffFixture.auxiliaryIsolationSnapshot(level, fixture));
                move(tick, Phase.FINISHED); sealCanceledEvidence(server, tick); action = Action.DONE;
            }
            case FINISHED -> { }
        }
    }

    /** Separate case: no server reopen, later stage, or post-return work is accepted here. */


    /** Case C's declared successful server-END cut; client shutdown can arrive on a later tick. */
    private static boolean observesNativeLifecycle() {
        return !finished && !evidenceSealed;
    }
    private static void sealCanceledEvidence(MinecraftServer server, long tick) {
        require(server.isSameThread() && phase == Phase.FINISHED && phaseAt == tick && !evidenceSealed && failure == null
                && observation == null && !settledEndPending && PENDING_LOADS.isEmpty() && RAW_LOADS.isEmpty()
                && helperPlacedA2 && canceledReceipt != null && Boolean.TRUE.equals(REPORT.get("boundedScenarioVerified")),
                "Cannot seal incomplete later-stage evidence");
        transit.assertNoTickets(server);
        require(EVENTS.stream().filter(value -> "terminal-native-bracket".equals(value.get("kind"))).count() == terminalObservedBrackets
                && Objects.equals(REPORT.get("terminalObservedBrackets"), terminalObservedBrackets), "Final observation count differs");
        REPORT.put("events", EVENTS.stream().map(value -> Collections.unmodifiableMap(new LinkedHashMap<>(value))).toList());
        REPORT.put("nativeWork", WORK.stream().map(value -> Collections.unmodifiableMap(new LinkedHashMap<>(value))).toList());
        REPORT.put("nativeWorkerJoins", JOINS.stream().map(Map::copyOf).toList());
        require(REPORT.get("activeHelperTransitEvents") instanceof List<?>, "Missing final native transit evidence");
        REPORT.put("activeHelperTransitEvents", List.copyOf((List<?>) REPORT.get("activeHelperTransitEvents")));
        REPORT.put("evidenceSeal", Map.of("sequence", ++sequence, "gameTime", tick, "serverEnd", true));
        // Ticket cleanup stays live during client finish and stop. Game observations stop exactly here.
        evidenceSealed = true;
    }

    private static void requireSettledNow(long tick) {
        require(!settledEndPending && settledAt > transferredAt && settledAt == tick, "Ticket release lacks later ordinary END custody");
    }
    private static void assertHelperOffline(MinecraftServer server) {
        transit.assertNoTickets(server);
        require(finalizedOffline != null && !transit.holdingLevel(server).hasChunkAt(transit.holdingFeet()), "Helper absence was not genuine disk eviction");
        for (var level : server.getAllLevels()) require(level.getEntity(helper) == null, "Helper joined before fixed A2 load boundary");
        var actor = WORKERS.get(helper);
        require(actor.isRemoved() && actor.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK
                && finalizedOffline.equals(NativeActiveHelperTransit.inventory(actor)), "Finalized offline actor/cargo changed");
    }


    private static boolean handProvenance(BuilderEntity worker, ConstructionEditLedger ledger) {
        if (worker.getUUID().equals(helper) && finalizedOffline != null && project(worker.getServer().overworld()) != null
                && project(worker.getServer().overworld()).activeStage() == 2) return laterProof(worker).valid();
        return ProtectedBuilderHandLifecycle.matches(worker.getPersistentData(), worker.getUUID(), ledger)
                && NativeConstructionGuard.validHandReceipt(worker.getPersistentData(), ledger);
    }

    /** Called before every later native participant bracket and at END, even after active-project compaction. */
    private static void observeCanceledAuthority(MinecraftServer server, ServerLevel level) throws ReflectiveOperationException {
        var snapshot = PerimeterProjectAuthority.snapshot(level, coreKey); var live = snapshot.get(projectId); var terminal = snapshot.terminal(projectId);
        if (live != null && live.state() != PerimeterProject.State.CANCELED) {
            require(canceledAuthorityAt < 0, "Canceled project recreated or resumed"); return;
        }
        require(live != null || terminal != null && terminal.state() == PerimeterProject.State.CANCELED, "Project vanished without canceled authority");
        if (canceledAuthorityAt >= 0) return;
        require(cancelSent && phase == Phase.L_CANCEL_WAIT && helperPlacedA2, "Cancellation did not follow authenticated A2 work request");
        if (live != null) require(live.activeStage() == 2 && live.receipts().size() == 2, "Canceled outside fixed unfinished A2");
        else require(terminal.activeStage() == 2 && terminal.verifiedStages() == 2, "Compacted outside fixed unfinished A2");
        canceledAuthorityAt = level.getGameTime(); canceledWorld = worldTargets(level); canceledStock = constructionStock(server);
        REPORT.put("canceledAuthorityGameTime", canceledAuthorityAt); boundary(server, "l-authoritative-canceled");
    }
    private static Map<Long, BlockState> worldTargets(ServerLevel level) {
        require(footprintReady(level), "Terminal world census would load chunks"); var result = new LinkedHashMap<Long, BlockState>();
        for (long cell : fixture.plan().blocks().keySet()) result.put(cell, level.getBlockState(BlockPos.of(cell))); return Map.copyOf(result);
    }
    private static Map<String, String> targetEvidence(Map<Long, BlockState> states) {
        var result = new TreeMap<String, String>(); states.forEach((cell, state) -> result.put(cell.toString(), state.toString())); return Map.copyOf(result);
    }
    private static Map<String, List<Integer>> constructionStock(MinecraftServer server) {
        var value = new LinkedHashMap<String, List<Integer>>();
        value.put("chest", List.of(chestStock(server.overworld(), Items.COBBLESTONE), chestStock(server.overworld(), Items.OAK_PLANKS)));
        for (UUID id : List.of(coordinator, helper)) {
            var worker = WORKERS.get(id); int cobble, oak;
            if (worker.isRemoved()) {
                require(id.equals(helper) && finalizedOffline != null && worker.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK, "Unproven offline terminal stock");
                cobble = savedMaterial(finalizedOffline, Items.COBBLESTONE); oak = savedMaterial(finalizedOffline, Items.OAK_PLANKS);
            } else { cobble = workerStock(worker, Items.COBBLESTONE); oak = workerStock(worker, Items.OAK_PLANKS); }
            value.put(id.toString(), List.of(cobble, oak));
        }
        return Map.copyOf(value);
    }
    private static int savedMaterial(NativeActiveHelperTransit.Inventory value, Item item) {
        return value.slots().stream().map(ItemStack::of).filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }
    private static void assertCanceledFreeze(MinecraftServer server, ServerLevel level) {
        require(canceledWorld != null && canceledStock != null, "Canceled authority was not frozen");
        CanceledObservation.verify(canceledWorld, worldTargets(level), canceledStock, constructionStock(server));
    }
    /** Dependency-free decision used by executed negative regressions; never reconciles or repairs. */
    static final class CanceledObservation {
        static boolean mayWork(boolean running, boolean participant, boolean owner) { return running && participant && owner; }
        static void verifyBracket(boolean authorized, int changes, boolean supply, Map<?, ?> beforeWorld, Map<?, ?> afterWorld,
                                  Map<?, ?> beforeStock, Map<?, ?> afterStock) {
            require(!authorized && changes == 0 && !supply, "Canceled participant retained work authority or mutated stock");
            verify(beforeWorld, afterWorld, beforeStock, afterStock);
        }
        static void verify(Map<?, ?> beforeWorld, Map<?, ?> afterWorld, Map<?, ?> beforeStock, Map<?, ?> afterStock) {
            require(beforeWorld.equals(afterWorld) && beforeStock.equals(afterStock), "Canceled work or individual material custody changed");
        }
    }


    private static void commission(ServerLevel level, ServerPlayer owner) throws Exception {
        var core = core(level.getServer()); long beforeClock = core.getLong("BankInterestAt");
        FactionBank.credit(core, 2000); RaidSavedData.get(owner.server).setDirty();
        var prepared = PerimeterConstruction.prepare(owner, NativeStagedHandoffFixture.CORE, 1);
        require(prepared.ready() && prepared.builder() == WORKERS.get(coordinator), "Real paid quote selected another builder or failed");
        var plan = fixture.plan(); var quote = prepared.quote();
        Map<Long, BlockState> before = new LinkedHashMap<>(), clearance = new LinkedHashMap<>();
        plan.blocks().keySet().forEach(cell -> before.put(cell, level.getBlockState(BlockPos.of(cell))));
        plan.clearance().forEach(cell -> clearance.put(cell, level.getBlockState(BlockPos.of(cell))));
        JsonObject expected = candidate.getAsJsonObject("candidate");
        layout = PerimeterStageLayout.partition(plan, part -> validateNative(part, expected));
        PerimeterStageLayout.validate(plan, layout);
        require(CALLS.size() == 46 && layout.stages().size() == 16, "Reviewed native cap-64 partition changed");
        var partition = new LinkedHashMap<String, Object>(); partition.put("cap", CAP); partition.put("layoutDigest", layout.digest());
        partition.put("stages", layout.stages().stream().map(NativeLaterStageHelperQa::stageEvidence).toList());
        partition.put("validatorCalls", List.copyOf(CALLS));
        require(JSON.toJsonTree(partition).equals(expected), "Native partition differs from complete immutable cap-64 candidate");
        requireLossless(plan, layout); SerializerShape.requireStable(serializerInstance, EntityDataSerializers.COMPOUND_TAG);
        serializerContract.put("instanceStable", true);
        require(plan.blocks().size() == 572 && plan.clearance().size() == 748 && plan.columns().size() == 220
                && plan.materialCounts().equals(Map.of("minecraft:cobblestone", 352, "minecraft:oak_planks", 220))
                && TerritoryFortification.PRICE == 64, "Immutable geometry, finite materials or native fee changed");
        REPORT.put("candidatePartition", candidate.deepCopy()); REPORT.put("candidateSha256", PIN);
        REPORT.put("nativePartitionRoundTrips", List.copyOf(ROUNDTRIPS)); REPORT.put("nativePartition", partition);
        String fingerprint = PerimeterReviewFingerprint.create(plan, layout, before, clearance, null, quote.core(), 1,
                prepared.claimIdentity(), owner.getUUID(), coordinator);
        require(PerimeterProjectStore.all(core).isEmpty() && pending(level).size() == NativeStagedHandoffFixture.BLOCKS,
                "Precommission world was already built or paid");
        require(NativePerimeterProjects.start(owner, WORKERS.get(coordinator), quote.core(), 1, plan, layout, before, clearance,
                null, prepared.territory(), fingerprint), "Public protected paid legacy-plan commission failed");
        var projects = PerimeterProjectStore.all(core); require(projects.size() == 1, "More than one project admitted");
        accepted = projects.get(0); projectId = accepted.header().projectId(); ledgerGeneration = ConstructionEditLedger.get(level).generation();
        require(accepted.state() == PerimeterProject.State.RUNNING && FactionBank.balance(core) == 1936
                && core.getLong("BankInterestAt") == beforeClock, "Paid commission or Treasury clock changed");
        var residents = RaidSavedData.get(owner.server).civilianFactions.get(coreKey);
        require(residents != null, "Starter civilian ledger missing");
        treasury = new NativeQaTreasury(core, bankRate(core), level.getGameTime(), 2000, 64, residents.getCompound("Residents").size());
        WORKERS.values().forEach(worker -> worker.setNoAi(false)); // Ends only the precommission fixture hold.
        REPORT.put("projectId", projectId.toString()); REPORT.put("manifestHash", accepted.manifestHash());
        REPORT.put("firstStageArea", accepted.active().areaId().toString());
        REPORT.put("firstStageTargets", accepted.active().layout().targets().keySet().stream().sorted().toList());
        REPORT.put("firstStageMaterials", accepted.active().layout().targets().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                entry -> entry.getKey().toString(), Map.Entry::getValue)));
        REPORT.put("acceptedProject", accepted.save().toString()); REPORT.put("ledgerGeneration", ledgerGeneration.toString());
        REPORT.put("projectGeneration", accepted.header().generation()); REPORT.put("coreKey", coreKey);
        REPORT.put("materialCounts", Map.of("minecraft:cobblestone", 352, "minecraft:oak_planks", 220));
        REPORT.put("syntheticPartition", true); REPORT.put("qaSectionTargetCap", 64);
        REPORT.put("commissionPath", "Explicit legacy 572-target plan through public protected admission and one real Treasury debit");
        transit.assertNoTickets(level.getServer());
    }

    private static void transfer(BuilderEntity original, ServerLevel destination, BlockPos feet) throws Exception {
        require(!settledEndPending && original.getUUID().equals(helper), "Previous transfer lacks settled custody or wrong actor");
        if (original.level() == destination.getServer().overworld()) assertActive(destination.getServer().overworld(), project(destination.getServer().overworld()));
        else require(laterProof(original).valid() && laterProof(original).state() == NativeLaterStageHelperProof.State.DETACHED_SUSPENDED,
                "Native return lacks exact detached historical provenance");
        var next = transit.transfer(original, destination, feet);
        transferredSource = original; transferredDestination = next; settledEndPending = true;
        transferredAt = destination.getServer().overworld().getGameTime(); settledAt = -1;
        WORKERS.put(helper, next); GROUNDED.put(helper, 0);
        require(GOALS.containsKey(helper) && GOALS.get(helper).builderEntity == next, "Native replacement goal was not observed at join");
    }

    /** Exact existing ticket only; the eviction clock starts after settled custody and verified removal. */


    private static void remember(BuilderEntity worker) {
        require(worker != null, "Missing exact worker");
        BuilderWorkGoal goal = worker.goalSelector.getAvailableGoals().stream().map(value -> value.getGoal())
                .filter(BuilderWorkGoal.class::isInstance).map(BuilderWorkGoal.class::cast).findFirst().orElseThrow();
        WORKERS.put(worker.getUUID(), worker); GOALS.put(worker.getUUID(), goal);
        PLACEMENTS.computeIfAbsent(worker.getUUID(), ignored -> new LinkedHashSet<>());
    }

    private static void updateGrounded() {
        for (var worker : WORKERS.values()) {
            boolean ready = worker.isAlive() && !worker.isRemoved() && worker.onGround() && worker.fallDistance == 0;
            GROUNDED.put(worker.getUUID(), ready ? Math.min(20, GROUNDED.getOrDefault(worker.getUUID(), 0) + 1) : 0);
        }
    }

    private static boolean eligible(BuilderEntity worker, long tick) {
        var reasons = new ArrayList<>(NativeActiveHelperTransit.eligibility(worker));
        if (PENDING_LOADS.containsKey(worker)) reasons.add("observed native load mirror not yet restored");
        if (GROUNDED.getOrDefault(worker.getUUID(), 0) < 5) reasons.add("five ordinary grounded END observations required");
        var ledger = ConstructionEditLedger.get(worker.getServer().overworld());
        if (!handProvenance(worker, ledger)) reasons.add("exact protected hand provenance");
        if (tick != lastEligibility && (tick % 20 == 0 || reasons.isEmpty())) {
            lastEligibility = tick;
            record("eligibility", Map.of("worker", worker.getUUID().toString(), "beforeItemSlot", worker.getBeforeItemSlot(),
                    "groundedTicks", GROUNDED.getOrDefault(worker.getUUID(), 0), "neededItemCount", worker.neededItems == null ? -1 : worker.neededItems.size(),
                    "rejections", List.copyOf(reasons), "nativeState", String.valueOf(GOALS.get(worker.getUUID()).state)));
        }
        return reasons.isEmpty();
    }

    private static void assertSuspended(ServerLevel level, PerimeterProject project) {
        var ledger = ConstructionEditLedger.get(level);
        require(level.getEntity(helper) == null && ledger.suspendedCrewMember(project, helper)
                && !ledger.crewMember(project, helper) && !ledger.crewStageGranted(project, helper)
                && ledger.knownCrewMembers(project).equals(Set.of(helper)), "Absent helper retained work authority or lost historical membership");
    }

    private static void assertActive(ServerLevel level, PerimeterProject project) {
        require(workAuthority(level, WORKERS.get(helper), project), "Helper lacks exact active current-stage production authority");
        if (project.activeStage() == 2) require(originalHand.equals(ProtectedBuilderHandLifecycle.read(
                WORKERS.get(helper).getPersistentData().getCompound(ProtectedBuilderHandLifecycle.KEY))), "A2 rebased original A0 hand identity");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void joined(EntityJoinLevelEvent event) {
        if (!ENABLED || !observesNativeLifecycle() || event.getLevel().isClientSide()) return;
        try {
            if (!(event.getEntity() instanceof BuilderEntity worker)) return;
            if (accepted == null) {
                require(phase == Phase.SETUP && !event.loadedFromDisk() && !event.isCanceled(), "Unexpected initial builder admission");
                SETUP_JOINS.add(Map.of("worker", worker.getUUID().toString(), "dimension", event.getLevel().dimension().location().toString(),
                        "loadedFromDisk", false, "syntheticPrecommission", true)); return;
            }
            transit.onJoin(event);
            require(worker.getUUID().equals(helper) && JOINS.size() < 3, "Unexpected worker admission after commission");
            boolean disk = JOINS.size() == 1;
            require((JOINS.isEmpty() && phase == Phase.L_OUT || disk && phase == Phase.L_LOAD
                    || JOINS.size() == 2 && phase == Phase.L_RETURN) && event.loadedFromDisk() == disk,
                    "Native helper admission phase or disk flag changed");
            long ordinal = ++sequence; var value = NativeActiveHelperTransit.inventory(worker);
            require(!event.isCanceled() && RAW_LOADS.put(worker, value) == null, "Canceled or repeated raw worker admission");
            JOINS.add(Map.of("sequence", ordinal, "worker", worker.getUUID().toString(), "dimension", event.getLevel().dimension().location().toString(),
                    "loadedFromDisk", disk, "gameTime", event.getLevel().getServer().overworld().getGameTime(), "inventory", inventoryEvidence(value)));
            if (disk) {
                require(finalizedOffline != null && value.equals(finalizedOffline) && DISK_JOINS.put(helper, value) == null,
                        "Disk load changed finalized offline custody");
            }
            remember(worker);
        } catch (Throwable problem) { fail(problem); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void joinedAfterProduction(EntityJoinLevelEvent event) {
        if (!ENABLED || !observesNativeLifecycle() || transit == null || event.getLevel().isClientSide()) return;
        try {
            transit.onJoined(event);
            if (!(event.getEntity() instanceof BuilderEntity worker) || !PLACEMENTS.containsKey(worker.getUUID())) return;
            var raw = RAW_LOADS.remove(worker); require(raw != null && !event.isCanceled(), "Missing uncanceled raw load custody");
            require(raw.beforeItemSlot() == -1 && !ProtectedBuilderHandMirror.pending(raw.persistent())
                    && !ProtectedBuilderHandMirror.reviewNeeded(raw.persistent()), "Raw load did not preserve naturally closed hands");
            var expectedData = raw.persistent(); expectedData.putBoolean("SiegeProtectedHandRefreshPending", true); // Comparison copy only.
            var armed = new NativeActiveHelperTransit.Inventory(raw.slots(), raw.main(), raw.off(), raw.beforeItemSlot(), expectedData);
            require(armed.equals(NativeActiveHelperTransit.inventory(worker)), "Production admission changed more than hand-arm receipt");
            ItemStack slot5 = worker.getInventory().getItem(5), main = worker.getMainHandItem();
            boolean thirdAlias = false;
            for (int index = 0; index < worker.getInventory().getContainerSize(); index++) if (index != 5 && !slot5.isEmpty())
                thirdAlias |= worker.getInventory().getItem(index) == slot5 || worker.getInventory().getItem(index) == main;
            require(!thirdAlias, "Third cargo slot aliases loaded hand");
            boolean split = !worker.getMainHandItem().isEmpty() && worker.getMainHandItem() != worker.getInventory().getItem(5);
            require(PENDING_LOADS.put(worker, new PendingMirror(armed, split, ProtectedBuilderHandMirror.rebindCount(raw.persistent()))) == null,
                    "Repeated pending load custody");
            record("load-mirror-armed", Map.of("worker", worker.getUUID().toString(), "raw", inventoryEvidence(raw),
                    "armed", inventoryEvidence(armed), "split", split, "thirdSlotAlias", false, "slot5Count", slot5.getCount(), "slot5Maximum", Math.min(worker.getInventory().getMaxStackSize(), slot5.getMaxStackSize())));
        } catch (Throwable problem) { fail(problem); }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void left(EntityLeaveLevelEvent event) {
        if (!ENABLED || !observesNativeLifecycle() || transit == null || event.getLevel().isClientSide()) return;
        try { transit.onLeave(event); } catch (Throwable problem) { fail(problem); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void stopping(ServerStoppingEvent event) {
        if (!ENABLED || transit == null) return;
        try {
            transit.cleanup(event.getServer()); transit.assertNoTickets(event.getServer());
            if (!evidenceSealed && failure == null) fail(new AssertionError("Later-stage server stopped before its successful final boundary"));
        } catch (Throwable problem) { fail(problem); }
    }



    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void living(LivingEvent.LivingTickEvent event) {
        if (!ENABLED || !observesNativeLifecycle() || failure != null || accepted == null
                || !(event.getEntity().level() instanceof ServerLevel level) || level != level.getServer().overworld()) return;
        try {
            require(footprintReady(level), "Living observation lost its already-loaded footprint"); flush(level);
            if (!(event.getEntity() instanceof BuilderEntity worker) || !PLACEMENTS.containsKey(worker.getUUID()) || !worker.isAlive()) return;
            observeCanceledAuthority(level.getServer(), level);
            var project = project(level); int index = project == null ? 2 : project.activeStage();
            require(index >= 0 && index <= 2, "Native observation escaped fixed A2 ceiling"); var stage = accepted.stages().get(index);
            boolean authorized = workAuthority(level, worker, project);
            if (worker.getUUID().equals(helper) && index == 2 && authorized && !helperActiveObserved) {
                require(settledAt > transferredAt && !settledEndPending && phase == Phase.L_REJOIN,
                        "A2 activation preceded settled native return observation");
                helperActiveObserved = true; boundary(level.getServer(), "l-helper-a2-active");
            }
            var auth = new LinkedHashMap<String, Object>(); auth.put("workerInventory", inventoryEvidence(NativeActiveHelperTransit.inventory(worker)));
            auth.put("ledgerSnapshot", ConstructionEditLedger.get(level).save(new CompoundTag()).toString());
            auth.put("projectSnapshot", project == null ? null : project.save().toString());
            auth.put("currentArea", worker.currentBuildArea == null ? null : worker.currentBuildArea.getUUID().toString()); auth.put("owner", String.valueOf(WorkersBridge.readWorkerOwner(worker)));
            observation = new Observation(worker, stage.areaId(), index, stage.digest(), level.getGameTime(), pending(level),
                    workerStock(worker, Items.COBBLESTONE), workerStock(worker, Items.OAK_PLANKS), chestStock(level, Items.COBBLESTONE), chestStock(level, Items.OAK_PLANKS),
                    String.valueOf(GOALS.get(worker.getUUID()).state), authorized, level.getEntity(stage.areaId()),
                    canceledAuthorityAt >= 0 ? worldTargets(level) : Map.of(), project == null ? null : project.save().toString(), auth);
        } catch (Throwable problem) { fail(problem); }
    }

    private static void flush(ServerLevel level) {
        Observation before = observation; observation = null;
        if (before == null) return;
        CapturedStage.frame(before.tick(), level.getGameTime(), before.worker().level() == level);
        Set<Long> after = pending(level), changed = new LinkedHashSet<>(before.pending());
        require(before.pending().containsAll(after), "A native accepted block disappeared"); changed.removeAll(after);
        int cobble = workerStock(before.worker(), Items.COBBLESTONE), oak = workerStock(before.worker(), Items.OAK_PLANKS);
        int chestCobble = chestStock(level, Items.COBBLESTONE), chestOak = chestStock(level, Items.OAK_PLANKS);
        long builtCobble = changed.stream().filter(cell -> fixture.plan().blocks().get(cell).equals("minecraft:cobblestone")).count();
        long builtOak = changed.size() - builtCobble;
        if (!changed.isEmpty()) {
            require(before.authorized() && before.worker().isAlive() && !before.worker().isNoAi() && before.state().equals("PLACE_BLOCKS")
                    && before.cobble() - cobble == builtCobble && before.oak() - oak == builtOak
                    && chestCobble == before.chestCobble() && chestOak == before.chestOak(), "Unattributed native placement/debit");
            Entity receipt = before.receiptArea();
            require(receipt instanceof ProtectedBuildArea && receipt.getUUID().equals(before.area()), "Placement lost native receipt");
            var completed = Arrays.stream(receipt.getPersistentData().getCompound("SiegeProtectedConstructionV1")
                    .getLongArray("Completed")).boxed().collect(java.util.stream.Collectors.toSet());
            Set<Long> alreadyPlaced = PLACEMENTS.values().stream().flatMap(Set::stream).collect(java.util.stream.Collectors.toSet());
            CapturedStage.cells(before.authorized(), before.stage(), before.worker().getUUID().equals(helper),
                    accepted.stages().get(before.stage()).layout().targets().keySet(), completed, alreadyPlaced, changed);
            PLACEMENTS.get(before.worker().getUUID()).addAll(changed);
        }
        boolean supply = cobble > before.cobble() || oak > before.oak();
        if (supply) {
            require(before.authorized() && changed.isEmpty() && cobble - before.cobble() == before.chestCobble() - chestCobble
                    && oak - before.oak() == before.chestOak() - chestOak, "Supply lacks actual finite chest withdrawal");
            SUPPLIED.add(before.worker().getUUID());
        }
        if (changed.isEmpty() && !supply) require(cobble == before.cobble() && oak == before.oak()
                && chestCobble == before.chestCobble() && chestOak == before.chestOak(), "Unexplained material change outside native supply/placement");
        if (canceledAuthorityAt >= 0) {
            CanceledObservation.verifyBracket(before.authorized(), changed.size(), supply, before.canceledTargets(), worldTargets(level),
                    Map.of("stock", List.of(before.cobble(), before.oak(), before.chestCobble(), before.chestOak())),
                    Map.of("stock", List.of(cobble, oak, chestCobble, chestOak)));
            terminalObservedBrackets++;
            require(terminalObservedBrackets < 10000, "Bounded terminal bracket capacity exceeded");
            record("terminal-native-bracket", Map.of("worker", before.worker().getUUID().toString(), "gameTime", before.tick(),
                    "authorized", false, "changedTargets", 0, "targetStatesUnchanged", true, "before", List.of(before.cobble(), before.oak(), before.chestCobble(), before.chestOak()),
                    "after", List.of(cobble, oak, chestCobble, chestOak)));
        }
        if (!changed.isEmpty() || supply) {
            var work = new LinkedHashMap<String, Object>(); work.put("sequence", ++sequence);
            work.put("worker", before.worker().getUUID().toString()); work.put("gameTime", before.tick()); work.put("area", before.area().toString());
            work.put("stage", before.stage()); work.put("stageDigest", before.digest()); work.put("projectSnapshot", before.projectSnapshot());
            require(before.receiptArea() != null, "Native work lost its captured paid marker");
            work.put("markerReceipt", before.receiptArea().getPersistentData().toString()); work.put("markerUuid", before.receiptArea().getUUID().toString());
            work.put("startGameTime", before.tick()); work.put("endGameTime", level.getGameTime());
            work.put("startDimension", "minecraft:overworld"); work.put("endDimension", before.worker().level().dimension().location().toString());
            work.put("authority", before.authority());
            work.put("kind", supply ? "native-supply" : "native-placement"); work.put("cells", List.copyOf(changed));
            work.put("before", List.of(before.cobble(), before.oak(), before.chestCobble(), before.chestOak()));
            work.put("after", List.of(cobble, oak, chestCobble, chestOak));
            work.put("placedCobble", builtCobble); work.put("placedOak", builtOak);
            work.put("authorized", before.authorized()); work.put("nativeState", before.state()); WORK.add(work);
        }
    }

    private static void initialize(Minecraft mc) throws Exception {
        String configured = System.getProperty("siegeoverhaul.nativeQa.directory", "");
        require(!configured.isBlank(), "Missing isolated helper QA directory");
        directory = Path.of(configured).toAbsolutePath().normalize();
        require(directory.endsWith(Path.of("build", "native-helper-qa", "client"))
                && directory.toRealPath().equals(directory) && mc.gameDirectory.toPath().toRealPath().equals(directory),
                "Unsafe active helper game directory");
        evidence = directory.getParent().resolve("evidence"); Files.createDirectories(evidence);
        require(!Files.exists(directory.resolve("saves").resolve(NativeStagedHandoffFixture.WORLD)), "Refusing an existing helper world");
        require("later-stage-disk".equals(System.getProperty("siegeoverhaul.nativeQa.activeHelperCase")), "Unknown later-stage case");
        require(evidence.toRealPath().equals(evidence) && !Files.exists(evidence.resolve("result.json")), "Previously used evidence directory");
        Path defaults = directory.resolve("defaultconfigs");
        require(!Files.isSymbolicLink(defaults), "Symlinked fresh default config directory"); Files.createDirectories(defaults);
        // Synthetic precommission world setup only. Never edit an existing world's server config.
        Files.writeString(defaults.resolve("recruits-server.toml"), CONFIG, java.nio.file.StandardOpenOption.CREATE_NEW);
        var parity = NativeActiveHelperApiParity.verify(evidence); REPORT.put("loadedApiParity", parity);
        attestSerializer(parity); candidate = readPins();
        REPORT.put("sourceCommit", Files.readString(evidence.resolve("source-commit.txt")).trim());
        REPORT.put("sourceTree", Files.readString(evidence.resolve("source-tree.txt")).trim());
        REPORT.put("startedUtc", Instant.now().toString()); REPORT.put("mode", "active-helper-later-stage-disk");
        REPORT.put("formatVersion", 1); REPORT.put("totalLimitSeconds", LIMIT_SECONDS);
        REPORT.put("phaseLimitTicks", 1200); REPORT.put("evictionLimitTicks", 1800); REPORT.put("finalizationLimitTicks", 200);
        REPORT.put("stableGroundedEndTicks", 5); REPORT.put("absenceObservationTicks", 100);
        REPORT.put("scope", "Separate fresh-world paid A0 helper disk absence, natural A0/A1 retirement, fixed unfinished A2 native return/work and loaded cancellation");
        REPORT.put("notCovered", List.of("Whole-site chunk eviction", "Sleep", "Post-DONE behavior", "Whole-project completion",
                "Server reopen", "Dedicated-client networking", "Normal review-menu commission", "Arbitrary add-ons or transformed-method equivalence"));
        REPORT.put("sameStageReturnVerified", false); REPORT.put("coordinatorFirstDiskOrderVerified", false); REPORT.put("canceledWhileAbsentVerified", false);
        REPORT.put("stageSkippedReturnVerified", false); REPORT.put("laterStageReturnVerified", false);
        REPORT.put("wholeSiteEvictionVerified", false); REPORT.put("ownerMode", "non-op Survival");
        REPORT.put("nativeWorkerChunkLoading", false); REPORT.put("configurationScope", "Fresh disposable world defaultconfigs only");
        REPORT.put("events", EVENTS); REPORT.put("nativeWork", WORK); REPORT.put("nativeWorkerJoins", JOINS);
        REPORT.put("treasuryObserverContracts", NativeQaTreasuryContracts.verify());
    }

    private static CompoundTag core(MinecraftServer server) {
        var value = RaidSavedData.get(server).siegeCores.get(coreKey); require(value != null, "Authoritative core missing"); return value;
    }
    private static int bankRate(CompoundTag core) { return FactionBank.interestRate(core, RaidConfig.BANK_INTEREST_BASIS_POINTS.get()); }
    private static CompoundTag authority(MinecraftServer server) {
        var core = core(server); var copy = new CompoundTag();
        for (String key : List.of(PerimeterProjectStore.KEY, PerimeterStageJournal.KEY, "BankEmeralds", "BankInterestAt",
                "BankInterestRemainder", "CivilianTaxesTotal", "BankLedger")) if (core.contains(key)) copy.put(key, core.get(key).copy());
        return copy;
    }
    private static PerimeterProject project(ServerLevel level) { return PerimeterProjectAuthority.snapshot(level, coreKey).get(projectId); }
    private static void requireFirstStage(PerimeterProject project) {
        require(project != null && project.state() == PerimeterProject.State.RUNNING && project.activeStage() == 0
                && project.receipts().isEmpty() && project.active() != null, "Bounded same-stage proof lost its unfinished RUNNING first stage");
    }
    private static void verifyImmutable(ServerLevel level) {
        require(footprintReady(level), "Immutable audit would load its observation footprint");
        require(ConstructionEditLedger.get(level).generation().equals(ledgerGeneration), "Ledger generation changed");
        var project = project(level);
        if (project != null) {
            require(project.header().equals(accepted.header()) && project.manifestHash().equals(accepted.manifestHash())
                    && project.layout().equals(layout) && project.targets().equals(accepted.targets())
                    && project.before().equals(accepted.before()) && project.reservation().equals(accepted.reservation())
                    && project.observations().equals(accepted.observations()) && project.formatVersion() == accepted.formatVersion()
                    && project.clearanceBefore().equals(accepted.clearanceBefore()) && project.payment().equals(accepted.payment())
                    && ConstructionEditLedger.get(level).assignedBuilder(project).equals(coordinator), "Immutable paid project or assigned coordinator changed");
            CaseWindow.progress(project.activeStage(), project.receipts().size(), project.state().name(), canceledAuthorityAt >= 0);
            if (canceledAuthorityAt >= 0) require(project.state() == PerimeterProject.State.CANCELED, "Canceled authority resumed");
        } else require(canceledAuthorityAt >= 0, "Project disappeared before authenticated cancellation");
        for (var entry : fixture.nonPlanCells().entrySet()) require(level.getBlockState(entry.getKey()).equals(entry.getValue()),
                "Native work modified a non-plan cell: " + entry.getKey());
        NativeStagedHandoffFixture.assertHollowAir(level, fixture.oracle());
        NativeStagedHandoffFixture.auxiliaryIsolationSnapshot(level, fixture);
        var territory = RecruitsClaimsBridge.getFactionTerritory(level, NativeStagedHandoffFixture.FACTION, PerimeterTerritory.MAX_CHUNKS);
        require(territory.ready() && territory.chunks().equals(NativeStagedHandoffFixture.TERRITORY), "Claim territory changed");
        require(PLACEMENTS.keySet().equals(Set.of(coordinator, helper)), "A third builder entered the fixture");
        require(572 - pending(level).size() == placements(coordinator) + placements(helper), "A placement escaped its loaded native observation bracket");
        for (var worker : WORKERS.values()) require((worker.isAlive() || worker.getUUID().equals(helper)
                && worker.isRemoved() && worker.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK && worker.getHealth() > 0)
                && ownerId.equals(WorkersBridge.readWorkerOwner(worker)),
                "Original worker died or lost exact ownership");
    }

    private static void verifyCanceled(ServerLevel level) {
        var snapshot = PerimeterProjectAuthority.snapshot(level, coreKey); var terminal = snapshot.terminal(projectId);
        var ledger = ConstructionEditLedger.get(level);
        require(snapshot.get(projectId) == null && terminal != null && terminal.state() == PerimeterProject.State.CANCELED
                && terminal.projectId().equals(projectId) && terminal.generation() == accepted.header().generation()
                && terminal.coreKey().equals(coreKey) && terminal.manifestHash().equals(accepted.manifestHash())
                && terminal.owner().equals(ownerId) && terminal.builder().equals(coordinator)
                && terminal.activeStage() == 2 && terminal.verifiedStages() == 2 && terminal.totalTargetCount() == 572
                && terminal.claimChunkCount() == 1 && terminal.payment().equals(accepted.payment())
                && terminal.stages().equals(accepted.stages().stream().map(part -> new PerimeterTerminalReceipt.Stage(
                        part.index(), part.areaId(), part.digest(), part.layout().targets().size())).toList())
                && ledger.sameGeneration(terminal.cleanup().ledgerGeneration()), "Terminal cancellation identity/payment/cleanup mismatch");
        require(ledger.workerCleanupAreas(terminal, helper).equals(Set.of(accepted.stages().get(0).areaId(), accepted.stages().get(2).areaId()))
                && ledger.handLifecycle(helper).equals(originalHand)
                && originalHand.equals(ProtectedBuilderHandLifecycle.read(WORKERS.get(helper).getPersistentData().getCompound(ProtectedBuilderHandLifecycle.KEY))),
                "Canceled helper lost exact A0/A2 history or original A0 hand");
        require(canceledReceipt == null || canceledReceipt.equals(terminal.save()), "Compact terminal changed after observation");
        require(PerimeterProjectStore.all(core(level.getServer())).isEmpty()
                && PerimeterStageJournal.get(core(level.getServer()), accepted) == null && !ledger.contains(projectId) && accepted.stages().stream().noneMatch(part -> ledger.contains(part.areaId()))
                && level.getEntitiesOfClass(ProtectedBuildArea.class, NativeStagedHandoffFixture.BOUNDS, Entity::isAlive).isEmpty(),
                "Canceled project retained native markers or reservations");
        for (var worker : WORKERS.values()) require(level.getEntity(worker.getUUID()) == worker && worker.currentBuildArea == null
                && !WorkersBridge.hasActiveBuildArea(worker) && !PerimeterProjectLink.reserved(worker)
                && !worker.getPersistentData().contains("SiegeProtectedAreaReceipt")
                && !worker.getPersistentData().contains("SiegeProtectedLedgerGeneration")
                && !worker.getPersistentData().contains("SiegePlayerFortificationOwner")
                && !worker.getPersistentData().contains(com.devfarinsky.siegeoverhaul.ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID)
                && ProtectedInventoryCleanup.read(worker.getPersistentData()).isEmpty() && worker.neededItems.isEmpty()
                && ledger.unresolvedCrewProject(worker.getUUID()) == null, "Loaded cancellation left worker scope or cleanup");
        require(stagePending(level, 0) == 0 && stagePending(level, 1) == 0 && stagePending(level, 2) > 0
                && stagePending(level, 2) < 30 && stagePlacements(helper, 2) > 0
                && 572 - pending(level).size() == 65 + 30 - stagePending(level, 2), "Cancellation missed unfinished A2 or lost native completed cells");
        for (int index = 3; index < accepted.stages().size(); index++) require(stagePending(level, index)
                == accepted.stages().get(index).layout().targets().size(), "A later section was built");
        assertCanceledFreeze(level.getServer(), level);
    }

    private static void conservation(MinecraftServer server, ServerPlayer owner) { materialCensus(server, owner); }
    private static Map<String, Object> materialCensus(MinecraftServer server, ServerPlayer owner) {
        require(footprintReady(server.overworld()), "Material census would load its observation footprint");
        var result = new LinkedHashMap<String, Object>();
        var level = server.overworld();
        for (Item material : List.of(Items.COBBLESTONE, Items.OAK_PLANKS)) {
            long built = fixture.plan().blocks().keySet().stream().filter(cell -> level.getBlockState(BlockPos.of(cell)).getBlock().asItem() == material).count();
            int workers = 0;
            for (UUID id : List.of(coordinator, helper)) {
                BuilderEntity current = WORKERS.get(id); int registered = 0;
                for (var world : server.getAllLevels()) if (world.getEntity(id) != null) {
                    require(world.getEntity(id) == current, "Material custody points at a stale actor"); registered++;
                }
                require(registered <= 1, "Duplicate cross-dimension material custody");
                if (registered == 1 || !current.isRemoved()) workers += workerStock(current, material);
                else {
                    require(id.equals(helper) && current.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK,
                            "Unaccounted removed-worker material custody");
                    var saved = offline == null ? NativeActiveHelperTransit.inventory(current) : offline;
                    workers += saved.slots().stream().map(ItemStack::of).filter(stack -> stack.is(material)).mapToInt(ItemStack::getCount).sum();
                }
            }
            long loose = 0;
            for (var world : server.getAllLevels()) for (Entity actor : world.getAllEntities())
                if (actor instanceof ItemEntity item && item.isAlive() && item.getItem().is(material)) loose += item.getItem().getCount();
            int ownerStock = owner == null ? 0 : count(owner.getInventory(), material);
            require(ownerStock == 0 && loose == 0, "Transit unexpectedly moved construction stock to owner or drops");
            long expected = material == Items.COBBLESTONE ? 352 : 220;
            require(built + chestStock(level, material) + workers + ownerStock + loose == expected,
                    "Finite cross-dimension/offline material conservation failed: " + material);
            result.put(material == Items.COBBLESTONE ? "cobble" : "oak", Map.of("built", built, "chest", chestStock(level, material),
                    "workerCustody", workers, "owner", ownerStock, "loose", loose, "total", expected));
        }
        return Map.copyOf(result);
    }

    /** Passive field census only. Never invoke claim.tick/renew/claim/release from QA. */
    private static List<Map<String, Object>> verifyClaims(ServerLevel level) throws ReflectiveOperationException {
        var field = NativeCrewWorkClaims.class.getDeclaredField("HELD"); field.setAccessible(true);
        var held = (Map<?, ?>) field.get(null);
        for (var key : held.keySet()) if (key instanceof BuilderEntity actor && PLACEMENTS.containsKey(actor.getUUID()))
            require(WORKERS.get(actor.getUUID()) == actor && !actor.isRemoved(), "An old actor retained a native claim after removal");
        var result = new ArrayList<Map<String, Object>>(); var targets = new HashSet<Long>();
        for (var worker : WORKERS.values()) {
            Object value = held.get(worker); if (value == null) continue;
            var tokenField = value.getClass().getDeclaredField("token"); tokenField.setAccessible(true);
            var token = (SharedConstructionWorkClaims.Token) tokenField.get(value);
            var areaField = value.getClass().getDeclaredField("area"); areaField.setAccessible(true);
            var area = (Entity) ((java.lang.ref.WeakReference<?>) areaField.get(value)).get();
            var goalField = value.getClass().getDeclaredField("goal"); goalField.setAccessible(true);
            Object goal = ((java.lang.ref.WeakReference<?>) goalField.get(value)).get();
            var project = project(level);
            require(project != null && area == level.getEntity(project.active().areaId()) && worker.level() == level
                    && level.getEntity(worker.getUUID()) == worker && goal == GOALS.get(worker.getUUID())
                    && token.worker().equals(worker.getUUID()) && targets.add(token.target())
                    && project.active().layout().targets().containsKey(token.target()), "Stale, foreign or duplicate native work claim");
            result.add(Map.of("worker", worker.getUUID().toString(), "target", token.target(), "area", area.getUUID().toString(),
                    "tokenIdentity", System.identityHashCode(token), "goalIdentity", System.identityHashCode(goal)));
        }
        return List.copyOf(result);
    }

    /** Exact predeclared observation envelope; availability checks never request chunks. */
    private static Set<net.minecraft.world.level.ChunkPos> observationEnvelope() {
        var chunks = new HashSet<net.minecraft.world.level.ChunkPos>();
        fixture.nonPlanCells().keySet().forEach(pos -> chunks.add(new net.minecraft.world.level.ChunkPos(pos)));
        fixture.plan().blocks().keySet().forEach(cell -> chunks.add(new net.minecraft.world.level.ChunkPos(BlockPos.of(cell))));
        fixture.plan().clearance().forEach(cell -> chunks.add(new net.minecraft.world.level.ChunkPos(BlockPos.of(cell))));
        fixture.isolationCells().keySet().forEach(pos -> chunks.add(new net.minecraft.world.level.ChunkPos(pos)));
        NativeStagedHandoffFixture.CHESTS.forEach(pos -> chunks.add(new net.minecraft.world.level.ChunkPos(pos)));
        return Set.copyOf(chunks);
    }
    private static boolean footprintReady(ServerLevel level) {
        if (fixture == null || observationChunks.isEmpty()) return false;
        for (var chunk : observationChunks) if (level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null) return false;
        for (UUID id : fixture.isolatedAuxiliaries().keySet()) if (level.getEntity(id) == null) return false;
        return true;
    }

    private static Set<Long> pending(ServerLevel level) {
        require(footprintReady(level), "Pending-target observation would load the footprint");
        Set<Long> result = new LinkedHashSet<>();
        fixture.plan().blocks().forEach((cell, material) -> {
            if (!material.equals(String.valueOf(ForgeRegistries.BLOCKS.getKey(level.getBlockState(BlockPos.of(cell)).getBlock())))) result.add(cell);
        });
        return Set.copyOf(result);
    }
    private static long placements(UUID worker) { return PLACEMENTS.get(worker).size(); }
    private static boolean bothContributed() { return placements(coordinator) > 0 && placements(helper) > 0; }
    private static Map<String, Integer> counts() {
        var result = new LinkedHashMap<String, Integer>(); PLACEMENTS.forEach((id, cells) -> result.put(id.toString(), cells.size())); return Map.copyOf(result);
    }
    private static int workerStock(BuilderEntity worker, Item item) {
        Set<ItemStack> seen = Collections.newSetFromMap(new IdentityHashMap<>()); int count = 0;
        for (int slot = 0; slot < worker.getInventory().getContainerSize(); slot++) {
            ItemStack stack = worker.getInventory().getItem(slot); seen.add(stack); if (stack.is(item)) count += stack.getCount();
        }
        ItemStack main = worker.getMainHandItem(), slot = worker.getInventory().getItem(5);
        PendingMirror pending = PENDING_LOADS.get(worker);
        if (pending != null) {
            require(!ProtectedBuilderHandMirror.reviewNeeded(worker.getPersistentData()), "Loaded mirror entered production review");
            if (ProtectedBuilderHandMirror.pending(worker.getPersistentData())) {
                var ledger = ConstructionEditLedger.get(worker.getServer().overworld());
                require(worker.getBeforeItemSlot() == -1 && !ProtectedBuilderHandMirror.activeUse(worker)
                        && handProvenance(worker, ledger)
                        && ProtectedBuilderHandMirror.sameValue(slot, main) && slot.getCount() >= 0
                        && slot.getCount() <= Math.min(worker.getInventory().getMaxStackSize(), slot.getMaxStackSize()),
                        "Pending load mirror lacks exact canonical provenance/value proof");
                var observed = NativeActiveHelperTransit.inventory(worker);
                require(observed.slots().equals(pending.armed().slots()) && observed.main().equals(pending.armed().main())
                        && observed.off().equals(pending.armed().off()), "Pending load custody changed before production rebind");
                for (int index = 0; index < worker.getInventory().getContainerSize(); index++) if (index != 5 && !slot.isEmpty())
                    require(worker.getInventory().getItem(index) != slot && worker.getInventory().getItem(index) != main,
                            "A third cargo slot aliases the pending native hand mirror");
                // Only this observed, exact authenticated load-only main-hand pair counts once.
                // Every cargo slot above remains independently counted; unequal or unobserved splits fail.
                seen.add(main);
            } else {
                require(main == slot && ProtectedBuilderHandMirror.rebindCount(worker.getPersistentData())
                        >= pending.rebinds() + (pending.split() ? 1 : 0), "Production never restored the native main-hand identity");
                record("load-mirror-restored", Map.of("worker", worker.getUUID().toString(), "split", pending.split(),
                        "before", inventoryEvidence(pending.armed()), "after", inventoryEvidence(NativeActiveHelperTransit.inventory(worker)),
                        "rebindsBefore", pending.rebinds(), "rebindsAfter", ProtectedBuilderHandMirror.rebindCount(worker.getPersistentData()),
                        "mainAliasesSlot5", true));
                PENDING_LOADS.remove(worker);
            }
        }
        if (seen.add(main) && main.is(item)) count += main.getCount();
        ItemStack off = worker.getOffhandItem(); if (seen.add(off) && off.is(item)) count += off.getCount();
        return count;
    }
    private static int count(Container container, Item item) {
        int value = 0; for (int slot = 0; slot < container.getContainerSize(); slot++) if (container.getItem(slot).is(item)) value += container.getItem(slot).getCount(); return value;
    }
    private static int chestStock(ServerLevel level, Item item) {
        require(footprintReady(level), "Stock observation would load its chest chunk");
        int result = 0;
        for (var pos : NativeStagedHandoffFixture.CHESTS) {
            require(level.getBlockEntity(pos) instanceof Container, "Finite source chest missing"); result += count((Container) level.getBlockEntity(pos), item);
        }
        return result;
    }
    private static Map<String, Object> inventoryEvidence(NativeActiveHelperTransit.Inventory inventory) {
        return Map.of("slots", inventory.slots().stream().map(CompoundTag::toString).toList(), "main", inventory.main().toString(),
                "off", inventory.off().toString(), "beforeItemSlot", inventory.beforeItemSlot(), "persistent", inventory.persistent().toString());
    }
    private static boolean sameInventory(NativeActiveHelperTransit.Inventory left, NativeActiveHelperTransit.Inventory right) {
        return left != null && left.equals(right);
    }
    private static void boundary(MinecraftServer server, String kind) throws ReflectiveOperationException {
        var level = server.overworld(); var project = project(level); var ledger = ConstructionEditLedger.get(level);
        var workers = new LinkedHashMap<String, Object>();
        for (var worker : WORKERS.values()) {
            var value = new LinkedHashMap<String, Object>(); value.put("dimension", worker.level().dimension().location().toString());
            value.put("removed", worker.isRemoved()); value.put("removalReason", String.valueOf(worker.getRemovalReason()));
            value.put("inventory", inventoryEvidence(NativeActiveHelperTransit.inventory(worker))); value.put("nativeState", String.valueOf(GOALS.get(worker.getUUID()).state));
            value.put("entityTick", worker.tickCount); value.put("handPending", ProtectedBuilderHandMirror.pending(worker.getPersistentData()));
            value.put("handRebindCount", ProtectedBuilderHandMirror.rebindCount(worker.getPersistentData()));
            value.put("mainAliasesSlot5", worker.getMainHandItem() == worker.getInventory().getItem(5));
            value.put("currentArea", worker.currentBuildArea == null ? null : worker.currentBuildArea.getUUID().toString());
            value.put("owner", String.valueOf(WorkersBridge.readWorkerOwner(worker)));
            value.put("position", List.of(worker.getX(), worker.getY(), worker.getZ())); value.put("beforeItemSlot", worker.getBeforeItemSlot());
            value.put("neededItemCount", worker.neededItems == null ? -1 : worker.neededItems.size());
 workers.put(worker.getUUID().toString(), value);
        }
        var data = new LinkedHashMap<String, Object>(); data.put("workers", workers); data.put("placementCounts", counts());
        data.put("pendingTargets", pending(level).size()); data.put("claims", verifyClaims(level));
        data.put("projectSnapshot", project == null ? null : project.save().toString()); data.put("coreSnapshot", core(server).toString());
        data.put("ledgerSnapshot", ledger.save(new CompoundTag()).toString()); data.put("journalSnapshot", core(server).getCompound(PerimeterStageJournal.KEY).toString());
        data.put("receipts", project == null ? List.of() : project.save().getList("Receipts", 10).stream().map(Object::toString).toList());
        data.put("gameTime", level.getGameTime()); data.put("nanoTime", System.nanoTime()); data.put("ownerMode", "non-op Survival");
        data.put("materials", materialCensus(server, server.getPlayerList().getPlayer(ownerId)));
        data.put("stock", constructionStock(server)); data.put("targetStates", targetEvidence(worldTargets(level)));
        data.put("remainingA0Targets", stagePending(level, 0));
        if (project != null) {
            data.put("projectState", project.state().name()); data.put("stage", project.activeStage());
            data.put("helperActive", ledger.crewMember(project, helper)); data.put("helperGranted", ledger.crewStageGranted(project, helper));
            data.put("helperSuspended", ledger.suspendedCrewMember(project, helper)); data.put("helperRejoining", ledger.rejoiningCrewMember(project, helper));
        }
        if (kind.startsWith("l-stage-") && kind.endsWith("-retired")) {
            int index = Integer.parseInt(kind.substring(8, 9)); UUID area = accepted.stages().get(index).areaId();
            data.put("markerPresent", level.getEntity(area) != null); data.put("leasePresent", ledger.contains(area));
        }
        if (Set.of("l-holding-disk-join", "l-holding-cleanup-ready", "l-before-native-return").contains(kind)) {
            var proof = laterProof(WORKERS.get(helper)); require(proof.valid(), "Historical boundary proof failed: " + proof.reason());
            data.put("helperProof", proof.evidence()); data.put("helperProofState", proof.state().name());
        }
        var terminal = PerimeterProjectAuthority.snapshot(level, coreKey).terminal(projectId);
        if (terminal != null) {
            data.put("terminalReceipt", terminal.save().toString());
            data.put("terminalReservationsAbsent", !ledger.contains(projectId) && accepted.stages().stream().noneMatch(part -> ledger.contains(part.areaId())));
            data.put("terminalMarkersAbsent", level.getEntitiesOfClass(ProtectedBuildArea.class, NativeStagedHandoffFixture.BOUNDS, Entity::isAlive).isEmpty());
        }
        if (kind.equals("l-authoritative-canceled")) {
            data.put("authoritySource", project == null ? "compact-terminal" : "live-project");
            data.put("authority", project == null ? terminal.save().toString() : project.save().toString());
        }
        record(kind, data);
    }
    private static void move(long tick, Phase next) { phase = next; phaseAt = tick; record("phase", Map.of("phase", next.name(), "gameTime", tick)); }
    private static void record(String kind, Map<String, ?> data) {
        require(!evidenceSealed, "Attempted observation append after canceled evidence seal");
        require(EVENTS.size() < 4096, "Bounded evidence event capacity exceeded");
        var value = new LinkedHashMap<String, Object>(); value.put("sequence", ++sequence); value.put("kind", kind); value.put("phase", phase.name()); value.putAll(data); EVENTS.add(value);
    }
    private static void screenshot(Minecraft mc, String name) throws Exception {
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            require(image.getWidth() >= 640 && image.getHeight() >= 360, "Framebuffer too small"); image.writeToFile(evidence.resolve(name)); SHOTS.add(name);
        }
    }
    private static void fail(Throwable problem) {
        if (failure == null) { failure = problem; com.devfarinsky.siegeoverhaul.FactionLogger.LOG.error("Active helper native QA failed in {}", phase, problem); }
    }
    private static void finish(Minecraft mc) {
        if (finished) return;
        var server = mc.getSingleplayerServer();
        if (transit != null && server != null && !finishCleanupReady) {
            if (!finishCleanupScheduled) {
                finishCleanupScheduled = true;
                server.execute(() -> {
                    try { transit.cleanup(server); transit.assertNoTickets(server); REPORT.put("finalOwnedTicketCount", 0); }
                    catch (Throwable problem) { fail(problem); REPORT.put("ticketCleanupFailure", problem.toString()); }
                    finally { finishCleanupReady = true; }
                });
            }
            return;
        }
        if (failure == null && !evidenceSealed) fail(new AssertionError("Client finish preceded canceled server evidence seal"));
        finished = true;
        try {
            if (mc.level != null && evidence != null) screenshot(mc, failure == null ? "04-helper-lifecycle-complete.png" : "failure-active-helper.png");
        } catch (Throwable problem) { fail(problem); }
        REPORT.put("status", failure == null && Boolean.TRUE.equals(REPORT.get("boundedScenarioVerified")) ? "passed" : "failed");
        REPORT.put("phase", phase.name()); REPORT.put("finishedUtc", Instant.now().toString());
        REPORT.put("elapsedSeconds", (System.nanoTime() - started) / (double) SECOND); REPORT.put("screenshots", List.copyOf(SHOTS));
        if (treasury != null) REPORT.put("treasuryAccounting", treasury.evidence());
        if (failure != null) REPORT.put("failure", failure.toString());
        try { if (evidence != null) Files.writeString(evidence.resolve("result.json"), new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(REPORT)); }
        catch (Exception problem) { com.devfarinsky.siegeoverhaul.FactionLogger.LOG.error("Cannot write helper QA evidence", problem); }
        mc.stop();
    }
    private static JsonObject readPins() throws Exception {
        byte[] bytes;
        try (var input = NativeLaterStageHelperQa.class.getResourceAsStream(RESOURCE)) {
            require(input != null, "Missing immutable cap-64 candidate resource"); bytes = input.readNBytes(256 * 1024 + 1);
        }
        require(bytes.length > 0 && bytes.length <= 256 * 1024
                && PIN.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))),
                "Changed or oversized cap-64 candidate resource");
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static void attestSerializer(Map<String, Object> parity) throws Exception {
        Map<?, ?> supplier = (Map<?, ?>) parity.get("forgeSuperclassTail");
        Path path = Path.of((String) supplier.get("provider")); String hash = (String) supplier.get("supplierSha256");
        serializerInstance = EntityDataSerializers.COMPOUND_TAG;
        List<Method> methods = SerializerShape.verify(serializerInstance, EntityDataSerializer.class,
                EntityDataSerializers.class, FriendlyByteBuf.class, CompoundTag.class);
        Class<?> implementation = serializerInstance.getClass();
        var closure = new LinkedHashSet<Class<?>>(List.of(CompoundTag.class, ListTag.class, NbtUtils.class,
                FriendlyByteBuf.class, EntityDataSerializers.class, EntityDataSerializer.class, implementation));
        methods.forEach(method -> closure.add(method.getDeclaringClass()));
        if (methods.get(1).getReturnType() != Object.class) closure.add(methods.get(1).getReturnType());
        require(closure.size() >= 7 && closure.size() <= 10, "Unexpected serializer resource closure");
        var proofs = new ArrayList<Map<String, String>>();
        for (Class<?> type : closure) proofs.add(NativeActiveHelperApiParity.TailResource.verify(type, path, hash));
        serializerContract = new LinkedHashMap<>();
        serializerContract.put("interface", EntityDataSerializer.class.getName());
        serializerContract.put("fieldOwner", EntityDataSerializers.class.getName()); serializerContract.put("field", "COMPOUND_TAG");
        serializerContract.put("implementation", implementation.getName()); serializerContract.put("sameDefiningLoader", true);
        serializerContract.put("implementationConcrete", true); serializerContract.put("instanceStable", false);
        serializerContract.put("methods", methods.stream().map(NativeLaterStageHelperQa::methodEvidence).toList());
        REPORT.put("serializerContract", serializerContract);
        REPORT.put("compoundTagImplementation", implementation.getName()); REPORT.put("serializerParity", List.copyOf(proofs));
        SerializerShape.requireStable(serializerInstance, EntityDataSerializers.COMPOUND_TAG);
    }

    private static Map<String, Object> methodEvidence(Method method) {
        var result = new LinkedHashMap<String, Object>();
        result.put("name", method.getName()); result.put("owner", method.getDeclaringClass().getName());
        result.put("parameters", java.util.Arrays.stream(method.getParameterTypes()).map(Class::getName).toList());
        result.put("returnType", method.getReturnType().getName()); result.put("public", true);
        result.put("static", false); result.put("abstract", false); result.put("returnCompatible", true);
        result.put("bridge", method.isBridge()); result.put("synthetic", method.isSynthetic()); return result;
    }

    /** Actual field-instance metadata; no assumed anonymous owner or method-body claim. */
    static final class SerializerShape {
        private SerializerShape() {}
        static List<Method> verify(Object instance, Class<?> type, Class<?> fieldOwner, Class<?> buffer, Class<?> payload) throws Exception {
            if (instance == null || !type.isInterface() || !type.isInstance(instance)) throw new AssertionError("Wrong native serializer instance/type");
            verifyType(instance.getClass(), type, fieldOwner);
            return methods(instance.getClass(), buffer, payload);
        }
        static void verifyType(Class<?> implementation, Class<?> type, Class<?> fieldOwner) {
            if (!type.isAssignableFrom(implementation) || implementation.isInterface() || Modifier.isAbstract(implementation.getModifiers()))
                throw new AssertionError("Native serializer implementation is not concrete");
            if (implementation.getClassLoader() != type.getClassLoader() || type.getClassLoader() != fieldOwner.getClassLoader())
                throw new AssertionError("Native serializer defining loader differs");
        }
        static List<Method> methods(Class<?> implementation, Class<?> buffer, Class<?> payload) throws Exception {
            Method write = implementation.getMethod("write", buffer, Object.class);
            Method read = implementation.getMethod("read", buffer);
            for (Method method : List.of(write, read)) {
                int flags = method.getModifiers();
                if (!Modifier.isPublic(flags) || Modifier.isStatic(flags) || Modifier.isAbstract(flags))
                    throw new AssertionError("Native serializer method is not public concrete instance behavior");
            }
            if (write.getReturnType() != void.class) throw new AssertionError("Native serializer write return changed");
            if (read.getReturnType() != Object.class && !payload.isAssignableFrom(read.getReturnType()))
                throw new AssertionError("Native serializer read return is incompatible");
            return List.of(write, read);
        }
        static void requireStable(Object initial, Object current) {
            if (initial == null || initial != current) throw new AssertionError("Native COMPOUND_TAG instance changed");
        }
    }

    private static String validateNative(PerimeterStageLayout.Stage part, JsonObject expected) {
        int ordinal = CALLS.size();
        require(ordinal < 46, "Extra native partition validator call");
        CompoundTag blueprint = TerritoryFortification.blueprint(part.targets(), part.min(), part.max());
        int entries = blueprint.getList("blocks", 10).size();
        require(entries == part.targets().size(), "Native blueprint omitted an unregistered target material");
        SerializerShape.requireStable(serializerInstance, EntityDataSerializers.COMPOUND_TAG);
        String nativeProblem = BlueprintNetworkBudget.problem(blueprint);
        SerializerShape.requireStable(serializerInstance, EntityDataSerializers.COMPOUND_TAG);
        var nativeEvidence = new LinkedHashMap<String, Object>();
        nativeEvidence.put("ordinal", ordinal); nativeEvidence.put("index", part.index());
        nativeEvidence.put("layoutStageDigest", part.digest()); nativeEvidence.put("blueprintBlockCount", entries);
        nativeEvidence.put("targetCount", part.targets().size()); nativeEvidence.put("nativeRoundTripPassed", nativeProblem == null);
        nativeEvidence.put("problem", nativeProblem); ROUNDTRIPS.add(nativeEvidence);
        // Native rejection is fatal, never a reason to silently adopt a different partition.
        String result = Decision.problem(nativeProblem, part.targets().size());
        var call = Map.<String, Object>of("index", part.index(), "columnCount", part.columns().size(),
                "targetCount", part.targets().size(), "layoutStageDigest", part.digest(), "capAccepts", result == null);
        require(JSON.toJsonTree(call).equals(expected.getAsJsonArray("validatorCalls").get(ordinal)),
                "Native validator proposal/order differs at call " + ordinal);
        CALLS.add(call); return result;
    }

    /** JDK-only decision boundary is executed directly by focused negative regressions. */
    static final class Decision {
        private Decision() {}
        static String problem(String nativeProblem, int targets) {
            if (nativeProblem != null) throw new AssertionError("Native serializer rejected reviewed proposal: " + nativeProblem);
            if (targets < 1 || targets > 572) throw new AssertionError("Unexpected bounded native proposal target count");
            return targets > 64 ? "QA-only reviewed cap-64 partition pressure" : null;
        }
    }

    private static Map<String, Object> stageEvidence(PerimeterStageLayout.Stage part) {
        var stage = new LinkedHashMap<String, Object>();
        stage.put("index", part.index()); stage.put("columns", part.columns()); stage.put("targets", targets(part.targets()));
        stage.put("clearance", part.clearance().stream().sorted().toList()); stage.put("targetCount", part.targets().size());
        stage.put("clearanceCount", part.clearance().size()); stage.put("reservationCount", part.reservation().size());
        stage.put("materials", materials(part.targets())); stage.put("min", coordinates(part.min())); stage.put("max", coordinates(part.max()));
        stage.put("layoutStageDigest", part.digest()); return stage;
    }

    private static Map<String, Object> planEvidence(PerimeterBlueprint.Plan plan) {
        var result = new LinkedHashMap<String, Object>();
        result.put("targetCount", plan.blocks().size()); result.put("clearanceCount", plan.clearance().size());
        result.put("columnCount", plan.columns().size()); result.put("materials", plan.materialCounts());
        result.put("min", coordinates(plan.min())); result.put("max", coordinates(plan.max()));
        result.put("columns", plan.columns().stream().map(column -> new BlockPos(column.base().getX(), 0, column.base().getZ()).asLong()).sorted().toList());
        result.put("targets", targets(plan.blocks())); result.put("clearance", plan.clearance().stream().sorted().toList()); return result;
    }

    private static List<Map<String, Object>> targets(Map<Long, String> targets) {
        return targets.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> Map.<String, Object>of("cell", entry.getKey(), "material", entry.getValue())).toList();
    }
    private static Map<String, Integer> materials(Map<Long, String> targets) {
        var result = new LinkedHashMap<String, Integer>(); targets.values().forEach(material -> result.merge(material, 1, Integer::sum)); return result;
    }
    private static List<Integer> coordinates(BlockPos pos) { return List.of(pos.getX(), pos.getY(), pos.getZ()); }

    private static void requireLossless(PerimeterBlueprint.Plan plan, PerimeterStageLayout.Layout layout) {
        var targets = new LinkedHashMap<Long, String>(); var clearance = new HashSet<Long>(); var reserved = new HashSet<Long>();
        for (var part : layout.stages()) {
            for (var entry : part.targets().entrySet()) require(targets.put(entry.getKey(), entry.getValue()) == null, "Overlapping native target stage");
            for (long cell : part.clearance()) require(clearance.add(cell), "Overlapping native clearance stage");
            for (long cell : part.reservation()) require(reserved.add(cell), "Overlapping native reservation stage");
        }
        var expected = new HashSet<>(plan.blocks().keySet()); expected.addAll(plan.clearance());
        require(targets.equals(plan.blocks()) && clearance.equals(plan.clearance()) && reserved.equals(expected)
                && reserved.size() == 1320, "Lossy native partition union");
    }


    private static NativeLaterStageHelperProof.Proof laterProof(BuilderEntity worker) {
        return NativeLaterStageHelperProof.inspect(worker, accepted, project(worker.getServer().overworld()),
                ConstructionEditLedger.get(worker.getServer().overworld()), helper, ownerId, ledgerGeneration, originalHand, finalizedOffline);
    }
    private static int stagePending(ServerLevel level, int index) {
        Set<Long> left = pending(level); return (int) accepted.stages().get(index).layout().targets().keySet().stream().filter(left::contains).count();
    }
    private static long stagePlacements(UUID actor, int stage) {
        return PLACEMENTS.get(actor).stream().filter(accepted.stages().get(stage).layout().targets()::containsKey).count();
    }
    private static void requireA2(PerimeterProject project) {
        require(project != null && project.state() == PerimeterProject.State.RUNNING && project.activeStage() == 2
                && project.receipts().size() == 2 && project.active() != null, "Fixed unfinished RUNNING A2 window was missed");
    }
    /** Pure fixed-window decisions are executed by Java regressions, never used to alter production timing. */
    static final class CaseWindow {
        static void removal(int stage, int receipts, long alreadyObserved) {
            require(stage == 0 && receipts == 0 && alreadyObserved < 0, "First finalized disk removal already missed unverified A0");
        }
        static void load(int stage, int receipts, long tick, long removed) {
            require(stage == 2 && receipts == 2 && removed >= 0 && tick - removed >= 100,
                    "Holding load preceded fixed A2 or genuine disk-absence minimum");
        }
        static void progress(int stage, int receipts, String state, boolean canceled) {
            require(stage >= 0 && stage <= 2 && receipts >= 0 && receipts <= 2
                    && Set.of("RUNNING", "STAGE_VERIFIED", "WAITING_FOR_NEXT_STAGE", "CANCELED").contains(state), "Unapproved stage/state advancement");
            require(!canceled || stage == 2 && receipts == 2 && state.equals("CANCELED"), "Canceled A2 authority changed");
            require(stage < 2 || receipts == 2 && !state.equals("STAGE_VERIFIED"), "A2 completed before authenticated cancellation");
        }
    }
    /** Captured native frame authority: a later current-stage lookup is never its substitute. */
    static final class CapturedStage {
        static void frame(long before, long after, boolean sameWorld) {
            require(before == after && sameWorld, "Placement bracket crossed a tick/world");
        }
        static void cells(boolean authorized, int stage, boolean helperActor, Set<Long> acceptedTargets,
                          Set<Long> completed, Set<Long> alreadyPlaced, Set<Long> changed) {
            require(authorized && stage >= 0 && stage <= 2 && (!helperActor || stage != 1), "Inactive or offstage native work");
            require(acceptedTargets.containsAll(changed) && completed.containsAll(changed)
                    && Collections.disjoint(alreadyPlaced, changed), "Duplicate/unreceipted/offstage native target");
        }
    }
    private static boolean workAuthority(ServerLevel level, BuilderEntity worker, PerimeterProject project) {
        if (project == null || project.active() == null || project.state() != PerimeterProject.State.RUNNING
                || project.activeStage() > 2 || worker.level() != level || level.getEntity(worker.getUUID()) != worker
                || !worker.isAlive() || worker.isNoAi() || !NativePerimeterCrew.workCommand(worker) || !ownerId.equals(WorkersBridge.readWorkerOwner(worker))) return false;
        var ledger = ConstructionEditLedger.get(level); var data = worker.getPersistentData(); var stage = project.active();
        Entity marker = level.getEntity(stage.areaId());
        if (!(marker instanceof ProtectedBuildArea area) || worker.currentBuildArea != marker || !marker.isAlive()
                || !coordinator.equals(area.reservedBuilderId()) || !ownerId.equals(WorkersBridge.readOwner(marker))
                || !NativeConstructionGuard.commissionPaid(marker) || !ledgerGeneration.equals(ledger.generation())
                || !ledger.matchesProjectReservation(project) || ledger.projectLeaseIndex(projectId) != project.activeStage()
                || !ledger.completeReservation(stage.areaId()) || !ledger.matches(stage.areaId(), stage.reservation().stream().map(BlockPos::of).collect(java.util.stream.Collectors.toSet()))
                || !coordinator.equals(ledger.assignedBuilder(project)) || !PerimeterProjectLink.matches(worker, project, worker.getUUID())
                || !ledgerGeneration.equals(PerimeterProjectLink.ledgerGeneration(data))
                || !data.hasUUID("SiegeProtectedAreaReceipt") || !data.hasUUID("SiegeProtectedLedgerGeneration")
                || !data.hasUUID("SiegePlayerFortificationAreaId") || !data.hasUUID("SiegePlayerFortificationOwner")
                || !stage.areaId().equals(data.getUUID("SiegeProtectedAreaReceipt"))
                || !ledgerGeneration.equals(data.getUUID("SiegeProtectedLedgerGeneration"))
                || !stage.areaId().equals(data.getUUID("SiegePlayerFortificationAreaId"))
                || !ownerId.equals(data.getUUID("SiegePlayerFortificationOwner"))
                || data.getLong("SiegePlayerFortificationPos") != marker.blockPosition().asLong()
                || !ProtectedBuilderHandLifecycle.matches(data, worker.getUUID(), ledger)
                || ProtectedBuilderHandMirror.pending(data) || ProtectedBuilderHandMirror.reviewNeeded(data)) return false;
        if (!PerimeterProjectAuthority.read(marker.getPersistentData()).equals(new PerimeterProjectAuthority.Scope(projectId,
                accepted.header().generation(), accepted.manifestHash(), coreKey, project.activeStage(), stage.digest()))) return false;
        if (worker.getUUID().equals(coordinator)) return true;
        return worker.getUUID().equals(helper) && (project.activeStage() == 0 || project.activeStage() == 2)
                && ledger.crewMember(project, helper) && ledger.crewStageGranted(project, helper)
                && !ledger.suspendedCrewMember(project, helper) && !ledger.rejoiningCrewMember(project, helper)
                && !ledger.knownProjectBuilder(project, helper) && ledger.crewBuilderDestructionReceipt(project, helper) == null
                && accepted.stages().get(0).areaId().equals(ledger.crewAdmissionArea(project, helper))
                && ledger.workerCleanupAreas(project, helper).equals(project.activeStage() == 0 ? Set.of(accepted.stages().get(0).areaId())
                        : Set.of(accepted.stages().get(0).areaId(), accepted.stages().get(2).areaId()))
                && (originalHand == null || originalHand.equals(ledger.handLifecycle(helper)));
    }
    private static void observeStages(MinecraftServer server, ServerLevel level) throws ReflectiveOperationException {
        PerimeterProject project = project(level); if (project == null) return;
        CaseWindow.progress(project.activeStage(), project.receipts().size(), project.state().name(), false);
        var journal = PerimeterStageJournal.get(core(server), project); require(journal != null, "Native stage journal disappeared");
        var ledger = ConstructionEditLedger.get(level); ListTag receipts = project.save().getList("Receipts", 10);
        for (int index = 0; index < project.receipts().size(); index++) {
            var stage = accepted.stages().get(index);
            var expected = new PerimeterProject.StageReceipt(projectId, accepted.header().generation(), accepted.manifestHash(),
                    index, stage.areaId(), stage.digest(), stage.layout().targets().size());
            require(project.receipts().get(index).equals(expected) && stagePending(level, index) == 0, "Stage receipt lost exact paid world proof");
            String raw = receipts.getCompound(index).toString();
            if (!VERIFIED.containsKey(index)) {
                require(finalizedOfflineAt >= 0 && index < 2 && index == VERIFIED.size(), "Stage verified before genuine disk removal or out of order");
                assertHelperOffline(server); assertSuspended(level, project); VERIFIED.put(index, raw);
                boundary(server, "l-stage-" + index + "-verified");
            } else require(VERIFIED.get(index).equals(raw), "First-seen immutable stage receipt changed");
            var attempt = journal.at(index);
            require(attempt != null && attempt.area().equals(stage.areaId()), "Stage journal identity mismatch");
            if (attempt.state() == PerimeterStageJournal.State.RETIRED && !RETIRED.contains(index)) {
                require(index == RETIRED.size() && level.getEntity(stage.areaId()) == null && ledger.retired(stage.areaId())
                        && !ledger.contains(stage.areaId()), "Retired section retained its native marker or child lease");
                assertHelperOffline(server); assertSuspended(level, project); RETIRED.add(index);
                boundary(server, "l-stage-" + index + "-retired");
            }
        }
    }

    /** Close the actual last native frame before the normal END controller can retire/rebind its stage. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeControllerEnd(TickEvent.ServerTickEvent event) {
        if (!ENABLED || !observesNativeLifecycle() || failure != null || accepted == null
                || event.phase != TickEvent.Phase.END || observation == null) return;
        try {
            var level = event.getServer().overworld();
            require(footprintReady(level), "Open native frame lost its loaded footprint before controller END");
            flush(level);
        } catch (Throwable problem) { fail(problem); }
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
