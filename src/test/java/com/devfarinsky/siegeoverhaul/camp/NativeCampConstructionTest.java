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
    void replacementStopsNativeCrewBeforeTheirAiCanMineIt() {
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
            var event = new net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent(worker);
            com.devfarinsky.siegeoverhaul.RaidEvents.onCampWorkerTick(event);
            assertTrue(event.isCanceled());
            assertTrue(raid.pendingCampBlocks.isEmpty());
            assertFalse(NativeCampConstruction.active(raid));
            org.mockito.Mockito.verify(worker).discard();
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
