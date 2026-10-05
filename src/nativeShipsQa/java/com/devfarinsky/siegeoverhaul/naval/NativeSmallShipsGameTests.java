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
    @GameTest(template = "native_server_empty", timeoutTicks = 400, required = true)
    public static void exact_release_naval_contract_probe(GameTestHelper helper) {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER || !(helper.getLevel().getServer() instanceof GameTestServer))
            throw new IllegalStateException("Expected physical dedicated Forge GameTestServer");
        try {
            NativeSmallShipsProbe probe = new NativeSmallShipsProbe(helper.getLevel(), helper.absolutePos(new BlockPos(8, 5, 8)));
            probe.prepare();
            helper.runAfterDelay(20, () -> {
                try {
                    probe.inspect();
                    helper.succeed();
                    System.out.println("SIEGE_NATIVE_SHIPS_PROBE_COMPLETED compatibility=unverified");
                } catch (Exception failure) {
                    probe.fail(failure);
                    throw new IllegalStateException("Native Ships probe failed", failure);
                }
            });
        } catch (Exception failure) {
            throw new IllegalStateException("Native Ships probe setup failed", failure);
        }
    }
}
