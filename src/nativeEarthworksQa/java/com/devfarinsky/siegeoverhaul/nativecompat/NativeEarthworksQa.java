package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.FactionLogger;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import com.devfarinsky.siegeoverhaul.core.FactionBank;
import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksJournal;
import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksManifest;
import com.devfarinsky.siegeoverhaul.core.SiegeCore;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.entities.ai.GetNeededItemsFromStorage;
import com.talhanation.workers.world.BuildBlockParse;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.devfarinsky.siegeoverhaul.nativecompat.NativeEarthworksFixture.*;

/** Real integrated server, authentic player admission, and read-only ordinary tick observations. */
@Mod.EventBusSubscriber(modid = SiegeOverhaul.MOD_ID, value = Dist.CLIENT)
public final class NativeEarthworksQa {
    private static final boolean ENABLED = Boolean.getBoolean("siegeoverhaul.nativeEarthworksQa");
    private static final long SECOND = 1_000_000_000L;
    private static final Map<String, Object> REPORT = new LinkedHashMap<>();
    private static final List<Map<String, Object>> SAMPLES = new ArrayList<>(), TRANSITIONS = new ArrayList<>(), REFUSALS = new ArrayList<>();
    private static final Set<String> STORAGE_PHASES = new LinkedHashSet<>();
    private static final List<String> SHOTS = new ArrayList<>();
    private static Fixture fixture;
    private static PerimeterEarthworksManifest manifest;
    private static EarthworksCommission.Review acceptedReview;
    private static UUID ownerId, areaId;
    private static CompletableFuture<Integer> pending;
    private static Path directory, evidence;
    private static long started, setupTick, acceptedTick, initialInterestAt, initialTaxes, stableSince = -1, lastTick = -1;
    private static int phase, repeatedAccepts, nativeTransferTicks, requestObservations, observedTickCount;
    private static long observedStartTick = -1;
    private static double maximumDisplacement, movementWhileStorage;
    private static Vec3 initialPosition, previousPosition;
    private static Sample before;
    private static volatile Throwable failure;
    private static volatile boolean measured;
    private static boolean finished, finishing, groundingReleased;
    private static long groundingStartTick;
    private static String capture;
    private static int renderFrames, captureFrame;
    private static long captureStarted;
    private record Sample(long tick, int workerTick, int chest, int cargo, int world, int loose,
                          String nativeStorageState, int requests, int receipts, Vec3 position, String journal, int journalReceipts, boolean cleanupOutstanding) {
        Map<String, Object> evidence() {
            var row = new LinkedHashMap<String, Object>();
            row.put("gameTime", tick); row.put("workerTickCount", workerTick); row.put("chestDirt", chest);
            row.put("workerDirt", cargo); row.put("worldDirt", world); row.put("looseDirt", loose);
            row.put("nativeStorageState", nativeStorageState); row.put("requestCount", requests); row.put("supplyReceiptCount", receipts);
            row.put("position", List.of(position.x, position.y, position.z)); row.put("journalState", journal);
            row.put("journalReceiptCount", journalReceipts); row.put("cleanupOutstanding", cleanupOutstanding);
            return row;
        }
    }
    private NativeEarthworksQa() {}

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (started == 0) started = System.nanoTime();
            if (System.nanoTime() - started > 240 * SECOND && failure == null)
                failure = new AssertionError("One-FILL QA exceeded four-minute wall-clock cap");
            if (failure != null && !finishing) { measured = false; finishing = true; requestCapture("failure.png"); }
            if (finishing) {
                if (capture == null || System.nanoTime() - captureStarted > 15 * SECOND) finish(mc);
                return;
            }
            if (phase == 0) {
                if (!(mc.screen instanceof TitleScreen)) return;
                initialize(mc); require(!Files.exists(directory.resolve("saves").resolve(WORLD)), "Refusing existing QA save");
                mc.options.pauseOnLostFocus = false; mc.options.renderDistance().set(6); mc.options.simulationDistance().set(6);
                mc.options.guiScale().set(2); mc.resizeDisplay();
                var rules = new GameRules();
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null); rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
                phase = 1;
                mc.createWorldOpenFlows().createFreshLevel(WORLD, new LevelSettings(WORLD, GameType.SURVIVAL, false,
                        Difficulty.PEACEFUL, false, rules, WorldDataConfiguration.DEFAULT), new WorldOptions(20261005L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions());
                return;
            }
            if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || mc.screen != null) return;
            if (ownerId == null) ownerId = mc.player.getUUID();
            if (pending != null) {
                if (!pending.isDone()) return;
                int action = pending.join(); pending = null;
                if (action == 1) { aim(mc); requestCapture("01-accepted-empty-target.png"); }
                if (action == 2) { aim(mc); requestCapture("02-native-filled-target.png"); finishing = true; return; }
            }
            var next = new CompletableFuture<Integer>(); pending = next;
            var server = mc.getSingleplayerServer();
            server.execute(() -> {
                try { next.complete(step(server.overworld(), server.getPlayerList().getPlayer(ownerId))); }
                catch (Throwable problem) {
                    try { REPORT.put("diagnostics", diagnostics(server.overworld())); } catch (Throwable ignored) {}
                    next.completeExceptionally(problem);
                }
            });
        } catch (Throwable problem) { failure = problem; measured = false; }
    }

    private static int step(ServerLevel level, ServerPlayer owner) throws Exception {
        require(owner != null && owner == level.getServer().getPlayerList().getPlayer(owner.getUUID()), "Actual player-list owner missing");
        long now = level.getGameTime();
        if (fixture == null) { fixture = setup(level, owner); setupTick = now; return 0; }
        if (manifest == null) {
            require(now - setupTick < 200, "Ordinary startup/grounding/treasury setup did not settle: grounded="
                    + fixture.builder().onGround() + ", noAi=" + fixture.builder().isNoAi()
                    + ", treasuryClock=" + core(level).contains("BankInterestAt", Tag.TAG_LONG));
            if (now - setupTick < 30 || !core(level).contains("BankInterestAt", Tag.TAG_LONG)) return 0;
            if (!groundingReleased) {
                // NoAi suppresses genuine floor contact in this fixture. Let the unassigned
                // native worker settle on ordinary ticks before any grading review exists.
                fixture.builder().setNoAi(false); groundingReleased = true; groundingStartTick = now; return 0;
            }
            if (!fixture.builder().onGround()) return 0;
            require(now > groundingStartTick && fixture.builder().currentBuildArea == null
                    && fixture.builder().neededItems.isEmpty() && fixture.builder().getInventory().countItem(Items.DIRT) == 0
                    && count(chest(level)) == STOCK && level.getBlockState(TARGET).isAir(),
                    "Unassigned grounding warmup changed work/material state");
            fixture.builder().setNoAi(true); // Hold the genuinely grounded body during same-thread review/refusal checks only.
            REPORT.put("groundingWarmup", Map.of("ordinaryTicks", now - groundingStartTick, "startGameTime", groundingStartTick,
                    "settledGameTime", now, "actualOnGround", fixture.builder().onGround(), "bodyMinY", fixture.builder().getBoundingBox().minY,
                    "unassigned", true, "targetAir", true));
            freezeUnrelatedStartup(level, fixture.builder());
            require(FactionBank.balance(core(level)) == 0, "Unexpected unfunded treasury balance");
            FactionBank.credit(core(level), 64); RaidSavedData.get(level.getServer()).setDirty();
            initialInterestAt = core(level).getLong("BankInterestAt"); initialTaxes = core(level).getLong("CivilianTaxesTotal");
            verifyRefusals(level, owner);
            manifest = NativeEarthworksFixture.manifest(level, owner, fixture.builder(), TARGET, false);
            acceptedReview = EarthworksCommission.reviewLocal(owner, fixture.builder(), manifest, CORE, MARKER);
            var staleReview = EarthworksCommission.reviewLocal(owner, fixture.builder(), manifest, CORE, MARKER);
            require(FactionBank.balance(core(level)) == 64 && level.getBlockState(TARGET).isAir()
                    && fixture.builder().currentBuildArea == null && EarthworksJobLedger.get(level).job(manifest.header().project()) == null,
                    "Pure review changed world, builder, journal or Treasury");
            areaId = EarthworksCommission.acceptLocal(owner, fixture.builder(), acceptedReview);
            require(EarthworksCommission.acceptLocal(owner, fixture.builder(), acceptedReview).equals(areaId), "Same review retry is not idempotent");
            repeatedAccepts++;
            verifyMissingPaymentRefusal(level, owner);
            refuse("stale-review-after-accept", () -> EarthworksCommission.acceptLocal(owner, fixture.builder(), staleReview), "recovery");
            require(FactionBank.balance(core(level)) == 0, "Exactly one 64-emerald debit required");
            var wrappers = fixture.builder().goalSelector.getAvailableGoals().stream().map(g -> g.getGoal())
                    .filter(ProtectedStorageAccess.class::isInstance).map(ProtectedStorageAccess.class::cast)
                    .filter(g -> g.kind == ProtectedStorageAccess.Kind.NEEDED).toList();
            require(wrappers.size() == 1 && wrappers.get(0).delegate == fixture.originalStorageGoal(), "Original storage callback not retained");
            require(WorkersEarthworksPort.pinnedMaterialApiProblem() == null, "Pinned unary material ABI rejected");
            var parsed = BuildBlockParse.parseBlock(Blocks.DIRT);
            require(parsed != null && parsed.getItem() == Items.DIRT && !parsed.wasParsed(), "Pinned dirt parse changed");
            REPORT.put("identity", Map.of("owner", owner.getUUID().toString(), "builder", fixture.builder().getUUID().toString(),
                    "project", manifest.header().project().toString(), "area", areaId.toString(), "manifestHash", manifest.hash(),
                    "actualPlayerListMember", true, "normalProfileCacheMatched", true));
            REPORT.put("refusals", REFUSALS); REPORT.put("acceptedGameTime", now);
            REPORT.put("before", sample(level).evidence());
            fixture.builder().setNoAi(false); // Final fixture mutation. Every later work/storage/movement callback is ordinary AI.
            acceptedTick = now; initialPosition = previousPosition = fixture.builder().position();
            measured = true; return 1;
        }
        require(failure == null, "Ordinary native tick observer failed: " + failure);
        require(now - acceptedTick <= 3000, "Genuine one-FILL work did not complete within 150 ordinary seconds");
        var job = job(level); var journal = job.read().journal();
        if (journal.state() != PerimeterEarthworksJournal.State.STAGE_VERIFIED || stableSince < 0 || now - stableSince < 40) return 0;
        verifyFinish(level, owner);
        measured = false; return 2;
    }

    private static void verifyMissingPaymentRefusal(ServerLevel level, ServerPlayer owner) {
        // Pre-measurement fault injection in this disposable world only. Restore exact same core object.
        var cores = RaidSavedData.get(level.getServer()).siegeCores;
        var authoritative = cores.remove("team:" + FACTION);
        require(authoritative != null, "Missing fault-fixture core");
        try {
            refuse("missing-core-payment-retry", () -> EarthworksCommission.acceptLocal(owner, fixture.builder(), acceptedReview), "payment");
        } finally { cores.put("team:" + FACTION, authoritative); }
        require(EarthworksCommission.acceptLocal(owner, fixture.builder(), acceptedReview).equals(areaId)
                && FactionBank.balance(authoritative) == 0, "Restored original payment was not retained idempotently");
        repeatedAccepts++;
    }

    private static void verifyRefusals(ServerLevel level, ServerPlayer owner) {
        var worker = fixture.builder();
        var changed = NativeEarthworksFixture.manifest(level, owner, worker, TARGET, false);
        var review = EarthworksCommission.reviewLocal(owner, worker, changed, CORE, MARKER);
        // Explicit pre-measurement adversarial edit. Restored before a fresh, independently reviewed manifest.
        level.setBlock(SUPPORT, Blocks.COBBLESTONE.defaultBlockState(), 3);
        refuse("changed-support-after-review", () -> EarthworksCommission.acceptLocal(owner, worker, review), "terrain changed");
        level.setBlock(SUPPORT, Blocks.STONE.defaultBlockState(), 3);
        require(EarthworksJobLedger.get(level).job(changed.header().project()) == null, "Rejected support edit prepared a job");
        var foreign = NativeEarthworksFixture.manifest(level, owner, worker, FOREIGN, false);
        refuse("unclaimed-write-cell", () -> EarthworksCommission.reviewLocal(owner, worker, foreign, CORE, MARKER), "permission changed");
        require(!level.hasChunkAt(unloadedCell()), "Remote unloaded refusal fixture is unexpectedly loaded");
        var unloaded = NativeEarthworksFixture.manifest(level, owner, worker, unloadedCell(), true);
        refuse("unloaded-observation", () -> EarthworksCommission.reviewLocal(owner, worker, unloaded, CORE, unloadedCell().above()), "loaded terrain changed");
        require(!level.hasChunkAt(unloadedCell()), "Refusal loaded a previously absent chunk");
        require(FactionBank.balance(core(level)) == 64 && worker.currentBuildArea == null
                && worker.neededItems.isEmpty() && worker.getInventory().countItem(Items.DIRT) == 0
                && count(chest(level)) == STOCK && level.getBlockState(TARGET).isAir(), "Refusal changed finite stock/payment/assignment/terrain");
    }
    private static void refuse(String name, Runnable call, String reason) {
        String rejected = null;
        try { call.run(); } catch (IllegalStateException | IllegalArgumentException expected) { rejected = expected.getMessage(); }
        require(rejected != null && rejected.toLowerCase(java.util.Locale.ROOT).contains(reason),
                "Expected specific fail-closed refusal for " + name + ", observed " + rejected);
        REFUSALS.add(Map.of("case", name, "reason", rejected, "noExtraDebit", true));
    }

    /** Both sides of genuine server ticks are observations only, including the actual retained native goal. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (!ENABLED || !measured || finished || failure != null) return;
        try {
            ServerLevel level = event.getServer().overworld(); Sample current = sample(level);
            if (event.phase == TickEvent.Phase.START) { before = current; return; }
            if (before == null) return; // Acceptance may occur after this first START event.
            require(current.tick == before.tick + 1 && current.workerTick == before.workerTick + 1,
                    "Missing actual worker/server tick progression");
            require(lastTick < 0 || before.tick == lastTick, "Missing or duplicate ordinary server tick observation");
            if (observedStartTick < 0) observedStartTick = before.tick;
            observedTickCount++; lastTick = current.tick;
            require(current.chest + current.cargo + current.world + current.loose == STOCK && current.loose == 0,
                    "Finite chest + worker + world dirt conservation failed");
            require(current.chest >= 1 && current.chest <= STOCK && current.cargo >= 0 && current.cargo <= 1
                    && current.world >= 0 && current.world <= 1, "Supply duplicated or exceeded one-FILL demand");
            require(core(level).getLong("BankInterestAt") == initialInterestAt, "Unexpected daily interest boundary in bounded fixture");
            long taxes = core(level).getLong("CivilianTaxesTotal") - initialTaxes;
            require(taxes >= 0 && FactionBank.balance(core(level)) == taxes, "Treasury contains unexplained payment/credit");
            int[] debits = Arrays.stream(FactionBank.ledgerDeltas(core(level))).filter(v -> v < 0).toArray();
            require(debits.length == 1 && debits[0] == -64, "Missing or repeated commission debit");
            if (current.requests == 1) {
                require(fixture.builder().neededItems.get(0).count == 1 && fixture.builder().neededItems.get(0).required,
                        "Actual native request is not exactly one required material");
                require(EarthworksSupplyDemand.requestsProblem(fixture.builder()) == null, "Native demand lacks bound provenance");
                requestObservations++;
            }
            STORAGE_PHASES.add(before.nativeStorageState); STORAGE_PHASES.add(current.nativeStorageState);
            if (before.chest - current.chest == 1) {
                require("TAKE_NEEDED_ITEMS".equals(before.nativeStorageState)
                        && current.nativeStorageState.startsWith("CLOSE_CHEST_")
                        && current.cargo - before.cargo == 1 && current.receipts == 1,
                        "Source movement was not the original native TAKE callback with retained receipt");
                nativeTransferTicks++;
                TRANSITIONS.add(Map.of("event", "native-source-to-worker", "before", before.evidence(), "after", current.evidence()));
            }
            if (current.world - before.world == 1) {
                require(before.cargo - current.cargo == 1 && current.chest == before.chest
                        && current.workerTick % 5 == 0 && job(level).read().journal().receipts().size() == 1,
                        "Native placement did not consume one finite worker item on the native five-tick cadence");
                TRANSITIONS.add(Map.of("event", "native-worker-to-world", "before", before.evidence(), "after", current.evidence()));
            }
            maximumDisplacement = Math.max(maximumDisplacement, current.position.distanceTo(initialPosition));
            if (before.nativeStorageState.equals("MOVE_TO_STORAGE") || before.nativeStorageState.equals("MOVE_TO_CHEST"))
                movementWhileStorage += current.position.distanceTo(previousPosition);
            previousPosition = current.position;
            boolean stable = "STAGE_VERIFIED".equals(current.journal) && current.requests == 0 && current.cargo == 0
                    && current.chest == 1 && current.world == 1 && current.receipts == 1 && current.journalReceipts == 1 && !current.cleanupOutstanding;
            boolean beganStable = stable && stableSince < 0;
            if (beganStable) stableSince = current.tick;
            if (!stable) stableSince = -1;
            if (SAMPLES.isEmpty() || current.tick % 10 == 0 || current.world != before.world || current.chest != before.chest || beganStable)
                SAMPLES.add(current.evidence());
        } catch (Throwable problem) {
            failure = problem;
            try { REPORT.put("diagnostics", diagnostics(event.getServer().overworld())); } catch (Throwable ignored) {}
        }
    }

    private static void verifyFinish(ServerLevel level, ServerPlayer owner) {
        var worker = fixture.builder(); var journal = job(level).read().journal();
        require(requestObservations > 0 && nativeTransferTicks == 1 && maximumDisplacement > .1 && movementWhileStorage > .1,
                "Missing actual native request, one transfer or short storage movement evidence");
        require(STORAGE_PHASES.containsAll(Set.of("MOVE_TO_STORAGE", "SCAN_STORAGE", "MOVE_TO_CHEST", "OPEN_CHEST", "TAKE_NEEDED_ITEMS", "CLOSE_CHEST_DONE")),
                "Incomplete original native storage callback sequence");
        require(journal.receipts().size() == 1 && journal.receipts().get(0).consumedMaterialItems() == 1
                && journal.retirements().isEmpty() && journal.completionReceipt().isEmpty() && job(level).read().inFlight() == null,
                "First-slice receipt/retirement boundary changed");
        var supply = worker.getPersistentData().getCompound(EarthworksSupplyDemand.KEY);
        var receipts = supply.getList("Receipts", Tag.TAG_COMPOUND); require(receipts.size() == 1, "Exact source receipt absent");
        var receipt = receipts.getCompound(0);
        require(Arrays.equals(receipt.getLongArray("SourceCells"), new long[]{CHEST.asLong()})
                && receipt.getCompound("Item").getString("id").equals("minecraft:dirt")
                && receipt.getCompound("Item").getByte("Count") == 1, "Wrong native source/item receipt");
        require(ProtectedStorageAccess.dirtyNotifications(worker) >= 1, "Original transfer lacked persistent-container dirty notification");
        require(EarthworksCommission.acceptLocal(owner, worker, acceptedReview).equals(areaId), "Completed first-step same review retry duplicated job");
        repeatedAccepts++;
        var row = EarthworksJobLedger.get(level).save(new CompoundTag()).getList("Jobs", Tag.TAG_COMPOUND).getCompound(0);
        require(row.getLong("Sequence") == 1 && row.getString("Audit").matches("[0-9a-f]{64}"), "Exact native dispatch evidence missing");
        REPORT.put("after", sample(level).evidence()); REPORT.put("ordinaryTicksObserved", level.getGameTime() - acceptedTick);
        REPORT.put("observedStartGameTime", observedStartTick); REPORT.put("observedEndGameTime", lastTick);
        REPORT.put("observedTickCount", observedTickCount); REPORT.put("stableSinceGameTime", stableSince);
        REPORT.put("nativeStoragePhases", List.copyOf(STORAGE_PHASES)); REPORT.put("nativeTransferTicks", nativeTransferTicks);
        REPORT.put("requestObservations", requestObservations); REPORT.put("nativeDispatchSequence", row.getLong("Sequence"));
        REPORT.put("maximumWorkerDisplacement", maximumDisplacement); REPORT.put("movementWhileNativeStorage", movementWhileStorage);
        REPORT.put("repeatedAccepts", repeatedAccepts); REPORT.put("stabilityTicks", level.getGameTime() - stableSince);
        REPORT.put("journalState", journal.state().name()); REPORT.put("supplyReceipt", receipt.toString());
        REPORT.put("nativeAccountingReceipt", journal.receipts().get(0).nativeAccountingReceipt());
        REPORT.put("treasury", Map.of("funding", 64, "debit", 64, "debitCount", 1,
                "finalBalance", FactionBank.balance(core(level)), "observedTaxes", core(level).getLong("CivilianTaxesTotal") - initialTaxes));
        var censusReceipt = NativeDirtCensusQa.capture(level, owner, worker, job(level), stableSince, evidence);
        if (censusReceipt != null) REPORT.put("dirtCensus", censusReceipt);
    }

    private static Sample sample(ServerLevel level) {
        var worker = fixture.builder(); require(worker.isAlive() && level.getEntity(worker.getUUID()) == worker, "Actual builder missing");
        var state = level.getBlockState(TARGET); require(state.isAir() || state.is(Blocks.DIRT), "Unexpected target state");
        int loose = level.getEntitiesOfClass(ItemEntity.class, new AABB(TARGET).inflate(12)).stream()
                .filter(e -> e.getItem().is(Items.DIRT)).mapToInt(e -> e.getItem().getCount()).sum();
        var supply = worker.getPersistentData().getCompound(EarthworksSupplyDemand.KEY);
        var currentJob = manifest == null ? null : EarthworksJobLedger.get(level).job(manifest.header().project());
        return new Sample(level.getGameTime(), worker.tickCount, count(chest(level)), worker.getInventory().countItem(Items.DIRT),
                state.is(Blocks.DIRT) ? 1 : 0, loose, String.valueOf(fixture.originalStorageGoal().state), worker.neededItems.size(),
                supply.getList("Receipts", Tag.TAG_COMPOUND).size(), worker.position(), currentJob == null ? "NONE" : currentJob.read().journal().state().name(),
                currentJob == null ? 0 : currentJob.read().journal().receipts().size(), ProtectedInventoryCleanup.outstanding(worker.getPersistentData()));
    }
    private static Map<String, Object> diagnostics(ServerLevel level) {
        var result = new LinkedHashMap<String, Object>();
        if (fixture != null) {
            result.put("sample", sample(level).evidence()); result.put("builderData", fixture.builder().getPersistentData().toString());
            result.put("nativeRequests", fixture.builder().neededItems.size());
            var worker = fixture.builder(); var body = worker.getBoundingBox();
            result.put("actualOnGround", worker.onGround()); result.put("noAi", worker.isNoAi());
            result.put("noGravity", worker.isNoGravity()); result.put("velocity", vector(worker.getDeltaMovement()));
            result.put("bodyBounds", List.of(body.minX, body.minY, body.minZ, body.maxX, body.maxY, body.maxZ));
            result.put("followState", worker.getFollowState());
            result.put("nativeGoals", worker.goalSelector.getAvailableGoals().stream().map(goal -> Map.of(
                    "class", goal.getGoal().getClass().getName(), "priority", goal.getPriority(), "running", goal.isRunning())).toList());
            var bodyCells = new ArrayList<Map<String, Object>>(); boolean bodyLoaded = true;
            for (BlockPos pos : BlockPos.betweenClosed((int)Math.floor(body.minX), (int)Math.floor(body.minY), (int)Math.floor(body.minZ),
                    (int)Math.floor(Math.nextDown(body.maxX)), (int)Math.ceil(body.maxY) - 1, (int)Math.floor(Math.nextDown(body.maxZ)))) {
                boolean loaded = level.hasChunkAt(pos); bodyLoaded &= loaded;
                bodyCells.add(Map.of("pos", List.of(pos.getX(), pos.getY(), pos.getZ()), "loaded", loaded,
                        "state", loaded ? level.getBlockState(pos).toString() : "unloaded"));
            }
            result.put("bodyBlocks", bodyCells);
            result.put("bodyNoCollision", bodyLoaded ? level.noCollision(worker, body) : "not-read-unloaded");
            BlockPos floor = worker.blockPosition().below(); boolean floorLoaded = level.hasChunkAt(floor);
            result.put("floor", Map.of("pos", List.of(floor.getX(), floor.getY(), floor.getZ()), "loaded", floorLoaded,
                    "state", floorLoaded ? level.getBlockState(floor).toString() : "unloaded",
                    "sturdyUp", floorLoaded && level.getBlockState(floor).isFaceSturdy(level, floor, net.minecraft.core.Direction.UP)));
            var owner = level.getServer().getPlayerList().getPlayer(ownerId);
            if (owner != null) result.put("owner", Map.of("position", vector(owner.position()), "onGround", owner.onGround(),
                    "velocity", vector(owner.getDeltaMovement()), "noGravity", owner.isNoGravity(), "mayBuild", owner.mayBuild()));
            var bank = core(level); var treasury = new LinkedHashMap<String, Object>();
            treasury.put("balance", FactionBank.balance(bank)); treasury.put("hasSettlementTimestamp", bank.contains("BankInterestAt", Tag.TAG_LONG));
            treasury.put("settlementGameTime", bank.getLong("BankInterestAt")); treasury.put("interestRemainder", bank.getLong("BankInterestRemainder"));
            treasury.put("civilianTaxesTotal", bank.getLong("CivilianTaxesTotal")); treasury.put("currentGameTime", level.getGameTime());
            treasury.put("setupGameTime", setupTick); treasury.put("elapsedSetupTicks", level.getGameTime() - setupTick);
            treasury.put("minimumSetupTicks", 30); treasury.put("maximumSetupTicks", 200); treasury.put("expectedPreFundingBalance", 0);
            treasury.put("expectedClockPredicate", "Real BankInterestAt long exists after ordinary RaidEvents settlement");
            result.put("treasurySetup", treasury);
            if (manifest != null && job(level) != null && job(level).runtimeGoal != null) result.put("workBlocker", job(level).runtimeGoal.status());
        }
        return result;
    }
    private static List<Double> vector(Vec3 position) { return List.of(position.x, position.y, position.z); }
    private static EarthworksJobLedger.Job job(ServerLevel level) { return EarthworksJobLedger.get(level).job(manifest.header().project()); }
    private static CompoundTag core(ServerLevel level) {
        var core = RaidSavedData.get(level.getServer()).siegeCores.get("team:" + FACTION); require(core != null, "Authoritative core missing"); return core;
    }
    private static Container chest(ServerLevel level) {
        require(level.hasChunkAt(CHEST) && level.getBlockEntity(CHEST) instanceof Container, "Finite native chest unavailable");
        return (Container) level.getBlockEntity(CHEST);
    }
    private static int count(Container inventory) {
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) if (inventory.getItem(i).is(Items.DIRT)) total += inventory.getItem(i).getCount();
        return total;
    }

    private static void initialize(Minecraft mc) throws Exception {
        String configured = System.getProperty("siegeoverhaul.nativeEarthworksQa.directory", ""); require(!configured.isBlank(), "Missing QA directory");
        directory = Path.of(configured).toAbsolutePath().normalize();
        require(directory.endsWith(Path.of("build", "native-earthworks-qa", "client"))
                && mc.gameDirectory.toPath().toRealPath().equals(directory.toRealPath()), "Unsafe QA game directory");
        evidence = directory.getParent().resolve("evidence"); Files.createDirectories(evidence);
        require(!Files.exists(evidence.resolve("result.json")), "Refusing old native evidence");
        REPORT.put("mode", "earthworks-one-fill"); REPORT.put("startedUtc", Instant.now().toString());
        REPORT.put("scope", "Synthetic flat terrain, one native faction claim and finite two-dirt chest. Real integrated non-op player, normal profile cache, internal production review/accept, one ordinary native storage approach/transfer and AIR-to-DIRT callback. Partial STAGE_VERIFIED coverage only.");
        REPORT.put("syntheticSetup", List.of("Flat stone platform and empty fill cell", "Native command-style faction/claim setup", "One spawned idle native builder with ordinary unassigned grounding warmup", "Two finite dirt items in one native chest", "64-emerald Treasury funding", "Pre-accept changed-support refusal and restoration", "Pre-measurement missing-core retry refusal with exact core restoration", "Freeze unrelated startup NPCs"));
        REPORT.put("notCovered", List.of("CUT and drop authority", "Multi-step grading", "Autonomous grading approach or ascent", "Retirement or COMPLETE", "Save/restart recovery", "Normal player-facing review menu", "Existing accepted v1 projects", "Distant storage return route", "PR276 stepped perimeter geometry"));
        REPORT.put("ordinaryTicksOnly", true); REPORT.put("seededCompletedTargets", 0); REPORT.put("postStartTeleports", 0);
        REPORT.put("sourceStock", STOCK); REPORT.put("target", List.of(TARGET.getX(), TARGET.getY(), TARGET.getZ()));
        var versions = new LinkedHashMap<String, String>(); var artifacts = new LinkedHashMap<String, Object>();
        for (String id : List.of("minecraft", "forge", "siegeoverhaul", "workers", "recruits", "smallships", "siegeweapons")) {
            var info = ModList.get().getModContainerById(id).orElseThrow().getModInfo(); versions.put(id, info.getVersion().toString());
            if (List.of("workers", "recruits", "smallships", "siegeweapons").contains(id)) {
                Path path = info.getOwningFile().getFile().getFilePath(); require(Files.isRegularFile(path), "Cannot hash actual companion");
                artifacts.put(id, Map.of("fileName", path.getFileName().toString(), "sha256", sha256(path)));
            }
        }
        require("2.0.3".equals(versions.get("workers")) && "1.15.2".equals(versions.get("recruits")), "Unsupported native versions");
        REPORT.put("loadedModVersions", versions); REPORT.put("loadedCompanionArtifacts", artifacts);
    }
    @SubscribeEvent
    public static void render(TickEvent.RenderTickEvent event) {
        if (!ENABLED || finished || event.phase != TickEvent.Phase.END) return;
        renderFrames++;
        if (capture == null || renderFrames < captureFrame || evidence == null) return;
        try (NativeImage image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            require(image.getWidth() >= 640 && image.getHeight() >= 360, "Framebuffer too small");
            int base = image.getPixelRGBA(0, 0), changed = 0;
            for (int y = 0; y < image.getHeight(); y += 16) for (int x = 0; x < image.getWidth(); x += 16)
                if (image.getPixelRGBA(x, y) != base) changed++;
            require(changed > 50, "Blank framebuffer"); image.writeToFile(evidence.resolve(capture)); SHOTS.add(capture); capture = null;
        } catch (Throwable problem) { failure = problem; capture = null; finishing = true; }
    }
    private static void requestCapture(String name) { capture = name; captureFrame = renderFrames + 3; captureStarted = System.nanoTime(); }
    private static void aim(Minecraft mc) {
        Vec3 delta = TARGET.getCenter().subtract(mc.player.getEyePosition());
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
    private static void finish(Minecraft mc) {
        if (finished) return; finished = true; measured = false;
        REPORT.put("status", failure == null ? "passed" : "failed"); REPORT.put("finishedUtc", Instant.now().toString());
        REPORT.put("elapsedSeconds", (System.nanoTime() - started) / (double) SECOND);
        REPORT.put("samples", List.copyOf(SAMPLES)); REPORT.put("transitions", List.copyOf(TRANSITIONS)); REPORT.put("screenshots", List.copyOf(SHOTS));
        if (failure != null) REPORT.put("failure", failure.toString());
        try { if (evidence != null) Files.writeString(evidence.resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(REPORT)); }
        catch (Exception problem) { FactionLogger.LOG.error("Cannot write one-FILL evidence", problem); }
        FactionLogger.LOG.info("Native earthworks one-FILL QA {}", failure == null ? "passed" : "failed"); mc.stop();
    }
}
