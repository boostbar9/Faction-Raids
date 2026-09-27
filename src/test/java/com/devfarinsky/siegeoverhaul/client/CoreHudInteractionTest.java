package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.core.*;
import net.minecraft.client.gui.components.Button;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises input/state without opening a GLFW window or claiming a visual playtest. */
class CoreHudInteractionTest extends MinecraftTestSupport {
    private static void set(Object target, String name, Object value) throws Exception {
        var field = CoreHireScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
    private static Object get(Object target, String name) throws Exception {
        var field = CoreHireScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
    private static void invoke(CoreHireScreen screen, String method, Class<?> type, Object value) throws Exception {
        var call = CoreHireScreen.class.getDeclaredMethod(method, type);
        call.setAccessible(true);
        call.invoke(screen, value);
    }
    @Test void intelWheelUsesScaledCoordinatesAndCannotScrollFromOutsideThePanel() throws Exception {
        var screen = mock(CoreHireScreen.class, CALLS_REAL_METHODS);
        doReturn(List.of()).when(screen).children();
        var layout = CoreHireLayout.fit(240, 180);
        set(screen, "layout", layout);
        set(screen, "tab", CoreCommandPage.INTEL);
        set(screen, "intelBodyX", 20); set(screen, "intelBodyY", 100);
        set(screen, "intelBodyW", 200); set(screen, "intelBodyH", 80);
        set(screen, "intelMaxOffset", 30); set(screen, "intelOffset", 24);
        assertTrue(screen.mouseScrolled(30 * layout.scale(), 110 * layout.scale(), -1));
        assertEquals(30, get(screen, "intelOffset"));
        assertFalse(screen.mouseScrolled(230 * layout.scale(), 110 * layout.scale(), -1));
        assertEquals(30, get(screen, "intelOffset"));
        assertTrue(screen.mouseScrolled(30 * layout.scale(), 110 * layout.scale(), 1));
        assertEquals(18, get(screen, "intelOffset"));
    }
    @Test void archiveSectionsRememberIndependentOffsetsAndStopDragging() throws Exception {
        var screen = mock(CoreHireScreen.class, CALLS_REAL_METHODS);
        set(screen, "intelOffsets", new int[3]);
        set(screen, "intelOffset", 72);
        set(screen, "intelDragging", true);
        invoke(screen, "selectIntelSection", int.class, 1);
        assertEquals(0, get(screen, "intelOffset"));
        assertEquals(false, get(screen, "intelDragging"));
        set(screen, "intelOffset", 24);
        invoke(screen, "selectIntelSection", int.class, 0);
        assertEquals(72, get(screen, "intelOffset"));
        invoke(screen, "selectIntelSection", int.class, 1);
        assertEquals(24, get(screen, "intelOffset"));
    }
    @Test void switchingPagesClearsPurchaseConfirmationAndKeepsSelectedTabVisible() throws Exception {
        var screen = mock(CoreHireScreen.class, CALLS_REAL_METHODS);
        set(screen, "layout", CoreHireLayout.fit(320, 240));
        set(screen, "tab", CoreCommandPage.LOOT);
        set(screen, "confirmBox", 2);
        set(screen, "intelDragging", true);
        Button[] buttons = new Button[CoreCommandPage.values().length];
        for (int i=0;i<buttons.length;i++) buttons[i] = mock(Button.class);
        set(screen, "pageButtons", buttons);
        set(screen, "previousPage", mock(Button.class));
        set(screen, "nextPage", mock(Button.class));
        set(screen, "hire", new Button[4]); // State refresh waits until controls exist.
        invoke(screen, "selectPage", CoreCommandPage.class, CoreCommandPage.INTEL);
        assertEquals(CoreCommandPage.INTEL, get(screen, "tab"));
        assertEquals(-1, get(screen, "confirmBox"));
        assertEquals(false, get(screen, "intelDragging"));
        assertTrue(buttons[CoreCommandPage.INTEL.ordinal()].visible);
        assertFalse(buttons[CoreCommandPage.ARMY.ordinal()].visible);
        assertSame(buttons[CoreCommandPage.INTEL.ordinal()], screen.getFocused());
    }
}
