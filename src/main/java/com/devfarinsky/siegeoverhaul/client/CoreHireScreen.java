package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.core.*;
import com.devfarinsky.siegeoverhaul.siege.SiegeIntegration;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Command Center HUD — medieval-fantasy but modern-sleek presentation.
 *
 * <p>Purchase authority remains entirely on the server; this screen only
 * paints server-authoritative state from {@link CoreHireMenu} and dispatches
 * inventory-button clicks or {@link RaidNetwork} packets. Layout math and
 * server contracts are unchanged from the compact version — only the
 * presentation layer is rebuilt around {@link CommandFrame} and
 * {@link CommandPalette}. Controls use readable labels; only real Minecraft
 * item sprites are used where an icon communicates a concrete item.
 *
 * <p>Five tabs share the window:
 * <ul>
 *   <li><b>Army &amp; Heroes</b> — four rotating hire cards with live armored
 *       entity previews, readable kit details and siege deployment kits.</li>
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
    private Button feedbackButton;

    /** Bounded caches avoid remeasuring and rewrapping unchanged labels every frame. */
    private final Map<TextMeasureKey, String> fittedText = boundedCache(256);
    private final Map<TextMeasureKey, List<FormattedCharSequence>> wrappedText = boundedCache(256);

    private record TextMeasureKey(String value, int width) {}

    private static <K, V> Map<K, V> boundedCache(int maximumSize) {
        return new LinkedHashMap<>(64, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maximumSize;
            }
        };
    }

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
        fittedText.clear();
        wrappedText.clear();
        layout = CoreHireLayout.fit(width, height);
        imageWidth = layout.width();
        imageHeight = layout.height();
        super.init();

        // Text-only tabs stay readable at every GUI scale. The old decorative
        // glyphs were ambiguous and competed with the labels.
        String[] tabLabels = layout.compact()
                ? new String[]{"Army", "Loot", "Treasury", "Land", "Intel"}
                : new String[]{"Army", "Loot", "Treasury", "Territory", "Intel"};
        int tabCount = tabLabels.length;
        int tabWidth = layout.tabWidth(tabCount);
        for (int i = 0; i < tabCount; i++) {
            final int index = i;
            addRenderableWidget(new CoreButton(
                    Component.literal(tabLabels[i]),
                    b -> {
                        tab = index;
                        confirmBox = -1;
                        updateControlState();
                    },
                    layout.tabX(i, tabCount),
                    layout.tabY(),
                    tabWidth, CoreHireLayout.TAB_HEIGHT,
                    true,
                    () -> tab == index));
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
            int bankGap = layout.controlGap();
            int bw = (layout.width() - layout.outerMargin() * 2 - bankGap * 3) / 4;
            bank[i] = addRenderableWidget(new CoreButton(
                    Component.literal(bankLabels[i]),
                    b -> action(40 + index),
                    layout.x() + layout.outerMargin() + i * (bw + bankGap),
                    layout.contentY() + 50,
                    bw, 20,
                    false, () -> false, ItemIcons.EMERALD));
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
                    false, () -> false));
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
        int tbCellW = layout.territoryCardWidth();
        int tbCellH = layout.territoryCardHeight();
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
                    false, () -> false));
        }
        // Fortify Perimeter strip: 3 side-by-side material buttons that
        // commission a Villager Recruits Builder to wall off the territory
        // in the chosen material. Bank + inventory pay 900 emeralds per job.
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
                    false, () -> false));
        }

        // Persistent path for beta feedback. Minecraft shows its normal
        // external-link confirmation before opening CurseForge comments.
        int feedbackW = layout.feedbackWidth();
        feedbackButton = addRenderableWidget(new CoreButton(
                Component.literal(layout.compact() ? "Feedback" : "Leave Feedback"),
                b -> ConfirmLinkScreen.confirmLinkNow(FEEDBACK_URL, this, true),
                layout.x() + layout.width() - feedbackW - 8,
                layout.footerY() + 2,
                feedbackW, 14,
                false, () -> false));

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
        updateControlState();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateControlState();
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

        super.render(g, logicalMouseX, logicalMouseY, partial);
        g.pose().popPose();
        // Tooltips stay at Minecraft's normal readable scale. Hover testing
        // still uses logical coordinates so tiny-window scaling cannot shift
        // the target away from the rendered card or button.
        drawTooltips(g, logicalMouseX, logicalMouseY, mx, my);
    }

    /**
     * Button state changes at game-tick frequency, not frame frequency. The
     * old render loop allocated dozens of Components every frame (often
     * 100+ times per second), even while every value was unchanged.
     */
    private void updateControlState() {
        if (layout == null || hire[0] == null) return;
        for (int i = 0; i < 4; i++) {
            hire[i].visible = tab == 0;
            hire[i].active = menu.role(i) >= 0 && menu.cost(i) >= 0
                    && !menu.sold(i) && menu.rotation() > 0 && canAfford(menu.cost(i));
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
            long missing = canAfford(TerritoryBuffs.PRICES[i]) ? 0L : Math.max(0L, TerritoryBuffs.PRICES[i] - availableFunds());
            territoryBuffs[i].setMessage(Component.literal(layout.compact()
                    ? (owned ? "Active"
                            : (missing == 0L ? "Buy  ·  " + TerritoryBuffs.PRICES[i] + "e"
                            : "Need  ·  " + missing + "e"))
                    : (owned ? TerritoryBuffs.LABELS[i] + " (active)"
                            : (missing == 0L
                            ? TerritoryBuffs.LABELS[i] + "  ·  " + TerritoryBuffs.PRICES[i] + "e"
                            : TerritoryBuffs.LABELS[i] + "  ·  need " + missing + "e"))));
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
            boxes[i].visible = buffs[i].visible = tab == 1;
            boxes[i].active = waitingTicks == 0 && revealTicks == 0
                    && canAfford(CoreLoot.price(i));
            boxes[i].setMessage(Component.literal(
                    waitingTicks > 0 ? "Waiting..."
                            : revealTicks > 0 ? "Unsealing..."
                            : (confirmBox == i ? "Confirm  ·  " : "Open  ·  ")
                                    + CoreLoot.price(i) + "e"));

            boolean active = minecraft != null && minecraft.player != null
                    && minecraft.player.hasEffect(CoreBuffs.effect(i));
            buffs[i].active = !active && canAfford(CoreBuffs.PRICES[i]);
            buffs[i].setMessage(Component.literal(
                    active ? "Blessing active" : "Bless  ·  " + CoreBuffs.PRICES[i] + "e"));
        }
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
                                            : "Need " + emeralds(menu.cost(i) - availableFunds()) + " more.";
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
                    } else {
                        SiegeIntegration.Footprint footprint =
                                SiegeIntegration.footprintOf(SiegeYard.TYPES[i]);
                        String area = SiegeYard.deploymentAreaGuidance(footprint);
                        if (!canAfford(SiegeYard.PRICES[i])) {
                            info = "You need " + emeralds(SiegeYard.PRICES[i] - availableFunds())
                                    + " more (faction Treasury). Deployment requires a clear, solid, flat "
                                    + area + ".";
                        } else {
                            info = "Buy the kit now, then right-click the top of a clear, solid, flat "
                                    + area + " to deploy your " + SiegeYard.LABELS[i]
                                    + ". A failed placement keeps the kit.";
                        }
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
                        "Faction treasury: %,d emeralds  |  Next wave: +%,d  |  Interest: +%,d in %s (%.2f%%/day, in-game time)  |  Deposit emeralds here before purchasing.",
                        menu.bank(), menu.nextReward(), dailyInterest,
                        formatInterestCountdown(menu.ticksUntilInterest()),
                        menu.interestRate() / 100.0), tooltipX, tooltipY);
            }
            for (int i = 0; i < bank.length; i++) {
                if (bank[i].isMouseOver(mx, my)) {
                    boolean deposit = i < 2;
                    int amount = i % 2 == 0 ? 8 : 64;
                    tooltip(g, (deposit ? "Deposit " : "Withdraw ") + amount
                            + " emeralds " + (deposit
                                    ? "from your purse into the shared faction Treasury."
                                    : "from the shared faction Treasury into your purse."),
                            tooltipX, tooltipY);
                }
            }
        }
        if (tab == 3) {
            int cols = 2;
            int rows = (TerritoryBuffs.COUNT + cols - 1) / cols;
            int gridTop = layout.contentY();
            int gridBottom = layout.contentBottom() - 32;
            int cellW = layout.territoryCardWidth();
            int cellH = layout.territoryCardHeight();
            for (int i = 0; i < TerritoryBuffs.COUNT; i++) {
                int cx = layout.x() + 10 + (i % cols) * (cellW + 8);
                int cy = gridTop + (i / cols) * (cellH + 8);
                if (over(mx, my, cx, cy, cellW, cellH)) {
                    long missing = canAfford(TerritoryBuffs.PRICES[i]) ? 0L : Math.max(0L, TerritoryBuffs.PRICES[i] - availableFunds());
                    String state = menu.hasTerritoryBuff(i)
                            ? "Already active for the whole faction."
                            : missing == 0L
                                    ? "One-time purchase. Paid from the faction Treasury only."
                                    : "Need " + emeralds(missing) + " more (faction Treasury).";
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
        int maxByViewport = Math.max(1, width - 24);
        int maxByPanel = layout == null ? maxByViewport : Math.max(1, layout.width() - 24);
        int wrapWidth = Math.max(1, Math.min(300, Math.min(maxByViewport, maxByPanel)));
        g.renderTooltip(font, wrapped(text, wrapWidth), x, y);
    }

    private static String emeralds(long amount) {
        return String.format(Locale.ROOT, "%,d emeralds", Math.max(0L, amount));
    }

    private void text(GuiGraphics g, String text, int x, int y, int width, int color) {
        int available = Math.max(1, width);
        TextMeasureKey key = new TextMeasureKey(text, available);
        String fitted = fittedText.get(key);
        if (fitted == null) {
            fitted = font.plainSubstrByWidth(text, available);
            if (!fitted.equals(text) && available > font.width("…")) {
                fitted = font.plainSubstrByWidth(text,
                        available - font.width("…")) + "…";
            }
            fittedText.put(key, fitted);
        }
        g.drawString(font, fitted, x, y, color, false);
    }

    private List<FormattedCharSequence> wrapped(String text, int width) {
        int available = Math.max(1, width);
        TextMeasureKey key = new TextMeasureKey(text, available);
        List<FormattedCharSequence> cached = wrappedText.get(key);
        if (cached != null) return cached;
        List<FormattedCharSequence> lines = List.copyOf(
                font.split(Component.literal(text), available));
        wrappedText.put(key, lines);
        return lines;
    }

    private long availableFunds() {
        return menu.bank();
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
        HudAtlas.blit(g, HudAtlas.TREASURY_PILL, chipX, y + 6, chipW, 24);
        // Real vanilla emerald sprite so the currency readout always matches
        // the player's resource pack.
        ItemIcons.emerald(g, chipX + 7, y + 12, 14);
        // Top-right pill shows the player's personal emeralds (what they can
        // spend right now). The Bank card shows the faction-wide treasury.
        // Two different pools, two clearly different labels.
        text(g, layout.compact() ? "PURSE" : "YOUR PURSE",
                chipX + 26, y + 10, chipW - 32, CommandPalette.ACCENT_GOLD);
        text(g, purse, chipX + 26, y + 20, chipW - 32, CommandPalette.ACCENT_EMERALD);

        // Active-siege ribbon under the header.
        drawSiegeRibbon(g, x, y, w);

        // Tab body.
        if (tab == 0) {
            for (int i = 0; i < 4; i++) drawHire(g, i, mx, my);
        } else if (tab == 1) {
            for (int i = 0; i < 3; i++) { drawLoot(g, i); drawBuff(g, i); }
            drawLootReserve(g);
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

    /** Textured hanging crest banner rendered from the HUD atlas. */
    private void drawCrestBanner(GuiGraphics g, int x, int y) {
        HudAtlas.blit(g, HudAtlas.CREST_BANNER, x, y - 4);
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
        text(g, membersLine, x + 10, y + 4,
                layout.compact() ? 42 : 108, CommandPalette.TEXT_MUTED);

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
            int hintLeft = x + 126;
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
        var lines = wrapped(s, w);
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
        int gridBottom = layout.contentBottom() - 32;
        int cellW = layout.territoryCardWidth();
        int cellH = layout.territoryCardHeight();
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

            // Readable heading replaces the former ambiguous flag glyph.
            text(g, TerritoryBuffs.LABELS[i], cx + 10, cy + 10, cellW - 20, accent);

            // Multi-line description below the label.
            String desc = TerritoryBuffs.DESCRIPTIONS[i];
            if (layout.territoryDescriptionLines() > 0) {
                drawWrappedText(g, layout.compact() ? TerritoryBuffs.compactSummary(i) : desc,
                        cx + 10, cy + 28, cellW - 20, layout.territoryDescriptionLines(), CommandPalette.TEXT_MUTED);
            }

            // Price / status line just above the purchase button.
            String status;
            int statusColor;
            if (owned) {
                status = "Active";
                statusColor = CommandPalette.ACCENT_EMERALD;
            } else if (canAfford(TerritoryBuffs.PRICES[i])) {
                status = "Ready to buy";
                statusColor = CommandPalette.ACCENT_GOLD;
            } else {
                long missing = canAfford(TerritoryBuffs.PRICES[i]) ? 0L : Math.max(0L, TerritoryBuffs.PRICES[i] - availableFunds());
                status = "Need " + String.format(Locale.ROOT, "%,d", missing) + "e";
                statusColor = CommandPalette.ACCENT_STEEL;
            }
            // Compact cards communicate status through the button and tooltip.
            if (!layout.compact()) text(g, status, cx + 10, cy + cellH - 40, cellW - 20, statusColor);
        }
    }

    /** Contextual band below the header: shows wave state or peacetime hint. */
    private void drawSiegeRibbon(GuiGraphics g, int x, int y, int w) {
        int wave = menu.currentWave();
        int vote = menu.voteSeconds();
        boolean siege = wave > 0;
        boolean voting = vote > 0;

        int accent = voting ? CommandPalette.ACCENT_ARCANE
                : siege ? CommandPalette.ACCENT_BLOOD
                : CommandPalette.ACCENT_STEEL;
        String status = layout.compact()
                ? (voting ? "RETREAT VOTE" : siege ? "WAVE " + wave : "KINGDOM WATCH")
                : (voting ? "RETREAT VOTE" : siege ? "SIEGE ACTIVE  |  Wave " + wave : "KINGDOM WATCH");
        String detail = layout.compact()
                ? (voting ? vote + "s left" : "Reward +" + String.format(Locale.ROOT, "%,d", menu.nextReward()) + "e")
                : (voting ? vote + "s remaining"
                        : siege ? "Next reward +" + menu.nextReward() + " to Treasury"
                        : "Treasury +" + menu.nextReward() + " on next wave clear");

        int ribbonY = layout.ribbonY();

        CommandFrame.card(g, x + 10, ribbonY, w - 20, CoreHireLayout.RIBBON_HEIGHT, accent);
        text(g, status, x + 16, ribbonY + 4,
                Math.max(1, w / 2 - 20), accent);
        text(g, detail, x + w / 2, ribbonY + 4,
                Math.max(1, w / 2 - 14), CommandPalette.TEXT);
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

        // Descriptive blurb: role summary for regular recruits/workers,
        // ability line for heroes. Wrapped across up to three lines so the
        // player doesn't have to hover the tooltip to know what they're
        // hiring.
        String blurb = i == 3 ? HeroTraits.description(role) : roleBlurb(role);
        int blurbY = y + 34;
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
                : menu.cost(i) < 0 ? "--" : String.format(Locale.ROOT, "%,d", menu.cost(i)) + "e";
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
                : String.format(Locale.ROOT, "%,d", menu.cost(slot)) + "e";
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
        List<FormattedCharSequence> lines = wrapped(s, width);
        int drawn = Math.min(maxLines, lines.size());
        for (int i = 0; i < drawn; i++) {
            g.drawString(font, lines.get(i), x, y + i * 10, color, false);
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
        if (CoreHiring.isHero(role)) {
            String armor = switch (CoreHiring.heroTier(role)) {
                case 0 -> "iron";
                case 4 -> "netherite";
                default -> "diamond";
            };
            return "Kit: " + armor + " themed armor, " + HeroTraits.weaponName(role);
        }
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
            default -> "";
        };
    }

    private static String shortRole(int role) {
        if (CoreHiring.isHero(role)) return "Hero";
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
            default -> "Unit";
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
            text(g, CoreLoot.NAMES[i], done ? x + 23 : x + 8, y + 5,
                    done ? w - 29 : w - 16, CommandPalette.TEXT);
            return;
        }

        // Show the real reward item after unsealing. Before opening, readable
        // text carries the tier instead of an unexplained miniature glyph.
        int textLeft = x + 8;
        if (done) {
            CommandFrame.chip(g, x + 6, y + 5, 20, 20, CommandPalette.ACCENT_GOLD);
            g.renderItem(revealed, x + 8, y + 7);
            textLeft = x + 30;
        }

        text(g, CoreLoot.NAMES[i], textLeft, y + 6,
                x + w - textLeft - 6, CommandPalette.TEXT);
        String subtitle = opening ? "Unsealing the seal..."
                : done ? revealed.getHoverName().getString()
                : CoreLoot.floorTier(i) > 0
                        ? CoreLoot.rarity(CoreLoot.floorTier(i)) + " floor  |  Epic ceiling"
                        : "Sealed Loot Box  |  " + CoreLoot.odds();
        text(g, subtitle, textLeft, y + 17, x + w - textLeft - 6,
                opening ? CommandPalette.ACCENT_ARCANE
                        : done ? CommandPalette.tier(revealedTier)
                        : CommandPalette.TEXT_MUTED);

        // Price / progress line sits directly under the subtitle; the Open
        // button occupies the bottom band of the (now shorter) card.
        int infoY = y + 28;
        if (opening) {
            float progress = 1f - (revealTicks / (float) CoreLoot.OPEN_TICKS);
            CommandFrame.progress(g, x + 8, infoY, w - 16, 4,
                    progress, CommandPalette.ACCENT_ARCANE);
        } else if (done) {
            text(g, "Rarity: " + CoreLoot.rarity(revealedTier),
                    x + 8, infoY, w - 16, CommandPalette.tier(revealedTier));
        } else {
            ItemIcons.emerald(g, x + 8, infoY - 2, 10);
            text(g, String.valueOf(CoreLoot.price(i)),
                    x + 20, infoY, w - 26, CommandPalette.ACCENT_GOLD);
        }
    }

    /**
     * Free band under the three Loot rows. Rows are capped at the height they
     * need, so this strip is genuine spare room. Displays a static hint about
     * the chest odds so newcomers understand the rarity spread at a glance.
     */
    private void drawLootReserve(GuiGraphics g) {
        int h = layout.marketFreeHeight();
        if (h < 16) return;
        int x = layout.x() + 10;
        int w = layout.width() - 20;
        int y = layout.marketFreeTop();
        int bandH = Math.min(h, 30);
        CommandFrame.card(g, x, y, w, bandH, CommandPalette.ACCENT_STEEL);
        text(g, "Purchases hand you a sealed box  ·  open it in your inventory to reveal the loot",
                x + 8, y + 6, w - 16, CommandPalette.TEXT_MUTED);
        if (bandH >= 26) {
            text(g, "Pricier chests floor higher rarity  ·  Field roll from Common, Veteran from Uncommon, Royal from Rare.",
                    x + 8, y + 17, w - 16, CommandPalette.TEXT_DIM);
        }
    }

    private void drawBuff(GuiGraphics g, int i) {
        int x = layout.cardX(1), y = layout.marketY(i);
        int w = layout.cardWidth(), h = layout.marketHeight();
        CommandFrame.card(g, x, y, w, h, CommandPalette.ACCENT_TEAL);

        if (layout.compact()) {
            text(g, CoreBuffs.NAMES[i], x + 8, y + 5, w - 16, CommandPalette.TEXT);
            return;
        }
        text(g, CoreBuffs.NAMES[i], x + 8, y + 6, w - 16, CommandPalette.TEXT);
        text(g, CoreBuffs.DETAILS[i], x + 8, y + 17, w - 16, CommandPalette.TEXT_MUTED);

        ItemIcons.emerald(g, x + 8, y + 26, 10);
        text(g, CoreBuffs.PRICES[i] + "  |  personal blessing, 5 min",
                x + 20, y + 28, w - 26, CommandPalette.ACCENT_TEAL);
    }


    private void drawFaction(GuiGraphics g) {
        int x = layout.x();
        int w = layout.width();

        // Bank summary card up top: taller card so we have three text rows
        // per side without overlap.
        int bankCardH = 46;
        int bankY = layout.contentY();
        CommandFrame.card(g, x + 10, bankY, w - 20, bankCardH, CommandPalette.ACCENT_GOLD);

        int leftX = x + 18;
        long dailyInterest = (long) menu.bank() * menu.interestRate() / 10000L;
        String interestCountdown = formatInterestCountdown(menu.ticksUntilInterest());
        if (layout.compact()) {
            int compactW = w - (leftX - x) - 14;
            text(g, menu.factionName(), leftX, bankY + 6,
                    compactW, CommandPalette.TEXT);
            text(g, String.format(Locale.ROOT, "Treasury %,de  |  Next +%,de",
                            menu.bank(), menu.nextReward()),
                    leftX, bankY + 18, compactW, CommandPalette.ACCENT_EMERALD);
            text(g, String.format(Locale.ROOT, "Interest +%,d in %s  |  Wave %d",
                            dailyInterest, interestCountdown, menu.nextWave()),
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
            text(g, "Next wave " + menu.nextWave() + ": +" + menu.nextReward() + " to Treasury",
                    rightX, bankY + 6, rightW, CommandPalette.ACCENT_TEAL);
            text(g, String.format(Locale.ROOT, "Interest: +%,d in %s (%.2f%%/day)",
                            dailyInterest, interestCountdown, menu.interestRate() / 100.0),
                    rightX, bankY + 18, rightW, CommandPalette.ACCENT_GOLD);
            text(g, "All purchases use Treasury emeralds",
                    rightX, bankY + 30, rightW, CommandPalette.TEXT_DIM);
        }

        // Roster panel below (pushed down to make room for the taller bank card
        // and its Deposit/Withdraw button row). We split it into a left roster
        // column and a right activity-graph column when the roster is short
        // enough to leave room; otherwise the roster takes the full width.
        int rosterY = bankRosterY();
        int rosterH = bankRosterHeight();
        CommandFrame.card(g, x + 10, rosterY, w - 20, rosterH, CommandPalette.ACCENT_ARCANE);
        text(g, "FACTION ROSTER", x + 18, rosterY + 6, w - 34,
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
        EntityPortrait.clear();
        super.onClose();
    }

    /**
     * v4.28.8: format an in-game tick countdown as a short human string.
     * 20 ticks = 1 second of active play, 24000 ticks = one Minecraft day.
     * Examples: 24000 -> "1d", 6000 -> "5m", 200 -> "10s", 0 -> "soon".
     */
    static String formatInterestCountdown(int ticks) {
        if (ticks <= 0) return "soon";
        long seconds = ticks / 20L;
        if (seconds <= 0) return "soon";
        long days = seconds / 1200L; // 20 real minutes = 1 in-game day
        long remSec = seconds - days * 1200L;
        long minutes = remSec / 60L;
        long finalSec = remSec - minutes * 60L;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d");
        if (minutes > 0) { if (sb.length() > 0) sb.append(' '); sb.append(minutes).append("m"); }
        if (sb.length() == 0) sb.append(finalSec).append("s");
        return sb.toString();
    }
}
