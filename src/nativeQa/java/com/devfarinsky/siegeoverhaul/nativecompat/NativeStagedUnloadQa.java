package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.FactionLogger;
import com.devfarinsky.siegeoverhaul.RaidNetwork;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import com.devfarinsky.siegeoverhaul.client.CoreHireScreen;
import com.devfarinsky.siegeoverhaul.core.*;
import com.devfarinsky.siegeoverhaul.items.ModItems;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.entities.ai.BuilderWorkGoal;
import com.talhanation.workers.entities.ai.GetNeededItemsFromStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
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

/** Real chunk unload/reload of one paid stage. QA observers never repair or drain native inventory. */
@Mod.EventBusSubscriber(modid = SiegeOverhaul.MOD_ID, value = Dist.CLIENT)
public final class NativeStagedUnloadQa {
    private static final boolean ENABLED = Boolean.getBoolean("siegeoverhaul.nativeQa")
            && "staged-unload".equals(System.getProperty("siegeoverhaul.nativeQa.mode"));
    private static final long SECOND = 1_000_000_000L, TOTAL_SECONDS = 600;
    private static final int CLOSED_TICKS = 40, UNLOADED_TICKS = 100, RETURN_PAUSE_TICKS = 40, UNLOAD_FINALIZATION_TICKS = 200;
    private static final String UNLOADED_BLOCKER = "Paused: load the original owned builder to continue this perimeter.";
    private static final String FIXTURE_SERVER_CONFIG = "[Recruits]\nRecruitsChunkLoading = false\n";
    private static final Map<String, Object> REPORT = new LinkedHashMap<>();
    private static final List<Map<String, Object>> SAMPLES = new ArrayList<>(), JOINS = new ArrayList<>();
    private static final List<String> CHECKS = new ArrayList<>(), SHOTS = new ArrayList<>();
    private static NativeStagedUnloadFixture.Fixture fixture;
    private static PerimeterProject accepted, lastVerified;
    private static UUID playerId, projectId, areaId;
    private static String coreKey;
    private static BlockPos marker;
    private static BuilderWorkGoal buildGoal;
    private static GetNeededItemsFromStorage storageGoal;
    private static NativeQaTreasury treasury;
    private static CompoundTag acceptedInvariant, departureJournal, departureLedger, departureRecipe;
    private static CompoundTag unloadedProject, unloadedJournal, unloadedLedger;
    private static Set<Long> departureCompleted;
    private static Map<Long, BlockState> departureGeometry, returnedGeometry, canceledGeometry;
    private static InventoryEvidence trackingEndInventory, removedInventory, loadedInventory;
    private static BuilderEntity departingWorker; // Exact native object retained read-only until actual serialization/unload is observed.
    private static Throwable eventFailure, failure;
    private static CompletableFuture<Action> pending;
    private static Path directory, evidence;
    private static long started, stageTick, unloadedAt, unloadedStableAt = -1, returnStableAt = -1, departurePlaced, returnedPlaced;
    private static long lastSampleTick = -1, lastStableTick = -1;
    private static int phase, stage, stableTicks, maxStableTicks, menuId, frames, captureFrame, unloadSettlingRevisions;
    private static boolean finished, finishing, departing, genuineUnload, returned;
    private static String capture;
    private record InventoryEvidence(List<CompoundTag> cargo, CompoundTag main, CompoundTag off,
                                     CompoundTag cleanup, String reason, long tick) {}
    private enum Action { NONE, USE_PLAN, CAPTURE_RETURN, OPEN_CORE, CANCEL_CORE, DONE }
    private NativeStagedUnloadQa() {}

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (started == 0) started = System.nanoTime();
            // The hard scenario cap is never suspended for screenshots, chunk loading or menus.
            require(System.nanoTime() - started < TOTAL_SECONDS * SECOND, "Staged unload scenario exceeded hard ten-minute cap");
            if (finishing) { finish(mc); return; }
            if (capture != null) return;
            if (phase == 0) {
                if (!(mc.screen instanceof TitleScreen)) return;
                initialize(mc);
                require(!Files.exists(directory.resolve("saves").resolve(NativeStagedUnloadFixture.WORLD)), "Refusing existing unload QA world");
                mc.options.renderDistance().set(6); mc.options.simulationDistance().set(6);
                mc.options.guiScale().set(2); mc.options.pauseOnLostFocus = false; mc.resizeDisplay();
                GameRules rules = new GameRules();
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
                phase = 1;
                mc.createWorldOpenFlows().createFreshLevel(NativeStagedUnloadFixture.WORLD,
                        new LevelSettings(NativeStagedUnloadFixture.WORLD, GameType.SURVIVAL, false, Difficulty.PEACEFUL,
                                false, rules, WorldDataConfiguration.DEFAULT), new WorldOptions(20261007L, false, false),
                        NativeStagedUnloadFixture::dimensions);
                return;
            }
            if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null
                    || mc.screen != null && !(mc.screen instanceof CoreHireScreen)) return;
            if (playerId == null) playerId = mc.player.getUUID();
            if (pending != null) {
                if (!pending.isDone()) return;
                Action action = pending.join(); pending = null;
                switch (action) {
                    case USE_PLAN -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                    case CAPTURE_RETURN -> {
                        if (!mc.levelRenderer.isChunkCompiled(marker)) { pending = CompletableFuture.completedFuture(action); return; }
                        aim(mc, Vec3.atCenterOf(marker)); requestCapture("01-returned-permission-pause.png"); return;
                    }
                    case OPEN_CORE -> {
                        aim(mc, Vec3.atCenterOf(NativeStagedUnloadFixture.CORE));
                        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                                new BlockHitResult(Vec3.atCenterOf(NativeStagedUnloadFixture.CORE).add(0, .5, 0), Direction.UP,
                                        NativeStagedUnloadFixture.CORE, false));
                    }
                    case CANCEL_CORE -> {
                        if (!(mc.screen instanceof CoreHireScreen) || !(mc.player.containerMenu instanceof CoreHireMenu menu)
                                || menu.containerId != menuId) { pending = CompletableFuture.completedFuture(action); return; }
                        RaidNetwork.cancelPerimeterProject(menuId, projectId, accepted.header().generation());
                    }
                    case DONE -> { finishing = true; finish(mc); return; }
                    default -> {}
                }
            }
            CompletableFuture<Action> next = new CompletableFuture<>(); pending = next;
            var server = mc.getSingleplayerServer();
            server.execute(() -> {
                try { next.complete(step(server.overworld(), server.getPlayerList().getPlayer(playerId))); }
                catch (Throwable problem) {
                    try { REPORT.put("failureDiagnostics", diagnostics(server.overworld())); }
                    catch (Throwable extra) { REPORT.put("diagnosticFailure", extra.toString()); }
                    next.completeExceptionally(problem);
                }
            });
        } catch (Throwable problem) { fail(mc, problem); }
    }

    /** Inspect every ordinary server tick, not sparse client polls. No stop/start/tick calls on native goals. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (!ENABLED || finished || fixture == null || event.phase != TickEvent.Phase.END || stage != 3) return;
        try {
            var level = event.getServer().overworld(); long now = level.getGameTime();
            var worker = level.getEntity(fixture.builderId());
            if (worker instanceof BuilderEntity builder && closed(builder)) {
                stableTicks = lastStableTick == now - 1 ? stableTicks + 1 : 1;
                lastStableTick = now; maxStableTicks = Math.max(maxStableTicks, stableTicks);
            } else { stableTicks = 0; lastStableTick = -1; }
        } catch (Throwable problem) { eventFailure = problem; }
    }

    private static boolean closed(BuilderEntity builder) {
        return !builder.isNoAi() && workerConstructionStock(builder) >= 32
                && builder.neededItems != null && builder.neededItems.isEmpty()
                && !ProtectedBuilderHandMirror.activeUse(builder) && ProtectedStorageAccess.runningProblem(builder) == null
                && ProtectedInventoryCleanup.read(builder.getPersistentData()).isEmpty()
                && buildGoal != null && buildGoal.getClass() == BuilderWorkGoal.class
                && "PLACE_BLOCKS".equals(String.valueOf(buildGoal.state));
    }

    private static Action step(ServerLevel level, ServerPlayer owner) throws Exception {
        require(owner != null && owner.isAlive(), "Real Survival owner unavailable");
        require(eventFailure == null, "Native event observation failed: " + eventFailure);
        long now = level.getGameTime();
        if (treasury != null) treasury.observe(core(level), bankRate(core(level)), now);
        if (stage >= 4 && stage <= 5) require(!owner.isCreative() && !owner.isSpectator(), "Departure/unloaded owner must remain Survival");
        if (stage > 0 && now - lastSampleTick >= 20) { lastSampleTick = now; sample(level); }
        switch (stage) {
            case 0 -> {
                require(!com.talhanation.recruits.config.RecruitsServerConfig.RecruitsChunkLoading.get(),
                        "Fresh unload fixture did not load its explicitly configured RecruitsChunkLoading=false");
                REPORT.put("recruitsChunkLoading", com.talhanation.recruits.config.RecruitsServerConfig.RecruitsChunkLoading.get());
                fixture = NativeStagedUnloadFixture.setup(level, owner); coreKey = SiegeCore.key(owner);
                buildGoal = fixture.buildGoal(); storageGoal = fixture.storageGoal();
                require(owner.blockPosition().distSqr(level.getSharedSpawnPos()) > 1024L * 1024L, "Fixture is too close to vanilla spawn loading");
                require(FactionBank.balance(core(level)) == 0, "Fresh Treasury was not empty");
                FactionBank.credit(core(level), 2000); RaidSavedData.get(owner.server).setDirty();
                var quote = PerimeterConstruction.prepare(owner, NativeStagedUnloadFixture.CORE, 1);
                require(quote.ready() && quote.quote() != null && !quote.quote().layout().stages().isEmpty(),
                        "Production stepped quote rejected: " + quote.problem());
                fixture = fixture.withPlan(quote.plan(), level);
                require(quote.builder() == builder(level), "Production quote did not bind the fixture builder");
                require(quote.quote().gateContract() != null && quote.quote().gateContract().gates().size() == 4,
                        "Production quote did not bind four cardinal gates");
                require(fixture.plan().materialCounts().getOrDefault("minecraft:dirt", 0) > 0,
                        "Production quote did not include fixture dirt-fill targets");
                require(PerimeterConstruction.review(owner, NativeStagedUnloadFixture.CORE, 1), "Production free review failed");
                require(FactionBank.balance(core(level)) == 2000 && PerimeterProjectStore.all(core(level)).isEmpty()
                        && placed(level) == 0, "Free review commissioned or changed the site");
                selectPlan(owner);
                REPORT.put("targetCount", targetCount()); REPORT.put("materialCounts", fixture.plan().materialCounts());
                REPORT.put("fillTargets", fixture.plan().materialCounts().getOrDefault("minecraft:dirt", 0));
                REPORT.put("gateCount", quote.quote().gateContract().gates().size());
                REPORT.put("gateCenters", quote.quote().gateContract().gates().stream()
                        .map(g -> g.facing().getName() + ":" + g.outerCenter().toShortString()).toList());
                REPORT.put("reviewedStageCount", quote.quote().layout().stages().size());
                REPORT.put("parkedUnrelatedStarterNpcIds", fixture.parkedAuxiliaries());
                check("Fresh non-op Survival owner, translated stepped/gated claim, free production review and exactly finite native chest stock");
                advance(now, 1);
            }
            case 1 -> {
                if (now - stageTick < 40) return Action.NONE;
                var selection = PerimeterPreview.read(owner.getMainHandItem(), owner.getUUID(), level.dimension().location(), now);
                require(selection != null && selection.ready(), "Production plan was not ready in real inventory");
                advance(now, 2); return Action.USE_PLAN;
            }
            case 2 -> {
                if (now - stageTick < 20) return Action.NONE;
                var projects = PerimeterProjectStore.all(core(level));
                require(projects.size() == 1 && FactionBank.balance(core(level)) == 1936 && owner.getMainHandItem().isEmpty(),
                        "Actual plan packet did not commit one 64-emerald commission");
                accepted = projects.get(0); projectId = accepted.header().projectId(); areaId = accepted.active().areaId();
                require(accepted.state() == PerimeterProject.State.RUNNING && accepted.activeStage() == 0
                        && accepted.plan().blocks().equals(fixture.plan().blocks()) && accepted.gateContract() != null
                        && !accepted.layout().stages().isEmpty(),
                        "Wrong initial staged authority");
                verifyPayment(accepted.payment());
                var journal = PerimeterStageJournal.get(core(level), accepted);
                require(journal != null && journal.attempts().size() == 1 && journal.at(0).state() == PerimeterStageJournal.State.LIVE,
                        "First native stage lacks durable LIVE creation evidence");
                marker = journal.at(0).marker(); acceptedInvariant = invariant(accepted);
                require(level.getEntity(areaId) instanceof ProtectedBuildArea, "Paid native marker absent");
                var residents = RaidSavedData.get(owner.server).civilianFactions.get(coreKey);
                require(residents != null, "Civilian tax ledger missing");
                treasury = new NativeQaTreasury(core(level), bankRate(core(level)), now, 2000, 64, residents.getCompound("Residents").size());
                builder(level).setNoAi(false); // Final worker fixture mutation; all later AI, movement and material use are original native behavior.
                REPORT.put("projectId", projectId.toString()); REPORT.put("manifestHash", accepted.manifestHash());
                REPORT.put("header", accepted.header().toString()); REPORT.put("originalMarker", marker.toShortString());
                REPORT.put("stageLayout", accepted.stages().stream().map(s -> Map.of("index", s.index(), "area", s.areaId().toString(),
                        "digest", s.digest(), "targets", s.layout().targets().size())).toList());
                check("Actual client plan-use packet commits full staged manifest and one noncreative 64-emerald fee");
                advance(now, 3);
            }
            case 3 -> {
                verifyUnadvanced(level); requireSurvival(owner);
                require(allLoaded(level), "Fixture terrain unloaded before departure");
                if (placed(level) == 0 || stableTicks < CLOSED_TICKS || now - lastStableTick > 1 || !closed(builder(level))) return Action.NONE;
                verifyWorld(level, owner);
                departurePlaced = placed(level); departureGeometry = geometry(level);
                var receipt = level.getEntity(areaId).getPersistentData().getCompound("SiegeProtectedConstructionV1");
                departureRecipe = immutableRecipe(receipt);
                departureCompleted = receiptPositions(receipt, "Completed");
                departureJournal = core(level).getCompound(PerimeterStageJournal.KEY).copy();
                departureLedger = ConstructionEditLedger.get(level).save(new CompoundTag());
                REPORT.put("closedLifecycle", Map.of("consecutiveServerTicks", stableTicks, "minimumTicks", CLOSED_TICKS,
                        "carriedConstruction", workerConstructionStock(builder(level)), "neededItemsEmpty", true,
                        "storageRunningProblemAbsent", true, "cleanupJournalEmpty", true, "activeHandUse", false,
                        "nativeGoalState", String.valueOf(buildGoal.state), "nativePlaced", departurePlaced, "gameTime", now));
                check("Observed real native placements and at least 40 consecutive ordinary ticks with a closed inventory lifecycle and at least 32 carried construction blocks");
                departing = true;
                owner.teleportTo(level, NativeStagedUnloadFixture.CORE.getX() + 2048.5, 65, 39.5, 0, 20);
                advance(now, 4);
            }
            case 4 -> {
                // NO world/block/container reads from departure until all fixture chunks are loaded on return.
                verifyUnadvanced(level); verifyDepartureSavedState(level);
                require(now - stageTick < 1800, "Ordinary player departure did not unload the entire fixture within 90 seconds");
                if (!allUnloaded(level) || level.getEntity(fixture.builderId()) != null || level.getEntity(areaId) != null) return Action.NONE;
                require(trackingEndInventory != null && departingWorker != null
                        && departingWorker.getUUID().equals(fixture.builderId()), "Original native worker tracking-end evidence missing");
                // Forge posts EntityLeaveLevelEvent at tracking end, before the hidden entity is
                // serialized and PersistentEntitySectionManager.unloadEntity sets its removal reason.
                // Absent visible lookups and a null event-time reason alone are not genuine unload.
                require(now - trackingEndInventory.tick() <= UNLOAD_FINALIZATION_TICKS,
                        "Native worker serialization/unload did not finish within 200 ordinary ticks after tracking end");
                Entity.RemovalReason reason = departingWorker.getRemovalReason();
                if (reason == null) return Action.NONE;
                require(reason == Entity.RemovalReason.UNLOADED_TO_CHUNK && departingWorker.isRemoved(),
                        "Original worker was removed for a reason other than actual chunk unload: " + reason);
                removedInventory = inventory(departingWorker, reason.name());
                require(sameInventoryValues(trackingEndInventory, removedInventory),
                        "Native inventory or cleanup changed between tracking end and actual serialized chunk unload");
                genuineUnload = true; unloadedAt = now;
                REPORT.put("unloadFinalization", Map.of("trackingEndTick", trackingEndInventory.tick(), "confirmedUnloadTick", now,
                        "waitTicks", now - trackingEndInventory.tick(), "limitTicks", UNLOAD_FINALIZATION_TICKS,
                        "trackingEndInventoryMatched", true, "actualRemovalReason", reason.name(),
                        "serializationInvocationObservedNotFsync", true));
                unloadedProject = project(level).save(); unloadedJournal = core(level).getCompound(PerimeterStageJournal.KEY).copy();
                if (UNLOADED_BLOCKER.equals(unloadedProject.getString("Blocker"))) unloadedStableAt = now;
                unloadedLedger = ConstructionEditLedger.get(level).save(new CompoundTag());
                REPORT.put("confirmedUnloadTick", now); REPORT.put("builderUnloadInventory", inventoryMap(removedInventory));
                check("All 49 fixture/apron chunks report hasChunk false; original builder and marker are unavailable after ordinary owner departure");
                advance(now, 5);
            }
            case 5 -> {
                verifyUnadvanced(level); verifyDepartureSavedState(level);
                require(allUnloaded(level) && level.getEntity(fixture.builderId()) == null && level.getEntity(areaId) == null,
                        "Unavailable fixture was unexpectedly reloaded");
                require(core(level).getCompound(PerimeterStageJournal.KEY).equals(unloadedJournal)
                        && ConstructionEditLedger.get(level).save(new CompoundTag()).equals(unloadedLedger), "Unloaded native journal/reservation changed");
                var project = project(level); var currentSaved = project.save();
                if (unloadedStableAt < 0) {
                    if (currentSaved.equals(unloadedProject)) {
                        require(now - unloadedAt < 40, "Controller blocker did not settle within two ordinary seconds");
                        return Action.NONE;
                    }
                    require(unloadSettlingRevisions == 0 && project.revision() == unloadedProject.getLong("Revision") + 1
                            && UNLOADED_BLOCKER.equals(project.blocker()),
                            "Unloaded authority changed beyond its one allowed controller settling revision");
                    // verifyUnadvanced already proved every field except Revision/Blocker is exact.
                    unloadedProject = currentSaved; unloadSettlingRevisions++; unloadedStableAt = now;
                }
                require(UNLOADED_BLOCKER.equals(project.blocker()) && currentSaved.equals(unloadedProject),
                        "Unloaded full project changed after the one controller settling boundary");
                var job = ConstructionReport.snapshot(owner).stream().filter(j -> projectId.equals(j.projectId())).findFirst().orElseThrow();
                require(!job.complete() && job.percent() < 0 && job.activity().equals(project.blocker())
                        && job.sectionText().contains("64 emeralds paid once"), "Unloaded server construction report inferred progress/completion or lost fee receipt");
                if (now - unloadedStableAt < UNLOADED_TICKS) return Action.NONE;
                REPORT.put("controllerSettlingRevisions", unloadSettlingRevisions);
                REPORT.put("unloadedBeforeSettlingTicks", unloadedStableAt - unloadedAt);
                REPORT.put("unloadedPause", Map.of("ticks", now - unloadedStableAt, "all49ChunksUnavailable", true,
                        "builderUnavailable", true, "markerUnavailable", true, "controllerBlocker", project.blocker(),
                        "serverReportActivity", job.activity(), "serverReportPercent", job.percent(), "remoteMenuRendered", false,
                        "activeStage", project.activeStage(), "completedStageReceipts", project.receipts().size()));
                check("100 ordinary unloaded ticks preserve complete manifest/header/payment, exact journal and reservation with no next-stage progression; server report remains blocked/unknown");
                owner.setGameMode(GameType.SPECTATOR); // Permission pause only for a stable reloaded snapshot.
                owner.teleportTo(level, NativeStagedUnloadFixture.CORE.getX() + 1.5, 65, 37.5, 0, 20);
                returned = true; advance(now, 6);
            }
            case 6 -> {
                require(owner.isSpectator(), "Reload observation lost spectator permission pause");
                verifyUnadvanced(level);
                require(now - stageTick < 1800, "Returned owner did not reload fixture within 90 seconds");
                if (!allLoaded(level) || !(level.getEntity(fixture.builderId()) instanceof BuilderEntity)
                        || !(level.getEntity(areaId) instanceof ProtectedBuildArea)) return Action.NONE;
                require(loadedInventory != null, "Native builder did not rejoin from disk before resumed work");
                require(removedInventory.cargo().equals(loadedInventory.cargo()) && removedInventory.main().equals(loadedInventory.main())
                        && removedInventory.off().equals(loadedInventory.off()), "Native cargo/hand values changed across chunk serialization/load");
                REPORT.put("builderLoadInventory", inventoryMap(loadedInventory));
                var cleanup = ProtectedInventoryCleanup.read(builder(level).getPersistentData());
                REPORT.put("returnedCleanupJournal", builder(level).getPersistentData().getCompound(ProtectedInventoryCleanup.KEY).toString());
                require(cleanup.isEmpty() && loadedInventory.cleanup().isEmpty(),
                        "Unloaded native inventory lifecycle requires REVIEW; evidence retained, no QA repair permitted");
                require(!ProtectedBuilderHandMirror.reviewNeeded(builder(level).getPersistentData()),
                        "Returned hand mirror requires REVIEW; native values and evidence retained");
                // Let the ordinary production LivingTick reconcile its own equal-value load mirror.
                // QA reads this flag only; it never invokes restore, a setter, or any cleanup method.
                if (ProtectedBuilderHandMirror.pending(builder(level).getPersistentData())) return Action.NONE;
                require(PerimeterProjectLink.matches(builder(level), accepted) && !builder(level).isNoAi(), "Returned original builder lost immutable job link or AI");
                require(level.getEntity(areaId).blockPosition().equals(marker)
                        && immutableRecipe(level.getEntity(areaId).getPersistentData().getCompound("SiegeProtectedConstructionV1")).equals(departureRecipe),
                        "Reloaded original marker or immutable accepted native recipe changed");
                verifyReturnedProgressReceipt(level);
                require(JOINS.stream().anyMatch(j -> areaId.toString().equals(j.get("id")) && Boolean.TRUE.equals(j.get("loadedFromDisk"))),
                        "Original native marker did not load from disk");
                verifyWorld(level, owner);
                if (returnStableAt < 0) {
                    returnedGeometry = geometry(level); returnedPlaced = placed(level); returnStableAt = now;
                    require(returnedPlaced >= departurePlaced && returnedPlaced < accepted.active().layout().targets().size(), "Departure lost placements or completed the bounded stage");
                    for (var e : departureGeometry.entrySet()) if (!e.getValue().isAir())
                        require(returnedGeometry.get(e.getKey()).equals(e.getValue()), "A pre-departure placed target changed across unload");
                    REPORT.put("placementsDuringDepartureBeforeConfirmedUnload", returnedPlaced - departurePlaced);
                    REPORT.put("returnedPlaced", returnedPlaced); return Action.CAPTURE_RETURN;
                }
                require(geometry(level).equals(returnedGeometry), "Spectator permission pause allowed placement after loaded snapshot");
                if (now - returnStableAt < RETURN_PAUSE_TICKS || !SHOTS.contains("01-returned-permission-pause.png")) return Action.NONE;
                REPORT.put("nativeInventoryReloadVerified", true); REPORT.put("returnedConservationVerified", true);
                check("Original builder and marker loaded from disk; exact native inventory values, all material totals and non-plan geometry survive genuine unload");
                owner.setGameMode(GameType.SURVIVAL); advance(now, 7);
            }
            case 7 -> {
                requireSurvival(owner); verifyUnadvanced(level); verifyWorld(level, owner);
                require(ProtectedInventoryCleanup.read(builder(level).getPersistentData()).isEmpty()
                        || !ProtectedStorageAccess.recoveryBlocked(builder(level)), "Resumed worker requires fail-closed inventory REVIEW");
                if (placed(level) <= returnedPlaced) return Action.NONE;
                REPORT.put("resumedPlaced", placed(level)); REPORT.put("nativePlacementResumed", true);
                check("Restoring real non-op Survival permission resumes original native placement without another plan or fee");
                advance(now, 8); return Action.OPEN_CORE;
            }
            case 8 -> {
                requireSurvival(owner);
                if (!(owner.containerMenu instanceof CoreHireMenu menu)) return Action.NONE;
                require(menu.stillValid(owner), "Returned core menu is not authenticated/nearby");
                menuId = menu.containerId; advance(now, 9); return Action.CANCEL_CORE;
            }
            case 9 -> {
                var snapshot = PerimeterProjectAuthority.snapshot(level, coreKey); var terminal = snapshot.terminal(projectId);
                if (terminal == null) {
                    var current = snapshot.get(projectId); require(current != null, "Cancellation lost all authority");
                    require(current.state() == PerimeterProject.State.RUNNING || current.state() == PerimeterProject.State.CANCELED,
                            "Cancellation reached unexpected authority state");
                    return Action.NONE;
                }
                require(terminal.state() == PerimeterProject.State.CANCELED && terminal.manifestHash().equals(accepted.manifestHash())
                        && terminal.owner().equals(accepted.header().owner()) && terminal.builder().equals(fixture.builderId())
                        && terminal.generation() == accepted.header().generation() && terminal.totalTargetCount() == targetCount()
                        && terminal.totalStageCount() == accepted.stages().size() && terminal.verifiedStages() == 0,
                        "CANCEL compact receipt changed the commission or falsely completed a stage");
                verifyPayment(terminal.payment());
                require(!ConstructionEditLedger.get(level).contains(projectId) && !ConstructionEditLedger.get(level).contains(areaId)
                        && level.getEntity(areaId) == null && !PerimeterProjectLink.reserved(builder(level))
                        && NativeConstructionGuard.currentArea(builder(level)) == null
                        && ProtectedInventoryCleanup.read(builder(level).getPersistentData()).isEmpty(), "Normal cancellation cleanup not complete");
                verifyWorld(level, owner); canceledGeometry = geometry(level);
                REPORT.put("cancelAuthorityNbt", terminal.save().toString());
                REPORT.put("cancelPacketAuthenticated", true); REPORT.put("safeCleanupVerified", true);
                owner.closeContainer(); advance(now, 10);
            }
            case 10 -> {
                require(geometry(level).equals(canceledGeometry), "Canceled job resumed a native placement");
                verifyWorld(level, owner);
                if (now - stageTick < 40) return Action.NONE;
                REPORT.put("finalPlaced", placed(level)); REPORT.put("treasuryAccounting", treasury.evidence());
                REPORT.put("outcome", "unloaded pause/resume/cancel"); REPORT.put("fullCompletionClaimed", false);
                check("Normal authenticated client CANCEL leaves partial blocks and finite supplies intact, retires reservations and detaches builder safely");
                return Action.DONE;
            }
            default -> throw new AssertionError("Unknown unload QA phase " + stage);
        }
        return Action.NONE;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void entityLeft(EntityLeaveLevelEvent event) {
        if (!ENABLED || fixture == null || !departing || genuineUnload || event.getLevel().isClientSide()
                || !(event.getEntity() instanceof BuilderEntity worker) || !worker.getUUID().equals(fixture.builderId())) return;
        try {
            if (trackingEndInventory == null) {
                departingWorker = worker;
                trackingEndInventory = inventory(worker, String.valueOf(worker.getRemovalReason()));
                REPORT.put("builderTrackingEndInventory", inventoryMap(trackingEndInventory));
            } else require(departingWorker == worker, "Tracking end reported a different native worker object for the reserved UUID");
        } catch (Throwable problem) { eventFailure = problem; }
    }

    /** Capture native load values before normal production join handlers wrap original goals. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void entityJoined(EntityJoinLevelEvent event) {
        if (!ENABLED || fixture == null || event.getLevel().isClientSide()) return;
        var entity = event.getEntity();
        if (entity instanceof ProtectedBuildArea || entity.getUUID().equals(fixture.builderId()))
            JOINS.add(Map.of("id", entity.getUUID().toString(), "type", entity.getClass().getName(),
                    "loadedFromDisk", event.loadedFromDisk(), "gameTime", event.getLevel().getGameTime()));
        if (!(entity instanceof BuilderEntity worker) || !worker.getUUID().equals(fixture.builderId())) return;
        try {
            buildGoal = worker.goalSelector.getAvailableGoals().stream().map(g -> g.getGoal()).filter(BuilderWorkGoal.class::isInstance)
                    .map(BuilderWorkGoal.class::cast).findFirst().orElseThrow();
            storageGoal = worker.goalSelector.getAvailableGoals().stream().map(g -> g.getGoal()).filter(GetNeededItemsFromStorage.class::isInstance)
                    .map(GetNeededItemsFromStorage.class::cast).findFirst().orElseThrow();
            if (genuineUnload) {
                require(event.loadedFromDisk(), "Original builder was recreated instead of loaded from disk");
                loadedInventory = inventory(worker, "loadedFromDisk");
            }
        } catch (Throwable problem) { eventFailure = problem; }
    }

    private static InventoryEvidence inventory(BuilderEntity worker, String reason) {
        var cargo = new ArrayList<CompoundTag>();
        for (int slot = 0; slot < worker.getInventory().getContainerSize(); slot++)
            cargo.add(worker.getInventory().getItem(slot).save(new CompoundTag()));
        return new InventoryEvidence(List.copyOf(cargo), worker.getMainHandItem().save(new CompoundTag()),
                worker.getOffhandItem().save(new CompoundTag()),
                worker.getPersistentData().getCompound(ProtectedInventoryCleanup.KEY).copy(), reason, worker.level().getGameTime());
    }
    private static boolean sameInventoryValues(InventoryEvidence first, InventoryEvidence second) {
        return first.cargo().equals(second.cargo()) && first.main().equals(second.main())
                && first.off().equals(second.off()) && first.cleanup().equals(second.cleanup());
    }
    private static Map<String, Object> inventoryMap(InventoryEvidence value) {
        return Map.of("cargo", value.cargo().stream().map(CompoundTag::toString).toList(), "main", value.main().toString(),
                "off", value.off().toString(), "cleanup", value.cleanup().toString(), "reason", value.reason(), "gameTime", value.tick());
    }
    /** Completed/Cleared are mutable native progress; every other recipe/context field is exact. */
    private static CompoundTag immutableRecipe(CompoundTag receipt) {
        var immutable = receipt.copy(); immutable.remove("Completed"); immutable.remove("Cleared"); return immutable;
    }
    private static Set<Long> receiptPositions(CompoundTag receipt, String key) {
        require(receipt.contains(key, net.minecraft.nbt.Tag.TAG_LONG_ARRAY), "Native progress receipt is missing " + key);
        long[] values = receipt.getLongArray(key);
        require(values.length <= accepted.active().layout().targets().size(), "Native progress receipt exceeds active stage");
        var positions = new java.util.HashSet<Long>();
        for (long value : values) require(accepted.active().layout().targets().containsKey(value) && positions.add(value),
                "Native progress receipt contains duplicate or out-of-stage cells");
        return Set.copyOf(positions);
    }
    private static void verifyReturnedProgressReceipt(ServerLevel level) {
        requireWorldReadable(level);
        var receipt = level.getEntity(areaId).getPersistentData().getCompound("SiegeProtectedConstructionV1");
        Set<Long> completed = receiptPositions(receipt, "Completed"), cleared = receiptPositions(receipt, "Cleared");
        require(completed.containsAll(departureCompleted), "Native unload lost previously completed receipt entries");
        for (long cell : completed) require(accepted.targets().get(cell).equals(level.getBlockState(BlockPos.of(cell))),
                "Returned completed receipt disagrees with exact accepted target geometry");
        require(cleared.isEmpty() && accepted.active().layout().targets().keySet().stream().allMatch(p -> accepted.before().get(p).isAir()),
                "Initially empty stepped fixture acquired unexpected cleared-cell receipts");
        REPORT.put("returnedProgressReceipt", Map.of("departureCompleted", departureCompleted.size(), "returnedCompleted", completed.size(),
                "cleared", cleared.size(), "immutableRecipeVerified", true, "everyCompletedCellMatchesExactTarget", true));
    }
    private static CompoundTag invariant(PerimeterProject project) {
        var tag = project.save(); tag.remove("Revision"); tag.remove("Blocker"); return tag;
    }
    private static void verifyUnadvanced(ServerLevel level) {
        var project = project(level);
        require(project != null, "Complete staged authority disappeared");
        // PerimeterProject is deeply immutable. Recheck every replacement, without serializing
        // the full target set again on every otherwise unchanged observation tick.
        if (project != lastVerified) {
            require(invariant(project).equals(acceptedInvariant),
                    "Complete staged manifest/header/payment/state or active-stage progression changed");
            lastVerified = project;
        }
        require(project.state() == PerimeterProject.State.RUNNING && project.activeStage() == 0 && project.receipts().isEmpty(),
                "Bounded unload scenario advanced a section or lost RUNNING authority");
        require(ConstructionEditLedger.get(level).matchesProjectReservation(project)
                && ConstructionEditLedger.get(level).projectLeaseIndex(projectId) == 0,
                "Global reservation or active native lease changed");
        verifyPayment(project.payment());
    }
    private static void verifyDepartureSavedState(ServerLevel level) {
        require(core(level).getCompound(PerimeterStageJournal.KEY).equals(departureJournal)
                && ConstructionEditLedger.get(level).save(new CompoundTag()).equals(departureLedger),
                "Departure changed durable native creation journal/reservation");
    }
    private static void verifyPayment(PerimeterProject.PaymentReceipt payment) {
        require(payment != null && payment.equals(accepted.payment()) && payment.projectId().equals(projectId)
                && payment.manifestHash().equals(accepted.manifestHash()) && payment.generation() == accepted.header().generation()
                && payment.debited() == 64 && payment.quotedPrice() == 64 && !payment.creative(), "One paid receipt changed or was duplicated");
    }
    private static void requireSurvival(ServerPlayer owner) {
        require(!owner.isCreative() && !owner.isSpectator() && !owner.hasPermissions(2) && owner.mayBuild(),
                "Native construction must have a real non-op Survival owner");
    }

    /** hasChunk is deliberately the only terrain query while the owner is away. */
    private static boolean allLoaded(ServerLevel level) {
        for (int x = 135; x <= 141; x++) for (int z = -1; z <= 5; z++) if (!level.hasChunk(x, z)) return false;
        return true;
    }
    private static boolean allUnloaded(ServerLevel level) {
        for (int x = 135; x <= 141; x++) for (int z = -1; z <= 5; z++) if (level.hasChunk(x, z)) return false;
        return true;
    }
    private static void requireWorldReadable(ServerLevel level) {
        require((!departing || returned) && allLoaded(level), "QA attempted a world read during unavailable-site observation");
    }
    private static Map<Long, BlockState> geometry(ServerLevel level) {
        requireWorldReadable(level); Map<Long, BlockState> result = new LinkedHashMap<>();
        fixture.plan().blocks().keySet().forEach(p -> result.put(p, level.getBlockState(BlockPos.of(p))));
        fixture.plan().clearance().forEach(p -> result.put(p, level.getBlockState(BlockPos.of(p))));
        return Map.copyOf(result);
    }
    private static long placed(ServerLevel level) {
        requireWorldReadable(level);
        return fixture.plan().blocks().entrySet().stream().filter(e -> e.getValue().equals(
                String.valueOf(ForgeRegistries.BLOCKS.getKey(level.getBlockState(BlockPos.of(e.getKey())).getBlock())))).count();
    }
    private static void verifyWorld(ServerLevel level, ServerPlayer owner) {
        requireWorldReadable(level);
        for (var entry : fixture.nonPlanCells().entrySet()) require(level.getBlockState(entry.getKey()).equals(entry.getValue()),
                "Native work modified a non-plan cell " + entry.getKey());
        for (var entry : fixture.plan().blocks().entrySet()) {
            var state = level.getBlockState(BlockPos.of(entry.getKey()));
            require(state.isAir() || entry.getValue().equals(String.valueOf(ForgeRegistries.BLOCKS.getKey(state.getBlock()))),
                    "Target contains a block outside its exact accepted material");
        }
        Map<String, Object> stock = new LinkedHashMap<>();
        for (Item material : List.of(Items.COBBLESTONE, Items.OAK_PLANKS, Items.DIRT)) {
            int supplied = supplied(material);
            long built = fixture.plan().blocks().keySet().stream().filter(p -> level.getBlockState(BlockPos.of(p)).getBlock().asItem() == material).count();
            int chests = 0;
            for (BlockPos pos : NativeStagedUnloadFixture.CHESTS) {
                require(level.getBlockEntity(pos) instanceof Container, "Finite source chest unavailable");
                chests += count((Container) level.getBlockEntity(pos), material);
            }
            int cargo = workerStock(builder(level), material), personal = count(owner.getInventory(), material);
            int loose = level.getEntitiesOfClass(ItemEntity.class, NativeStagedUnloadFixture.BOUNDS,
                    entity -> entity.isAlive() && entity.getItem().is(material)).stream().mapToInt(entity -> entity.getItem().getCount()).sum();
            require(built + chests + cargo + personal + loose == supplied, "Exact finite material conservation failed: " + material);
            stock.put(String.valueOf(ForgeRegistries.ITEMS.getKey(material)), Map.of("supplied", supplied, "built", built,
                    "chests", chests, "builder", cargo, "owner", personal, "loose", loose));
        }
        REPORT.put("latestConservation", stock);
    }
    private static int supplied(Item material) {
        if (material == Items.COBBLESTONE) return NativeStagedUnloadFixture.COBBLE;
        if (material == Items.OAK_PLANKS) return NativeStagedUnloadFixture.OAK;
        if (material == Items.DIRT) return NativeStagedUnloadFixture.DIRT;
        throw new AssertionError("Unexpected material " + material);
    }
    private static int targetCount() { return fixture.plan().blocks().size(); }
    private static int workerConstructionStock(BuilderEntity worker) {
        return workerStock(worker, Items.COBBLESTONE) + workerStock(worker, Items.OAK_PLANKS) + workerStock(worker, Items.DIRT);
    }
    private static int count(Container inventory, Item item) {
        int result = 0; for (int slot = 0; slot < inventory.getContainerSize(); slot++)
            if (inventory.getItem(slot).is(item)) result += inventory.getItem(slot).getCount();
        return result;
    }
    private static int workerStock(BuilderEntity worker, Item material) {
        // Native cargo slots each count independently; only identical equipment mirrors count once.
        Set<ItemStack> seen = Collections.newSetFromMap(new IdentityHashMap<>()); int result = 0;
        for (int slot = 0; slot < worker.getInventory().getContainerSize(); slot++) {
            var stack = worker.getInventory().getItem(slot); seen.add(stack); if (stack.is(material)) result += stack.getCount();
        }
        for (ItemStack stack : List.of(worker.getMainHandItem(), worker.getOffhandItem()))
            if (seen.add(stack) && stack.is(material)) result += stack.getCount();
        return result;
    }
    private static BuilderEntity builder(ServerLevel level) {
        require(level.getEntity(fixture.builderId()) instanceof BuilderEntity, "Original builder unavailable");
        return (BuilderEntity) level.getEntity(fixture.builderId());
    }
    private static CompoundTag core(ServerLevel level) {
        var value = RaidSavedData.get(level.getServer()).siegeCores.get(coreKey); require(value != null, "Authoritative core missing"); return value;
    }
    private static PerimeterProject project(ServerLevel level) { return PerimeterProjectAuthority.snapshot(level, coreKey).get(projectId); }
    private static int bankRate(CompoundTag core) {
        return FactionBank.interestRate(core, com.devfarinsky.siegeoverhaul.RaidConfig.BANK_INTEREST_BASIS_POINTS.get());
    }
    private static void selectPlan(ServerPlayer owner) {
        int found = -1;
        for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++)
            if (owner.getInventory().getItem(slot).is(ModItems.PERIMETER_PLAN.get())) { found = slot; break; }
        require(found >= 0 && found < 9, "Free plan was not delivered to the fresh owner's hotbar");
        owner.getInventory().selected = found; owner.inventoryMenu.broadcastChanges();
        owner.connection.send(new ClientboundSetCarriedItemPacket(found));
    }

    /** Diagnostics deliberately do not touch block states, chunks, containers, or the remote menu. */
    private static Map<String, Object> diagnostics(ServerLevel level) {
        Map<String, Object> values = new LinkedHashMap<>(); values.put("stage", stage); values.put("gameTime", level.getGameTime());
        values.put("closedTicks", stableTicks); values.put("maxClosedTicks", maxStableTicks);
        if (fixture == null) return values;
        values.put("all49ChunksLoaded", allLoaded(level)); values.put("all49ChunksUnloaded", allUnloaded(level));
        values.put("builderAvailable", level.getEntity(fixture.builderId()) != null);
        values.put("markerAvailable", areaId != null && level.getEntity(areaId) != null);
        if (departingWorker != null) {
            values.put("retainedWorkerRemovalReason", String.valueOf(departingWorker.getRemovalReason()));
            values.put("trackingEndTick", trackingEndInventory.tick());
        }
        if (projectId != null) {
            var current = project(level);
            if (current != null) values.put("project", Map.of("state", current.state().name(), "stage", current.activeStage(),
                    "receipts", current.receipts().size(), "revision", current.revision(), "blocker", current.blocker(), "manifest", current.manifestHash()));
            else values.put("project", "compact terminal");
        }
        if (level.getEntity(fixture.builderId()) instanceof BuilderEntity worker) {
            values.put("nativeBuildState", buildGoal == null ? "unavailable" : String.valueOf(buildGoal.state));
            values.put("nativeStorageState", storageGoal == null ? "unavailable" : String.valueOf(storageGoal.state));
            values.put("builderPosition", worker.position().toString()); values.put("builderCobble", workerStock(worker, Items.COBBLESTONE));
            values.put("builderOak", workerStock(worker, Items.OAK_PLANKS)); values.put("builderDirt", workerStock(worker, Items.DIRT));
            values.put("neededItems", String.valueOf(worker.neededItems)); values.put("usingItem", worker.isUsingItem());
            values.put("storageRunningProblem", String.valueOf(ProtectedStorageAccess.runningProblem(worker)));
            values.put("cleanupJournal", worker.getPersistentData().getCompound(ProtectedInventoryCleanup.KEY).toString());
        }
        if (treasury != null) values.put("treasury", treasury.lastObservation());
        return values;
    }
    private static void sample(ServerLevel level) {
        var value = diagnostics(level); SAMPLES.add(value);
        FactionLogger.LOG.info("Native staged unload QA: {}", new GsonBuilder().create().toJson(value));
    }
    private static void advance(long tick, int next) { stage = next; stageTick = tick; }
    private static void check(String value) { CHECKS.add(value); }
    private static void require(boolean okay, String message) { if (!okay) throw new AssertionError(message); }

    private static void initialize(Minecraft mc) throws Exception {
        String configured = System.getProperty("siegeoverhaul.nativeQa.directory", "");
        require(!configured.isBlank(), "Missing isolated game directory");
        directory = Path.of(configured).toAbsolutePath().normalize();
        require(directory.endsWith(Path.of("build", "native-unload-qa", "client"))
                && mc.gameDirectory.toPath().toRealPath().equals(directory.toRealPath()), "Unsafe unload QA directory");
        require(!Files.exists(directory.resolve("saves").resolve(NativeStagedUnloadFixture.WORLD)), "Refusing existing unload fixture world before config setup");
        Path defaults = directory.resolve("defaultconfigs"); Files.createDirectories(defaults);
        require(defaults.toRealPath().startsWith(directory.toRealPath()), "Fixture defaultconfigs escaped the isolated directory");
        Path config = defaults.resolve("recruits-server.toml");
        if (Files.exists(config)) require(!Files.isSymbolicLink(config) && Files.isRegularFile(config)
                && Files.readString(config).equals(FIXTURE_SERVER_CONFIG), "Refusing conflicting existing fixture server configuration");
        else Files.writeString(config, FIXTURE_SERVER_CONFIG, java.nio.file.StandardOpenOption.CREATE_NEW);
        evidence = directory.getParent().resolve("evidence"); Files.createDirectories(evidence);
        REPORT.put("scenarioConfiguration", "Fresh isolated fixture with RecruitsChunkLoading=false; default enabled chunk loading is not covered");
        REPORT.put("fixtureServerConfigSha256", sha256(config));
        REPORT.put("startedUtc", Instant.now().toString()); REPORT.put("mode", "staged-unload"); REPORT.put("totalLimitSeconds", TOTAL_SECONDS);
        REPORT.put("scope", "Explicit fresh-fixture RecruitsChunkLoading=false configuration. Real integrated non-op Survival owner, production free review and plan-use packet, paid stepped/gated staged manifest, original native goals and finite stock. Only the owner departs for ordinary chunk unload. Spectator permission pause on return stabilizes observation; Survival resumes native work, then a real authenticated core CANCEL packet performs normal cleanup.");
        REPORT.put("notCovered", List.of("Default RecruitsChunkLoading=true behavior", "Full project completion or later stage scheduling", "Dedicated-client networking", "Remote core menu rendering while chunks are unloaded",
                "Open inventory lifecycle unload recovery (must fail closed, never repaired here)", "Unloaded-owner absence or disconnected owner", "Disjoint or holed claims or other palettes"));
        REPORT.put("treasuryObserverContracts", NativeQaTreasuryContracts.verify());
        var versions = new LinkedHashMap<String, String>(); var artifacts = new LinkedHashMap<String, Object>();
        for (String id : List.of("minecraft", "forge", "siegeoverhaul", "workers", "recruits", "smallships", "siegeweapons")) {
            var container = ModList.get().getModContainerById(id); require(container.isPresent(), "Required real companion missing: " + id);
            var info = container.orElseThrow().getModInfo(); versions.put(id, info.getVersion().toString());
            if (List.of("workers", "recruits", "smallships", "siegeweapons").contains(id)) {
                Path jar = info.getOwningFile().getFile().getFilePath(); require(Files.isRegularFile(jar), "Cannot hash companion " + id);
                artifacts.put(id, Map.of("fileName", jar.getFileName().toString(), "sha256", sha256(jar), "kind", "remapped development runtime, not release bytes"));
            }
        }
        require("2.0.3".equals(versions.get("workers")) && "1.15.2".equals(versions.get("recruits")), "Unreviewed native companion versions");
        REPORT.put("loadedModVersions", versions); REPORT.put("loadedCompanionArtifacts", artifacts);
        REPORT.put("openGlVendor", GL11.glGetString(GL11.GL_VENDOR)); REPORT.put("openGlRenderer", GL11.glGetString(GL11.GL_RENDERER));
    }
    private static String sha256(Path path) throws Exception {
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) { byte[] buffer = new byte[65536]; int read;
            while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read); }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }
    @SubscribeEvent
    public static void renderTick(TickEvent.RenderTickEvent event) {
        if (!ENABLED || finished || event.phase != TickEvent.Phase.END) return;
        frames++; if (capture == null || frames < captureFrame) return;
        Minecraft mc = Minecraft.getInstance();
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            require(image.getWidth() >= 640 && image.getHeight() >= 360, "Framebuffer too small");
            int first = image.getPixelRGBA(0, 0), changed = 0;
            for (int y = 0; y < image.getHeight(); y += 16) for (int x = 0; x < image.getWidth(); x += 16)
                if (image.getPixelRGBA(x, y) != first) changed++;
            require(changed > 50, "Blank framebuffer"); image.writeToFile(evidence.resolve(capture)); SHOTS.add(capture); capture = null;
        } catch (Throwable problem) { capture = null; failure = problem; finishing = true; }
    }
    private static void requestCapture(String name) { capture = name; captureFrame = frames + 3; }
    private static void aim(Minecraft mc, Vec3 target) {
        Vec3 delta = target.subtract(mc.player.getEyePosition());
        float yaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90);
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)));
        mc.player.setYRot(yaw); mc.player.yRotO = yaw; mc.player.setXRot(pitch); mc.player.xRotO = pitch;
    }
    private static void fail(Minecraft mc, Throwable problem) {
        if (finished) return; failure = problem; finishing = true;
        FactionLogger.LOG.error("Native staged unload QA failed in phase {}", stage, problem); finish(mc);
    }
    private static void finish(Minecraft mc) {
        if (finished) return; finished = true;
        REPORT.put("status", failure == null ? "passed" : "failed"); REPORT.put("finishedUtc", Instant.now().toString());
        REPORT.put("elapsedSeconds", (System.nanoTime() - started) / (double) SECOND); REPORT.put("checks", List.copyOf(CHECKS));
        REPORT.put("screenshots", List.copyOf(SHOTS)); REPORT.put("samples", List.copyOf(SAMPLES)); REPORT.put("nativeJoins", List.copyOf(JOINS));
        REPORT.put("failedStage", failure == null ? -1 : stage); if (failure != null) REPORT.put("failure", failure.toString());
        if (trackingEndInventory != null) REPORT.put("builderTrackingEndInventory", inventoryMap(trackingEndInventory));
        if (removedInventory != null) REPORT.put("builderUnloadInventory", inventoryMap(removedInventory));
        if (loadedInventory != null) REPORT.put("builderLoadInventory", inventoryMap(loadedInventory));
        try { if (evidence != null) Files.writeString(evidence.resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(REPORT)); }
        catch (Exception problem) { FactionLogger.LOG.error("Could not write staged unload evidence", problem); }
        FactionLogger.LOG.info("Native staged unload QA {}", failure == null ? "passed" : "failed"); mc.stop();
    }
}
