package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.compat.CampClaims;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.*;

/** Original, bounded native Workers blueprints, added sequentially after the initial defenses. */
public final class CampDevelopment {
    private CampDevelopment() {}
    public static void tick(ServerLevel level,RaidSavedData.RaidState raid) {
        if(raid.campPos==null || raid.campClaimId==null || raid.campUpgradeStage>=3 || raid.coreCaptured
                || !RaidConfig.ENABLE_CAMP_CONSTRUCTION.get() || !RaidConfig.CLEANUP_WAR_CAMPS.get()
                || !raid.pendingCampBlocks.isEmpty() || !raid.pendingFortifications.isEmpty()
                || NativeCampConstruction.active(raid) || !CampClaims.owns(level,raid)) return;
        if(raid.campWorkers.stream().noneMatch(id -> level.getEntity(id) instanceof Mob worker && worker.isAlive())) return;
        raid.campUpgradeTicks+=ModConstants.TICK_INTERVAL;
        RaidSavedData.get(level.getServer()).setDirty();
        if(raid.campUpgradeTicks<2400)return;
        raid.campUpgradeTicks=0;
        double x=-Math.cos(raid.approachAngle),z=-Math.sin(raid.approachAngle);
        Direction front=Math.abs(x)>=Math.abs(z)?(x>=0?Direction.EAST:Direction.WEST):(z>=0?Direction.SOUTH:Direction.NORTH);
        Direction extension=raid.campUpgradeStage==0?front.getClockWise():raid.campUpgradeStage==1?front.getCounterClockWise():front.getOpposite();
        BlockPos center=raid.campPos.relative(extension,15);
        Map<Long,String> plan=new LinkedHashMap<>();
        int y=Integer.MIN_VALUE;
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++) {
            BlockPos p=center.offset(dx,0,dz);
            if(!level.hasChunkAt(p))return;
            y=Math.max(y,level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p.getX(),p.getZ()));
        }
        if(Math.abs(y-raid.campPos.getY())>2)return;
        center=new BlockPos(center.getX(),y,center.getZ());
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++) {
            BlockPos p=center.offset(dx,0,dz);
            int ground=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p.getX(),p.getZ());
            if(y-ground>2 || !level.getFluidState(new BlockPos(p.getX(),ground-1,p.getZ())).isEmpty())return;
            for(int sy=ground;sy<y;sy++)plan.put(new BlockPos(p.getX(),sy,p.getZ()).asLong(),"minecraft:cobblestone");
            plan.put(p.asLong(),"minecraft:spruce_planks");
        }
        // Timber frame, open doorways, contrasting pitched roof. Supports precede roof cells.
        for(int dy=1;dy<=3;dy++)for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++) {
            boolean corner=Math.abs(dx)==3 && Math.abs(dz)==3;
            boolean wall=(Math.abs(dx)==3 || Math.abs(dz)==3) && !(Math.abs(dx)<=1 && Math.abs(dz)==3);
            if(corner || wall && dy==1) plan.put(center.offset(dx,dy,dz).asLong(),corner?"minecraft:stripped_spruce_log":"minecraft:spruce_planks");
        }
        String roof=raid.campUpgradeStage==0?"minecraft:gray_wool":raid.campUpgradeStage==1?"minecraft:red_terracotta":"minecraft:dark_oak_planks";
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)plan.put(center.offset(dx,4+(3-Math.abs(dx))/2,dz).asLong(),roof);
        plan.put(center.offset(2,1,2).asLong(),raid.campUpgradeStage==1?"minecraft:crafting_table":"minecraft:composter");
        plan.put(center.offset(-2,1,2).asLong(),"minecraft:hay_block");
        raid.pendingCampBlocks.putAll(plan);
        // Never fall back to remote placement for an upgrade or replace an obstructing player block.
        if(NativeCampConstruction.start(level,raid)) raid.campUpgradeStage++;
        else raid.pendingCampBlocks.clear();
    }
}
