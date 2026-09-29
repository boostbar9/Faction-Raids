package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.nbt.CompoundTag;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CivilianLedgerTest extends MinecraftTestSupport {
    @Test void taxesPayOnlyCompletedIndividualDaysAndNeverTwice() {
        var ledger=new CompoundTag();var core=new CompoundTag();
        CivilianLedger.register(ledger,UUID.randomUUID(),100);
        CivilianLedger.register(ledger,UUID.randomUUID(),200);
        assertEquals(0,CivilianLedger.settle(ledger,core,24099,true));
        assertEquals(1,CivilianLedger.settle(ledger,core,24100,true));
        assertEquals(0,CivilianLedger.settle(ledger,core,24100,true));
        assertEquals(1,CivilianLedger.settle(ledger,core,24200,true));
        assertEquals(2,FactionBank.balance(core));
    }
    @Test void deathStopsIncomeWhileReloadPreservesClocksAndStarterCount() {
        var data=new RaidSavedData();var ledger=CoreCivilians.ledger(data,"team:test");
        UUID id=UUID.randomUUID();CivilianLedger.register(ledger,id,100);ledger.putInt("Starters",2);
        var loaded=RaidSavedData.load(data.save(new CompoundTag()));var restored=CoreCivilians.ledger(loaded,"team:test");
        assertEquals(2,restored.getInt("Starters"));assertEquals(1,CivilianLedger.count(restored));
        CivilianLedger.register(restored,id,20000);
        var core=new CompoundTag();assertEquals(1,CivilianLedger.settle(restored,core,24100,true));
        CivilianLedger.remove(restored,id);assertEquals(0,CivilianLedger.settle(restored,core,48100,true));
    }
    @Test void noCoreOrOccupationCannotBankBackTaxesAndClockRewindDoesNotPay() {
        var ledger=new CompoundTag();var core=new CompoundTag();
        CivilianLedger.register(ledger,UUID.randomUUID(),0);
        assertEquals(0,CivilianLedger.settle(ledger,core,48000,false));
        assertEquals(0,CivilianLedger.settle(ledger,core,48001,true));
        assertEquals(0,CivilianLedger.settle(ledger,core,100,true));
        assertEquals(1,CivilianLedger.settle(ledger,core,24100,true));
    }
    @Test void populationAndTreasuryAreBounded() {
        var ledger=new CompoundTag();var core=new CompoundTag();
        for(int i=0;i<64;i++)assertTrue(CivilianLedger.register(ledger,UUID.randomUUID(),0));
        assertFalse(CivilianLedger.register(ledger,UUID.randomUUID(),0));
        core.putLong("BankEmeralds",FactionBank.LIMIT-2);
        assertEquals(2,CivilianLedger.settle(ledger,core,24000,true));
        assertEquals(FactionBank.LIMIT,FactionBank.balance(core));
        assertEquals(0,CivilianLedger.settle(ledger,core,24000,true));
    }
}
