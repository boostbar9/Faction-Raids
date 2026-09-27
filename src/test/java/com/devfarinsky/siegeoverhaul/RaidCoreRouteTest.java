package com.devfarinsky.siegeoverhaul;
import com.devfarinsky.siegeoverhaul.raid.CoreApproach;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
class RaidCoreRouteTest extends MinecraftTestSupport {
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
