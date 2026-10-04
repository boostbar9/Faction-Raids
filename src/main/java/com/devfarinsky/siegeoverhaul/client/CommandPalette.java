package com.devfarinsky.siegeoverhaul.client;

/**
 * Central palette for the command center HUD.
 *
 * <p>Quiet midnight-navy surfaces, legible cool text and restrained teal
 * selection retain the fantasy identity through small gold accents. Every screen
 * and widget in the HUD reads from these constants so revisions stay coherent.
 */
public final class CommandPalette {
    private CommandPalette() {}

    // Shadow layers behind the whole window.
    public static final int SHADOW_OUTER = 0x66000000;
    public static final int SHADOW_INNER = 0xaa000000;

    // Small heritage ornaments; the main window uses a single flat border.
    public static final int BEVEL_LIGHT = 0xffd9b968;
    public static final int BEVEL_DARK  = 0xff63491f;

    // Main window body (flat midnight navy).
    public static final int PANEL_TOP    = 0xff101824;
    public static final int PANEL_BOTTOM = 0xff101824;
    public static final int PANEL_INSET  = 0xff1b303c;

    // Header banner (deeper navy).
    public static final int HEADER_TOP    = 0xff142031;
    public static final int HEADER_BOTTOM = 0xff142031;

    // Interior card panels and action states (flat slate blue).
    public static final int CARD_TOP    = 0xff172333;
    public static final int CARD_BOTTOM = 0xff172333;
    public static final int CARD_HOVER_TOP = 0xff203344;
    public static final int CARD_HOVER_BOTTOM = 0xff203344;
    public static final int CARD_BORDER = 0xff34465c;
    public static final int CARD_BORDER_HOVER = 0xff7a9baa;
    public static final int CONTROL_SELECTED = 0xff1b303c;
    public static final int CONTROL_PRIMARY = 0xff29494b;

    public static final int CARD_TOP_DIM    = 0xff181c28;
    public static final int CARD_BOTTOM_DIM = 0xff0d101a;
    public static final int CARD_BORDER_DIM = 0xff242938;

    // Currency / status chips.
    public static final int CHIP_BORDER = 0xff34465c;
    public static final int CHIP_FILL   = 0xff0c131e;

    // Hairlines and dividers.
    public static final int HAIRLINE = 0xffa8874e;
    public static final int DIVIDER  = 0xff34465c;

    // Corner rivets.
    public static final int RIVET_LIGHT = 0xffe3b968;
    public static final int RIVET_DARK  = 0xff1e1610;

    // Text.
    public static final int TEXT       = 0xffeef1ed;
    public static final int TEXT_MUTED = 0xffb1becd;
    public static final int TEXT_DIM   = 0xff93a3b7;

    // Accents (gameplay-coded).
    public static final int ACCENT_GOLD    = 0xffe4bd74;
    public static final int ACCENT_EMERALD = 0xff58e089;
    public static final int ACCENT_ARCANE  = 0xffb89aff;
    public static final int ACCENT_TEAL    = 0xff86ded2;
    public static final int ACCENT_BLOOD   = 0xffe06060;
    public static final int ACCENT_STEEL   = 0xff9ac9ff;

    /** Rare/tier tinting for loot chips. */
    public static int tier(int tier) {
        return switch (tier) {
            case 0 -> 0xffc7c1b5;   // Common
            case 1 -> ACCENT_EMERALD; // Uncommon
            case 2 -> ACCENT_STEEL;   // Rare
            case 3 -> ACCENT_ARCANE;  // Epic
            default -> ACCENT_GOLD;   // Legendary
        };
    }
}
