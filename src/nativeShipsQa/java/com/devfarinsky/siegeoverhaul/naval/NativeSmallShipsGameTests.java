package com.devfarinsky.siegeoverhaul.naval;

import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Explicit reproduction, not a declaration that captain/navigation compatibility is fixed. */
@GameTestHolder(SiegeOverhaul.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NativeSmallShipsGameTests {
    @GameTest(template = "native_ships_empty", timeoutTicks = 900, required = true)
    public static void exact_release_naval_contract_probe(GameTestHelper helper) {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER || !(helper.getLevel().getServer() instanceof GameTestServer))
            throw new IllegalStateException("Expected physical dedicated Forge GameTestServer");
        try {
            NativeSmallShipsProbe probe = new NativeSmallShipsProbe(helper.getLevel(), helper.absolutePos(new BlockPos(24, 5, 24)));
            probe.prepare();
            boolean adapter = Boolean.getBoolean("siegeoverhaul.nativeShipsQa.adapter");
            // Register the complete schedule before ticking. Never mutate the GameTest
            // scheduler while it is dispatching another callback.
            helper.runAfterDelay(80, () -> checked(probe, () -> {
                probe.inspect();
                if (adapter) probe.beginPrototype(); else complete(helper);
            }));
            if (adapter) {
                for (int tick = 81; tick <= 100; tick++) helper.runAfterDelay(tick, () -> checked(probe, probe::issuePrototypeTurns));
                helper.runAfterDelay(101, () -> checked(probe, probe::finishPrototypeTurns));
                helper.runAfterDelay(104, () -> checked(probe, () -> { probe.finishPrototypeExpiry(); probe.beginNativeNavigation(); }));
                for (int tick = 124; tick <= 704; tick += 20) helper.runAfterDelay(tick, () -> checked(probe, probe::sampleNativeNavigation));
                helper.runAfterDelay(705, () -> checked(probe, () -> { probe.finishNativeNavigation(); complete(helper); }));
            }
        } catch (Exception failure) {
            throw new IllegalStateException("Native Ships probe setup failed", failure);
        }
    }
    @FunctionalInterface private interface Checked { void run() throws Exception; }
    private static void checked(NativeSmallShipsProbe probe, Checked action) {
        try { action.run(); } catch (Exception failure) { probe.fail(failure); throw new IllegalStateException(failure); }
    }
    private static void complete(GameTestHelper helper) {
        helper.succeed();
        System.out.println("SIEGE_NATIVE_SHIPS_PROBE_COMPLETED compatibility=unverified");
    }
}
