package com.devfarinsky.siegeoverhaul.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Procedural 16x16 pixel-art glyphs for the command center HUD.
 *
 * <p>Each icon is encoded as sixteen rows of sixteen characters:
 * <ul>
 *   <li>{@code '#'} — main fill (icon color).</li>
 *   <li>{@code '='} — highlight (a brighter tint of the icon color).</li>
 *   <li>{@code '.'} — outline (dark iron; sits one pixel behind the fill).</li>
 *   <li>{@code ' '} — transparent.</li>
 * </ul>
 *
 * <p>Rendering scales the bitmap to any requested pixel size and drops a soft
 * shadow behind the outline for depth at every Minecraft GUI scale (1x-4x).
 * No external textures, no atlas dependencies, no per-frame allocations.
 */
public enum CommandIcon {

    CROWN(0xffe8c968,
            "                ",
            "  =    =    =   ",
            " =#=  =#=  =#=  ",
            " =##=.##=.=##=  ",
            " =##########=.  ",
            " =##########=.  ",
            " =##########=.  ",
            " =##..####..#=  ",
            " =##########=.  ",
            " =##########=.  ",
            " .==========.   ",
            "  ..........    ",
            " ============   ",
            " ==########==   ",
            " .==========.   ",
            "                "),

    SWORDS(0xffdedad0,
            "                ",
            " =.          .= ",
            " ==.        .== ",
            "  ==.      .==  ",
            "   ==.    .==   ",
            "    ==.  .==    ",
            "     ==..==     ",
            "      ====      ",
            "     .==.=.     ",
            "    .= =. =.    ",
            "   .=   =. =.   ",
            "  .=     =. =.  ",
            " .=       =. =. ",
            " =.        .=.  ",
            " ..         ..  ",
            "                "),

    SHIELD(0xff9dc5ff,
            "                ",
            "  ==========    ",
            " =##########=.  ",
            " =##########=.  ",
            " =##########=.  ",
            " =####..####=.  ",
            " =####..####=.  ",
            "  =##########.  ",
            "  =####..####.  ",
            "   =########.   ",
            "    =######.    ",
            "     =####.     ",
            "      =##.      ",
            "       =.       ",
            "        .       ",
            "                "),

    CHEST(0xffe8b56b,
            "                ",
            "   ..........   ",
            "  .==========.  ",
            " .=##########=. ",
            " .=##########=. ",
            " .=##########=. ",
            " ..==========.. ",
            " .============. ",
            " =====.==.====  ",
            " ####..##..#### ",
            " ####.####.#### ",
            " ####..##..#### ",
            " ============.  ",
            " ##..######..## ",
            " ..............  ",
            "                "),

    BANK(0xffe8c968,
            "                ",
            "       ==       ",
            "      ====      ",
            "     ======     ",
            "    ========    ",
            "   ==========   ",
            "  ============  ",
            " ..##########.. ",
            "  ##  ##  ##    ",
            "  ##  ##  ##    ",
            "  ##  ##  ##    ",
            "  ##  ##  ##    ",
            "  ##  ##  ##    ",
            " .############. ",
            " ============== ",
            "                "),

    WIND(0xff9ee5cf,
            "                ",
            "   =========.   ",
            "           .=.  ",
            "  .==     ..=.  ",
            "   .=========.  ",
            "                ",
            "  .=========.   ",
            "  .=.           ",
            "  .==.     ..   ",
            "  .=========.   ",
            "                ",
            "   .======.     ",
            "                ",
            "                ",
            "                ",
            "                "),

    POWER(0xffffb14d,
            "                ",
            "        ==      ",
            "       ==       ",
            "      ==        ",
            "     ==         ",
            "    ==          ",
            "   =====        ",
            "  =======       ",
            "       ==       ",
            "      ==        ",
            "     ==         ",
            "    ==          ",
            "   ==           ",
            "  ==            ",
            "                ",
            "                "),

    AEGIS(0xff9dc5ff,
            "                ",
            "     .====.     ",
            "    .======.    ",
            "   .========.   ",
            "  .==########.  ",
            " .=############ ",
            " .=####..####.= ",
            " .=####..####.= ",
            " .=############ ",
            "  .=##########  ",
            "   .========.   ",
            "    .======.    ",
            "     .====.     ",
            "      .==.      ",
            "                ",
            "                "),

    EMERALD(0xff58e089,
            "                ",
            "     .====.     ",
            "    .======.    ",
            "   .========.   ",
            "  .====##====.  ",
            " .====######==. ",
            " .==########==. ",
            " .==########==. ",
            " .==########==. ",
            " .====######==. ",
            "  .====##====.  ",
            "   .========.   ",
            "    .======.    ",
            "     .====.     ",
            "      .==.      ",
            "                "),

    SCROLL(0xffefe0b8,
            "                ",
            "  ============  ",
            " .============. ",
            " .=          =. ",
            " .= ======== =. ",
            " .=          =. ",
            " .= ======== =. ",
            " .=          =. ",
            " .= ======== =. ",
            " .=          =. ",
            " .= ======== =. ",
            " .=          =. ",
            " .============. ",
            "  ============  ",
            "                ",
            "                "),

    RUNE(0xffb89aff,
            "                ",
            "      ====      ",
            "     ======     ",
            "    ==####==    ",
            "   ==######==   ",
            "  ==###..###==  ",
            "  =############ ",
            "  ==###..###==  ",
            "   ==######==   ",
            "    ==####==    ",
            "     ======     ",
            "      ====      ",
            "                ",
            "                ",
            "                ",
            "                "),

    HAMMER(0xffbfb0a0,
            "                ",
            " ============.  ",
            " =##########=.  ",
            " =####..####=.  ",
            " =##########=.  ",
            " ============.  ",
            "     ####       ",
            "     ####       ",
            "     ####       ",
            "     ####       ",
            "     ####       ",
            "     ####       ",
            "     ####       ",
            "     ####       ",
            "                ",
            "                "),

    BOOK(0xffcc9d54,
            "                ",
            " ============.  ",
            " =##########=.  ",
            " =##########=.  ",
            " =############  ",
            " =====##=====.  ",
            " =####==####=.  ",
            " =####==####=.  ",
            " =====##=====.  ",
            " =##########=.  ",
            " =##########=.  ",
            " ============.  ",
            "  ..........    ",
            "                ",
            "                ",
            "                "),

    MAP(0xff9dc5ff,
            "                ",
            " =====   =====  ",
            " =###=. =###=.  ",
            " =####==####=.  ",
            " =##########=.  ",
            " =####..####=.  ",
            " =##.####.##=.  ",
            " =##.####.##=.  ",
            " =##.####.##=.  ",
            " =####..####=.  ",
            " =##########=.  ",
            " =####==####=.  ",
            " =###=. =###=.  ",
            " =====   =====  ",
            "                ",
            "                "),

    FLAG(0xffd9534f,
            "                ",
            " ==             ",
            " ==============.",
            " =############=.",
            " =##..######..= ",
            " =############= ",
            " =##..######..= ",
            " ==============.",
            " ==             ",
            " ==             ",
            " ==             ",
            " ==             ",
            " ==             ",
            " ==             ",
            " ==             ",
            "                ");

    private final int color;
    private final char[][] cells = new char[16][16];

    CommandIcon(int color, String... pattern) {
        this.color = color;
        for (int y = 0; y < 16; y++) {
            String row = y < pattern.length ? pattern[y] : "";
            for (int x = 0; x < 16; x++) {
                cells[y][x] = x < row.length() ? row.charAt(x) : ' ';
            }
        }
    }

    public int color() { return color; }

    public void draw(GuiGraphics g, int x, int y) { draw(g, x, y, 16); }

    /** Render scaled to {@code size} pixels square with a soft drop shadow. */
    public void draw(GuiGraphics g, int x, int y, int size) {
        int cellW = Math.max(1, size / 16);
        int inset = (size - cellW * 16) / 2;
        int shadow = 0xff0a0d18;
        int outline = 0xff17110c;
        int highlight = brighten(color, 0x30);

        for (int row = 0; row < 16; row++) {
            for (int col = 0; col < 16; col++) {
                char c = cells[row][col];
                if (c == ' ') continue;
                int left = x + inset + col * cellW;
                int top = y + inset + row * cellW;
                int right = left + cellW;
                int bottom = top + cellW;

                // Drop shadow for depth.
                g.fill(left + 1, top + 1, right + 1, bottom + 1, shadow);
                int fill;
                switch (c) {
                    case '.': fill = outline; break;
                    case '=': fill = highlight; break;
                    default:  fill = color;
                }
                g.fill(left, top, right, bottom, fill);
            }
        }
    }

    private static int brighten(int argb, int amount) {
        int a = argb & 0xff000000;
        int r = Math.min(255, ((argb >> 16) & 0xff) + amount);
        int gg = Math.min(255, ((argb >> 8) & 0xff) + amount);
        int b = Math.min(255, (argb & 0xff) + amount);
        return a | (r << 16) | (gg << 8) | b;
    }
}
