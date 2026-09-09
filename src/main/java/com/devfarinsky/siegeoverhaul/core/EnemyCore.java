package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.camp.*;
import com.devfarinsky.siegeoverhaul.compat.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Capturable command core on the existing protected War Gate pad. */
public final class EnemyCore {
    private EnemyCore() {}
    public static BlockPos position(RaidSavedData.RaidState raid) {
        return raid.campaign.contains("EnemyCore", Tag.TAG_LONG) ? BlockPos.of(raid.campaign.getLong("EnemyCore")) : null;
    }
    public static boolean ensure(ServerLevel level, RaidSavedData.RaidState raid) {
        BlockPos existing = position(raid);
        if (existing != null) return level.hasChunkAt(existing) && level.getBlockState(existing).is(CoreBlocks.CORE.get());
        if (!WarGate.ready(level, raid) || !CampClaims.owns(level, raid)) return false;
        if (level.getGameTime() % 100 != 0) return false;
        BlockPos center = WarGate.center(raid);
        for (int side : new int[]{-1,1}) {
            BlockPos pos = center.relative(WarGate.facing(raid).getClockWise(), side).relative(WarGate.facing(raid).getOpposite()).above();
            if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) continue;
            var claim = RecruitsClaimsBridge.getClaimAt(level, pos).orElse(null);
            if (claim == null || !claim.claimId().equals(raid.campClaimId)) continue;
            var before = level.getBlockState(pos);
            if (!CampVegetation.replaceable(before) || before.hasBlockEntity() || !before.getFluidState().isEmpty()) continue;
            var change = new CampTerrain.Change(pos, before, CoreBlocks.CORE.get().defaultBlockState());
            if (!CampTerrain.apply(level, raid, new CampTerrain.Plan(List.of(change)))) continue;
            raid.campaign.putLong("EnemyCore", pos.asLong());
            raid.warGate.getCompound("Blocks").putString(Long.toString(pos.asLong()), "siegeoverhaul:siege_core");
            RaidSavedData.get(level.getServer()).setDirty(); return true;
        }
        return false;
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
