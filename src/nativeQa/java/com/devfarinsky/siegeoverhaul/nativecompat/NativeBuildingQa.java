package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.FactionLogger;
import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import com.devfarinsky.siegeoverhaul.client.ProtectedConstructionScreen;
import com.devfarinsky.siegeoverhaul.client.CoreHireScreen;
import com.devfarinsky.siegeoverhaul.core.CoreHireMenu;
import com.devfarinsky.siegeoverhaul.core.ConstructionReport;
import com.devfarinsky.siegeoverhaul.core.NativeQaPerimeterTemplate;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import com.talhanation.recruits.client.events.ClientEvent;
import com.talhanation.workers.client.render.WorkerAreaRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Opt-in, unshipped real-client smoke test. Only a fresh build/native-qa/client
 * world is allowed. Fixture setup deliberately bypasses commissioning; the report
 * names this limitation. No mock companion, renderer replacement or fake ray hit.
 */
@Mod.EventBusSubscriber(modid = SiegeOverhaul.MOD_ID, value = Dist.CLIENT)
public final class NativeBuildingQa {
    private static final boolean ENABLED = Boolean.getBoolean("siegeoverhaul.nativeQa")
            && !java.util.Set.of("perimeter-completion", "territory-perimeter")
                    .contains(System.getProperty("siegeoverhaul.nativeQa.mode", "baseline"));
    private static final String WORLD = "siege-native-qa-fixture";
    private static final BlockPos ORIGIN = new BlockPos(8, 65, 8);
    private static final BlockPos MARKER = new BlockPos(0, 65, 0);
    private static final BlockPos OBSTRUCTION = new BlockPos(0, 66, -1);
    private static final List<String> CHECKS = new ArrayList<>();
    private static final List<String> SHOTS = new ArrayList<>();
    private static final Map<String, Object> REPORT = new LinkedHashMap<>();
    private static Path directory;
    private static Path evidence;
    private static UUID areaId;
    private static UUID builderId;
    private static UUID playerId;
    private static UUID perimeterId;
    private static int phase;
    private static int ticks;
    private static int readyAt;
    private static int renderFrames;
    private static int screenshotReadyFrame;
    private static long started;
    private static boolean finished;
    private static boolean sawEntities;
    private static boolean restoredCulling;
    private static String screenshot;
    private static Runnable afterScreenshot;
    private static CompletableFuture<Void> serverTask;

    private NativeBuildingQa() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void observeNativeBuilder(EntityJoinLevelEvent event) {
        if (ENABLED && !finished) NativeBuildingGameplay.observeNativeBuilder(event);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            ticks++;
            if (started == 0) started = System.nanoTime();
            require(System.nanoTime() - started < 8L * 60 * 1_000_000_000,
                    "Client fixture exceeded eight-minute deadline in phase " + phase);
            if (serverTask != null) {
                if (!serverTask.isDone()) return;
                serverTask.join();
                serverTask = null;
                readyAt = ticks + 30;
            }
            if (ticks < readyAt || screenshot != null) return;
            switch (phase) {
                case 0 -> {
                    if (!(mc.screen instanceof TitleScreen)) return;
                    initializeEvidence(mc);
                    require(!Files.exists(directory.resolve("saves").resolve(WORLD)),
                            "Refusing to replace or reuse a pre-existing fixture world");
                    mc.options.renderDistance().set(4);
                    mc.options.simulationDistance().set(5);
                    mc.options.guiScale().set(2);
                    mc.options.pauseOnLostFocus = false;
                    mc.resizeDisplay();
                    GameRules rules = new GameRules();
                    rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                    rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                    rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
                    LevelSettings settings = new LevelSettings(WORLD, GameType.SURVIVAL, false,
                            Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
                    phase = 1;
                    mc.createWorldOpenFlows().createFreshLevel(WORLD, settings,
                            new WorldOptions(20261003L, false, false), access ->
                                    access.registryOrThrow(Registries.WORLD_PRESET)
                                            .getOrThrow(WorldPresets.FLAT).createWorldDimensions());
                }
                case 1 -> {
                    if (!worldReady(mc)) return;
                    playerId = mc.player.getUUID();
                    submitServer(mc, () -> setUpFixture(mc.getSingleplayerServer().overworld(),
                            mc.getSingleplayerServer().getPlayerList().getPlayer(playerId)));
                    phase = 2;
                }
                case 2 -> {
                    ProtectedBuildArea area = clientArea(mc);
                    if (area == null) return;
                    assertClientParity(mc, area);
                    aim(mc, new Vec3(.5, 65.8, .5));
                    delay(25); phase = 3;
                }
                case 3 -> {
                    require(ClientEvent.getEntityByLooking() == clientArea(mc),
                            "Native Minecraft/Recruits raycast did not hit the visible shovel marker");
                    check("Native raycast hits separately positioned marker");
                    capture("01-marker-focused.png");
                    phase = 4;
                }
                case 4 -> {
                    // Exercise actual C2S interaction and Workers' S2C screen-opening packet.
                    mc.gameMode.interact(mc.player, clientArea(mc), InteractionHand.MAIN_HAND);
                    delay(30); phase = 5;
                }
                case 5 -> {
                    require(mc.screen instanceof ProtectedConstructionScreen,
                            "Native interaction did not open ProtectedConstructionScreen");
                    assertInspectionControls(mc);
                    check("Actual native interaction/network path opens protected inspection screen");
                    capture("02-native-inspection-scale2.png"); phase = 6;
                }
                case 6 -> {
                    mc.options.guiScale().set(3); mc.resizeDisplay(); delay(20); phase = 7;
                }
                case 7 -> {
                    require(mc.screen instanceof ProtectedConstructionScreen, "Inspection screen lost during resize");
                    assertInspectionControls(mc);
                    check("Native protected inspection Projection, Cancel job and Close fit compact GUI scale without overlap");
                    capture("03-native-inspection-scale3.png"); phase = 8;
                }
                case 8 -> {
                    clickVisibleButton(mc, "Cancel job");
                    require(mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen, "Cancel job did not show confirmation");
                    clickVisibleButton(mc, "No");
                    require(mc.screen instanceof ProtectedConstructionScreen, "Declining cancel did not return to inspection");
                    clickVisibleButton(mc, "Close");
                    require(mc.screen == null, "Native protected Close control did not close screen");
                    check("Native protected cancel-back and Close controls work through actual visible hitboxes");
                    mc.options.guiScale().set(2); mc.resizeDisplay();
                    submitServer(mc, () -> player(mc).teleportTo(.5, 65, -2));
                    delay(20); phase = 9;
                }
                case 9 -> {
                    aim(mc, new Vec3(6.5, 66, 10)); delay(25); phase = 10;
                }
                case 10 -> {
                    require(clientArea(mc).getAlwaysShowProjection(), "Always projection flag was not synchronized");
                    captureThen("04-projection-unfocused.png",
                            () -> submitServer(mc, () -> serverArea(mc).projectionAuthorized(false))); phase = 11;
                }
                case 11 -> {
                    require(!clientArea(mc).getAlwaysShowProjection(), "Focus-only flag was not synchronized");
                    require(ClientEvent.getEntityByLooking() != clientArea(mc), "Expected unfocused camera");
                    captureThen("05-projection-focus-only.png", () -> submitServer(mc, () -> {
                        serverArea(mc).projectionAuthorized(true);
                        player(mc).teleportTo(7.5, 66, 5);
                    })); phase = 12;
                }
                case 12 -> {
                    aim(mc, new Vec3(6.5, 66, 10)); delay(25); phase = 13;
                }
                case 13 -> {
                    require(ClientEvent.getEntityByLooking() != clientArea(mc), "Marker should be behind camera");
                    require(sawEntities && restoredCulling, "Projection culling was not restored after entity pass");
                    check("Real entity render pass restores temporary noCulling state");
                    captureThen("06-projection-marker-behind-camera.png", () -> submitServer(mc, () -> {
                        player(mc).teleportTo(.5, 65, -2);
                        mc.getSingleplayerServer().overworld().setBlock(OBSTRUCTION, Blocks.STONE.defaultBlockState(), 3);
                    })); phase = 14;
                }
                case 14 -> { aim(mc, new Vec3(.5, 65.8, .5)); delay(25); phase = 15; }
                case 15 -> {
                    require(ClientEvent.getEntityByLooking() != clientArea(mc),
                            "Raycast incorrectly selects shovel through solid obstruction");
                    check("Native raycast respects solid-block occlusion");
                    captureThen("07-marker-occluded.png", () -> submitServer(mc, () -> {
                        ServerLevel level = mc.getSingleplayerServer().overworld();
                        level.setBlock(OBSTRUCTION, Blocks.AIR.defaultBlockState(), 3);
                        assertServerParity(level, serverArea(mc));
                        mc.getSingleplayerServer().saveEverything(false, true, true);
                    })); phase = 16;
                }
                case 16 -> {
                    // Normal client disconnect drains and closes its integrated server.
                    mc.level.disconnect(); mc.clearLevel(); mc.setScreen(new TitleScreen());
                    delay(30); phase = 17;
                }
                case 17 -> {
                    require(mc.getSingleplayerServer() == null, "Previous integrated server is still attached");
                    phase = 18;
                    mc.createWorldOpenFlows().loadLevel(new TitleScreen(), WORLD);
                }
                case 18 -> {
                    if (!worldReady(mc) || clientArea(mc) == null) return;
                    submitServer(mc, () -> {
                        ServerLevel level = mc.getSingleplayerServer().overworld();
                        assertServerParity(level, serverArea(mc));
                        require(level.getEntity(builderId) instanceof Mob, "Native builder did not survive world reload");
                    }); phase = 19;
                }
                case 19 -> {
                    assertClientParity(mc, clientArea(mc));
                    aim(mc, new Vec3(.5, 65.8, .5)); delay(25); phase = 20;
                }
                case 20 -> {
                    require(ClientEvent.getEntityByLooking() == clientArea(mc), "Reloaded marker is not ray-pickable");
                    check("Full world save/close/reopen preserves marker, origin, owner, blueprint and native builder");
                    capture("08-marker-after-world-reload.png"); phase = 21;
                }
                case 21 -> {
                    CoreHireMenu menu = new CoreHireMenu(99, mc.player.getInventory());
                    menu.details("QA fixture (no live claim)", List.of("Fixture owner"), new int[] {100, -20});
                    menu.setData(18, 480); menu.setData(19, 0);
                    menu.construction(List.of(
                            new ConstructionReport.Job("QA perimeter fixture", 42, "42% placed; fixture display only",
                                    "Marker 0, 65, 0", "Paused: owner offline (sample)", "192 cobblestone (sample)"),
                            new ConstructionReport.Job("QA watchtower fixture", 75, "75% placed; fixture display only",
                                    "Marker 8, 65, 8", "Resupplying (sample)", "48 oak planks (sample)"),
                            new ConstructionReport.Job("QA blocked fixture", -1, "Progress unavailable (sample)",
                                    "Marker 16, 65, 16", "Paused: claim permission changed (sample)", ""),
                            new ConstructionReport.Job("QA wall fixture", 10, "10% placed; fixture display only",
                                    "Marker 24, 65, 24", "Working (sample)", "96 cobblestone (sample)")));
                    mc.setScreen(new CoreHireScreen(menu, mc.player.getInventory(), Component.literal("QA Building fixture")));
                    for (int attempts = 0; !hasVisibleButton(mc, "Building") && attempts < 7; attempts++)
                        clickVisibleButton(mc, ">");
                    clickVisibleButton(mc, "Building");
                    delay(20); phase = 22;
                }
                case 22 -> {
                    require(mc.screen instanceof CoreHireScreen, "Actual Building HUD is missing");
                    require(hasVisibleButton(mc, "Auto perimeter", "Perimeter"), "Perimeter section is not visible");
                    capture("09-building-auto-perimeter.png"); phase = 23;
                }
                case 23 -> {
                    clickVisibleButton(mc, "Place structure", "Structures"); delay(20); phase = 24;
                }
                case 24 -> {
                    require(hasVisibleButton(mc, "Take free plan", "Take free plan: Wall Section"),
                            "Actual structure-plan controls are missing");
                    capture("10-building-place-structure.png"); phase = 25;
                }
                case 25 -> { clickVisibleButton(mc, "Construction"); delay(20); phase = 26; }
                case 26 -> {
                    capture("11-building-construction.png"); phase = 27;
                }
                case 27 -> {
                    mc.options.guiScale().set(3); mc.resizeDisplay(); delay(20); phase = 28;
                }
                case 28 -> {
                    require(mc.screen instanceof CoreHireScreen && hasVisibleButton(mc, "Construction"),
                            "Compact actual Building HUD lost its section navigation");
                    require(mc.getWindow().getGuiScaledHeight() <= 240, "Compact GUI-scale view was not exercised");
                    check("Actual CoreHireScreen Building sections clicked/rendered, including compact construction fixture");
                    capture("12-building-construction-compact.png"); phase = 29;
                }
                case 29 -> {
                    mc.setScreen(null); mc.options.guiScale().set(2); mc.resizeDisplay();
                    submitServer(mc, () -> setUpPerimeterFixture(mc)); phase = 30;
                }
                case 30 -> {
                    if (findClientArea(mc, perimeterId) == null) return;
                    aim(mc, new Vec3(40, 68, 8)); delay(25); phase = 31;
                }
                case 31 -> {
                    ProtectedBuildArea perimeter = findClientArea(mc, perimeterId);
                    require(perimeter != null && perimeter.getAlwaysShowProjection(), "Native perimeter projection fixture missing");
                    var expected = NativeQaPerimeterTemplate.create();
                    require(perimeter.getOriginPos().equals(expected.origin())
                                    && perimeter.getStructureNBT().equals(expected.blueprint()),
                            "Production perimeter template changed during network synchronization");
                    require(mc.getEntityRenderDispatcher().getRenderer(perimeter).getClass().getName()
                                    .equals(WorkerAreaRenderer.class.getName()), "Perimeter fixture has wrong native renderer");
                    check("Production perimeter template rendered by actual Workers renderer with synchronized native coordinates");
                    capture("13-native-perimeter-template.png"); phase = 32;
                }
                case 32 -> { phase = 33; started = System.nanoTime(); }
                case 33 -> {
                    if (NativeBuildingGameplay.tick(mc)) {
                        REPORT.put("gameplay", NativeBuildingGameplay.result());
                        finish(mc, null);
                    }
                }
                default -> throw new AssertionError("Unexpected phase " + phase);
            }
        } catch (Throwable failure) { finish(mc, failure); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterEntities(RenderLevelStageEvent event) {
        if (!ENABLED || finished || event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        ProtectedBuildArea area = clientArea(Minecraft.getInstance());
        if (area != null) { sawEntities = true; restoredCulling = !area.noCulling; }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void render(TickEvent.RenderTickEvent event) {
        if (!ENABLED || finished || event.phase != TickEvent.Phase.END) return;
        renderFrames++;
        if (screenshot == null || renderFrames < screenshotReadyFrame) return;
        Minecraft mc = Minecraft.getInstance();
        try (NativeImage pixels = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            String name = screenshot;
            if (mc.screen != null) REPORT.put("cursor-" + name, Map.of(
                    "x", mc.mouseHandler.xpos(), "y", mc.mouseHandler.ypos(),
                    "windowFocused", org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(), org.lwjgl.glfw.GLFW.GLFW_FOCUSED)));
            require(pixels.getWidth() >= 640 && pixels.getHeight() >= 360, "Framebuffer is unexpectedly small");
            // Do not silently accept a blank framebuffer as visual evidence.
            int first = pixels.getPixelRGBA(0, 0), changed = 0;
            for (int y = 0; y < pixels.getHeight(); y += 16)
                for (int x = 0; x < pixels.getWidth(); x += 16)
                    if (pixels.getPixelRGBA(x, y) != first) changed++;
            require(changed > 50, "Framebuffer appears blank");
            pixels.writeToFile(evidence.resolve(name));
            SHOTS.add(name);
            screenshot = null;
            Runnable continuation = afterScreenshot; afterScreenshot = null;
            if (continuation != null) continuation.run();
        } catch (Throwable failure) { finish(mc, failure); }
    }

    private static void initializeEvidence(Minecraft mc) throws Exception {
        String configured = System.getProperty("siegeoverhaul.nativeQa.directory", "");
        require(!configured.isBlank(), "Missing isolated QA directory property");
        directory = Path.of(configured).toAbsolutePath().normalize();
        require(directory.endsWith(Path.of("build", "native-qa", "client")), "Unsafe QA game directory");
        require(mc.gameDirectory.toPath().toRealPath().equals(directory.toRealPath()), "Unexpected active game directory");
        evidence = directory.getParent().resolve("evidence");
        Files.createDirectories(evidence);
        REPORT.put("startedUtc", Instant.now().toString());
        REPORT.put("coverage", "Phase1: real native rendering/lifecycle fixtures. Phase2: isolated survival production commissioning, native AI/materials and permission/reload acceptance. See gameplay assertions and exclusions.");
        REPORT.put("notCovered", List.of("Menu-issued Review/Take-plan commands beyond the read-only live Core HUD acceptance", "Full claim/core/owner mutation matrix beyond enumerated gameplay cases", "Dedicated-server connection", "Shader/resource-pack or GPU-driver matrix", "1024/1025-cell boundary and scan-bound profiling"));
        Map<String, String> mods = new LinkedHashMap<>();
        Map<String, Object> artifacts = new LinkedHashMap<>();
        for (String id : List.of("minecraft", "forge", "siegeoverhaul", "workers", "recruits", "smallships", "siegeweapons")) {
            var container = ModList.get().getModContainerById(id);
            require(container.isPresent(), "Required real companion is not loaded: " + id);
            var info = container.orElseThrow().getModInfo();
            mods.put(id, info.getVersion().toString());
            if (List.of("workers", "recruits", "smallships", "siegeweapons").contains(id)) {
                Path jar = info.getOwningFile().getFile().getFilePath();
                require(Files.isRegularFile(jar), "Cannot hash actual loaded companion jar: " + id);
                artifacts.put(id, Map.of("fileName", jar.getFileName().toString(), "sha256", sha256(jar),
                        "kind", "ForgeGradle remapped development runtime JAR, not original release bytes"));
            }
        }
        require("2.0.3".equals(mods.get("workers")), "Unreviewed Workers runtime version");
        require("1.15.2".equals(mods.get("recruits")), "Unreviewed Recruits runtime version");
        REPORT.put("hudFixture", "Actual CoreHireScreen/client CoreHireMenu with clearly labeled sample report rows; no real core/claim or purchase, no server-authoritative report claim");
        REPORT.put("loadedModVersions", mods);
        REPORT.put("loadedCompanionArtifacts", artifacts);
        REPORT.put("nativeApiClasses", List.of(WorkerAreaRenderer.class.getName(),
                com.talhanation.workers.entities.workarea.BuildArea.class.getName(),
                com.talhanation.workers.entities.BuilderEntity.class.getName(), ClientEvent.class.getName()));
        REPORT.put("openGlVendor", GL11.glGetString(GL11.GL_VENDOR));
        REPORT.put("openGlRenderer", GL11.glGetString(GL11.GL_RENDERER));
        REPORT.put("openGlVersion", GL11.glGetString(GL11.GL_VERSION));
        check("All four required real companion mods loaded");
    }

    private static void setUpFixture(ServerLevel level, ServerPlayer owner) throws Exception {
        require(owner != null, "Real integrated-server player is missing");
        require(level.getServer().getWorldPath(LevelResource.ROOT).toRealPath()
                        .equals(directory.resolve("saves").resolve(WORLD).toRealPath()), "Unsafe fixture world path");
        level.setDayTime(6000);
        for (int x = -12; x <= 20; x++) for (int z = -12; z <= 22; z++) {
            level.setBlock(new BlockPos(x, 64, z), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            for (int y = 65; y <= 70; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        }
        owner.teleportTo(.5, 65, -2);
        var type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("workers", "builder"));
        require(type != null, "Native Workers builder type is absent");
        Entity raw = type.create(level);
        require(raw instanceof Mob, "Native Workers builder type is not a Mob");
        Mob builder = (Mob) raw;
        builder.setPos(-3, 65, 2); builder.setNoAi(true); builder.setPersistenceRequired();
        WorkersBridge.enablePlayerJob(builder, owner.getUUID());
        require(level.addFreshEntity(builder), "Could not add native builder fixture");
        builderId = builder.getUUID();
        ProtectedBuildArea area = ProtectedConstructionAreas.TYPE.get().create(level);
        require(area != null, "Protected native entity registry entry is absent");
        area.initialize(ORIGIN, MARKER, owner.getUUID(), owner.getGameProfile().getName(), builderId, 4, 5, 3, blueprint());
        area.initializeBlueprint(blueprint());
        area.initializeProjection(true);
        require(level.addFreshEntity(area), "Could not add protected area fixture");
        areaId = area.getUUID();
        assertServerParity(level, area);
        for (String contract : ProtectedNativeEntityContracts.verify(level, owner, area, builder)) check(contract);
        CompoundTag saved = area.saveWithoutId(new CompoundTag());
        ProtectedBuildArea decoded = ProtectedConstructionAreas.TYPE.get().create(level);
        require(decoded != null, "Could not create native NBT decode fixture");
        decoded.load(saved);
        require(decoded.getOriginPos().equals(ORIGIN) && decoded.position().equals(area.position())
                && decoded.getStructureNBT().equals(blueprint()) && builderId.equals(decoded.reservedBuilderId()),
                "Full native entity NBT round trip changed sealed contract");
        // Real native virtual calls, including the creative direct-write entry, must remain inert.
        area.setStartBuild(true); area.setFreeArea(true); area.setFacing(net.minecraft.core.Direction.NORTH);
        area.moveTo(99, 99, 99); area.setDone(true);
        assertServerParity(level, area);
        require(!area.getFreeArea() && !area.isDone(), "Unauthenticated native setters changed sealed state");
        check("Real server entity NBT round trip and native mutator denial");
    }

    private static void setUpPerimeterFixture(Minecraft mc) {
        ServerLevel level = mc.getSingleplayerServer().overworld();
        var template = NativeQaPerimeterTemplate.create();
        for (int x = 28; x <= 50; x++) for (int z = -3; z <= 19; z++)
            level.setBlock(new BlockPos(x, 64, z), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        ProtectedBuildArea area = ProtectedConstructionAreas.TYPE.get().create(level);
        require(area != null, "Could not create native perimeter fixture");
        area.initialize(template.origin(), new BlockPos(29, 65, 7), playerId,
                player(mc).getGameProfile().getName(), builderId,
                template.width(), template.depth(), template.height(), template.blueprint());
        area.initializeBlueprint(template.blueprint()); area.initializeProjection(true);
        require(level.addFreshEntity(area), "Could not publish native perimeter fixture");
        perimeterId = area.getUUID();
        // A spectator fixture camera provides a stable aerial view. No production player is changed.
        player(mc).setGameMode(GameType.SPECTATOR);
        player(mc).teleportTo(40.5, 83, -9);
        REPORT.put("perimeterTemplateBlocks", template.blockCount());
    }

    private static void assertServerParity(ServerLevel level, ProtectedBuildArea area) {
        require(area != null && area.isAlive(), "Server protected marker is missing");
        require(area.getOriginPos().equals(ORIGIN), "Server world blueprint origin shifted");
        require(area.position().equals(Vec3.atBottomCenterOf(MARKER)), "Server physical marker moved");
        require(area.getStructureNBT().equals(blueprint()), "Server blueprint changed");
        require(playerId.equals(area.getPlayerUUID()), "Server owner changed");
        require(builderId.equals(area.reservedBuilderId()), "Reserved builder changed");
        require(area.getFacing() == net.minecraft.core.Direction.SOUTH, "Native facing changed");
        for (BlockPos cell : AcceptedConstructionPlan.decode(ORIGIN, net.minecraft.core.Direction.SOUTH, 4, 5, 3, blueprint()).cells.keySet())
            require(level.getBlockState(cell).isAir(), "Fixture-only projection unexpectedly placed a world block");
    }

    private static void assertClientParity(Minecraft mc, ProtectedBuildArea area) {
        require(area.getOriginPos().equals(ORIGIN), "Network-synchronized native origin differs");
        require(area.position().distanceToSqr(Vec3.atBottomCenterOf(MARKER)) < .0001, "Client physical marker shifted");
        require(area.getStructureNBT().equals(blueprint()), "Network-synchronized blueprint differs");
        require(area.canPlayerSee(mc.player), "Actual native owner visibility denies fixture owner");
        require(mc.getEntityRenderDispatcher().getRenderer(area).getClass().getName().equals(WorkerAreaRenderer.class.getName()),
                "Protected area is not using Workers' actual native renderer");
        check("Client native renderer identity and synchronized origin/marker/blueprint parity");
    }

    private static CompoundTag blueprint() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("width", 4); tag.putInt("depth", 5); tag.putInt("height", 3); tag.putString("facing", "south");
        ListTag cells = new ListTag();
        for (int z = 0; z < 5; z++) for (int y = 0; y < 3; y++) {
            CompoundTag cell = new CompoundTag();
            cell.putInt("x", 0); cell.putInt("y", y); cell.putInt("z", z);
            cell.put("state", NbtUtils.writeBlockState(Blocks.STONE_BRICKS.defaultBlockState())); cells.add(cell);
        }
        tag.put("blocks", cells); return tag;
    }

    private static boolean worldReady(Minecraft mc) {
        return mc.level != null && mc.player != null && mc.getSingleplayerServer() != null && mc.screen == null;
    }
    private static ProtectedBuildArea clientArea(Minecraft mc) { return findClientArea(mc, areaId); }
    private static ProtectedBuildArea findClientArea(Minecraft mc, UUID id) {
        if (mc.level == null || id == null) return null;
        for (Entity entity : mc.level.entitiesForRendering())
            if (id.equals(entity.getUUID()) && entity instanceof ProtectedBuildArea area) return area;
        return null;
    }
    private static void assertInspectionControls(Minecraft mc) {
        require(mc.screen instanceof ProtectedConstructionScreen, "Protected inspection screen is absent");
        var controls = new java.util.ArrayList<Button>();
        boolean projection = false, cancel = false, close = false;
        for (var child : mc.screen.children()) if (child instanceof Button button && button.visible) {
            String text = button.getMessage().getString();
            if (text.startsWith("Projection:") || text.equals("Always shown") || text.equals("Focus only")) projection = true;
            else if (text.equals("Cancel job")) cancel = true;
            else if (text.equals("Close")) close = true;
            else continue;
            require(button.active && button.getX() >= 0 && button.getY() >= 0
                            && button.getX() + button.getWidth() <= mc.screen.width
                            && button.getY() + button.getHeight() <= mc.screen.height,
                    "Native inspection control is clipped/inactive: " + text);
            controls.add(button);
        }
        require(projection && cancel && close, "Protected inspector is missing required controls");
        for (int i = 0; i < controls.size(); i++) for (int j = i + 1; j < controls.size(); j++) {
            Button a = controls.get(i), b = controls.get(j);
            require(a.getX() + a.getWidth() <= b.getX() || b.getX() + b.getWidth() <= a.getX()
                            || a.getY() + a.getHeight() <= b.getY() || b.getY() + b.getHeight() <= a.getY(),
                    "Native inspection controls overlap");
        }
    }
    static boolean hasVisibleButton(Minecraft mc, String... labels) {
        if (mc.screen == null) return false;
        for (var child : mc.screen.children()) if (child instanceof Button button && button.visible && button.active)
            for (String label : labels) if (button.getMessage().getString().equals(label)) return true;
        return false;
    }
    static void clickVisibleButton(Minecraft mc, String... labels) {
        require(mc.screen != null, "No actual screen for navigation click");
        for (var child : mc.screen.children()) if (child instanceof Button button && button.visible && button.active)
            for (String label : labels) if (button.getMessage().getString().equals(label)) {
                double x = button.getX() + button.getWidth() / 2.0;
                double y = button.getY() + button.getHeight() / 2.0;
                require(x >= 0 && x < mc.screen.width && y >= 0 && y < mc.screen.height,
                        "Visible navigation control is outside the viewport: " + label);
                var clickedScreen = mc.screen;
                require(clickedScreen.mouseClicked(x, y, 0), "Actual navigation hitbox rejected click: " + label);
                clickedScreen.mouseReleased(x, y, 0);
                return;
            }
        throw new AssertionError("Visible actual navigation button missing: " + String.join(" / ", labels));
    }
    private static ProtectedBuildArea serverArea(Minecraft mc) {
        Entity area = mc.getSingleplayerServer().overworld().getEntity(areaId);
        require(area instanceof ProtectedBuildArea, "Saved protected native entity was not loaded");
        return (ProtectedBuildArea) area;
    }
    private static ServerPlayer player(Minecraft mc) {
        ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(playerId);
        require(player != null, "Actual server player is missing"); return player;
    }
    private static void aim(Minecraft mc, Vec3 target) {
        Vec3 delta = target.subtract(mc.player.getEyePosition());
        float yaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90);
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)));
        mc.player.setYRot(yaw); mc.player.setXRot(pitch);
        mc.player.yRotO = yaw; mc.player.xRotO = pitch;
    }
    private static String sha256(Path file) throws Exception {
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        try (var stream = Files.newInputStream(file)) {
            byte[] buffer = new byte[65536]; int size;
            while ((size = stream.read(buffer)) != -1) digest.update(buffer, 0, size);
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }
    static void captureGameplay(String name) { capture(name); }
    private static void capture(String name) {
        // Leave ordinary HUD/tooltips unmodified; move the actual test cursor out
        // of the content and allow native mouse callbacks to settle before capture.
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            long window = mc.getWindow().getWindow();
            // GLFW ignores cursor warps for an unfocused Xvfb window. Request
            // ordinary input focus first and generate two native cursor moves.
            org.lwjgl.glfw.GLFW.glfwFocusWindow(window);
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(window, 32, 32);
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(window, 2, 2);
        }
        screenshotReadyFrame = renderFrames + 3;
        screenshot = name;
    }
    private static void captureThen(String name, Runnable continuation) {
        afterScreenshot = continuation; capture(name);
    }
    private static void delay(int count) { readyAt = ticks + count; }
    private static void check(String message) { synchronized (CHECKS) { if (!CHECKS.contains(message)) CHECKS.add(message); } }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void submitServer(Minecraft mc, CheckedAction action) {
        CompletableFuture<Void> pending = new CompletableFuture<>(); serverTask = pending;
        mc.getSingleplayerServer().execute(() -> {
            try { action.run(); pending.complete(null); }
            catch (Throwable failure) { pending.completeExceptionally(failure); }
        });
    }
    private static void finish(Minecraft mc, Throwable failure) {
        if (finished) return;
        finished = true;
        if (phase >= 33) REPORT.put("gameplay", NativeBuildingGameplay.result());
        REPORT.put("status", failure == null ? "passed" : "failed");
        REPORT.put("finishedUtc", Instant.now().toString());
        REPORT.put("phase", phase); REPORT.put("renderFrames", renderFrames);
        synchronized (CHECKS) { REPORT.put("assertions", List.copyOf(CHECKS)); }
        REPORT.put("screenshots", List.copyOf(SHOTS));
        if (failure != null) {
            REPORT.put("failure", failure.toString());
            FactionLogger.LOG.error("Native Building QA failed in phase {}", phase, failure);
            if (evidence != null && mc.level != null) try (NativeImage pixels = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                pixels.writeToFile(evidence.resolve("failure-frame.png"));
                REPORT.put("failureFramebuffer", "failure-frame.png");
            } catch (Throwable captureFailure) { REPORT.put("failureFramebufferError", captureFailure.toString()); }
        }
        try {
            if (evidence != null) Files.writeString(evidence.resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));
        } catch (Exception writeFailure) { FactionLogger.LOG.error("Could not write native QA evidence", writeFailure); }
        FactionLogger.LOG.info("Native Building QA {}: {} framebuffer screenshots", failure == null ? "passed" : "failed", SHOTS.size());
        mc.stop();
    }
    @FunctionalInterface private interface CheckedAction { void run() throws Exception; }
}
