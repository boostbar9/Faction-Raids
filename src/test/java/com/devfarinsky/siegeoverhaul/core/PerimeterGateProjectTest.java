package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PerimeterGateProjectTest extends MinecraftTestSupport {
    @Test void fixedVersionOneManifestRetainsHistoricalDigestsAreaIdentityAndPaidRunningSaveShape() {
        var territory = Set.of(new ChunkPos(0, 0));
        var plan = PerimeterStageLayoutTest.flat(territory);
        var layout = PerimeterStageLayout.partition(plan, stage -> null);
        var header = PerimeterProject.Header.newCommission(new UUID(0, 1), 1, new UUID(0, 2), new UUID(0, 3),
                "team:test", new BlockPos(8, 64, 8), "test", 1, "a".repeat(64), territory);
        Map<Long, BlockState> before = new HashMap<>(), clearance = new HashMap<>();
        plan.blocks().keySet().forEach(cell -> before.put(cell, Blocks.AIR.defaultBlockState()));
        plan.clearance().forEach(cell -> clearance.put(cell, Blocks.AIR.defaultBlockState()));
        var project = PerimeterProject.prepare(header, plan, layout, before, clearance).paid(false);
        project = project.activate(project.check());
        // Independently calculated from the pinned v1 format; do not regenerate these from the current codec.
        assertEquals("621baca9f9528cec3cf87f816746e6e1ec5774bdb19da436ffbeec9daca8f3fd", project.manifestHash());
        assertEquals("3f899bf53e2c2caa2a3feef1a98a1d2fde6efa97af992ba3f0b01dcb1b38d6fe", project.layout().digest());
        assertEquals("e296116a0e5eae3c432f1c9b32ad8d4ba13d92baa44c310f854b95eab5a71849", project.active().digest());
        assertEquals(UUID.fromString("9949032a-b590-3bdc-b41a-04df27ed4e37"), project.active().areaId());
        CompoundTag saved = project.save();
        assertEquals(1, saved.getInt("Version")); assertFalse(saved.contains("GateContract"));
        assertEquals(Set.of("Version", "Project", "Generation", "Owner", "Builder", "CoreKey", "Core", "Faction", "Material",
                "FeeVersion", "Price", "Review", "Hash", "Territory", "State", "Revision", "Active", "Blocker", "Palette",
                "Targets", "Clearance", "Columns", "Min", "Max", "Layout", "Stages", "Receipts", "Payment"), saved.getAllKeys());
        var loaded = PerimeterProject.load(saved.copy());
        assertEquals(saved, loaded.save()); assertTrue(loaded.executionSupported());
        assertNull(loaded.gateContract()); assertTrue(loaded.observations().isEmpty());
        assertEquals(project.payment(), loaded.payment()); assertEquals(project.stages(), loaded.stages());
    }

    @Test void gateAwareV2RoundTripsEveryRoleWithoutAddingObservationCellsToNativeStages() {
        var project = PerimeterGateProjectFixture.project();
        var loaded = PerimeterProject.load(project.save());
        assertEquals(2, loaded.formatVersion()); assertFalse(loaded.executionSupported());
        assertEquals(project.save(), loaded.save()); assertEquals(project.manifestHash(), loaded.manifestHash());
        assertEquals(project.observations(), loaded.observations()); assertEquals(project.stages(), loaded.stages());
        assertTrue(loaded.observations().keySet().stream().map(BlockPos::of).anyMatch(p -> !loaded.header().territory().contains(new ChunkPos(p))));
        Set<Long> nativeCells = new HashSet<>(loaded.targets().keySet()); nativeCells.addAll(loaded.clearanceBefore().keySet());
        assertTrue(Collections.disjoint(nativeCells, loaded.observations().keySet()));
        Set<Long> all = new HashSet<>(nativeCells); all.addAll(loaded.observations().keySet());
        assertEquals(all, loaded.reservation());
        Set<Long> stageUnion = new HashSet<>(); loaded.stages().forEach(s -> stageUnion.addAll(s.reservation()));
        assertEquals(nativeCells, stageUnion);
        assertThrows(UnsupportedOperationException.class, () -> loaded.observations().clear());
    }

    @Test void versionKeysCannotUpgradeDowngradeOrOmitTheExactContract() {
        assertThrows(IllegalArgumentException.class, () -> PerimeterProject.load(null));
        var v2 = PerimeterGateProjectFixture.project();
        PerimeterProjectTest.assertCorrupt(v2, tag -> tag.putInt("Version", 1));
        PerimeterProjectTest.assertCorrupt(v2, tag -> tag.putInt("Version", 3));
        PerimeterProjectTest.assertCorrupt(v2, tag -> tag.remove("GateContract"));
        PerimeterProjectTest.assertCorrupt(v2, tag -> tag.putString("GateContract", "missing"));
        var v1 = PerimeterProjectTest.project();
        PerimeterProjectTest.assertCorrupt(v1, tag -> tag.put("GateContract", v2.gateContract().save()));
        PerimeterProjectTest.assertCorrupt(v1, tag -> tag.putInt("Version", 2));
    }

    @Test void modeledV2StateTransitionsKeepGateObservationsHashesAndPaymentImmutable() {
        var running = PerimeterGateProjectFixture.paidRunning();
        for (var project : List.of(running, running.verifyStage(running.check(), running.expectedStageReceipt()),
                running.blockRecovery(running.check(), "Observation changed"), running.cancel(running.check(), "Owner canceled"))) {
            var restored = PerimeterProject.load(project.save());
            assertEquals(project.save(), restored.save()); assertEquals(running.manifestHash(), restored.manifestHash());
            assertEquals(running.observations(), restored.observations()); assertEquals(running.payment(), restored.payment());
            assertFalse(restored.executionSupported());
        }
    }

    @Test void storePreservesBothFormatsAndRefusesGatePaymentWithoutChangingTreasuryOrRecord() {
        var core = new CompoundTag(); core.putLong("BankEmeralds", 128);
        var legacy = PerimeterProjectTest.project(); var gates = PerimeterGateProjectFixture.project();
        PerimeterProjectStore.prepare(core, legacy, () -> {}); PerimeterProjectStore.prepare(core, gates, () -> {});
        CompoundTag unchanged = core.copy();
        var failure = assertThrows(IllegalArgumentException.class, () -> PerimeterProjectStore.consumeOnce(core,
                gates.header().projectId(), gates.manifestHash(), 64, false, () -> fail("No dirty signal before a rejected debit")));
        assertEquals(PerimeterProject.GATE_EXECUTION_BLOCKER, failure.getMessage());
        assertEquals(unchanged, core); assertEquals(128, core.getLong("BankEmeralds"));
        assertEquals(gates.save(), PerimeterProjectStore.get(core, gates.header().projectId()).save());
        assertEquals(legacy.save(), PerimeterProjectStore.get(core, legacy.header().projectId()).save());
        assertTrue(PerimeterProjectStore.consumeOnce(core, legacy.header().projectId(), legacy.manifestHash(), 64, false, () -> {}).paid());
        assertEquals(64, core.getLong("BankEmeralds"));
    }
}
