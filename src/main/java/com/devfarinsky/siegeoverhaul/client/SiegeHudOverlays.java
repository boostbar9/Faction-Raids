package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Siege Overhaul HUD cards as real GUI overlays instead of RenderGuiEvent.Post, so
 * vanilla titles, chat and the TAB list draw on top of them rather than underneath.
 */
@Mod.EventBusSubscriber(modid = SiegeOverhaul.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SiegeHudOverlays {
    private SiegeHudOverlays() {}

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerBelow(VanillaGuiOverlay.TITLE_TEXT.id(), "capture_card",
                (gui, graphics, partialTick, width, height) -> CaptureBeaconRenderer.drawCard(graphics));
        event.registerBelow(VanillaGuiOverlay.TITLE_TEXT.id(), "reward_popup",
                (gui, graphics, partialTick, width, height) -> RewardPopups.draw(graphics));
        event.registerBelow(VanillaGuiOverlay.CHAT_PANEL.id(), "plan_review", (gui, graphics, partialTick, width, height) -> {
            DefensePreviewRenderer.drawCard(graphics);
            PerimeterPreviewRenderer.drawCard(graphics);
        });
    }
}
