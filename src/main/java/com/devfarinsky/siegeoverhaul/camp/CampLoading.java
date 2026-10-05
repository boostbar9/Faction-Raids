package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import java.util.Comparator;

/** Short-lived tickets for the core and one camp/search neighborhood; no synchronous terrain loads or permanent forced chunks. */
public final class CampLoading {
    private static final TicketType<ChunkPos> TICKET = TicketType.create("siegeoverhaul_camp",
            Comparator.comparingLong(ChunkPos::toLong),100);
    private CampLoading() {}
    public static BlockPos candidate(BlockPos core,double angle,int attempt) {
        // Cover every direction at each distance; chunk-centered sites maximize useful loaded ground.
        double facing=angle+(attempt%24)*Math.PI*2/24;
        int band=attempt/24;
        // The 200-attempt search has seven full 24-direction rings plus 32
        // attempts. Repeating ring zero after attempt 168 wastes the final
        // loaded-site checks on already rejected chunks. Use the intervening
        // radial half-rings; keep the existing first 168 sites and search cap.
        int distance=band<7 ? 160+band*64 : 160+(band-7)*64+32;
        int x=core.getX()+(int)Math.round(Math.cos(facing)*distance);
        int z=core.getZ()+(int)Math.round(Math.sin(facing)*distance);
        ChunkPos chunk=new ChunkPos(new BlockPos(x,core.getY(),z));
        return new BlockPos(chunk.getMiddleBlockX(),core.getY(),chunk.getMiddleBlockZ());
    }
    /**
     * A single wider rescue survey, interleaving radii so a slow load cannot spend
     * its whole deadline on the nearest ring again. Same chunk-centered footprint,
     * same claim/border checks, and no larger simultaneous ticket neighborhood.
     */
    public static BlockPos recoveryCandidate(BlockPos core, double angle, int attempt) {
        int bounded = Math.floorMod(attempt, 200);
        int distance = 608 + (bounded % 7) * 64;
        double facing = angle + Math.PI / 24 + (bounded / 7) * Math.PI * 2 / 29;
        int x = core.getX() + (int) Math.round(Math.cos(facing) * distance);
        int z = core.getZ() + (int) Math.round(Math.sin(facing) * distance);
        ChunkPos chunk = new ChunkPos(new BlockPos(x, core.getY(), z));
        return new BlockPos(chunk.getMiddleBlockX(), core.getY(), chunk.getMiddleBlockZ());
    }
    /** Balanced local search that keeps even the fallback radius-16 survey inside the loaded 3x3 chunks. */
    public static BlockPos localCandidate(BlockPos scout, int attempt, boolean expanded) {
        int width = expanded ? 5 : 3;
        int spacing = expanded ? 4 : 6;
        int index = Math.floorMod(attempt, width * width);
        int dx = (index % width - width / 2) * spacing;
        int dz = (index / width - width / 2) * spacing;
        // At chunk-center +8, another +8 plus the fallback's 16-block
        // survey reaches the first column outside the ready neighborhood.
        // Keep the 25 distinct, symmetric sites without rejecting that edge unloaded.
        if (expanded) { dx = Math.max(-7, Math.min(7, dx)); dz = Math.max(-7, Math.min(7, dz)); }
        return scout.offset(dx, 0, dz);
    }
    public static void keep(ServerLevel level,BlockPos pos) {
        // Radius 3 keeps the 3x3 camp neighborhood entity-ticking, with vanilla's surrounding load margin.
        ChunkPos chunk=new ChunkPos(pos);
        level.getChunkSource().addRegionTicket(TICKET,chunk,3,chunk);
    }
    public static void release(ServerLevel level,BlockPos pos) {
        if(pos==null)return;
        ChunkPos chunk=new ChunkPos(pos);
        level.getChunkSource().removeRegionTicket(TICKET,chunk,3,chunk);
    }
    public static boolean ready(ServerLevel level,BlockPos pos) {
        ChunkPos c=new ChunkPos(pos);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)
            if(level.getChunkSource().getChunkNow(c.x+x,c.z+z)==null)return false;
        return true;
    }
}
