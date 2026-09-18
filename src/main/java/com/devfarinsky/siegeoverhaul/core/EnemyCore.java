package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.camp.*;
import com.devfarinsky.siegeoverhaul.compat.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** Capturable command core, raised on a small keep at the centre of the enemy town. */
public final class EnemyCore {
    private EnemyCore() {}
    public static BlockPos position(RaidSavedData.RaidState raid) {
        return raid.campaign.contains("EnemyCore", Tag.TAG_LONG) ? BlockPos.of(raid.campaign.getLong("EnemyCore")) : null;
    }
    /**
     * Keep decoration around the core, relative to the plinth-top centre {@code base}.
     * A five-wide raised stone-brick plinth with four corner pillars capped by
     * beacons marks the town centre and stays visible over the camp. The core
     * sits one block above the plinth centre; the pillars are diagonal and the
     * plinth top is open, so nothing blocks the horizontal line of sight the
     * capture ring requires.
     */
    public static Map<BlockPos, String> keepBlueprint(BlockPos base) {
        Map<BlockPos, String> plan = new LinkedHashMap<>();
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
            plan.put(base.offset(dx, 0, dz), "minecraft:stone_bricks");
        for (int cx : new int[]{-2, 2}) for (int cz : new int[]{-2, 2}) {
            for (int y = 1; y <= 5; y++) plan.put(base.offset(cx, y, cz), "minecraft:stone_bricks");
            plan.put(base.offset(cx, 6, cz), "minecraft:sea_lantern");
        }
        return plan;
    }
    /** The core rests one block above the plinth centre so raiders can stand beside it. */
    public static BlockPos corePos(BlockPos base) { return base.above(); }

    public static boolean ensure(ServerLevel level, RaidSavedData.RaidState raid) {
        BlockPos existing = position(raid);
        if (existing != null) return level.hasChunkAt(existing) && level.getBlockState(existing).is(CoreBlocks.CORE.get());
        if (raid.campPos == null || raid.campClaimId == null || !CampClaims.owns(level, raid)) return false;
        if (level.getGameTime() % 100 != 0) return false;
        return build(level, raid);
    }
    /** New placements only: erect the keep at the town centre and enshrine the core. */
    private static boolean build(ServerLevel level, RaidSavedData.RaidState raid) {
        var anchor = RaidSavedData.get(level.getServer()).anchors.get(raid.teamKey);
        if (anchor == null) return false;
        Map<ChunkPos, Boolean> allowedChunks = new HashMap<>();
        java.util.function.Predicate<BlockPos> allowed = cell -> allowedChunks.computeIfAbsent(
                new ChunkPos(cell), chunk -> {
                    var claim = RecruitsClaimsBridge.getClaimAt(level, cell).orElse(null);
                    return claim != null && claim.claimId().equals(raid.campClaimId)
                            && !ClaimBridge.isForeignClaim(level, cell,
                            anchor.withIdentity(claim.ownerFactionStringId(), anchor.teamDisplay()));
                });
        for (BlockPos base : EnemyCoreSite.candidates(raid.campPos, CampPerimeter.mainGateSide(raid))) {
            if (buildAt(level, raid, base, allowed)) return true;
        }
        return false;
    }
    private static boolean buildAt(ServerLevel level, RaidSavedData.RaidState raid, BlockPos base,
                                   java.util.function.Predicate<BlockPos> allowed) {
        BlockPos core = corePos(base);
        if (!EnemyCoreSite.clear(level, raid, base, allowed)) return false;
        Map<BlockPos, String> blocks = new LinkedHashMap<>(keepBlueprint(base));
        blocks.put(core, "siegeoverhaul:siege_core");
        List<CampTerrain.Change> changes = new ArrayList<>();
        for (var entry : blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
            if (!allowed.test(pos)) return false;
            BlockState before = level.getBlockState(pos);
            if (!CampVegetation.replaceable(before) || before.hasBlockEntity() || !before.getFluidState().isEmpty()) return false;
            BlockState after = state(entry.getValue());
            if (after == null) return false;
            if (!before.equals(after)) changes.add(new CampTerrain.Change(pos, before, after));
        }
        if (!CampTerrain.apply(level, raid, new CampTerrain.Plan(changes))) return false;
        raid.campaign.putLong("EnemyCore", core.asLong());
        raid.campaign.putBoolean("EnemyCoreCourtyard", true);
        raid.warGate.getCompound("Blocks").putString(Long.toString(core.asLong()), "siegeoverhaul:siege_core");
        RaidSavedData.get(level.getServer()).setDirty();
        FactionLogger.LOG.info("Enemy Siege Core keep raised at clear camp site {} for {}", core, raid.teamKey);
        return true;
    }
    private static BlockState state(String id) {
        if (id.equals("siegeoverhaul:siege_core")) return CoreBlocks.CORE.get().defaultBlockState();
        var block = ForgeRegistries.BLOCKS.getValue(ResourceLocation.tryParse(id));
        return block == null ? null : block.defaultBlockState();
    }
    public static boolean tick(ServerLevel level, RaidSavedData data, RaidSavedData.RaidState raid, RaidSavedData.Anchor anchor) {
        if (!EndlessSiege.active(raid) || raid.preparationTicks > 0 || raid.coreCaptured || !ensure(level, raid)) return false;
        BlockPos pos = position(raid);
        int[] counts = CoreOccupation.counts(level, pos, raid.teamKey, anchor.members());
        int maximum = RaidConfig.CORE_RECAPTURE_SECONDS.get() * 20;
        int before = raid.campaign.getInt("EnemyCaptureTicks");
        int progress = CoreControl.advance(before, maximum, counts[1], counts[0]);
        raid.campaign.putInt("EnemyCaptureTicks", progress);
        if (progress != before) data.setDirty();
        if (progress > 0 && level.getGameTime() % 100 == 0) for (var player : level.players())
            if (raid.teamKey.equals(SiegeCore.key(player))) player.displayClientMessage(Component.literal(
                    "Enemy core capture: " + progress * 100 / maximum + "% | " + counts[1] + " allies / " + counts[0] + " enemies"), true);
        return progress >= maximum && counts[1] > counts[0];
    }
}
