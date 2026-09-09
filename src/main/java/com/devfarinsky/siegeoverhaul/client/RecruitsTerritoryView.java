package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.FactionLogger;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.level.Level;
import java.lang.reflect.*;

/** Client-only view of Recruits' own map cache and claim overlays. Never scans or generates terrain. */
public final class RecruitsTerritoryView implements AutoCloseable {
    private static final String ROOT="com.talhanation.recruits.client.gui.worldmap.";
    private Object renderer;
    private Method render, claims, players, close;
    private Class<?> overlayType;
    private boolean attempted, failed;
    private static final double SCALE=2;

    private boolean initialize(Minecraft mc) {
        if(attempted)return !failed;
        attempted=true;
        try {
            Class<?> cacheClass=Class.forName(ROOT+"storage.WorldMapCacheManager");
            Object cache=cacheClass.getMethod("getInstance").invoke(null);
            cacheClass.getMethod("initialize",Level.class).invoke(cache,mc.level);
            Class<?> rendererClass=Class.forName(ROOT+"render.WorldMapRenderer");
            renderer=rendererClass.getConstructor(cacheClass).newInstance(cache);
            close=rendererClass.getMethod("close");
            for(Method method:rendererClass.getMethods())
                if(method.getName().equals("render") && method.getParameterCount()==7)render=method;
            if(render==null)throw new NoSuchMethodException("WorldMapRenderer.render");
            overlayType=render.getParameterTypes()[6];
            claims=Class.forName(ROOT+"claim.ClaimRenderer").getMethod("renderClaimsOverlay",GuiGraphics.class,
                    Class.forName("com.talhanation.recruits.world.RecruitsClaim"),double.class,double.class,double.class);
            players=Class.forName(ROOT+"render.WorldMapPlayerRenderer").getMethod("render",GuiGraphics.class,
                    net.minecraft.client.gui.Font.class,net.minecraft.world.entity.player.Player.class,
                    double.class,double.class,double.class,boolean.class);
            return true;
        } catch(ReflectiveOperationException | RuntimeException | LinkageError ex) { fail(ex);return false; }
    }

    /** Render at screen coordinates: the native framebuffer requires the real window projection. */
    public boolean draw(GuiGraphics g,int x,int y,int width,int height) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null || mc.level.dimension()!=Level.OVERWORLD || mc.player==null || width<=0 || height<=0 || !initialize(mc))return false;
        double ox=x+width/2.0-mc.player.getX()*SCALE,oz=y+height/2.0-mc.player.getZ()*SCALE;
        Object overlay=Proxy.newProxyInstance(overlayType.getClassLoader(),new Class<?>[]{overlayType},(proxy,method,args)->{
            if(method.getDeclaringClass()==Object.class)return switch(method.getName()){
                case "toString" -> "Siege territory overlay";case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy==args[0];default -> null;};
            GuiGraphics map=(GuiGraphics)args[0];Object frame=args[1];Class<?> type=frame.getClass();
            double secondary=(double)type.getMethod("secondaryScale").invoke(frame);
            map.pose().pushPose();
            try {
                map.pose().translate((double)type.getMethod("secondaryOffsetX").invoke(frame),
                        (double)type.getMethod("secondaryOffsetZ").invoke(frame),0);
                map.pose().scale((float)(1/secondary),(float)(1/secondary),1);
                claims.invoke(null,map,null,ox,oz,SCALE);
                players.invoke(null,map,mc.font,mc.player,ox,oz,SCALE,true);
            } finally {map.pose().popPose();}
            return null;
        });
        g.flush();g.enableScissor(x,y,x+width,y+height);
        try {
            render.invoke(renderer,g,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),ox,oz,SCALE,overlay);
            return true;
        } catch(ReflectiveOperationException | RuntimeException | LinkageError ex) {fail(ex);return false;}
        finally {
            mc.getMainRenderTarget().bindWrite(true);
            RenderSystem.setShaderColor(1,1,1,1);RenderSystem.depthMask(true);RenderSystem.enableDepthTest();
            g.disableScissor();
        }
    }

    public static void openFullMap() {
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null || mc.level.dimension()!=Level.OVERWORLD)return;
        try {
            Screen screen=(Screen)Class.forName(ROOT+"WorldMapScreen").getConstructor().newInstance();
            if(mc.player!=null)mc.player.closeContainer();
            mc.setScreen(screen);
        } catch(ReflectiveOperationException | RuntimeException | LinkageError ex) {
            FactionLogger.LOG.warn("Could not open Recruits territory map",ex);
            if(mc.player!=null)mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("Recruits map is unavailable; try its map key."),false);
        }
    }
    private void fail(Throwable ex){failed=true;FactionLogger.LOG.warn("Recruits territory preview unavailable",ex);close();}
    @Override public void close(){
        if(renderer!=null && close!=null)try{close.invoke(renderer);}catch(ReflectiveOperationException ignored){}
        renderer=null;
    }
}
