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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

        // Position the entity so its full body (head to feet) fits inside the
        // tile with a small margin. The vanilla renderer uses cy as the FOOT
        // position and scale as pixels-per-block-height, so a 1.95-block-tall
        // mob rendered at scale S occupies ~1.95*S pixels vertically. Keep
        // 6 px of padding top and bottom.
        int padding = Math.max(3, size / 12);
        int cx = x + size / 2;
        int cy = y + size - padding;
        int usableHeight = size - padding * 2;
        // 1.95 blocks tall (player-height) -> divide by ~2 for scale.
        int scale = Math.max(8, (int) (usableHeight / 2.05f));
        try {
            // Cap head-tracking offset so mice at screen edges do not spin the
            // model wildly; only track when the cursor is near the portrait.
            float lookX = Math.max(-40f, Math.min(40f, cx - mouseX));
            float lookY = Math.max(-40f, Math.min(40f, cy - size * 0.7f - mouseY));
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx, cy, scale,
                    lookX, lookY, entity);
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
            // Client-side entities never receive the server's spawn loadout,
            // so equip a role-appropriate vanilla kit here so the portrait
            // matches the printed "Kit:" line on the hire card. Cosmetic only:
            // this instance never sees combat.
            applyKit(living, role);
            CACHE.put(role, living);
            return living;
        } catch (Throwable t) {
            FAILED.add(role);
            return null;
        }
    }

    /**
     * Equip a display-only vanilla loadout so the portrait mob actually looks
     * like the printed kit. Uses only vanilla items so it works whether or not
     * downstream mods (Epic Knights, Musket, etc.) are present.
     */
    private static void applyKit(LivingEntity e, int role) {
        // Helmet + body per role. Heroes get netherite so they read as elite.
        ItemStack head = ItemStack.EMPTY;
        ItemStack chest = ItemStack.EMPTY;
        ItemStack legs = ItemStack.EMPTY;
        ItemStack feet = ItemStack.EMPTY;
        ItemStack main = ItemStack.EMPTY;
        ItemStack off = ItemStack.EMPTY;
        switch (role) {
            case 0 -> { // Recruit
                head = new ItemStack(Items.LEATHER_HELMET);
                chest = new ItemStack(Items.LEATHER_CHESTPLATE);
                main = new ItemStack(Items.IRON_SWORD);
                off = new ItemStack(Items.SHIELD);
            }
            case 1 -> { // Shieldman
                head = new ItemStack(Items.IRON_HELMET);
                chest = new ItemStack(Items.IRON_CHESTPLATE);
                legs = new ItemStack(Items.IRON_LEGGINGS);
                main = new ItemStack(Items.IRON_SWORD);
                off = new ItemStack(Items.SHIELD);
            }
            case 2 -> { // Archer
                head = new ItemStack(Items.LEATHER_HELMET);
                chest = new ItemStack(Items.LEATHER_CHESTPLATE);
                main = new ItemStack(Items.BOW);
            }
            case 3 -> { // Crossbowman
                head = new ItemStack(Items.CHAINMAIL_HELMET);
                chest = new ItemStack(Items.CHAINMAIL_CHESTPLATE);
                main = new ItemStack(Items.CROSSBOW);
            }
            case 4 -> { // Farmer
                main = new ItemStack(Items.IRON_HOE);
                off = new ItemStack(Items.WHEAT_SEEDS);
            }
            case 5 -> { // Lumberjack
                head = new ItemStack(Items.LEATHER_HELMET);
                main = new ItemStack(Items.IRON_AXE);
            }
            case 6 -> { // Miner
                head = new ItemStack(Items.IRON_HELMET);
                main = new ItemStack(Items.IRON_PICKAXE);
                off = new ItemStack(Items.TORCH);
            }
            case 7 -> { // Builder
                head = new ItemStack(Items.LEATHER_HELMET);
                main = new ItemStack(Items.OAK_PLANKS);
            }
            case 8 -> { // Cook
                main = new ItemStack(Items.IRON_SWORD);
                off = new ItemStack(Items.BREAD);
            }
            case 9 -> { // Courier
                feet = new ItemStack(Items.LEATHER_BOOTS);
                main = new ItemStack(Items.FILLED_MAP);
            }
            case 10 -> { // Kael Bloodthorn (Warblade)
                head = new ItemStack(Items.NETHERITE_HELMET);
                chest = new ItemStack(Items.NETHERITE_CHESTPLATE);
                legs = new ItemStack(Items.NETHERITE_LEGGINGS);
                feet = new ItemStack(Items.NETHERITE_BOOTS);
                main = new ItemStack(Items.NETHERITE_SWORD);
            }
            case 11 -> { // Branna Dawnwarden (Bulwark)
                head = new ItemStack(Items.NETHERITE_HELMET);
                chest = new ItemStack(Items.NETHERITE_CHESTPLATE);
                legs = new ItemStack(Items.NETHERITE_LEGGINGS);
                feet = new ItemStack(Items.NETHERITE_BOOTS);
                main = new ItemStack(Items.NETHERITE_AXE);
                off = new ItemStack(Items.SHIELD);
            }
            case 12 -> { // Sylva Stormbow (Archer hero)
                head = new ItemStack(Items.NETHERITE_HELMET);
                chest = new ItemStack(Items.NETHERITE_CHESTPLATE);
                legs = new ItemStack(Items.NETHERITE_LEGGINGS);
                feet = new ItemStack(Items.NETHERITE_BOOTS);
                main = new ItemStack(Items.BOW);
            }
            case 13 -> { // Orin Frostbinder (Crossbow hero)
                head = new ItemStack(Items.NETHERITE_HELMET);
                chest = new ItemStack(Items.NETHERITE_CHESTPLATE);
                legs = new ItemStack(Items.NETHERITE_LEGGINGS);
                feet = new ItemStack(Items.NETHERITE_BOOTS);
                main = new ItemStack(Items.CROSSBOW);
            }
            default -> { /* leave bare */ }
        }
        try {
            if (!head.isEmpty()) e.setItemSlot(EquipmentSlot.HEAD, head);
            if (!chest.isEmpty()) e.setItemSlot(EquipmentSlot.CHEST, chest);
            if (!legs.isEmpty()) e.setItemSlot(EquipmentSlot.LEGS, legs);
            if (!feet.isEmpty()) e.setItemSlot(EquipmentSlot.FEET, feet);
            if (!main.isEmpty()) e.setItemSlot(EquipmentSlot.MAINHAND, main);
            if (!off.isEmpty()) e.setItemSlot(EquipmentSlot.OFFHAND, off);
        } catch (Throwable t) {
            // Some modded entities override setItemSlot with strict checks;
            // ignore, portrait still renders without the kit.
        }
    }

    /** Called on screen close so cached entities do not keep client resources alive between opens. */
    public static void clear() {
        CACHE.clear();
        FAILED.clear();
    }
}
