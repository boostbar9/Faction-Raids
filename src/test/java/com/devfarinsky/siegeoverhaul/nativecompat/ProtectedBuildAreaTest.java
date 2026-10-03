package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.talhanation.workers.network.MessageUpdateBuildArea;
import com.talhanation.workers.network.MessageUpdateOwner;
import com.talhanation.workers.network.MessageUpdateWorkArea;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProtectedBuildAreaTest extends MinecraftTestSupport {
    private final ServerLevel level = mock(ServerLevel.class);
    private final UUID owner = UUID.randomUUID(), builder = UUID.randomUUID();
    private final BlockPos origin = new BlockPos(-15, 61, -16), marker = new BlockPos(-5, 67, -3);

    private ProtectedBuildArea area() {
        when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        var area = new ProtectedBuildArea(EntityType.ARMOR_STAND, level);
        area.initialize(origin, marker, owner, "Owner", builder, 5, 7, 8,
                AcceptedConstructionPlanTest.blueprint(5, 2, 3, 4, Blocks.COBBLESTONE.defaultBlockState()));
        area.onAddedToWorld();
        return area;
    }

    @Test void physicalShovelAndPickBoxHaveIndependentNativeGeometryWithoutNegativeOffsets() throws Exception {
        var area = area();
        assertEquals(Vec3.atBottomCenterOf(marker), area.position());
        assertEquals(origin, area.getOriginPos());
        assertEquals(new AABB(origin, origin.offset(-4, 8, 6)), area.getArea());
        var accepted = AcceptedConstructionPlan.capture(area);
        assertEquals(java.util.Set.of(origin.offset(-2, 3, 4)), accepted.cells.keySet());
        assertTrue(area.getBoundingBox().contains(area.position().add(0, .5, 0)));
        assertFalse(area.getBoundingBox().contains(Vec3.atCenterOf(origin)));
    }

    @Test void actualNativeCreativePacketCannotChangeOrPlaceAnyAcceptedBlocksOrEntities() {
        var area = area(); CompoundTag before = area.getStructureNBT();
        CompoundTag malicious = AcceptedConstructionPlanTest.blueprint(5, 0, 0, 0, Blocks.TNT.defaultBlockState());
        ListTag entities = new ListTag(); entities.add(new CompoundTag()); malicious.put("entities", entities);
        new MessageUpdateBuildArea(area.getUUID(), 30, 30, 30, malicious, true, true, true).update(area);
        area.setStartBuild(true);
        assertEquals(before, area.getStructureNBT()); assertEquals(5, area.getWidthSize());
        assertEquals(7, area.getDepthSize()); assertEquals(8, area.getHeightSize());
        assertFalse(area.getFreeArea());
        verify(level, never()).setBlock(any(), any(), anyInt());
        verify(level, never()).setBlockAndUpdate(any(), any());
        verify(level, never()).addFreshEntity(any());
    }

    @Test void allActualEntityMoveToOverloadsAreSealedBeforeTheirSetPosRawMutation() {
        var area = area(); Vec3 before = area.position();
        area.moveTo(100, 100, 100);
        area.moveTo(200, 200, 200, 90, 30);
        area.moveTo(new Vec3(300, 300, 300));
        area.moveTo(new BlockPos(400, 400, 400), 180, 20);
        area.setPos(500, 500, 500);
        assertEquals(before, area.position());
        assertEquals(origin, area.getOriginPos());
    }

    @Test void nativeOwnershipRotationAndRawDeleteControlsCannotModifyAPublishedMarker() {
        var area = area();
        var change = new MessageUpdateOwner(); change.playerUUID = UUID.randomUUID(); change.playerName = "Someone else";
        change.updateWorkArea(area);
        area.setFacing(Direction.WEST); area.setTeamAccess(true); area.setTeamStringID("foreign");
        assertEquals(owner, area.getPlayerUUID()); assertEquals("Owner", area.getPlayerName());
        assertEquals(Direction.SOUTH, area.getFacing()); assertFalse(area.getTeamAccess()); assertEquals("", area.getTeamStringID());
        var delete = new MessageUpdateWorkArea(); delete.destroy = true;
        delete.updateWorkArea(area, mock(ServerPlayer.class));
        assertFalse(area.isRemoved());
        assertTrue(area.abortBeforePayment()); assertTrue(area.isRemoved());
    }

    @Test void nativeStructureAliasesAndRestartPacketsCannotResetRemainingQueues() {
        var area = area(); CompoundTag accepted = area.getStructureNBT();
        area.initializeBlueprint(accepted); assertEquals(1, area.stackToPlace.size());
        area.stackToPlace.clear();
        area.setStartBuild(false); assertTrue(area.stackToPlace.isEmpty());
        accepted.putInt("width", 900);
        area.getStructureNBT().putInt("width", 901);
        assertEquals(5, area.getStructureNBT().getInt("width"));
        assertThrows(IllegalStateException.class, () -> area.initializeBlueprint(accepted));
    }

    @Test void persistedOriginIsRestoredAndMalformedNewTypeSavesRemainClosed() throws Exception {
        var area = area(); CompoundTag saved = new CompoundTag(); area.addAdditionalSaveData(saved);
        var loaded = new ProtectedBuildArea(EntityType.ARMOR_STAND, level);
        loaded.readAdditionalSaveData(saved); loaded.onAddedToWorld();
        assertEquals(origin, loaded.getOriginPos());
        assertEquals(area.getArea(), loaded.getArea());
        assertEquals(AcceptedConstructionPlan.capture(area).cells, AcceptedConstructionPlan.capture(loaded).cells);
        saved.remove("SiegeNativeSealed");
        var invalid = new ProtectedBuildArea(EntityType.ARMOR_STAND, level);
        invalid.readAdditionalSaveData(saved); invalid.onAddedToWorld();
        assertThrows(IllegalStateException.class, invalid::rebuildAcceptedQueues);
        invalid.setStartBuild(true); invalid.moveTo(100, 100, 100);
        assertEquals(Vec3.ZERO, invalid.position());
        verify(level, never()).setBlock(any(), any(), anyInt());
    }

    @Test void unpaidAndWrongBuildersCannotDiscoverTheArea() {
        var area = area(); var worker = mock(com.talhanation.workers.entities.AbstractWorkerEntity.class);
        when(worker.getUUID()).thenReturn(builder);
        assertFalse(area.canWorkHere(worker));
        area.getPersistentData().putBoolean("SiegeConstructionCommissionPaid", true);
        when(worker.getUUID()).thenReturn(UUID.randomUUID());
        assertFalse(area.canWorkHere(worker));
        assertFalse(area.abortBeforePayment());
        area.remove(Entity.RemovalReason.DISCARDED); assertFalse(area.isRemoved());
    }
}
