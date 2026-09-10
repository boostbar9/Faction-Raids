package com.devfarinsky.siegeoverhaul.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.util.HashMap;
import java.util.LinkedHashMap;
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

    /**
     * Per-chunk uploaded texture cache. Uploading one 16x16 texture and
     * blitting it once is drastically cheaper than 256 g.fill() calls per
     * chunk per frame. LRU-capped at 1024 chunks (256 KB of client texture
     * memory worst case) so long sessions don't leak.
     */
    private final LinkedHashMap<Long, ChunkTex> texCache =
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, ChunkTex> e) {
                    if (size() > MAX_CACHED_TEXTURES) {
                        e.getValue().close();
                        return true;
                    }
                    return false;
                }
            };
    private static final int MAX_CACHED_TEXTURES = 1024;

    /**
     * Simple holder so we can free the underlying texture on eviction /
     * reset without leaking a GL handle.
     */
    private static final class ChunkTex {
        final DynamicTexture tex;
        final ResourceLocation loc;
        ChunkTex(DynamicTexture tex, ResourceLocation loc) {
            this.tex = tex;
            this.loc = loc;
        }
        void close() {
            try {
                Minecraft.getInstance().getTextureManager().release(loc);
                tex.close();
            } catch (Exception ignored) {}
        }
    }

    private static int UPLOAD_COUNTER = 0;

    /** Reset any drag state (called on close or on tab switch). */
    public void reset() {
        dragging = false;
        panBlockX = 0;
        panBlockZ = 0;
    }

    /** Drop all cached textures. Called when the screen closes. */
    public void closeTextures() {
        for (ChunkTex ct : texCache.values()) ct.close();
        texCache.clear();
        chunkCache.clear();
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
                for (ChunkTex ct : texCache.values()) ct.close();
                texCache.clear();
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

        // Per-frame sampling budget: sampling is the expensive part, so cap
        // hard. Cached chunks always draw regardless of budget.
        int budget = 8;
        for (int cz = chunkMinZ; cz <= chunkMaxZ; cz++) {
            for (int cx = chunkMinX; cx <= chunkMaxX; cx++) {
                long key = (((long) cx) << 32) | (cz & 0xffffffffL);
                ChunkTex ct = texCache.get(key);
                if (ct == null) {
                    if (budget <= 0) continue;
                    int[] colors = chunkCache.get(key);
                    if (colors == null) {
                        colors = sampleChunk(level, cx, cz);
                        if (colors == null) continue;
                        chunkCache.put(key, colors);
                    }
                    ct = uploadChunkTexture(colors);
                    if (ct == null) continue;
                    texCache.put(key, ct);
                    budget--;
                }
                drawChunkBlit(g, ct, cx, cz, mx, my, mw, mh, camX, camZ, bpp);
            }
        }
    }

    private static ChunkTex uploadChunkTexture(int[] colors) {
        try {
            NativeImage img = new NativeImage(NativeImage.Format.RGBA, 16, 16, false);
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int c = colors[z * 16 + x];
                    // Vanilla NativeImage.setPixelRGBA takes 0xAABBGGRR (little-endian ABGR).
                    int a = (c >>> 24) & 0xff;
                    int r = (c >>> 16) & 0xff;
                    int gc = (c >>> 8) & 0xff;
                    int b = c & 0xff;
                    int abgr = (a << 24) | (b << 16) | (gc << 8) | r;
                    img.setPixelRGBA(x, z, abgr);
                }
            }
            DynamicTexture tex = new DynamicTexture(img);
            ResourceLocation loc = new ResourceLocation("siegeoverhaul",
                    "territory_chunk_" + (UPLOAD_COUNTER++));
            Minecraft.getInstance().getTextureManager().register(loc, tex);
            return new ChunkTex(tex, loc);
        } catch (Exception ex) {
            return null;
        }
    }

    private static void drawChunkBlit(GuiGraphics g, ChunkTex ct,
                                      int cx, int cz,
                                      int mx, int my, int mw, int mh,
                                      double camX, double camZ, int bpp) {
        int screenCenterX = mx + mw / 2;
        int screenCenterY = my + mh / 2;
        int chunkWorldX = cx << 4;
        int chunkWorldZ = cz << 4;
        int sx = screenCenterX + (int) Math.floor((chunkWorldX - camX) / (double) bpp);
        int sy = screenCenterY + (int) Math.floor((chunkWorldZ - camZ) / (double) bpp);
        int tileSize = Math.max(1, 16 / bpp) * 16;
        // Trivial reject if fully off-screen.
        if (sx + tileSize <= mx || sx >= mx + mw) return;
        if (sy + tileSize <= my || sy >= my + mh) return;
        g.blit(ct.loc, sx, sy, 0, 0f, 0f, tileSize, tileSize, tileSize, tileSize);
    }

    /**
     * Sample a loaded chunk's top block colors. Uses the WORLD_SURFACE
     * heightmap so we do at most one blockstate fetch per column (256 per
     * chunk) instead of scanning the entire build height (256 * ~320).
     * Returns null if the chunk isn't loaded.
     */
    private static int[] sampleChunk(Level level, int cx, int cz) {
        LevelChunk chunk;
        try {
            if (!((LevelReader) level).hasChunk(cx, cz)) return null;
            chunk = level.getChunk(cx, cz);
        } catch (Exception ex) {
            return null;
        }
        int minY = level.getMinBuildHeight();
        int[] out = new int[16 * 16];
        var pos = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int worldX = (cx << 4) + x;
                int worldZ = (cz << 4) + z;
                int surfaceY;
                try {
                    surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                } catch (Exception ex) {
                    surfaceY = minY;
                }
                if (surfaceY < minY) surfaceY = minY;
                pos.set(worldX, surfaceY, worldZ);
                int color = 0xff0a1219;
                var state = chunk.getBlockState(pos);
                if (state.isAir()) {
                    // Rare but possible: heightmap can point at air above a fluid.
                    // Try one step down.
                    pos.setY(surfaceY - 1);
                    state = chunk.getBlockState(pos);
                }
                MapColor mc = state.getMapColor(level, pos);
                if (mc != MapColor.NONE) {
                    color = 0xff000000 | mc.calculateRGBColor(MapColor.Brightness.NORMAL);
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
