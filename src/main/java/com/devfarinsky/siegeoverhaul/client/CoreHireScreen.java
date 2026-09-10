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
    private ItemStack revealed = ItemStack.EMPTY;
    private int revealedTier;

    private final Button[] hire = new Button[4];
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
        String[] tabLabels = {"Army & Heroes", "Loot & Blessings", "Bank & Faction", "Territory"};
        CommandIcon[] tabIcons = {CommandIcon.SWORDS, CommandIcon.CHEST, CommandIcon.BANK, CommandIcon.MAP};
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

        // Hire keys and bank keys share the same iteration to stay compact.
        for (int i = 0; i < 4; i++) {
            final int index = i;
            hire[i] = addRenderableWidget(new CoreButton(
                    Component.literal("Recruit"),
                    b -> RaidNetwork.purchaseCoreOffer(menu.containerId, index, menu.rotation()),
                    layout.cardX(i) + 8,
                    layout.cardY(i) + layout.cardHeight() - 23,
                    layout.cardWidth() - 16, 18,
                    false, () -> false));

            String[] bankLabels = {"Store 8", "Store 64", "Take 8", "Take 64"};
            int bw = (layout.width() - 38) / 4;
            bank[i] = addRenderableWidget(new CoreButton(
                    Component.literal(bankLabels[i]),
                    b -> action(40 + index),
                    layout.x() + 10 + i * (bw + 6),
                    layout.y() + 102,
                    bw, 18,
                    false, () -> false));
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
                    ? "Recruited"
                    : menu.cost(i) < 0
                            ? "Unavailable"
                            : "Recruit  " + menu.cost(i);
            hire[i].setMessage(Component.literal(cost));

            bank[i].visible = tab == 2;
            bank[i].active = i < 2 ? menu.emeralds() > 0 : menu.canWithdraw() && menu.bank() > 0;
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

        super.render(g, mx, my, partial);
        drawTooltips(g, mx, my);
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

        // Crown + title cluster on the left.
        CommandIcon.CROWN.draw(g, x + 12, y + 10, 14);
        text(g, "COMMAND CENTER", x + 32, y + 12, w / 2 - 40, CommandPalette.ACCENT_GOLD);
        text(g, menu.factionName().toUpperCase(Locale.ROOT),
                x + 32, y + 22, w / 2 - 40, CommandPalette.TEXT_MUTED);

        // Treasury chip on the right, with emerald glyph.
        String purse = String.format(Locale.ROOT, "%,d", menu.emeralds());
        int chipW = Math.min(w / 3, Math.max(66, font.width(purse) + 34));
        int chipX = x + w - chipW - 12;
        CommandFrame.chip(g, chipX, y + 10, chipW, 16, CommandPalette.ACCENT_EMERALD);
        CommandIcon.EMERALD.draw(g, chipX + 4, y + 12, 12);
        text(g, purse, chipX + 20, y + 14, chipW - 26, CommandPalette.ACCENT_EMERALD);

        // Active-siege ribbon under the header.
        drawSiegeRibbon(g, x, y, w);

        // Tab body.
        if (tab == 0) {
            for (int i = 0; i < 4; i++) drawHire(g, i, mx, my);
        } else if (tab == 1) {
            for (int i = 0; i < 3; i++) { drawLoot(g, i); drawBuff(g, i); }
        } else if (tab == 2) {
            drawFaction(g);
        } else {
            drawTerritory(g);
        }

        // Footer contextual hint.
        String footer = switch (tab) {
            case 0 -> String.format(Locale.ROOT,
                    "Shared stock  |  Refresh in %d:%02d",
                    menu.seconds() / 60, menu.seconds() % 60);
            case 1 -> "Mystery loot & five-minute blessings  |  Personal emeralds";
            case 2 -> "Interest " + menu.interestRate() / 100.0
                    + "% /24h  |  Leader withdrawals  |  Scroll roster";
            default -> "";
        };
        if (tab != 3) text(g, footer, x + 12, y + h - 13, w - 24, CommandPalette.TEXT_DIM);
    }

    /** Dimensions of the map body inside the Territory tab. */
    private int mapX() { return layout.x() + 10; }
    private int mapY() { return layout.y() + 62; }
    private int mapW() { return layout.width() - 20; }
    private int mapH() { return layout.height() - 62 - 40; }

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

        // Avatar portrait tile on the left. Size scales with card height so
        // the layout stays readable on the compact 350-tall window.
        int portrait = Math.min(Math.max(36, h - 24), 66);
        EntityPortrait.draw(g, role, x + 6, y + 6, portrait, mouseX, mouseY);

        int textLeft = x + 10 + portrait;
        int textAreaWidth = w - textLeft + x - 6;
        String prefix = i == 3 ? "HERO" : i == 2 ? "WORKER" : "SOLDIER";
        text(g, prefix, textLeft, y + 8, textAreaWidth, accent);
        text(g, CoreHiring.NAMES[role], textLeft, y + 18, textAreaWidth,
                CommandPalette.TEXT);
        text(g, CoreHiring.rarity(role), textLeft, y + 30, textAreaWidth,
                CommandPalette.TEXT_MUTED);

        if (h > 80) {
            CommandFrame.divider(g, textLeft, y + 43, textAreaWidth);
            String blurb = i == 3
                    ? HeroTraits.description(role)
                    : "Ready to join your faction";
            text(g, blurb, textLeft, y + 47, textAreaWidth, CommandPalette.TEXT_MUTED);
            // Signature item chip with cost on the right, portrait side already used.
            String currency = menu.getSlot(4).getItem().getHoverName().getString();
            String costLine = "Cost " + menu.cost(i) + " " + currency;
            CommandFrame.chip(g, textLeft, y + h - 32, textAreaWidth - 2, 14,
                    CommandPalette.ACCENT_EMERALD);
            g.renderItem(menu.getSlot(i).getItem(), textLeft + 2, y + h - 34);
            text(g, costLine, textLeft + 22, y + h - 29,
                    textAreaWidth - 24, CommandPalette.ACCENT_EMERALD);
        }
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

        // Bank summary card up top: bank icon + name/balance on the left,
        // wave payout + vote/countdown on the right.
        CommandFrame.card(g, x + 10, y + 62, w - 20, 34, CommandPalette.ACCENT_GOLD);
        CommandFrame.chip(g, x + 14, y + 66, 26, 26, CommandPalette.ACCENT_GOLD);
        CommandIcon.BANK.draw(g, x + 18, y + 70, 18);

        text(g, menu.factionName(), x + 46, y + 66, w / 2 - 52, CommandPalette.TEXT);
        text(g, String.format(Locale.ROOT, "Treasury: %,d emeralds", menu.bank()),
                x + 46, y + 80, w / 2 - 52, CommandPalette.ACCENT_EMERALD);

        int rightX = x + w / 2;
        text(g, "Next wave " + menu.nextWave() + ": +" + menu.nextReward(),
                rightX, y + 66, w / 2 - 18, CommandPalette.ACCENT_TEAL);

        String status = menu.voteSeconds() > 0
                ? "Retreat vote in progress: " + menu.voteSeconds() + "s"
                : menu.currentWave() > 0
                        ? "Surviving wave " + menu.currentWave()
                        : "Preparing for the next siege";
        text(g, status, rightX, y + 80, w / 2 - 18, CommandPalette.TEXT_MUTED);

        // Roster panel below.
        int rosterY = y + 118;
        int rosterH = h - (rosterY - y) - 22;
        CommandFrame.card(g, x + 10, rosterY, w - 20, rosterH, CommandPalette.ACCENT_ARCANE);
        CommandIcon.SCROLL.draw(g, x + 16, rosterY + 4, 12);
        text(g, "FACTION ROSTER", x + 32, rosterY + 6, w - 48,
                CommandPalette.ACCENT_ARCANE);

        int listTop = rosterY + 22;
        int lines = Math.max(1, (rosterH - 26) / 12);
        int count = menu.members().size();
        int maxOffset = Math.max(0, count - lines);
        rosterOffset = Math.max(0, Math.min(rosterOffset, maxOffset));

        if (count == 0) {
            text(g, "Roster is synchronizing...", x + 18, listTop, w - 36,
                    CommandPalette.TEXT_MUTED);
        } else {
            for (int i = 0; i < lines && i + rosterOffset < count; i++) {
                text(g, "-  " + menu.members().get(i + rosterOffset),
                        x + 18, listTop + i * 12, w - 36, CommandPalette.TEXT);
            }
        }
        // Scroll indicator when overflow is present.
        if (count > lines) {
            String indicator = String.format(Locale.ROOT,
                    "%d - %d of %d  |  Scroll",
                    rosterOffset + 1, Math.min(count, rosterOffset + lines), count);
            text(g, indicator, x + w - 18 - font.width(indicator),
                    rosterY + 6, w / 2, CommandPalette.TEXT_DIM);
        }
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
        return super.mouseScrolled(x, y, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dx, double dy) {
        if (tab == 3 && territory.mouseDragged(mouseX, mouseY, dx, dy)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public void onClose() {
        territory.reset();
        EntityPortrait.clear();
        super.onClose();
    }
}
