package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.core.HeroCasting;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import java.util.*;

/** Original client geometry, local particles and audio driven by server cast events. */
@Mod.EventBusSubscriber(modid=SiegeOverhaul.MOD_ID,value=Dist.CLIENT)
public final class HeroCastVisuals {
    private static final Map<UUID,RaidNetwork.HeroCast> CASTS=new LinkedHashMap<>();
    private static ClientLevel world;
    private HeroCastVisuals() {}
    private static void world() {
        var current=Minecraft.getInstance().level;
        if(world!=current) { CASTS.clear();world=current; }
    }
    public static void accept(RaidNetwork.HeroCast packet) {
        world();if(world==null)return;
        if(packet.phase()==2) { CASTS.remove(packet.uuid());return; }
        long age=world.getGameTime()-packet.start();
        if(age< -5 || age>(packet.phase()==0?HeroCasting.WINDUP:HeroCasting.RELEASE))return;
        var existing=CASTS.get(packet.uuid());
        if(existing!=null && existing.start()>packet.start())return;
        if(!CASTS.containsKey(packet.uuid()) && CASTS.size()>=64)CASTS.remove(CASTS.keySet().iterator().next());
        CASTS.put(packet.uuid(),packet);
        var camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if(camera.distanceToSqr(new Vec3(packet.x(),packet.y(),packet.z()))>square(HeroVisualConfig.DISTANCE.get()))return;
        // Late trackers see the current pose without replaying a stale charging sound.
        if(age<=2 && HeroVisualConfig.VOLUME.get()>0) {
            SoundEvent sound=packet.phase()==0?SoundEvents.BEACON_POWER_SELECT:
                    packet.role()==27?SoundEvents.BLAZE_SHOOT:SoundEvents.TRIDENT_RIPTIDE_1;
            world.playLocalSound(packet.x(),packet.y(),packet.z(),sound,SoundSource.HOSTILE,
                    HeroVisualConfig.VOLUME.get().floatValue(),packet.phase()==0?.7F:1.1F,false);
        }
    }
    public static RaidNetwork.HeroCast current(LivingEntity entity) {
        world();var cast=CASTS.get(entity.getUUID());
        if(cast==null || cast.entityId()!=entity.getId() || world==null)return null;
        long age=world.getGameTime()-cast.start();
        return age>=0 && age<(cast.phase()==0?HeroCasting.WINDUP:HeroCasting.RELEASE)?cast:null;
    }
    public static float poseWeight(int phase,float age) {
        if(age<0)return 0;
        if(phase==0)return Math.min(1,age/8F);
        if(phase==1)return Math.max(0,1-age/HeroCasting.RELEASE);
        return 0;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        world();if(world==null || Minecraft.getInstance().isPaused())return;
        long now=world.getGameTime();int budget=128;
        var camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        var it=CASTS.values().iterator();
        while(it.hasNext()) {
            var cast=it.next();long age=now-cast.start();
            var entity=world.getEntity(cast.entityId());
            if(age< -5 || age>=(cast.phase()==0?HeroCasting.WINDUP:HeroCasting.RELEASE)
                    || entity!=null && (!entity.getUUID().equals(cast.uuid()) || !entity.isAlive())) { it.remove();continue; }
            if(entity==null || entity.isInvisible() || budget<=0 || HeroVisualConfig.PARTICLES.get()==0
                    || camera.distanceToSqr(entity.position())>square(HeroVisualConfig.DISTANCE.get()) || age%3!=0)continue;
            if(cast.phase()==1 && HeroVisualConfig.REDUCED_FLASHES.get())continue;
            int count=Math.min(budget,HeroVisualConfig.PARTICLES.get()==1?2:5);budget-=count;
            Vec3 origin=cast.phase()==0?entity.position():new Vec3(cast.x(),cast.y(),cast.z());
            for(int i=0;i<count;i++) {
                double angle=(age*.35)+(i*Math.PI*2/count);
                double radius=cast.phase()==0?.6:HeroCasting.radius(cast.role())*Math.min(1,age/(double)HeroCasting.RELEASE);
                world.addParticle(cast.role()==27?ParticleTypes.FLAME:ParticleTypes.SPLASH,
                        origin.x+Math.cos(angle)*radius,origin.y+(cast.phase()==0?1.2:.2),
                        origin.z+Math.sin(angle)*radius,0,.025,0);
            }
        }
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        world();if(world==null || CASTS.isEmpty())return;
        var mc=Minecraft.getInstance();var pose=event.getPoseStack();var camera=event.getCamera().getPosition();
        var buffers=mc.renderBuffers().bufferSource();boolean drew=false;
        for(var cast:CASTS.values()) {
            var entity=world.getEntity(cast.entityId());
            if(!(entity instanceof LivingEntity living) || !entity.getUUID().equals(cast.uuid()) || entity.isInvisible())continue;
            float age=world.getGameTime()-cast.start()+event.getPartialTick();
            int duration=cast.phase()==0?HeroCasting.WINDUP:HeroCasting.RELEASE;
            if(age<0 || age>=duration)continue;
            Vec3 origin=cast.phase()==0?living.getPosition(event.getPartialTick()):new Vec3(cast.x(),cast.y(),cast.z());
            if(camera.distanceToSqr(origin)>square(HeroVisualConfig.DISTANCE.get()))continue;
            float radius=cast.phase()==0?.65F:(float)(HeroCasting.radius(cast.role())*age/duration);
            float alpha=(HeroVisualConfig.REDUCED_FLASHES.get()?.18F:.42F)*(1-age/(duration*1.3F));
            pose.pushPose();pose.translate(origin.x-camera.x,origin.y-camera.y+.12,origin.z-camera.z);
            var v=buffers.getBuffer(RenderType.lightning());
            ring(pose.last().pose(),v,radius,cast.role(),alpha);
            if(cast.phase()==0) {pose.translate(0,1.15,0);ring(pose.last().pose(),v,.35F,cast.role(),alpha*.7F);}
            pose.popPose();drew=true;
        }
        if(drew)buffers.endBatch(RenderType.lightning());
    }
    private static void ring(Matrix4f matrix,VertexConsumer vertices,float radius,int role,float alpha) {
        for(int i=0;i<32;i++) {
            double a=i*Math.PI/16,b=(i+1)*Math.PI/16;
            vertex(matrix,vertices,a,radius,role,alpha);vertex(matrix,vertices,a,Math.max(0,radius-.075F),role,alpha);
            vertex(matrix,vertices,b,Math.max(0,radius-.075F),role,alpha);vertex(matrix,vertices,b,radius,role,alpha);
        }
    }
    private static void vertex(Matrix4f m,VertexConsumer v,double angle,float radius,int role,float alpha) {
        v.vertex(m,(float)Math.cos(angle)*radius,0,(float)Math.sin(angle)*radius)
                .color(role==27?1F:.2F,role==27?.22F:.8F,role==27?.08F:1F,alpha).endVertex();
    }
    private static double square(double n) {return n*n;}
}
