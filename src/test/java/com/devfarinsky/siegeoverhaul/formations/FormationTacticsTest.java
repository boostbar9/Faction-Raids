package com.devfarinsky.siegeoverhaul.formations;
import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class FormationTacticsTest extends MinecraftTestSupport {
    @Test void roleAndTerrainChooseAppropriateShapes() {
        assertEquals(Formation.LINE,FormationTactics.choose(FormationTactics.group("recruit_shieldman"),false,false));
        assertEquals(Formation.SKIRMISH,FormationTactics.choose(FormationTactics.group("bowman"),false,false));
        assertEquals(Formation.WEDGE,FormationTactics.choose(FormationTactics.group("assassin"),false,false));
        assertEquals(Formation.SQUARE,FormationTactics.choose(FormationTactics.group("captain"),false,false));
        assertEquals(Formation.SKIRMISH,FormationTactics.choose("mobile",false,true));
        for(String group:List.of("front","ranged","mobile","support"))assertEquals(Formation.COLUMN,FormationTactics.choose(group,true,false));
    }
    @Test void slotsAreDistinctAndColumnsDoNotExpandSideways() {
        for(Formation shape:Formation.values()) {
            Set<net.minecraft.world.phys.Vec3> seen=new HashSet<>();
            for(int i=0;i<24;i++) {
                var offset=FormationTactics.offset(shape,i,24); assertTrue(seen.add(offset));
                if(shape==Formation.COLUMN)assertEquals(0,offset.x);
            }
        }
    }
}
