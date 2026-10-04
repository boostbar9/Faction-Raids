package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.RaidEvents;
import com.devfarinsky.siegeoverhaul.RaidNetwork;
import com.devfarinsky.siegeoverhaul.client.codex.DefensePlaybook;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** The three reachable Codex pages. Live state and actions remain server-authoritative. */
@OnlyIn(Dist.CLIENT)
public final class SiegeCommandScreen extends Screen {
    enum Tab {
        OVERVIEW("Core"), DEFENSE("How to play"), JOURNAL("Journal");
        final String label;
        Tab(String label) { this.label = label; }
    }

    private RaidEvents.DashboardSnapshot snapshot;
    private SiegeCommandLayout layout;
    private final EnumMap<Tab, CoreButton> tabs = new EnumMap<>(Tab.class);
    private Tab activeTab = Tab.OVERVIEW;
    private int syncTicks, guideIndex, guideScroll, journalPage;
    private CoreButton previous, next;
    private GuideBody guide;

    public SiegeCommandScreen(RaidEvents.DashboardSnapshot snapshot) {
        super(Component.literal("Warlord's Codex"));
        this.snapshot = snapshot;
    }

    /** Refresh data without replacing focused widgets or losing the reader's place. */
    public void updateSnapshot(RaidEvents.DashboardSnapshot snapshot) {
        this.snapshot = snapshot;
        updateNavigation();
    }

    @Override public void tick() {
        if (++syncTicks >= 40) {
            syncTicks = 0;
            RaidNetwork.sendDashboardAction(RaidNetwork.Action.SYNC);
        }
    }

    @Override protected void init() {
        String focused = getFocused() instanceof AbstractWidget widget ? widget.getMessage().getString() : null;
        setFocused(null);
        clearWidgets();
        tabs.clear(); previous = null; next = null; guide = null;
        layout = SiegeCommandLayout.fit(width, height);
        for (Tab tab : Tab.values()) {
            var button = add(layout.tab(tab.ordinal()), tab.label, true, () -> selectTab(tab));
            tabs.put(tab, button);
        }
        add(layout.action(0), "Faction", false, NativeRecruitsMenus::factions);
        add(layout.action(1), "Claim map", false, NativeRecruitsMenus::claims);
        add(layout.action(2), "Sync", false, () -> RaidNetwork.sendDashboardAction(RaidNetwork.Action.SYNC));
        add(layout.action(3), "Close", false, this::onClose);
        if (activeTab == Tab.DEFENSE) {
            guide = addRenderableWidget(new GuideBody(layout.guideViewport()));
            previous = add(layout.previous(), "Previous", false, () -> changeGuide(-1));
            next = add(layout.next(), "Next tip", false, () -> changeGuide(1));
        } else if (activeTab == Tab.JOURNAL) {
            previous = add(layout.previous(), "Previous", false, () -> changeJournal(-1));
            next = add(layout.next(), "Next", false, () -> changeJournal(1));
        }
        updateNavigation();
        if (focused != null) for (var child : children()) {
            if (child instanceof AbstractWidget widget && widget.active && widget.getMessage().getString().equals(focused)) {
                setFocused(widget); break;
            }
        }
        if (getFocused() == null) setFocused(tabs.get(activeTab));
    }

    @Override public void setFocused(GuiEventListener focused) {
        // Vanilla assigns the clicked child after onPress. A tab callback may have rebuilt it.
        if (focused != null && (!children().contains(focused)
                || focused instanceof AbstractWidget widget && (!widget.visible || !widget.active))) return;
        var old = getFocused();
        if (old != null && old != focused) old.setFocused(false);
        super.setFocused(focused);
        if (focused != null) focused.setFocused(true);
    }

    private CoreButton add(SiegeCommandLayout.Rect bounds, String label, boolean tab, Runnable action) {
        return addRenderableWidget(new CoreButton(Component.literal(label), ignored -> action.run(),
                bounds.x(), bounds.y(), bounds.width(), bounds.height(), tab,
                () -> tab && activeTab.label.equals(label)));
    }

    private void selectTab(Tab tab) {
        activeTab = tab;
        init();
        setFocused(tabs.get(tab));
    }

    private void changeGuide(int amount) {
        int selected = Mth.clamp(guideIndex + amount, 0, DefensePlaybook.TIPS.size() - 1);
        if (selected != guideIndex) {
            guideIndex = selected; guideScroll = 0;
            if (guide != null) guide.refresh();
        }
        updateNavigation();
    }

    private void changeJournal(int amount) {
        journalPage = Mth.clamp(journalPage + amount, 0, journalPages() - 1);
        updateNavigation();
    }

    private int journalPages() { return layout == null ? 1 : layout.journalPages(snapshot.warJournal().size()); }

    private void updateNavigation() {
        if (layout == null) return;
        journalPage = Mth.clamp(journalPage, 0, journalPages() - 1);
        if (previous == null || next == null) return;
        int position = activeTab == Tab.DEFENSE ? guideIndex : journalPage;
        int count = activeTab == Tab.DEFENSE ? DefensePlaybook.TIPS.size() : journalPages();
        previous.active = position > 0;
        next.active = position + 1 < count;
        var focused = getFocused();
        if (focused != null && (!children().contains(focused)
                || focused instanceof AbstractWidget widget && (!widget.visible || !widget.active)))
            setFocused(guide != null ? guide : tabs.get(activeTab));
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        if (layout == null) return;
        var panel = layout.panel(); var body = layout.body();
        CommandFrame.window(g, panel.x(), panel.y(), panel.width(), panel.height());
        CommandFrame.header(g, panel.x(), panel.y(), panel.width(), 30);
        String status = occupied() ? "CORE OCCUPIED" : snapshot.active() ? "SIEGE ACTIVE" : snapshot.registered() ? "CORE READY" : "PLACE CORE";
        int statusWidth = Math.min(panel.width() / 2, font.width(status) + 12);
        int statusX = panel.right() - statusWidth - 8;
        CommandFrame.badge(g, statusX, panel.y() + 7, statusWidth, 15,
                occupied() || snapshot.active() ? CommandPalette.ACCENT_BLOOD : CommandPalette.ACCENT_TEAL);
        text(g, status, statusX + 6, panel.y() + 11, statusWidth - 12, CommandPalette.TEXT);
        text(g, "Warlord's Codex", panel.x() + 10, panel.y() + 9, statusX - panel.x() - 18, CommandPalette.ACCENT_GOLD);
        text(g, snapshot.faction(), panel.x() + 10, panel.y() + 23, panel.width() - 20, CommandPalette.TEXT_MUTED);
        switch (activeTab) {
            case OVERVIEW -> drawOverview(g, body);
            case DEFENSE -> {
                CommandFrame.surface(g, body.x(), body.y(), body.width(), body.height());
                text(g, DefensePlaybook.TIPS.get(guideIndex).tag().toUpperCase(java.util.Locale.ROOT),
                        body.x() + 10, body.y() + 8, body.width() - 20, CommandPalette.ACCENT_TEAL);
                pageNumber(g, (guideIndex + 1) + " / " + DefensePlaybook.TIPS.size());
            }
            case JOURNAL -> drawJournal(g, body);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private boolean occupied() {
        return snapshot.nextWaveLabel().startsWith("Recapture") || snapshot.cooldown().startsWith("Core occupied");
    }

    private void drawOverview(GuiGraphics g, SiegeCommandLayout.Rect body) {
        int x = body.x(), y = body.y(), w = body.width(), h = body.height();
        if (h < 100) {
            card(g, x, y, w, h, "SIEGE CORE", CommandPalette.ACCENT_STEEL);
            if (h >= 30) text(g, snapshot.registered() ? snapshot.stronghold() : "Place a core in your faction claim", x + 10, y + 20, w - 20, CommandPalette.TEXT);
            if (h >= 44) text(g, occupied() ? "Outnumber enemies at the core" : snapshot.cooldown(), x + 10, y + 33, w - 20, CommandPalette.TEXT_MUTED);
            if (h >= 58) text(g, snapshot.recruits() + " nearby recruits", x + 10, y + 46, w - 20, CommandPalette.TEXT_MUTED);
            return;
        }
        int coreHeight = Math.min(56, Math.max(34, h / 3));
        int statusHeight = Math.min(72, Math.max(40, (h - coreHeight - 8) / 2));
        card(g, x, y, w, coreHeight, "SIEGE CORE", CommandPalette.ACCENT_STEEL);
        text(g, snapshot.registered() ? snapshot.stronghold() : "Place a core in your faction claim",
                x + 10, y + 21, w - 20, CommandPalette.TEXT);
        if (coreHeight >= 42) text(g, snapshot.claimName().isBlank() ? "Use the claim map below" : snapshot.claimName(),
                x + 10, y + 32, w - 20, CommandPalette.TEXT_MUTED);
        int sy = y + coreHeight + 4;
        card(g, x, sy, w, statusHeight, occupied() ? "RECAPTURE" : snapshot.active() ? "DEFEND" : "PREPARE",
                occupied() ? CommandPalette.ACCENT_BLOOD : CommandPalette.ACCENT_GOLD);
        String status = snapshot.active() ? snapshot.cooldown() : occupied() ? "Outnumber enemies at the core"
                : snapshot.registered() ? "Next siege: " + snapshot.cooldown() : "Claim land, then place your core";
        text(g, status, x + 10, sy + 20, w - 20, CommandPalette.TEXT);
        if (snapshot.active() || occupied()) {
            CommandFrame.progress(g, x + 10, sy + 32, Math.max(1, w - 54), 5,
                    snapshot.occupationPercent() / 100F, occupied() ? CommandPalette.ACCENT_TEAL : CommandPalette.ACCENT_BLOOD);
            text(g, snapshot.occupationPercent() + "%", x + w - 38, sy + 30, 30, CommandPalette.TEXT_MUTED);
        } else if (statusHeight >= 42) text(g, "Hire troops and heroes at the core", x + 10, sy + 32, w - 20, CommandPalette.TEXT_MUTED);
        int fy = sy + statusHeight + 4, fieldHeight = Math.max(1, body.bottom() - fy);
        card(g, x, fy, w, fieldHeight, "FIELD REPORT", CommandPalette.ACCENT_TEAL);
        if (fieldHeight >= 30) text(g, snapshot.active() ? "Wave " + snapshot.wave() + "/" + snapshot.totalWaves() + " · " + snapshot.deployed() + " enemies"
                : snapshot.recruits() + " nearby recruits", x + 10, fy + 20, w - 20, CommandPalette.TEXT);
        if (fieldHeight >= 44) text(g, snapshot.active() && !snapshot.campDirection().isBlank()
                ? "Camp: " + snapshot.campDirection() + " · " + snapshot.campDistance() + "m" : "Keep a reserve beside your core",
                x + 10, fy + 33, w - 20, CommandPalette.TEXT_MUTED);
    }

    private void drawJournal(GuiGraphics g, SiegeCommandLayout.Rect body) {
        var rows = snapshot.warJournal();
        if (rows.isEmpty()) {
            card(g, body.x(), body.y(), body.width(), Math.max(24, body.height() - 26), "WAR JOURNAL", CommandPalette.ACCENT_GOLD);
            int yy = body.y() + 24;
            for (var line : font.split(Component.literal("No sieges recorded yet. Survive or fall to a siege and it will appear here, most recent first."), body.width() - 20)) {
                if (yy + font.lineHeight > body.bottom() - 28) break;
                g.drawString(font, line, body.x() + 10, yy, CommandPalette.TEXT_MUTED, false); yy += font.lineHeight + 2;
            }
        } else {
            int from = journalPage * layout.journalRows();
            for (int i = 0; i < layout.journalRows() && from + i < rows.size(); i++) {
                var row = rows.get(from + i); int y = body.y() + i * 44;
                int accent = switch (row.outcome()) {
                    case "victory" -> CommandPalette.ACCENT_EMERALD;
                    case "victory_practice" -> CommandPalette.ACCENT_GOLD;
                    case "defeat" -> CommandPalette.ACCENT_BLOOD;
                    default -> CommandPalette.TEXT_MUTED;
                };
                String outcome = switch (row.outcome()) {
                    case "victory" -> "VICTORY"; case "victory_practice" -> "PRACTICE WIN"; case "defeat" -> "DEFEAT";
                    default -> row.outcome().toUpperCase(java.util.Locale.ROOT);
                };
                card(g, body.x(), y, body.width(), 40, "", accent);
                String payout = row.emeraldPayout() > 0 ? "+" + row.emeraldPayout() + " emeralds" : "no reward";
                int payoutWidth = Math.min(body.width() / 2, font.width(payout));
                text(g, outcome, body.x() + 10, y + 8, body.width() - payoutWidth - 30, accent);
                text(g, payout, body.right() - 10 - payoutWidth, y + 8, payoutWidth, CommandPalette.ACCENT_GOLD);
                String faction = row.factionName() == null || row.factionName().isBlank() ? "Unknown faction" : row.factionName();
                text(g, faction + " · wave " + row.wavesReached() + "/" + row.totalWaves(), body.x() + 10, y + 24, body.width() - 20, CommandPalette.TEXT);
            }
        }
        pageNumber(g, (journalPage + 1) + " / " + journalPages());
    }

    private void pageNumber(GuiGraphics g, String label) {
        var previous = layout.previous(); var next = layout.next();
        int available = next.x() - previous.right() - 8;
        text(g, label, previous.right() + 4 + Math.max(0, (available - font.width(label)) / 2), previous.y() + 5,
                Math.max(1, available), CommandPalette.TEXT_MUTED);
    }

    private void card(GuiGraphics g, int x, int y, int w, int h, String label, int accent) {
        CommandFrame.card(g, x, y, w, h, accent);
        text(g, label, x + 10, y + 8, w - 20, accent);
    }

    private void text(GuiGraphics g, String value, int x, int y, int available, int color) {
        g.drawString(font, ellipsize(value, available), x, y, color, false);
    }

    private String ellipsize(String value, int available) {
        int width = Math.max(1, available);
        if (font.width(value) <= width) return value;
        String suffix = font.width("…") <= width ? "…" : "";
        return font.plainSubstrByWidth(value, Math.max(0, width - font.width(suffix))) + suffix;
    }

    @Override public boolean mouseScrolled(double x, double y, double amount) {
        if (guide != null && guide.isMouseOver(x, y)) return guide.mouseScrolled(x, y, amount);
        if (activeTab == Tab.JOURNAL && layout.body().contains(x, y) && amount != 0) {
            changeJournal(amount > 0 ? -1 : 1); return true;
        }
        return super.mouseScrolled(x, y, amount);
    }

    @Override public boolean isPauseScreen() { return false; }

    /** Focusable, narrated body keeps every source paragraph reachable at any supported GUI scale. */
    private final class GuideBody extends AbstractWidget {
        private final List<FormattedCharSequence> lines = new ArrayList<>();
        private int titleLines, maximum;
        private boolean dragging;

        GuideBody(SiegeCommandLayout.Rect bounds) {
            super(bounds.x(), bounds.y(), bounds.width(), bounds.height(), Component.empty());
            refresh();
        }
        void refresh() {
            var tip = DefensePlaybook.TIPS.get(guideIndex);
            setMessage(Component.literal(tip.title() + ". " + tip.body()));
            lines.clear(); lines.addAll(font.split(Component.literal(tip.title()), Math.max(1, width - 10)));
            titleLines = lines.size(); lines.add(FormattedCharSequence.EMPTY);
            lines.addAll(font.split(Component.literal(tip.body()), Math.max(1, width - 10)));
            maximum = Math.max(0, lines.size() * (font.lineHeight + 2) - height);
            guideScroll = Mth.clamp(guideScroll, 0, maximum);
        }
        private void scroll(int amount) { guideScroll = Mth.clamp(guideScroll + amount, 0, maximum); }
        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.enableScissor(getX(), getY(), getX() + width, getY() + height);
            for (int i = 0; i < lines.size(); i++) {
                int y = getY() + i * (font.lineHeight + 2) - guideScroll;
                if (y + font.lineHeight > getY() && y < getY() + height)
                    g.drawString(font, lines.get(i), getX(), y, i < titleLines ? CommandPalette.TEXT : CommandPalette.TEXT_MUTED, false);
            }
            g.disableScissor();
            if (maximum > 0) {
                int thumbHeight = Math.max(12, height * height / (height + maximum));
                int thumbY = getY() + (height - thumbHeight) * guideScroll / maximum;
                g.fill(getX() + width - 3, getY(), getX() + width, getY() + height, CommandPalette.DIVIDER);
                g.fill(getX() + width - 3, thumbY, getX() + width, thumbY + thumbHeight, CommandPalette.ACCENT_TEAL);
            }
            if (isFocused()) {
                g.fill(getX() - 2, getY(), getX() - 1, getY() + height, CommandPalette.ACCENT_TEAL);
                g.fill(getX() - 2, getY() + height, getX() + width, getY() + height + 1, CommandPalette.ACCENT_TEAL);
            }
        }
        @Override public boolean mouseScrolled(double x, double y, double amount) {
            if (!isMouseOver(x, y) || amount == 0) return false;
            scroll(amount > 0 ? -22 : 22); return true;
        }
        @Override public void onClick(double x, double y) {
            dragging = maximum > 0 && x >= getX() + width - 7;
            if (dragging) seek(y);
        }
        private void seek(double y) {
            int thumbHeight = Math.max(12, height * height / (height + maximum));
            guideScroll = Mth.clamp((int) Math.round((y - getY() - thumbHeight / 2.0) * maximum / Math.max(1, height - thumbHeight)), 0, maximum);
        }
        @Override protected void onDrag(double x, double y, double dx, double dy) { if (dragging) seek(y); }
        @Override public void onRelease(double x, double y) { dragging = false; }
        @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
            if (!isFocused()) return false;
            switch (key) {
                case GLFW.GLFW_KEY_UP -> scroll(-11);
                case GLFW.GLFW_KEY_DOWN -> scroll(11);
                case GLFW.GLFW_KEY_PAGE_UP -> scroll(-Math.max(11, height - 11));
                case GLFW.GLFW_KEY_PAGE_DOWN -> scroll(Math.max(11, height - 11));
                case GLFW.GLFW_KEY_HOME -> guideScroll = 0;
                case GLFW.GLFW_KEY_END -> guideScroll = maximum;
                default -> { return super.keyPressed(key, scanCode, modifiers); }
            }
            return true;
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, getMessage());
            output.add(NarratedElementType.USAGE, Component.literal("Use Up and Down, Page Up and Page Down, or the mouse wheel to read."));
        }
    }
}
