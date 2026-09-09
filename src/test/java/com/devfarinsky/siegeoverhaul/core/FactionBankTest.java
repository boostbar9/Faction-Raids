package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FactionBankTest extends MinecraftTestSupport {
    @Test void interestRequiresWholeRealDaysAndCannotRepeatAfterReloadOrClockRollback() {
        var core = new CompoundTag();
        FactionBank.credit(core, 10000);
        FactionBank.settle(core, 1000, 100);
        assertFalse(FactionBank.settle(core, 1000 + FactionBank.DAY - 1, 100));
        FactionBank.settle(core, 1000 + FactionBank.DAY, 100);
        assertEquals(10100, FactionBank.balance(core));
        var data = new RaidSavedData(); data.siegeCores.put("team:test", core);
        core = RaidSavedData.load(data.save(new CompoundTag())).siegeCores.get("team:test");
        assertFalse(FactionBank.settle(core, 1000 + FactionBank.DAY, 100));
        assertFalse(FactionBank.settle(core, 0, 100));
        FactionBank.settle(core, 1000 + 2 * FactionBank.DAY, 100);
        assertEquals(10201, FactionBank.balance(core));
    }
    @Test void fractionalInterestAccumulatesForSmallBalances() {
        var core = new CompoundTag(); FactionBank.credit(core, 10);
        FactionBank.settle(core, 1000, 100);
        FactionBank.settle(core, 1000 + 10 * FactionBank.DAY, 100);
        assertEquals(11, FactionBank.balance(core));
    }
    @Test void ledgerRejectsNegativePaymentsOverdraftsAndOverflow() {
        var core = new CompoundTag();
        assertEquals(0, FactionBank.credit(core, -1));
        assertEquals(FactionBank.LIMIT, FactionBank.credit(core, Long.MAX_VALUE));
        assertEquals(0, FactionBank.credit(core, 1));
        assertFalse(FactionBank.debit(core, 0)); assertFalse(FactionBank.debit(core, -1));
        assertFalse(FactionBank.debit(core, FactionBank.LIMIT + 1));
        assertTrue(FactionBank.debit(core, FactionBank.LIMIT));
        assertEquals(0, FactionBank.balance(core));
    }
}
