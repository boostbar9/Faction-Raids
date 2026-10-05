package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.compat.RecruitsClaimsBridge;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.core.*;
import com.talhanation.workers.entities.BuilderEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

import java.util.*;
import java.util.stream.Collectors;

/** Internal first production vertical slice. No packet/menu calls this until native review gates pass. */
final class EarthworksCommission {
    private static final String DEBITS="SiegeEarthworksDebitsV1";
    private EarthworksCommission(){}
    static final class Review {
        private final PerimeterEarthworksManifest manifest;
        private final BlockPos core,marker;
        private final UUID area;
        private final PerimeterEarthworksJournal.Binding binding;
        private final long expires;
        private Review(PerimeterEarthworksManifest manifest,BlockPos core,BlockPos marker,UUID area,PerimeterEarthworksJournal.Binding binding,long expires){
            this.manifest=manifest;this.core=core.immutable();this.marker=marker.immutable();this.area=area;this.binding=binding;this.expires=expires;
        }
    }
    static String claimsDigest(RecruitsClaimsBridge.TerritorySnapshot territory){
        if(territory==null||!territory.ready())throw new IllegalArgumentException("Current exact territory unavailable");
        var values=new ArrayList<Object>();values.add("local-earthworks-claims-v1");values.add(territory.factionStringId());
        territory.chunks().stream().map(ChunkPos::toLong).sorted().forEach(values::add);return NativeEarthworksAdapter.evidenceHash(values.toArray());
    }
    static Review reviewLocal(ServerPlayer owner,BuilderEntity builder,PerimeterEarthworksManifest manifest,BlockPos core,BlockPos marker){
        requireIdentity(owner,builder,manifest,core);
        if(manifest.steps().size()!=1 || manifest.steps().get(0).kind()!=PerimeterEarthworksManifest.Kind.FILL
                || !manifest.steps().get(0).after().equals(Blocks.DIRT.defaultBlockState()))
            throw new IllegalArgumentException("First native slice admits one exact dirt fill; cut/drop and multi-step recovery remain gated");
        if(manifest.observations().size()>NativeEarthworksAdapter.MAX_OBSERVATIONS)throw new IllegalArgumentException("Snapshot exceeds local bound");
        var ledger=ConstructionEditLedger.get(owner.serverLevel());
        var binding=new PerimeterEarthworksJournal.Binding(ledger.generation(),NativeEarthworksAdapter.evidenceHash("review",manifest.hash(),UUID.randomUUID()),
                NativeEarthworksAdapter.evidenceHash("payment",manifest.hash(),UUID.randomUUID()));
        var review=new Review(manifest,core,marker,UUID.randomUUID(),binding,owner.serverLevel().getGameTime()+1200);
        // Pure temporary objects: preview does not reserve cells, add an entity, mutate inventory or charge the Treasury.
        var temporary=new EarthworksJobLedger().prepare(manifest,binding,review.area,core);
        var candidate=create(owner,temporary,marker);revalidate(owner,builder,review,temporary,candidate);return review;
    }
    static UUID acceptLocal(ServerPlayer owner,BuilderEntity builder,Review review){
        Objects.requireNonNull(review);requireIdentity(owner,builder,review.manifest,review.core);
        var level=owner.serverLevel();var jobs=EarthworksJobLedger.get(level);var existing=jobs.job(review.manifest.header().project());
        if(existing!=null){
            if(retryMatches(existing,review.area,review.manifest.hash(),review.binding,review.core)
                    &&paidMatches(level,existing))return existing.area;
            throw new IllegalStateException("Retained preparation or payment needs recovery review; no repeat debit");
        }
        if(level.getGameTime()>review.expires)throw new IllegalStateException("Review expired; no payment taken");
        if(builder.currentBuildArea!=null||NativeConstructionGuard.hasProtectedReceipt(builder)||NativeEarthworksJobs.selected(builder)
                ||ProtectedStorageAccess.runningProblem(builder)!=null)throw new IllegalStateException("Builder is not idle for new grading");
        var edits=ConstructionEditLedger.get(level);
        if(!edits.canRetainHandLifecycle(builder.getPersistentData(),builder.getUUID()))throw new IllegalStateException("Hand history cannot accept the new job");
        var temporary=new EarthworksJobLedger().prepare(review.manifest,review.binding,review.area,review.core);
        var area=create(owner,temporary,review.marker);revalidate(owner,builder,review,temporary,area);
        var data=RaidSavedData.get(owner.server);String key="team:"+review.manifest.header().faction();var core=data.siegeCores.get(key);
        if(core==null||FactionBank.balance(core)<review.manifest.header().price())throw new IllegalStateException("Faction Treasury lacks the reviewed fee");
        var paidCore=core.copy();long bankBefore=FactionBank.balance(core);long[] historyBefore=core.getLongArray("BankLedger").clone();ListTag debits=readDebits(paidCore);
        if(debits.size()>=EarthworksJobLedger.MAX_JOBS)throw new IllegalStateException("Retained grading payment history is full");
        if(core.contains("BankEmeralds")&&!core.contains("BankEmeralds",Tag.TAG_LONG)||core.contains("BankLedger")&&!core.contains("BankLedger",Tag.TAG_LONG_ARRAY)
                ||core.getLongArray("BankLedger").length>FactionBank.LEDGER_MAX)throw new IllegalStateException("Malformed Treasury history");
        if(!FactionBank.debit(paidCore,review.manifest.header().price()))throw new IllegalStateException("Faction Treasury changed");
        FactionBank.record(paidCore,-review.manifest.header().price());
        CompoundTag debit=new CompoundTag();debit.putUUID("Project",review.manifest.header().project());debit.putUUID("Area",review.area);
        debit.putString("Manifest",review.manifest.hash());debit.putString("Receipt",review.binding.paymentReceipt());debit.putInt("Price",review.manifest.header().price());
        for(Tag row:debits)if(((CompoundTag)row).getUUID("Project").equals(review.manifest.header().project()))throw new IllegalStateException("Duplicate grading payment identity");
        debits.add(debit);paidCore.put(DEBITS,debits);
        if(!EarthworksWorkGoal.install(builder,()->NativeEarthworksJobs.workGoal(builder))||!ProtectedStorageAccess.install(builder))
            throw new IllegalStateException("Exclusive native work/storage hooks unavailable; no payment taken");
        Set<BlockPos> reservation=review.manifest.observations().keySet().stream().map(BlockPos::of).collect(Collectors.toSet());
        if(edits.reserves(reservation)||!edits.register(review.area,reservation))throw new IllegalStateException("Exact snapshot reservation changed");
        var job=jobs.prepare(review.manifest,review.binding,review.area,review.core);
        builder.getPersistentData().put(NativeEarthworksJobs.KEY,NativeEarthworksJobs.selector(job));
        ProtectedBuilderHandMirror.arm(builder.getPersistentData());
        if(ProtectedBuilderHandMirror.restore(builder)!=null||!edits.retainHandLifecycle(builder.getPersistentData(),builder.getUUID(),owner.getUUID(),review.area))
            throw new IllegalStateException("New hand provenance needs review; unpaid reservation retained");
        if(!level.addFreshEntity(area))throw new IllegalStateException("Native marker could not be registered; unpaid job retained");
        builder.currentBuildArea=area;
        // Bank values and the matching payment receipt share one authoritative core compound write. The separate job/entity files still need recovery proof.
        if(FactionBank.balance(core)!=bankBefore||!Arrays.equals(historyBefore,core.getLongArray("BankLedger"))
                ||!FactionBank.debit(core,review.manifest.header().price()))throw new IllegalStateException("Treasury changed during admission; unpaid job retained");
        core.putLongArray("BankLedger",paidCore.getLongArray("BankLedger"));
        core.put(DEBITS,paidCore.get(DEBITS).copy());data.setDirty();job.acknowledgeDebit();
        return area.getUUID();
    }
    private static EarthworksBuildArea create(ServerPlayer owner,EarthworksJobLedger.Job job,BlockPos marker){
        var area=ProtectedConstructionAreas.EARTHWORKS_TYPE.get().create(owner.serverLevel());if(area==null)throw new IllegalStateException("Local grading marker unavailable");
        area.setUUID(job.area);area.initialize(job,marker);return area;
    }
    private static void requireIdentity(ServerPlayer owner,BuilderEntity builder,PerimeterEarthworksManifest manifest,BlockPos core){
        if(owner==null||builder==null||manifest==null||core==null||owner.serverLevel()!=builder.level()||!owner.server.isSameThread()
                ||!owner.getUUID().equals(manifest.header().owner())||!builder.getUUID().equals(manifest.header().builder())
                ||!owner.getUUID().equals(WorkersBridge.readWorkerOwner(builder))||!SiegeCore.key(owner).equals("team:"+manifest.header().faction())
                ||!owner.serverLevel().dimension().location().toString().equals(manifest.header().dimension()))throw new IllegalArgumentException("Foreign local grading review");
        String runtime=WorkersConstructionRuntime.problem();if(runtime!=null)throw new IllegalStateException(runtime);
    }
    private static void revalidate(ServerPlayer owner,BuilderEntity builder,Review review,EarthworksJobLedger.Job job,EarthworksBuildArea area){
        var level=owner.serverLevel();var territory=RecruitsClaimsBridge.getFactionTerritory(level,review.manifest.header().faction(),PerimeterTerritory.MAX_CHUNKS);
        if(!claimsDigest(territory).equals(review.manifest.header().claimsDigest()))throw new IllegalStateException("Exact territory changed");
        if(!ConstructionEditLedger.get(level).sameGeneration(review.binding.ledgerGeneration()))throw new IllegalStateException("World reservation generation changed");
        Set<BlockPos> writes=new HashSet<>();
        for(var observation:review.manifest.observations().values()){
            BlockPos pos=BlockPos.of(observation.pos());
            if(!level.hasChunkAt(pos)||!level.getWorldBorder().isWithinBounds(pos)||pos.getY()<level.getMinBuildHeight()||pos.getY()>=level.getMaxBuildHeight()
                    ||!level.getBlockState(pos).equals(observation.original()))throw new IllegalStateException("Original loaded terrain changed");
            if(observation.role()==PerimeterEarthworksManifest.Role.WORK)writes.add(pos);
        }
        String policy=NativeConstructionPolicy.problem(level,builder,area,owner.getUUID(),"team:"+review.manifest.header().faction(),review.core,writes);
        if(policy!=null)throw new IllegalStateException(policy);
        var step=review.manifest.steps().get(0);
        String support=WorkersEarthworksPort.fillSupportProblem(level,review.manifest,step);if(support!=null)throw new IllegalStateException(support);
        if(!level.getEntities((net.minecraft.world.entity.Entity)null,new net.minecraft.world.phys.AABB(BlockPos.of(step.pos())),NativeConstructionGuard::blocksPlacement).isEmpty())
            throw new IllegalStateException("An entity occupies the exact fill target; no payment taken");
        writes.add(review.marker);writes.add(review.marker.below());writes.add(review.marker.above());
        String markerPolicy=NativeConstructionPolicy.problem(level,builder,area,owner.getUUID(),"team:"+review.manifest.header().faction(),review.core,writes);
        if(markerPolicy!=null)throw new IllegalStateException(markerPolicy);
        String standing=EarthworksStandingAccess.problem(level,builder,area,job);if(standing!=null)throw new IllegalStateException(standing);
        String neighborhood=NativeConstructionGuard.neighborhoodProblem(level,BlockPos.of(review.manifest.steps().get(0).pos()));if(neighborhood!=null)throw new IllegalStateException(neighborhood);
        if(level.captureBlockSnapshots||level.restoringBlockSnapshots)throw new IllegalStateException("Another world transaction owns this region");
    }
    static boolean retryMatches(EarthworksJobLedger.Job job,UUID area,String manifestHash,PerimeterEarthworksJournal.Binding binding,BlockPos core){
        return job!=null&&job.area.equals(area)&&job.manifest.hash().equals(manifestHash)&&job.read().journal().binding().equals(binding)
                &&job.core.equals(core)&&job.active()&&job.read().inFlight()==null;
    }
    static boolean paidMatches(net.minecraft.server.level.ServerLevel level,EarthworksJobLedger.Job job){
        try {
            return paidMatches(RaidSavedData.get(level.getServer()).siegeCores.get("team:"+job.manifest.header().faction()),job);
        }catch(RuntimeException|LinkageError unavailable){return false;}
    }
    static boolean paidMatches(CompoundTag core,EarthworksJobLedger.Job job){
        try {
            if(core==null)return false;
            for(Tag value:readDebits(core)){
                var row=(CompoundTag)value;
                if(row.getUUID("Project").equals(job.manifest.header().project()))return row.getUUID("Area").equals(job.area)
                        &&row.getString("Manifest").equals(job.manifest.hash())&&row.getString("Receipt").equals(job.read().journal().binding().paymentReceipt())
                        &&row.getInt("Price")==job.manifest.header().price();
            }
            return false;
        }catch(RuntimeException|LinkageError unavailable){return false;}
    }
    private static ListTag readDebits(CompoundTag core){
        if(!core.contains(DEBITS))return new ListTag();
        if(!core.contains(DEBITS,Tag.TAG_LIST))throw new IllegalStateException("Malformed grading payment history");
        var rows=(ListTag)core.get(DEBITS);if(rows.size()>EarthworksJobLedger.MAX_JOBS||!rows.isEmpty()&&rows.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalStateException("Malformed grading payment history");
        Set<UUID> projects=new HashSet<>();
        for(Tag value:rows){var row=(CompoundTag)value;
            if(!row.getAllKeys().equals(Set.of("Project","Area","Manifest","Receipt","Price"))||!row.hasUUID("Project")||!row.hasUUID("Area")
                    ||!row.contains("Price",Tag.TAG_INT)||row.getInt("Price")!=64||!row.contains("Manifest",Tag.TAG_STRING)||!row.getString("Manifest").matches("[0-9a-f]{64}")
                    ||!row.contains("Receipt",Tag.TAG_STRING)||!row.getString("Receipt").matches("[0-9a-f]{64}")||!projects.add(row.getUUID("Project")))
                throw new IllegalStateException("Malformed grading payment receipt");
        }
        return rows.copy();
    }
}
