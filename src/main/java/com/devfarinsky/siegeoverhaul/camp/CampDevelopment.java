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
        if(!WarGate.ready(level,raid) || raid.campPos==null || raid.campClaimId==null || raid.campUpgradeStage>CampPerimeter.LAST_STAGE || raid.coreCaptured
                || !RaidConfig.ENABLE_CAMP_CONSTRUCTION.get() || !RaidConfig.CLEANUP_WAR_CAMPS.get()
                || !raid.pendingCampBlocks.isEmpty() || !raid.pendingFortifications.isEmpty()
                || NativeCampConstruction.active(raid) || !CampClaims.owns(level,raid)) return;
        if(raid.campWorkers.stream().noneMatch(id -> level.getEntity(id) instanceof Mob worker && worker.isAlive())) return;
        raid.campUpgradeTicks+=ModConstants.TICK_INTERVAL;
        RaidSavedData.get(level.getServer()).setDirty();
        if(raid.campUpgradeTicks<RaidConfig.CAMP_UPGRADE_SECONDS.get()*20)return;
        raid.campUpgradeTicks=0;
        if(CampPerimeter.perimeterStage(raid.campUpgradeStage)) { tryPerimeter(level,raid); return; }
        double x=-Math.cos(raid.approachAngle),z=-Math.sin(raid.approachAngle);
        Direction front=Math.abs(x)>=Math.abs(z)?(x>=0?Direction.EAST:Direction.WEST):(z>=0?Direction.SOUTH:Direction.NORTH);
        Direction extension=raid.campUpgradeStage==0?front.getClockWise():raid.campUpgradeStage==1?front.getCounterClockWise():front.getOpposite();
        for(BlockPos center : candidates(raid.campPos,extension)) if(trySite(level,raid,center))return;
    }
    static List<BlockPos> candidates(BlockPos camp,Direction preferred) {
        var sites=new ArrayList<BlockPos>();
        for(int distance:new int[]{15,23}) for(Direction side:new Direction[]{preferred,preferred.getClockWise(),preferred.getCounterClockWise(),preferred.getOpposite()})
            sites.add(camp.relative(side,distance));
        return sites;
    }
    /**
     * One wall side or corner tower per pass. Unbuildable columns are simply
     * left out, and a stage that cannot place anything is retried a bounded
     * number of times before the camp moves on to the next section.
     */
    static void tryPerimeter(ServerLevel level,RaidSavedData.RaidState raid) {
        var plan=CampPerimeter.plan(level,raid,raid.campUpgradeStage);
        if(!plan.isEmpty()) {
            raid.pendingCampBlocks.putAll(plan);
            if(NativeCampConstruction.start(level,raid)) {
                if(raid.campUpgradeStage==CampPerimeter.FIRST_STAGE) {
                    BlockPos gate=CampPerimeter.mainGateCenter(raid);
                    if(gate!=null) {
                        raid.warGate.putLong("PerimeterGate",gate.asLong());
                        raid.warGate.putInt("PerimeterGateFacing",CampPerimeter.mainGateSide(raid).get2DDataValue());
                    }
                }
                raid.campUpgradeStage++;
                raid.warGate.remove("PerimeterRetries");
                return;
            }
            // Never fall back to remote placement or replace an obstructing player block.
            plan.keySet().forEach(raid.pendingCampBlocks::remove);
        }
        int retries=raid.warGate.getInt("PerimeterRetries")+1;
        if(retries>=3) { raid.campUpgradeStage++; raid.warGate.remove("PerimeterRetries"); }
        else raid.warGate.putInt("PerimeterRetries",retries);
    }

    private static boolean trySite(ServerLevel level,RaidSavedData.RaidState raid,BlockPos center) {        var anchor=RaidSavedData.get(level.getServer()).anchors.get(raid.teamKey);
        if(anchor==null)return false;
        Set<net.minecraft.world.level.ChunkPos> checked=new HashSet<>();
        Map<Long,String> plan=new LinkedHashMap<>();
        int y=Integer.MIN_VALUE;
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++) {
            BlockPos p=center.offset(dx,0,dz);
            if(!level.hasChunkAt(p) || !level.getWorldBorder().isWithinBounds(p))return false;
            if(checked.add(new net.minecraft.world.level.ChunkPos(p))) {
                var claim=com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge.getClaimAt(level,p).orElse(null);
                if(claim==null || !claim.claimId().equals(raid.campClaimId)
                        || com.devfarinsky.siegeoverhaul.compat.ClaimBridge.isForeignClaim(level,p,anchor.withIdentity(claim.ownerFactionStringId(),anchor.teamDisplay())))return false;
            }
            y=Math.max(y,level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p.getX(),p.getZ()));
        }
        if(Math.abs(y-raid.campPos.getY())>2)return false;
        center=new BlockPos(center.getX(),y,center.getZ());
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++) {
            BlockPos p=center.offset(dx,0,dz);
            int ground=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p.getX(),p.getZ());
            if(y-ground>2 || !level.getFluidState(new BlockPos(p.getX(),ground-1,p.getZ())).isEmpty())return false;
            for(int sy=ground;sy<y;sy++)plan.put(new BlockPos(p.getX(),sy,p.getZ()).asLong(),"minecraft:cobblestone");
            plan.put(p.asLong(),"minecraft:spruce_planks");
        }
        // Entrances face the main camp; each upgrade has its own purpose and silhouette.
        int towardX=raid.campPos.getX()-center.getX(), towardZ=raid.campPos.getZ()-center.getZ();
        Direction entrance=Math.abs(towardX)>=Math.abs(towardZ)
                ? (towardX>=0?Direction.EAST:Direction.WEST) : (towardZ>=0?Direction.SOUTH:Direction.NORTH);
        plan.putAll(CampUpgradeLayout.structure(center, entrance, raid.campUpgradeStage, raid.factionId));
        raid.pendingCampBlocks.putAll(plan);
        // Never fall back to remote placement for an upgrade or replace an obstructing player block.
        if(NativeCampConstruction.start(level,raid)) { CampStructures.record(raid,raid.campUpgradeStage,center,entrance); raid.campUpgradeStage++; return true; }
        raid.pendingCampBlocks.clear(); return false;
    }
}
