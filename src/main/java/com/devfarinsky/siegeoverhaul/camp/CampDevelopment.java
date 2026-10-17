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
        // After the last wall/tower the crew moves on to war works instead of idling.
        boolean works=raid.campUpgradeStage>CampPerimeter.LAST_STAGE;
        if(!WarGate.ready(level,raid) || raid.campPos==null || raid.campClaimId==null || (works && !CampWarWorks.enabled()) || raid.coreCaptured
                || !RaidConfig.ENABLE_CAMP_CONSTRUCTION.get() || !RaidConfig.CLEANUP_WAR_CAMPS.get()
                || !raid.pendingCampBlocks.isEmpty() || !raid.pendingFortifications.isEmpty()
                || NativeCampConstruction.active(raid) || !CampClaims.owns(level,raid)) return;
        if(raid.campWorkers.stream().noneMatch(id -> level.getEntity(id) instanceof Mob worker && worker.isAlive())) return;
        raid.campUpgradeTicks+=ModConstants.TICK_INTERVAL;
        RaidSavedData.get(level.getServer()).setDirty();
        int interval=works ? CampWarWorks.intervalTicks(raid) : RaidConfig.CAMP_UPGRADE_SECONDS.get()*20;
        if(raid.campUpgradeTicks<interval)return;
        raid.campUpgradeTicks=0;
        if(works) { CampWarWorks.develop(level,raid); return; }
        if(CampPerimeter.perimeterStage(raid.campUpgradeStage)) { tryPerimeter(level,raid); return; }
        Direction front=CampPerimeter.mainGateSide(raid);
        Direction extension=raid.campUpgradeStage==0?front.getClockWise():raid.campUpgradeStage==1?front.getCounterClockWise():front.getOpposite();
        for(BlockPos center : candidates(raid.campPos,extension,front)) if(trySite(level,raid,center))return;
    }
    static List<BlockPos> candidates(BlockPos camp,Direction preferred,Direction gateSide) {
        var sites=new ArrayList<BlockPos>();
        // A 7x7 pavilion centred eight blocks out occupies radii 5..11:
        // wholly inside the radius-12 palisade, with a one-block buffer at
        // the wall. The old 15/23 positions put the only doorway directly on
        // (or beyond) the future wall and could seal the building shut.
        for(int distance:new int[]{8,7}) for(Direction side:new Direction[]{preferred,preferred.getClockWise(),preferred.getCounterClockWise(),preferred.getOpposite()})
            if(side!=gateSide)sites.add(camp.relative(side,distance));
        // Corner courtyards provide alternatives when the three wings are
        // occupied. Seven leaves room for both the wall and its corner tower;
        // the central avenues remain at least seven blocks wide.
        for(Direction side:new Direction[]{preferred,preferred.getClockWise(),preferred.getCounterClockWise(),preferred.getOpposite()})
            sites.add(camp.relative(side,7).relative(side.getClockWise(),7));
        return sites;
    }
    /**
     * One wall side or corner tower per pass. Unbuildable columns are simply
     * left out, and a stage that cannot place anything is retried a bounded
     * number of times before the camp moves on to the next section.
     */
    static void tryPerimeter(ServerLevel level,RaidSavedData.RaidState raid) {
        BlockPos gate=raid.campUpgradeStage==CampPerimeter.FIRST_STAGE
                ? CampPerimeter.plannedGateCenter(level,raid) : null;
        if(raid.campUpgradeStage==CampPerimeter.FIRST_STAGE && gate==null) {
            raid.constructionPauseReason="Main gate needs loaded, supported ground";
            return;
        }
        var plan=CampPerimeter.plan(level,raid,raid.campUpgradeStage);
        if(!plan.isEmpty()) {
            raid.pendingCampBlocks.putAll(plan);
            if(NativeCampConstruction.start(level,raid)) {
                if(raid.campUpgradeStage==CampPerimeter.FIRST_STAGE) {
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

    private static boolean trySite(ServerLevel level,RaidSavedData.RaidState raid,BlockPos center) {
        var anchor=RaidSavedData.get(level.getServer()).anchors.get(raid.teamKey);
        if(anchor==null)return false;
        Set<net.minecraft.world.level.ChunkPos> checked=new HashSet<>();
        int stage=raid.campUpgradeStage;
        var site=sitePlan(level,raid,anchor,checked,center,false,
                (c,entrance) -> CampUpgradeLayout.structure(c,entrance,stage,raid.factionId));
        if(site.isEmpty())return false;
        raid.pendingCampBlocks.putAll(site.get().plan());
        // Never fall back to remote placement for an upgrade or replace an obstructing player block.
        String structures=com.devfarinsky.siegeoverhaul.ModConstants.Tags.CAMP_STRUCTURES;
        var previous=raid.campaign.getCompound(structures).copy();
        // Reserve the entrance before choosing a supply barrel for this job.
        CampStructures.record(raid,raid.campUpgradeStage,site.get().center(),site.get().entrance());
        if(NativeCampConstruction.start(level,raid)) {
            CampStructures.constructionStarted(level,raid,raid.campUpgradeStage);
            raid.campUpgradeStage++;
            return true;
        }
        if(previous.isEmpty())raid.campaign.remove(structures);else raid.campaign.put(structures,previous);
        raid.pendingCampBlocks.clear(); return false;
    }

    /** A validated 7x7 hall site: its raised floor, the layout and its entrance steps. */
    record Site(BlockPos center, Direction entrance, Map<Long,String> plan) {}

    /**
     * Plan a 7x7 hall at {@code center}: loaded, claimed, near the camp's height, on supported
     * ground, with clear interior and buildable three-wide entrance steps. {@code outward}
     * turns the doorway away from the camp (for halls outside the perimeter wall). Read-only.
     */
    static Optional<Site> sitePlan(ServerLevel level,RaidSavedData.RaidState raid,RaidSavedData.Anchor anchor,
                                   Set<net.minecraft.world.level.ChunkPos> checked,BlockPos center,boolean outward,
                                   java.util.function.BiFunction<BlockPos,Direction,Map<Long,String>> layout) {
        Map<Long,String> plan=new LinkedHashMap<>();
        int y=Integer.MIN_VALUE;
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++) {
            BlockPos p=center.offset(dx,0,dz);
            if(CampPerimeter.mainApproachColumn(raid,p))return Optional.empty();
            if(!level.hasChunkAt(p) || !level.getWorldBorder().isWithinBounds(p))return Optional.empty();
            if(!claimed(level,raid,anchor,checked,p))return Optional.empty();
            y=Math.max(y,level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p.getX(),p.getZ()));
        }
        if(Math.abs(y-raid.campPos.getY())>2)return Optional.empty();
        center=new BlockPos(center.getX(),y,center.getZ());
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++) {
            BlockPos p=center.offset(dx,0,dz);
            int ground=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p.getX(),p.getZ());
            BlockPos support=p.atY(ground-1);
            if(y-ground>2 || !level.getFluidState(support).isEmpty()
                    || !level.getBlockState(support).isFaceSturdy(level,support,Direction.UP))return Optional.empty();
            for(int sy=ground;sy<y;sy++)plan.put(new BlockPos(p.getX(),sy,p.getZ()).asLong(),"minecraft:cobblestone");
            plan.put(p.asLong(),"minecraft:spruce_planks");
        }
        // Entrances face the main camp; halls outside the wall face away from it.
        int towardX=raid.campPos.getX()-center.getX(), towardZ=raid.campPos.getZ()-center.getZ();
        Direction entrance=Math.abs(towardX)>=Math.abs(towardZ)
                ? (towardX>=0?Direction.EAST:Direction.WEST) : (towardZ>=0?Direction.SOUTH:Direction.NORTH);
        if(outward)entrance=entrance.getOpposite();
        plan.putAll(layout.apply(center,entrance));

        // Hollow cells are absent from Workers' placement list, so validate
        // them too: a valid shell must not enclose an existing obstruction.
        if (!CampBuildingAccess.clearInterior(level, raid, center, plan)) return Optional.empty();
        var access=CampBuildingAccess.plan(level,raid,center,entrance,
                pos -> claimed(level,raid,anchor,checked,pos));
        if(access.isEmpty())return Optional.empty();
        // A raised apron can reach past the pavilion footprint. Do not fill
        // the gate avenue even when the pavilion itself sits safely beside it.
        if(access.get().keySet().stream().anyMatch(key ->
                CampPerimeter.mainApproachColumn(raid,BlockPos.of(key))))return Optional.empty();
        plan.putAll(access.get());
        return Optional.of(new Site(center,entrance,plan));
    }

    static boolean claimed(ServerLevel level,RaidSavedData.RaidState raid,
                                   RaidSavedData.Anchor anchor,
                                   Set<net.minecraft.world.level.ChunkPos> checked,BlockPos pos) {
        if(!checked.add(new net.minecraft.world.level.ChunkPos(pos)))return true;
        var claim=com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge.getClaimAt(level,pos).orElse(null);
        return claim!=null && claim.claimId().equals(raid.campClaimId)
                && !com.devfarinsky.siegeoverhaul.compat.ClaimBridge.isForeignClaim(level,pos,
                anchor.withIdentity(claim.ownerFactionStringId(),anchor.teamDisplay()));
    }
}
