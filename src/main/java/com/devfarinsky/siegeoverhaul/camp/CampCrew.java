package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;

import static com.devfarinsky.siegeoverhaul.ModConstants.Tags.*;

/**
 * Keeps an enemy camp crew working as a team when individual builders die.
 *
 * <p>A dead builder releases its crew slot immediately, hands the job materials it
 * was carrying back to the camp's own supply barrel (so the survivors can finish
 * the same finite job), and a bounded, saved number of replacements can walk in
 * from the camp to pick up the remaining work. Unloaded builders are never treated
 * as dead.</p>
 */
public final class CampCrew {
    private CampCrew() {}

    /** Loaded, living crew members. Unloaded members are not included but keep their slot. */
    public static List<Mob> live(ServerLevel level, RaidSavedData.RaidState raid) {
        List<Mob> crew = new ArrayList<>();
        for (UUID id : raid.campWorkers)
            if (level.getEntity(id) instanceof Mob worker && worker.isAlive()) crew.add(worker);
        return crew;
    }

    /** Builder death: free its slot and return carried job materials to the shared barrel. */
    public static void onDeath(ServerLevel level, Mob worker) {
        String team = worker.getPersistentData().getString(CAMP_WORKER_TEAM);
        if (team.isBlank()) return;
        RaidSavedData data = RaidSavedData.get(level.getServer());
        RaidSavedData.RaidState raid = data.raids.get(team);
        if (raid == null || !raid.campWorkers.remove(worker.getUUID())) return;
        int returned = returnMaterials(level, raid, worker);
        FactionLogger.LOG.info("Camp builder for {} died; {} builders remain, {} job materials returned to camp supplies",
                team, raid.campWorkers.size(), returned);
        data.setDirty();
    }

    /**
     * Move carried items that the active job still needs into the job's own barrel.
     * Items are removed from the builder before its death drops are produced, so the
     * stock is conserved rather than duplicated. Anything that does not fit stays put.
     */
    static int returnMaterials(ServerLevel level, RaidSavedData.RaidState raid, Mob worker) {
        if (!NativeCampConstruction.active(raid) || raid.pendingCampBlocks.isEmpty()) return 0;
        BlockPos supply = BlockPos.of(raid.nativeCamp.getLong(CAMP_SUPPLY_POS));
        if (!level.hasChunkAt(supply)) return 0;
        var be = level.getBlockEntity(supply);
        if (!(be instanceof Container barrel) || !be.getPersistentData().hasUUID(CAMP_SUPPLY_OWNER)
                || !be.getPersistentData().getUUID(CAMP_SUPPLY_OWNER).equals(raid.nativeCamp.getUUID(CAMP_OWNER))) return 0;
        SimpleContainer inventory;
        Set<Item> needed = new HashSet<>();
        try {
            if (!(worker.getClass().getMethod("getInventory").invoke(worker) instanceof SimpleContainer found)) return 0;
            inventory = found;
            for (ItemStack stack : NativeCampConstruction.materials(level, raid.pendingCampBlocks)) needed.add(stack.getItem());
        } catch (ReflectiveOperationException | RuntimeException ex) {
            FactionLogger.LOG.debug("Cannot recover camp builder materials", ex);
            return 0;
        }
        int moved = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack carried = inventory.getItem(slot);
            if (carried.isEmpty() || !needed.contains(carried.getItem())) continue;
            int before = carried.getCount();
            insert(barrel, carried);
            moved += before - carried.getCount();
            if (carried.isEmpty()) inventory.setItem(slot, ItemStack.EMPTY);
        }
        if (moved > 0) { inventory.setChanged(); barrel.setChanged(); }
        return moved;
    }

    /** Merge into matching stacks first, then empty slots. Shrinks {@code stack} by what was stored. */
    static void insert(Container target, ItemStack stack) {
        for (int i = 0; i < target.getContainerSize() && !stack.isEmpty(); i++) {
            ItemStack current = target.getItem(i);
            if (current.isEmpty() || !ItemStack.isSameItemSameTags(current, stack)) continue;
            int n = Math.min(stack.getCount(), Math.min(current.getMaxStackSize(), target.getMaxStackSize()) - current.getCount());
            if (n <= 0) continue;
            current.grow(n);
            stack.shrink(n);
        }
        for (int i = 0; i < target.getContainerSize() && !stack.isEmpty(); i++) {
            if (!target.getItem(i).isEmpty()) continue;
            int n = Math.min(stack.getCount(), Math.min(stack.getMaxStackSize(), target.getMaxStackSize()));
            ItemStack placed = stack.copy();
            placed.setCount(n);
            target.setItem(i, placed);
            stack.shrink(n);
        }
    }

    /**
     * Periodic crew upkeep: drop identities that are loaded but no longer alive, and
     * send bounded replacements while the camp still has work for them.
     */
    public static void tick(ServerLevel level, RaidSavedData.RaidState raid) {
        if (raid.campPos == null || !raid.campUsesWorkers) return;
        boolean changed = false;
        for (UUID id : new ArrayList<>(raid.campWorkers)) {
            Entity entity = level.getEntity(id);
            // Unloaded crew keep their identity; a loaded body that is gone or dead does not.
            if (entity != null && (!(entity instanceof Mob) || !entity.isAlive())) {
                raid.campWorkers.remove(id);
                changed = true;
            }
        }
        if (raid.campBuilderReplaceTicks > 0) {
            raid.campBuilderReplaceTicks = Math.max(0, raid.campBuilderReplaceTicks - ModConstants.TICK_INTERVAL);
            changed = true;
        }
        int missing = RaidConfig.CAMP_BUILDER_MAX.get() - raid.campWorkers.size();
        if (missing > 0 && needsCrew(raid) && replacementAllowed(raid)) changed |= replenish(level, raid, missing);
        if (changed) RaidSavedData.get(level.getServer()).setDirty();
    }

    /** Work remains, or the camp still has outbuildings and perimeter sections to raise. */
    static boolean needsCrew(RaidSavedData.RaidState raid) {
        return !raid.coreCaptured && (!raid.pendingCampBlocks.isEmpty() || NativeCampConstruction.active(raid)
                || raid.campUpgradeStage < CampPerimeter.COMPLETE_STAGE || CampWarWorks.hasWork(raid));
    }

    static boolean replacementAllowed(RaidSavedData.RaidState raid) {
        return RaidConfig.ENABLE_CAMP_CONSTRUCTION.get() && raid.campBuilderReplaceTicks <= 0
                && raid.campBuilderReplacements < RaidConfig.CAMP_BUILDER_REPLACEMENTS.get();
    }

    private static boolean replenish(ServerLevel level, RaidSavedData.RaidState raid, int missing) {
        if (!WorkersBridge.available() || !level.hasChunkAt(raid.campPos)
                || !com.devfarinsky.siegeoverhaul.compat.CampClaims.owns(level, raid)) return false;
        boolean spawned = false;
        for (int i = 0; i < missing && raid.campBuilderReplacements < RaidConfig.CAMP_BUILDER_REPLACEMENTS.get(); i++) {
            // Replacements arrive at the camp's centre, never inside a pending blueprint cell.
            BlockPos spawn = CampBuilderAccess.spawnPosition(level, raid, raid.campPos.offset(7 + i, 0, 0));
            if (spawn == null) break;
            Optional<Mob> worker = WorkersBridge.spawnBuilder(level, spawn, raid.teamKey);
            if (worker.isEmpty()) break;
            Mob builder = worker.get();
            raid.campWorkers.add(builder.getUUID());
            raid.campBuilderReplacements++;
            spawned = true;
            if (NativeCampConstruction.active(raid)) {
                try {
                    WorkersBridge.enableNative(builder, raid.nativeCamp.getUUID(CAMP_OWNER), true);
                } catch (ReflectiveOperationException | RuntimeException ex) {
                    FactionLogger.LOG.warn("Replacement camp builder could not join the active job", ex);
                    raid.campWorkers.remove(builder.getUUID());
                    builder.discard();
                    continue;
                }
            }
            FactionLogger.LOG.info("Replacement camp builder {}/{} joined {} at {}", raid.campBuilderReplacements,
                    RaidConfig.CAMP_BUILDER_REPLACEMENTS.get(), raid.teamKey, spawn);
        }
        if (spawned) raid.campBuilderReplaceTicks = ModConstants.secondsToTicks(RaidConfig.CAMP_BUILDER_REPLACE_SECONDS.get());
        return spawned;
    }
}
