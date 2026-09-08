package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.*;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** A native-builder blueprint and a bounded reinforcement pad, not a dimension portal. */
@Mod.EventBusSubscriber(modid=SiegeOverhaul.MOD_ID)
public final class WarGate {
    private WarGate() {}
    public static BlockPos center(RaidSavedData.RaidState raid) { return BlockPos.of(raid.warGate.getLong("Center")); }
    public static Direction facing(RaidSavedData.RaidState raid) { return Direction.from2DDataValue(raid.warGate.getInt("Facing")); }
    public static Map<Long,String> blueprint(BlockPos center,Direction front) {
        Map<Long,String> blocks=new LinkedHashMap<>();Direction side=front.getClockWise();
        for(int x=-3;x<=3;x++)for(int z=-2;z<=2;z++)blocks.put(center.relative(side,x).relative(front,z).asLong(),"minecraft:polished_blackstone_bricks");
        for(int y=1;y<=5;y++)for(int x:new int[]{-2,2})blocks.put(center.relative(side,x).above(y).asLong(),y%2==0?"minecraft:crying_obsidian":"minecraft:obsidian");
        for(int x=-3;x<=3;x++)blocks.put(center.relative(side,x).above(6).asLong(),Math.abs(x)<=1?"minecraft:crying_obsidian":"minecraft:polished_blackstone_bricks");
        for(int x:new int[]{-3,3}) {
            for(int y=1;y<=3;y++)blocks.put(center.relative(side,x).above(y).asLong(),"minecraft:polished_blackstone_bricks");
            blocks.put(center.relative(side,x).above(4).asLong(),"minecraft:amethyst_block");
        }
        blocks.put(center.above(7).asLong(),"minecraft:lodestone");return blocks;
    }
    public static boolean plan(ServerLevel level,RaidSavedData.RaidState raid,BlockPos objective) {
        if(!raid.warGate.isEmpty() || raid.campPos==null)return false;
        Vec3 d=Vec3.atCenterOf(objective).subtract(Vec3.atCenterOf(raid.campPos));
        Direction preferred=Math.abs(d.x)>=Math.abs(d.z)?(d.x>=0?Direction.EAST:Direction.WEST):(d.z>=0?Direction.SOUTH:Direction.NORTH);
        for(Direction front:new Direction[]{preferred,preferred.getClockWise(),preferred.getCounterClockWise(),preferred.getOpposite()})
        for(int distance:new int[]{14,17,20,24}) {
            BlockPos c=raid.campPos.relative(front,distance);int y=Integer.MIN_VALUE;
            boolean valid=true;
            for(int x=-3;x<=3;x++)for(int z=-2;z<=2;z++) {
                BlockPos p=c.relative(front.getClockWise(),x).relative(front,z);
                if(!level.hasChunkAt(p)) { valid=false;continue; }
                y=Math.max(y,level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p.getX(),p.getZ()));
            }
            if(!valid || Math.abs(y-raid.campPos.getY())>6)continue;
            c=new BlockPos(c.getX(),y,c.getZ());
            var volume=new net.minecraft.world.phys.AABB(c.offset(-3,0,-3),c.offset(4,8,4));
            if(level.getEntities((net.minecraft.world.entity.Entity)null,volume).stream().anyMatch(entity->{
                var id=ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
                return entity instanceof net.minecraft.world.entity.LivingEntity || id!=null && id.getNamespace().equals("siegeweapons");
            }))continue;
            var plan=blueprint(c,front);
            for(int x=-3;x<=3;x++)for(int z=-2;z<=2;z++) {
                BlockPos p=c.relative(front.getClockWise(),x).relative(front,z);
                int floor=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p.getX(),p.getZ());
                if(y-floor>3 || !level.getFluidState(p.atY(floor-1)).isEmpty())valid=false;
                for(int sy=floor;sy<y;sy++)plan.put(p.atY(sy).asLong(),"minecraft:polished_blackstone_bricks");
                for(int sy=y;sy<=y+7;sy++)if(!com.devfarinsky.siegeoverhaul.camp.CampVegetation.replaceable(level.getBlockState(p.atY(sy))) || !level.getFluidState(p.atY(sy)).isEmpty())valid=false;
            }
            if(!valid)continue;
            var road=CampRoad.plan(level,raid,c,front);
            if(road.isEmpty())continue;
            plan.putAll(road.get().blocks());
            if(raid.pendingCampBlocks.size()+plan.size()>512)continue;
            var combined=new HashSet<Long>(raid.pendingCampBlocks.keySet());combined.addAll(plan.keySet());
            int minX=Integer.MAX_VALUE,minZ=minX,maxX=Integer.MIN_VALUE,maxZ=maxX;
            for(long key:combined){BlockPos p=BlockPos.of(key);minX=Math.min(minX,p.getX());minZ=Math.min(minZ,p.getZ());maxX=Math.max(maxX,p.getX());maxZ=Math.max(maxZ,p.getZ());}
            if(maxX-minX>=32 || maxZ-minZ>=32)continue;
            CompoundTag tag=new CompoundTag(),cells=new CompoundTag();plan.forEach((p,id)->cells.putString(Long.toString(p),id));
            tag.putLong("Center",c.asLong());tag.putInt("Facing",front.get2DDataValue());tag.put("Blocks",cells);raid.warGate=tag;
            // Foundations must be first in the native job sequence.
            var jobs=new LinkedHashMap<Long,String>();plan.entrySet().stream().sorted(Comparator.comparingInt(e->BlockPos.of(e.getKey()).getY())).forEach(e->jobs.put(e.getKey(),e.getValue()));
            jobs.putAll(raid.pendingCampBlocks);raid.pendingCampBlocks.clear();raid.pendingCampBlocks.putAll(jobs);
            CampRoad.record(raid,road.get());
            return true;
        }
        return false;
    }
    public static boolean ready(ServerLevel level,RaidSavedData.RaidState raid) {
        if(raid.warGate.isEmpty() || raid.warGate.getBoolean("RoadPending"))return false;
        var cells=raid.warGate.getCompound("Blocks");
        for(String key:cells.getAllKeys()) {
            BlockPos p=BlockPos.of(Long.parseLong(key));
            if(!level.hasChunkAt(p) || !cells.getString(key).equals(String.valueOf(ForgeRegistries.BLOCKS.getKey(level.getBlockState(p).getBlock()))))return false;
        }
        return !cells.isEmpty();
    }
    public static BlockPos spawn(ServerLevel level,RaidSavedData.RaidState raid,Mob mob) {
        if(!ready(level,raid))return null;
        BlockPos c=center(raid);Direction front=facing(raid),side=front.getClockWise();
        for(int depth:new int[]{1,2,-1,-2})for(int lateral:new int[]{0,-1,1}) {
            BlockPos p=c.relative(front,depth).relative(side,lateral).above();
            if(!level.hasChunkAt(p) || !level.getBlockState(p.below()).isFaceSturdy(level,p.below(),Direction.UP) || !level.getFluidState(p).isEmpty())continue;
            var box=mob.getBoundingBox().move(Vec3.atBottomCenterOf(p).subtract(mob.position()));
            if(level.noCollision(mob,box) && level.getEntities(mob,box).isEmpty())return p;
        }
        return null;
    }
    public static void tick(ServerLevel level,RaidSavedData.RaidState raid,BlockPos objective) {
        if(raid.campPos==null)return;
        if(raid.warGate.isEmpty() && raid.pendingCampBlocks.isEmpty() && !NativeCampConstruction.active(raid)
                && com.devfarinsky.siegeoverhaul.compat.CampClaims.owns(level,raid) && plan(level,raid,objective)) {
            NativeCampConstruction.start(level,raid);RaidSavedData.get(level.getServer()).setDirty();
        }
        CampRoad.retrofit(level,raid);
        if(!raid.warGate.isEmpty())CampLoading.keep(level,center(raid));
        if(!ready(level,raid)) {
            raid.warGateWaitTicks=Math.min(20*60*30,raid.warGateWaitTicks+20);
            return;
        }
        raid.warGateWaitTicks=0;
        BlockPos c=center(raid);Direction side=facing(raid).getClockWise();
        for(int i=0;i<14;i++) {
            double angle=level.getGameTime()*.04+i*Math.PI/7;
            level.sendParticles(ParticleTypes.PORTAL,c.getX()+.5+side.getStepX()*Math.cos(angle)*1.1,c.getY()+3+Math.sin(angle)*1.8,
                    c.getZ()+.5+side.getStepZ()*Math.cos(angle)*1.1,2,.08,.08,.08,.05);
        }
    }
    public static String status(ServerLevel level,RaidSavedData.RaidState raid) {
        if(raid.warGate.isEmpty())return "Finding a clear War Gate site";
        var cells=raid.warGate.getCompound("Blocks");int missing=0;
        for(String key:cells.getAllKeys()) {
            BlockPos p=BlockPos.of(Long.parseLong(key));
            if(!level.hasChunkAt(p) || !cells.getString(key).equals(String.valueOf(ForgeRegistries.BLOCKS.getKey(level.getBlockState(p).getBlock()))))missing++;
        }
        return "War Gate: "+missing+" blocks unfinished"+(raid.constructionPauseReason.isEmpty()?"":" — "+raid.constructionPauseReason);
    }
    private static boolean protectedAt(ServerLevel level,BlockPos p) {
        for(var raid:RaidSavedData.get(level.getServer()).raids.values()) {
            if(raid.warGate.isEmpty() || !level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD))continue;
            var road=raid.warGate.getCompound("RoadBlocks");
            for(String key:road.getAllKeys()) {
                BlockPos floor=BlockPos.of(Long.parseLong(key));
                if(p.getX()==floor.getX() && p.getZ()==floor.getZ() && p.getY()>=floor.getY() && p.getY()<=floor.getY()+4)return true;
            }
            BlockPos c=center(raid);
            if(Math.abs(p.getX()-c.getX())<=3 && Math.abs(p.getZ()-c.getZ())<=3 && p.getY()>=c.getY()-2 && p.getY()<=c.getY()+7)return true;
        }
        return false;
    }
    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent event) {
        if(event.getLevel() instanceof ServerLevel level && protectedAt(level,event.getPos()))event.setCanceled(true);
    }
    @SubscribeEvent public static void placeBlock(BlockEvent.EntityPlaceEvent event) {
        if(event.getEntity() instanceof net.minecraft.world.entity.player.Player && event.getLevel() instanceof ServerLevel level && protectedAt(level,event.getPos()))event.setCanceled(true);
    }
    @SubscribeEvent public static void explosion(ExplosionEvent.Detonate event) {
        if(event.getLevel() instanceof ServerLevel level)event.getAffectedBlocks().removeIf(p->protectedAt(level,p));
    }
    @SubscribeEvent public static void piston(net.minecraftforge.event.level.PistonEvent.Pre event) {
        if(event.getLevel() instanceof ServerLevel level)for(int i=0;i<=12;i++)
            if(protectedAt(level,event.getPos().relative(event.getDirection(),i))) { event.setCanceled(true);break; }
    }
    /** Unconditional gate cleanup, even when ordinary camp cleanup is disabled. */
    public static void cleanup(ServerLevel level,RaidSavedData.RaidState raid) {
        if(!raid.warGate.isEmpty())CampLoading.release(level,center(raid));
        var cells=raid.warGate.getCompound("Blocks");var keys=new ArrayList<>(cells.getAllKeys());
        keys.sort(Comparator.comparingInt((String k)->BlockPos.of(Long.parseLong(k)).getY()).reversed());
        for(String key:keys) {
            long packed=Long.parseLong(key);BlockPos p=BlockPos.of(packed);
            var record=raid.campBlocks.get(packed);
            if(record==null)continue;
            if(cells.getString(key).equals(String.valueOf(ForgeRegistries.BLOCKS.getKey(level.getBlockState(p).getBlock()))))
                level.setBlock(p,Blocks.AIR.defaultBlockState(),3);
        }
        Collections.reverse(keys);
        for(String key:keys) {
            long packed=Long.parseLong(key);var record=raid.campBlocks.remove(packed);
            if(record!=null && !record.getCompound("Original").isEmpty())
                com.devfarinsky.siegeoverhaul.siege.BlockRestoration.applyTo(level,BlockPos.of(packed),record.getCompound("Original"));
        }
    }
}
