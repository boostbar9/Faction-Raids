package com.devfarinsky.siegeoverhaul.core;
import com.devfarinsky.siegeoverhaul.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class CoreControlTest extends MinecraftTestSupport {
    @Test void aMajorityMustHoldForTheFullTimerRegardlessOfArmySize() {
        int small=0,large=0;
        for(int i=0;i<119;i++) { small=CoreControl.advance(small,2400,2,1); large=CoreControl.advance(large,2400,200,1); }
        assertEquals(2380,small); assertEquals(small,large);
        assertEquals(2400,CoreControl.advance(small,2400,2,1));
    }
    @Test void emptyOrTiedRingCannotCaptureOrRecapture() {
        assertEquals(1180,CoreControl.advance(1200,2400,0,0));
        assertEquals(1200,CoreControl.advance(1200,2400,3,3));
        assertEquals(1180,CoreControl.advance(1200,2400,1,2));
        assertEquals(0,CoreControl.advance(0,2400,0,1));
    }
    @Test void abandonedProgressRunsDownWithoutGoingNegative() {
        int progress=100;
        for(int i=0;i<10;i++)progress=CoreControl.advance(progress,2400,0,0);
        assertEquals(0,progress);
        assertEquals(20,CoreControl.advance(progress,2400,1,0));
    }
    @Test void recaptureProgressIsBoundToTheObservedOwnerAcrossReloads() {
        var tag=new CompoundTag();tag.putInt("RecaptureTicks",800);
        CoreControl.bindOwner(tag,"RecaptureTicks","red");
        assertEquals(800,tag.getInt("RecaptureTicks"),"old saves retain their progress");
        tag=tag.copy();
        CoreControl.bindOwner(tag,"RecaptureTicks","red");
        assertEquals(800,tag.getInt("RecaptureTicks"));
        CoreControl.bindOwner(tag,"RecaptureTicks","green");
        assertEquals(0,tag.getInt("RecaptureTicks"));
    }
    @Test void soldiersFromOtherSiegesCannotBeLuredIntoThisContest() {
        RaidConfig.CORE_CAPTURE_REQUIRE_SIGHT.set(false);
        var level=mock(net.minecraft.server.level.ServerLevel.class);
        var own=mobAtCore();var other=mobAtCore();var otherGuard=mobAtCore();var neutral=mobAtCore();
        own.getPersistentData().putString(ModConstants.Tags.RAID_TEAM,"team:blue");
        other.getPersistentData().putString(ModConstants.Tags.RAID_TEAM,"team:green");
        otherGuard.getPersistentData().putString(com.devfarinsky.siegeoverhaul.camp.CampGuards.TEAM_TAG,"team:green");
        when(level.getEntitiesOfClass(eq(net.minecraft.world.entity.Mob.class),any(net.minecraft.world.phys.AABB.class),any()))
                .thenReturn(List.of(own,other,otherGuard,neutral));
        try(var recruits=mockStatic(RecruitsBridge.class)) {
            recruits.when(()->RecruitsBridge.isRecruitSoldier(any())).thenReturn(true);
            // Even a native faction match must not override a different raid's tag.
            recruits.when(()->RecruitsBridge.belongsTo(any(),eq("team:red"),any())).thenReturn(true);
            assertArrayEquals(new int[]{2,0},CoreOccupation.counts(level,BlockPos.ZERO,"team:blue",Set.of(),"red"));
        }
    }
    @Test void roofAndNearbyOutsideTroopsCannotCount() {
        RaidConfig.CORE_CAPTURE_RADIUS.set(10);
        RaidConfig.CORE_CAPTURE_VERTICAL.set(2);
        assertTrue(CoreOccupation.inRing(new Vec3(10.5,.5,.5),BlockPos.ZERO));
        assertFalse(CoreOccupation.inRing(new Vec3(10.6,.5,.5),BlockPos.ZERO));
        assertFalse(CoreOccupation.inRing(new Vec3(.5,4,.5),BlockPos.ZERO));
        assertFalse(CoreOccupation.inRing(new Vec3(.5,3.5,.5),BlockPos.ZERO));
        assertTrue(CoreOccupation.inRing(new Vec3(.5,2.5,.5),BlockPos.ZERO));
        assertTrue(CoreOccupation.inRing(new Vec3(.5,-1.5,.5),BlockPos.ZERO));
    }
    @Test void captureRingIsACylinderWithAConfigurableVerticalBand() {
        assertTrue(CaptureRing.inside(6,2,0,6,2));
        assertFalse(CaptureRing.inside(6,2.01,0,6,2));
        assertFalse(CaptureRing.inside(6.5,0,0,6,2));
        assertFalse(CaptureRing.inside(0,0,0,0,2));
    }
    @Test void occupationAndRecaptureSurviveRaidRemovalAndSaveReload() {
        var data=new RaidSavedData(); var core=new CompoundTag();
        core.putLong("Position",BlockPos.ZERO.asLong()); core.putBoolean("Occupied",true);
        var id=java.util.UUID.randomUUID(); core.putUUID("OccupiedClaim",id); core.putInt("RecaptureTicks",860);
        data.siegeCores.put("team:blue",core);
        var state=new RaidSavedData.RaidState("team:blue","siege_core",0); state.coreCaptured=true;
        data.raids.put(state.teamKey,state);
        var loaded=RaidSavedData.load(data.save(new CompoundTag()));
        assertTrue(loaded.raids.get(state.teamKey).coreCaptured);
        loaded.raids.clear();
        loaded=RaidSavedData.load(loaded.save(new CompoundTag()));
        assertTrue(CoreOccupation.occupied(loaded,"team:blue"));
        assertEquals(id,loaded.siegeCores.get("team:blue").getUUID("OccupiedClaim"));
        assertEquals(860,loaded.siegeCores.get("team:blue").getInt("RecaptureTicks"));
    }
    @Test void currentForeignClaimHolderPlayersAndSoldiersContestRecovery() {
        RaidConfig.CORE_CAPTURE_REQUIRE_SIGHT.set(false);
        RaidConfig.CORE_CAPTURE_RADIUS.set(10);
        RaidConfig.CORE_CAPTURE_VERTICAL.set(2);
        var level=mock(net.minecraft.server.level.ServerLevel.class);
        var defender=playerAtCore(); var occupier=playerAtCore(); var unrelated=playerAtCore();
        when(level.players()).thenReturn(List.of(defender,occupier,unrelated));
        var defenderSoldier=mobAtCore(); var occupierSoldier=mobAtCore(); var unrelatedSoldier=mobAtCore();
        when(level.getEntitiesOfClass(eq(net.minecraft.world.entity.Mob.class),any(net.minecraft.world.phys.AABB.class),any()))
                .thenReturn(List.of(defenderSoldier,occupierSoldier,unrelatedSoldier));
        Set<java.util.UUID> members=Set.of();
        try(var keys=mockStatic(SiegeCore.class);var recruits=mockStatic(com.devfarinsky.siegeoverhaul.RecruitsBridge.class)) {
            keys.when(()->SiegeCore.key(defender)).thenReturn("team:blue");
            keys.when(()->SiegeCore.key(occupier)).thenReturn("team:red");
            keys.when(()->SiegeCore.key(unrelated)).thenReturn("team:green");
            recruits.when(()->com.devfarinsky.siegeoverhaul.RecruitsBridge.isRecruitSoldier(any())).thenReturn(false);
            recruits.when(()->com.devfarinsky.siegeoverhaul.RecruitsBridge.belongsTo(defenderSoldier,"team:blue",members)).thenReturn(true);
            recruits.when(()->com.devfarinsky.siegeoverhaul.RecruitsBridge.belongsTo(occupierSoldier,"team:red",Set.of())).thenReturn(true);
            int[] counts=CoreOccupation.counts(level,BlockPos.ZERO,"team:blue",members,"red");
            assertArrayEquals(new int[]{2,2},counts,"unrelated factions must stay neutral to recovery");
        }
    }
    private static net.minecraft.server.level.ServerPlayer playerAtCore() {
        var player=mock(net.minecraft.server.level.ServerPlayer.class);
        when(player.isAlive()).thenReturn(true);
        when(player.position()).thenReturn(new Vec3(.5,.5,.5));
        return player;
    }
    private static net.minecraft.world.entity.Mob mobAtCore() {
        var mob=mock(net.minecraft.world.entity.Mob.class);
        when(mob.isAlive()).thenReturn(true);
        when(mob.position()).thenReturn(new Vec3(.5,.5,.5));
        when(mob.getPersistentData()).thenReturn(new CompoundTag());
        return mob;
    }
}
