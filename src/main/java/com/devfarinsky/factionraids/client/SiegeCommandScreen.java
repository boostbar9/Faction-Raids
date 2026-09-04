package com.devfarinsky.factionraids.client;

import com.devfarinsky.factionraids.RaidEvents;
import com.devfarinsky.factionraids.RaidNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class SiegeCommandScreen extends Screen {
    private static final int PANEL_WIDTH = 384;
    private static final int PANEL_HEIGHT = 244;
    private static final int INK = 0xFFF2E9D2;
    private static final int MUTED = 0xFFB8AD98;
    private static final int GOLD = 0xFFE0B45B;
    private static final int RED = 0xFFD9534F;
    private static final int GREEN = 0xFF58B878;
    private static final int BLUE = 0xFF60A9C7;

    private RaidEvents.DashboardSnapshot snapshot;
    private int left;
    private int top;
    private int syncTicks;

    public SiegeCommandScreen(RaidEvents.DashboardSnapshot snapshot) {
        super(Component.literal("Faction Raids Command Table"));
        this.snapshot = snapshot;
    }

    public void updateSnapshot(RaidEvents.DashboardSnapshot snapshot) {
        this.snapshot = snapshot;
        clearWidgets();
        init();
    }

    @Override
    public void tick() {
        if (++syncTicks >= 40) {
            syncTicks = 0;
            RaidNetwork.sendDashboardAction(RaidNetwork.Action.SYNC);
        }
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        int buttonsY = top + PANEL_HEIGHT - 31;
        addRenderableWidget(new SiegeButton(left + 18, buttonsY, 105, 20,
                Component.literal("Refresh Home"), BLUE,
                () -> RaidNetwork.sendDashboardAction(RaidNetwork.Action.REFRESH_HOME)));
        SiegeButton start = new SiegeButton(left + 139, buttonsY, 106, 20,
                Component.literal(snapshot.active() ? "Siege Active" : "Test Siege"), RED,
                () -> RaidNetwork.sendDashboardAction(RaidNetwork.Action.START_PRACTICE));
        start.active = !snapshot.active();
        addRenderableWidget(start);
        addRenderableWidget(new SiegeButton(left + 261, buttonsY, 105, 20,
                Component.literal("Command Guide"), GOLD,
                () -> RaidNetwork.sendDashboardAction(RaidNetwork.Action.HELP)));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        drawPanel(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawPanel(GuiGraphics graphics) {
        graphics.fillGradient(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT,
                0xF21A1A20, 0xF20C0D11);
        border(graphics, left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFF6D5840);
        graphics.fillGradient(left + 1, top + 1, left + PANEL_WIDTH - 1, top + 45,
                0xFF5A1718, 0xFF291619);
        graphics.fill(left + 15, top + 13, left + 19, top + 34, RED);
        graphics.drawString(font, "FACTION WAR TABLE", left + 27, top + 12, GOLD, false);
        graphics.drawString(font, trim(snapshot.faction(), 245), left + 27, top + 26, INK, false);
        String readiness = snapshot.active() ? "SIEGE ACTIVE" : "STRONGHOLD SECURE";
        int readinessColor = snapshot.active() ? RED : GREEN;
        graphics.drawString(font, readiness, left + PANEL_WIDTH - 18 - font.width(readiness),
                top + 20, readinessColor, false);

        card(graphics, left + 14, top + 54, 226, 68, "STRONGHOLD", BLUE);
        graphics.drawString(font, trim(snapshot.stronghold(), 202), left + 25, top + 76, INK, false);
        graphics.drawString(font, snapshot.registered() ? "Target synchronized with your faction" :
                "Sleep at the base, then refresh", left + 25, top + 91,
                snapshot.registered() ? MUTED : RED, false);
        graphics.drawString(font, snapshot.active() ? "War camp deployed on the approach" :
                "Next siege: " + snapshot.cooldown(), left + 25, top + 106, MUTED, false);

        card(graphics, left + 250, top + 54, 120, 68, "DEFENDERS", GREEN);
        stat(graphics, left + 261, top + 77, "Recruits", snapshot.recruits(), INK);
        stat(graphics, left + 261, top + 91, "Workers", snapshot.workers(), MUTED);
        stat(graphics, left + 261, top + 105, "War assets", snapshot.ships() + snapshot.siegeWeapons(), MUTED);

        card(graphics, left + 14, top + 130, 356, 73, snapshot.active() ? "LIVE SIEGE" : "READINESS", RED);
        if (snapshot.active()) drawActiveSiege(graphics);
        else drawReadiness(graphics);
    }

    private void drawActiveSiege(GuiGraphics graphics) {
        graphics.drawString(font, "Wave " + snapshot.wave() + " / " + snapshot.totalWaves(),
                left + 25, top + 152, INK, false);
        graphics.drawString(font, snapshot.deployed() + " deployed  •  " + snapshot.reinforcing() +
                " reinforcing  •  " + snapshot.defeated() + " defeated", left + 111, top + 152, MUTED, false);

        int strategicProgress = snapshot.breached() ? snapshot.occupationPercent() : snapshot.breachPercent();
        String strategicLabel = snapshot.breached() ? "Stronghold occupation" : "Marked perimeter breach";
        progressBar(graphics, left + 25, top + 168, 211, strategicProgress,
                snapshot.breached() ? RED : GOLD);
        graphics.drawString(font, strategicLabel + "  " + strategicProgress + "%",
                left + 245, top + 167, MUTED, false);

        int gateColor = snapshot.gateBreachPercent() >= 75 ? RED : GOLD;
        progressBar(graphics, left + 25, top + 184, 211, snapshot.gateBreachPercent(), gateColor);
        graphics.drawString(font, trim("Gate: " + snapshot.gateTarget(), 109),
                left + 245, top + 183, MUTED, false);
        if (snapshot.breachedBlockCount() > 0) {
            graphics.drawString(font, snapshot.breachedBlockCount() + " block(s) queued for automatic repair",
                    left + 25, top + 196, GREEN, false);
        }
    }

    private void drawReadiness(GuiGraphics graphics) {
        graphics.drawString(font, "Nearby army strength", left + 25, top + 153, MUTED, false);
        graphics.drawString(font, snapshot.recruits() + " Recruits", left + 151, top + 153, INK, false);
        graphics.drawString(font, "Expected treasury", left + 25, top + 171, MUTED, false);
        graphics.drawString(font, snapshot.emeraldReward() + " emeralds per online member",
                left + 151, top + 171, GREEN, false);
        graphics.drawString(font, "Practice battles are " + (snapshot.rewardEligible() ? "reward eligible" : "training only"),
                left + 25, top + 189, snapshot.rewardEligible() ? GOLD : MUTED, false);
    }

    private void card(GuiGraphics graphics, int x, int y, int w, int h, String title, int accent) {
        graphics.fill(x, y, x + w, y + h, 0xD926272E);
        graphics.fill(x, y, x + 3, y + h, accent);
        border(graphics, x, y, w, h, 0xFF454149);
        graphics.drawString(font, title, x + 11, y + 9, accent, false);
    }

    private void stat(GuiGraphics graphics, int x, int y, String name, int value, int color) {
        graphics.drawString(font, name, x, y, MUTED, false);
        String number = Integer.toString(value);
        graphics.drawString(font, number, left + 357 - font.width(number), y, color, false);
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

    private static int darken(int color) {
        int r = ((color >> 16) & 0xFF) * 2 / 3;
        int g = ((color >> 8) & 0xFF) * 2 / 3;
        int b = (color & 0xFF) * 2 / 3;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class SiegeButton extends AbstractButton {
        private final int accent;
        private final Runnable action;

        private SiegeButton(int x, int y, int width, int height, Component message,
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
