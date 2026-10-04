package com.devfarinsky.siegeoverhaul.nativecompat;

import net.minecraft.nbt.CompoundTag;
import java.util.List;

/** Pure accounting regressions, executed before this mode starts its real gameplay fixture. */
final class NativeQaTreasuryContracts {
    private NativeQaTreasuryContracts() {}
    static List<String> verify() {
        CompoundTag core = core(); var observer = observer(core);
        CompoundTag untouched = core.copy(); observer.observe(core, 100, 10);
        require(core.equals(untouched), "Observer mutated its input");
        core.putLong("BankEmeralds", 1955); core.putLong("BankInterestAt", 24000); core.putLong("BankInterestRemainder", 3600);
        observer.observe(core, 100, 24000);
        core.putLong("BankEmeralds", 1974); core.putLong("BankInterestAt", 48000); core.putLong("BankInterestRemainder", 9100);
        observer.observe(core, 100, 48000);
        require(observer.evidence().get("interestCredits").equals(38L), "Interest total differs");
        for (long remainder : new long[]{3600, 3800}) {
            core = core(); observer = observer(core);
            core.putLong("BankEmeralds", 1957); core.putLong("BankInterestAt", 24000);
            core.putLong("BankInterestRemainder", remainder); core.putLong("CivilianTaxesTotal", 2);
            core.putLongArray("BankLedger", new long[]{-64, 2}); observer.observe(core, 100, 24000);
        }
        core = core(); observer = observer(core);
        core.putLong("BankEmeralds", 1938); core.putLong("CivilianTaxesTotal", 2);
        core.putLongArray("BankLedger", new long[]{-64, 2}); observer.observe(core, 100, 1200);
        reject(tag -> tag.putLong("BankEmeralds", 1935), 100, 10);
        reject(tag -> tag.putLong("BankEmeralds", 1937), 100, 10);
        reject(tag -> tag.putLong("BankInterestRemainder", 1), 100, 10);
        reject(tag -> tag.putLong("BankInterestAt", 24001), 100, 24001);
        reject(tag -> tag.putLong("BankInterestAt", -1), 100, 10);
        reject(tag -> tag.putLongArray("BankLedger", new long[]{-64, -64}), 100, 10);
        reject(tag -> { tag.putLong("CivilianTaxesTotal", 3); tag.putLong("BankEmeralds", 1939); }, 100, 1200);
        reject(tag -> {}, 200, 10);
        return List.of("Input NBT remains unchanged", "Two exact daily interest payouts retain fractional remainder",
                "Tax-only credit", "Both exact tax/interest listener orders", "Unexplained debit/gain rejected",
                "Remainder mutation rejected", "Misaligned/backwards clock rejected", "Duplicate fee rejected",
                "Excess tax counter rejected", "Unexpected rate change rejected");
    }
    private static CompoundTag core() {
        CompoundTag tag = new CompoundTag(); tag.putLong("BankEmeralds", 1936); tag.putLong("BankInterestAt", 0);
        tag.putLongArray("BankLedger", new long[]{-64}); return tag;
    }
    private static NativeQaTreasury observer(CompoundTag tag) { return new NativeQaTreasury(tag, 100, 0, 2000, 64, 2); }
    private static void reject(java.util.function.Consumer<CompoundTag> change, int rate, long now) {
        CompoundTag tag = core(); var observer = observer(tag); change.accept(tag); boolean rejected = false;
        try { observer.observe(tag, rate, now); } catch (AssertionError expected) { rejected = true; }
        require(rejected, "Observer accepted malformed/unexplained Treasury state");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
