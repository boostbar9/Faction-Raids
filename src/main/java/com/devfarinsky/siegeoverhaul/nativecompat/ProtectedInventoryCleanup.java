package com.devfarinsky.siegeoverhaul.nativecompat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.EnumMap;
import java.util.Map;

/** Bounded durable evidence of unfinished native callbacks, never a recipe for replaying them. */
final class ProtectedInventoryCleanup {
    static final String KEY = "SiegeProtectedInventoryCleanup";
    static final int CLEANUP = 1, REVIEW = 2;
    private ProtectedInventoryCleanup() {}

    static Map<ProtectedStorageAccess.Kind,Integer> read(CompoundTag data) {
        if(data==null)throw new IllegalStateException("Missing native cleanup persistence");
        var entries=new EnumMap<ProtectedStorageAccess.Kind,Integer>(ProtectedStorageAccess.Kind.class);
        if(!data.contains(KEY))return entries;
        if(!data.contains(KEY,Tag.TAG_COMPOUND))throw new IllegalStateException("Invalid native cleanup journal");
        CompoundTag tag=data.getCompound(KEY);
        if(!tag.contains("Version",Tag.TAG_INT) || tag.getInt("Version")!=1
                || tag.getAllKeys().size()>1+ProtectedStorageAccess.Kind.values().length)
            throw new IllegalStateException("Unknown native cleanup journal");
        for(String key:tag.getAllKeys())if(!key.equals("Version")) {
            var kind=ProtectedStorageAccess.Kind.valueOf(key);
            int state=tag.getInt(key);
            if(!tag.contains(key,Tag.TAG_INT) || state!=CLEANUP && state!=REVIEW)
                throw new IllegalStateException("Unknown native cleanup obligation");
            entries.put(kind,state);
        }
        return entries;
    }

    static boolean outstanding(CompoundTag data) {
        try { return !read(data).isEmpty(); }
        catch(RuntimeException unavailable) { return true; }
    }

    static void record(CompoundTag data,ProtectedStorageAccess.Kind kind,int state) {
        var entries=read(data); // Never overwrite a malformed or future journal.
        if(kind==null || state!=CLEANUP && state!=REVIEW)throw new IllegalArgumentException();
        entries.put(kind,state);write(data,entries);
    }

    static void complete(CompoundTag data,ProtectedStorageAccess.Kind kind) {
        var entries=read(data);entries.remove(kind);write(data,entries);
    }

    private static void write(CompoundTag data,Map<ProtectedStorageAccess.Kind,Integer> entries) {
        if(entries.isEmpty()){data.remove(KEY);return;}
        var tag=new CompoundTag();tag.putInt("Version",1);
        entries.forEach((kind,state)->tag.putInt(kind.name(),state));data.put(KEY,tag);
    }
}
