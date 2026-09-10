package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.core.CoreHiring;
import com.devfarinsky.siegeoverhaul.core.CoreOffers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

/**
 * Renders the actual Recruits / Workers mob for a given hire role inside a GUI
 * portrait tile, so players see the real character they are about to hire.
 *
 * <p>The mob is created once per role on the client side, cached, and drawn
 * with {@link InventoryScreen#renderEntityInInventoryFollowsMouse} so the head
 * gently tracks the cursor. If creation fails (missing dep, strict client-only
 * ctor in another mod, etc.), we fall back to the procedural
 * {@link RolePortrait} so a hire card is never empty.
 *
 * <p>Role indices follow {@link CoreHiring#IDS}. Heroes 10-13 remap to the
 * matching hero entity id (indices 0-3 in the recruits registry).
 */
public final class EntityPortrait {
    private EntityPortrait() {}

    /** Cached client-only instances keyed by role index. Only touched from the render thread. */
    private static final Map<Integer, LivingEntity> CACHE = new HashMap<>();
    /** Roles that failed to create so we do not retry every frame. */
    private static final java.util.Set<Integer> FAILED = new java.util.HashSet<>();

    /**
     * Draw the mob for {@code role} centred on the given square. Falls back to
     * {@link RolePortrait} when the entity cannot be constructed on the client.
     */
    public static void draw(GuiGraphics g, int role, int x, int y, int size,
                            float mouseX, float mouseY) {
        // Card backdrop drawn identically to RolePortrait so both paths line up.
        g.fill(x, y, x + size, y + size, 0xff0e0906);
        g.fillGradient(x + 1, y + 1, x + size - 1, y + size - 1,
                0xff2a2016, 0xff130f0c);
        int b = 0xff4a3826;
        g.fill(x, y, x + size, y + 1, b);
        g.fill(x, y + size - 1, x + size, y + size, b);
        g.fill(x, y, x + 1, y + size, b);
        g.fill(x + size - 1, y, x + size, y + size, b);

        LivingEntity entity = getOrCreate(role);
        if (entity == null) {
            // Failure path: draw the procedural bust inside the same frame.
            RolePortrait.draw(g, role, x, y, size);
            return;
        }

        // Aureole glow for heroes so they read as legendary.
        if (role >= 10) {
            int gx = x + size / 2;
            int gy = y + size / 2;
            for (int r = size / 2; r >= 1; r--) {
                int alpha = Math.max(3, 40 - r * 3);
                int color = (alpha << 24) | 0xffe4a8;
                g.fill(gx - r, gy - r, gx + r, gy + r, color);
            }
        }

        int cx = x + size / 2;
        int cy = y + size - Math.max(4, size / 12);
        // Scale so a two-block-tall mob fits comfortably inside the tile.
        int scale = Math.max(12, (int) (size / 2.6));
        try {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx, cy, scale,
                    (cx - mouseX), (cy - size * 0.6f - mouseY), entity);
        } catch (Throwable t) {
            // Any renderer NPE from a mod with strict client init: fall back.
            FAILED.add(role);
            CACHE.remove(role);
            RolePortrait.draw(g, role, x, y, size);
        }
    }

    private static LivingEntity getOrCreate(int role) {
        if (FAILED.contains(role)) return null;
        LivingEntity cached = CACHE.get(role);
        if (cached != null) return cached;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return null;

        int typeRole = role >= 10 ? role - 10 : role;
        if (typeRole < 0 || typeRole >= CoreHiring.IDS.length) {
            FAILED.add(role);
            return null;
        }
        String namespace = typeRole < CoreOffers.WORKER_START ? "recruits" : "workers";
        String path = CoreHiring.IDS[typeRole];
        ResourceLocation id = new ResourceLocation(namespace, path);
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
        if (type == null) {
            FAILED.add(role);
            return null;
        }
        try {
            Entity created = type.create(level);
            if (!(created instanceof LivingEntity living)) {
                if (created != null) created.discard();
                FAILED.add(role);
                return null;
            }
            // Position at origin; the GUI renderer ignores world position.
            living.moveTo(0, 0, 0, 0, 0);
            living.setYRot(0);
            living.setYHeadRot(0);
            if (role >= 10) {
                living.getPersistentData().putBoolean("SiegeHiredHero", true);
            }
            CACHE.put(role, living);
            return living;
        } catch (Throwable t) {
            FAILED.add(role);
            return null;
        }
    }

    /** Called on screen close so cached entities do not keep client resources alive between opens. */
    public static void clear() {
        CACHE.clear();
        FAILED.clear();
    }
}
