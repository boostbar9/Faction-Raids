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
        var full=plan(level,raid,base,allowed,false);
        return full.isPresent()?full:plan(level,raid,base,allowed,true);
    }
    private static Optional<Plan> plan(ServerLevel level,RaidState raid,BlockPos base,Predicate<BlockPos> allowed,boolean narrow) {
        if(raid.campPos==null)return Optional.empty();
        Direction front=CampPerimeter.mainGateSide(raid);
        BlockPos start=raid.campPos.relative(front,13);
        Map<Long,Optional<BlockPos>> ground=new HashMap<>();
        // Try the avenue-facing stair first, then the other three sides.
        for(Direction entrance:List.of(front,front.getClockWise(),front.getCounterClockWise(),front.getOpposite())) {
            BlockPos end=base.relative(entrance,3);
            var last=feet(level,raid,end,base,allowed,ground);
            if(last.isEmpty() || last.get().getY()!=base.getY())continue;
            Map<BlockPos,BlockPos> previous=new LinkedHashMap<>();
            ArrayDeque<BlockPos> queue=new ArrayDeque<>();
            if (!narrow) {
                var first=feet(level,raid,start,base,allowed,ground);
                if(first.isPresent()){previous.put(first.get(),first.get());queue.add(first.get());}
            } else {
                // Existing diagonal camps can have an offset three-wide starter
                // gate. A one-wide walkable route is sufficient to capture a core;
                // do not require an unbuilt outer avenue to exist first.
                for(int lateral=-8;lateral<=8;lateral++) {
                    var gate=raid.campPos.relative(front,9).relative(front.getClockWise(),lateral);
                    var inside=feet(level,raid,gate,base,allowed,ground);
                    var outside=feet(level,raid,gate.relative(front),base,allowed,ground);
                    if(inside.isPresent() && outside.isPresent() && Math.abs(inside.get().getY()-outside.get().getY())<=1) {
                        previous.put(inside.get(),inside.get());queue.add(inside.get());
                    }
                }
            }
            while(!queue.isEmpty() && previous.size()<=512) {
                BlockPos current=queue.remove();
                if(!wide(level,raid,current,base,end,allowed,ground,narrow?0:1))continue;
                if(current.equals(last.get())) {
                    Map<Long,Integer> reserved=new LinkedHashMap<>();
                    for(BlockPos p=current;;p=previous.get(p)) {
                        int width=narrow && !p.equals(end)?0:1;
                        for(int x=-width;x<=width;x++)for(int z=-width;z<=width;z++) {
                            BlockPos column=p.offset(x,0,z);
                            if(insideKeep(column,base))continue;
                            BlockPos standing=feet(level,raid,column,base,allowed,ground).orElseThrow();
                            reserved.put(columnKey(standing),standing.getY());
                        }
                        if(p.equals(previous.get(p)))break;
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
                                Predicate<BlockPos> allowed,Map<Long,Optional<BlockPos>> ground,int width) {
        if(Math.abs(p.getX()-base.getX())<=3 && Math.abs(p.getZ()-base.getZ())<=3 && !p.equals(end))return false;
        if(p.equals(end))width=1; // all three stairs still need full clearance
        for(int x=-width;x<=width;x++)for(int z=-width;z<=width;z++) {
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
                    var road=raid.warGate.getCompound("RoadBlocks");
                    boolean roadFloor=road.contains(Long.toString(cell.asLong()))
                            && com.devfarinsky.siegeoverhaul.camp.CampTerrain.matchesPlaced(state,road.getString(Long.toString(cell.asLong())));
                    if(raid.campBlocks.containsKey(cell.asLong()) && !roadFloor
                            && !com.devfarinsky.siegeoverhaul.camp.CampRoad.soil(state))return Optional.empty();
                    if(!state.isFaceSturdy(level,cell,Direction.UP) || state.is(Blocks.MAGMA_BLOCK)
                            || state.is(Blocks.CACTUS))return Optional.empty();
                } else if(!(state.isAir() || com.devfarinsky.siegeoverhaul.camp.CampVegetation.plant(state))
                        || !state.getCollisionShape(level,cell).isEmpty() || state.is(Blocks.WITHER_ROSE)
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
