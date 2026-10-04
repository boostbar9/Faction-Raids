package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.core.FactionBank;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only oracle for the long L fixture's one fee and ordinary passive income. */
final class NativeQaTreasury {
    private final long startingFunds, fee, initialBalance, initialTaxes;
    private final int maximumTaxCredit;
    private State previous;
    private long interestCredits, taxCredits;
    private Map<String, Object> lastObservation;
    private final List<Map<String, Object>> credits = new ArrayList<>();
    private record State(long balance, long interestAt, long remainder, long taxes, int rate) {
        Map<String, Object> evidence() { return Map.of("balance", balance, "interestAt", interestAt,
                "interestRemainder", remainder, "civilianTaxesTotal", taxes, "basisPoints", rate); }
    }

    NativeQaTreasury(CompoundTag core, int rate, long now, long startingFunds, long fee, int maximumTaxCredit) {
        this.startingFunds = startingFunds; this.fee = fee; this.maximumTaxCredit = maximumTaxCredit;
        previous = read(core, rate, now); initialBalance = previous.balance(); initialTaxes = previous.taxes();
        require(initialBalance == startingFunds - fee, "Initial Treasury is not the exact single commission debit");
        verifySingleDebit(core);
        lastObservation = Map.of("before", previous.evidence(), "after", previous.evidence(), "interestCredit", 0L, "taxCredit", 0L);
    }

    void observe(CompoundTag core, int rate, long now) {
        State next = read(core, rate, now); verifySingleDebit(core);
        require(next.rate() == previous.rate(), "Fixture bank interest rate changed unexpectedly");
        long clockDelta = next.interestAt() - previous.interestAt();
        require(clockDelta >= 0 && clockDelta % FactionBank.DAY_TICKS == 0
                        && clockDelta <= FactionBank.DAY_TICKS, "Unexplained or missed Treasury settlement clock");
        long taxes = next.taxes() - previous.taxes();
        require(taxes >= 0 && taxes <= maximumTaxCredit, "Unexplained civilian tax counter change");
        long interest = 0;
        if (clockDelta == 0) {
            require(next.remainder() == previous.remainder() && next.balance() == previous.balance() + taxes,
                    "Unexplained Treasury gain/debit without daily settlement");
        } else {
            // Both production tick listeners can run between observations. Require an exact
            // balance AND fractional remainder for either legitimate ordering of those credits.
            long beforeTaxNumerator = previous.balance() * rate + previous.remainder();
            long afterTaxNumerator = (previous.balance() + taxes) * rate + previous.remainder();
            boolean interestFirst = next.balance() == previous.balance() + beforeTaxNumerator / 10000 + taxes
                    && next.remainder() == beforeTaxNumerator % 10000;
            boolean taxFirst = next.balance() == previous.balance() + taxes + afterTaxNumerator / 10000
                    && next.remainder() == afterTaxNumerator % 10000;
            require(interestFirst || taxFirst, "Treasury does not match exact daily interest and recorded taxes");
            interest = next.balance() - previous.balance() - taxes;
        }
        var observation = new LinkedHashMap<String, Object>();
        observation.put("gameTime", now); observation.put("before", previous.evidence()); observation.put("after", next.evidence());
        observation.put("interestCredit", interest); observation.put("taxCredit", taxes);
        observation.put("ledgerDeltas", Arrays.stream(FactionBank.ledgerDeltas(core)).boxed().toList());
        lastObservation = Map.copyOf(observation);
        if (interest != 0 || taxes != 0) credits.add(lastObservation);
        interestCredits += interest; taxCredits += taxes; previous = next;
        require(next.balance() == initialBalance + interestCredits + taxCredits
                        && next.taxes() == initialTaxes + taxCredits, "Treasury conservation equation failed");
    }

    Map<String, Object> lastObservation() { return lastObservation; }
    long passiveCredits() { return interestCredits + taxCredits; }
    Map<String, Object> evidence() {
        return Map.of("startingFunds", startingFunds, "commissionDebit", fee, "initialPaidBalance", initialBalance,
                "interestCredits", interestCredits, "taxCredits", taxCredits, "expectedFinalBalance", previous.balance(),
                "singleNegativeLedgerDelta", -fee, "passiveCreditEvents", List.copyOf(credits),
                "equation", startingFunds + " - " + fee + " + " + interestCredits + " + " + taxCredits + " = " + previous.balance());
    }

    private void verifySingleDebit(CompoundTag core) {
        int[] negative = Arrays.stream(FactionBank.ledgerDeltas(core)).filter(value -> value < 0).toArray();
        require(negative.length == 1 && negative[0] == -fee, "Treasury ledger contains a missing or additional debit");
    }
    private static State read(CompoundTag core, int rate, long now) {
        require(core != null && core.contains("BankInterestAt", Tag.TAG_LONG), "Treasury has no real interest clock");
        long balance = core.getLong("BankEmeralds"), clock = core.getLong("BankInterestAt");
        long remainder = core.getLong("BankInterestRemainder"), taxes = core.getLong("CivilianTaxesTotal");
        require(balance >= 0 && balance < FactionBank.LIMIT - 100_000 && clock >= 0 && clock <= now
                        && remainder >= 0 && remainder < 10000 && taxes >= 0 && rate >= 0 && rate <= 1000,
                "Unsupported/malformed fixture Treasury counters");
        return new State(balance, clock, remainder, taxes, rate);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
