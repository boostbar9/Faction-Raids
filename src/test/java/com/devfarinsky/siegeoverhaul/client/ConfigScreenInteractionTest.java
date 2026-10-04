package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Model/input regressions. These do not open a Minecraft window or claim native QA. */
class ConfigScreenInteractionTest extends MinecraftTestSupport {
    private enum Mode { FIRST, SECOND, THIRD }

    @Test void repeatedToggleAndEnumEditsAlwaysUseThePendingValueAndSaveOnlyOnCommit() {
        var config = mock(ForgeConfigSpec.ConfigValue.class);
        var entry = new SiegeOverhaulConfigScreen.Entry("raid.enabled", config, false);
        entry.activate();
        assertEquals(true, entry.pending);
        assertEquals("Enabled", entry.displayValue());
        entry.activate();
        assertEquals(false, entry.pending);
        entry.activate();
        verifyNoInteractions(config);
        entry.commit();
        verify(config).set(true);
        entry.commit();
        verifyNoMoreInteractions(config);

        var mode = new SiegeOverhaulConfigScreen.Entry("hero.mode", config, Mode.THIRD);
        mode.activate(); assertEquals(Mode.FIRST, mode.pending);
        mode.activate(); assertEquals(Mode.SECOND, mode.pending);
    }

    @Test void incompleteNumericTextAndStringListsRetainExistingCodecSemantics() {
        var config = mock(ForgeConfigSpec.ConfigValue.class);
        var number = new SiegeOverhaulConfigScreen.Entry("raid.count", config, 12);
        number.edit("42");
        number.edit("-");
        assertEquals("-", number.text);
        assertFalse(number.validText);
        assertEquals(42, number.pending);
        verifyNoInteractions(config);
        number.commit();
        verify(config).set(42);
        var list = new SiegeOverhaulConfigScreen.Entry("raid.entities", config, List.of("minecraft:villager"));
        list.edit("[minecraft:villager, recruits:recruit]");
        assertEquals(List.of("minecraft:villager", "recruits:recruit"), list.pending);
        list.edit("");
        assertEquals(List.of(), list.pending);
        var text = new SiegeOverhaulConfigScreen.Entry("raid.label", config, "before");
        text.edit("  exact text  ");
        assertEquals("  exact text  ", text.pending);
    }

    @Test void hiddenAndPartiallyClippedRowsRejectMouseKeyboardAndNarration() {
        var control = control(20, 100, 100, 20);
        when(control.isMouseOver(anyDouble(), anyDouble())).thenReturn(true);
        when(control.mouseClicked(anyDouble(), anyDouble(), anyInt())).thenReturn(true);
        when(control.keyPressed(anyInt(), anyInt(), anyInt())).thenReturn(true);
        var entry = new SiegeOverhaulConfigScreen.Entry("raid.enabled", null, false);
        var row = new SiegeOverhaulConfigScreen.RowWidget(entry, control, new ConfigScreenLayout.Bounds(10, 90, 140, 60));
        row.setFocused(true);
        assertTrue(row.keyPressed(GLFW.GLFW_KEY_SPACE, 0, 0));
        assertTrue(row.mouseClicked(30, 110, 0));
        assertFalse(row.mouseClicked(30, 89, 0));
        assertFalse(row.mouseClicked(150, 110, 0));
        clearInvocations(control);
        row.visible = false;
        assertFalse(row.keyPressed(GLFW.GLFW_KEY_SPACE, 0, 0));
        assertFalse(row.charTyped('x', 0));
        assertFalse(row.mouseClicked(30, 110, 0));
        var narration = mock(NarrationElementOutput.class);
        row.updateWidgetNarration(narration);
        verifyNoInteractions(narration);
        verify(control, never()).keyPressed(anyInt(), anyInt(), anyInt());
        verify(control, never()).mouseClicked(anyDouble(), anyDouble(), anyInt());
        var clipped = new SiegeOverhaulConfigScreen.RowWidget(entry, control, new ConfigScreenLayout.Bounds(10, 105, 140, 60));
        clipped.setFocused(true);
        assertFalse(clipped.isFocused());
        assertFalse(clipped.mouseClicked(30, 110, 0));
        assertFalse(clipped.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
    }

    @Test void enterAndSpaceActivateTheRealButtonWithoutCommittingTheValue() throws Exception {
        var screen = screen(320, 240, 4);
        screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
        var minecraft = mock(Minecraft.class);
        when(minecraft.getSoundManager()).thenReturn(mock(net.minecraft.client.sounds.SoundManager.class));
        try (var client = mockStatic(Minecraft.class)) {
            client.when(Minecraft::getInstance).thenReturn(minecraft);
            assertTrue(screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
            assertEquals(true, entries(screen).get(0).pending);
            assertTrue(screen.keyPressed(GLFW.GLFW_KEY_SPACE, 0, 0));
            assertEquals(false, entries(screen).get(0).pending);
        }
        verify(entries(screen).get(0).cfg, never()).set(any());
    }

    @Test void textFocusAndTypingAreForwardedToTheNativeEditor() {
        EditBox box = mock(EditBox.class);
        box.visible = true; box.active = true;
        when(box.getX()).thenReturn(20); when(box.getY()).thenReturn(100);
        when(box.getWidth()).thenReturn(100); when(box.getHeight()).thenReturn(20);
        when(box.keyPressed(GLFW.GLFW_KEY_LEFT, 0, 0)).thenReturn(true);
        when(box.charTyped('7', 0)).thenReturn(true);
        var entry = new SiegeOverhaulConfigScreen.Entry("raid.count", null, 12);
        var row = new SiegeOverhaulConfigScreen.RowWidget(entry, box, new ConfigScreenLayout.Bounds(10, 90, 140, 60));
        assertFalse(row.charTyped('7', 0));
        row.setFocused(true);
        verify(box).setFocused(true);
        assertTrue(row.charTyped('7', 0));
        assertTrue(row.keyPressed(GLFW.GLFW_KEY_LEFT, 0, 0));
        row.setFocused(false);
        verify(box).setFocused(false);
        assertFalse(row.charTyped('7', 0));
    }

    @Test void narrationIncludesFullPathValueAndInvalidInputContext() {
        var entry = new SiegeOverhaulConfigScreen.Entry("raid.enabled", null, false);
        var row = new SiegeOverhaulConfigScreen.RowWidget(entry, control(20, 100, 100, 20),
                new ConfigScreenLayout.Bounds(10, 90, 140, 60));
        var out = mock(NarrationElementOutput.class);
        row.updateWidgetNarration(out);
        verify(out).add(eq(NarratedElementType.TITLE), argThat((Component c) -> c.getString().equals("raid.enabled: Disabled")));
        verify(out).add(eq(NarratedElementType.USAGE), argThat((Component c) -> c.getString().contains("Enter or Space")));
        entry.validText = false;
        row.updateWidgetNarration(out);
        verify(out).add(eq(NarratedElementType.HINT), argThat((Component c) -> c.getString().contains("last valid value")));
    }

    @Test void resizingAndReturningFromNestedScreensKeepPendingEditsFilterAndFocus() throws Exception {
        var screen = screen(320, 240, 12);
        var entry = entries(screen).get(0);
        entry.activate();
        screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
        assertEquals(entry.path, ((SiegeOverhaulConfigScreen.RowWidget) screen.getFocused()).entry.path);
        screen.removed(); // Opening Hero visuals must not commit the parent.
        reinitialize(screen, 480, 360);
        assertSame(entry, entries(screen).get(0));
        assertEquals(true, entry.pending);
        assertEquals("Enabled", ((SiegeOverhaulConfigScreen.RowWidget) screen.getFocused()).control.getMessage().getString());
        verify(entry.cfg, never()).set(any());
        var filter = (EditBox) get(screen, "filterBox");
        screen.setFocused(filter);
        filter.setValue("setting1");
        reinitialize(screen, 320, 240);
        assertEquals("setting1", ((EditBox) get(screen, "filterBox")).getValue());
        assertSame(get(screen, "filterBox"), screen.getFocused());
        verify((ForgeConfigSpec) get(screen, "spec"), never()).save();
    }

    @Test void repeatedDirectInitializationCannotDuplicateRowsActionsOrNarrationRegistrations() throws Exception {
        var screen = screen(320, 240, 20);
        int children = screen.children().size();
        int narratables = ((List<?>) getFrom(screen, Screen.class, "narratables")).size();
        var original = rows(screen).get(0);
        screen.init();
        screen.init();
        assertEquals(children, screen.children().size());
        assertEquals(narratables, ((List<?>) getFrom(screen, Screen.class, "narratables")).size());
        assertFalse(original.visible);
        assertFalse(original.isFocused());
    }

    @Test void tabAndReverseTabReachEveryHiddenEntryAndOnlyVisibleRowsAreRegistered() throws Exception {
        var screen = screen(320, 240, 173);
        for (int index = 0; index < 173; index++) {
            assertTrue(screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0));
            var row = (SiegeOverhaulConfigScreen.RowWidget) screen.getFocused();
            assertSame(entries(screen).get(index), row.entry);
            assertTrue(row.isFocused());
            assertTrue(row.getY() >= ((ConfigScreenLayout) get(screen, "layout")).viewport.y());
            assertEquals(rows(screen).size(), screen.children().stream().filter(SiegeOverhaulConfigScreen.RowWidget.class::isInstance).count());
            assertTrue(rows(screen).size() <= 2);
        }
        screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
        assertSame(get(screen, "done"), screen.getFocused());
        for (int index = 172; index >= 0; index--) {
            screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, GLFW.GLFW_MOD_SHIFT);
            assertSame(entries(screen).get(index), ((SiegeOverhaulConfigScreen.RowWidget) screen.getFocused()).entry);
        }
        screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, GLFW.GLFW_MOD_SHIFT);
        assertSame(get(screen, "filterBox"), screen.getFocused());
    }

    @Test void scrollingClearsHiddenFocusAndCannotBeTriggeredOutsideTheViewport() throws Exception {
        var screen = screen(320, 240, 20);
        var layout = (ConfigScreenLayout) get(screen, "layout");
        screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
        var old = (SiegeOverhaulConfigScreen.RowWidget) screen.getFocused();
        assertFalse(screen.mouseScrolled(0, layout.viewport.y() + 10, -1));
        assertEquals(0, get(screen, "firstRow"));
        assertTrue(screen.mouseScrolled(layout.viewport.x() + 10, layout.viewport.y() + 10, -1));
        assertEquals(1, get(screen, "firstRow"));
        assertFalse(old.visible);
        assertFalse(old.isFocused());
        assertFalse(old.keyPressed(GLFW.GLFW_KEY_SPACE, 0, 0));
        assertSame(get(screen, "filterBox"), screen.getFocused());
    }

    @Test void rawInvalidEditorTextAndCaretSurviveRebuildingRows() throws Exception {
        var screen = screen(320, 240, 4);
        var entry = new SiegeOverhaulConfigScreen.Entry("raid.amount", mock(ForgeConfigSpec.ConfigValue.class), 12);
        entries(screen).set(0, entry);
        reinitialize(screen, 320, 240);
        screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
        var box = (EditBox) ((SiegeOverhaulConfigScreen.RowWidget) screen.getFocused()).control;
        box.setValue("-");
        box.moveCursorTo(0);
        reinitialize(screen, 480, 360);
        var next = (EditBox) ((SiegeOverhaulConfigScreen.RowWidget) screen.getFocused()).control;
        assertEquals("-", next.getValue());
        assertEquals(0, next.getCursorPosition());
        assertEquals(12, entry.pending);
        assertFalse(entry.validText);
    }

    @Test void everyRowControlRendersBeforeTheViewportScissorIsDisabled() throws Exception {
        var screen = spy(screen(320, 240, 4));
        doNothing().when(screen).renderBackground(any(GuiGraphics.class));
        ((List<?>) getFrom(screen, Screen.class, "renderables")).clear();
        var layout = (ConfigScreenLayout) get(screen, "layout");
        var bounds = layout.input(0);
        var control = control(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        rows(screen).clear();
        rows(screen).add(new SiegeOverhaulConfigScreen.RowWidget(entries(screen).get(0), control, layout.viewport));
        var graphics = mock(GuiGraphics.class);
        screen.render(graphics, -1, -1, 0);
        var order = inOrder(graphics, control);
        order.verify(graphics).enableScissor(layout.viewport.x(), layout.viewport.y(), layout.viewport.right(), layout.viewport.bottom());
        order.verify(control).render(graphics, -1, -1, 0);
        order.verify(graphics).disableScissor();
        verify(control, times(1)).render(graphics, -1, -1, 0);
    }

    @Test void emptyFilterResultsRemoveAllRowInputAndCloseSavesOnlyThisSpec() throws Exception {
        var screen = screen(320, 240, 4);
        var filter = (EditBox) get(screen, "filterBox");
        filter.setValue("nothing-matches-this");
        assertTrue(rows(screen).isEmpty());
        assertTrue(screen.children().stream().noneMatch(SiegeOverhaulConfigScreen.RowWidget.class::isInstance));
        screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
        assertSame(get(screen, "done"), screen.getFocused());
        entries(screen).get(0).activate();
        var spec = (ForgeConfigSpec) get(screen, "spec");
        verify(spec, never()).save();
        screen.onClose();
        verify(entries(screen).get(0).cfg).set(true);
        verify(spec).save();
        verify((Minecraft) getFrom(screen, Screen.class, "minecraft")).setScreen(null);
        assertEquals("Hero visuals", new SiegeOverhaulConfigScreen(null,
                com.devfarinsky.siegeoverhaul.HeroVisualConfig.SPEC).getTitle().getString());
    }

    private static AbstractWidget control(int x, int y, int width, int height) {
        var control = mock(AbstractWidget.class);
        control.active = true; control.visible = true;
        when(control.getX()).thenReturn(x); when(control.getY()).thenReturn(y);
        when(control.getWidth()).thenReturn(width); when(control.getHeight()).thenReturn(height);
        return control;
    }

    private static SiegeOverhaulConfigScreen screen(int width, int height, int count) throws Exception {
        var screen = new SiegeOverhaulConfigScreen(null, mock(ForgeConfigSpec.class));
        set(screen, SiegeOverhaulConfigScreen.class, "loaded", true);
        for (int i = 0; i < count; i++) entries(screen).add(new SiegeOverhaulConfigScreen.Entry(
                "raid.setting" + i, mock(ForgeConfigSpec.ConfigValue.class), false));
        Font font = mock(Font.class);
        when(font.width(anyString())).thenAnswer(call -> ((String) call.getArgument(0)).length() * 6);
        when(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(call -> {
            String value = call.getArgument(0); int available = call.getArgument(1);
            return value.substring(0, Math.min(value.length(), Math.max(0, available / 6)));
        });
        set(screen, Screen.class, "font", font);
        set(screen, Screen.class, "minecraft", mock(Minecraft.class));
        screen.width = width; screen.height = height;
        screen.init();
        return screen;
    }

    private static void reinitialize(SiegeOverhaulConfigScreen screen, int width, int height) throws Exception {
        screen.resize((Minecraft) getFrom(screen, Screen.class, "minecraft"), width, height);
    }
    @SuppressWarnings("unchecked") private static List<SiegeOverhaulConfigScreen.Entry> entries(SiegeOverhaulConfigScreen screen) throws Exception {
        return (List<SiegeOverhaulConfigScreen.Entry>) get(screen, "entries");
    }
    @SuppressWarnings("unchecked") private static List<SiegeOverhaulConfigScreen.RowWidget> rows(SiegeOverhaulConfigScreen screen) throws Exception {
        return (List<SiegeOverhaulConfigScreen.RowWidget>) get(screen, "rows");
    }
    private static Object get(Object screen, String field) throws Exception {
        return getFrom(screen, SiegeOverhaulConfigScreen.class, field);
    }
    private static Object getFrom(Object target, Class<?> owner, String name) throws Exception {
        var field = owner.getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static void set(Object target, Class<?> owner, String name, Object value) throws Exception {
        var field = owner.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
}
