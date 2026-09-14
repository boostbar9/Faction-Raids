package com.devfarinsky.siegeoverhaul.siege;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.naval.BridgeBuilder;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class RaiderHoleAvoidGoalTest extends MinecraftTestSupport {
    @Test
    void bridgeJobControlsItsOwnSafeEdgeMovement() {
        PathfinderMob mob = mock(PathfinderMob.class);
        PathNavigation navigation = mock(PathNavigation.class);
        when(mob.getNavigation()).thenReturn(navigation);
        try (var builders = mockStatic(BridgeBuilder.class)) {
            builders.when(() -> BridgeBuilder.assigned(mob)).thenReturn(true);
            new RaiderHoleAvoidGoal(mob).tick();
            verify(mob, never()).level();
            verifyNoInteractions(navigation);
        }
    }
}
