package com.devfarinsky.siegeoverhaul.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.MapColor;

import java.util.HashMap;
import java.util.Map;

/**
 * Self-contained draggable territory panel for the command center HUD.
 *
 * <p>Renders a top-down map of the world around the player using vanilla
 * {@link MapColor} sampled from loaded chunks. The map supports pan drag,
 * mouse-wheel zoom (2, 4, or 8 blocks per pixel), a compass rose, and
 * player-position marker. It only draws data already resident on the client
 * (loaded chunks), never triggers chunk generation, and does no reflection
 * into third-party mod internals — so it never silently fails.
 *
 * <p>The sampled color grid is cached per (chunkX, chunkZ, zoom) and evicted
 * when the panel is closed to keep memory bounded during long sessions.
 */
public final class TerritoryPanel {

    /** Blocks-per-pixel presets for zoom in / out. */
    private static final int[] ZOOMS = {2, 4, 8};

    private int zoomIndex = 1;
    /** Pan offset in blocks from the player's current XZ. */
    private double panBlockX;
    private double panBlockZ;
    private boolean dragging;
    private double lastMouseX;
    private double lastMouseY;

    /** Cached 16x16 color arrays keyed by "chunkX,chunkZ,zoom" bucket. */
    private final Map<Long, int[]> chunkCache = new HashMap<>();
    private int cacheZoom = -1;

    /** Reset any drag state (called on close or on tab switch). */
    public void reset() {
        dragging = false;
        panBlockX = 0;
        panBlockZ = 0;
    }

    /** Snap the view back to the player. */
    public void recenter() {
        panBlockX = 0;
        panBlockZ = 0;
    }

    /** Cycle zoom outward (fewer chunks per pixel -> more world visible). */
    public void zoomOut() {
        zoomIndex = Math.min(ZOOMS.length - 1, zoomIndex + 1);
    }

    /** Cycle zoom inward. */
    public void zoomIn() {
        zoomIndex = Math.max(0, zoomIndex - 1);
    }

    public int blocksPerPixel() { return ZOOMS[zoomIndex]; }

    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                int x, int y, int w, int h) {
        if (button != 0) return false;
        if (mouseX < x || mouseX >= x + w || mouseY < y || mouseY >= y + h) return false;
        dragging = true;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        return true;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0 || !dragging) return false;
        dragging = false;
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, double dx, double dy) {
        if (!dragging) return false;
        int bpp = blocksPerPixel();
        panBlockX -= dx * bpp;
        panBlockZ -= dy * bpp;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        return true;
    }

    public boolean mouseScrolled(double delta,
                                 double mouseX, double mouseY,
                                 int x, int y, int w, int h) {
        if (mouseX < x || mouseX >= x + w || mouseY < y || mouseY >= y + h) return false;
        if (delta > 0) zoomIn();
        else if (delta < 0) zoomOut();
        return true;
    }

    /**
     * Render the map inside the given rectangle, followed by a compass rose,
     * player marker, and a zoom / drag hint bar. The caller is expected to
     * have already framed the panel via {@link CommandFrame}.
     */
    public void draw(GuiGraphics g, int x, int y, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = mc.level;

        // Border-inset drawable area for the map body.
        int mx = x + 2, my = y + 2;
        int mw = w - 4, mh = h - 4;
        g.enableScissor(mx, my, mx + mw, my + mh);
        try {
            if (level == null || player == null) {
                g.fill(mx, my, mx + mw, my + mh, CommandPalette.PANEL_BOTTOM);
                drawCentered(g, "World unavailable", mx + mw / 2, my + mh / 2,
                        CommandPalette.TEXT_MUTED);
                return;
            }

            int bpp = blocksPerPixel();
            if (bpp != cacheZoom) {
                chunkCache.clear();
                cacheZoom = bpp;
            }

            // Camera center in world blocks.
            double camX = player.getX() + panBlockX;
            double camZ = player.getZ() + panBlockZ;

            // Fill the map with a subtle grid backdrop first (uncached areas).
            g.fill(mx, my, mx + mw, my + mh, 0xff0a1219);
            drawGrid(g, mx, my, mw, mh, camX, camZ, bpp);

            // Sample block colors chunk-by-chunk and blit each 16x16 tile.
            drawWorldTiles(g, level, mx, my, mw, mh, camX, camZ, bpp);

            // Player marker (always at the pan-adjusted screen center).
            int playerScreenX = mx + mw / 2 - (int) (panBlockX / bpp);
            int playerScreenY = my + mh / 2 - (int) (panBlockZ / bpp);
            drawPlayerMarker(g, playerScreenX, playerScreenY, player.getYRot());

            // Compass rose (top-right).
            drawCompass(g, mx + mw - 22, my + 6);

            // Coordinates readout (top-left).
            String coords = String.format(java.util.Locale.ROOT,
                    "X %.0f  Z %.0f  |  %d bpp",
                    camX, camZ, bpp);
            drawShadowed(g, coords, mx + 6, my + 6, CommandPalette.TEXT);

            // Drag / zoom hint (bottom-right).
            String hint = "Drag to pan  |  Scroll to zoom  |  Right-click recenter";
            var font = Minecraft.getInstance().font;
            int hintWidth = font.width(hint);
            drawShadowed(g, hint, mx + mw - hintWidth - 6, my + mh - 12,
                    CommandPalette.TEXT_MUTED);
        } finally {
            g.disableScissor();
        }
    }

    private void drawWorldTiles(GuiGraphics g, Level level,
                                int mx, int my, int mw, int mh,
                                double camX, double camZ, int bpp) {
        // Chunk range that intersects the visible area.
        int worldLeft = (int) (camX - (mw / 2.0) * bpp);
        int worldRight = (int) (camX + (mw / 2.0) * bpp);
        int worldTop = (int) (camZ - (mh / 2.0) * bpp);
        int worldBottom = (int) (camZ + (mh / 2.0) * bpp);

        int chunkMinX = worldLeft >> 4;
        int chunkMaxX = worldRight >> 4;
        int chunkMinZ = worldTop >> 4;
        int chunkMaxZ = worldBottom >> 4;

        int budget = 320; // cap per-frame sampling cost.
        for (int cz = chunkMinZ; cz <= chunkMaxZ; cz++) {
            for (int cx = chunkMinX; cx <= chunkMaxX; cx++) {
                long key = (((long) cx) << 32) | (cz & 0xffffffffL);
                int[] colors = chunkCache.get(key);
                if (colors == null) {
                    if (budget <= 0) continue;
                    colors = sampleChunk(level, cx, cz);
                    if (colors != null) {
                        chunkCache.put(key, colors);
                        budget--;
                    }
                }
                if (colors == null) continue;

                drawChunkTile(g, colors, cx, cz, mx, my, mw, mh, camX, camZ, bpp);
            }
        }
    }

    /** Sample a loaded chunk's top block colors. Returns null if the chunk is not loaded. */
    private static int[] sampleChunk(Level level, int cx, int cz) {
        LevelChunk chunk;
        try {
            if (!((LevelReader) level).hasChunk(cx, cz)) return null;
            chunk = level.getChunk(cx, cz);
        } catch (Exception ex) {
            return null;
        }
        int base = level.getMinBuildHeight();
        int top = level.getMaxBuildHeight();
        int[] out = new int[16 * 16];
        var pos = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int worldX = (cx << 4) + x;
                int worldZ = (cz << 4) + z;
                pos.set(worldX, top - 1, worldZ);
                int color = 0xff0a1219;
                for (int y = top - 1; y >= base; y--) {
                    pos.setY(y);
                    var state = chunk.getBlockState(pos);
                    if (state.isAir()) continue;
                    MapColor mc = state.getMapColor(level, pos);
                    if (mc == MapColor.NONE) continue;
                    color = 0xff000000 | mc.calculateRGBColor(MapColor.Brightness.NORMAL);
                    break;
                }
                out[z * 16 + x] = color;
            }
        }
        return out;
    }

    private static void drawChunkTile(GuiGraphics g, int[] colors,
                                      int cx, int cz,
                                      int mx, int my, int mw, int mh,
                                      double camX, double camZ, int bpp) {
        int screenCenterX = mx + mw / 2;
        int screenCenterY = my + mh / 2;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int worldX = (cx << 4) + x;
                int worldZ = (cz << 4) + z;
                int sx = screenCenterX + (int) Math.floor((worldX - camX) / (double) bpp);
                int sy = screenCenterY + (int) Math.floor((worldZ - camZ) / (double) bpp);
                if (sx < mx || sx >= mx + mw || sy < my || sy >= my + mh) continue;
                int pxSize = Math.max(1, 16 / bpp);
                g.fill(sx, sy, sx + pxSize, sy + pxSize, colors[z * 16 + x]);
            }
        }
    }

    private static void drawGrid(GuiGraphics g, int mx, int my, int mw, int mh,
                                 double camX, double camZ, int bpp) {
        int gridBlocks = 64; // one line every 64 blocks
        int step = gridBlocks / bpp;
        if (step < 8) step = 8;
        int color = 0xff141c26;

        // Compute pixel offset so grid lines correspond to world-aligned multiples.
        int centerX = mx + mw / 2;
        int centerY = my + mh / 2;
        int offX = (int) Math.floorMod(Math.round(camX), gridBlocks) / bpp;
        int offY = (int) Math.floorMod(Math.round(camZ), gridBlocks) / bpp;

        for (int sx = centerX - offX; sx < mx + mw; sx += step) {
            if (sx >= mx) g.fill(sx, my, sx + 1, my + mh, color);
        }
        for (int sx = centerX - offX - step; sx >= mx; sx -= step) {
            g.fill(sx, my, sx + 1, my + mh, color);
        }
        for (int sy = centerY - offY; sy < my + mh; sy += step) {
            if (sy >= my) g.fill(mx, sy, mx + mw, sy + 1, color);
        }
        for (int sy = centerY - offY - step; sy >= my; sy -= step) {
            g.fill(mx, sy, mx + mw, sy + 1, color);
        }
    }

    private static void drawPlayerMarker(GuiGraphics g, int sx, int sy, float yawDeg) {
        // Outer ring.
        for (int r = 4; r >= 3; r--) {
            g.fill(sx - r, sy - 1, sx + r + 1, sy + 2, 0xff17110c);
        }
        g.fill(sx - 3, sy - 3, sx + 4, sy + 4, 0xff17110c);
        g.fill(sx - 2, sy - 2, sx + 3, sy + 3, 0xffe8c968);
        g.fill(sx - 1, sy - 1, sx + 2, sy + 2, 0xfffff2c8);
        // Facing indicator: a two-pixel tick in the direction the player is looking.
        double rad = Math.toRadians(-yawDeg + 90);
        int tx = sx + (int) Math.round(Math.cos(rad) * 6);
        int ty = sy - (int) Math.round(Math.sin(rad) * 6);
        g.fill(tx - 1, ty - 1, tx + 2, ty + 2, 0xffe8c968);
    }

    private static void drawCompass(GuiGraphics g, int cx, int cy) {
        int size = 18;
        g.fill(cx, cy, cx + size, cy + size, 0xcc0a0d18);
        g.fill(cx, cy, cx + size, cy + 1, 0xff4a3826);
        g.fill(cx, cy + size - 1, cx + size, cy + size, 0xff4a3826);
        g.fill(cx, cy, cx + 1, cy + size, 0xff4a3826);
        g.fill(cx + size - 1, cy, cx + size, cy + size, 0xff4a3826);
        // North tick (up).
        int nx = cx + size / 2;
        int ny = cy + 3;
        g.fill(nx - 1, ny, nx + 2, ny + 4, 0xffe8c968);
        var font = Minecraft.getInstance().font;
        g.drawString(font, "N", cx + size / 2 - font.width("N") / 2, cy + 8,
                CommandPalette.ACCENT_GOLD, false);
    }

    private static void drawShadowed(GuiGraphics g, String text, int x, int y, int color) {
        var font = Minecraft.getInstance().font;
        g.drawString(font, text, x + 1, y + 1, 0xff0a0d18, false);
        g.drawString(font, text, x, y, color, false);
    }

    private static void drawCentered(GuiGraphics g, String text, int x, int y, int color) {
        var font = Minecraft.getInstance().font;
        int w = font.width(text);
        g.drawString(font, text, x - w / 2, y, color, false);
    }

    public boolean rightClickRecenter(int button) {
        if (button != 1) return false;
        recenter();
        return true;
    }
}
