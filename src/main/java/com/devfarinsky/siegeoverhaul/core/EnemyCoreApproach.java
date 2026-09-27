package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.RaidSavedData.RaidState;
import com.devfarinsky.siegeoverhaul.camp.CampPerimeter;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.*;
import java.util.function.Predicate;

/** Bounded three-wide walking route from the main avenue to a new sanctuary. */
public final class EnemyCoreApproach {
    private static final String SAVED = "EnemyCoreApproach";
    private EnemyCoreApproach() {}
    record Plan(Map<BlockPos,BlockState> steps, Map<Long,Integer> feet) {
        Plan { steps=Map.copyOf(steps);feet=Map.copyOf(feet); }
    }
    static Optional<Plan> plan(ServerLevel level, RaidState raid, BlockPos base, Predicate<BlockPos> allowed) {
        if(raid.campPos==null)return Optional.empty();
        Direction front=CampPerimeter.mainGateSide(raid);
        BlockPos start=raid.campPos.relative(front,13);
        Map<Long,Optional<BlockPos>> ground=new HashMap<>();
        // Try the avenue-facing stair first, then the other three sides.
        for(Direction entrance:List.of(front,front.getClockWise(),front.getCounterClockWise(),front.getOpposite())) {
            BlockPos end=base.relative(entrance,3);
            var first=feet(level,raid,start,base,allowed,ground);
            var last=feet(level,raid,end,base,allowed,ground);
            if(first.isEmpty() || last.isEmpty() || last.get().getY()!=base.getY())continue;
            Map<BlockPos,BlockPos> previous=new LinkedHashMap<>();
            ArrayDeque<BlockPos> queue=new ArrayDeque<>();
            previous.put(first.get(),first.get());queue.add(first.get());
            while(!queue.isEmpty() && previous.size()<=512) {
                BlockPos current=queue.remove();
                if(!wide(level,raid,current,base,end,allowed,ground))continue;
                if(current.equals(last.get())) {
                    Map<Long,Integer> reserved=new LinkedHashMap<>();
                    for(BlockPos p=current;;p=previous.get(p)) {
                        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++) {
                            BlockPos column=p.offset(x,0,z);
                            if(insideKeep(column,base))continue;
                            BlockPos standing=feet(level,raid,column,base,allowed,ground).orElseThrow();
                            reserved.put(columnKey(standing),standing.getY());
                        }
                        if(p.equals(first.get()))break;
                    }
                    Map<BlockPos,BlockState> steps=new LinkedHashMap<>();
                    for(int side=-1;side<=1;side++) {
                        BlockPos step=end.relative(entrance.getClockWise(),side);
                        steps.put(step,Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,entrance.getOpposite()));
                    }
                    return Optional.of(new Plan(steps,reserved));
                }
                for(Direction direction:Direction.Plane.HORIZONTAL) {
                    var next=feet(level,raid,current.relative(direction),base,allowed,ground);
                    if(next.isPresent() && Math.abs(next.get().getY()-current.getY())<=1 && !previous.containsKey(next.get())) {
                        previous.put(next.get(),current);queue.add(next.get());
                    }
                }
            }
        }
        return Optional.empty();
    }
    private static boolean wide(ServerLevel level,RaidState raid,BlockPos p,BlockPos base,BlockPos end,
                                Predicate<BlockPos> allowed,Map<Long,Optional<BlockPos>> ground) {
        if(Math.abs(p.getX()-base.getX())<=3 && Math.abs(p.getZ()-base.getZ())<=3 && !p.equals(end))return false;
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++) {
            BlockPos column=p.offset(x,0,z);
            if(p.equals(end) && insideKeep(column,base))continue;
            var standing=feet(level,raid,column,base,allowed,ground);
            if(standing.isEmpty() || Math.abs(standing.get().getY()-p.getY())>1)return false;
            // The stair row must sit on one level, with dry space for every step.
            if(p.equals(end) && column.getX()*endDirectionX(base,end)+column.getZ()*endDirectionZ(base,end)
                    ==end.getX()*endDirectionX(base,end)+end.getZ()*endDirectionZ(base,end)
                    && standing.get().getY()!=base.getY())return false;
        }
        return true;
    }
    private static int endDirectionX(BlockPos base,BlockPos end){return Integer.signum(end.getX()-base.getX());}
    private static int endDirectionZ(BlockPos base,BlockPos end){return Integer.signum(end.getZ()-base.getZ());}
    private static boolean insideKeep(BlockPos p,BlockPos base) {
        return Math.abs(p.getX()-base.getX())<=2 && Math.abs(p.getZ()-base.getZ())<=2;
    }
    private static Optional<BlockPos> feet(ServerLevel level,RaidState raid,BlockPos column,BlockPos base,
                                          Predicate<BlockPos> allowed,Map<Long,Optional<BlockPos>> cache) {
        long key=columnKey(column);
        return cache.computeIfAbsent(key,ignored->{
            int dx=column.getX()-raid.campPos.getX(),dz=column.getZ()-raid.campPos.getZ();
            if(Math.abs(dx)>14 || Math.abs(dz)>14 || !level.hasChunkAt(column))return Optional.empty();
            // Do not route through a future fortified wall outside its five-wide main gate.
            Direction front=CampPerimeter.mainGateSide(raid),side=front.getClockWise();
            int depth=dx*front.getStepX()+dz*front.getStepZ(),lateral=dx*side.getStepX()+dz*side.getStepZ();
            if((Math.abs(dx)>=12 || Math.abs(dz)>=12) && !(depth>=12 && Math.abs(lateral)<=2))return Optional.empty();
            int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,column.getX(),column.getZ());
            if(Math.abs(y-base.getY())>2 || y-1<level.getMinBuildHeight() || y+2>=level.getMaxBuildHeight())return Optional.empty();
            BlockPos p=new BlockPos(column.getX(),y,column.getZ());
            for(int h=-1;h<=2;h++) {
                BlockPos cell=p.above(h);
                if(!level.hasChunkAt(cell)||!level.getWorldBorder().isWithinBounds(cell)||!allowed.test(cell))return Optional.empty();
                BlockState state=level.getBlockState(cell);
                if(state.is(Blocks.WATER) || state.is(Blocks.LAVA)
                        || !state.getFluidState().isEmpty() || state.hasBlockEntity())return Optional.empty();
                if(h==-1) {
                    if(!state.isFaceSturdy(level,cell,Direction.UP) || state.is(Blocks.MAGMA_BLOCK)
                            || state.is(Blocks.CACTUS))return Optional.empty();
                } else if(!state.getCollisionShape(level,cell).isEmpty() || state.is(Blocks.FIRE)
                        || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.SWEET_BERRY_BUSH)
                        || raid.pendingCampBlocks.containsKey(cell.asLong()) || raid.pendingFortifications.containsKey(cell.asLong()))return Optional.empty();
            }
            return Optional.of(p);
        });
    }
    private static long columnKey(BlockPos p){return new BlockPos(p.getX(),0,p.getZ()).asLong();}
    static void save(RaidState raid,Plan plan) {
        CompoundTag columns=new CompoundTag();
        plan.feet.forEach((key,y)->columns.putInt(Long.toString(key),y));
        raid.campaign.put(SAVED,columns);
    }
    /** Construction must leave the walking body space clear; paving below it remains possible. */
    public static boolean reserved(RaidState raid,BlockPos pos) {
        CompoundTag columns=raid.campaign.getCompound(SAVED);String key=Long.toString(columnKey(pos));
        return columns.contains(key) && pos.getY()>=columns.getInt(key) && pos.getY()<=columns.getInt(key)+2;
    }
    /** Protect the supporting floor too, without reserving it against safe road paving. */
    public static boolean protectedCell(RaidState raid,BlockPos pos) {
        return reserved(raid,pos)||reserved(raid,pos.above());
    }
}
