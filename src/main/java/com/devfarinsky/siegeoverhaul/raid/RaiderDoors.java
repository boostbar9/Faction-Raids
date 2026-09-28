package com.devfarinsky.siegeoverhaul.raid;

import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.compat.ClaimBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.function.Predicate;

/** Door interaction for siege soldiers, including Recruits' non-vanilla ground navigator. */
public final class RaiderDoors extends Goal {
    private final Mob mob;
    private long nextCheck;
    private BlockPos door;
    private RaiderDoors(Mob mob) { this.mob=mob; }

    public static void install(Mob mob) {
        var navigation=mob.getNavigation();
        if (!supports(navigation)) return;
        var evaluator=navigation.getNodeEvaluator();
        if(evaluator==null)return;
        evaluator.setCanOpenDoors(true);
        evaluator.setCanPassDoors(true);
        for(var wrapped:new ArrayList<>(mob.goalSelector.getAvailableGoals())) {
            var goal=wrapped.getGoal();
            if(goal instanceof RaiderDoors || goal instanceof OpenDoorGoal
                    || inherits(goal.getClass(),"com.talhanation.recruits.entities.ai.navigation.RecruitsOpenDoorGoal"))
                mob.goalSelector.removeGoal(goal);
        }
        // No MOVE/LOOK flags: opening an entrance must not cancel combat or marching.
        mob.goalSelector.addGoal(1,new RaiderDoors(mob));
    }
    static boolean supports(PathNavigation navigation) {
        return navigation instanceof GroundPathNavigation || navigation!=null && inherits(navigation.getClass(),
                "com.talhanation.recruits.pathfinding.AsyncGroundPathNavigation");
    }
    private static boolean inherits(Class<?> type,String name) {
        for(;type!=null;type=type.getSuperclass())if(type.getName().equals(name))return true;
        return false;
    }
    @Override public boolean canUse() {
        door=null;
        if(!(mob.level() instanceof ServerLevel level) || mob.isPassenger() || mob.isNoAi()
                || !mob.isAlive())return false;
        long now=level.getGameTime();
        if(now<nextCheck && nextCheck<=now+10)return false;
        nextCheck=now+5;
        String team=mob.getPersistentData().getString(ModConstants.Tags.RAID_TEAM);
        if(team.isBlank())return false;
        var data=RaidSavedData.get(level.getServer());
        if(!data.raids.containsKey(team))return false;
        var anchor=data.anchors.get(team);
        door=find(level,mob,mob.getNavigation().getPath(),pos -> !RaidConfig.RESPECT_FOREIGN_CLAIMS.get()
                || !ClaimBridge.isForeignClaim(level,pos,anchor));
        return door!=null;
    }
    static BlockPos find(ServerLevel level,Mob mob,Path path,Predicate<BlockPos> allowed) {
        if(path==null || path.isDone())return null;
        int first=Math.max(0,path.getNextNodeIndex()-1);
        int end=Math.min(path.getNodeCount(),path.getNextNodeIndex()+3);
        for(int i=first;i<end;i++) {
            var node=path.getNode(i);
            for(int y=0;y<=1;y++) {
                BlockPos candidate=new BlockPos(node.x,node.y+y,node.z);
                BlockPos lower=closedWoodenDoor(level,mob,candidate);
                if(lower!=null && allowed.test(lower))return lower;
            }
        }
        return null;
    }
    private static BlockPos closedWoodenDoor(ServerLevel level,Mob mob,BlockPos pos) {
        if(!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos))return null;
        var state=level.getBlockState(pos);
        if(!(state.getBlock() instanceof DoorBlock) || !DoorBlock.isWoodenDoor(level,pos)
                || state.getValue(DoorBlock.OPEN))return null;
        BlockPos lower=state.getValue(DoorBlock.HALF)==DoubleBlockHalf.UPPER?pos.below():pos;
        if(Math.abs(lower.getY()-mob.getY())>1.5 || mob.position().distanceToSqr(Vec3.atBottomCenterOf(lower))>6.25
                || !level.hasChunkAt(lower) || !level.hasChunkAt(lower.above())
                || !level.getWorldBorder().isWithinBounds(lower) || !level.getWorldBorder().isWithinBounds(lower.above()))return null;
        var bottom=level.getBlockState(lower);var top=level.getBlockState(lower.above());
        return bottom.is(state.getBlock()) && top.is(state.getBlock())
                && bottom.getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER
                && top.getValue(DoorBlock.HALF)==DoubleBlockHalf.UPPER?lower:null;
    }
    @Override public void start() {
        if(door==null || !(mob.level() instanceof ServerLevel level))return;
        // Revalidate after goal selection; never replace a changed block or force an iron door.
        if(closedWoodenDoor(level,mob,door)==null)return;
        var state=level.getBlockState(door);
        ((DoorBlock)state.getBlock()).setOpen(mob,level,state,door,true);
    }
    @Override public boolean canContinueToUse() {return false;}
    // Deliberately leave opened doors open for following soldiers. No block destruction.
}
