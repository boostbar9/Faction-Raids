package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.entities.workarea.StorageArea;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Bounded read-only source identity and native scan-read envelope validation. */
final class ProtectedStorageContext {
    static final int MAX_SCAN_CELLS = 8192;
    record Source(Container container, BlockPos pos, Set<BlockPos> cells) {}
    private static final Set<Class<?>> BLOCK_CONTAINERS = Set.of(ChestBlockEntity.class, TrappedChestBlockEntity.class,
            BarrelBlockEntity.class, ShulkerBoxBlockEntity.class, HopperBlockEntity.class,
            DispenserBlockEntity.class, DropperBlockEntity.class, FurnaceBlockEntity.class,
            BlastFurnaceBlockEntity.class, SmokerBlockEntity.class);
    private static final Set<Class<?>> BLOCK_TYPES=Set.of(ChestBlock.class,TrappedChestBlock.class,BarrelBlock.class,
            ShulkerBoxBlock.class,HopperBlock.class,DispenserBlock.class,DropperBlock.class,FurnaceBlock.class,
            BlastFurnaceBlock.class,SmokerBlock.class);
    private ProtectedStorageContext() {}

    static void blockImplementation(ServerLevel level,BlockPos pos) {
        var block=level.getBlockState(pos).getBlock();var entity=level.getBlockEntity(pos);
        if(block instanceof ChestBlock && !BLOCK_TYPES.contains(block.getClass())
                || entity instanceof Container && (!BLOCK_CONTAINERS.contains(entity.getClass()) || !BLOCK_TYPES.contains(block.getClass())))
            throw new IllegalStateException("Unknown native storage block/container implementation");
    }

    static ServerLevel level(BuilderEntity worker) {
        if (!(worker.level() instanceof ServerLevel level)) throw new IllegalStateException("Storage dimension unavailable");
        return level;
    }
    static AABB storage(BuilderEntity worker, StorageArea area, boolean owned) {
        ServerLevel level = level(worker);
        if (area == null || area.getClass() != StorageArea.class || !area.isAlive() || area.level() != level || level.getEntity(area.getUUID()) != area
                || !area.canWorkHere(worker) || owned && !java.util.Objects.equals(WorkersBridge.readWorkerOwner(worker), WorkersBridge.readOwner(area)))
            throw new IllegalStateException("Storage owner or native permission changed");
        if (!owned && !java.util.Objects.equals(WorkersBridge.readWorkerOwner(worker), WorkersBridge.readOwner(area))
                && !NativeConstructionGuard.sharedStorageFactionMatches(worker))
            throw new IllegalStateException("Shared upkeep storage requires current worker faction membership");
        AABB current = area.getArea();
        if (current == null || area.area != null && !current.equals(area.area))
            throw new IllegalStateException("Native storage scan cache moved; refresh the source before resuming");
        return current;
    }
    static Set<BlockPos> scan(BuilderEntity worker, StorageArea area, boolean owned) {
        AABB current = storage(worker, area, owned);
        // Native scanStorageBlocks uses area if present, otherwise this current computed envelope.
        return envelope(level(worker), area.area == null ? current : area.area, 1, 1);
    }
    static boolean contains(AABB box, BlockPos pos) {
        return pos.getX() >= Math.floor(box.minX) && pos.getX() <= Math.floor(box.maxX)
                && pos.getY() >= Math.floor(box.minY) && pos.getY() <= Math.floor(box.maxY)
                && pos.getZ() >= Math.floor(box.minZ) && pos.getZ() <= Math.floor(box.maxZ);
    }
    static Set<BlockPos> envelope(ServerLevel level, AABB box, int horizontal, int above) {
        if (box == null || !Double.isFinite(box.minX) || !Double.isFinite(box.maxX) || !Double.isFinite(box.minY)
                || !Double.isFinite(box.maxY) || !Double.isFinite(box.minZ) || !Double.isFinite(box.maxZ))
            throw new IllegalStateException("Invalid storage envelope");
        long x1 = (long)Math.floor(box.minX) - horizontal, x2 = (long)Math.floor(box.maxX) + horizontal;
        long y1 = (long)Math.floor(box.minY), y2 = (long)Math.floor(box.maxY) + above;
        long z1 = (long)Math.floor(box.minZ) - horizontal, z2 = (long)Math.floor(box.maxZ) + horizontal;
        long volume = Math.multiplyExact(Math.multiplyExact(x2-x1+1, y2-y1+1), z2-z1+1);
        if (volume < 1 || volume > MAX_SCAN_CELLS || x1 < -30000000 || x2 > 30000000 || z1 < -30000000 || z2 > 30000000
                || y1 < level.getMinBuildHeight() || y2 >= level.getMaxBuildHeight())
            throw new IllegalStateException("Native storage scan envelope is too large or outside world limits");
        var positions = new HashSet<BlockPos>();
        for (int x=(int)x1;x<=x2;x++) for (int y=(int)y1;y<=y2;y++) for (int z=(int)z1;z<=z2;z++)
            positions.add(new BlockPos(x,y,z));
        loaded(level, positions);
        return Set.copyOf(positions);
    }
    static void loaded(ServerLevel level, Set<BlockPos> positions) {
        for (BlockPos pos : positions) if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos))
            throw new IllegalStateException("Native storage source/read neighbors are unloaded or outside the border");
    }

    /** Forge BlockEntity.setChanged notifies output-signal neighbors and may read one further
     * block through a conductor in any of six directions. Check every possible read first. */
    static Set<BlockPos> dirtyReadPositions(ServerLevel level,Set<BlockPos> sources) {
        if(sources.isEmpty() || sources.size()>2)throw new IllegalStateException("Unknown dirty-source locations");
        var reads=new HashSet<BlockPos>();
        for(BlockPos source:sources)for(int x=-2;x<=2;x++)for(int y=-2;y<=2;y++)for(int z=-2;z<=2;z++)
            if(Math.abs(x)+Math.abs(y)+Math.abs(z)<=2) {
                BlockPos pos=source.offset(x,y,z);
                if(pos.getY()<level.getMinBuildHeight() || pos.getY()>=level.getMaxBuildHeight())
                    throw new IllegalStateException("Dirty notification exceeds world height");
                reads.add(pos);
            }
        loaded(level,reads);return Set.copyOf(reads);
    }

    static void cleanup(ServerLevel level, Container container, BlockPos pos) {
        cleanup(level,container,pos,Set.of());
    }
    static void cleanup(ServerLevel level, Container container, BlockPos pos,Set<BlockPos> retainedCells) {
        if (container == null) return;
        if (pos != null) envelope(level, new AABB(pos,pos), 1, 1);
        // Cleanup does not withdraw items and need not reauthorize future selection. It still must
        // not force-load any block entity touched by the native close/dirty operations.
        if (container instanceof BlockEntity blockEntity) {
            if (!BLOCK_CONTAINERS.contains(blockEntity.getClass()) || blockEntity.getLevel()!=level)
                throw new IllegalStateException("Unsupported cached cleanup container");
            dirtyReadPositions(level,Set.of(blockEntity.getBlockPos()));
        } else if (container.getClass()==CompoundContainer.class) {
            if(pos==null && retainedCells.isEmpty())throw new IllegalStateException("Compound cleanup location unavailable");
            // A cleared position performs no chest-close world read, but position upkeep still
            // dirties the retained old halves. Their verified locations remain mandatory.
            if(pos!=null)envelope(level,new AABB(pos,pos),1,1);
            if(!retainedCells.isEmpty())dirtyReadPositions(level,retainedCells);
        } else throw new IllegalStateException("Unsupported cached cleanup container");
    }

    static void validateScanContainers(BuilderEntity worker,StorageArea area,AABB bounds) {
        ServerLevel level=level(worker);
        for(BlockPos pos:BlockPos.betweenClosedStream(bounds).map(BlockPos::immutable).toList())
            if(level.getBlockState(pos.above()).isAir()) {
                blockImplementation(level,pos);
                Container container=area.getContainer(pos);
                if(container!=null)source(level,container,pos);
            }
    }

    static Source source(ServerLevel level, Container container, BlockPos selected) {
        if (container == null || selected == null) throw new IllegalStateException("Native container unavailable");
        BlockPos pos = selected.immutable(); envelope(level,new AABB(pos,pos),1,1);
        blockImplementation(level,pos);
        Set<BlockPos> cells = new HashSet<>();
        if (container instanceof BlockEntity blockEntity) {
            if (!BLOCK_CONTAINERS.contains(blockEntity.getClass()) || blockEntity.isRemoved() || blockEntity.getLevel() != level
                    || !pos.equals(blockEntity.getBlockPos()) || level.getBlockEntity(pos) != blockEntity)
                throw new IllegalStateException("Unverified or replaced native block container");
            // Read-only preflight cannot trigger lazy loot generation through Container.getItem.
            if (blockEntity.saveWithoutMetadata().contains("LootTable")) throw new IllegalStateException("Open loot storage before commissioning");
            cells.add(pos);
        } else if (container.getClass() == CompoundContainer.class) {
            CompoundContainer combined = (CompoundContainer) container;
            var candidates = new HashSet<BlockPos>(); candidates.add(pos);
            for (Direction direction : Direction.Plane.HORIZONTAL) candidates.add(pos.relative(direction));
            loaded(level, candidates); int slots = 0;
            for (BlockPos candidate : candidates) {
                var blockEntity = level.getBlockEntity(candidate);
                if (blockEntity instanceof ChestBlockEntity chest && combined.contains(chest)) {
                    if (!BLOCK_CONTAINERS.contains(chest.getClass()) || chest.isRemoved() || chest.getLevel() != level
                            || chest.saveWithoutMetadata().contains("LootTable")) throw new IllegalStateException("Unverified chest half");
                    cells.add(candidate); slots += chest.getContainerSize();
                }
            }
            if (cells.size() != 2 || !cells.contains(pos) || slots != combined.getContainerSize())
                throw new IllegalStateException("Native compound-container identity changed");
        } else throw new IllegalStateException("Native container location is unsupported");
        var state=level.getBlockState(pos);
        if(state.getBlock() instanceof ChestBlock chestBlock) {
            Container current=ChestBlock.getContainer(chestBlock,state,level,pos,false);
            if(current!=container) {
                if(current==null || current.getClass()!=CompoundContainer.class || container.getClass()!=CompoundContainer.class)
                    throw new IllegalStateException("Current native chest connectivity changed");
                CompoundContainer combined=(CompoundContainer)current;
                for(BlockPos cell:cells)if(!(level.getBlockEntity(cell) instanceof Container half) || !combined.contains(half))
                    throw new IllegalStateException("Current native chest halves changed");
            }
        }
        if (container.getContainerSize() < 1 || container.getContainerSize() > 128)
            throw new IllegalStateException("Unsupported native container size");
        dirtyReadPositions(level,cells);
        return new Source(container, pos, Set.copyOf(cells));
    }
}
