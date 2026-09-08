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
    public CoreButton(Component text,OnPress press,int x,int y,int width,int height,boolean tab,BooleanSupplier selected) {
        super(x,y,width,height,text,press,DEFAULT_NARRATION);this.tab=tab;this.selected=selected;
    }
    public static void panel(GuiGraphics g,int x,int y,int w,int h,int color) {
        g.fill(x+3,y,x+w-3,y+h,color);g.fill(x+1,y+1,x+w-1,y+h-1,color);g.fill(x,y+3,x+w,y+h-3,color);
    }
    @Override public void renderWidget(GuiGraphics g,int mouseX,int mouseY,float partial) {
        boolean chosen=selected.getAsBoolean();boolean hover=isHoveredOrFocused();
        int border=!active?0xff26303c:chosen?0xff86dec8:hover?0xff8ea8bd:0xff3c5066;
        int fill=!active?0xff1b2430:chosen?0xff244f4d:hover?0xff33495e:tab?0xff182532:0xff253b4b;
        panel(g,getX(),getY(),width,height,border);panel(g,getX()+1,getY()+1,width-2,height-2,fill);
        var font=Minecraft.getInstance().font;
        String label=font.plainSubstrByWidth(getMessage().getString(),Math.max(1,width-12));
        g.drawCenteredString(font,label,getX()+width/2,getY()+(height-8)/2,!active?0xff728091:chosen?0xffb3f4df:0xfff1f5f8);
    }
}
