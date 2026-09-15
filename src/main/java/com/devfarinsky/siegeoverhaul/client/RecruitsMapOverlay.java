package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.FactionLogger;
import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;

/**
 * Draws the marching enemy army on the Recruits world map.
 *
 * <p>The Recruits map is a custom screen with no marker API: it only ever draws
 * the local player, positioned as {@code offset + world * scale}. This reads
 * that same pan/zoom state reflectively and paints our own icons on top with
 * the identical transform, so an army icon sits exactly where the terrain under
 * it does and slides along as the army marches, just like the player arrow.
 *
 * <p>Everything here fails soft: if Recruits is missing, or a future version
 * renames its fields, the map simply renders without our icons.
 */
@Mod.EventBusSubscriber(modid = SiegeOverhaul.MOD_ID, value = Dist.CLIENT)
public final class RecruitsMapOverlay {

    private static final String SCREEN = "com.talhanation.recruits.client.gui.worldmap.WorldMapScreen";
    /** Half-size of a soldier icon, in pixels. Equipment draws one pixel larger. */
    private static final int SIZE = 2;
    private static final int TROOPS = 0xFFCC2222;
    private static final int EQUIPMENT = 0xFFFFAA00;
    private static final int OUTLINE = 0xFF1A1A1A;

    private static Field offsetXField;
    private static Field offsetZField;
    private static Field scaleField;
    private static boolean unavailable;

    private RecruitsMapOverlay() {}

    @SubscribeEvent
    public static void onRenderScreen(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (unavailable || screen == null || !SCREEN.equals(screen.getClass().getName())) return;
        var markers = ArmyMapMarkers.current();
        if (markers.isEmpty()) return;
        try {
            if (!bind(screen.getClass())) return;
            double offsetX = offsetXField.getDouble(screen);
            double offsetZ = offsetZField.getDouble(screen);
            double scale = scaleField.getDouble(null);
            GuiGraphics graphics = event.getGuiGraphics();
            for (var marker : markers) {
                int x = (int) Math.round(offsetX + marker.x() * scale);
                int y = (int) Math.round(offsetZ + marker.z() * scale);
                if (x < 0 || y < 0 || x > screen.width || y > screen.height) continue;
                int size = marker.equipment() ? SIZE + 1 : SIZE;
                graphics.fill(x - size - 1, y - size - 1, x + size + 1, y + size + 1, OUTLINE);
                graphics.fill(x - size, y - size, x + size, y + size,
                        marker.equipment() ? EQUIPMENT : TROOPS);
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            unavailable = true;
            FactionLogger.LOG.debug("Recruits world map markers unavailable: {}", ex.toString());
        }
    }

    private static boolean bind(Class<?> screenClass) throws ReflectiveOperationException {
        if (offsetXField != null) return true;
        Field x = screenClass.getDeclaredField("offsetX");
        Field z = screenClass.getDeclaredField("offsetZ");
        Field s = screenClass.getDeclaredField("scale");
        x.setAccessible(true);
        z.setAccessible(true);
        s.setAccessible(true);
        if (x.getType() != double.class || z.getType() != double.class || s.getType() != double.class) {
            unavailable = true;
            return false;
        }
        offsetXField = x;
        offsetZField = z;
        scaleField = s;
        return true;
    }
}
