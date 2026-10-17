package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidConfig;
import com.devfarinsky.siegeoverhaul.RaidSavedData.RaidState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CampWarWorksTest extends MinecraftTestSupport {
    private static final BlockPos CAMP = new BlockPos(0, 64, 0);

    private RaidState raid() {
        RaidState raid = new RaidState("team:test", "Home", 0);
        raid.campPos = CAMP;
        raid.factionId = "wilds_marauders";
        return raid;
    }

    private static void mark(RaidState raid, CampWarWorks.Work work, boolean built, boolean active, int rebuilds) {
        CompoundTag root = raid.campaign.getCompound(CampWarWorks.ROOT), works = root.getCompound("Works");
        CompoundTag entry = new CompoundTag();
        entry.putBoolean("Built", built); entry.putBoolean("Active", active); entry.putInt("Rebuilds", rebuilds);
        entry.putLong("Center", CAMP.offset(18, 0, 0).asLong());
        works.put(work.key, entry); root.put("Works", works); raid.campaign.put(CampWarWorks.ROOT, root);
    }

    @Test void everyWorkIsAFiniteHallWithItsOwnRealNonContainerKeystone() {
        Set<String> keystones = new HashSet<>();
        for (CampWarWorks.Work work : CampWarWorks.Work.values()) for (Direction entrance : Direction.Plane.HORIZONTAL) {
            BlockPos center = CAMP.offset(18, 0, 0);
            Map<Long, String> plan = CampWarWorksLayout.structure(center, entrance, work, "wilds_marauders");
            // Foundation (49 floor cells plus raised fill) and entrance steps must still fit one native job.
            assertTrue(plan.size() + 49 * 3 + 40 < 512, work + " " + plan.size());
            assertEquals(work.keystoneBlock, plan.get(CampStructures.keystone(center, entrance).asLong()), work.toString());
            for (String id : plan.values()) {
                var block = BuiltInRegistries.BLOCK.get(new ResourceLocation(id));
                assertNotEquals(Blocks.AIR, block, id);
                assertFalse(block instanceof EntityBlock, work + " places a block entity: " + id);
            }
            for (long key : plan.keySet()) {
                BlockPos p = BlockPos.of(key);
                assertTrue(Math.abs(p.getX() - center.getX()) <= 3 && Math.abs(p.getZ() - center.getZ()) <= 3, work + " " + p);
                assertTrue(p.getY() > center.getY() && p.getY() <= center.getY() + 8, work + " " + p);
            }
        }
        for (CampWarWorks.Work work : CampWarWorks.Work.values())
            assertTrue(keystones.add(work.keystoneBlock), "duplicate keystone " + work.keystoneBlock);
    }

    @Test void worksAreBuiltInOrderThenRebuiltThenTheCampKeepsExpanding() {
        RaidState raid = raid();
        assertEquals(CampWarWorks.Action.BUILD, CampWarWorks.next(raid).action());
        assertEquals(CampWarWorks.Work.SUPPLY_DEPOT, CampWarWorks.next(raid).work());
        mark(raid, CampWarWorks.Work.SUPPLY_DEPOT, true, true, 0);
        assertEquals(CampWarWorks.Work.LOOKOUT, CampWarWorks.next(raid).work());
        for (CampWarWorks.Work work : CampWarWorks.Work.values()) mark(raid, work, true, true, 0);
        assertEquals(CampWarWorks.Action.TENT, CampWarWorks.next(raid).action(), "everything stands: expand");
        mark(raid, CampWarWorks.Work.BARRACKS, true, false, 0);
        assertEquals(new CampWarWorks.Next(CampWarWorks.Action.REBUILD, CampWarWorks.Work.BARRACKS), CampWarWorks.next(raid));
        mark(raid, CampWarWorks.Work.BARRACKS, true, false, RaidConfig.CAMP_WORKS_REBUILDS.get());
        assertEquals(CampWarWorks.Action.TENT, CampWarWorks.next(raid).action(), "rebuilds are bounded");
        CompoundTag root = raid.campaign.getCompound(CampWarWorks.ROOT);
        root.putInt("Tents", RaidConfig.CAMP_WORKS_TENTS.get());
        raid.campaign.put(CampWarWorks.ROOT, root);
        assertEquals(CampWarWorks.Action.NONE, CampWarWorks.next(raid).action());
        assertFalse(CampWarWorks.hasWork(raid));
        RaidConfig.CAMP_WAR_WORKS.set(false);
        assertEquals(CampWarWorks.Action.NONE, CampWarWorks.next(raid).action());
    }

    @Test void aWorkThatCannotBeStartedIsSkippedAfterBoundedAttempts() {
        RaidState raid = raid();
        CompoundTag root = new CompoundTag(), works = new CompoundTag(), depot = new CompoundTag();
        depot.putInt("Attempts", CampWarWorks.MAX_ATTEMPTS);
        works.put(CampWarWorks.Work.SUPPLY_DEPOT.key, depot); root.put("Works", works);
        raid.campaign.put(CampWarWorks.ROOT, root);
        assertEquals(CampWarWorks.Work.LOOKOUT, CampWarWorks.next(raid).work());
    }

    @Test void effectsApplyOnlyWhileTheKeystoneStands() {
        RaidState raid = raid();
        int base = RaidConfig.CAMP_WORKS_SECONDS.get() * 20;
        assertEquals(base, CampWarWorks.intervalTicks(raid));
        assertEquals(0, CampWarWorks.waveBonus(raid));
        mark(raid, CampWarWorks.Work.SUPPLY_DEPOT, true, true, 0);
        mark(raid, CampWarWorks.Work.BARRACKS, true, true, 0);
        assertEquals(base / 2, CampWarWorks.intervalTicks(raid));
        assertEquals(RaidConfig.BARRACKS_EXTRA_RAIDERS.get(), CampWarWorks.waveBonus(raid));
        mark(raid, CampWarWorks.Work.BARRACKS, true, false, 0);
        assertEquals(0, CampWarWorks.waveBonus(raid), "destroyed barracks add nothing");
        CompoundTag entry = CampWarWorks.entry(raid, CampWarWorks.Work.SUPPLY_DEPOT).copy();
        entry.putBoolean("AwaitingBuild", true);
        CompoundTag root = raid.campaign.getCompound(CampWarWorks.ROOT), works = root.getCompound("Works");
        works.put(CampWarWorks.Work.SUPPLY_DEPOT.key, entry); root.put("Works", works);
        assertEquals(base, CampWarWorks.intervalTicks(raid), "a rebuild under way is not standing yet");
    }

    @Test void completionUnlocksOnlyTheWorkWhoseKeystoneWasInTheJob() {
        RaidState raid = raid();
        BlockPos depotKey = CAMP.offset(18, 65, 0), lookoutKey = CAMP.offset(-18, 65, 0);
        CompoundTag root = new CompoundTag(), works = new CompoundTag();
        for (var pair : Map.of(CampWarWorks.Work.SUPPLY_DEPOT, depotKey, CampWarWorks.Work.LOOKOUT, lookoutKey).entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putBoolean("AwaitingBuild", true); entry.putLong("Pos", pair.getValue().asLong());
            works.put(pair.getKey().key, entry);
        }
        root.put("Works", works); raid.campaign.put(CampWarWorks.ROOT, root);
        raid.pendingCampBlocks.put(depotKey.asLong(), CampWarWorks.Work.SUPPLY_DEPOT.keystoneBlock);
        CampWarWorks.constructionCompleted(raid);
        assertTrue(CampWarWorks.entry(raid, CampWarWorks.Work.SUPPLY_DEPOT).getBoolean("Built"));
        assertFalse(CampWarWorks.entry(raid, CampWarWorks.Work.SUPPLY_DEPOT).getBoolean("AwaitingBuild"));
        assertFalse(CampWarWorks.entry(raid, CampWarWorks.Work.LOOKOUT).getBoolean("Built"));
    }

    @Test void sitesRingTheWallAndKeepClearOfTheWarGate() {
        RaidState raid = raid();
        for (CampWarWorks.Work work : CampWarWorks.Work.values()) {
            var sites = CampWarWorks.workSites(raid, work);
            assertEquals(8, sites.size());
            for (BlockPos p : sites) {
                double distance = Math.sqrt(p.distSqr(CAMP));
                assertTrue(distance >= 17 && distance <= 19, p.toString());
            }
        }
        raid.warGate.putLong("Center", CAMP.offset(18, 0, 0).asLong());
        assertTrue(CampWarWorks.workSites(raid, CampWarWorks.Work.SUPPLY_DEPOT).stream()
                .noneMatch(p -> p.equals(CAMP.offset(18, 0, 0))));
        mark(raid, CampWarWorks.Work.LOOKOUT, true, true, 0);
        assertTrue(CampWarWorks.crowded(raid, CAMP.offset(20, 0, 2), 10));
        assertFalse(CampWarWorks.crowded(raid, CAMP.offset(-18, 0, 0), 10));
    }

    @Test void tentsStandOnAPadOverLowerGroundAndUseFactionCloth() {
        BlockPos floor = new BlockPos(28, 65, 0);
        Map<Long, Integer> ground = new HashMap<>();
        ground.put(new BlockPos(26, 0, -2).asLong(), 64);
        Map<Long, String> plan = CampWarWorksLayout.tent(floor, ground, "minecraft:green_wool");
        assertEquals("minecraft:dirt", plan.get(new BlockPos(26, 64, -2).asLong()));
        assertEquals("minecraft:green_wool", plan.get(floor.offset(0, 3, 0).asLong()), "ridge");
        assertEquals("minecraft:hay_block", plan.get(floor.offset(0, 1, 0).asLong()));
        assertTrue(plan.size() < 60, String.valueOf(plan.size()));
    }
}
