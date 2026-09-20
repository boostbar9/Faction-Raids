package com.devfarinsky.siegeoverhaul.client;

/**
 * Central palette for the command center HUD.
 *
 * <p>The palette is medieval-fantasy but modern-sleek: deep midnight-navy
 * panels with warm aged-gold framing and cool arcane accents. Every screen
 * and widget in the HUD reads from these constants so revisions stay coherent.
 */
public final class CommandPalette {
    private CommandPalette() {}

    // Shadow layers behind the whole window.
    public static final int SHADOW_OUTER = 0x66000000;
    public static final int SHADOW_INNER = 0xaa000000;

    // Outer bevel (aged gold framing).
    public static final int BEVEL_LIGHT = 0xffd9b968;
    public static final int BEVEL_DARK  = 0xff63491f;

    // Main window body (midnight navy gradient).
    public static final int PANEL_TOP    = 0xff121a2a;
    public static final int PANEL_BOTTOM = 0xff080c14;
    public static final int PANEL_INSET  = 0xff202b40;

    // Header banner (deeper navy).
    public static final int HEADER_TOP    = 0xff17243a;
    public static final int HEADER_BOTTOM = 0xff0b111d;

    // Interior card panels (slate blue).
    public static final int CARD_TOP    = 0xff202c42;
    public static final int CARD_BOTTOM = 0xff111827;
    public static final int CARD_HOVER_TOP = 0xff2a3852;
    public static final int CARD_HOVER_BOTTOM = 0xff172137;
    public static final int CARD_BORDER = 0xff42516e;
    public static final int CARD_BORDER_HOVER = 0xff7f91b3;

    public static final int CARD_TOP_DIM    = 0xff181c28;
    public static final int CARD_BOTTOM_DIM = 0xff0d101a;
    public static final int CARD_BORDER_DIM = 0xff242938;

    // Currency / status chips.
    public static final int CHIP_BORDER = 0xff4b5b78;
    public static final int CHIP_FILL   = 0xff0b111d;

    // Hairlines and dividers.
    public static final int HAIRLINE = 0xffa8874e;
    public static final int DIVIDER  = 0xff303c54;

    // Corner rivets.
    public static final int RIVET_LIGHT = 0xffe3b968;
    public static final int RIVET_DARK  = 0xff1e1610;

    // Text.
    public static final int TEXT       = 0xfff2ecdb;
    public static final int TEXT_MUTED = 0xffaab5ca;
    public static final int TEXT_DIM   = 0xff738098;

    // Accents (gameplay-coded).
    public static final int ACCENT_GOLD    = 0xfff2c96b;
    public static final int ACCENT_EMERALD = 0xff58e089;
    public static final int ACCENT_ARCANE  = 0xffb89aff;
    public static final int ACCENT_TEAL    = 0xff81e8da;
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
