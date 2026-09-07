package com.devfarinsky.siegeoverhaul.naval;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.ModConstants;
import com.devfarinsky.siegeoverhaul.RaidSavedData.RaidState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NavalConvoyTest extends MinecraftTestSupport {
    @Test void stationaryVesselTimesOutInTwentySecondsIncludingWhileAfloat() {
        var progress = new NavalConvoy.Progress(40, 1000);
        for (int tick=1020; tick<1400; tick+=20) assertFalse(progress.stalled(40, tick));
        assertTrue(progress.stalled(40,1400));
    }
    @Test void actualProgressResetsTimerButOscillationDoesNot() {
        var progress = new NavalConvoy.Progress(40,0);
        assertFalse(progress.stalled(39,380));
        assertFalse(progress.stalled(40,760));
        assertTrue(progress.stalled(39.5,780));
    }
    @Test void nestedShipSeatsFindOnlyRaidCrew() {
        Entity ship = mock(Entity.class), seat = mock(Entity.class), player = mock(Entity.class);
        Mob raider = raider(), civilian = mock(Mob.class);
        when(civilian.isAlive()).thenReturn(true);
        when(civilian.getPersistentData()).thenReturn(new CompoundTag());
        when(ship.getPassengers()).thenReturn(List.of(seat,player,civilian));
        when(seat.getPassengers()).thenReturn(List.of(raider));
        assertEquals(List.of(raider), NavalConvoy.raidCrew(ship,"team:test"));
        verify(player,never()).stopRiding();
        verify(civilian,never()).stopRiding();
    }
    @Test void vesselTagsRecoverTrackingAfterRestart() {
        ServerLevel level = mock(ServerLevel.class);
        Mob mob=raider(); Entity ship=mock(Entity.class);
        UUID id=UUID.randomUUID(); CompoundTag data=new CompoundTag();
        when(ship.getUUID()).thenReturn(id); when(ship.getPersistentData()).thenReturn(data);
        when(mob.isPassenger()).thenReturn(true); when(mob.getRootVehicle()).thenReturn(ship);
        RaidState raid=new RaidState("team:test","home",0);raid.raiders.add(mob.getUUID());
        when(level.getEntity(mob.getUUID())).thenReturn(mob);
        NavalConvoy.enlist(raid.teamKey,ship,new BlockPos(20,64,20));
        NavalConvoy.forget(raid.teamKey);
        NavalConvoy.recover(level,raid);
        assertTrue(NavalConvoy.isRaiderBoat(raid.teamKey,ship));
        assertTrue(data.getBoolean(ModConstants.Tags.NAVAL_DISPOSABLE));
        NavalConvoy.forget(raid.teamKey);
    }
    @Test void legacyBoatRecoversWithoutClaimingOwnership() {
        ServerLevel level=mock(ServerLevel.class); Mob mob=raider(); Boat boat=mock(Boat.class);
        CompoundTag data=new CompoundTag(); when(boat.getPersistentData()).thenReturn(data);
        when(boat.getUUID()).thenReturn(UUID.randomUUID());when(mob.isPassenger()).thenReturn(true);
        when(mob.getRootVehicle()).thenReturn(boat);when(level.getEntity(mob.getUUID())).thenReturn(mob);
        RaidState raid=new RaidState("team:test","home",0);raid.raiders.add(mob.getUUID());raid.navalBeachPos=BlockPos.ZERO;
        NavalConvoy.recover(level,raid);
        assertTrue(NavalConvoy.isRaiderBoat(raid.teamKey,boat));
        assertFalse(data.getBoolean(ModConstants.Tags.NAVAL_DISPOSABLE));
        NavalConvoy.forget(raid.teamKey);
    }
    @Test void safeLandingRejectsWaterHazardsAndCollisions() {
        ServerLevel level=land(); Mob mob=raider(); BlockPos feet=new BlockPos(2,64,2);
        assertTrue(NavalConvoy.safeLanding(level,mob,feet,List.of()));
        doReturn(Blocks.WATER.defaultBlockState()).when(level).getBlockState(feet.below());
        assertFalse(NavalConvoy.safeLanding(level,mob,feet,List.of()));
        doReturn(Blocks.MAGMA_BLOCK.defaultBlockState()).when(level).getBlockState(feet.below());
        assertFalse(NavalConvoy.safeLanding(level,mob,feet,List.of()));
        doReturn(Blocks.DIRT.defaultBlockState()).when(level).getBlockState(feet.below());
        when(level.noCollision(eq(mob),any(AABB.class))).thenReturn(false);
        assertFalse(NavalConvoy.safeLanding(level,mob,feet,List.of()));
    }
    @Test void landingHandsOffToGroundNavigationAndSpreadsCrew() {
        ServerLevel level=land(); Entity boat=mock(Entity.class);when(boat.blockPosition()).thenReturn(new BlockPos(0,64,0));
        Mob first=raider(),second=raider();BlockPos objective=new BlockPos(30,64,30);
        assertEquals(2,NavalConvoy.disembark(level,boat,List.of(first,second),new BlockPos(3,64,3),objective));
        verify(first).stopRiding();verify(second).stopRiding();
        verify(first.getNavigation()).moveTo(30.5,64,30.5,1.1);
        var x=org.mockito.ArgumentCaptor.forClass(Double.class);var y=org.mockito.ArgumentCaptor.forClass(Double.class);var z=org.mockito.ArgumentCaptor.forClass(Double.class);
        verify(first).teleportTo(x.capture(),y.capture(),z.capture());
        verify(second,never()).teleportTo(x.getValue(),y.getValue(),z.getValue());
    }
    @Test void noSafeLandKeepsCrewAboardInsteadOfBlindTeleport() {
        ServerLevel level=land();when(level.hasChunkAt(any())).thenReturn(false);
        Entity boat=mock(Entity.class);when(boat.blockPosition()).thenReturn(new BlockPos(0,64,0));Mob mob=raider();
        assertEquals(0,NavalConvoy.disembark(level,boat,List.of(mob),new BlockPos(3,64,3),BlockPos.ZERO));
        verify(mob,never()).stopRiding();verify(mob,never()).teleportTo(anyDouble(),anyDouble(),anyDouble());
    }
    private static Mob raider() {
        Mob mob=mock(Mob.class);CompoundTag tag=new CompoundTag();tag.putString(ModConstants.Tags.RAID_TEAM,"team:test");
        when(mob.getPersistentData()).thenReturn(tag);when(mob.isAlive()).thenReturn(true);
        when(mob.getUUID()).thenReturn(UUID.randomUUID());when(mob.getY()).thenReturn(64.0);
        when(mob.getBoundingBox()).thenReturn(new AABB(-0.3,64,-0.3,0.3,65.8,0.3));
        when(mob.getNavigation()).thenReturn(mock(PathNavigation.class));return mob;
    }
    private static ServerLevel land() {
        ServerLevel level=mock(ServerLevel.class);when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getWorldBorder()).thenReturn(new WorldBorder());when(level.getMinBuildHeight()).thenReturn(-64);
        when(level.getMaxBuildHeight()).thenReturn(320);
        when(level.getHeight(any(Heightmap.Types.class),anyInt(),anyInt())).thenReturn(64);
        when(level.getBlockState(any())).thenAnswer(c -> ((BlockPos)c.getArgument(0)).getY()<64
                ? Blocks.DIRT.defaultBlockState():Blocks.AIR.defaultBlockState());
        when(level.noCollision(any(Entity.class),any(AABB.class))).thenReturn(true);return level;
    }
}
