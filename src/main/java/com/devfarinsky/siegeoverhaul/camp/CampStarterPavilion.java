package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.RaidSavedData.RaidState;
import com.devfarinsky.siegeoverhaul.core.EnemyCoreSite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Original compact Olympian pavilions, built through the existing Workers/restoration pipeline. */
public final class CampStarterPavilion {
    private static final String SAVED = "StarterPavilionEntrances";
    private CampStarterPavilion() {}

    public static void reserveEntrance(RaidState raid, BlockPos center, Direction front) {
        var entries=raid.campaign.getList(SAVED,net.minecraft.nbt.Tag.TAG_COMPOUND);
        var entry=new net.minecraft.nbt.CompoundTag();
        entry.putLong("Center",center.asLong());entry.putInt("Front",front.get2DDataValue());
        entries.add(entry);raid.campaign.put(SAVED,entries);
    }

    static boolean accessColumn(RaidState raid,BlockPos p) {
        var entries=raid.campaign.getList(SAVED,net.minecraft.nbt.Tag.TAG_COMPOUND);
        for(int i=0;i<entries.size();i++) {
            var entry=entries.getCompound(i);
            BlockPos center=BlockPos.of(entry.getLong("Center"));
            Direction front=Direction.from2DDataValue(entry.getInt("Front"));
            int dx=p.getX()-center.getX(),dz=p.getZ()-center.getZ();
            int depth=dx*front.getStepX()+dz*front.getStepZ();
            int side=dx*front.getClockWise().getStepX()+dz*front.getClockWise().getStepZ();
            if(depth>=2 && depth<=6 && Math.abs(side)<=1)return true;
        }
        return false;
    }

    public static BlockPos anchor(BlockPos camp, Direction front, int side) {
        return camp.relative(front, -5).relative(front.getClockWise(), side * 4);
    }

    public static Map<Long, String> plan(ServerLevel level, RaidState raid, BlockPos requested, Direction front) {
        Map<Long, String> plan = new LinkedHashMap<>();
        int floor = requested.getY();
        for (int x=-2;x<=2;x++) for (int z=-2;z<=2;z++) {
            BlockPos p=requested.offset(x,0,z);
            if (!allowed(level,raid,p)) return Map.of();
            floor=Math.max(floor,level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p.getX(),p.getZ()));
        }
        if (floor-requested.getY()>2 || floor+7>=level.getMaxBuildHeight()) return Map.of();
        BlockPos center=requested.atY(floor);
        for (int x=-2;x<=2;x++) for (int z=-2;z<=2;z++) {
            BlockPos column=center.offset(x,0,z);
            int ground=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,column.getX(),column.getZ());
            BlockPos support=column.atY(ground-1);
            if (floor-ground>2 || ground<=level.getMinBuildHeight()
                    || !level.getBlockState(support).isFaceSturdy(level,support,Direction.UP)
                    || !level.getFluidState(support).isEmpty()) return Map.of();
            // Validate the complete occupied volume AND the hollow interior before queuing any cell.
            for (int y=ground;y<=floor+6;y++) {
                BlockPos p=column.atY(y);
                if (!free(level,raid,p)) return Map.of();
                if (y<floor) plan.put(p.asLong(),"minecraft:cobblestone");
            }
        }
        plan.putAll(structure(center,front,raid.factionId));
        var approach=CampBuildingAccess.plan(level,raid,center,front,p->allowed(level,raid,p),2);
        if (approach.isEmpty()) return Map.of();
        for(long key:approach.get().keySet()) if(!free(level,raid,BlockPos.of(key)))return Map.of();
        plan.putAll(approach.get());
        return plan;
    }

    private static boolean allowed(ServerLevel level,RaidState raid,BlockPos p) {
        return level.hasChunkAt(p) && level.getWorldBorder().isWithinBounds(p)
                && Math.abs(p.getX()-raid.campPos.getX())<9 && Math.abs(p.getZ()-raid.campPos.getZ())<9;
    }

    private static boolean free(ServerLevel level,RaidState raid,BlockPos p) {
        return allowed(level,raid,p) && !EnemyCoreSite.reserved(raid,p)
                && !raid.pendingCampBlocks.containsKey(p.asLong()) && !raid.pendingFortifications.containsKey(p.asLong())
                && !raid.campBlocks.containsKey(p.asLong()) && level.getBlockEntity(p)==null
                && level.getFluidState(p).isEmpty() && CampVegetation.replaceable(level.getBlockState(p));
    }

    static Map<Long,String> structure(BlockPos center,Direction front,String faction) {
        if(front.getAxis().isVertical())throw new IllegalArgumentException("Horizontal entrance required");
        Map<Long,String> plan=new LinkedHashMap<>();
        var palette=CampUpgradeLayout.palette(faction,0);
        // Broad stone plinth, six columns and a stepped pediment give even the first camp a skyline.
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)put(plan,center,front,x,0,z,palette.base());
        for(int y=1;y<=3;y++)for(int x:new int[]{-2,2})for(int z:new int[]{-2,0,2})
            put(plan,center,front,x,y,z,palette.column());
        for(int x=-1;x<=1;x++)for(int y=1;y<=3;y++)put(plan,center,front,x,y,-2,palette.roof());
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)put(plan,center,front,x,4,z,palette.column());
        for(int x=-1;x<=1;x++)for(int z=-2;z<=2;z++)put(plan,center,front,x,5,z,palette.roof());
        for(int z=-2;z<=2;z++)put(plan,center,front,0,6,z,palette.column());
        String light="blackbay_reavers".equals(faction)?"minecraft:sea_lantern":"minecraft:glowstone";
        put(plan,center,front,-2,4,2,light);put(plan,center,front,2,4,2,light);
        put(plan,center,front,-1,1,-1,"minecraft:crafting_table");
        put(plan,center,front,1,1,-1,"minecraft:barrel");
        return plan;
    }

    private static void put(Map<Long,String> plan,BlockPos center,Direction front,int x,int y,int z,String block) {
        plan.put(center.relative(front,z).relative(front.getClockWise(),x).above(y).asLong(),block);
    }
}
