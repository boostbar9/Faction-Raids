package com.devfarinsky.siegeoverhaul.nativecompat;

import com.talhanation.workers.entities.BuilderEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/** First bounded access adapter: loaded, flat one-step escape. Stepped ascent remains a separate proof. */
final class EarthworksStandingAccess {
    private EarthworksStandingAccess() {}
    static String problem(ServerLevel level, BuilderEntity worker, EarthworksBuildArea area, EarthworksJobLedger.Job job) {
        var journal=job.read().journal();if(journal.nextStep()>=job.manifest.steps().size())return "No local grading step remains";
        var step=job.manifest.steps().get(journal.nextStep());BlockPos target=BlockPos.of(step.pos());
        BlockPos standing=worker.blockPosition();
        if(!safe(level,worker,standing,target))return "Waiting: native builder needs loaded safe flat footing";
        String claims=NativeConstructionPolicy.problem(level,worker,area,job.manifest.header().owner(),"team:"+job.manifest.header().faction(),
                job.core,Set.of(standing.below(),standing,standing.above()));if(claims!=null)return claims;
        // Clip only after the whole short segment's chunks were checked; no read may cause loading.
        Vec3 eyes=worker.getEyePosition(),end=target.getCenter();
        if(eyes.distanceToSqr(end)>9)return "Waiting: exact grading target is out of native reach";
        for(int i=0;i<=12;i++)if(!loaded(level,BlockPos.containing(eyes.lerp(end,i/12D))))return "Paused: sightline crosses unloaded terrain";
        var hit=level.clip(new ClipContext(eyes,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,worker));
        if(hit.getType()!=HitResult.Type.MISS&&!hit.getBlockPos().equals(target))return "Paused: the exact first grading target is hidden";
        for(Direction direction:Direction.Plane.HORIZONTAL){
            BlockPos escape=standing.relative(direction);
            if(!safe(level,worker,escape,target))continue;
            if(NativeConstructionPolicy.problem(level,worker,area,job.manifest.header().owner(),"team:"+job.manifest.header().faction(),
                    job.core,Set.of(escape.below(),escape,escape.above()))!=null)continue;
            AABB from=worker.getBoundingBox(),to=body(worker,escape);
            // Same-Y adjacent full sweep proves a connected exit, not a disconnected nearby candidate.
            AABB sweep=from.minmax(to);
            if(sweep.intersects(new AABB(target))||!loaded(level,sweep)||!level.noCollision(worker,sweep))continue;
            return null;
        }
        return "Paused: no loaded claimed post-mutation escape is proved";
    }
    private static boolean safe(ServerLevel level,BuilderEntity worker,BlockPos feet,BlockPos target){
        AABB body=body(worker,feet);
        if(feet.below().equals(target)||body.intersects(new AABB(target))||!loaded(level,body)||!loaded(level,feet.below()))return false;
        return level.getBlockState(feet.below()).isFaceSturdy(level,feet.below(),Direction.UP)
                && level.getFluidState(feet).isEmpty()&&level.getFluidState(feet.above()).isEmpty()&&level.noCollision(worker,body);
    }
    private static AABB body(BuilderEntity worker,BlockPos feet){
        return worker.getBoundingBox().move(feet.getX()+.5-worker.getX(),feet.getY()-worker.getY(),feet.getZ()+.5-worker.getZ());
    }
    private static boolean loaded(ServerLevel level,AABB box){
        for(BlockPos pos:BlockPos.betweenClosed((int)Math.floor(box.minX),(int)Math.floor(box.minY)-1,(int)Math.floor(box.minZ),
                (int)Math.floor(box.maxX),(int)Math.ceil(box.maxY),(int)Math.floor(box.maxZ)))if(!loaded(level,pos))return false;return true;
    }
    private static boolean loaded(ServerLevel level,BlockPos pos){return pos.getY()>=level.getMinBuildHeight()&&pos.getY()<level.getMaxBuildHeight()
            &&level.getWorldBorder().isWithinBounds(pos)&&level.hasChunkAt(pos);}
}
