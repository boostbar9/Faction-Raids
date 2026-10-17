package com.devfarinsky.siegeoverhaul.naval;

import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Actual renderer, synchronization and integrated-server disk reload in a fresh QA world. */
@Mod.EventBusSubscriber(modid = SiegeOverhaul.MOD_ID, value = Dist.CLIENT)
public final class NativeSmallShipsClientQa {
    private static final boolean ENABLED = Boolean.getBoolean("siegeoverhaul.nativeShipsClientQa");
    private static final String WORLD = "siege-native-ships-client-fixture";
    private static final Map<String, Object> REPORT = new LinkedHashMap<>();
    private static Path directory, evidence;
    private static NativeSmallShipsProbe probe;
    private static List<NativeSmallShipsProbe.CrewReceipt> receipts;
    private static CompletableFuture<Void> task;
    private static int phase, clientTicks, readyAt;
    private static long started;
    private static boolean done;
    private static String screenshot;
    private static UUID player;

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || done || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            clientTicks++;
            if (started == 0) started = System.nanoTime();
            require(System.nanoTime() - started < 8L * 60 * 1_000_000_000, "Client fixture timeout, phase " + phase);
            if (task != null) {
                if (!task.isDone()) return;
                task.join(); task = null; readyAt = clientTicks + 40;
            }
            if (clientTicks < readyAt || screenshot != null) return;
            switch (phase) {
                case 0 -> {
                    if (!(mc.screen instanceof TitleScreen)) return;
                    directory = Path.of(System.getProperty("siegeoverhaul.nativeShipsQa.directory")).toRealPath();
                    require(directory.equals(Path.of("").toRealPath()) && directory.endsWith(Path.of("build", "native-ships-final-client-qa", "client")),
                            "Unsafe native ship client directory");
                    evidence = directory.resolveSibling("evidence"); Files.createDirectories(evidence);
                    require(!Files.exists(evidence.resolve("client-result.json")) && !Files.exists(directory.resolve("saves").resolve(WORLD)),
                            "Refusing existing client evidence or world");
                    REPORT.put("status", "running");
                    REPORT.put("scope", "Real native client rendering, passenger-seat synchronization and integrated-server disk reload; initialized disposable scene, no waypoint/combat/multiplayer authentication claim");
                    mc.options.pauseOnLostFocus = false;
                    mc.options.renderDistance().set(4); mc.options.simulationDistance().set(5); mc.options.guiScale().set(2);
                    GameRules rules = new GameRules();
                    rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                    rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                    rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
                    phase = 1;
                    mc.createWorldOpenFlows().createFreshLevel(WORLD,
                            new LevelSettings(WORLD, GameType.SPECTATOR, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                            new WorldOptions(20261005L, false, false), access ->
                                    access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions());
                }
                case 1 -> {
                    if (!ready(mc)) return;
                    player = mc.player.getUUID();
                    submit(mc, () -> {
                        ServerLevel level = mc.getSingleplayerServer().overworld();
                        require(level.getServer().getWorldPath(LevelResource.ROOT).toRealPath().equals(directory.resolve("saves").resolve(WORLD).toRealPath()), "Wrong QA world");
                        probe = new NativeSmallShipsProbe(level, new BlockPos(24, 65, 24)); probe.prepare();
                        receipts = probe.receipts();
                        var observer = level.getServer().getPlayerList().getPlayer(player);
                        require(observer != null, "Real client player missing");
                        observer.setGameMode(GameType.SPECTATOR);
                        observer.connection.teleport(24.5, 76, -3.5, 0, 21);
                    }); phase = 2;
                }
                case 2 -> {
                    if (!ready(mc)) return;
                    submit(mc, () -> probe.inspect()); phase = 3;
                }
                case 3 -> {
                    if (!ready(mc) || mc.screen != null) return;
                    verifyClient(mc, "beforeReload");
                    screenshot = "01-native-ships-before-reload.png"; phase = 4;
                }
                case 4 -> {
                    // Actual disconnect closes and saves the integrated server; no synthetic entity reload.
                    mc.level.disconnect(); mc.clearLevel(); mc.setScreen(new TitleScreen());
                    phase = 5; readyAt = clientTicks + 30;
                }
                case 5 -> {
                    if (mc.getSingleplayerServer() != null || !(mc.screen instanceof TitleScreen)) return;
                    mc.createWorldOpenFlows().loadLevel(new TitleScreen(), WORLD); phase = 6;
                }
                case 6 -> {
                    if (!ready(mc)) return;
                    submit(mc, () -> {
                        ServerLevel level = mc.getSingleplayerServer().overworld();
                        for (var receipt : receipts) verifyEntity(level.getEntity(receipt.ship()), receipt);
                        REPORT.put("actualIntegratedServerDiskReload", true);
                        REPORT.put("serverWorldAfterReload", level.getServer().getWorldPath(LevelResource.ROOT).getFileName().toString());
                    }); phase = 7;
                }
                case 7 -> {
                    if (!ready(mc) || mc.screen != null) return;
                    verifyClient(mc, "afterReload");
                    screenshot = "02-native-ships-after-reload.png"; phase = 8;
                }
                case 8 -> finish(mc, null);
                default -> throw new IllegalStateException("Unexpected client phase");
            }
        } catch (Throwable failure) { finish(mc, failure); }
    }

    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent event) {
        if (!ENABLED || done || screenshot == null || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            require(image.getWidth() >= 960 && image.getHeight() >= 540, "Insufficient actual framebuffer");
            image.writeToFile(evidence.resolve(screenshot));
            REPORT.put(screenshot, Map.of("width", image.getWidth(), "height", image.getHeight(), "nativeFramebuffer", true));
            screenshot = null; readyAt = clientTicks + 15;
        } catch (Throwable failure) { finish(mc, failure); }
    }

    private static void verifyClient(Minecraft mc, String key) throws Exception {
        List<Map<String, Object>> found = new ArrayList<>();
        for (var receipt : receipts) {
            Entity ship = null;
            for (Entity entity : mc.level.entitiesForRendering()) if (entity.getUUID().equals(receipt.ship())) { ship = entity; break; }
            verifyEntity(ship, receipt);
            found.add(Map.of("ship", receipt.ship().toString(), "hull", ship.getType().toString(), "passengers", ship.getPassengers().size(), "captainSeat", "DRIVER"));
        }
        REPORT.put(key, found);
    }

    private static void verifyEntity(Entity ship, NativeSmallShipsProbe.CrewReceipt receipt) throws Exception {
        require(ship != null && ship.isAlive(), "Native saved/synced ship missing " + receipt.ship());
        require(ship.getPassengers().size() == 2, "Native saved/synced crew count mismatch");
        Entity captain = ship.getPassengers().stream().filter(e -> e.getUUID().equals(receipt.captain())).findFirst().orElseThrow();
        require(ship.getPassengers().stream().anyMatch(e -> e.getUUID().equals(receipt.recruit())), "Native recruit identity lost");
        Object seat = ship.getClass().getMethod("getSeatOf", Entity.class).invoke(ship, captain);
        require(seat != null && "DRIVER".equals(String.valueOf(seat.getClass().getMethod("type").invoke(seat))), "Native saved/synced helm assignment lost");
    }
    private static boolean ready(Minecraft mc) { return mc.player != null && mc.level != null && mc.getSingleplayerServer() != null; }
    @FunctionalInterface private interface Checked { void run() throws Exception; }
    private static void submit(Minecraft mc, Checked operation) {
        task = CompletableFuture.runAsync(() -> { try { operation.run(); } catch (Exception failure) { throw new IllegalStateException(failure); } }, mc.getSingleplayerServer());
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static void finish(Minecraft mc, Throwable failure) {
        if (done) return;
        done = true;
        REPORT.put("status", failure == null ? "passed" : "failed");
        if (failure != null) { REPORT.put("failure", failure.toString()); failure.printStackTrace(); }
        try { Files.writeString(evidence.resolve("client-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(REPORT) + "\n"); }
        catch (Exception reporting) { reporting.printStackTrace(); }
        System.out.println("SIEGE_NATIVE_SHIPS_CLIENT_" + (failure == null ? "COMPLETED" : "FAILED"));
        mc.stop();
    }
}
