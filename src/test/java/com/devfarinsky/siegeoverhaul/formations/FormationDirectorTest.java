package com.devfarinsky.siegeoverhaul.formations;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FormationDirectorTest extends MinecraftTestSupport {
    @Test void loneCaptainAndPatrolLeaderReceiveForwardOrdersInsteadOfBeingReleasedAsSupport() {
        for (String role : java.util.List.of("captain", "patrol_leader")) {
            var level = mock(net.minecraft.server.level.ServerLevel.class);
            var raid = new com.devfarinsky.siegeoverhaul.RaidSavedData.RaidState("team:" + role, "siege_core", 0);
            Mob leader = mock(Mob.class);
            var tag = new net.minecraft.nbt.CompoundTag();
            tag.putString(com.devfarinsky.siegeoverhaul.ModConstants.Tags.RAID_TEAM, raid.teamKey);
            tag.putString(com.devfarinsky.siegeoverhaul.ModConstants.Tags.RAID_ROLE, role);
            when(leader.getPersistentData()).thenReturn(tag);
            var id = java.util.UUID.randomUUID();
            when(leader.getUUID()).thenReturn(id);
            when(leader.isAlive()).thenReturn(true);
            when(leader.distanceToSqr(any(Vec3.class))).thenReturn(10000D);
            when(leader.position()).thenReturn(new Vec3(.5, 64.5, .5));
            when(leader.getX()).thenReturn(.5);
            when(leader.getY()).thenReturn(64.5);
            when(leader.getZ()).thenReturn(.5);
            raid.raiders.add(id);
            when(level.getEntity(id)).thenReturn(leader);
            var forward = new Vec3(1, 0, 0);
            var waypoint = new Vec3(14.5, 64.5, .5);
            try (var bridge = mockStatic(RecruitsFormationBridge.class)) {
                bridge.when(RecruitsFormationBridge::available).thenReturn(true);
                bridge.when(() -> RecruitsFormationBridge.applyTactical(level, Formation.COLUMN,
                        forward, waypoint, java.util.List.of(leader))).thenReturn(true);
                assertTrue(FormationDirector.tick(level, raid, new BlockPos(100, 64, 0), Formation.LINE));
                bridge.verify(() -> RecruitsFormationBridge.release(leader), never());
            } finally {
                FormationDirector.forget(raid.teamKey);
            }
        }
    }
    @Test
    void auxiliaryWithoutRecruitApiCannotKeepPhantomMarchOrder() {
        Mob auxiliary = mock(Mob.class);
        var data = new net.minecraft.nbt.CompoundTag();
        data.putBoolean(com.devfarinsky.siegeoverhaul.ModConstants.Tags.FORMATION_MARCH, true);
        when(auxiliary.getPersistentData()).thenReturn(data);
        RecruitsFormationBridge.release(auxiliary);
        assertFalse(data.contains(com.devfarinsky.siegeoverhaul.ModConstants.Tags.FORMATION_MARCH));
    }

    @Test
    void formationsMarchAcrossUnclaimedApproachButOnlyForTheirAssignedRaid() {
        var level = mock(net.minecraft.server.level.ServerLevel.class);
        Mob soldier = mock(Mob.class);
        when(soldier.getPersistentData()).thenReturn(new net.minecraft.nbt.CompoundTag());
        when(soldier.blockPosition()).thenReturn(new BlockPos(100,64,100));
        when(soldier.distanceToSqr(any(Vec3.class))).thenReturn(1600.0);
        assertFalse(FormationDirector.shouldMarch(level, "team:defenders", soldier, BlockPos.ZERO));
        soldier.getPersistentData().putString(com.devfarinsky.siegeoverhaul.ModConstants.Tags.RAID_TEAM, "team:defenders");
        assertTrue(FormationDirector.shouldMarch(level, "team:defenders", soldier, BlockPos.ZERO));
        soldier.horizontalCollision = true;
        assertFalse(FormationDirector.shouldMarch(level, "team:defenders", soldier, BlockPos.ZERO));
        soldier.horizontalCollision = false;
        when(soldier.onClimbable()).thenReturn(true);
        assertFalse(FormationDirector.shouldMarch(level, "team:defenders", soldier, BlockPos.ZERO));
    }

    @Test
    void fightingSoldiersAndPassengersDoNotReceiveMarchingOrders() {
        Mob soldier = mock(Mob.class), defender = mock(Mob.class);
        when(soldier.getPersistentData()).thenReturn(new net.minecraft.nbt.CompoundTag());
        when(soldier.distanceToSqr(any(Vec3.class))).thenReturn(1600.0);
        assertTrue(FormationDirector.shouldMarch(soldier, BlockPos.ZERO));
        when(soldier.isPassenger()).thenReturn(true);
        assertFalse(FormationDirector.shouldMarch(soldier, BlockPos.ZERO));
        when(soldier.isPassenger()).thenReturn(false);
        when(soldier.getTarget()).thenReturn(defender);
        when(defender.isAlive()).thenReturn(true);
        assertFalse(FormationDirector.shouldMarch(soldier, BlockPos.ZERO));
        when(defender.isAlive()).thenReturn(false);
        assertTrue(FormationDirector.shouldMarch(soldier, BlockPos.ZERO));
        when(soldier.distanceToSqr(any(Vec3.class))).thenReturn(100.0);
        assertFalse(FormationDirector.shouldMarch(soldier, BlockPos.ZERO));
    }
    @Test void commanderWallWindupRetainsMovementOwnership() {
        Mob commander = mock(Mob.class);
        var tag = new net.minecraft.nbt.CompoundTag();
        when(commander.getPersistentData()).thenReturn(tag);
        when(commander.distanceToSqr(any(Vec3.class))).thenReturn(1600D);
        assertTrue(FormationDirector.shouldMarch(commander, BlockPos.ZERO));
        tag.putBoolean(com.devfarinsky.siegeoverhaul.siege.CommanderWallStrikeGoal.CHARGING, true);
        assertFalse(FormationDirector.shouldMarch(commander, BlockPos.ZERO));
        tag.remove(com.devfarinsky.siegeoverhaul.siege.CommanderWallStrikeGoal.CHARGING);
        assertTrue(FormationDirector.shouldMarch(commander, BlockPos.ZERO));
    }
    @Test void bridgeWorkerRetainsMovementOwnershipUntilAssignmentEnds() {
        Mob worker = mock(Mob.class);
        when(worker.getPersistentData()).thenReturn(new net.minecraft.nbt.CompoundTag());
        when(worker.distanceToSqr(any(Vec3.class))).thenReturn(1600D);
        try (var builders = mockStatic(com.devfarinsky.siegeoverhaul.naval.BridgeBuilder.class)) {
            assertTrue(FormationDirector.shouldMarch(worker, BlockPos.ZERO));
            builders.when(() -> com.devfarinsky.siegeoverhaul.naval.BridgeBuilder.assigned(worker)).thenReturn(true);
            assertFalse(FormationDirector.shouldMarch(worker, BlockPos.ZERO));
            builders.when(() -> com.devfarinsky.siegeoverhaul.naval.BridgeBuilder.assigned(worker)).thenReturn(false);
            assertTrue(FormationDirector.shouldMarch(worker, BlockPos.ZERO));
        }
    }
    @Test void dismountedEngineerKeepsEquipmentAssignmentInsteadOfInfantryFormation() {
        var level=mock(net.minecraft.server.level.ServerLevel.class);Mob engineer=mock(Mob.class);
        var tag=new net.minecraft.nbt.CompoundTag();tag.putString(com.devfarinsky.siegeoverhaul.ModConstants.Tags.RAID_TEAM,"team:test");
        tag.putString(com.devfarinsky.siegeoverhaul.siege.SiegeDeployment.TEAM_TAG,"team:test");
        when(engineer.getPersistentData()).thenReturn(tag);when(engineer.distanceToSqr(any(Vec3.class))).thenReturn(10000.0);
        assertFalse(FormationDirector.shouldMarch(level,"team:test",engineer,BlockPos.ZERO));
    }
}
