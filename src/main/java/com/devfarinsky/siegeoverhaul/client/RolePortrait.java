package com.devfarinsky.siegeoverhaul.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Procedural pixel-art bust portraits for the recruit / worker / hero roles
 * shown on the command center hire cards.
 *
 * <p>Each portrait is a 22x22 grid; the color palette is per-role (skin,
 * clothing, helmet, hair, accent) with a shared parchment backdrop and inset
 * frame. Rendering is pure rectangle fills — no external textures, no per-frame
 * allocations. This keeps the portraits sharp at every GUI scale and avoids
 * needing to spawn a client-side {@link net.minecraft.world.entity.LivingEntity}
 * (which can misbehave with other mods that add strict client-only init).
 *
 * <p>Role indices follow {@link com.devfarinsky.siegeoverhaul.core.CoreHiring#IDS}:
 * <pre>
 *   0  Recruit         5  Lumberjack     10 Kael Bloodthorn (hero)
 *   1  Shieldman       6  Miner          11 Branna Dawnwarden (hero)
 *   2  Archer          7  Builder        12 Sylva Stormbow (hero)
 *   3  Crossbowman     8  Cook           13 Orin Frostbinder (hero)
 *   4  Farmer          9  Courier
 * </pre>
 */
public final class RolePortrait {
    private RolePortrait() {}

    private static final int SIZE = 22;

    // Silhouette bit-pattern for a generic humanoid bust: head, shoulders, chest.
    // Each string is 22 chars wide; characters are keys into per-role palettes:
    //   ' ' transparent    'S' skin       'H' hair
    //   'A' accent         'C' cloth      'M' metal / helmet
    //   'L' leather        'B' belt       'F' feature (eye/mouth)
    private static final String[] BUST = {
            "                      ",
            "                      ",
            "         HHHH         ",
            "        HMMMMH        ",
            "       HMMMMMMH       ",
            "       HMSSSSMH       ",
            "       MSSSSSSM       ",
            "       MSFSSFSM       ",
            "       MSSSSSSM       ",
            "       MSSMMSSM       ",
            "        SSMMSS        ",
            "        SSMMSS        ",
            "       LCCLLCCL       ",
            "      LCCCLLCCCL      ",
            "     LCCCCLLCCCCL     ",
            "    LCCCCCLLCCCCCL    ",
            "   LCCCCCCLLCCCCCCL   ",
            "  LCCCCCCCLLCCCCCCCL  ",
            "  LCCCBBBBBBBBBBCCCL  ",
            "  LCCCBAAAAAAAABCCCL  ",
            "  LLCCCBBBBBBBBCCCLL  ",
            "                      ",
    };

    /** Per-role palette. Order matches the role index. */
    private static int[] palette(int role) {
        // Return { skin, hair, cloth, leather, metal, accent, belt, feature }
        return switch (role) {
            case 0 -> pal(0xffe3b592, 0xff6b4a2c, 0xff5a5f47, 0xff6a4a2a, 0xff97a1a8, 0xffe8c968, 0xff3d2a1a, 0xff1e120a); // Recruit
            case 1 -> pal(0xffdda58a, 0xff3a2818, 0xff34445c, 0xff5a3f28, 0xffcbd0d5, 0xff9dc5ff, 0xff2a1c10, 0xff17110c); // Shieldman
            case 2 -> pal(0xffd8b48e, 0xff6a3d1d, 0xff4a5e3a, 0xff523a24, 0xffbfb0a0, 0xff8fbf6f, 0xff2a1c10, 0xff17110c); // Archer
            case 3 -> pal(0xffe0b489, 0xff7a542a, 0xff5c3e4d, 0xff4a3220, 0xffb2b8bf, 0xffb89aff, 0xff2a1c10, 0xff17110c); // Crossbowman
            case 4 -> pal(0xffe8ba8c, 0xff8b6b3a, 0xff9c8148, 0xff6a4a2a, 0xffe8c968, 0xff58e089, 0xff5a3d1c, 0xff17110c); // Farmer
            case 5 -> pal(0xffd9a781, 0xff4a2e18, 0xff5a3a24, 0xff6b4020, 0xffbfb0a0, 0xff58e089, 0xff3a260f, 0xff17110c); // Lumberjack
            case 6 -> pal(0xffd0a380, 0xff3a2818, 0xff3a3e4a, 0xff4a3320, 0xffb2b8bf, 0xffe8c968, 0xff2a1c10, 0xff17110c); // Miner
            case 7 -> pal(0xffdfae83, 0xff5a3a20, 0xff8a6a40, 0xff6a4a24, 0xffbfb0a0, 0xffe8c968, 0xff3a260f, 0xff17110c); // Builder
            case 8 -> pal(0xffe8ba90, 0xff4a3020, 0xffefe0b8, 0xff8b6a3a, 0xffcbd0d5, 0xffd9534f, 0xff5a3d1c, 0xff17110c); // Cook
            case 9 -> pal(0xffddb18c, 0xff6a3f22, 0xff4a3a5c, 0xff5a3d24, 0xffbfb0a0, 0xffe8c968, 0xff2a1c10, 0xff17110c); // Courier
            case 10 -> pal(0xffcaa079, 0xff2a1610, 0xff7a1e1e, 0xff5a2016, 0xff4a3226, 0xffff5c3a, 0xff1e0f0a, 0xffff8f4a); // Kael Bloodthorn
            case 11 -> pal(0xfff2caa5, 0xffefe0b8, 0xffe8c968, 0xff9c7a3a, 0xfff5efd8, 0xffffe4a8, 0xff8b6a3a, 0xff17110c); // Branna Dawnwarden
            case 12 -> pal(0xffd8b48e, 0xff2a3a56, 0xff2a4a5c, 0xff3a4a5a, 0xff8faabf, 0xff9dc5ff, 0xff1e2a3a, 0xff17110c); // Sylva Stormbow
            case 13 -> pal(0xffcadde8, 0xffe0eaff, 0xff2a3a5c, 0xff3a4a6a, 0xff9dc5ff, 0xffb89aff, 0xff1e2a3a, 0xff81e8da); // Orin Frostbinder
            default -> pal(0xffb89968, 0xff3a2818, 0xff4a4a4a, 0xff3a2818, 0xffb2b8bf, 0xffe8c968, 0xff2a1c10, 0xff17110c);
        };
    }

    private static int[] pal(int skin, int hair, int cloth, int leather,
                             int metal, int accent, int belt, int feature) {
        return new int[]{skin, hair, cloth, leather, metal, accent, belt, feature};
    }

    /** Draw the portrait into the given square. Size is clamped to a multiple of 22 pixel cells. */
    public static void draw(GuiGraphics g, int role, int x, int y, int size) {
        int cell = Math.max(1, size / SIZE);
        int inset = (size - cell * SIZE) / 2;

        int[] p = palette(role);
        int skin = p[0], hair = p[1], cloth = p[2], leather = p[3];
        int metal = p[4], accent = p[5], belt = p[6], feature = p[7];

        // Backing plate: subtle parchment gradient inside a dark card.
        g.fill(x, y, x + size, y + size, 0xff0e0906);
        g.fillGradient(x + 1, y + 1, x + size - 1, y + size - 1,
                0xff2a2016, 0xff130f0c);

        // Aureole glow behind heads for heroes so they read as important.
        if (role >= 10) {
            int gx = x + size / 2;
            int gy = y + inset + cell * 6;
            for (int r = size / 3; r >= 1; r--) {
                int alpha = Math.max(4, 40 - r * 3);
                int color = (alpha << 24) | (accent & 0xffffff);
                g.fill(gx - r, gy - r, gx + r, gy + r, color);
            }
        }

        for (int row = 0; row < SIZE; row++) {
            String line = BUST[row];
            for (int col = 0; col < SIZE; col++) {
                char c = line.charAt(col);
                if (c == ' ') continue;
                int color = switch (c) {
                    case 'S' -> skin;
                    case 'H' -> hair;
                    case 'C' -> cloth;
                    case 'L' -> leather;
                    case 'M' -> metal;
                    case 'A' -> accent;
                    case 'B' -> belt;
                    case 'F' -> feature;
                    default -> 0xff000000;
                };
                int px = x + inset + col * cell;
                int py = y + inset + row * cell;
                g.fill(px, py, px + cell, py + cell, color);
            }
        }

        // Role-specific overlays: helmet plume for hero, hat for farmer,
        // hood for courier, tool silhouette on the shoulder for workers.
        drawRoleAccent(g, role, x + inset, y + inset, cell, accent, metal, hair);

        // Thin inner border for a framed-portrait feel.
        int b = 0xff4a3826;
        g.fill(x, y, x + size, y + 1, b);
        g.fill(x, y + size - 1, x + size, y + size, b);
        g.fill(x, y, x + 1, y + size, b);
        g.fill(x + size - 1, y, x + size, y + size, b);
    }

    private static void drawRoleAccent(GuiGraphics g, int role,
                                       int originX, int originY, int cell,
                                       int accent, int metal, int hair) {
        switch (role) {
            case 4 -> { // Farmer straw hat brim
                fill(g, originX, originY, cell, 3, 0, 12, 0xffd7b25f);
                fill(g, originX, originY, cell, 5, 1, 10, 0xffefc86f);
            }
            case 5 -> { // Lumberjack cap
                fill(g, originX, originY, cell, 6, 2, 10, 0xff6a2a20);
                fill(g, originX, originY, cell, 8, 4, 6, 0xffaa3a30);
            }
            case 6 -> { // Miner helmet lamp
                fill(g, originX, originY, cell, 6, 4, 5, 0xffe8c968);
                fill(g, originX, originY, cell, 7, 4, 5, 0xfffff2c8);
            }
            case 7 -> { // Builder tool handle over shoulder
                fill(g, originX, originY, cell, 11, 14, 3, 0xff6a4a2a);
                fill(g, originX, originY, cell, 12, 14, 3, 0xffb08a52);
                fill(g, originX, originY, cell, 13, 14, 3, 0xff6a4a2a);
            }
            case 8 -> { // Cook chef hat
                fill(g, originX, originY, cell, 2, 6, 10, 0xfffff2e0);
                fill(g, originX, originY, cell, 3, 5, 12, 0xfffff2e0);
            }
            case 9 -> { // Courier hood peak
                fill(g, originX, originY, cell, 2, 8, 6, 0xff4a3a5c);
                fill(g, originX, originY, cell, 3, 7, 8, 0xff4a3a5c);
            }
            case 1, 3 -> { // Shieldman / Crossbowman helmet crest
                fill(g, originX, originY, cell, 2, 10, 2, accent);
                fill(g, originX, originY, cell, 3, 10, 2, accent);
            }
            case 2 -> { // Archer hood
                fill(g, originX, originY, cell, 2, 7, 8, 0xff4a5e3a);
                fill(g, originX, originY, cell, 3, 6, 10, 0xff4a5e3a);
            }
            case 10 -> { // Kael horned helmet
                fill(g, originX, originY, cell, 2, 6, 2, metal);
                fill(g, originX, originY, cell, 3, 5, 2, metal);
                fill(g, originX, originY, cell, 2, 14, 2, metal);
                fill(g, originX, originY, cell, 3, 15, 2, metal);
                // Red plume
                fill(g, originX, originY, cell, 1, 10, 2, accent);
            }
            case 11 -> { // Branna radiant crown
                fill(g, originX, originY, cell, 2, 8, 6, accent);
                fill(g, originX, originY, cell, 3, 7, 2, accent);
                fill(g, originX, originY, cell, 3, 10, 2, accent);
                fill(g, originX, originY, cell, 3, 13, 2, accent);
            }
            case 12 -> { // Sylva feathered arrow behind shoulder
                fill(g, originX, originY, cell, 10, 15, 4, 0xff9dc5ff);
                fill(g, originX, originY, cell, 11, 14, 5, 0xffcbd0d5);
                fill(g, originX, originY, cell, 12, 15, 4, 0xff9dc5ff);
            }
            case 13 -> { // Orin frost rune floating beside head
                fill(g, originX, originY, cell, 4, 2, 2, 0xff81e8da);
                fill(g, originX, originY, cell, 5, 2, 2, 0xffb89aff);
                fill(g, originX, originY, cell, 4, 18, 2, 0xff81e8da);
                fill(g, originX, originY, cell, 5, 18, 2, 0xffb89aff);
            }
            default -> {}
        }
    }

    private static void fill(GuiGraphics g, int ox, int oy, int cell,
                             int row, int col, int cols, int color) {
        int x = ox + col * cell;
        int y = oy + row * cell;
        g.fill(x, y, x + cols * cell, y + cell, color);
    }
}
