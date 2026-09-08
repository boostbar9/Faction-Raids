package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.siege.BlockRestoration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

import static com.devfarinsky.siegeoverhaul.ModConstants.Tags.*;

/** Native Workers jobs, with finite supplies and a pre-recorded restoration footprint. */
public final class NativeCampConstruction {
    private NativeCampConstruction() {}

    public static boolean active(RaidSavedData.RaidState raid) {
        return raid.nativeCamp.hasUUID(CAMP_OWNER);
    }

    public static boolean start(ServerLevel level, RaidSavedData.RaidState raid) {
        if (!com.devfarinsky.siegeoverhaul.compat.CampClaims.owns(level, raid)) return false;
        if (raid.campWorkers.isEmpty() || raid.pendingCampBlocks.isEmpty()
                || raid.pendingCampBlocks.size() > 512 || !RaidConfig.CLEANUP_WAR_CAMPS.get()) return false;
        if (!raid.warGate.isEmpty() && !GateAssembly.install(level,raid)) return false;
        Entity build = null, storage = null;
        BlockPos supply = null;
        try {
            if(!CampRoad.prepare(level,raid))return false;
            for (long key : raid.pendingCampBlocks.keySet()) {
                BlockPos p = BlockPos.of(key);
                if (!level.hasChunkAt(p) || !level.getWorldBorder().isWithinBounds(p)
                        || !CampVegetation.replaceable(level.getBlockState(p)) || !level.getFluidState(p).isEmpty()
                        || level.getBlockEntity(p) != null) return false;
            }
            supply = findSupplyPosition(level, raid);
            if (supply == null) return false;
            UUID owner = UUID.randomUUID();
            BlockPos min = bounds(raid.pendingCampBlocks, false), max = bounds(raid.pendingCampBlocks, true);
            BlockPos origin = new BlockPos(max.getX(), min.getY(), min.getZ());
            build = WorkersBridge.createArea(level, "buildarea", origin, owner,
                    max.getX() - min.getX() + 1, max.getZ() - min.getZ() + 1, max.getY() - min.getY() + 1);
            CompoundTag blueprint = blueprint(raid.pendingCampBlocks);
            WorkersBridge.startBlueprint(build, blueprint);
            List<ItemStack> supplies = splitStacks(materials(level, raid.pendingCampBlocks));
            if (supplies.size() > 27) throw new IllegalStateException("Camp supplies exceed barrel capacity");
            storage = WorkersBridge.createArea(level, "storagearea", supply, owner, 1, 1, 1);

            // Everything is checked before mutation. Native placement bypasses Forge's player
            // placement events, so every permitted cell must be snapshotted BEFORE jobs run.
            for (var job : raid.pendingCampBlocks.entrySet()) {
                BlockPos p = BlockPos.of(job.getKey());
                BlockState before = level.getBlockState(p);
                CompoundTag original = before.isAir() ? new CompoundTag() : BlockRestoration.serializeState(level, p, before);
                raid.recordCampBlock(job.getKey(), job.getValue(), original);
                if (!prepareCell(level, raid, p)) throw new IllegalStateException("Cannot prepare camp cell at " + p);
            }
            if(CampVegetation.plant(level.getBlockState(supply)) && !CampVegetation.clear(level,raid,supply))throw new IllegalStateException("Supply site vegetation blocked");
            if(CampVegetation.plant(level.getBlockState(supply.above())) && !CampVegetation.clear(level,raid,supply.above()))throw new IllegalStateException("Supply access vegetation blocked");
            raid.recordCampBlock(supply.asLong(), "minecraft:barrel", new CompoundTag());
            if (!level.setBlock(supply, Blocks.BARREL.defaultBlockState(), 3)
                    || !(level.getBlockEntity(supply) instanceof Container container))
                throw new IllegalStateException("Cannot create camp supply barrel");
            for (int i = 0; i < supplies.size(); i++) container.setItem(i, supplies.get(i));
            container.setChanged();
            level.getBlockEntity(supply).getPersistentData().putUUID(CAMP_SUPPLY_OWNER, owner);
            level.getBlockEntity(supply).setChanged();

            CompoundTag state = new CompoundTag();
            state.putUUID(CAMP_OWNER, owner);
            state.putUUID(CAMP_BUILD_AREA, build.getUUID());
            state.putUUID(CAMP_STORAGE_AREA, storage.getUUID());
            state.putLong(CAMP_SUPPLY_POS, supply.asLong());
            raid.nativeCamp = state;
            raid.campCompletedBlocks = 0;
            raid.campBuildTicks = 0;
            raid.constructionPauseReason = "";
            for (Entity area : List.of(build, storage)) {
                area.getPersistentData().putString(CAMP_AREA_TEAM, raid.teamKey);
                if (!level.addFreshEntity(area)) throw new IllegalStateException("Work area registration rejected");
            }
            for (UUID id : raid.campWorkers) {
                if (level.getEntity(id) instanceof Mob worker) WorkersBridge.enableNative(worker, owner, true);
            }
            RaidSavedData.get(level.getServer()).setDirty();
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            if (build != null) build.discard();
            if (storage != null) storage.discard();
            // Never retry provisioning after a reload or partial failure: supplies are finite.
            cleanupAreas(level, raid);
            for (UUID id : raid.campWorkers) {
                if (level.getEntity(id) instanceof Mob worker) WorkersBridge.parkBuilder(worker);
            }
            if (supply != null && level.getBlockEntity(supply) instanceof Container container
                    && level.getBlockEntity(supply).getPersistentData().hasUUID(CAMP_SUPPLY_OWNER)) {
                container.clearContent();
                level.setBlock(supply, Blocks.AIR.defaultBlockState(), 3);
            }
            FactionLogger.LOG.warn("Native camp construction unavailable; using bounded fallback", ex);
            return false;
        }
    }

    /** Workers uses a scanned relative-coordinate blueprint, not a vanilla structure template. */
    static CompoundTag blueprint(Map<Long, String> jobs) {
        BlockPos min = bounds(jobs, false), max = bounds(jobs, true);
        CompoundTag tag = new CompoundTag();
        tag.putInt("width", max.getX() - min.getX() + 1);
        tag.putString("facing", "south");
        ListTag blocks = new ListTag();
        jobs.forEach((key, id) -> {
            BlockPos p = BlockPos.of(key);
            var block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
            if (block == null || block == Blocks.AIR) throw new IllegalArgumentException("Invalid camp block " + id);
            CompoundTag entry = new CompoundTag();
            entry.putInt("x", p.getX() - min.getX());
            entry.putInt("y", p.getY() - min.getY());
            entry.putInt("z", p.getZ() - min.getZ());
            entry.put("state", NbtUtils.writeBlockState(block.defaultBlockState()));
            blocks.add(entry);
        });
        tag.put("blocks", blocks);
        return tag;
    }

    /** Rescan an existing job in place: preserve bounds, ownership and finite supplies. */
    public static void refreshAfterGateAssembly(ServerLevel level, RaidSavedData.RaidState raid) {
        if (!active(raid)) return;
        Entity area=level.getEntity(raid.nativeCamp.getUUID(CAMP_BUILD_AREA));
        if (area==null || !reloadArea(level,area,raid)) return;
        for (UUID id : raid.campWorkers) {
            if (level.getEntity(id) instanceof Mob worker && worker.isAlive()) {
                worker.goalSelector.getRunningGoals().toList().forEach(net.minecraft.world.entity.ai.goal.WrappedGoal::stop);
                WorkersBridge.parkBuilder(worker);
                try { WorkersBridge.enableNative(worker,raid.nativeCamp.getUUID(CAMP_OWNER),false); }
                catch (ReflectiveOperationException ex) { FactionLogger.LOG.warn("Cannot resume camp builder after gate assembly",ex); }
            }
        }
    }

    static void recoverMissingGateCells(ServerLevel level, RaidSavedData.RaidState raid) {
        if (raid.warGate.getBoolean("GateRepair480")) return;
        raid.warGate.putBoolean("GateRepair480", true);
        var cells=raid.warGate.getCompound("Blocks");
        for(String key:cells.getAllKeys()) {
            BlockPos pos=BlockPos.of(Long.parseLong(key));
            // Recover only empty cells in the existing protected blueprint. Never
            // remove player replacements or replenish completed construction.
            if(raid.pendingCampBlocks.size()<512 && level.hasChunkAt(pos) && level.getBlockState(pos).isAir())
                raid.pendingCampBlocks.putIfAbsent(pos.asLong(),cells.getString(key));
        }
    }

    static boolean prepareCell(ServerLevel level, RaidSavedData.RaidState raid, BlockPos pos) {
        if (CampVegetation.plant(level.getBlockState(pos))) return CampVegetation.clear(level, raid, pos);
        return level.getBlockState(pos).isAir() || level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    static List<ItemStack> materials(ServerLevel level, Map<Long, String> jobs) throws ReflectiveOperationException {
        Map<net.minecraft.world.item.Item, Integer> counts = new LinkedHashMap<>();
        for (String id : jobs.values()) {
            var block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
            if (block == null || block == Blocks.AIR) throw new IllegalArgumentException("Invalid camp material " + id);
            var item = WorkersBridge.buildMaterial(level, block);
            if (item == null || item == net.minecraft.world.item.Items.AIR)
                throw new IllegalStateException("No native material for " + id);
            // Native placement consumes one parsed item per cell, including non-block
            // ingredients such as amethyst shards omitted by getRequiredMaterials().
            counts.merge(item, 1, Integer::sum);
        }
        return counts.entrySet().stream().map(e -> new ItemStack(e.getKey(), e.getValue())).toList();
    }

    static List<ItemStack> splitStacks(List<ItemStack> materials) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack material : materials) {
            int remaining = material.getCount();
            while (remaining > 0) {
                ItemStack stack = material.copy();
                int count = Math.min(remaining, stack.getMaxStackSize());
                stack.setCount(count);
                result.add(stack);
                remaining -= count;
            }
        }
        return result;
    }

    private static BlockPos bounds(Map<Long, String> jobs, boolean maximum) {
        int x = maximum ? Integer.MIN_VALUE : Integer.MAX_VALUE, y = x, z = x;
        for (long key : jobs.keySet()) {
            BlockPos p = BlockPos.of(key);
            x = maximum ? Math.max(x, p.getX()) : Math.min(x, p.getX());
            y = maximum ? Math.max(y, p.getY()) : Math.min(y, p.getY());
            z = maximum ? Math.max(z, p.getZ()) : Math.min(z, p.getZ());
        }
        return new BlockPos(x, y, z);
    }

    private static BlockPos findSupplyPosition(ServerLevel level, RaidSavedData.RaidState raid) {
        for (int radius = 2; radius <= 6; radius++) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos p = raid.campPos.relative(direction, radius);
                if (!level.hasChunkAt(p) || !level.getWorldBorder().isWithinBounds(p)
                        || raid.pendingCampBlocks.containsKey(p.asLong())
                        || raid.pendingCampBlocks.containsKey(p.above().asLong())) continue;
                if ((level.getBlockState(p).isAir() || CampVegetation.plant(level.getBlockState(p)))
                        && (level.getBlockState(p.above()).isAir() || CampVegetation.plant(level.getBlockState(p.above())))
                        && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)
                        && level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new AABB(p)).isEmpty()) return p;
            }
        }
        return null;
    }

    /** Called immediately before EACH worker AI tick, including between periodic raid passes. */
    public static boolean safeToTick(ServerLevel level, RaidSavedData.RaidState raid) {
        if (!com.devfarinsky.siegeoverhaul.compat.CampClaims.owns(level, raid)) return pause(raid, "Camp claim lost or unavailable");
        if (!RaidConfig.ENABLED.get() || !RaidConfig.ENABLE_CAMP_CONSTRUCTION.get() || !WorkersBridge.available()) return false;
        BlockPos supply = BlockPos.of(raid.nativeCamp.getLong(CAMP_SUPPLY_POS));
        if (!level.hasChunkAt(supply)) return false;
        var be = level.getBlockEntity(supply);
        if (be == null || !be.getPersistentData().hasUUID(CAMP_SUPPLY_OWNER)
                || !be.getPersistentData().getUUID(CAMP_SUPPLY_OWNER).equals(raid.nativeCamp.getUUID(CAMP_OWNER))) {
            stop(level, raid);
            return false;
        }
        for (var job : raid.pendingCampBlocks.entrySet()) {
            BlockPos p = BlockPos.of(job.getKey());
            if (!level.hasChunkAt(p)) return false;
            BlockState current = level.getBlockState(p);
            if(CampVegetation.plant(current)) {
                if(!CampVegetation.clear(level,raid,p))return pause(raid,"Vegetation clearance blocked at "+p.toShortString());
                current=level.getBlockState(p);
            }
            if (!safeCell(current, job.getValue()) || !level.getFluidState(p).isEmpty()) {
                return pause(raid, "Blueprint blocked at " + p.toShortString());
            }
            // Do not let native builders enclose a player who entered the blueprint.
            if (current.isAir() && level.players().stream().anyMatch(player -> player.isAlive()
                    && !player.isSpectator() && player.getBoundingBox().intersects(new AABB(p)))) {
                return pause(raid, "Player inside blueprint");
            }
        }
        if (!raid.constructionPauseReason.isEmpty()) {
            FactionLogger.LOG.info("Camp builders for {} resumed", raid.teamKey);
            raid.constructionPauseReason = "";
        }
        return true;
    }

    private static boolean pause(RaidSavedData.RaidState raid, String reason) {
        if (!reason.equals(raid.constructionPauseReason)) {
            FactionLogger.LOG.info("Camp builders for {} paused: {}", raid.teamKey, reason);
            raid.constructionPauseReason = reason;
        }
        return false;
    }

    static boolean safeCell(BlockState current, String planned) {
        if(current.isAir())return true;
        if(!planned.equals(String.valueOf(ForgeRegistries.BLOCKS.getKey(current.getBlock()))))return false;
        BlockState expected=current.getBlock().defaultBlockState();
        // Neighbor updates legitimately connect fences/walls and bend stairs after placement.
        for(var property:current.getProperties()) {
            boolean connection=(current.getBlock() instanceof net.minecraft.world.level.block.FenceBlock
                    || current.getBlock() instanceof net.minecraft.world.level.block.WallBlock
                    || current.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock)
                    && Set.of("north","south","east","west","up").contains(property.getName());
            boolean stairShape=current.getBlock() instanceof net.minecraft.world.level.block.StairBlock && property.getName().equals("shape");
            if(!connection && !stairShape && !current.getValue(property).equals(expected.getValue(property)))return false;
        }
        return true;
    }

    public static void tick(ServerLevel level, RaidSavedData.RaidState raid) {
        if (!safeToTick(level, raid)) return;
        int completed = (int) raid.pendingCampBlocks.keySet().stream().filter(key -> !level.getBlockState(BlockPos.of(key)).isAir()).count();
        if (completed == raid.pendingCampBlocks.size()) {
            FactionLogger.LOG.info("Camp builders for {} completed {} planned cells", raid.teamKey, completed);
            stop(level, raid); return;
        }
        if (completed > raid.campCompletedBlocks) raid.campBuildTicks = 0;
        raid.campCompletedBlocks = completed;
        // Native jobs remain valid until completed, sabotaged, or the siege ends. Do not destroy
        // their supplies/work orders just because a builder spent time walking or gathering.
        if (level.isDay()) {
            raid.campBuildTicks = Math.min(Integer.MAX_VALUE - 20, raid.campBuildTicks) + 20;
            if (raid.campBuildTicks == RaidConfig.CAMP_MAX_BUILD_SECONDS.get() * 20)
                FactionLogger.LOG.info("Camp builders for {} have made no progress for {}s; retaining native jobs and finite supplies", raid.teamKey, RaidConfig.CAMP_MAX_BUILD_SECONDS.get());
        }
    }

    public static void stop(ServerLevel level, RaidSavedData.RaidState raid) {
        for (UUID id : new ArrayList<>(raid.campWorkers)) {
            Entity entity = level.getEntity(id);
            if (entity instanceof Mob worker && !WorkersBridge.parkBuilder(worker)) {
                worker.discard();
                raid.campWorkers.remove(id);
            }
        }
        cleanupAreas(level, raid);
        raid.pendingCampBlocks.clear();
        RaidSavedData.get(level.getServer()).setDirty();
    }

    public static void cleanupAreas(ServerLevel level, RaidSavedData.RaidState raid) {
        for (String key : List.of(CAMP_BUILD_AREA, CAMP_STORAGE_AREA)) {
            if (raid.nativeCamp.hasUUID(key)) {
                Entity area = level.getEntity(raid.nativeCamp.getUUID(key));
                if (area != null) area.discard();
            }
        }
        raid.nativeCamp = new CompoundTag();
    }

    public static boolean reloadArea(ServerLevel level, Entity area, RaidSavedData.RaidState raid) {
        try {
            if (raid == null || !active(raid)) return false;
            if (area.getUUID().equals(raid.nativeCamp.getUUID(CAMP_BUILD_AREA))) {
                CompoundTag plan=blueprint(raid.pendingCampBlocks);
                BlockPos min=bounds(raid.pendingCampBlocks,false);
                for(var entry:plan.getList("blocks",net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                    CompoundTag block=(CompoundTag)entry;
                    BlockPos p=min.offset(block.getInt("x"),block.getInt("y"),block.getInt("z"));
                    if(level.hasChunkAt(p)) {
                        BlockState current=level.getBlockState(p);
                        if(!current.isAir() && safeCell(current,raid.pendingCampBlocks.get(p.asLong())))
                            block.put("state",NbtUtils.writeBlockState(current));
                    }
                }
                WorkersBridge.startBlueprint(area,plan);
                return true;
            }
            return area.getUUID().equals(raid.nativeCamp.getUUID(CAMP_STORAGE_AREA));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            if (raid != null) stop(level, raid);
            return false;
        }
    }
}
