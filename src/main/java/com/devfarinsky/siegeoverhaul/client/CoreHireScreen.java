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
    private boolean heroes, loot;
    private int confirmBox=-1;
    private int seenLoot, revealBox=-1, revealTicks, waitingTicks;
    private net.minecraft.world.item.ItemStack revealed=net.minecraft.world.item.ItemStack.EMPTY;
    private int revealedTier;
    private static final int REVEAL_DURATION=CoreLoot.OPEN_TICKS;
    private void chime(float pitch) {
        if(minecraft!=null)minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,pitch));
    }
    @Override protected void containerTick() {
        super.containerTick();
        if(waitingTicks>0)waitingTicks--;
        if(menu.lootSequence()!=seenLoot) {
            seenLoot=menu.lootSequence();revealBox=menu.lootBox();revealed=menu.lootReward().copy();
            revealedTier=menu.lootTier();revealTicks=REVEAL_DURATION;waitingTicks=0;
        }
        if(revealTicks>0) {
            revealTicks--;
            if(loot && (revealTicks==0 || revealTicks% (revealTicks>20?5:10)==0))
                chime(revealTicks==0?1.2f:0.6f+(REVEAL_DURATION-revealTicks)*.01f);
        }
    }
    private final Button[] boxes=new Button[3];
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
        addRenderableWidget(new CoreButton(Component.literal("Army"),b -> { heroes=false; loot=false; confirmBox=-1; },layout.x()+10,layout.y()+32,(layout.width()-28)/3,24,true,()->!heroes&&!loot));
        addRenderableWidget(new CoreButton(Component.literal("Heroes"),b -> { heroes=true; loot=false; confirmBox=-1; },layout.x()+14+(layout.width()-28)/3,layout.y()+32,(layout.width()-28)/3,24,true,()->heroes));
        addRenderableWidget(new CoreButton(Component.literal("Loot boxes"),b -> {loot=true;heroes=false;confirmBox=-1;},layout.x()+18+2*((layout.width()-28)/3),layout.y()+32,(layout.width()-28)/3,24,true,()->loot));
        for(int i=0;i<3;i++) {
            final int box=i;int cw=layout.cardWidth(),ch=layout.cardHeight();int bw=layout.compact()?72:cw-24;
            int bx=layout.compact()?layout.cardX(i)+cw-bw-8:layout.cardX(i)+12;
            int by=layout.compact()?layout.cardY(i)+(ch-20)/2:layout.cardY(i)+ch-32;
            boxes[i]=addRenderableWidget(new CoreButton(Component.literal("Open"),b -> {
                if(confirmBox!=box){confirmBox=box;return;}
                if(waitingTicks>0 || revealTicks>0)return;
                if(minecraft!=null && minecraft.gameMode!=null) {
                    waitingTicks=60;
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId,20+box);
                }
                confirmBox=-1;
            },bx,by,bw,20,false,()->confirmBox==box));
        }
        for (int i = 0; i < 4; i++) {
            final int index = i;
            int cw = layout.cardWidth(), ch = layout.cardHeight();
            int bw = layout.compact() ? 72 : cw - 24;
            int bx = layout.compact() ? layout.cardX(cardIndex(i)) + cw - bw - 8 : layout.cardX(cardIndex(i)) + 12;
            int by = layout.compact() ? layout.cardY(cardIndex(i)) + (ch - 20) / 2 : layout.cardY(cardIndex(i)) + ch - 32;
            hire[i] = addRenderableWidget(new CoreButton(Component.literal("Hire"), b -> {
                RaidNetwork.purchaseCoreOffer(menu.containerId, index, menu.rotation());
            },bx,by,bw,20,false,()->false));
        }
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        for (int i = 0; i < 4; i++) {
            hire[i].visible = !loot && heroes==(i==3);
            hire[i].active = menu.role(i) >= 0 && menu.cost(i) >= 0 && !menu.sold(i) && menu.rotation() > 0;
            hire[i].setMessage(Component.literal(menu.sold(i) ? "Recruited" : menu.cost(i) < 0 ? "Unavailable" : "Recruit"));
        }
        for(int i=0;i<3;i++){
            boxes[i].visible=loot;
            boxes[i].active=waitingTicks==0 && revealTicks==0 && menu.emeralds()>=CoreLoot.price(i);
            boxes[i].setMessage(Component.literal(waitingTicks>0?"Waiting...":revealTicks>0?"Opening...":confirmBox==i?"Confirm":"Open"));
        }
        super.render(g, mouseX, mouseY, partial);
        if(loot) {
            for(int i=0;i<3;i++)if(!boxes[i].isMouseOver(mouseX,mouseY) && mouseX>=layout.cardX(i) && mouseX<layout.cardX(i)+layout.cardWidth() && mouseY>=layout.cardY(i) && mouseY<layout.cardY(i)+layout.cardHeight())
                g.renderTooltip(font,font.split(Component.literal(CoreLoot.price(i)+" emeralds | One mystery reward. "+CoreLoot.odds()),Math.min(300,width-24)),mouseX,mouseY);
            return;
        }
        for (int i = 0; i < 4; i++) {
            if (heroes!=(i==3)) continue;
            int x = layout.cardX(cardIndex(i)), y = layout.cardY(cardIndex(i));
            if (mouseX >= x && mouseX < x + layout.cardWidth() && mouseY >= y && mouseY < y + layout.cardHeight()
                    && !hire[i].isMouseOver(mouseX, mouseY) && menu.role(i) >= 0) {
                int role = menu.role(i);
                String tip = CoreHiring.NAMES[role] + " • " + CoreHiring.rarity(role) + " • " + CoreHiring.weight(role) + "% per "
                        + (i==3?"hero":i == 2 ? "worker" : "recruit") + " slot";
                if(i==3) tip += " • Level 10 • 60+ health • " + com.devfarinsky.siegeoverhaul.core.HeroTraits.description(role);
                g.renderTooltip(font, font.split(Component.literal(tip),Math.min(300,width-24)), mouseX, mouseY);
            }
        }
    }
    @Override protected void renderLabels(GuiGraphics g, int x, int y) { }
    @Override protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        int x = layout.x(), y = layout.y(), w = layout.width(), h = layout.height();
        CoreButton.panel(g,x-5,y+4,w+10,h+5,0x66000000);
        CoreButton.panel(g,x-1,y-1,w+2,h+2,0xff405469);
        CoreButton.panel(g,x,y,w,h,0xff111c29);
        g.fill(x+12,y+27,x+w-12,y+28,0xff293a4a);
        g.drawString(font,layout.compact()?"SIEGE CORE":"SIEGE  /  COMMAND",x+12,y+12,TEXT,false);
        String clock=String.format(java.util.Locale.ROOT,"%d:%02d",menu.seconds()/60,menu.seconds()%60);
        int emeralds=menu.emeralds();
        String status=loot?emeralds+" emeralds":clock+"  |  "+emeralds+" emeralds";
        g.drawString(font,font.plainSubstrByWidth(status,Math.max(1,w/2-12)),x+w-font.width(font.plainSubstrByWidth(status,Math.max(1,w/2-12)))-12,y+12,TEAL,false);
        if(!loot) {
            g.fill(x+12,y+h-23,x+w-12,y+h-21,0xff263949);
            int progress=(int)((w-24)*Math.max(0,Math.min(900,menu.seconds()))/900.0);
            g.fill(x+12,y+h-23,x+12+progress,y+h-21,TEAL);
        }

        if(loot)for(int i=0;i<3;i++)drawBox(g,i);
        else for (int i = 0; i < 4; i++) if(heroes==(i==3)) drawCard(g, i);
        if(heroes && layout.compact() && menu.role(3)>=10) {
            int ty=layout.cardY(1)+6;
            for(var line:font.split(Component.literal(HeroTraits.description(menu.role(3))),w-28)) {
                if(ty>y+h-30)break;
                g.drawString(font,line,x+14,ty,TEXT,false);ty+=11;
            }
        }
        String footer = loot ? "Mystery rewards | Hover for rarity odds | Confirm one purchase" : heroes ? "One featured hero • Exact unit shown • No paid rerolls" : layout.compact() ? "Shared faction stock • 15-minute rotation" : "Two recruit offers + one worker offer  •  Shared faction stock  •  Refreshes every 15 minutes";
        g.drawString(font, font.plainSubstrByWidth(footer, w - 24), x + 12, y + h - 14, MUTED, false);
    }
    private void drawBox(GuiGraphics g,int i) {
        int x=layout.cardX(i),y=layout.cardY(i),w=layout.cardWidth(),h=layout.cardHeight();
        CoreButton.panel(g,x,y,w,h,0xff40516a);CoreButton.panel(g,x+1,y+1,w-2,h-2,0xff202b3c);
        if(layout.compact()) {
            g.drawString(font,font.plainSubstrByWidth(CoreLoot.NAMES[i],w-96),x+10,y+8,TEXT,false);
            if(h>=34) {
                String line=revealBox==i ? revealTicks>0?"Unsealing...":revealed.getCount()+"x "+revealed.getHoverName().getString():CoreLoot.price(i)+" emeralds | Mystery";
                g.drawString(font,font.plainSubstrByWidth(line,w-96),x+10,y+22,GOLD,false);
            }
        } else {
            g.drawCenteredString(font,CoreLoot.NAMES[i],x+w/2,y+16,GOLD);
            boolean opening=revealBox==i && revealTicks>0;
            boolean done=revealBox==i && revealTicks==0 && !revealed.isEmpty();
            for(int r=0;r<3;r++) {
                int ix=x+w/2-35+r*26;
                CoreButton.panel(g,ix-2,y+34,24,28,opening?0xff534467:0xff132030);
                String symbol=opening?new String[]{"*","+","?","#"}[(revealTicks/4+r)%4]:"?";
                g.drawCenteredString(font,symbol,ix+10,y+44,opening?GOLD:TEAL);
            }
            if(done) {
                CoreButton.panel(g,x+w/2-13,y+33,26,30,0xff263949);
                g.renderItem(revealed,x+w/2-8,y+39);
            }
            g.drawCenteredString(font,CoreLoot.price(i)+" emeralds",x+w/2,y+70,TEXT);
            String message=opening?"Unsealing...":done?CoreLoot.rarity(revealedTier)+" - "+revealed.getCount()+"x "+revealed.getHoverName().getString():"Sealed mystery reward";
            int ty=y+90;
            for(var line:font.split(Component.literal(message),w-24)) {if(ty>y+h-50)break;g.drawString(font,line,x+12,ty,done?GOLD:MUTED,false);ty+=11;}

        }
    }
    private void drawCard(GuiGraphics g, int i) {
        int x = layout.cardX(cardIndex(i)), y = layout.cardY(cardIndex(i)), w = layout.cardWidth(), h = layout.cardHeight();
        int accent = i==3 ? 0xffc19bff : i == 2 ? TEAL : GOLD;
        CoreButton.panel(g,x,y,w,h,menu.sold(i)?0xff283342:accent);
        CoreButton.panel(g,x+1,y+1,w-2,h-2,menu.sold(i)?0xff16202c:i==3?0xff292438:0xff1c2b3b);
        g.fill(x+8,y+1,x+w-8,y+2,accent);
        int role = menu.role(i);
        if (role < 0 || role >= CoreHiring.NAMES.length) return;
        String name = CoreHiring.NAMES[role];
        String price = menu.sold(i) ? "Returns next rotation" : menu.cost(i) < 0 ? "Hiring unavailable"
                : menu.cost(i) + " " + menu.getSlot(4).getItem().getHoverName().getString();
        if (layout.compact()) {
            g.renderItem(menu.getSlot(i).getItem(), x + 8, y + (h - 16) / 2);
            g.drawString(font, font.plainSubstrByWidth((i==3?"Hero: ":i == 2 ? "Worker: " : "Recruit: ") + name, w - 120), x + 30, y + 7, TEXT, false);
            if(h>=30)g.drawString(font, font.plainSubstrByWidth(price, w - 120), x + 30, y + 19, MUTED, false);
            if (h >= 45) g.drawString(font, CoreHiring.rarity(role) + " • " + CoreHiring.weight(role) + "%", x + 30, y + 31, accent, false);
        } else {
            g.drawString(font, i==3 ? "FEATURED HERO" : i == 2 ? "WORKFORCE" : "RECRUIT " + (i + 1), x + 12, y + 12, accent, false);
            g.fill(x + 12, y + 30, x + w - 12, y + 31, 0xff41506a);
            CoreButton.panel(g,x+w/2-25,y+36,50,44,0xff111c29);
            g.pose().pushPose();
            g.pose().translate(x + w / 2f - 16, y + 42, 0); g.pose().scale(2, 2, 1);
            g.renderItem(menu.getSlot(i).getItem(), 0, 0); g.pose().popPose();
            g.drawCenteredString(font,font.plainSubstrByWidth(name,w-20),x+w/2,y+86,TEXT);
            g.drawCenteredString(font, CoreHiring.rarity(role) + " • " + CoreHiring.weight(role) + "%", x + w / 2, y + 101, accent);
            if(i==3) {
                int ty=y+120;
                for(var line:font.split(Component.literal(HeroTraits.description(role)),w-24)) {
                    if(ty>y+h-60)break;
                    g.drawString(font,line,x+12,ty,MUTED,false);ty+=11;
                }
            } else g.drawCenteredString(font,font.plainSubstrByWidth(DETAILS[role],w-20),x+w/2,y+122,MUTED);
            g.drawCenteredString(font, font.plainSubstrByWidth(price, w - 20), x + w / 2, y + h - 49, TEXT);
        }
    }
}
