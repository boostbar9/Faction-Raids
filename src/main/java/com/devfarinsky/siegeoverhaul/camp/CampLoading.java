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
        int distance=160+((attempt/24)%7)*64;
        int x=core.getX()+(int)Math.round(Math.cos(facing)*distance);
        int z=core.getZ()+(int)Math.round(Math.sin(facing)*distance);
        ChunkPos chunk=new ChunkPos(new BlockPos(x,core.getY(),z));
        return new BlockPos(chunk.getMiddleBlockX(),core.getY(),chunk.getMiddleBlockZ());
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
