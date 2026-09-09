package com.devfarinsky.siegeoverhaul.formations;
import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class MarchSquadsTest extends MinecraftTestSupport {
    @Test void squadAndSlotsSurviveChunkCrossingAndReorderedInput() {
        Mob a=unit(1,15),b=unit(2,16),c=unit(3,18);
        var first=MarchSquads.group(List.of(c,a,b));assertEquals(1,first.size());
        String key=first.keySet().iterator().next();
        when(a.position()).thenReturn(new Vec3(17,64,0));when(b.position()).thenReturn(new Vec3(19,64,0));
        var next=MarchSquads.group(List.of(b,c,a));assertEquals(List.of(a,b,c),next.get(key));
        when(c.position()).thenReturn(new Vec3(70,64,0));
        assertEquals(2,MarchSquads.group(List.of(a,b,c)).size());
    }
    @Test void largeArmySplitsIntoSmallLocalSquads() {
        List<Mob> units=new ArrayList<>();for(int i=1;i<=14;i++)units.add(unit(i,i/2.0));
        var squads=MarchSquads.group(units);assertEquals(3,squads.size());
        assertEquals(14,squads.values().stream().mapToInt(List::size).sum());
        assertTrue(squads.values().stream().allMatch(g->g.size()<=6));
    }
    @Test void isolatedSoldiersRejoinAfterApproachingAndKeepMembershipOnTheNextTick() {
        Mob a=unit(1,0),b=unit(2,40);
        assertEquals(2,MarchSquads.group(List.of(a,b)).size());
        when(b.position()).thenReturn(new Vec3(8,64,0));
        var regrouped=MarchSquads.group(List.of(b,a));
        assertEquals(1,regrouped.size());
        assertEquals(List.of(a,b),regrouped.values().iterator().next());
        assertEquals(regrouped,MarchSquads.group(List.of(a,b)));
    }
    @Test void casualtySurvivorJoinsIntactSquadWithoutMovingItsMembers() {
        Mob a=unit(1,0),b=unit(2,1),c=unit(3,40),d=unit(4,41);
        var original=MarchSquads.group(List.of(a,b,c,d));
        String intact=c.getPersistentData().getString("SiegeMarchSquad");
        assertEquals(2,original.size());
        when(a.position()).thenReturn(new Vec3(42,64,0));
        // b is dead or unloaded and is absent from the eligible marching list.
        var regrouped=MarchSquads.group(List.of(d,a,c));
        assertEquals(1,regrouped.size());assertEquals(List.of(a,c,d),regrouped.get(intact));
        assertEquals(intact,a.getPersistentData().getString("SiegeMarchSquad"));
        assertEquals(regrouped,MarchSquads.group(List.of(c,d,a)));
    }
    @Test void regroupingKeepsDistantSurvivorsSeparateAndNeverOverfillsAnIntactSquad() {
        List<Mob> army=new ArrayList<>();for(int i=1;i<=6;i++)army.add(unit(i,i));
        MarchSquads.group(army);
        Mob nearby=unit(7,8),distant=unit(8,100);
        MarchSquads.group(List.of(nearby,distant));army.add(nearby);army.add(distant);
        var regrouped=MarchSquads.group(army);
        assertEquals(3,regrouped.size());
        assertTrue(regrouped.values().stream().allMatch(g->g.size()<=6));
        assertEquals(8,regrouped.values().stream().mapToInt(List::size).sum());
    }
    private static Mob unit(int id,double x) {
        Mob mob=mock(Mob.class);when(mob.getUUID()).thenReturn(new UUID(0,id));doReturn(EntityType.ZOMBIE).when(mob).getType();
        when(mob.position()).thenReturn(new Vec3(x,64,0));when(mob.getPersistentData()).thenReturn(new CompoundTag());return mob;
    }
}
