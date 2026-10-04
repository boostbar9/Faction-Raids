package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CoreButtonStyleTest extends MinecraftTestSupport {
    @Test void keyboardFocusRemainsVisibleAcrossSelectionHoverAndPrimaryStates() {
        for (boolean hovered : new boolean[]{false, true}) {
            for (boolean selected : new boolean[]{false, true}) {
                for (boolean primary : new boolean[]{false, true}) {
                    assertEquals(CommandPalette.ACCENT_TEAL,
                            CoreButton.borderColor(true, true, hovered, selected, primary));
                    assertNotEquals(CommandPalette.ACCENT_TEAL,
                            CoreButton.borderColor(true, false, hovered, selected, primary));
                    assertEquals(CommandPalette.CARD_BORDER_DIM,
                            CoreButton.borderColor(false, true, hovered, selected, primary));
                }
            }
        }
    }

    @Test void nativeFontTextAndFocusHaveReadableContrastOnEveryControlSurface() {
        for (int surface : new int[]{CommandPalette.PANEL_TOP, CommandPalette.CARD_TOP,
                CommandPalette.CARD_HOVER_TOP, CommandPalette.CONTROL_SELECTED,
                CommandPalette.CONTROL_PRIMARY, CommandPalette.CARD_TOP_DIM}) {
            assertTrue(contrast(CommandPalette.TEXT, surface) >= 4.5);
            assertTrue(contrast(CommandPalette.TEXT_MUTED, surface) >= 4.5);
            assertTrue(contrast(CommandPalette.ACCENT_TEAL, surface) >= 3);
        }
        assertTrue(contrast(CommandPalette.TEXT_DIM, CommandPalette.CARD_TOP_DIM) >= 4.5);
        assertTrue(contrast(CommandPalette.ACCENT_GOLD, CommandPalette.CARD_TOP) >= 4.5);
    }

    private static double contrast(int a, int b) {
        double first = luminance(a), second = luminance(b);
        return (Math.max(first, second) + .05) / (Math.min(first, second) + .05);
    }

    private static double luminance(int color) {
        return .2126 * linear(color >> 16 & 255) + .7152 * linear(color >> 8 & 255)
                + .0722 * linear(color & 255);
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
    }
}
