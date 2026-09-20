package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BankInterestCountdownTest extends MinecraftTestSupport {
    @Test
    void countdownStaysDueUntilSettlementAdvancesTheAnchor() {
        var core = new CompoundTag();
        FactionBank.credit(core, 10000);
        FactionBank.settle(core, 1000, 100);
        long due = 1000 + FactionBank.DAY_TICKS;

        assertEquals(FactionBank.DAY_TICKS, FactionBank.ticksUntilInterest(core, 1000));
        assertEquals(1, FactionBank.ticksUntilInterest(core, due - 1));
        assertEquals(0, FactionBank.ticksUntilInterest(core, due));
        assertEquals(0, FactionBank.ticksUntilInterest(core, due + 1));
        assertEquals(0, FactionBank.ticksUntilInterest(core, due + 3 * FactionBank.DAY_TICKS));
        assertEquals(10000, FactionBank.balance(core));

        assertTrue(FactionBank.settle(core, due, 100));
        assertEquals(10100, FactionBank.balance(core));
        assertEquals(FactionBank.DAY_TICKS, FactionBank.ticksUntilInterest(core, due));
        assertFalse(FactionBank.settle(core, due, 100));
    }

    @Test
    void missingLegacyAndFutureAnchorsDoNotAdvertiseImmediateInterest() {
        assertEquals(FactionBank.DAY_TICKS, FactionBank.ticksUntilInterest(null, 1000));
        var core = new CompoundTag();
        assertEquals(FactionBank.DAY_TICKS, FactionBank.ticksUntilInterest(core, 1000));

        core.putLong("BankInterestAt", 2000);
        assertEquals(FactionBank.DAY_TICKS, FactionBank.ticksUntilInterest(core, 1000));

        core.putLong("BankInterestAt", 1_800_000_000_000L);
        core.putLong("BankInterestRemainder", 9000);
        FactionBank.credit(core, 10000);
        assertEquals(FactionBank.DAY_TICKS, FactionBank.ticksUntilInterest(core, 1000));
        assertTrue(FactionBank.settle(core, 1000, 100));
        assertEquals(1000, core.getLong("BankInterestAt"));
        assertEquals(0, core.getLong("BankInterestRemainder"));
        assertEquals(10000, FactionBank.balance(core));
        assertEquals(FactionBank.DAY_TICKS, FactionBank.ticksUntilInterest(core, 1000));
    }
}
