package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Mob;
import java.util.Set;
import java.util.UUID;

/** A builder stays reserved between native sections. This selector grants no construction authority. */
public final class PerimeterProjectLink {
    public static final String KEY="SiegeWholePerimeter";
    public record Link(UUID id,long generation,String hash,String core) {}
    private PerimeterProjectLink() {}
    public static boolean reserved(Mob worker) { return worker!=null && worker.getPersistentData().contains(KEY); }
    public static void set(Mob worker,PerimeterProject project) {
        if(!worker.getUUID().equals(project.header().builder()))throw new IllegalArgumentException("Wrong perimeter builder");
        var expected=new Link(project.header().projectId(),project.header().generation(),project.manifestHash(),project.header().coreKey());
        if(reserved(worker)) { if(!expected.equals(read(worker.getPersistentData())))throw new IllegalArgumentException("Builder has another perimeter reservation");return; }
        var tag=new CompoundTag();tag.putInt("Version",1);tag.putUUID("Project",expected.id());tag.putLong("Generation",expected.generation());
        tag.putString("Hash",expected.hash());tag.putString("Core",expected.core());worker.getPersistentData().put(KEY,tag);
    }
    public static Link read(CompoundTag data) {
        if(!data.contains(KEY,Tag.TAG_COMPOUND))throw new IllegalArgumentException("Perimeter builder reservation is missing");
        var tag=data.getCompound(KEY);
        if((!tag.getAllKeys().equals(Set.of("Version","Project","Generation","Hash","Core"))
                && !tag.getAllKeys().equals(Set.of("Version","Project","Generation","Hash","Core","LedgerGeneration")))
                || !tag.contains("Version",Tag.TAG_INT)||tag.getInt("Version")!=1||!tag.hasUUID("Project")
                ||tag.getUUID("Project").equals(new UUID(0,0))||!tag.contains("Generation",Tag.TAG_LONG)||tag.getLong("Generation")<1
                ||!tag.contains("Hash",Tag.TAG_STRING)||!tag.getString("Hash").matches("[0-9a-f]{64}")
                ||!tag.contains("Core",Tag.TAG_STRING)||!tag.getString("Core").startsWith("team:")
                ||tag.getString("Core").length()<=5||tag.getString("Core").length()>256
                ||tag.contains("LedgerGeneration")&&(!tag.hasUUID("LedgerGeneration")||tag.getUUID("LedgerGeneration").equals(new UUID(0,0))))
            throw new IllegalArgumentException("Malformed perimeter builder reservation");
        return new Link(tag.getUUID("Project"),tag.getLong("Generation"),tag.getString("Hash"),tag.getString("Core"));
    }
    public static boolean matches(Mob worker,PerimeterProject project) {
        try {var link=read(worker.getPersistentData());return worker.getUUID().equals(project.header().builder())
                &&link.equals(new Link(project.header().projectId(),project.header().generation(),project.manifestHash(),project.header().coreKey()));}
        catch(RuntimeException unavailable){return false;}
    }
    /** Preserve the accepted ledger identity while native child receipts are absent between sections. */
    public static boolean bindLedgerGeneration(Mob worker,UUID generation) {
        try {
            read(worker.getPersistentData());if(generation==null||generation.equals(new UUID(0,0)))return false;
            var tag=worker.getPersistentData().getCompound(KEY);
            if(tag.contains("LedgerGeneration")&&!tag.getUUID("LedgerGeneration").equals(generation))return false;
            tag.putUUID("LedgerGeneration",generation);return true;
        }catch(RuntimeException unavailable){return false;}
    }
    public static UUID ledgerGeneration(CompoundTag data) {
        read(data);var tag=data.getCompound(KEY);return tag.hasUUID("LedgerGeneration")?tag.getUUID("LedgerGeneration"):null;
    }
    public static void clear(Mob worker,UUID id,long generation,String hash) {
        if(!reserved(worker))return;var link=read(worker.getPersistentData());
        if(link.id().equals(id)&&link.generation()==generation&&link.hash().equals(hash))worker.getPersistentData().remove(KEY);
    }
}
