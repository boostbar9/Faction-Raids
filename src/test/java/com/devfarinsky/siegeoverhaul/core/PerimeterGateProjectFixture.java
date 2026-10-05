package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Synthetic immutable-contract fixture only; never evidence of live terrain or native admission. */
public final class PerimeterGateProjectFixture {
    private PerimeterGateProjectFixture() {}
    public static PerimeterProject project() {
        return project(Set.of(new ChunkPos(0, 0)), true);
    }
    static PerimeterProject project(Set<ChunkPos> territory, boolean componentPure) {
        var original = PerimeterBlueprint.create(territory, (x, z) -> PerimeterBlueprint.Surface.ready(64),
                PerimeterBlueprint.Palette.COBBLESTONE);
        var gates = PerimeterGateLayout.create(territory, original, (feet, role) -> null);
        Map<Long, BlockState> observations = new HashMap<>();
        gates.approachClearance().forEach(cell -> observations.put(cell, Blocks.AIR.defaultBlockState()));
        gates.approachFooting().forEach(cell -> observations.put(cell, Blocks.DIRT.defaultBlockState()));
        gates.passageClearance().stream().map(BlockPos::of).filter(p -> p.getY() == 64)
                .forEach(p -> observations.put(p.below().asLong(), Blocks.DIRT.defaultBlockState()));
        var contract = PerimeterGateContract.create(territory, original, gates, observations);
        var plan = contract.applyOpenings(original);
        var layout = componentPure
                ? PerimeterGateStages.partition(plan, stage -> stage.targets().size() <= 300 ? null : "Fixture capacity")
                : PerimeterStageLayout.partition(plan, stage -> null);
        var header = PerimeterProject.Header.newCommission(UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(),
                "team:gates", new BlockPos(8, 64, 8), "gates", 1, "a".repeat(64), territory);
        Map<Long, BlockState> before = new HashMap<>(), clearance = new HashMap<>();
        plan.blocks().keySet().forEach(cell -> before.put(cell, Blocks.AIR.defaultBlockState()));
        plan.clearance().forEach(cell -> clearance.put(cell, Blocks.AIR.defaultBlockState()));
        return PerimeterProject.prepareWithGates(header, plan, layout, before, clearance, contract);
    }
    /** Models payment/state persistence only; the production store rejects a v2 debit at this checkpoint. */
    public static PerimeterProject paidRunning() {
        var paid = project().paid(false);
        return paid.activate(paid.check());
    }
}
