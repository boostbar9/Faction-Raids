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
    private static Mob unit(int id,double x) {
        Mob mob=mock(Mob.class);when(mob.getUUID()).thenReturn(new UUID(0,id));doReturn(EntityType.ZOMBIE).when(mob).getType();
        when(mob.position()).thenReturn(new Vec3(x,64,0));when(mob.getPersistentData()).thenReturn(new CompoundTag());return mob;
    }
}
