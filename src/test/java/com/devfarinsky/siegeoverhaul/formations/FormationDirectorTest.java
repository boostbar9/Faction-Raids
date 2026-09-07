package com.devfarinsky.siegeoverhaul.formations;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FormationDirectorTest extends MinecraftTestSupport {
    @Test
    void auxiliaryWithoutRecruitApiCannotKeepPhantomMarchOrder() {
        Mob auxiliary = mock(Mob.class);
        var data = new net.minecraft.nbt.CompoundTag();
        data.putBoolean(com.devfarinsky.siegeoverhaul.ModConstants.Tags.FORMATION_MARCH, true);
        when(auxiliary.getPersistentData()).thenReturn(data);
        RecruitsFormationBridge.release(auxiliary);
        assertFalse(data.contains(com.devfarinsky.siegeoverhaul.ModConstants.Tags.FORMATION_MARCH));
    }

    @Test
    void fightingSoldiersAndPassengersDoNotReceiveMarchingOrders() {
        Mob soldier = mock(Mob.class), defender = mock(Mob.class);
        when(soldier.distanceToSqr(any(Vec3.class))).thenReturn(1600.0);
        assertTrue(FormationDirector.shouldMarch(soldier, BlockPos.ZERO));
        when(soldier.isPassenger()).thenReturn(true);
        assertFalse(FormationDirector.shouldMarch(soldier, BlockPos.ZERO));
        when(soldier.isPassenger()).thenReturn(false);
        when(soldier.getTarget()).thenReturn(defender);
        when(defender.isAlive()).thenReturn(true);
        assertFalse(FormationDirector.shouldMarch(soldier, BlockPos.ZERO));
        when(defender.isAlive()).thenReturn(false);
        assertTrue(FormationDirector.shouldMarch(soldier, BlockPos.ZERO));
        when(soldier.distanceToSqr(any(Vec3.class))).thenReturn(100.0);
        assertFalse(FormationDirector.shouldMarch(soldier, BlockPos.ZERO));
    }
}
