package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CampBuildingAccessTest extends MinecraftTestSupport {
    private ServerLevel ground(int y) {
        var level=mock(ServerLevel.class);
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getMinBuildHeight()).thenReturn(-64);
        when(level.getMaxBuildHeight()).thenReturn(320);
        var border=mock(net.minecraft.world.level.border.WorldBorder.class);
        when(level.getWorldBorder()).thenReturn(border);
        when(border.isWithinBounds(any(BlockPos.class))).thenReturn(true);
        when(level.getHeight(any(),anyInt(),anyInt())).thenReturn(y);
        when(level.getFluidState(any())).thenReturn(net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState());
        when(level.getBlockState(any())).thenAnswer(call -> ((BlockPos)call.getArgument(0)).getY()<y
                ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        return level;
    }
    private RaidSavedData.RaidState raid() { return new RaidSavedData.RaidState("team:test","siege_core",0); }

    @Test void lowCourtyardGetsThreeWideStepsInsteadOfAThreeBlockDrop() {
        var level=ground(62);var raid=raid();var center=new BlockPos(-30,64,20);
        for(Direction front:Direction.Plane.HORIZONTAL) {
            var plan=CampBuildingAccess.plan(level,raid,center,front,p->true).orElseThrow();
            for(int side=-1;side<=1;side++) {
                assertEquals("minecraft:spruce_planks",plan.get(center.relative(front,4)
                        .relative(front.getClockWise(),side).atY(63).asLong()));
                assertEquals("minecraft:spruce_planks",plan.get(center.relative(front,5)
                        .relative(front.getClockWise(),side).atY(62).asLong()));
                assertFalse(plan.containsKey(center.relative(front,6).relative(front.getClockWise(),side).atY(62).asLong()));
            }
            assertTrue(plan.size()<=9);
        }
        verify(level,never()).setBlock(any(),any(),anyInt());
    }

    @Test void flatCourtyardNeedsNoRaisedApronOrReplacementOfExistingGround() {
        assertTrue(CampBuildingAccess.plan(ground(64),raid(),new BlockPos(0,64,0),Direction.NORTH,p->true)
                .orElseThrow().isEmpty());
    }

    @Test void inaccessibleOrProtectedEntranceRejectsWholeSiteWithoutMutation() {
        var level=ground(62);var raid=raid();var center=new BlockPos(0,64,0);
        BlockPos blocked=center.south(4).above();
        when(level.getBlockState(blocked)).thenReturn(Blocks.CHEST.defaultBlockState());
        assertTrue(CampBuildingAccess.plan(level,raid,center,Direction.SOUTH,p->true).isEmpty());
        when(level.getBlockState(blocked)).thenReturn(Blocks.AIR.defaultBlockState());
        assertTrue(CampBuildingAccess.plan(level,raid,center,Direction.SOUTH,p->p.getZ()<5).isEmpty());
        when(level.hasChunkAt(center.south(4))).thenReturn(false);
        assertTrue(CampBuildingAccess.plan(level,raid,center,Direction.SOUTH,p->true).isEmpty());
        assertTrue(CampBuildingAccess.plan(ground(60),raid,center,Direction.SOUTH,p->true).isEmpty());
        assertTrue(raid.campBlocks.isEmpty());verify(level,never()).setBlock(any(),any(),anyInt());
    }

    @Test void hollowInteriorMustBeClearAndSavedEntrancesStayReservedAfterReload() {
        var level=ground(64);var raid=raid();var center=new BlockPos(0,64,0);
        var plan=CampUpgradeLayout.structure(center,Direction.NORTH,0);
        assertTrue(CampBuildingAccess.clearInterior(level,raid,center,plan));
        when(level.getBlockState(center.above())).thenReturn(Blocks.OAK_LOG.defaultBlockState());
        assertFalse(CampBuildingAccess.clearInterior(level,raid,center,plan));
        CampStructures.record(raid,0,center,Direction.NORTH);
        var loaded=RaidSavedData.RaidState.load(raid.save());
        assertTrue(CampStructures.accessColumn(loaded,center.north(5)));
        assertFalse(CampStructures.accessColumn(loaded,center.south(5)));
        assertFalse(CampStructures.accessColumn(loaded,center.north(5).east(2)));
    }

    @Test void legacyKeystoneOnlyRecordsStillProtectTheirEntrances() {
        var raid=raid();raid.campPos=new BlockPos(0,64,0);
        var center=raid.campPos.north(15);
        CampStructures.record(raid,0,center,Direction.SOUTH);
        var old=raid.campaign.getCompound(ModConstants.Tags.CAMP_STRUCTURES).getCompound("granary");
        old.remove("Center");old.remove("Entrance");
        var loaded=RaidSavedData.RaidState.load(raid.save());
        assertTrue(CampStructures.accessColumn(loaded,center.south(3)));
        assertTrue(CampStructures.accessColumn(loaded,center.south(5).east()));
        assertFalse(CampStructures.accessColumn(loaded,center.north(3)));
    }
}
