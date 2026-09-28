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
                    packet.role()==27?SoundEvents.BLAZE_SHOOT:packet.role()==22?SoundEvents.TRIDENT_THUNDER:SoundEvents.CONDUIT_ATTACK_TARGET;
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
                world.addParticle(cast.role()==27?ParticleTypes.FLAME:cast.role()==22?ParticleTypes.ELECTRIC_SPARK:ParticleTypes.BUBBLE_POP,
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
            float alpha=(HeroVisualConfig.REDUCED_FLASHES.get()?.18F:.42F)*(1-age/(duration*1.3F));
            pose.pushPose();pose.translate(origin.x-camera.x,origin.y-camera.y+.12,origin.z-camera.z);
            var v=buffers.getBuffer(RenderType.lightning());
            Matrix4f matrix=pose.last().pose();
            HeroCastShape.emit(cast.role(),cast.phase(),age,(x1,y1,z1,x2,y2,z2) ->
                    ribbon(matrix,v,x1,y1,z1,x2,y2,z2,cast.role(),alpha));
            pose.popPose();drew=true;
        }
        if(drew)buffers.endBatch(RenderType.lightning());
    }
    private static void ribbon(Matrix4f matrix,VertexConsumer v,double x1,double y1,double z1,
                               double x2,double y2,double z2,int role,float alpha) {
        // Crossed strips keep the silhouette visible from above and at ground level.
        double width=role==27?.13:.055;
        point(matrix,v,x1-width,y1,z1,role,alpha);point(matrix,v,x1+width,y1,z1,role,alpha);
        point(matrix,v,x2+width,y2,z2,role,alpha);point(matrix,v,x2-width,y2,z2,role,alpha);
        point(matrix,v,x1,y1-width,z1-width,role,alpha);point(matrix,v,x1,y1+width,z1+width,role,alpha);
        point(matrix,v,x2,y2+width,z2+width,role,alpha);point(matrix,v,x2,y2-width,z2-width,role,alpha);
    }
    private static void point(Matrix4f m,VertexConsumer v,double x,double y,double z,int role,float alpha) {
        v.vertex(m,(float)x,(float)y,(float)z)
                .color(role==27?1F:role==22?.6F:.12F,role==27?.3F:role==22?.75F:.9F,role==27?.06F:1F,alpha).endVertex();
    }
    private static double square(double n) {return n*n;}
}
