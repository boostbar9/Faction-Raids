package com.devfarinsky.siegeoverhaul.compat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EngineerSpawnCompatibilityTest extends MinecraftTestSupport {
    @Test void nativeCastSucceedsAndOriginalNavigatorIsRestored() throws Exception {
        var mob = mock(Mob.class);
        var nativeNavigation = mock(PathNavigation.class);
        var compatible = mock(GroundPathNavigation.class);
        var field = EngineerSpawnCompatibility.navigationField();
        field.set(mob, nativeNavigation);
        when(mob.getNavigation()).thenAnswer(call -> field.get(mob));
        AtomicInteger spawnBonuses = new AtomicInteger();
        EngineerSpawnCompatibility.withNavigation(mob,compatible,() -> {
            spawnBonuses.incrementAndGet();
            ((GroundPathNavigation) mob.getNavigation()).setCanOpenDoors(true);
        });
        assertEquals(1,spawnBonuses.get());
        assertSame(nativeNavigation,mob.getNavigation());
        verify(compatible).setCanOpenDoors(true);
    }
    @Test void unrelatedFailureRestoresNavigationAndIsNotRetried() throws Exception {
        var mob = mock(Mob.class);
        var nativeNavigation = mock(PathNavigation.class);
        var field = EngineerSpawnCompatibility.navigationField();
        field.set(mob,nativeNavigation);
        AtomicInteger calls = new AtomicInteger();
        var failure = new IllegalStateException("Unrelated broken spawn hook");
        assertSame(failure,assertThrows(IllegalStateException.class,() ->
                EngineerSpawnCompatibility.withNavigation(mob,mock(GroundPathNavigation.class),() -> {
                    calls.incrementAndGet(); throw failure;
                })));
        assertEquals(1,calls.get());
        assertSame(nativeNavigation,field.get(mob));
    }
}
