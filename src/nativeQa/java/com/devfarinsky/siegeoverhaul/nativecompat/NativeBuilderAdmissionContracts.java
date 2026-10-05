package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.core.DefenseBlueprint;
import com.devfarinsky.siegeoverhaul.core.DefenseStructures;
import com.devfarinsky.siegeoverhaul.core.FactionBank;
import com.devfarinsky.siegeoverhaul.core.PerimeterConstruction;
import com.devfarinsky.siegeoverhaul.core.PerimeterProjectStore;
import com.devfarinsky.siegeoverhaul.core.SiegeCore;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.talhanation.workers.entities.BuilderEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.LinkedHashMap;
import java.util.Map;

/** Short real-registry review checks on explicitly seeded terrain before any construction commission. */
final class NativeBuilderAdmissionContracts {
    private NativeBuilderAdmissionContracts() {}

    static Map<String, Object> verify(ServerLevel level, ServerPlayer owner,
                                      NativeGameplayFixture.Fixture fixture, BuilderEntity builder) {
        CompoundTag inventory = new CompoundTag();
        inventory.put("Items", inventory(builder));
        inventory.put("MainHand", builder.getMainHandItem().save(new CompoundTag()));
        CompoundTag workerData = builder.getPersistentData().copy();
        CompoundTag core = RaidSavedData.get(owner.server).siegeCores.get(SiegeCore.key(owner));
        long treasury = FactionBank.balance(core);
        require(PerimeterProjectStore.all(core).isEmpty(), "Admission fixture must run before the first commission");

        BlockPos neighbor = new BlockPos(127, 65, 8);
        BlockPos cavity = new BlockPos(130, 65, 8);
        BlockPos manual = fixture.wallAnchor();
        var original = new LinkedHashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        for (BlockPos pos : java.util.List.of(neighbor, cavity.below(), cavity, cavity.above(),
                manual.below(), manual, manual.above())) {
            require(level.hasChunkAt(pos), "Admission fixture terrain must already be loaded");
            original.put(pos, level.getBlockState(pos));
        }
        try {
            level.setBlock(neighbor, Blocks.SAND.defaultBlockState(), 2);
            String problem = PerimeterConstruction.prepare(owner, fixture.corePos(), 1).problem();
            rejectedAt(problem, neighbor, "minecraft:sand");
            require(level.getBlockState(neighbor).is(Blocks.SAND), "Free review removed its outside obstruction");
            level.setBlock(neighbor, original.get(neighbor), 2);

            plant(level, cavity);
            problem = PerimeterConstruction.prepare(owner, fixture.corePos(), 1).problem();
            rejectedAt(problem, cavity, "minecraft:tall_grass");
            require(level.getBlockState(cavity).is(Blocks.TALL_GRASS)
                            && level.getBlockState(cavity.above()).is(Blocks.TALL_GRASS),
                    "Free perimeter review cleared part of a paired plant");
            for (BlockPos pos : java.util.List.of(cavity.above(), cavity, cavity.below()))
                level.setBlock(pos, original.get(pos), 2);

            plant(level, manual);
            problem = DefenseStructures.prepare(owner, manual, Direction.SOUTH, DefenseBlueprint.Kind.GATEHOUSE).problem();
            rejectedAt(problem, manual, "minecraft:tall_grass");
            require(level.getBlockState(manual).is(Blocks.TALL_GRASS)
                            && level.getBlockState(manual.above()).is(Blocks.TALL_GRASS),
                    "Free manual review cleared part of a paired plant");
        } finally {
            original.forEach((pos, state) -> level.setBlock(pos, state, 2));
        }
        require(original.entrySet().stream().allMatch(entry -> level.getBlockState(entry.getKey()).equals(entry.getValue())),
                "Admission fixture did not restore its explicitly seeded terrain");
        require(FactionBank.balance(core) == treasury && PerimeterProjectStore.all(core).isEmpty()
                        && workerData.equals(builder.getPersistentData())
                        && inventory.getList("Items", 10).equals(inventory(builder))
                        && inventory.getCompound("MainHand").equals(builder.getMainHandItem().save(new CompoundTag())),
                "Read-only admission checks changed Treasury, project, worker receipt or inventory");
        return Map.of("perimeterNeighborRejected", true, "perimeterPairedClearanceRejected", true,
                "manualPairedClearanceRejected", true, "exactInventoryAndTreasury", true,
                "noProjectOrWorkerReceiptChange", true, "fixtureTerrainRestored", true,
                "scope", "Explicit pre-commission obstacle fixtures with real registry, claim and production prepare APIs; no clearing or native placement claimed");
    }

    private static void plant(ServerLevel level, BlockPos pos) {
        level.setBlock(pos.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        level.setBlock(pos, Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER), 2);
        level.setBlock(pos.above(), Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER), 2);
    }

    private static net.minecraft.nbt.ListTag inventory(BuilderEntity builder) {
        var items = new net.minecraft.nbt.ListTag();
        for (int slot = 0; slot < builder.getInventory().getContainerSize(); slot++) {
            CompoundTag item = builder.getInventory().getItem(slot).save(new CompoundTag());
            item.putInt("Slot", slot); items.add(item);
        }
        return items;
    }

    private static void rejectedAt(String problem, BlockPos pos, String block) {
        require(problem != null && problem.contains(pos.toShortString()) && problem.contains(block),
                "Review lost the exact native admission blocker: " + problem);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
