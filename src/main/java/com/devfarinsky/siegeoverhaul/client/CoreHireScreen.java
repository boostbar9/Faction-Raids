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
    private static final net.minecraft.resources.ResourceLocation COMMAND_ART=new net.minecraft.resources.ResourceLocation(SiegeOverhaul.MOD_ID,"textures/gui/kingdom_command.png");
    private CoreHireLayout layout;
    private RecruitsTerritoryView territory;
    private Button mapButton;
    private final CoreRecruitPreviews portraits=new CoreRecruitPreviews();
    private int tab,confirmBox=-1,seenLoot,revealBox=-1,revealTicks,waitingTicks,rosterOffset;
    private ItemStack revealed=ItemStack.EMPTY;
    private int revealedTier;
    private final Button[] hire=new Button[4],boxes=new Button[3],buffs=new Button[3],bank=new Button[4];
    public CoreHireScreen(CoreHireMenu menu,Inventory inventory,Component title){super(menu,inventory,title);}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event){event.enqueueWork(()->MenuScreens.register(CoreMenus.HIRING.get(),CoreHireScreen::new));}
    private void action(int id){if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);}
    @Override protected void init(){
        if(territory!=null)territory.close();
        portraits.clear();
        territory=new RecruitsTerritoryView();
        layout=CoreHireLayout.fit(width,height);imageWidth=layout.width();imageHeight=layout.height();super.init();
        String[] tabs={"Army & Heroes","Loot & Buffs","Bank & Faction"};int tw=(layout.width()-28)/3;
        for(int i=0;i<3;i++){final int index=i;addRenderableWidget(new CoreButton(Component.literal(tabs[i]),b->{tab=index;confirmBox=-1;},layout.x()+10+i*(tw+4),layout.tabY(),tw,22,true,()->tab==index));}
        mapButton=addRenderableWidget(new CoreButton(Component.literal("Open map"),b->RecruitsTerritoryView.openFullMap(),layout.x()+layout.width()-92,layout.y()+7,82,21,false,()->false));
        for(int i=0;i<4;i++){
            final int index=i;
            hire[i]=addRenderableWidget(new CoreButton(Component.literal("Recruit"),b->RaidNetwork.purchaseCoreOffer(menu.containerId,index,menu.rotation()),layout.cardX(i)+8,layout.cardY(i)+layout.cardHeight()-23,layout.cardWidth()-16,18,false,()->false));
            int bw=(layout.width()-38)/4;
            bank[i]=addRenderableWidget(new CoreButton(Component.literal(new String[]{"Store 8","Store 64","Take 8","Take 64"}[i]),b->action(40+index),layout.x()+10+i*(bw+6),layout.contentY()+40,bw,18,false,()->false));
        }
        for(int i=0;i<3;i++){
            final int index=i;int y=layout.marketButtonY(i);
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
        mapButton.active=minecraft!=null && minecraft.level!=null && minecraft.level.dimension()==net.minecraft.world.level.Level.OVERWORLD;
        for(int i=0;i<4;i++){
            hire[i].visible=tab==0;hire[i].active=menu.role(i)>=0&&menu.role(i)<CoreHiring.NAMES.length&&menu.cost(i)>=0&&!menu.sold(i)&&menu.rotation()>0;
            hire[i].setMessage(Component.literal(menu.sold(i)?"Recruited":menu.cost(i)<0?"Unavailable":"Recruit • "+menu.cost(i)));
            bank[i].visible=tab==2;bank[i].active=i<2?menu.emeralds()>0:menu.canWithdraw()&&menu.bank()>0;
        }
        for(int i=0;i<3;i++){
            boxes[i].visible=buffs[i].visible=tab==1;
            boxes[i].active=waitingTicks==0&&revealTicks==0&&menu.emeralds()>=CoreLoot.price(i);
            boxes[i].setMessage(Component.literal(waitingTicks>0?"Waiting…":revealTicks>0?"Unsealing…":(confirmBox==i?"Confirm • ":"Open • ")+CoreLoot.price(i)));
            boolean active=minecraft!=null&&minecraft.player!=null&&minecraft.player.hasEffect(CoreBuffs.effect(i));
            buffs[i].active=!active&&menu.emeralds()>=CoreBuffs.PRICES[i];
            buffs[i].setMessage(Component.literal(active?"Active • "+effectTime(i):"Bless • "+CoreBuffs.PRICES[i]));
        }
        super.render(g,mx,my,partial);
        if(mapButton.isHovered())tooltip(g,mapButton.active?"Open the full Recruits map. Closes this command center.":"The Recruits territory map is available in the Overworld.",mx,my);
        if(tab==2)for(int i=0;i<4;i++)if(bank[i].isHovered())tooltip(g,i<2?"Deposit up to "+(i==0?8:64)+" personal emeralds. Your purse: "+menu.emeralds():!menu.canWithdraw()?"Only the faction leader can withdraw emeralds.":"Withdraw up to "+(i==2?8:64)+" emeralds. Limited by bank balance and inventory space.",mx,my);
        if(tab==0)for(int i=0;i<4;i++)if(over(mx,my,layout.cardX(i),layout.cardY(i),layout.cardWidth(),layout.cardHeight())&&menu.role(i)>=0&&menu.role(i)<CoreHiring.NAMES.length){
            int role=menu.role(i);String info=CoreHiring.NAMES[role]+" • "+CoreHiring.rarity(role)+" • "+(i==3?HeroTraits.description(role):"Shared faction offer; refreshes every 15 minutes. Portrait shows the role; hired equipment varies.");
            tooltip(g,info,mx,my);
        }
        if(tab==1)for(int i=0;i<3;i++){
            if(over(mx,my,layout.cardX(0),layout.marketY(i),layout.cardWidth(),layout.marketHeight()))tooltip(g,revealBox==i&&revealTicks==0&&!revealed.isEmpty()?CoreLoot.rarity(revealedTier)+" • "+revealed.getCount()+"× "+revealed.getHoverName().getString():"One mystery reward • "+CoreLoot.odds(),mx,my);
            if(over(mx,my,layout.cardX(1),layout.marketY(i),layout.cardWidth(),layout.marketHeight()))tooltip(g,CoreBuffs.DETAILS[i]+" • "+(minecraft!=null&&minecraft.player!=null&&minecraft.player.hasEffect(CoreBuffs.effect(i))?"Current effect remaining: "+effectTime(i):"Duration: 5 minutes")+". Uses your personal emeralds; existing effects are preserved.",mx,my);
        }
    }
    private String effectTime(int index){
        var effect=minecraft==null||minecraft.player==null?null:minecraft.player.getEffect(CoreBuffs.effect(index));
        if(effect==null)return "0:00";
        if(effect.isInfiniteDuration())return "Infinite";
        int seconds=(int)Math.max(0,(effect.getDuration()+19L)/20);
        return String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60);
    }
    private boolean over(int mx,int my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    private void tooltip(GuiGraphics g,String text,int x,int y){g.renderTooltip(font,font.split(Component.literal(text),Math.min(300,width-24)),x,y);}
    private void text(GuiGraphics g,String text,int x,int y,int width,int color){g.drawString(font,font.plainSubstrByWidth(text,Math.max(1,width)),x,y,color,false);}
    private void panel(GuiGraphics g,int x,int y,int w,int h,int accent){
        CoreButton.panel(g,x+1,y+3,w,h,0x70000000);
        CoreButton.panel(g,x,y,w,h,0xff354157);
        g.fillGradient(x+1,y+1,x+w-1,y+h-1,0xff202a3e,0xff101827);
        g.fill(x+7,y+1,x+w-7,y+2,accent);
    }
    @Override protected void renderLabels(GuiGraphics g,int x,int y){}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        int x=layout.x(),y=layout.y(),w=layout.width(),h=layout.height();
        CoreButton.panel(g,x-4,y+3,w+8,h+5,0x99000000);
        // Scale the decorative frame only; all interactive controls retain their tested bounds.
        g.blit(COMMAND_ART,x-6,y-6,w+12,h+12,0.0f,0.0f,1613,975,1613,975);
        g.fillGradient(x+7,y+2,x+w-7,y+33,0xb0273044,0xd0151c2b);
        CommandIcon.CROWN.draw(g,x+12,y+9,16);
        text(g,"KINGDOM COMMAND",x+36,y+7,w>=600?w-286:w-140,GOLD);
        text(g,menu.factionName(),x+36,y+20,w>=600?w-286:w-140,MUTED);
        if(w>=600)text(g,String.format(java.util.Locale.ROOT,"Treasury %,d",menu.bank()),x+w-240,y+14,138,TEAL);
        g.fill(x+10,y+32,x+w-10,y+33,0xff63567a);
        if(layout.overviewHeight()>0)drawOverview(g);
        if(tab==0)for(int i=0;i<4;i++)drawHire(g,i);
        else if(tab==1)for(int i=0;i<3;i++){drawLoot(g,i);drawBuff(g,i);}
        else drawFaction(g);
        String footer=tab==0?String.format(java.util.Locale.ROOT,"Shared stock • Refresh %d:%02d",menu.seconds()/60,menu.seconds()%60):tab==1?"Your emeralds: "+menu.emeralds()+" • Loot & five-minute blessings":"Interest "+menu.interestRate()/100.0+"% /24h • Leader withdrawals • Scroll roster";
        text(g,footer,x+10,y+h-13,w-20,MUTED);
    }
    private void drawOverview(GuiGraphics g){
        int x=layout.x()+10,y=layout.overviewY(),h=layout.overviewHeight(),mw=layout.mapWidth();
        panel(g,x,y,mw,h,VIOLET);
        boolean available=territory.draw(g,x+2,y+19,mw-4,h-21);
        if(!available){
            g.fillGradient(x+2,y+19,x+mw-2,y+h-2,0xff162331,0xff111827);
            text(g,"Preview unavailable — Open map",x+10,y+Math.max(24,h/2),mw-20,MUTED);
        }
        CommandIcon.MAP.draw(g,x+5,y+2,16);
        text(g,"TERRITORY",x+25,y+6,mw-30,GOLD);
        int sx=x+mw+10,sw=layout.width()-30-mw;
        panel(g,sx,y,sw,h,GOLD);
        (menu.currentWave()>0?CommandIcon.SWORDS:CommandIcon.SHIELD).draw(g,sx+7,y+4,16);
        text(g,menu.currentWave()>0?"SIEGE ACTIVE":"KINGDOM WATCH",sx+27,y+8,sw-34,GOLD);
        text(g,menu.currentWave()>0?"Wave "+menu.currentWave():"Stand ready",sx+10,y+23,sw-20,TEXT);
        if(h>=85){
            String next=menu.voteSeconds()>0?"Retreat vote: "+menu.voteSeconds()+"s":menu.currentWave()>0?"Retreat checkpoint: "+EndlessSiege.nextCheckpoint(menu.currentWave()):"Prepare your defenses";
            text(g,next,sx+10,y+38,sw-20,MUTED);
            g.fill(sx+10,y+52,sx+sw-10,y+53,0xff3a4154);
            text(g,"Next wave: +"+menu.nextReward()+" emeralds",sx+10,y+61,sw-20,TEAL);
        }
        if(h>=115){text(g,String.format(java.util.Locale.ROOT,"Faction bank: %,d",menu.bank()),sx+10,y+80,sw-20,TEXT);
            text(g,"Your purse: "+menu.emeralds(),sx+10,y+96,sw-20,MUTED);}
    }
    @Override public void removed(){if(territory!=null)territory.close();portraits.clear();super.removed();}
    private void drawHire(GuiGraphics g,int i){
        int x=layout.cardX(i),y=layout.cardY(i),w=layout.cardWidth(),h=layout.cardHeight(),role=menu.role(i);
        int accent=i==3?VIOLET:i==2?TEAL:GOLD;panel(g,x,y,w,h,menu.sold(i)?0xff45485f:accent);
        if(role<0||role>=CoreHiring.NAMES.length)return;
        g.renderItem(menu.getSlot(i).getItem(),x+8,y+8);
        text(g,(i==3?"Hero • ":i==2?"Worker • ":"")+CoreHiring.NAMES[role],x+29,y+7,w-36,TEXT);
        text(g,CoreHiring.rarity(role),x+29,y+19,w-36,accent);
        boolean portrait=h>=100&&w>=220;
        int detailWidth=w-(portrait?92:18);
        if(h>80){text(g,i==3?HeroTraits.description(role):"Ready to join your faction",x+9,y+40,detailWidth,MUTED);text(g,"Cost: "+menu.cost(i)+" "+menu.getSlot(4).getItem().getHoverName().getString(),x+9,y+54,detailWidth,TEXT);}
        if(portrait){
            g.enableScissor(x+2,y+29,x+w-2,y+h-25);
            try{portraits.draw(g,role,x+w-40,y+h-26,Math.min(32,(h-56)/2));}finally{g.disableScissor();}
        }
    }
    private void drawLoot(GuiGraphics g,int i){
        int x=layout.cardX(0),y=layout.marketY(i),w=layout.cardWidth(),h=layout.marketHeight();panel(g,x,y,w,h,GOLD);
        boolean opening=revealBox==i&&revealTicks>0,done=revealBox==i&&revealTicks==0&&!revealed.isEmpty();
        if(done)g.renderItem(revealed,x+7,y+7);else if(opening)g.drawCenteredString(font,new String[]{"*","+","?","#"}[(revealTicks/4)%4],x+16,y+11,VIOLET);else CommandIcon.CHEST.draw(g,x+7,y+7,16);
        text(g,CoreLoot.NAMES[i],x+29,y+6,w-36,TEXT);
        if(h>=49)text(g,opening?"✦  +  ?  ✦":done?revealed.getHoverName().getString():"Sealed mystery",x+29,y+17,w-36,opening?VIOLET:MUTED);
        if(layout.reelHeight()>=12 && opening){
            int bottom=y+31+layout.reelHeight();
            g.enableScissor(x+7,y+31,x+w-7,bottom);
            double elapsed=CoreLoot.OPEN_TICKS-revealTicks;
            int shift=(int)(elapsed*6-elapsed*elapsed*2/CoreLoot.OPEN_TICKS)%24;
            for(int n=-1;n<=w/24+1;n++)g.drawCenteredString(font,new String[]{"?","*","+","#"}[Math.floorMod(n,4)],x+12+n*24-shift,y+31+(layout.reelHeight()-8)/2,n%2==0?VIOLET:TEAL);
            g.disableScissor();g.fill(x+w/2-1,y+31,x+w/2+1,y+35,GOLD);g.fill(x+w/2-1,bottom-4,x+w/2+1,bottom,GOLD);
        }else if(h>=68)text(g,done?CoreLoot.rarity(revealedTier):"Reveal your reward",x+9,y+34,w-18,GOLD);
    }
    private void drawBuff(GuiGraphics g,int i){
        int x=layout.cardX(1),y=layout.marketY(i),w=layout.cardWidth(),h=layout.marketHeight();panel(g,x,y,w,h,TEAL);
        (i==0?CommandIcon.WIND:i==1?CommandIcon.POWER:CommandIcon.SHIELD).draw(g,x+7,y+7,16);
        text(g,CoreBuffs.NAMES[i],x+29,y+6,w-36,TEXT);if(h>=49)text(g,CoreBuffs.DETAILS[i],x+29,y+17,w-36,MUTED);
        if(h>=68)text(g,"Personal blessing • 5 min",x+9,y+34,w-18,TEAL);
    }
    private void drawFaction(GuiGraphics g){
        int x=layout.x(),y=layout.contentY()-62,w=layout.width(),h=layout.contentHeight()+84;
        panel(g,x+10,y+62,w-20,34,VIOLET);
        CommandIcon.BANK.draw(g,x+17,y+68,20);
        text(g,menu.factionName(),x+42,y+67,w/2-48,TEXT);
        text(g,String.format(java.util.Locale.ROOT,"Bank: %,d emeralds",menu.bank()),x+42,y+80,w/2-48,GOLD);
        text(g,"Next wave "+menu.nextWave()+": +"+menu.nextReward(),x+w/2,y+68,w/2-18,TEAL);
        text(g,menu.voteSeconds()>0?"Retreat vote: "+menu.voteSeconds()+"s":menu.currentWave()>0?"Surviving wave "+menu.currentWave():"Preparing for the next siege",x+w/2,y+81,w/2-18,MUTED);
        int lines=layout.rosterLines(),count=menu.members().size();
        rosterOffset=layout.rosterOffset(rosterOffset,count);
        text(g,"FACTION ROSTER • "+count,x+12,y+130,w-130,VIOLET);
        if(count>0)text(g,(rosterOffset+1)+"–"+Math.min(count,rosterOffset+lines)+" / "+count,x+w-112,y+130,100,MUTED);
        if(count==0)text(g,"No faction members listed",x+14,layout.rosterY(),w-28,MUTED);
        for(int i=0;i<lines&&i+rosterOffset<count;i++){
            int rowY=layout.rosterY()+i*12;
            if(i%2==0)g.fill(x+11,rowY-1,x+w-17,rowY+10,0x202f425b);
            text(g,menu.members().get(i+rosterOffset),x+14,rowY,w-36,TEXT);
        }
        if(count>lines){
            int track=lines*12,thumb=Math.max(8,track*lines/count);
            int top=layout.rosterY()+(track-thumb)*rosterOffset/(count-lines);
            g.fill(x+w-14,layout.rosterY(),x+w-11,layout.rosterY()+track,0xff293447);
            g.fill(x+w-14,top,x+w-11,top+thumb,VIOLET);
        }
    }
    @Override public boolean mouseScrolled(double x,double y,double delta){
        if(tab==2&&layout.overRoster(x,y)&&delta!=0){rosterOffset=layout.rosterOffset(rosterOffset-(int)Math.signum(delta),menu.members().size());return true;}
        return super.mouseScrolled(x,y,delta);
    }
}
