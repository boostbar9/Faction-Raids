package com.devfarinsky.factionraids.naval;

import com.devfarinsky.factionraids.RaidConfig;
import com.devfarinsky.factionraids.RaidSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sibling of the ladder builder: when raiders stall on the shore of a narrow
 * water span they can't wade across, drop a temporary planks bridge across it.
 *
 * Wide water bodies are the naval convoy's job \u2014 bridging an ocean would be
 * absurd and expensive. This class caps span length at
 * {@link RaidConfig#MAX_BRIDGE_SPAN}; anything wider is left for the boats.
 *
 * <h4>Cleanup + safety</h4>
 * Every placed plank is recorded in the raid's {@code campBlocks} map so the
 * existing {@code cleanupWarCamp} pipeline removes it on raid end, and
 * player-modified positions are naturally preserved by the id-match check.
 * We only replace water and air \u2014 no ground blocks are ever displaced.
 */
public final class BridgeBuilder {

    private BridgeBuilder() {}

    private static final Map<String, Long> LAST_ATTEMPT = new HashMap<>();
    private static final Map<String, Integer> BRIDGES_PLACED = new HashMap<>();
    private static final Block BRIDGE_BLOCK = Blocks.OAK_PLANKS;

    /**
     * Tick hook for the naval bridge builder. Returns true if a bridge was
     * placed this tick (caller may want to announce it).
     */
    public static boolean tick(ServerLevel level, RaidSavedData.RaidState state, BlockPos objective) {
        if (!RaidConfig.ENABLE_BRIDGE_BUILDING.get()) return false;

        long now = level.getGameTime();
        long last = LAST_ATTEMPT.getOrDefault(state.teamKey, 0L);
        if (now - last < 20L * 20L) return false; // 20 s rate limit

        int placed = BRIDGES_PLACED.getOrDefault(state.teamKey, 0);
        if (placed >= RaidConfig.MAX_BRIDGES_PER_RAID.get()) return false;

        // Find a stall cluster: >=3 raiders standing on land with water directly
        // in front of them and clear land on the other side of the water.
        List<Mob> raiders = level.getEntitiesOfClass(Mob.class,
                new AABB(objective).inflate(96, 32, 96),
                m -> m.getPersistentData().getString("FactionRaidsTeam").equals(state.teamKey));
        if (raiders.size() < 3) return false;

        for (Mob raider : raiders) {
            if (!isOnLand(level, raider.blockPosition())) continue;
            for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                BlockPos span = detectSpannableWater(level, raider.blockPosition(), dir,
                        RaidConfig.MAX_BRIDGE_SPAN.get());
                if (span == null) continue;
                int spanLen = placeBridge(level, state, raider.blockPosition(), dir, span);
                if (spanLen > 0) {
                    LAST_ATTEMPT.put(state.teamKey, now);
                    BRIDGES_PLACED.put(state.teamKey, placed + 1);
                    return true;
                }
            }
        }

        LAST_ATTEMPT.put(state.teamKey, now); // rate-limit even on failure
        return false;
    }

    /** Solid, walkable, non-water ground under and around the position. */
    private static boolean isOnLand(ServerLevel level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return !below.isAir() && below.getFluidState().isEmpty();
    }

    /**
     * Starting from {@code from} + one step in {@code dir}, walk forward while
     * we're in water. If we reach land within {@code maxSpan} steps, return the
     * position of the far shore. Return null if the water is too wide, too
     * shallow, or if there is no water at all.
     */
    private static BlockPos detectSpannableWater(ServerLevel level, BlockPos from, Direction dir, int maxSpan) {
        BlockPos cursor = from.relative(dir);
        // Snap cursor down to what would be walked on \u2014 same y as source.
        boolean sawWater = false;
        for (int i = 0; i < maxSpan + 1; i++) {
            BlockPos step = cursor.relative(dir, i);
            BlockState feet = level.getBlockState(step);
            BlockState under = level.getBlockState(step.below());
            boolean feetWater = feet.getFluidState().getType() == Fluids.WATER
                    || under.getFluidState().getType() == Fluids.WATER;
            if (feetWater) {
                sawWater = true;
                continue;
            }
            // Far shore: solid ground below, AND raider can actually stand here
            // (feet block is non-solid / passable). Without the feet check we
            // sometimes returned a shore tile whose feet-block was a wall face.
            if (sawWater && !under.isAir() && under.getFluidState().isEmpty()
                    && !feet.isCollisionShapeFullBlock(level, step)) {
                return step; // far shore
            }
            if (!sawWater) return null; // no water in this direction
        }
        return null; // ran off the end without hitting shore
    }

    /**
     * Lay a one-wide plank strip from the raider's shore to the far shore.
     * Records each placed block in {@code state.campBlocks}. Returns the number
     * of planks placed (0 on failure).
     */
    private static int placeBridge(ServerLevel level, RaidSavedData.RaidState state,
                                    BlockPos start, Direction dir, BlockPos end) {
        int y = start.getY();
        int steps = 0;
        int distance = Math.abs(end.getX() - start.getX()) + Math.abs(end.getZ() - start.getZ());
        for (int i = 1; i < distance; i++) {
            BlockPos p = start.relative(dir, i).atY(y);
            BlockState existing = level.getBlockState(p);
            // Only displace water/air. Never overwrite ground blocks or blocks
            // the player might have placed.
            boolean placeable = existing.isAir()
                    || existing.getFluidState().getType() == Fluids.WATER;
            if (!placeable) continue;
            level.setBlockAndUpdate(p, BRIDGE_BLOCK.defaultBlockState());
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(BRIDGE_BLOCK);
            state.campBlocks.put(p.asLong(), id.toString());
            steps++;
        }
        return steps;
    }

    /** Drop rate-limit and counter state for a finished raid. */
    public static void forget(String teamKey) {
        LAST_ATTEMPT.remove(teamKey);
        BRIDGES_PLACED.remove(teamKey);
    }
}
