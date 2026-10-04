package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.*;

/**
 * Bounded cross-file creation intent. A saved intent never grants another creation permit.
 * The caller must reconcile the exact existing marker, worker and lease after reload.
 * Mutations require the exact current project in the same authoritative core compound.
 * Store/journal initialization belongs to the original synchronous commission transaction;
 * a missing journal during recovery is not permission to initialize one again.
 * This is a recovery fence, not a claim of atomic Minecraft entity/SavedData persistence.
 */
public final class PerimeterStageJournal {
    public static final String KEY="PerimeterStageRuntime";
    public enum State { INTENT, LIVE, RETIRED }
    public record Attempt(int stage, UUID area, BlockPos marker, State state) {
        public Attempt { marker=marker.immutable(); }
    }
    public record Entry(UUID project,long generation,String hash,List<Attempt> attempts) {
        public Entry { attempts=List.copyOf(attempts); }
        public Attempt at(int stage) { return stage>=0 && stage<attempts.size()?attempts.get(stage):null; }
    }
    /** One transient permit for the issued project revision and exact intent. Never serialized. */
    public static final class CreationPermit {
        private final CompoundTag core;
        private final PerimeterProject.Check issued;
        private final Attempt intent;
        private boolean used;
        private CreationPermit(CompoundTag core,PerimeterProject project,Attempt intent) {
            this.core=core;this.issued=project.check();this.intent=intent;
        }
        public boolean claim(CompoundTag core,PerimeterProject project) {
            if(used || this.core!=core || !creationState(project) || !issued.equals(project.check())
                    || !current(core,project)) return false;
            Entry entry=get(core,project); Attempt attempt=entry==null?null:entry.at(issued.activeStage());
            if(!intent.equals(attempt)) return false;
            used=true;return true;
        }
    }
    private PerimeterStageJournal() {}

    public static Entry get(CompoundTag core,PerimeterProject project) {
        Entry entry=read(core).get(project.header().projectId());
        if(entry!=null) validate(entry,project);
        return entry;
    }
    public static void prepare(CompoundTag core,PerimeterProject project,Runnable dirty) {
        authoritative(core,project);
        if(project.state()!=PerimeterProject.State.PREPARED_UNPAID || project.revision()!=0 || project.payment()!=null)
            throw invalid("Journal requires a fresh unpaid manifest");
        var entries=read(core); Entry old=entries.get(project.header().projectId());
        if(old!=null) {validate(old,project);dirty.run();return;}
        entries.put(project.header().projectId(),new Entry(project.header().projectId(),project.header().generation(),project.manifestHash(),List.of()));
        write(core,entries,dirty);
    }
    /** Null means this intent already exists: reconnect or pause; never create it again. */
    public static CreationPermit begin(CompoundTag core,PerimeterProject project,BlockPos marker,Runnable dirty) {
        authoritative(core,project);
        if(!creationState(project))
            throw invalid("A native stage cannot be created in this state");
        var entries=read(core);Entry entry=required(entries,project);Attempt previous=entry.at(project.activeStage());
        if(previous!=null) {
            if(!previous.marker().equals(marker)) throw invalid("The attempted marker position changed");
            dirty.run();return null;
        }
        if(entry.attempts().size()!=project.activeStage()
                || entry.attempts().stream().anyMatch(a->a.state()!=State.RETIRED)) throw invalid("Previous native stages are not reconciled");
        validPosition(marker);
        Attempt intent=new Attempt(project.activeStage(),project.active().areaId(),marker,State.INTENT);
        var attempts=new ArrayList<>(entry.attempts());attempts.add(intent);
        entries.put(entry.project(),new Entry(entry.project(),entry.generation(),entry.hash(),attempts));
        write(core,entries,dirty);return new CreationPermit(core,project,intent);
    }
    /** Call only after the exact entity has joined and its immutable accepted contract has been checked. */
    public static void live(CompoundTag core,PerimeterProject project,Runnable dirty) {
        transition(core,project,State.LIVE,dirty);
    }
    /** Call only after exact worker detachment, known marker removal and child lease retirement. */
    public static void retired(CompoundTag core,PerimeterProject project,Runnable dirty) {
        transition(core,project,State.RETIRED,dirty);
    }
    private static void transition(CompoundTag core,PerimeterProject project,State next,Runnable dirty) {
        authoritative(core,project);
        if(next==State.LIVE && project.state()!=PerimeterProject.State.PREPARED_UNPAID
                && project.state()!=PerimeterProject.State.PREPARED_PAID && project.state()!=PerimeterProject.State.RUNNING
                && project.state()!=PerimeterProject.State.WAITING_FOR_NEXT_STAGE
                || next==State.RETIRED && project.state()!=PerimeterProject.State.STAGE_VERIFIED
                && project.state()!=PerimeterProject.State.CANCELED) throw invalid("Native stage transition conflicts with authoritative progress");
        var entries=read(core);Entry entry=required(entries,project);Attempt current=entry.at(project.activeStage());
        if(current==null) throw invalid("Native stage intent is missing");
        if(current.state()==next) {dirty.run();return;}
        if(next==State.LIVE && current.state()!=State.INTENT) throw invalid("Native stage recovery cannot rewind or skip cleanup");
        var attempts=new ArrayList<>(entry.attempts());attempts.set(current.stage(),new Attempt(current.stage(),current.area(),current.marker(),next));
        entries.put(entry.project(),new Entry(entry.project(),entry.generation(),entry.hash(),attempts));write(core,entries,dirty);
    }
    /** Full runtime evidence is removed only after the caller commits an exact compact terminal receipt. */
    public static void compacted(CompoundTag core,PerimeterTerminalReceipt terminal,Runnable dirty) {
        PerimeterTerminalReceipt committed=PerimeterProjectStore.terminal(core,terminal.projectId());
        if(committed==null || !committed.receiptHash().equals(terminal.receiptHash())) throw invalid("Exact terminal compaction is not committed");
        var entries=read(core);Entry entry=entries.get(terminal.projectId());
        if(entry==null) {dirty.run();return;}
        if(!entry.hash().equals(terminal.manifestHash()) || entry.generation()!=terminal.generation()
                || entry.attempts().size()<terminal.verifiedStages()
                || entry.attempts().size()>Math.min(terminal.totalStageCount(),terminal.activeStage()+1)
                || entry.attempts().stream().anyMatch(a->a.state()!=State.RETIRED)) throw invalid("Native runtime cleanup is incomplete");
        for(Attempt attempt:entry.attempts()) if(!attempt.area().equals(terminal.stages().get(attempt.stage()).areaId()))
            throw invalid("Native runtime cleanup identities differ from the committed terminal");
        entries.remove(terminal.projectId());write(core,entries,dirty);
    }
    private static boolean creationState(PerimeterProject project) {
        return project!=null && project.active()!=null && (project.state()==PerimeterProject.State.PREPARED_UNPAID
                || project.state()==PerimeterProject.State.WAITING_FOR_NEXT_STAGE);
    }
    private static boolean current(CompoundTag core,PerimeterProject project) {
        PerimeterProject stored=PerimeterProjectStore.get(core,project.header().projectId());
        return stored!=null && stored.sameSnapshot(project);
    }
    private static void authoritative(CompoundTag core,PerimeterProject project) {
        if(project==null || !current(core,project)) throw invalid("Native stage operation requires the exact authoritative project snapshot");
    }
    private static Entry required(Map<UUID,Entry> entries,PerimeterProject project) {
        Entry entry=entries.get(project.header().projectId());if(entry==null)throw invalid("Native stage journal is missing");
        validate(entry,project);return entry;
    }
    private static void validate(Entry entry,PerimeterProject project) {
        if(entry.generation()!=project.header().generation() || !entry.hash().equals(project.manifestHash())
                || entry.attempts().size()>project.stages().size()) throw invalid("Native stage journal identity conflicts");
        for(Attempt attempt:entry.attempts()) if(!attempt.area().equals(project.stages().get(attempt.stage()).areaId()))
            throw invalid("Native stage journal area identity conflicts");
    }
    private static LinkedHashMap<UUID,Entry> read(CompoundTag core) {
        var entries=new LinkedHashMap<UUID,Entry>();if(!core.contains(KEY))return entries;
        if(!core.contains(KEY,Tag.TAG_COMPOUND))throw invalid("Malformed native stage journal");
        var root=core.getCompound(KEY);
        if(!root.getAllKeys().equals(Set.of("Version","Entries")) || !root.contains("Version",Tag.TAG_INT)
                || root.getInt("Version")!=1 || !root.contains("Entries",Tag.TAG_LIST))throw invalid("Unknown native stage journal format");
        ListTag list=(ListTag)root.get("Entries");
        if(list.size()>PerimeterProjectStore.MAX_PROJECTS || !list.isEmpty()&&list.getElementType()!=Tag.TAG_COMPOUND)
            throw invalid("Invalid native stage journal list");
        Set<UUID> identities=new HashSet<>();
        for(Tag raw:list) {
            var tag=(CompoundTag)raw;
            if(!tag.getAllKeys().equals(Set.of("Id","Generation","Hash","Attempts")) || !tag.hasUUID("Id")
                    || !tag.contains("Generation",Tag.TAG_LONG) || tag.getLong("Generation")<1
                    || !tag.contains("Hash",Tag.TAG_STRING) || !tag.getString("Hash").matches("[0-9a-f]{64}")
                    || !tag.contains("Attempts",Tag.TAG_LIST))throw invalid("Malformed native stage recovery record");
            UUID id=tag.getUUID("Id");unique(identities,id);ListTag encoded=(ListTag)tag.get("Attempts");
            if(encoded.size()>PerimeterStageLayout.MAX_STAGES || !encoded.isEmpty()&&encoded.getElementType()!=Tag.TAG_COMPOUND)
                throw invalid("Invalid native stage attempt list");
            var attempts=new ArrayList<Attempt>();
            for(Tag cell:encoded) {
                var value=(CompoundTag)cell;
                if(!value.getAllKeys().equals(Set.of("Index","Area","Marker","State")) || !value.contains("Index",Tag.TAG_INT)
                        || value.getInt("Index")!=attempts.size() || !value.hasUUID("Area")
                        || !value.contains("Marker",Tag.TAG_LONG) || !value.contains("State",Tag.TAG_STRING))throw invalid("Malformed native stage attempt");
                UUID area=value.getUUID("Area");unique(identities,area);BlockPos marker=BlockPos.of(value.getLong("Marker"));validPosition(marker);
                State state;try{state=State.valueOf(value.getString("State"));}catch(RuntimeException wrong){throw invalid("Unknown native stage attempt state");}
                if(!attempts.isEmpty() && attempts.get(attempts.size()-1).state()!=State.RETIRED)throw invalid("Unfinished earlier native stage attempt");
                attempts.add(new Attempt(attempts.size(),area,marker,state));
            }
            entries.put(id,new Entry(id,tag.getLong("Generation"),tag.getString("Hash"),attempts));
        }
        return entries;
    }
    private static void write(CompoundTag core,Map<UUID,Entry> entries,Runnable dirty) {
        if(entries.size()>PerimeterProjectStore.MAX_PROJECTS)throw invalid("Native recovery record capacity reached");
        var root=new CompoundTag();root.putInt("Version",1);var list=new ListTag();
        entries.values().stream().sorted(Comparator.comparing(Entry::project)).forEach(entry->{
            var tag=new CompoundTag();tag.putUUID("Id",entry.project());tag.putLong("Generation",entry.generation());tag.putString("Hash",entry.hash());
            var attempts=new ListTag();for(Attempt attempt:entry.attempts()){
                var value=new CompoundTag();value.putInt("Index",attempt.stage());value.putUUID("Area",attempt.area());
                value.putLong("Marker",attempt.marker().asLong());value.putString("State",attempt.state().name());attempts.add(value);
            }
            tag.put("Attempts",attempts);list.add(tag);
        });root.put("Entries",list);core.put(KEY,root);dirty.run();
    }
    private static void validPosition(BlockPos pos) {
        if(pos==null || Math.abs((long)pos.getX())>=30_000_000 || Math.abs((long)pos.getZ())>=30_000_000
                || pos.getY()<-2048 || pos.getY()>2047)
            throw invalid("Invalid native marker position");
    }
    private static void unique(Set<UUID> ids,UUID id) {if(id.equals(new UUID(0,0))||!ids.add(id))throw invalid("Reused native recovery identity");}
    private static IllegalArgumentException invalid(String reason){return new IllegalArgumentException(reason);}
}
