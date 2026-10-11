package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.FactionLogger;
import com.devfarinsky.siegeoverhaul.client.CoreHireScreen;
import com.devfarinsky.siegeoverhaul.core.CoreHireMenu;
import com.devfarinsky.siegeoverhaul.core.PerimeterProject;
import com.devfarinsky.siegeoverhaul.core.PerimeterProjectStore;
import com.devfarinsky.siegeoverhaul.core.PerimeterStageJournal;
import com.devfarinsky.siegeoverhaul.core.PerimeterStageLayout;
import com.devfarinsky.siegeoverhaul.core.PerimeterTerminalReceipt;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.BlockHitResult;
import com.devfarinsky.siegeoverhaul.ModConstants;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import com.devfarinsky.siegeoverhaul.compat.WorkersConstructionView;
import com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge;
import com.devfarinsky.siegeoverhaul.core.PerimeterTerritory;
import com.talhanation.workers.entities.ai.BuilderWorkGoal;
import com.talhanation.workers.entities.ai.GetNeededItemsFromStorage;
import com.devfarinsky.siegeoverhaul.core.FactionBank;
import com.devfarinsky.siegeoverhaul.core.PerimeterConstruction;
import com.devfarinsky.siegeoverhaul.core.PerimeterReviewFingerprint;
import com.devfarinsky.siegeoverhaul.core.TerritoryFortification;
import com.devfarinsky.siegeoverhaul.core.SiegeCore;
import com.devfarinsky.siegeoverhaul.items.ModItems;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import com.talhanation.recruits.ClaimEvents;
import com.talhanation.recruits.world.RecruitsClaim;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.entities.workarea.StorageArea;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import org.lwjgl.glfw.GLFW;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.opengl.GL11;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Bounded representative handoff: synthetic partition, real native work/restarts and authenticated cancellation. */
@Mod.EventBusSubscriber(modid = SiegeOverhaul.MOD_ID, value = Dist.CLIENT)
public final class NativeStagedHandoffQa {
    private static final boolean ENABLED = Boolean.getBoolean("siegeoverhaul.nativeQa")
            && "staged-handoff".equals(System.getProperty("siegeoverhaul.nativeQa.mode"));
    private static final boolean REPLACE_BUILDER = Boolean.getBoolean("siegeoverhaul.nativeQa.builderReplacement");
    private static final boolean SHARED_CREW = Boolean.getBoolean("siegeoverhaul.nativeQa.sharedCrew");
    private static final int SECTION_TARGET_CAP = REPLACE_BUILDER || SHARED_CREW ? 192 : 96;
    private static UUID originalCoordinator, sharedHelper;
    private static boolean sharedDeathRequested, sharedDeathDropsVerified, sharedPromotionVerified, sharedReloadVerified;
    private static long sharedDeathTick, sharedDeathPlaced;
    private static final Map<UUID, BuilderWorkGoal> CREW_GOALS = new LinkedHashMap<>();
    private static final Map<UUID, GetNeededItemsFromStorage> CREW_STORAGE = new LinkedHashMap<>();
    private static final Map<UUID, Set<Long>> CREW_PLACEMENTS = new LinkedHashMap<>();
    private static final List<Map<String, Object>> CREW_EVENTS = new ArrayList<>();
    private static final Set<UUID> CREW_SUPPLIED = new java.util.HashSet<>();
    private static final Map<UUID, Cargo> LOADED_CREW = new LinkedHashMap<>();
    private static Map<UUID, Integer> crewBeforeReload = Map.of();
    private static WorkObservation workObservation;
    private record Cargo(List<CompoundTag> slots, CompoundTag main, CompoundTag off) {}
    private record WorkObservation(BuilderEntity worker, UUID area, int stage, long gameTime,
                                   Set<Long> pending, int cobble, int oak, int chestCobble, int chestOak,
                                   int looseCobble, int looseOak, int ownerCobble, int ownerOak,
                                   boolean ownerPresent, boolean deathTransferKnown,
                                   boolean participating, String nativeState) {}
    private static boolean replacementSpawned, replacementVerified;
    private static long replacementTick, replacementPlaced;
    private static UUID deadBuilder;
    private static final long SECOND = 1_000_000_000L, CONSTRUCTION_SECONDS = 9 * 60, TOTAL_SECONDS = 10 * 60;
    private static final Map<String, Object> REPORT = new LinkedHashMap<>();
    private static final List<Map<String, Object>> SAMPLES = new ArrayList<>(), RESTARTS = new ArrayList<>(), AREA_JOINS = new ArrayList<>(), KEYBOARD = new ArrayList<>();
    private static final List<String> CHECKS = new ArrayList<>(), SHOTS = new ArrayList<>();
    private static NativeStagedHandoffFixture.Fixture fixture;
    private static CompletableFuture<Action> pending;
    private static Path directory, evidence;
    private static UUID playerId, projectId, jobId;
    private static String coreKey, reloadKind, hudFaction;
    private static long started, constructionStarted, lastProgress, lastSampleTick = -1, placedBefore = -1, stageTick;
    private static long captureRequested, pauseStarted, pausedNanos, lastStableTick = -1, escapeObservedTick = -1;
    private static int stableTicks;
    private static Throwable observerFailure;
    private static int clientPhase, stage, renderFrames, captureFrame, menuId, menuUiStage;
    private static boolean finished, finishing, midRestart, betweenRestart, canceledRestart, restartRequested;
    private static Map<Long, BlockState> canceledGeometry;
    private static long nextStagePlaced;
    private static CompoundTag canceledReceipt;
    private static Throwable failure, stoppingFailure, loadFailure;
    private static String capture;
    private static PerimeterProject acceptedProject;
    private static PerimeterStageLayout.Layout reviewedLayout;
    private static BlockPos originalMarker;
    private static Map<Long, BlockState> pausedGeometry;
    private static BuilderWorkGoal liveBuildGoal;
    private static GetNeededItemsFromStorage liveStorageGoal;
    private static NativeQaTreasury treasuryObserver;
    private static StopSnapshot stopped;
    private static CompoundTag loadedAuthority;
    private static List<CompoundTag> loadedCargo;
    private static CompoundTag loadedMain, loadedOff;
    private static String lastTransition = "";
    private record StopSnapshot(Map<Long, BlockState> geometry, List<List<CompoundTag>> chests,
                                List<CompoundTag> cargo, CompoundTag main, CompoundTag off,
                                CompoundTag authority, CompoundTag recipe, UUID ledgerGeneration,
                                Set<Long> pendingCells, long placed, long gameTime, long passiveCredits,
                                PerimeterProject.State state, int activeStage, Map<UUID, Cargo> crewCargo) {}
    private enum Action { NONE, OPEN_CORE, CANCEL_MENU, CAPTURE_COMMISSION, RELOAD, CAPTURE_FINAL, DONE }
    private NativeStagedHandoffQa() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (started == 0) started = System.nanoTime();
            if (System.nanoTime() - started >= TOTAL_SECONDS * SECOND) {
                if (failure == null) failure = new AssertionError("Representative handoff client exceeded the hard ten-minute cap");
                finish(mc); return;
            }
            if (finishing) { if (capture == null || System.nanoTime() - captureRequested > 20 * SECOND) finish(mc); return; }
            if (capture != null) return;
            if (clientPhase == 0) {
                if (!(mc.screen instanceof TitleScreen)) return;
                initialize(mc);
                require(!Files.exists(directory.resolve("saves").resolve(NativeStagedHandoffFixture.WORLD)), "Refusing an existing staged QA world");
                mc.options.renderDistance().set(6); mc.options.simulationDistance().set(6);
                mc.options.guiScale().set(2); mc.options.pauseOnLostFocus = false; mc.resizeDisplay();
                GameRules rules = new GameRules();
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
                clientPhase = 1;
                mc.createWorldOpenFlows().createFreshLevel(NativeStagedHandoffFixture.WORLD,
                        new LevelSettings(NativeStagedHandoffFixture.WORLD, GameType.SURVIVAL, false, Difficulty.PEACEFUL,
                                false, rules, WorldDataConfiguration.DEFAULT), new WorldOptions(20261007L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions());
                return;
            }
            if (clientPhase == 2) {
                if (mc.getSingleplayerServer() != null || !(mc.screen instanceof TitleScreen)) return;
                require(stoppingFailure == null && stopped != null, "Final stopping snapshot unavailable: " + stoppingFailure);
                clientPhase = 1; mc.createWorldOpenFlows().loadLevel(new TitleScreen(), NativeStagedHandoffFixture.WORLD); return;
            }
            if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null
                    || mc.screen != null && !(mc.screen instanceof CoreHireScreen) && !(mc.screen instanceof ConfirmScreen)) return;
            if (playerId == null) playerId = mc.player.getUUID();
            if (pending != null) {
                if (!pending.isDone()) return;
                Action action = pending.join(); pending = null;
                switch (action) {
                    case OPEN_CORE -> {
                        aim(mc, Vec3.atCenterOf(NativeStagedHandoffFixture.CORE));
                        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                                new BlockHitResult(Vec3.atCenterOf(NativeStagedHandoffFixture.CORE).add(0, .5, 0), Direction.UP,
                                        NativeStagedHandoffFixture.CORE, false));
                    }
                    case CANCEL_MENU -> { if (!cancelThroughMenu(mc)) { pending = CompletableFuture.completedFuture(action); return; } }
                    case CAPTURE_COMMISSION -> {
                        aim(mc, new Vec3(128, 67, 0)); requestCapture("01-direct-server-commission.png"); return;
                    }
                    case RELOAD -> { mc.level.disconnect(); mc.clearLevel(); mc.setScreen(new TitleScreen()); clientPhase = 2; return; }
                    case CAPTURE_FINAL -> {
                        aim(mc, new Vec3(128, 67, 0)); requestCapture("04-canceled-after-reopen.png"); return;
                    }
                    case DONE -> { finishing = true; finish(mc); return; }
                    default -> {}
                }
            }
            CompletableFuture<Action> next = new CompletableFuture<>(); pending = next;
            var server = mc.getSingleplayerServer();
            server.execute(() -> {
                ServerLevel level = server.overworld(); ServerPlayer owner = server.getPlayerList().getPlayer(playerId);
                try { next.complete(step(level, owner)); }
                catch (Throwable problem) {
                    try { REPORT.put("blockerDiagnostics", diagnostics(level, owner, true)); }
                    catch (Throwable extra) { REPORT.put("diagnosticFailure", extra.toString()); }
                    next.completeExceptionally(problem);
                }
            });
        } catch (Throwable problem) { fail(mc, problem); }
    }

    private static Action step(ServerLevel level, ServerPlayer owner) throws Exception {
        require(owner != null && owner.isAlive(), "Actual owner missing/dead");
        long now = level.getGameTime();
        require(observerFailure == null, "Native closed-lifecycle observation failed: " + observerFailure);
        if (treasuryObserver != null) observeTreasury(level, core(owner));
        if (stage != 5 && stage != 12 && stage > 0) require(now - stageTick < 2400, "Bounded setup/reload phase timed out: " + stage);
        PerimeterProject project = projectId == null ? null : project(level);
        jobId = project != null && project.active() != null ? project.active().areaId() : null;
        switch (stage) {
            case 0 -> {
                fixture = NativeStagedHandoffFixture.setup(level, owner);
                if (SHARED_CREW) {
                    originalCoordinator = fixture.builderId();
                    rememberCrew(builder(level));
                    var helper = NativeStagedHandoffFixture.spawnSharedHelper(level, owner);
                    sharedHelper = helper.getUUID(); rememberCrew(helper);
                    var initialHelperCargo = new ArrayList<Map<String, Object>>();
                    for (int slot = 6; slot < helper.getInventory().getContainerSize(); slot++) {
                        var value = new LinkedHashMap<>(stack(helper.getInventory().getItem(slot)));
                        value.put("slot", slot); initialHelperCargo.add(value);
                    }
                    REPORT.put("sharedCrewInitialHelperKit", Map.of("worker", sharedHelper.toString(),
                            "scope", "Bread-only helper on reviewed clear terrain; coordinator retains its normal tools",
                            "beforeCommission", projectId == null, "cargo", initialHelperCargo));
                    require(level.getEntitiesOfClass(BuilderEntity.class, NativeStagedHandoffFixture.BOUNDS,
                            worker -> owner.getUUID().equals(WorkersBridge.readWorkerOwner(worker))).size() == 2,
                            "Shared fixture must have exactly two eligible owned builders");
                    REPORT.put("sharedCrewBuilders", List.of(originalCoordinator.toString(), sharedHelper.toString()));
                    conservation(level, owner);
                }
                coreKey = SiegeCore.key(owner); liveBuildGoal = fixture.buildGoal(); liveStorageGoal = fixture.storageGoal();
                verifyCurrentTerritory(level, NativeStagedHandoffFixture.TERRITORY);
                require(balance(owner) == 0, "Fresh Treasury was not empty");
                advance(now, 1); return Action.NONE;
            }
            case 1 -> {
                // RaidEvents settles real faction banks every 20 ordinary END ticks. The direct
                // commission must wait for that listener, not manufacture its durable clock.
                require(now - stageTick <= 100, "Real Treasury interest clock did not initialize within five seconds");
                var treasury = core(owner);
                if (now <= stageTick || !treasury.contains("BankInterestAt", net.minecraft.nbt.Tag.TAG_LONG)) return Action.NONE;
                long interestAt = treasury.getLong("BankInterestAt");
                require(interestAt >= stageTick && interestAt <= now && balance(owner) == 0,
                        "Fresh Treasury clock or pre-funding balance is not authoritative");
                REPORT.put("treasuryClockSetup", Map.of("ordinaryWaitTicks", now - stageTick,
                        "interestAt", interestAt, "fundingGameTime", now));
                FactionBank.credit(core(owner), 2000); RaidSavedData.get(owner.server).setDirty();
                var prepared = PerimeterConstruction.prepare(owner, NativeStagedHandoffFixture.CORE, 1);
                require(prepared.ready() && prepared.quote() != null, "Production full-plan quote rejected: " + prepared.problem());
                require(prepared.builder() == builder(level),"Production selected another fixture builder");
                if (!REPLACE_BUILDER && !SHARED_CREW) require(prepared.plan().blocks().equals(fixture.plan().blocks())
                        && prepared.plan().clearance().equals(fixture.plan().clearance()), "Production quote changed the complete 572-target one-claim plan");
                // Opt-in replacement/shared variants test explicit legacy geometry through protected public admission.
                // It never substitutes a legacy fallback in production's new-plan quote path.
                var testedPlan=REPLACE_BUILDER || SHARED_CREW?fixture.plan():prepared.plan();
                var quote=prepared.quote();
                Map<Long,BlockState> baseline=new LinkedHashMap<>(),headroom=new LinkedHashMap<>();
                if (REPLACE_BUILDER || SHARED_CREW) {
                    testedPlan.blocks().keySet().forEach(cell->baseline.put(cell,level.getBlockState(BlockPos.of(cell))));
                    testedPlan.clearance().forEach(cell->headroom.put(cell,level.getBlockState(BlockPos.of(cell))));
                    REPORT.put(SHARED_CREW ? "sharedCrewGeometry" : "replacementGeometry","explicit legacy hollow plan; production new gated quote remains unchanged");
                    REPORT.put("commissionPath","Explicit legacy whole-plan public direct server commission with unchanged native protection and one payment");
                } else {baseline.putAll(quote.before());headroom.putAll(quote.clearance());}
                var testedGates=REPLACE_BUILDER || SHARED_CREW?null:quote.gateContract();
                REPORT.put("productionQuoteStageCount", prepared.quote().layout().stages().size());
                // Deliberately QA-only partition pressure. Always run the unchanged actual native serializer validator too.
                reviewedLayout = PerimeterStageLayout.partition(testedPlan, part -> {
                    String nativeProblem = BlueprintNetworkBudget.problem(TerritoryFortification.blueprint(part.targets(), part.min(), part.max()));
                    return nativeProblem != null ? nativeProblem : part.targets().size() > SECTION_TARGET_CAP ? "QA-only representative section target cap" : null;
                });
                PerimeterStageLayout.validate(testedPlan, reviewedLayout);
                require(reviewedLayout.stages().size() > 1 && reviewedLayout.stages().stream().allMatch(part -> part.targets().size() <= SECTION_TARGET_CAP),
                        "Synthetic partition did not retain bounded multiple native sections");
                String fingerprint = PerimeterReviewFingerprint.create(testedPlan, reviewedLayout, baseline, headroom,
                        testedGates, quote.core(), 1, prepared.claimIdentity(), owner.getUUID(), prepared.builder().getUUID());
                require(balance(owner) == 2000 && placed(level) == 0 && areaCount(level) == 0 && PerimeterProjectStore.all(core(owner)).isEmpty(),
                        "Full quote/synthetic review charged, placed blocks or started a project");
                require(!SHARED_CREW || prepared.builder().getUUID().equals(originalCoordinator),
                        "Fixture coordinator differs from the real reviewed builder");
                require(NativePerimeterProjects.start(owner, prepared.builder(), quote.core(), 1, testedPlan, reviewedLayout,
                        baseline, headroom, testedGates, prepared.territory(), fingerprint), "Public direct server commission failed");
                var projects = PerimeterProjectStore.all(core(owner));
                require(projects.size() == 1 && balance(owner) == 1936, "Direct server commission did not charge exactly 64 once");
                acceptedProject = projects.get(0); projectId = acceptedProject.header().projectId();
                require(acceptedProject.state() == PerimeterProject.State.RUNNING && acceptedProject.layout().equals(reviewedLayout)
                        && acceptedProject.plan().blocks().equals(fixture.plan().blocks()), "Commission changed full plan or synthetic partition");
                verifyPayment(acceptedProject.payment());
                var journal = PerimeterStageJournal.get(core(owner), acceptedProject);
                require(journal != null && journal.attempts().size() == 1 && journal.at(0).state() == PerimeterStageJournal.State.LIVE,
                        "First native marker is not durably LIVE");
                originalMarker = journal.at(0).marker(); jobId = acceptedProject.active().areaId(); verifyActiveStage(level, acceptedProject);
                var residents = RaidSavedData.get(owner.server).civilianFactions.get(coreKey);
                require(residents != null, "Starter civilian ledger missing");
                require(core(owner) == treasury && treasury.contains("BankInterestAt", net.minecraft.nbt.Tag.TAG_LONG)
                        && treasury.getLong("BankInterestAt") == interestAt, "Commission replaced the authoritative Treasury clock");
                treasuryObserver = new NativeQaTreasury(core(owner), bankRate(core(owner)), now, 2000, 64, residents.getCompound("Residents").size());
                builder(level).setNoAi(false); // End fixture AI hold. Subsequent work belongs to native AI.
                if (SHARED_CREW) ((BuilderEntity) level.getEntity(sharedHelper)).setNoAi(false);
                constructionStarted = lastProgress = System.nanoTime(); placedBefore = 0;
                REPORT.put("projectId", projectId.toString()); REPORT.put("manifestHash", acceptedProject.manifestHash());
                if (SHARED_CREW) REPORT.put("commissionGameTime", now);
                REPORT.put("reviewFingerprint", fingerprint); REPORT.put("originalMarker", originalMarker.toShortString());
                REPORT.put("parkedUnrelatedStarterNpcIds", fixture.parkedAuxiliaries());
                REPORT.put("reviewedStageCount", reviewedLayout.stages().size());
                REPORT.put("stageLayout", acceptedProject.stages().stream().map(part -> Map.of("index", part.index(), "area", part.areaId().toString(),
                        "digest", part.digest(), "targets", part.layout().targets().size(), "min", part.layout().min().toShortString(), "max", part.layout().max().toShortString())).toList());
                REPORT.put("hollowOracle", Map.of("columns", fixture.oracle().columns(), "skinColumns", fixture.oracle().skinColumns(),
                        "cavityColumns", fixture.oracle().columns() - fixture.oracle().skinColumns(), "targetCount", NativeStagedHandoffFixture.BLOCKS,
                        "cavityAirCount", fixture.oracle().cavities().size(), "headroomAirCount", fixture.oracle().headroom().size(),
                        "reservedCellCount", NativeStagedHandoffFixture.RESERVED));
                recordAuthority(level, "commission");
                check("Full production 572-target quote, lossless QA-only <="+SECTION_TARGET_CAP+"-target partition plus unchanged native serializer validation, public direct server commission and one noncreative 64-emerald debit");
                sample(level, owner, "direct-server-commission"); advance(now, 5); return Action.CAPTURE_COMMISSION;
            }
            case 5 -> {
                if (SHARED_CREW && advanceSharedCrew(level, owner, project, now)) return Action.NONE;
                if (REPLACE_BUILDER && !replacementVerified) {
                    if (replacementSpawned) {
                        require(now-replacementTick<1200,"Confirmed-death replacement failed to resume within one minute");
                        if (level.getEntity(deadBuilder)!=null || project==null
                                || !ConstructionEditLedger.get(level).assignedBuilder(project).equals(fixture.builderId())
                                || !NativePerimeterProjects.projectLinkMatches(builder(level),project)
                                || placed(level)<=replacementPlaced) return Action.NONE;
                        require(project.header().builder().equals(deadBuilder),"Replacement rewrote original paid manifest");
                        verifyActiveStage(level,project); conservation(level,owner);
                        REPORT.put("builderReplacement",Map.of("deadBuilder",deadBuilder.toString(),"replacement",fixture.builderId().toString(),
                                "placedBefore",replacementPlaced,"placedAfter",placed(level),"extraCommission",false));
                        replacementVerified=true; lastProgress=System.nanoTime();
                        check("Confirmed killed builder replaced by a new idle owned hire; original native AI resumed the same paid partial section without copying materials");
                    } else if (project!=null && project.state()==PerimeterProject.State.RUNNING && project.activeStage()==0
                            && stagePlaced(level,0)>=8 && workerStock(builder(level),Items.COBBLESTONE)==0
                            && workerStock(builder(level),Items.OAK_PLANKS)==0 && !ProtectedBuilderHandMirror.activeUse(builder(level))
                            && ProtectedStorageAccess.runningProblem(builder(level))==null) {
                        spawnReplacement(level,owner,now); return Action.NONE;
                    }
                }
                require(!owner.isCreative() && !owner.isSpectator() && !owner.hasPermissions(2) && !builder(level).isNoAi(), "Native work lost real Survival/AI conditions");
                require(project != null && project.state() != PerimeterProject.State.COMPLETE, "Representative run unexpectedly lost its unfinished whole project");
                if (midRestart && !betweenRestart && project.state() == PerimeterProject.State.WAITING_FOR_NEXT_STAGE && project.activeStage() == 1) {
                    beginRestart(level, owner, "between-stages"); return Action.NONE;
                }
                if (now - lastSampleTick < 20) return Action.NONE;
                lastSampleTick = now; long placed = placed(level);
                require(placed >= placedBefore, "An already placed exact target disappeared");
                if (placed != placedBefore) { lastProgress = System.nanoTime(); placedBefore = placed; }
                require(System.nanoTime() - constructionStarted - pausedNanos < CONSTRUCTION_SECONDS * SECOND, "Native representative work exceeded nine-minute construction cap");
                require(System.nanoTime() - lastProgress < 180 * SECOND, "No native construction progress for three minutes");
                conservation(level, owner); verifyProject(level, project);
                String transition = project.state() + ":" + project.activeStage() + ":" + project.receipts().size() + ":" + project.blocker();
                if (!transition.equals(lastTransition) || now % 200 < 20) { sample(level, owner, "native-progress"); lastTransition = transition; }
                if (!midRestart && (!REPLACE_BUILDER || replacementVerified) && project.state() == PerimeterProject.State.RUNNING && project.activeStage() == 0) {
                    long stagePlaced = stagePlaced(level, 0);
                    if (stagePlaced >= 8 && stagePlaced < project.active().layout().targets().size()
                            && stableTicks >= 40 && now - lastStableTick <= 1 && closed(builder(level))
                            && (!SHARED_CREW || bothContributed() && closed((BuilderEntity) level.getEntity(sharedHelper)))) {
                        REPORT.put("partialRestartClosedLifecycle", Map.of("consecutiveServerTicks", stableTicks, "minimumTicks", 40,
                                "cleanupJournalEmpty", true, "activeHandUse", false, "storageRunningProblemAbsent", true));
                        beginRestart(level, owner, "mid-stage"); return Action.NONE;
                    }
                }
                if (project.state() == PerimeterProject.State.RUNNING && project.activeStage() == 1 && stagePlaced(level, 1) > 0) {
                    require((!SHARED_CREW || sharedPromotionVerified && sharedReloadVerified)
                            && midRestart && project.receipts().size() == 1 && stagePlaced(level, 0) == acceptedProject.stages().get(0).layout().targets().size(),
                            "Native next-section work lacks first-section completion and the actual partial first-stage restart");
                    verifyActiveStage(level, project);
                    var journal = PerimeterStageJournal.get(core(owner), project);
                    require(journal.at(0).state() == PerimeterStageJournal.State.RETIRED && journal.at(1).state() == PerimeterStageJournal.State.LIVE,
                            "Native controller did not retire the first section and activate the second");
                    verifyJoins(); nextStagePlaced = stagePlaced(level, 1);
                    REPORT.put("firstSectionCompletionObserved", true); REPORT.put("nextSectionNativePlaced", nextStagePlaced);
                    REPORT.put("firstSectionTargetCount", acceptedProject.stages().get(0).layout().targets().size());
                    REPORT.put("constructionSeconds", (System.nanoTime() - constructionStarted - pausedNanos) / (double) SECOND);
                    REPORT.put("betweenStageRestartVerified", betweenRestart);
                    REPORT.put("betweenStageCoverage", betweenRestart ? "Real close/reopen while WAITING_FOR_NEXT_STAGE" : "Boundary was not cleanly observed; live handoff only");
                    recordAuthority(level, "native-handoff");
                    check("Original native AI completed the first section; production controller handed off at the original physical shovel site and native AI placed the next section");
                    advance(now, 6); return Action.OPEN_CORE;
                }
            }
            case 6 -> {
                if (!(owner.containerMenu instanceof CoreHireMenu menu)) return Action.NONE;
                require(menu.stillValid(owner) && owner.getTeam() != null && owner.getTeam().getName().equals(NativeStagedHandoffFixture.FACTION),
                        "Actual cancellation menu owner mismatch");
                menuId = menu.containerId; advance(now, 7); return Action.CANCEL_MENU;
            }
            case 7 -> {
                if (menuUiStage == 4) {
                    require(owner.containerMenu instanceof CoreHireMenu menu && menu.containerId == menuId && menu.stillValid(owner)
                            && project != null && project.state() == PerimeterProject.State.RUNNING
                            && PerimeterProjectAuthority.snapshot(level, coreKey).terminal(projectId) == null,
                            "Escape changed the authenticated menu or canceled the live project");
                    verifyProject(level, project); conservation(level, owner); observeTreasury(level, core(owner));
                    if (escapeObservedTick < 0) escapeObservedTick = now;
                    if (now - escapeObservedTick < 20) return Action.NONE; // Let ordinary packet/tick processing expose an unintended cancellation.
                    REPORT.put("keyboardEscapeObservationTicks", now - escapeObservedTick);
                    REPORT.put("keyboardEscapePreservedProject", true);
                    menuUiStage = 5; return Action.CANCEL_MENU;
                }
                require(menuUiStage == 7, "Actual menu cancellation and confirmation controls were not keyboard-activated");
                if (PerimeterProjectAuthority.snapshot(level, coreKey).terminal(projectId) == null) return Action.NONE;
                verifyCancellation(level, owner); recordAuthority(level, "canceled-before-reopen");
                beginRestart(level, owner, "canceled");
            }
            case 10 -> {
                require(owner.isSpectator() && geometry(level).equals(pausedGeometry), "Permission-pause boundary allowed an accepted-cell write");
                if (now - stageTick < 100) return Action.NONE;
                if (reloadKind.equals("canceled")) verifyCancellation(level, owner);
                else {
                    require(project != null && !project.blocker().isEmpty(), "Production whole-project owner-unavailable pause was not observed");
                    if (reloadKind.equals("between-stages")) require(project.state() == PerimeterProject.State.WAITING_FOR_NEXT_STAGE
                            && areaCount(level) == 0 && builder(level).currentBuildArea == null, "Between-stage boundary created a child while owner permission was absent");
                }
                conservation(level, owner); sample(level, owner, reloadKind + "-before-shutdown"); recordAuthority(level, reloadKind + "-before-shutdown");
                owner.server.saveEverything(false, true, true);
                restartRequested = true; advance(now, 11); return Action.RELOAD;
            }
            case 11 -> {
                require(stopped != null && stoppingFailure == null && loadFailure == null && loadedAuthority != null
                        && loadedAuthority.equals(stopped.authority()), "Exact pre-tick project/journal/Treasury reload boundary failed: " + loadFailure);
                require(owner.isSpectator() && geometry(level).equals(stopped.geometry()) && pendingCells(level).equals(stopped.pendingCells()),
                        "Saved permission/partial geometry/remaining union changed on restart");
                require(chestValues(level).equals(stopped.chests()) && loadedCargo != null && loadedCargo.equals(stopped.cargo())
                        && materialValue(loadedMain).equals(materialValue(stopped.main())) && materialValue(loadedOff).equals(materialValue(stopped.off())),
                        "Native exact chest withdrawals or pre-AI loaded cargo/equipment changed across reload");
                if (SHARED_CREW) {
                    require(LOADED_CREW.equals(stopped.crewCargo()), "Exact construction cargo for every surviving crew member changed on reload");
                    REPORT.put("sharedCrewCargoReload-" + reloadKind, Map.of("stopped", cargoEvidence(stopped.crewCargo()),
                            "loaded", cargoEvidence(LOADED_CREW)));
                    if (reloadKind.equals("mid-stage")) require(LOADED_CREW.keySet().equals(Set.of(originalCoordinator, sharedHelper)),
                            "Shared partial restart did not reload both original builders");
                }
                require(ConstructionEditLedger.get(level).sameGeneration(stopped.ledgerGeneration()), "Durable ledger generation changed across reload");
                verifyCurrentTerritory(level, NativeStagedHandoffFixture.TERRITORY);
                if (reloadKind.equals("canceled")) verifyCancellation(level, owner);
                else {
                    require(project != null && project.state() == stopped.state() && project.activeStage() == stopped.activeStage()
                            && ConstructionEditLedger.get(level).matchesProjectReservation(project), "Durable stage/global reservation changed across reload");
                    if (reloadKind.equals("mid-stage")) require(level.getEntity(project.active().areaId()) instanceof ProtectedBuildArea area
                            && area.getPersistentData().getCompound("SiegeProtectedConstructionV1").equals(stopped.recipe()), "Paid native stage recipe changed across reload");
                    else require(areaCount(level) == 0 && PerimeterStageJournal.get(core(owner), project).at(project.activeStage()) == null,
                            "Reload recreated a not-yet-started section before owner permission returned");
                    verifyProject(level, project);
                }
                RESTARTS.add(Map.of("kind", reloadKind, "gameTime", stopped.gameTime(), "placed", stopped.placed(), "pending", stopped.pendingCells().size(),
                        "projectState", stopped.state().name(), "activeStage", stopped.activeStage(), "ledgerGeneration", stopped.ledgerGeneration().toString(),
                        "verifiedPassiveCreditsAfterStop", treasuryObserver.passiveCredits() - stopped.passiveCredits()));
                recordAuthority(level, reloadKind + "-after-reopen");
                owner.setGameMode(GameType.SURVIVAL); restartRequested = false;
                if (reloadKind.equals("canceled")) { canceledRestart = true; advance(now, 13); } else advance(now, 12);
            }
            case 12 -> {
                if (SHARED_CREW && now - lastSampleTick >= 140) {
                    lastSampleTick = now; sample(level, owner, "shared-reload-progress");
                }
                require(now - stageTick < 2400, "Production did not resume within two minutes after actual reload and restored Survival permission");
                if (project == null || project.state() != PerimeterProject.State.RUNNING || placed(level) <= stopped.placed()) return Action.NONE;
                if (SHARED_CREW && reloadKind.equals("mid-stage") && CREW_PLACEMENTS.entrySet().stream()
                        .anyMatch(entry -> entry.getValue().size() <= crewBeforeReload.getOrDefault(entry.getKey(), 0))) return Action.NONE;
                if (SHARED_CREW && reloadKind.equals("mid-stage")) sharedReloadVerified = true;
                verifyActiveStage(level, project); conservation(level, owner);
                require(!ProtectedBuilderHandMirror.pending(builder(level).getPersistentData())
                        && !ProtectedBuilderHandMirror.reviewNeeded(builder(level).getPersistentData()), "Native hand reload repair is unresolved after resumed work");
                if (reloadKind.equals("mid-stage")) midRestart = true; else betweenRestart = true;
                pausedNanos += System.nanoTime() - pauseStarted; lastProgress = System.nanoTime(); placedBefore = placed(level);
                check("Actual " + reloadKind + " close/reopen preserved paid global authority, full stage journal, exact stock/geometry and ledger; original native AI resumed without another charge");
                sample(level, owner, reloadKind + "-resumed"); advance(now, 5);
            }
            case 13 -> {
                verifyCancellation(level, owner);
                if (now - stageTick < 60) return Action.NONE;
                require(canceledRestart && midRestart && nextStagePlaced > 0 && placed(level) < NativeStagedHandoffFixture.BLOCKS,
                        "Representative acceptance lacks its required lifecycle evidence");
                REPORT.put("finalDiagnostics", diagnostics(level, owner, true)); REPORT.put("retainedPlacedBlocks", placed(level));
                REPORT.put("treasuryDebit", 64); REPORT.put("materialCounts", Map.of("minecraft:cobblestone", NativeStagedHandoffFixture.COBBLE, "minecraft:oak_planks", NativeStagedHandoffFixture.OAK));
                REPORT.put("midStageRestartVerified", midRestart); REPORT.put("canceledRestartVerified", canceledRestart);
                REPORT.put("hollowCavityAirPreserved", true); REPORT.put("walkwayHeadroomAirPreserved", true);
                REPORT.put("wholePlanCompleted", false); REPORT.put("territoryChunkCount", 1);
                REPORT.put("nativeClaimRecords", fixture.claimIds().stream().map(UUID::toString).toList());
                check("Real Screen.keyPressed Tab/Enter reached Building, Construction, Cancel and Yes; confirmation Escape returned without canceling. These are screen-handler checks, not physical OS keyboard input. Authenticated cancellation and real close/reopen retained CANCELED receipt, cleanup, exact geometry/stock and one 64-emerald debit");
                advance(now, 14); return Action.CAPTURE_FINAL;
            }
            case 14 -> {
                require(!REPLACE_BUILDER || replacementVerified,"Replacement construction evidence missing");
                if (SHARED_CREW) finishSharedCrew(level);
                require(SHOTS.contains("04-canceled-after-reopen.png"), "Missing actual canceled-world framebuffer");
                verifyCancellation(level, owner); return Action.DONE;
            }
            default -> throw new AssertionError("Unexpected representative QA phase " + stage);
        }
        return Action.NONE;
    }

    /** Fixture hires a fresh worker with tools only; production selects and assigns it. */
    private static void spawnReplacement(ServerLevel level,ServerPlayer owner,long now) throws Exception {
        BuilderEntity old=builder(level);deadBuilder=old.getUUID();replacementPlaced=placed(level);
        require(old.hurt(level.damageSources().generic(),Float.MAX_VALUE),"Fixture builder did not take lethal damage");
        require(!old.isAlive(),"Fixture builder survived lethal damage");
        var type=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("workers","builder"));
        require(type!=null,"Replacement builder registry unavailable");
        var entity=type.create(level); require(entity instanceof BuilderEntity,"Replacement builder type changed");
        BuilderEntity next=(BuilderEntity)entity;
        next.moveTo(NativeStagedHandoffFixture.CORE.getX()+2.5,65,NativeStagedHandoffFixture.CORE.getZ()+2.5,0,0);
        next.finalizeSpawn(level,level.getCurrentDifficultyAt(next.blockPosition()),net.minecraft.world.entity.MobSpawnType.COMMAND,null,null);
        WorkersBridge.enablePlayerJob(next,owner.getUUID());next.setPersistenceRequired();
        next.getInventory().setItem(6,new ItemStack(Items.BREAD,64));
        next.getInventory().setItem(7,new ItemStack(Items.DIAMOND_PICKAXE));
        next.getInventory().setItem(8,new ItemStack(Items.DIAMOND_AXE));
        next.getInventory().setItem(9,new ItemStack(Items.DIAMOND_SHOVEL));next.getInventory().setChanged();
        var goal=next.goalSelector.getAvailableGoals().stream().map(g->g.getGoal()).filter(BuilderWorkGoal.class::isInstance).map(BuilderWorkGoal.class::cast).findFirst().orElseThrow();
        var storage=next.goalSelector.getAvailableGoals().stream().map(g->g.getGoal()).filter(GetNeededItemsFromStorage.class::isInstance).map(GetNeededItemsFromStorage.class::cast).findFirst().orElseThrow();
        fixture=new NativeStagedHandoffFixture.Fixture(next.getUUID(),fixture.storageIds(),fixture.claimIds(),fixture.plan(),fixture.oracle(),
                goal,storage,fixture.nonPlanCells(),fixture.parkedAuxiliaries());
        liveBuildGoal=goal;liveStorageGoal=storage;
        require(level.addFreshEntity(next),"Replacement hire could not enter fixture world");
        replacementSpawned=true;replacementTick=now;stableTicks=0;
    }

    private static void rememberCrew(BuilderEntity worker) {
        CREW_GOALS.put(worker.getUUID(), worker.goalSelector.getAvailableGoals().stream().map(g -> g.getGoal())
                .filter(BuilderWorkGoal.class::isInstance).map(BuilderWorkGoal.class::cast).findFirst().orElseThrow());
        CREW_STORAGE.put(worker.getUUID(), worker.goalSelector.getAvailableGoals().stream().map(g -> g.getGoal())
                .filter(GetNeededItemsFromStorage.class::isInstance).map(GetNeededItemsFromStorage.class::cast).findFirst().orElseThrow());
        CREW_PLACEMENTS.computeIfAbsent(worker.getUUID(), ignored -> new java.util.HashSet<>());
    }
    private static Cargo cargo(BuilderEntity worker) {
        return new Cargo(constructionValues(worker.getInventory()), materialValue(worker.getMainHandItem().save(new CompoundTag())),
                materialValue(worker.getOffhandItem().save(new CompoundTag())));
    }
    private static Map<String, Object> cargoEvidence(Map<UUID, Cargo> cargo) {
        var result = new LinkedHashMap<String, Object>();
        cargo.forEach((id, value) -> result.put(id.toString(), Map.of("slots", value.slots().stream().map(CompoundTag::toString).toList(),
                "main", value.main().toString(), "off", value.off().toString())));
        return result;
    }
    private static Map<UUID, Cargo> crewCargo(ServerLevel level) {
        var result = new LinkedHashMap<UUID, Cargo>();
        for (UUID id : CREW_PLACEMENTS.keySet()) if (level.getEntity(id) instanceof BuilderEntity worker && worker.isAlive())
            result.put(id, cargo(worker));
        return Map.copyOf(result);
    }
    private static int allWorkerStock(ServerLevel level, Item material) {
        if (!SHARED_CREW) return workerStock(builder(level), material);
        int total = 0;
        for (UUID id : CREW_PLACEMENTS.keySet()) if (level.getEntity(id) instanceof BuilderEntity worker) {
            if (sharedDeathDropsVerified && id.equals(originalCoordinator)) {
                require(!worker.isAlive(), "Verified native death cargo exclusion encountered a living original actor");
                continue; // Its exact native drops already own this cargo; the dying inventory is retained by Recruits.
            }
            total += workerStock(worker, material);
        }
        return total;
    }
    private static Map<String, Integer> crewCounts() {
        var counts = new LinkedHashMap<String, Integer>();
        CREW_PLACEMENTS.forEach((id, cells) -> counts.put(id.toString(), cells.size())); return counts;
    }
    private static boolean bothContributed() {
        return CREW_PLACEMENTS.size() == 2 && CREW_PLACEMENTS.values().stream().allMatch(cells -> !cells.isEmpty());
    }

    /**
     * Read-only attribution bracket: the actual LivingTick boundary before a builder's AI, closed
     * by the next living entity's boundary or the ordinary server END tick. This fixture has only
     * two construction actors; every credited exact world change must have that actor's matching
     * material debit and the production guard's native Completed receipt. No goal is invoked here.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void observeCrewLivingTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        if (!ENABLED || !SHARED_CREW || finished || fixture == null || projectId == null
                || !(event.getEntity().level() instanceof ServerLevel level) || level != level.getServer().overworld()) return;
        try {
            flushWorkObservation(level);
            if (!(event.getEntity() instanceof BuilderEntity worker) || !worker.isAlive()
                    || !CREW_PLACEMENTS.containsKey(worker.getUUID())) return;
            var project = project(level);
            if (project == null || project.active() == null) return;
            var owner = level.getServer().getPlayerList().getPlayer(playerId);
            workObservation = new WorkObservation(worker, project.active().areaId(), project.activeStage(), level.getGameTime(),
                    pendingCells(level), workerStock(worker, Items.COBBLESTONE), workerStock(worker, Items.OAK_PLANKS),
                    chestStock(level, Items.COBBLESTONE), chestStock(level, Items.OAK_PLANKS),
                    dropped(level, Items.COBBLESTONE), dropped(level, Items.OAK_PLANKS),
                    owner == null ? 0 : count(owner.getInventory(), Items.COBBLESTONE),
                    owner == null ? 0 : count(owner.getInventory(), Items.OAK_PLANKS), owner != null, sharedDeathDropsVerified,
                    NativePerimeterProjects.participantLinkMatches(worker, project)
                            && project.header().owner().equals(WorkersBridge.readWorkerOwner(worker)),
                    String.valueOf(CREW_GOALS.get(worker.getUUID()).state));
        } catch (Throwable problem) { observerFailure = problem; }
    }
    private static void flushWorkObservation(ServerLevel level) {
        WorkObservation before = workObservation; workObservation = null;
        if (before == null) return;
        require(before.worker().level() == level && before.gameTime() == level.getGameTime(),
                "Shared native observation crossed a world/tick boundary");
        Set<Long> afterPending = pendingCells(level);
        require(before.pending().containsAll(afterPending), "Native shared work removed an already placed block");
        var changed = new java.util.HashSet<>(before.pending()); changed.removeAll(afterPending);
        var worker = before.worker();
        int cobbleAfter = workerStock(worker, Items.COBBLESTONE), oakAfter = workerStock(worker, Items.OAK_PLANKS);
        int chestCobbleAfter = chestStock(level, Items.COBBLESTONE), chestOakAfter = chestStock(level, Items.OAK_PLANKS);
        int looseCobbleAfter = dropped(level, Items.COBBLESTONE), looseOakAfter = dropped(level, Items.OAK_PLANKS);
        var owner = level.getServer().getPlayerList().getPlayer(playerId);
        int ownerCobbleAfter = owner == null ? 0 : count(owner.getInventory(), Items.COBBLESTONE);
        int ownerOakAfter = owner == null ? 0 : count(owner.getInventory(), Items.OAK_PLANKS);
        int pickedCobble = before.looseCobble() - looseCobbleAfter, pickedOak = before.looseOak() - looseOakAfter;
        boolean pickedUp = pickedCobble > 0 || pickedOak > 0;
        long cobblePlaced = changed.stream().filter(cell -> fixture.plan().blocks().get(cell).equals("minecraft:cobblestone")).count();
        long oakPlaced = changed.size() - cobblePlaced;
        if (pickedUp) {
            require(before.deathTransferKnown() && sharedDeathDropsVerified && worker.getUUID().equals(sharedHelper)
                    && before.gameTime() > sharedDeathTick && before.ownerPresent() && owner != null
                    && pickedCobble >= 0 && pickedOak >= 0
                    && before.chestCobble() == chestCobbleAfter && before.chestOak() == chestOakAfter
                    && before.ownerCobble() == ownerCobbleAfter && before.ownerOak() == ownerOakAfter
                    && before.cobble() + pickedCobble - cobbleAfter == cobblePlaced
                    && before.oak() + pickedOak - oakAfter == oakPlaced,
                    "Native death-drop pickup lacks exact surviving-helper, loose-item and material-debit evidence");
        }
        if (!changed.isEmpty()) {
            require(before.participating() && worker.isAlive() && !worker.isNoAi()
                    && before.nativeState().equals("PLACE_BLOCKS"), "Unattributed/non-native shared placement");
            require(before.cobble() + (pickedUp ? pickedCobble : 0) - cobbleAfter == cobblePlaced
                    && before.oak() + (pickedUp ? pickedOak : 0) - oakAfter == oakPlaced
                    && (pickedUp || before.looseCobble() == looseCobbleAfter && before.looseOak() == looseOakAfter)
                    && before.chestCobble() == chestCobbleAfter && before.chestOak() == chestOakAfter,
                    "Exact per-builder placement material debit was not observed");
            require(level.getEntity(before.area()) instanceof ProtectedBuildArea, "Placement lost its authenticated native area");
            var completed = java.util.Arrays.stream(level.getEntity(before.area()).getPersistentData()
                    .getCompound("SiegeProtectedConstructionV1").getLongArray("Completed")).boxed()
                    .collect(java.util.stream.Collectors.toSet());
            for (long cell : changed) {
                require(acceptedProject.stages().get(before.stage()).layout().targets().containsKey(cell)
                        && completed.contains(cell) && CREW_PLACEMENTS.values().stream().noneMatch(cells -> cells.contains(cell)),
                        "Duplicate or unreceipted shared target placement");
                CREW_PLACEMENTS.get(worker.getUUID()).add(cell);
            }
        }
        boolean withdrawn = !pickedUp && (cobbleAfter > before.cobble() || oakAfter > before.oak());
        if (withdrawn) {
            require(changed.isEmpty() && before.looseCobble() == looseCobbleAfter && before.looseOak() == looseOakAfter
                    && before.chestCobble() - chestCobbleAfter == cobbleAfter - before.cobble()
                    && before.chestOak() - chestOakAfter == oakAfter - before.oak(), "Shared supply withdrawal lacks exact finite-chest evidence");
            CREW_SUPPLIED.add(worker.getUUID());
        }
        if (!changed.isEmpty() || withdrawn || pickedUp) {
            var record = new LinkedHashMap<String, Object>();
            record.put("builder", worker.getUUID().toString()); record.put("area", before.area().toString());
            record.put("stage", before.stage()); record.put("gameTime", before.gameTime());
            record.put("nativeStateBefore", before.nativeState()); record.put("kind", !changed.isEmpty()
                    ? "native-placement" : pickedUp ? "native-loose-pickup" : "native-supply-withdrawal");
            record.put("verifiedDeathDropPickup", pickedUp);
            record.put("looseCobbleBefore", before.looseCobble()); record.put("looseCobbleAfter", looseCobbleAfter);
            record.put("looseOakBefore", before.looseOak()); record.put("looseOakAfter", looseOakAfter);
            record.put("ownerCobbleBefore", before.ownerCobble()); record.put("ownerCobbleAfter", ownerCobbleAfter);
            record.put("ownerOakBefore", before.ownerOak()); record.put("ownerOakAfter", ownerOakAfter);
            record.put("ownerPresentBefore", before.ownerPresent()); record.put("ownerPresentAfter", owner != null);
            record.put("cells", changed.stream().sorted().map(cell -> Map.of("packed", cell,
                    "position", BlockPos.of(cell).toShortString(), "material", fixture.plan().blocks().get(cell))).toList());
            record.put("cobbleBefore", before.cobble()); record.put("cobbleAfter", cobbleAfter);
            record.put("oakBefore", before.oak()); record.put("oakAfter", oakAfter);
            record.put("chestCobbleBefore", before.chestCobble()); record.put("chestCobbleAfter", chestCobbleAfter);
            record.put("chestOakBefore", before.chestOak()); record.put("chestOakAfter", chestOakAfter);
            CREW_EVENTS.add(record);
        }
    }

    private static boolean advanceSharedCrew(ServerLevel level, ServerPlayer owner, PerimeterProject project, long now) {
        if (sharedPromotionVerified) return false;
        require(project != null && project.activeStage() == 0, "Shared acceptance advanced before coordinator succession evidence");
        var helper = level.getEntity(sharedHelper);
        require(helper instanceof BuilderEntity && helper.isAlive(), "Original enlisted helper did not survive");
        var ledger = ConstructionEditLedger.get(level);
        if (sharedDeathRequested) {
            require(now - sharedDeathTick < 1200, "Surviving shared helper did not resume within one minute of coordinator death");
            if (level.getEntity(originalCoordinator) != null || !ledger.assignedBuilder(project).equals(sharedHelper)
                    || !NativePerimeterProjects.participantLinkMatches((BuilderEntity) helper, project)
                    || placed(level) <= sharedDeathPlaced) return true;
            require(project.header().builder().equals(originalCoordinator) && ledger.knownCrewMember(project, sharedHelper),
                    "Succession rewrote the paid original identity or invented a replacement hire");
            verifyActiveStage(level, project); conservation(level, owner); observeTreasury(level, core(owner));
            var deathHandoff = verifiedDeathHandoff(ledger, project);
            REPORT.put("sharedCrewSuccession", Map.of("deadCoordinator", originalCoordinator.toString(), "survivingHelper", sharedHelper.toString(),
                    "placedBefore", sharedDeathPlaced, "placedAfter", placed(level), "deathGameTime", sharedDeathTick,
                    "resumeGameTime", now, "manifestHash", project.manifestHash(), "extraCommission", false,
                    "newBuilderSpawnedAfterDeath", false, "deathHandoff", deathHandoff));
            sharedPromotionVerified = true; lastProgress = System.nanoTime();
            check("Already-enlisted surviving helper became coordinator after actual original-builder death; normal native placement resumed the identical paid project");
            return false;
        }
        if (!ledger.crewMembers(project).equals(Set.of(sharedHelper))) return false;
        require(ledger.assignedBuilder(project).equals(originalCoordinator)
                && NativePerimeterProjects.participantLinkMatches((BuilderEntity) helper, project), "Wrong native shared crew admission");
        if (!REPORT.containsKey("sharedCrewAdmission")) REPORT.put("sharedCrewAdmission", Map.of("coordinator", originalCoordinator.toString(),
                "helper", sharedHelper.toString(), "owner", owner.getUUID().toString(), "project", projectId.toString(),
                "manifestHash", project.manifestHash(), "area", project.active().areaId().toString(), "gameTime", now));
        BuilderEntity original = builder(level);
        BuilderEntity survivor = (BuilderEntity) helper;
        if (!midRestart || !sharedReloadVerified || !bothContributed() || !bothResumedBeforeDeath(now)
                || project.state() != PerimeterProject.State.RUNNING
                || !deathLifecycleClosed(original, ledger) || !deathLifecycleClosed(survivor, ledger)) return false;
        flushWorkObservation(level); conservation(level, owner);
        sharedDeathPlaced = placed(level); sharedDeathTick = now;
        verifyNativeDeathCargo(level, owner, original, survivor, project, now);
        sharedDeathRequested = true;
        fixture = new NativeStagedHandoffFixture.Fixture(sharedHelper, fixture.storageIds(), fixture.claimIds(), fixture.plan(), fixture.oracle(),
                CREW_GOALS.get(sharedHelper), CREW_STORAGE.get(sharedHelper), fixture.nonPlanCells(), fixture.parkedAuxiliaries());
        liveBuildGoal = CREW_GOALS.get(sharedHelper); liveStorageGoal = CREW_STORAGE.get(sharedHelper);
        require(survivor.isAlive() && !survivor.isNoAi(), "Surviving helper lost ordinary native AI");
        return true;
    }
    private static boolean bothResumedBeforeDeath(long now) {
        return stopped != null && CREW_PLACEMENTS.keySet().stream().allMatch(id -> CREW_EVENTS.stream().anyMatch(event ->
                "native-placement".equals(event.get("kind")) && id.toString().equals(event.get("builder"))
                        && ((Number) event.get("gameTime")).longValue() > stopped.gameTime()
                        && ((Number) event.get("gameTime")).longValue() < now));
    }
    /** Read the persisted assignment chain; disappearance or an UNAVAILABLE transfer never proves this death scenario. */
    private static Map<String, Object> verifiedDeathHandoff(ConstructionEditLedger ledger, PerimeterProject project) {
        var saved = ledger.save(new CompoundTag()).getCompound("BuilderAssignments");
        var assignments = com.devfarinsky.siegeoverhaul.core.PerimeterBuilderAssignments.load(saved);
        require(saved.getInt("Version") == 2 && assignments.builder(project).equals(sharedHelper),
                "Saved coordinator assignment does not authenticate the surviving helper");
        CompoundTag entry = null;
        for (var raw : saved.getList("Projects", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            var candidate = (CompoundTag) raw;
            if (projectId.equals(candidate.getUUID("Project"))) {
                require(entry == null, "Duplicate saved project assignment"); entry = candidate;
            }
        }
        require(entry != null, "Missing saved coordinator death handoff");
        var history = entry.getList("History", net.minecraft.nbt.Tag.TAG_COMPOUND);
        require(history.size() == 1, "Shared native acceptance requires exactly one coordinator handoff");
        var handoff = history.getCompound(0);
        UUID area = project.stages().get(0).areaId();
        String evidence = "perimeter-builder-destroyed-v1:" + projectId + ":" + project.header().generation()
                + ":" + project.manifestHash() + ":" + originalCoordinator + ":" + area + ":" + ledger.generation();
        String expected;
        try { expected = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(evidence.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        require(handoff.getUUID("From").equals(originalCoordinator) && handoff.getUUID("To").equals(sharedHelper)
                && handoff.getUUID("Area").equals(area) && handoff.getString("Cause").equals("DEATH")
                && handoff.getString("Receipt").equals(expected), "Saved handoff lacks exact authenticated coordinator death evidence");
        return Map.of("from", handoff.getUUID("From").toString(), "to", handoff.getUUID("To").toString(),
                "area", handoff.getUUID("Area").toString(), "cause", handoff.getString("Cause"), "receipt", handoff.getString("Receipt"),
                "projectGeneration", project.header().generation(), "ledgerGeneration", ledger.generation().toString());
    }

    /** One actual baseline-native death, with exact synchronous transfer proof before excluding the corpse's retained inventory. */
    private static void verifyNativeDeathCargo(ServerLevel level, ServerPlayer owner, BuilderEntity original,
                                              BuilderEntity helper, PerimeterProject project, long now) {
        require(!sharedDeathRequested && !sharedDeathDropsVerified && !REPORT.containsKey("sharedCrewDeathCargo")
                && original.getUUID().equals(originalCoordinator) && helper.getUUID().equals(sharedHelper)
                && original.isAlive() && helper.isAlive() && project.activeStage() == 0
                && project.state() == PerimeterProject.State.RUNNING
                && deathLifecycleClosed(original, ConstructionEditLedger.get(level))
                && deathLifecycleClosed(helper, ConstructionEditLedger.get(level)),
                "Native death cargo boundary lost its exact live original/helper or closed lifecycle");
        boolean dropsEnabled = level.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS);
        require(dropsEnabled && !ModList.get().isLoaded("corpse") && !ModList.get().isLoaded("rpgz"),
                "Death cargo acceptance requires baseline entity drops; corpse addons are not covered");
        var before = Map.of("cobble", workerStock(original, Items.COBBLESTONE), "oak", workerStock(original, Items.OAK_PLANKS));
        require(before.get("cobble") + before.get("oak") > 0, "Native death cargo case must exercise nonempty construction cargo");
        var looseBefore = nativeMaterialDrops(level); var remainingBefore = remainingDeathStores(level, owner, helper);
        require(!aliasedMaterialDrops(level), "Native material drop stacks were already aliased before death");
        var proof = new LinkedHashMap<String, Object>(); REPORT.put("sharedCrewDeathCargo", proof);
        proof.put("worker", originalCoordinator.toString()); proof.put("helper", sharedHelper.toString());
        proof.put("gameTime", now); proof.put("stage", project.activeStage()); proof.put("projectState", project.state().name());
        proof.put("closedLifecycles", true); proof.put("dropsEnabled", dropsEnabled);
        proof.put("originalAliveBefore", true); proof.put("verified", false); proof.put("before", before);
        proof.put("originalCargoBefore", oneCargoEvidence(cargo(original)));
        proof.put("looseBefore", List.copyOf(looseBefore.values())); proof.put("remainingStoresBefore", remainingBefore);
        proof.put("accountingExclusion", "exact dying original after verified native drop transfer");
        proof.put("baselineScope", "Pinned baseline companions only; corpse addons not exercised");
        boolean damaged = original.hurt(level.damageSources().generic(), Float.MAX_VALUE);
        proof.put("originalAliveAfter", original.isAlive()); proof.put("originalCargoAfter", oneCargoEvidence(cargo(original)));
        var looseAfter = nativeMaterialDrops(level); var remainingAfter = remainingDeathStores(level, owner, helper);
        var newDrops = looseAfter.entrySet().stream().filter(entry -> !looseBefore.containsKey(entry.getKey()))
                .map(Map.Entry::getValue).toList();
        proof.put("looseAfter", List.copyOf(looseAfter.values())); proof.put("newDrops", newDrops);
        proof.put("remainingStoresAfter", remainingAfter); proof.put("aliasedDropStacks", aliasedMaterialDrops(level));
        require(damaged && !original.isAlive(), "Original coordinator did not die from actual lethal damage");
        require(remainingBefore.equals(remainingAfter) && looseBefore.entrySet().stream()
                        .allMatch(entry -> entry.getValue().equals(looseAfter.get(entry.getKey()))),
                "Synchronous native death altered unrelated construction stores, world targets or existing drops");
        require(!Boolean.TRUE.equals(proof.get("aliasedDropStacks")), "Native death produced aliased construction drop stacks");
        for (var material : Map.of("cobble", "minecraft:cobblestone", "oak", "minecraft:oak_planks").entrySet()) {
            int dropped = newDrops.stream().filter(drop -> material.getValue().equals(drop.get("material")))
                    .mapToInt(drop -> (Integer) drop.get("count")).sum();
            require(dropped == before.get(material.getKey()), "Native death lost or duplicated " + material.getValue()
                    + ": carried=" + before.get(material.getKey()) + ", actual new drops=" + dropped);
        }
        // No stack is cleared, copied to an actor, moved, spawned or compensated by this observer.
        // Only this exact dead actor is excluded after its full native transfer has been measured.
        sharedDeathDropsVerified = true; proof.put("verified", true);
        conservation(level, owner);
    }
    private static boolean deathLifecycleClosed(BuilderEntity worker, ConstructionEditLedger ledger) {
        var data = worker.getPersistentData();
        return closed(worker) && !ProtectedBuilderHandMirror.pending(data) && !ProtectedBuilderHandMirror.reviewNeeded(data)
                && ProtectedBuilderHandLifecycle.matches(data, worker.getUUID(), ledger)
                && NativeConstructionGuard.validHandReceipt(data, ledger);
    }
    private static Map<String, Object> oneCargoEvidence(Cargo cargo) {
        return Map.of("slots", cargo.slots().stream().map(CompoundTag::toString).toList(),
                "main", cargo.main().toString(), "off", cargo.off().toString());
    }
    private static Map<String, Long> remainingDeathStores(ServerLevel level, ServerPlayer owner, BuilderEntity helper) {
        var values = new LinkedHashMap<String, Long>();
        for (var material : Map.of("Cobble", Items.COBBLESTONE, "Oak", Items.OAK_PLANKS).entrySet()) {
            values.put("chest" + material.getKey(), (long) chestStock(level, material.getValue()));
            values.put("helper" + material.getKey(), (long) workerStock(helper, material.getValue()));
            values.put("owner" + material.getKey(), (long) count(owner.getInventory(), material.getValue()));
            values.put("placed" + material.getKey(), fixture.plan().blocks().keySet().stream()
                    .filter(cell -> level.getBlockState(BlockPos.of(cell)).getBlock().asItem() == material.getValue()).count());
        }
        return Map.copyOf(values);
    }
    private static List<ItemEntity> materialDrops(ServerLevel level) {
        var drops = level.getEntitiesOfClass(ItemEntity.class, NativeStagedHandoffFixture.BOUNDS,
                entity -> entity.isAlive() && (entity.getItem().is(Items.COBBLESTONE) || entity.getItem().is(Items.OAK_PLANKS)));
        require(drops.size() <= 256, "Unexpectedly large native construction drop census");
        return drops;
    }
    private static Map<UUID, Map<String, Object>> nativeMaterialDrops(ServerLevel level) {
        var values = new LinkedHashMap<UUID, Map<String, Object>>();
        for (ItemEntity drop : materialDrops(level).stream().sorted(java.util.Comparator.comparing(e -> e.getUUID().toString())).toList()) {
            var item = drop.getItem();
            require(item.getCount() > 0 && item.getCount() <= item.getMaxStackSize() && !item.hasTag(),
                    "Native death/drop cargo changed an ordinary fixture material stack");
            require(values.put(drop.getUUID(), Map.of("uuid", drop.getUUID().toString(),
                    "material", String.valueOf(ForgeRegistries.ITEMS.getKey(item.getItem())), "count", item.getCount(),
                    "tag", "", "stack", item.save(new CompoundTag()).toString())) == null,
                    "Duplicate native construction drop identity");
        }
        return Map.copyOf(values);
    }
    private static boolean aliasedMaterialDrops(ServerLevel level) {
        Set<ItemStack> stacks = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ItemEntity drop : materialDrops(level)) if (!stacks.add(drop.getItem())) return true;
        return false;
    }

    private static void finishSharedCrew(ServerLevel level) {
        flushWorkObservation(level);
        require(observerFailure == null, "Shared observer failed: " + observerFailure);
        require(sharedReloadVerified && sharedPromotionVerified && sharedDeathDropsVerified && bothContributed()
                && CREW_SUPPLIED.equals(Set.of(originalCoordinator, sharedHelper)), "Shared acceptance lacks native contribution/supply/reload/succession evidence");
        long unique = CREW_PLACEMENTS.values().stream().flatMap(Set::stream).distinct().count();
        require(unique == placed(level) && CREW_PLACEMENTS.values().stream().mapToInt(Set::size).sum() == unique,
                "Observed native actor placements do not equal retained exact world targets");
        REPORT.put("sharedCrewEvents", CREW_EVENTS); REPORT.put("sharedCrewPlacementCounts", crewCounts());
        REPORT.put("sharedCrewDistinctTargets", unique); REPORT.put("sharedCrewPartialReloadVerified", sharedReloadVerified);
        REPORT.put("sharedCrewInterruption", "Owner's ordinary Spectator permission pause interrupted both native workers; exact per-worker cargo loaded before AI and both resumed real placement");
        REPORT.put("sharedCrewSleepCoverage", "Not exercised: fixture remains at noon; no forced sleeping or native state changes");
        REPORT.put("sharedCrewAttribution", "Read-only actual LivingTick-to-next-living-tick/server-END brackets; exact world transition plus actor material debit plus native guard Completed receipt");
        REPORT.put("sharedCrewVerified", true);
    }

    private static boolean cancelThroughMenu(Minecraft mc) {
        if (menuUiStage == 3) {
            require(mc.screen instanceof ConfirmScreen, "Actual whole-project confirmation is missing");
            // Vanilla ConfirmScreen.keyPressed handles Escape by invoking callback(false), not onClose().
            require(mc.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0), "Confirmation did not handle Escape");
            require(mc.screen instanceof CoreHireScreen && mc.player.containerMenu instanceof CoreHireMenu menu
                    && menu.containerId == menuId, "Escape did not return to the same authenticated core menu");
            KEYBOARD.add(Map.of("control", "Cancel confirmation", "key", "Escape", "returnedWithoutCancelRequest", true));
            menuUiStage = 4; return true; // Server verifies the project is still live before confirmation is reopened.
        }
        if (menuUiStage == 6) {
            require(mc.screen instanceof ConfirmScreen, "Reopened cancellation confirmation is missing");
            keyboardActivate(mc, "Yes");
            require(mc.screen instanceof CoreHireScreen, "Confirm did not return to the authenticated core menu");
            mc.player.closeContainer(); menuUiStage = 7; return true;
        }
        if (!(mc.screen instanceof CoreHireScreen screen) || !(mc.player.containerMenu instanceof CoreHireMenu menu)) return false;
        require(screen.getMenu() == menu && menu.containerId == menuId, "Live cancellation menu client/server identity differs");
        if (menuUiStage == 0) {
            for (int attempts = 0; !NativeBuildingQa.hasVisibleButton(mc, "Building") && attempts < 7; attempts++) NativeBuildingQa.clickVisibleButton(mc, ">");
            keyboardActivate(mc, "Building"); keyboardActivate(mc, "Construction"); menuUiStage = 1;
        } else if (menuUiStage == 1) {
            if (!NativeBuildingQa.hasVisibleButton(mc, "Cancel entire perimeter")) return false;
            require(menu.construction().size() == 1 && projectId.equals(menu.construction().get(0).projectId())
                    && menu.construction().get(0).generation() == acceptedProject.header().generation(), "Displayed cancellation target is not the accepted project");
            requestCapture("02-authenticated-core-cancel.png"); menuUiStage = 2;
        } else if (menuUiStage == 2) {
            keyboardActivate(mc, "Cancel entire perimeter");
            require(mc.screen instanceof ConfirmScreen, "Cancellation confirmation was not opened through the real key handler");
            requestCapture("03-confirm-whole-project-cancel.png"); menuUiStage = 3;
        } else if (menuUiStage == 5) {
            keyboardActivate(mc, "Cancel entire perimeter");
            require(mc.screen instanceof ConfirmScreen, "Keyboard did not reopen the real cancellation confirmation");
            menuUiStage = 6;
        }
        return false;
    }

    private static void keyboardActivate(Minecraft mc, String label) {
        Screen screen = mc.screen; require(screen != null, "No actual screen for keyboard traversal");
        Button target = screen.children().stream().filter(child -> child instanceof Button button && button.visible && button.active
                        && button.getMessage().getString().equals(label)).map(Button.class::cast).findFirst().orElseThrow();
        require(target.getX() >= 0 && target.getY() >= 0 && target.getX() + target.getWidth() <= screen.width
                && target.getY() + target.getHeight() <= screen.height, "Keyboard target is clipped: " + label);
        int tabs = 0; boolean shiftAtStart = Screen.hasShiftDown(), focusMoved = false;
        do {
            // CoreHireScreen reads real Ctrl state; vanilla Tab reads real Shift state. Observe, never fake it.
            require(mc.screen == screen && !Screen.hasControlDown() && !Screen.hasAltDown(), "Held Ctrl/Alt or a changed screen prevents plain Tab acceptance");
            require(tabs++ < 128, "Keyboard target was not reachable within 128 Tab handlers: " + label);
            var previousFocus = screen.getFocused();
            screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, Screen.hasShiftDown() ? GLFW.GLFW_MOD_SHIFT : 0);
            focusMoved |= screen.getFocused() != previousFocus;
            // Vanilla returns false after moving focus, so inspect the actual focused child instead.
        } while (screen.getFocused() != target);
        require(focusMoved && target.visible && target.active && screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0),
                "Focused control did not handle Enter: " + label);
        KEYBOARD.add(Map.of("control", label, "keys", "Tab/Enter", "tabHandlers", tabs, "focusMoved", focusMoved, "shiftDownAtStart", shiftAtStart));
    }

    private static long stagePlaced(ServerLevel level, int index) {
        return acceptedProject.stages().get(index).layout().targets().entrySet().stream().filter(entry -> expected(level, entry.getKey(), entry.getValue())).count();
    }
    private static void recordAuthority(ServerLevel level, String when) {
        REPORT.put("authority-" + when, authority(RaidSavedData.get(level.getServer()).siegeCores.get(coreKey)).toString());
    }
    private static void verifyJoins() {
        var expected = acceptedProject.stages().subList(0, 2).stream().map(part -> part.areaId().toString()).collect(java.util.stream.Collectors.toSet());
        require(AREA_JOINS.stream().map(entry -> entry.get("area")).collect(java.util.stream.Collectors.toSet()).equals(expected)
                && AREA_JOINS.stream().allMatch(entry -> entry.get("marker").equals(originalMarker.toShortString()))
                && AREA_JOINS.stream().filter(entry -> Boolean.FALSE.equals(entry.get("loadedFromDisk"))).count() == 2,
                "Native sections were duplicated, skipped or created at a different physical shovel site");
    }
    private static void verifyCancellation(ServerLevel level, ServerPlayer owner) {
        var snapshot = PerimeterProjectAuthority.snapshot(level, coreKey); var terminal = snapshot.terminal(projectId);
        require(snapshot.get(projectId) == null && terminal != null && terminal.state() == PerimeterProject.State.CANCELED,
                "Expected durable compact CANCELED receipt after actual native cleanup");
        require(terminal.projectId().equals(projectId) && terminal.manifestHash().equals(acceptedProject.manifestHash())
                && terminal.owner().equals(acceptedProject.header().owner()) && terminal.builder().equals(acceptedProject.header().builder())
                && ConstructionEditLedger.get(level).assignedTerminalBuilder(terminal).equals(fixture.builderId())
                && terminal.totalTargetCount() == NativeStagedHandoffFixture.BLOCKS && terminal.claimChunkCount() == 1
                && terminal.activeStage() == 1 && terminal.verifiedStages() == 1
                && terminal.stages().equals(acceptedProject.stages().stream().map(part -> new PerimeterTerminalReceipt.Stage(
                        part.index(), part.areaId(), part.digest(), part.layout().targets().size())).toList()), "Canceled receipt changed identity, full plan or verified first-section evidence");
        verifyPayment(terminal.payment()); verifyJoins();
        var ledger = ConstructionEditLedger.get(level); var worker = builder(level);
        require(areaCount(level) == 0 && worker.currentBuildArea == null && !WorkersBridge.hasActiveBuildArea(worker)
                && !worker.getPersistentData().contains(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID)
                && !com.devfarinsky.siegeoverhaul.core.PerimeterProjectLink.reserved(worker)
                && ProtectedInventoryCleanup.read(worker.getPersistentData()).isEmpty()
                && !ledger.contains(projectId) && acceptedProject.stages().stream().noneMatch(part -> ledger.contains(part.areaId()))
                && ledger.sameGeneration(terminal.cleanup().ledgerGeneration()), "Canceled terminal cleanup left a native marker, builder link or reservation");
        require(placed(level) >= acceptedProject.stages().get(0).layout().targets().size() + nextStagePlaced
                && placed(level) < NativeStagedHandoffFixture.BLOCKS, "Cancellation lost native work or completed the whole plan");
        if (canceledGeometry == null) { canceledGeometry = geometry(level); canceledReceipt = terminal.save(); }
        require(geometry(level).equals(canceledGeometry) && terminal.save().equals(canceledReceipt), "Canceled geometry/receipt changed after cleanup or reopen");
        for (var entry : fixture.nonPlanCells().entrySet()) require(level.getBlockState(entry.getKey()).equals(entry.getValue()),
                "Native job modified a non-plan cell: " + entry.getKey());
        verifyCurrentTerritory(level, NativeStagedHandoffFixture.TERRITORY); conservation(level, owner); observeTreasury(level, core(owner));
        REPORT.put("cancellationAuthority", "CANCELED compact terminal receipt"); REPORT.put("cancellationAuthorityNbt", terminal.save().toString());
        REPORT.put("cancellationCleanup", Map.of("markersGone", true, "builderDetached", true, "reservationsGone", true));
    }

    /** Read-only ordinary server-tick observation; never manually run or stop native goals. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (!ENABLED || finished || fixture == null || event.phase != TickEvent.Phase.END) return;
        if (SHARED_CREW) try { flushWorkObservation(event.getServer().overworld()); }
        catch (Throwable problem) { observerFailure = problem; }
        if (stage != 5 || midRestart) return;
        try {
            var level = event.getServer().overworld(); long now = level.getGameTime();
            if (level.getEntity(fixture.builderId()) instanceof BuilderEntity worker && closed(worker)
                    && (!SHARED_CREW || level.getEntity(sharedHelper) instanceof BuilderEntity helper && closed(helper))) {
                stableTicks = lastStableTick == now - 1 ? stableTicks + 1 : 1; lastStableTick = now;
            } else { stableTicks = 0; lastStableTick = -1; }
        } catch (Throwable problem) { observerFailure = problem; }
    }
    private static boolean closed(BuilderEntity worker) {
        BuilderWorkGoal goal = SHARED_CREW && worker != null ? CREW_GOALS.get(worker.getUUID()) : liveBuildGoal;
        return worker != null && !worker.isNoAi() && workerStock(worker, Items.COBBLESTONE) >= (SHARED_CREW ? 16 : 32)
                && worker.neededItems != null && worker.neededItems.isEmpty()
                && !ProtectedBuilderHandMirror.activeUse(worker) && ProtectedStorageAccess.runningProblem(worker) == null
                && ProtectedInventoryCleanup.read(worker.getPersistentData()).isEmpty()
                && goal != null && goal.getClass() == BuilderWorkGoal.class
                && "PLACE_BLOCKS".equals(String.valueOf(goal.state));
    }
    private static void beginRestart(ServerLevel level, ServerPlayer owner, String kind) {
        if (SHARED_CREW) {
            flushWorkObservation(level); LOADED_CREW.clear();
            var counts = new LinkedHashMap<UUID, Integer>();
            CREW_PLACEMENTS.forEach((id, cells) -> counts.put(id, cells.size())); crewBeforeReload = Map.copyOf(counts);
        }
        reloadKind = kind; pauseStarted = System.nanoTime(); owner.setGameMode(GameType.SPECTATOR);
        pausedGeometry = geometry(level); stopped = null; stoppingFailure = null; loadFailure = null; loadedAuthority = null;
        loadedCargo = null; loadedMain = null; loadedOff = null;
        advance(level.getGameTime(), 10);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void nativeEntityJoined(EntityJoinLevelEvent event) {
        if (!ENABLED || fixture == null || event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof ProtectedBuildArea area) AREA_JOINS.add(Map.of("area", area.getUUID().toString(),
                "marker", area.blockPosition().toShortString(), "loadedFromDisk", event.loadedFromDisk()));
        if (SHARED_CREW && event.getEntity() instanceof BuilderEntity member && CREW_PLACEMENTS.containsKey(member.getUUID())) {
            rememberCrew(member);
            if (restartRequested && stopped != null) LOADED_CREW.put(member.getUUID(), cargo(member));
        }
        if (!(event.getEntity() instanceof BuilderEntity worker) || !worker.getUUID().equals(fixture.builderId())) return;
        liveBuildGoal = worker.goalSelector.getAvailableGoals().stream().map(g -> g.getGoal()).filter(BuilderWorkGoal.class::isInstance)
                .map(BuilderWorkGoal.class::cast).findFirst().orElse(null);
        liveStorageGoal = worker.goalSelector.getAvailableGoals().stream().map(g -> g.getGoal()).filter(GetNeededItemsFromStorage.class::isInstance)
                .map(GetNeededItemsFromStorage.class::cast).findFirst().orElse(null);
        if (restartRequested && stopped != null) {
            loadedCargo = constructionValues(worker.getInventory()); loadedMain = worker.getMainHandItem().save(new CompoundTag());
            loadedOff = worker.getOffhandItem().save(new CompoundTag());
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void serverStopping(ServerStoppingEvent event) {
        if (!ENABLED || !restartRequested || stopped != null || fixture == null) return;
        try {
            ServerLevel level = event.getServer().overworld(); if (SHARED_CREW) flushWorkObservation(level);
            var project = project(level); var worker = builder(level);
            require(geometry(level).equals(pausedGeometry), "Final shutdown changed paused project geometry");
            var terminal = PerimeterProjectAuthority.snapshot(level, coreKey).terminal(projectId);
            require(project != null || terminal != null && terminal.state() == PerimeterProject.State.CANCELED, "Shutdown lost live/terminal authority");
            CompoundTag core = RaidSavedData.get(event.getServer()).siegeCores.get(coreKey); observeTreasury(level, core); conservation(level, null);
            CompoundTag recipe = project != null && project.active() != null && level.getEntity(project.active().areaId()) instanceof ProtectedBuildArea area
                    ? area.getPersistentData().getCompound("SiegeProtectedConstructionV1").copy() : new CompoundTag();
            stopped = new StopSnapshot(geometry(level), chestValues(level), constructionValues(worker.getInventory()), worker.getMainHandItem().save(new CompoundTag()),
                    worker.getOffhandItem().save(new CompoundTag()), authority(core), recipe, ConstructionEditLedger.get(level).generation(), pendingCells(level),
                    placed(level), level.getGameTime(), treasuryObserver.passiveCredits(), project == null ? terminal.state() : project.state(), project == null ? terminal.activeStage() : project.activeStage(), SHARED_CREW ? crewCargo(level) : Map.of());
            REPORT.put("authority-" + reloadKind + "-final-stop", stopped.authority().toString());
        } catch (Throwable problem) { stoppingFailure = problem; }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void serverStarted(ServerStartedEvent event) {
        if (!ENABLED || !restartRequested || stopped == null) return;
        try {
            require(event.getServer().overworld().getGameTime() == stopped.gameTime(), "Reload authority observation was not before resumed ticks");
            loadedAuthority = authority(RaidSavedData.get(event.getServer()).siegeCores.get(coreKey));
            require(loadedAuthority.equals(stopped.authority()), "Saved complete project/journal/Treasury changed before resumed ticks");
        } catch (Throwable problem) { loadFailure = problem; }
    }
    private static CompoundTag authority(CompoundTag core) {
        require(core != null, "Authoritative core missing"); var copy = new CompoundTag();
        for (String key : List.of(PerimeterProjectStore.KEY, PerimeterStageJournal.KEY, "BankEmeralds", "BankInterestAt", "BankInterestRemainder", "CivilianTaxesTotal", "BankLedger"))
            if (core.contains(key)) copy.put(key, core.get(key).copy());
        return copy;
    }
    private static PerimeterProject project(ServerLevel level) { return PerimeterProjectAuthority.snapshot(level, coreKey).get(projectId); }
    private static void verifyPayment(PerimeterProject.PaymentReceipt payment) {
        require(payment != null && payment.projectId().equals(projectId) && payment.generation() == acceptedProject.header().generation()
                && payment.manifestHash().equals(acceptedProject.manifestHash()) && payment.quotedPrice() == 64 && payment.debited() == 64
                && !payment.creative(), "Changed one-time noncreative payment receipt");
    }
    private static void verifyProject(ServerLevel level, PerimeterProject project) {
        require(project.header().equals(acceptedProject.header()) && project.manifestHash().equals(acceptedProject.manifestHash())
                && project.layout().equals(reviewedLayout) && project.targets().equals(acceptedProject.targets()), "Durable global manifest or stage partition changed");
        require(project.targets().keySet().equals(fixture.oracle().targets().keySet())
                && project.clearanceBefore().keySet().equals(fixture.oracle().clearance())
                && project.clearanceBefore().values().stream().allMatch(Blocks.AIR.defaultBlockState()::equals),
                "Accepted new hollow project omitted or reinterpreted protected cavity/headroom AIR");
        NativeStagedHandoffFixture.assertHollowAir(level, fixture.oracle());
        verifyPayment(project.payment());
        var journal = PerimeterStageJournal.get(RaidSavedData.get(level.getServer()).siegeCores.get(coreKey), project);
        require(journal != null && journal.at(0) != null, "Saved native creation journal missing");
        for (var attempt : journal.attempts()) require(attempt.marker().equals(originalMarker)
                && attempt.area().equals(acceptedProject.stages().get(attempt.stage()).areaId()), "Native stage changed the original physical shovel site or stage identity");
        if (project.state() != PerimeterProject.State.COMPLETE) require(ConstructionEditLedger.get(level).matchesProjectReservation(project), "Complete footprint reservation was lost between stages");
        require(areaCount(level) <= 1, "More than one native stage is live concurrently");
        for (var receipt : project.receipts()) {
            var expected = acceptedProject.stages().get(receipt.stageIndex());
            require(receipt.projectId().equals(projectId) && receipt.generation() == acceptedProject.header().generation()
                    && receipt.manifestHash().equals(acceptedProject.manifestHash()) && receipt.areaId().equals(expected.areaId())
                    && receipt.stageDigest().equals(expected.digest()) && receipt.verifiedTargets() == expected.layout().targets().size(), "Changed stage completion receipt");
        }
    }
    private static void verifyActiveStage(ServerLevel level, PerimeterProject project) {
        verifyProject(level, project);
        require(project.active() != null && level.getEntity(project.active().areaId()) instanceof ProtectedBuildArea, "Current paid native stage is missing");
        var area = (ProtectedBuildArea) level.getEntity(project.active().areaId());
        require(area.blockPosition().equals(originalMarker) && NativeConstructionGuard.commissionPaid(area)
                && NativeConstructionGuard.hasReservation(level, area.getUUID()), "Current native marker identity/payment/lease differs");
        var recipe = AcceptedConstructionPlan.load(area.getPersistentData().getCompound("SiegeProtectedConstructionV1"));
        Map<Long, String> cells = new LinkedHashMap<>(); recipe.cells.forEach((pos, value) -> cells.put(pos.asLong(), String.valueOf(ForgeRegistries.BLOCKS.getKey(value.getBlock()))));
        require(cells.equals(project.active().layout().targets()), "Native accepted section differs from exact partition targets");
        var reservation = AcceptedConstructionReservation.load(recipe,
                area.getPersistentData().getCompound("SiegeProtectedConstructionV1").getCompound("Reservation"));
        var reserved = reservation.cells.stream().map(BlockPos::asLong).collect(java.util.stream.Collectors.toSet());
        var clearance = reservation.clearance.keySet().stream().map(BlockPos::asLong).collect(java.util.stream.Collectors.toSet());
        require(reserved.equals(project.active().layout().reservation()) && clearance.equals(project.active().layout().clearance())
                && reservation.clearance.values().stream().allMatch(Blocks.AIR.defaultBlockState()::equals),
                "Native stage reservation omitted or changed the exact hollow cavity/headroom contract");
        for (long cavity : fixture.oracle().cavities()) {
            require(!recipe.cells.containsKey(BlockPos.of(cavity)), "A hollow AIR cavity became a native mutation target");
            if (project.active().layout().clearance().contains(cavity)) require(reservation.clearance.containsKey(BlockPos.of(cavity)),
                    "Active native stage lost a protected hollow cavity");
        }
        REPORT.put("nativeStageHollowReservationVerified-" + project.activeStage(), true);
    }
    private static void verifyCurrentTerritory(ServerLevel level, Set<net.minecraft.world.level.ChunkPos> expected) {
        var actual = RecruitsClaimsBridge.getFactionTerritory(level, NativeStagedHandoffFixture.FACTION, PerimeterTerritory.MAX_CHUNKS);
        require(actual.ready() && actual.chunks().equals(expected), "Actual native territory differs from the exact one claim chunk");
        for (var chunk : expected) require(ClaimEvents.recruitsClaimManager.getClaim(chunk) != null
                && fixture.claimIds().contains(ClaimEvents.recruitsClaimManager.getClaim(chunk).getUUID()), "Native claim index differs");
    }
    private static boolean expected(ServerLevel level, long pos, String material) {
        return material.equals(String.valueOf(ForgeRegistries.BLOCKS.getKey(level.getBlockState(BlockPos.of(pos)).getBlock())));
    }
    private static Map<Long, BlockState> geometry(ServerLevel level) {
        Map<Long, BlockState> cells = new LinkedHashMap<>();
        fixture.plan().blocks().keySet().forEach(p -> cells.put(p, level.getBlockState(BlockPos.of(p))));
        fixture.plan().clearance().forEach(p -> cells.put(p, level.getBlockState(BlockPos.of(p))));
        return Map.copyOf(cells);
    }
    private static Set<Long> pendingCells(ServerLevel level) {
        var cells = new java.util.HashSet<Long>();
        fixture.plan().blocks().forEach((packed, material) -> {
            if (!material.equals(String.valueOf(ForgeRegistries.BLOCKS.getKey(level.getBlockState(BlockPos.of(packed)).getBlock())))) cells.add(packed);
        });
        return Set.copyOf(cells);
    }
    private static List<List<CompoundTag>> chestValues(ServerLevel level) {
        var values = new ArrayList<List<CompoundTag>>();
        for (BlockPos pos : NativeStagedHandoffFixture.CHESTS) {
            Container chest = chest(level, pos); var slots = new ArrayList<CompoundTag>();
            for (int slot = 0; slot < chest.getContainerSize(); slot++) slots.add(chest.getItem(slot).save(new CompoundTag()));
            values.add(List.copyOf(slots));
        }
        return List.copyOf(values);
    }
    private static List<CompoundTag> constructionValues(Container inventory) {
        var values = new ArrayList<CompoundTag>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) values.add(materialValue(inventory.getItem(slot).save(new CompoundTag())));
        return List.copyOf(values);
    }
    private static CompoundTag materialValue(CompoundTag value) {
        ItemStack stack = ItemStack.of(value);
        return stack.is(Items.COBBLESTONE) || stack.is(Items.OAK_PLANKS) ? value.copy() : new CompoundTag();
    }

    private static void conservation(ServerLevel level, ServerPlayer owner) {
        NativeStagedHandoffFixture.assertHollowAir(level, fixture.oracle());
        for (Item material : List.of(Items.COBBLESTONE, Items.OAK_PLANKS)) {
            int supplied = material == Items.COBBLESTONE ? NativeStagedHandoffFixture.COBBLE : NativeStagedHandoffFixture.OAK;
            long built = fixture.plan().blocks().keySet().stream().filter(p -> level.getBlockState(BlockPos.of(p)).getBlock().asItem() == material).count();
            long total = built + chestStock(level, material) + allWorkerStock(level, material)
                    + (owner == null ? 0 : count(owner.getInventory(), material)) + dropped(level, material);
            require(total == supplied, "Finite native stock mismatch for " + material + ": supplied=" + supplied + ", accounted=" + total);
        }
    }

    /** Count the native hand mirror once if it is the identical stack; a split duplicate is an error, not hidden. */
    private static int workerStock(BuilderEntity worker, Item item) {
        Set<ItemStack> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        int count = 0;
        for (int slot = 0; slot < worker.getInventory().getContainerSize(); slot++) {
            ItemStack stack = worker.getInventory().getItem(slot);
            // Cargo slots are independently counted by native inventory APIs; never hide a
            // duplicate cargo alias. Identity de-duplication applies only to equipment mirrors.
            seen.add(stack);
            if (stack.is(item)) count += stack.getCount();
        }
        for (ItemStack stack : List.of(worker.getMainHandItem(), worker.getOffhandItem()))
            if (seen.add(stack) && stack.is(item)) count += stack.getCount();
        return count;
    }
    private static int count(Container container, Item item) {
        int count = 0; for (int i = 0; i < container.getContainerSize(); i++)
            if (container.getItem(i).is(item)) count += container.getItem(i).getCount();
        return count;
    }
    private static int dropped(ServerLevel level, Item item) {
        return level.getEntitiesOfClass(ItemEntity.class, NativeStagedHandoffFixture.BOUNDS,
                entity -> entity.isAlive() && entity.getItem().is(item)).stream().mapToInt(entity -> entity.getItem().getCount()).sum();
    }
    private static long placed(ServerLevel level) {
        return fixture.plan().blocks().entrySet().stream().filter(e -> e.getValue().equals(
                String.valueOf(ForgeRegistries.BLOCKS.getKey(level.getBlockState(BlockPos.of(e.getKey())).getBlock())))).count();
    }
    private static void sample(ServerLevel level, ServerPlayer owner, String when) {
        var sample = new LinkedHashMap<>(diagnostics(level, owner, false)); sample.put("when", when);
        SAMPLES.add(sample);
        FactionLogger.LOG.info("Native staged handoff QA: {}", new GsonBuilder().create().toJson(sample));
    }
    private static Map<String, Object> diagnostics(ServerLevel level, ServerPlayer owner, boolean detailed) {
        var result = new LinkedHashMap<String, Object>(); result.put("stage", stage); result.put("gameTime", level.getGameTime());
        if (fixture == null) return result;
        if (projectId != null) {
            var project = project(level);
            if (project != null) result.put("project", Map.of("state", project.state().name(), "stage", project.activeStage(),
                    "receipts", project.receipts().size(), "revision", project.revision(), "blocker", project.blocker(), "manifest", project.manifestHash()));
            else result.put("project", "compact terminal");
        }
        BuilderEntity builder = builder(level); var goal = liveBuildGoal; var storageGoal = liveStorageGoal;
        result.put("placed", placed(level)); result.put("treasury", owner == null ? -1 : balance(owner));
        if (treasuryObserver != null) result.put("treasuryObservation", treasuryObserver.lastObservation());
        result.put("builderPosition", builder.position().toString()); result.put("builderNoAi", builder.isNoAi());
        var builderData = builder.getPersistentData();
        result.put("selfClearanceRequests", builderData.getInt("SiegeSelfClearanceRequests"));
        if (builderData.contains("SiegeSelfClearanceTarget", net.minecraft.nbt.Tag.TAG_LONG))
            result.put("selfClearanceTarget", BlockPos.of(builderData.getLong("SiegeSelfClearanceTarget")).toShortString());
        result.put("selfClearanceBounds", builderData.getString("SiegeSelfClearanceBounds"));
        result.put("sleeping", builder.needsToSleep()); result.put("followState", builder.getFollowState());
        require(goal != null && storageGoal != null, "Fresh native goal references were unavailable after world load");
        result.put("nativeBuildState", String.valueOf(goal.state)); result.put("nativeTarget", String.valueOf(goal.blockPos));
        result.put("nativeBuildError", String.valueOf(goal.errorMessage));
        result.put("nativeRemaining", builder.currentBuildArea == null ? -1 : builder.currentBuildArea.stackToPlace.size());
        result.put("storageState", String.valueOf(storageGoal.state)); result.put("storageChestTarget", String.valueOf(storageGoal.chestPos));
        result.put("requestedSupplies", passiveRequestedSupplies(builder));
        result.put("cleanupJournal", builder.getPersistentData().getCompound(ProtectedInventoryCleanup.KEY).toString());
        result.put("closedLifecycleTicks", stableTicks);
        result.put("usingItem", builder.isUsingItem());
        result.put("navigationDone", builder.getNavigation().isDone());
        var path = builder.getNavigation().getPath();
        if (path != null) {
            result.put("pathCanReach", path.canReach()); result.put("pathTarget", String.valueOf(path.getTarget()));
            result.put("pathNextIndex", path.getNextNodeIndex()); result.put("pathNodeCount", path.getNodeCount());
            if (detailed) { var nodes = new ArrayList<String>(); for (int i = 0; i < Math.min(128, path.getNodeCount()); i++)
                nodes.add(path.getNode(i).asBlockPos().toShortString()); result.put("pathNodes", nodes); }
        }
        var rawArea = jobId == null ? null : level.getEntity(jobId);
        if (rawArea instanceof ProtectedBuildArea area) {
            result.put("guardStatus", NativeConstructionGuard.status(area)); result.put("nativeQueuesReady", area.nativeQueuesReady());
            result.put("nativeDone", area.isDone()); result.put("commissionPaid", NativeConstructionGuard.commissionPaid(area));
        }
        var storages = new ArrayList<Map<String, Object>>();
        for (UUID id : fixture.storageIds()) if (level.getEntity(id) instanceof StorageArea storage) {
            storages.add(Map.of("id", id.toString(), "containers", storage.storageMap.keySet().stream().map(BlockPos::toShortString).toList(),
                    "allowsBuilder", storage.canWorkHere(builder)));
        }
        result.put("storages", storages);
        result.put("mainHand", stack(builder.getMainHandItem())); result.put("offHand", stack(builder.getOffhandItem()));
        result.put("mainHandSameObjectAsSlot5", builder.getMainHandItem() == builder.getInventory().getItem(5));
        result.put("chestCobble", chestStock(level, Items.COBBLESTONE)); result.put("chestOak", chestStock(level, Items.OAK_PLANKS));
        result.put("builderCobble", allWorkerStock(level, Items.COBBLESTONE)); result.put("builderOak", allWorkerStock(level, Items.OAK_PLANKS));
        result.put("ownerCobble", owner == null ? 0 : count(owner.getInventory(), Items.COBBLESTONE));
        result.put("ownerOak", owner == null ? 0 : count(owner.getInventory(), Items.OAK_PLANKS));
        if (SHARED_CREW) {
            result.put("sharedCrewPlacementCounts", crewCounts());
            result.put("sharedCrewDiagnostics", crewDiagnostics(level));
        }
        result.put("looseCobble", dropped(level, Items.COBBLESTONE)); result.put("looseOak", dropped(level, Items.OAK_PLANKS));
        result.put("placedCobble", fixture.plan().blocks().keySet().stream().filter(p -> level.getBlockState(BlockPos.of(p)).is(Blocks.COBBLESTONE)).count());
        result.put("placedOak", fixture.plan().blocks().keySet().stream().filter(p -> level.getBlockState(BlockPos.of(p)).is(Blocks.OAK_PLANKS)).count());
        if (detailed) {
            result.put("builderUuid", builder.getUUID().toString());
            result.put("builderBounds", builder.getBoundingBox().toString());
            result.put("builderWidth", builder.getBbWidth()); result.put("builderHeight", builder.getBbHeight());
            result.put("registeredBuilderWidth", builder.getType().getDimensions().width);
            result.put("registeredBuilderHeight", builder.getType().getDimensions().height);
            var mutationCells = NativeConstructionGuard.mutationCells(goal);
            result.put("nativeMutationCandidates", mutationCells.stream().map(BlockPos::toShortString).toList());
            var occupants = new ArrayList<Map<String, Object>>();
            var footprint = new net.minecraft.world.phys.AABB(fixture.plan().min(), fixture.plan().max().offset(1, 1, 1));
            for (var entity : level.getEntities((net.minecraft.world.entity.Entity) null, footprint,
                    net.minecraft.world.entity.Entity::isAlive)) {
                var occupant = new LinkedHashMap<String, Object>();
                occupant.put("uuid", entity.getUUID().toString());
                occupant.put("type", String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType())));
                occupant.put("position", entity.position().toString()); occupant.put("bounds", entity.getBoundingBox().toString());
                occupant.put("isTestBuilder", entity == builder); occupant.put("isOwner", entity == owner);
                occupant.put("isNativeArea", entity == rawArea);
                occupant.put("isFixtureAuxiliary", fixture.parkedAuxiliaries().contains(entity.getUUID().toString()));
                occupant.put("blocksPlacement", NativeConstructionGuard.blocksPlacement(entity));
                if (entity instanceof net.minecraft.world.entity.Mob mob) occupant.put("noAi", mob.isNoAi());
                occupant.put("intersectedMutationCells", mutationCells.stream()
                        .filter(cell -> new net.minecraft.world.phys.AABB(cell).intersects(entity.getBoundingBox()))
                        .map(BlockPos::toShortString).toList());
                occupant.put("intersectedPendingCells", fixture.plan().blocks().entrySet().stream()
                        .filter(entry -> !entry.getValue().equals(String.valueOf(ForgeRegistries.BLOCKS.getKey(
                                level.getBlockState(BlockPos.of(entry.getKey())).getBlock()))))
                        .filter(entry -> new net.minecraft.world.phys.AABB(BlockPos.of(entry.getKey())).intersects(entity.getBoundingBox()))
                        .limit(32).map(entry -> BlockPos.of(entry.getKey()).toShortString()).toList());
                occupants.add(occupant);
                if (occupants.size() >= 64) break;
            }
            result.put("footprintEntities", occupants);
            var inventory = new ArrayList<Map<String, Object>>();
            for (int slot = 0; slot < builder.getInventory().getContainerSize(); slot++) {
                var value = new LinkedHashMap<>(stack(builder.getInventory().getItem(slot))); value.put("slot", slot); inventory.add(value);
            }
            result.put("inventorySlots", inventory);
            result.put("missingTargets", fixture.plan().blocks().entrySet().stream().filter(e -> !e.getValue().equals(
                    String.valueOf(ForgeRegistries.BLOCKS.getKey(level.getBlockState(BlockPos.of(e.getKey())).getBlock()))))
                    .limit(128).map(e -> Map.of("position", BlockPos.of(e.getKey()).toShortString(), "expected", e.getValue(),
                            "actual", level.getBlockState(BlockPos.of(e.getKey())).toString())).toList());
            BlockPos target = goal.blockPos == null ? builder.blockPosition() : goal.blockPos;
            var surroundings = new ArrayList<Map<String, String>>();
            for (BlockPos cell : BlockPos.betweenClosed(target.offset(-2, -2, -2), target.offset(2, 2, 2)))
                surroundings.add(Map.of("position", cell.toShortString(), "state", level.getBlockState(cell).toString()));
            result.put("targetNeighborhood", surroundings);
        }
        return result;
    }
    /** Passive, bounded server-thread snapshots. Never dispatch a goal, renew a claim or drain cleanup. */
    private static List<Map<String, Object>> crewDiagnostics(ServerLevel level) {
        var members = new ArrayList<Map<String, Object>>();
        var project = projectId == null ? null : project(level);
        var ledger = ConstructionEditLedger.get(level);
        for (UUID id : CREW_PLACEMENTS.keySet().stream().limit(4).toList()) {
            var member = new LinkedHashMap<String, Object>(); members.add(member); member.put("worker", id.toString());
            try {
                if (project != null) {
                    member.put("assignedCoordinator", ledger.assignedBuilder(project).equals(id));
                    member.put("activeMember", ledger.crewMember(project, id));
                    member.put("pendingMember", ledger.pendingCrewMember(project, id));
                    member.put("knownMember", ledger.knownCrewMember(project, id));
                }
                var entity = level.getEntity(id); member.put("loaded", entity != null);
                if (!(entity instanceof BuilderEntity worker)) continue;
                var data = worker.getPersistentData(); var goal = CREW_GOALS.get(id); var storage = CREW_STORAGE.get(id);
                var area = worker.currentBuildArea;
                member.put("alive", worker.isAlive()); member.put("position", worker.position().toString());
                member.put("tickCount", worker.tickCount); member.put("followState", worker.getFollowState());
                member.put("sleeping", worker.needsToSleep()); member.put("noAi", worker.isNoAi());
                member.put("owner", String.valueOf(WorkersBridge.readWorkerOwner(worker)));
                member.put("usingItem", worker.isUsingItem()); member.put("combatTarget", String.valueOf(worker.getTarget()));
                member.put("passenger", worker.isPassenger()); member.put("leashed", worker.isLeashed());
                member.put("projectSelector", data.getCompound(com.devfarinsky.siegeoverhaul.core.PerimeterProjectLink.KEY).toString());
                member.put("childReceipt", String.valueOf(data.get("SiegeProtectedAreaReceipt")));
                member.put("childGeneration", String.valueOf(data.get("SiegeProtectedLedgerGeneration")));
                member.put("handLifecycle", data.getCompound(ProtectedBuilderHandLifecycle.KEY).toString());
                member.put("handLifecycleMatches", ProtectedBuilderHandLifecycle.matches(data, id, ledger));
                member.put("handReceiptValid", NativeConstructionGuard.validHandReceipt(data, ledger));
                member.put("handPending", ProtectedBuilderHandMirror.pending(data));
                member.put("handReviewNeeded", ProtectedBuilderHandMirror.reviewNeeded(data));
                member.put("cleanupJournal", data.getCompound(ProtectedInventoryCleanup.KEY).toString());
                member.put("workerGuardStatus", NativeConstructionGuard.status(worker));
                member.put("requestedSupplies", passiveRequestedSupplies(worker));
                member.put("mainHand", stack(worker.getMainHandItem())); member.put("offHand", stack(worker.getOffhandItem()));
                member.put("mainHandSameObjectAsSlot5", worker.getMainHandItem() == worker.getInventory().getItem(5));
                member.put("currentArea", area == null ? "null" : area.getUUID().toString());
                member.put("currentAreaIsLoadedObject", area != null && level.getEntity(area.getUUID()) == area);
                member.put("currentAreaRemoved", area != null && area.isRemoved());
                member.put("storageState", storage == null ? "missing" : String.valueOf(storage.state));
                member.put("storageTarget", storage == null ? "missing" : String.valueOf(storage.chestPos));
                member.put("nativeGoalWorkerMatches", goal != null && goal.builderEntity == worker);
                member.put("wrappedNativeGoalMatches", goal != null &&
                        com.devfarinsky.siegeoverhaul.core.WallBuilderAccess.protectedNativeGoal(worker) == goal);
                member.put("nativeState", goal == null ? "missing" : String.valueOf(goal.state));
                member.put("nativeTarget", goal == null ? "missing" : String.valueOf(goal.blockPos));
                member.put("nativeError", goal == null ? "missing" : String.valueOf(goal.errorMessage));
                member.put("navigationDone", worker.getNavigation().isDone());
                var path = worker.getNavigation().getPath();
                if (path != null) member.put("path", Map.of("target", String.valueOf(path.getTarget()),
                        "canReach", path.canReach(), "next", path.getNextNodeIndex(), "nodes", path.getNodeCount()));
                var wrappedGoals = new ArrayList<Map<String, Object>>();
                for (var wrapped : worker.goalSelector.getAvailableGoals()) {
                    if (wrappedGoals.size() >= 32) break;
                    var wrappedGoal = wrapped.getGoal();
                    var state = new LinkedHashMap<String, Object>();
                    state.put("class", wrappedGoal.getClass().getSimpleName()); state.put("running", wrapped.isRunning());
                    state.put("priority", wrapped.getPriority()); state.put("flags", String.valueOf(wrappedGoal.getFlags()));
                    if (wrappedGoal instanceof com.devfarinsky.siegeoverhaul.core.WallBuilderAccess) {
                        for (String name : List.of("lastTarget", "destination", "nextSearch", "nextRoute", "pendingUntil", "approachTarget"))
                            state.put(name, String.valueOf(diagnosticField(wrappedGoal.getClass(), wrappedGoal, name)));
                        Object pendingPath = diagnosticField(wrappedGoal.getClass(), wrappedGoal, "pendingPath");
                        state.put("pendingPathClass", pendingPath == null ? "null" : pendingPath.getClass().getName());
                    }
                    wrappedGoals.add(state);
                }
                member.put("goals", wrappedGoals);
                if (goal != null && area instanceof ProtectedBuildArea protectedArea) {
                    var receipt = area.getPersistentData().getCompound("SiegeProtectedConstructionV1");
                    var accepted = AcceptedConstructionPlan.load(receipt);
                    var completed = java.util.Arrays.stream(receipt.getLongArray("Completed")).boxed()
                            .collect(java.util.stream.Collectors.toSet());
                    member.put("areaGuardStatus", NativeConstructionGuard.status(area));
                    member.put("acceptedCount", accepted.cells.size()); member.put("completedCount", completed.size());
                    member.put("rawQueueProblem", String.valueOf(NativeConstructionGuard.nativeStacksProblem(area, goal, accepted)));
                    member.put("sharedQueueCount", area.stackToPlace == null ? -1 : area.stackToPlace.size());
                    member.put("privateQueueCount", goal.stackToPlace == null ? -1 : goal.stackToPlace.size());
                    member.put("placementQueueAliased", goal.stackToPlace == (Object) area.stackToPlace
                            || goal.stackToPlace == (Object) area.stackToPlaceMultiBlock || goal.stackToPlace == area.stackToBreak
                            || goal.stackToPlace == area.stackToFree || goal.stackToPlace == goal.stackToBreak || goal.stackToPlace == goal.stackToFree);
                    var pendingCells = new java.util.HashSet<BlockPos>(); int duplicateShared = 0, changedShared = 0;
                    member.put("queueInspectionLimit", SECTION_TARGET_CAP + 1);
                    member.put("queueInspectionTruncated", area.stackToPlace != null && area.stackToPlace.size() > SECTION_TARGET_CAP + 1
                            || goal.stackToPlace != null && goal.stackToPlace.size() > SECTION_TARGET_CAP + 1);
                    if (area.stackToPlace != null) for (var cell : area.stackToPlace.stream().limit(SECTION_TARGET_CAP + 1).toList()) {
                        if (cell == null || cell.getPos() == null) { changedShared++; continue; }
                        if (!pendingCells.add(cell.getPos())) duplicateShared++;
                        if (!java.util.Objects.equals(cell.getState(), accepted.cells.get(cell.getPos()))) changedShared++;
                    }
                    member.put("duplicateSharedPositions", duplicateShared); member.put("changedSharedEntries", changedShared);
                    var privateCells = new ArrayList<BlockPos>();
                    if (goal.stackToPlace != null) privateCells.addAll(goal.stackToPlace.stream().limit(SECTION_TARGET_CAP + 1).toList());
                    member.put("duplicatePrivatePositions", privateCells.size() - new java.util.HashSet<>(privateCells).size());
                    if (goal.blockPos != null && !privateCells.contains(goal.blockPos)) privateCells.add(goal.blockPos);
                    var rejections = new ArrayList<Map<String, Object>>(); int rejectedCount = 0;
                    for (BlockPos cell : privateCells) {
                        BlockState expected = cell == null ? null : accepted.cells.get(cell);
                        boolean loaded = cell != null && level.hasChunkAt(cell);
                        BlockState actual = loaded ? level.getBlockState(cell) : null;
                        boolean exact = expected != null && expected.equals(actual);
                        if (cell != null && expected != null && loaded && actual != null
                                && (pendingCells.contains(cell) && !completed.contains(cell.asLong()) || exact)) continue;
                        rejectedCount++;
                        if (rejections.size() < 16) rejections.add(Map.of("cell", String.valueOf(cell), "loaded", loaded,
                                "expected", String.valueOf(expected), "actual", String.valueOf(actual),
                                "pending", pendingCells.contains(cell), "completed", cell != null && completed.contains(cell.asLong())));
                    }
                    member.put("schedulerRejectedCellCount", rejectedCount); member.put("schedulerRejectedCells", rejections);
                    member.put("privateQueueTail", privateCells.stream().skip(Math.max(0, privateCells.size() - 8)).map(String::valueOf).toList());
                    member.put("claims", crewClaimDiagnostics(worker, protectedArea));
                }
            } catch (ReflectiveOperationException | RuntimeException unavailable) { member.put("diagnosticError", unavailable.toString()); }
        }
        return members;
    }

    private static List<Map<String, Object>> passiveRequestedSupplies(BuilderEntity worker) {
        var requests = new ArrayList<Map<String, Object>>();
        try {
            for (var request : worker.neededItems.stream().limit(8).toList()) {
                Object key = diagnosticField(request.getClass(), request, "cachedMatchKey");
                requests.add(Map.of("count", request.count, "required", request.required,
                        "sourceKey", passiveRequestKey(request.sourceKey), "cachedMatchKey", passiveRequestKey(key),
                        "matchKeyResolved", diagnosticField(request.getClass(), request, "matchKeyResolved")));
            }
        } catch (ReflectiveOperationException | RuntimeException unavailable) { requests.add(Map.of("diagnosticError", unavailable.toString())); }
        return requests;
    }
    private static String passiveRequestKey(Object value) {
        if (value == null) return "null";
        if (value instanceof Item item) return String.valueOf(ForgeRegistries.ITEMS.getKey(item));
        if (value instanceof UUID || value instanceof String || value instanceof ResourceLocation) return value.toString();
        return "uninspected:" + value.getClass().getName();
    }

    /** Reflective inspection is test-only and read-only; never calls claim/tick/renew/release. */
    private static Map<String, Object> crewClaimDiagnostics(BuilderEntity worker, ProtectedBuildArea area)
            throws ReflectiveOperationException {
        var result = new LinkedHashMap<String, Object>();
        var sessions = (Map<?, ?>) diagnosticField(NativeCrewWorkClaims.class, null, "SESSIONS");
        var held = (Map<?, ?>) diagnosticField(NativeCrewWorkClaims.class, null, "HELD");
        var closed = (Set<?>) diagnosticField(NativeCrewWorkClaims.class, null, "CLOSED");
        Object session = sessions.get(area), member = held.get(worker);
        result.put("closed", closed.contains(area)); result.put("sessionPresent", session != null);
        result.put("heldPresent", member != null);
        if (member != null) {
            var token = (SharedConstructionWorkClaims.Token) diagnosticField(member.getClass(), member, "token");
            result.put("heldTarget", BlockPos.of(token.target()).toShortString());
            result.put("heldSessionMatches", diagnosticField(member.getClass(), member, "session") == session);
            result.put("heldGoalMatches", ((java.lang.ref.WeakReference<?>) diagnosticField(member.getClass(), member, "goal")).get() == CREW_GOALS.get(worker.getUUID()));
        }
        if (session != null) {
            Object claims = diagnosticField(session.getClass(), session, "claims");
            result.put("lastGameTick", diagnosticField(claims.getClass(), claims, "lastGameTick"));
            var leases = (Map<?, ?>) diagnosticField(claims.getClass(), claims, "workers");
            var entries = new ArrayList<Map<String, Object>>();
            for (var lease : leases.entrySet().stream().limit(4).toList()) {
                Object value = lease.getValue();
                var token = (SharedConstructionWorkClaims.Token) diagnosticField(value.getClass(), value, "token");
                entries.add(Map.of("worker", String.valueOf(lease.getKey()), "target", BlockPos.of(token.target()).toShortString(),
                        "renewedAt", diagnosticField(value.getClass(), value, "renewedAt")));
            }
            result.put("leases", entries);
        }
        return result;
    }
    private static Object diagnosticField(Class<?> type, Object instance, String name) throws ReflectiveOperationException {
        var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(instance);
    }

    private static Map<String, Object> stack(ItemStack stack) {
        return Map.of("item", String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem())), "count", stack.getCount(),
                "tag", stack.hasTag() ? stack.getTag().toString() : "");
    }
    private static BuilderEntity builder(ServerLevel level) {
        require(level.getEntity(fixture.builderId()) instanceof BuilderEntity, "Native builder disappeared");
        return (BuilderEntity) level.getEntity(fixture.builderId());
    }
    private static Container chest(ServerLevel level, BlockPos pos) {
        require(level.getBlockEntity(pos) instanceof Container, "Native stock chest disappeared: " + pos);
        return (Container) level.getBlockEntity(pos);
    }
    private static int chestStock(ServerLevel level, Item material) {
        return NativeStagedHandoffFixture.CHESTS.stream().mapToInt(pos -> count(chest(level, pos), material)).sum();
    }
    private static int areaCount(ServerLevel level) { return level.getEntitiesOfClass(ProtectedBuildArea.class, NativeStagedHandoffFixture.BOUNDS, e -> e.isAlive()).size(); }
    private static CompoundTag core(ServerPlayer owner) {
        var tag = RaidSavedData.get(owner.server).siegeCores.get(SiegeCore.key(owner)); require(tag != null, "Core treasury missing"); return tag;
    }
    private static long balance(ServerPlayer owner) { return FactionBank.balance(core(owner)); }
    private static int bankRate(CompoundTag core) {
        return FactionBank.interestRate(core, com.devfarinsky.siegeoverhaul.RaidConfig.BANK_INTEREST_BASIS_POINTS.get());
    }
    private static void observeTreasury(ServerLevel level, CompoundTag core) {
        require(treasuryObserver != null, "Treasury observer was not initialized after the exact fee");
        treasuryObserver.observe(core, bankRate(core), level.getGameTime());
    }
    private static void initialize(Minecraft mc) throws Exception {
        String configured = System.getProperty("siegeoverhaul.nativeQa.directory", "");
        require(!configured.isBlank(), "Missing isolated QA directory");
        directory = Path.of(configured).toAbsolutePath().normalize();
        require(directory.endsWith(Path.of("build", "native-handoff-qa", "client"))
                && mc.gameDirectory.toPath().toRealPath().equals(directory.toRealPath()), "Unsafe active QA game directory");
        evidence = directory.getParent().resolve("evidence"); Files.createDirectories(evidence);
        REPORT.put("treasuryObserverContracts", NativeQaTreasuryContracts.verify());
        REPORT.put("startedUtc", Instant.now().toString()); REPORT.put("mode", "staged-handoff");
        REPORT.put("constructionLimitSeconds", CONSTRUCTION_SECONDS);
        REPORT.put("totalLimitSeconds", TOTAL_SECONDS);
        REPORT.put("geometryProfile", "hollow-five-wide-one-claim");
        REPORT.put("geometrySourceCommit", NativeStagedHandoffFixture.GEOMETRY_SOURCE);
        REPORT.put("legacySolidRecords", "Not recompiled or reinterpreted by QA; unchanged production saved-plan semantics remain authoritative");
        REPORT.put("syntheticPartition", true); REPORT.put("qaSectionTargetCap", SECTION_TARGET_CAP);
        REPORT.put("commissionPath", "Public NativePerimeterProjects.start direct server commission; no normal review-menu/plan-use packet coverage in this mode");
        REPORT.put("scope", "Complete 572-target one-claim plan, exact finite 352 cobblestone plus 220 oak, " + (SHARED_CREW ? "two exactly owned native builders" : "one native builder") + ", unchanged native goals/ticks/materials. QA-only bounded target partition uses unchanged native blueprint serializer checks. Actual partial first-stage save/close/reopen, native first-section completion and controller handoff at the original shovel site, native next-section placement, authenticated real core-menu cancellation, and canceled close/reopen. Between-stage close/reopen only when its ordinary spectator pause boundary is cleanly observed.");
        REPORT.put("notCovered", List.of("Whole-plan completion", "Migration or reinterpretation of existing accepted solid records", "Naturally occurring production partition sizes", "Normal commission review-menu and plan-use packet path", "Dedicated-client networking", "Unloaded-owner absence", "Uneven/disjoint/holed territory or other material palettes", "Storage farther than native reach", "Arbitrary shader/GPU combinations"));
        var mods = new LinkedHashMap<String, String>(); var artifacts = new LinkedHashMap<String, Object>();
        for (String id : List.of("minecraft", "forge", "siegeoverhaul", "workers", "recruits", "smallships", "siegeweapons")) {
            var container = ModList.get().getModContainerById(id); require(container.isPresent(), "Required real companion missing: " + id);
            var info = container.orElseThrow().getModInfo(); mods.put(id, info.getVersion().toString());
            if (List.of("workers", "recruits", "smallships", "siegeweapons").contains(id)) {
                Path jar = info.getOwningFile().getFile().getFilePath(); require(Files.isRegularFile(jar), "Cannot hash real companion: " + id);
                artifacts.put(id, Map.of("fileName", jar.getFileName().toString(), "sha256", sha256(jar), "kind", "remapped development runtime, not release bytes"));
            }
        }
        require("2.0.3".equals(mods.get("workers")) && "1.15.2".equals(mods.get("recruits")), "Unreviewed native API versions");
        require(!(SHARED_CREW && REPLACE_BUILDER), "Shared crew and replacement are separate acceptance scenarios");
        REPORT.put("sharedCrewRequested", SHARED_CREW);
        if (SHARED_CREW) {
            REPORT.put("sharedCrewEvents", CREW_EVENTS); // Retain partial evidence in a failing run too.
            REPORT.put("sourceCommit", Files.readString(evidence.resolve("source-commit.txt")).trim());
            REPORT.put("sourceTree", Files.readString(evidence.resolve("source-tree.txt")).trim());
            REPORT.put("sharedCrewScope", "Two exact-owner builders; one unchanged finite chest; explicit legacy hollow fixture geometry and synthetic partition; normal native ticks; read-only per-living-tick world/material observation; both builders interrupt/reload/resume before coordinator death; surviving enlisted helper continues immutable paid project. No forced sleep, target writes, native goal calls, receipt creation or speed changes.");
        }
        REPORT.put("builderReplacementRequested", REPLACE_BUILDER);
        REPORT.put("loadedModVersions", mods); REPORT.put("loadedCompanionArtifacts", artifacts);
        REPORT.put("nativeApiClasses", Map.of("builder", BuilderEntity.class.getName(), "buildGoal", BuilderWorkGoal.class.getName(),
                "storageGoal", GetNeededItemsFromStorage.class.getName(), "storageArea", StorageArea.class.getName(),
                "nativeClaim", RecruitsClaim.class.getName(), "protectedArea", ProtectedBuildArea.class.getName(),
                "builderClassLoader", String.valueOf(BuilderEntity.class.getClassLoader())));
        REPORT.put("openGlVendor", GL11.glGetString(GL11.GL_VENDOR)); REPORT.put("openGlRenderer", GL11.glGetString(GL11.GL_RENDERER));
    }
    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent event) {
        if (!ENABLED || finished || event.phase != TickEvent.Phase.END) return;
        renderFrames++;
        if (capture == null || renderFrames < captureFrame) return;
        Minecraft mc = Minecraft.getInstance();
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            require(image.getWidth() >= 640 && image.getHeight() >= 360, "Framebuffer too small");
            int first = image.getPixelRGBA(0, 0), changed = 0;
            for (int y = 0; y < image.getHeight(); y += 16) for (int x = 0; x < image.getWidth(); x += 16)
                if (image.getPixelRGBA(x, y) != first) changed++;
            require(changed > 50, "Blank framebuffer");
            image.writeToFile(evidence.resolve(capture)); SHOTS.add(capture); capture = null;
        } catch (Throwable problem) { capture = null; if (failure == null) failure = problem; finishing = true; }
    }
    private static void requestCapture(String name) { capture = name; captureFrame = renderFrames + 3; captureRequested = System.nanoTime(); }
    private static void aim(Minecraft mc, Vec3 target) {
        Vec3 delta = target.subtract(mc.player.getEyePosition());
        float yaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90);
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)));
        mc.player.setYRot(yaw); mc.player.yRotO = yaw; mc.player.setXRot(pitch); mc.player.xRotO = pitch;
    }
    private static String sha256(Path path) throws Exception {
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        try (var stream = Files.newInputStream(path)) { byte[] buffer = new byte[65536]; int count;
            while ((count = stream.read(buffer)) >= 0) digest.update(buffer, 0, count); }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }
    private static void fail(Minecraft mc, Throwable problem) {
        if (finishing || finished) return;
        failure = problem; finishing = true;
        FactionLogger.LOG.error("Native staged-perimeter QA failed in stage {}", stage, problem);
        if (mc.level != null && evidence != null) requestCapture("failure-native-staged-handoff.png"); else finish(mc);
    }
    private static void finish(Minecraft mc) {
        if (finished) return; finished = true;
        REPORT.put("status", failure == null ? "passed" : "failed"); REPORT.put("stage", stage);
        REPORT.put("finishedUtc", Instant.now().toString()); REPORT.put("elapsedSeconds", (System.nanoTime() - started) / (double) SECOND); REPORT.put("assertions", List.copyOf(CHECKS));
        REPORT.put("restarts", List.copyOf(RESTARTS)); REPORT.put("nativeStageJoins", List.copyOf(AREA_JOINS));
        REPORT.put("samples", List.copyOf(SAMPLES)); REPORT.put("screenshots", List.copyOf(SHOTS));
        REPORT.put("keyboardInputScope", "Real Screen.keyPressed handlers and runtime modifier observation; no physical OS input, key-state changes or QA focus setters");
        REPORT.put("keyboardInteractions", List.copyOf(KEYBOARD));
        if (treasuryObserver != null) REPORT.put("treasuryAccounting", treasuryObserver.evidence());
        if (failure != null) REPORT.put("failure", failure.toString());
        try { if (evidence != null) Files.writeString(evidence.resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(REPORT)); }
        catch (Exception writeFailure) { FactionLogger.LOG.error("Could not write staged-handoff evidence", writeFailure); }
        FactionLogger.LOG.info("Native staged-perimeter QA {}", failure == null ? "passed" : "failed"); mc.stop();
    }
    private static void advance(long now, int next) { stage = next; stageTick = now; }
    private static void check(String message) { CHECKS.add(message); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
