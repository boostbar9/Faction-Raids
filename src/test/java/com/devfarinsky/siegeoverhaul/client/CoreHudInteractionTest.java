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
            var building = new CoreBuildingLayout(layout);
            assertTrue(building.reportY() + rows * rowH <= building.actionY() - 4);
        }
    }

    @Test void buildingSectionChangesClearHiddenFocusAndKeepTheSelectedPlan() throws Exception {
        var screen = new CoreHireScreen(mock(CoreHireMenu.class), mock(Inventory.class), Component.literal("Command"));
        var sections = new Button[BuildingSection.values().length];
        for (int i = 0; i < sections.length; i++) sections[i] = mock(Button.class);
        set(screen, "buildingSections", sections);
        invoke(screen, "selectDefense", DefenseBlueprint.Kind.class, DefenseBlueprint.Kind.GATEHOUSE);
        invoke(screen, "selectBuildingSection", BuildingSection.class, BuildingSection.CONSTRUCTION);
        assertEquals(BuildingSection.CONSTRUCTION, get(screen, "buildingSection"));
        assertSame(sections[BuildingSection.CONSTRUCTION.ordinal()], screen.getFocused());
        invoke(screen, "selectBuildingSection", BuildingSection.class, BuildingSection.STRUCTURES);
        assertEquals(DefenseBlueprint.Kind.GATEHOUSE, get(screen, "selectedDefense"));
        assertSame(sections[BuildingSection.STRUCTURES.ordinal()], screen.getFocused());
    }

    @Test void longConstructionLabelsAreExplicitlyEllipsizedAndUnknownProgressHasNoFakeBar() throws Exception {
        var menu = mock(CoreHireMenu.class);
        String longLabel = "Territory perimeter for the entire northern claim boundary with retained materials";
        var job = new ConstructionReport.Job(longLabel, -1, "Progress unavailable until the marker is loaded",
                "Marker 1200, 70, -3400", "Paused: marker unavailable. Load the site to inspect its status.",
                "192 cobblestone, 64 oak planks and additional reported materials");
        when(menu.construction()).thenReturn(java.util.List.of(job));
        var screen = new CoreHireScreen(menu, mock(Inventory.class), Component.literal("Command"));
        var font = mock(net.minecraft.client.gui.Font.class);
        when(font.width(anyString())).thenAnswer(call -> ((String) call.getArgument(0)).length() * 6);
        when(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(call -> {
            String value = call.getArgument(0);
            int available = call.getArgument(1);
            return value.substring(0, Math.min(value.length(), Math.max(0, available / 6)));
        });
        when(font.split(any(net.minecraft.network.chat.FormattedText.class), anyInt()))
                .thenReturn(java.util.List.of());
        var fontField = net.minecraft.client.gui.screens.Screen.class.getDeclaredField("font");
        fontField.setAccessible(true);
        fontField.set(screen, font);
        var draw = CoreHireScreen.class.getDeclaredMethod("drawConstruction", net.minecraft.client.gui.GuiGraphics.class);
        draw.setAccessible(true);
        for (int[] size : new int[][]{{320, 240}, {640, 360}, {854, 480}}) {
            var frame = CoreHireLayout.fit(size[0], size[1]);
            var building = new CoreBuildingLayout(frame);
            set(screen, "layout", frame);
            var graphics = mock(net.minecraft.client.gui.GuiGraphics.class);
            draw.invoke(screen, graphics);
            var labels = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(graphics, atLeastOnce()).drawString(eq(font), labels.capture(), anyInt(), anyInt(), anyInt(), eq(false));
            String title = labels.getAllValues().stream().filter(value -> value.startsWith("1/1  ")).findFirst().orElseThrow();
            assertTrue(title.endsWith("…"));
            assertTrue(font.width(title) <= building.reportMainWidth() - 20);
            verify(graphics, never()).fill(eq(building.x() + 10),
                    eq(building.reportY() + building.reportRowHeight() - 10), anyInt(), anyInt(),
                    eq(CommandPalette.ACCENT_TEAL));
        }
    }

    @Test void projectReportsUseOneUnambiguousCancelTargetAndKeepCompleteJobsNonCancelable() throws Exception {
        var menu = mock(CoreHireMenu.class);
        var pending = new ConstructionReport.Job("Territory perimeter", 100, "All placed; verifying", "Whole territory",
                "Awaiting final verification", "", java.util.UUID.randomUUID(), 7,
                "3 / 3 sections verified\n64 emeralds paid once · materials separate", true, false);
        var complete = new ConstructionReport.Job("Territory perimeter", 100, "All verified", "Whole territory",
                "Complete", "", java.util.UUID.randomUUID(), 8, "3 / 3 sections verified", false, true);
        when(menu.construction()).thenReturn(java.util.List.of(pending, complete));
        var screen = new CoreHireScreen(menu, mock(Inventory.class), Component.literal("Command"));
        var rows = CoreHireScreen.class.getDeclaredMethod("constructionRows"); rows.setAccessible(true);
        var pages = CoreHireScreen.class.getDeclaredMethod("constructionPages"); pages.setAccessible(true);
        var selected = CoreHireScreen.class.getDeclaredMethod("selectedConstruction"); selected.setAccessible(true);
        for (int[] size : new int[][]{{240, 180}, {320, 240}, {640, 360}, {1920, 1080}}) {
            set(screen, "layout", CoreHireLayout.fit(size[0], size[1]));
            assertEquals(1, rows.invoke(screen)); assertEquals(2, pages.invoke(screen));
            set(screen, "constructionPage", 0); assertSame(pending, selected.invoke(screen));
            set(screen, "constructionPage", 1); assertSame(complete, selected.invoke(screen));
        }
        assertTrue(pending.cancelable()); assertFalse(pending.complete());
        assertFalse(complete.cancelable()); assertTrue(complete.complete());
        assertEquals(CommandPalette.ACCENT_GOLD, CoreHireScreen.constructionProgressColor(pending));
        assertEquals(CommandPalette.ACCENT_EMERALD, CoreHireScreen.constructionProgressColor(complete));
    }

    @Test void leavingConstructionClearsTheCancelButtonsKeyboardFocus() throws Exception {
        var screen = new CoreHireScreen(mock(CoreHireMenu.class), mock(Inventory.class), Component.literal("Command"));
        var cancel = mock(Button.class);
        var sections = new Button[BuildingSection.values().length];
        for (int i = 0; i < sections.length; i++) sections[i] = mock(Button.class);
        set(screen, "buildingSections", sections);
        set(screen, "buildingSection", BuildingSection.CONSTRUCTION);
        set(screen, "constructionCancel", cancel);
        screen.setFocused(cancel);
        invoke(screen, "selectBuildingSection", BuildingSection.class, BuildingSection.PERIMETER);
        assertSame(sections[BuildingSection.PERIMETER.ordinal()], screen.getFocused());
        assertNotSame(cancel, screen.getFocused());
    }

    @Test void keyboardReadingIsBoundedAndDoesNotHijackOrdinaryArrowNavigation() {
        assertEquals(0, CoreHireScreen.keyboardScroll(12, 100, 40, org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP));
        assertEquals(52, CoreHireScreen.keyboardScroll(12, 100, 40, org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN));
        assertEquals(100, CoreHireScreen.keyboardScroll(92, 100, 40, org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN));
        assertEquals(0, CoreHireScreen.keyboardScroll(50, 100, 40, org.lwjgl.glfw.GLFW.GLFW_KEY_HOME));
        assertEquals(100, CoreHireScreen.keyboardScroll(50, 100, 40, org.lwjgl.glfw.GLFW.GLFW_KEY_END));
        assertEquals(-1, CoreHireScreen.keyboardScroll(50, 100, 40, org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN));
    }

    @Test void compactPlanPagingKeepsSelectionVisibleAndCannotCommission() throws Exception {
        var screen = new CoreHireScreen(mock(CoreHireMenu.class), mock(Inventory.class), Component.literal("Command"));
        set(screen, "layout", CoreHireLayout.fit(320, 240));
        set(screen, "hire", new Button[4]);
        Button[] plans = new Button[DefenseBlueprint.Kind.values().length];
        for (int i = 0; i < plans.length; i++) plans[i] = mock(Button.class);
        set(screen, "defensePlans", plans);
        try (var packets = mockStatic(com.devfarinsky.siegeoverhaul.RaidNetwork.class)) {
            invoke(screen, "selectDefense", DefenseBlueprint.Kind.class, DefenseBlueprint.Kind.WALL);
            assertEquals(1, get(screen, "planPage"));
            invoke(screen, "movePlanPage", int.class, 1);
            assertEquals(2, get(screen, "planPage"));
            assertEquals(DefenseBlueprint.Kind.CORNER, get(screen, "selectedDefense"));
            assertSame(plans[4], screen.getFocused());
            invoke(screen, "movePlanPage", int.class, 1);
            assertEquals(2, get(screen, "planPage"));
            invoke(screen, "movePlanPage", int.class, -1);
            assertEquals(DefenseBlueprint.Kind.GATEHOUSE, get(screen, "selectedDefense"));
            packets.verifyNoInteractions();
        }
    }

    @Test void compactArmyRetainsItsRefreshCountdownWithPageNavigation() {
        assertEquals("1/7 · Refresh 2:03 · Ctrl+Tab", CoreHireScreen.footerHint(CoreCommandPage.ARMY, true, 123));
        assertTrue(CoreHireScreen.footerHint(CoreCommandPage.INTEL, true, 123).contains("7/7"));
        assertTrue(CoreHireScreen.footerHint(CoreCommandPage.ARMY, false, 123).contains("Ctrl+Shift+Tab"));
    }

    @Test @SuppressWarnings("unchecked")
    void disabledPlanPagerFocusMovesToTheVisibleSelection() throws Exception {
        var screen = new CoreHireScreen(mock(CoreHireMenu.class), mock(Inventory.class), Component.literal("Command"));
        set(screen, "layout", CoreHireLayout.fit(320, 240));
        set(screen, "tab", CoreCommandPage.DEFENSES);
        set(screen, "buildingSection", BuildingSection.STRUCTURES);
        var pager = mock(Button.class); pager.visible = true; pager.active = false;
        var plan = mock(Button.class); plan.visible = true; plan.active = true;
        Button[] plans = new Button[DefenseBlueprint.Kind.values().length]; plans[DefenseBlueprint.Kind.WALL.ordinal()] = plan;
        set(screen, "defensePlans", plans);
        var children = (java.util.List<net.minecraft.client.gui.components.events.GuiEventListener>)(java.util.List<?>)screen.children();
        children.add(pager); children.add(plan);
        screen.setFocused(pager);
        var normalize = CoreHireScreen.class.getDeclaredMethod("ensureVisibleFocus"); normalize.setAccessible(true); normalize.invoke(screen);
        assertSame(plan, screen.getFocused());
        assertTrue(screen.children().contains(screen.getFocused()));
        assertTrue(((Button)screen.getFocused()).active);
    }

}
