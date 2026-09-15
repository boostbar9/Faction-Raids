package com.devfarinsky.siegeoverhaul.raid;

import com.devfarinsky.siegeoverhaul.RaidConfig;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps the ground under a marching army ticking.
 *
 * <p>Camps are established well outside the defenders' view distance, so the
 * chunks between the camp and the objective are not loaded by anything. An army
 * that leaves camp walks a few chunks, leaves the camp ticket behind and then
 * stops ticking entirely until a player walks out to it - the classic "the
 * enemy never arrives" report. This tracks the last known chunk of every raider
 * in the wave and re-issues short-lived region tickets around them each pass, so
 * the column carries its own loaded corridor from camp to objective.
 *
 * <p>Tickets expire on their own, so a crashed or stopped siege never leaves
 * chunks force-loaded. The last known chunks are persisted with the raid, which
 * lets a restarted server pull the army back into memory instead of stranding it.
 */
public final class MarchLoading {
    /** Entity-ticking radius around each tracked chunk. */
    public static final int RADIUS = 2;
    private static final TicketType<ChunkPos> TICKET = TicketType.create("siegeoverhaul_march",
            Comparator.comparingLong(ChunkPos::toLong), 100);

    private MarchLoading() {}

    /**
     * Chunks worth keeping loaded, nearest the objective first and limited to
     * {@code cap}. Sorting by objective distance means the front of the column
     * always keeps its corridor when a very large wave exceeds the budget.
     */
    public static List<Long> selection(Map<UUID, Long> lastKnown, BlockPos objective, int cap) {
        if (cap <= 0 || lastKnown.isEmpty()) return List.of();
        List<Long> unique = new ArrayList<>(new java.util.LinkedHashSet<>(lastKnown.values()));
        unique.sort(Comparator.<Long>comparingDouble(packed -> distanceSq(packed, objective))
                .thenComparingLong(packed -> packed));
        return unique.size() <= cap ? unique : new ArrayList<>(unique.subList(0, cap));
    }

    private static double distanceSq(long packed, BlockPos objective) {
        ChunkPos chunk = new ChunkPos(packed);
        double dx = chunk.getMiddleBlockX() - objective.getX();
        double dz = chunk.getMiddleBlockZ() - objective.getZ();
        return dx * dx + dz * dz;
    }

    /** Refresh tracked positions from live raiders and ticket their corridor. */
    public static void tick(ServerLevel level, RaidSavedData.RaidState state, BlockPos objective) {
        if (!RaidConfig.KEEP_MARCHING_ARMY_LOADED.get()) {
            state.marchChunks.clear();
            return;
        }
        Map<UUID, Long> tracked = new LinkedHashMap<>();
        for (UUID id : state.raiders) {
            Entity entity = level.getEntity(id);
            if (entity instanceof Mob mob && mob.isAlive()) {
                tracked.put(id, new ChunkPos(mob.blockPosition()).toLong());
            } else {
                // Not loaded (or not loaded yet): keep the last chunk we saw it in
                // so the ticket below can bring it back into the simulation.
                Long remembered = state.marchChunks.get(id);
                if (remembered != null) tracked.put(id, remembered);
            }
        }
        state.marchChunks.clear();
        state.marchChunks.putAll(tracked);
        for (long packed : selection(tracked, objective, RaidConfig.MAX_MARCH_CHUNKS.get())) {
            ChunkPos chunk = new ChunkPos(packed);
            level.getChunkSource().addRegionTicket(TICKET, chunk, RADIUS, chunk);
        }
    }
}
