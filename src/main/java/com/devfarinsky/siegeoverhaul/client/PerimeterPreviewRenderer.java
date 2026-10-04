package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import com.devfarinsky.siegeoverhaul.core.PerimeterPreview;
import com.devfarinsky.siegeoverhaul.core.TerritoryFortification;
import com.devfarinsky.siegeoverhaul.items.PerimeterPlanItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.List;

/** Read-only held-plan outline. Accepted jobs continue to use the real Workers 2 renderer and shovel. */
@Mod.EventBusSubscriber(modid = SiegeOverhaul.MOD_ID, value = Dist.CLIENT)
public final class PerimeterPreviewRenderer {
    private static PerimeterPreview.Selection selection;
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private static ItemStack previous = ItemStack.EMPTY;
    private static long updatedAt = Long.MIN_VALUE;
    private PerimeterPreviewRenderer() {}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (world != mc.level) { world = mc.level; selection = null; previous = ItemStack.EMPTY; updatedAt = Long.MIN_VALUE; }
        if (mc.level == null || mc.player == null) { selection = null; return; }
        ItemStack held = mc.player.getMainHandItem();
        if (!(held.getItem() instanceof PerimeterPlanItem)) held = mc.player.getOffhandItem();
        if (!(held.getItem() instanceof PerimeterPlanItem)) { selection = null; previous = ItemStack.EMPTY; return; }
        long now = mc.level.getGameTime();
        if (held != previous || updatedAt == Long.MIN_VALUE || now < updatedAt || now - updatedAt >= 5) {
            selection = PerimeterPreview.read(held, mc.player.getUUID(), world.dimension().location(), now);
            previous = held; updatedAt = now;
        }
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        var mc = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || selection == null
                || mc.level != world || mc.options.hideGui) return;
        var camera = event.getCamera().getPosition(); var pose = event.getPoseStack();
        var buffers = mc.renderBuffers().bufferSource(); var lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose(); pose.translate(-camera.x, -camera.y, -camera.z);
        for (var box : selection.boxes()) {
            AABB bounds = new AABB(box.min(), box.max().offset(1, 1, 1));
            if (bounds.maxX < camera.x - 128 || bounds.minX > camera.x + 128
                    || bounds.maxZ < camera.z - 128 || bounds.minZ > camera.z + 128) continue;
            float r = selection.ready() ? box.material() == 1 ? .95F : .3F : 1F;
            float g = selection.ready() ? .85F : .6F;
            float b = selection.ready() ? box.material() == 1 ? .4F : .95F : .2F;
            LevelRenderer.renderLineBox(pose, lines, bounds.deflate(.01), r, g, b, .8F);
        }
        pose.popPose(); buffers.endBatch(RenderType.lines());
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (selection == null || mc.level != world || mc.screen != null || mc.options.hideGui) return;
        if (mc.player == null || !PlanReviewHud.shouldDisplay(
                mc.player.getMainHandItem().getItem() instanceof PerimeterPlanItem,
                mc.player.getMainHandItem().getItem() instanceof com.devfarinsky.siegeoverhaul.items.DefensePlanItem)) return;
        PlanReviewHud.render(event.getGuiGraphics(), mc.font,
                mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(),
                "PERIMETER REVIEW · " + TerritoryFortification.PRICE + " faction Treasury emeralds",
                selection.ready() ? "Use: confirm · Sneak-use: cancel · No charge until accepted" : selection.problem(),
                selection.ready(), List.of(selection.materials(), selection.ready()
                        ? "Exact grouped outline within 128 blocks. The server rechecks before building."
                        : "Blocked preview. Fix the problem, then use the plan to refresh. No payment taken."));
    }
}
