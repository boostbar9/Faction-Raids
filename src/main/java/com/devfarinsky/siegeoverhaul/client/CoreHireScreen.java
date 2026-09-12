package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.core.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
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
 * <p>Five tabs share the window:
 * <ul>
 *   <li><b>Army &amp; Heroes</b> — four rotating hire cards with role icon,
 *       rarity ribbon and forged "Recruit" key, plus siege deployment kits.</li>
 *   <li><b>Loot &amp; Blessings</b> — three mystery-loot cards with animated
 *       reveal reel and three blessing cards with cool-down state.</li>
 *   <li><b>Bank &amp; Faction</b> — deposit/withdraw controls with an
 *       emerald-etched balance readout and a scrollable roster.</li>
 *   <li><b>Territory</b> — permanent faction-wide upgrades.</li>
 *   <li><b>Intel</b> — units, enemy lore and the field playbook.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = SiegeOverhaul.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CoreHireScreen extends AbstractContainerScreen<CoreHireMenu> {

    private static final String FEEDBACK_URL =
            "https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/comments";

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
    private final Button[] fortifyButtons = new Button[TerritoryFortification.MATERIALS.length];
    private final Button[] boxes = new Button[3];
    private final Button[] buffs = new Button[3];
    private final Button[] bank = new Button[4];
    private final TerritoryPanel territory = new TerritoryPanel();
    private Button recenterButton;
    private Button zoomInButton;
    private Button zoomOutButton;
    private Button feedbackButton;

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
        String[] tabLabels = layout.compact()
                ? new String[]{"Army", "Loot", "Bank", "Land", "Intel"}
                : new String[]{"Army", "Loot", "Bank", "Territory", "Intel"};
        CommandIcon[] tabIcons = {CommandIcon.SWORDS, CommandIcon.CHEST, CommandIcon.BANK, CommandIcon.MAP, CommandIcon.BOOK};
        int tabCount = tabLabels.length;
        int tabWidth = layout.tabWidth(tabCount);
        for (int i = 0; i < tabCount; i++) {
            final int index = i;
            addRenderableWidget(new CoreButton(
                    Component.literal(tabLabels[i]),
                    b -> { tab = index; confirmBox = -1; territory.reset(); },
                    layout.tabX(i, tabCount),
                    layout.tabY(),
                    tabWidth, CoreHireLayout.TAB_HEIGHT,
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
            int hirePortrait = hirePortraitSize();
            int hireX = layout.compact()
                    ? layout.cardX(i) + layout.cardWidth() - 49
                    : layout.cardX(i) + hirePortrait + 14;
            int hireY = layout.compact()
                    ? layout.cardY(i) + layout.cardHeight() - 18
                    : layout.cardY(i) + layout.cardHeight() - 25;
            int hireW = layout.compact()
                    ? 43
                    : layout.cardWidth() - hirePortrait - 22;
            int hireH = layout.compact() ? 14 : 20;
            hire[i] = addRenderableWidget(new CoreButton(
                    Component.literal("Hire"),
                    b -> RaidNetwork.purchaseCoreOffer(menu.containerId, index, menu.rotation()),
                    hireX, hireY, hireW, hireH,
                    false, () -> false));

            String[] bankLabels = layout.compact()
                    ? new String[]{"+8", "+64", "-8", "-64"}
                    : new String[]{"Deposit 8", "Deposit 64", "Withdraw 8", "Withdraw 64"};
            CommandIcon[] bankIcons = {CommandIcon.EMERALD, CommandIcon.EMERALD, CommandIcon.BANK, CommandIcon.BANK};
            int bankGap = layout.controlGap();
            int bw = (layout.width() - layout.outerMargin() * 2 - bankGap * 3) / 4;
            bank[i] = addRenderableWidget(new CoreButton(
                    Component.literal(bankLabels[i]),
                    b -> action(40 + index),
                    layout.x() + layout.outerMargin() + i * (bw + bankGap),
                    layout.contentY() + 50,
                    bw, 20,
                    false, () -> false, bankIcons[i]));
        }

        // Siege Yard buttons issue paid placement kits. The player then
        // right-clicks a clear outdoor area, avoiding failed auto-spawns when
        // the Core is indoors or boxed in by furniture/walls.
        int yardGap = layout.controlGap();
        int yardW = (layout.width() - layout.outerMargin() * 2 - yardGap) / 2;
        for (int i = 0; i < 2; i++) {
            final int index = i;
            siegeYard[i] = addRenderableWidget(new CoreButton(
                    Component.literal(SiegeYard.LABELS[i] + "  " + SiegeYard.PRICES[i]),
                    b -> action(50 + index),
                    layout.x() + layout.outerMargin() + i * (yardW + yardGap),
                    layout.siegeYardY(),
                    yardW, CoreHireLayout.ARMY_ACTION_HEIGHT,
                    false, () -> false,
                    i == 0 ? CommandIcon.SWORDS : CommandIcon.BOOK));
        }

        // Territory-level buff buttons: four one-time upgrades that apply
        // faction-wide effects. Placed as a 2x2 grid of tall purchase cards
        // that fill the tab body. No map, no zoom controls; this tab is a
        // pure upgrade shop for kingdom-wide territory buffs.
        int tbCols = 2;
        int tbRows = (TerritoryBuffs.COUNT + tbCols - 1) / tbCols;
        int tbGridTop = layout.contentY();
        // Reserve a bottom strip for the Fortify Perimeter material buttons.
        int fortifyStripH = 26;
        int tbGridBottom = layout.contentBottom() - fortifyStripH - 6;
        int tbCellW = (layout.width() - 20 - (tbCols - 1) * 8) / tbCols;
        int tbCellH = (tbGridBottom - tbGridTop - (tbRows - 1) * 8) / tbRows;
        // Purchase button lives inside its card, near the bottom-right.
        int btnH = 18;
        for (int i = 0; i < TerritoryBuffs.COUNT; i++) {
            final int index = i;
            int col = i % tbCols, row = i / tbCols;
            int cellX = layout.x() + 10 + col * (tbCellW + 8);
            int cellY = tbGridTop + row * (tbCellH + 8);
            int btnW = Math.min(140, tbCellW - 16);
            territoryBuffs[i] = addRenderableWidget(new CoreButton(
                    Component.literal(TerritoryBuffs.LABELS[i]),
                    b -> action(60 + index),
                    cellX + tbCellW - btnW - 8,
                    cellY + tbCellH - btnH - 6,
                    btnW, btnH,
                    false, () -> false, CommandIcon.FLAG));
        }
        // Fortify Perimeter strip: 3 side-by-side material buttons that
        // commission a Villager Recruits Builder to wall off the territory
        // in the chosen material. Bank + inventory pay 1200 emeralds per job.
        int stripY = tbGridBottom + 8;
        int stripH = 22;
        int stripW = layout.width() - 20;
        int fbGap = 6;
        int fbW = (stripW - (fortifyButtons.length - 1) * fbGap) / fortifyButtons.length;
        for (int i = 0; i < fortifyButtons.length; i++) {
            final int index = i;
            String label = TerritoryFortification.material(i).label();
            fortifyButtons[i] = addRenderableWidget(new CoreButton(
                    Component.literal(label + "  " + TerritoryFortification.PRICE + "e"),
                    b -> action(70 + index),
                    layout.x() + 10 + i * (fbW + fbGap),
                    stripY,
                    fbW, stripH,
                    false, () -> false, CommandIcon.FLAG));
        }

        // Recenter/zoom buttons removed; the Territory tab is now a pure
        // upgrade shop. Keep the button fields non-null for the render loop
        // by pointing them at hidden placeholders that render nothing.
        recenterButton = addRenderableWidget(new CoreButton(
                Component.literal(""), b -> {}, 0, -100, 1, 1, false, () -> false));
        zoomInButton = addRenderableWidget(new CoreButton(
                Component.literal(""), b -> {}, 0, -100, 1, 1, false, () -> false));
        zoomOutButton = addRenderableWidget(new CoreButton(
                Component.literal(""), b -> {}, 0, -100, 1, 1, false, () -> false));

        // Persistent path for beta feedback. Minecraft shows its normal
        // external-link confirmation before opening CurseForge comments.
        int feedbackW = layout.feedbackWidth();
        feedbackButton = addRenderableWidget(new CoreButton(
                Component.literal(layout.compact() ? "Feedback" : "Leave Feedback"),
                b -> ConfirmLinkScreen.confirmLinkNow(FEEDBACK_URL, this, true),
                layout.x() + layout.width() - feedbackW - 8,
                layout.footerY() + 2,
                feedbackW, 14,
                false, () -> false, CommandIcon.BOOK));

        // Loot boxes and blessing keys.
        for (int i = 0; i < 3; i++) {
            final int index = i;
            int keyY = layout.marketY(i) + layout.marketHeight() - (layout.compact() ? 17 : 21);
            int keyXInset = layout.compact() ? 23 : 7;
            int keyHeight = layout.compact() ? 14 : 17;
            boxes[i] = addRenderableWidget(new CoreButton(
                    Component.literal("Open"),
                    b -> {
                        if (confirmBox != index) { confirmBox = index; return; }
                        if (waitingTicks > 0 || revealTicks > 0) return;
                        waitingTicks = 60;
                        action(20 + index);
                        confirmBox = -1;
                    },
                    layout.cardX(0) + keyXInset, keyY,
                    layout.cardWidth() - keyXInset - 6, keyHeight,
                    false, () -> confirmBox == index));
            buffs[i] = addRenderableWidget(new CoreButton(
                    Component.literal("Bless"),
                    b -> action(30 + index),
                    layout.cardX(1) + keyXInset, keyY,
                    layout.cardWidth() - keyXInset - 6, keyHeight,
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

        int logicalMouseX = layout.logicalX(mx);
        int logicalMouseY = layout.logicalY(my);
        g.pose().pushPose();
        g.pose().scale(layout.scale(), layout.scale(), 1.0F);

        // Wire visibility/active state of buttons before AbstractContainerScreen paints them.
        for (int i = 0; i < 4; i++) {
            hire[i].visible = tab == 0;
            hire[i].active = menu.role(i) >= 0 && menu.cost(i) >= 0
                    && !menu.sold(i) && menu.rotation() > 0;
            String actionLabel = menu.sold(i)
                    ? "Hired"
                    : menu.cost(i) < 0
                            ? (layout.compact() ? "N/A" : "Unavailable")
                            : "Hire";
            hire[i].setMessage(Component.literal(actionLabel));

            bank[i].visible = tab == 2;
            bank[i].active = i < 2 ? menu.emeralds() > 0 : menu.canWithdraw() && menu.bank() > 0;
        }
        for (int i = 0; i < siegeYard.length; i++) {
            siegeYard[i].visible = tab == 0;
            siegeYard[i].active = SiegeYard.available() && canAfford(SiegeYard.PRICES[i]);
            String shortLabel = i == 0 ? "Catapult" : "Ballista";
            siegeYard[i].setMessage(Component.literal(
                    SiegeYard.available()
                            ? (layout.compact() ? shortLabel : shortLabel + " Kit")
                                    + "  ·  " + SiegeYard.PRICES[i] + "e"
                            : shortLabel + "  ·  unavailable"));
        }
        for (int i = 0; i < territoryBuffs.length; i++) {
            territoryBuffs[i].visible = tab == 3;
            boolean owned = menu.hasTerritoryBuff(i);
            territoryBuffs[i].active = !owned && canAfford(TerritoryBuffs.PRICES[i]);
            territoryBuffs[i].setMessage(Component.literal(
                    layout.compact()
                            ? (owned ? "Active" : "Buy  ·  " + TerritoryBuffs.PRICES[i] + "e")
                            : (owned ? TerritoryBuffs.LABELS[i] + " (active)"
                                    : TerritoryBuffs.LABELS[i] + "  ·  " + TerritoryBuffs.PRICES[i] + "e")));
        }
        for (int i = 0; i < fortifyButtons.length; i++) {
            fortifyButtons[i].visible = tab == 3;
            fortifyButtons[i].active = canAfford(TerritoryFortification.PRICE);
            String label = TerritoryFortification.material(i).label();
            fortifyButtons[i].setMessage(Component.literal(
                    layout.compact() ? label
                            : "Fortify  " + label + "  " + TerritoryFortification.PRICE + "e"));
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
                            : (confirmBox == i ? "Confirm  ·  " : "Open  ·  ")
                                    + CoreLoot.price(i) + "e"));

            boolean active = minecraft != null && minecraft.player != null
                    && minecraft.player.hasEffect(CoreBuffs.effect(i));
            buffs[i].active = !active && menu.emeralds() >= CoreBuffs.PRICES[i];
            buffs[i].setMessage(Component.literal(
                    active ? "Blessing active" : "Bless  ·  " + CoreBuffs.PRICES[i] + "e"));
        }

        // Draw an active-tab under-glow before the tab bar paints so the
        // active tab gets a soft candlelight backing.
        drawActiveTabGlow(g);

        super.render(g, logicalMouseX, logicalMouseY, partial);
        g.pose().popPose();
        // Tooltips stay at Minecraft's normal readable scale. Hover testing
        // still uses logical coordinates so tiny-window scaling cannot shift
        // the target away from the rendered card or button.
        drawTooltips(g, logicalMouseX, logicalMouseY, mx, my);
    }

    /**
     * Paint a soft additive glow behind the currently active tab button so
     * the selected tab clearly reads as "lit". Tab button positions match
     * the layout in {@link #init()}.
     */
    private void drawActiveTabGlow(GuiGraphics g) {
        int tabCount = 5;
        int tabWidth = layout.tabWidth(tabCount);
        int gx = layout.tabX(tab, tabCount);
        int gy = layout.tabY();
        HudAtlas.enableAdditive();
        HudAtlas.blitTinted(g, HudAtlas.GLOW_SOFT,
                gx - 8, gy - 6, tabWidth + 16, 32, 0x50ffd08a);
        HudAtlas.disableAdditive();
    }

    private void drawTooltips(GuiGraphics g, int mx, int my,
                              int tooltipX, int tooltipY) {
        if (tab == 0) {
            for (int i = 0; i < 4; i++) {
                if (over(mx, my, layout.cardX(i), layout.cardY(i),
                        layout.cardWidth(), layout.cardHeight()) && menu.role(i) >= 0) {
                    int role = menu.role(i);
                    String purchaseState = menu.sold(i)
                            ? "Already hired this rotation."
                            : menu.cost(i) < 0
                                    ? "This offer is unavailable."
                                    : canAfford(menu.cost(i))
                                            ? "Ready to hire."
                                            : "Need " + (menu.cost(i) - availableFunds())
                                                    + " more emeralds.";
                    String info = CoreHiring.NAMES[role]
                            + "  |  " + CoreHiring.rarity(role)
                            + "  |  " + (i == 3 ? HeroTraits.description(role) : roleBlurb(role))
                            + "  |  " + kitDescriptor(role)
                            + "  |  " + purchaseState
                            + "  |  Shared faction offer; refreshes every 15 minutes.";
                    tooltip(g, info, tooltipX, tooltipY);
                }
            }
            for (int i = 0; i < siegeYard.length; i++) {
                if (siegeYard[i].isMouseOver(mx, my)) {
                    String info;
                    if (!SiegeYard.available()) {
                        info = "Requires both Villager Recruits and Siege Weapons.";
                    } else if (!canAfford(SiegeYard.PRICES[i])) {
                        info = "You need " + (SiegeYard.PRICES[i] - availableFunds())
                                + " more emeralds (purse + faction bank).";
                    } else {
                        info = "Buy the kit now, then right-click the top of a clear, solid, flat 3x3 area to deploy your "
                                + SiegeYard.LABELS[i] + ". A failed placement keeps the kit.";
                    }
                    tooltip(g, info, tooltipX, tooltipY);
                }
            }
        }
        if (feedbackButton != null && feedbackButton.isMouseOver(mx, my)) {
            tooltip(g, "Open the Siege Overhaul CurseForge comments page to share feedback or report a problem.",
                    tooltipX, tooltipY);
        }
        if (tab == 1) {
            for (int i = 0; i < 3; i++) {
                if (over(mx, my, layout.cardX(0), layout.marketY(i),
                        layout.cardWidth(), layout.marketHeight())) {
                    String t = revealBox == i && revealTicks == 0 && !revealed.isEmpty()
                            ? CoreLoot.rarity(revealedTier) + "  |  " + revealed.getCount()
                                    + "x " + revealed.getHoverName().getString()
                            : "One mystery reward  |  " + CoreLoot.odds();
                    tooltip(g, t, tooltipX, tooltipY);
                }
                if (over(mx, my, layout.cardX(1), layout.marketY(i),
                        layout.cardWidth(), layout.marketHeight())) {
                    tooltip(g, CoreBuffs.DETAILS[i]
                            + " for 5 minutes. Uses your personal emeralds;"
                            + " existing effects are preserved.", tooltipX, tooltipY);
                }
            }
        }
        if (tab == 2) {
            int bankY = layout.contentY();
            if (over(mx, my, layout.x() + 10, bankY,
                    layout.width() - 20, 46)) {
                long dailyInterest = (long) menu.bank() * menu.interestRate() / 10000L;
                tooltip(g, String.format(Locale.ROOT,
                        "Faction treasury: %,d emeralds  |  Next wave: +%,d  |  Daily interest: +%,d (%.2f%%)  |  Purchases use the bank before your purse.",
                        menu.bank(), menu.nextReward(), dailyInterest,
                        menu.interestRate() / 100.0), tooltipX, tooltipY);
            }
            for (int i = 0; i < bank.length; i++) {
                if (bank[i].isMouseOver(mx, my)) {
                    boolean deposit = i < 2;
                    int amount = i % 2 == 0 ? 8 : 64;
                    tooltip(g, (deposit ? "Deposit " : "Withdraw ") + amount
                            + " emeralds " + (deposit
                                    ? "from your purse into the shared faction bank."
                                    : "from the shared faction bank into your purse."),
                            tooltipX, tooltipY);
                }
            }
        }
        if (tab == 3) {
            int cols = 2;
            int rows = (TerritoryBuffs.COUNT + cols - 1) / cols;
            int gridTop = layout.contentY();
            int gridBottom = layout.contentBottom();
            int cellW = (layout.width() - 20 - (cols - 1) * 8) / cols;
            int cellH = (gridBottom - gridTop - (rows - 1) * 8) / rows;
            for (int i = 0; i < TerritoryBuffs.COUNT; i++) {
                int cx = layout.x() + 10 + (i % cols) * (cellW + 8);
                int cy = gridTop + (i / cols) * (cellH + 8);
                if (over(mx, my, cx, cy, cellW, cellH)) {
                    String state = menu.hasTerritoryBuff(i)
                            ? "Already active for the whole faction."
                            : canAfford(TerritoryBuffs.PRICES[i])
                                    ? "Ready to purchase from bank + purse."
                                    : "Need " + (TerritoryBuffs.PRICES[i] - availableFunds())
                                            + " more emeralds.";
                    tooltip(g, TerritoryBuffs.LABELS[i] + "  |  "
                            + TerritoryBuffs.DESCRIPTIONS[i] + "  |  " + state,
                            tooltipX, tooltipY);
                }
            }
        }
    }

    private boolean over(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void tooltip(GuiGraphics g, String text, int x, int y) {
        g.renderTooltip(font, font.split(Component.literal(text),
                Math.max(1, Math.min(300, width - 24))), x, y);
    }

    private void text(GuiGraphics g, String text, int x, int y, int width, int color) {
        int available = Math.max(1, width);
        String fitted = font.plainSubstrByWidth(text, available);
        if (!fitted.equals(text) && available > font.width("…")) {
            fitted = font.plainSubstrByWidth(text,
                    available - font.width("…")) + "…";
        }
        g.drawString(font, fitted, x, y, color, false);
    }

    private long availableFunds() {
        return (long) menu.emeralds() + menu.bank();
    }

    private boolean canAfford(int price) {
        boolean creative = minecraft != null && minecraft.player != null
                && minecraft.player.getAbilities().instabuild;
        return creative || availableFunds() >= price;
    }

    private int hirePortraitSize() {
        return layout.compact() ? Math.min(28, layout.cardHeight() - 10)
                : Math.min(layout.cardHeight() - 12, 88);
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
        String title = layout.compact() ? "COMMAND" : "KINGDOM COMMAND";
        g.drawString(font, title, x + 43, y + 13, 0xff000000, false);
        g.drawString(font, title, x + 42, y + 13, CommandPalette.BEVEL_DARK, false);
        text(g, title, x + 42, y + 12, w / 2 - 50, CommandPalette.ACCENT_GOLD);
        text(g, menu.factionName(),
                x + 42, y + 22, w / 2 - 50, CommandPalette.TEXT_MUTED);

        // Treasury pill on the right, using the textured rounded pill from
        // the atlas plus an emerald icon glyph and a live-updating balance.
        // Leaves 26px of room on the far right for the close (X) button.
        String purse = String.format(Locale.ROOT, "%,d", menu.emeralds());
        int chipW = Math.max(layout.compact() ? 82 : 96,
                Math.min(w / 3, font.width(purse) + (layout.compact() ? 44 : 60)));
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
        text(g, layout.compact() ? "PURSE" : "YOUR PURSE",
                chipX + 26, y + 10, chipW - 32, CommandPalette.ACCENT_GOLD);
        text(g, purse, chipX + 26, y + 20, chipW - 32, CommandPalette.ACCENT_EMERALD);

        // Active-siege ribbon under the header.
        drawSiegeRibbon(g, x, y, w);

        // Drifting golden motes across the tab body for atmosphere. Motes
        // are procedural so they cost no textures; positions come from a
        // stable hash mixed with real time so they slowly drift diagonally.
        drawMotes(g, x + 4, layout.contentY(), w - 8,
                Math.max(0, layout.contentBottom() - layout.contentY()));

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
        drawFooterStrip(g, x, layout.footerY(), w);
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
        // Footer is its own fixed band. Keeping all controls inside this band
        // prevents the Army deployment row from colliding at large GUI scales.
        g.fill(x + 4, y, x + w - 4, y + CoreHireLayout.FOOTER_HEIGHT,
                0xB0100C08);
        g.fill(x + 6, y, x + w - 6, y + 1, CommandPalette.HAIRLINE);

        int feedbackW = layout.feedbackWidth();
        int feedbackLeft = x + w - feedbackW - 8;

        // Faction member count on the left.
        int members = menu.members().size();
        String membersLine = layout.compact()
                ? Integer.toString(members)
                : members + (members == 1 ? " member" : " members");
        CommandIcon.SHIELD.draw(g, x + 10, y + 2, 10);
        text(g, membersLine, x + 24, y + 4,
                layout.compact() ? 28 : 94, CommandPalette.TEXT_MUTED);

        // Context hint in the middle.
        String hint = switch (tab) {
            case 0 -> "Shared stock rotates every 15 minutes";
            case 1 -> "Loot & blessings use personal emeralds";
            case 2 -> "Interest " + menu.interestRate() / 100.0 + "% every 24h";
            case 3 -> "Faction-wide upgrades apply to every member";
            case 4 -> "Unit reference, enemy lore and field guidance";
            default -> "";
        };
        if (!layout.compact()) {
            int hintLeft = x + 122;
            int hintRight = tab == 0 ? feedbackLeft - 92 : feedbackLeft - 8;
            int hintArea = Math.max(1, hintRight - hintLeft);
            int hintW = Math.min(font.width(hint), hintArea);
            text(g, hint, hintLeft + Math.max(0, (hintArea - hintW) / 2),
                    y + 4, hintW, CommandPalette.TEXT_DIM);
        }

        // Refresh timer on the right (only for tabs where it applies).
        if (tab == 0) {
            String timer = String.format(Locale.ROOT, "Refresh %d:%02d",
                    menu.seconds() / 60, menu.seconds() % 60);
            int tw = font.width(timer);
            int timerRight = feedbackLeft - 8;
            text(g, timer, Math.max(x + 56, timerRight - tw), y + 4,
                    Math.max(1, Math.min(tw + 2, timerRight - (x + 56))),
                    CommandPalette.TEXT_MUTED);
        }
    }

    /**
     * Intel tab: three sub-sections (Units, Enemy Lore, How to Play) that
     * bake in the old Warlord's Codex content so the player no longer needs
     * to spawn a book.
     */
    private void drawIntel(GuiGraphics g) {
        int x = layout.x() + 10, y = layout.contentY();
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
        enableLayoutScissor(g, x + 2, bodyY + 2,
                contentRight, bodyY + bodyH - 2);
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

    /** GuiGraphics scissor coordinates do not inherit the pose-stack scale. */
    private void enableLayoutScissor(GuiGraphics g, int left, int top,
                                     int right, int bottom) {
        float scale = layout.scale();
        g.enableScissor(
                (int) Math.floor(left * scale),
                (int) Math.floor(top * scale),
                (int) Math.ceil(right * scale),
                (int) Math.ceil(bottom * scale));
    }

    private void drawTerritory(GuiGraphics g) {
        // Territory tab is a pure upgrade shop: four one-time faction-wide
        // purchases laid out as a 2x2 grid of tall cards. Each card shows
        // the label, a short description, price and status, with the
        // purchase button in the bottom-right (added in init()).
        int cols = 2;
        int rows = (TerritoryBuffs.COUNT + cols - 1) / cols;
        int gridTop = layout.contentY();
        int gridBottom = layout.contentBottom();
        int cellW = (layout.width() - 20 - (cols - 1) * 8) / cols;
        int cellH = (gridBottom - gridTop - (rows - 1) * 8) / rows;
        for (int i = 0; i < TerritoryBuffs.COUNT; i++) {
            int col = i % cols, row = i / cols;
            int cx = layout.x() + 10 + col * (cellW + 8);
            int cy = gridTop + row * (cellH + 8);
            boolean owned = menu.hasTerritoryBuff(i);
            int accent = owned ? CommandPalette.ACCENT_EMERALD
                    : (canAfford(TerritoryBuffs.PRICES[i])
                            ? CommandPalette.ACCENT_GOLD
                            : CommandPalette.ACCENT_STEEL);
            if (owned) CommandFrame.cardDimmed(g, cx, cy, cellW, cellH);
            else CommandFrame.card(g, cx, cy, cellW, cellH, accent);

            // Flag icon + label at top of card.
            CommandIcon.FLAG.draw(g, cx + 8, cy + 8, 14);
            text(g, TerritoryBuffs.LABELS[i], cx + 28, cy + 10, cellW - 34, accent);

            // Multi-line description below the label.
            if (!layout.compact()) {
                String desc = TerritoryBuffs.DESCRIPTIONS[i];
                drawWrapped(g, desc, cx + 10, cy + 28, cellW - 20, CommandPalette.TEXT);
            }

            // Price / status line just above the purchase button.
            String status;
            int statusColor;
            if (owned) {
                status = "Active";
                statusColor = CommandPalette.ACCENT_EMERALD;
            } else {
                status = TerritoryBuffs.PRICES[i] + "e";
                statusColor = CommandPalette.ACCENT_GOLD;
            }
            text(g, status, cx + 10,
                    layout.compact() ? cy + 25 : cy + cellH - 40,
                    80, statusColor);
        }
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
        String status = layout.compact()
                ? (voting ? "RETREAT VOTE" : siege ? "WAVE " + wave : "KINGDOM WATCH")
                : (voting ? "RETREAT VOTE" : siege ? "SIEGE ACTIVE  |  Wave " + wave : "KINGDOM WATCH");
        String detail = layout.compact()
                ? (voting ? vote + "s left" : "Reward +" + menu.nextReward() + "e")
                : (voting ? vote + "s remaining"
                        : siege ? "Next reward +" + menu.nextReward() + " to bank"
                        : "Bank +" + menu.nextReward() + " on next wave clear");

        int ribbonY = layout.ribbonY();

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

        CommandFrame.card(g, x + 10, ribbonY, w - 20, CoreHireLayout.RIBBON_HEIGHT, accent);
        icon.draw(g, x + 14, ribbonY + 2, 11);
        text(g, status, x + 29, ribbonY + 4,
                Math.max(1, w / 2 - 33), accent);
        text(g, detail, x + w / 2, ribbonY + 4,
                Math.max(1, w / 2 - 14), CommandPalette.TEXT);

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
            emblem.draw(g, x + w - 46, layout.contentY() - 16, 32);
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

        if (layout.compact()) {
            drawCompactHire(g, i, role, x, y, w, h, mouseX, mouseY);
            return;
        }

        // Portrait tile on the left with heraldic corner brackets around it,
        // matching the reference kingdom-command mockup.
        int portrait = hirePortraitSize();
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
        int chipY = y + h - 42;
        int maxBlurbLines = Math.max(1, Math.min(3,
                (chipY - blurbY - 2) / 10));
        int blurbLines = drawWrappedText(g, blurb, infoLeft, blurbY,
                infoWidth, maxBlurbLines, CommandPalette.TEXT_MUTED);

        // Stat/armor line: shows the loadout tier text and the armor material
        // so players understand what armor the unit is going to wear. Regular
        // recruits are cloth/leather; workers wear their tool kit; heroes wear
        // a themed trimmed set from HeroTraits.
        String kit = kitDescriptor(role);
        int kitY = blurbY + Math.min(blurbLines, 3) * 10 + 2;
        if (kitY + 9 < chipY) {
            text(g, kit, infoLeft, kitY, infoWidth, CommandPalette.ACCENT_TEAL);
        }

        // Item chip in the bottom-left of the info column: shows the recruit's
        // signature item (bread, arrows, tool). Sits above the Hire button.
        g.renderItem(menu.getSlot(i).getItem(), infoLeft, chipY);
        // Cost readout next to the item so the player sees the price at a
        // glance without hovering.
        String price = menu.sold(i) ? "Hired"
                : menu.cost(i) < 0 ? "--" : menu.cost(i) + "e";
        int priceColor = menu.sold(i) || menu.cost(i) < 0
                ? CommandPalette.TEXT_DIM
                : canAfford(menu.cost(i))
                        ? CommandPalette.ACCENT_EMERALD
                        : CommandPalette.ACCENT_BLOOD;
        text(g, price, infoLeft + 20, chipY + 4,
                infoWidth - 20, priceColor);
    }

    /**
     * Dense Army card used when Minecraft's scaled GUI is narrow or short.
     * It deliberately keeps only the identity, role and price in the card;
     * the full kit/ability detail remains available through the card tooltip.
     */
    private void drawCompactHire(GuiGraphics g, int slot, int role,
                                 int x, int y, int w, int h,
                                 int mouseX, int mouseY) {
        int portrait = hirePortraitSize();
        int px = x + 5;
        int py = y + Math.max(4, (h - portrait) / 2 - 1);
        EntityPortrait.draw(g, role, px, py, portrait, mouseX, mouseY);

        int infoLeft = px + portrait + 6;
        int infoWidth = Math.max(1, x + w - infoLeft - 5);
        text(g, CoreHiring.NAMES[role], infoLeft, y + 5,
                infoWidth, CommandPalette.TEXT);
        text(g, slot == 3 ? "Hero" : slot == 2 ? "Worker" : shortRole(role),
                infoLeft, y + 16, infoWidth, CommandPalette.TEXT_MUTED);

        String price = menu.sold(slot) ? "Hired"
                : menu.cost(slot) < 0 ? "--"
                : menu.cost(slot) + "e";
        int priceRight = hire[slot].getX() - 3;
        text(g, price, infoLeft, y + h - 14,
                Math.max(1, priceRight - infoLeft),
                menu.sold(slot) || menu.cost(slot) < 0
                        ? CommandPalette.TEXT_DIM
                        : canAfford(menu.cost(slot))
                                ? CommandPalette.ACCENT_EMERALD
                                : CommandPalette.ACCENT_BLOOD);
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

        if (layout.compact()) {
            if (done) g.renderItem(revealed, x + 4, y + Math.max(2, (h - 16) / 2));
            else CommandIcon.CHEST.draw(g, x + 6, y + Math.max(3, (h - 12) / 2), 12);
            text(g, CoreLoot.NAMES[i], x + 23, y + 5, w - 29, CommandPalette.TEXT);
            return;
        }

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
        if (layout.compact()) {
            icon.draw(g, x + 6, y + Math.max(3, (h - 12) / 2), 12);
            text(g, CoreBuffs.NAMES[i], x + 23, y + 5, w - 29, CommandPalette.TEXT);
            return;
        }
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
        int x = layout.x();
        int w = layout.width();

        // Bank summary card up top: taller card so we have three text rows
        // per side without overlap.
        int bankCardH = 46;
        int bankY = layout.contentY();
        CommandFrame.card(g, x + 10, bankY, w - 20, bankCardH, CommandPalette.ACCENT_GOLD);

        // Textured bank coin-stack icon with a soft glow.
        int coinX = x + 14, coinY = bankY + 4;
        HudAtlas.enableAdditive();
        HudAtlas.blitTinted(g, HudAtlas.GLOW_SOFT,
                coinX - 8, coinY - 4, 42, 34, 0x80ffdd8a);
        HudAtlas.disableAdditive();
        HudAtlas.blit(g, HudAtlas.ICON_BANK, coinX, coinY);

        int leftX = x + 46;
        long dailyInterest = (long) menu.bank() * menu.interestRate() / 10000L;
        if (layout.compact()) {
            int compactW = w - (leftX - x) - 14;
            text(g, menu.factionName(), leftX, bankY + 6,
                    compactW, CommandPalette.TEXT);
            text(g, String.format(Locale.ROOT, "Treasury %,de  |  Next +%,de",
                            menu.bank(), menu.nextReward()),
                    leftX, bankY + 18, compactW, CommandPalette.ACCENT_EMERALD);
            text(g, String.format(Locale.ROOT, "Interest +%,d/day  |  Wave %d",
                            dailyInterest, menu.nextWave()),
                    leftX, bankY + 30, compactW, CommandPalette.TEXT_MUTED);
        } else {
            int leftW = w / 2 - 52;
            text(g, menu.factionName(), leftX, bankY + 6, leftW, CommandPalette.TEXT);
            text(g, String.format(Locale.ROOT, "Treasury: %,d emeralds", menu.bank()),
                    leftX, bankY + 18, leftW, CommandPalette.ACCENT_EMERALD);
            // Status line (peace / siege / retreat vote) below treasury.
            String status = menu.voteSeconds() > 0
                    ? "Retreat vote: " + menu.voteSeconds() + "s remaining"
                    : menu.currentWave() > 0
                            ? "Surviving wave " + menu.currentWave()
                            : "Preparing for the next siege";
            text(g, status, leftX, bankY + 30, leftW, CommandPalette.TEXT_MUTED);

            int rightX = x + w / 2;
            int rightW = w / 2 - 18;
            text(g, "Next wave " + menu.nextWave() + ": +" + menu.nextReward() + " to bank",
                    rightX, bankY + 6, rightW, CommandPalette.ACCENT_TEAL);
            text(g, String.format(Locale.ROOT, "Interest: +%,d /24h (%.2f%%)",
                            dailyInterest, menu.interestRate() / 100.0),
                    rightX, bankY + 18, rightW, CommandPalette.ACCENT_GOLD);
            text(g, "Purchases pull from bank first, then your pack",
                    rightX, bankY + 30, rightW, CommandPalette.TEXT_DIM);
        }

        // Roster panel below (pushed down to make room for the taller bank card
        // and its Deposit/Withdraw button row). We split it into a left roster
        // column and a right activity-graph column when the roster is short
        // enough to leave room; otherwise the roster takes the full width.
        int rosterY = bankRosterY();
        int rosterH = bankRosterHeight();
        CommandFrame.card(g, x + 10, rosterY, w - 20, rosterH, CommandPalette.ACCENT_ARCANE);
        CommandIcon.SCROLL.draw(g, x + 16, rosterY + 4, 12);
        text(g, "FACTION ROSTER", x + 32, rosterY + 6, w - 48,
                CommandPalette.ACCENT_ARCANE);

        boolean showGraph = !layout.compact() && rosterH >= 82;
        int graphW = showGraph ? Math.min(180, (w - 20) / 2 - 8) : 0;
        int rosterListW = showGraph ? w - 32 - graphW - 8 : w - 36;
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
        if (showGraph) {
            int gx = x + 18 + rosterListW + 8;
            int gy = rosterY + 22;
            int gh = rosterH - 30;
            drawBankGraph(g, gx, gy, graphW, gh);
        }
    }

    private int bankRosterY() {
        return layout.contentY() + 74;
    }

    private int bankRosterHeight() {
        return Math.max(34, layout.contentBottom() - bankRosterY());
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
        x = layout.logicalX(x);
        y = layout.logicalY(y);
        // Territory tab is now a pure upgrade shop; no map scroll needed.
        if (tab == 2) {
            int count = menu.members().size();
            int rosterH = bankRosterHeight();
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
        mouseX = layout.logicalX(mouseX);
        mouseY = layout.logicalY(mouseY);
        if (tab == 4 && button == 0) {
            // Sub-tab strip at the top of the Intel panel: hit-test one of
            // three equal segments and switch section.
            int x = layout.x() + 10, y = layout.contentY(), w = layout.width() - 20;
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
        // Territory tab is now a pure upgrade shop; no map click handling.
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        mouseX = layout.logicalX(mouseX);
        mouseY = layout.logicalY(mouseY);
        if (intelDragging && button == 0) { intelDragging = false; return true; }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dx, double dy) {
        mouseX = layout.logicalX(mouseX);
        mouseY = layout.logicalY(mouseY);
        dx /= layout.scale();
        dy /= layout.scale();
        // Territory tab is now a pure upgrade shop; no map drag handling.
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
