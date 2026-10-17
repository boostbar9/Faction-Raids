package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.ModConstants;
import com.devfarinsky.siegeoverhaul.RaidConfig;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.RaidSavedData.RaidState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CampCrewTest extends MinecraftTestSupport {
    @Test
    void deadBuilderReleasesItsCrewSlotAndSurvivorsKeepTheJob() {
        RaidSavedData data = new RaidSavedData();
        RaidState raid = raid();
        UUID dead = UUID.randomUUID(), survivor = UUID.randomUUID();
        raid.campWorkers.add(dead);
        raid.campWorkers.add(survivor);
        data.raids.put(raid.teamKey, raid);
        ServerLevel level = mock(ServerLevel.class);
        MinecraftServer server = mock(MinecraftServer.class);
        when(level.getServer()).thenReturn(server);
        Mob worker = builder(dead, raid.teamKey);
        try (var saves = mockStatic(RaidSavedData.class)) {
            saves.when(() -> RaidSavedData.get(server)).thenReturn(data);
            CampCrew.onDeath(level, worker);
        }
        assertFalse(raid.campWorkers.contains(dead));
        assertTrue(raid.campWorkers.contains(survivor));
        assertFalse(raid.pendingCampBlocks.isEmpty(), "the unfinished job belongs to the survivors");
    }

    @Test
    void periodicUpkeepDropsLoadedDeadBuildersButKeepsUnloadedOnes() {
        RaidSavedData data = new RaidSavedData();
        RaidState raid = raid();
        raid.campUsesWorkers = true;
        raid.campUpgradeStage = CampPerimeter.COMPLETE_STAGE;
        raid.pendingCampBlocks.clear();
        UUID dead = UUID.randomUUID(), unloaded = UUID.randomUUID();
        raid.campWorkers.add(dead);
        raid.campWorkers.add(unloaded);
        data.raids.put(raid.teamKey, raid);
        ServerLevel level = mock(ServerLevel.class);
        MinecraftServer server = mock(MinecraftServer.class);
        when(level.getServer()).thenReturn(server);
        Mob body = builder(dead, raid.teamKey);
        when(body.isAlive()).thenReturn(false);
        when(level.getEntity(dead)).thenReturn(body);
        try (var saves = mockStatic(RaidSavedData.class)) {
            saves.when(() -> RaidSavedData.get(server)).thenReturn(data);
            CampCrew.tick(level, raid);
        }
        assertFalse(raid.campWorkers.contains(dead));
        assertTrue(raid.campWorkers.contains(unloaded), "an unloaded builder is never treated as dead");
    }

    @Test
    void replacementsAreBoundedByBudgetAndCooldown() {
        RaidConfig.CAMP_BUILDER_REPLACEMENTS.set(2);
        RaidState raid = raid();
        assertTrue(CampCrew.needsCrew(raid));
        assertTrue(CampCrew.replacementAllowed(raid));
        raid.campBuilderReplaceTicks = 40;
        assertFalse(CampCrew.replacementAllowed(raid));
        raid.campBuilderReplaceTicks = 0;
        raid.campBuilderReplacements = 2;
        assertFalse(CampCrew.replacementAllowed(raid));
        RaidConfig.CAMP_BUILDER_REPLACEMENTS.set(0);
        raid.campBuilderReplacements = 0;
        assertFalse(CampCrew.replacementAllowed(raid), "0 keeps the old never-replace behavior");
    }

    @Test
    void finishedOrCapturedCampsNeedNoReplacements() {
        RaidState raid = raid();
        raid.pendingCampBlocks.clear();
        raid.campUpgradeStage = CampPerimeter.COMPLETE_STAGE;
        assertFalse(CampCrew.needsCrew(raid));
        raid.campUpgradeStage = CampPerimeter.FIRST_STAGE + 2;
        assertTrue(CampCrew.needsCrew(raid));
        raid.coreCaptured = true;
        assertFalse(CampCrew.needsCrew(raid));
    }

    @Test
    void returnedMaterialsMergeIntoTheSupplyBarrel() {
        SimpleContainer barrel = new SimpleContainer(2);
        barrel.setItem(0, new ItemStack(Items.OAK_PLANKS, 60));
        ItemStack carried = new ItemStack(Items.OAK_PLANKS, 70);
        CampCrew.insert(barrel, carried);
        assertEquals(64, barrel.getItem(0).getCount());
        assertEquals(64, barrel.getItem(1).getCount());
        assertEquals(2, carried.getCount(), "what does not fit stays with the builder");
    }

    @Test
    void reloadKeepsPerimeterProgressAndReplacementBudget() {
        for (int stage : new int[]{CampPerimeter.FIRST_STAGE + 4, CampPerimeter.COMPLETE_STAGE}) {
            RaidState raid = raid();
            raid.campUpgradeStage = stage;
            raid.campUpgradeTicks = 6000;
            raid.campBuilderReplacements = 1;
            raid.campBuilderReplaceTicks = 400;
            RaidState loaded = RaidState.load(raid.save());
            assertEquals(stage, loaded.campUpgradeStage, "perimeter sections must not restart after a reload");
            assertEquals(6000, loaded.campUpgradeTicks);
            assertEquals(1, loaded.campBuilderReplacements);
            assertEquals(400, loaded.campBuilderReplaceTicks);
        }
        CompoundTag legacy = raid().save();
        legacy.remove("CampBuilderReplacements");
        legacy.remove("CampBuilderReplaceTicks");
        RaidState old = RaidState.load(legacy);
        assertEquals(0, old.campBuilderReplacements);
        assertEquals(0, old.campBuilderReplaceTicks);
    }

    @Test
    void nativeJobIsReleasedOnceTheWholeCrewIsLostForGood() {
        RaidConfig.CAMP_BUILDER_REPLACEMENTS.set(1);
        RaidSavedData data = new RaidSavedData();
        RaidState raid = raid();
        raid.nativeCamp.putUUID(ModConstants.Tags.CAMP_OWNER, UUID.randomUUID());
        raid.campBuilderReplacements = 1;
        data.raids.put(raid.teamKey, raid);
        ServerLevel level = mock(ServerLevel.class);
        MinecraftServer server = mock(MinecraftServer.class);
        when(level.getServer()).thenReturn(server);
        try (var saves = mockStatic(RaidSavedData.class)) {
            saves.when(() -> RaidSavedData.get(server)).thenReturn(data);
            NativeCampConstruction.tick(level, raid);
        }
        assertFalse(NativeCampConstruction.active(raid));
        assertTrue(raid.pendingCampBlocks.isEmpty());
        verify(level, never()).setBlock(any(), any(), anyInt());
    }

    private static Mob builder(UUID id, String team) {
        Mob worker = mock(Mob.class);
        CompoundTag tag = new CompoundTag();
        tag.putString(ModConstants.Tags.CAMP_WORKER_TEAM, team);
        when(worker.getPersistentData()).thenReturn(tag);
        when(worker.getUUID()).thenReturn(id);
        return worker;
    }

    private static RaidState raid() {
        RaidState raid = new RaidState("team:test", "home", 0);
        raid.campPos = new BlockPos(0, 64, 0);
        raid.pendingCampBlocks.put(new BlockPos(3, 64, 0).asLong(), "minecraft:oak_planks");
        return raid;
    }
}
