package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.core.CaptureBeacon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Vanilla beacon geometry and texture, with server-supplied capture colors. */
@Mod.EventBusSubscriber(modid=SiegeOverhaul.MOD_ID,value=Dist.CLIENT)
public final class CaptureBeaconRenderer {
    private static final ResourceLocation TEXTURE = new ResourceLocation("minecraft","textures/entity/beacon_beam.png");
    private static final CaptureBeacon.State STATE = new CaptureBeacon.State();
    private static ClientLevel world;
    private CaptureBeaconRenderer() {}
    private static void world() {
        var current=Minecraft.getInstance().level;
        if(current!=world) { STATE.clear();world=current; }
    }
    public static void accept(RaidNetwork.CaptureBeam packet) {
        world();
        if(world!=null && packet.dimension().equals(world.dimension().location())) STATE.accept(packet,world.getGameTime());
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        world();if(world!=null)STATE.active(world.getGameTime());
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        world();if(world==null || !HeroVisualConfig.CAPTURE_BEAMS.get())return;
        var mc=Minecraft.getInstance();var pose=event.getPoseStack();var camera=event.getCamera().getPosition();
        var buffers=mc.renderBuffers().bufferSource();boolean drew=false;
        for(var beam:STATE.active(world.getGameTime())) {
            if(camera.distanceToSqr(Vec3.atCenterOf(beam.pos()))>CaptureBeacon.RANGE*CaptureBeacon.RANGE
                    || !world.hasChunkAt(beam.pos()))continue;
            pose.pushPose();
            pose.translate(beam.pos().getX()-camera.x,beam.pos().getY()+1-camera.y,beam.pos().getZ()-camera.z);
            BeaconRenderer.renderBeaconBeam(pose,buffers,TEXTURE,event.getPartialTick(),1F,world.getGameTime(),
                    0,CaptureBeacon.HEIGHT,CaptureBeacon.color(beam.percent()),.2F,.25F);
            pose.popPose();drew=true;
        }
        if(drew) {
            buffers.endBatch(RenderType.beaconBeam(TEXTURE,false));
            buffers.endBatch(RenderType.beaconBeam(TEXTURE,true));
        }
    }
}
