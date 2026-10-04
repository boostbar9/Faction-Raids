package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.camp.CampGuards;
import com.devfarinsky.siegeoverhaul.camp.WarGate;
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
import net.minecraft.nbt.CompoundTag;
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
import net.minecraftforge.event.server.ServerStartedEvent;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Real command, complete bounded searches, actual world reload and permitted first waves. Never shipped. */
@Mod.EventBusSubscriber(modid = SiegeOverhaul.MOD_ID, value = Dist.CLIENT)
public final class NativeCampLifecycleQa {
    private static final boolean ENABLED = Boolean.getBoolean("siegeoverhaul.nativeQa")
            && "camp-lifecycle".equals(System.getProperty("siegeoverhaul.nativeQa.mode"));
    private static final long SECOND = 1_000_000_000L;
    private static final List<String> WORLDS = List.of("siege-native-camp-no-safe-land", "siege-native-camp-recovery-island");
    private static final Map<String,Object> REPORT = new LinkedHashMap<>();
    private static final List<Map<String,Object>> SCENARIOS = new ArrayList<>(), SAMPLES = new ArrayList<>();
    private static final List<String> SHOTS = new ArrayList<>();
    private static final Map<String,Map<String,Integer>> PASSES = new LinkedHashMap<>();
    private static Path directory, evidence;
    private static NativeCampFixture.Fixture fixture;
    private static CompletableFuture<Action> pending;
    private static UUID playerId;
    private static String coreKey, capture;
    private static BlockPos islandCenter, captureCenter;
    private static Vec3 captureObserver;
    private static long started, scenarioStarted, lastSample = -1, establishedAt = -1, fallbackAt = -1;
    private static long firstRecoveryAt = -1, islandCompletedAt = -1, firstWaveAt = -1, captureStarted;
    private static long searchObservedTicks, cooldownObservedTicks, previousObservedTick = -1, lastIslandTick = -1;
    private static int scenario, clientPhase, stage, islandCursor, captureReadyFrames, captureAttempts;
    private static boolean finished, restartRequested, reloaded, scenarioComplete, crewVerified;
    private static boolean sawNatural, sawEarthworks, sawRecovery, sawCooldown;
    private static volatile Throwable observerFailure, stoppingFailure, loadFailure;
    private static CompoundTag stoppedAuthority, loadedAuthority;
    private static long stoppedGameTime;
    private static Map<String,Object> crewEvidence;
    private enum Action { NONE, START_COMMAND, RELOAD, CAPTURE, NEXT_WORLD, DONE }
    private NativeCampLifecycleQa() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (started == 0) started = System.nanoTime();
            require(System.nanoTime() - started < 40 * 60 * SECOND, "Camp lifecycle exceeded its 40-minute total bound");
            require(observerFailure == null && stoppingFailure == null && loadFailure == null,
                    "Server lifecycle observer failed: " + observerFailure + "; " + stoppingFailure + "; " + loadFailure);
            if (capture != null) {
                require(System.nanoTime() - captureStarted < 30 * SECOND, "Lifecycle framebuffer readiness timed out");
                if (mc.player != null && mc.player.position().distanceToSqr(captureObserver) < .01)
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
                require(!Files.exists(directory.resolve("saves").resolve(world)), "Refusing existing lifecycle fixture world");
                GameRules rules = new GameRules();
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
                scenarioStarted = System.nanoTime(); clientPhase = 2;
                mc.createWorldOpenFlows().createFreshLevel(world,
                        new LevelSettings(world, GameType.SURVIVAL, false, Difficulty.NORMAL, false,
                                rules, WorldDataConfiguration.DEFAULT), new WorldOptions(20261014L + scenario, false, false),
                        NativeCampLifecycleFixture::dimensions);
                return;
            }
            if (clientPhase == 3) {
                if (mc.getSingleplayerServer() != null || !(mc.screen instanceof TitleScreen)) return;
                require(stoppedAuthority != null && stoppingFailure == null, "Actual shutdown snapshot unavailable");
                clientPhase = 2;
                mc.createWorldOpenFlows().loadLevel(new TitleScreen(), WORLDS.get(scenario));
                return;
            }
            if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || mc.screen != null) return;
            require(System.nanoTime() - scenarioStarted < 21 * 60 * SECOND, "Lifecycle scenario exceeded its 21-minute wall-clock bound");
            if (playerId == null) playerId = mc.player.getUUID();
            if (pending != null) {
                if (!pending.isDone()) return;
                Action action = pending.join(); pending = null;
                if (action == Action.START_COMMAND) {
                    require(mc.getConnection() != null, "Actual Survival command connection missing");
                    mc.getConnection().sendCommand("siegeoverhaul start");
                } else if (action == Action.RELOAD) {
                    mc.level.disconnect(); mc.clearLevel(); mc.setScreen(new TitleScreen()); clientPhase = 3; return;
                } else if (action == Action.CAPTURE) {
                    mc.options.hideGui = true;
                    capture = scenario == 0 ? "01-bounded-campless-fallback.png" : "02-recovered-native-camp.png";
                    captureStarted = System.nanoTime(); captureReadyFrames = captureAttempts = 0; return;
                } else if (action == Action.NEXT_WORLD) {
                    mc.options.hideGui = false;
                    mc.level.disconnect(); mc.clearLevel(); mc.setScreen(new TitleScreen());
                    scenario++; resetScenario(); clientPhase = 1; return;
                } else if (action == Action.DONE) { finish(mc, null); return; }
            }
            CompletableFuture<Action> next = new CompletableFuture<>(); pending = next;
            var server = mc.getSingleplayerServer();
            server.execute(() -> {
                try { next.complete(step(server.overworld(), server.getPlayerList().getPlayer(playerId))); }
                catch (Throwable failure) {
                    try { REPORT.put("failureDiagnostics", sample(server.overworld(), raid(server.overworld()))); } catch (Throwable ignored) {}
                    next.completeExceptionally(failure);
                }
            });
        } catch (Throwable failure) { finish(mc, failure); }
    }

    private static Action step(ServerLevel level, ServerPlayer owner) throws Exception {
        require(owner != null && owner.isAlive(), "Actual lifecycle actor missing/dead");
        if (stage == 0) {
            fixture = NativeCampFixture.setup(level, owner, WORLDS.get(scenario), true);
            coreKey = SiegeCore.key(owner);
            require(CampClaims.unavailableReason(level).isEmpty(), "Native camp claim setup unavailable");
            require(RaidConfig.BUILD_WAR_CAMPS.get() && RaidConfig.CAMP_TERRAFORM.get()
                            && RaidConfig.LEVEL_CAMP_TERRAIN.get() && RaidConfig.CLEANUP_WAR_CAMPS.get()
                            && RaidConfig.ENABLE_CAMP_CONSTRUCTION.get(), "Production camp capabilities disabled");
            require(RaidConfig.PREPARATION_MINUTES.get() == 3 && RaidConfig.ENABLE_NARRATIVE.get()
                            && RaidConfig.ALLOWED_RAIDER_FACTIONS.get().equals(List.of("wilds_marauders")),
                    "Fresh QA configuration must select 3-minute preparation and existing Artemis doctrine");
            stage = 1; return Action.START_COMMAND;
        }
        var raid = raid(level);
        require(stage == 1 || raid != null, "Production raid vanished before lifecycle acceptance");
        if (raid == null) return Action.NONE;
        if (stage == 1) {
            require(!owner.isCreative() && !owner.hasPermissions(2), "Start actor was not non-op Survival");
            if (scenario == 1 && islandCenter == null)
                islandCenter = NativeCampLifecycleFixture.islandCenter(fixture.core(), raid.approachAngle);
            stage = 2;
        }
        if (scenario == 1 && islandCursor < NativeCampLifecycleFixture.ISLAND_WRITES) {
            if (lastIslandTick == level.getGameTime()) return Action.NONE;
            lastIslandTick = level.getGameTime();
            require(!raid.campSearchRecovery && !raid.campTerraformed, "Island setup did not finish during initial natural scouting");
            islandCursor = NativeCampLifecycleFixture.buildIslandBatch(level, fixture, islandCenter, islandCursor);
            if (islandCursor == NativeCampLifecycleFixture.ISLAND_WRITES) {
                fixture = NativeCampLifecycleFixture.addBoundarySpike(level, fixture, islandCenter);
                islandCompletedAt = level.getGameTime();
            }
        }
        if (lastSample < 0 || level.getGameTime() - lastSample >= 100) {
            NativeCampFixture.verifyProtected(level, fixture);
            for (BlockPos pos : fixture.protectedCells().keySet())
                require(!raid.campBlocks.containsKey(pos.asLong()), "Protected cell entered restoration ledger");
            var state = sample(level, raid); SAMPLES.add(state); lastSample = level.getGameTime();
            FactionLogger.LOG.info("Native camp lifecycle QA: {}", new GsonBuilder().create().toJson(state));
        }
        if (stage == 2 && scenario == 0 && sawCooldown && !restartRequested) {
            require(raid.campSearchRetryTicks > 0, "Missed actual recovery cooldown for reload");
            restartRequested = true; stage = 3; return Action.RELOAD;
        }
        if (stage == 3) {
            require(loadedAuthority != null && loadedAuthority.equals(stoppedAuthority), "Reload did not preserve actual search authority");
            require(raid.campSearchRecovery && raid.campSearchRetryTicks > 0 && raid.campPos == null
                            && !raid.campSearchAbandoned, "Recovery cooldown disappeared across actual reload");
            reloaded = true; stage = 2;
        }
        if (scenario == 1 && raid.campPos != null && !crewVerified) {
            if (establishedAt < 0) establishedAt = level.getGameTime();
            if (level.getGameTime() - establishedAt >= 60) {
                crewEvidence = verifyNativeCamp(level, raid); crewVerified = true;
            }
        }
        if (stage == 2 && raid.wave > 0 && raid.totalSpawned > 0) {
            require(sawNatural && sawEarthworks && sawCooldown && sawRecovery,
                    "Did not observe both ordinary passes, real cooldown and bounded recovery");
            require(searchObservedTicks > 0 && cooldownObservedTicks >= 1200, "Continuous pre-assault observer did not verify a full 60-second regroup");
            NativeCampFixture.verifyProtected(level, fixture);
            for (BlockPos pos : fixture.protectedCells().keySet())
                require(!raid.campBlocks.containsKey(pos.asLong()), "Protected cell entered final restoration ledger");
            if (scenario == 0) require(reloaded && raid.campPos == null && raid.campSearchAbandoned
                            && raid.campSearchDiagnostics.exhausted() && fallbackAt >= 0 && raid.preparationTicks == 0,
                    "Impossible terrain must reach explicit bounded camp-less fallback after complete preparation");
            else require(crewVerified && !raid.campSearchAbandoned && raid.campSearchRecovery
                            && islandCompletedAt >= 0 && islandCompletedAt < firstRecoveryAt
                            && establishedAt >= firstRecoveryAt && firstWaveAt > establishedAt
                            && WarGate.ready(level, raid), "Recovery wave preceded real established native camp/War Gate");
            Map<String,Object> result = new LinkedHashMap<>();
            result.put("scenario", scenario == 0 ? "no-safe-land-reload-fallback" : "recovery-only-island");
            result.put("status", "passed"); result.put("entry", "Actual non-op client /siegeoverhaul start; no raid-field or timer writes");
            result.put("sawNaturalSearch", sawNatural); result.put("sawEarthworksSearch", sawEarthworks);
            result.put("sawRecoveryCooldown", sawCooldown); result.put("sawWiderRecovery", sawRecovery);
            result.put("searchTicksWithoutWaveOrAttackers", searchObservedTicks);
            result.put("cooldownTicksWithoutWaveOrAttackers", cooldownObservedTicks);
            result.put("passes", new LinkedHashMap<>(PASSES)); result.put("realWorldReload", reloaded);
            result.put("savedSearchAuthorityVerified", reloaded && loadedAuthority.equals(stoppedAuthority));
            if (reloaded) result.put("reloadedAuthority", loadedAuthority.toString());
            result.put("protectedCellCount", fixture.protectedCells().size());
            result.put("protectedChests", fixture.protectedContainers().size());
            result.put("restorationExcludedProtectedCells", true);
            result.put("recoveryCooldownAtGameTime", firstRecoveryAt); result.put("fallbackAtGameTime", fallbackAt);
            result.put("establishedAtGameTime", establishedAt); result.put("firstWaveAtGameTime", firstWaveAt);
            result.put("wave", raid.wave); result.put("totalSpawned", raid.totalSpawned);
            result.put("campSearchAbandoned", raid.campSearchAbandoned);
            result.put("campSearchDiagnostics", raid.campSearchDiagnostics.save().toString());
            result.put("finalState", sample(level, raid));
            if (scenario == 1) {
                result.put("nativeCamp", crewEvidence);
                result.put("recoveryIsland", NativeCampLifecycleFixture.evidence(islandCenter, islandCompletedAt, firstRecoveryAt));
            }
            SCENARIOS.add(result); scenarioComplete = true; stage = 4;
            captureCenter = scenario == 0 ? fixture.core() : raid.campPos;
            captureObserver = Vec3.atCenterOf(captureCenter).add(30, 27, 30);
            owner.setGameMode(GameType.SPECTATOR);
            owner.teleportTo(level, captureObserver.x, captureObserver.y, captureObserver.z, 135, 32);
            return Action.CAPTURE;
        }
        if (stage == 4) return scenario == 0 ? Action.NEXT_WORLD : Action.DONE;
        return Action.NONE;
    }

    /** Observe every ordinary integrated-server tick, including periods with no client futures. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (!ENABLED || finished || fixture == null || scenarioComplete || event.phase != TickEvent.Phase.END) return;
        try {
            ServerLevel level = event.getServer().overworld(); var raid = raid(level);
            if (raid == null) return;
            long now = level.getGameTime();
            if (now == previousObservedTick) return;
            previousObservedTick = now;
            if (raid.campSearchRecovery && firstRecoveryAt < 0) firstRecoveryAt = now;
            if (raid.campSearchAbandoned && fallbackAt < 0) fallbackAt = now;
            if (raid.wave > 0 && firstWaveAt < 0) firstWaveAt = now;
            // A previously generated candidate may establish in its selection tick.
            // Seeing the genuine recovery phase/step is sufficient; no artificial
            // loading wait is required for coverage.
            if (raid.campSearchRecovery && raid.campSearchRetryTicks == 0 && raid.campSearchStep > 0) sawRecovery = true;
            if (raid.campPos == null && !raid.campSearchAbandoned) {
                searchObservedTicks++;
                require(raid.wave == 0 && raid.totalSpawned == 0 && raid.raiders.isEmpty()
                                && raid.pendingWaveSpawns == 0 && raid.plannedWaveSize == 0,
                        "Wave/attackers appeared before scouting/recovery completed");
                require(raid.preparationTicks == raid.preparationTotalTicks,
                        "Preparation advanced while camp search or recovery was still active");
                for (var entity : level.getAllEntities())
                    require(!coreKey.equals(entity.getPersistentData().getString(ModConstants.Tags.RAID_TEAM)),
                            "A tagged assault entity appeared before search completed");
                if (!raid.campSearchRecovery && !raid.campTerraformed) sawNatural = true;
                if (!raid.campSearchRecovery && raid.campTerraformed) sawEarthworks = true;
                if (raid.campSearchRetryTicks > 0) { sawCooldown = true; cooldownObservedTicks++; }
            }
            if (scenario == 0) require(raid.campPos == null && raid.campClaimId == null && raid.campWorkers.isEmpty()
                            && raid.campGuards.isEmpty(), "Impossible deep ocean unexpectedly established a camp");
            if (scenario == 1 && raid.wave > 0)
                require(raid.campPos != null && raid.campCrewStarted && CampClaims.owns(level, raid),
                        "Recovery-only world queued a camp-less wave");
            String pass = raid.campSearchRecovery ? "recovery" : raid.campTerraformed ? "earthworks" : "natural";
            var max = PASSES.computeIfAbsent(pass, ignored -> new LinkedHashMap<>());
            max.merge("maxCandidates", raid.campSearchStep, Math::max);
            max.merge("maxElapsedTicks", raid.campSearchElapsedTicks, Math::max);
            require(raid.campSearchStep <= 200 && raid.campSearchElapsedTicks <= 4800, "Production search exceeded its finite pass budget");
        } catch (Throwable failure) { observerFailure = failure; }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void serverStopping(ServerStoppingEvent event) {
        if (!ENABLED || !restartRequested || reloaded || stoppedAuthority != null || fixture == null) return;
        try {
            var level = event.getServer().overworld(); var raid = raid(level);
            require(raid != null && raid.campSearchRetryTicks > 0, "Shutdown missed live recovery cooldown");
            NativeCampFixture.verifyProtected(level, fixture);
            stoppedAuthority = authority(raid); stoppedGameTime = level.getGameTime();
        } catch (Throwable failure) { stoppingFailure = failure; }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void serverStarted(ServerStartedEvent event) {
        if (!ENABLED || !restartRequested || reloaded || stoppedAuthority == null) return;
        try {
            var level = event.getServer().overworld(); var raid = raid(level);
            require(level.getGameTime() == stoppedGameTime && raid != null, "Reload did not observe authority before resumed ticks");
            loadedAuthority = authority(raid);
            require(loadedAuthority.equals(stoppedAuthority), "Saved scouting/cooldown/diagnostics changed during actual close/reopen");
        } catch (Throwable failure) { loadFailure = failure; }
    }

    private static CompoundTag authority(RaidSavedData.RaidState raid) {
        CompoundTag saved = raid.save(), result = new CompoundTag();
        for (String key : List.of("Team", "DefensePoint", "FactionId", "CampSearchRecovery", "CampSearchRetryTicks",
                "CampSearchStep", "CampSearchTicks", "CampSearchElapsedTicks", "CampSearchDiagnostics", "CampSearchPos",
                "CampTerraformed", "CampSearchAbandoned", "PreparationTicks", "PreparationTotal", "Wave", "PendingWaveSpawns"))
            if (saved.contains(key)) result.put(key, saved.get(key).copy());
        require(result.getBoolean("CampSearchRecovery") && result.getInt("CampSearchRetryTicks") > 0,
                "Persisted recovery authority is missing its cooldown");
        return result;
    }

    private static Map<String,Object> verifyNativeCamp(ServerLevel level, RaidSavedData.RaidState raid) {
        require(raid.campSearchRecovery && raid.campTerraformed && raid.campCrewStarted && raid.campClaimId != null
                        && CampClaims.owns(level, raid), "Recovery did not establish native claimed camp");
        require(raid.campPos.equals(NativeCampLifecycleFixture.expectedCamp(islandCenter))
                        && raid.campaign.getBoolean("CampGradePreservedEdges"),
                "Recovery camp did not accept the first actual EDGE-rescue site with its durable receipt");
        var claim = ClaimEvents.recruitsClaimManager.getClaim(raid.campClaimId);
        require(claim != null && claim.getOwnerFactionStringID().equals(RaiderFactions.id(raid.factionId))
                        && new HashSet<>(claim.getClaimedChunks()).equals(CampClaims.footprint(raid.campPos)),
                "Recovery camp lacks exact owned 25-chunk claim");
        for (var chunk : claim.getClaimedChunks()) require(ClaimEvents.recruitsClaimManager.getClaim(chunk) != null
                        && raid.campClaimId.equals(ClaimEvents.recruitsClaimManager.getClaim(chunk).getUUID()), "Recovery native claim index missing");
        BlockPos core = EnemyCore.position(raid);
        require(core != null && level.getBlockState(core).is(CoreBlocks.CORE.get())
                        && raid.campfirePos != null && level.getBlockState(raid.campfirePos).is(Blocks.CAMPFIRE)
                        && raid.barrelPos != null && level.getBlockState(raid.barrelPos).is(Blocks.BARREL),
                "Recovery camp strategic blocks are incomplete");
        require(!raid.campWorkers.isEmpty() && raid.campGuards.size() == 5, "Recovery native workers or five registered guards missing");
        require(com.devfarinsky.siegeoverhaul.camp.NativeCampConstruction.active(raid), "Recovery native construction job missing");
        UUID nativeOwner = raid.nativeCamp.getUUID(ModConstants.Tags.CAMP_OWNER);
        List<Map<String,Object>> workers = new ArrayList<>(), guards = new ArrayList<>();
        String faction = RaiderFactions.id(raid.factionId);
        for (UUID id : raid.campWorkers) {
            require(level.getEntity(id) instanceof Mob, "Recovery worker not loaded");
            Mob mob = (Mob)level.getEntity(id);
            require(mob.isAlive() && !mob.isNoAi() && WorkersBridge.isBuilder(mob)
                            && nativeOwner.equals(WorkersBridge.readWorkerOwner(mob)) && mob.getTeam() != null
                            && faction.equals(mob.getTeam().getName())
                            && coreKey.equals(mob.getPersistentData().getString(ModConstants.Tags.CAMP_WORKER_TEAM)),
                    "Recovery worker native identity/AI is incorrect");
            workers.add(entity(mob));
        }
        for (UUID id : raid.campGuards) {
            require(level.getEntity(id) instanceof Mob, "Recovery guard not loaded");
            Mob mob = (Mob)level.getEntity(id);
            require(mob.isAlive() && !mob.isNoAi() && RecruitsBridge.isRecruitSoldier(mob)
                            && RecruitsBridge.RAIDERS_LEADER_UUID.equals(WorkersBridge.readWorkerOwner(mob))
                            && mob.getTeam() != null && faction.equals(mob.getTeam().getName())
                            && coreKey.equals(mob.getPersistentData().getString(CampGuards.TEAM_TAG)),
                    "Recovery guard native identity/AI is incorrect");
            guards.add(entity(mob));
        }
        return Map.of("claimId", raid.campClaimId.toString(), "nativeClaimChunks", claim.getClaimedChunks().size(),
                "enemyCore", core.toShortString(), "workers", workers, "guards", guards,
                "ordinaryTicksSinceEstablishment", level.getGameTime() - establishedAt,
                "expectedEnemyFaction", faction, "nativeJobOwner", nativeOwner.toString(),
                "warGateReadyAtCrewCheck", WarGate.ready(level, raid),
                "preservedEdgesRescue", raid.campaign.getBoolean("CampGradePreservedEdges"));
    }

    private static Map<String,Object> entity(Mob mob) {
        return Map.of("uuid", mob.getUUID().toString(), "type", String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(mob.getType())),
                "noAi", mob.isNoAi(), "faction", mob.getTeam().getName(),
                "owner", String.valueOf(WorkersBridge.readWorkerOwner(mob)));
    }
    private static RaidSavedData.RaidState raid(ServerLevel level) { return coreKey == null ? null : RaidSavedData.get(level.getServer()).raids.get(coreKey); }
    private static Map<String,Object> sample(ServerLevel level, RaidSavedData.RaidState raid) {
        var result = new LinkedHashMap<String,Object>(); result.put("scenario", scenario); result.put("gameTime", level.getGameTime());
        if (raid == null) return result;
        result.put("searchRecovery", raid.campSearchRecovery); result.put("retryTicks", raid.campSearchRetryTicks);
        result.put("searchStep", raid.campSearchStep); result.put("searchElapsedTicks", raid.campSearchElapsedTicks);
        result.put("searchCandidate", String.valueOf(raid.campSearchPos)); result.put("terraformFallback", raid.campTerraformed);
        result.put("searchAbandoned", raid.campSearchAbandoned); result.put("campPosition", String.valueOf(raid.campPos));
        result.put("preparationTicks", raid.preparationTicks); result.put("preparationTotalTicks", raid.preparationTotalTicks);
        result.put("wave", raid.wave); result.put("totalSpawned", raid.totalSpawned); result.put("raiders", raid.raiders.size());
        result.put("pendingWaveSpawns", raid.pendingWaveSpawns); result.put("crewStarted", raid.campCrewStarted);
        result.put("diagnostics", raid.campSearchDiagnostics.save().toString()); result.put("objectiveStatus", raid.objectiveStatus);
        return result;
    }

    @SubscribeEvent
    public static void render(TickEvent.RenderTickEvent event) {
        if (!ENABLED || finished || capture == null || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            boolean ready = mc.level != null && mc.player != null && mc.screen == null && mc.getOverlay() == null
                    && mc.player.isSpectator() && mc.player.position().distanceToSqr(captureObserver) < .01
                    && mc.level.hasChunkAt(captureCenter) && mc.levelRenderer.isChunkCompiled(captureCenter.below());
            if (!ready) { captureReadyFrames = 0; return; }
            aim(mc, Vec3.atCenterOf(captureCenter).add(0, 3, 0));
            if (++captureReadyFrames < 8) return;
            captureAttempts++;
            try (NativeImage pixels = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                int first = pixels.getPixelRGBA(0, 0), changed = 0;
                for (int x = 0; x < pixels.getWidth(); x += 13) for (int y = 0; y < pixels.getHeight(); y += 13)
                    if (pixels.getPixelRGBA(x, y) != first) changed++;
                if (changed <= 50) return;
                pixels.writeToFile(evidence.resolve(capture)); SHOTS.add(capture);
                SCENARIOS.get(scenario).put("framebuffer", Map.of("file", capture, "readyFrames", captureReadyFrames,
                        "attempts", captureAttempts, "changedPixelSamples", changed,
                        "elapsedSeconds", (System.nanoTime() - captureStarted) / (double)SECOND));
                capture = null;
            }
        } catch (Throwable failure) { finish(mc, failure); }
    }

    private static void initialize(Minecraft mc) throws Exception {
        directory = Path.of(System.getProperty("siegeoverhaul.nativeQa.directory", "")).toAbsolutePath().normalize();
        require(directory.endsWith(Path.of("build", "native-camp-lifecycle-qa", "client"))
                        && mc.gameDirectory.toPath().toAbsolutePath().normalize().equals(directory), "Refusing non-isolated lifecycle directory");
        evidence = directory.getParent().resolve("evidence"); Files.createDirectories(evidence);
        REPORT.put("mode", "camp-lifecycle"); REPORT.put("startedUtc", Instant.now().toString());
        REPORT.put("scope", "Actual Survival start command, two complete original search passes, real 60-second recovery cooldown, wider bounded recovery, actual save/close/reopen, native camp and permitted first waves");
        REPORT.put("fixtureConfiguration", Map.of("siegePreparationMinutes", 3, "waterDepth", NativeCampLifecycleFixture.WATER_DEPTH,
                "scoutingTimersChanged", false, "recoveryTimersChanged", false, "raidFieldsWritten", false));
        REPORT.put("notCovered", List.of("Arbitrary generated terrain or mod packs", "Dedicated multiplayer connection", "Complete decorative camp build", "Wave combat outcome and occupation"));
        var versions = new LinkedHashMap<String,String>(); var artifacts = new LinkedHashMap<String,Object>();
        for (String id : List.of("minecraft", "forge", "siegeoverhaul", "workers", "recruits", "smallships", "siegeweapons")) {
            versions.put(id, ModList.get().getModContainerById(id).orElseThrow().getModInfo().getVersion().toString());
            if (List.of("workers", "recruits", "smallships", "siegeweapons").contains(id)) {
                Path file = ModList.get().getModFileById(id).getFile().getFilePath();
                artifacts.put(id, Map.of("fileName", file.getFileName().toString(), "sha256", sha256(file),
                        "kind", "Loaded ForgeGradle remapped development JAR; not original release bytes"));
            }
        }
        REPORT.put("loadedModVersions", versions); REPORT.put("loadedCompanionArtifacts", artifacts);
        REPORT.put("openGlRenderer", GL11.glGetString(GL11.GL_RENDERER));
        mc.options.guiScale().set(2); mc.options.renderDistance().set(4); mc.options.simulationDistance().set(5);
        mc.options.pauseOnLostFocus = false; mc.resizeDisplay();
    }
    private static void resetScenario() {
        fixture = null; playerId = null; coreKey = null; stage = 0; islandCenter = null; islandCursor = 0;
        lastSample = establishedAt = fallbackAt = firstRecoveryAt = islandCompletedAt = firstWaveAt = previousObservedTick = lastIslandTick = -1;
        searchObservedTicks = cooldownObservedTicks = 0;
        restartRequested = reloaded = scenarioComplete = crewVerified = sawNatural = sawEarthworks = sawRecovery = sawCooldown = false;
        stoppedAuthority = loadedAuthority = null; crewEvidence = null; PASSES.clear();
        observerFailure = stoppingFailure = loadFailure = null;
    }
    private static String sha256(Path file) throws Exception {
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        try (var stream = Files.newInputStream(file)) { byte[] bytes = new byte[65536]; int n; while ((n = stream.read(bytes)) > 0) digest.update(bytes, 0, n); }
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
            REPORT.put("failure", failure.toString()); FactionLogger.LOG.error("Native camp lifecycle failed in scenario {} stage {}", scenario, stage, failure);
            if (evidence != null && mc.level != null) try (NativeImage pixels = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                pixels.writeToFile(evidence.resolve("failure-native-camp-lifecycle.png"));
            } catch (Throwable ignored) {}
        }
        try { if (evidence != null) Files.writeString(evidence.resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(REPORT)); }
        catch (Exception problem) { FactionLogger.LOG.error("Could not write native lifecycle evidence", problem); }
        mc.stop();
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
