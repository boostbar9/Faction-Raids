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
 *   <li><b>Treasury</b> — deposit/withdraw controls, glanceable financial
 *       metrics, a scrollable roster and recent activity.</li>
 *   <li><b>Territory</b> — permanent faction-wide upgrades and builder
 *       fortification contracts.</li>
 *   <li><b>Intel</b> — units, enemy lore and the field playbook.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = SiegeOverhaul.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CoreHireScreen extends AbstractContainerScreen<CoreHireMenu> {

    private static final String FEEDBACK_URL =
            "https://www.curseforge.com/minecraft/mc-mods/siege-overhaul/comments";
    private static final String[] PAGE_TITLES = {
            "War Council", "Olympian Reliquary", "Faction Treasury",
            "Kingdom Development", "Warlord Intelligence"
    };
    private static final String[] PAGE_SUBTITLES = {
            "Recruit defenders, specialists and legendary heroes",
            "Unseal divine spoils and prepare battlefield blessings",
            "Manage shared wealth, rewards and faction activity",
            "Commission permanent upgrades and perimeter works",
            "Study units, enemy hosts and defensive doctrine"
    };


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
        // in the chosen material. The faction Treasury pays 900 emeralds.
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
            territoryBuffs[i].setMessage(Component.literal(
                    owned ? "Active"
                            : missing == 0L
                                    ? (layout.compact() ? "Buy" : "Enact")
                                            + "  ·  " + TerritoryBuffs.PRICES[i] + "e"
                                    : "Need  ·  " + missing + "e"));
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
                            + " for 5 minutes. Uses the faction Treasury;"
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
            for (int i = 0; i < fortifyButtons.length; i++) {
                if (fortifyButtons[i].isMouseOver(mx, my)) {
                    String material = TerritoryFortification.material(i).label();
                    String affordability = canAfford(TerritoryFortification.PRICE)
                            ? "Treasury funds are ready."
                            : "Need " + emeralds(TerritoryFortification.PRICE - availableFunds())
                                    + " more in the faction Treasury.";
                    tooltip(g, "Commission a " + material
                                    + " perimeter from a Workers 2 builder. Requires a nearby"
                                    + " Builder-enabled storage area with materials. " + affordability,
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

    /** Draw a right-aligned, text-only state badge and return its width. */
    private int drawBadge(GuiGraphics g, String label, int right, int y, int accent) {
        int badgeWidth = font.width(label) + 10;
        int left = right - badgeWidth;
        CommandFrame.badge(g, left, y, badgeWidth, 13, accent);
        text(g, label, left + 6, y + 3, badgeWidth - 8, accent);
        return badgeWidth;
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

        // Personal purse on the right. It uses the same quiet chip language
        // as the rest of the console and a real emerald item sprite.
        // Leaves 26px of room on the far right for the close (X) button.
        String purse = String.format(Locale.ROOT, "%,d", menu.emeralds());
        int chipW = Math.max(layout.compact() ? 82 : 96,
                Math.min(w / 3, font.width(purse) + (layout.compact() ? 44 : 60)));
        int chipX = x + w - chipW - 30;
        CommandFrame.chip(g, chipX, y + 6, chipW, 24,
                CommandPalette.ACCENT_EMERALD);
        // Real vanilla emerald sprite so the currency readout always matches
        // the player's resource pack.
        ItemIcons.emerald(g, chipX + 7, y + 12, 14);
        // Top-right pill is an informational view of personal emeralds. Core
        // purchases still debit only the faction Treasury shown on its tab.
        text(g, layout.compact() ? "PURSE" : "YOUR PURSE",
                chipX + 26, y + 10, chipW - 32, CommandPalette.ACCENT_GOLD);
        text(g, purse, chipX + 26, y + 20, chipW - 32, CommandPalette.ACCENT_EMERALD);

        // Active-siege ribbon under the header.
        drawSiegeRibbon(g, x, y, w);

        // Tab body.
        drawPageHeader(g);

        if (tab == 0) {
            for (int i = 0; i < 4; i++) drawHire(g, i, mx, my);
        } else if (tab == 1) {
            for (int i = 0; i < 3; i++) {
                drawLoot(g, i, mx, my);
                drawBuff(g, i, mx, my);
            }
            drawLootReserve(g);
        } else if (tab == 2) {
            drawFaction(g);
        } else if (tab == 3) {
            drawTerritory(g, mx, my);
        } else if (tab == 4) {
            drawIntel(g, mx, my);
        }

        // Footer stats strip: faction size + contextual tab hint.
        drawFooterStrip(g, x, layout.footerY(), w);
    }

    /** Modern two-line page identity shared by every roomy tab. */
    private void drawPageHeader(GuiGraphics g) {
        if (layout.pageHeaderHeight() == 0) return;
        int x = layout.x() + 10;
        int y = layout.pageHeaderY();
        int w = layout.width() - 20;
        int accent = pageAccent();

        g.fill(x, y, x + w, y + CoreHireLayout.PAGE_HEADER_HEIGHT,
                0x8a0b111d);
        g.fill(x, y, x + 2, y + CoreHireLayout.PAGE_HEADER_HEIGHT, accent);
        g.fill(x + 2, y + CoreHireLayout.PAGE_HEADER_HEIGHT - 1,
                x + w, y + CoreHireLayout.PAGE_HEADER_HEIGHT,
                CommandPalette.DIVIDER);

        String title = PAGE_TITLES[Math.max(0, Math.min(tab, PAGE_TITLES.length - 1))];
        String subtitle = PAGE_SUBTITLES[Math.max(0, Math.min(tab, PAGE_SUBTITLES.length - 1))];
        int titleWidth = Math.min(w / 3, Math.max(90, font.width(title) + 8));
        text(g, title.toUpperCase(Locale.ROOT), x + 8, y + 2,
                titleWidth, accent);
        text(g, subtitle, x + titleWidth + 10, y + 2,
                Math.max(1, w - titleWidth - 170), CommandPalette.TEXT_MUTED);

        String metric = pageMetric();
        int metricWidth = Math.min(150, font.width(metric) + 4);
        text(g, metric, x + w - metricWidth - 7, y + 2,
                metricWidth, CommandPalette.TEXT);
        text(g, pageContext(), x + 8, y + 10,
                w - 16, CommandPalette.TEXT_DIM);
    }

    private int pageAccent() {
        return switch (tab) {
            case 1 -> CommandPalette.ACCENT_ARCANE;
            case 2 -> CommandPalette.ACCENT_EMERALD;
            case 3 -> CommandPalette.ACCENT_TEAL;
            case 4 -> CommandPalette.ACCENT_STEEL;
            default -> CommandPalette.ACCENT_GOLD;
        };
    }

    private String pageMetric() {
        return switch (tab) {
            case 0 -> String.format(Locale.ROOT, "Refresh %d:%02d",
                    menu.seconds() / 60, menu.seconds() % 60);
            case 1 -> String.format(Locale.ROOT, "Treasury %,de", menu.bank());
            case 2 -> String.format(Locale.ROOT, "Balance %,de", menu.bank());
            case 3 -> ownedTerritoryBuffs() + "/" + TerritoryBuffs.COUNT + " active";
            case 4 -> switch (intelSection) {
                case 0 -> "Unit archive";
                case 1 -> "Host archive";
                default -> "Field doctrine";
            };
            default -> "";
        };
    }

    private String pageContext() {
        return switch (tab) {
            case 0 -> "Four faction-wide offers · purchases deploy from the shared Treasury";
            case 1 -> "Rewards stay concealed until opened · purchases use the shared Treasury";
            case 2 -> "Every transaction is faction-wide and recorded in recent activity";
            case 3 -> "Permanent decrees affect every member · contracts dispatch equipped builders";
            case 4 -> "Scroll the archive or switch dossiers without leaving the command center";
            default -> "";
        };
    }

    private int ownedTerritoryBuffs() {
        int owned = 0;
        for (int i = 0; i < TerritoryBuffs.COUNT; i++) {
            if (menu.hasTerritoryBuff(i)) owned++;
        }
        return owned;
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
            case 1 -> "Loot & blessings draw from the faction Treasury";
            case 2 -> "Interest " + menu.interestRate() / 100.0 + "% per in-game day";
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
    private void drawIntel(GuiGraphics g, int mouseX, int mouseY) {
        int x = layout.x() + 10, y = layout.contentY();
        int w = layout.width() - 20;
        int h = layout.height() - (y - layout.y()) - 22;

        // Sub-tab strip
        String[] labels = {"Units", "Enemy Lore", "How to Play"};
        int segW = w / 3;
        for (int i = 0; i < 3; i++) {
            int sx = x + i * segW;
            boolean active = intelSection == i;
            int accent = active ? CommandPalette.ACCENT_STEEL : CommandPalette.TEXT_DIM;
            CommandFrame.card(g, sx, y, segW - 2, 18, accent,
                    over(mouseX, mouseY, sx, y, segW - 2, 18));
            if (active) {
                g.fill(sx + 4, y + 16, sx + segW - 6, y + 17,
                        CommandPalette.ACCENT_GOLD);
            }
            int labelW = font.width(labels[i]);
            text(g, labels[i], sx + (segW - labelW) / 2, y + 5, segW,
                    active ? CommandPalette.TEXT : CommandPalette.TEXT_MUTED);
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
            int innerW = Math.max(1, w - 16);
            int behaviorH = wrapped(entry.behavior(), innerW).size() * 10;
            int counterH = wrapped("Counter: " + entry.counter(), innerW).size() * 10;
            int dropsH = wrapped("Drops: " + entry.drops(), innerW).size() * 10;
            int cardH = 12 + 12 + 10 + 10 + behaviorH + counterH + dropsH + 10;
            CommandFrame.card(g, x, y, w, cardH, CommandPalette.ACCENT_STEEL);
            int tx = x + 8;
            int ty = y + 6;
            text(g, entry.name(), tx, ty, innerW, CommandPalette.ACCENT_GOLD); ty += 12;
            text(g, entry.tagline(), tx, ty, innerW, CommandPalette.TEXT); ty += 10;
            text(g, entry.stats(), tx, ty, innerW, CommandPalette.ACCENT_TEAL); ty += 10;
            ty += drawWrapped(g, entry.behavior(), tx, ty, innerW, CommandPalette.TEXT_MUTED);
            ty += drawWrapped(g, "Counter: " + entry.counter(), tx, ty, innerW, CommandPalette.TEXT);
            ty += drawWrapped(g, "Drops: " + entry.drops(), tx, ty, innerW, CommandPalette.ACCENT_EMERALD);
            text(g, entry.availability(), tx, ty, innerW, CommandPalette.TEXT_DIM);
            y += cardH + 6;
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
            int innerW = Math.max(1, w - 16);
            int bodyH = 0;
            for (String line : entry.getValue()) {
                bodyH += wrapped(line, innerW).size() * 10;
            }
            int cardH = 12 + 12 + bodyH;
            CommandFrame.card(g, x, y, w, cardH, CommandPalette.ACCENT_ARCANE);
            int tx = x + 8;
            int ty = y + 6;
            text(g, title.toString(), tx, ty, innerW, CommandPalette.ACCENT_GOLD);
            ty += 12;
            for (String line : entry.getValue()) {
                ty += drawWrapped(g, line, tx, ty, innerW, CommandPalette.TEXT_MUTED);
            }
            y += cardH + 6;
        }
        return y - startY;
    }

    private int drawHowToPlaySection(GuiGraphics g, int x, int startY, int w) {
        int y = startY;
        for (var tip : com.devfarinsky.siegeoverhaul.client.codex.DefensePlaybook.TIPS) {
            int innerW = Math.max(1, w - 16);
            int bodyH = wrapped(tip.body(), innerW).size() * 10;
            int cardH = 12 + 12 + bodyH;
            CommandFrame.card(g, x, y, w, cardH, CommandPalette.ACCENT_TEAL);
            int tx = x + 8;
            int ty = y + 6;
            text(g, tip.tag().toUpperCase(Locale.ROOT), tx, ty,
                    Math.min(70, innerW), CommandPalette.ACCENT_TEAL);
            text(g, tip.title(), tx + Math.min(76, innerW / 3), ty,
                    Math.max(1, innerW - Math.min(76, innerW / 3)),
                    CommandPalette.ACCENT_GOLD);
            ty += 12;
            drawWrapped(g, tip.body(), tx, ty, innerW, CommandPalette.TEXT_MUTED);
            y += cardH + 6;
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

    private void drawTerritory(GuiGraphics g, int mouseX, int mouseY) {
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
            else CommandFrame.card(g, cx, cy, cellW, cellH, accent,
                    over(mouseX, mouseY, cx, cy, cellW, cellH));

            // Readable heading replaces the former ambiguous flag glyph.
            if (!layout.compact()) {
                String stateLabel = owned ? "ACTIVE" : "DECREE";
                int stateWidth = drawBadge(g, stateLabel, cx + cellW - 7,
                        cy + 6, accent);
                text(g, TerritoryBuffs.LABELS[i], cx + 10, cy + 10,
                        Math.max(1, cellW - stateWidth - 24), accent);
            } else {
                text(g, TerritoryBuffs.LABELS[i], cx + 10, cy + 10,
                        cellW - 20, accent);
            }

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

        drawTerritoryOverview(g);
    }

    /** Uses the formerly empty Territory band as a compact kingdom summary. */
    private void drawTerritoryOverview(GuiGraphics g) {
        int available = layout.territoryFreeHeight();
        if (available < 18) return;
        int x = layout.x() + 10;
        int y = layout.territoryFreeTop();
        int w = layout.width() - 20;
        int h = available;
        int owned = ownedTerritoryBuffs();
        CommandFrame.card(g, x, y, w, h, CommandPalette.ACCENT_TEAL);
        text(g, "KINGDOM READINESS", x + 9, y + 6,
                Math.max(1, w / 3), CommandPalette.ACCENT_TEAL);
        String summary = owned + " of " + TerritoryBuffs.COUNT
                + " permanent decrees active";
        text(g, summary, x + w / 3, y + 6,
                Math.max(1, w - w / 3 - 9), CommandPalette.TEXT);
        if (h >= 30) {
            CommandFrame.progress(g, x + 9, y + 19, w - 18, 5,
                    owned / (float) TerritoryBuffs.COUNT,
                    CommandPalette.ACCENT_TEAL);
            text(g, owned == TerritoryBuffs.COUNT
                            ? "All kingdom decrees are active across the faction"
                            : "Permanent decrees improve every member of the faction",
                    x + 9, y + 29, w - 18, CommandPalette.TEXT_DIM);
        }
        if (h >= 56) {
            CommandFrame.divider(g, x + 9, y + 43, w - 18);
            text(g, "PERIMETER CONTRACTS", x + 9, y + 50,
                    Math.max(1, w / 3), CommandPalette.ACCENT_GOLD);
            text(g, "Choose a material below to dispatch an equipped Workers 2 builder",
                    x + w / 3, y + 50,
                    Math.max(1, w - w / 3 - 9), CommandPalette.TEXT);
        }
        if (h >= 72) {
            text(g, "Requires a Builder-enabled storage area stocked near the wall line",
                    x + 9, y + 64, w - 18, CommandPalette.TEXT_MUTED);
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

        boolean hovered = over(mouseX, mouseY, x, y, w, h);
        if (menu.sold(i)) CommandFrame.cardDimmed(g, x, y, w, h);
        else CommandFrame.card(g, x, y, w, h, accent, hovered);

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
        String badge = menu.sold(i) ? "HIRED"
                : i == 3 ? CoreHiring.rarity(role).toUpperCase(Locale.ROOT)
                : "AVAILABLE";
        int badgeColor = menu.sold(i) ? CommandPalette.TEXT_DIM : accent;
        int badgeWidth = drawBadge(g, badge, x + w - 7, y + 6, badgeColor);
        String name = CoreHiring.NAMES[role];
        text(g, name, infoLeft, y + 8,
                Math.max(1, infoWidth - badgeWidth - 5), CommandPalette.TEXT);
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

    private void drawLoot(GuiGraphics g, int i, int mouseX, int mouseY) {
        int x = layout.cardX(0), y = layout.marketY(i);
        int w = layout.cardWidth(), h = layout.marketHeight();
        CommandFrame.card(g, x, y, w, h, CommandPalette.ACCENT_GOLD,
                over(mouseX, mouseY, x, y, w, h));

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

        String lootState = opening ? "OPENING"
                : done ? CoreLoot.rarity(revealedTier).toUpperCase(Locale.ROOT)
                : "SEALED";
        int stateColor = opening ? CommandPalette.ACCENT_ARCANE
                : done ? CommandPalette.tier(revealedTier)
                : CommandPalette.ACCENT_GOLD;
        int badgeWidth = drawBadge(g, lootState, x + w - 6, y + 5, stateColor);
        text(g, CoreLoot.NAMES[i], textLeft, y + 6,
                Math.max(1, x + w - textLeft - badgeWidth - 10), CommandPalette.TEXT);
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

    /** Free band under the market becomes a readable reliquary protocol. */
    private void drawLootReserve(GuiGraphics g) {
        int h = layout.marketFreeHeight();
        if (h < 16) return;
        int x = layout.x() + 10;
        int w = layout.width() - 20;
        int y = layout.marketFreeTop();
        CommandFrame.card(g, x, y, w, h, CommandPalette.ACCENT_STEEL);
        int badgeWidth = 0;
        if (!layout.compact() && w >= 260) {
            badgeWidth = drawBadge(g, "REWARDS HIDDEN", x + w - 7, y + 5,
                    CommandPalette.ACCENT_ARCANE);
        }
        text(g, "RELIQUARY PROTOCOL", x + 9, y + 7,
                Math.max(1, w - badgeWidth - 24), CommandPalette.ACCENT_STEEL);
        if (h >= 30) {
            text(g, "Purchases deliver a sealed box; its item remains unknown until the reveal",
                    x + 9, y + 19, w - 18, CommandPalette.TEXT_MUTED);
        }
        if (h >= 48) {
            CommandFrame.divider(g, x + 9, y + 35, w - 18);
            text(g, "FIELD", x + 9, y + 42, w / 3 - 12, CommandPalette.TEXT);
            text(g, "VETERAN", x + w / 3, y + 42, w / 3 - 12, CommandPalette.ACCENT_EMERALD);
            text(g, "ROYAL", x + (w * 2) / 3, y + 42, w / 3 - 12, CommandPalette.ACCENT_GOLD);
        }
        if (h >= 62) {
            text(g, "Any rarity", x + 9, y + 53, w / 3 - 12, CommandPalette.TEXT_DIM);
            text(g, "Uncommon floor", x + w / 3, y + 53, w / 3 - 12, CommandPalette.TEXT_DIM);
            text(g, "Rare floor", x + (w * 2) / 3, y + 53, w / 3 - 12, CommandPalette.TEXT_DIM);
        }
        if (h >= 82) {
            CommandFrame.divider(g, x + 9, y + 72, w - 18);
            text(g, "Treasury funded  ·  server-authoritative roll  ·  no reward previews",
                    x + 9, y + 79, w - 18, CommandPalette.TEXT_DIM);
        }
    }

    private void drawBuff(GuiGraphics g, int i, int mouseX, int mouseY) {
        int x = layout.cardX(1), y = layout.marketY(i);
        int w = layout.cardWidth(), h = layout.marketHeight();
        CommandFrame.card(g, x, y, w, h, CommandPalette.ACCENT_TEAL,
                over(mouseX, mouseY, x, y, w, h));

        if (layout.compact()) {
            text(g, CoreBuffs.NAMES[i], x + 8, y + 5, w - 16, CommandPalette.TEXT);
            return;
        }
        boolean active = minecraft != null && minecraft.player != null
                && minecraft.player.hasEffect(CoreBuffs.effect(i));
        String state = active ? "ACTIVE" : "5 MIN";
        int stateColor = active ? CommandPalette.ACCENT_EMERALD : CommandPalette.ACCENT_TEAL;
        int badgeWidth = drawBadge(g, state, x + w - 6, y + 5, stateColor);
        text(g, CoreBuffs.NAMES[i], x + 8, y + 6,
                Math.max(1, w - badgeWidth - 20), CommandPalette.TEXT);
        text(g, CoreBuffs.DETAILS[i], x + 8, y + 17, w - 16, CommandPalette.TEXT_MUTED);

        ItemIcons.emerald(g, x + 8, y + 26, 10);
        text(g, CoreBuffs.PRICES[i] + "  |  personal blessing, 5 min",
                x + 20, y + 28, w - 26, CommandPalette.ACCENT_TEAL);
    }


    private void drawFaction(GuiGraphics g) {
        int x = layout.x();
        int w = layout.width();

        // Treasury overview: one compact summary on small screens, three
        // glanceable KPI cards on roomy screens.
        int bankCardH = 46;
        int bankY = layout.contentY();
        long dailyInterest = (long) menu.bank() * menu.interestRate() / 10000L;
        String interestCountdown = formatInterestCountdown(menu.ticksUntilInterest());
        if (layout.compact()) {
            CommandFrame.card(g, x + 10, bankY, w - 20, bankCardH,
                    CommandPalette.ACCENT_EMERALD);
            int leftX = x + 18;
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
            int gap = 6;
            int cardsW = w - 20;
            int metricW = (cardsW - gap * 2) / 3;
            drawMetricCard(g, x + 10, bankY, metricW, bankCardH,
                    "TREASURY", String.format(Locale.ROOT, "%,de", menu.bank()),
                    menu.factionName(), CommandPalette.ACCENT_EMERALD);
            drawMetricCard(g, x + 10 + metricW + gap, bankY, metricW, bankCardH,
                    "NEXT REWARD", String.format(Locale.ROOT, "+%,de", menu.nextReward()),
                    "Wave " + menu.nextWave(), CommandPalette.ACCENT_TEAL);
            drawMetricCard(g, x + 10 + (metricW + gap) * 2, bankY,
                    cardsW - (metricW + gap) * 2, bankCardH,
                    "DAILY INTEREST", String.format(Locale.ROOT, "+%,de", dailyInterest),
                    interestCountdown + "  ·  "
                            + String.format(Locale.ROOT, "%.2f%%", menu.interestRate() / 100.0),
                    CommandPalette.ACCENT_GOLD);
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
                int rowY = listTop + i * 12;
                if ((i & 1) == 0) {
                    g.fill(x + 16, rowY - 2, x + 16 + rosterListW,
                            rowY + 10, 0x261f2c42);
                }
                g.fill(x + 18, rowY + 2, x + 20, rowY + 7,
                        CommandPalette.ACCENT_EMERALD);
                text(g, menu.members().get(i + rosterOffset),
                        x + 24, rowY, rosterListW - 8, CommandPalette.TEXT);
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

    private void drawMetricCard(GuiGraphics g, int x, int y, int w, int h,
                                String label, String value, String detail, int accent) {
        CommandFrame.card(g, x, y, w, h, accent);
        text(g, label, x + 9, y + 6, w - 18, CommandPalette.TEXT_DIM);
        text(g, value, x + 9, y + 18, w - 18, accent);
        text(g, detail, x + 9, y + 30, w - 18, CommandPalette.TEXT_MUTED);
    }

    private int bankRosterY() {
        return layout.contentY() + 74;
    }

    private int bankRosterHeight() {
        return Math.max(34, layout.contentBottom() - bankRosterY());
    }

    /** v4.18.0 bank activity sparkline. Renders a zero-centered bar chart. */
    private void drawBankGraph(GuiGraphics g, int gx, int gy, int gw, int gh) {
        // Inset analytics panel inside the roster card.
        g.fill(gx, gy, gx + gw, gy + gh, CommandPalette.CHIP_BORDER);
        g.fillGradient(gx + 1, gy + 1, gx + gw - 1, gy + gh - 1,
                CommandPalette.HEADER_TOP, CommandPalette.CHIP_FILL);
        g.fill(gx + 1, gy + 1, gx + 3, gy + gh - 1,
                CommandPalette.ACCENT_EMERALD);
        text(g, "RECENT ACTIVITY", gx + 7, gy + 4, gw - 12,
                CommandPalette.ACCENT_GOLD);

        int[] ledger = menu.bankLedger();
        int plotTop = gy + 16;
        int plotBottom = gy + gh - 12;
        int plotH = plotBottom - plotTop;
        int zeroY = plotTop + plotH / 2;
        // Zero line.
        g.fill(gx + 5, zeroY, gx + gw - 5, zeroY + 1,
                CommandPalette.DIVIDER);

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
                g.fill(bx + 1, zeroY - barH, bx + barW - 1, zeroY,
                        CommandPalette.ACCENT_EMERALD);
                totalCredit += d;
            } else {
                g.fill(bx + 1, zeroY + 1, bx + barW - 1, zeroY + 1 + barH,
                        CommandPalette.ACCENT_BLOOD);
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
