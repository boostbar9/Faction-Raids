package com.devfarinsky.siegeoverhaul.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import java.util.function.BooleanSupplier;

/** Custom painted buttons retain vanilla focus, narration and keyboard activation. */
public final class CoreButton extends Button {
    private final BooleanSupplier selected;
    private final boolean tab;
    private float hoverLight;
    private long lastFrame;
    public CoreButton(Component text,OnPress press,int x,int y,int width,int height,boolean tab,BooleanSupplier selected) {
        super(x,y,width,height,text,press,DEFAULT_NARRATION);this.tab=tab;this.selected=selected;
    }
    public static void panel(GuiGraphics g,int x,int y,int w,int h,int color) {
        g.fill(x+3,y,x+w-3,y+h,color);g.fill(x+1,y+1,x+w-1,y+h-1,color);g.fill(x,y+3,x+w,y+h-3,color);
    }
    @Override public void renderWidget(GuiGraphics g,int mouseX,int mouseY,float partial) {
        boolean chosen=selected.getAsBoolean();boolean hover=isHoveredOrFocused();
        long now=System.nanoTime();
        float elapsed=lastFrame==0?0:Math.min(.1f,(now-lastFrame)/1_000_000_000f);lastFrame=now;
        hoverLight+=( (active && (hover||chosen)?1:0)-hoverLight)*Math.min(1,elapsed*12);
        int border=!active?0xff283142:chosen?0xffc6a66c:hover?0xff9985c2:0xff3d495f;
        int fill=!active?0xff151b27:chosen?0xff323047:tab?0xff172131:0xff273348;
        panel(g,getX()+1,getY()+2,width,height,0x65000000);
        panel(g,getX(),getY(),width,height,border);panel(g,getX()+1,getY()+1,width-2,height-2,fill);
        int light=(int)(hoverLight*35);
        g.fillGradient(getX()+2,getY()+2,getX()+width-2,getY()+height-2,(light<<24)|0xd7b8ff,0x00d7b8ff);
        if(chosen)g.fill(getX()+8,getY()+height-2,getX()+width-8,getY()+height-1,0xffe2c581);
        var font=Minecraft.getInstance().font;
        String label=font.plainSubstrByWidth(getMessage().getString(),Math.max(1,width-12));
        g.drawCenteredString(font,label,getX()+width/2,getY()+(height-8)/2,!active?0xff728091:chosen?0xfff1ddb2:0xfff1f5f8);
    }
}
