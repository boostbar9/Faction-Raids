package com.devfarinsky.siegeoverhaul.raid;
import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class MarchProgressTest extends MinecraftTestSupport {
    @Test void progressingDetourKeepsPathAndStationaryRouteRetriesAfterTwoSeconds() {
        Mob mob=mock(Mob.class);var nav=mock(PathNavigation.class);when(mob.getNavigation()).thenReturn(nav);
        when(mob.getPersistentData()).thenReturn(new CompoundTag());
        Vec3 goal=new Vec3(100,64,0);position(mob,new Vec3(0,64,0));
        assertTrue(MarchProgress.shouldRepath(mob,goal,0));
        // Progress sideways around an obstacle is still useful movement.
        position(mob,new Vec3(0,64,2));assertFalse(MarchProgress.shouldRepath(mob,goal,20));
        assertFalse(MarchProgress.shouldRepath(mob,goal,40));
        assertTrue(MarchProgress.shouldRepath(mob,goal,60));
        assertFalse(MarchProgress.shouldRepath(mob,goal,80));
        when(nav.isDone()).thenReturn(true);assertTrue(MarchProgress.shouldRepath(mob,goal,81));
    }
    @Test void changedObjectiveAndWorldClockResetInvalidateOldOrder() {
        Mob mob=mock(Mob.class);when(mob.getNavigation()).thenReturn(mock(PathNavigation.class));
        when(mob.getPersistentData()).thenReturn(new CompoundTag());position(mob,new Vec3(0,64,0));
        assertTrue(MarchProgress.shouldRepath(mob,new Vec3(100,64,0),100));
        assertTrue(MarchProgress.shouldRepath(mob,new Vec3(-100,64,0),101));
        assertTrue(MarchProgress.shouldRepath(mob,new Vec3(-100,64,0),1));
    }
    @Test void movingInCirclesEventuallyRetriesButAdvancingDetoursKeepTheirRoute() {
        Mob mob=mock(Mob.class);when(mob.getNavigation()).thenReturn(mock(PathNavigation.class));
        when(mob.getPersistentData()).thenReturn(new CompoundTag());Vec3 goal=new Vec3(100,64,0);
        position(mob,new Vec3(0,64,0));assertTrue(MarchProgress.shouldRepath(mob,goal,0));
        for(int tick=20;tick<200;tick+=20){position(mob,new Vec3(0,64,(tick/20)%2*2));assertFalse(MarchProgress.shouldRepath(mob,goal,tick));}
        position(mob,new Vec3(0,64,0));assertTrue(MarchProgress.shouldRepath(mob,goal,200));
        for(int tick=220;tick<=420;tick+=20){position(mob,new Vec3((tick-200)/10.0,64,2));assertFalse(MarchProgress.shouldRepath(mob,goal,tick));}
    }
    private static void position(Mob mob,Vec3 p) {
        when(mob.position()).thenReturn(p);when(mob.getX()).thenReturn(p.x);when(mob.getY()).thenReturn(p.y);when(mob.getZ()).thenReturn(p.z);
    }
}
