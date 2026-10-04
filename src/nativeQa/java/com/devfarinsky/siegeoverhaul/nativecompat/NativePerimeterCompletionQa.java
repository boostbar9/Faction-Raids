package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.FactionLogger;
import com.devfarinsky.siegeoverhaul.ModConstants;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import com.devfarinsky.siegeoverhaul.compat.WorkersConstructionView;
import com.devfarinsky.siegeoverhaul.core.FactionBank;
import com.devfarinsky.siegeoverhaul.core.PerimeterConstruction;
import com.devfarinsky.siegeoverhaul.core.PerimeterPreview;
import com.devfarinsky.siegeoverhaul.core.SiegeCore;
import com.devfarinsky.siegeoverhaul.items.ModItems;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import com.talhanation.recruits.ClaimEvents;
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
public final class NativePerimeterCompletionQa {
    private static final boolean ENABLED = Boolean.getBoolean("siegeoverhaul.nativeQa")
            && "perimeter-completion".equals(System.getProperty("siegeoverhaul.nativeQa.mode"));
    private static final long SECOND = 1_000_000_000L;
    private static final Map<String, Object> REPORT = new LinkedHashMap<>();
    private static final List<Map<String, Object>> SAMPLES = new ArrayList<>();
    private static final List<String> CHECKS = new ArrayList<>();
    private static final List<String> SHOTS = new ArrayList<>();
    private static NativePerimeterFixture.Fixture fixture;
    private static CompletableFuture<Action> pending;
    private static Path directory, evidence;
    private static UUID playerId, jobId;
    private static long started, constructionStarted, lastProgress, lastSampleTick = -1, placedBefore = -1, stageTick;
    private static int clientPhase, stage, renderFrames, captureFrame;
    private static boolean finished, finishing;
    private static Throwable failure;
    private static String capture;
    private static long captureRequested;
    private enum Action { NONE, USE_PLAN, CAPTURE_COMPLETE, DONE }

    private NativePerimeterCompletionQa() {}

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
            require(System.nanoTime() - started < 24 * 60 * SECOND, "Focused client exceeded 24-minute wall-clock bound");
            if (capture != null) return;
            if (clientPhase == 0) {
                if (!(mc.screen instanceof TitleScreen)) return;
                initialize(mc);
                require(!Files.exists(directory.resolve("saves").resolve(NativePerimeterFixture.WORLD)),
                        "Refusing an existing perimeter-completion world");
                mc.options.renderDistance().set(4); mc.options.simulationDistance().set(5);
                mc.options.guiScale().set(2); mc.options.pauseOnLostFocus = false; mc.resizeDisplay();
                GameRules rules = new GameRules();
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
                clientPhase = 1;
                mc.createWorldOpenFlows().createFreshLevel(NativePerimeterFixture.WORLD,
                        new LevelSettings(NativePerimeterFixture.WORLD, GameType.SURVIVAL, false, Difficulty.PEACEFUL,
                                false, rules, WorldDataConfiguration.DEFAULT), new WorldOptions(20261005L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions());
                return;
            }
            if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || mc.screen != null) return;
            if (playerId == null) playerId = mc.player.getUUID();
            if (pending != null) {
                if (!pending.isDone()) return;
                Action action = pending.join(); pending = null;
                if (action == Action.USE_PLAN) mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                if (action == Action.CAPTURE_COMPLETE) {
                    aim(mc, new Vec3(136, 67, 8));
                    if (!mc.levelRenderer.isChunkCompiled(new BlockPos(128, 64, 0))
                            || !mc.levelRenderer.isChunkCompiled(new BlockPos(143, 69, 15))) {
                        pending = CompletableFuture.completedFuture(Action.CAPTURE_COMPLETE); return;
                    }
                    REPORT.put("completedCaptureTerrainCompiled", true);
                    requestCapture("01-native-completed-perimeter.png"); return;
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
        if (stage > 0 && stage != 4) require(now - stageTick < 2400, "Nonconstruction stage timeout: " + stage);
        switch (stage) {
            case 0 -> {
                fixture = NativePerimeterFixture.setup(level, owner);
                REPORT.put("parkedUnrelatedStarterNpcIds", fixture.parkedAuxiliaries());
                require(balance(owner) == 0, "Fresh core unexpectedly had Treasury funds");
                FactionBank.credit(core(owner), 2000); RaidSavedData.get(owner.server).setDirty();
                var prepared = PerimeterConstruction.prepare(owner, NativePerimeterFixture.CORE, 1);
                require(prepared.ready(), "Production perimeter prepare rejected: " + prepared.problem());
                require(prepared.builder() == builder(level) && prepared.plan().blocks().equals(fixture.plan().blocks())
                        && prepared.plan().clearance().equals(fixture.plan().clearance()), "Production quote differs from exact one-claim oracle");
                require(PerimeterConstruction.review(owner, NativePerimeterFixture.CORE, 1), "Production review failed");
                require(balance(owner) == 2000 && areaCount(level) == 0 && placed(level) == 0,
                        "Free review mutated blocks, charged money or created a job");
                selectPlan(owner);
                check("Fresh cheats-off non-op survival owner, native faction/core and one actual 16x16 claim");
                check("Production review exactly matches 968-block oracle and is free");
                sample(level, owner, "review"); advance(now, 1);
            }
            case 1 -> {
                if (now - stageTick < 40) return Action.NONE;
                var selection = PerimeterPreview.read(owner.getMainHandItem(), owner.getUUID(), level.dimension().location(), now);
                require(selection != null && selection.ready(), "Production plan preview not ready");
                advance(now, 2); return Action.USE_PLAN;
            }
            case 2 -> {
                if (now - stageTick < 20) return Action.NONE;
                require(balance(owner) == 1100 && owner.getMainHandItem().isEmpty(), "Real plan-use packet did not charge exactly 900 and consume one plan");
                require(builder(level).getPersistentData().hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID), "Production builder/job link missing");
                jobId = builder(level).getPersistentData().getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID);
                require(level.getEntity(jobId) instanceof ProtectedBuildArea, "Real paid protected job missing");
                ProtectedBuildArea area = (ProtectedBuildArea) level.getEntity(jobId);
                require(NativeConstructionGuard.commissionPaid(area) && NativeConstructionGuard.hasReservation(level, jobId)
                        && areaCount(level) == 1, "Paid activation/reservation missing or duplicate job exists");
                AcceptedConstructionPlan accepted = AcceptedConstructionPlan.capture(area);
                require(accepted.cells.size() == NativePerimeterFixture.BLOCKS, "Native accepted plan count differs");
                for (var entry : accepted.cells.entrySet()) require(fixture.plan().blocks().get(entry.getKey().asLong())
                        .equals(String.valueOf(ForgeRegistries.BLOCKS.getKey(entry.getValue().getBlock())))
                        && ClaimEvents.recruitsClaimManager.getClaim(new net.minecraft.world.level.ChunkPos(entry.getKey())).getUUID().equals(fixture.claimId()),
                        "Native transformed target leaves exact paid plan/real claim");
                builder(level).setNoAi(false); // Last fixture write to the tested worker; native AI owns all movement/work now.
                constructionStarted = lastProgress = System.nanoTime(); placedBefore = placed(level);
                owner.teleportTo(level, 136.5, 65, -5.5, 0, 10); // Stationary survival observer, outside the claimed ring.
                check("Actual client plan-use packet produces one guarded native job, exactly 900 Treasury debit and consumed plan");
                sample(level, owner, "commissioned-ai-enabled"); advance(now, 3);
                return Action.USE_PLAN; // Ordinary second use of the now-empty hand must not commission again.
            }
            case 3 -> {
                if (now - stageTick < 20) return Action.NONE;
                require(balance(owner) == 1100 && areaCount(level) == 1, "Repeated client use charged or created another job");
                check("Repeated real client use after plan consumption cannot double-charge or create another job");
                advance(now, 4);
            }
            case 4 -> {
                require(!builder(level).isNoAi(), "Tested native builder AI disabled");
                if (now - lastSampleTick < 20) return Action.NONE;
                lastSampleTick = now;
                long placed = placed(level);
                if (placed != placedBefore) { lastProgress = System.nanoTime(); placedBefore = placed; }
                require(balance(owner) == 1100, "Native construction charged Treasury again");
                conservation(level, owner);
                if (SAMPLES.isEmpty() || now % 200 < 20 || placed == NativePerimeterFixture.BLOCKS)
                    sample(level, owner, "native-progress");
                require(System.nanoTime() - constructionStarted <= 20 * 60 * SECOND, "Native completion exceeded 20-minute construction bound");
                require(System.nanoTime() - lastProgress <= 180 * SECOND, "Native perimeter placed no new block for three minutes");
                if (placed < NativePerimeterFixture.BLOCKS) return Action.NONE;
                verifyExactCompletion(level, owner);
                REPORT.put("constructionSeconds", (System.nanoTime() - constructionStarted) / (double) SECOND);
                check("Production-wrapped native builder goal, native storage collection and pathfinding placed all 968 exact perimeter blocks");
                advance(now, 5);
            }
            case 5 -> {
                if (now - stageTick < 100) return Action.NONE;
                verifyExactCompletion(level, owner);
                require(level.getEntity(jobId) == null || ((ProtectedBuildArea) level.getEntity(jobId)).isDone(),
                        "Exact world completion did not reach native marker completion");
                REPORT.put("finalDiagnostics", diagnostics(level, owner, true));
                REPORT.put("completedBlocks", placed(level)); REPORT.put("treasuryDebit", 900);
                REPORT.put("materialCounts", Map.of("minecraft:cobblestone", NativePerimeterFixture.COBBLE, "minecraft:oak_planks", NativePerimeterFixture.OAK));
                REPORT.put("nativeCompletionObserved", true);
                check("Exact block states, unchanged non-plan cells, zero residual/loose stock and single 900 charge remain stable after completion");
                // Only after all payment, native-completion, geometry and stock checks pass: visual fixture camera.
                owner.setGameMode(GameType.SPECTATOR); owner.teleportTo(level, 151.5, 88, -12.5, 40, 45);
                advance(now, 6);
            }
            case 6 -> {
                if (now - stageTick < 60) return Action.NONE;
                advance(now, 7); return Action.CAPTURE_COMPLETE;
            }
            case 7 -> {
                require(SHOTS.contains("01-native-completed-perimeter.png"), "Missing actual completed-ring framebuffer");
                return Action.DONE;
            }
            default -> throw new AssertionError("Unexpected perimeter stage: " + stage);
        }
        return Action.NONE;
    }

    private static void verifyExactCompletion(ServerLevel level, ServerPlayer owner) {
        require(placed(level) == NativePerimeterFixture.BLOCKS && balance(owner) == 1100, "Completion geometry/payment mismatch");
        for (var entry : fixture.plan().blocks().entrySet()) {
            BlockState expected = ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation(entry.getValue())).defaultBlockState();
            require(level.getBlockState(BlockPos.of(entry.getKey())).equals(expected), "Wrong exact block state at " + BlockPos.of(entry.getKey()));
        }
        for (var entry : fixture.nonPlanCells().entrySet()) require(level.getBlockState(entry.getKey()).equals(entry.getValue()),
                "Native job modified a non-plan cell: " + entry.getKey());
        conservation(level, owner);
        for (Item material : List.of(Items.COBBLESTONE, Items.OAK_PLANKS)) require(count(chest(level), material) == 0
                && workerStock(builder(level), material) == 0 && count(owner.getInventory(), material) == 0
                && dropped(level, material) == 0, "Construction stock remained after exact finite-stock completion");
    }

    private static void conservation(ServerLevel level, ServerPlayer owner) {
        for (Item material : List.of(Items.COBBLESTONE, Items.OAK_PLANKS)) {
            int supplied = material == Items.COBBLESTONE ? NativePerimeterFixture.COBBLE : NativePerimeterFixture.OAK;
            long built = fixture.plan().blocks().keySet().stream().filter(p -> level.getBlockState(BlockPos.of(p)).getBlock().asItem() == material).count();
            long total = built + count(chest(level), material) + workerStock(builder(level), material)
                    + count(owner.getInventory(), material) + dropped(level, material);
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
        return level.getEntitiesOfClass(ItemEntity.class, NativePerimeterFixture.BOUNDS,
                entity -> entity.isAlive() && entity.getItem().is(item)).stream().mapToInt(entity -> entity.getItem().getCount()).sum();
    }
    private static long placed(ServerLevel level) {
        return fixture.plan().blocks().entrySet().stream().filter(e -> e.getValue().equals(
                String.valueOf(ForgeRegistries.BLOCKS.getKey(level.getBlockState(BlockPos.of(e.getKey())).getBlock())))).count();
    }
    private static void sample(ServerLevel level, ServerPlayer owner, String when) {
        var sample = new LinkedHashMap<>(diagnostics(level, owner, false)); sample.put("when", when);
        SAMPLES.add(sample);
        FactionLogger.LOG.info("Native perimeter QA: {}", new GsonBuilder().create().toJson(sample));
    }
    private static Map<String, Object> diagnostics(ServerLevel level, ServerPlayer owner, boolean detailed) {
        var result = new LinkedHashMap<String, Object>(); result.put("stage", stage); result.put("gameTime", level.getGameTime());
        if (fixture == null) return result;
        BuilderEntity builder = builder(level); var goal = fixture.buildGoal(); var storageGoal = fixture.storageGoal();
        result.put("placed", placed(level)); result.put("treasury", owner == null ? -1 : balance(owner));
        result.put("builderPosition", builder.position().toString()); result.put("builderNoAi", builder.isNoAi());
        result.put("sleeping", builder.needsToSleep()); result.put("followState", builder.getFollowState());
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
        if (level.getEntity(fixture.storageId()) instanceof StorageArea storage) {
            result.put("storageContainers", storage.storageMap.keySet().stream().map(BlockPos::toShortString).toList());
            result.put("storageAllowsBuilder", storage.canWorkHere(builder));
        }
        result.put("mainHand", stack(builder.getMainHandItem())); result.put("offHand", stack(builder.getOffhandItem()));
        result.put("mainHandSameObjectAsSlot5", builder.getMainHandItem() == builder.getInventory().getItem(5));
        result.put("chestCobble", count(chest(level), Items.COBBLESTONE)); result.put("chestOak", count(chest(level), Items.OAK_PLANKS));
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
    private static Container chest(ServerLevel level) {
        require(level.getBlockEntity(NativePerimeterFixture.CHEST) instanceof Container, "Native stock chest disappeared");
        return (Container) level.getBlockEntity(NativePerimeterFixture.CHEST);
    }
    private static int areaCount(ServerLevel level) { return level.getEntitiesOfClass(ProtectedBuildArea.class, NativePerimeterFixture.BOUNDS, e -> e.isAlive()).size(); }
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
        require(directory.endsWith(Path.of("build", "native-perimeter-qa", "client"))
                && mc.gameDirectory.toPath().toRealPath().equals(directory.toRealPath()), "Unsafe active QA game directory");
        evidence = directory.getParent().resolve("evidence"); Files.createDirectories(evidence);
        REPORT.put("startedUtc", Instant.now().toString()); REPORT.put("mode", "perimeter-completion");
        REPORT.put("scope", "Fresh integrated survival world; one real Recruits claim; actual production perimeter plan packet and native Workers AI/storage/pathfinding. Fixture-only terrain, faction, core, finite stock and parked unrelated NPCs. Spectator camera only after all completion checks pass.");
        REPORT.put("notCovered", List.of("Dedicated-server networking", "Offline-owner persistence", "Different terrain/claims", "Other material palettes", "Baseline HUD/mutation-matrix tests (separate QA workflow)"));
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
        FactionLogger.LOG.error("Native full-perimeter QA failed in stage {}", stage, problem);
        if (mc.level != null && evidence != null) requestCapture("failure-native-perimeter.png"); else finish(mc);
    }
    private static void finish(Minecraft mc) {
        if (finished) return; finished = true;
        REPORT.put("status", failure == null ? "passed" : "failed"); REPORT.put("stage", stage);
        REPORT.put("finishedUtc", Instant.now().toString()); REPORT.put("assertions", List.copyOf(CHECKS));
        REPORT.put("samples", List.copyOf(SAMPLES)); REPORT.put("screenshots", List.copyOf(SHOTS));
        if (failure != null) REPORT.put("failure", failure.toString());
        try { if (evidence != null) Files.writeString(evidence.resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(REPORT)); }
        catch (Exception writeFailure) { FactionLogger.LOG.error("Could not write full-perimeter evidence", writeFailure); }
        FactionLogger.LOG.info("Native full-perimeter QA {}", failure == null ? "passed" : "failed"); mc.stop();
    }
    private static void advance(long now, int next) { stage = next; stageTick = now; }
    private static void check(String message) { CHECKS.add(message); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
