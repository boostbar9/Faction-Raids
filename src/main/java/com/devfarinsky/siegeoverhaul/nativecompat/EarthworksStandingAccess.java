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
        AABB actual=worker.getBoundingBox();
        if(!worker.onGround()||worker.isPassenger()||worker.isLeashed()||worker.isInWaterOrBubble()||worker.isInLava()
                ||!loaded(level,actual)||!loaded(level,standing.below())||!clearSafePrism(level,actual)||!level.noCollision(worker,actual)
                ||actual.intersects(new AABB(target))||standing.below().equals(target)
                ||!level.getBlockState(standing.below()).isFaceSturdy(level,standing.below(),Direction.UP))
            return "Paused: actual current worker body or footing is not safe for grading";
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
            if(sweep.intersects(new AABB(target))||!loaded(level,sweep)||!clearSafePrism(level,sweep)||!level.noCollision(worker,sweep))continue;
            return null;
        }
        return "Paused: no loaded claimed post-mutation escape is proved";
    }
    private static boolean safe(ServerLevel level,BuilderEntity worker,BlockPos feet,BlockPos target){
        AABB body=body(worker,feet);
        if(feet.below().equals(target)||body.intersects(new AABB(target))||!loaded(level,body)||!loaded(level,feet.below()))return false;
        return clearSafePrism(level,body)&&level.getBlockState(feet.below()).isFaceSturdy(level,feet.below(),Direction.UP)
                && level.getFluidState(feet).isEmpty()&&level.getFluidState(feet.above()).isEmpty()&&level.noCollision(worker,body);
    }
    static boolean safeFloor(net.minecraft.world.level.block.state.BlockState state) {
        // Fixed full vanilla support shapes only; damaging, falling, callback-bearing and unaudited modded floors are outside this first adapter.
        return state.is(net.minecraft.world.level.block.Blocks.DIRT)||state.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)
                ||state.is(net.minecraft.world.level.block.Blocks.STONE)||state.is(net.minecraft.world.level.block.Blocks.COBBLESTONE)
                ||state.is(net.minecraft.world.level.block.Blocks.STONE_BRICKS)||state.is(net.minecraft.world.level.block.Blocks.OAK_PLANKS)
                ||state.is(net.minecraft.world.level.block.Blocks.BEDROCK);
    }
    static boolean clearSafePrism(ServerLevel level,AABB box) {
        if(!loaded(level,box))return false;
        int minX=(int)Math.floor(box.minX),maxX=(int)Math.floor(Math.nextDown(box.maxX));
        int minZ=(int)Math.floor(box.minZ),maxZ=(int)Math.floor(Math.nextDown(box.maxZ));
        int feet=(int)Math.floor(box.minY),top=(int)Math.ceil(box.maxY)-1;
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){
            BlockPos floor=new BlockPos(x,feet-1,z);var state=level.getBlockState(floor);
            if(!safeFloor(state)||!state.isFaceSturdy(level,floor,Direction.UP)||!level.getFluidState(floor).isEmpty()||level.getBlockEntity(floor)!=null)return false;
            for(int y=feet;y<=top;y++){
                var pos=new BlockPos(x,y,z);
                if(!level.getBlockState(pos).isAir()||!level.getFluidState(pos).isEmpty()||level.getBlockEntity(pos)!=null)return false;
            }
        }
        return true;
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
