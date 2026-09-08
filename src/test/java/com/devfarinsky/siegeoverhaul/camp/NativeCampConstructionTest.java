package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.devfarinsky.siegeoverhaul.ModConstants.Tags.*;

class NativeCampConstructionTest extends MinecraftTestSupport {
    @Test void upgradeRecoversOnlyEmptyGateCellsOnce() {
        var level=org.mockito.Mockito.mock(net.minecraft.server.level.ServerLevel.class);
        var raid=new RaidSavedData.RaidState("team:test","home",0);
        CompoundTag cells=new CompoundTag();
        for(int i=0;i<3;i++)cells.putString(Long.toString(new BlockPos(i,70,0).asLong()),"minecraft:obsidian");
        raid.warGate.put("Blocks",cells);
        org.mockito.Mockito.when(level.hasChunkAt(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        org.mockito.Mockito.when(level.getBlockState(org.mockito.ArgumentMatchers.any())).thenAnswer(a -> switch(((BlockPos)a.getArgument(0)).getX()) {
            case 0 -> Blocks.AIR.defaultBlockState(); case 1 -> Blocks.CHEST.defaultBlockState(); default -> Blocks.OBSIDIAN.defaultBlockState();
        });
        NativeCampConstruction.recoverMissingGateCells(level,raid);
        assertEquals(Set.of(new BlockPos(0,70,0).asLong()),raid.pendingCampBlocks.keySet());
        raid.pendingCampBlocks.clear();
        NativeCampConstruction.recoverMissingGateCells(level,raid);
        assertTrue(raid.pendingCampBlocks.isEmpty());
    }

    @Test void successfulFlowerClearDoesNotAttemptToSetAirTwice() {
        var level=org.mockito.Mockito.mock(net.minecraft.server.level.ServerLevel.class);
        var raid=new RaidSavedData.RaidState("team:test","home",0);
        org.mockito.Mockito.when(level.getBlockState(BlockPos.ZERO)).thenReturn(Blocks.POPPY.defaultBlockState());
        try(var plants=org.mockito.Mockito.mockStatic(CampVegetation.class)) {
            plants.when(() -> CampVegetation.plant(Blocks.POPPY.defaultBlockState())).thenReturn(true);
            plants.when(() -> CampVegetation.clear(level,raid,BlockPos.ZERO)).thenReturn(true);
            assertTrue(NativeCampConstruction.prepareCell(level,raid,BlockPos.ZERO));
            org.mockito.Mockito.verify(level,org.mockito.Mockito.never()).setBlock(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyInt());
        }
    }

    @Test void nativeSuppliesIncludeNonBlockIngredientsExactlyOncePerCell() throws Exception {
        var level = org.mockito.Mockito.mock(net.minecraft.server.level.ServerLevel.class);
        Map<Long,String> jobs = new LinkedHashMap<>();
        jobs.put(BlockPos.ZERO.asLong(), "minecraft:amethyst_block");
        jobs.put(BlockPos.ZERO.above().asLong(), "minecraft:amethyst_block");
        jobs.put(BlockPos.ZERO.east().asLong(), "minecraft:stone_bricks");
        try(var bridge=org.mockito.Mockito.mockStatic(com.devfarinsky.siegeoverhaul.compat.WorkersBridge.class)) {
            bridge.when(() -> com.devfarinsky.siegeoverhaul.compat.WorkersBridge.buildMaterial(level,Blocks.AMETHYST_BLOCK)).thenReturn(Items.AMETHYST_SHARD);
            bridge.when(() -> com.devfarinsky.siegeoverhaul.compat.WorkersBridge.buildMaterial(level,Blocks.STONE_BRICKS)).thenReturn(Items.STONE);
            var supplies=NativeCampConstruction.materials(level,jobs);
            assertEquals(2,supplies.size());
            assertTrue(supplies.get(0).is(Items.AMETHYST_SHARD));
            assertEquals(2,supplies.get(0).getCount());
            assertTrue(supplies.get(1).is(Items.STONE));
            assertEquals(1,supplies.get(1).getCount());
        }
    }

    @Test void connectedFenceAndBentStairsDoNotPauseConstruction() {
        assertTrue(NativeCampConstruction.safeCell(Blocks.OAK_FENCE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.FenceBlock.NORTH,true),"minecraft:oak_fence"));
        assertTrue(NativeCampConstruction.safeCell(Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                .setValue(net.minecraft.world.level.block.StairBlock.SHAPE,net.minecraft.world.level.block.state.properties.StairsShape.INNER_LEFT),"minecraft:stone_brick_stairs"));
        assertFalse(NativeCampConstruction.safeCell(Blocks.OAK_FENCE.defaultBlockState(),"minecraft:spruce_fence"));
    }
    @Test void flowersAreClearableButCropsContainersAndTreesAreNot() {
        for(var block:new net.minecraft.world.level.block.Block[]{Blocks.DANDELION,Blocks.POPPY,Blocks.SUNFLOWER,Blocks.LARGE_FERN})
            assertTrue(CampVegetation.plant(block.defaultBlockState()));
        for(var block:new net.minecraft.world.level.block.Block[]{Blocks.WHEAT,Blocks.OAK_SAPLING,Blocks.OAK_LOG,Blocks.CHEST,Blocks.STONE})
            assertFalse(CampVegetation.plant(block.defaultBlockState()));
    }
    @Test
    void nativeBlueprintRoundTripsNegativeCoordinatesAndHeight() {
        Map<Long, String> jobs = new LinkedHashMap<>();
        jobs.put(new BlockPos(-35, 70, -42).asLong(), "minecraft:spruce_log");
        jobs.put(new BlockPos(-30, 74, -38).asLong(), "minecraft:red_wool");
        CompoundTag blueprint = NativeCampConstruction.blueprint(jobs);
        assertEquals("south", blueprint.getString("facing"));
        assertFalse(blueprint.contains("entities"));
        Set<Long> recovered = new HashSet<>();
        for (Tag tag : blueprint.getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag block = (CompoundTag) tag;
            // Workers 2: origin + facing*z + clockwise*(width-1-x) + up*y.
            recovered.add(new BlockPos(-30 - (blueprint.getInt("width") - 1 - block.getInt("x")),
                    70 + block.getInt("y"), -42 + block.getInt("z")).asLong());
        }
        assertEquals(jobs.keySet(), recovered);
    }

    @Test
    void suppliesRespectStackLimitsWithoutLosingOrDuplicatingMaterials() {
        ItemStack wool = new ItemStack(Items.RED_WOOL, 145);
        var stacks = NativeCampConstruction.splitStacks(List.of(wool, new ItemStack(Items.IRON_PICKAXE, 2)));
        assertEquals(List.of(64, 64, 17, 1, 1), stacks.stream().map(ItemStack::getCount).toList());
        assertEquals(145, wool.getCount());
        assertEquals(145, stacks.stream().filter(s -> s.is(Items.RED_WOOL)).mapToInt(ItemStack::getCount).sum());
    }

    @Test
    void playerReplacementsAreNeverApprovedForNativeMining() {
        assertTrue(NativeCampConstruction.safeCell(Blocks.AIR.defaultBlockState(), "minecraft:red_wool"));
        assertTrue(NativeCampConstruction.safeCell(Blocks.RED_WOOL.defaultBlockState(), "minecraft:red_wool"));
        assertFalse(NativeCampConstruction.safeCell(Blocks.CHEST.defaultBlockState(), "minecraft:red_wool"));
        assertFalse(NativeCampConstruction.safeCell(Blocks.STONE.defaultBlockState(), "minecraft:red_wool"));
        assertFalse(NativeCampConstruction.safeCell(Blocks.FURNACE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, true), "minecraft:furnace"));
        assertFalse(NativeCampConstruction.safeCell(Blocks.WATER.defaultBlockState(), "minecraft:red_wool"));
    }

    @Test
    void temporaryObstructionsPauseAndResumeTheSameNativeJobs() {
        var level = org.mockito.Mockito.mock(net.minecraft.server.level.ServerLevel.class);
        var server = org.mockito.Mockito.mock(net.minecraft.server.MinecraftServer.class);
        var worker = org.mockito.Mockito.mock(net.minecraft.world.entity.Mob.class);
        var barrel = org.mockito.Mockito.mock(net.minecraft.world.level.block.entity.BlockEntity.class);
        RaidSavedData data = new RaidSavedData();
        RaidSavedData.RaidState raid = new RaidSavedData.RaidState("team:test", "home", 0);
        UUID owner = UUID.randomUUID(), workerId = UUID.randomUUID();
        raid.nativeCamp.putUUID(CAMP_OWNER, owner);
        raid.nativeCamp.putLong(CAMP_SUPPLY_POS, BlockPos.ZERO.asLong());
        raid.pendingCampBlocks.put(new BlockPos(2, 0, 0).asLong(), "minecraft:red_wool");
        raid.campWorkers.add(workerId);
        raid.campUsesWorkers = true;
        data.raids.put(raid.teamKey, raid);
        CompoundTag supplyData = new CompoundTag();
        supplyData.putUUID(CAMP_SUPPLY_OWNER, owner);
        CompoundTag workerData = new CompoundTag();
        workerData.putString(CAMP_WORKER_TEAM, raid.teamKey);
        org.mockito.Mockito.when(level.getServer()).thenReturn(server);
        org.mockito.Mockito.when(level.hasChunkAt(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        org.mockito.Mockito.when(level.getBlockEntity(BlockPos.ZERO)).thenReturn(barrel);
        org.mockito.Mockito.when(barrel.getPersistentData()).thenReturn(supplyData);
        org.mockito.Mockito.when(level.getEntity(workerId)).thenReturn(worker);
        org.mockito.Mockito.when(worker.level()).thenReturn(level);
        org.mockito.Mockito.when(worker.getPersistentData()).thenReturn(workerData);
        org.mockito.Mockito.when(level.getBlockState(new BlockPos(2, 0, 0))).thenReturn(Blocks.CHEST.defaultBlockState());
        com.devfarinsky.siegeoverhaul.RaidConfig.PAUSE_WHEN_FACTION_OFFLINE.set(false);
        try (var saves = org.mockito.Mockito.mockStatic(RaidSavedData.class);
             var bridge = org.mockito.Mockito.mockStatic(com.devfarinsky.siegeoverhaul.compat.WorkersBridge.class)) {
            saves.when(() -> RaidSavedData.get(server)).thenReturn(data);
            bridge.when(com.devfarinsky.siegeoverhaul.compat.WorkersBridge::available).thenReturn(true);
            bridge.when(() -> com.devfarinsky.siegeoverhaul.compat.WorkersBridge.parkBuilder(worker)).thenReturn(true);
            var event = new net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent(worker);
            com.devfarinsky.siegeoverhaul.RaidEvents.onCampWorkerTick(event);
            assertTrue(event.isCanceled());
            assertFalse(raid.pendingCampBlocks.isEmpty());
            assertTrue(NativeCampConstruction.active(raid));
            org.mockito.Mockito.verify(worker, org.mockito.Mockito.never()).discard();
            assertTrue(raid.campWorkers.contains(workerId));
            bridge.verify(() -> com.devfarinsky.siegeoverhaul.compat.WorkersBridge.parkBuilder(worker), org.mockito.Mockito.never());
            org.mockito.Mockito.when(level.getBlockState(new BlockPos(2, 0, 0))).thenReturn(Blocks.AIR.defaultBlockState());
            org.mockito.Mockito.when(level.getFluidState(new BlockPos(2, 0, 0))).thenReturn(net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState());
            net.minecraft.server.level.ServerPlayer player = org.mockito.Mockito.mock(net.minecraft.server.level.ServerPlayer.class);
            org.mockito.Mockito.when(player.isAlive()).thenReturn(true);
            org.mockito.Mockito.when(player.getBoundingBox()).thenReturn(new net.minecraft.world.phys.AABB(new BlockPos(2,0,0)));
            org.mockito.Mockito.when(level.players()).thenReturn(java.util.List.of(player));
            assertFalse(NativeCampConstruction.safeToTick(level, raid));
            assertTrue(NativeCampConstruction.active(raid));
            org.mockito.Mockito.when(level.players()).thenReturn(java.util.List.of());
            assertTrue(NativeCampConstruction.safeToTick(level, raid));
            assertFalse(raid.pendingCampBlocks.isEmpty());
            org.mockito.Mockito.when(level.isDay()).thenReturn(true);
            raid.campBuildTicks = com.devfarinsky.siegeoverhaul.RaidConfig.CAMP_MAX_BUILD_SECONDS.get()*20-20;
            NativeCampConstruction.tick(level, raid);
            assertTrue(NativeCampConstruction.active(raid));
            assertFalse(raid.pendingCampBlocks.isEmpty());
            org.mockito.Mockito.verify(level, org.mockito.Mockito.never()).setBlock(
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
        }
    }

    @Test
    void saveRetainsNativeOwnershipAndSupplyIdentityWithoutReprovisioning() {
        RaidSavedData.RaidState raid = new RaidSavedData.RaidState("team:test", "home", 0);
        raid.nativeCamp.putUUID(CAMP_OWNER, UUID.randomUUID());
        raid.nativeCamp.putUUID(CAMP_BUILD_AREA, UUID.randomUUID());
        raid.nativeCamp.putUUID(CAMP_STORAGE_AREA, UUID.randomUUID());
        raid.nativeCamp.putLong(CAMP_SUPPLY_POS, new BlockPos(-20, 70, 3).asLong());
        assertEquals(raid.nativeCamp, RaidSavedData.RaidState.load(raid.save()).nativeCamp);
    }
}
