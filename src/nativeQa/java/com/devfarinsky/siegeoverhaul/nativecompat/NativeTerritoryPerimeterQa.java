package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.FactionLogger;
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
import com.devfarinsky.siegeoverhaul.core.PerimeterPreview;
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

/** Focused opt-in real-client acceptance. No fake goals, forced builder motion, refills or tick acceleration. */
@Mod.EventBusSubscriber(modid = SiegeOverhaul.MOD_ID, value = Dist.CLIENT)
public final class NativeTerritoryPerimeterQa {
    private static final boolean ENABLED = Boolean.getBoolean("siegeoverhaul.nativeQa")
            && "territory-perimeter".equals(System.getProperty("siegeoverhaul.nativeQa.mode"));
    private static final long SECOND = 1_000_000_000L;
    private static final Map<String, Object> REPORT = new LinkedHashMap<>();
    private static final List<Map<String, Object>> SAMPLES = new ArrayList<>();
    private static final List<String> CHECKS = new ArrayList<>();
    private static final List<String> SHOTS = new ArrayList<>();
    private static NativeTerritoryPerimeterFixture.Fixture fixture;
    private static CompletableFuture<Action> pending;
    private static Path directory, evidence;
    private static UUID playerId, jobId;
    private static long started, constructionStarted, lastProgress, lastSampleTick = -1, placedBefore = -1, stageTick;
    private static int clientPhase, stage, renderFrames, captureFrame;
    private static boolean finished, finishing;
    private static Throwable failure;
    private static String capture;
    private static long captureRequested;
    private static final long CONSTRUCTION_SECONDS = NativeTerritoryPerimeterFixture.BLOCKS + 180L;
    private static String coreKey;
    private static long pausedStarted, pausedNanos;
    private static boolean restartRequested, restartVerified;
    private static CompoundTag acceptedScope;
    private static Map<Long, BlockState> pausedGeometry;
    private static BuilderWorkGoal liveBuildGoal;
    private static GetNeededItemsFromStorage liveStorageGoal;
    private static StopSnapshot stopped;
    private static Throwable stoppingFailure;
    private static List<CompoundTag> loadedCargo;
    private static CompoundTag loadedMain, loadedOff;
    private record StopSnapshot(Map<Long, BlockState> geometry, List<List<CompoundTag>> chests,
                                List<CompoundTag> cargo, CompoundTag main, CompoundTag off,
                                CompoundTag scope, CompoundTag recipe, UUID ledgerGeneration,
                                Set<Long> pendingCells, long treasury, long placed, long gameTime) {}
    private enum Action { NONE, USE_PLAN, RELOAD, CAPTURE_COMPLETE, DONE }

    private NativeTerritoryPerimeterQa() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (finishing) {
                if (capture == null || System.nanoTime() - captureRequested > 20 * SECOND) finish(mc);
                return;
            }
            if (started == 0) started = System.nanoTime();
            require(System.nanoTime() - started < (CONSTRUCTION_SECONDS + 480) * SECOND, "Territory client exceeded count-derived construction plus eight-minute setup/reload bound");
            if (capture != null) return;
            if (clientPhase == 0) {
                if (!(mc.screen instanceof TitleScreen)) return;
                initialize(mc);
                require(!Files.exists(directory.resolve("saves").resolve(NativeTerritoryPerimeterFixture.WORLD)),
                        "Refusing an existing territory-perimeter world");
                mc.options.renderDistance().set(4); mc.options.simulationDistance().set(5);
                mc.options.guiScale().set(2); mc.options.pauseOnLostFocus = false; mc.resizeDisplay();
                GameRules rules = new GameRules();
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
                clientPhase = 1;
                mc.createWorldOpenFlows().createFreshLevel(NativeTerritoryPerimeterFixture.WORLD,
                        new LevelSettings(NativeTerritoryPerimeterFixture.WORLD, GameType.SURVIVAL, false, Difficulty.PEACEFUL,
                                false, rules, WorldDataConfiguration.DEFAULT), new WorldOptions(20261006L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions());
                return;
            }
            if (clientPhase == 2) {
                if (mc.getSingleplayerServer() != null || !(mc.screen instanceof TitleScreen)) return;
                require(stoppingFailure == null && stopped != null, "Final stopping snapshot unavailable: " + stoppingFailure);
                clientPhase = 1;
                mc.createWorldOpenFlows().loadLevel(new TitleScreen(), NativeTerritoryPerimeterFixture.WORLD);
                return;
            }
            if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || mc.screen != null) return;
            if (playerId == null) playerId = mc.player.getUUID();
            if (pending != null) {
                if (!pending.isDone()) return;
                Action action = pending.join(); pending = null;
                if (action == Action.USE_PLAN) mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                if (action == Action.RELOAD) {
                    mc.level.disconnect(); mc.clearLevel(); mc.setScreen(new TitleScreen()); clientPhase = 2; return;
                }
                if (action == Action.CAPTURE_COMPLETE) {
                    aim(mc, new Vec3(144, 67, 16));
                    if (!mc.levelRenderer.isChunkCompiled(new BlockPos(128, 64, 0))
                            || !mc.levelRenderer.isChunkCompiled(new BlockPos(159, 69, 31))) {
                        pending = CompletableFuture.completedFuture(Action.CAPTURE_COMPLETE); return;
                    }
                    REPORT.put("completedCaptureTerrainCompiled", true);
                    requestCapture("01-native-completed-territory-perimeter.png"); return;
                }
                if (action == Action.DONE) { finishing = true; finish(mc); return; }
            }
            CompletableFuture<Action> next = new CompletableFuture<>(); pending = next;
            var server = mc.getSingleplayerServer();
            server.execute(() -> {
                ServerLevel level = server.overworld();
                ServerPlayer owner = server.getPlayerList().getPlayer(playerId);
                try { next.complete(step(level, owner)); }
                catch (Throwable problem) {
                    try { REPORT.put("blockerDiagnostics", diagnostics(level, owner, true)); }
                    catch (Throwable diagnosticFailure) { REPORT.put("diagnosticFailure", diagnosticFailure.toString()); }
                    next.completeExceptionally(problem);
                }
            });
        } catch (Throwable problem) { fail(mc, problem); }
    }

    private static Action step(ServerLevel level, ServerPlayer owner) throws Exception {
        require(owner != null && owner.isAlive(), "Real player missing/dead");
        long now = level.getGameTime();
        if (stage > 0 && stage != 4 && stage != 14) require(now - stageTick < 2400, "Nonconstruction stage timeout: " + stage);
        switch (stage) {
            case 0 -> {
                require(CONSTRUCTION_SECONDS <= 45 * 60, "Fixture exceeds supported 45-minute construction budget");
                fixture = NativeTerritoryPerimeterFixture.setup(level, owner);
                liveBuildGoal = fixture.buildGoal(); liveStorageGoal = fixture.storageGoal();
                coreKey = SiegeCore.key(owner);
                verifyCurrentTerritory(level, NativeTerritoryPerimeterFixture.TERRITORY);
                REPORT.put("parkedUnrelatedStarterNpcIds", fixture.parkedAuxiliaries());
                require(balance(owner) == 0, "Fresh core unexpectedly had Treasury funds");
                FactionBank.credit(core(owner), 2000); RaidSavedData.get(owner.server).setDirty();
                var prepared = PerimeterConstruction.prepare(owner, NativeTerritoryPerimeterFixture.CORE, 1);
                require(prepared.ready(), "Production perimeter prepare rejected: " + prepared.problem());
                require(prepared.territory() != null && prepared.territory().chunks().equals(NativeTerritoryPerimeterFixture.TERRITORY),
                        "Production preparation omitted a same-faction claim record");
                require(prepared.builder() == builder(level) && prepared.plan().blocks().equals(fixture.plan().blocks())
                        && prepared.plan().clearance().equals(fixture.plan().clearance()), "Production quote differs from exact multi-claim union oracle");
                require(PerimeterConstruction.review(owner, NativeTerritoryPerimeterFixture.CORE, 1), "Production review failed");
                require(balance(owner) == 2000 && areaCount(level) == 0 && placed(level) == 0,
                        "Free review mutated blocks, charged money or created a job");
                selectPlan(owner);
                check("Fresh cheats-off survival owner, native core, two distinct same-faction claim records and three-chunk L territory");
                check("Production full-territory review exactly matches independent 2376-block L oracle and is free");
                sample(level, owner, "review"); advance(now, 1);
            }
            case 1 -> {
                if (now - stageTick < 40) return Action.NONE;
                var selection = PerimeterPreview.read(owner.getMainHandItem(), owner.getUUID(), level.dimension().location(), now);
                require(selection != null && selection.ready(), "Production plan preview not ready");
                verifyPreview(selection);
                check("Every free-plan preview box expands exactly to the complete same-faction L territory; no hidden bounding fill/internal border wall");
                advance(now, 2); return Action.USE_PLAN;
            }
            case 2 -> {
                if (now - stageTick < 20) return Action.NONE;
                require(balance(owner) == 1936 && owner.getMainHandItem().isEmpty(), "Real plan-use packet did not charge exactly 64 and consume one plan");
                require(builder(level).getPersistentData().hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID), "Production builder/job link missing");
                jobId = builder(level).getPersistentData().getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID);
                require(level.getEntity(jobId) instanceof ProtectedBuildArea, "Real paid protected job missing");
                ProtectedBuildArea area = (ProtectedBuildArea) level.getEntity(jobId);
                require(NativeConstructionGuard.commissionPaid(area) && NativeConstructionGuard.hasReservation(level, jobId)
                        && areaCount(level) == 1, "Paid activation/reservation missing or duplicate job exists");
                AcceptedConstructionPlan accepted = AcceptedConstructionPlan.capture(area);
                require(accepted.cells.size() == NativeTerritoryPerimeterFixture.BLOCKS, "Native accepted plan count differs");
                for (var entry : accepted.cells.entrySet()) require(fixture.plan().blocks().get(entry.getKey().asLong())
                        .equals(String.valueOf(ForgeRegistries.BLOCKS.getKey(entry.getValue().getBlock())))
                        && NativeTerritoryPerimeterFixture.TERRITORY.contains(new net.minecraft.world.level.ChunkPos(entry.getKey()))
                        && fixture.claimIds().contains(ClaimEvents.recruitsClaimManager.getClaim(new net.minecraft.world.level.ChunkPos(entry.getKey())).getUUID()),
                        "Native transformed target leaves exact paid territory/real claim records");
                verifyReceipt(level, area);
                acceptedScope = area.getPersistentData().getCompound("SiegePerimeterTerritory").copy();
                builder(level).setNoAi(false); // Last fixture write to the tested worker; native AI owns all movement/work now.
                constructionStarted = lastProgress = System.nanoTime(); placedBefore = placed(level);
                owner.teleportTo(level, 136.5, 65, -5.5, 0, 10); // Stationary survival observer, outside the claimed ring.
                check("Actual client plan-use packet produces one guarded native job, exactly 64 Treasury debit and consumed plan");
                sample(level, owner, "commissioned-ai-enabled"); advance(now, 3);
                return Action.USE_PLAN; // Ordinary second use of the now-empty hand must not commission again.
            }
            case 3 -> {
                if (now - stageTick < 20) return Action.NONE;
                require(balance(owner) == 1936 && areaCount(level) == 1, "Repeated client use charged or created another job");
                check("Repeated real client use after plan consumption cannot double-charge or create another job");
                advance(now, 4);
            }
            case 4 -> {
                require(!builder(level).isNoAi(), "Tested native builder AI disabled");
                if (now - lastSampleTick < 20) return Action.NONE;
                lastSampleTick = now;
                long placed = placed(level);
                if (placed != placedBefore) { lastProgress = System.nanoTime(); placedBefore = placed; }
                require(balance(owner) == 1936, "Native construction charged Treasury again");
                conservation(level, owner);
                if (SAMPLES.isEmpty() || now % 200 < 20 || placed == NativeTerritoryPerimeterFixture.BLOCKS)
                    sample(level, owner, "native-progress");
                require(System.nanoTime() - constructionStarted - pausedNanos <= CONSTRUCTION_SECONDS * SECOND, "Native completion exceeded count-derived construction bound");
                require(System.nanoTime() - lastProgress <= 180 * SECOND, "Native perimeter placed no new block for three minutes");
                if (!restartVerified && placed >= NativeTerritoryPerimeterFixture.BLOCKS / 4) {
                    require(placed < NativeTerritoryPerimeterFixture.BLOCKS, "Restart was not at genuine partial progress");
                    owner.setGameMode(GameType.SPECTATOR); pausedStarted = System.nanoTime(); pausedGeometry = geometry(level);
                    advance(now, 10); return Action.NONE;
                }
                if (placed < NativeTerritoryPerimeterFixture.BLOCKS) return Action.NONE;
                require(restartVerified, "Completion did not exercise an actual partial-progress restart");
                verifyExactCompletion(level, owner);
                REPORT.put("constructionSeconds", (System.nanoTime() - constructionStarted - pausedNanos) / (double) SECOND);
                check("Production-wrapped native builder goal, native storage collection and pathfinding placed all 2376 exact L-territory perimeter blocks through two finite native storages and a genuine restart");
                advance(now, 5);
            }
            case 5 -> {
                if (now - stageTick < 100) return Action.NONE;
                verifyExactCompletion(level, owner);
                require(level.getEntity(jobId) == null || ((ProtectedBuildArea) level.getEntity(jobId)).isDone(),
                        "Exact world completion did not reach native marker completion");
                REPORT.put("finalDiagnostics", diagnostics(level, owner, true));
                REPORT.put("completedBlocks", placed(level)); REPORT.put("treasuryDebit", 64);
                REPORT.put("materialCounts", Map.of("minecraft:cobblestone", NativeTerritoryPerimeterFixture.COBBLE, "minecraft:oak_planks", NativeTerritoryPerimeterFixture.OAK));
                REPORT.put("nativeCompletionObserved", true);
                REPORT.put("restartVerified", restartVerified);
                REPORT.put("territoryChunkCount", NativeTerritoryPerimeterFixture.TERRITORY.size());
                REPORT.put("nativeClaimRecords", fixture.claimIds().stream().map(UUID::toString).toList());
                check("Exact block states, unchanged non-plan cells, zero residual/loose stock and single 64 charge remain stable after completion");
                // Only after all payment, native-completion, geometry and stock checks pass: visual fixture camera.
                owner.setGameMode(GameType.SPECTATOR); owner.teleportTo(level, 173.5, 101, -18.5, 40, 45);
                advance(now, 6);
            }
            case 6 -> {
                if (now - stageTick < 60) return Action.NONE;
                advance(now, 7); return Action.CAPTURE_COMPLETE;
            }
            case 7 -> {
                require(SHOTS.contains("01-native-completed-territory-perimeter.png"), "Missing actual completed-ring framebuffer");
                return Action.DONE;
            }
            case 10 -> {
                require(geometry(level).equals(pausedGeometry), "Native construction continued after owner permission pause");
                if (now - stageTick < 60) return Action.NONE;
                require(NativeConstructionGuard.status(area(level)).toLowerCase(java.util.Locale.ROOT).contains("permission"),
                        "Actual permission-loss guard pause was not observed");
                var second = detachedClaimUpdate(ClaimEvents.recruitsClaimManager.getClaim(fixture.claimIds().get(1)));
                require(second != null && !second.getClaimedChunks().contains(NativeTerritoryPerimeterFixture.EXPANSION),
                        "Unexpected expansion fixture claim state");
                second.addChunk(NativeTerritoryPerimeterFixture.EXPANSION);
                ClaimEvents.recruitsClaimManager.addOrUpdateClaim(level, second);
                require(ClaimEvents.recruitsClaimManager.getClaim(second.getUUID()) == second,
                        "Native expansion update was canceled or did not replace the indexed claim");
                ClaimEvents.recruitsClaimManager.save(level);
                verifyCurrentTerritory(level, expandedTerritory());
                require(PerimeterTerritory.problem(level, area(level), NativeTerritoryPerimeterFixture.FACTION) != null,
                        "Expanded current territory was incorrectly accepted as the paid scope");
                verifyReceipt(level, area(level));
                advance(now, 11);
            }
            case 11 -> {
                require(geometry(level).equals(pausedGeometry) && balance(owner) == 1936,
                        "Expanded territory/permission pause changed paid geometry or Treasury");
                if (now - stageTick < 60) return Action.NONE;
                conservation(level, owner); sample(level, owner, "partial-before-save-request");
                require(count(owner.getInventory(), Items.COBBLESTONE) == 0 && count(owner.getInventory(), Items.OAK_PLANKS) == 0,
                        "Observer unexpectedly holds construction stock");
                owner.server.saveEverything(false, true, true);
                restartRequested = true; advance(now, 12); return Action.RELOAD;
            }
            case 12 -> {
                require(stopped != null && stoppingFailure == null, "Final pre-shutdown snapshot missing: " + stoppingFailure);
                require(owner.isSpectator(), "Actual saved owner permission state did not survive restart");
                require(geometry(level).equals(stopped.geometry()) && pendingCells(level).equals(stopped.pendingCells()),
                        "World restart changed accepted partial geometry/pending targets");
                require(chestValues(level).equals(stopped.chests()), "Actual native chest withdrawals did not persist through restart");
                require(loadedCargo != null && loadedCargo.equals(stopped.cargo())
                        && materialValue(loadedMain).equals(materialValue(stopped.main()))
                        && materialValue(loadedOff).equals(materialValue(stopped.off())),
                        "Native entity load changed exact construction cargo/equipment values at the pre-AI join boundary");
                require(area(level).getPersistentData().getCompound("SiegePerimeterTerritory").equals(stopped.scope())
                        && area(level).getPersistentData().getCompound("SiegeProtectedConstructionV1").equals(stopped.recipe())
                        && ConstructionEditLedger.get(level).sameGeneration(stopped.ledgerGeneration())
                        && NativeConstructionGuard.hasReservation(level, jobId)
                        && NativeConstructionGuard.commissionPaid(area(level)) && balance(owner) == stopped.treasury(),
                        "Full paid recipe/reservation/scope/ledger did not persist exactly");
                verifyCurrentTerritory(level, expandedTerritory()); verifyReceipt(level, area(level)); conservation(level, owner);
                REPORT.put("restartPlacedBlocks", stopped.placed()); REPORT.put("restartPendingCells", stopped.pendingCells().size());
                REPORT.put("shutdownSnapshotGameTime", stopped.gameTime());
                REPORT.put("reloadStockSnapshotBoundary", "ServerStoppingEvent after the final native simulation tick; entity cargo compared at EntityJoinLevelEvent before native AI");
                sample(level, owner, "actual-world-reload-exact-stock-and-recipe");
                check("Genuine partial-progress close/reopen preserves both native chests, entity cargo, full recipe, reserved scope, pending geometry, paid state and ledger");
                pausedGeometry = geometry(level);
                owner.setGameMode(GameType.SURVIVAL); // The territory mismatch must independently block a permitted, online owner.
                advance(now, 13);
            }
            case 13 -> {
                require(!owner.isSpectator() && owner.mayBuild() && !owner.isCreative(), "Territory-only pause lacks a real permitted Survival owner");
                require(geometry(level).equals(pausedGeometry) && balance(owner) == 1936,
                        "Native construction mutated accepted geometry or Treasury while complete territory scope differed");
                verifyReceipt(level, area(level)); conservation(level, owner);
                if (now - stageTick < 100) return Action.NONE;
                require(NativeConstructionGuard.status(area(level)).toLowerCase(java.util.Locale.ROOT).contains("territory"),
                        "Native scope-change pause is masked by another guard: " + NativeConstructionGuard.status(area(level)));
                check("After restart, Survival owner with expanded same-faction territory remains paused for 100 ticks with exact old scope and no mutation or payment");
                var second = detachedClaimUpdate(ClaimEvents.recruitsClaimManager.getClaim(fixture.claimIds().get(1)));
                require(second != null, "Second native claim record vanished");
                second.removeChunk(NativeTerritoryPerimeterFixture.EXPANSION);
                ClaimEvents.recruitsClaimManager.addOrUpdateClaim(level, second); ClaimEvents.recruitsClaimManager.save(level);
                require(ClaimEvents.recruitsClaimManager.getClaim(second.getUUID()) == second,
                        "Native scope restoration was canceled or did not replace the indexed claim");
                verifyCurrentTerritory(level, NativeTerritoryPerimeterFixture.TERRITORY);
                require(PerimeterTerritory.problem(level, area(level), NativeTerritoryPerimeterFixture.FACTION) == null,
                        "Restoring the original complete native territory did not restore the accepted scope");
                pausedNanos += System.nanoTime() - pausedStarted; lastProgress = System.nanoTime();
                advance(now, 14);
            }
            case 14 -> {
                conservation(level, owner); require(balance(owner) == 1936, "Restart/resume charged again");
                require(System.nanoTime() - lastProgress < 180 * SECOND, "Native AI did not resume within the normal no-placement bound");
                if (placed(level) <= stopped.placed()) return Action.NONE;
                require(area(level).nativeQueuesReady(), "Native accepted queues were not rebuilt after reload");
                var queued = new java.util.HashSet<Long>();
                for (var block : area(level).stackToPlace) require(queued.add(block.getPos().asLong()), "Duplicate native pending target after reload");
                require(queued.equals(pendingCells(level)), "Rebuilt native pending queue differs from exact remaining accepted geometry");
                restartVerified = true; lastProgress = System.nanoTime(); placedBefore = placed(level);
                check("Restoring original complete territory resumes actual native AI with an exact rebuilt remaining-target queue and no second charge");
                sample(level, owner, "native-resumed-after-restart-and-scope-restore");
                advance(now, 4);
            }
            default -> throw new AssertionError("Unexpected perimeter stage: " + stage);
        }
        return Action.NONE;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void nativeEntityJoined(EntityJoinLevelEvent event) {
        if (!ENABLED || fixture == null || event.getLevel().isClientSide()
                || !(event.getEntity() instanceof BuilderEntity builder)
                || !builder.getUUID().equals(fixture.builderId())) return;
        // This reads the actual freshly loaded native goals before production installs its wrappers.
        liveBuildGoal = builder.goalSelector.getAvailableGoals().stream().map(g -> g.getGoal())
                .filter(BuilderWorkGoal.class::isInstance).map(BuilderWorkGoal.class::cast).findFirst().orElse(null);
        liveStorageGoal = builder.goalSelector.getAvailableGoals().stream().map(g -> g.getGoal())
                .filter(GetNeededItemsFromStorage.class::isInstance).map(GetNeededItemsFromStorage.class::cast).findFirst().orElse(null);
        if (restartRequested && stopped != null) {
            loadedCargo = constructionValues(builder.getInventory());
            loadedMain = builder.getMainHandItem().save(new CompoundTag());
            loadedOff = builder.getOffhandItem().save(new CompoundTag());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void serverStopping(ServerStoppingEvent event) {
        if (!ENABLED || !restartRequested || stopped != null || fixture == null
                || !NativeTerritoryPerimeterFixture.WORLD.equals(event.getServer().getWorldData().getLevelName())) return;
        try {
            ServerLevel level = event.getServer().overworld(); BuilderEntity builder = builder(level);
            ProtectedBuildArea area = area(level);
            require(geometry(level).equals(pausedGeometry), "Native target changed during final shutdown ticks");
            conservation(level, null); verifyReceipt(level, area);
            CompoundTag core = RaidSavedData.get(event.getServer()).siegeCores.get(coreKey);
            require(core != null && FactionBank.balance(core) == 1936, "Final shutdown Treasury changed");
            stopped = new StopSnapshot(geometry(level), chestValues(level), constructionValues(builder.getInventory()),
                    builder.getMainHandItem().save(new CompoundTag()), builder.getOffhandItem().save(new CompoundTag()),
                    area.getPersistentData().getCompound("SiegePerimeterTerritory").copy(),
                    area.getPersistentData().getCompound("SiegeProtectedConstructionV1").copy(),
                    ConstructionEditLedger.get(level).generation(), pendingCells(level), FactionBank.balance(core), placed(level), level.getGameTime());
            REPORT.put("stoppingStock", Map.of("chestCobble", chestStock(level, Items.COBBLESTONE), "chestOak", chestStock(level, Items.OAK_PLANKS),
                    "builderCobble", workerStock(builder, Items.COBBLESTONE), "builderOak", workerStock(builder, Items.OAK_PLANKS), "placed", placed(level)));
        } catch (Throwable problem) { stoppingFailure = problem; }
    }

    private static ProtectedBuildArea area(ServerLevel level) {
        require(jobId != null && level.getEntity(jobId) instanceof ProtectedBuildArea, "Paid native territory marker missing");
        return (ProtectedBuildArea) level.getEntity(jobId);
    }
    private static RecruitsClaim detachedClaimUpdate(RecruitsClaim original) {
        require(original != null, "Native claim record unavailable for an update");
        CompoundTag before = original.toNBT().copy();
        RecruitsClaim update = RecruitsClaim.fromNBT(before.copy());
        require(update != original && update.getUUID().equals(original.getUUID()) && update.toNBT().equals(before),
                "Native public claim NBT copy did not preserve the exact update values");
        // The native manager removes old chunk indexes by reading its old object's chunk list.
        // Keep that indexed object intact until addOrUpdateClaim replaces it with this update.
        return update;
    }
    private static Set<net.minecraft.world.level.ChunkPos> expandedTerritory() {
        var chunks = new java.util.HashSet<>(NativeTerritoryPerimeterFixture.TERRITORY);
        chunks.add(NativeTerritoryPerimeterFixture.EXPANSION); return Set.copyOf(chunks);
    }
    private static void verifyCurrentTerritory(ServerLevel level, Set<net.minecraft.world.level.ChunkPos> expected) {
        var current = RecruitsClaimsBridge.getFactionTerritory(level, NativeTerritoryPerimeterFixture.FACTION, PerimeterTerritory.MAX_CHUNKS);
        require(current.ready() && current.chunks().equals(expected), "Native full same-faction territory is incomplete or changed unexpectedly");
        for (var chunk : expected) {
            var claim = ClaimEvents.recruitsClaimManager.getClaim(chunk);
            require(claim != null && fixture.claimIds().contains(claim.getUUID()), "Union cell is not in either actual native claim record");
        }
        if (!expected.contains(NativeTerritoryPerimeterFixture.EXPANSION))
            require(ClaimEvents.recruitsClaimManager.getClaim(NativeTerritoryPerimeterFixture.EXPANSION) == null, "Unowned notch acquired a claim");
    }
    private static void verifyReceipt(ServerLevel level, ProtectedBuildArea area) {
        require(PerimeterTerritory.tracked(area) && area.getPersistentData().getBoolean("SiegePerimeterTerritoryRequired"),
                "Paid perimeter has no required complete territory receipt");
        CompoundTag scope = area.getPersistentData().getCompound("SiegePerimeterTerritory");
        require(scope.getInt("Version") == 1 && NativeTerritoryPerimeterFixture.FACTION.equals(scope.getString("Faction")), "Unsupported accepted territory receipt");
        Set<net.minecraft.world.level.ChunkPos> chunks = new java.util.HashSet<>();
        for (long packed : scope.getLongArray("Chunks")) require(chunks.add(new net.minecraft.world.level.ChunkPos(packed)), "Duplicate accepted territory chunk");
        require(chunks.equals(NativeTerritoryPerimeterFixture.TERRITORY), "Paid scope differs from the complete original L territory");
        if (acceptedScope != null) require(scope.equals(acceptedScope), "Native runtime rewrote the immutable paid territory scope");
        CompoundTag recipe = area.getPersistentData().getCompound("SiegeProtectedConstructionV1");
        AcceptedConstructionPlan accepted = AcceptedConstructionPlan.load(recipe);
        AcceptedConstructionReservation reservation = AcceptedConstructionReservation.load(accepted, recipe.getCompound("Reservation"));
        Map<Long, String> cells = new LinkedHashMap<>();
        accepted.cells.forEach((pos, state) -> cells.put(pos.asLong(), String.valueOf(ForgeRegistries.BLOCKS.getKey(state.getBlock()))));
        require(cells.equals(fixture.plan().blocks()), "Persisted native recipe omits or changes full-territory cells");
        Set<BlockPos> expectedReservation = new java.util.HashSet<>();
        fixture.plan().blocks().keySet().forEach(p -> expectedReservation.add(BlockPos.of(p)));
        fixture.plan().clearance().forEach(p -> expectedReservation.add(BlockPos.of(p)));
        require(reservation.cells.equals(expectedReservation)
                && NativeConstructionGuard.hasReservation(level, jobId) && NativeConstructionGuard.commissionPaid(area),
                "Persisted exact structural/headroom reservation or single paid commission missing");
    }
    private static void verifyPreview(PerimeterPreview.Selection selection) {
        Map<Long, String> expanded = new LinkedHashMap<>();
        for (var box : selection.boxes()) {
            String material = switch (box.material()) { case 0 -> "minecraft:cobblestone"; case 1 -> "minecraft:oak_planks"; default -> "minecraft:dirt"; };
            for (BlockPos cell : BlockPos.betweenClosed(box.min(), box.max()))
                require(expanded.putIfAbsent(cell.asLong(), material) == null, "Free preview boxes overlap");
        }
        require(expanded.equals(NativeTerritoryPerimeterFixture.independentOracle()), "Free preview omits a claim or adds unowned/internal-border geometry");
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
        for (BlockPos pos : NativeTerritoryPerimeterFixture.CHESTS) {
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

    private static void verifyExactCompletion(ServerLevel level, ServerPlayer owner) {
        require(placed(level) == NativeTerritoryPerimeterFixture.BLOCKS && balance(owner) == 1936, "Completion geometry/payment mismatch");
        for (var entry : fixture.plan().blocks().entrySet()) {
            BlockState expected = ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation(entry.getValue())).defaultBlockState();
            require(level.getBlockState(BlockPos.of(entry.getKey())).equals(expected), "Wrong exact block state at " + BlockPos.of(entry.getKey()));
        }
        NativeTerritoryPerimeterFixture.assertInternalBordersOpen(level, fixture.plan().blocks());
        verifyCurrentTerritory(level, NativeTerritoryPerimeterFixture.TERRITORY);
        for (var entry : fixture.nonPlanCells().entrySet()) require(level.getBlockState(entry.getKey()).equals(entry.getValue()),
                "Native job modified a non-plan cell: " + entry.getKey());
        conservation(level, owner);
        for (Item material : List.of(Items.COBBLESTONE, Items.OAK_PLANKS)) require(chestStock(level, material) == 0
                && workerStock(builder(level), material) == 0 && (owner == null ? 0 : count(owner.getInventory(), material)) == 0
                && dropped(level, material) == 0, "Construction stock remained after exact finite-stock completion");
    }

    private static void conservation(ServerLevel level, ServerPlayer owner) {
        for (Item material : List.of(Items.COBBLESTONE, Items.OAK_PLANKS)) {
            int supplied = material == Items.COBBLESTONE ? NativeTerritoryPerimeterFixture.COBBLE : NativeTerritoryPerimeterFixture.OAK;
            long built = fixture.plan().blocks().keySet().stream().filter(p -> level.getBlockState(BlockPos.of(p)).getBlock().asItem() == material).count();
            long total = built + chestStock(level, material) + workerStock(builder(level), material)
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
        return level.getEntitiesOfClass(ItemEntity.class, NativeTerritoryPerimeterFixture.BOUNDS,
                entity -> entity.isAlive() && entity.getItem().is(item)).stream().mapToInt(entity -> entity.getItem().getCount()).sum();
    }
    private static long placed(ServerLevel level) {
        return fixture.plan().blocks().entrySet().stream().filter(e -> e.getValue().equals(
                String.valueOf(ForgeRegistries.BLOCKS.getKey(level.getBlockState(BlockPos.of(e.getKey())).getBlock())))).count();
    }
    private static void sample(ServerLevel level, ServerPlayer owner, String when) {
        var sample = new LinkedHashMap<>(diagnostics(level, owner, false)); sample.put("when", when);
        SAMPLES.add(sample);
        FactionLogger.LOG.info("Native territory perimeter QA: {}", new GsonBuilder().create().toJson(sample));
    }
    private static Map<String, Object> diagnostics(ServerLevel level, ServerPlayer owner, boolean detailed) {
        var result = new LinkedHashMap<String, Object>(); result.put("stage", stage); result.put("gameTime", level.getGameTime());
        if (fixture == null) return result;
        BuilderEntity builder = builder(level); var goal = liveBuildGoal; var storageGoal = liveStorageGoal;
        result.put("placed", placed(level)); result.put("treasury", owner == null ? -1 : balance(owner));
        result.put("builderPosition", builder.position().toString()); result.put("builderNoAi", builder.isNoAi());
        result.put("sleeping", builder.needsToSleep()); result.put("followState", builder.getFollowState());
        require(goal != null && storageGoal != null, "Fresh native goal references were unavailable after world load");
        result.put("nativeBuildState", String.valueOf(goal.state)); result.put("nativeTarget", String.valueOf(goal.blockPos));
        result.put("nativeBuildError", String.valueOf(goal.errorMessage));
        result.put("nativeRemaining", builder.currentBuildArea == null ? -1 : builder.currentBuildArea.stackToPlace.size());
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
        result.put("builderCobble", workerStock(builder, Items.COBBLESTONE)); result.put("builderOak", workerStock(builder, Items.OAK_PLANKS));
        result.put("looseCobble", dropped(level, Items.COBBLESTONE)); result.put("looseOak", dropped(level, Items.OAK_PLANKS));
        result.put("placedCobble", fixture.plan().blocks().keySet().stream().filter(p -> level.getBlockState(BlockPos.of(p)).is(Blocks.COBBLESTONE)).count());
        result.put("placedOak", fixture.plan().blocks().keySet().stream().filter(p -> level.getBlockState(BlockPos.of(p)).is(Blocks.OAK_PLANKS)).count());
        if (detailed) {
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
        return NativeTerritoryPerimeterFixture.CHESTS.stream().mapToInt(pos -> count(chest(level, pos), material)).sum();
    }
    private static int areaCount(ServerLevel level) { return level.getEntitiesOfClass(ProtectedBuildArea.class, NativeTerritoryPerimeterFixture.BOUNDS, e -> e.isAlive()).size(); }
    private static CompoundTag core(ServerPlayer owner) {
        var tag = RaidSavedData.get(owner.server).siegeCores.get(SiegeCore.key(owner)); require(tag != null, "Core treasury missing"); return tag;
    }
    private static long balance(ServerPlayer owner) { return FactionBank.balance(core(owner)); }
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
        require(directory.endsWith(Path.of("build", "native-territory-qa", "client"))
                && mc.gameDirectory.toPath().toRealPath().equals(directory.toRealPath()), "Unsafe active QA game directory");
        evidence = directory.getParent().resolve("evidence"); Files.createDirectories(evidence);
        REPORT.put("startedUtc", Instant.now().toString()); REPORT.put("mode", "territory-perimeter");
        REPORT.put("constructionLimitSeconds", CONSTRUCTION_SECONDS);
        REPORT.put("timeoutBasis", "One chunk measured 714.39s / 968 blocks = 0.738s per block; allow 1s per target plus 180s = 2556s for 2376 blocks, below 45min. Separate 8min setup/reload cap; normal 20TPS, no build-speed change.");
        REPORT.put("scope", "Fresh integrated survival world; two native Recruits same-faction claim records forming a three-chunk L; actual production free-plan/commission packets and native Workers AI with two finite single-chest storage areas. Actual partial-progress save/reopen, persisted complete scope and Survival territory-change pause. Fixture-only bounded terrain/setup and parked unrelated NPCs; spectator permission pause exercises production guard, aerial camera only after exact completion.");
        REPORT.put("notCovered", List.of("Dedicated-server networking", "Disjoint or holed territory native pathfinding", "Uneven terrain/other material palettes", "Storage coverage beyond 64 blocks", "Claim/block planning-cap boundaries (separate unit tests)", "Baseline HUD/mutation-matrix tests (separate QA workflow)"));
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
        FactionLogger.LOG.error("Native territory-perimeter QA failed in stage {}", stage, problem);
        if (mc.level != null && evidence != null) requestCapture("failure-native-territory-perimeter.png"); else finish(mc);
    }
    private static void finish(Minecraft mc) {
        if (finished) return; finished = true;
        REPORT.put("status", failure == null ? "passed" : "failed"); REPORT.put("stage", stage);
        REPORT.put("finishedUtc", Instant.now().toString()); REPORT.put("assertions", List.copyOf(CHECKS));
        REPORT.put("samples", List.copyOf(SAMPLES)); REPORT.put("screenshots", List.copyOf(SHOTS));
        if (failure != null) REPORT.put("failure", failure.toString());
        try { if (evidence != null) Files.writeString(evidence.resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(REPORT)); }
        catch (Exception writeFailure) { FactionLogger.LOG.error("Could not write full-perimeter evidence", writeFailure); }
        FactionLogger.LOG.info("Native territory-perimeter QA {}", failure == null ? "passed" : "failed"); mc.stop();
    }
    private static void advance(long now, int next) { stage = next; stageTick = now; }
    private static void check(String message) { CHECKS.add(message); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
