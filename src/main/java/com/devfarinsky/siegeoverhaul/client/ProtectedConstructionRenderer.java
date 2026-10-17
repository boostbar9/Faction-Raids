package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import com.devfarinsky.siegeoverhaul.nativecompat.ProtectedConstructionAreas;
import com.talhanation.workers.client.render.WorkerAreaRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Register Workers' actual renderer; no copied shovel or blueprint drawing pipeline. */
@Mod.EventBusSubscriber(modid = SiegeOverhaul.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ProtectedConstructionRenderer {
    private ProtectedConstructionRenderer() {}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ProtectedConstructionAreas.TYPE.get(), WorkerAreaRenderer::new);
        event.registerEntityRenderer(ProtectedConstructionAreas.EARTHWORKS_TYPE.get(), WorkerAreaRenderer::new);
    }
}
