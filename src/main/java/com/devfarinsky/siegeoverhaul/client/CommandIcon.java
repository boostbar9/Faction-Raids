package com.devfarinsky.siegeoverhaul.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Procedural pixel-art icons for the command center HUD.
 *
 * <p>Each icon is a 12x12 monochrome bitmap encoded as one string per row.
 * Rendering scales the bitmap to any requested pixel size and draws a subtle
 * one-pixel drop shadow behind each lit cell so the glyph reads cleanly on
 * dark parchment or forged-iron backing.
 *
 * <p>All rendering is per-cell rectangle fills — no external textures, no
 * per-frame allocations, no atlas dependencies. This keeps the HUD sharp at
 * every Minecraft GUI scale (1x through 4x) and avoids texture-pack skinning
 * conflicts.
 */
public enum CommandIcon {
    CROWN(0xffe8c968,
            "............",
            "..#..#..#...",
            "..#..#..#...",
            "..##.##.##..",
            "..########..",
            "..########..",
            "..#.####.#..",
            "..########..",
            "..########..",
            "............",
            ".##########.",
            "............"),
    SWORDS(0xffe0d8c4,
            ".#........#.",
            ".##......##.",
            "..##....##..",
            "...##..##...",
            "....####....",
            ".....##.....",
            "....####....",
            "..###..###..",
            "...#....#...",
            "..#......#..",
            ".#........#.",
            "............"),
    SHIELD(0xff9ac9ff,
            "..########..",
            ".##########.",
            ".##......##.",
            ".##..##..##.",
            ".##..##..##.",
            ".##..##..##.",
            "..#..##..#..",
            "..##....##..",
            "...##..##...",
            "....####....",
            ".....##.....",
            "............"),
    CHEST(0xffe8b56b,
            "............",
            "...######...",
            "..########..",
            ".##########.",
            ".##########.",
            ".####..####.",
            ".####..####.",
            ".##########.",
            ".##########.",
            "............",
            ".##########.",
            "............"),
    BANK(0xffe8c968,
            ".....##.....",
            "...######...",
            ".##########.",
            "............",
            ".##.##.##.#.",
            ".##.##.##.#.",
            ".##.##.##.#.",
            ".##.##.##.#.",
            ".##.##.##.#.",
            "............",
            ".##########.",
            ".##########."),
    WIND(0xff9ee5cf,
            "............",
            "......###...",
            "........##..",
            ".#########..",
            "............",
            ".########...",
            "........##..",
            "......###...",
            "............",
            ".######.....",
            "............",
            "............"),
    POWER(0xffff8b6d,
            ".......##...",
            "......##....",
            ".....##.....",
            "....##......",
            "...######...",
            "..######....",
            ".....##.....",
            "....##......",
            "...##.......",
            "..##........",
            "............",
            "............"),
    AEGIS(0xff9ac9ff,
            "............",
            "...####.....",
            "..#....#....",
            ".#..##..#...",
            ".#.####.#...",
            ".#.####.#...",
            ".#.####.#...",
            "..######....",
            "...####.....",
            "....##......",
            "............",
            "............"),
    EMERALD(0xff58e089,
            "............",
            "....####....",
            "...######...",
            "..########..",
            ".##########.",
            ".##########.",
            "..########..",
            "..########..",
            "...######...",
            "....####....",
            ".....##.....",
            "............"),
    SCROLL(0xffefe0b8,
            "............",
            ".##########.",
            ".#........#.",
            ".#..####..#.",
            ".#........#.",
            ".#..####..#.",
            ".#........#.",
            ".#..####..#.",
            ".#........#.",
            ".#..####..#.",
            ".##########.",
            "............"),
    RUNE(0xffb89aff,
            "............",
            ".....##.....",
            "....####....",
            "...##..##...",
            "..##....##..",
            ".##......##.",
            ".##########.",
            ".##......##.",
            ".##......##.",
            ".##......##.",
            "............",
            "............"),
    HAMMER(0xffbfb0a0,
            "............",
            ".########...",
            ".########...",
            ".########...",
            "....##......",
            "....##......",
            "....##......",
            "....##......",
            "....##......",
            "....##......",
            "....##......",
            "............");

    private final int color;
    private final int[] rows = new int[12];

    CommandIcon(int color, String... pattern) {
        this.color = color;
        for (int y = 0; y < 12; y++)
            for (int x = 0; x < 12; x++)
                if (pattern[y].charAt(x) == '#') rows[y] |= 1 << x;
    }

    public int color() { return color; }

    /** Render at native 12px size with a one-pixel drop shadow. */
    public void draw(GuiGraphics g, int x, int y) {
        draw(g, x, y, 12);
    }

    /**
     * Render scaled to {@code size} pixels square, with a one-pixel drop shadow.
     * The glyph is drawn on transparent backing so it composites over any panel.
     */
    public void draw(GuiGraphics g, int x, int y, int size) {
        int cellW = Math.max(1, size / 12);
        int inset = (size - cellW * 12) / 2;
        int shadow = 0xff0a0d18;
        int fill = color;
        for (int row = 0; row < 12; row++) {
            int bits = rows[row];
            if (bits == 0) continue;
            for (int col = 0; col < 12; col++) {
                if ((bits & (1 << col)) == 0) continue;
                int left = x + inset + col * cellW;
                int top = y + inset + row * cellW;
                g.fill(left + 1, top + 1, left + cellW + 1, top + cellW + 1, shadow);
                g.fill(left, top, left + cellW, top + cellW, fill);
            }
        }
    }
}
