package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.RaidConfig;
import com.devfarinsky.siegeoverhaul.RaidNetwork;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import com.devfarinsky.siegeoverhaul.client.CoreHireScreen;
import com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.core.*;
import com.google.gson.GsonBuilder;
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
public final class NativeActiveHelperQa {
    private static final boolean ENABLED = Boolean.getBoolean("siegeoverhaul.nativeQa")
            && Boolean.getBoolean("siegeoverhaul.nativeQa.activeHelper")
            && "staged-handoff".equals(System.getProperty("siegeoverhaul.nativeQa.mode"));
    private static final long SECOND = 1_000_000_000L, LIMIT_SECONDS = 600;
    private static final String CONFIG = "[Recruits]\nRecruitsChunkLoading = false\n";
    private enum Phase { SETUP, COMMISSION, BOTH_WORK, FIRST_OUT, FIRST_ABSENCE, FIRST_RETURN,
        FIRST_RESUME, SECOND_OUT, EVICT, RELOADING, COORDINATOR_FIRST, LOAD_HELPER, DISK_RETURN,
        DISK_RESUME, CANCEL_MENU, CANCEL_WAIT, STABLE_CANCELED, FINISHED }
    private enum Action { NONE, RELOAD, OPEN_CORE, CANCEL, DONE }
    private static final Map<String, Object> REPORT = new LinkedHashMap<>();
    private static final List<Map<String, Object>> EVENTS = new ArrayList<>(), WORK = new ArrayList<>(), JOINS = new ArrayList<>();
    private static final Map<UUID, BuilderEntity> WORKERS = new LinkedHashMap<>();
    private static final Map<UUID, BuilderWorkGoal> GOALS = new LinkedHashMap<>();
    private static final Map<UUID, Set<Long>> PLACEMENTS = new LinkedHashMap<>();
    private static final Set<UUID> SUPPLIED = new HashSet<>();
    private static final Map<UUID, Integer> GROUNDED = new HashMap<>();
    private static final Map<UUID, NativeActiveHelperTransit.Inventory> DISK_JOINS = new LinkedHashMap<>();
    private static final Map<UUID, Long> DISK_ORDINALS = new HashMap<>();
    private static final Map<BuilderEntity, NativeActiveHelperTransit.Inventory> RAW_LOADS = new IdentityHashMap<>();
    private static final Map<BuilderEntity, PendingMirror> PENDING_LOADS = new IdentityHashMap<>();
    private record PendingMirror(NativeActiveHelperTransit.Inventory armed, boolean split, int rebinds) {}
    private static Phase phase = Phase.SETUP;
    private static volatile Action action = Action.NONE;
    private static volatile Throwable failure;
    private static volatile boolean reloadClient, finished;
    private static long started, phaseAt, sequence, absenceAt, coordinatorBefore, helperBefore, lastEligibility = -1, unavailableAt = -1;
    private static BuilderEntity transferredSource, transferredDestination;
    private static boolean settledEndPending;
    private static volatile boolean finishCleanupScheduled, finishCleanupReady;
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
    private static NativeActiveHelperTransit.Inventory offline, stoppedCoordinator;
    private static CompoundTag stoppedAuthority, loadedAuthority, stoppedLedger, loadedLedger, canceledReceipt;
    private static Set<Long> canceledGeometry;
    private static boolean stopObserved, startObserved, sameStageVerified, diskVerified, cancelSent;
    private static Observation observation;
    private record Observation(BuilderEntity worker, UUID area, long tick, Set<Long> pending,
                               int cobble, int oak, int chestCobble, int chestOak, String state, boolean authorized) {}
    private NativeActiveHelperQa() {}

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (started == 0) started = System.nanoTime();
            require(System.nanoTime() - started < LIMIT_SECONDS * SECOND, "Helper lifecycle exceeded its hard ten-minute cap");
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
            if (reloadClient) {
                if (mc.getSingleplayerServer() == null && mc.screen instanceof TitleScreen) {
                    require(stopObserved, "Native stop snapshot missing"); reloadClient = false;
                    mc.createWorldOpenFlows().loadLevel(new TitleScreen(), NativeStagedHandoffFixture.WORLD);
                }
                return;
            }
            if (mc.player != null) ownerId = mc.player.getUUID();
            Action next = action;
            if (next == Action.NONE) return;
            if (next == Action.DONE) { action = Action.NONE; finish(mc); return; }
            if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
            switch (next) {
                case RELOAD -> {
                    action = Action.NONE; reloadClient = true;
                    // Owner is saved in Survival. No spectator pause or participant relocation.
                    mc.level.disconnect(); mc.clearLevel(); mc.setScreen(new TitleScreen());
                }
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
        if (!ENABLED || finished || failure != null || event.phase != TickEvent.Phase.END || directory == null || ownerId == null) return;
        try {
            var server = event.getServer(); var level = server.overworld();
            long tick = level.getGameTime();
            if (phaseAt == 0) phaseAt = tick;
            require(tick - phaseAt <= (phase == Phase.EVICT ? 1800 : 1200), "Bounded helper phase timed out: " + phase);
            // Close every old-server native tick bracket before any reopen wait. A requested
            // disconnect can leave ordinary ticks between the END stimulus and actual stop.
            if (observation != null) { require(footprintReady(level), "Open native bracket lost its resident footprint"); flush(level); }
            if (phase == Phase.RELOADING && stopObserved && (!startObserved || !DISK_JOINS.containsKey(coordinator)
                    || level.getEntity(coordinator) != WORKERS.get(coordinator) || !footprintReady(level))) return;
            if (accepted != null) require(footprintReady(level), "Observation footprint became unavailable outside actual reopen");
            if (settledEndPending) {
                transit.assertSettled(transferredSource, transferredDestination); settledEndPending = false;
                record("ordinary-end-settled-custody", Map.of("worker", helper.toString(), "dimension", transferredDestination.level().dimension().location().toString()));
            }
            ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
            if (owner == null) { require(phase == Phase.RELOADING || phase == Phase.SETUP, "Owner disappeared outside real reopen"); return; }
            require(owner.isAlive() && !owner.isCreative() && !owner.isSpectator() && !owner.hasPermissions(2)
                    && owner.mayBuild() && owner.serverLevel() == level, "Lost real non-op Survival authority");
            if (accepted != null) {
                treasury.observe(core(server), bankRate(core(server)), tick);
                verifyImmutable(level); conservation(server, owner); verifyClaims(level);
            }
            updateGrounded();
            step(server, level, owner, tick);
        } catch (Throwable problem) { fail(problem); try { if (transit != null) transit.cleanup(event.getServer()); } catch (Throwable cleanup) { REPORT.put("ticketCleanupFailure", cleanup.toString()); } }
    }

    private static void step(MinecraftServer server, ServerLevel level, ServerPlayer owner, long tick) throws Exception {
        PerimeterProject project = accepted == null ? null : project(level);
        switch (phase) {
            case SETUP -> {
                require(!RecruitsServerConfig.RecruitsChunkLoading.get(), "Fresh worker chunk-loading config is not disabled");
                fixture = NativeStagedHandoffFixture.setup(level, owner); observationChunks = observationEnvelope(); coordinator = fixture.builderId();
                remember((BuilderEntity) level.getEntity(coordinator));
                var member = NativeStagedHandoffFixture.spawnSharedHelper(level, owner); helper = member.getUUID(); remember(member);
                transit = new NativeActiveHelperTransit(helper, REPORT); transit.setup(level);
                coreKey = SiegeCore.key(owner);
                REPORT.put("workers", List.of(coordinator.toString(), helper.toString())); REPORT.put("owner", ownerId.toString());
                REPORT.put("auxiliaryIsolationInitial", NativeStagedHandoffFixture.auxiliaryIsolationSnapshot(level, fixture));
                require(FactionBank.balance(core(server)) == 0, "Fresh Treasury was not empty");
                move(tick, Phase.COMMISSION);
            }
            case COMMISSION -> {
                if (!core(server).contains("BankInterestAt") || tick == phaseAt) return;
                commission(level, owner); boundary(server, "commission"); move(tick, Phase.BOTH_WORK);
            }
            case BOTH_WORK -> {
                requireFirstStage(project);
                if (!bothContributed() || SUPPLIED.size() != 2 || !eligible(WORKERS.get(helper), tick)) return;
                transit.ensureHoldingTicket(server); move(tick, Phase.FIRST_OUT);
            }
            case FIRST_OUT -> {
                requireFirstStage(project);
                if (!transit.holdingReady(server) || !eligible(WORKERS.get(helper), tick)) return;
                helperBefore = placements(helper); coordinatorBefore = placements(coordinator);
                boundary(server, "before-first-transfer");
                transfer(WORKERS.get(helper), transit.holdingLevel(server), transit.holdingFeet());
                absenceAt = tick; move(tick, Phase.FIRST_ABSENCE);
            }
            case FIRST_ABSENCE -> {
                requireFirstStage(project);
                if (tick - absenceAt < 100) return;
                assertSuspended(level, project);
                if (tick - absenceAt < 100 || placements(coordinator) <= coordinatorBefore) return;
                if (!eligible(WORKERS.get(helper), tick) || !transit.returnReady(level, WORKERS.get(helper))) return;
                boundary(server, "same-stage-absence"); move(tick, Phase.FIRST_RETURN);
            }
            case FIRST_RETURN -> {
                requireFirstStage(project);
                if (!eligible(WORKERS.get(helper), tick) || !transit.returnReady(level, WORKERS.get(helper))) return;
                transfer(WORKERS.get(helper), level, transit.returnFeet()); transit.releaseHoldingTicket(server);
                boundary(server, "first-native-return"); move(tick, Phase.FIRST_RESUME);
            }
            case FIRST_RESUME -> {
                requireFirstStage(project);
                if (placements(helper) <= helperBefore) return;
                assertActive(level, project); sameStageVerified = true;
                if (!Boolean.TRUE.equals(REPORT.get("sameStageReturnVerified"))) {
                    REPORT.put("sameStageReturnVerified", true); boundary(server, "same-stage-resumed");
                }
                if (!eligible(WORKERS.get(helper), tick)) return;
                transit.ensureHoldingTicket(server); move(tick, Phase.SECOND_OUT);
            }
            case SECOND_OUT -> {
                requireFirstStage(project);
                if (!transit.holdingReady(server) || !eligible(WORKERS.get(helper), tick)) return;
                boundary(server, "before-disk-transfer");
                transfer(WORKERS.get(helper), transit.holdingLevel(server), transit.holdingFeet());
                transit.releaseHoldingTicket(server); absenceAt = tick; move(tick, Phase.EVICT);
            }
            case EVICT -> {
                requireFirstStage(project); transit.assertNoTickets(server);
                if (tick - absenceAt < 100) return;
                assertSuspended(level, project);
                var member = WORKERS.get(helper); var holding = transit.holdingLevel(server);
                if (holding.hasChunkAt(transit.holdingFeet()) || holding.getEntity(helper) != null) return;
                if (unavailableAt < 0) unavailableAt = tick;
                if (offline == null) require(tick - unavailableAt <= 200, "Unavailable helper unload did not finalize within ten seconds");
                if (!member.isRemoved()) return;
                require(member.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK
                        && !member.getRemovalReason().shouldDestroy() && member.getRemovalReason().shouldSave(), "Helper disappeared without native disk unload");
                var finalized = NativeActiveHelperTransit.inventory(member);
                require(offline == null || offline.equals(finalized), "Finalized offline custody changed");
                if (offline == null) {
                    offline = finalized; REPORT.put("finalizedOfflineInventory", inventoryEvidence(offline));
                    REPORT.put("offlineRemovalProof", Map.of("reason", member.getRemovalReason().name(), "shouldDestroy", member.getRemovalReason().shouldDestroy(),
                            "shouldSave", member.getRemovalReason().shouldSave(), "chunkAvailable", holding.hasChunkAt(transit.holdingFeet()),
                            "registered", holding.getEntity(helper) != null, "gameTime", tick, "unavailableAt", unavailableAt));
                }
                if (!eligible(WORKERS.get(coordinator), tick)) return;
                boundary(server, "genuine-helper-disk-unload");
                DISK_JOINS.clear(); DISK_ORDINALS.clear(); stopObserved = startObserved = false;
                move(tick, Phase.RELOADING); action = Action.RELOAD;
            }
            case RELOADING -> {
                if (!stopObserved || !startObserved || !DISK_JOINS.containsKey(coordinator)) return;
                require(stoppedAuthority.equals(loadedAuthority) && stoppedLedger.equals(loadedLedger), "Paid authority/ledger changed before reopened ticks");
                require(sameInventory(stoppedCoordinator, DISK_JOINS.get(coordinator)), "Coordinator cargo changed across disk reload");
                transit.assertNoTickets(server);
                require(!DISK_JOINS.containsKey(helper) && !transit.holdingLevel(server).hasChunkAt(transit.holdingFeet())
                        && transit.holdingLevel(server).getEntity(helper) == null, "Helper loaded before coordinator-first evidence");
                coordinatorBefore = placements(coordinator); absenceAt = tick;
                boundary(server, "coordinator-first-disk-reopen"); move(tick, Phase.COORDINATOR_FIRST);
            }
            case COORDINATOR_FIRST -> {
                requireFirstStage(project); transit.assertNoTickets(server); assertSuspended(level, project);
                require(!DISK_JOINS.containsKey(helper) && !transit.holdingLevel(server).hasChunkAt(transit.holdingFeet()), "Helper prematurely loaded during coordinator-only progress");
                if (tick - absenceAt < 100 || placements(coordinator) <= coordinatorBefore) return;
                boundary(server, "coordinator-native-progress-while-helper-offline");
                transit.ensureHoldingTicket(server); move(tick, Phase.LOAD_HELPER);
            }
            case LOAD_HELPER -> {
                requireFirstStage(project);
                if (!transit.holdingReady(server) || !(transit.holdingLevel(server).getEntity(helper) instanceof BuilderEntity member)) return;
                require(DISK_JOINS.containsKey(helper) && DISK_ORDINALS.get(coordinator) < DISK_ORDINALS.get(helper)
                        && sameInventory(offline, DISK_JOINS.get(helper)), "Actual helper disk join/order/cargo differs from finalized offline evidence");
                NativeActiveHelperTransit.assertUnique(server, helper, member); WORKERS.put(helper, member);
                if (!eligible(member, tick) || !transit.returnReady(level, member)) return;
                helperBefore = placements(helper); boundary(server, "later-helper-disk-join"); move(tick, Phase.DISK_RETURN);
            }
            case DISK_RETURN -> {
                requireFirstStage(project);
                if (!eligible(WORKERS.get(helper), tick) || !transit.returnReady(level, WORKERS.get(helper))) return;
                transfer(WORKERS.get(helper), level, transit.returnFeet()); offline = null; transit.releaseHoldingTicket(server);
                boundary(server, "disk-native-return");
                move(tick, Phase.DISK_RESUME);
            }
            case DISK_RESUME -> {
                requireFirstStage(project);
                if (placements(helper) <= helperBefore) return;
                assertActive(level, project); transit.assertNoTickets(server); diskVerified = true;
                REPORT.put("helperDiskEvictionVerified", true); REPORT.put("coordinatorFirstDiskOrderVerified", true);
                REPORT.put("postDiskHelperPlacementVerified", true); boundary(server, "disk-return-resumed");
                move(tick, Phase.CANCEL_MENU); action = Action.OPEN_CORE;
            }
            case CANCEL_MENU -> {
                requireFirstStage(project);
                if (!(owner.containerMenu instanceof CoreHireMenu menu)) return;
                require(menu.stillValid(owner), "Authenticated core menu is invalid");
                action = Action.CANCEL; move(tick, Phase.CANCEL_WAIT);
            }
            case CANCEL_WAIT -> {
                if (!cancelSent) return;
                var terminal = PerimeterProjectAuthority.snapshot(level, coreKey).terminal(projectId);
                if (terminal == null) return;
                verifyCanceled(level); canceledReceipt = terminal.save(); canceledGeometry = pending(level);
                transit.assertNoTickets(server); boundary(server, "loaded-workers-canceled"); move(tick, Phase.STABLE_CANCELED);
            }
            case STABLE_CANCELED -> {
                verifyCanceled(level);
                require(pending(level).equals(canceledGeometry)
                        && PerimeterProjectAuthority.snapshot(level, coreKey).terminal(projectId).save().equals(canceledReceipt), "Canceled work changed");
                if (tick - phaseAt < 100) return;
                require(sameStageVerified && diskVerified && bothContributed() && SUPPLIED.size() == 2 && PENDING_LOADS.isEmpty() && RAW_LOADS.isEmpty(), "Incomplete bounded lifecycle proof");
                REPORT.put("cancellationStableTicks", tick - phaseAt); REPORT.put("boundedScenarioVerified", true);
                REPORT.put("placementCounts", counts()); REPORT.put("retainedPlacedBlocks", fixture.plan().blocks().size() - pending(level).size());
                REPORT.put("paidFee", 64); REPORT.put("wholePlanCompleted", false);
                REPORT.put("finalMaterials", materialCensus(server, owner));
                REPORT.put("auxiliaryIsolationFinal", NativeStagedHandoffFixture.auxiliaryIsolationSnapshot(level, fixture));
                move(tick, Phase.FINISHED); action = Action.DONE;
            }
            case FINISHED -> { }
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
        layout = PerimeterStageLayout.partition(plan, part -> {
            String nativeProblem = BlueprintNetworkBudget.problem(TerritoryFortification.blueprint(part.targets(), part.min(), part.max()));
            return nativeProblem != null ? nativeProblem : part.targets().size() > 192 ? "QA-only representative target cap" : null;
        });
        PerimeterStageLayout.validate(plan, layout);
        require(layout.stages().size() > 1 && layout.stages().get(0).targets().size() == 139, "Reviewed first-stage partition changed");
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
        REPORT.put("materialCounts", Map.of("minecraft:cobblestone", 352, "minecraft:oak_planks", 220));
        REPORT.put("syntheticPartition", true); REPORT.put("qaSectionTargetCap", 192);
        REPORT.put("commissionPath", "Explicit legacy 572-target plan through public protected admission and one real Treasury debit");
        transit.assertNoTickets(level.getServer());
    }

    private static void transfer(BuilderEntity original, ServerLevel destination, BlockPos feet) throws Exception {
        if (original.level() == destination.getServer().overworld()) assertActive(destination.getServer().overworld(), project(destination.getServer().overworld()));
        var next = transit.transfer(original, destination, feet);
        transferredSource = original; transferredDestination = next; settledEndPending = true;
        WORKERS.put(helper, next); GROUNDED.put(helper, 0);
        require(GOALS.containsKey(helper) && GOALS.get(helper).builderEntity == next, "Native replacement goal was not observed at join");
    }

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
        if (!ProtectedBuilderHandLifecycle.matches(worker.getPersistentData(), worker.getUUID(), ledger)
                || !NativeConstructionGuard.validHandReceipt(worker.getPersistentData(), ledger)) reasons.add("exact protected hand provenance");
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
        var ledger = ConstructionEditLedger.get(level); var worker = WORKERS.get(helper);
        require(level.getEntity(helper) == worker && worker.isAlive() && !worker.isNoAi()
                && ledger.crewMember(project, helper) && ledger.crewStageGranted(project, helper)
                && !ledger.suspendedCrewMember(project, helper) && !ledger.rejoiningCrewMember(project, helper)
                && NativePerimeterProjects.participantLinkMatches(worker, project)
                && worker.currentBuildArea == level.getEntity(project.active().areaId())
                && !ProtectedBuilderHandMirror.pending(worker.getPersistentData())
                && !ProtectedBuilderHandMirror.reviewNeeded(worker.getPersistentData()), "Returned helper lacks fully restored exact production authority");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void joined(EntityJoinLevelEvent event) {
        if (!ENABLED || finished || fixture == null || event.getLevel().isClientSide()) return;
        try {
            if (transit != null) transit.onJoin(event);
            if (!(event.getEntity() instanceof BuilderEntity worker) || !PLACEMENTS.containsKey(worker.getUUID())) return;
            long ordinal = ++sequence;
            var value = NativeActiveHelperTransit.inventory(worker);
            require(!event.isCanceled() && RAW_LOADS.put(worker, value) == null, "Canceled or repeated raw worker admission");
            JOINS.add(Map.of("sequence", ordinal, "worker", worker.getUUID().toString(), "dimension", event.getLevel().dimension().location().toString(),
                    "loadedFromDisk", event.loadedFromDisk(), "inventory", inventoryEvidence(value)));
            if (event.loadedFromDisk() && phase.ordinal() >= Phase.RELOADING.ordinal()) {
                require(!DISK_JOINS.containsKey(worker.getUUID()), "Duplicate worker disk join");
                DISK_JOINS.put(worker.getUUID(), value); DISK_ORDINALS.put(worker.getUUID(), ordinal);
            }
            // Entity-local goal/identity reads only. World registration checks wait for ordinary END.
            remember(worker);
        } catch (Throwable problem) { fail(problem); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void joinedAfterProduction(EntityJoinLevelEvent event) {
        if (!ENABLED || finished || transit == null || event.getLevel().isClientSide()) return;
        try {
            transit.onJoined(event);
            if (!(event.getEntity() instanceof BuilderEntity worker) || !PLACEMENTS.containsKey(worker.getUUID())) return;
            var raw = RAW_LOADS.remove(worker); require(raw != null && !event.isCanceled(), "Missing uncanceled raw load custody");
            require(raw.beforeItemSlot() == -1 && !ProtectedBuilderHandMirror.pending(raw.persistent())
                    && !ProtectedBuilderHandMirror.reviewNeeded(raw.persistent()), "Raw load did not preserve naturally closed hands");
            var expectedData = raw.persistent(); expectedData.putBoolean("SiegeProtectedHandRefreshPending", true); // Comparison copy only.
            var armed = new NativeActiveHelperTransit.Inventory(raw.slots(), raw.main(), raw.off(), raw.beforeItemSlot(), expectedData);
            require(armed.equals(NativeActiveHelperTransit.inventory(worker)), "Production admission changed more than hand-arm receipt");
            boolean split = !worker.getMainHandItem().isEmpty() && worker.getMainHandItem() != worker.getInventory().getItem(5);
            require(PENDING_LOADS.put(worker, new PendingMirror(armed, split, ProtectedBuilderHandMirror.rebindCount(raw.persistent()))) == null,
                    "Repeated pending load custody");
            record("load-mirror-armed", Map.of("worker", worker.getUUID().toString(), "raw", inventoryEvidence(raw),
                    "armed", inventoryEvidence(armed), "split", split));
        } catch (Throwable problem) { fail(problem); }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void left(EntityLeaveLevelEvent event) {
        if (!ENABLED || finished || transit == null || event.getLevel().isClientSide()) return;
        try { transit.onLeave(event); } catch (Throwable problem) { fail(problem); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void stopping(ServerStoppingEvent event) {
        if (!ENABLED || transit == null) return;
        try {
            transit.cleanup(event.getServer()); transit.assertNoTickets(event.getServer());
            if (phase != Phase.RELOADING || failure != null) return;
            var level = event.getServer().overworld(); flush(level);
            require(offline != null && WORKERS.get(helper).getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK,
                    "Saved while helper lacked confirmed offline custody");
            stoppedCoordinator = NativeActiveHelperTransit.inventory(WORKERS.get(coordinator));
            stoppedAuthority = authority(event.getServer()); stoppedLedger = ConstructionEditLedger.get(level).save(new CompoundTag()).copy();
            conservation(event.getServer(), null); stopObserved = true;
            record("actual-server-stop", Map.of("authority", stoppedAuthority.toString(), "ledger", stoppedLedger.toString(),
                    "coordinator", inventoryEvidence(stoppedCoordinator), "offlineHelper", inventoryEvidence(offline)));
        } catch (Throwable problem) { fail(problem); }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void started(ServerStartedEvent event) {
        if (!ENABLED || phase != Phase.RELOADING || !stopObserved) return;
        try {
            loadedAuthority = authority(event.getServer());
            loadedLedger = ConstructionEditLedger.get(event.getServer().overworld()).save(new CompoundTag()).copy();
            transit.assertNoTickets(event.getServer()); startObserved = true;
            record("actual-server-reopen-before-ticks", Map.of("authority", loadedAuthority.toString(), "ledger", loadedLedger.toString()));
        } catch (Throwable problem) { fail(problem); }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void living(LivingEvent.LivingTickEvent event) {
        if (!ENABLED || finished || failure != null || accepted == null
                || !(event.getEntity().level() instanceof ServerLevel level) || level != level.getServer().overworld()) return;
        try {
            if (!footprintReady(level)) {
                require(phase == Phase.RELOADING && observation == null, "Living observation lost its loaded footprint"); return;
            }
            flush(level);
            if (!(event.getEntity() instanceof BuilderEntity worker) || !PLACEMENTS.containsKey(worker.getUUID()) || !worker.isAlive()) return;
            PerimeterProject project = project(level);
            if (project == null || project.active() == null) return;
            observation = new Observation(worker, project.active().areaId(), level.getGameTime(), pending(level),
                    workerStock(worker, Items.COBBLESTONE), workerStock(worker, Items.OAK_PLANKS),
                    chestStock(level, Items.COBBLESTONE), chestStock(level, Items.OAK_PLANKS), String.valueOf(GOALS.get(worker.getUUID()).state),
                    NativePerimeterProjects.participantLinkMatches(worker, project)
                            && project.header().owner().equals(WorkersBridge.readWorkerOwner(worker)));
        } catch (Throwable problem) { fail(problem); }
    }

    private static void flush(ServerLevel level) {
        Observation before = observation; observation = null;
        if (before == null) return;
        require(before.tick() == level.getGameTime() && before.worker().level() == level, "Placement bracket crossed a tick/world");
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
            require(level.getEntity(before.area()) instanceof ProtectedBuildArea, "Placement lost native receipt");
            var completed = Arrays.stream(level.getEntity(before.area()).getPersistentData().getCompound("SiegeProtectedConstructionV1")
                    .getLongArray("Completed")).boxed().collect(java.util.stream.Collectors.toSet());
            for (long cell : changed) {
                require(accepted.stages().get(0).layout().targets().containsKey(cell) && completed.contains(cell)
                        && PLACEMENTS.values().stream().noneMatch(cells -> cells.contains(cell)), "Duplicate/unreceipted/offstage native target");
                PLACEMENTS.get(before.worker().getUUID()).add(cell);
            }
        }
        boolean supply = cobble > before.cobble() || oak > before.oak();
        if (supply) {
            require(before.authorized() && changed.isEmpty() && cobble - before.cobble() == before.chestCobble() - chestCobble
                    && oak - before.oak() == before.chestOak() - chestOak, "Supply lacks actual finite chest withdrawal");
            SUPPLIED.add(before.worker().getUUID());
        }
        if (changed.isEmpty() && !supply) require(cobble == before.cobble() && oak == before.oak()
                && chestCobble == before.chestCobble() && chestOak == before.chestOak(), "Unexplained material change outside native supply/placement");
        if (!changed.isEmpty() || supply) {
            var work = new LinkedHashMap<String, Object>(); work.put("sequence", ++sequence);
            work.put("worker", before.worker().getUUID().toString()); work.put("gameTime", before.tick()); work.put("area", before.area().toString());
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
        Path defaults = directory.resolve("defaultconfigs");
        require(!Files.isSymbolicLink(defaults), "Symlinked fresh default config directory"); Files.createDirectories(defaults);
        // Synthetic precommission world setup only. Never edit an existing world's server config.
        Files.writeString(defaults.resolve("recruits-server.toml"), CONFIG, java.nio.file.StandardOpenOption.CREATE_NEW);
        REPORT.put("loadedApiParity", NativeActiveHelperApiParity.verify(evidence));
        REPORT.put("sourceCommit", Files.readString(evidence.resolve("source-commit.txt")).trim());
        REPORT.put("sourceTree", Files.readString(evidence.resolve("source-tree.txt")).trim());
        REPORT.put("startedUtc", Instant.now().toString()); REPORT.put("mode", "active-helper-same-stage-disk");
        REPORT.put("formatVersion", 1); REPORT.put("totalLimitSeconds", LIMIT_SECONDS);
        REPORT.put("phaseLimitTicks", 1200); REPORT.put("evictionLimitTicks", 1800); REPORT.put("finalizationLimitTicks", 200);
        REPORT.put("stableGroundedEndTicks", 5); REPORT.put("absenceObservationTicks", 100);
        REPORT.put("scope", "Separately opted-in synthetic helper dimension transit and genuine disk eviction; same-stage return, real coordinator-first reopen and later helper disk join, native rebind/placement, loaded-worker cancellation");
        REPORT.put("notCovered", List.of("Stage-skipped return", "Cancellation while helper is absent", "Whole-site chunk eviction",
                "Whole-project completion", "Dedicated-client networking", "Normal review-menu commission", "Arbitrary add-ons or transformed-method equivalence"));
        REPORT.put("stageSkippedReturnVerified", false); REPORT.put("canceledWhileAbsentVerified", false);
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
                    && project.clearanceBefore().equals(accepted.clearanceBefore()) && project.payment().equals(accepted.payment())
                    && ConstructionEditLedger.get(level).assignedBuilder(project).equals(coordinator), "Immutable paid project or assigned coordinator changed");
            if (phase != Phase.CANCEL_WAIT) requireFirstStage(project);
            else require(project.activeStage() == 0 && project.receipts().isEmpty(), "Cancellation advanced first stage");
        } else require(phase == Phase.CANCEL_WAIT || phase == Phase.STABLE_CANCELED || phase == Phase.FINISHED,
                "Project disappeared before authenticated cancellation");
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
                && terminal.projectId().equals(projectId) && terminal.manifestHash().equals(accepted.manifestHash())
                && terminal.owner().equals(ownerId) && terminal.builder().equals(coordinator)
                && terminal.activeStage() == 0 && terminal.verifiedStages() == 0 && terminal.totalTargetCount() == 572
                && terminal.claimChunkCount() == 1 && terminal.payment().equals(accepted.payment())
                && terminal.stages().equals(accepted.stages().stream().map(part -> new PerimeterTerminalReceipt.Stage(
                        part.index(), part.areaId(), part.digest(), part.layout().targets().size())).toList())
                && ledger.sameGeneration(terminal.cleanup().ledgerGeneration()), "Terminal cancellation identity/payment/cleanup mismatch");
        require(!ledger.contains(projectId) && accepted.stages().stream().noneMatch(part -> ledger.contains(part.areaId()))
                && level.getEntitiesOfClass(ProtectedBuildArea.class, NativeStagedHandoffFixture.BOUNDS, Entity::isAlive).isEmpty(),
                "Canceled project retained native markers or reservations");
        for (var worker : WORKERS.values()) require(level.getEntity(worker.getUUID()) == worker && worker.currentBuildArea == null
                && !WorkersBridge.hasActiveBuildArea(worker) && !PerimeterProjectLink.reserved(worker)
                && !worker.getPersistentData().contains(com.devfarinsky.siegeoverhaul.ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID)
                && ProtectedInventoryCleanup.read(worker.getPersistentData()).isEmpty() && worker.neededItems.isEmpty()
                && ledger.unresolvedCrewProject(worker.getUUID()) == null, "Loaded cancellation left worker scope or cleanup");
        require(!pending(level).isEmpty() && pending(level).size() < 572, "Canceled partial work was lost or whole plan completed");
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
                        && ProtectedBuilderHandLifecycle.matches(worker.getPersistentData(), worker.getUUID(), ledger)
                        && NativeConstructionGuard.validHandReceipt(worker.getPersistentData(), ledger)
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
        for (var worker : WORKERS.values()) workers.put(worker.getUUID().toString(), Map.of(
                "dimension", worker.level().dimension().location().toString(), "removed", worker.isRemoved(),
                "removalReason", String.valueOf(worker.getRemovalReason()), "inventory", inventoryEvidence(NativeActiveHelperTransit.inventory(worker)),
                "nativeState", String.valueOf(GOALS.get(worker.getUUID()).state), "entityTick", worker.tickCount,
                "handPending", ProtectedBuilderHandMirror.pending(worker.getPersistentData()), "handRebindCount", ProtectedBuilderHandMirror.rebindCount(worker.getPersistentData()),
                "mainAliasesSlot5", worker.getMainHandItem() == worker.getInventory().getItem(5)));
        var data = new LinkedHashMap<String, Object>(); data.put("workers", workers); data.put("placementCounts", counts());
        data.put("pendingTargets", pending(level).size()); data.put("claims", verifyClaims(level));
        data.put("authority", authority(server).toString()); data.put("ledger", ledger.save(new CompoundTag()).toString());
        data.put("gameTime", level.getGameTime()); data.put("ownerMode", "non-op Survival");
        data.put("materials", materialCensus(server, server.getPlayerList().getPlayer(ownerId)));
        if (project != null) {
            data.put("projectState", project.state().name()); data.put("stage", project.activeStage());
            data.put("helperActive", ledger.crewMember(project, helper)); data.put("helperGranted", ledger.crewStageGranted(project, helper));
            data.put("helperSuspended", ledger.suspendedCrewMember(project, helper)); data.put("helperRejoining", ledger.rejoiningCrewMember(project, helper));
        }
        record(kind, data);
    }
    private static void move(long tick, Phase next) { phase = next; phaseAt = tick; record("phase", Map.of("phase", next.name(), "gameTime", tick)); }
    private static void record(String kind, Map<String, ?> data) {
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
        finished = true;
        try {
            if (mc.level != null && evidence != null) screenshot(mc, failure == null ? "04-helper-lifecycle-complete.png" : "failure-active-helper.png");
        } catch (Throwable problem) { fail(problem); }
        REPORT.put("status", failure == null && Boolean.TRUE.equals(REPORT.get("boundedScenarioVerified")) ? "passed" : "failed");
        REPORT.put("phase", phase.name()); REPORT.put("finishedUtc", Instant.now().toString());
        REPORT.put("elapsedSeconds", (System.nanoTime() - started) / (double) SECOND); REPORT.put("screenshots", List.copyOf(SHOTS));
        if (treasury != null) REPORT.put("treasuryAccounting", treasury.evidence());
        if (failure != null) REPORT.put("failure", failure.toString());
        try { if (evidence != null) Files.writeString(evidence.resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(REPORT)); }
        catch (Exception problem) { com.devfarinsky.siegeoverhaul.FactionLogger.LOG.error("Cannot write helper QA evidence", problem); }
        mc.stop();
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
