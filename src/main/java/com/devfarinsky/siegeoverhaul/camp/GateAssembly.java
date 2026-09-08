package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.siege.BlockRestoration;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** One-time, transactional arrival infrastructure; ordinary camp buildings remain worker jobs. */
public final class GateAssembly {
    private GateAssembly() {}
    public static boolean install(ServerLevel level, RaidSavedData.RaidState raid) {
        if (raid.warGate.isEmpty() || !raid.warGate.contains("Center", net.minecraft.nbt.Tag.TAG_LONG)) return false;
        if (raid.warGate.getBoolean("Assembled493")) return true;
        var cells = raid.warGate.getCompound("Blocks");
        if (cells.isEmpty()) return false;
        var before = raid.warGate.getCompound("RoadBefore");
        Set<String> keys = new HashSet<>(cells.getAllKeys());
        keys.addAll(before.getAllKeys());
        // Include the open arch and pad headroom, including plants between solid blueprint cells.
        BlockPos center = WarGate.center(raid);
        var front = WarGate.facing(raid);
        for (int x=-3;x<=3;x++) for (int z=-2;z<=2;z++) for (int y=1;y<=7;y++)
            keys.add(Long.toString(center.relative(front.getClockWise(),x).relative(front,z).above(y).asLong()));
        List<CampTerrain.Change> changes = new ArrayList<>();
        for (String key : keys) {
            BlockPos pos = WarGate.savedPosition(key);
            if (pos == null) return false;
            if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    || pos.getY()<level.getMinBuildHeight() || pos.getY()>=level.getMaxBuildHeight()) return false;
            var current = level.getBlockState(pos);
            var block = cells.contains(key) ? WarGate.savedBlock(cells, key) : Blocks.AIR;
            if (block == null) return false;
            var target = block.defaultBlockState();
            if (current.equals(target)) continue;
            // New road cuts must still match their surveyed terrain. Saved completed roads
            // may only fill empty/vegetation cells; never erase later foreign replacements.
            boolean surveyed = !raid.warGate.getBoolean("RoadPrepared") && before.contains(key)
                    && current.equals(BlockRestoration.deserializeState(before.getCompound(key)));
            if (current.hasBlockEntity() || !current.getFluidState().isEmpty()
                    || !surveyed && !CampVegetation.replaceable(current)) return false;
            changes.add(new CampTerrain.Change(pos,current,target));
        }
        if (!CampTerrain.apply(level,raid,new CampTerrain.Plan(changes))) return false;
        for (var change : changes) {
            String key=Long.toString(change.pos().asLong());
            if (!cells.contains(key) && !before.contains(key))
                before.put(key,raid.campBlocks.get(change.pos().asLong()).getCompound("Original").copy());
        }
        raid.warGate.put("RoadBefore",before);
        raid.warGate.putBoolean("RoadPrepared",true);
        raid.warGate.putBoolean("RoadPending",false);
        raid.warGate.putBoolean("Assembled493",true);
        raid.warGateWaitTicks=0;
        // Active legacy work areas keep their original bounds until their queue is rescanned.
        if (!NativeCampConstruction.active(raid)) cells.getAllKeys().forEach(key -> raid.pendingCampBlocks.remove(Long.parseLong(key)));
        cells.getAllKeys().forEach(key -> raid.pendingFortifications.remove(Long.parseLong(key)));
        RaidSavedData.get(level.getServer()).setDirty();
        return true;
    }
}
