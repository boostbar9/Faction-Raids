package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.core.CoreHiring;
import com.devfarinsky.siegeoverhaul.core.CoreOffers;
import com.devfarinsky.siegeoverhaul.core.HeroTraits;
import com.devfarinsky.siegeoverhaul.core.RecruitPersonality;
import com.devfarinsky.siegeoverhaul.core.WorkerStartingKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
 * ctor in another mod, etc.), we fall back to the role's real Minecraft item
 * sprite so the card stays understandable without showing a fake avatar.
 *
 * <p>Role indices follow {@link CoreHiring#IDS}. Every hero role remaps through
 * {@link CoreHiring#heroBase(int)} to its matching Recruits entity type.
 */
public final class EntityPortrait {
    private EntityPortrait() {}

    /** Cached client-only instances keyed by role index. Only touched from the render thread. */
    private static final Map<Integer, LivingEntity> CACHE = new HashMap<>();
    /** Roles that failed to create so we do not retry every frame. */
    private static final java.util.Set<Integer> FAILED = new java.util.HashSet<>();

    private static final Map<Integer, ItemStack[]> KITS = new HashMap<>();
    private static final Map<Integer, Integer> ROLES = new HashMap<>();

    /**
     * Draw the mob for {@code role} centred on the given square. Falls back to
     * a role-item preview when the entity cannot be constructed on the client.
     */
    public static void draw(GuiGraphics g, int role, int x, int y, int size,
                            float mouseX, float mouseY, int offer, com.devfarinsky.siegeoverhaul.core.CoreHireMenu menu) {
        // Stable card backdrop shared by the entity and failure paths.
        g.fill(x, y, x + size, y + size, 0xff0e0906);
        g.fillGradient(x + 1, y + 1, x + size - 1, y + size - 1,
                0xff2a2016, 0xff130f0c);
        int b = 0xff4a3826;
        g.fill(x, y, x + size, y + 1, b);
        g.fill(x, y + size - 1, x + size, y + size, b);
        g.fill(x, y, x + 1, y + size, b);
        g.fill(x + size - 1, y, x + size, y + size, b);

        boolean changed = !java.util.Objects.equals(ROLES.get(offer),role);
        ItemStack[] old=KITS.get(offer);
        for(int i=0;i<6;i++)if(old==null || !ItemStack.matches(old[i],menu.previewEquipment(offer,i)))changed=true;
        if(changed) {
            CACHE.remove(offer);FAILED.remove(offer);
            ItemStack[] snapshot=new ItemStack[6];
            for(int i=0;i<6;i++)snapshot[i]=menu.previewEquipment(offer,i).copy();
            KITS.put(offer,snapshot);ROLES.put(offer,role);
        }
        LivingEntity entity = getOrCreate(role,offer);
        if(entity!=null && changed) {
            try {
                for(int i=0;i<6;i++)entity.setItemSlot(
                        com.devfarinsky.siegeoverhaul.core.CoreOfferEquipment.SLOTS[i],KITS.get(offer)[i].copy());
            } catch(RuntimeException failure) {
                FAILED.add(offer);CACHE.remove(offer);entity=null;
            }
        }
        if (entity == null) {
            drawFallback(g, role, x, y, size);
            return;
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
            FAILED.add(offer);
            CACHE.remove(offer);
            drawFallback(g, role, x, y, size);
        }
    }

    private static void drawFallback(GuiGraphics g, int role, int x, int y, int size) {
        int iconSize = Math.max(12, Math.min(24, size - 8));
        ItemStack icon = new ItemStack(CoreHiring.icon(role));
        ItemIcons.draw(g, icon,
                x + (size - iconSize) / 2,
                y + (size - iconSize) / 2,
                iconSize);
    }

    private static LivingEntity getOrCreate(int role,int offer) {
        if (FAILED.contains(offer)) return null;
        LivingEntity cached = CACHE.get(offer);
        if (cached != null) return cached;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return null;

        int typeRole = entityRole(role);
        if (typeRole < 0 || typeRole >= CoreHiring.IDS.length) {
            FAILED.add(offer);
            return null;
        }
        String namespace = typeRole < CoreOffers.WORKER_START ? "recruits" : "workers";
        String path = CoreHiring.IDS[typeRole];
        ResourceLocation id = new ResourceLocation(namespace, path);
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
        if (type == null) {
            FAILED.add(offer);
            return null;
        }
        try {
            Entity created = type.create(level);
            if (!(created instanceof LivingEntity living)) {
                if (created != null) created.discard();
                FAILED.add(offer);
                return null;
            }
            // Position at origin; the GUI renderer ignores world position.
            living.moveTo(0, 0, 0, 0, 0);
            living.setYRot(0);
            living.setYHeadRot(0);
            if (CoreHiring.isHero(role)) {
                living.getPersistentData().putBoolean("SiegeHiredHero", true);
            }
            // draw() applies the server-synchronized offered equipment.
            CACHE.put(offer, living);
            return living;
        } catch (Throwable t) {
            FAILED.add(offer);
            return null;
        }
    }

    static int entityRole(int role) {
        return CoreHiring.isHero(role) ? CoreHiring.heroBase(role) : role;
    }

    /** Called on screen close so cached entities do not keep client resources alive between opens. */
    public static void clear() {
        KITS.clear(); ROLES.clear();
        CACHE.clear();
        FAILED.clear();
    }
}
