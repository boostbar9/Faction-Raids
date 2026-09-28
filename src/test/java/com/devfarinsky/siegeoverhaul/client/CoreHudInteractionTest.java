package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.core.*;
import net.minecraft.client.gui.components.Button;
import org.junit.jupiter.api.Test;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
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
    @Test void hiddenNavigationControlsCannotAnnounceTooltips() {
        var button = mock(Button.class);
        when(button.isMouseOver(12, 34)).thenReturn(true);
        button.visible = false;
        assertFalse(CoreHireScreen.visibleHover(button, 12, 34));
        button.visible = true;
        assertTrue(CoreHireScreen.visibleHover(button, 12, 34));
    }
    @Test void intelWheelUsesScaledCoordinatesAndCannotScrollFromOutsideThePanel() throws Exception {
        var screen = new CoreHireScreen(mock(CoreHireMenu.class), mock(Inventory.class), Component.literal("Command"));
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
        var screen = new CoreHireScreen(mock(CoreHireMenu.class), mock(Inventory.class), Component.literal("Command"));
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
        var screen = new CoreHireScreen(mock(CoreHireMenu.class), mock(Inventory.class), Component.literal("Command"));
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
    @Test void changingTheIntelFilterResetsEveryScrollPositionAndDrag() throws Exception {
        var screen = new CoreHireScreen(mock(CoreHireMenu.class), mock(Inventory.class), Component.literal("Command"));
        set(screen, "intelOffsets", new int[]{20, 80, 100});
        set(screen, "intelOffset", 60);
        set(screen, "intelMaxOffset", 300);
        set(screen, "intelDragging", true);
        invoke(screen, "filterIntel", String.class, "builder");
        assertEquals("builder", get(screen, "intelQuery"));
        assertArrayEquals(new int[3], (int[]) get(screen, "intelOffsets"));
        assertEquals(0, get(screen, "intelOffset"));
        assertEquals(0, get(screen, "intelMaxOffset"));
        assertEquals(false, get(screen, "intelDragging"));
        invoke(screen, "selectIntelSection", int.class, 2);
        assertEquals(0, get(screen, "intelOffset"));
        assertEquals("builder", get(screen, "intelQuery"));
    }

    @Test void largestSignedBankDeltasRenderInsideGraphAndKeepCorrectTotals() throws Exception {
        var menu = mock(CoreHireMenu.class);
        when(menu.bankLedger()).thenReturn(new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MIN_VALUE, Integer.MIN_VALUE});
        var screen = new CoreHireScreen(menu, mock(Inventory.class), Component.literal("Command"));
        var font = mock(net.minecraft.client.gui.Font.class);
        when(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(call -> call.getArgument(0));
        var fontField = net.minecraft.client.gui.screens.Screen.class.getDeclaredField("font");
        fontField.setAccessible(true);
        fontField.set(screen, font);
        var graphics = mock(net.minecraft.client.gui.GuiGraphics.class);
        var call = CoreHireScreen.class.getDeclaredMethod("drawBankGraph",
                net.minecraft.client.gui.GuiGraphics.class, int.class, int.class, int.class, int.class);
        call.setAccessible(true);
        call.invoke(screen, graphics, 10, 20, 180, 100);
        var y1 = org.mockito.ArgumentCaptor.forClass(Integer.class);
        var y2 = org.mockito.ArgumentCaptor.forClass(Integer.class);
        verify(graphics, atLeastOnce()).fill(anyInt(), y1.capture(), anyInt(), y2.capture(), anyInt());
        for (int i = 0; i < y1.getAllValues().size(); i++) {
            assertTrue(y1.getAllValues().get(i) >= 20);
            assertTrue(y2.getAllValues().get(i) <= 120);
            assertTrue(y2.getAllValues().get(i) >= y1.getAllValues().get(i));
        }
        verify(graphics).drawString(eq(font), eq("+4294967294  /  -4294967296 over last 4"),
                anyInt(), anyInt(), anyInt(), eq(false));
    }

    @Test void typingInventoryHotkeyInSearchDoesNotCloseTheHub() throws Exception {
        var screen = new CoreHireScreen(mock(CoreHireMenu.class), mock(Inventory.class), Component.literal("Command"));
        var search = mock(net.minecraft.client.gui.components.EditBox.class);
        when(search.isFocused()).thenReturn(true);
        set(screen, "tab", CoreCommandPage.INTEL);
        set(screen, "intelSearch", search);
        try (var keys = mockStatic(net.minecraft.client.gui.screens.Screen.class)) {
            assertTrue(screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_E, 0, 0));
            verify(search).keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_E, 0, 0);
        }
    }

    @Test void constructionPagesKeepEveryRowAboveNavigationAtAllScales() throws Exception {
        var menu = mock(CoreHireMenu.class);
        when(menu.construction()).thenReturn(java.util.Collections.nCopies(12,
                new ConstructionReport.Job("Wall", 0, "0%", "", "Working", "")));
        var screen = new CoreHireScreen(menu, mock(Inventory.class), Component.literal("Command"));
        var rowsMethod = CoreHireScreen.class.getDeclaredMethod("constructionRows");
        var heightMethod = CoreHireScreen.class.getDeclaredMethod("constructionRowHeight");
        rowsMethod.setAccessible(true); heightMethod.setAccessible(true);
        for (int w : new int[]{240,320,640,1920}) for (int h : new int[]{180,240,360,1080}) {
            var layout = CoreHireLayout.fit(w,h); set(screen,"layout",layout);
            int rows = (int) rowsMethod.invoke(screen), rowH = (int) heightMethod.invoke(screen);
            assertTrue(rows > 0);
            assertTrue(layout.contentY() + 24 + rows * rowH <= layout.contentBottom() - 20);
        }
    }

}
