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
import net.minecraft.world.phys.BlockHitResult;
import com.devfarinsky.siegeoverhaul.ModConstants;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import com.devfarinsky.siegeoverhaul.compat.WorkersConstructionView;
import com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge;
import com.devfarinsky.siegeoverhaul.core.PerimeterTerritory;
import com.talhanation.workers.entities.ai.BuilderWorkGoal;
import com.talhanation.workers.entities.ai.GetNeededItemsFromStorage;
import com.talhanation.workers.world.BuildBlock;
import com.devfarinsky.siegeoverhaul.core.FactionBank;
import com.devfarinsky.siegeoverhaul.core.PerimeterConstruction;
import com.devfarinsky.siegeoverhaul.core.PerimeterPreview;
import com.devfarinsky.siegeoverhaul.core.SiegeCore;
import com.devfarinsky.siegeoverhaul.items.ModItems;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import com.talhanation.recruits.ClaimEvents;
import com.talhanation.recruits.pathfinding.AsyncPathNavigation;
import com.talhanation.recruits.world.RecruitsClaim;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.entities.ai.navigation.WorkerPathNavigation;
import com.talhanation.workers.entities.ai.navigation.WorkersAsyncPathfinder;
import com.talhanation.workers.entities.ai.navigation.WorkersGroundPathNavigation;
import com.talhanation.workers.entities.workarea.StorageArea;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
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
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** One isolated paid stepped/gated project, real native stages and two complete client-world restarts. */
@Mod.EventBusSubscriber(modid = SiegeOverhaul.MOD_ID, value = Dist.CLIENT)
public final class NativeStagedPerimeterQa {
    private static final boolean ENABLED = Boolean.getBoolean("siegeoverhaul.nativeQa")
            && "staged-perimeter".equals(System.getProperty("siegeoverhaul.nativeQa.mode"));
    private static final long SECOND = 1_000_000_000L, CONSTRUCTION_SECONDS = 180 * 60, TOTAL_SECONDS = 195 * 60;
    private static final Map<String, Object> REPORT = new LinkedHashMap<>();
    private static final List<Map<String, Object>> SAMPLES = new ArrayList<>(), RESTARTS = new ArrayList<>(), AREA_JOINS = new ArrayList<>();
    private static final List<String> CHECKS = new ArrayList<>(), SHOTS = new ArrayList<>();
    private static final Set<Integer> BUILDER_FEET_Y = new TreeSet<>();
    private static NativeStagedPerimeterFixture.Fixture fixture;
    private static CompletableFuture<Action> pending;
    private static Path directory, evidence;
    private static UUID playerId, projectId, jobId;
    private static String coreKey, reloadKind, hudFaction;
    private static long started, constructionStarted, lastProgress, lastSampleTick = -1, placedBefore = -1, stageTick;
    private static long lastCoreOpenAttemptTick;
    private static long captureRequested, pauseStarted, pausedNanos;
    private static int clientPhase, stage, renderFrames, captureFrame, menuId, menuUiStage, coreOpenAttempts;
    private static boolean finished, finishing, midRestart, betweenRestart, restartRequested;
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
                                PerimeterProject.State state, int activeStage) {}
    private enum Action { NONE, OPEN_CORE, LIVE_MENU, CAPTURE_PLAN, USE_PLAN, RELOAD, CAPTURE_COMPLETE, DONE }
    private NativeStagedPerimeterQa() {}

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (!ENABLED || finished || fixture == null || event.phase != TickEvent.Phase.END) return;
        try {
            var entity = event.getServer().overworld().getEntity(fixture.builderId());
            if (entity instanceof BuilderEntity builder && !builder.isNoAi())
                BUILDER_FEET_Y.add(builder.blockPosition().getY());
        } catch (Throwable ignored) {}
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (finishing) { if (capture == null || System.nanoTime() - captureRequested > 20 * SECOND) finish(mc); return; }
            if (started == 0) started = System.nanoTime();
            require(System.nanoTime() - started < TOTAL_SECONDS * SECOND, "Staged client exceeded the 195-minute total bound");
            if (capture != null) return;
            if (clientPhase == 0) {
                if (!(mc.screen instanceof TitleScreen)) return;
                initialize(mc);
                require(!Files.exists(directory.resolve("saves").resolve(NativeStagedPerimeterFixture.WORLD)), "Refusing an existing staged QA world");
                mc.options.renderDistance().set(6); mc.options.simulationDistance().set(6);
                mc.options.guiScale().set(2); mc.options.pauseOnLostFocus = false; mc.resizeDisplay();
                GameRules rules = new GameRules();
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
                clientPhase = 1;
                mc.createWorldOpenFlows().createFreshLevel(NativeStagedPerimeterFixture.WORLD,
                        new LevelSettings(NativeStagedPerimeterFixture.WORLD, GameType.SURVIVAL, false, Difficulty.PEACEFUL,
                                false, rules, WorldDataConfiguration.DEFAULT), new WorldOptions(20261007L, false, false),
                        NativeStagedPerimeterFixture::dimensions);
                return;
            }
            if (clientPhase == 2) {
                if (mc.getSingleplayerServer() != null || !(mc.screen instanceof TitleScreen)) return;
                require(stoppingFailure == null && stopped != null, "Final stopping snapshot unavailable: " + stoppingFailure);
                clientPhase = 1; mc.createWorldOpenFlows().loadLevel(new TitleScreen(), NativeStagedPerimeterFixture.WORLD); return;
            }
            if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null
                    || mc.screen != null && !(mc.screen instanceof CoreHireScreen)) return;
            if (playerId == null) playerId = mc.player.getUUID();
            if (pending != null) {
                if (!pending.isDone()) return;
                Action action = pending.join(); pending = null;
                switch (action) {
                    case OPEN_CORE -> {
                        if (!(mc.screen instanceof CoreHireScreen)) {
                            aim(mc, Vec3.atCenterOf(NativeStagedPerimeterFixture.CORE));
                            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                                    new BlockHitResult(Vec3.atCenterOf(NativeStagedPerimeterFixture.CORE).add(0, .5, 0), Direction.UP,
                                            NativeStagedPerimeterFixture.CORE, false));
                        }
                    }
                    case LIVE_MENU -> { if (!reviewThroughMenu(mc)) { pending = CompletableFuture.completedFuture(action); return; } }
                    case CAPTURE_PLAN -> {
                        aim(mc, new Vec3(168, 68, 1)); requestCapture("02-free-stepped-gated-perimeter-review.png"); return;
                    }
                    case USE_PLAN -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                    case RELOAD -> { mc.level.disconnect(); mc.clearLevel(); mc.setScreen(new TitleScreen()); clientPhase = 2; return; }
                    case CAPTURE_COMPLETE -> {
                        aim(mc, new Vec3(168, 67, 40));
                        if (!mc.levelRenderer.isChunkCompiled(new BlockPos(128, 65, 0))
                                || !mc.levelRenderer.isChunkCompiled(new BlockPos(207, 69, 79))) {
                            pending = CompletableFuture.completedFuture(action); return;
                        }
                        REPORT.put("completedCaptureTerrainCompiled", true);
                        requestCapture("03-native-completed-staged-perimeter.png"); return;
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
        if (treasuryObserver != null) observeTreasury(level, core(owner));
        if (stage != 5 && stage != 12 && stage > 0) require(now - stageTick < 3600, "Bounded setup/reload phase timed out: " + stage);
        PerimeterProject project = projectId == null ? null : project(level);
        jobId = project != null && project.active() != null ? project.active().areaId() : null;
        switch (stage) {
            case 0 -> {
                fixture = NativeStagedPerimeterFixture.setup(level, owner);
                coreKey = SiegeCore.key(owner); liveBuildGoal = fixture.buildGoal(); liveStorageGoal = fixture.storageGoal();
                verifyCurrentTerritory(level, NativeStagedPerimeterFixture.TERRITORY);
                require(balance(owner) == 0, "Fresh Treasury was not empty");
                FactionBank.credit(core(owner), 2000); RaidSavedData.get(owner.server).setDirty();
                var prepared = PerimeterConstruction.prepare(owner, NativeStagedPerimeterFixture.CORE, 1);
                require(prepared.ready(), "Production staged quote rejected: " + prepared.problem());
                require(prepared.quote() != null && !prepared.quote().layout().stages().isEmpty(),
                        "Stepped quote did not provide a genuine native layout");
                reviewedLayout = prepared.quote().layout();
                fixture = fixture.withPlan(prepared.plan(), level);
                require(prepared.builder() == builder(level), "Production quote did not bind the fixture builder");
                require(prepared.quote().gateContract() != null && prepared.quote().gateContract().gates().size() == 4,
                        "Production quote did not bind four cardinal gates");
                require(fixture.plan().materialCounts().getOrDefault("minecraft:dirt", 0) > 0,
                        "Production quote did not include fixture dirt-fill targets");
                Map<String, Object> step = fixture.stepEvidence();
                REPORT.put("targetCount", targetCount()); REPORT.put("materialCounts", fixture.plan().materialCounts());
                REPORT.put("fillTargets", fixture.plan().materialCounts().getOrDefault("minecraft:dirt", 0));
                REPORT.put("terrainStepEvidence", step); REPORT.put("nonLevelTransitionCount", step.get("nonLevelTransitionCount"));
                REPORT.put("deckBaseLevels", step.get("baseLevels"));
                REPORT.put("transitionClearanceVerified", step.get("transitionClearanceVerified"));
                REPORT.put("loweredSurfaceY", NativeStagedPerimeterFixture.LOWERED_SURFACE_Y);
                REPORT.put("flatSurfaceY", NativeStagedPerimeterFixture.FLAT_SURFACE_Y);
                REPORT.put("gateCount", prepared.quote().gateContract().gates().size());
                REPORT.put("gateCenters", prepared.quote().gateContract().gates().stream()
                        .map(g -> g.facing().getName() + ":" + g.outerCenter().toShortString()).toList());
                REPORT.put("parkedUnrelatedStarterNpcIds", fixture.parkedAuxiliaries());
                REPORT.put("reviewedStageCount", reviewedLayout.stages().size());
                check("Fresh non-op Survival owner, public native faction/25-chunk claim, actual core and four finite native chests; production stepped/gated targets initially empty");
                coreOpenAttempts = 1; lastCoreOpenAttemptTick = now; REPORT.put("coreOpenAttempts", coreOpenAttempts);
                advance(now, 1); return Action.OPEN_CORE;
            }
            case 1 -> {
                if (!(owner.containerMenu instanceof CoreHireMenu menu)) {
                    if (now - lastCoreOpenAttemptTick >= 40) {
                        lastCoreOpenAttemptTick = now;
                        coreOpenAttempts++; REPORT.put("coreOpenAttempts", coreOpenAttempts);
                        return Action.OPEN_CORE;
                    }
                    return Action.NONE;
                }
                require(menu.stillValid(owner) && menu.bank() == 2000 && owner.getTeam() != null
                        && owner.getTeam().getName().equals(NativeStagedPerimeterFixture.FACTION), "Real core menu owner/Treasury mismatch");
                menuId = menu.containerId;
                hudFaction = owner.getTeam() instanceof net.minecraft.world.scores.PlayerTeam team ? team.getDisplayName().getString() : owner.getTeam().getName();
                advance(now, 2); return Action.LIVE_MENU;
            }
            case 2 -> {
                if (owner.containerMenu instanceof CoreHireMenu) return Action.NONE;
                require(menuUiStage == 3, "Actual Building review button was not clicked");
                selectPlan(owner);
                var selection = PerimeterPreview.read(owner.getMainHandItem(), owner.getUUID(), level.dimension().location(), now);
                require(selection != null && selection.ready(), "Real menu-issued free plan was not ready"); verifyPreview(selection);
                require(balance(owner) == 2000 && areaCount(level) == 0 && placed(level) == 0
                        && PerimeterProjectStore.all(core(owner)).isEmpty(), "Free menu review mutated, charged or commissioned");
                check("Actual core-use packet and visible Building / Auto perimeter / Review in world controls issue the exact free stepped/gated preview");
                advance(now, 3);
            }
            case 3 -> {
                if (now - stageTick < 320) return Action.NONE; // Let ordinary review/setup chat fade naturally before the raw framebuffer.
                if (!SHOTS.contains("02-free-stepped-gated-perimeter-review.png")) return Action.CAPTURE_PLAN;
                advance(now, 4); return Action.USE_PLAN;
            }
            case 4 -> {
                if (now - stageTick < 20) return Action.NONE;
                var projects = PerimeterProjectStore.all(core(owner));
                require(projects.size() == 1 && balance(owner) == 1936 && owner.getMainHandItem().isEmpty(),
                        "Real plan-use packet did not create exactly one paid project and consume the plan for 64 emeralds");
                acceptedProject = projects.get(0); projectId = acceptedProject.header().projectId();
                require(acceptedProject.state() == PerimeterProject.State.RUNNING && acceptedProject.layout().equals(reviewedLayout)
                        && acceptedProject.plan().blocks().equals(fixture.plan().blocks())
                        && acceptedProject.gateContract() != null, "Paid project changed the reviewed stages, global union or gates");
                verifyPayment(acceptedProject.payment());
                var journal = PerimeterStageJournal.get(core(owner), acceptedProject);
                require(journal != null && journal.attempts().size() == 1 && journal.at(0).state() == PerimeterStageJournal.State.LIVE,
                        "First native marker is not durably LIVE");
                originalMarker = journal.at(0).marker(); jobId = acceptedProject.active().areaId();
                verifyActiveStage(level, acceptedProject);
                var residents = RaidSavedData.get(owner.server).civilianFactions.get(coreKey);
                require(residents != null, "Starter civilian ledger missing");
                treasuryObserver = new NativeQaTreasury(core(owner), bankRate(core(owner)), now, 2000, 64, residents.getCompound("Residents").size());
                builder(level).setNoAi(false); // Final tested-worker fixture mutation; original native AI owns all subsequent work/movement.
                constructionStarted = lastProgress = System.nanoTime(); placedBefore = placed(level);
                REPORT.put("projectId", projectId.toString()); REPORT.put("manifestHash", acceptedProject.manifestHash());
                REPORT.put("originalMarker", originalMarker.toShortString());
                REPORT.put("stageLayout", acceptedProject.stages().stream().map(s -> Map.of("index", s.index(), "area", s.areaId().toString(),
                        "digest", s.digest(), "targets", s.layout().targets().size(), "min", s.layout().min().toShortString(), "max", s.layout().max().toShortString())).toList());
                check("One actual plan-use packet commits the complete immutable manifest/reservation, first native stage and exactly one 64-emerald noncreative payment");
                sample(level, owner, "paid-first-stage"); advance(now, 5); return Action.USE_PLAN;
            }
            case 5 -> {
                require(!owner.isCreative() && !owner.isSpectator() && !owner.hasPermissions(2) && !builder(level).isNoAi(), "Native work lost real Survival/AI conditions");
                if (project != null && midRestart && !betweenRestart && project.state() == PerimeterProject.State.WAITING_FOR_NEXT_STAGE) {
                    beginRestart(level, owner, "between-stages"); return Action.NONE;
                }
                if (now - lastSampleTick < 20) return Action.NONE;
                lastSampleTick = now; long placed = placed(level);
                require(placed >= placedBefore, "An already placed exact target disappeared");
                if (placed != placedBefore) { lastProgress = System.nanoTime(); placedBefore = placed; }
                require(System.nanoTime() - constructionStarted - pausedNanos < CONSTRUCTION_SECONDS * SECOND, "Native staged work exceeded 180-minute construction cap");
                require(System.nanoTime() - lastProgress < 300 * SECOND, "No native construction progress for five minutes");
                conservation(level, owner);
                if (project != null) {
                    verifyProject(level, project);
                    String transition = project.state() + ":" + project.activeStage() + ":" + project.receipts().size() + ":" + project.blocker();
                    if (!transition.equals(lastTransition) || now % 200 < 20) { sample(level, owner, "native-progress"); lastTransition = transition; }
                    if (!midRestart && project.state() == PerimeterProject.State.RUNNING) {
                        long stagePlaced = project.active().layout().targets().entrySet().stream().filter(e -> expected(level, e.getKey(), e.getValue())).count();
                        if (stagePlaced >= Math.max(8, project.active().layout().targets().size() / 4) && stagePlaced < project.active().layout().targets().size()) {
                            beginRestart(level, owner, "mid-stage"); return Action.NONE;
                        }
                    }
                }
                if (!completeAuthority(level)) return Action.NONE;
                require(midRestart && (reviewedLayout.stages().size() == 1 || betweenRestart),
                        "Completion missed a required actual restart boundary");
                verifyExactCompletion(level, owner);
                REPORT.put("constructionSeconds", (System.nanoTime() - constructionStarted - pausedNanos) / (double) SECOND);
                check("All native stages completed the exact stepped/gated target union through original AI/material collection and every applicable real restart");
                advance(now, 6);
            }
            case 6 -> {
                if (now - stageTick < 60) return Action.NONE;
                verifyExactCompletion(level, owner);
                REPORT.put("finalDiagnostics", diagnostics(level, owner, true)); REPORT.put("completedBlocks", placed(level));
                REPORT.put("treasuryDebit", 64); REPORT.put("materialCounts", fixture.plan().materialCounts());
                REPORT.put("builderFeetYLevelsObserved", List.copyOf(BUILDER_FEET_Y));
                REPORT.put("completedStepStandingEvidence", fixture.completedStepStandingEvidence(level, builder(level)));
                REPORT.put("betweenStageRestartApplicable", reviewedLayout.stages().size() > 1);
                REPORT.put("nativeCompletionObserved", true); REPORT.put("midStageRestartVerified", midRestart); REPORT.put("betweenStageRestartVerified", betweenRestart);
                REPORT.put("territoryChunkCount", 25); REPORT.put("nativeClaimRecords", fixture.claimIds().stream().map(UUID::toString).toList());
                owner.setGameMode(GameType.SPECTATOR); owner.teleportTo(level, 168.5, 155, 40.5, 0, 90);
                advance(now, 7);
            }
            case 7 -> {
                if (now - stageTick < 100) return Action.NONE;
                advance(now, 8); return Action.CAPTURE_COMPLETE;
            }
            case 8 -> {
                require(SHOTS.contains("03-native-completed-staged-perimeter.png"), "Missing actual completed stepped perimeter framebuffer");
                verifyExactCompletion(level, owner); REPORT.put("finalDiagnostics", diagnostics(level, owner, true)); return Action.DONE;
            }
            case 10 -> {
                require(owner.isSpectator() && geometry(level).equals(pausedGeometry), "Permission-pause boundary allowed an accepted-cell write");
                if (now - stageTick < 100) return Action.NONE;
                require(project != null && !project.blocker().isEmpty(), "Production whole-project owner-unavailable pause was not observed");
                if (reloadKind.equals("between-stages")) require(project.state() == PerimeterProject.State.WAITING_FOR_NEXT_STAGE
                        && areaCount(level) == 0 && builder(level).currentBuildArea == null, "Between-stage boundary created a child while owner permission was absent");
                conservation(level, owner); sample(level, owner, reloadKind + "-before-shutdown");
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
                require(project != null && project.state() == stopped.state() && project.activeStage() == stopped.activeStage()
                        && ConstructionEditLedger.get(level).sameGeneration(stopped.ledgerGeneration())
                        && ConstructionEditLedger.get(level).matchesProjectReservation(project), "Durable stage/ledger/global reservation changed across reload");
                if (reloadKind.equals("mid-stage")) require(level.getEntity(project.active().areaId()) instanceof ProtectedBuildArea area
                        && area.getPersistentData().getCompound("SiegeProtectedConstructionV1").equals(stopped.recipe()), "Paid native stage recipe changed across reload");
                else require(areaCount(level) == 0 && PerimeterStageJournal.get(core(owner), project).at(project.activeStage()) == null,
                        "Reload recreated a not-yet-started stage before owner permission returned");
                verifyProject(level, project);
                RESTARTS.add(Map.of("kind", reloadKind, "gameTime", stopped.gameTime(), "placed", stopped.placed(), "pending", stopped.pendingCells().size(),
                        "projectState", stopped.state().name(), "activeStage", stopped.activeStage(), "ledgerGeneration", stopped.ledgerGeneration().toString(),
                        "verifiedPassiveCreditsAfterStop", treasuryObserver.passiveCredits() - stopped.passiveCredits()));
                owner.setGameMode(GameType.SURVIVAL); restartRequested = false;
                advance(now, 12);
            }
            case 12 -> {
                require(now - stageTick < 6000, "Production did not resume within five minutes after actual reload and restored Survival permission");
                if (project == null || project.state() != PerimeterProject.State.RUNNING || placed(level) <= stopped.placed()) return Action.NONE;
                verifyActiveStage(level, project); conservation(level, owner);
                require(!ProtectedBuilderHandMirror.pending(builder(level).getPersistentData())
                        && !ProtectedBuilderHandMirror.reviewNeeded(builder(level).getPersistentData()), "Native hand reload repair is unresolved after resumed work");
                if (reloadKind.equals("mid-stage")) midRestart = true; else betweenRestart = true;
                pausedNanos += System.nanoTime() - pauseStarted; lastProgress = System.nanoTime(); placedBefore = placed(level);
                check("Actual " + reloadKind + " close/reopen preserved paid global authority, stage journal, exact stock/geometry and ledger; original native AI resumed without another charge");
                sample(level, owner, reloadKind + "-resumed"); advance(now, 5);
            }
            default -> throw new AssertionError("Unexpected staged QA phase " + stage);
        }
        return Action.NONE;
    }

    private static boolean reviewThroughMenu(Minecraft mc) {
        require(System.nanoTime() - started < 180 * SECOND, "Actual core menu did not become ready in three minutes");
        if (!(mc.screen instanceof CoreHireScreen screen) || !(mc.player.containerMenu instanceof CoreHireMenu menu)) return false;
        require(screen.getMenu() == menu && menu.containerId == menuId, "Live core menu client/server identity differs");
        if (menu.bank() != 2000 || !menu.factionName().equals(hudFaction)) return false;
        if (menuUiStage == 0) {
            for (int attempts = 0; !NativeBuildingQa.hasVisibleButton(mc, "Building") && attempts < 7; attempts++) NativeBuildingQa.clickVisibleButton(mc, ">");
            NativeBuildingQa.clickVisibleButton(mc, "Building"); NativeBuildingQa.clickVisibleButton(mc, "Auto perimeter", "Perimeter"); menuUiStage = 1;
        } else if (menuUiStage == 1) {
            require(NativeBuildingQa.hasVisibleButton(mc, "Review in world"), "Actual review control is not visible");
            requestCapture("01-live-core-stepped-gated-review.png"); menuUiStage = 2;
        } else if (menuUiStage == 2) {
            NativeBuildingQa.clickVisibleButton(mc, "Review in world"); menuUiStage = 3; return true;
        }
        return false;
    }

    private static void beginRestart(ServerLevel level, ServerPlayer owner, String kind) {
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
            ServerLevel level = event.getServer().overworld(); var project = project(level); var worker = builder(level);
            require(project != null && geometry(level).equals(pausedGeometry), "Final shutdown changed paused project geometry");
            CompoundTag core = RaidSavedData.get(event.getServer()).siegeCores.get(coreKey); observeTreasury(level, core); conservation(level, null);
            CompoundTag recipe = project.active() != null && level.getEntity(project.active().areaId()) instanceof ProtectedBuildArea area
                    ? area.getPersistentData().getCompound("SiegeProtectedConstructionV1").copy() : new CompoundTag();
            stopped = new StopSnapshot(geometry(level), chestValues(level), constructionValues(worker.getInventory()), worker.getMainHandItem().save(new CompoundTag()),
                    worker.getOffhandItem().save(new CompoundTag()), authority(core), recipe, ConstructionEditLedger.get(level).generation(), pendingCells(level),
                    placed(level), level.getGameTime(), treasuryObserver.passiveCredits(), project.state(), project.activeStage());
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
    }
    private static boolean completeAuthority(ServerLevel level) {
        var snapshot = PerimeterProjectAuthority.snapshot(level, coreKey); var current = snapshot.get(projectId); var terminal = snapshot.terminal(projectId);
        if (current != null) {
            if (current.state() != PerimeterProject.State.COMPLETE) return false;
            require(current.header().equals(acceptedProject.header()) && current.manifestHash().equals(acceptedProject.manifestHash())
                    && current.targets().equals(acceptedProject.targets()) && current.layout().equals(reviewedLayout),
                    "Full COMPLETE record changed the accepted identity or exact geometry");
            require(current.completedTargetCount() == targetCount() && current.receipts().size() == acceptedProject.stages().size(), "Full COMPLETE record lacks exact stage receipts");
            verifyPayment(current.payment()); REPORT.put("completionAuthority", "COMPLETE full durable project");
            REPORT.put("completionAuthorityNbt", current.save().toString()); return true;
        }
        require(terminal != null, "Neither full project nor terminal authority exists");
        require(terminal.state() == PerimeterProject.State.COMPLETE && terminal.projectId().equals(projectId)
                && terminal.manifestHash().equals(acceptedProject.manifestHash()) && terminal.totalTargetCount() == targetCount()
                && terminal.verifiedStages() == acceptedProject.stages().size() && terminal.claimChunkCount() == 25
                && terminal.stages().stream().map(PerimeterTerminalReceipt.Stage::areaId).toList()
                    .equals(acceptedProject.stages().stream().map(PerimeterProject.Stage::areaId).toList()), "Terminal receipt does not prove every paid stage completed");
        verifyPayment(terminal.payment()); REPORT.put("completionAuthority", "COMPLETE compact terminal receipt");
        REPORT.put("completionAuthorityNbt", terminal.save().toString()); return true;
    }
    private static void verifyCurrentTerritory(ServerLevel level, Set<net.minecraft.world.level.ChunkPos> expected) {
        var actual = RecruitsClaimsBridge.getFactionTerritory(level, NativeStagedPerimeterFixture.FACTION, PerimeterTerritory.MAX_CHUNKS);
        require(actual.ready() && actual.chunks().equals(expected), "Actual native territory differs from the exact 25 chunks");
        for (var chunk : expected) require(ClaimEvents.recruitsClaimManager.getClaim(chunk) != null
                && fixture.claimIds().contains(ClaimEvents.recruitsClaimManager.getClaim(chunk).getUUID()), "Native claim index differs");
    }
    private static void verifyPreview(PerimeterPreview.Selection selection) {
        Map<Long, String> expanded = new LinkedHashMap<>();
        for (var box : selection.boxes()) {
            String material = switch (box.material()) { case 0 -> "minecraft:cobblestone"; case 1 -> "minecraft:oak_planks"; default -> "minecraft:dirt"; };
            for (BlockPos cell : BlockPos.betweenClosed(box.min(), box.max())) require(expanded.putIfAbsent(cell.asLong(), material) == null, "Preview boxes overlap");
        }
        require(expanded.equals(fixture.plan().blocks()), "Free preview is not the exact stepped/gated target union");
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
        for (BlockPos pos : NativeStagedPerimeterFixture.CHESTS) {
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
        return stack.is(Items.COBBLESTONE) || stack.is(Items.OAK_PLANKS) || stack.is(Items.DIRT) ? value.copy() : new CompoundTag();
    }

    private static void verifyExactCompletion(ServerLevel level, ServerPlayer owner) {
        require(completeAuthority(level), "No COMPLETE authority; absent marker alone is not completion");
        require(placed(level) == targetCount(), "Completion geometry mismatch");
        var joined = AREA_JOINS.stream().map(entry -> entry.get("area")).collect(java.util.stream.Collectors.toSet());
        require(joined.equals(acceptedProject.stages().stream().map(s -> s.areaId().toString()).collect(java.util.stream.Collectors.toSet())),
                "Not every native stage actually joined, or an extra child was created");
        require(AREA_JOINS.stream().allMatch(entry -> entry.get("marker").equals(originalMarker.toShortString())), "A native stage joined at a different shovel site");
        long freshJoins = AREA_JOINS.stream().filter(entry -> Boolean.FALSE.equals(entry.get("loadedFromDisk"))).count();
        require(freshJoins == acceptedProject.stages().size(), "A native section was created more than once instead of loaded");
        REPORT.put("nativeStageJoins", List.copyOf(AREA_JOINS));
        var ledger = ConstructionEditLedger.get(level);
        boolean markersGone = areaCount(level) == 0;
        boolean builderDetached = builder(level).currentBuildArea == null && !WorkersBridge.hasActiveBuildArea(builder(level))
                && !builder(level).getPersistentData().contains(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID)
                && !com.devfarinsky.siegeoverhaul.core.PerimeterProjectLink.reserved(builder(level));
        boolean reservationsGone = !ledger.contains(projectId)
                && acceptedProject.stages().stream().noneMatch(part -> ledger.contains(part.areaId()));
        var terminal = PerimeterProjectAuthority.snapshot(level, coreKey).terminal(projectId);
        boolean compacted = terminal != null;
        if (compacted) reservationsGone &= ledger.sameGeneration(terminal.cleanup().ledgerGeneration());
        REPORT.put("completionCleanup", Map.of("compacted", compacted, "markersGone", markersGone,
                "builderDetached", builderDetached, "reservationsGone", reservationsGone));
        if (compacted) require(markersGone && builderDetached && reservationsGone,
                "Compact COMPLETE receipt claimed cleanup while actual native references/reservations remain");
        observeTreasury(level, core(owner));
        for (var entry : fixture.plan().blocks().entrySet()) {
            BlockState expected = ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation(entry.getValue())).defaultBlockState();
            require(level.getBlockState(BlockPos.of(entry.getKey())).equals(expected), "Wrong exact block state at " + BlockPos.of(entry.getKey()));
        }
        verifyCurrentTerritory(level, NativeStagedPerimeterFixture.TERRITORY);
        for (var entry : fixture.nonPlanCells().entrySet()) require(level.getBlockState(entry.getKey()).equals(entry.getValue()),
                "Native job modified a non-plan cell: " + entry.getKey());
        fixture.completedStepStandingEvidence(level, builder(level));
        conservation(level, owner);
    }

    private static void conservation(ServerLevel level, ServerPlayer owner) {
        for (Item material : List.of(Items.COBBLESTONE, Items.OAK_PLANKS, Items.DIRT)) {
            int supplied = supplied(material);
            long built = fixture.plan().blocks().keySet().stream().filter(p -> level.getBlockState(BlockPos.of(p)).getBlock().asItem() == material).count();
            long total = built + chestStock(level, material) + workerStock(builder(level), material)
                    + (owner == null ? 0 : count(owner.getInventory(), material)) + dropped(level, material);
            require(total == supplied, "Finite native stock mismatch for " + material + ": supplied=" + supplied + ", accounted=" + total);
        }
    }
    private static int supplied(Item material) {
        if (material == Items.COBBLESTONE) return NativeStagedPerimeterFixture.COBBLE;
        if (material == Items.OAK_PLANKS) return NativeStagedPerimeterFixture.OAK;
        if (material == Items.DIRT) return NativeStagedPerimeterFixture.DIRT;
        throw new AssertionError("Unexpected material " + material);
    }
    private static int targetCount() { return fixture.plan().blocks().size(); }

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
        return level.getEntitiesOfClass(ItemEntity.class, NativeStagedPerimeterFixture.BOUNDS,
                entity -> entity.isAlive() && entity.getItem().is(item)).stream().mapToInt(entity -> entity.getItem().getCount()).sum();
    }
    private static long placed(ServerLevel level) {
        return fixture.plan().blocks().entrySet().stream().filter(e -> e.getValue().equals(
                String.valueOf(ForgeRegistries.BLOCKS.getKey(level.getBlockState(BlockPos.of(e.getKey())).getBlock())))).count();
    }
    private static void sample(ServerLevel level, ServerPlayer owner, String when) {
        var sample = new LinkedHashMap<>(diagnostics(level, owner, false)); sample.put("when", when);
        SAMPLES.add(sample);
        FactionLogger.LOG.info("Native staged perimeter QA: {}", new GsonBuilder().create().toJson(sample));
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
        result.put("builderBlockPosition", builder.blockPosition().toShortString());
        if (goal.blockPos != null) {
            double dx = builder.getX() - (goal.blockPos.getX() + 0.5D), dz = builder.getZ() - (goal.blockPos.getZ() + 0.5D);
            result.put("nativeTargetHorizontalDistanceSquared", dx * dx + dz * dz);
            result.put("nativeTargetDistanceSquared", builder.position().distanceToSqr(Vec3.atCenterOf(goal.blockPos)));
            result.put("nativeTargetCurrentState", level.getBlockState(goal.blockPos).toString());
        }
        result.put("nativeBuildError", String.valueOf(goal.errorMessage));
        result.put("nativeRemaining", builder.currentBuildArea == null ? -1 : builder.currentBuildArea.stackToPlace.size());
        result.put("nativeGoalQueueSize", goal.stackToPlace == null ? -1 : goal.stackToPlace.size());
        result.put("nativeMinBuildHeight", goal.minBuildHeight);
        if (goal.stackToPlace != null && !goal.stackToPlace.isEmpty())
            result.put("nativeGoalQueueTop", goal.stackToPlace.peek().toShortString());
        if (builder.currentBuildArea != null) result.put("nativeLowestPendingLayer",
                lowestPendingLayer(level, builder.currentBuildArea.stackToPlace));
        result.put("storageState", String.valueOf(storageGoal.state)); result.put("storageChestTarget", String.valueOf(storageGoal.chestPos));
        result.put("requestedSupplies", WorkersConstructionView.requests(builder));
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
        result.put("chestDirt", chestStock(level, Items.DIRT));
        result.put("builderCobble", workerStock(builder, Items.COBBLESTONE)); result.put("builderOak", workerStock(builder, Items.OAK_PLANKS));
        result.put("builderDirt", workerStock(builder, Items.DIRT));
        result.put("looseCobble", dropped(level, Items.COBBLESTONE)); result.put("looseOak", dropped(level, Items.OAK_PLANKS));
        result.put("looseDirt", dropped(level, Items.DIRT));
        result.put("ownerCobble", owner == null ? -1 : count(owner.getInventory(), Items.COBBLESTONE));
        result.put("ownerOak", owner == null ? -1 : count(owner.getInventory(), Items.OAK_PLANKS));
        result.put("ownerDirt", owner == null ? -1 : count(owner.getInventory(), Items.DIRT));
        result.put("placedCobble", fixture.plan().blocks().keySet().stream().filter(p -> level.getBlockState(BlockPos.of(p)).is(Blocks.COBBLESTONE)).count());
        result.put("placedOak", fixture.plan().blocks().keySet().stream().filter(p -> level.getBlockState(BlockPos.of(p)).is(Blocks.OAK_PLANKS)).count());
        result.put("placedDirt", fixture.plan().blocks().keySet().stream().filter(p -> level.getBlockState(BlockPos.of(p)).is(Blocks.DIRT)).count());
        if (detailed) {
            result.put("builderUuid", builder.getUUID().toString());
            result.put("builderBounds", builder.getBoundingBox().toString());
            result.put("builderWidth", builder.getBbWidth()); result.put("builderHeight", builder.getBbHeight());
            result.put("registeredBuilderWidth", builder.getType().getDimensions().width);
            result.put("registeredBuilderHeight", builder.getType().getDimensions().height);
            var mutationCells = NativeConstructionGuard.mutationCells(goal);
            result.put("nativeMutationCandidates", mutationCells.stream().map(BlockPos::toShortString).toList());
            if (goal.blockPos != null) result.put("nativeTargetStandingCandidates",
                    standingCandidates(level, builder, goal.blockPos, mutationCells));
            var occupants = new ArrayList<Map<String, Object>>();
            var footprint = new AABB(fixture.plan().min(), fixture.plan().max().offset(1, 1, 1));
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
    private static Map<String, Object> lowestPendingLayer(ServerLevel level, java.util.Stack<BuildBlock> pending) {
        var result = new LinkedHashMap<String, Object>();
        result.put("pending", pending == null ? -1 : pending.size());
        if (pending == null || pending.isEmpty()) return result;
        int minY = pending.stream().mapToInt(block -> block.getPos().getY()).min().orElse(Integer.MAX_VALUE);
        result.put("y", minY);
        var counts = new LinkedHashMap<String, Integer>();
        var positions = new ArrayList<Map<String, String>>();
        for (BuildBlock block : pending) {
            if (block.getPos().getY() != minY) continue;
            String material = String.valueOf(ForgeRegistries.BLOCKS.getKey(block.getState().getBlock()));
            counts.merge(material, 1, Integer::sum);
            if (positions.size() < 48) positions.add(Map.of("position", block.getPos().toShortString(),
                    "expected", material, "actual", level.getBlockState(block.getPos()).toString()));
        }
        result.put("materials", counts); result.put("sample", positions);
        return result;
    }
    private static List<Map<String, Object>> standingCandidates(ServerLevel level, BuilderEntity builder,
                                                                BlockPos target, Set<BlockPos> mutationCells) {
        var reserved = new java.util.HashSet<Long>();
        fixture.plan().blocks().keySet().forEach(cell -> reserved.add(BlockPos.of(cell).atY(0).asLong()));
        var candidates = new ArrayList<Map<String, Object>>();
        for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
            if (dx * dx + dz * dz >= 40) continue;
            BlockPos column = target.offset(dx, 0, dz);
            var entry = new LinkedHashMap<String, Object>(); entry.put("column", column.atY(target.getY()).toShortString());
            entry.put("nativeReachDistanceSquared", dx * dx + dz * dz);
            entry.put("chunkLoaded", level.hasChunkAt(column));
            if (level.hasChunkAt(column)) {
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
                BlockPos feet = column.atY(y);
                entry.put("feet", feet.toShortString()); entry.put("reservedColumn", reserved.contains(feet.atY(0).asLong()));
                entry.put("support", level.getBlockState(feet.below()).toString());
                entry.put("feetState", level.getBlockState(feet).toString());
                entry.put("headState", level.getBlockState(feet.above()).toString());
                AABB body = builder.getBoundingBox().move(Vec3.atBottomCenterOf(feet).subtract(builder.position()));
                entry.put("bodyCollisionFree", level.noCollision(builder, body));
                entry.put("intersectsMutation", mutationCells.stream().anyMatch(cell -> new AABB(cell).intersects(body)));
            }
            candidates.add(entry);
        }
        return candidates;
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
        return NativeStagedPerimeterFixture.CHESTS.stream().mapToInt(pos -> count(chest(level, pos), material)).sum();
    }
    private static int areaCount(ServerLevel level) { return level.getEntitiesOfClass(ProtectedBuildArea.class, NativeStagedPerimeterFixture.BOUNDS, e -> e.isAlive()).size(); }
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
    private static void selectPlan(ServerPlayer owner) {
        int found = -1;
        for (int i = 0; i < owner.getInventory().getContainerSize(); i++) if (owner.getInventory().getItem(i).is(ModItems.PERIMETER_PLAN.get())) { found = i; break; }
        require(found >= 0, "Free reviewed plan not in real owner inventory");
        if (found >= 9) { ItemStack previous = owner.getInventory().getItem(8); owner.getInventory().setItem(8, owner.getInventory().getItem(found));
            owner.getInventory().setItem(found, previous); found = 8; }
        owner.getInventory().selected = found; owner.inventoryMenu.broadcastChanges(); owner.connection.send(new ClientboundSetCarriedItemPacket(found));
    }
    private static void initialize(Minecraft mc) throws Exception {
        String configured = System.getProperty("siegeoverhaul.nativeQa.directory", "");
        require(!configured.isBlank(), "Missing isolated QA directory");
        directory = Path.of(configured).toAbsolutePath().normalize();
        require(directory.endsWith(Path.of("build", "native-staged-qa", "client"))
                && mc.gameDirectory.toPath().toRealPath().equals(directory.toRealPath()), "Unsafe active QA game directory");
        evidence = directory.getParent().resolve("evidence"); Files.createDirectories(evidence);
        REPORT.put("treasuryObserverContracts", NativeQaTreasuryContracts.verify());
        REPORT.put("startedUtc", Instant.now().toString()); REPORT.put("mode", "staged-perimeter");
        REPORT.put("constructionLimitSeconds", CONSTRUCTION_SECONDS);
        REPORT.put("totalLimitSeconds", TOTAL_SECONDS);
        REPORT.put("timeoutBasis", "Historical flat-perimeter calibration at afb855b: seven finite 64-block withdrawals took 80-90s per cycle; sustained 0.76-0.78 blocks/s at 20 TPS projects 141-145min. Declare 180min native work plus 15min setup/restarts before this fresh run; no tick/build-speed changes.");
        REPORT.put("scope", "Fresh cheats-off Survival integrated world, vanilla generated flat stone ground, native public 25-chunk claim/core, actual core menu review and plan-use packets, one 64e commission, four finite native chests, original native goals. Genuine mid-stage and between-stage close/reopen; owner spectator permission pauses only to stabilize shutdown boundaries. Builder is never teleported/refilled/disabled after initial commission. Original physical shovel site for every stage. Final aerial observer only after durable COMPLETE plus exact-world/stock proof.");
        REPORT.put("notCovered", List.of("Dedicated-client networking", "Unloaded-owner absence differs from the actual spectator permission edge tested here", "Actual project cancellation/recovery-corruption edges remain separately pure/source tested", "Uneven/disjoint/holed territory or other material palettes", "Storage farther than native reach", "Arbitrary shader/GPU combinations"));
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
        REPORT.put("loadedModVersions", mods); REPORT.put("loadedCompanionArtifacts", artifacts);
        var nativeClasses = new LinkedHashMap<String, Class<?>>();
        nativeClasses.put("builder", BuilderEntity.class); nativeClasses.put("buildGoal", BuilderWorkGoal.class);
        nativeClasses.put("storageGoal", GetNeededItemsFromStorage.class); nativeClasses.put("storageArea", StorageArea.class);
        nativeClasses.put("nativeClaim", RecruitsClaim.class); nativeClasses.put("protectedArea", ProtectedBuildArea.class);
        nativeClasses.put("asyncPathNavigation", AsyncPathNavigation.class);
        nativeClasses.put("workerPathNavigation", WorkerPathNavigation.class);
        nativeClasses.put("workersGroundPathNavigation", WorkersGroundPathNavigation.class);
        nativeClasses.put("workersAsyncPathfinder", WorkersAsyncPathfinder.class);
        var nativeApiClasses = new LinkedHashMap<String, String>();
        var nativeApiClassArtifacts = new LinkedHashMap<String, Object>();
        nativeClasses.forEach((name, type) -> {
            nativeApiClasses.put(name, type.getName());
            try { nativeApiClassArtifacts.put(name, classArtifact(type)); }
            catch (Exception e) { nativeApiClassArtifacts.put(name, Map.of("className", type.getName(), "error", e.toString())); }
        });
        nativeApiClasses.put("builderClassLoader", String.valueOf(BuilderEntity.class.getClassLoader()));
        REPORT.put("nativeApiClasses", nativeApiClasses);
        REPORT.put("nativeApiClassArtifacts", nativeApiClassArtifacts);
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
    private static Map<String, Object> classArtifact(Class<?> type) throws Exception {
        var result = new LinkedHashMap<String, Object>();
        result.put("className", type.getName());
        var domain = type.getProtectionDomain();
        var source = domain == null || domain.getCodeSource() == null ? null : domain.getCodeSource().getLocation();
        result.put("codeSource", String.valueOf(source));
        if (source != null && "file".equalsIgnoreCase(source.getProtocol())) {
            Path path = Path.of(source.toURI());
            result.put("fileName", path.getFileName().toString());
            result.put("regularFile", Files.isRegularFile(path));
            if (Files.isRegularFile(path)) result.put("sha256", sha256(path));
        }
        return result;
    }
    private static void fail(Minecraft mc, Throwable problem) {
        if (finishing || finished) return;
        failure = problem; finishing = true;
        FactionLogger.LOG.error("Native staged-perimeter QA failed in stage {}", stage, problem);
        if (mc.level != null && evidence != null) requestCapture("failure-native-staged-perimeter.png"); else finish(mc);
    }
    private static void finish(Minecraft mc) {
        if (finished) return; finished = true;
        REPORT.put("status", failure == null ? "passed" : "failed"); REPORT.put("stage", stage);
        REPORT.put("finishedUtc", Instant.now().toString()); REPORT.put("assertions", List.copyOf(CHECKS));
        REPORT.put("restarts", List.copyOf(RESTARTS)); REPORT.put("nativeStageJoins", List.copyOf(AREA_JOINS));
        REPORT.put("samples", List.copyOf(SAMPLES)); REPORT.put("screenshots", List.copyOf(SHOTS));
        if (treasuryObserver != null) REPORT.put("treasuryAccounting", treasuryObserver.evidence());
        if (failure != null) REPORT.put("failure", failure.toString());
        try { if (evidence != null) Files.writeString(evidence.resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(REPORT)); }
        catch (Exception writeFailure) { FactionLogger.LOG.error("Could not write staged-perimeter evidence", writeFailure); }
        FactionLogger.LOG.info("Native staged-perimeter QA {}", failure == null ? "passed" : "failed"); mc.stop();
    }
    private static void advance(long now, int next) { stage = next; stageTick = now; }
    private static void check(String message) { CHECKS.add(message); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
