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
        assertArrayEquals(new int[]{1,1},FactionBank.ledgerDeltas(core));
        assertEquals(1,core.getLong("CivilianTaxesLast"));
        assertEquals(2,core.getLong("CivilianTaxesTotal"));
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
    @Test void waitingCiviliansDoNotAccrueBackTaxes() {
        var ledger=new CompoundTag();var core=new CompoundTag();UUID id=UUID.randomUUID();
        CivilianLedger.register(ledger,id,0);CivilianLedger.pause(ledger,id,true,100);
        assertEquals(0,CivilianLedger.settle(ledger,core,48000,true));
        CivilianLedger.pause(ledger,id,false,48010);
        assertEquals(0,CivilianLedger.settle(ledger,core,72009,true));
        assertEquals(1,CivilianLedger.settle(ledger,core,72010,true));
    }
    @Test void starterGrantsRetryFailuresAndCannotRepeatAfterSaving() {
        var ledger=new CompoundTag();
        assertFalse(CivilianLedger.grantStarter(ledger,()->false));assertEquals(0,ledger.getInt("Starters"));
        assertThrows(IllegalStateException.class,()->CivilianLedger.grantStarter(ledger,()->{throw new IllegalStateException();}));
        assertEquals(0,ledger.getInt("Starters"));
        assertTrue(CivilianLedger.grantStarter(ledger,()->true));
        var restored=ledger.copy();assertTrue(CivilianLedger.grantStarter(restored,()->true));
        assertFalse(CivilianLedger.grantStarter(restored,()->{fail("Already granted both civilians");return true;}));
    }
    @Test void populationAndTreasuryAreBounded() {
        var ledger=new CompoundTag();var core=new CompoundTag();
        for(int i=0;i<64;i++)assertTrue(CivilianLedger.register(ledger,UUID.randomUUID(),0));
        assertFalse(CivilianLedger.register(ledger,UUID.randomUUID(),0));
        core.putLong("BankEmeralds",FactionBank.LIMIT-2);
        assertEquals(2,CivilianLedger.settle(ledger,core,24000,true));
        assertEquals(FactionBank.LIMIT,FactionBank.balance(core));
        assertArrayEquals(new int[]{2},FactionBank.ledgerDeltas(core));
        assertEquals(2,core.getLong("CivilianTaxesTotal"));
        assertEquals(0,CivilianLedger.settle(ledger,core,24000,true));
        assertArrayEquals(new int[]{2},FactionBank.ledgerDeltas(core));
    }
}
