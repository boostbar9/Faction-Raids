package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.camp.CampLoading;
import com.devfarinsky.siegeoverhaul.camp.CampGuards;
import com.devfarinsky.siegeoverhaul.compat.CampClaims;
import com.devfarinsky.siegeoverhaul.compat.RaiderFactions;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.core.CoreBlocks;
import com.devfarinsky.siegeoverhaul.core.EnemyCore;
import com.devfarinsky.siegeoverhaul.core.SiegeCore;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import com.talhanation.recruits.ClaimEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Actual command, ordinary raid ticks and native camp establishment in two isolated fresh worlds. */
@Mod.EventBusSubscriber(modid = SiegeOverhaul.MOD_ID, value = Dist.CLIENT)
public final class NativeCampQa {
    private static final boolean ENABLED = Boolean.getBoolean("siegeoverhaul.nativeQa")
            && "camp-spawn".equals(System.getProperty("siegeoverhaul.nativeQa.mode"));
    private static final long SECOND = 1_000_000_000L;
    private static final long CAPTURE_TIMEOUT = 30 * SECOND;
    private static final List<String> WORLDS = List.of("siege-native-camp-flat", "siege-native-camp-shallow-water");
    private static final Map<String, Object> REPORT = new LinkedHashMap<>();
    private static final List<Map<String, Object>> SCENARIOS = new ArrayList<>(), SAMPLES = new ArrayList<>();
    private static final List<String> SHOTS = new ArrayList<>();
    private static NativeCampFixture.Fixture fixture;
    private static CompletableFuture<Action> pending;
    private static Path directory, evidence;
    private static UUID playerId;
    private static String coreKey, capture;
    private static long started, scenarioStarted, lastSample = -1, establishedAt = -1, captureStarted;
    private static int scenario, clientPhase, stage, captureReadyFrames, captureAttempts, captureBlankFrames;
    private static boolean finished, hostilePrepared, sawNaturalSearch, sawFallback;
    private static BlockPos landingScout, captureCenter, captureCore;
    private static Vec3 captureObserver;
    private static Map<String, Object> landingEvidence, captureDiagnostics;
    private enum Action { NONE, START_COMMAND, CAPTURE, NEXT_WORLD, DONE }
    private NativeCampQa() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (started == 0) started = System.nanoTime();
            require(System.nanoTime() - started < 22 * 60 * SECOND, "Camp QA exceeded its overall 22-minute limit");
            if (capture != null) {
                require(System.nanoTime() - captureStarted < CAPTURE_TIMEOUT,
                        "Camp capture did not produce a ready, nonblank framebuffer within 30 seconds: " + captureDiagnostics);
                // The server future can complete before the client handles its teleport packet.
                // Aim only from the acknowledged observer position, and maintain that aim while loading.
                if (mc.player != null && mc.player.position().distanceToSqr(captureObserver) < 0.01)
                    aim(mc, Vec3.atCenterOf(captureCenter).add(0, 3, 0));
                return;
            }
            if (clientPhase == 0) {
                if (!(mc.screen instanceof TitleScreen)) return;
                initialize(mc); clientPhase = 1;
            }
            if (clientPhase == 1) {
                if (mc.getSingleplayerServer() != null || !(mc.screen instanceof TitleScreen)) return;
                String world = WORLDS.get(scenario);
                require(!Files.exists(directory.resolve("saves").resolve(world)), "Refusing an existing camp fixture world");
                GameRules rules = new GameRules();
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
                boolean hostile = scenario == 1;
                scenarioStarted = System.nanoTime(); clientPhase = 2;
                mc.createWorldOpenFlows().createFreshLevel(world,
                        new LevelSettings(world, GameType.SURVIVAL, false, Difficulty.NORMAL, false,
                                rules, WorldDataConfiguration.DEFAULT), new WorldOptions(20261008L + scenario, false, false),
                        access -> NativeCampFixture.dimensions(access, hostile));
                return;
            }
            if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || mc.screen != null) return;
            require(System.nanoTime() - scenarioStarted < (scenario == 1 ? 11 : 6) * 60 * SECOND,
                    "Camp scenario exceeded its real-time scouting/establishment bound");
            if (playerId == null) playerId = mc.player.getUUID();
            if (pending != null) {
                if (!pending.isDone()) return;
                Action action = pending.join(); pending = null;
                if (action == Action.START_COMMAND) {
                    require(mc.getConnection() != null, "Actual client command connection unavailable");
                    mc.getConnection().sendCommand("siegeoverhaul start");
                } else if (action == Action.CAPTURE) {
                    mc.options.hideGui = true;
                    capture = scenario == 0 ? "01-established-flat-camp.png" : "02-established-shallow-water-forest-camp.png";
                    captureStarted = System.nanoTime();
                    captureReadyFrames = captureAttempts = captureBlankFrames = 0;
                    captureDiagnostics = new LinkedHashMap<>();
                    return;
                } else if (action == Action.NEXT_WORLD) {
                    mc.options.hideGui = false;
                    mc.level.disconnect(); mc.clearLevel(); mc.setScreen(new TitleScreen());
                    scenario++; stage = 0; fixture = null; playerId = null; coreKey = null;
                    lastSample = -1; establishedAt = -1; landingScout = null; landingEvidence = null;
                    hostilePrepared = sawNaturalSearch = sawFallback = false; clientPhase = 1; return;
                } else if (action == Action.DONE) { finish(mc, null); return; }
            }
            CompletableFuture<Action> next = new CompletableFuture<>(); pending = next;
            var server = mc.getSingleplayerServer();
            server.execute(() -> {
                try { next.complete(step(server.overworld(), server.getPlayerList().getPlayer(playerId))); }
                catch (Throwable failure) {
                    try { REPORT.put("failureDiagnostics", sample(server.overworld(), server.getPlayerList().getPlayer(playerId))); }
                    catch (Throwable ignored) { }
                    next.completeExceptionally(failure);
                }
            });
        } catch (Throwable failure) { finish(mc, failure); }
    }

    private static Action step(ServerLevel level, ServerPlayer owner) throws Exception {
        require(owner != null, "Real camp QA player unavailable");
        if (stage == 0) {
            fixture = NativeCampFixture.setup(level, owner, WORLDS.get(scenario), scenario == 1);
            coreKey = SiegeCore.key(owner);
            require(CampClaims.unavailableReason(level).isEmpty(), "Native camp claim setup unavailable: " + CampClaims.unavailableReason(level));
            require(RaidConfig.BUILD_WAR_CAMPS.get() && RaidConfig.CAMP_TERRAFORM.get() && RaidConfig.LEVEL_CAMP_TERRAIN.get()
                            && RaidConfig.CLEANUP_WAR_CAMPS.get() && RaidConfig.ENABLE_CAMP_CONSTRUCTION.get(),
                    "Required production camp settings are disabled; QA will not bypass them");
            require(RaidConfig.ENABLE_NARRATIVE.get() && RaidConfig.ALLOWED_RAIDER_FACTIONS.get().equals(List.of("wilds_marauders")),
                    "Isolated camp QA must select the existing Artemis doctrine through its supported configuration");
            stage = 1; return Action.START_COMMAND;
        }
        RaidSavedData.RaidState raid = RaidSavedData.get(owner.server).raids.get(coreKey);
        require(stage == 1 || raid != null, "Production raid disappeared before camp verification");
        if (raid == null) return Action.NONE;
        if (level.getGameTime() - lastSample >= 100 || lastSample < 0) {
            NativeCampFixture.verifyProtected(level, fixture);
            Map<String, Object> state = sample(level, owner); SAMPLES.add(state); lastSample = level.getGameTime();
            FactionLogger.LOG.info("Native camp QA: {}", new GsonBuilder().create().toJson(state));
        }
        if (stage == 1) {
            require(!owner.isCreative() && !owner.hasPermissions(2), "Raid command actor is not real non-op Survival");
            stage = 2;
        }
        if (stage == 2) {
            if (!raid.campTerraformed && raid.campPos == null) sawNaturalSearch = true;
            if (raid.campTerraformed) sawFallback = true;
            if (scenario == 1 && !hostilePrepared) {
                require(raid.campPos == null && !raid.campTerraformed,
                        "Hostile camp advanced before its controlled terrain setup; do not accept an accidental natural camp");
                if (landingScout == null && raid.campSearchPos != null) {
                    BlockPos candidate = raid.campSearchPos;
                    if (raid.navalStagingPos == null || horizontalDistanceSquared(candidate, raid.navalStagingPos) > 48 * 48)
                        landingScout = candidate.immutable();
                }
                if (landingScout != null && CampLoading.ready(level, landingScout)) {
                    var landing = NativeCampFixture.hostileLanding(level, fixture, landingScout);
                    fixture = landing.fixture(); landingEvidence = landing.evidence(); hostilePrepared = true;
                }
            }
            require(!raid.campSearchAbandoned, "Production camp search exhausted its bounded candidates; see scouting/terrain rejection logs");
            if (raid.campPos == null || !raid.campCrewStarted) return Action.NONE;
            require(raid.campTerraformed == (scenario == 1), "Camp established through the wrong natural/fallback path");
            if (scenario == 1) require(hostilePrepared && sawNaturalSearch && sawFallback,
                    "Hostile case did not exercise genuine natural search and fallback");
            if (establishedAt < 0) establishedAt = level.getGameTime();
            BlockPos enemyCore = EnemyCore.position(raid);
            if (enemyCore == null || raid.campWorkers.isEmpty() || raid.campGuards.isEmpty()) {
                require(level.getGameTime() - establishedAt < 1200, "Camp claim exists but its actual enemy core/crew did not establish within 60 seconds");
                return Action.NONE;
            }
            // beginRaid creates workers before publishing the raid in SavedData. Their native
            // faction resolves on the normal 20-tick RaiderFactions.sync pass; observe that
            // lifecycle without invoking it or rewriting the entity/raid ourselves.
            if (level.getGameTime() - establishedAt < 60) return Action.NONE;
            require(raid.campClaimId != null && CampClaims.owns(level, raid), "Camp lacks its real native enemy claim");
            var claim = ClaimEvents.recruitsClaimManager.getClaim(raid.campClaimId);
            require(claim != null && claim.getOwnerFactionStringID().equals(RaiderFactions.id(raid.factionId))
                            && new HashSet<>(claim.getClaimedChunks()).equals(CampClaims.footprint(raid.campPos)),
                    "Native enemy claim identity or 25-chunk footprint differs");
            for (var chunk : claim.getClaimedChunks()) require(ClaimEvents.recruitsClaimManager.getClaim(chunk) != null
                            && raid.campClaimId.equals(ClaimEvents.recruitsClaimManager.getClaim(chunk).getUUID()), "Enemy camp chunk index missing");
            require(level.getBlockState(enemyCore).is(CoreBlocks.CORE.get()), "Actual enemy core block is missing");
            require(raid.campfirePos != null && level.getBlockState(raid.campfirePos).is(Blocks.CAMPFIRE)
                            && raid.barrelPos != null && level.getBlockState(raid.barrelPos).is(Blocks.BARREL)
                            && raid.bannerPos != null && !level.getBlockState(raid.bannerPos).isAir(), "Camp strategic blocks are incomplete");
            // NativeCampConstruction gives the crew and its work areas a private job owner,
            // distinct from the guards' shared hostile leader. Verify that actual persisted link.
            require(com.devfarinsky.siegeoverhaul.camp.NativeCampConstruction.active(raid),
                    "Controlled camp did not establish its actual native construction job");
            UUID nativeOwner = raid.nativeCamp.getUUID(ModConstants.Tags.CAMP_OWNER);
            require(!nativeOwner.equals(owner.getUUID()) && !nativeOwner.equals(RecruitsBridge.RAIDERS_LEADER_UUID),
                    "Native camp job must retain its separate non-player supply owner");
            var nativeBuild = level.getEntity(raid.nativeCamp.getUUID(ModConstants.Tags.CAMP_BUILD_AREA));
            var nativeStorage = level.getEntity(raid.nativeCamp.getUUID(ModConstants.Tags.CAMP_STORAGE_AREA));
            BlockPos nativeSupply = BlockPos.of(raid.nativeCamp.getLong(ModConstants.Tags.CAMP_SUPPLY_POS));
            var supplyEntity = level.getBlockEntity(nativeSupply);
            require(nativeBuild instanceof com.talhanation.workers.entities.workarea.BuildArea
                            && nativeStorage instanceof com.talhanation.workers.entities.workarea.StorageArea storage
                            && storage.getStorageTypes().contains(com.talhanation.workers.entities.workarea.StorageArea.StorageType.BUILDERS)
                            && nativeOwner.equals(WorkersBridge.readOwner(nativeBuild))
                            && nativeOwner.equals(WorkersBridge.readOwner(nativeStorage))
                            && coreKey.equals(nativeBuild.getPersistentData().getString(ModConstants.Tags.CAMP_AREA_TEAM))
                            && coreKey.equals(nativeStorage.getPersistentData().getString(ModConstants.Tags.CAMP_AREA_TEAM))
                            && level.getBlockState(nativeSupply).is(Blocks.BARREL)
                            && supplyEntity != null && supplyEntity.getPersistentData().hasUUID(ModConstants.Tags.CAMP_SUPPLY_OWNER)
                            && nativeOwner.equals(supplyEntity.getPersistentData().getUUID(ModConstants.Tags.CAMP_SUPPLY_OWNER)),
                    "Native camp worker/build/storage/supply ownership is not one isolated persisted job");
            var workers = new ArrayList<Map<String, Object>>();
            for (UUID id : raid.campWorkers) {
                require(level.getEntity(id) instanceof Mob, "Recorded native camp worker is not loaded");
                Mob worker = (Mob)level.getEntity(id);
                require(worker.isAlive() && !worker.isNoAi() && WorkersBridge.isBuilder(worker)
                                && worker instanceof com.talhanation.workers.entities.BuilderEntity nativeWorker
                                && nativeWorker.getIsOwned() && !nativeWorker.getListen()
                                && ((com.talhanation.workers.entities.workarea.BuildArea)nativeBuild).canWorkHere(nativeWorker)
                                && ((com.talhanation.workers.entities.workarea.StorageArea)nativeStorage).canWorkHere(nativeWorker)
                                && worker.getTeam() != null && RaiderFactions.id(raid.factionId).equals(worker.getTeam().getName())
                                && coreKey.equals(worker.getPersistentData().getString(ModConstants.Tags.CAMP_WORKER_TEAM))
                                && nativeOwner.equals(WorkersBridge.readWorkerOwner(worker)),
                        "Camp worker is missing real native hostile ownership/crew identity");
                workers.add(entity(worker));
            }
            var guards = new ArrayList<Map<String, Object>>();
            var roles = com.devfarinsky.siegeoverhaul.narrative.OlympianHostIdentity.forFaction(raid.factionId).campDoctrine().guardRoles();
            Set<Integer> expectedGuardSlots = new LinkedHashSet<>(), actualGuardSlots = new LinkedHashSet<>();
            for (int slot = 0; slot < roles.size(); slot++)
                if (ForgeRegistries.ENTITY_TYPES.containsKey(new net.minecraft.resources.ResourceLocation("recruits", roles.get(slot))))
                    expectedGuardSlots.add(slot);
            for (UUID id : raid.campGuards) {
                require(level.getEntity(id) instanceof com.talhanation.recruits.entities.AbstractRecruitEntity guard
                                && guard.isAlive() && !guard.isNoAi() && guard.getTeam() != null
                                && RaiderFactions.id(raid.factionId).equals(guard.getTeam().getName())
                                && com.devfarinsky.siegeoverhaul.RecruitsBridge.RAIDERS_LEADER_UUID.equals(WorkersBridge.readWorkerOwner(guard))
                                && coreKey.equals(guard.getPersistentData().getString(CampGuards.TEAM_TAG)), "Native active camp guard type/faction/identity is missing");
                Mob guard = (Mob)level.getEntity(id);
                int slot = guard.getPersistentData().getInt("SiegeGuardSlot");
                require(expectedGuardSlots.contains(slot) && actualGuardSlots.add(slot)
                                && new net.minecraft.resources.ResourceLocation("recruits", roles.get(slot)).equals(ForgeRegistries.ENTITY_TYPES.getKey(guard.getType())),
                        "Native camp guard does not match its exact requested registered role/slot");
                guards.add(entity(guard));
            }
            require(actualGuardSlots.equals(expectedGuardSlots), "Controlled camp failed to establish every registered requested guard role: expected="
                    + expectedGuardSlots + ", actual=" + actualGuardSlots);
            require("wilds_marauders".equals(raid.factionId) && roles.equals(List.of("bowman", "scout", "crossbowman", "bowman", "scout", "assassin"))
                            && !ForgeRegistries.ENTITY_TYPES.containsKey(new net.minecraft.resources.ResourceLocation("recruits", "assassin"))
                            && guards.size() == 5 && guards.stream().filter(guard -> "recruits:scout".equals(guard.get("type"))
                            && Boolean.TRUE.equals(guard.get("scoutSpawnTailRecovered"))).count() == 2,
                    "Camp acceptance did not exercise both actual native scout recoveries and the unavailable assassin slot");
            NativeCampFixture.verifyProtected(level, fixture);
            require(raid.campBlocks.keySet().stream().noneMatch(pos -> fixture.protectedCells().containsKey(BlockPos.of(pos))),
                    "Camp restoration ledger includes a protected player/neighbor cell");
            var originalTerrain = new LinkedHashMap<String, Long>();
            for (String block : List.of("minecraft:water", "minecraft:oak_log", "minecraft:oak_leaves"))
                originalTerrain.put(block, raid.campBlocks.values().stream()
                        .filter(record -> block.equals(record.getCompound("Original").getString("Name"))).count());
            if (scenario == 1) require(originalTerrain.values().stream().allMatch(count -> count > 0),
                    "Hostile camp did not actually replace shallow water and supported logs/leaves through the production ledger");
            var result = new LinkedHashMap<String, Object>(sample(level, owner));
            result.put("status", "passed"); result.put("enemyCore", enemyCore.toShortString());
            result.put("expectedEnemyFaction", RaiderFactions.id(raid.factionId));
            result.put("nativeJobOwner", nativeOwner.toString());
            result.put("guardOwner", RecruitsBridge.RAIDERS_LEADER_UUID.toString());
            result.put("nativeJobOwnershipVerified", true);
            result.put("nativeWorkAreaAccessVerified", true); result.put("nativeBuilderStorageEnabled", true);
            result.put("nativeJobAreas", Map.of("build", nativeBuild.getUUID().toString(), "storage", nativeStorage.getUUID().toString(),
                    "buildOwner", WorkersBridge.readOwner(nativeBuild).toString(), "storageOwner", WorkersBridge.readOwner(nativeStorage).toString(),
                    "supplyOwner", supplyEntity.getPersistentData().getUUID(ModConstants.Tags.CAMP_SUPPLY_OWNER).toString()));
            result.put("nativeClaimChunks", claim.getClaimedChunks().size()); result.put("workers", workers); result.put("guards", guards);
            result.put("registeredGuardRoleCount", expectedGuardSlots.size());
            result.put("protectedCellCount", fixture.protectedCells().size()); result.put("protectedChests", fixture.protectedContainers().size());
            result.put("entry", "Actual non-op client /siegeoverhaul start command; normal production raid/scouting ticks");
            result.put("elapsedSeconds", (System.nanoTime() - scenarioStarted) / (double)SECOND);
            result.put("ordinaryTicksSinceEstablishment", level.getGameTime() - establishedAt);
            result.put("recordedOriginalTerrainCounts", originalTerrain);
            if (landingEvidence != null) result.put("controlledHostileTerrain", landingEvidence);
            result.put("constructionScope", "Camp claim, core, strategic blocks and live native crew established; full decorative build-out and waves are not asserted");
            SCENARIOS.add(result); captureCenter = raid.campPos.immutable(); captureCore = enemyCore.immutable();
            // Observer movement occurs only after real Survival command/establishment assertions.
            owner.setGameMode(GameType.SPECTATOR);
            captureObserver = new Vec3(raid.campPos.getX() + 30.5, raid.campPos.getY() + 24, raid.campPos.getZ() + 30.5);
            owner.teleportTo(level, captureObserver.x, captureObserver.y, captureObserver.z, 135, 30);
            stage = 3; return Action.CAPTURE;
        }
        if (stage == 3) {
            require(SHOTS.size() == scenario + 1, "Established camp framebuffer was not captured");
            return scenario == 0 ? Action.NEXT_WORLD : Action.DONE;
        }
        return Action.NONE;
    }

    private static Map<String, Object> sample(ServerLevel level, ServerPlayer owner) {
        var result = new LinkedHashMap<String, Object>();
        result.put("scenario", scenario == 0 ? "natural-flat" : "controlled-shallow-water-forest");
        result.put("stage", stage); result.put("gameTime", level.getGameTime());
        if (fixture == null) return result;
        result.put("nativeClaimAvailability", CampClaims.unavailableReason(level)); result.put("hostileTerrainPrepared", hostilePrepared);
        var raid = RaidSavedData.get(level.getServer()).raids.get(coreKey);
        if (raid != null) {
            result.put("campPosition", String.valueOf(raid.campPos)); result.put("claimId", String.valueOf(raid.campClaimId));
            result.put("nativeJobOwner", raid.nativeCamp.hasUUID(ModConstants.Tags.CAMP_OWNER)
                    ? raid.nativeCamp.getUUID(ModConstants.Tags.CAMP_OWNER).toString() : "absent");
            result.put("searchStep", raid.campSearchStep); result.put("searchElapsedTicks", raid.campSearchElapsedTicks);
            result.put("searchCandidate", String.valueOf(raid.campSearchPos)); result.put("terraformFallback", raid.campTerraformed);
            result.put("searchAbandoned", raid.campSearchAbandoned); result.put("crewStarted", raid.campCrewStarted);
            result.put("workers", raid.campWorkers.size()); result.put("guards", raid.campGuards.size());
            result.put("workerEntities", raid.campWorkers.stream().map(id -> crewEntity(level, id)).toList());
            result.put("guardEntities", raid.campGuards.stream().map(id -> crewEntity(level, id)).toList());
            var roles = com.devfarinsky.siegeoverhaul.narrative.OlympianHostIdentity.forFaction(raid.factionId).campDoctrine().guardRoles();
            result.put("requestedGuardRoles", roles);
            result.put("unregisteredGuardRoles", roles.stream().filter(role -> !ForgeRegistries.ENTITY_TYPES.containsKey(
                    new net.minecraft.resources.ResourceLocation("recruits", role))).toList());
            result.put("enemyCore", String.valueOf(EnemyCore.position(raid))); result.put("constructionPause", raid.constructionPauseReason);
        }
        return result;
    }
    private static Map<String, Object> entity(Mob entity) {
        var result = new LinkedHashMap<String, Object>(Map.of("uuid", entity.getUUID().toString(), "type", String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType())),
                "position", entity.position().toString(), "noAi", entity.isNoAi(), "owner", String.valueOf(WorkersBridge.readWorkerOwner(entity)),
                "faction", entity.getTeam() == null ? "" : entity.getTeam().getName(), "alive", entity.isAlive(),
                "workerTeamTag", entity.getPersistentData().getString(ModConstants.Tags.CAMP_WORKER_TEAM),
                "guardTeamTag", entity.getPersistentData().getString(CampGuards.TEAM_TAG),
                "scoutSpawnTailRecovered", entity.getPersistentData().getBoolean("SiegeScoutSpawnTailRecovered")));
        if (entity instanceof com.talhanation.recruits.entities.AbstractRecruitEntity recruit) {
            result.put("owned", recruit.getIsOwned()); result.put("listening", recruit.getListen());
        }
        return result;
    }
    private static Map<String, Object> crewEntity(ServerLevel level, UUID id) {
        return level.getEntity(id) instanceof Mob mob ? entity(mob)
                : Map.of("uuid", id.toString(), "missingOrWrongType", true);
    }
    private static long horizontalDistanceSquared(BlockPos a, BlockPos b) { long x = a.getX() - b.getX(), z = a.getZ() - b.getZ(); return x*x + z*z; }

    @SubscribeEvent
    public static void render(TickEvent.RenderTickEvent event) {
        if (!ENABLED || finished || event.phase != TickEvent.Phase.END) return;
        if (capture == null) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            boolean worldReady = mc.level != null && mc.player != null && mc.screen == null && mc.getOverlay() == null;
            boolean observerReady = worldReady && mc.player.isSpectator()
                    && mc.player.position().distanceToSqr(captureObserver) < 0.01;
            boolean coreReceived = worldReady && mc.level.hasChunkAt(captureCore)
                    && mc.level.getBlockState(captureCore).is(CoreBlocks.CORE.get());
            boolean terrainCompiled = worldReady && captureTerrainCompiled(mc);
            var camera = mc.gameRenderer.getMainCamera();
            boolean cameraReady = observerReady && mc.getCameraEntity() == mc.player
                    && camera.getPosition().distanceToSqr(mc.player.getEyePosition()) < 0.01
                    && Math.abs(camera.getXRot() - mc.player.getXRot()) < 0.1
                    && Math.abs(camera.getYRot() - mc.player.getYRot()) < 0.1;
            captureDiagnostics.put("screenshot", capture);
            captureDiagnostics.put("elapsedSeconds", (System.nanoTime() - captureStarted) / (double)SECOND);
            captureDiagnostics.put("worldReady", worldReady); captureDiagnostics.put("observerReady", observerReady);
            captureDiagnostics.put("coreReceived", coreReceived); captureDiagnostics.put("terrainCompiled", terrainCompiled);
            captureDiagnostics.put("cameraReady", cameraReady);
            captureDiagnostics.put("expectedObserver", captureObserver.toString());
            captureDiagnostics.put("clientPosition", mc.player == null ? "absent" : mc.player.position().toString());
            captureDiagnostics.put("cameraPosition", camera.getPosition().toString());
            captureDiagnostics.put("campCore", captureCore.toString());
            captureDiagnostics.put("framebufferAttempts", captureAttempts); captureDiagnostics.put("blankFrames", captureBlankFrames);
            require(System.nanoTime() - captureStarted < CAPTURE_TIMEOUT,
                    "Camp capture did not produce a ready, nonblank framebuffer within 30 seconds: " + captureDiagnostics);
            if (!(observerReady && coreReceived && terrainCompiled && cameraReady)) {
                captureReadyFrames = 0;
                return;
            }
            // Four complete frames must follow client teleport, real core delivery and terrain compilation.
            if (++captureReadyFrames < 4) return;
            try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                captureAttempts++;
                require(image.getWidth() >= 640 && image.getHeight() >= 360, "Camp framebuffer too small");
                int first = image.getPixelRGBA(0, 0), changed = 0;
                for (int y = 0; y < image.getHeight(); y += 16) for (int x = 0; x < image.getWidth(); x += 16)
                    if (image.getPixelRGBA(x, y) != first) changed++;
                captureDiagnostics.put("changedPixelSamples", changed);
                captureDiagnostics.put("framebufferAttempts", captureAttempts);
                long captureElapsed = System.nanoTime() - captureStarted;
                captureDiagnostics.put("elapsedSeconds", captureElapsed / (double)SECOND);
                require(captureElapsed < CAPTURE_TIMEOUT,
                        "Camp framebuffer readback/sampling exceeded the original 30-second capture deadline: " + captureDiagnostics);
                if (changed <= 50) {
                    captureDiagnostics.put("blankFrames", ++captureBlankFrames);
                    captureReadyFrames = 0;
                    return; // Re-observe a later real frame; the original deadline is never extended.
                }
                require(changed > 50, "Camp framebuffer appears blank");
                image.writeToFile(evidence.resolve(capture));
                captureElapsed = System.nanoTime() - captureStarted;
                captureDiagnostics.put("elapsedSeconds", captureElapsed / (double)SECOND);
                require(captureElapsed < CAPTURE_TIMEOUT,
                        "Camp framebuffer write exceeded the original 30-second capture deadline: " + captureDiagnostics);
                SHOTS.add(capture);
                SCENARIOS.get(scenario).put("framebufferCapture", new LinkedHashMap<>(captureDiagnostics));
                capture = null;
            }
        } catch (Throwable failure) { finish(mc, failure); }
    }

    private static boolean captureTerrainCompiled(Minecraft mc) {
        // Only demand compilation at the actual camp view target and its supporting surface.
        // Unrelated empty or out-of-frustum sections may legitimately remain uncompiled.
        BlockPos surface = captureCenter.below();
        return mc.level.hasChunkAt(surface) && !mc.level.getBlockState(surface).isAir()
                && mc.levelRenderer.isChunkCompiled(captureCore) && mc.levelRenderer.isChunkCompiled(surface);
    }

    private static void initialize(Minecraft mc) throws Exception {
        directory = Path.of(System.getProperty("siegeoverhaul.nativeQa.directory", "")).toAbsolutePath().normalize();
        require(directory.endsWith(Path.of("build", "native-camp-qa", "client"))
                        && mc.gameDirectory.toPath().toAbsolutePath().normalize().equals(directory), "Refusing a non-isolated camp QA directory");
        evidence = directory.getParent().resolve("evidence"); Files.createDirectories(evidence);
        REPORT.put("mode", "camp-spawn"); REPORT.put("startedUtc", Instant.now().toString());
        REPORT.put("scope", "Two fresh isolated worlds; real non-op client command and native production camp pipeline. Labeled terrain/faction/core fixtures; no private camp helper calls, timer changes or forced acceptance.");
        REPORT.put("notCovered", List.of("Arbitrary terrain or mod packs", "Dedicated multiplayer connection", "Full camp decorative completion", "Raid-wave combat and occupation"));
        var versions = new LinkedHashMap<String, String>(); var artifacts = new LinkedHashMap<String, Object>();
        for (String id : List.of("minecraft", "forge", "siegeoverhaul", "workers", "recruits", "smallships", "siegeweapons")) {
            var mod = ModList.get().getModContainerById(id).orElseThrow(); versions.put(id, mod.getModInfo().getVersion().toString());
            if (List.of("workers", "recruits", "smallships", "siegeweapons").contains(id)) {
                Path file = ModList.get().getModFileById(id).getFile().getFilePath();
                artifacts.put(id, Map.of("fileName", file.getFileName().toString(), "sha256", sha256(file),
                        "kind", "Loaded ForgeGradle remapped development JAR; not original release bytes"));
            }
        }
        REPORT.put("loadedModVersions", versions); REPORT.put("loadedCompanionArtifacts", artifacts);
        REPORT.put("nativeApiClasses", List.of("com.devfarinsky.siegeoverhaul.RaidEvents", "com.devfarinsky.siegeoverhaul.camp.CampScouting",
                "com.devfarinsky.siegeoverhaul.camp.CampTerrain", "com.talhanation.recruits.world.RecruitsClaimManager", "com.talhanation.workers.entities.BuilderEntity"));
        REPORT.put("openGlRenderer", GL11.glGetString(GL11.GL_RENDERER)); REPORT.put("openGlVersion", GL11.glGetString(GL11.GL_VERSION));
        mc.options.guiScale().set(2); mc.options.renderDistance().set(4); mc.options.simulationDistance().set(5);
        mc.options.pauseOnLostFocus = false; mc.resizeDisplay();
    }
    private static String sha256(Path file) throws Exception {
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(file)) { byte[] bytes = new byte[65536]; int count; while ((count = input.read(bytes)) > 0) digest.update(bytes, 0, count); }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }
    private static void aim(Minecraft mc, Vec3 target) {
        Vec3 delta = target.subtract(mc.player.getEyePosition());
        float yaw = (float)(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90);
        float pitch = (float)-Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x*delta.x + delta.z*delta.z)));
        mc.player.setYRot(yaw); mc.player.yRotO = yaw; mc.player.setXRot(pitch); mc.player.xRotO = pitch;
    }
    private static void finish(Minecraft mc, Throwable failure) {
        if (finished) return; finished = true;
        REPORT.put("status", failure == null ? "passed" : "failed"); REPORT.put("finishedUtc", Instant.now().toString());
        REPORT.put("scenarios", List.copyOf(SCENARIOS)); REPORT.put("samples", List.copyOf(SAMPLES)); REPORT.put("screenshots", List.copyOf(SHOTS));
        if (failure != null) {
            if (capture != null) REPORT.put("captureDiagnostics", captureDiagnostics);
            REPORT.put("failure", failure.toString()); FactionLogger.LOG.error("Native camp QA failed at scenario {} stage {}", scenario, stage, failure);
            if (evidence != null && mc.level != null) try (NativeImage pixels = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                pixels.writeToFile(evidence.resolve("failure-native-camp.png")); REPORT.put("failureFramebuffer", "failure-native-camp.png");
            } catch (Throwable ignored) { }
        }
        try { if (evidence != null) Files.writeString(evidence.resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(REPORT)); }
        catch (Exception writeFailure) { FactionLogger.LOG.error("Could not write camp QA evidence", writeFailure); }
        mc.stop();
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
