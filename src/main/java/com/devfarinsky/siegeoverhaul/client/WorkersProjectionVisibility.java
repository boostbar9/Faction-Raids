package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.compat.WorkersProjectionView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Keep native anchored projections visible when their shovel marker leaves the camera. */
@Mod.EventBusSubscriber(modid = SiegeOverhaul.MOD_ID, value = Dist.CLIENT)
public final class WorkersProjectionVisibility {
    private static final ProjectionCulling CULLING = new ProjectionCulling();
    private static ClientLevel world;

    private WorkersProjectionVisibility() {}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var level = Minecraft.getInstance().level;
        if (world != level) {
            CULLING.clear();
            world = level;
        }
    }

    @SubscribeEvent public static void beforeEntities(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            CULLING.clear();
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) return;
        // Scope the override to this frame's native entity-render pass. Also
        // clean up an interrupted previous frame before reading new settings.
        CULLING.clear();
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        world = mc.level;
        for (Entity entity : world.entitiesForRendering()) {
            if (!entity.isAlive() || !WorkersBridge.isBuildArea(entity)) continue;
            var bounds = WorkersProjectionView.visibleBounds(entity, mc.player);
            // The native renderer is still responsible for positions, access,
            // distance limits and drawing. Only its marker-sized frustum test
            // is bypassed, and only while the actual projection is on screen.
            CULLING.update(entity, bounds, event.getFrustum()::isVisible);
        }
    }
}
