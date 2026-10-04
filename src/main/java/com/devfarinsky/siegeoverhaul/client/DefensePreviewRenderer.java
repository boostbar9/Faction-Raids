package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import com.devfarinsky.siegeoverhaul.core.DefenseBlueprint;
import com.devfarinsky.siegeoverhaul.core.DefensePreview;
import com.devfarinsky.siegeoverhaul.items.DefensePlanItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

/** Held-plan outline only. Server checks remain authoritative and run again on confirmation. */
@Mod.EventBusSubscriber(modid = SiegeOverhaul.MOD_ID, value = Dist.CLIENT)
public final class DefensePreviewRenderer {
    private static ClientLevel world;
    private static DefenseBlueprint.Plan plan;
    private static DefenseBlueprint.Kind kind;
    private static DefensePreview.Selection selection;
    private static final Set<Long> blocked = new HashSet<>();
    private static final Set<Long> badGround = new HashSet<>();
    private DefensePreviewRenderer() {}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (world != mc.level) { world = mc.level; plan = null; selection = null; }
        if (world == null || mc.player == null) { plan = null; selection = null; return; }
        ItemStack held = mc.player.getMainHandItem();
        if (!(held.getItem() instanceof DefensePlanItem)) held = mc.player.getOffhandItem();
        if (!(held.getItem() instanceof DefensePlanItem item)) { selection = null; plan = null; return; }
        var next = DefensePreview.read(held, item.kind(), world.dimension().location(), mc.player.getUUID(), world.getGameTime());
        if (next == null || mc.player.distanceToSqr(next.origin().getX() + .5, next.origin().getY(), next.origin().getZ() + .5)
                > DefensePreview.RANGE * DefensePreview.RANGE) { selection = null; plan = null; return; }
        boolean changed = plan == null || kind != item.kind() || selection == null
                || !selection.origin().equals(next.origin()) || selection.facing() != next.facing();
        kind = item.kind(); selection = next;
        if (changed) plan = DefenseBlueprint.create(kind, next.origin(), next.facing());
        if (!changed && world.getGameTime() % 5 != 0) return;
        blocked.clear(); badGround.clear();
        for (BlockPos base : plan.footprint()) {
            if (!world.hasChunkAt(base) || !world.getWorldBorder().isWithinBounds(base)) { badGround.add(base.asLong()); continue; }
            var floor = world.getBlockState(base.below());
            if (!floor.getFluidState().isEmpty() || floor.hasBlockEntity() || !floor.isFaceSturdy(world, base.below(), Direction.UP))
                badGround.add(base.asLong());
            for (int y = base.getY(); y <= plan.max().getY(); y++) {
                var pos = new BlockPos(base.getX(), y, base.getZ()); var state = world.getBlockState(pos);
                if (!state.getFluidState().isEmpty() || state.hasBlockEntity() || (!state.isAir() && !state.canBeReplaced()))
                    blocked.add(pos.asLong());
            }
        }
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        var mc = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || plan == null || selection == null
                || mc.level != world || mc.options.hideGui) return;
        var camera = event.getCamera().getPosition(); var pose = event.getPoseStack();
        var buffers = mc.renderBuffers().bufferSource(); var lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose(); pose.translate(-camera.x, -camera.y, -camera.z);
        for (long cell : plan.blocks().keySet()) {
            boolean red = blocked.contains(cell);
            float r = red ? 1F : selection.ready() ? .2F : 1F;
            float g = red ? .18F : .85F;
            float b = red ? .12F : selection.ready() ? 1F : .2F;
            LevelRenderer.renderLineBox(pose, lines, new AABB(BlockPos.of(cell)).deflate(.01), r, g, b, .65F);
        }
        for (long cell : blocked) if (!plan.blocks().containsKey(cell))
            LevelRenderer.renderLineBox(pose, lines, new AABB(BlockPos.of(cell)).deflate(.01), 1F, .18F, .12F, .8F);
        for (long cell : badGround)
            LevelRenderer.renderLineBox(pose, lines, new AABB(BlockPos.of(cell).below()), 1F, .18F, .12F, .8F);
        var anchor = selection.origin();
        LevelRenderer.renderLineBox(pose, lines, new AABB(anchor.getX(), anchor.getY() + .01, anchor.getZ(),
                anchor.getX() + 1, anchor.getY() + .06, anchor.getZ() + 1), 1F, 1F, .2F, 1F);
        pose.popPose(); buffers.endBatch(RenderType.lines());
    }

    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (plan == null || selection == null || mc.level != world || mc.screen != null || mc.options.hideGui) return;
        if (mc.player == null || !PlanReviewHud.shouldDisplay(
                mc.player.getMainHandItem().getItem() instanceof DefensePlanItem,
                mc.player.getMainHandItem().getItem() instanceof com.devfarinsky.siegeoverhaul.items.PerimeterPlanItem)) return;
        PlanReviewHud.render(event.getGuiGraphics(), mc.font,
                mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(),
                kind.label + " · " + kind.price + " Treasury emeralds",
                selection.ready() ? "Site ready. Confirmation checks it again." : selection.problem(), selection.ready(),
                List.of("Use yellow anchor: confirm · Sneak-use: rotate · Use air: cancel", plan.materials()));
    }
}
