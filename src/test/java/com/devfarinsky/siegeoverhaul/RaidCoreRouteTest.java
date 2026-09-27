package com.devfarinsky.siegeoverhaul;
import com.devfarinsky.siegeoverhaul.raid.CoreApproach;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
class RaidCoreRouteTest extends MinecraftTestSupport {
    @Test void stalledUnitsNearCoreCannotBeTeleportedOntoDefenderRoof() {
        var level=mock(ServerLevel.class);var mob=mock(Mob.class);
        when(mob.position()).thenReturn(new Vec3(10,64,0));
        RaidEvents.teleportStuckRaiderForward(level,mob,new Vec3(0,64,0));
        verifyNoInteractions(level);
        verify(mob,never()).teleportTo(anyDouble(),anyDouble(),anyDouble());
    }
    @Test void marchRecoveryRejectsRoofJumpsButAllowsSafeLevelGround() {
        var level=mock(ServerLevel.class);var mob=mock(Mob.class);var nav=mock(PathNavigation.class);
        when(mob.position()).thenReturn(new Vec3(0,64,0));when(mob.getY()).thenReturn(64.0);
        when(mob.getNavigation()).thenReturn(nav);
        when(mob.getBoundingBox()).thenReturn(new net.minecraft.world.phys.AABB(-0.3,64,-0.3,0.3,65.8,0.3));
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getHeight(any(),anyInt(),anyInt())).thenReturn(72);
        var objective=new Vec3(100,64,0);
        RaidEvents.teleportStuckRaiderForward(level,mob,objective);
        verify(mob,never()).teleportTo(anyDouble(),anyDouble(),anyDouble());
        when(level.getHeight(any(),anyInt(),anyInt())).thenReturn(64);
        when(level.isLoaded(any())).thenReturn(true);
        when(level.getMinBuildHeight()).thenReturn(-64);when(level.getMaxBuildHeight()).thenReturn(320);
        when(level.getWorldBorder()).thenReturn(new net.minecraft.world.level.border.WorldBorder());
        when(level.noCollision(eq(mob),any(net.minecraft.world.phys.AABB.class))).thenReturn(true);
        doAnswer(i -> ((net.minecraft.core.BlockPos)i.getArgument(0)).getY()<64
                ? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()
                : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()).when(level).getBlockState(any());
        RaidEvents.teleportStuckRaiderForward(level,mob,objective);
        verify(mob).teleportTo(6.5,64,0.5);
    }
    @Test void reachableCoreFloorIsUsedBeforeBreachPhaseCompletes() {
        var level=mock(ServerLevel.class);var mob=mock(Mob.class);var nav=mock(PathNavigation.class);
        when(mob.getNavigation()).thenReturn(nav);
        var raid=new RaidSavedData.RaidState("team:blue","siege_core",0);
        raid.breached=false;var objective=new Vec3(0.5,64.5,0.5);
        try(var approach=mockStatic(CoreApproach.class)) {
            approach.when(()->CoreApproach.moveTo(level,mob,objective,1.2)).thenReturn(true);
            RaidEvents.moveToRaidObjective(level,mob,raid,objective,1.2);
            approach.verify(()->CoreApproach.moveTo(level,mob,objective,1.2));
            verifyNoInteractions(nav);
            raid.defensePointName="legacy";
            RaidEvents.moveToRaidObjective(level,mob,raid,objective,1.2);
            verify(nav).moveTo(0.5,64.5,0.5,1.2);
        }
    }
}
