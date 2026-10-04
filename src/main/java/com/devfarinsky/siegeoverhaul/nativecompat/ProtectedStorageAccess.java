package com.devfarinsky.siegeoverhaul.nativecompat;

import com.talhanation.recruits.entities.ai.RecruitUpkeepEntityGoal;
import com.talhanation.recruits.entities.ai.RecruitUpkeepPosGoal;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.entities.ai.*;
import com.talhanation.workers.entities.workarea.StorageArea;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;

/** Original native supply/deposit lifecycle with bounded read/identity/capacity and persistence boundaries. */
final class ProtectedStorageAccess extends ProtectedInventoryGoal {
    enum Kind { NEEDED, DEPOSIT, POSITION_UPKEEP, ENTITY_UPKEEP }
    private static final String DIRTY_WRITES="SiegeProtectedStorageDirtyWrites";
    private final AbstractChestGoal chest;
    final Kind kind;
    private StorageArea scannedArea;
    private AABB scannedBounds;
    private String phase;
    private ProtectedStorageContext.Source selected;

    private ProtectedStorageAccess(BuilderEntity worker, AbstractChestGoal delegate, Kind kind, Session session) {
        super(worker,delegate,session);this.chest=delegate;this.kind=kind;
    }
    static String runningProblem(Mob worker) {
        if(worker.goalSelector==null)return null; // Installation independently rejects an unavailable selector.
        for(var wrapped:worker.goalSelector.getAvailableGoals())
            if(family(wrapped.getGoal()) && (wrapped.isRunning()
                    || wrapped.getGoal() instanceof ProtectedInventoryGoal adapter && !adapter.session.pending.isEmpty()))
                return "Builder is finishing native storage/upkeep; wait before commissioning. No payment taken.";
        if(worker instanceof BuilderEntity builder && builder.neededItems!=null && !builder.neededItems.isEmpty())
            return "Builder has unfinished native supply requests; let them finish before commissioning. No payment taken.";
        return null;
    }
    private static boolean family(Goal goal) {
        return goal instanceof ProtectedInventoryGoal || goal instanceof GetNeededItemsFromStorage
                || goal instanceof DepositItemsToStorage || goal instanceof RecruitUpkeepPosGoal || goal instanceof RecruitUpkeepEntityGoal;
    }
    private static Kind kind(Goal goal) {
        if(goal instanceof ProtectedStorageAccess access)return access.kind;
        if(goal instanceof ProtectedUpkeepAccess access)return access.kind;
        if(goal.getClass()==GetNeededItemsFromStorage.class)return Kind.NEEDED;
        if(goal.getClass()==DepositItemsToStorage.class)return Kind.DEPOSIT;
        if(goal.getClass()==RecruitUpkeepPosGoal.class)return Kind.POSITION_UPKEEP;
        if(goal.getClass()==RecruitStorageUpkeepGoal.class || goal.getClass()==RecruitUpkeepEntityGoal.class)return Kind.ENTITY_UPKEEP;
        throw new IllegalStateException("Unknown native inventory-goal subclass");
    }
    static boolean install(Mob mob) {
        if(!(mob instanceof BuilderEntity worker)||mob.goalSelector==null)return false;
        try {
            var goals=new ArrayList<>(worker.goalSelector.getAvailableGoals());
            if(goals.size()>128)return false;
            var managed=new EnumMap<Kind,WrappedGoal>(Kind.class);
            Session session=null;
            for(var wrapped:goals)if(family(wrapped.getGoal())) {
                Kind kind=kind(wrapped.getGoal());
                if(managed.put(kind,wrapped)!=null)return false;
                if(wrapped.getGoal() instanceof ProtectedInventoryGoal existing) {
                    if(existing.worker!=worker || session!=null&&session!=existing.session)return false;
                    session=existing.session;
                } else if(wrapped.isRunning())return false; // removeGoal would call the unguarded native stop.
            }
            if(managed.size()!=Kind.values().length)return false;
            if(session==null)session=new Session(worker);
            var originals=new ArrayList<WrappedGoal>();var replacements=new ArrayList<Goal>();
            for(var entry:managed.entrySet()) {
                var wrapped=entry.getValue();if(wrapped.getGoal() instanceof ProtectedInventoryGoal)continue;
                Goal nativeGoal=wrapped.getGoal();
                Goal replacement;
                if(nativeGoal instanceof AbstractChestGoal chest) {
                    if(chest.worker!=worker)return false;
                    replacement=new ProtectedStorageAccess(worker,chest,entry.getKey(),session);
                } else replacement=new ProtectedUpkeepAccess(worker,nativeGoal,entry.getKey(),session);
                originals.add(wrapped);replacements.add(replacement);
            }
            // Every class, owner, private read capability and running state is resolved before selector mutation.
            for(int i=0;i<originals.size();i++) {
                var original=originals.get(i);worker.goalSelector.removeGoal(original.getGoal());
                worker.goalSelector.addGoal(original.getPriority(),replacements.get(i));
            }
            return true;
        } catch(ReflectiveOperationException|RuntimeException|LinkageError unsupported){return false;}
    }
    private String phase() {
        Enum<?> state=chest instanceof GetNeededItemsFromStorage needed?needed.state:((DepositItemsToStorage)chest).state;
        if(state==null)throw new IllegalStateException("Native storage state unavailable");return state.name();
    }
    @Override String beforeStart(){return null;}
    @Override void afterStart(){scannedArea=null;scannedBounds=null;selected=null;}
    @Override String beforeTick() {
        phase=phase(); selected=null;
        String requests=ProtectedTransferCapacity.requestsProblem(worker);if(requests!=null)return requests;
        if(phase.equals("SELECT_STORAGE")||phase.startsWith("ERROR_")||phase.equals("DONE"))return null;
        AABB bounds=ProtectedStorageContext.storage(worker,chest.storageArea,true);
        if(phase.equals("MOVE_TO_STORAGE"))return NativeConstructionGuard.storageProblem(worker,Set.of(chest.storageArea.blockPosition()));
        if(phase.equals("SCAN_STORAGE")) {
            Set<BlockPos> reads=ProtectedStorageContext.scan(worker,chest.storageArea,true);
            String authority=NativeConstructionGuard.storageProblem(worker,reads);if(authority!=null)return authority;
            // Prevalidate all containers the original scan may expose to live native predicates.
            var level=ProtectedStorageContext.level(worker);
            for(BlockPos pos:BlockPos.betweenClosedStream(bounds).map(BlockPos::immutable).toList())
                if(level.getBlockState(pos.above()).isAir()) {
                    ProtectedStorageContext.blockImplementation(level,pos);
                    Container container=chest.storageArea.getContainer(pos);
                    if(container!=null)ProtectedStorageContext.source(level,container,pos);
                }
            scannedArea=chest.storageArea;scannedBounds=bounds;return null;
        }
        if(scannedArea!=chest.storageArea || scannedBounds==null || !scannedBounds.equals(bounds))
            return "Paused: native storage source moved or its scan is stale; refresh the source";
        if(phase.equals("SELECT_CHEST"))return null;
        if(chest.chestPos==null || !ProtectedStorageContext.contains(bounds,chest.chestPos))
            return "Paused: native chest target escaped the current storage area";
        var level=ProtectedStorageContext.level(worker);
        Set<BlockPos> reads=ProtectedStorageContext.envelope(level,new AABB(chest.chestPos,chest.chestPos),1,1);
        String authority=NativeConstructionGuard.storageProblem(worker,reads);if(authority!=null)return authority;
        if(phase.equals("MOVE_TO_CHEST"))return null;
        Container cached=phase.equals("CHECK_CHEST")?chest.storageArea.storageMap.get(chest.chestPos):chest.container;
        if(cached==null && phase.equals("CHECK_CHEST"))return null; // Original native missing-container retry has no transfer.
        if(cached==null || chest.storageArea.storageMap.get(chest.chestPos)!=cached)
            return "Paused: native cached container selection changed";
        selected=ProtectedStorageContext.source(level,cached,chest.chestPos);
        authority=NativeConstructionGuard.storageProblem(worker,selected.cells());if(authority!=null)return authority;
        if(phase.equals("TAKE_NEEDED_ITEMS")) {
            String capacity=ProtectedTransferCapacity.problem(worker,cached);if(capacity!=null)return capacity;
            writes=List.of(cached);
        } else if(phase.equals("DEPOSIT")) {
            String deposit=ProtectedTransferCapacity.depositProblem(worker,cached);if(deposit!=null)return deposit;
            writes=List.of(cached);
        }
        return null;
    }
    @Override void afterTick() {
        if(phase.equals("SCAN_STORAGE")) {
            if(scannedArea!=chest.storageArea || !scannedBounds.equals(ProtectedStorageContext.storage(worker,scannedArea,true)))
                throw new IllegalStateException("Native scan source changed");
            for(var entry:scannedArea.storageMap.entrySet()) {
                if(!ProtectedStorageContext.contains(scannedBounds,entry.getKey()))throw new IllegalStateException("Native scan map escaped its envelope");
                ProtectedStorageContext.source(ProtectedStorageContext.level(worker),entry.getValue(),entry.getKey());
            }
        }
        if(selected!=null && !writes.isEmpty()) {
            if(chest.container!=selected.container() || !selected.pos().equals(chest.chestPos)
                    || chest.storageArea!=scannedArea || scannedArea.storageMap.get(selected.pos())!=selected.container())
                throw new IllegalStateException("Native transfer replaced its source");
            if(!phase().startsWith("CLOSE_CHEST_"))throw new IllegalStateException("Unsupported native transfer transition");
        }
    }
    @Override String cleanup() {
        if(chest.chestPos!=null && chest.container!=null)
            ProtectedStorageContext.cleanup(ProtectedStorageContext.level(worker),chest.container,chest.chestPos);
        return null;
    }
    @Override void stopped(){scannedArea=null;scannedBounds=null;selected=null;writes=List.of();}
    static void notifyAfterNativeTransfer(Container selected,Runnable tick){try{tick.run();}finally{selected.setChanged();}}
    static void recordDirtyNotification(Mob worker) {
        var data=worker.getPersistentData();data.putInt(DIRTY_WRITES,(int)Math.min(Integer.MAX_VALUE,(long)dirtyNotifications(worker)+1));
    }
    static int dirtyNotifications(Mob worker){return Math.max(0,worker.getPersistentData().getInt(DIRTY_WRITES));}
}
