package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.RaidNetwork;
import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import com.devfarinsky.siegeoverhaul.core.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** A responsive command desk: wide offer cards or compact rows at larger GUI scales. */
@Mod.EventBusSubscriber(modid = SiegeOverhaul.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CoreHireScreen extends AbstractContainerScreen<CoreHireMenu> {
    private CoreHireLayout layout;
    private boolean heroes;
    private long revealedAt;
    private int cardIndex(int i) { return i==3?(layout.compact()?0:1):i; }
    private final Button[] hire = new Button[4];
    private static final int GOLD = 0xffdfbf79, TEAL = 0xff75cabb, TEXT = 0xffeef0f4, MUTED = 0xffa0adbf;
    private static final String[] DETAILS = {"Reliable frontline infantry", "Defends with a shield", "Long-range bow support",
            "Heavy ranged support", "Tends your crop fields", "Harvests your timber", "Excavates your mines",
            "Builds assigned blueprints", "Prepares food at work", "Moves goods between stores"};
    public CoreHireScreen(CoreHireMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(CoreMenus.HIRING.get(), CoreHireScreen::new));
    }
    @Override protected void init() {
        layout = CoreHireLayout.fit(width, height);
        imageWidth = layout.width(); imageHeight = layout.height();
        super.init();
        revealedAt=net.minecraft.Util.getMillis();
        addRenderableWidget(Button.builder(Component.literal("Army & workers"),b -> { heroes=false; revealedAt=net.minecraft.Util.getMillis(); }).bounds(layout.x()+10,layout.y()+25,104,20).build());
        addRenderableWidget(Button.builder(Component.literal("Hire a Hero"),b -> { heroes=true; revealedAt=net.minecraft.Util.getMillis(); }).bounds(layout.x()+118,layout.y()+25,104,20).build());
        for (int i = 0; i < 4; i++) {
            final int index = i;
            int cw = layout.cardWidth(), ch = layout.cardHeight();
            int bw = layout.compact() ? 72 : cw - 24;
            int bx = layout.compact() ? layout.cardX(cardIndex(i)) + cw - bw - 8 : layout.cardX(cardIndex(i)) + 12;
            int by = layout.compact() ? layout.cardY(cardIndex(i)) + (ch - 20) / 2 : layout.cardY(cardIndex(i)) + ch - 32;
            hire[i] = addRenderableWidget(Button.builder(Component.literal("Hire"), b -> {
                RaidNetwork.purchaseCoreOffer(menu.containerId, index, menu.rotation());
            }).bounds(bx, by, bw, 20).build());
        }
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        for (int i = 0; i < 4; i++) {
            hire[i].visible = heroes==(i==3);
            hire[i].active = menu.role(i) >= 0 && menu.cost(i) >= 0 && !menu.sold(i) && menu.rotation() > 0;
            hire[i].setMessage(Component.literal(menu.sold(i) ? "Hired" : menu.cost(i) < 0 ? "Unavailable" : "Hire"));
        }
        super.render(g, mouseX, mouseY, partial);
        for (int i = 0; i < 4; i++) {
            if (heroes!=(i==3)) continue;
            int x = layout.cardX(cardIndex(i)), y = layout.cardY(cardIndex(i));
            if (mouseX >= x && mouseX < x + layout.cardWidth() && mouseY >= y && mouseY < y + layout.cardHeight()
                    && !hire[i].isMouseOver(mouseX, mouseY) && menu.role(i) >= 0) {
                int role = menu.role(i);
                String tip = CoreHiring.NAMES[role] + " • " + CoreHiring.rarity(role) + " • " + CoreHiring.weight(role) + "% per "
                        + (i==3?"hero":i == 2 ? "worker" : "recruit") + " slot";
                g.renderTooltip(font, Component.literal(tip), mouseX, mouseY);
            }
        }
    }
    @Override protected void renderLabels(GuiGraphics g, int x, int y) { }
    @Override protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        int x = layout.x(), y = layout.y(), w = layout.width(), h = layout.height();
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xff080d16);
        g.fillGradient(x, y, x + w, y + h, 0xff1c293a, 0xff101722);
        g.fill(x, y, x + w, y + 2, GOLD);
        g.drawString(font, "SIEGE CORE", x + 12, y + 12, GOLD, false);
        String clock = String.format(java.util.Locale.ROOT, "New offers %d:%02d", menu.seconds() / 60, menu.seconds() % 60);
        g.drawString(font, clock, x + w - font.width(clock) - 12, y + 12, TEAL, false);

        for (int i = 0; i < 4; i++) if(heroes==(i==3)) drawCard(g, i);
        String footer = heroes ? "One featured hero • Exact unit shown • No paid rerolls" : layout.compact() ? "Shared faction stock • 15-minute rotation" : "Two recruit offers + one worker offer  •  Shared faction stock  •  Refreshes every 15 minutes";
        g.drawString(font, font.plainSubstrByWidth(footer, w - 24), x + 12, y + h - 14, MUTED, false);
    }
    private void drawCard(GuiGraphics g, int i) {
        int x = layout.cardX(cardIndex(i)), y = layout.cardY(cardIndex(i)), w = layout.cardWidth(), h = layout.cardHeight();
        int accent = i==3 ? 0xffc19bff : i == 2 ? TEAL : GOLD;
        g.fillGradient(x,y,x+w,y+h,menu.sold(i)?0xff171e29:i==3?0xff453463:0xff273b54,0xff142033);
        g.fill(x,y,x+w,y+1,accent);
        long elapsed=net.minecraft.Util.getMillis()-revealedAt;
        if(elapsed<600) {
            int line=x+(int)(elapsed*w/600);
            g.fill(line,y,Math.min(x+w,line+2),y+h,0x4475cabb);
        }
        g.fill(x, y, x + 2, y + h, accent);
        int role = menu.role(i);
        if (role < 0 || role >= CoreHiring.NAMES.length) return;
        String name = CoreHiring.NAMES[role];
        String price = menu.sold(i) ? "Returns next rotation" : menu.cost(i) < 0 ? "Hiring unavailable"
                : menu.cost(i) + " " + menu.getSlot(4).getItem().getHoverName().getString();
        if (layout.compact()) {
            g.renderItem(menu.getSlot(i).getItem(), x + 8, y + (h - 16) / 2);
            g.drawString(font, font.plainSubstrByWidth((i==3?"Hero: ":i == 2 ? "Worker: " : "Recruit: ") + name, w - 120), x + 30, y + 7, TEXT, false);
            g.drawString(font, font.plainSubstrByWidth(price, w - 120), x + 30, y + 19, MUTED, false);
            if (h >= 45) g.drawString(font, CoreHiring.rarity(role) + " • " + CoreHiring.weight(role) + "%", x + 30, y + 31, accent, false);
        } else {
            g.drawString(font, i==3 ? "FEATURED HERO" : i == 2 ? "WORKFORCE" : "RECRUIT " + (i + 1), x + 12, y + 12, accent, false);
            g.fill(x + 12, y + 30, x + w - 12, y + 31, 0xff41506a);
            g.pose().pushPose();
            g.pose().translate(x + w / 2f - 16, y + 42, 0); g.pose().scale(2, 2, 1);
            g.renderItem(menu.getSlot(i).getItem(), 0, 0); g.pose().popPose();
            g.drawCenteredString(font, name, x + w / 2, y + 86, TEXT);
            g.drawCenteredString(font, CoreHiring.rarity(role) + " • " + CoreHiring.weight(role) + "%", x + w / 2, y + 101, accent);
            g.drawCenteredString(font, font.plainSubstrByWidth(i==3?"Level 10 • Diamond armor":DETAILS[role], w - 20), x + w / 2, y + 122, MUTED);
            g.drawCenteredString(font, font.plainSubstrByWidth(price, w - 20), x + w / 2, y + h - 49, TEXT);
        }
    }
}
