package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.core.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.Locale;

/**
 * Command Center HUD — medieval-fantasy but modern-sleek presentation.
 *
 * <p>Purchase authority remains entirely on the server; this screen only
 * paints server-authoritative state from {@link CoreHireMenu} and dispatches
 * inventory-button clicks or {@link RaidNetwork} packets. Layout math and
 * server contracts are unchanged from the compact version — only the
 * presentation layer is rebuilt around {@link CommandFrame},
 * {@link CommandPalette} and {@link CommandIcon}.
 *
 * <p>Three tabs share the window:
 * <ul>
 *   <li><b>Army &amp; Heroes</b> — four rotating hire cards with role icon,
 *       rarity ribbon and forged "Recruit" key.</li>
 *   <li><b>Loot &amp; Blessings</b> — three mystery-loot cards with animated
 *       reveal reel and three blessing cards with cool-down state.</li>
 *   <li><b>Bank &amp; Faction</b> — deposit/withdraw controls with an
 *       emerald-etched balance readout and a scrollable roster.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = SiegeOverhaul.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CoreHireScreen extends AbstractContainerScreen<CoreHireMenu> {

    private CoreHireLayout layout;
    private int tab;
    private int confirmBox = -1;
    private int seenLoot;
    private int revealBox = -1;
    private int revealTicks;
    private int waitingTicks;
    private int rosterOffset;
    /** Intel tab: 0 = Units, 1 = Enemy Lore, 2 = How to Play. */
    private int intelSection;
    private int intelOffset;
    /** Last-drawn intel body geometry, cached so mouseClicked/mouseDragged can hit-test the scrollbar. */
    private int intelBodyX, intelBodyY, intelBodyW, intelBodyH;
    /** Last-computed max scroll offset so the scrollbar drag can map cleanly. */
    private int intelMaxOffset;
    /** Drag state for the intel scrollbar thumb. */
    private boolean intelDragging;
    private int intelDragGrabY;
    private int intelDragStartOffset;
    private ItemStack revealed = ItemStack.EMPTY;
    private int revealedTier;

    private final Button[] hire = new Button[4];
    private final Button[] siegeYard = new Button[2];
    private final Button[] territoryBuffs = new Button[4];
    private final Button[] boxes = new Button[3];
    private final Button[] buffs = new Button[3];
    private final Button[] bank = new Button[4];
    private final TerritoryPanel territory = new TerritoryPanel();
    private Button recenterButton;
    private Button zoomInButton;
    private Button zoomOutButton;

    public CoreHireScreen(CoreHireMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(CoreMenus.HIRING.get(), CoreHireScreen::new));
    }

    private void action(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void init() {
        layout = CoreHireLayout.fit(width, height);
        imageWidth = layout.width();
        imageHeight = layout.height();
        super.init();

        // Tab rail across the top: icon + label, evenly spaced.
        String[] tabLabels = {"Army", "Loot", "Bank", "Territory", "Intel"};
        CommandIcon[] tabIcons = {CommandIcon.SWORDS, CommandIcon.CHEST, CommandIcon.BANK, CommandIcon.MAP, CommandIcon.BOOK};
        int tabCount = tabLabels.length;
        int tabWidth = (layout.width() - 28 - 4 * (tabCount - 1)) / tabCount;
        for (int i = 0; i < tabCount; i++) {
            final int index = i;
            addRenderableWidget(new CoreButton(
                    Component.literal(tabLabels[i]),
                    b -> { tab = index; confirmBox = -1; territory.reset(); },
                    layout.x() + 10 + i * (tabWidth + 4),
                    layout.y() + 32,
                    tabWidth, 22,
                    true,
                    () -> tab == index,
                    tabIcons[i]));
        }

        // Close (X) button in the header for players who can't reach Escape
        // (e.g. controller users, one-handed play, remap conflicts).
        addRenderableWidget(new CoreButton(
                Component.literal("X"),
                b -> onClose(),
                layout.x() + layout.width() - 22, layout.y() + 6,
                16, 16,
                false, () -> false));

        // Hire keys and bank keys share the same iteration to stay compact.
        for (int i = 0; i < 4; i++) {
            final int index = i;
            // Portrait occupies the left ~80px of the card; the hire button
            // sits under the info column on the right.
            int hirePortrait = Math.min(layout.cardHeight() - 12, 88);
            hire[i] = addRenderableWidget(new CoreButton(
                    Component.literal("Hire"),
                    b -> RaidNetwork.purchaseCoreOffer(menu.containerId, index, menu.rotation()),
                    layout.cardX(i) + hirePortrait + 14,
                    layout.cardY(i) + layout.cardHeight() - 25,
                    layout.cardWidth() - hirePortrait - 22, 20,
                    false, () -> false));

            String[] bankLabels = {"Deposit 8", "Deposit 64", "Withdraw 8", "Withdraw 64"};
            CommandIcon[] bankIcons = {CommandIcon.EMERALD, CommandIcon.EMERALD, CommandIcon.BANK, CommandIcon.BANK};
            int bw = (layout.width() - 38) / 4;
            bank[i] = addRenderableWidget(new CoreButton(
                    Component.literal(bankLabels[i]),
                    b -> action(40 + index),
                    layout.x() + 10 + i * (bw + 6),
                    layout.y() + 114,
                    bw, 20,
                    false, () -> false, bankIcons[i]));
        }

        // Siege Yard buttons on the Army tab (v4.18.0): hire a friendly
        // siege engineer pre-mounted on a catapult or ballista. Sits along
        // the bottom of the Army tab under the offer cards.
        int yardW = (layout.width() - 32) / 2;
        for (int i = 0; i < 2; i++) {
            final int index = i;
            siegeYard[i] = addRenderableWidget(new CoreButton(
                    Component.literal(SiegeYard.LABELS[i] + "  " + SiegeYard.PRICES[i]),
                    b -> action(50 + index),
                    layout.x() + 10 + i * (yardW + 6),
                    layout.y() + layout.height() - 32,
                    yardW, 20,
                    false, () -> false,
                    i == 0 ? CommandIcon.SWORDS : CommandIcon.BOOK));
        }

        // Territory-level buff buttons (v4.18.0): four one-time purchases
        // that apply faction-wide effects. Row sits between the tab bar and
        // the map, above the recenter/zoom controls.
        int tbW = (layout.width() - 32) / TerritoryBuffs.COUNT;
        for (int i = 0; i < TerritoryBuffs.COUNT; i++) {
            final int index = i;
            territoryBuffs[i] = addRenderableWidget(new CoreButton(
                    Component.literal(TerritoryBuffs.LABELS[i]),
                    b -> action(60 + index),
                    layout.x() + 10 + i * (tbW + 6),
                    layout.y() + 40,
                    tbW, 18,
                    false, () -> false, CommandIcon.FLAG));
        }

        // Territory tab helper keys: recenter + zoom in / out.
        recenterButton = addRenderableWidget(new CoreButton(
                Component.literal("Recenter"),
                b -> territory.recenter(),
                layout.x() + 10, layout.y() + layout.height() - 32,
                80, 18, false, () -> false, CommandIcon.FLAG));
        zoomInButton = addRenderableWidget(new CoreButton(
                Component.literal("Zoom in"),
                b -> territory.zoomIn(),
                layout.x() + 96, layout.y() + layout.height() - 32,
                80, 18, false, () -> false, CommandIcon.SCROLL));
        zoomOutButton = addRenderableWidget(new CoreButton(
                Component.literal("Zoom out"),
                b -> territory.zoomOut(),
                layout.x() + 182, layout.y() + layout.height() - 32,
                80, 18, false, () -> false, CommandIcon.MAP));

        // Loot boxes and blessing keys.
        for (int i = 0; i < 3; i++) {
            final int index = i;
            int keyY = layout.marketY(i) + layout.marketHeight() - 21;
            boxes[i] = addRenderableWidget(new CoreButton(
                    Component.literal("Open"),
                    b -> {
                        if (confirmBox != index) { confirmBox = index; return; }
                        if (waitingTicks > 0 || revealTicks > 0) return;
                        waitingTicks = 60;
                        action(20 + index);
                        confirmBox = -1;
                    },
                    layout.cardX(0) + 7, keyY,
                    layout.cardWidth() - 14, 17,
                    false, () -> confirmBox == index));
            buffs[i] = addRenderableWidget(new CoreButton(
                    Component.literal("Bless"),
                    b -> action(30 + index),
                    layout.cardX(1) + 7, keyY,
                    layout.cardWidth() - 14, 17,
                    false, () -> false));
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (waitingTicks > 0) waitingTicks--;
        if (menu.lootSequence() != seenLoot) {
            seenLoot = menu.lootSequence();
            revealBox = menu.lootBox();
            revealed = menu.lootReward().copy();
            revealedTier = menu.lootTier();
            revealTicks = CoreLoot.OPEN_TICKS;
            waitingTicks = 0;
        }
        if (revealTicks > 0) {
            revealTicks--;
            if (tab == 1 && (revealTicks == 0
                    || revealTicks % (revealTicks > 20 ? 5 : 10) == 0)
                    && minecraft != null) {
                float pitch = revealTicks == 0
                        ? 1.2f
                        : 0.6f + (CoreLoot.OPEN_TICKS - revealTicks) * 0.01f;
                minecraft.getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, pitch));
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);

        // Wire visibility/active state of buttons before AbstractContainerScreen paints them.
        for (int i = 0; i < 4; i++) {
            hire[i].visible = tab == 0;
            hire[i].active = menu.role(i) >= 0 && menu.cost(i) >= 0
                    && !menu.sold(i) && menu.rotation() > 0;
            String cost = menu.sold(i)
                    ? "Hired"
                    : menu.cost(i) < 0
                            ? "Unavailable"
                            : menu.cost(i) + "  Hire";
            hire[i].setMessage(Component.literal(cost));

            bank[i].visible = tab == 2;
            bank[i].active = i < 2 ? menu.emeralds() > 0 : menu.canWithdraw() && menu.bank() > 0;
        }
        for (int i = 0; i < siegeYard.length; i++) {
            siegeYard[i].visible = tab == 0;
            int total = menu.emeralds() + menu.bank();
            siegeYard[i].active = SiegeYard.available() && total >= SiegeYard.PRICES[i];
            siegeYard[i].setMessage(Component.literal(
                    SiegeYard.available()
                            ? "Hire " + SiegeYard.LABELS[i] + "  " + SiegeYard.PRICES[i]
                            : SiegeYard.LABELS[i] + " (needs Siege Weapons)"));
        }
        for (int i = 0; i < territoryBuffs.length; i++) {
            territoryBuffs[i].visible = tab == 3;
            boolean owned = menu.hasTerritoryBuff(i);
            int totalFunds = menu.emeralds() + menu.bank();
            territoryBuffs[i].active = !owned && totalFunds >= TerritoryBuffs.PRICES[i];
            territoryBuffs[i].setMessage(Component.literal(
                    owned ? TerritoryBuffs.LABELS[i] + " (active)"
                          : TerritoryBuffs.LABELS[i] + "  " + TerritoryBuffs.PRICES[i]));
        }
        for (int i = 0; i < 3; i++) {
            recenterButton.visible = tab == 3;
            zoomInButton.visible = tab == 3;
            zoomOutButton.visible = tab == 3;
            boxes[i].visible = buffs[i].visible = tab == 1;
            boxes[i].active = waitingTicks == 0 && revealTicks == 0
                    && menu.emeralds() >= CoreLoot.price(i);
            boxes[i].setMessage(Component.literal(
                    waitingTicks > 0 ? "Waiting..."
                            : revealTicks > 0 ? "Unsealing..."
                            : (confirmBox == i ? "Confirm  " : "Open  ") + CoreLoot.price(i)));

            boolean active = minecraft != null && minecraft.player != null
                    && minecraft.player.hasEffect(CoreBuffs.effect(i));
            buffs[i].active = !active && menu.emeralds() >= CoreBuffs.PRICES[i];
            buffs[i].setMessage(Component.literal(
                    active ? "Blessing active" : "Bless  " + CoreBuffs.PRICES[i]));
        }

        // Draw an active-tab under-glow before the tab bar paints so the
        // active tab gets a soft candlelight backing.
        drawActiveTabGlow(g);

        super.render(g, mx, my, partial);
        drawTooltips(g, mx, my);
    }

    /**
     * Paint a soft additive glow behind the currently active tab button so
     * the selected tab clearly reads as "lit". Tab button positions match
     * the layout in {@link #init()}.
     */
    private void drawActiveTabGlow(GuiGraphics g) {
        int tabCount = 5;
        int tabWidth = (layout.width() - 28 - 4 * (tabCount - 1)) / tabCount;
        int gx = layout.x() + 10 + tab * (tabWidth + 4);
        int gy = layout.y() + 32;
        HudAtlas.enableAdditive();
        HudAtlas.blitTinted(g, HudAtlas.GLOW_SOFT,
                gx - 8, gy - 6, tabWidth + 16, 32, 0x50ffd08a);
        HudAtlas.disableAdditive();
    }

    private void drawTooltips(GuiGraphics g, int mx, int my) {
        if (tab == 0) {
            for (int i = 0; i < 4; i++) {
                if (over(mx, my, layout.cardX(i), layout.cardY(i),
                        layout.cardWidth(), layout.cardHeight()) && menu.role(i) >= 0) {
                    int role = menu.role(i);
                    String info = CoreHiring.NAMES[role]
                            + "  |  " + CoreHiring.rarity(role)
                            + "  |  " + (i == 3
                                    ? HeroTraits.description(role)
                                    : "Shared faction offer; refreshes every 15 minutes.");
                    tooltip(g, info, mx, my);
                }
            }
        }
        if (tab == 1) {
            for (int i = 0; i < 3; i++) {
                if (over(mx, my, layout.cardX(0), layout.marketY(i),
                        layout.cardWidth(), layout.marketHeight())) {
                    String t = revealBox == i && revealTicks == 0 && !revealed.isEmpty()
                            ? CoreLoot.rarity(revealedTier) + "  |  " + revealed.getCount()
                                    + "x " + revealed.getHoverName().getString()
                            : "One mystery reward  |  " + CoreLoot.odds();
                    tooltip(g, t, mx, my);
                }
                if (over(mx, my, layout.cardX(1), layout.marketY(i),
                        layout.cardWidth(), layout.marketHeight())) {
                    tooltip(g, CoreBuffs.DETAILS[i]
                            + " for 5 minutes. Uses your personal emeralds;"
                            + " existing effects are preserved.", mx, my);
                }
            }
        }
    }

    private boolean over(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void tooltip(GuiGraphics g, String text, int x, int y) {
        g.renderTooltip(font, font.split(Component.literal(text), Math.min(300, width - 24)), x, y);
    }

    private void text(GuiGraphics g, String text, int x, int y, int width, int color) {
        g.drawString(font, font.plainSubstrByWidth(text, Math.max(1, width)), x, y, color, false);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int x, int y) {}

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x = layout.x(), y = layout.y(), w = layout.width(), h = layout.height();

        // Frame the whole window: shadow, bevel, parchment body, hairlines, rivets.
        CommandFrame.window(g, x, y, w, h);
        // Header banner strip that sits behind the crown, title and treasury chip.
        CommandFrame.header(g, x, y, w, 26);

        // Hanging crest banner on the left of the header (like the reference).
        drawCrestBanner(g, x - 6, y + 6);

        // Title cluster to the right of the crest. Double-shadow for a
        // struck-metal look: dark drop shadow, then warm bronze halo, then
        // the crisp gold glyph on top.
        String title = "KINGDOM COMMAND";
        g.drawString(font, title, x + 43, y + 13, 0xff000000, false);
        g.drawString(font, title, x + 42, y + 13, CommandPalette.BEVEL_DARK, false);
        text(g, title, x + 42, y + 12, w / 2 - 50, CommandPalette.ACCENT_GOLD);
        text(g, menu.factionName(),
                x + 42, y + 22, w / 2 - 50, CommandPalette.TEXT_MUTED);

        // Treasury pill on the right, using the textured rounded pill from
        // the atlas plus an emerald icon glyph and a live-updating balance.
        // Leaves 26px of room on the far right for the close (X) button.
        String purse = String.format(Locale.ROOT, "%,d", menu.emeralds());
        int chipW = Math.max(96, Math.min(w / 3, font.width(purse) + 60));
        int chipX = x + w - chipW - 30;
        // Soft glow behind the pill for treasury prominence.
        HudAtlas.enableAdditive();
        HudAtlas.blitTinted(g, HudAtlas.GLOW_SOFT,
                chipX - 16, y + 4, chipW + 32, 32, 0x60ffe0a0);
        HudAtlas.disableAdditive();
        HudAtlas.blit(g, HudAtlas.TREASURY_PILL, chipX, y + 6, chipW, 24);
        // Top-right pill shows the player's personal emeralds (what they can
        // spend right now). The Bank card shows the faction-wide treasury.
        // Two different pools, two clearly different labels.
        text(g, "YOUR PURSE", chipX + 26, y + 10, chipW - 32, CommandPalette.ACCENT_GOLD);
        text(g, purse, chipX + 26, y + 20, chipW - 32, CommandPalette.ACCENT_EMERALD);

        // Active-siege ribbon under the header.
        drawSiegeRibbon(g, x, y, w);

        // Drifting golden motes across the tab body for atmosphere. Motes
        // are procedural so they cost no textures; positions come from a
        // stable hash mixed with real time so they slowly drift diagonally.
        drawMotes(g, x + 4, y + 60, w - 8, h - 80);

        // Tab body.
        if (tab == 0) {
            for (int i = 0; i < 4; i++) drawHire(g, i, mx, my);
        } else if (tab == 1) {
            for (int i = 0; i < 3; i++) { drawLoot(g, i); drawBuff(g, i); }
        } else if (tab == 2) {
            drawFaction(g);
        } else if (tab == 3) {
            drawTerritory(g);
        } else if (tab == 4) {
            drawIntel(g);
        }

        // Footer stats strip: faction size + contextual tab hint.
        drawFooterStrip(g, x, y + h - 16, w);
    }

    /**
     * Drifting golden atmosphere motes across the tab body. Motes are pure
     * math (no textures, no allocations) so they stay cheap; positions come
     * from a stable hash mixed with real time so each mote drifts diagonally
     * and wraps within the bounds.
     */
    private void drawMotes(GuiGraphics g, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        long tick = minecraft != null && minecraft.level != null
                ? minecraft.level.getGameTime() : 0;
        float t = tick + (minecraft != null ? minecraft.getFrameTime() : 0);
        int count = Math.min(24, Math.max(6, (w * h) / 4000));
        for (int i = 0; i < count; i++) {
            float phase = i * 137.5f;
            float sx = (float) ((Math.sin(i * 12.9898) * 43758.5453) % 1.0);
            float sy = (float) ((Math.sin(i * 78.233) * 43758.5453) % 1.0);
            if (sx < 0) sx += 1;
            if (sy < 0) sy += 1;
            float px = sx + (t * 0.0007f + phase * 0.001f);
            float py = sy + (t * 0.0011f);
            px -= (float) Math.floor(px);
            py -= (float) Math.floor(py);
            int mx = x + (int) (px * (w - 2));
            int my = y + (int) (py * (h - 2));
            float twinkle = 0.4f + 0.6f * (float) Math.sin(t * 0.05f + i);
            int a = Math.min(200, (int) (twinkle * 200));
            int argb = (a << 24) | 0x00ffd88a;
            g.fill(mx, my, mx + 1, my + 1, argb);
            if (twinkle > 0.85f) {
                int halo = ((a / 3) << 24) | 0x00ffd88a;
                g.fill(mx - 1, my, mx + 2, my + 1, halo);
                g.fill(mx, my - 1, mx + 1, my + 2, halo);
            }
        }
    }

    /**
     * Textured hanging crest banner rendered from the HUD atlas. The banner
     * gently sways with a sine-wave x-offset driven by the render tick so it
     * feels alive without ever leaving the header rail.
     */
    private void drawCrestBanner(GuiGraphics g, int x, int y) {
        long tick = minecraft != null ? minecraft.level != null
                ? minecraft.level.getGameTime() : 0 : 0;
        float phase = (tick + (minecraft != null ? minecraft.getFrameTime() : 0)) * 0.04f;
        int sway = (int) Math.round(Math.sin(phase) * 1.4);
        HudAtlas.blit(g, HudAtlas.CREST_BANNER, x + sway, y - 4);
    }

    /**
     * Bottom strip with the faction size chip on the left, contextual tab
     * hint in the middle, and refresh timer / interest on the right.
     */
    private void drawFooterStrip(GuiGraphics g, int x, int y, int w) {
        // Faction member count on the left.
        int members = menu.members().size();
        String membersLine = members + (members == 1 ? " member" : " members");
        CommandIcon.SHIELD.draw(g, x + 10, y + 2, 10);
        text(g, membersLine, x + 24, y + 3, 100, CommandPalette.TEXT_MUTED);

        // Context hint in the middle.
        String hint = switch (tab) {
            case 0 -> "Shared stock rotates every 15 minutes";
            case 1 -> "Loot & blessings use personal emeralds";
            case 2 -> "Interest " + menu.interestRate() / 100.0 + "% every 24h";
            case 3 -> "Drag to pan  |  Scroll to zoom  |  Right-click to recenter";
            default -> "";
        };
        int hintW = font.width(hint);
        text(g, hint, x + (w - hintW) / 2, y + 3, hintW + 4, CommandPalette.TEXT_DIM);

        // Refresh timer on the right (only for tabs where it applies).
        if (tab == 0) {
            String timer = String.format(Locale.ROOT, "Refresh %d:%02d",
                    menu.seconds() / 60, menu.seconds() % 60);
            int tw = font.width(timer);
            text(g, timer, x + w - tw - 12, y + 3, tw + 4, CommandPalette.TEXT_MUTED);
        }
    }

    /**
     * Intel tab: three sub-sections (Units, Enemy Lore, How to Play) that
     * bake in the old Warlord's Codex content so the player no longer needs
     * to spawn a book.
     */
    private void drawIntel(GuiGraphics g) {
        int x = layout.x() + 10, y = layout.y() + 62;
        int w = layout.width() - 20;
        int h = layout.height() - (y - layout.y()) - 22;

        // Sub-tab strip
        String[] labels = {"Units", "Enemy Lore", "How to Play"};
        int segW = w / 3;
        // Under-glow behind the active sub-tab.
        int activeSx = x + intelSection * segW;
        HudAtlas.enableAdditive();
        HudAtlas.blitTinted(g, HudAtlas.GLOW_SOFT,
                activeSx - 8, y - 6, segW + 16, 32, 0x40ffd08a);
        HudAtlas.disableAdditive();
        for (int i = 0; i < 3; i++) {
            int sx = x + i * segW;
            boolean active = intelSection == i;
            int bg = active ? CommandPalette.CARD_TOP : CommandPalette.CARD_TOP_DIM;
            g.fill(sx, y, sx + segW - 2, y + 18, bg);
            // Top hairline + bottom accent for a struck-metal tab feel.
            g.fill(sx, y, sx + segW - 2, y + 1,
                    active ? CommandPalette.ACCENT_GOLD : CommandPalette.CARD_BORDER);
            g.fill(sx, y + 17, sx + segW - 2, y + 18,
                    active ? CommandPalette.ACCENT_GOLD : CommandPalette.BEVEL_DARK);
            int labelW = font.width(labels[i]);
            text(g, labels[i], sx + (segW - labelW) / 2, y + 5, segW,
                    active ? CommandPalette.ACCENT_GOLD : CommandPalette.TEXT_MUTED);
        }

        // Body panel
        int bodyY = y + 22;
        int bodyH = h - 24;
        CommandFrame.card(g, x, bodyY, w, bodyH, CommandPalette.ACCENT_ARCANE);

        // Reserve an 8px scrollbar gutter on the right so content never draws
        // under the thumb. Content clip is narrower than the panel.
        int scrollGutter = 10;
        int contentRight = x + w - scrollGutter;

        // Use scissor so long content clips at the panel edges.
        g.enableScissor(x + 2, bodyY + 2, contentRight, bodyY + bodyH - 2);
        int cursorY = bodyY + 8 - intelOffset;
        int textX = x + 10;
        int textW = w - 20 - scrollGutter;

        int drawn = switch (intelSection) {
            case 0 -> drawUnitsSection(g, textX, cursorY, textW);
            case 1 -> drawLoreSection(g, textX, cursorY, textW);
            default -> drawHowToPlaySection(g, textX, cursorY, textW);
        };
        g.disableScissor();

        // Clamp scroll so we can't drag past the end.
        int maxOffset = Math.max(0, drawn - bodyH + 16);
        if (intelOffset > maxOffset) intelOffset = maxOffset;

        // Cache geometry for mouseClicked / mouseDragged.
        intelBodyX = x;
        intelBodyY = bodyY;
        intelBodyW = w;
        intelBodyH = bodyH;
        intelMaxOffset = maxOffset;

        // === Scrollbar ===
        // Track sits in the right gutter, inset a couple of pixels so it
        // reads as separate from the card border.
        int trackX = x + w - 8;
        int trackY = bodyY + 4;
        int trackW = 4;
        int trackH = bodyH - 8;
        g.fill(trackX, trackY, trackX + trackW, trackY + trackH, 0x66000000);
        g.fill(trackX, trackY, trackX + 1, trackY + trackH, CommandPalette.BEVEL_DARK);
        g.fill(trackX + trackW - 1, trackY, trackX + trackW, trackY + trackH, CommandPalette.BEVEL_DARK);
        if (drawn > bodyH) {
            // Thumb sized proportional to visible / total, min 16px so it's clickable.
            int thumbH = Math.max(16, (int) ((long) trackH * bodyH / Math.max(1, drawn)));
            int thumbTravel = trackH - thumbH;
            int thumbY = trackY + (maxOffset == 0 ? 0
                    : (int) ((long) thumbTravel * intelOffset / maxOffset));
            int thumbColor = intelDragging
                    ? CommandPalette.ACCENT_GOLD
                    : CommandPalette.ACCENT_ARCANE;
            g.fill(trackX, thumbY, trackX + trackW, thumbY + thumbH, thumbColor);
            g.fill(trackX, thumbY, trackX + trackW, thumbY + 1, CommandPalette.ACCENT_GOLD);
            g.fill(trackX, thumbY + thumbH - 1, trackX + trackW, thumbY + thumbH,
                    CommandPalette.BEVEL_DARK);
        } else {
            // Content fits: draw an inactive marker so the gutter still reads
            // as a scrollbar (empty track alone looks like a UI bug).
            g.fill(trackX, trackY, trackX + trackW, trackY + Math.min(16, trackH),
                    0x33ffffff);
        }
    }

    private int drawUnitsSection(GuiGraphics g, int x, int startY, int w) {
        int y = startY;
        for (var entry : com.devfarinsky.siegeoverhaul.client.codex.UnitCodex.ENTRIES) {
            text(g, entry.name(), x, y, w, CommandPalette.ACCENT_GOLD); y += 12;
            text(g, entry.tagline(), x, y, w, CommandPalette.TEXT); y += 10;
            text(g, entry.stats(), x, y, w, CommandPalette.ACCENT_TEAL); y += 10;
            y += drawWrapped(g, entry.behavior(), x, y, w, CommandPalette.TEXT_MUTED);
            y += drawWrapped(g, "Counter: " + entry.counter(), x, y, w, CommandPalette.TEXT);
            y += drawWrapped(g, "Drops: " + entry.drops(), x, y, w, CommandPalette.ACCENT_EMERALD);
            text(g, entry.availability(), x, y, w, CommandPalette.TEXT_DIM); y += 10;
            y += 4;
            g.fill(x, y, x + w, y + 1, CommandPalette.BEVEL_DARK);
            y += 6;
        }
        return y - startY;
    }

    private int drawLoreSection(GuiGraphics g, int x, int startY, int w) {
        int y = startY;
        for (var entry : com.devfarinsky.siegeoverhaul.client.codex.FactionLore.all().entrySet()) {
            String name = entry.getKey().replace('_', ' ');
            // Simple title case.
            StringBuilder title = new StringBuilder(name.length());
            boolean cap = true;
            for (char c : name.toCharArray()) {
                title.append(cap ? Character.toUpperCase(c) : c);
                cap = c == ' ';
            }
            text(g, title.toString(), x, y, w, CommandPalette.ACCENT_GOLD); y += 12;
            for (String line : entry.getValue()) {
                text(g, line, x, y, w, CommandPalette.TEXT_MUTED); y += 10;
            }
            y += 4;
            g.fill(x, y, x + w, y + 1, CommandPalette.BEVEL_DARK);
            y += 6;
        }
        return y - startY;
    }

    private int drawHowToPlaySection(GuiGraphics g, int x, int startY, int w) {
        int y = startY;
        for (var tip : com.devfarinsky.siegeoverhaul.client.codex.DefensePlaybook.TIPS) {
            text(g, "[" + tip.tag() + "] " + tip.title(), x, y, w, CommandPalette.ACCENT_GOLD);
            y += 12;
            y += drawWrapped(g, tip.body(), x, y, w, CommandPalette.TEXT_MUTED);
            y += 4;
            g.fill(x, y, x + w, y + 1, CommandPalette.BEVEL_DARK);
            y += 6;
        }
        return y - startY;
    }

    /** Word-wrap helper. Returns the total height consumed. */
    private int drawWrapped(GuiGraphics g, String s, int x, int y, int w, int colour) {
        var lines = font.split(net.minecraft.network.chat.Component.literal(s), w);
        int used = 0;
        for (var line : lines) {
            g.drawString(font, line, x, y + used, colour, false);
            used += 10;
        }
        return used;
    }

    /** Dimensions of the map body inside the Territory tab. */
    private int mapX() { return layout.x() + 10; }
    private int mapY() { return layout.y() + 62; }
    private int mapW() { return layout.width() - 20; }
    private int mapH() { return layout.height() - 62 - 40; } // room for buff row above + zoom row below

    private void drawTerritory(GuiGraphics g) {
        int mx = mapX(), my = mapY(), mw = mapW(), mh = mapH();
        CommandFrame.card(g, mx, my, mw, mh, CommandPalette.ACCENT_STEEL);
        territory.draw(g, mx + 2, my + 2, mw - 4, mh - 4);
    }

    /** Contextual band below the header: shows wave state or peacetime hint. */
    private void drawSiegeRibbon(GuiGraphics g, int x, int y, int w) {
        int wave = menu.currentWave();
        int vote = menu.voteSeconds();
        boolean siege = wave > 0;
        boolean voting = vote > 0;

        CommandIcon icon = voting ? CommandIcon.SCROLL : siege ? CommandIcon.SWORDS : CommandIcon.SHIELD;
        int accent = voting ? CommandPalette.ACCENT_ARCANE
                : siege ? CommandPalette.ACCENT_BLOOD
                : CommandPalette.ACCENT_STEEL;
        String status = voting ? "RETREAT VOTE"
                : siege ? "SIEGE ACTIVE  |  Wave " + wave
                : "KINGDOM WATCH";
        String detail = voting
                ? vote + "s remaining"
                : siege ? "Next reward +" + menu.nextReward() + " to bank"
                : "Bank +" + menu.nextReward() + " on next wave clear";

        int ribbonY = y + 42;

        // While a siege is active the ribbon pulses with a hot red glow behind
        // it; peacetime uses a neutral steel accent card.
        if (siege) {
            long tick = minecraft != null && minecraft.level != null
                    ? minecraft.level.getGameTime() : 0;
            float pulse = 0.55f + 0.45f * (float) Math.sin(tick * 0.15f);
            int alpha = Math.min(255, (int) (pulse * 220));
            int glowArgb = (alpha << 24) | 0x00ff5a3c;
            HudAtlas.enableAdditive();
            HudAtlas.blitTinted(g, HudAtlas.GLOW_HOT,
                    x + w / 2 - 96, ribbonY - 24, 192, 64, glowArgb);
            HudAtlas.disableAdditive();
        }

        CommandFrame.card(g, x + 10, ribbonY, w - 20, 16, accent);
        icon.draw(g, x + 14, ribbonY + 2, 12);
        text(g, status, x + 30, ribbonY + 4, w / 2, accent);
        text(g, detail, x + w / 2, ribbonY + 4, w / 2 - 14, CommandPalette.TEXT);

        // Faint tab-specific emblem watermark in the top-right of the tab body.
        TabEmblem emblem = switch (tab) {
            case 0 -> TabEmblem.ARMY;
            case 1 -> TabEmblem.LOOT;
            case 2 -> TabEmblem.BANK;
            default -> TabEmblem.TERRITORY;
        };
        // Only draw the watermark if there is room and we are not in territory
        // (the map fills that area itself).
        if (tab != 3 && w > 480) {
            emblem.draw(g, x + w - 46, y + 58, 32);
        }
    }

    private void drawHire(GuiGraphics g, int i, int mouseX, int mouseY) {
        int x = layout.cardX(i), y = layout.cardY(i);
        int w = layout.cardWidth(), h = layout.cardHeight();
        int role = menu.role(i);

        int accent = i == 3 ? CommandPalette.ACCENT_ARCANE
                : i == 2 ? CommandPalette.ACCENT_TEAL
                : CommandPalette.ACCENT_GOLD;

        if (menu.sold(i)) CommandFrame.cardDimmed(g, x, y, w, h);
        else CommandFrame.card(g, x, y, w, h, accent);

        if (role < 0 || role >= CoreHiring.NAMES.length) return;

        // Portrait tile on the left with heraldic corner brackets around it,
        // matching the reference kingdom-command mockup.
        int portrait = Math.min(h - 12, 88);
        int px = x + 6, py = y + 6;
        EntityPortrait.draw(g, role, px, py, portrait, mouseX, mouseY);
        // Heraldic bracket ornaments at each corner of the portrait.
        CommandFrame.cornerBracket(g, px + 1, py + 1, +1, +1);
        CommandFrame.cornerBracket(g, px + portrait - 2, py + 1, -1, +1);
        CommandFrame.cornerBracket(g, px + 1, py + portrait - 2, +1, -1);
        CommandFrame.cornerBracket(g, px + portrait - 2, py + portrait - 2, -1, -1);

        // Info column to the right of the portrait.
        int infoLeft = px + portrait + 8;
        int infoWidth = w - portrait - 18;
        // Character name in white.
        String name = CoreHiring.NAMES[role];
        text(g, name, infoLeft, y + 8, infoWidth, CommandPalette.TEXT);
        // Role/class line in muted gold-brown.
        String roleLabel = i == 3 ? "Hero"
                : i == 2 ? "Worker " + CoreHiring.NAMES[role]
                : shortRole(role);
        text(g, roleLabel, infoLeft, y + 20, infoWidth, CommandPalette.TEXT_MUTED);

        // Equipment glyphs row (three or four small icons) hinting at loadout.
        CommandIcon[] gear = gearGlyphs(role);
        int glyphY = y + 32;
        int glyphSize = 12;
        int glyphGap = 6;
        for (int g_i = 0; g_i < gear.length && g_i < 4; g_i++) {
            int gx = infoLeft + g_i * (glyphSize + glyphGap);
            gear[g_i].draw(g, gx, glyphY, glyphSize);
        }

        // Descriptive blurb: role summary for regular recruits/workers,
        // ability line for heroes. Wrapped across up to three lines so the
        // player doesn't have to hover the tooltip to know what they're
        // hiring.
        String blurb = i == 3 ? HeroTraits.description(role) : roleBlurb(role);
        int blurbY = y + 48;
        int blurbLines = drawWrappedText(g, blurb, infoLeft, blurbY,
                infoWidth, 3, CommandPalette.TEXT_MUTED);

        // Stat/armor line: shows the loadout tier text and the armor material
        // so players understand what armor the unit is going to wear. Regular
        // recruits are cloth/leather; workers wear their tool kit; heroes wear
        // a themed trimmed set from HeroTraits.
        String kit = kitDescriptor(role);
        int kitY = blurbY + Math.min(blurbLines, 3) * 10 + 2;
        if (kitY + 10 <= y + h - 26) {
            text(g, kit, infoLeft, kitY, infoWidth, CommandPalette.ACCENT_TEAL);
        }

        // Item chip in the bottom-left of the info column: shows the recruit's
        // signature item (bread, arrows, tool). Sits above the Hire button.
        int chipY = y + h - 44;
        g.renderItem(menu.getSlot(i).getItem(), infoLeft, chipY);
        // Cost readout next to the item so the player sees the price at a
        // glance without hovering.
        text(g, menu.cost(i) + "e", infoLeft + 20, chipY + 4,
                infoWidth - 20, CommandPalette.ACCENT_EMERALD);
    }

    /**
     * Draw text wrapped to a max width across up to maxLines lines. Returns
     * the number of lines actually rendered so callers can position the
     * next element under it.
     */
    private int drawWrappedText(GuiGraphics g, String s, int x, int y, int width,
                                int maxLines, int color) {
        if (s == null || s.isEmpty()) return 0;
        String[] words = s.split(" ");
        StringBuilder line = new StringBuilder();
        int drawn = 0;
        for (int w = 0; w < words.length; w++) {
            String candidate = line.length() == 0 ? words[w] : line + " " + words[w];
            if (font.width(candidate) <= width) {
                line.setLength(0);
                line.append(candidate);
            } else {
                if (line.length() > 0) {
                    g.drawString(font, line.toString(), x, y + drawn * 10, color, false);
                    drawn++;
                    if (drawn >= maxLines) return drawn;
                }
                line.setLength(0);
                line.append(words[w]);
            }
        }
        if (line.length() > 0 && drawn < maxLines) {
            g.drawString(font, line.toString(), x, y + drawn * 10, color, false);
            drawn++;
        }
        return drawn;
    }

    /** One-sentence description for non-hero recruits and workers. */
    private static String roleBlurb(int role) {
        return switch (role) {
            case 0 -> "Front-line recruit. Sword and shield. Cheapest hire, good in numbers.";
            case 1 -> "Shield wall anchor. Absorbs melee pressure so archers can work.";
            case 2 -> "Ranged skirmisher. Bow. Break-away kites best of the recruit line.";
            case 3 -> "Heavy ranged. Crossbow bolts pierce armor. Slow to reload.";
            case 4 -> "Tends crops in the war camp. Feeds the whole faction.";
            case 5 -> "Chops trees near the camp. Keeps timber flowing for repairs.";
            case 6 -> "Mines stone and coal nearby. Restocks fortification materials.";
            case 7 -> "Repairs damaged blocks after a siege ends. Speeds recovery.";
            case 8 -> "Cooks raw ingredients into food. Multiplies farmer output.";
            case 9 -> "Runs goods between chests. Ties your logistics together.";
            default -> "Faction unit.";
        };
    }

    /** Loadout / armor hint shown under the blurb. */
    private static String kitDescriptor(int role) {
        return switch (role) {
            case 0 -> "Kit: leather cap, iron sword, wooden shield";
            case 1 -> "Kit: iron helm, iron sword, iron shield";
            case 2 -> "Kit: leather cap, bow, arrows";
            case 3 -> "Kit: chain helm, crossbow, tipped bolts";
            case 4 -> "Kit: straw hat, hoe, seeds";
            case 5 -> "Kit: leather cap, iron axe";
            case 6 -> "Kit: iron helm, iron pickaxe, torches";
            case 7 -> "Kit: leather cap, hammer, timber";
            case 8 -> "Kit: chef hat, iron knife, cook pot";
            case 9 -> "Kit: leather boots, satchel, map";
            case 10 -> "Kit: netherite helm, Cinderfang blade, redstone-trimmed armor";
            case 11 -> "Kit: netherite helm, Oathkeeper blade, ward-trimmed armor";
            case 12 -> "Kit: netherite helm, Thornsong bow, wild-trimmed armor";
            case 13 -> "Kit: netherite helm, Stormbolt crossbow, eye-trimmed armor";
            default -> "";
        };
    }

    private static String shortRole(int role) {
        return switch (role) {
            case 0 -> "Recruit";
            case 1 -> "Shieldman";
            case 2 -> "Archer";
            case 3 -> "Crossbowman";
            case 4 -> "Farmer";
            case 5 -> "Lumberjack";
            case 6 -> "Miner";
            case 7 -> "Builder";
            case 8 -> "Cook";
            case 9 -> "Courier";
            case 10, 11, 12, 13 -> "Hero";
            default -> "Unit";
        };
    }

    private static CommandIcon[] gearGlyphs(int role) {
        return switch (role) {
            case 0 -> new CommandIcon[]{CommandIcon.SWORDS, CommandIcon.SHIELD};
            case 1 -> new CommandIcon[]{CommandIcon.SHIELD, CommandIcon.SWORDS, CommandIcon.SHIELD};
            case 2 -> new CommandIcon[]{CommandIcon.SWORDS, CommandIcon.SCROLL};
            case 3 -> new CommandIcon[]{CommandIcon.SWORDS, CommandIcon.SCROLL, CommandIcon.SHIELD};
            case 4 -> new CommandIcon[]{CommandIcon.WIND, CommandIcon.SCROLL};
            case 5 -> new CommandIcon[]{CommandIcon.SWORDS, CommandIcon.SCROLL};
            case 6 -> new CommandIcon[]{CommandIcon.POWER, CommandIcon.SCROLL};
            case 7 -> new CommandIcon[]{CommandIcon.SHIELD, CommandIcon.SCROLL};
            case 8 -> new CommandIcon[]{CommandIcon.SCROLL, CommandIcon.EMERALD};
            case 9 -> new CommandIcon[]{CommandIcon.SCROLL, CommandIcon.FLAG};
            case 10 -> new CommandIcon[]{CommandIcon.SWORDS, CommandIcon.POWER, CommandIcon.SHIELD, CommandIcon.CROWN};
            case 11 -> new CommandIcon[]{CommandIcon.AEGIS, CommandIcon.SHIELD, CommandIcon.CROWN};
            case 12 -> new CommandIcon[]{CommandIcon.SWORDS, CommandIcon.WIND, CommandIcon.CROWN};
            case 13 -> new CommandIcon[]{CommandIcon.POWER, CommandIcon.AEGIS, CommandIcon.CROWN};
            default -> new CommandIcon[]{CommandIcon.SHIELD};
        };
    }

    private void drawLoot(GuiGraphics g, int i) {
        int x = layout.cardX(0), y = layout.marketY(i);
        int w = layout.cardWidth(), h = layout.marketHeight();
        CommandFrame.card(g, x, y, w, h, CommandPalette.ACCENT_GOLD);

        boolean opening = revealBox == i && revealTicks > 0;
        boolean done = revealBox == i && revealTicks == 0 && !revealed.isEmpty();

        // Portrait slot: chest icon by default, revealed item after unsealing.
        CommandFrame.chip(g, x + 6, y + 6, 20, 20, CommandPalette.ACCENT_GOLD);
        if (done) {
            g.renderItem(revealed, x + 8, y + 8);
        } else {
            CommandIcon.CHEST.draw(g, x + 10, y + 10, 12);
        }

        text(g, CoreLoot.NAMES[i], x + 30, y + 7, w - 36, CommandPalette.TEXT);
        String subtitle = opening ? "Unsealing the seal..."
                : done ? revealed.getHoverName().getString()
                : "Sealed mystery  |  " + CoreLoot.price(i) + " emeralds";
        text(g, subtitle, x + 30, y + 19, w - 36,
                opening ? CommandPalette.ACCENT_ARCANE
                        : done ? CommandPalette.tier(revealedTier)
                        : CommandPalette.TEXT_MUTED);

        // Progress-style reveal bar or rarity readout in the middle band.
        if (h > 64) {
            if (opening) {
                float progress = 1f - (revealTicks / (float) CoreLoot.OPEN_TICKS);
                CommandFrame.progress(g, x + 8, y + 32, w - 16, 4,
                        progress, CommandPalette.ACCENT_ARCANE);
                text(g, "Common | Uncommon | Rare | Epic",
                        x + 8, y + 40, w - 16, CommandPalette.TEXT_DIM);
            } else if (done) {
                text(g, "Rarity: " + CoreLoot.rarity(revealedTier),
                        x + 8, y + 34, w - 16, CommandPalette.tier(revealedTier));
            } else {
                text(g, "Reveal your reward", x + 8, y + 34, w - 16,
                        CommandPalette.ACCENT_GOLD);
            }
        }
    }

    private void drawBuff(GuiGraphics g, int i) {
        int x = layout.cardX(1), y = layout.marketY(i);
        int w = layout.cardWidth(), h = layout.marketHeight();
        CommandFrame.card(g, x, y, w, h, CommandPalette.ACCENT_TEAL);

        CommandIcon icon = i == 0 ? CommandIcon.WIND
                : i == 1 ? CommandIcon.POWER
                : CommandIcon.AEGIS;
        CommandFrame.chip(g, x + 6, y + 6, 20, 20, CommandPalette.ACCENT_TEAL);
        icon.draw(g, x + 10, y + 10, 12);

        text(g, CoreBuffs.NAMES[i], x + 30, y + 7, w - 36, CommandPalette.TEXT);
        text(g, CoreBuffs.DETAILS[i], x + 30, y + 19, w - 36, CommandPalette.TEXT_MUTED);

        if (h > 64) {
            text(g, "Personal blessing  |  5 min",
                    x + 8, y + 34, w - 16, CommandPalette.ACCENT_TEAL);
        }
    }

    private void drawFaction(GuiGraphics g) {
        int x = layout.x(), y = layout.y();
        int w = layout.width(), h = layout.height();

        // Bank summary card up top: taller card so we have three text rows
        // per side without overlap.
        int bankCardH = 46;
        CommandFrame.card(g, x + 10, y + 62, w - 20, bankCardH, CommandPalette.ACCENT_GOLD);

        // Textured bank coin-stack icon with a soft glow.
        int coinX = x + 14, coinY = y + 66;
        HudAtlas.enableAdditive();
        HudAtlas.blitTinted(g, HudAtlas.GLOW_SOFT,
                coinX - 8, coinY - 4, 42, 34, 0x80ffdd8a);
        HudAtlas.disableAdditive();
        HudAtlas.blit(g, HudAtlas.ICON_BANK, coinX, coinY);

        int leftX = x + 46;
        int leftW = w / 2 - 52;
        text(g, menu.factionName(), leftX, y + 68, leftW, CommandPalette.TEXT);
        text(g, String.format(Locale.ROOT, "Treasury: %,d emeralds", menu.bank()),
                leftX, y + 80, leftW, CommandPalette.ACCENT_EMERALD);
        // Status line (peace / siege / retreat vote) below treasury.
        String status = menu.voteSeconds() > 0
                ? "Retreat vote: " + menu.voteSeconds() + "s remaining"
                : menu.currentWave() > 0
                        ? "Surviving wave " + menu.currentWave()
                        : "Preparing for the next siege";
        text(g, status, leftX, y + 92, leftW, CommandPalette.TEXT_MUTED);

        int rightX = x + w / 2;
        int rightW = w / 2 - 18;
        long dailyInterest = (long) menu.bank() * menu.interestRate() / 10000L;
        text(g, "Next wave " + menu.nextWave() + ": +" + menu.nextReward() + " to bank",
                rightX, y + 68, rightW, CommandPalette.ACCENT_TEAL);
        text(g, String.format(Locale.ROOT, "Interest: +%,d /24h (%.2f%%)",
                        dailyInterest, menu.interestRate() / 100.0),
                rightX, y + 80, rightW, CommandPalette.ACCENT_GOLD);
        text(g, "Purchases pull from bank first, then your pack",
                rightX, y + 92, rightW, CommandPalette.TEXT_DIM);

        // Roster panel below (pushed down to make room for the taller bank card
        // and its Deposit/Withdraw button row). We split it into a left roster
        // column and a right activity-graph column when the roster is short
        // enough to leave room; otherwise the roster takes the full width.
        int rosterY = y + 138;
        int rosterH = h - (rosterY - y) - 22;
        CommandFrame.card(g, x + 10, rosterY, w - 20, rosterH, CommandPalette.ACCENT_ARCANE);
        CommandIcon.SCROLL.draw(g, x + 16, rosterY + 4, 12);
        text(g, "FACTION ROSTER", x + 32, rosterY + 6, w - 48,
                CommandPalette.ACCENT_ARCANE);

        int graphW = Math.min(180, (w - 20) / 2 - 8);
        int rosterListW = w - 32 - graphW - 8;
        int listTop = rosterY + 22;
        int lines = Math.max(1, (rosterH - 26) / 12);
        int count = menu.members().size();
        int maxOffset = Math.max(0, count - lines);
        rosterOffset = Math.max(0, Math.min(rosterOffset, maxOffset));

        if (count == 0) {
            text(g, "Roster is synchronizing...", x + 18, listTop, rosterListW,
                    CommandPalette.TEXT_MUTED);
        } else {
            for (int i = 0; i < lines && i + rosterOffset < count; i++) {
                text(g, "-  " + menu.members().get(i + rosterOffset),
                        x + 18, listTop + i * 12, rosterListW, CommandPalette.TEXT);
            }
        }
        // Scroll indicator when overflow is present.
        if (count > lines) {
            String indicator = String.format(Locale.ROOT,
                    "%d - %d of %d  |  Scroll",
                    rosterOffset + 1, Math.min(count, rosterOffset + lines), count);
            text(g, indicator, x + 18 + rosterListW - font.width(indicator),
                    rosterY + 6, rosterListW, CommandPalette.TEXT_DIM);
        }

        // Activity graph: right side of the roster panel. Uses the recent
        // bank ledger (deposits, withdrawals, wave rewards) as bars centered
        // on a zero line. Green above = credit, red below = debit.
        int gx = x + 18 + rosterListW + 8;
        int gy = rosterY + 22;
        int gh = rosterH - 30;
        drawBankGraph(g, gx, gy, graphW, gh);
    }

    /** v4.18.0 bank activity sparkline. Renders a zero-centered bar chart. */
    private void drawBankGraph(GuiGraphics g, int gx, int gy, int gw, int gh) {
        // Frame + label header.
        g.fill(gx, gy, gx + gw, gy + gh, 0x40000000);
        g.fill(gx, gy, gx + gw, gy + 1, 0xFF6b4a1a);
        g.fill(gx, gy + gh - 1, gx + gw, gy + gh, 0xFF6b4a1a);
        g.fill(gx, gy, gx + 1, gy + gh, 0xFF6b4a1a);
        g.fill(gx + gw - 1, gy, gx + gw, gy + gh, 0xFF6b4a1a);
        text(g, "RECENT ACTIVITY", gx + 4, gy + 3, gw - 8, CommandPalette.ACCENT_GOLD);

        int[] ledger = menu.bankLedger();
        int plotTop = gy + 16;
        int plotBottom = gy + gh - 12;
        int plotH = plotBottom - plotTop;
        int zeroY = plotTop + plotH / 2;
        // Zero line.
        g.fill(gx + 4, zeroY, gx + gw - 4, zeroY + 1, 0x60ffffff);

        if (ledger.length == 0) {
            text(g, "No transactions yet", gx + 6, zeroY + 6, gw - 12, CommandPalette.TEXT_DIM);
            return;
        }
        // Find max absolute delta for scaling.
        int maxAbs = 1;
        for (int d : ledger) maxAbs = Math.max(maxAbs, Math.abs(d));
        int plotW = gw - 8;
        int barW = Math.max(2, plotW / Math.max(ledger.length, 8));
        int startX = gx + 4 + (plotW - barW * ledger.length) / 2;
        int totalCredit = 0, totalDebit = 0;
        for (int i = 0; i < ledger.length; i++) {
            int d = ledger[i];
            int bx = startX + i * barW;
            int barH = (int) ((long) Math.abs(d) * (plotH / 2 - 2) / maxAbs);
            if (d >= 0) {
                g.fill(bx + 1, zeroY - barH, bx + barW - 1, zeroY, 0xFF2E9E4A);
                totalCredit += d;
            } else {
                g.fill(bx + 1, zeroY + 1, bx + barW - 1, zeroY + 1 + barH, 0xFFB1352B);
                totalDebit += -d;
            }
        }
        // Footer totals.
        String footer = String.format(Locale.ROOT, "+%d  /  -%d over last %d",
                totalCredit, totalDebit, ledger.length);
        text(g, footer, gx + 6, gy + gh - 10, gw - 12, CommandPalette.TEXT_MUTED);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        if (tab == 3) {
            if (territory.mouseScrolled(delta, x, y,
                    mapX() + 2, mapY() + 2, mapW() - 4, mapH() - 4)) return true;
        }
        if (tab == 2) {
            int count = menu.members().size();
            int rosterH = layout.height() - (118 - 0) - 22;
            int lines = Math.max(1, (rosterH - 26) / 12);
            int maxOffset = Math.max(0, count - lines);
            int step = (int) -Math.signum(delta);
            rosterOffset = Math.max(0, Math.min(maxOffset, rosterOffset + step));
            return true;
        }
        if (tab == 4) {
            int step = (int) -Math.signum(delta) * 12;
            intelOffset = Math.max(0, intelOffset + step);
            return true;
        }
        return super.mouseScrolled(x, y, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tab == 4 && button == 0) {
            // Sub-tab strip at the top of the Intel panel: hit-test one of
            // three equal segments and switch section.
            int x = layout.x() + 10, y = layout.y() + 62, w = layout.width() - 20;
            int segW = w / 3;
            for (int i = 0; i < 3; i++) {
                int sx = x + i * segW;
                if (mouseX >= sx && mouseX < sx + segW
                        && mouseY >= y && mouseY < y + 18) {
                    if (intelSection != i) { intelSection = i; intelOffset = 0; }
                    return true;
                }
            }
            // Scrollbar hit-test: 8px wide gutter on the right edge of the body panel.
            int trackX = intelBodyX + intelBodyW - 8;
            int trackY = intelBodyY + 4;
            int trackH = intelBodyH - 8;
            if (mouseX >= trackX && mouseX < trackX + 4
                    && mouseY >= trackY && mouseY < trackY + trackH
                    && intelMaxOffset > 0) {
                intelDragging = true;
                intelDragGrabY = (int) mouseY;
                intelDragStartOffset = intelOffset;
                // Also jump the thumb to the click point immediately so
                // click-anywhere on the track scrolls there (standard behavior).
                int thumbH = Math.max(16, (int) ((long) trackH * intelBodyH
                        / Math.max(1, intelBodyH + intelMaxOffset)));
                int thumbTravel = Math.max(1, trackH - thumbH);
                int newOffset = (int) ((mouseY - trackY - thumbH / 2.0)
                        * intelMaxOffset / thumbTravel);
                intelOffset = Math.max(0, Math.min(intelMaxOffset, newOffset));
                intelDragStartOffset = intelOffset;
                return true;
            }
        }
        if (tab == 3) {
            if (button == 1 && mouseX >= mapX() && mouseX < mapX() + mapW()
                    && mouseY >= mapY() && mouseY < mapY() + mapH()) {
                territory.recenter();
                return true;
            }
            if (territory.mouseClicked(mouseX, mouseY, button,
                    mapX() + 2, mapY() + 2, mapW() - 4, mapH() - 4)) return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (tab == 3 && territory.mouseReleased(mouseX, mouseY, button)) return true;
        if (intelDragging && button == 0) { intelDragging = false; return true; }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dx, double dy) {
        if (tab == 3 && territory.mouseDragged(mouseX, mouseY, dx, dy)) return true;
        if (tab == 4 && intelDragging && button == 0 && intelMaxOffset > 0) {
            // Map the mouse's Y travel back into scroll offset via the
            // thumb's travel range. Same formula as the draw call.
            int trackH = intelBodyH - 8;
            int thumbH = Math.max(16, (int) ((long) trackH * intelBodyH
                    / Math.max(1, intelBodyH + intelMaxOffset)));
            int thumbTravel = Math.max(1, trackH - thumbH);
            int deltaPx = (int) mouseY - intelDragGrabY;
            int newOffset = intelDragStartOffset + (int) ((long) deltaPx
                    * intelMaxOffset / thumbTravel);
            intelOffset = Math.max(0, Math.min(intelMaxOffset, newOffset));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public void onClose() {
        territory.reset();
        territory.closeTextures();
        EntityPortrait.clear();
        super.onClose();
    }
}
