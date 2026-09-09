package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.core.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Three compact command panels; purchase authority remains entirely on the server. */
@Mod.EventBusSubscriber(modid=SiegeOverhaul.MOD_ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class CoreHireScreen extends AbstractContainerScreen<CoreHireMenu> {
    private static final int TEXT=0xffeee9ff,MUTED=0xffa6a9c7,GOLD=0xffe2c581,TEAL=0xff81e8da,VIOLET=0xffb89aff;
    private CoreHireLayout layout;
    private int tab,confirmBox=-1,seenLoot,revealBox=-1,revealTicks,waitingTicks,rosterOffset;
    private ItemStack revealed=ItemStack.EMPTY;
    private int revealedTier;
    private final Button[] hire=new Button[4],boxes=new Button[3],buffs=new Button[3],bank=new Button[4];
    public CoreHireScreen(CoreHireMenu menu,Inventory inventory,Component title){super(menu,inventory,title);}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event){event.enqueueWork(()->MenuScreens.register(CoreMenus.HIRING.get(),CoreHireScreen::new));}
    private void action(int id){if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);}
    @Override protected void init(){
        layout=CoreHireLayout.fit(width,height);imageWidth=layout.width();imageHeight=layout.height();super.init();
        String[] tabs={"Army & Heroes","Loot & Buffs","Bank & Faction"};int tw=(layout.width()-28)/3;
        for(int i=0;i<3;i++){final int index=i;addRenderableWidget(new CoreButton(Component.literal(tabs[i]),b->{tab=index;confirmBox=-1;},layout.x()+10+i*(tw+4),layout.y()+32,tw,22,true,()->tab==index));}
        for(int i=0;i<4;i++){
            final int index=i;
            hire[i]=addRenderableWidget(new CoreButton(Component.literal("Recruit"),b->RaidNetwork.purchaseCoreOffer(menu.containerId,index,menu.rotation()),layout.cardX(i)+8,layout.cardY(i)+layout.cardHeight()-23,layout.cardWidth()-16,18,false,()->false));
            int bw=(layout.width()-38)/4;
            bank[i]=addRenderableWidget(new CoreButton(Component.literal(new String[]{"Store 8","Store 64","Take 8","Take 64"}[i]),b->action(40+index),layout.x()+10+i*(bw+6),layout.y()+102,bw,18,false,()->false));
        }
        for(int i=0;i<3;i++){
            final int index=i;int y=layout.marketY(i)+layout.marketHeight()-21;
            boxes[i]=addRenderableWidget(new CoreButton(Component.literal("Open"),b->{
                if(confirmBox!=index){confirmBox=index;return;}
                if(waitingTicks>0||revealTicks>0)return;
                waitingTicks=60;action(20+index);confirmBox=-1;
            },layout.cardX(0)+7,y,layout.cardWidth()-14,17,false,()->confirmBox==index));
            buffs[i]=addRenderableWidget(new CoreButton(Component.literal("Bless"),b->action(30+index),layout.cardX(1)+7,y,layout.cardWidth()-14,17,false,()->false));
        }
    }
    @Override protected void containerTick(){
        super.containerTick();if(waitingTicks>0)waitingTicks--;
        if(menu.lootSequence()!=seenLoot){seenLoot=menu.lootSequence();revealBox=menu.lootBox();revealed=menu.lootReward().copy();revealedTier=menu.lootTier();revealTicks=CoreLoot.OPEN_TICKS;waitingTicks=0;}
        if(revealTicks>0){revealTicks--;if(tab==1&&(revealTicks==0||revealTicks%(revealTicks>20?5:10)==0)&&minecraft!=null)
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,revealTicks==0?1.2f:.6f+(CoreLoot.OPEN_TICKS-revealTicks)*.01f));}
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g);
        for(int i=0;i<4;i++){
            hire[i].visible=tab==0;hire[i].active=menu.role(i)>=0&&menu.cost(i)>=0&&!menu.sold(i)&&menu.rotation()>0;
            hire[i].setMessage(Component.literal(menu.sold(i)?"Recruited":menu.cost(i)<0?"Unavailable":"Recruit • "+menu.cost(i)));
            bank[i].visible=tab==2;bank[i].active=i<2?menu.emeralds()>0:menu.canWithdraw()&&menu.bank()>0;
        }
        for(int i=0;i<3;i++){
            boxes[i].visible=buffs[i].visible=tab==1;
            boxes[i].active=waitingTicks==0&&revealTicks==0&&menu.emeralds()>=CoreLoot.price(i);
            boxes[i].setMessage(Component.literal(waitingTicks>0?"Waiting…":revealTicks>0?"Unsealing…":(confirmBox==i?"Confirm • ":"Open • ")+CoreLoot.price(i)));
            boolean active=minecraft!=null&&minecraft.player!=null&&minecraft.player.hasEffect(CoreBuffs.effect(i));
            buffs[i].active=!active&&menu.emeralds()>=CoreBuffs.PRICES[i];
            buffs[i].setMessage(Component.literal(active?"Blessing active":"Bless • "+CoreBuffs.PRICES[i]));
        }
        super.render(g,mx,my,partial);
        if(tab==0)for(int i=0;i<4;i++)if(over(mx,my,layout.cardX(i),layout.cardY(i),layout.cardWidth(),layout.cardHeight())&&menu.role(i)>=0){
            int role=menu.role(i);String info=CoreHiring.NAMES[role]+" • "+CoreHiring.rarity(role)+" • "+(i==3?HeroTraits.description(role):"Shared faction offer; refreshes every 15 minutes.");
            tooltip(g,info,mx,my);
        }
        if(tab==1)for(int i=0;i<3;i++){
            if(over(mx,my,layout.cardX(0),layout.marketY(i),layout.cardWidth(),layout.marketHeight()))tooltip(g,revealBox==i&&revealTicks==0&&!revealed.isEmpty()?CoreLoot.rarity(revealedTier)+" • "+revealed.getCount()+"× "+revealed.getHoverName().getString():"One mystery reward • "+CoreLoot.odds(),mx,my);
            if(over(mx,my,layout.cardX(1),layout.marketY(i),layout.cardWidth(),layout.marketHeight()))tooltip(g,CoreBuffs.DETAILS[i]+" for 5 minutes. Uses your personal emeralds; existing effects are preserved.",mx,my);
        }
    }
    private boolean over(int mx,int my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    private void tooltip(GuiGraphics g,String text,int x,int y){g.renderTooltip(font,font.split(Component.literal(text),Math.min(300,width-24)),x,y);}
    private void text(GuiGraphics g,String text,int x,int y,int width,int color){g.drawString(font,font.plainSubstrByWidth(text,Math.max(1,width)),x,y,color,false);}
    private void panel(GuiGraphics g,int x,int y,int w,int h,int accent){CoreButton.panel(g,x,y,w,h,accent);CoreButton.panel(g,x+1,y+1,w-2,h-2,0xff171c32);g.fill(x+7,y+1,x+w-7,y+2,accent);}
    @Override protected void renderLabels(GuiGraphics g,int x,int y){}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        int x=layout.x(),y=layout.y(),w=layout.width(),h=layout.height();
        CoreButton.panel(g,x-4,y+3,w+8,h+5,0x99000000);CoreButton.panel(g,x-1,y-1,w+2,h+2,0xff7663ac);
        g.fillGradient(x,y,x+w,y+h,0xff20203c,0xff0d1828);
        // Small arcane sigil: restrained animation with no custom textures or per-frame entities.
        long time=minecraft!=null&&minecraft.level!=null?minecraft.level.getGameTime():0;
        for(int i=0;i<8;i++){double a=i*Math.PI/4+time*.012;int sx=x+21+(int)(Math.cos(a)*10),sy=y+16+(int)(Math.sin(a)*10);g.fill(sx,sy,sx+2,sy+2,i%2==0?TEAL:VIOLET);}
        g.drawCenteredString(font,"✦",x+22,y+12,GOLD);
        text(g,"ARCANE COMMAND",x+40,y+12,w/2-36,TEXT);
        String purse=menu.emeralds()+" emeralds";text(g,purse,x+w-font.width(purse)-12,y+12,w/2,TEAL);
        g.fill(x+10,y+27,x+w-10,y+28,0xff564776);
        if(tab==0)for(int i=0;i<4;i++)drawHire(g,i);
        else if(tab==1)for(int i=0;i<3;i++){drawLoot(g,i);drawBuff(g,i);}
        else drawFaction(g);
        String footer=tab==0?String.format(java.util.Locale.ROOT,"Shared stock • Refresh %d:%02d",menu.seconds()/60,menu.seconds()%60):tab==1?"Mystery loot & five-minute blessings • Personal emeralds":"Interest "+menu.interestRate()/100.0+"% /24h • Leader withdrawals • Scroll roster";
        text(g,footer,x+10,y+h-13,w-20,MUTED);
    }
    private void drawHire(GuiGraphics g,int i){
        int x=layout.cardX(i),y=layout.cardY(i),w=layout.cardWidth(),h=layout.cardHeight(),role=menu.role(i);
        int accent=i==3?VIOLET:i==2?TEAL:GOLD;panel(g,x,y,w,h,menu.sold(i)?0xff45485f:accent);
        if(role<0||role>=CoreHiring.NAMES.length)return;
        g.renderItem(menu.getSlot(i).getItem(),x+8,y+8);
        text(g,(i==3?"Hero • ":i==2?"Worker • ":"")+CoreHiring.NAMES[role],x+29,y+7,w-36,TEXT);
        text(g,CoreHiring.rarity(role),x+29,y+19,w-36,accent);
        if(h>80){text(g,i==3?HeroTraits.description(role):"Ready to join your faction",x+9,y+40,w-18,MUTED);text(g,"Cost: "+menu.cost(i)+" "+menu.getSlot(4).getItem().getHoverName().getString(),x+9,y+54,w-18,TEXT);}
    }
    private void drawLoot(GuiGraphics g,int i){
        int x=layout.cardX(0),y=layout.marketY(i),w=layout.cardWidth(),h=layout.marketHeight();panel(g,x,y,w,h,GOLD);
        boolean opening=revealBox==i&&revealTicks>0,done=revealBox==i&&revealTicks==0&&!revealed.isEmpty();
        if(done)g.renderItem(revealed,x+7,y+7);else g.drawCenteredString(font,opening?new String[]{"*","+","?","#"}[(revealTicks/4)%4]:"?",x+16,y+11,opening?VIOLET:GOLD);
        text(g,CoreLoot.NAMES[i],x+29,y+6,w-36,TEXT);
        if(h>=49)text(g,opening?"✦  +  ?  ✦":done?revealed.getHoverName().getString():"Sealed mystery",x+29,y+17,w-36,opening?VIOLET:MUTED);
        if(h>64 && opening){
            g.enableScissor(x+7,y+31,x+w-7,y+57);
            double elapsed=CoreLoot.OPEN_TICKS-revealTicks;
            int shift=(int)(elapsed*6-elapsed*elapsed*2/CoreLoot.OPEN_TICKS)%24;
            for(int n=-1;n<=w/24+1;n++)g.drawCenteredString(font,new String[]{"?","*","+","#"}[Math.floorMod(n,4)],x+12+n*24-shift,y+39,n%2==0?VIOLET:TEAL);
            g.disableScissor();g.fill(x+w/2-1,y+31,x+w/2+1,y+35,GOLD);g.fill(x+w/2-1,y+53,x+w/2+1,y+57,GOLD);
        }else if(h>64)text(g,done?CoreLoot.rarity(revealedTier):"Reveal your reward",x+9,y+34,w-18,GOLD);
    }
    private void drawBuff(GuiGraphics g,int i){
        int x=layout.cardX(1),y=layout.marketY(i),w=layout.cardWidth(),h=layout.marketHeight();panel(g,x,y,w,h,TEAL);
        g.renderItem(new ItemStack(i==0?Items.FEATHER:i==1?Items.BLAZE_POWDER:Items.AMETHYST_SHARD),x+7,y+7);
        text(g,CoreBuffs.NAMES[i],x+29,y+6,w-36,TEXT);if(h>=49)text(g,CoreBuffs.DETAILS[i],x+29,y+17,w-36,MUTED);
        if(h>64)text(g,"Personal blessing • 5 min",x+9,y+34,w-18,TEAL);
    }
    private void drawFaction(GuiGraphics g){
        int x=layout.x(),y=layout.y(),w=layout.width(),h=layout.height();
        panel(g,x+10,y+62,w-20,34,VIOLET);
        text(g,menu.factionName(),x+18,y+67,w/2-24,TEXT);
        text(g,String.format(java.util.Locale.ROOT,"Bank: %,d emeralds",menu.bank()),x+18,y+80,w/2-24,GOLD);
        text(g,"Next wave "+menu.nextWave()+": +"+menu.nextReward(),x+w/2,y+68,w/2-18,TEAL);
        text(g,menu.voteSeconds()>0?"Retreat vote: "+menu.voteSeconds()+"s":menu.currentWave()>0?"Surviving wave "+menu.currentWave():"Preparing for the next siege",x+w/2,y+81,w/2-18,MUTED);
        text(g,"FACTION ROSTER",x+12,y+130,w-24,VIOLET);
        int lines=Math.max(1,(h-163)/12);rosterOffset=Math.min(rosterOffset,Math.max(0,menu.members().size()-lines));
        if(menu.members().isEmpty())text(g,"Roster is synchronizing…",x+14,y+145,w-28,MUTED);
        for(int i=0;i<lines&&i+rosterOffset<menu.members().size();i++)text(g,menu.members().get(i+rosterOffset),x+14,y+145+i*12,w-28,TEXT);
    }
    @Override public boolean mouseScrolled(double x,double y,double delta){
        if(tab==2){rosterOffset=Math.max(0,Math.min(Math.max(0,menu.members().size()-1),rosterOffset-(int)Math.signum(delta)));return true;}
        return super.mouseScrolled(x,y,delta);
    }
}
