package com.devfarinsky.factionraids.client;

import com.devfarinsky.factionraids.RaidEvents;
import com.devfarinsky.factionraids.RaidNetwork;
import com.devfarinsky.factionraids.client.codex.DefensePlaybook;
import com.devfarinsky.factionraids.client.codex.FactionLore;
import com.devfarinsky.factionraids.client.codex.UnitCodex;
import com.devfarinsky.factionraids.narrative.RaiderFaction;
import com.devfarinsky.factionraids.narrative.RaiderFactionRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Warlord's Codex — a tabbed in-game reference book.
 *
 * <p>Design goals for v2.11.0:
 * <ul>
 *   <li>Every tab must be actionable during a live siege — no lore-only pages
 *       with no gameplay value.</li>
 *   <li>Left-rail tab navigation preserves the "book" feel while giving
 *       enough panel width to render tables and cards on the main pane.</li>
 *   <li>The Overview tab is the crisis dashboard: it opens by default and is
 *       laid out to be readable while a raid is happening on screen behind
 *       the panel.</li>
 *   <li>The Test Siege button was intentionally removed in 2.11.0. All players
 *       can trigger a siege manually with <code>/factionraids start</code>,
 *       so the button was redundant and confused new players about which
 *       action was "safe" versus "will pay out rewards."</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class SiegeCommandScreen extends Screen {

    // Panel is wider than the pre-2.11 dashboard because the Factions tab needs
    // room for a faction list column + faction detail pane side-by-side, and
    // the Units tab needs a bestiary list + stat block. 460x256 is close to
    // the maximum that fits comfortably on a 1080p window at default GUI scale.
    private static final int PANEL_WIDTH = 460;
    private static final int PANEL_HEIGHT = 256;
    private static final int TAB_RAIL_WIDTH = 96;
    private static final int HEADER_HEIGHT = 34;
    private static final int FOOTER_HEIGHT = 30;

    // Palette — kept identical to the pre-2.11 dashboard so nothing looks
    // out of place if a screenshot is compared before/after.
    private static final int INK = 0xFFF2E9D2;
    private static final int MUTED = 0xFFB8AD98;
    private static final int SUBTLE = 0xFF8A8172;
    private static final int GOLD = 0xFFE0B45B;
    private static final int RED = 0xFFD9534F;
    private static final int GREEN = 0xFF58B878;
    private static final int BLUE = 0xFF60A9C7;
    private static final int PANEL_TOP = 0xF21A1A20;
    private static final int PANEL_BOTTOM = 0xF20C0D11;
    private static final int RAIL_BG = 0xFF13141A;
    private static final int CARD_BG = 0xD926272E;
    private static final int CARD_BORDER = 0xFF454149;
    private static final int OUTER_BORDER = 0xFF6D5840;

    private enum Tab {
        OVERVIEW("Overview", GOLD),
        FACTIONS("Factions", BLUE),
        UNITS("Units", RED),
        DEFENSE("Defense", GREEN),
        JOURNAL("Journal", 0xFFD0A05C),
        COMMANDS("Commands", 0xFFB08CE0);

        final String label;
        final int accent;
        Tab(String label, int accent) { this.label = label; this.accent = accent; }
    }

    // Wave filter chip options for the Units tab. "all" is the default.
    private static final String[] WAVE_FILTERS = {"all", "1+", "2+", "3+", "final"};

    private RaidEvents.DashboardSnapshot snapshot;
    private int left;
    private int top;
    private int syncTicks;
    private Tab activeTab = Tab.OVERVIEW;
    // Sub-selection within Factions and Units tabs. Persist across re-init
    // (which happens on server sync) so the player doesn't lose their place.
    private int selectedFactionIndex = 0;
    private int selectedUnitIndex = 0;
    // Defense tab scroll offset in tip rows (2 tips per row).
    private int defenseScrollRows = 0;
    // Units tab wave filter index into WAVE_FILTERS.
    private int unitFilterIndex = 0;

    public SiegeCommandScreen(RaidEvents.DashboardSnapshot snapshot) {
        super(Component.literal("Warlord's Codex"));
        this.snapshot = snapshot;
    }

    /**
     * Called when a new DashboardSync arrives from the server. Rebuild widgets
     * because button labels/states depend on {@link RaidEvents.DashboardSnapshot#active()}.
     */
    public void updateSnapshot(RaidEvents.DashboardSnapshot snapshot) {
        this.snapshot = snapshot;
        clearWidgets();
        init();
        // If the currently open faction tab is highlighting the actual attacker,
        // auto-scroll to the attacker's index on the first snapshot that has it.
        // This is a nice-to-have; if nothing matches we leave selection alone.
        if (activeTab == Tab.FACTIONS && snapshot.factionId() != null && !snapshot.factionId().isEmpty()) {
            List<String> ids = List.copyOf(RaiderFactionRegistry.all().keySet());
            int idx = ids.indexOf(snapshot.factionId());
            if (idx >= 0) selectedFactionIndex = idx;
        }
    }

    @Override
    public void tick() {
        // Poll the server every 2 seconds so a siege in progress updates the
        // Overview card. 40 ticks matches the pre-2.11 cadence.
        if (++syncTicks >= 40) {
            syncTicks = 0;
            RaidNetwork.sendDashboardAction(RaidNetwork.Action.SYNC);
        }
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;

        // Tab rail buttons. Each tab is a full-width button in the left rail.
        int railX = left + 8;
        int railTop = top + HEADER_HEIGHT + 8;
        int tabHeight = 22;
        int tabSpacing = 4;
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            final Tab t = tabs[i];
            int y = railTop + i * (tabHeight + tabSpacing);
            addRenderableWidget(new TabButton(railX, y, TAB_RAIL_WIDTH - 16, tabHeight,
                    Component.literal(t.label), t, activeTab == t,
                    () -> { activeTab = t; clearWidgets(); init(); }));
        }

        // Footer actions. Refresh Home is universally useful; the Test Siege
        // button from the pre-2.11 layout is deliberately gone.
        int footerY = top + PANEL_HEIGHT - FOOTER_HEIGHT + 5;
        int contentX = left + TAB_RAIL_WIDTH;
        int contentW = PANEL_WIDTH - TAB_RAIL_WIDTH - 8;
        addRenderableWidget(new ActionButton(contentX + 8, footerY, (contentW - 24) / 2, 20,
                Component.literal("Refresh Home"), BLUE,
                () -> RaidNetwork.sendDashboardAction(RaidNetwork.Action.REFRESH_HOME)));
        addRenderableWidget(new ActionButton(contentX + 16 + (contentW - 24) / 2, footerY,
                (contentW - 24) / 2, 20,
                Component.literal("Sync Now"), GOLD,
                () -> RaidNetwork.sendDashboardAction(RaidNetwork.Action.SYNC)));

        // Faction / Unit sub-navigation for their respective tabs.
        if (activeTab == Tab.FACTIONS) {
            initFactionSubnav(contentX, contentW);
        } else if (activeTab == Tab.UNITS) {
            initUnitSubnav(contentX, contentW);
            initUnitFilterChips(contentX, contentW);
        }
    }

    /**
     * Renders the wave filter chip row above the Units tab detail pane so
     * players can slice the codex by which wave a unit first appears in.
     * The filter is a display-only convenience; it never hides units the
     * player has already discovered.
     */
    private void initUnitFilterChips(int contentX, int contentW) {
        int chipX = contentX + 148;
        int chipY = top + HEADER_HEIGHT + 10;
        int chipW = 44;
        int chipH = 14;
        int gap = 4;
        for (int i = 0; i < WAVE_FILTERS.length; i++) {
            final int idx = i;
            String label = "Wave " + WAVE_FILTERS[i];
            if (WAVE_FILTERS[i].equals("all")) label = "All";
            if (WAVE_FILTERS[i].equals("final")) label = "Final";
            addRenderableWidget(new TabButton(chipX + i * (chipW + gap), chipY, chipW, chipH,
                    Component.literal(label), null, unitFilterIndex == i,
                    () -> { unitFilterIndex = idx; clearWidgets(); init(); }, RED));
        }
    }

    private void initFactionSubnav(int contentX, int contentW) {
        // Vertical list of faction picker chips in the left half of the content.
        List<Map.Entry<String, RaiderFaction>> list = new ArrayList<>(RaiderFactionRegistry.all().entrySet());
        int listX = contentX + 8;
        int listY = top + HEADER_HEIGHT + 12;
        int chipH = 20;
        for (int i = 0; i < list.size(); i++) {
            final int idx = i;
            RaiderFaction f = list.get(i).getValue();
            int color = chatColorToArgb(f.accent(), BLUE);
            boolean isAttacker = f.id().equals(snapshot.factionId());
            String label = f.name() + (isAttacker ? "  \u2694" : "");
            addRenderableWidget(new TabButton(listX, listY + i * (chipH + 3), 150, chipH,
                    Component.literal(label), null, selectedFactionIndex == i,
                    () -> { selectedFactionIndex = idx; clearWidgets(); init(); }, color));
        }
    }

    private void initUnitSubnav(int contentX, int contentW) {
        // Vertical list of unit picker chips.
        int listX = contentX + 8;
        int listY = top + HEADER_HEIGHT + 12;
        int chipH = 16;
        List<UnitCodex.Entry> entries = UnitCodex.ENTRIES;
        for (int i = 0; i < entries.size(); i++) {
            final int idx = i;
            UnitCodex.Entry e = entries.get(i);
            addRenderableWidget(new TabButton(listX, listY + i * (chipH + 2), 130, chipH,
                    Component.literal(e.name()), null, selectedUnitIndex == i,
                    () -> { selectedUnitIndex = idx; clearWidgets(); init(); }, RED));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        drawPanel(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawPanel(GuiGraphics graphics) {
        // Panel chrome.
        graphics.fillGradient(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, PANEL_TOP, PANEL_BOTTOM);
        border(graphics, left, top, PANEL_WIDTH, PANEL_HEIGHT, OUTER_BORDER);

        // Header bar.
        graphics.fillGradient(left + 1, top + 1, left + PANEL_WIDTH - 1, top + HEADER_HEIGHT + 1,
                0xFF5A1718, 0xFF291619);
        graphics.fill(left + 12, top + 10, left + 16, top + HEADER_HEIGHT - 6, RED);
        graphics.drawString(font, "WARLORD'S CODEX", left + 22, top + 8, GOLD, false);
        graphics.drawString(font, trim(snapshot.faction(), 260), left + 22, top + 20, INK, false);
        String readiness = snapshot.active() ? "SIEGE ACTIVE" : "STRONGHOLD SECURE";
        int readinessColor = snapshot.active() ? RED : GREEN;
        graphics.drawString(font, readiness, left + PANEL_WIDTH - 12 - font.width(readiness),
                top + 14, readinessColor, false);

        // Tab rail background.
        graphics.fill(left + 1, top + HEADER_HEIGHT + 1, left + TAB_RAIL_WIDTH,
                top + PANEL_HEIGHT - 1, RAIL_BG);
        graphics.fill(left + TAB_RAIL_WIDTH - 1, top + HEADER_HEIGHT + 1, left + TAB_RAIL_WIDTH,
                top + PANEL_HEIGHT - 1, CARD_BORDER);

        // Content pane per tab.
        int contentX = left + TAB_RAIL_WIDTH + 4;
        int contentY = top + HEADER_HEIGHT + 8;
        int contentW = PANEL_WIDTH - TAB_RAIL_WIDTH - 12;
        int contentH = PANEL_HEIGHT - HEADER_HEIGHT - FOOTER_HEIGHT - 10;
        switch (activeTab) {
            case OVERVIEW -> drawOverview(graphics, contentX, contentY, contentW, contentH);
            case FACTIONS -> drawFactions(graphics, contentX, contentY, contentW, contentH);
            case UNITS -> drawUnits(graphics, contentX, contentY, contentW, contentH);
            case DEFENSE -> drawDefense(graphics, contentX, contentY, contentW, contentH);
            case JOURNAL -> drawJournal(graphics, contentX, contentY, contentW, contentH);
            case COMMANDS -> drawCommands(graphics, contentX, contentY, contentW, contentH);
        }
    }

    // ---------------------------------------------------------------------
    // OVERVIEW TAB — live siege dashboard
    // ---------------------------------------------------------------------

    private void drawOverview(GuiGraphics graphics, int x, int y, int w, int h) {
        // Top row: stronghold card + defenders card.
        int strongholdW = (w - 8) * 3 / 5;
        int defendersW = w - 8 - strongholdW;
        card(graphics, x, y, strongholdW, 62, "STRONGHOLD", BLUE);
        graphics.drawString(font, trim(snapshot.stronghold(), strongholdW - 20), x + 10, y + 22, INK, false);
        graphics.drawString(font, snapshot.registered() ? "Target synchronized" :
                "Sleep at the base, then refresh", x + 10, y + 34,
                snapshot.registered() ? MUTED : RED, false);
        String coolLabel = snapshot.active() ? "War camp deployed" : "Next siege: " + snapshot.cooldown();
        graphics.drawString(font, coolLabel, x + 10, y + 46, MUTED, false);
        // v2.28.0: claim-linked indicator when the defense point was picked
        // from a Recruits claim (synthetic "claim:" name), so players can tell
        // at a glance whether v2.27 claim-aware anchoring is in effect here.
        if (snapshot.claimLinked()) {
            String tag = snapshot.claimName().isEmpty()
                    ? "Recruits claim linked"
                    : "Claim: " + snapshot.claimName();
            graphics.drawString(font, trim(tag, strongholdW - 20),
                    x + 10, y + 56, GOLD, false);
        }

        card(graphics, x + strongholdW + 8, y, defendersW, 62, "DEFENDERS", GREEN);
        int dx = x + strongholdW + 18;
        stat(graphics, dx, y + 22, "Recruits", snapshot.recruits(), INK, x + strongholdW + defendersW + 8);
        stat(graphics, dx, y + 34, "Workers", snapshot.workers(), MUTED, x + strongholdW + defendersW + 8);
        stat(graphics, dx, y + 46, "War assets",
                snapshot.ships() + snapshot.siegeWeapons(), MUTED, x + strongholdW + defendersW + 8);

        // Middle row: casus belli quote (if narrative present) OR readiness card.
        int midY = y + 68;
        int midH = 62;
        if (snapshot.active() && !snapshot.factionOpening().isEmpty()) {
            card(graphics, x, midY, w, midH, "CASUS BELLI", GOLD);
            graphics.drawString(font, trim("\u201C" + snapshot.factionOpening() + "\u201D", w - 20),
                    x + 10, midY + 22, INK, false);
            if (!snapshot.factionChant().isEmpty()) {
                graphics.drawString(font, trim(snapshot.factionChant(), w - 20),
                        x + 10, midY + 36, MUTED, false);
            }
            if (!snapshot.campDirection().isEmpty()) {
                String camp = "War camp: " + snapshot.campDirection() + " \u2022 " + snapshot.campDistance() + "m";
                graphics.drawString(font, camp,
                        x + w - 12 - font.width(camp), midY + 48, GOLD, false);
            }
        } else {
            card(graphics, x, midY, w, midH, "READINESS", GOLD);
            graphics.drawString(font, "Emeralds per member:  " + snapshot.emeraldReward(),
                    x + 10, midY + 22, INK, false);
            graphics.drawString(font, "Next wave: " + snapshot.nextWaveLabel(),
                    x + 10, midY + 34, MUTED, false);
            if (!snapshot.nextWaveComposition().isEmpty()) {
                graphics.drawString(font, trim(snapshot.nextWaveComposition(), w - 20),
                        x + 10, midY + 46, SUBTLE, false);
            }
        }

        // Bottom row: live siege progress OR defense forecast.
        int botY = y + 68 + midH + 6;
        int botH = h - (botY - y);
        if (snapshot.active()) {
            card(graphics, x, botY, w, botH, "LIVE SIEGE", RED);
            graphics.drawString(font, "Wave " + snapshot.wave() + " / " + snapshot.totalWaves(),
                    x + 10, botY + 22, INK, false);
            graphics.drawString(font, snapshot.deployed() + " deployed  \u2022  " +
                            snapshot.reinforcing() + " incoming  \u2022  " + snapshot.defeated() + " defeated",
                    x + 96, botY + 22, MUTED, false);

            // v2.12.0 threat breakdown — the whole point of Know Your Enemy.
            // Shows exactly what unit types are on the field right now.
            if (!snapshot.threatBreakdown().isEmpty()) {
                graphics.drawString(font, "On field:", x + 10, botY + 34, MUTED, false);
                graphics.drawString(font, trim(snapshot.threatBreakdown(), w - 60),
                        x + 55, botY + 34, INK, false);
            }

            int strategicProgress = snapshot.breached() ? snapshot.occupationPercent() : snapshot.breachPercent();
            String strategicLabel = snapshot.breached() ? "Occupation" : "Perimeter";
            progressBar(graphics, x + 10, botY + 48, w - 100, strategicProgress,
                    snapshot.breached() ? RED : GOLD);
            graphics.drawString(font, strategicLabel + " " + strategicProgress + "%",
                    x + w - 90, botY + 47, MUTED, false);

            int gateColor = snapshot.gateBreachPercent() >= 75 ? RED : GOLD;
            progressBar(graphics, x + 10, botY + 60, w - 100, snapshot.gateBreachPercent(), gateColor);
            graphics.drawString(font, "Gate " + snapshot.gateBreachPercent() + "%",
                    x + w - 90, botY + 59, MUTED, false);

            // Defense score + explainer stack.
            int scoreColor = snapshot.defenseScore() >= 55 ? GREEN :
                    snapshot.defenseScore() >= 35 ? GOLD : RED;
            graphics.drawString(font, "Defense: " + snapshot.defenseScore() + " / 100 — " +
                            snapshot.defenseScoreLabel(),
                    x + 10, botY + 74, scoreColor, false);
            if (!snapshot.defenseExplainer().isEmpty()) {
                graphics.drawString(font, trim(snapshot.defenseExplainer(), w - 20),
                        x + 10, botY + 86, SUBTLE, false);
            }
        } else {
            card(graphics, x, botY, w, botH, "DEFENSE FORECAST", GREEN);
            graphics.drawString(font, "Nearby army:  " + snapshot.recruits() + " Recruits",
                    x + 10, botY + 22, INK, false);
            graphics.drawString(font, "Support:  " + snapshot.workers() + " Workers  \u2022  " +
                            snapshot.ships() + " ships  \u2022  " + snapshot.siegeWeapons() + " siege engines",
                    x + 10, botY + 34, MUTED, false);

            int scoreColor = snapshot.defenseScore() >= 55 ? GREEN :
                    snapshot.defenseScore() >= 35 ? GOLD : RED;
            graphics.drawString(font, "Estimated defense: " + snapshot.defenseScore() + " / 100 — " +
                            snapshot.defenseScoreLabel(),
                    x + 10, botY + 50, scoreColor, false);
            if (!snapshot.defenseExplainer().isEmpty()) {
                graphics.drawString(font, trim(snapshot.defenseExplainer(), w - 20),
                        x + 10, botY + 62, SUBTLE, false);
            }
            graphics.drawString(font, "Reward eligible: " + (snapshot.rewardEligible() ? "yes" : "no"),
                    x + 10, botY + 76, snapshot.rewardEligible() ? GOLD : MUTED, false);
        }
        // v2.28.0: compat strip — tells the player which optional-mod bridges
        // are actually loaded right now. Rendered at the bottom of Overview
        // so the lore/promise ("linked with Small Ships / Siege Weapons")
        // matches what the runtime can actually deliver on this world.
        int stripY = y + h - 10;
        String compat = "Compat: "
                + "Recruits Claims " + (snapshot.recruitsClaimsBridgeReady() ? "on" : "off") + "  \u2022  "
                + "Workers " + (snapshot.workersBridgeReady() ? "on" : "off") + "  \u2022  "
                + "Small Ships " + (snapshot.smallShipsBridgeReady() ? "on" : "off") + "  \u2022  "
                + "Siege Weapons " + (snapshot.siegeWeaponsBridgeReady() ? "on" : "off");
        graphics.drawString(font, trim(compat, w - 12), x + 6, stripY, SUBTLE, false);
    }

    // ---------------------------------------------------------------------
    // FACTIONS TAB — bestiary of raider factions
    // ---------------------------------------------------------------------

    private void drawFactions(GuiGraphics graphics, int x, int y, int w, int h) {
        // Left side: list of faction chips (rendered as widgets in initFactionSubnav)
        // Right side: detail pane.
        int detailX = x + 162;
        int detailW = w - 162;
        List<Map.Entry<String, RaiderFaction>> list = new ArrayList<>(RaiderFactionRegistry.all().entrySet());
        if (selectedFactionIndex >= list.size()) selectedFactionIndex = 0;
        RaiderFaction f = list.get(selectedFactionIndex).getValue();
        int accent = chatColorToArgb(f.accent(), BLUE);

        card(graphics, detailX, y, detailW, h, f.name().toUpperCase(), accent);
        graphics.drawString(font, "Epithet:  " + f.epithet(), detailX + 10, y + 22, INK, false);
        graphics.drawString(font, "Accent:  " + f.accent().getName(), detailX + 10, y + 34, MUTED, false);

        String tagLine = f.casusBelliTags().isEmpty() ? "Universal \u2014 any pretext" :
                String.join(", ", f.casusBelliTags());
        graphics.drawString(font, "Pretexts:", detailX + 10, y + 50, MUTED, false);
        graphics.drawString(font, trim(tagLine, detailW - 30), detailX + 10, y + 62, INK, false);

        // Faction lore — v2.12.0 pulls from client-side FactionLore registry
        // instead of a hardcoded switch, so datapack factions can register
        // matching entries at client mod init without touching this file.
        boolean discoveredFaction = isFactionDiscovered(f.id());
        if (discoveredFaction) {
            List<String> lore = FactionLore.get(f.id());
            int loreY = y + 82;
            for (String line : lore) {
                graphics.drawString(font, trim(line, detailW - 30), detailX + 10, loreY, MUTED, false);
                loreY += 12;
                if (loreY > y + h - 30) break;
            }
        } else {
            // Undiscovered — hide lore behind fog-of-war.
            graphics.drawString(font, "UNKNOWN FACTION", detailX + 10, y + 82, MUTED, false);
            graphics.drawString(font, "Survive a siege against them or kill", detailX + 10, y + 96, SUBTLE, false);
            graphics.drawString(font, "one of their raiders to reveal.", detailX + 10, y + 108, SUBTLE, false);
        }

        // Attacker indicator.
        if (f.id().equals(snapshot.factionId())) {
            graphics.drawString(font, "\u2694 CURRENTLY ATTACKING", detailX + 10, y + h - 20, RED, false);
            if (!snapshot.factionChant().isEmpty()) {
                graphics.drawString(font, trim("Chant: " + snapshot.factionChant(), detailW - 30),
                        detailX + 10, y + h - 8, GOLD, false);
            }
        }
    }

    private boolean isFactionDiscovered(String id) {
        // The active attacker is always considered discovered so players can
        // read up on who's currently at their gates.
        if (id.equals(snapshot.factionId())) return true;
        return snapshot.discoveredFactions().contains(id);
    }

    private boolean isUnitDiscovered(String id) {
        return snapshot.discoveredUnits().contains(id);
    }

    /**
     * Wave-filter predicate: whether {@code entry.availability()} matches the
     * currently selected wave filter chip. Availability strings from
     * {@link UnitCodex} look like "Wave 1+", "Wave 2+", "Final wave" — we do
     * a substring check because the strings are hand-authored.
     */
    private boolean matchesWaveFilter(UnitCodex.Entry entry) {
        String filter = WAVE_FILTERS[unitFilterIndex];
        if ("all".equals(filter)) return true;
        String a = entry.availability() == null ? "" : entry.availability().toLowerCase(java.util.Locale.ROOT);
        return switch (filter) {
            case "1+" -> a.contains("wave 1") || a.contains("all wave") || a.contains("any wave");
            case "2+" -> a.contains("wave 2") || a.contains("wave 3") || a.contains("final") || a.contains("all wave");
            case "3+" -> a.contains("wave 3") || a.contains("final") || a.contains("all wave");
            case "final" -> a.contains("final");
            default -> true;
        };
    }

    // ---------------------------------------------------------------------
    // UNITS TAB — bestiary of individual raider unit types
    // ---------------------------------------------------------------------

    private void drawUnits(GuiGraphics graphics, int x, int y, int w, int h) {
        int detailX = x + 142;
        int detailW = w - 142;
        if (selectedUnitIndex >= UnitCodex.ENTRIES.size()) selectedUnitIndex = 0;
        UnitCodex.Entry e = UnitCodex.ENTRIES.get(selectedUnitIndex);

        // Filter chip strip is added as widgets in initUnitFilterChips; leave
        // a small header row for it above the detail pane.
        int titleY = y + 20;
        boolean discovered = isUnitDiscovered(e.id());
        boolean matchesFilter = matchesWaveFilter(e);

        card(graphics, detailX, titleY, detailW, h - 20,
                discovered ? e.name().toUpperCase() : "???", RED);

        if (!discovered) {
            graphics.drawString(font, "Unknown unit type", detailX + 10, titleY + 22, MUTED, false);
            graphics.drawString(font, "Kill one to add it to your codex.", detailX + 10, titleY + 36, SUBTLE, false);
            graphics.drawString(font, "Appears: " + trim(e.availability(), detailW - 80),
                    detailX + 10, titleY + h - 40, MUTED, false);
            return;
        }

        // Discovered — render full page. Filter mismatch just gets a soft note
        // so the entry is never fully hidden after it's been earned.
        graphics.drawString(font, e.tagline(), detailX + 10, titleY + 22, INK, false);
        graphics.drawString(font, trim(e.stats(), detailW - 20), detailX + 10, titleY + 38, GOLD, false);

        int lineY = titleY + 56;
        lineY = drawLabelBody(graphics, detailX + 10, lineY, detailW - 20, "Behavior", e.behavior());
        lineY = drawLabelBody(graphics, detailX + 10, lineY + 4, detailW - 20, "Counter", e.counter());
        lineY = drawLabelBody(graphics, detailX + 10, lineY + 4, detailW - 20, "Drops", e.drops());
        graphics.drawString(font, "Appears: " + e.availability(),
                detailX + 10, y + h - 18, matchesFilter ? MUTED : SUBTLE, false);
    }

    private int drawLabelBody(GuiGraphics graphics, int x, int y, int w, String label, String body) {
        graphics.drawString(font, label + ":", x, y, MUTED, false);
        int consumed = y + 10;
        // Naive wrap: break body into ~w-width lines using the font.
        List<String> lines = wrap(body, w);
        for (String line : lines) {
            graphics.drawString(font, line, x, consumed, INK, false);
            consumed += 10;
        }
        return consumed;
    }

    // ---------------------------------------------------------------------
    // DEFENSE TAB — tips playbook
    // ---------------------------------------------------------------------

    private void drawDefense(GuiGraphics graphics, int x, int y, int w, int h) {
        // Two-column layout of tip cards, now with scrolling. Each card ~ half
        // width, three rows tall per page. Mouse wheel or arrow keys advance
        // {@code defenseScrollRows} to reveal more tips — v2.11.0 could only
        // show 6 of 10 tips and the rest were unreachable.
        int colGap = 6;
        int cardW = (w - colGap) / 2;
        int cardH = 54;
        int rowGap = 4;
        int rowsPerPage = 3;
        int tipsPerRow = 2;
        List<DefensePlaybook.Tip> tips = DefensePlaybook.TIPS;
        int totalRows = (tips.size() + tipsPerRow - 1) / tipsPerRow;
        int maxScroll = Math.max(0, totalRows - rowsPerPage);
        if (defenseScrollRows > maxScroll) defenseScrollRows = maxScroll;
        int startTip = defenseScrollRows * tipsPerRow;
        int endTip = Math.min(tips.size(), startTip + rowsPerPage * tipsPerRow);

        for (int i = startTip; i < endTip; i++) {
            DefensePlaybook.Tip t = tips.get(i);
            int rel = i - startTip;
            int col = rel % tipsPerRow;
            int row = rel / tipsPerRow;
            int cx = x + col * (cardW + colGap);
            int cy = y + row * (cardH + rowGap);
            card(graphics, cx, cy, cardW, cardH, t.tag().toUpperCase(), GREEN);
            graphics.drawString(font, trim(t.title(), cardW - 20), cx + 10, cy + 22, INK, false);
            List<String> bodyLines = wrap(t.body(), cardW - 20);
            int by = cy + 32;
            for (int b = 0; b < Math.min(bodyLines.size(), 2); b++) {
                graphics.drawString(font, bodyLines.get(b), cx + 10, by, MUTED, false);
                by += 10;
            }
        }

        // Scroll indicator + hint (only when scrolling is possible).
        if (maxScroll > 0) {
            String hint = "Tips " + (startTip + 1) + "\u2013" + endTip + " of " + tips.size() +
                    "  \u2022  scroll or \u2191/\u2193";
            graphics.drawString(font, hint, x + 4, y + h - 12, SUBTLE, false);
        }
    }

    // ---------------------------------------------------------------------
    // JOURNAL TAB — last N sieges (v2.12.0)
    // ---------------------------------------------------------------------

    /**
     * War Journal renders the newest-first list of siege outcomes for this
     * team. Each row is a compact card with faction, waves reached, outcome
     * tag, and payout. This is the durable memory of "what has happened to
     * us" and drives the discovery + fog-of-war reveal on Factions/Units.
     */
    private void drawJournal(GuiGraphics graphics, int x, int y, int w, int h) {
        List<RaidEvents.JournalRow> rows = snapshot.warJournal();
        if (rows.isEmpty()) {
            card(graphics, x, y, w, h, "WAR JOURNAL", 0xFFD0A05C);
            graphics.drawString(font, "No sieges recorded yet.", x + 10, y + 22, INK, false);
            graphics.drawString(font, "Survive (or fall to) a siege and it will", x + 10, y + 36, MUTED, false);
            graphics.drawString(font, "appear here \u2014 most recent first.", x + 10, y + 48, MUTED, false);
            return;
        }
        int rowH = 32;
        int gap = 3;
        int visible = Math.min(rows.size(), (h - 10) / (rowH + gap));
        for (int i = 0; i < visible; i++) {
            RaidEvents.JournalRow r = rows.get(i);
            int cy = y + i * (rowH + gap);
            int outcomeColor = switch (r.outcome()) {
                case "victory" -> GREEN;
                case "victory_practice" -> GOLD;
                case "defeat" -> RED;
                default -> MUTED;
            };
            String outcomeLabel = switch (r.outcome()) {
                case "victory" -> "VICTORY";
                case "victory_practice" -> "PRACTICE WIN";
                case "defeat" -> "DEFEAT";
                default -> r.outcome().toUpperCase(java.util.Locale.ROOT);
            };
            card(graphics, x, cy, w, rowH, outcomeLabel, outcomeColor);
            String title = (r.factionName() == null || r.factionName().isEmpty() ? "Unknown faction" : r.factionName())
                    + "  \u2022  wave " + r.wavesReached() + "/" + r.totalWaves();
            graphics.drawString(font, trim(title, w - 120), x + 10, cy + 20, INK, false);
            String right = r.emeraldPayout() > 0 ? ("+" + r.emeraldPayout() + " emeralds") : "no reward";
            graphics.drawString(font, right,
                    x + w - 10 - font.width(right), cy + 20,
                    r.emeraldPayout() > 0 ? GOLD : SUBTLE, false);
        }
        if (rows.size() > visible) {
            String note = "+" + (rows.size() - visible) + " older entries (kept up to 10 total)";
            graphics.drawString(font, note, x + 4, y + h - 12, SUBTLE, false);
        }
    }

    // ---------------------------------------------------------------------
    // COMMANDS TAB — /factionraids reference
    // ---------------------------------------------------------------------

    /**
     * Commands reference. v2.28.0 rewrite: entries are pulled from the
     * actual registered command tree in {@link com.devfarinsky.factionraids.command.RaidCommands}
     * instead of a hardcoded list \u2014 the pre-2.28 list invented
     * {@code /reset} and {@code /config reload} which never existed, and
     * omitted every anchor/territory/team command. Grouped so the tab
     * doesn't turn into a wall of text.
     */
    private void drawCommands(GuiGraphics graphics, int x, int y, int w, int h) {
        card(graphics, x, y, w, h, "COMMAND REFERENCE", 0xFFB08CE0);
        String[][] cmds = new String[][]{
                // -- Play --
                {"/factionraids menu", "Open this Codex."},
                {"/factionraids start", "Trigger your stronghold's next siege now."},
                {"/factionraids status", "Print live siege status in chat."},
                {"/factionraids help", "List every subcommand in chat."},
                // -- Anchor --
                {"/factionraids anchor set <team>", "Set your anchor at your current position."},
                {"/factionraids anchor claim", "Claim ownership of the anchor at your position."},
                {"/factionraids anchor remove", "Remove the anchor at your position."},
                {"/factionraids home automatic <bool>", "Toggle auto-detecting bed/anchor as objective."},
                {"/factionraids home refresh", "Re-scan your bed/anchor as the stronghold objective."},
                // -- Team --
                {"/factionraids member add|remove|list", "Manage your faction roster."},
                {"/factionraids territory add|remove|list", "Track additional bases as defense points."},
                // -- Admin --
                {"/factionraids stop", "Cancel the active siege (ops only)."},
                {"/factionraids admin list|stop|remove|repair", "Server-wide raid administration (ops only)."},
                {"/factionraids debug", "Verbose diagnostic dump (ops only)."},
        };
        int lineY = y + 22;
        for (String[] pair : cmds) {
            graphics.drawString(font, pair[0], x + 10, lineY, GOLD, false);
            List<String> desc = wrap(pair[1], w - 30);
            int dy = lineY + 10;
            for (String line : desc) {
                graphics.drawString(font, line, x + 20, dy, MUTED, false);
                dy += 10;
            }
            lineY = dy + 3;
            if (lineY > y + h - 16) break;
        }
        graphics.drawString(font, "All commands verified against the command tree on load.",
                x + 10, y + h - 12, SUBTLE, false);
    }

    // ---------------------------------------------------------------------
    // Rendering helpers
    // ---------------------------------------------------------------------

    private void card(GuiGraphics graphics, int x, int y, int w, int h, String title, int accent) {
        graphics.fill(x, y, x + w, y + h, CARD_BG);
        graphics.fill(x, y, x + 3, y + h, accent);
        border(graphics, x, y, w, h, CARD_BORDER);
        graphics.drawString(font, title, x + 10, y + 8, accent, false);
    }

    private void stat(GuiGraphics graphics, int x, int y, String name, int value, int color, int rightEdge) {
        graphics.drawString(font, name, x, y, MUTED, false);
        String number = Integer.toString(value);
        graphics.drawString(font, number, rightEdge - 10 - font.width(number), y, color, false);
    }

    private void progressBar(GuiGraphics graphics, int x, int y, int width, int percent, int color) {
        graphics.fill(x, y, x + width, y + 7, 0xFF111217);
        int fill = Mth.clamp(percent, 0, 100) * width / 100;
        if (fill > 0) graphics.fillGradient(x, y, x + fill, y + 7, darken(color), color);
        border(graphics, x, y, width, 7, 0xFF514A43);
    }

    private void border(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    private String trim(String text, int maxWidth) {
        return font.plainSubstrByWidth(text, maxWidth);
    }

    /**
     * Naive word-wrap. Splits on spaces and greedily packs words up to
     * {@code maxWidth}. Adequate for the short tip and command strings used
     * in this book; not intended for long paragraphs.
     */
    private List<String> wrap(String text, int maxWidth) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) { out.add(""); return out; }
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (font.width(candidate) > maxWidth && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    private static int darken(int color) {
        int r = ((color >> 16) & 0xFF) * 2 / 3;
        int g = ((color >> 8) & 0xFF) * 2 / 3;
        int b = (color & 0xFF) * 2 / 3;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int chatColorToArgb(ChatFormatting fmt, int fallback) {
        if (fmt == null || fmt.getColor() == null) return fallback;
        return 0xFF000000 | fmt.getColor();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Mouse wheel scroll for the Defense tab. Ignored on other tabs so the
     * scroll wheel keeps behaving like a no-op there rather than surprising
     * the player.
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (activeTab == Tab.DEFENSE) {
            if (delta > 0 && defenseScrollRows > 0) {
                defenseScrollRows--;
                return true;
            } else if (delta < 0) {
                defenseScrollRows++;
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (activeTab == Tab.DEFENSE) {
            // 265 = up, 264 = down (GLFW).
            if (keyCode == 265 && defenseScrollRows > 0) {
                defenseScrollRows--;
                return true;
            } else if (keyCode == 264) {
                defenseScrollRows++;
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---------------------------------------------------------------------
    // Widgets
    // ---------------------------------------------------------------------

    /**
     * Left-rail tab button. When {@code accentOverride > 0} the accent bar
     * uses that color; used by the faction sub-nav to color-tag each faction
     * chip with its faction accent.
     */
    private static final class TabButton extends AbstractButton {
        private final Tab tab;
        private final boolean selected;
        private final Runnable action;
        private final int accentOverride;

        private TabButton(int x, int y, int width, int height, Component message, Tab tab,
                          boolean selected, Runnable action) {
            this(x, y, width, height, message, tab, selected, action, 0);
        }

        private TabButton(int x, int y, int width, int height, Component message, Tab tab,
                          boolean selected, Runnable action, int accentOverride) {
            super(x, y, width, height, message);
            this.tab = tab;
            this.selected = selected;
            this.action = action;
            this.accentOverride = accentOverride;
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int accent = accentOverride != 0 ? accentOverride : (tab != null ? tab.accent : GOLD);
            int background = selected ? 0xFF302E34 :
                    isHoveredOrFocused() ? 0xFF25252A : 0xFF1B1B21;
            graphics.fill(getX(), getY(), getX() + width, getY() + height, background);
            graphics.fill(getX(), getY(), getX() + 3, getY() + height,
                    selected ? accent : 0xFF3A3A40);
            int textColor = selected ? INK : MUTED;
            graphics.drawString(Minecraft.getInstance().font, getMessage(),
                    getX() + 8, getY() + (height - 8) / 2, textColor, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    /** Footer action button. Same visual language as the pre-2.11 SiegeButton. */
    private static final class ActionButton extends AbstractButton {
        private final int accent;
        private final Runnable action;

        private ActionButton(int x, int y, int width, int height, Component message,
                             int accent, Runnable action) {
            super(x, y, width, height, message);
            this.accent = accent;
            this.action = action;
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int background = !active ? 0xFF25252A : isHoveredOrFocused() ? 0xFF46414A : 0xFF302E34;
            graphics.fill(getX(), getY(), getX() + width, getY() + height, background);
            graphics.fill(getX(), getY(), getX() + 3, getY() + height, active ? accent : 0xFF55555A);
            int textColor = active ? INK : 0xFF77777B;
            graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(),
                    getX() + width / 2 + 1, getY() + (height - 8) / 2, textColor);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
