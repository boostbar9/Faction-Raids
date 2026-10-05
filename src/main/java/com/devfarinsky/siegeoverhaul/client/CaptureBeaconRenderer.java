package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.core.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.ArrayList;
import java.util.List;

/** Depth-tested terrain boundary and read-only HUD; server snapshots own geometry, counts and eligibility. */
@Mod.EventBusSubscriber(modid = SiegeOverhaul.MOD_ID, value = Dist.CLIENT)
public final class CaptureBeaconRenderer {
    private static final ResourceLocation TEXTURE = new ResourceLocation("minecraft", "textures/entity/beacon_beam.png");
    private static final CaptureBeacon.State STATE = new CaptureBeacon.State();
    private static ClientLevel world;
    private static RaidNetwork.CaptureBeam nearest, projected;
    private static List<CaptureBoundary.Segment> boundary = List.of();
    private static CaptureStatus.Participation participation = CaptureStatus.Participation.UNAVAILABLE;
    private static int bossBottom;
    private static long projectedAt = Long.MIN_VALUE;
    private CaptureBeaconRenderer() {}
    private static void world() {
        var current = Minecraft.getInstance().level;
        if (current != world) {
            world = current;
            STATE.world(current, current == null ? null : current.dimension().location());
            nearest = null; projected = null; boundary = List.of(); projectedAt = Long.MIN_VALUE;
        }
    }
    public static void accept(RaidNetwork.CaptureBeam packet) {
        world();
        if (world != null) STATE.accept(packet, world.getGameTime());
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        world();
        var mc = Minecraft.getInstance();
        if (world == null || mc.player == null) return;
        var active = STATE.active(world.getGameTime());
        nearest = null;
        double distance = Double.MAX_VALUE;
        if (HeroVisualConfig.CAPTURE_BEAMS.get()) for (var packet : active) {
            if (!near(packet, mc.player.position()) || !corePresent(packet)) continue;
            double next = mc.player.distanceToSqr(Vec3.atCenterOf(packet.pos()));
            if (next < distance) { nearest = packet; distance = next; }
        }
        if (nearest == null) { boundary = List.of(); projected = null; return; }
        participation = local(nearest);
        long now = world.getGameTime();
        if (projected == null || !projected.pos().equals(nearest.pos()) || projected.radius() != nearest.radius()
                || projected.vertical() != nearest.vertical() || now < projectedAt
                || now - projectedAt >= CaptureBoundary.REFRESH_TICKS) {
            projected = nearest; projectedAt = now;
            boundary = CaptureBoundary.sample(nearest.pos(), nearest.radius(),
                    (x, z) -> CaptureBoundary.surface(world, projected.pos(), projected.vertical(), x, z));
        }
    }
    private static boolean near(RaidNetwork.CaptureBeam packet, Vec3 at) {
        double dx = at.x - packet.pos().getX() - .5, dz = at.z - packet.pos().getZ() - .5;
        double range = packet.radius() + CaptureBoundary.APPROACH;
        return dx * dx + dz * dz <= range * range
                && Math.abs(at.y - packet.pos().getY() - .5) <= packet.vertical() + 8;
    }
    private static boolean corePresent(RaidNetwork.CaptureBeam packet) {
        return world.hasChunkAt(packet.pos()) && world.getBlockState(packet.pos()).is(CoreBlocks.CORE.get());
    }
    private static CaptureStatus.Participation local(RaidNetwork.CaptureBeam packet) {
        var player = Minecraft.getInstance().player;
        if (player == null) return CaptureStatus.Participation.UNAVAILABLE;
        var eligibility = CaptureStatus.eligibility(player.isAlive(), player.isCreative(), player.isSpectator());
        if (eligibility != CaptureStatus.Participation.COUNTED) return eligibility;
        Vec3 at = player.position();
        double dx = at.x - packet.pos().getX() - .5, dy = at.y - packet.pos().getY() - .5, dz = at.z - packet.pos().getZ() - .5;
        boolean inside = CaptureRing.inside(dx, dy, dz, packet.radius(), packet.vertical());
        boolean loaded = !packet.requireSight() || !inside || CaptureBoundary.loadedSight(world, packet.pos(), at);
        boolean visible = !inside || !packet.requireSight() || loaded && CaptureRing.visible(world, packet.pos(), at);
        return CaptureStatus.participation(dx, dy, dz, packet.radius(), packet.vertical(),
                player.isAlive() && !player.isCreative() && !player.isSpectator(), loaded, visible);
    }
    private static CaptureStatus.Participation freshParticipation(RaidNetwork.CaptureBeam packet) {
        return world.getGameTime() - packet.time() <= 25 ? packet.participation() : CaptureStatus.Participation.UNAVAILABLE;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        world();
        var mc = Minecraft.getInstance();
        if (world == null || !HeroVisualConfig.CAPTURE_BEAMS.get() || mc.options.hideGui) return;
        var pose = event.getPoseStack(); var camera = event.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource(); boolean drew = false;
        for (var beam : STATE.active(world.getGameTime())) {
            if (beam.percent() == 0 && beam.allies() == 0) continue;
            if (camera.distanceToSqr(Vec3.atCenterOf(beam.pos())) > CaptureBeacon.RANGE * CaptureBeacon.RANGE
                    || !corePresent(beam)) continue;
            pose.pushPose();
            pose.translate(beam.pos().getX() - camera.x, beam.pos().getY() + 1 - camera.y, beam.pos().getZ() - camera.z);
            BeaconRenderer.renderBeaconBeam(pose, buffers, TEXTURE, event.getPartialTick(), 1F, world.getGameTime(),
                    0, CaptureBeacon.HEIGHT, CaptureBeacon.color(beam.percent()), .2F, .25F);
            pose.popPose(); drew = true;
        }
        if (drew) {
            buffers.endBatch(RenderType.beaconBeam(TEXTURE, false));
            buffers.endBatch(RenderType.beaconBeam(TEXTURE, true));
        }
        if (nearest == null || mc.player == null || !near(nearest, mc.player.position()) || !corePresent(nearest)) return;
        boolean counted = participation == CaptureStatus.Participation.COUNTED
                && freshParticipation(nearest) == CaptureStatus.Participation.COUNTED;
        float r = counted ? .38F : .95F, g = counted ? .9F : .74F, b = counted ? .83F : .36F;
        // Vanilla lines retain LEQUAL depth testing. No x-ray fill or global RenderSystem state changes.
        var lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose(); pose.translate(-camera.x, -camera.y, -camera.z);
        for (var segment : boundary) {
            // A chunk may unload between the bounded terrain refresh and this frame.
            if (!world.hasChunkAt(net.minecraft.core.BlockPos.containing(segment.from()))
                    || !world.hasChunkAt(net.minecraft.core.BlockPos.containing(segment.to()))) continue;
            line(pose, lines, segment.from(), segment.to(), r, g, b);
            if (segment.marker()) line(pose, lines, segment.from(), segment.from().add(0, .24, 0), r, g, b);
        }
        pose.popPose(); buffers.endBatch(RenderType.lines());
    }
    private static void line(PoseStack pose, VertexConsumer out, Vec3 a, Vec3 b, float r, float g, float blue) {
        Vec3 normal = b.subtract(a).normalize();
        for (Vec3 point : new Vec3[]{a, b}) out.vertex(pose.last().pose(), (float)point.x, (float)point.y, (float)point.z)
                .color(r, g, blue, .95F).normal(pose.last().normal(), (float)normal.x, (float)normal.y, (float)normal.z).endVertex();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beginHud(RenderGuiEvent.Pre event) { bossBottom = 0; }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void bossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        // Observe native placement only; never cancel, move or rename raid/commander bars.
        bossBottom = Math.max(bossBottom, event.getY() + 6);
    }

    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        world(); var mc = Minecraft.getInstance();
        if (world == null || nearest == null || mc.player == null || mc.screen != null || mc.options.hideGui
                || !HeroVisualConfig.CAPTURE_BEAMS.get() || !near(nearest, mc.player.position()) || !corePresent(nearest)) return;
        var local = participation;
        var server = freshParticipation(nearest);
        int color = local == CaptureStatus.Participation.COUNTED && server == CaptureStatus.Participation.COUNTED
                ? CommandPalette.ACCENT_TEAL : CommandPalette.ACCENT_GOLD;
        Vec3 at = mc.player.position();
        double distance = Math.hypot(at.x - nearest.pos().getX() - .5, at.z - nearest.pos().getZ() - .5);
        int viewportWidth = mc.getWindow().getGuiScaledWidth(), viewportHeight = mc.getWindow().getGuiScaledHeight();
        int width = Math.min(350, viewportWidth - 16);
        if (width < 100 || viewportHeight < 160) return;
        var text = new ArrayList<FormattedCharSequence>();
        List<String> rows = hudRows(nearest, local, server, distance, viewportWidth);
        for (String row : rows) text.addAll(mc.font.split(Component.literal(row), width - 14));
        var box = CaptureHudLayout.bounds(viewportWidth, viewportHeight, text.size(), bossBottom);
        if (box.width() != width) {
            width = box.width(); text.clear();
            for (String row : hudRows(nearest, local, server, distance, width))
                text.addAll(mc.font.split(Component.literal(row), width - 14));
            box = CaptureHudLayout.bounds(viewportWidth, viewportHeight, text.size(), bossBottom);
            // Keep the chosen narrow width even if the shortened wording fits above aim.
            if (box.width() != width) box = new CaptureHudLayout.Bounds(8, box.y(), width, text.size() * 10 + 18, text.size());
        }
        int height = box.height(), x = box.x(), y = box.y();
        var graphics = event.getGuiGraphics();
        CommandFrame.surface(graphics, x, y, width, height);
        graphics.fill(x + 1, y + 1, x + 3, y + height - 1, color);
        for (int i = 0; i < box.lines(); i++) graphics.drawString(mc.font, text.get(i), x + 7, y + 5 + i * 10,
                i == 0 ? CommandPalette.ACCENT_GOLD : i == 1 ? color : CommandPalette.TEXT_MUTED, false);
        int barY = y + height - 8;
        graphics.fill(x + 7, barY, x + width - 7, barY + 3, CommandPalette.PANEL_INSET);
        graphics.fill(x + 7, barY, x + 7 + (width - 14) * nearest.percent() / 100, barY + 3, color);
    }
    private static List<String> hudRows(RaidNetwork.CaptureBeam packet, CaptureStatus.Participation local,
                                        CaptureStatus.Participation server, double distance, int width) {
        if (width <= 220) {
            String location = switch (local) {
                case COUNTED -> server == CaptureStatus.Participation.COUNTED ? "Inside · counted" : "Inside · checking";
                case OUTSIDE -> "Outside · step closer";
                case HEIGHT -> "Wrong height";
                case BLOCKED -> "Blocked by wall/roof";
                case CREATIVE -> "Creative: not counted";
                case SPECTATOR -> "Spectator: not counted";
                case DEAD -> "Dead: not counted";
                default -> "Not counted yet";
            };
            String contest = switch (CaptureStatus.contest(packet.percent(), packet.allies(), packet.enemies())) {
                case ADVANCING -> "Capturing"; case TIED -> "Tied"; case OUTNUMBERED -> "Outnumbered";
                case EMPTY -> "Unheld"; case COMPLETE -> "Complete";
            };
            return List.of("Enemy core · r" + packet.radius() + " ±" + packet.vertical(), location,
                    contest + " · " + packet.percent() + "%", CaptureStatus.countsText(packet.allies(), packet.enemies()),
                    packet.requireSight() ? "Clear sight needed" : "No sight check");
        }
        if (width <= 400) return List.of(
                "ENEMY CORE · radius " + packet.radius() + " · ±" + packet.vertical() + " high",
                CaptureStatus.participationText(local, server, distance, packet.radius()),
                CaptureStatus.contestText(packet.percent(), packet.allies(), packet.enemies()) + " · " + packet.percent() + "%",
                CaptureStatus.countsText(packet.allies(), packet.enemies()) + " · " + (packet.requireSight() ? "Clear sight" : "No sight check"));
        return List.of("ENEMY CORE · " + packet.radius() + "-block radius",
                CaptureStatus.participationText(local, server, distance, packet.radius()),
                CaptureStatus.contestText(packet.percent(), packet.allies(), packet.enemies()) + " · " + packet.percent() + "%",
                CaptureStatus.countsText(packet.allies(), packet.enemies()) + " counted by server",
                "Feet within ±" + packet.vertical() + " vertically · " + (packet.requireSight() ? "Clear sight required" : "Sight not required"));
    }

}
