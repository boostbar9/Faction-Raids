package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.nbt.CompoundTag;
import java.util.UUID;

/** Saved lifetime grants and individual game-time tax clocks; independent of core relocation. */
public final class CivilianLedger {
    public static final int LIMIT = 64;
    private CivilianLedger() {}
    static boolean grantStarter(CompoundTag ledger,java.util.function.BooleanSupplier spawn) {
        int before=Math.max(0,ledger.getInt("Starters"));
        if(before>=2)return false;
        ledger.putInt("Starters",before+1);
        boolean success=false;
        try {success=spawn.getAsBoolean();return success;}
        finally {if(!success)ledger.putInt("Starters",before);}
    }
    static CompoundTag residents(CompoundTag ledger) {
        if (!ledger.contains("Residents",10)) ledger.put("Residents",new CompoundTag());
        return ledger.getCompound("Residents");
    }
    public static int count(CompoundTag ledger) { return residents(ledger).size(); }
    static boolean register(CompoundTag ledger, UUID id, long now) {
        var entries=residents(ledger); String key=id.toString();
        if(entries.contains(key)) return true;
        if(entries.size()>=LIMIT) return false;
        entries.putLong(key,now); return true;
    }
    static void remove(CompoundTag ledger,UUID id) { residents(ledger).remove(id.toString()); }
    static long settle(CompoundTag ledger,CompoundTag core,long now,boolean eligible) {
        long total=0;var entries=residents(ledger);
        for(String id:entries.getAllKeys()) {
            long last=entries.getLong(id);
            if(now<last || !eligible) { entries.putLong(id,now); continue; }
            long days=(now-last)/FactionBank.DAY_TICKS;
            if(days<=0)continue;
            total+=days; entries.putLong(id,last+days*FactionBank.DAY_TICKS);
        }
        long paid=FactionBank.credit(core,total);
        FactionBank.record(core,(int)paid);
        return paid;
    }
}
