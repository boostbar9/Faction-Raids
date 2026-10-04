package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PerimeterProjectTest extends MinecraftTestSupport {
    @Test void exactFrozenManifestRoundTripsLosslesslyWithOriginalPropertiesAndStableAreaIds() {
        var project = project(); var restored = roundtrip(project);
        assertEquals(project.header(), restored.header()); assertEquals(project.manifestHash(), restored.manifestHash());
        assertEquals(project.targets(), restored.targets()); assertEquals(project.before(), restored.before());
        assertEquals(project.clearanceBefore(), restored.clearanceBefore()); assertEquals(project.stages(), restored.stages());
        assertEquals(project.save(), restored.save()); assertNotNull(restored.before().values().stream()
                .filter(state -> state.is(Blocks.SNOW)).findFirst().orElse(null));
        assertThrows(UnsupportedOperationException.class, () -> restored.before().clear());
    }

    @Test void independentlyConstructedPaidLegacySolidJobRetainsGeometryPaymentHashAndStageNbt() {
        var solid = LegacySolidPerimeterFixture.plan();
        assertEquals(968, solid.blocks().size());
        assertEquals(352, solid.clearance().size());
        var header = project().header();
        var layout = PerimeterStageLayout.partition(solid, part -> part.targets().size() <= 500 ? null : "Legacy capacity");
        Map<Long, BlockState> before = new LinkedHashMap<>(), clear = new LinkedHashMap<>();
        solid.blocks().keySet().forEach(cell -> before.put(cell, Blocks.AIR.defaultBlockState()));
        solid.clearance().forEach(cell -> clear.put(cell, Blocks.AIR.defaultBlockState()));
        var paid = PerimeterProject.prepare(header, solid, layout, before, clear).paid(false);
        var running = paid.activate(paid.check());
        CompoundTag saved = running.save();
        var restored = PerimeterProject.load(saved.copy());
        assertEquals(saved, restored.save());
        assertEquals(running.manifestHash(), restored.manifestHash());
        assertEquals(running.payment(), restored.payment()); assertEquals(64, restored.payment().debited());
        assertEquals(running.stages(), restored.stages()); assertEquals(solid.blocks(), restored.plan().blocks());
        assertEquals(before, restored.before()); assertEquals(clear, restored.clearanceBefore());
        var newPlan = PerimeterStageLayoutTest.flat(header.territory());
        assertEquals(572, newPlan.blocks().size()); assertNotEquals(newPlan.blocks(), restored.plan().blocks());
        long legacyInterior = new BlockPos(2, 65, 7).asLong();
        assertTrue(newPlan.clearance().contains(legacyInterior));
        assertEquals(Blocks.COBBLESTONE.defaultBlockState(), restored.targets().get(legacyInterior));
        assertFalse(restored.clearanceBefore().containsKey(legacyInterior));
        for (int i = 0; i < running.stages().size(); i++) {
            var oldStage = running.stages().get(i).layout(); var loadedStage = restored.stages().get(i).layout();
            assertEquals(TerritoryFortification.blueprint(oldStage.targets(), oldStage.min(), oldStage.max()),
                    TerritoryFortification.blueprint(loadedStage.targets(), loadedStage.min(), loadedStage.max()));
        }
    }

    @Test void newQuoteUses64AndFrozenVersionOneNeverDependsOnAChangedGlobalPriceDuringLoad() {
        assertEquals(64, TerritoryFortification.PRICE);
        assertEquals(TerritoryFortification.PRICE, PerimeterProject.NEW_PROJECT_PRICE);
        var project = project(); assertEquals(64, project.header().quotedPrice()); assertEquals(1, project.header().feeVersion());
        CompoundTag wrongPrice = project.save(); wrongPrice.putInt("Price", 900);
        assertThrows(IllegalArgumentException.class, () -> PerimeterProject.load(wrongPrice));
        CompoundTag finalSaved = project.save(); finalSaved.putInt("FeeVersion", 2);
        assertThrows(IllegalArgumentException.class, () -> PerimeterProject.load(finalSaved));
    }

    @Test void inputStateAndColumnOrderingDoNotChangeManifestHash() {
        var original = project(); var plan = original.plan();
        var columns = new ArrayList<>(plan.columns()); Collections.reverse(columns);
        var reordered = new PerimeterBlueprint.Plan(reverse(plan.blocks()), columns, plan.clearance(), plan.min(), plan.max(),
                plan.runs(), plan.connections(), plan.materialCounts(), List.of());
        var copy = PerimeterProject.prepare(original.header(), reordered, original.layout(), reverse(original.before()), reverse(original.clearanceBefore()));
        assertEquals(original.manifestHash(), copy.manifestHash()); assertEquals(original.stages(), copy.stages());
        var changedBefore = new LinkedHashMap<>(original.before()); changedBefore.put(changedBefore.keySet().iterator().next(), Blocks.DIRT.defaultBlockState());
        var changed = PerimeterProject.prepare(original.header(), plan, original.layout(), changedBefore, original.clearanceBefore());
        assertNotEquals(original.manifestHash(), changed.manifestHash()); assertNotEquals(original.stages().get(0).areaId(), changed.stages().get(0).areaId());
    }

    @Test void everyCrashBoundaryRoundTripsAndOnlyExplicitVerifiedRetirementAdvancesOnce() {
        var unpaid = roundtrip(project()); assertEquals(PerimeterProject.State.PREPARED_UNPAID, unpaid.state());
        assertThrows(IllegalArgumentException.class, () -> unpaid.activate(unpaid.check()));
        var current = roundtrip(unpaid.paid(false)); assertEquals(PerimeterProject.State.PREPARED_PAID, current.state());
        while (current.activeStage() < current.stages().size()) {
            current = roundtrip(current.activate(current.check()));
            var running = current; assertThrows(IllegalArgumentException.class, () -> running.retireVerifiedStage(running.check()));
            var expected = current.check(); var receipt = current.expectedStageReceipt();
            current = roundtrip(current.verifyStage(expected, receipt));
            assertEquals(PerimeterProject.State.STAGE_VERIFIED, current.state());
            assertSame(current, current.verifyStage(current.check(), receipt));
            var verified = current; assertThrows(IllegalArgumentException.class, () -> verified.retireVerifiedStage(expected));
            int old = current.activeStage(); current = roundtrip(current.retireVerifiedStage(current.check()));
            assertEquals(old + 1, current.activeStage());
            var retired = current; assertThrows(IllegalArgumentException.class, () -> retired.retireVerifiedStage(retired.check()));
        }
        assertEquals(PerimeterProject.State.VERIFYING_COMPLETE, current.state());
        assertEquals(current.targets().size(), current.completedTargetCount());
        var verify = current; assertThrows(IllegalArgumentException.class, () -> verify.complete(verify.check(), "wrong"));
        current = roundtrip(current.complete(current.check(), current.manifestHash()));
        assertEquals(PerimeterProject.State.COMPLETE, current.state()); assertEquals(64, current.payment().debited());
    }

    @Test void recoveryAndCancellationNeverAdvanceOrReviveOrLosePayment() {
        var running = project().paid(false); running = running.activate(running.check());
        var blocked = roundtrip(running.blockRecovery(running.check(), "Marker chunk is unknown"));
        assertEquals(running.activeStage(), blocked.activeStage()); assertEquals(running.receipts(), blocked.receipts());
        assertThrows(IllegalArgumentException.class, () -> blocked.activate(blocked.check()));
        assertThrows(IllegalArgumentException.class, () -> blocked.reconciled(blocked.check(), PerimeterProject.State.WAITING_FOR_NEXT_STAGE));
        var resumed = blocked.reconciled(blocked.check(), PerimeterProject.State.RUNNING);
        var canceled = roundtrip(resumed.cancel(resumed.check(), "Owner canceled the whole commission"));
        assertEquals(64, canceled.payment().debited()); assertEquals(resumed.activeStage(), canceled.activeStage());
        assertThrows(IllegalArgumentException.class, () -> canceled.activate(canceled.check()));
        assertThrows(IllegalArgumentException.class, () -> canceled.waitFor(canceled.check(), "Try again"));
        assertSame(canceled, canceled.cancel(canceled.check(), "Retry"));
        var unpaid = project(); assertNull(roundtrip(unpaid.cancel(unpaid.check(), "Canceled before acceptance")).payment());
    }

    @Test void unknownVersionsWrongTypesMissingReceiptAndCorruptCountsFailClosed() {
        var project = project().paid(false);
        assertCorrupt(project, tag -> tag.putInt("Version", 2));
        assertCorrupt(project, tag -> tag.remove("Payment"));
        assertCorrupt(project, tag -> tag.putInt("Active", -1));
        assertCorrupt(project, tag -> tag.putInt("Active", 5000));
        assertCorrupt(project, tag -> tag.putLong("Revision", -1));
        assertCorrupt(project, tag -> tag.putString("State", "COMPLETE"));
        assertCorrupt(project, tag -> tag.putString("Hash", "0".repeat(64)));
        assertCorrupt(project, tag -> tag.putInt("Price", 63));
        assertCorrupt(project, tag -> tag.putString("Generation", "1"));
        assertCorrupt(project, tag -> { ListTag list = new ListTag(); list.add(StringTag.valueOf("not a cell")); tag.put("Targets", list); });
        assertCorrupt(project, tag -> tag.getList("Targets", Tag.TAG_COMPOUND).add(tag.getList("Targets", Tag.TAG_COMPOUND).get(0).copy()));
        assertCorrupt(project, tag -> tag.getList("Stages", Tag.TAG_COMPOUND).getCompound(0).putUUID("Area", UUID.randomUUID()));
        assertCorrupt(project, tag -> tag.getList("Stages", Tag.TAG_COMPOUND).getCompound(0).putLongArray("Columns", new long[0]));
        assertCorrupt(project, tag -> tag.getCompound("Payment").putInt("Debited", 0));
        assertCorrupt(project, tag -> tag.getCompound("Payment").putByte("Creative", (byte) 2));
        assertCorrupt(project, tag -> tag.getList("Palette", Tag.TAG_COMPOUND).getCompound(0).putString("Name", "missing:state"));
    }

    @Test void nonPrefixOrConflictingStageReceiptsCannotAdvance() {
        var current = project().paid(false); current = current.activate(current.check());
        var receipt = current.expectedStageReceipt();
        var bad = new PerimeterProject.StageReceipt(receipt.projectId(), receipt.generation() + 1, receipt.manifestHash(),
                receipt.stageIndex(), receipt.areaId(), receipt.stageDigest(), receipt.verifiedTargets());
        var running = current; assertThrows(IllegalArgumentException.class, () -> running.verifyStage(running.check(), bad));
        current = current.verifyStage(current.check(), receipt);
        assertCorrupt(current, tag -> tag.getList("Receipts", Tag.TAG_COMPOUND).getCompound(0).putInt("Index", 1));
        assertCorrupt(current, tag -> tag.getList("Receipts", Tag.TAG_COMPOUND).getCompound(0).putInt("Targets", 1));
        assertCorrupt(current, tag -> tag.getList("Receipts", Tag.TAG_COMPOUND).add(tag.getList("Receipts", Tag.TAG_COMPOUND).get(0).copy()));
    }

    @Test void zeroIdsWrongCoreOrMissingOriginalStatesAreRejectedBeforePayment() {
        var p = project(); var h = p.header();
        assertThrows(IllegalArgumentException.class, () -> PerimeterProject.Header.newCommission(new UUID(0, 0), 1, h.owner(), h.builder(), h.coreKey(), h.originalCore(), h.faction(), h.material(), h.reviewedFingerprint(), h.territory()));
        assertThrows(IllegalArgumentException.class, () -> PerimeterProject.Header.newCommission(h.projectId(), 1, h.owner(), h.builder(), "team:someone_else", h.originalCore(), h.faction(), h.material(), h.reviewedFingerprint(), h.territory()));
        assertThrows(IllegalArgumentException.class, () -> PerimeterProject.prepare(h, p.plan(), p.layout(), Map.of(), p.clearanceBefore()));
    }

    @Test void callerMutablePositionsCannotChangeAnyFrozenPlanPositionOrManifest() {
        var reference = project(); var plan = reference.plan(); List<BlockPos.MutableBlockPos> mutable = new ArrayList<>();
        java.util.function.Function<BlockPos, BlockPos.MutableBlockPos> wrap = p -> {
            var value = new BlockPos.MutableBlockPos(p.getX(), p.getY(), p.getZ()); mutable.add(value); return value;
        };
        var columns = plan.columns().stream().map(c -> new PerimeterBlueprint.Column(wrap.apply(c.base()),
                c.supportDepth(), c.inwardDistance(), c.componentId())).toList();
        var runs = plan.runs().stream().map(r -> new PerimeterBlueprint.Run(r.id(), r.componentId(),
                wrap.apply(r.start()), wrap.apply(r.end()), r.direction())).toList();
        var connections = plan.connections().stream().map(c -> new PerimeterBlueprint.Connection(c.runId(), c.componentId(),
                wrap.apply(c.center()), c.facing(), c.width())).toList();
        var source = new PerimeterBlueprint.Plan(plan.blocks(), columns, plan.clearance(), wrap.apply(plan.min()), wrap.apply(plan.max()),
                runs, connections, plan.materialCounts(), plan.problems());
        var frozen = PerimeterProject.prepare(reference.header(), source, reference.layout(), reference.before(), reference.clearanceBefore());
        CompoundTag exact = frozen.save(); String hash = frozen.manifestHash();
        List<BlockPos> savedRunStarts = frozen.plan().runs().stream().map(PerimeterBlueprint.Run::start).toList();
        List<BlockPos> savedCenters = frozen.plan().connections().stream().map(PerimeterBlueprint.Connection::center).toList();
        mutable.forEach(p -> p.set(123456, -500, 654321));
        assertEquals(exact, frozen.save()); assertEquals(hash, frozen.manifestHash());
        assertEquals(plan.columns(), frozen.plan().columns()); assertEquals(plan.min(), frozen.plan().min()); assertEquals(plan.max(), frozen.plan().max());
        assertEquals(savedRunStarts, frozen.plan().runs().stream().map(PerimeterBlueprint.Run::start).toList());
        assertEquals(savedCenters, frozen.plan().connections().stream().map(PerimeterBlueprint.Connection::center).toList());
        assertEquals(hash, roundtrip(frozen).manifestHash());
    }

    @Test void reservationsAreCachedDerivedAndImmutableAndIdenticalWaitingIsANoop() {
        var project = project(); assertSame(project.reservation(), project.reservation());
        assertEquals(project.targets().size() + project.clearanceBefore().size(), project.reservation().size());
        assertThrows(UnsupportedOperationException.class, () -> project.reservation().clear());
        for (var stage : project.stages()) {
            assertSame(stage.reservation(), stage.reservation()); assertSame(stage.reservation(), stage.layout().reservation());
            assertEquals(stage.layout().targets().size() + stage.layout().clearance().size(), stage.reservation().size());
            assertThrows(UnsupportedOperationException.class, () -> stage.reservation().clear());
        }
        var waiting = project.waitFor(project.check(), "Owner offline");
        assertSame(waiting, waiting.waitFor(waiting.check(), "Owner offline"));
        assertSame(project.plan(), waiting.plan()); assertSame(project.targets(), waiting.targets());
        assertSame(project.stages(), waiting.stages()); assertSame(project.reservation(), waiting.reservation());
        assertEquals(project.manifestHash(), waiting.manifestHash());
    }

    static PerimeterProject project() {
        Set<ChunkPos> territory = Set.of(new ChunkPos(0, 0)); var plan = PerimeterStageLayoutTest.flat(territory);
        var layout = PerimeterStageLayout.partition(plan, part -> part.targets().size() <= 500 ? null : "Test capacity");
        var header = PerimeterProject.Header.newCommission(UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(), "team:test",
                new BlockPos(8, 64, 8), "test", 1, "a".repeat(64), territory);
        Map<Long, BlockState> before = new LinkedHashMap<>(), clear = new LinkedHashMap<>();
        plan.blocks().keySet().forEach(cell -> before.put(cell, Blocks.AIR.defaultBlockState()));
        before.put(before.keySet().iterator().next(), Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, 3));
        plan.clearance().forEach(cell -> clear.put(cell, Blocks.AIR.defaultBlockState()));
        return PerimeterProject.prepare(header, plan, layout, before, clear);
    }
    static PerimeterProject roundtrip(PerimeterProject project) { return PerimeterProject.load(project.save()); }
    static void assertCorrupt(PerimeterProject project, java.util.function.Consumer<CompoundTag> corrupt) {
        CompoundTag tag = project.save(); corrupt.accept(tag); assertThrows(IllegalArgumentException.class, () -> PerimeterProject.load(tag));
    }
    private static <T> Map<Long, T> reverse(Map<Long, T> values) {
        List<Long> keys = new ArrayList<>(values.keySet()); Collections.reverse(keys); Map<Long, T> result = new LinkedHashMap<>();
        keys.forEach(key -> result.put(key, values.get(key))); return result;
    }
}
