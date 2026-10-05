package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.RaidConfig;
import com.devfarinsky.siegeoverhaul.RaidNetwork;
import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import com.devfarinsky.siegeoverhaul.client.CaptureBeaconRenderer;
import com.devfarinsky.siegeoverhaul.core.*;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Unshipped, explicitly seeded capture display fixture. It creates no raid, claim, purchase or capture victory. */
@Mod.EventBusSubscriber(modid = SiegeOverhaul.MOD_ID, value = Dist.CLIENT)
public final class NativeCaptureBoundaryQa {
    private static final boolean ENABLED = Boolean.getBoolean("siegeoverhaul.nativeQa")
            && "hud".equals(System.getProperty("siegeoverhaul.nativeQa.mode"));
    @FunctionalInterface interface Action { void run() throws Exception; }
    record Step(String name, Action action) {}
    private static volatile ServerPlayer owner;
    private static volatile BlockPos core;
    private static volatile boolean sending, active;
    private static volatile int progress, allies, enemies;
    private static CompletableFuture<Void> pending;
    private static long started, settleAt = -1;
    private static int settleTicks;
    private static net.minecraft.resources.ResourceKey<Level> expectedDimension = Level.OVERWORLD;
    private static String scenario;
    private static int originalRadius, originalVertical;
    private static boolean originalSight;
    private NativeCaptureBoundaryQa() {}
    private static Minecraft mc() { return Minecraft.getInstance(); }
    private static void require(boolean condition, String text) { if (!condition) throw new AssertionError(text); }

    static List<Step> steps() {
        List<Step> result = new ArrayList<>();
        result.add(new Step("seed isolated capture terrain", () -> {
            require(ENABLED && mc().getSingleplayerServer() != null, "Capture fixture requires the isolated integrated HUD world");
            mc().setScreen(null); mc().mouseHandler.releaseMouse();
            mc().options.setCameraType(CameraType.THIRD_PERSON_BACK); mc().options.hideGui = false;
            submit(() -> {
                owner = mc().getSingleplayerServer().getPlayerList().getPlayer(mc().player.getUUID());
                require(owner != null, "Capture fixture owner is missing");
                originalRadius = RaidConfig.CORE_CAPTURE_RADIUS.get(); originalVertical = RaidConfig.CORE_CAPTURE_VERTICAL.get();
                originalSight = RaidConfig.CORE_CAPTURE_REQUIRE_SIGHT.get();
                core = owner.blockPosition().offset(24, 0, 0);
                ServerLevel level = owner.serverLevel();
                // Deliberately seeded miniature plaza, not an enemy town produced by native AI.
                for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) {
                    BlockPos floor = core.offset(x, -1, z);
                    require(level.hasChunkAt(floor), "Capture fixture refuses an unloaded terrain column");
                    level.setBlock(floor, Blocks.STONE_BRICKS.defaultBlockState(), 3);
                    for (int y = 0; y <= 6; y++) level.setBlock(core.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                    // A real one-block step and a three-block drop exercise interrupted projection.
                    if (x >= 6) level.setBlock(core.offset(x, 0, z), Blocks.STONE_BRICKS.defaultBlockState(), 3);
                    if (x <= -4 && z >= 3) for (int y = -1; y >= -4; y--)
                        level.setBlock(core.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
                for (int x : new int[]{-2, 2}) for (int z : new int[]{-2, 2}) {
                    for (int y = 0; y < 3; y++) level.setBlock(core.offset(x, y, z), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 3);
                    level.setBlock(core.offset(x, 3, z), Blocks.SHROOMLIGHT.defaultBlockState(), 3);
                }
                level.setBlock(core, CoreBlocks.CORE.get().defaultBlockState(), 3);
                level.setDayTime(6000); active = true;
            }, 30, Level.OVERWORLD);
        }));
        for (String prefix : List.of("compact-scale3", "roomy-scale1")) {
            result.add(new Step(prefix + " capture viewport", () -> NativeHudQa.requestCaptureViewport(
                    prefix.startsWith("compact") ? 960 : 1440, prefix.startsWith("compact") ? 720 : 960,
                    prefix.endsWith("3") ? 3 : 1)));
            for (String name : List.of("idle", "inside", "wall", "height", "low-ceiling", "custom", "creative", "tied")) {
                result.add(new Step(prefix + " capture setup " + name, () -> scene(name)));
                result.add(new Step(prefix + " capture frame " + name, () -> {
                    scenario = name; evidence(); NativeHudQa.captureWorld(prefix + "-capture-" + name,
                            "QA SEEDED CAPTURE: actual S2C snapshot/renderer and native terrain; seeded counts, no live raid");
                }));
            }
            result.add(new Step(prefix + " capture stop snapshots", () -> {
                sending = false; scenario = "waiting";
                submit(() -> {}, 32, Level.OVERWORLD);
            }));
            result.add(new Step(prefix + " capture waiting frame", () -> {
                evidence(); NativeHudQa.captureWorld(prefix + "-capture-waiting", "QA SEEDED CAPTURE: stale server participation; awaiting a new snapshot");
            }));
            result.add(new Step(prefix + " capture expire snapshots", () -> {
                scenario = "expired"; submit(() -> {}, 65, Level.OVERWORLD);
            }));
            result.add(new Step(prefix + " capture expired frame", () -> {
                evidence(); NativeHudQa.captureWorld(prefix + "-capture-expired", "QA SEEDED CAPTURE: interrupted snapshots expired from actual renderer cache");
            }));
            result.add(new Step(prefix + " capture restore before dimension change", () -> scene("inside")));
            result.add(new Step(prefix + " capture actual dimension change", () -> {
                sending = false; scenario = "dimension-cleared";
                submit(() -> {
                    owner.setGameMode(GameType.SPECTATOR);
                    owner.teleportTo(owner.server.getLevel(Level.NETHER), .5, 90, .5, 180, 20);
                }, 25, Level.NETHER);
            }));
            result.add(new Step(prefix + " capture dimension frame", () -> {
                evidence(); NativeHudQa.captureWorld(prefix + "-capture-dimension-cleared", "QA SEEDED CAPTURE: actual Nether transition cleared the Overworld cache");
            }));
        }
        result.add(new Step("restore capture fixture preferences", () -> {
            sending = false; active = false;
            submit(() -> {
                RaidConfig.CORE_CAPTURE_RADIUS.set(originalRadius); RaidConfig.CORE_CAPTURE_VERTICAL.set(originalVertical);
                RaidConfig.CORE_CAPTURE_REQUIRE_SIGHT.set(originalSight);
                owner.setGameMode(GameType.SURVIVAL);
                owner.teleportTo(owner.server.overworld(), core.getX() + .5, core.getY(), core.getZ() + 8.5, 180, 20);
            }, 25, Level.OVERWORLD);
            mc().options.setCameraType(CameraType.FIRST_PERSON);
        }));
        return List.copyOf(result);
    }

    private static void scene(String name) {
        sending = false; scenario = name;
        submit(() -> {
            var level = owner.server.overworld();
            RaidConfig.CORE_CAPTURE_RADIUS.set(name.equals("custom") ? 9 : 6);
            RaidConfig.CORE_CAPTURE_VERTICAL.set(name.equals("custom") ? 4 : 2);
            RaidConfig.CORE_CAPTURE_REQUIRE_SIGHT.set(!name.equals("custom"));
            // Only a tiny central wall/roof fixture is changed between observations.
            for (int x = -1; x <= 1; x++) for (int y = 0; y <= 3; y++)
                level.setBlock(core.offset(x, y, 2), name.equals("wall") ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
            for (int x = -3; x <= 3; x++) for (int z = 3; z <= 8; z++)
                level.setBlock(core.offset(x, 2, z), (name.equals("height") || name.equals("low-ceiling")) ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
            double y = core.getY() + (name.equals("height") ? 3 : 0);
            double z = core.getZ() + (name.equals("idle") || name.equals("custom") ? 8.5 : 4.5);
            owner.setGameMode(name.equals("creative") ? GameType.CREATIVE : GameType.SURVIVAL);
            owner.teleportTo(level, core.getX() + .5, y, z, 180, 20);
            progress = name.equals("idle") ? 0 : name.equals("tied") ? 1200 : 1008;
            allies = name.equals("idle") ? 0 : 2; enemies = name.equals("idle") ? 0 : name.equals("tied") ? 2 : 1;
            sending = true;
            CaptureBeacon.send(level, SiegeCore.key(owner), core, progress, 2400, allies, enemies);
        }, 25, Level.OVERWORLD);
    }

    private static void submit(Runnable action, int waitTicks, net.minecraft.resources.ResourceKey<Level> dimension) {
        require(pending == null, "Overlapping capture fixture server operations");
        settleAt = -1; settleTicks = waitTicks; expectedDimension = dimension; started = System.nanoTime();
        pending = new CompletableFuture<>(); var task = pending;
        mc().getSingleplayerServer().execute(() -> {
            try { action.run(); task.complete(null); }
            catch (Throwable failure) { task.completeExceptionally(failure); }
        });
    }
    /** NativeHudQa pauses its step cursor until this real server action and client world settle. */
    static boolean ready() {
        if (pending == null) return true;
        require(System.nanoTime() - started < 25_000_000_000L, "Capture fixture transition timed out: " + scenario);
        if (!pending.isDone()) return false;
        pending.join();
        if (mc().level == null || mc().player == null || mc().screen != null || !mc().level.dimension().equals(expectedDimension)) return false;
        if (settleAt < 0) settleAt = mc().level.getGameTime() + settleTicks;
        if (mc().level.getGameTime() < settleAt) return false;
        mc().mouseHandler.releaseMouse(); pending = null; return true;
    }
    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent event) {
        if (!ENABLED || !active || !sending || owner == null || core == null || event.phase != TickEvent.Phase.END) return;
        ServerLevel level = owner.serverLevel();
        if (level.dimension().equals(Level.OVERWORLD) && level.getGameTime() % 20 == 0)
            CaptureBeacon.send(level, SiegeCore.key(owner), core, progress, 2400, allies, enemies);
    }
    @SubscribeEvent public static void label(RenderGuiEvent.Post event) {
        if (!ENABLED || !active || mc().screen != null) return;
        var font = mc().font; int y = mc().getWindow().getGuiScaledHeight() - 43;
        for (var line : font.split(Component.literal("QA SEEDED CAPTURE · Cosmetic counts; no live raid"), mc().getWindow().getGuiScaledWidth() - 12)) {
            event.getGuiGraphics().drawString(font, line, 6, y, 0xffffcc66, true); y += 10;
        }
    }
    private static Object field(String name) throws Exception {
        var field = CaptureBeaconRenderer.class.getDeclaredField(name); field.setAccessible(true); return field.get(null);
    }
    /** Read-only reflection observes production state; never assigns renderer fields or alters packets in transit. */
    static Map<String, Object> evidence() throws Exception {
        var packet = (RaidNetwork.CaptureBeam)field("nearest");
        @SuppressWarnings("unchecked") var segments = (List<CaptureBoundary.Segment>)field("boundary");
        var state = (CaptureBeacon.State)field("STATE");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scenario", scenario); result.put("seeded", true); result.put("liveRaid", false);
        result.put("dimension", mc().level.dimension().location().toString());
        result.put("segments", segments.size()); result.put("activeSnapshots", state.active(mc().level.getGameTime()).size());
        if (scenario.equals("expired") || scenario.equals("dimension-cleared")) {
            require(packet == null && segments.isEmpty() && state.active(mc().level.getGameTime()).isEmpty(), "Stale capture visuals survived " + scenario);
            result.put("cleared", true); return result;
        }
        require(packet != null && packet.pos().equals(core), "Actual S2C capture snapshot was not selected");
        var local = (CaptureStatus.Participation)field("participation");
        var method = CaptureBeaconRenderer.class.getDeclaredMethod("freshParticipation", RaidNetwork.CaptureBeam.class);
        method.setAccessible(true);
        var fresh = (CaptureStatus.Participation)method.invoke(null, packet);
        var expected = switch (scenario) {
            case "idle" -> CaptureStatus.Participation.OUTSIDE;
            case "wall" -> CaptureStatus.Participation.BLOCKED;
            case "height" -> CaptureStatus.Participation.HEIGHT;
            case "creative" -> CaptureStatus.Participation.CREATIVE;
            default -> CaptureStatus.Participation.COUNTED;
        };
        require(local == expected, "Unexpected native local eligibility: " + scenario + " -> " + local);
        require(packet.participation() == expected, "Server snapshot disagrees with actual native viewer: " + packet.participation());
        require(scenario.equals("waiting") ? fresh == CaptureStatus.Participation.UNAVAILABLE : fresh == expected,
                "Ring/HUD freshness mismatch: " + scenario + " -> " + fresh);
        require(!segments.isEmpty() && segments.size() < CaptureBoundary.SEGMENTS, "Native terrain boundary missing, unbounded or bridging the seeded cliff");
        int radius = scenario.equals("custom") ? 9 : 6;
        require(packet.radius() == radius && packet.vertical() == (scenario.equals("custom") ? 4 : 2), "Client ignored server cylinder geometry");
        for (var segment : segments) {
            require(Math.abs(Math.hypot(segment.from().x - core.getX() - .5, segment.from().z - core.getZ() - .5) - radius) < .000001,
                    "Rendered vertex is off the authoritative capture radius");
            require(Math.abs(segment.from().y - .045 - core.getY() - .5) <= packet.vertical(), "Ring projected onto an out-of-height roof");
            require(Math.abs(segment.from().y - segment.to().y) <= 1.05, "Ring bridged the terrain cliff");
        }
        var rowsMethod = CaptureBeaconRenderer.class.getDeclaredMethod("hudRows", RaidNetwork.CaptureBeam.class,
                CaptureStatus.Participation.class, CaptureStatus.Participation.class, double.class, int.class);
        rowsMethod.setAccessible(true);
        int viewportWidth = mc().getWindow().getGuiScaledWidth(), viewportHeight = mc().getWindow().getGuiScaledHeight();
        @SuppressWarnings("unchecked") var rows = (List<String>)rowsMethod.invoke(null, packet, local, fresh,
                Math.hypot(mc().player.getX() - core.getX() - .5, mc().player.getZ() - core.getZ() - .5), viewportWidth);
        int textWidth = Math.min(350, viewportWidth - 16) - 14;
        int lines = rows.stream().mapToInt(row -> mc().font.split(Component.literal(row), textWidth).size()).sum();
        var hud = com.devfarinsky.siegeoverhaul.client.CaptureHudLayout.bounds(viewportWidth, viewportHeight, lines);
        require(hud.lines() == lines, "Capture HUD truncated required status at native font scale");
        require(hud.y() + hud.height() <= viewportHeight / 2 - 18, "Capture HUD obstructed the aiming reticle");
        result.put("hud", Map.of("x", hud.x(), "y", hud.y(), "width", hud.width(), "height", hud.height(), "lines", lines));
        result.put("reticleClear", true);
        result.put("radius", packet.radius()); result.put("vertical", packet.vertical()); result.put("requireSight", packet.requireSight());
        result.put("percent", packet.percent()); result.put("allies", packet.allies()); result.put("enemies", packet.enemies());
        result.put("local", local.name()); result.put("server", packet.participation().name()); result.put("fresh", fresh.name());
        result.put("status", CaptureStatus.contest(packet.percent(), packet.allies(), packet.enemies()).name());
        result.put("geometryMatches", true); return result;
    }
}
