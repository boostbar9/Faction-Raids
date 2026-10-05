package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.ModConstants;
import com.devfarinsky.siegeoverhaul.RaidNetwork;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.client.CoreHireScreen;
import com.devfarinsky.siegeoverhaul.core.*;
import com.devfarinsky.siegeoverhaul.items.DefensePlanItem;
import com.devfarinsky.siegeoverhaul.items.ModItems;
import com.talhanation.recruits.ClaimEvents;
import com.talhanation.recruits.world.RecruitsClaim;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.entities.ai.BuilderWorkGoal;
import com.talhanation.workers.world.NeededItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Bounded real-player/AI acceptance in a second fresh, cheats-off fixture world. */
final class NativeBuildingGameplay {
    private static final String WORLD = "siege-native-gameplay";
    private static final List<String> CHECKS = new ArrayList<>();
    private static final Map<String, Object> RESULT = new LinkedHashMap<>();
    private static final List<Map<String, Object>> STOCK = new ArrayList<>();
    private static CompletableFuture<Action> pending;
    private static NativeGameplayFixture.Fixture fixture;
    private static UUID playerId, jobId, ledgerGeneration;
    private static PerimeterProject acceptedPerimeter;
    private static UUID perimeterLedgerGeneration;
    private static int activeProjectionTargets;
    private static int clientStage, stage;
    private static long stageSince = -1, resumeAt, lastChange, lastPlaced = -1;
    private static long bankAfterManual;
    private static int suppliedCobble = 8, suppliedOak = 8;
    private static Map<Long, BlockState> pausedCells;
    private static List<ChunkPos> claimChunks;
    private static RecruitsClaim claim;
    private static BlockPos headroomCell, cavityCell;
    private static BlockState originalHeadroom, originalCavity;
    private static boolean done;
    private static long observedPlaced = -1;
    private static String observedHand = "";
    private static int observedReloadPlacements;
    private static int rebindsBeforeReload;
    private static List<Map<String, Object>> inventoryBeforeReload;
    private static Map<String, Object> mainHandBeforeReload;
    private static List<Map<String, Object>> chestBeforeReload;
    private static long completedClosedSince = -1;
    private static CompoundTag completedInventoryBeforeReload, completedInventoryLoaded;
    private static boolean completedLoadedSplitMirror;
    private static int completedRebindsBeforeReload;
    private static int rebindsBeforeFirstCommission;
    private static List<Map<String, Object>> inventoryBeforeFirstCommission;
    private static Map<String, Object> mainHandBeforeFirstCommission;
    private static int manualCommissionRetries;
    private static CompoundTag manualConfirmationPlan;
    private static int coreHudClientStage, coreHudMenuId;
    private static long coreHudDeadline;
    private static String coreHudFaction;
    private static List<ConstructionReport.Job> coreHudExpectedJobs;
    private static BlockPos plantCell;
    private static BlockPos sameStateEditCell;
    private static Map<Long, BlockState> perimeterBeforeEdit;
    private static boolean recordedNativeRequestMetadata;
    private static final Map<UUID, BuilderWorkGoal> NATIVE_BUILD_GOALS = new HashMap<>();

    private enum Action { NONE, USE_BLOCK, USE_AIR, CANCEL, HIDE, SHOW, RELOAD, CAPTURE_REVIEW, CAPTURE_PAID, CAPTURE_COMPLETED, AIM_WALL, OPEN_CORE, LIVE_CORE_HUD, PLACE_EDIT, START_BREAK_EDIT, CONTINUE_BREAK_EDIT, DONE }
    private NativeBuildingGameplay() {}

    static boolean tick(Minecraft mc) throws Exception {
        if (done) return true;
        if (clientStage == 0) {
            mc.setScreen(null); mc.level.disconnect(); mc.clearLevel(); mc.setScreen(new TitleScreen());
            clientStage = 1; return false;
        }
        if (clientStage == 1) {
            if (mc.getSingleplayerServer() != null || !(mc.screen instanceof TitleScreen)) return false;
            Path world = mc.gameDirectory.toPath().resolve("saves").resolve(WORLD);
            require(!Files.exists(world), "Refusing an existing gameplay world");
            GameRules rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
            clientStage = 2;
            mc.createWorldOpenFlows().createFreshLevel(WORLD,
                    new LevelSettings(WORLD, GameType.SURVIVAL, false, Difficulty.PEACEFUL, false,
                            rules, WorldDataConfiguration.DEFAULT), new WorldOptions(20261004L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions());
            return false;
        }
        if (clientStage == 4) {
            if (mc.getSingleplayerServer() != null || !(mc.screen instanceof TitleScreen)) return false;
            clientStage = 2;
            mc.createWorldOpenFlows().loadLevel(new TitleScreen(), WORLD);
            return false;
        }
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null
                || mc.screen != null && !(mc.screen instanceof CoreHireScreen && stage >= 301 && stage <= 302)) return false;
        if (playerId == null) playerId = mc.player.getUUID();
        clientStage = 3;
        if (pending != null) {
            if (!pending.isDone()) return false;
            Action action = pending.join(); pending = null;
            switch (action) {
                case AIM_WALL -> {
                    mc.player.setYRot(0); mc.player.yRotO = 0;
                    mc.player.setXRot(25); mc.player.xRotO = 25;
                    return false;
                }
                case USE_BLOCK -> {
                    BlockPos anchor = fixture.wallAnchor();
                    mc.player.setYRot(0); mc.player.yRotO = 0;
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(new Vec3(anchor.getX() + .5, anchor.getY(), anchor.getZ() + .5),
                                    Direction.UP, anchor.below(), false));
                }
                case USE_AIR -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                case OPEN_CORE -> {
                    aim(mc, Vec3.atCenterOf(fixture.corePos()));
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(fixture.corePos()).add(0, .5, 0),
                                    Direction.UP, fixture.corePos(), false));
                }
                case LIVE_CORE_HUD -> {
                    if (!captureLiveCoreHud(mc)) {
                        pending = CompletableFuture.completedFuture(Action.LIVE_CORE_HUD);
                        return false;
                    }
                }
                case PLACE_EDIT -> {
                    require(mc.player.getMainHandItem().is(Items.DIRT) && mc.player.getMainHandItem().getCount() == 1,
                            "Actual Survival client has not received its one fixture edit block");
                    aim(mc, Vec3.atCenterOf(sameStateEditCell));
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atBottomCenterOf(sameStateEditCell), Direction.UP, sameStateEditCell.below(), false));
                }
                case START_BREAK_EDIT -> {
                    aim(mc, Vec3.atCenterOf(sameStateEditCell));
                    mc.gameMode.startDestroyBlock(sameStateEditCell, Direction.WEST);
                }
                case CONTINUE_BREAK_EDIT -> mc.gameMode.continueDestroyBlock(sameStateEditCell, Direction.WEST);
                case CAPTURE_REVIEW -> {
                    aim(mc, new Vec3(fixture.wallAnchor().getX() + .5, 67, 30));
                    if (!mc.levelRenderer.isChunkCompiled(fixture.corePos().below())
                            || !mc.levelRenderer.isChunkCompiled(fixture.wallAnchor().below())
                            || !mc.levelRenderer.isChunkCompiled(new BlockPos(fixture.wallAnchor().getX(), 64, 30))) {
                        pending = CompletableFuture.completedFuture(Action.CAPTURE_REVIEW);
                        return false;
                    }
                    RESULT.put("reviewCaptureTerrainCompiled", true);
                    NativeBuildingQa.captureGameplay("14-production-free-perimeter-review.png");
                    return false;
                }
                case CAPTURE_PAID -> {
                    ProtectedBuildArea target = null;
                    for (var entity : mc.level.entitiesForRendering())
                        if (jobId.equals(entity.getUUID()) && entity instanceof ProtectedBuildArea p) target = p;
                    require(target != null, "Paid native marker did not synchronize to client");
                    aim(mc, target.position().add(0, .8, 0));
                    NativeBuildingQa.captureGameplay("15-production-paid-native-marker.png");
                    return false;
                }
                case CAPTURE_COMPLETED -> {
                    BuilderEntity nativeBuilder = null;
                    for (var entity : mc.level.entitiesForRendering())
                        if (fixture.builderId().equals(entity.getUUID()) && entity instanceof BuilderEntity builder) nativeBuilder = builder;
                    require(nativeBuilder != null, "Completed-wall native builder is not loaded on the real client");
                    Vec3 wallCenter = Vec3.atCenterOf(fixture.wallAnchor()).add(0, 2, 0);
                    aim(mc, wallCenter.add(nativeBuilder.position().add(0, 1, 0)).scale(.5));
                    if (!mc.levelRenderer.isChunkCompiled(fixture.expectedPlan().min())
                            || !mc.levelRenderer.isChunkCompiled(fixture.expectedPlan().max())) {
                        pending = CompletableFuture.completedFuture(Action.CAPTURE_COMPLETED);
                        return false;
                    }
                    for (var entry : fixture.expectedPlan().blocks().entrySet())
                        require(entry.getValue().equals(String.valueOf(ForgeRegistries.BLOCKS.getKey(mc.level.getBlockState(BlockPos.of(entry.getKey())).getBlock()))),
                                "Completed-wall client world has not synchronized its accepted geometry");
                    RESULT.put("completedWallCapture", "Real world blocks and native builder after strict completion/material assertions; no builder teleport or AI override");
                    NativeBuildingQa.captureGameplay("18-completed-manual-wall-and-builder.png");
                    return false;
                }
                case CANCEL -> RaidNetwork.protectedConstructionAction(jobId, ProtectedConstructionActions.CANCEL);
                case HIDE -> RaidNetwork.protectedConstructionAction(jobId, ProtectedConstructionActions.HIDE);
                case SHOW -> RaidNetwork.protectedConstructionAction(jobId, ProtectedConstructionActions.SHOW);
                case RELOAD -> {
                    mc.level.disconnect(); mc.clearLevel(); mc.setScreen(new TitleScreen()); clientStage = 4;
                    return false;
                }
                case DONE -> { done = true; return true; }
                default -> { }
            }
        }
        CompletableFuture<Action> next = new CompletableFuture<>(); pending = next;
        mc.getSingleplayerServer().execute(() -> {
            try {
                var server = mc.getSingleplayerServer();
                next.complete(step(server.overworld(), server.getPlayerList().getPlayer(playerId)));
            } catch (Throwable failure) {
                try {
                    var server = mc.getSingleplayerServer();
                    if (server != null && fixture != null) RESULT.put("failureGeometry", spatialDiagnostics(server.overworld()));
                } catch (Throwable unavailable) { failure.addSuppressed(unavailable); }
                next.completeExceptionally(failure);
            }
        });
        return false;
    }

    static Map<String, Object> result() {
        var result = new LinkedHashMap<>(RESULT);
        result.put("status", done ? "passed" : "incomplete"); result.put("stage", stage);
        result.put("stockSnapshots", List.copyOf(STOCK));
        result.put("assertions", List.copyOf(CHECKS));
        result.put("scope", "Fresh cheats-off integrated world; real survival player, real Recruits claims, production plan item packets and native worker AI. Fixture-only terrain/faction/core/stock setup.");
        result.put("notCovered", List.of("Dedicated network server", "All shader/resource-pack combinations", "Offline owner with a separately connected second player", "Scan-work upper-bound performance", "Malicious custom client fuzzing", "Additional player-edit/restart combinations beyond the enumerated same-state case", "1024/1025 exact rendered geometry", "Competing active builder integration", "Fought raid combat; economy uses explicitly seeded cleared-wave states", "Process crash between independent player/world SavedData writes"));
        return result;
    }

    static void observeNativeBuilder(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !WORLD.equals(level.getServer().getWorldData().getLevelName())
                || !(event.getEntity() instanceof BuilderEntity builder)) return;
        if (stage == 22 && fixture != null && builder.getUUID().equals(fixture.builderId())) {
            // This HIGHEST-priority join observer reads native NBT restoration before the living AI tick.
            completedInventoryLoaded = inventoryAndHands(builder);
            completedLoadedSplitMirror = builder.getMainHandItem() != builder.getInventory().getItem(5)
                    && ProtectedBuilderHandMirror.sameValue(builder.getMainHandItem(), builder.getInventory().getItem(5));
        }
        // Read the original goal before the production listener installs its wrapper; never alter it.
        builder.goalSelector.getAvailableGoals().stream().map(goal -> goal.getGoal())
                .filter(BuilderWorkGoal.class::isInstance).map(BuilderWorkGoal.class::cast).findFirst()
                .ifPresent(goal -> NATIVE_BUILD_GOALS.put(builder.getUUID(), goal));
    }

    private static Action step(ServerLevel level, ServerPlayer owner) throws Exception {
        require(owner != null, "Real gameplay player unavailable");
        long now = level.getGameTime();
        if (stageSince < 0) stageSince = now;
        require(now - stageSince < 2400, "Gameplay stage " + stage + " timed out: " + diagnostics(level));
        if (now < resumeAt) return Action.NONE;
        if (fixture != null && jobId != null && level.getEntity(jobId) instanceof ProtectedBuildArea liveArea
                && NativeConstructionGuard.status(liveArea).contains("move entities")
                && !RESULT.containsKey("firstPlacementBlocker"))
            RESULT.put("firstPlacementBlocker", spatialDiagnostics(level));
        if (stage >= 9 && stage <= 18 && !recordedNativeRequestMetadata && !builder(level).neededItems.isEmpty()) {
            stockSnapshot(level, "first-native-request-observed");
            recordedNativeRequestMetadata = true; // Evidence remains available even if a new provenance gate pauses before first placement.
        }
        if (stage == 18) {
            long currentPlaced = placed(level);
            String currentHand = String.valueOf(ForgeRegistries.ITEMS.getKey(builder(level).getMainHandItem().getItem()));
            if (!currentHand.equals(observedHand)) stockSnapshot(level, "post-reload-native-hand-material-switch");
            else if (currentPlaced != observedPlaced && observedReloadPlacements < 8)
                stockSnapshot(level, "post-reload-native-placement-" + (++observedReloadPlacements));
            observedHand = currentHand; observedPlaced = currentPlaced;
        }
        switch (stage) {
            case 0 -> {
                require(!owner.isCreative() && !owner.hasPermissions(2) && owner.mayBuild(), "Payment actor is not real non-op survival");
                fixture = NativeGameplayFixture.setup(level, owner);
                require(SiegeCore.point(owner.server, SiegeCore.key(owner)) != null, "Actual core/claim/anchor unavailable");
                stockSnapshot(level, "fixture-initial-stock");
                BuilderEntity idleBuilder = builder(level);
                require(!idleBuilder.getMainHandItem().isEmpty()
                                && idleBuilder.getMainHandItem() != idleBuilder.getInventory().getItem(5)
                                && ProtectedBuilderHandMirror.sameValue(idleBuilder.getMainHandItem(), idleBuilder.getInventory().getItem(5)),
                        "Initial idle native reload did not reproduce an equal-valued split hand mirror");
                rebindsBeforeFirstCommission = ProtectedBuilderHandMirror.rebindCount(idleBuilder.getPersistentData());
                inventoryBeforeFirstCommission = inventoryValues(idleBuilder);
                mainHandBeforeFirstCommission = stackDescription(idleBuilder.getMainHandItem());
                builder(level).setNoAi(true); // Transaction-only perimeter; wall AI below is enabled.
                FactionBank.credit(core(owner), 2000); RaidSavedData.get(owner.server).setDirty();
                RESULT.put("admissionReviews", NativeBuilderAdmissionContracts.verify(level, owner, fixture, idleBuilder));
                check("Real free manual/perimeter review identifies neighboring and paired-plant blockers without changing Treasury, worker receipts or inventory");
                require(PerimeterConstruction.review(owner, fixture.corePos(), 1), "Real perimeter review rejected");
                require(balance(owner) == 2000 && protectedAreas(level) == 0, "Free perimeter review changed money/jobs");
                select(owner, ModItems.PERIMETER_PLAN.get());
                check("Real non-op survival actor, indexed native faction/claim and production core placement");
                // Let ordinary onboarding/chat/claim notices fade naturally; do not hide them.
                advance(now, 1, 240);
            }
            case 1 -> {
                var selection = PerimeterPreview.read(owner.getMainHandItem(), owner.getUUID(), level.dimension().location(), now);
                require(selection != null && selection.ready(), "Real perimeter review is blocked: " + (selection == null ? "missing selection" : selection.problem()));
                advance(now, 100, 20); return Action.CAPTURE_REVIEW;
            }
            case 100 -> { advance(now, 2, 25); return Action.USE_AIR; }
            case 2 -> {
                require(balance(owner) == 1936, "Perimeter did not debit exactly 64 Treasury emeralds");
                jobId = linkedJob(level);
                var area = area(level);
                require(NativeConstructionGuard.commissionPaid(area), "Perimeter was not activated after payment");
                var projects = PerimeterProjectStore.all(core(owner));
                require(projects.size() == 1 && projects.get(0).active() != null
                                && projects.get(0).active().areaId().equals(jobId), "Paid native area is not the active whole-perimeter section");
                acceptedPerimeter = projects.get(0); perimeterLedgerGeneration = ConstructionEditLedger.get(level).generation();
                activeProjectionTargets = AcceptedConstructionPlan.capture(area).cells.size();
                require(activeProjectionTargets == acceptedPerimeter.active().layout().targets().size()
                                && area.getAlwaysShowProjection() == (activeProjectionTargets > 0 && activeProjectionTargets <= 1024),
                        "Native projection default differs from the established active-area target threshold");
                RESULT.put("perimeterProjection", Map.of("wholeTargets", acceptedPerimeter.targets().size(), "activeTargets", activeProjectionTargets,
                        "alwaysShownThreshold", 1024, "defaultAlwaysShown", area.getAlwaysShowProjection()));
                require(owner.getMainHandItem().isEmpty(), "Confirmed perimeter plan was not consumed");
                stockSnapshot(level, "after-first-production-commission-idle-reload-handoff");
                assertHandRebind(level, rebindsBeforeFirstCommission + 1);
                require(inventoryValues(builder(level)).equals(inventoryBeforeFirstCommission)
                                && stackDescription(builder(level).getMainHandItem()).equals(mainHandBeforeFirstCommission),
                        "Initial protected handoff changed idle builder inventory or main-hand values");
                check("Initial production commission value-preservingly binds an equal split mirror caused by idle native NBT reload");
                check("Real perimeter plan packet commissions once, consumes plan and debits exactly 64 Treasury");
                advance(now, 101, 20); return Action.CAPTURE_PAID;
            }
            case 101 -> { advance(now, 102, 20); return Action.HIDE; }
            case 102 -> {
                require(!area(level).getAlwaysShowProjection(), "Authenticated native-section hide action failed");
                RESULT.put("authenticatedProjectionHide", true);
                advance(now, 3, 20); return Action.SHOW;
            }
            case 3 -> {
                require(area(level).getAlwaysShowProjection(), "Authenticated native-section show action failed");
                RESULT.put("authenticatedProjectionShow", true);
                check("Native active-area target count determines the unchanged 1024 threshold; authenticated owner hide/show controls both work");
                owner.teleportTo(fixture.corePos().getX() + 3.5, 65, fixture.corePos().getZ() - 2.5);
                advance(now, 300, 20);
            }
            case 300 -> { advance(now, 301, 20); return Action.OPEN_CORE; }
            case 301 -> {
                if (!(owner.containerMenu instanceof CoreHireMenu menu)) return Action.NONE;
                require(menu.stillValid(owner) && balance(owner) == 1936 && menu.bank() == 1936,
                        "Real core menu has wrong owner access or Treasury");
                require(owner.getTeam() instanceof net.minecraft.world.scores.PlayerTeam
                                && fixture.factionId().equals(owner.getTeam().getName()),
                        "Real core menu owner has wrong faction identity");
                coreHudFaction = ((net.minecraft.world.scores.PlayerTeam) owner.getTeam()).getDisplayName().getString();
                // factionName is a client packet cache; the authoritative server value is its actual PlayerTeam.
                coreHudMenuId = menu.containerId;
                coreHudExpectedJobs = ConstructionReport.snapshot(owner);
                require(coreHudExpectedJobs.size() == 1 && coreHudExpectedJobs.get(0).label().toLowerCase(Locale.ROOT).contains("perimeter")
                                && acceptedPerimeter.header().projectId().equals(coreHudExpectedJobs.get(0).projectId()),
                        "Live paid perimeter is missing from server construction report");
                coreHudDeadline = System.nanoTime() + 30L * 1_000_000_000;
                advance(now, 302, 0); return Action.LIVE_CORE_HUD;
            }
            case 302 -> {
                if (owner.containerMenu instanceof CoreHireMenu) return Action.NONE;
                require(balance(owner) == 1936 && protectedAreas(level) == 1
                                && NativeConstructionGuard.commissionPaid(area(level))
                                && NativeConstructionGuard.hasReservation(level, jobId),
                        "Read-only live Building navigation changed Treasury, job or reservation");
                check("Production core-use packets open live owner/faction/Treasury menu; actual Building navigation receives exact server construction rows without commissioning");
                // Use a real accepted base cell from the active section, preserving the western-edge
                // Survival placement/mining approach even if native serialization splits that section further.
                sameStateEditCell = AcceptedConstructionPlan.capture(area(level)).cells.keySet().stream()
                        .filter(pos -> pos.getX() == acceptedPerimeter.plan().min().getX() && pos.getY() == 65)
                        .filter(pos -> level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).is(Blocks.STONE))
                        .min(Comparator.comparingInt((BlockPos pos) -> Math.abs(pos.getZ() - 14)).thenComparingInt(BlockPos::getZ)).orElseThrow();
                require(!owner.isCreative() && !owner.hasPermissions(2) && owner.mayBuild()
                                && level.mayInteract(owner, sameStateEditCell), "Same-state edit actor lacks real non-op Survival permission");
                require(AcceptedConstructionPlan.capture(area(level)).cells.containsKey(sameStateEditCell)
                                && level.getBlockState(sameStateEditCell).is(Blocks.AIR)
                                && level.getBlockState(sameStateEditCell.below()).is(Blocks.STONE),
                        "Chosen same-state edit cell is not accepted AIR with existing solid footing");
                Set<Long> reserved = acceptedPerimeter.reservation();
                require(!reserved.isEmpty() && ConstructionEditLedger.get(level).matchesProjectReservation(acceptedPerimeter),
                        "Complete paid perimeter reservation unavailable before the real player edit");
                Map<Long, BlockState> beforeEdit = new HashMap<>();
                for (long pos : reserved) beforeEdit.put(pos, level.getBlockState(BlockPos.of(pos)));
                perimeterBeforeEdit = Map.copyOf(beforeEdit);
                require(owner.getMainHandItem().isEmpty(), "Fixture edit would overwrite a real held item");
                owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT, 1));
                owner.inventoryMenu.broadcastChanges();
                owner.teleportTo(sameStateEditCell.getX() - 1.5, 65, sameStateEditCell.getZ() + .5);
                RESULT.put("sameStatePlayerEdit", Map.of("cell", sameStateEditCell.toShortString(), "fixtureDirtProvided", 1,
                        "action", "Actual Survival placement and ordinary empty-hand mining packets; no QA world replacement"));
                advance(now, 303, 20);
            }
            case 303 -> { advance(now, 304, 20); return Action.PLACE_EDIT; }
            case 304 -> {
                require(level.getBlockState(sameStateEditCell).is(Blocks.DIRT) && owner.getMainHandItem().isEmpty()
                                && ConstructionEditLedger.get(level).edited(jobId),
                        "Actual Survival placement did not consume one dirt block and record the player edit");
                require(balance(owner) == 1936, "Player placement changed the paid perimeter Treasury");
                advance(now, 305, 0); return Action.START_BREAK_EDIT;
            }
            case 305 -> {
                if (level.getBlockState(sameStateEditCell).is(Blocks.DIRT)) return Action.CONTINUE_BREAK_EDIT;
                require(level.getBlockState(sameStateEditCell).is(Blocks.AIR)
                                && ConstructionEditLedger.get(level).edited(jobId) && perimeterUnchanged(level),
                        "Actual mining did not return accepted AIR while preserving same-state edit history");
                check("Real Survival place/break packets restore the exact accepted AIR state while the native ledger retains edited=true");
                builder(level).setNoAi(false); // Let the real native goal attempt work; never invoke its guard from QA.
                advance(now, 306, 120);
            }
            case 306 -> {
                var currentPerimeter = PerimeterProjectStore.get(core(owner), acceptedPerimeter.header().projectId());
                String editPause = NativeConstructionGuard.status(area(level)) + " " + (currentPerimeter == null ? "" : currentPerimeter.blocker());
                require(editPause.contains("site was edited") || editPause.contains("edit history"),
                        "Real staged/native authority did not expose its player-edit pause: " + editPause);
                require(ConstructionEditLedger.get(level).sameGeneration(perimeterLedgerGeneration)
                                && ConstructionEditLedger.get(level).matchesProjectIdentity(acceptedPerimeter)
                                && ConstructionEditLedger.get(level).edited(acceptedPerimeter.header().projectId()),
                        "Actual player edit was not retained by the complete perimeter ledger");
                require(perimeterUnchanged(level) && ConstructionEditLedger.get(level).edited(jobId)
                                && balance(owner) == 1936 && NativeConstructionGuard.commissionPaid(area(level)),
                        "Native AI altered accepted cells, edit history or payment after a same-state player edit");
                conservation(level); // Transfers may occur naturally, but all eight of each initial material must still exist.
                stockSnapshot(level, "same-state-player-edit-native-pause-before-cancel");
                check("Actual same-state player edit pauses active native construction with no accepted-cell writes or material/Treasury change before normal cancellation");
                owner.teleportTo(fixture.corePos().getX() + 3.5, 65, fixture.corePos().getZ() - 2.5);
                advance(now, 307, 20);
            }
            case 307 -> {
                require(owner.distanceToSqr(area(level)) <= 16 * 16, "Fixture observer is outside actual cancellation reach");
                advance(now, 4, 20); return Action.CANCEL;
            }
            case 4 -> {
                var canceled = PerimeterProjectStore.get(core(owner), acceptedPerimeter.header().projectId());
                var terminal = PerimeterProjectStore.terminal(core(owner), acceptedPerimeter.header().projectId());
                require(canceled != null && canceled.state() == PerimeterProject.State.CANCELED
                                || terminal != null && terminal.state() == PerimeterProject.State.CANCELED,
                        "Native marker cancellation did not cancel the whole paid project");
                require(ConstructionEditLedger.get(level).sameGeneration(perimeterLedgerGeneration)
                                && balance(owner) == 1936 && perimeterUnchanged(level), "Canceled project changed ledger generation, Treasury or accepted cells");
                conservation(level);
                boolean wholeCleanup = !ConstructionEditLedger.get(level).contains(acceptedPerimeter.header().projectId())
                                && acceptedPerimeter.stages().stream().noneMatch(part -> ConstructionEditLedger.get(level).contains(part.areaId()))
                                && !PerimeterProjectLink.reserved(builder(level));
                if (!wholeCleanup) {
                    require(now - stageSince < 600, "Whole-project cancellation did not finish native cleanup within 30 seconds");
                    return Action.NONE; // Native item use or transfer cleanup can finish on ordinary ticks.
                }
                require(level.getEntity(jobId) == null && !NativeConstructionGuard.hasReservation(level, jobId),
                        "Real perimeter cancellation did not retire marker/reservation");
                require(balance(owner) == 1936 && builder(level).currentBuildArea == null,
                        "Perimeter cancellation refunded or left builder attached");
                RESULT.put("canceledWholePerimeter", Map.of("project", acceptedPerimeter.header().projectId().toString(),
                        "globalTargets", acceptedPerimeter.targets().size(), "stageCount", acceptedPerimeter.stages().size(), "treasury", balance(owner)));
                check("Real perimeter cancellation retires every global/stage reservation and builder association without refund");
                plantCell = fixture.wallAnchor().offset(2, 0, 2);
                require("minecraft:cobblestone".equals(fixture.expectedPlan().blocks().get(plantCell.asLong()))
                                && !fixture.expectedPlan().blocks().containsKey(plantCell.below().asLong()),
                        "Plant fixture or its support is outside the intended native mutation contract");
                // One pre-acceptance fixture seed only. The real native worker must remove it later.
                level.setBlock(plantCell.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                level.setBlock(plantCell, Blocks.DANDELION.defaultBlockState(), 3);
                assertLivePlant(level);
                RESULT.put("singleCellPlant", Map.of("cell", plantCell.toShortString(), "plant", "minecraft:dandelion",
                        "support", plantCell.below().toShortString(), "supportOutsideMutationPlan", true));
                FactionBank.debit(core(owner), 1936); RaidSavedData.get(owner.server).setDirty();
                require(DefenseStructures.givePlan(owner, DefenseBlueprint.Kind.WALL.ordinal()), "Free manual plan delivery failed");
                select(owner, ModItems.defensePlan(DefenseBlueprint.Kind.WALL).get());
                owner.teleportTo(fixture.wallAnchor().getX() + .5, 65, fixture.wallAnchor().getZ() - 3.5);
                advance(now, 5, 20); return Action.AIM_WALL;
            }
            case 5 -> {
                require(owner.getDirection() == Direction.SOUTH, "Actual movement packets did not establish manual-plan facing");
                advance(now, 6, 20); return Action.USE_BLOCK;
            }
            case 6 -> {
                assertLivePlant(level);
                require(balance(owner) == 0 && protectedAreas(level) == 0 && owner.getMainHandItem().getItem() instanceof DefensePlanItem,
                        "Free manual preview changed Treasury, plan or job count");
                require(DefensePreview.read(owner.getMainHandItem(), DefenseBlueprint.Kind.WALL, level.dimension().location(), owner.getUUID(), now) != null,
                        "Actual plan use did not create an owner-bound preview");
                advance(now, 7, 20); return Action.USE_BLOCK;
            }
            case 7 -> {
                assertLivePlant(level);
                require(balance(owner) == 0 && protectedAreas(level) == 0 && owner.getMainHandItem().getItem() instanceof DefensePlanItem,
                        "Insufficient Treasury failure spent money, consumed plan or leaked a job");
                require(!ConstructionEditLedger.get(level).reserves(plannedPositions()), "Failed commission leaked reservation");
                check("Actual free manual preview and insufficient-Treasury confirmation preserve funds, plan and reservations");
                FactionBank.credit(core(owner), 1000); RaidSavedData.get(owner.server).setDirty();
                builder(level).setNoAi(false);
                manualConfirmationPlan = owner.getMainHandItem().save(new CompoundTag());
                advance(now, 8, 20); return Action.USE_BLOCK;
            }
            case 8 -> {
                if (balance(owner) == 1000) {
                    assertLivePlant(level);
                    require(protectedAreas(level) == 0
                                    && owner.getMainHandItem().getItem() instanceof DefensePlanItem
                                    && owner.getMainHandItem().save(new CompoundTag()).equals(manualConfirmationPlan)
                                    && DefensePreview.read(owner.getMainHandItem(), DefenseBlueprint.Kind.WALL, level.dimension().location(), owner.getUUID(), now) != null
                                    && !ConstructionEditLedger.get(level).reserves(plannedPositions()),
                            "Temporary manual confirmation refusal changed the plan, jobs or reservations");
                    String temporary = NativeConstructionGuard.commissionProblem(builder(level));
                    require(temporary == null || temporary.contains("using an item"),
                            "Manual confirmation encountered a non-transient builder blocker: " + temporary);
                    if (temporary != null) {
                        RESULT.put("manualCommissionWaitedForNativeItemUse", true);
                        return Action.NONE; // Actual native eating/use finishes normally; never stop it for QA.
                    }
                    require(manualCommissionRetries < 3, "Manual confirmation remained unpaid after bounded natural retries: " + diagnostics(level));
                    manualCommissionRetries++;
                    RESULT.put("manualCommissionRetries", manualCommissionRetries);
                    if (manualCommissionRetries == 1)
                        check("Temporary unpaid manual confirmation preserves exact Treasury, plan and reservations before natural retry");
                    resumeAt = now + 20; // Keep the original stage deadline; retries cannot extend it.
                    return Action.USE_BLOCK;
                }
                require(DefenseBlueprint.Kind.WALL.price == 8 && balance(owner) == 1000 - DefenseBlueprint.Kind.WALL.price
                                && balance(owner) == 992, "Manual commissioning did not debit exactly 8 Treasury emeralds");
                bankAfterManual = balance(owner); jobId = linkedJob(level);
                require(NativeConstructionGuard.commissionPaid(area(level)) && owner.getMainHandItem().isEmpty(),
                        "Manual job was not paid/activated or plan remains");
                require(!builder(level).isNoAi(), "Native builder AI is disabled");
                require(hasAcceptedPlant(level) && !NativeConstructionGuard.reserves(level, List.of(plantCell.below())),
                        "Paid native acceptance did not record the live dandelion or reserved its support for mutation");
                check("Persistent single-cell dandelion survives free/unpaid review and is recorded intact in the real paid acceptance snapshot");
                NativeHollowWallOracle.assertManualCavitiesAir(level, fixture.wallAnchor());
                CompoundTag paidRecipe = area(level).getPersistentData().getCompound("SiegeProtectedConstructionV1");
                var acceptedWall = AcceptedConstructionPlan.load(paidRecipe);
                var acceptedReservation = AcceptedConstructionReservation.load(acceptedWall, paidRecipe.getCompound("Reservation"));
                for (long cavity : NativeHollowWallOracle.manualCavities(fixture.wallAnchor())) {
                    BlockPos cell = BlockPos.of(cavity);
                    require(!acceptedWall.cells.containsKey(cell) && acceptedReservation.clearance.containsKey(cell)
                                    && acceptedReservation.clearance.get(cell).isAir()
                                    && NativeConstructionGuard.reserves(level, List.of(cell)),
                            "Paid manual job omitted a cavity from explicit AIR clearance/reservation: " + cell);
                }
                require(acceptedWall.cells.size() == 83 && acceptedWall.cells.values().stream().noneMatch(BlockState::isAir),
                        "Native paid recipe differs from the hollow solid-target-only contract");
                RESULT.put("manualHollowGeometry", Map.of("targets", 83, "cobblestone", 58, "oakPlanks", 25,
                        "cavityCells", 27, "cavityReservedAtAcceptance", true, "airPlacementTargets", 0));
                check("Actual manual plan confirmation consumes one plan and charges exactly 8 Treasury");
                advance(now, 9, 20); return Action.USE_BLOCK;
            }
            case 9 -> {
                require(balance(owner) == bankAfterManual && protectedAreas(level) == 1,
                        "Repeated actual use duplicated payment or commissioned job");
                check("Repeated post-confirmation use cannot duplicate payment or job");
                lastPlaced = placed(level); lastChange = now; advance(now, 10, 0);
            }
            case 10 -> {
                long count = placed(level);
                if (count != lastPlaced) { lastPlaced = count; lastChange = now; }
                if (count <= 0 || now - lastChange < 100 || builder(level).neededItems.isEmpty()) return Action.NONE;
                require(count < fixture.expectedPlan().blocks().size(), "Finite initial stock unexpectedly completed wall");
                stockSnapshot(level, "first-material-stall");
                require(hasClearedPlant(level) && !level.getBlockState(plantCell).is(Blocks.DANDELION),
                        "Actual native clearing did not remove and record the accepted single-cell plant");
                check("Actual native break path clears the accepted dandelion and writes its cleared-cell receipt");
                conservation(level);
                check("Actual native builder places from finite chest stock, then stalls with material request");
                RESULT.put("initialStockPlacedBlocks", count);
                replenish(level, 32, 32); lastPlaced = count; advance(now, 11, 0);
            }
            case 11 -> {
                if (placed(level) <= lastPlaced) return Action.NONE;
                check("Actual native resupply resumes placement without another Treasury debit");
                claim = ClaimEvents.recruitsClaimManager.getClaim(fixture.claimId());
                require(claim != null, "Real claim vanished before permission case");
                claimChunks = List.copyOf(claim.getClaimedChunks());
                for (ChunkPos chunk : claimChunks) claim.removeChunk(chunk);
                ClaimEvents.recruitsClaimManager.addOrUpdateClaim(level, claim);
                advance(now, 12, 20);
            }
            case 12 -> {
                require(NativeConstructionGuard.status(area(level)).contains("Core")
                                || NativeConstructionGuard.status(area(level)).contains("claim"),
                        "Native guard did not expose claim/core pause: " + diagnostics(level));
                pausedCells = snapshot(level); advance(now, 13, 80);
            }
            case 13 -> {
                require(snapshot(level).equals(pausedCells) && balance(owner) == bankAfterManual,
                        "Native world or Treasury mutated while claim permission was absent");
                check("Actual native claim removal pauses world mutations and preserves payment");
                for (ChunkPos chunk : claimChunks) claim.addChunk(chunk);
                ClaimEvents.recruitsClaimManager.addOrUpdateClaim(level, claim); ClaimEvents.recruitsClaimManager.save(level);
                lastPlaced = placed(level); advance(now, 14, 0);
            }
            case 14 -> {
                if (placed(level) <= lastPlaced) return Action.NONE;
                require(balance(owner) == bankAfterManual, "Claim restore charged again");
                headroomCell = fixture.wallAnchor().atY(fixture.expectedPlan().max().getY());
                require(!fixture.expectedPlan().blocks().containsKey(headroomCell.asLong())
                                && NativeConstructionGuard.reserves(level, List.of(headroomCell)),
                        "Chosen headroom is not an accepted non-structural reservation");
                originalHeadroom = level.getBlockState(headroomCell);
                require(originalHeadroom.isAir(), "Reserved headroom is not initially clear");
                // Raw fixture environmental change, intentionally NOT a simulated player event.
                level.setBlock(headroomCell, Blocks.STONE.defaultBlockState(), 3);
                pausedCells = snapshot(level); // Same server action: no worker tick can run between insertion and this snapshot.
                RESULT.put("headroomObstructionCell", headroomCell.toShortString());
                advance(now, 201, 20);
            }
            case 201 -> {
                require(NativeConstructionGuard.status(area(level)).contains("headroom"),
                        "Non-structural headroom change did not pause the native job: " + diagnostics(level));
                require(snapshot(level).equals(pausedCells) && level.getBlockState(headroomCell).is(Blocks.STONE),
                        "Native job mutated structural cells or obstruction after the headroom change");
                advance(now, 202, 80);
            }
            case 202 -> {
                require(snapshot(level).equals(pausedCells) && level.getBlockState(headroomCell).is(Blocks.STONE),
                        "Native job mutated structural cells or obstruction during the headroom pause");
                require(balance(owner) == bankAfterManual, "Headroom pause charged again");
                check("Raw fixture solid in reserved non-structural headroom pauses actual mutation and is preserved");
                // Restore top headroom first, then test an independent protected body cavity by itself.
                level.setBlock(headroomCell, originalHeadroom, 3);
                cavityCell = fixture.wallAnchor().above();
                require(NativeHollowWallOracle.manualCavities(fixture.wallAnchor()).contains(cavityCell.asLong())
                                && !fixture.expectedPlan().blocks().containsKey(cavityCell.asLong())
                                && NativeConstructionGuard.reserves(level, List.of(cavityCell)),
                        "Chosen body cavity is not independently reserved non-target clearance");
                originalCavity = level.getBlockState(cavityCell);
                require(originalCavity.isAir(), "Protected body cavity was filled during native work");
                level.setBlock(cavityCell, Blocks.STONE.defaultBlockState(), 3);
                pausedCells = snapshot(level);
                RESULT.put("cavityObstructionCell", cavityCell.toShortString());
                advance(now, 204, 20);
            }
            case 204 -> {
                require(NativeConstructionGuard.status(area(level)).contains("headroom")
                                && snapshot(level).equals(pausedCells) && level.getBlockState(cavityCell).is(Blocks.STONE)
                                && level.getBlockState(headroomCell).equals(originalHeadroom),
                        "Body-cavity-only obstruction did not independently pause native mutation");
                advance(now, 205, 80);
            }
            case 205 -> {
                require(snapshot(level).equals(pausedCells) && level.getBlockState(cavityCell).is(Blocks.STONE)
                                && balance(owner) == bankAfterManual,
                        "Native job changed protected cavity obstruction, structure or payment while paused");
                RESULT.put("cavityObstructionPaused", true);
                check("Raw body-cavity obstruction independently pauses native work without an AIR job or excavation");
                owner.setGameMode(GameType.SPECTATOR); advance(now, 15, 20);
            }
            case 15 -> {
                require(NativeConstructionGuard.status(area(level)).contains("permission"), "Owner permission pause missing");
                pausedCells = snapshot(level); ledgerGeneration = ConstructionEditLedger.get(level).generation();
                advance(now, 16, 80);
            }
            case 16 -> {
                require(snapshot(level).equals(pausedCells), "Native mutation continued during owner permission pause");
                stockSnapshot(level, "immediately-before-world-save");
                rebindsBeforeReload = ProtectedBuilderHandMirror.rebindCount(builder(level).getPersistentData());
                inventoryBeforeReload = inventoryValues(builder(level));
                mainHandBeforeReload = stackDescription(builder(level).getMainHandItem());
                chestBeforeReload = containerValues((Container) level.getBlockEntity(fixture.chestPos()));
                owner.server.saveEverything(false, true, true);
                check("Owner permission loss pauses actual AI without changing world cells");
                advance(now, 17, 0); return Action.RELOAD;
            }
            case 17 -> {
                stockSnapshot(level, "immediately-after-world-reload-before-resupply");
                require(inventoryValues(builder(level)).equals(inventoryBeforeReload)
                                && stackDescription(builder(level).getMainHandItem()).equals(mainHandBeforeReload),
                        "Protected reload changed native inventory or main-hand values");
                assertSingleHandRebind(level);
                check("Protected reload rebinds the equal main-hand mirror exactly once before AI, preserving every inventory value");
                require(containerValues((Container) level.getBlockEntity(fixture.chestPos())).equals(chestBeforeReload),
                        "Actual native storage chest changed item/tag/count values across world reload");
                conservation(level);
                check("Actual native chest withdrawals persist exact stock through world reload");
                require(snapshot(level).equals(pausedCells) && balance(owner) == bankAfterManual,
                        "Mid-job world restart changed protected cells or Treasury");
                require(NativeConstructionGuard.commissionPaid(area(level))
                                && ConstructionEditLedger.get(level).sameGeneration(ledgerGeneration)
                                && NativeConstructionGuard.hasReservation(level, jobId), "Restart lost paid job or durable reservation");
                require(hasAcceptedPlant(level) && hasClearedPlant(level), "Native plant before/cleared receipts did not survive real reload");
                check("Single-cell native plant clearance and original acceptance receipts survive real world reload");
                check("Mid-job real world restart preserves exact cells, paid state and durable ledger identity");
                require(level.getBlockState(cavityCell).is(Blocks.STONE)
                                && level.getBlockState(headroomCell).equals(originalHeadroom) && !area(level).nativeQueuesReady(),
                        "Reload adopted obstructed clearance or enabled native queues");
                owner.setGameMode(GameType.SURVIVAL); advance(now, 203, 60);
            }
            case 203 -> {
                require(snapshot(level).equals(pausedCells) && level.getBlockState(cavityCell).is(Blocks.STONE)
                                && level.getBlockState(headroomCell).equals(originalHeadroom) && !area(level).nativeQueuesReady(),
                        "Restoring owner permission bypassed the saved body-cavity obstruction");
                RESULT.put("cavityObstructionRestartVerified", true);
                check("Body-cavity obstruction survives real reload and keeps native queues unready even after owner permission returns");
                level.setBlock(cavityCell, originalCavity, 3);
                replenish(level, 128, 128); advance(now, 18, 0);
            }
            case 18 -> {
                if (placed(level) < fixture.expectedPlan().blocks().size()) return Action.NONE;
                stockSnapshot(level, "completed-native-wall-before-conservation-assertion");
                assertSingleHandRebind(level);
                conservation(level);
                require(balance(owner) == bankAfterManual, "Completion charged again");
                require(level.getBlockState(headroomCell).equals(originalHeadroom), "Restored clearance changed during resumed construction");
                require(level.getBlockState(plantCell).is(Blocks.COBBLESTONE)
                                && (level.getEntity(jobId) == null || hasClearedPlant(level)),
                        "Native worker did not replace the actually cleared plant with its accepted cobblestone");
                require(level.getBlockState(plantCell.below()).is(Blocks.GRASS_BLOCK)
                                || level.getBlockState(plantCell.below()).is(Blocks.DIRT),
                        "Native worker excavated the plant support outside its mutation plan");
                check("Native clearing replaces only the accepted dandelion with planned cobblestone, preserving its support and exact material totals");
                check("Restoring original raw fixture clearance resumes protected native work without another charge");
                NativeHollowWallOracle.assertManualCavitiesAir(level, fixture.wallAnchor());
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                    require(level.getBlockState(fixture.wallAnchor().offset(x, 3, z)).is(Blocks.OAK_PLANKS),
                            "Native completion did not place the walkable deck above the hollow body");
                    require(level.getBlockState(fixture.wallAnchor().offset(x, -1, z)).is(Blocks.STONE),
                            "Native construction excavated full-width footing below the hollow body");
                }
                RESULT.put("nativeDeckOverCavityVerified", true);
                RESULT.put("finalCavityAirCells", 27);
                RESULT.put("unchangedCavityFootingColumns", 9);
                check("Native AI places the actual nine-cell central deck above 27 untouched AIR cavity cells and intact footing");
                check("Native AI completes the exact manual template after resupply/restart with material conservation");
                RESULT.put("completedManualBlocks", placed(level));
                RESULT.put("manualTreasuryDebit", DefenseBlueprint.Kind.WALL.price); RESULT.put("manualPaidTreasury", bankAfterManual); RESULT.put("perimeterTreasuryDebit", 64);
                advance(now, 19, 30);
            }
            case 19 -> {
                require(level.getEntity(jobId) == null || ((ProtectedBuildArea) level.getEntity(jobId)).isDone(),
                        "Native completion did not retire or complete its marker");
                BuilderEntity worker = builder(level);
                if (!completedConstructionDetached(level) || ProtectedBuilderHandMirror.activeUse(worker)
                        || !worker.neededItems.isEmpty() || !ProtectedInventoryCleanup.read(worker.getPersistentData()).isEmpty()
                        || ProtectedStorageAccess.runningProblem(worker) != null) {
                    completedClosedSince = -1; return Action.NONE;
                }
                if (completedClosedSince < 0) completedClosedSince = now;
                if (now - completedClosedSince < 40) return Action.NONE;
                require(!worker.isNoAi() && !worker.getMainHandItem().isEmpty()
                                && worker.getMainHandItem() == worker.getInventory().getItem(5),
                        "Completed reload checkpoint lacks an active native builder with a nonempty shared hand mirror");
                conservation(level); stockSnapshot(level, "completed-detached-before-world-save");
                pausedCells = snapshot(level); ledgerGeneration = ConstructionEditLedger.get(level).generation();
                completedInventoryBeforeReload = inventoryAndHands(worker); completedInventoryLoaded = null;
                completedRebindsBeforeReload = ProtectedBuilderHandMirror.rebindCount(worker.getPersistentData());
                chestBeforeReload = containerValues((Container) level.getBlockEntity(fixture.chestPos()));
                owner.server.saveEverything(false, true, true);
                advance(now, 22, 0); return Action.RELOAD;
            }
            case 22 -> {
                stockSnapshot(level, "completed-detached-after-world-reload");
                require(completedInventoryLoaded != null && completedInventoryLoaded.equals(completedInventoryBeforeReload)
                                && completedLoadedSplitMirror,
                        "Completed native reload did not preserve exact inventory/equipment values and expose the pinned split mirror");
                assertHandRebind(level, completedRebindsBeforeReload + 1);
                require(inventoryAndHands(builder(level)).equals(completedInventoryBeforeReload),
                        "Completed production hand rebind changed serialized inventory or equipment values");
                require(completedConstructionDetached(level) && !builder(level).isNoAi()
                                && ProtectedInventoryCleanup.read(builder(level).getPersistentData()).isEmpty(),
                        "Completed reload recreated active construction or left native cleanup pending");
                require(snapshot(level).equals(pausedCells) && placed(level) == fixture.expectedPlan().blocks().size()
                                && ConstructionEditLedger.get(level).sameGeneration(ledgerGeneration)
                                && containerValues((Container) level.getBlockEntity(fixture.chestPos())).equals(chestBeforeReload)
                                && balance(owner) == bankAfterManual,
                        "Completed reload changed geometry, ledger identity, native chest or Treasury");
                NativeHollowWallOracle.assertManualCavitiesAir(level, fixture.wallAnchor());
                conservation(level);
                RESULT.put("completedManualRestart", Map.of("verified", true, "preAiExactInventoryValues", true,
                        "preAiEqualSplitMirror", completedLoadedSplitMirror, "singleValuePreservingRebind", true,
                        "constructionDetached", true, "reservationRetired", true, "placedBlocks", placed(level),
                        "cavityAirCells", 27, "treasury", balance(owner)));
                check("Actual completed manual job close/reopen preserves exact stock,83 blocks and27 cavity AIR; native hand rebinds once while all construction links/reservations stay retired");
                owner.teleportTo(fixture.wallAnchor().getX() + 7.5, 65, fixture.wallAnchor().getZ() - 8.5);
                advance(now, 20, 40);
            }
            case 20 -> {
                require(placed(level) == fixture.expectedPlan().blocks().size(), "Finished manual wall changed before capture");
                conservation(level);
                assertHandRebind(level, completedRebindsBeforeReload + 1);
                require(inventoryAndHands(builder(level)).equals(completedInventoryBeforeReload),
                        "Completed inventory/equipment changed during the ordinary post-reload observation window");
                require(completedConstructionDetached(level), "Completed job resumed construction after reload");
                advance(now, 21, 10); return Action.CAPTURE_COMPLETED;
            }
            case 21 -> {
                NativeEconomyContracts.beforeReload(level, owner, fixture.corePos());
                advance(now, 23, 0); return Action.RELOAD;
            }
            case 23 -> {
                RESULT.put("economy", NativeEconomyContracts.afterReload(level, owner, fixture.corePos()));
                check("Real Survival economy rejects unavailable buffs, retains ownership through disk reload and grants eligible checkpoint loot exactly once with actual inventory overflow");
                RESULT.put("status", "passed"); return Action.DONE;
            }
            default -> throw new AssertionError("Unexpected gameplay stage " + stage);
        }
        return Action.NONE;
    }

    private static void aim(Minecraft mc, Vec3 target) {
        Vec3 delta = target.subtract(mc.player.getEyePosition());
        float yaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90);
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)));
        mc.player.setYRot(yaw); mc.player.setXRot(pitch); mc.player.yRotO = yaw; mc.player.xRotO = pitch;
    }
    private static void assertLivePlant(ServerLevel level) {
        BlockState plant = level.getBlockState(plantCell);
        require(plant.is(Blocks.DANDELION) && plant.canSurvive(level, plantCell)
                        && level.getBlockState(plantCell.below()).is(Blocks.GRASS_BLOCK),
                "Seeded single-cell plant did not persist with valid grass-block support until acceptance");
    }
    private static boolean hasAcceptedPlant(ServerLevel level) {
        CompoundTag receipt = area(level).getPersistentData().getCompound("SiegeProtectedConstructionV1");
        for (var entry : receipt.getList("Before", Tag.TAG_COMPOUND)) {
            CompoundTag cell = (CompoundTag) entry;
            if (cell.getLong("Pos") == plantCell.asLong())
                return cell.getCompound("State").equals(NbtUtils.writeBlockState(Blocks.DANDELION.defaultBlockState()));
        }
        return false;
    }
    private static boolean hasClearedPlant(ServerLevel level) {
        return Arrays.stream(area(level).getPersistentData().getCompound("SiegeProtectedConstructionV1").getLongArray("Cleared"))
                .anyMatch(pos -> pos == plantCell.asLong());
    }
    private static boolean perimeterUnchanged(ServerLevel level) {
        return perimeterBeforeEdit.entrySet().stream().allMatch(entry ->
                level.getBlockState(BlockPos.of(entry.getKey())).equals(entry.getValue()));
    }
    private static boolean captureLiveCoreHud(Minecraft mc) {
        require(System.nanoTime() < coreHudDeadline, "Live core HUD packets or widgets did not become ready within 30 seconds");
        if (!(mc.screen instanceof CoreHireScreen screen) || !(mc.player.containerMenu instanceof CoreHireMenu menu)) return false;
        require(screen.getMenu() == menu && menu.containerId == coreHudMenuId, "Client/server live core menu identities differ");
        if (menu.bank() != 1936 || !menu.factionName().equals(coreHudFaction)) return false;
        switch (coreHudClientStage) {
            case 0 -> {
                for (int attempts = 0; !NativeBuildingQa.hasVisibleButton(mc, "Building") && attempts < 7; attempts++)
                    NativeBuildingQa.clickVisibleButton(mc, ">");
                NativeBuildingQa.clickVisibleButton(mc, "Building");
                NativeBuildingQa.clickVisibleButton(mc, "Auto perimeter", "Perimeter");
                coreHudClientStage = 1;
            }
            case 1 -> {
                require(NativeBuildingQa.hasVisibleButton(mc, "Review in world"), "Real live Building perimeter controls missing");
                NativeBuildingQa.captureGameplay("16-live-core-building-auto.png");
                coreHudClientStage = 2;
            }
            case 2 -> {
                NativeBuildingQa.clickVisibleButton(mc, "Construction"); // Actual84 subscription packet.
                coreHudClientStage = 3;
            }
            case 3 -> {
                if (!menu.constructionLoaded()) return false;
                require(menu.construction().equals(coreHudExpectedJobs), "Client construction rows differ from the actual server report");
                RESULT.put("liveCoreHud", Map.of("menuId", coreHudMenuId, "faction", coreHudFaction,
                        "treasury", menu.bank(), "serverReportRows", List.copyOf(menu.construction()),
                        "perimeterPriceSource", "Shared production constant; actual 64e debit asserted separately",
                        "perimeterPrice", TerritoryFortification.PRICE, "newCommissionActions", 0));
                NativeBuildingQa.captureGameplay("17-live-core-construction.png");
                coreHudClientStage = 4;
            }
            case 4 -> { mc.player.closeContainer(); return true; }
            default -> throw new AssertionError("Unexpected live core HUD stage " + coreHudClientStage);
        }
        return false;
    }
    private static void select(ServerPlayer owner, Item item) {
        int slot = -1;
        for (int i = 0; i < owner.getInventory().getContainerSize(); i++)
            if (owner.getInventory().getItem(i).is(item)) { slot = i; break; }
        require(slot >= 0, "Production plan not found in actual inventory");
        if (slot >= 9) {
            ItemStack held = owner.getInventory().getItem(8);
            owner.getInventory().setItem(8, owner.getInventory().getItem(slot)); owner.getInventory().setItem(slot, held); slot = 8;
        }
        owner.getInventory().selected = slot; owner.inventoryMenu.broadcastChanges();
        owner.connection.send(new ClientboundSetCarriedItemPacket(slot));
    }
    private static CompoundTag core(ServerPlayer owner) {
        var tag = RaidSavedData.get(owner.server).siegeCores.get(SiegeCore.key(owner));
        require(tag != null, "No real core Treasury"); return tag;
    }
    private static long balance(ServerPlayer owner) { return FactionBank.balance(core(owner)); }
    private static BuilderEntity builder(ServerLevel level) {
        var entity = level.getEntity(fixture.builderId()); require(entity instanceof BuilderEntity, "Native gameplay builder unavailable");
        return (BuilderEntity) entity;
    }
    private static UUID linkedJob(ServerLevel level) {
        var tag = builder(level).getPersistentData();
        require(tag.hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID), "Production builder/job link missing");
        return tag.getUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID);
    }
    private static ProtectedBuildArea area(ServerLevel level) {
        var entity = level.getEntity(jobId); require(entity instanceof ProtectedBuildArea, "Commissioned native marker unavailable");
        return (ProtectedBuildArea) entity;
    }
    private static int protectedAreas(ServerLevel level) {
        return level.getEntitiesOfClass(ProtectedBuildArea.class, new AABB(120, 60, -8, 168, 80, 40), e -> e.isAlive()).size();
    }
    private static Set<BlockPos> plannedPositions() {
        Set<BlockPos> positions = new HashSet<>(); fixture.expectedPlan().blocks().keySet().forEach(p -> positions.add(BlockPos.of(p))); return positions;
    }
    private static Map<Long, BlockState> snapshot(ServerLevel level) {
        Map<Long, BlockState> snapshot = new HashMap<>();
        // Observe the complete accepted manual footprint, including its non-structural clearance.
        for (BlockPos base : fixture.expectedPlan().footprint())
            for (int y = base.getY(); y <= fixture.expectedPlan().max().getY(); y++) {
                BlockPos pos = base.atY(y); snapshot.put(pos.asLong(), level.getBlockState(pos));
            }
        return Map.copyOf(snapshot);
    }
    private static long placed(ServerLevel level) {
        return fixture.expectedPlan().blocks().entrySet().stream().filter(entry -> {
            var block = ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation(entry.getValue()));
            return block != null && level.getBlockState(BlockPos.of(entry.getKey())).is(block);
        }).count();
    }
    private static void replenish(ServerLevel level, int cobble, int oak) {
        stockSnapshot(level, "before-resupply-" + cobble + "-" + oak);
        NativeGameplayFixture.replenishContainer(level, cobble, oak);
        suppliedCobble += cobble; suppliedOak += oak;
        stockSnapshot(level, "after-resupply-" + cobble + "-" + oak);
    }
    private static void stockSnapshot(ServerLevel level, String when) {
        BuilderEntity builder = builder(level);
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("when", when); snap.put("gameTime", level.getGameTime()); snap.put("stage", stage);
        snap.put("builderPosition", builder.position().toString()); snap.put("builderBounds", builder.getBoundingBox().toString());
        selfClearanceTelemetry(snap, builder);
        snap.put("placedBlocks", placed(level)); snap.put("suppliedCobblestone", suppliedCobble); snap.put("suppliedOakPlanks", suppliedOak);
        snap.put("mainHand", stackDescription(builder.getMainHandItem()));
        snap.put("offHand", stackDescription(builder.getOffhandItem()));
        snap.put("mainHandSameObjectAsInventorySlot5", builder.getMainHandItem() == builder.getInventory().getItem(5));
        snap.put("offHandSameObjectAsInventorySlot4", builder.getOffhandItem() == builder.getInventory().getItem(4));
        snap.put("protectedMainHandRebindCount", ProtectedBuilderHandMirror.rebindCount(builder.getPersistentData()));
        snap.put("protectedMainHandRebindPending", ProtectedBuilderHandMirror.pending(builder.getPersistentData()));
        snap.put("protectedMainHandReviewRequired", ProtectedBuilderHandMirror.reviewNeeded(builder.getPersistentData()));
        snap.put("protectedStorageDirtyNotifications", ProtectedStorageAccess.dirtyNotifications(builder));
        snap.put("nativeInventoryClass", builder.getInventory().getClass().getName());
        snap.put("nativeInventorySupportedByCapacityGuard", ProtectedTransferCapacity.supportedInventory(builder.getInventory()));
        var requests = new ArrayList<Map<String, Object>>();
        for (NeededItem request : builder.neededItems) {
            if (request == null) { requests.add(Map.of("nullRequest", true)); continue; }
            var metadata = new LinkedHashMap<String, Object>();
            metadata.put("requestClass", request.getClass().getName());
            metadata.put("exactNativeRequestClass", request.getClass() == NeededItem.class);
            metadata.put("count", request.count); metadata.put("required", request.required);
            metadata.put("sourceKeyIsNull", request.sourceKey == null);
            metadata.put("trustedMatcher", ProtectedTransferCapacity.trustedMatcher(request.matcher));
            if (request.matcher != null) {
                Class<?> matcher = request.matcher.getClass();
                metadata.put("matcherClass", matcher.getName());
                metadata.put("matcherHidden", matcher.isHidden()); metadata.put("matcherSynthetic", matcher.isSynthetic());
                metadata.put("matcherFinal", java.lang.reflect.Modifier.isFinal(matcher.getModifiers()));
                metadata.put("matcherNestHost", matcher.getNestHost().getName());
                metadata.put("matcherNestHostIsBuilderWorkGoal", matcher.getNestHost() == BuilderWorkGoal.class);
                metadata.put("matcherLoaderIsBuilderWorkGoalLoader", matcher.getClassLoader() == BuilderWorkGoal.class.getClassLoader());
            }
            requests.add(Map.copyOf(metadata));
        }
        snap.put("nativeRequestMetadata", requests); // No predicate invocation or class-name-based trust inference.
        var slots = new ArrayList<Map<String, Object>>();
        var identities = new IdentityHashMap<ItemStack, Integer>();
        for (int i = 0; i < builder.getInventory().getContainerSize(); i++) {
            ItemStack stack = builder.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            var entry = new LinkedHashMap<>(stackDescription(stack)); entry.put("slot", i);
            entry.put("sameObjectAsSlot", identities.getOrDefault(stack, i)); identities.putIfAbsent(stack, i);
            entry.put("sameObjectAsMainHand", stack == builder.getMainHandItem()); slots.add(entry);
        }
        snap.put("nativeInventorySlots", slots);
        Container chest = (Container) level.getBlockEntity(fixture.chestPos());
        snap.put("chestInventorySlots", containerValues(chest));
        snap.put("chestChunkUnsaved", level.getChunkAt(fixture.chestPos()).isUnsaved());
        snap.put("serializedChest", level.getBlockEntity(fixture.chestPos()).saveWithFullMetadata().toString());
        snap.put("chestCobblestone", count(chest, Items.COBBLESTONE)); snap.put("chestOakPlanks", count(chest, Items.OAK_PLANKS));
        snap.put("nativeInventoryCobblestone", count(builder.getInventory(), Items.COBBLESTONE));
        snap.put("nativeInventoryOakPlanks", count(builder.getInventory(), Items.OAK_PLANKS));
        snap.put("placedCobblestone", fixture.expectedPlan().blocks().keySet().stream()
                .filter(p -> level.getBlockState(BlockPos.of(p)).is(Blocks.COBBLESTONE)).count());
        snap.put("placedOakPlanks", fixture.expectedPlan().blocks().keySet().stream()
                .filter(p -> level.getBlockState(BlockPos.of(p)).is(Blocks.OAK_PLANKS)).count());
        CompoundTag nbt = builder.saveWithoutId(new CompoundTag());
        var savedSlots = new ArrayList<Map<String, Object>>();
        for (var value : nbt.getList("Items", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            var tag = (CompoundTag) value; var entry = new LinkedHashMap<>(stackDescription(ItemStack.of(tag)));
            entry.put("slot", tag.getByte("Slot") & 255); savedSlots.add(entry);
        }
        snap.put("serializedItems", savedSlots);
        var hands = new ArrayList<Map<String, Object>>();
        for (var value : nbt.getList("HandItems", net.minecraft.nbt.Tag.TAG_COMPOUND))
            hands.add(stackDescription(ItemStack.of((CompoundTag) value)));
        snap.put("serializedHandItems", hands);
        STOCK.add(Map.copyOf(snap));
    }
    private static CompoundTag inventoryAndHands(BuilderEntity builder) {
        CompoundTag saved = builder.saveWithoutId(new CompoundTag()), values = new CompoundTag();
        for (String key : List.of("Items", "HandItems"))
            values.put(key, saved.getList(key, Tag.TAG_COMPOUND).copy());
        return values;
    }
    private static boolean completedConstructionDetached(ServerLevel level) {
        BuilderEntity worker = builder(level);
        return worker.currentBuildArea == null && !WorkersBridge.hasActiveBuildArea(worker)
                && !worker.getPersistentData().contains(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID)
                && !NativeConstructionGuard.hasProtectedReceipt(worker) && !PerimeterProjectLink.reserved(worker)
                && !NativeConstructionGuard.hasReservation(level, jobId);
    }
    private static void assertSingleHandRebind(ServerLevel level) {
        assertHandRebind(level, rebindsBeforeReload + 1);
    }
    private static void assertHandRebind(ServerLevel level, int expectedCount) {
        BuilderEntity builder = builder(level);
        CompoundTag data = builder.getPersistentData();
        require(builder.getMainHandItem() == builder.getInventory().getItem(5)
                        && ProtectedBuilderHandMirror.rebindCount(data) == expectedCount
                        && !ProtectedBuilderHandMirror.pending(data) && !ProtectedBuilderHandMirror.reviewNeeded(data),
                "Protected main-hand mirror was not rebound exactly once before native AI");
    }
    private static List<Map<String, Object>> inventoryValues(BuilderEntity builder) {
        return containerValues(builder.getInventory());
    }
    private static List<Map<String, Object>> containerValues(Container inventory) {
        var values = new ArrayList<Map<String, Object>>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++)
            values.add(stackDescription(inventory.getItem(slot)));
        return List.copyOf(values);
    }
    private static Map<String, Object> stackDescription(ItemStack stack) {
        return Map.of("item", String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem())), "count", stack.getCount(),
                "tag", stack.getTag() == null ? "" : stack.getTag().toString());
    }
    private static int count(Container inventory, Item item) {
        int total = 0; for (int i = 0; i < inventory.getContainerSize(); i++) if (inventory.getItem(i).is(item)) total += inventory.getItem(i).getCount();
        return total;
    }
    private static void conservation(ServerLevel level) {
        var be = level.getBlockEntity(fixture.chestPos()); require(be instanceof Container, "Real stock chest is missing");
        Container chest = (Container) be;
        for (Item item : List.of(Items.COBBLESTONE, Items.OAK_PLANKS)) {
            int supplied = item == Items.COBBLESTONE ? suppliedCobble : suppliedOak;
            long built = fixture.expectedPlan().blocks().keySet().stream()
                    .filter(p -> level.getBlockState(BlockPos.of(p)).getBlock().asItem() == item).count();
            long accounted = built + count(chest, item) + count(builder(level).getInventory(), item);
            require(accounted == supplied, "Native material conservation failed for " + item + ": supplied=" + supplied + ", accounted=" + accounted);
        }
    }
    private static String diagnostics(ServerLevel level) {
        if (fixture == null) return "fixture not initialized";
        var entity = level.getEntity(fixture.builderId());
        if (!(entity instanceof BuilderEntity builder)) return "builder absent";
        String pause = jobId != null && level.getEntity(jobId) instanceof ProtectedBuildArea area ? NativeConstructionGuard.status(area) : "no marker";
        String requested = String.join(", ", com.devfarinsky.siegeoverhaul.compat.WorkersConstructionView.requests(builder));
        return "placed=" + placed(level) + ", follow=" + builder.getFollowState() + ", requests=" + requested
                + ", builderPosition=" + builder.position()
                + ", sleeping=" + builder.needsToSleep() + ", noAi=" + builder.isNoAi()
                + ", navigationDone=" + builder.getNavigation().isDone()
                + ", nativeRemaining=" + (builder.currentBuildArea == null ? -1 : builder.currentBuildArea.stackToPlace.size())
                + ", pause=" + pause;
    }
    private static Map<String, Object> spatialDiagnostics(ServerLevel level) {
        var result = new LinkedHashMap<String, Object>();
        result.put("gameTime", level.getGameTime()); result.put("stage", stage);
        if (fixture == null) return result;
        BuilderEntity builder = builder(level);
        result.put("builderPosition", builder.position().toString()); result.put("builderBounds", builder.getBoundingBox().toString());
        selfClearanceTelemetry(result, builder);
        result.put("navigationDone", builder.getNavigation().isDone());
        var nativeGoal = NATIVE_BUILD_GOALS.get(builder.getUUID());
        if (nativeGoal != null) {
            result.put("nativeBuildState", String.valueOf(nativeGoal.state));
            result.put("nativeTarget", String.valueOf(nativeGoal.blockPos));
            result.put("nativeMutationCandidates", NativeConstructionGuard.mutationCells(nativeGoal).stream().map(BlockPos::toShortString).toList());
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(playerId);
        if (owner != null) { result.put("ownerPosition", owner.position().toString()); result.put("ownerBounds", owner.getBoundingBox().toString()); }
        Set<String> auxiliary = new HashSet<>();
        if (owner != null) for (Tag id : owner.getPersistentData().getList("NativeGameplayFixtureAuxiliaries", Tag.TAG_STRING)) auxiliary.add(id.getAsString());
        var occupants = new ArrayList<Map<String, Object>>();
        AABB bounds = new AABB(fixture.expectedPlan().min(), fixture.expectedPlan().max().offset(1, 1, 1));
        for (var entity : level.getEntities((net.minecraft.world.entity.Entity) null, bounds, net.minecraft.world.entity.Entity::isAlive)) {
            var details = new LinkedHashMap<String, Object>();
            details.put("uuid", entity.getUUID().toString()); details.put("type", String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType())));
            details.put("position", entity.position().toString()); details.put("bounds", entity.getBoundingBox().toString());
            details.put("isTestBuilder", entity == builder); details.put("isOwner", entity == owner);
            details.put("isFixtureAuxiliary", auxiliary.contains(entity.getUUID().toString()));
            details.put("blocksPlacement", NativeConstructionGuard.blocksPlacement(entity));
            if (entity instanceof net.minecraft.world.entity.Mob mob) details.put("noAi", mob.isNoAi());
            details.put("intersectedPendingCells", fixture.expectedPlan().blocks().entrySet().stream()
                    .filter(entry -> !entry.getValue().equals(String.valueOf(ForgeRegistries.BLOCKS.getKey(level.getBlockState(BlockPos.of(entry.getKey())).getBlock()))))
                    .filter(entry -> new AABB(BlockPos.of(entry.getKey())).intersects(entity.getBoundingBox()))
                    .limit(32).map(entry -> BlockPos.of(entry.getKey()).toShortString()).toList());
            occupants.add(details); if (occupants.size() >= 32) break;
        }
        result.put("footprintEntities", occupants);
        return result;
    }
    private static void selfClearanceTelemetry(Map<String, Object> result, BuilderEntity builder) {
        var data = builder.getPersistentData();
        result.put("selfClearanceRequests", data.getInt("SiegeSelfClearanceRequests"));
        if (data.contains("SiegeSelfClearanceTarget", Tag.TAG_LONG))
            result.put("selfClearanceTarget", BlockPos.of(data.getLong("SiegeSelfClearanceTarget")).toShortString());
        result.put("selfClearanceBounds", data.getString("SiegeSelfClearanceBounds"));
    }
    private static void advance(long now, int next, int delay) { stage = next; stageSince = now; resumeAt = now + delay; }
    private static void check(String text) { CHECKS.add(text); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
