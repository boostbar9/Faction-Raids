package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DefenseStructuresTest extends MinecraftTestSupport {
    private final BlockPos origin = new BlockPos(-20, 70, -30);

    @Test void allRotationsRetainMaterialsAndExactNativeWorldCoordinates() {
        for (var kind : DefenseBlueprint.Kind.values()) {
            var reference = DefenseBlueprint.create(kind, origin, Direction.SOUTH);
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                var plan = DefenseBlueprint.create(kind, origin, facing);
                assertEquals(kind.width * kind.depth, plan.footprint().size());
                assertEquals(reference.blocks().size(), plan.blocks().size());
                assertEquals(reference.materials(), plan.materials());
                assertTrue(plan.blocks().size() < 300);
                var nbt = TerritoryFortification.blueprint(plan.blocks(), plan.min(), plan.max());
                var recovered = new HashSet<Long>();
                for (var value : nbt.getList("blocks", Tag.TAG_COMPOUND)) {
                    var cell = (CompoundTag) value;
                    BlockPos world = new BlockPos(plan.max().getX() - (nbt.getInt("width") - 1 - cell.getInt("x")),
                            plan.min().getY() + cell.getInt("y"), plan.min().getZ() + cell.getInt("z"));
                    recovered.add(world.asLong());
                    assertEquals(plan.blocks().get(world.asLong()), cell.getCompound("state").getString("Name"));
                }
                assertEquals(plan.blocks().keySet(), recovered);
            }
        }
    }

    @Test void elevatedDecksHaveContinuousTwoWideStepsAndTwoBlocksOfHeadroom() {
        for (var kind : List.of(DefenseBlueprint.Kind.WATCHTOWER, DefenseBlueprint.Kind.GATEHOUSE)) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                var plan = DefenseBlueprint.create(kind, origin, facing);
                for (int x : kind == DefenseBlueprint.Kind.WATCHTOWER ? new int[]{0, 1} : new int[]{-4, -3}) {
                    for (int z = 0; z <= 4; z++) {
                        int floor = Math.min(z, 3);
                        BlockPos step = origin.relative(facing.getClockWise(), x).relative(facing, z).above(floor);
                        assertNotNull(plan.blocks().get(step.asLong()));
                        assertFalse(plan.blocks().containsKey(step.above().asLong()));
                        assertFalse(plan.blocks().containsKey(step.above(2).asLong()));
                    }
                }
            }
        }
    }

    @Test void gatehouseKeepsItsThreeWidePassageOpenUnderTheDeck() {
        var plan = DefenseBlueprint.create(DefenseBlueprint.Kind.GATEHOUSE, origin, Direction.SOUTH);
        for (int x = -1; x <= 1; x++) for (int z = 0; z < 9; z++) {
            for (int y = 0; y < 3; y++) assertFalse(plan.blocks().containsKey(origin.offset(x, y, z).asLong()));
            if (z >= 4) assertNotNull(plan.blocks().get(origin.offset(x, 3, z).asLong()));
        }
    }

    private ServerLevel clearLevel() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.getMinBuildHeight()).thenReturn(-64);
        when(level.getAllEntities()).thenReturn(List.of());
        var storage=mock(net.minecraft.world.level.storage.DimensionDataStorage.class);
        when(level.getDataStorage()).thenReturn(storage);
        when(storage.computeIfAbsent(any(),any(),anyString())).thenAnswer(call -> ((java.util.function.Supplier<?>)call.getArgument(1)).get());
        when(level.getMaxBuildHeight()).thenReturn(320);
        when(level.getWorldBorder()).thenReturn(new WorldBorder());
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getBlockState(any())).thenAnswer(call -> ((BlockPos) call.getArgument(0)).getY() < origin.getY()
                ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        return level;
    }
    private DefenseBlueprint.Plan tower() { return DefenseBlueprint.create(DefenseBlueprint.Kind.WATCHTOWER, origin, Direction.NORTH); }

    @Test void everyManualPlanRetainsItsExactPreflightFootprintAndHeadroom() {
        for (var kind : DefenseBlueprint.Kind.values()) for (var facing : Direction.Plane.HORIZONTAL) {
            var plan = DefenseBlueprint.create(kind, origin, facing);
            var reserved = DefenseStructures.reservedCells(plan);
            assertEquals(plan.footprint().size() * kind.height, reserved.size(), kind.label);
            for (var base : plan.footprint()) {
                assertTrue(reserved.contains(base));
                assertTrue(reserved.contains(base.atY(plan.max().getY())));
                assertFalse(reserved.contains(base.atY(plan.max().getY() + 1)));
            }
            assertTrue(plan.blocks().keySet().stream().allMatch(cell -> reserved.contains(BlockPos.of(cell))));
            assertThrows(UnsupportedOperationException.class, reserved::clear);
        }
    }

    @Test void everyManualBodyCavityIsReservedAndOccupiedCavitiesRejectWithoutExcavation() {
        for (var kind : List.of(DefenseBlueprint.Kind.WALL, DefenseBlueprint.Kind.CORNER))
            for (var facing : Direction.Plane.HORIZONTAL) {
                var plan = DefenseBlueprint.create(kind, origin, facing); var reserved = DefenseStructures.reservedCells(plan);
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = 0; y < 3; y++) {
                    var cell = origin.relative(facing.getClockWise(), x).relative(facing, z).above(y);
                    assertTrue(reserved.contains(cell)); assertFalse(plan.blocks().containsKey(cell.asLong()));
                }
                for (int y = 0; y < 3; y++) for (var block : List.of(Blocks.COBBLESTONE, Blocks.CHEST, Blocks.WATER)) {
                    var level = clearLevel(); when(level.getBlockState(origin.above(y))).thenReturn(block.defaultBlockState());
                    assertNotNull(DefenseStructures.siteProblem(level, plan, p -> true));
                    verify(level, never()).setBlock(any(), any(), anyInt(), anyInt());
                }
            }
    }

    @Test void clearFlatClaimedSiteIsAccepted() {
        assertNull(DefenseStructures.siteProblem(clearLevel(), tower(), p -> true));
    }
    @Test void clearManualFootprintReportsTheSameNearbySandRefusalAsNativeAdmission() {
        var level = clearLevel();
        var plan = DefenseBlueprint.create(DefenseBlueprint.Kind.WALL, origin, Direction.SOUTH);
        BlockPos sand = origin.east(3);
        when(level.getBlockState(sand)).thenReturn(Blocks.SAND.defaultBlockState());
        assertFalse(plan.footprint().contains(sand), "The neighbor is outside the previously checked footprint");
        for (BlockPos base : plan.footprint()) {
            assertTrue(level.getBlockState(base).isAir());
            assertTrue(level.getBlockState(base.below()).isFaceSturdy(level, base.below(), Direction.UP));
        }
        String nativeProblem = com.devfarinsky.siegeoverhaul.nativecompat.NativeConstructionGuard.placementNeighborhoodProblem(
                level, plan.blocks().keySet().stream().map(BlockPos::of).toList());
        assertNotNull(nativeProblem);
        assertTrue(nativeProblem.contains("minecraft:sand"));
        assertTrue(nativeProblem.contains(sand.toShortString()));
        assertEquals(nativeProblem, DefenseStructures.siteProblem(level, plan, p -> true));
        verify(level, never()).setBlock(any(), any(), anyInt(), anyInt());
        verify(level, never()).destroyBlock(any(), anyBoolean(), any());
    }
    @Test void unloadedChunksAreRejectedBeforeReadingAnyTerrain() {
        var level = clearLevel(); when(level.hasChunkAt(any())).thenReturn(false);
        assertNotNull(DefenseStructures.siteProblem(level, tower(), p -> true));
        verify(level, never()).getBlockState(any());
    }
    @Test void foreignClaimsAndWorldLimitsAreRejected() {
        assertNotNull(DefenseStructures.siteProblem(clearLevel(), tower(), p -> !p.equals(origin)));
        var level = clearLevel(); level.getWorldBorder().setSize(10);
        assertNotNull(DefenseStructures.siteProblem(level, tower(), p -> true));
        level = clearLevel(); when(level.getMaxBuildHeight()).thenReturn(73);
        assertNotNull(DefenseStructures.siteProblem(level, tower(), p -> true));
    }
    @Test void gapsHazardsAndWaterloggedOrContainerGroundAreRejected() {
        for (var block : List.of(Blocks.AIR, Blocks.WATER, Blocks.MAGMA_BLOCK, Blocks.CHEST, Blocks.POWDER_SNOW)) {
            var level = clearLevel(); when(level.getBlockState(origin.below())).thenReturn(block.defaultBlockState());
            assertNotNull(DefenseStructures.siteProblem(level, tower(), p -> true), block.toString());
        }
    }
    @Test void existingBuildingsFluidsAndBlockedApproachesAreNeverMined() {
        for (var block : List.of(Blocks.OAK_PLANKS, Blocks.CHEST, Blocks.WATER, Blocks.LAVA, Blocks.NETHER_PORTAL)) {
            var level = clearLevel(); when(level.getBlockState(origin)).thenReturn(block.defaultBlockState());
            assertNotNull(DefenseStructures.siteProblem(level, tower(), p -> true));
        }
    }
    @Test void occupiedBlockCellsAreRejected() {
        var level = clearLevel();
        when(level.getEntities(isNull(Entity.class), any(AABB.class), any())).thenReturn(List.of(mock(Entity.class)));
        assertNotNull(DefenseStructures.siteProblem(level, tower(), p -> true));
    }
    @Test void anotherUnstartedDefenseReservesItsEmptyFootprint() {
        var level = clearLevel(); var area = mock(Entity.class); var tag = new CompoundTag();
        tag.putLong("SiegeDefenseSiteMin", tower().min().asLong());
        tag.putLong("SiegeDefenseSiteMax", tower().max().asLong());
        when(area.getPersistentData()).thenReturn(tag);
        when(level.getEntitiesOfClass(eq(Entity.class), any(), any())).thenReturn(List.of(area));
        when(level.getAllEntities()).thenReturn(List.of(area)); when(area.isAlive()).thenReturn(true);
        try (var bridge = mockStatic(WorkersBridge.class)) {
            bridge.when(() -> WorkersBridge.isBuildArea(area)).thenReturn(true);
            assertNotNull(DefenseStructures.siteProblem(level, tower(), p -> true));
        }
    }
    @Test void structureJobsRequireAlreadyHiredBuilders() {
        var level = clearLevel(); var player = mock(ServerPlayer.class); var builder = mock(Mob.class);
        UUID owner = UUID.randomUUID();
        when(player.getUUID()).thenReturn(owner); when(builder.getPersistentData()).thenReturn(new CompoundTag());
        when(level.getEntitiesOfClass(eq(Mob.class), any(), any())).thenReturn(List.of(builder));
        try (var bridge = mockStatic(WorkersBridge.class)) {
            bridge.when(() -> WorkersBridge.isBuilder(builder)).thenReturn(true);
            assertNull(TerritoryFortification.findNearbyBuilder(level, player, origin, true).builder());
            bridge.when(() -> WorkersBridge.readWorkerOwner(builder)).thenReturn(owner);
            assertSame(builder, TerritoryFortification.findNearbyBuilder(level, player, origin, true).builder());
        }
    }

    @Test void nativeHandoffPrecedesPaymentAndKeepsInventoryUntouched() throws Exception {
        checkHandoff(true, true, true);
    }
    @Test void rejectedBuilderIsNotChargedAndItsAreaIsRemoved() throws Exception {
        checkHandoff(false, true, false);
    }
    @Test void rejectedPaymentDetachesAndRemovesTheUnpaidArea() throws Exception {
        checkHandoff(true, false, false);
    }
    private void checkHandoff(boolean accepts, boolean pays, boolean success) throws Exception {
        var player = mock(ServerPlayer.class); var builder = mock(Mob.class); var area = mock(Entity.class);
        var level = clearLevel(); UUID owner = UUID.randomUUID();
        when(player.serverLevel()).thenReturn(level); when(player.getUUID()).thenReturn(owner);
        when(player.getGameProfile()).thenReturn(new GameProfile(owner, "BuilderOwner"));
        when(builder.getPersistentData()).thenReturn(new CompoundTag()); when(builder.getUUID()).thenReturn(UUID.randomUUID());
        when(area.getPersistentData()).thenReturn(new CompoundTag()); when(area.getUUID()).thenReturn(UUID.randomUUID());
        when(area.blockPosition()).thenReturn(tower().min()); when(level.addFreshEntity(area)).thenReturn(true);
        try (var bridge = mockStatic(WorkersBridge.class); var payment = mockStatic(PaymentSource.class);
             var access = mockStatic(WallBuilderAccess.class);
             var guard = mockStatic(com.devfarinsky.siegeoverhaul.nativecompat.NativeConstructionGuard.class)) {
            guard.when(() -> com.devfarinsky.siegeoverhaul.nativecompat.NativeConstructionGuard.protect(
                    player, builder, area, DefenseStructures.reservedCells(tower()))).thenReturn(true);
            guard.when(() -> com.devfarinsky.siegeoverhaul.nativecompat.NativeConstructionGuard.activate(area)).thenReturn(true);
            bridge.when(() -> WorkersBridge.createProtectedPlayerArea(eq(player), eq(builder), any(), anyInt(), anyInt(), anyInt(), any())).thenReturn(area);
            bridge.when(() -> WorkersBridge.discardPlayerArea(area)).thenAnswer(call -> { area.discard(); return true; });
            bridge.when(() -> WorkersBridge.assignBuildAreaDirectly(builder, area)).thenReturn(accepts);
            bridge.when(() -> WorkersBridge.releasePlayerJob(builder, area)).thenReturn(true);
            payment.when(() -> PaymentSource.consume(player, DefenseBlueprint.Kind.WATCHTOWER.price)).thenAnswer(call -> {
                bridge.verify(() -> WorkersBridge.startBlueprint(eq(area), any()));
                bridge.verify(() -> WorkersBridge.assignBuildAreaDirectly(builder, area));
                return pays;
            });
            assertEquals(success, DefenseStructures.startJob(player, builder, tower(), DefenseBlueprint.Kind.WATCHTOWER));
            payment.verify(() -> PaymentSource.consume(player, DefenseBlueprint.Kind.WATCHTOWER.price), accepts ? times(1) : never());
            guard.verify(() -> com.devfarinsky.siegeoverhaul.nativecompat.NativeConstructionGuard.activate(area), success ? times(1) : never());
            if (!success) {
                verify(area).discard();
                assertFalse(builder.getPersistentData().hasUUID(com.devfarinsky.siegeoverhaul.ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID));
            } else verify(area, never()).discard();
            verify(builder, never()).setItemSlot(any(), any());
        }
    }
}
