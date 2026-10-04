package com.devfarinsky.siegeoverhaul.nativecompat;

import com.talhanation.recruits.entities.ai.RecruitUpkeepEntityGoal;
import com.talhanation.recruits.entities.ai.RecruitUpkeepPosGoal;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.entities.ai.RecruitStorageUpkeepGoal;
import com.talhanation.workers.entities.workarea.StorageArea;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.horse.*;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.entity.vehicle.MinecartHopper;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Read-only inspection around the original pinned upkeep goals. No food/payment algorithm is replaced. */
final class ProtectedUpkeepAccess extends ProtectedInventoryGoal {
    final ProtectedStorageAccess.Kind kind;
    private final RecruitUpkeepPosGoal positionGoal;
    private final RecruitUpkeepEntityGoal entityGoal;
    private final StorageFields fields;
    private StorageArea acceptedStorage;
    private AABB acceptedBounds;
    private Entity acceptedEntity;
    private BlockPos acceptedPosition;
    private Container transferSource;
    private BlockPos transferPosition;
    private Set<BlockPos> transferCells=Set.of();
    private Container cleanupSource;
    private Set<BlockPos> cleanupCells=Set.of();
    private static final Set<Class<?>> ENTITY_SOURCES=Set.of(Horse.class,Donkey.class,Mule.class,Llama.class,
            TraderLlama.class,SkeletonHorse.class,ZombieHorse.class,Villager.class,Allay.class,Piglin.class,
            ChestBoat.class,MinecartChest.class,MinecartHopper.class,BuilderEntity.class);

    ProtectedUpkeepAccess(BuilderEntity worker,Goal delegate,ProtectedStorageAccess.Kind kind,Session session)
            throws ReflectiveOperationException {
        super(worker,delegate,session);this.kind=kind;
        positionGoal=delegate instanceof RecruitUpkeepPosGoal p?p:null;
        entityGoal=delegate instanceof RecruitUpkeepEntityGoal e?e:null;
        if(positionGoal!=null && positionGoal.recruit!=worker || entityGoal!=null && entityGoal.recruit!=worker)
            throw new IllegalStateException("Native upkeep owner differs");
        fields=delegate.getClass()==RecruitStorageUpkeepGoal.class?new StorageFields():null;
    }

    @Override ProtectedStorageAccess.Kind kind(){return kind;}
    // Recruits 1.15.2 cff03e0 keeps pending payment/timer finalization in the goal,
    // not entity NBT. A fresh goal cannot reconstruct or safely replay that stop.
    @Override boolean upkeep(){return true;}

    @Override String beforeStart() {
        String requests=ProtectedTransferCapacity.requestsProblem(worker);if(requests!=null)return requests;
        acceptedStorage=null;acceptedBounds=null;acceptedEntity=null;acceptedPosition=null;
        if(positionGoal!=null) {
            BlockPos pos=worker.getUpkeepPos();if(pos==null)return "Paused: configure a loaded native upkeep source";
            Container source=blockSource(pos);
            String data=ProtectedTransferCapacity.upkeepProblem(worker,source);if(data!=null)return data;
            acceptedPosition=pos.immutable();return null;
        }
        Entity target=configuredEntity();
        if(target instanceof StorageArea area && fields!=null) {
            acceptedBounds=ProtectedStorageContext.storage(worker,area,false);
            Set<BlockPos> reads=ProtectedStorageContext.scan(worker,area,false);
            String authority=NativeConstructionGuard.storageProblem(worker,reads);if(authority!=null)return authority;
            var level=ProtectedStorageContext.level(worker);
            for(BlockPos pos:BlockPos.betweenClosedStream(acceptedBounds).map(BlockPos::immutable).toList())
                if(level.getBlockState(pos.above()).isAir()) {
                    ProtectedStorageContext.blockImplementation(level,pos);
                    Container source=area.getContainer(pos);
                    if(source!=null) {
                        ProtectedStorageContext.source(level,source,pos);
                        String data=ProtectedTransferCapacity.upkeepProblem(worker,source);if(data!=null)return data;
                    }
                }
            acceptedStorage=area;return null;
        }
        if(target instanceof AbstractHorse) {
            // The original native start only captures the horse's container; it performs no
            // inventory read/transfer. Its public captured reference is verified after start and
            // again before every transfer through the horse's public identity predicate.
            validateEntityTarget(target);acceptedEntity=target;return null;
        }
        Container source=entitySource(target);
        String data=ProtectedTransferCapacity.upkeepProblem(worker,source);if(data!=null)return data;
        acceptedEntity=target;return null;
    }

    @Override void afterStart() {
        if(positionGoal!=null) {
            if(positionGoal.chestPos==null || !positionGoal.chestPos.equals(acceptedPosition))
                throw new IllegalStateException("Native upkeep target changed during start");
            retainCleanup(ProtectedStorageContext.source(ProtectedStorageContext.level(worker),positionGoal.container,positionGoal.chestPos));
        } else if(acceptedStorage!=null) {
            if(!fields.mode(delegate) || fields.area(delegate)!=acceptedStorage)
                throw new IllegalStateException("Native storage-upkeep selection changed");
            validateStorage();
        } else if(entityGoal.entity==null || entityGoal.entity.orElse(null)!=acceptedEntity
                || entityGoal.container!=entitySource(acceptedEntity))
            throw new IllegalStateException("Native entity-upkeep selection changed");
    }

    @Override String beforeTick() {
        transferSource=null;transferPosition=null;transferCells=Set.of();
        String requests=ProtectedTransferCapacity.requestsProblem(worker);if(requests!=null)return requests;
        if(positionGoal!=null)return positionTick();
        if(fields!=null && fields.mode(delegate))return storageTick();
        Entity target=configuredEntity();
        if(target!=acceptedEntity || entityGoal.entity==null || entityGoal.entity.orElse(null)!=target
                || entityGoal.pos==null || !entityGoal.pos.equals(target.getOnPos()))
            return "Paused: native entity upkeep target moved or changed; finish or refresh upkeep";
        Container source=entitySource(target);
        if(entityGoal.container!=source)return "Paused: native entity upkeep inventory was replaced";
        if(worker.position().distanceToSqr(Vec3.atCenterOf(entityGoal.pos))<50)
            return prepareTransfer(source,new java.util.HashSet<>(List.of(target.blockPosition(),target.getOnPos())),target.getOnPos());
        return null;
    }

    private String positionTick() {
        BlockPos current=worker.getUpkeepPos();
        if(positionGoal.chestPos!=current) {
            // Native tick takes this exact early-stop branch, updates its public position then closes
            // the old container. Authorize no withdrawal, but validate both bounded cleanup locations.
            positionCleanup(current);
            return null;
        }
        if(current==null || !current.equals(acceptedPosition) || positionGoal.container==null)
            return "Paused: restore the configured native upkeep container; automatic nearby fallback is unavailable";
        blockSource(current); // Current chest connectivity/loading/authority, independently of the cached pointer.
        var selected=ProtectedStorageContext.source(ProtectedStorageContext.level(worker),positionGoal.container,current);
        retainCleanup(selected);
        if(!positionGoal.setTimer && current.closerThan(worker.getOnPos(),3))
            return prepareTransfer(selected.container(),selected.cells(),selected.pos());
        return null;
    }

    private String storageTick() {
        validateStorage();
        BlockPos pos=fields.position(delegate);Container source=fields.container(delegate);
        if(pos==null) {
            List<?> queue=fields.queue(delegate);
            if(queue.isEmpty())return null;
            Object next=queue.get(0);if(!(next instanceof BlockPos nextPos))throw new IllegalStateException();
            pos=nextPos;source=acceptedStorage.storageMap.get(pos);
            if(source==null)return null; // Native skips the stale queue entry without any container access.
        } else if(source==null || acceptedStorage.storageMap.get(pos)!=source)
            return "Paused: native upkeep cached chest changed";
        if(!ProtectedStorageContext.contains(acceptedBounds,pos))throw new IllegalStateException("Upkeep queue escaped scan");
        Set<BlockPos> reads=ProtectedStorageContext.envelope(ProtectedStorageContext.level(worker),new AABB(pos,pos),1,1);
        String authority=NativeConstructionGuard.storageProblem(worker,reads);if(authority!=null)return authority;
        var selected=ProtectedStorageContext.source(ProtectedStorageContext.level(worker),source,pos);
        retainCleanup(selected);
        if(fields.position(delegate)!=null && fields.timer(delegate)>=16
                && pos.getCenter().distanceToSqr(worker.position())<=9)
            return prepareTransfer(source,selected.cells(),selected.pos());
        return null;
    }

    private void validateStorage() {
        if(acceptedStorage==null || configuredEntity()!=acceptedStorage || fields.area(delegate)!=acceptedStorage
                || !acceptedBounds.equals(ProtectedStorageContext.storage(worker,acceptedStorage,false)))
            throw new IllegalStateException("Configured storage upkeep target changed");
        List<?> queue=fields.queue(delegate);
        if(queue.size()>ProtectedStorageContext.MAX_SCAN_CELLS)throw new IllegalStateException("Unbounded upkeep queue");
        for(Object item:queue)if(!(item instanceof BlockPos pos) || !ProtectedStorageContext.contains(acceptedBounds,pos))
            throw new IllegalStateException("Upkeep queue escaped accepted storage");
    }

    private void retainCleanup(ProtectedStorageContext.Source source) {
        cleanupSource=source.container();cleanupCells=source.cells();
    }

    private String prepareTransfer(Container source,Set<BlockPos> positions,BlockPos selected) {
        String authority=NativeConstructionGuard.storageProblem(worker,positions);if(authority!=null)return authority;
        String data=ProtectedTransferCapacity.upkeepProblem(worker,source);if(data!=null)return data;
        transferSource=source;transferPosition=selected.immutable();transferCells=Set.copyOf(positions);writes=List.of(source);return null;
    }

    @Override void afterTick() {
        if(transferSource!=null) {
            // Storage mode intentionally clears its private source after service; retain the exact
            // inspected container for finally/setChanged, then revalidate the live source location.
            if(acceptedStorage!=null || positionGoal!=null) {
                ProtectedStorageContext.source(ProtectedStorageContext.level(worker),transferSource,transferPosition);
            } else if(entitySource(acceptedEntity)!=transferSource)throw new IllegalStateException("Upkeep entity source changed during callback");
        }
    }

    private Container blockSource(BlockPos pos) {
        var level=ProtectedStorageContext.level(worker);
        Set<BlockPos> reads=ProtectedStorageContext.envelope(level,new AABB(pos,pos),1,1);
        String authority=NativeConstructionGuard.storageProblem(worker,reads);if(authority!=null)throw new IllegalStateException(authority);
        ProtectedStorageContext.blockImplementation(level,pos);
        var state=level.getBlockState(pos);var be=level.getBlockEntity(pos);
        Container source=state.getBlock() instanceof ChestBlock chest?ChestBlock.getContainer(chest,state,level,pos,false)
                :be instanceof Container container?container:null;
        ProtectedStorageContext.source(level,source,pos);return source;
    }

    private Entity configuredEntity() {
        UUID id=worker.getUpkeepUUID();var level=ProtectedStorageContext.level(worker);
        Entity target=id==null?null:level.getEntity(id);
        if(target==null || !target.isAlive() || target.level()!=level
                || !worker.getBoundingBox().inflate(100).intersects(target.getBoundingBox()))
            throw new IllegalStateException("Configured native upkeep entity is unavailable");
        return target;
    }

    private void validateEntityTarget(Entity target) {
        if(target==null || configuredEntity()!=target || !ENTITY_SOURCES.contains(target.getClass()))
            throw new IllegalStateException("Native upkeep entity/container implementation is unsupported");
        var level=ProtectedStorageContext.level(worker);
        Set<BlockPos> cells=new java.util.HashSet<>(List.of(target.blockPosition(),target.getOnPos()));
        ProtectedStorageContext.loaded(level,cells);
        String authority=NativeConstructionGuard.storageProblem(worker,cells);if(authority!=null)throw new IllegalStateException(authority);
    }

    private Container entitySource(Entity target) {
        validateEntityTarget(target);
        Container source=target instanceof AbstractHorse horse?capturedHorseInventory(horse,entityGoal.container)
                :target instanceof InventoryCarrier carrier?carrier.getInventory():target instanceof Container container?container:null;
        if(source==null || source!=target && (source.getClass()!=SimpleContainer.class
                && !(source instanceof SimpleContainer simple && ProtectedTransferCapacity.supportedInventory(simple))))
            throw new IllegalStateException("Native upkeep inventory behavior is unsupported");
        // Container minecarts/boats may have ungenerated loot; preflight must not open it implicitly.
        if(target.saveWithoutId(new net.minecraft.nbt.CompoundTag()).contains("LootTable"))
            throw new IllegalStateException("Open loot upkeep storage first");
        return source;
    }

    /** Official 1.20.1 hasInventoryChanged is a public, read-only reference comparison.
     * Use the original goal's captured source, never reflect/read the protected horse field. */
    static Container capturedHorseInventory(AbstractHorse horse,Container captured) {
        if(horse==null || captured==null || captured.getClass()!=SimpleContainer.class
                || horse.hasInventoryChanged(captured))
            throw new IllegalStateException("Native horse upkeep inventory was replaced or is unsupported");
        return captured;
    }

    private void positionCleanup(BlockPos closeAt) {
        if(positionGoal.container!=null && positionGoal.container.getClass()==net.minecraft.world.CompoundContainer.class
                && positionGoal.container!=cleanupSource)
            throw new IllegalStateException("Old compound upkeep source provenance is unavailable");
        ProtectedStorageContext.cleanup(ProtectedStorageContext.level(worker),positionGoal.container,closeAt,
                positionGoal.container==cleanupSource?cleanupCells:Set.of());
    }

    @Override String cleanup() {
        var level=ProtectedStorageContext.level(worker);
        if(positionGoal!=null)positionCleanup(positionGoal.chestPos);
        else if(fields!=null && fields.mode(delegate))ProtectedStorageContext.cleanup(level,fields.container(delegate),fields.position(delegate));
        // Stock entity upkeep stop only finalizes worker flags/timers; it reads no source container.
        return null;
    }
    @Override void stopped(){acceptedStorage=null;acceptedBounds=null;acceptedEntity=null;acceptedPosition=null;transferSource=null;cleanupSource=null;cleanupCells=Set.of();writes=List.of();}

    /** Exact audited private fields, read-only. No guessed names, lambda captures, or state writes. */
    private static final class StorageFields {
        final Field mode=field("storageMode",boolean.class),area=field("storageArea",StorageArea.class),
                queue=field("chestQueue",List.class),position=field("currentChestPos",BlockPos.class),
                container=field("chestContainer",Container.class),timer=field("interactTimer",int.class);
        StorageFields() throws ReflectiveOperationException {}
        private static Field field(String name,Class<?> type)throws ReflectiveOperationException {
            Field field=RecruitStorageUpkeepGoal.class.getDeclaredField(name);
            if(field.getType()!=type || !Modifier.isPrivate(field.getModifiers()) || Modifier.isStatic(field.getModifiers())
                    || !field.trySetAccessible())throw new NoSuchFieldException(name);
            return field;
        }
        private Object read(Field field,Object goal){try{return field.get(goal);}catch(IllegalAccessException failure){throw new IllegalStateException(failure);}}
        boolean mode(Object goal){return (boolean)read(mode,goal);} StorageArea area(Object goal){return (StorageArea)read(area,goal);}
        List<?> queue(Object goal){return (List<?>)read(queue,goal);} BlockPos position(Object goal){return (BlockPos)read(position,goal);}
        Container container(Object goal){return (Container)read(container,goal);} int timer(Object goal){return (int)read(timer,goal);}
    }
}
