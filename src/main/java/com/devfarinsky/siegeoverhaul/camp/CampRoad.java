package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.siege.BlockRestoration;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** Three-wide graded road. All cuts are validated and recorded before native builders pave it. */
public final class CampRoad {
    private CampRoad() {}
    public record Plan(Map<Long,String> blocks,Map<Long,CompoundTag> before,Set<Long> clearance) {}
    public static int floorHeight(int campFeet,int gateFloor,int index,int length) {
        return campFeet-1+(int)Math.round((gateFloor-(campFeet-1))*(double)index/Math.max(1,length));
    }
    public static boolean soil(BlockState state) {
        return state.is(Blocks.DIRT)||state.is(Blocks.GRASS_BLOCK)||state.is(Blocks.COARSE_DIRT)
                ||state.is(Blocks.ROOTED_DIRT)||state.is(Blocks.PODZOL)||state.is(Blocks.MYCELIUM);
    }
    public static Optional<Plan> plan(ServerLevel level,RaidSavedData.RaidState raid,BlockPos gate,Direction front) {
        if(raid.campPos==null)return Optional.empty();
        int distance=Math.abs(gate.getX()-raid.campPos.getX())+Math.abs(gate.getZ()-raid.campPos.getZ());
        int start=8,end=distance-3,length=end-start;
        // Leave at least one flat block between rises. Refuse a gate with no safe ramp.
        if(length<1 || Math.abs(gate.getY()-(raid.campPos.getY()-1))*2>length)return Optional.empty();
        Map<Long,String> blocks=new LinkedHashMap<>();Map<Long,CompoundTag> before=new LinkedHashMap<>();Set<Long> clearance=new LinkedHashSet<>();
        Direction side=front.getClockWise();
        for(int i=0;i<=length;i++)for(int width=-1;width<=1;width++) {
            BlockPos column=raid.campPos.relative(front,start+i).relative(side,width);
            int floor=floorHeight(raid.campPos.getY(),gate.getY(),i,length);
            if(!level.hasChunkAt(column)||!level.getWorldBorder().isWithinBounds(column))return Optional.empty();
            int ground=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,column.getX(),column.getZ())-1;
            // A camp palisade is removable only if it belongs to this raid's restoration ledger.
            while(ground>floor && ground-floor<=5 && ownedCampBlock(level,raid,column.atY(ground)))ground--;
            if(Math.abs(ground-floor)>3 || floor<level.getMinBuildHeight() || floor+4>=level.getMaxBuildHeight())return Optional.empty();
            for(int y=Math.min(ground,floor);y<=Math.max(ground+1,floor+4);y++) {
                BlockPos pos=column.atY(y);BlockState state=level.getBlockState(pos);
                if(state.hasBlockEntity()||!state.getFluidState().isEmpty())return Optional.empty();
                if(!state.isAir() && !soil(state) && !CampVegetation.replaceable(state) && !ownedCampBlock(level,raid,pos))return Optional.empty();
                if(y<floor && !state.isAir() && !soil(state))return Optional.empty();
                if(!level.getEntitiesOfClass(LivingEntity.class,new AABB(pos),LivingEntity::isAlive).isEmpty())return Optional.empty();
                if(y<=floor)blocks.put(pos.asLong(),"minecraft:stone_bricks");
                else clearance.add(pos.asLong());
                before.put(pos.asLong(),BlockRestoration.serializeState(level,pos,state));
            }
        }
        if(blocks.size()>192 || before.size()>512)return Optional.empty();
        return Optional.of(new Plan(blocks,before,clearance));
    }
    private static boolean ownedCampBlock(ServerLevel level,RaidSavedData.RaidState raid,BlockPos pos) {
        var record=raid.campBlocks.get(pos.asLong());if(record==null)return false;
        var current=level.getBlockState(pos);
        return !current.hasBlockEntity() && record.getString("Placed").equals(String.valueOf(net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(current.getBlock())))
                && (current.getBlock() instanceof FenceBlock || current.is(Blocks.SPRUCE_LOG));
    }
    public static void record(RaidSavedData.RaidState raid,Plan plan) {
        CompoundTag before=new CompoundTag(),blocks=new CompoundTag();
        plan.before.forEach((pos,tag)->before.put(Long.toString(pos),tag));
        plan.blocks.forEach((pos,id)->blocks.putString(Long.toString(pos),id));
        raid.warGate.putBoolean("RoadPending",false);raid.warGate.put("RoadBefore",before);raid.warGate.put("RoadBlocks",blocks);raid.warGate.putBoolean("RoadPrepared",false);
        var all=raid.warGate.getCompound("Blocks");plan.blocks.forEach((pos,id)->all.putString(Long.toString(pos),id));raid.warGate.put("Blocks",all);
        plan.clearance.forEach(pos->{raid.pendingCampBlocks.remove(pos);raid.pendingFortifications.remove(pos);});
        raid.pendingCampBlocks.putAll(plan.blocks);
    }
    public static boolean prepare(ServerLevel level,RaidSavedData.RaidState raid) {
        if(!raid.warGate.contains("RoadBefore") || raid.warGate.getBoolean("RoadPrepared"))return true;
        CompoundTag before=raid.warGate.getCompound("RoadBefore"),blocks=raid.warGate.getCompound("RoadBlocks");
        for(String key:before.getAllKeys()) {
            BlockPos pos=BlockPos.of(Long.parseLong(key));
            if(!level.hasChunkAt(pos)||!level.getBlockState(pos).equals(BlockRestoration.deserializeState(before.getCompound(key)))
                    ||!level.getEntitiesOfClass(LivingEntity.class,new AABB(pos),LivingEntity::isAlive).isEmpty())return false;
        }
        // Capture the entire footprint first, including both halves of any tall plants.
        for(String key:before.getAllKeys())raid.recordCampBlock(Long.parseLong(key),blocks.contains(key)?blocks.getString(key):"minecraft:air",before.getCompound(key));
        var ordered=new ArrayList<>(before.getAllKeys());ordered.sort(Comparator.comparingInt((String key)->BlockPos.of(Long.parseLong(key)).getY()).reversed());
        for(String key:ordered) {
            BlockPos pos=BlockPos.of(Long.parseLong(key));
            if(!level.getBlockState(pos).isAir())level.setBlock(pos,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL|Block.UPDATE_SUPPRESS_DROPS);
        }
        raid.warGate.putBoolean("RoadPrepared",true);RaidSavedData.get(level.getServer()).setDirty();return true;
    }
    /** Retrofit saved gates once the current native job finishes; never refill an existing job's barrel. */
    public static void retrofit(ServerLevel level,RaidSavedData.RaidState raid) {
        if(raid.warGate.isEmpty() || raid.warGate.contains("RoadBlocks") || !raid.pendingCampBlocks.isEmpty() || NativeCampConstruction.active(raid))return;
        if(raid.campWorkers.stream().noneMatch(id->level.getEntity(id) instanceof LivingEntity worker && worker.isAlive()))return;
        raid.warGate.putBoolean("RoadPending",true);
        var plan=plan(level,raid,WarGate.center(raid),WarGate.facing(raid));
        if(plan.isEmpty()){raid.constructionPauseReason="Existing gate needs a clear, gently sloped road to camp";return;}
        record(raid,plan.get());NativeCampConstruction.start(level,raid);RaidSavedData.get(level.getServer()).setDirty();
    }
}
