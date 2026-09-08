package com.devfarinsky.siegeoverhaul.raid;
import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class RaidMarchDisciplineTest extends MinecraftTestSupport {
    @Test void removesAmbientWalkingButPreservesCombatAndClearsOldRestriction() {
        Mob mob=mock(Mob.class);
        mob.goalSelector=new GoalSelector(()->net.minecraft.util.profiling.InactiveProfiler.INSTANCE);
        Goal wander=mock(RandomStrollGoal.class),combat=mock(MeleeAttackGoal.class);
        mob.goalSelector.addGoal(10,wander);mob.goalSelector.addGoal(2,combat);
        RaidMarchDiscipline.install(mob);
        assertEquals(1,mob.goalSelector.getAvailableGoals().size());
        assertSame(combat,mob.goalSelector.getAvailableGoals().iterator().next().getGoal());
        verify(mob).clearRestriction();
    }
    @Test void releasesDeadDistantAndAlliedTargetsButKeepsNearbyCombat() {
        Mob mob=mock(Mob.class);LivingEntity target=mock(LivingEntity.class);
        when(target.isAlive()).thenReturn(true);when(mob.distanceToSqr(target)).thenReturn(16D);
        when(target.distanceToSqr(Vec3.ZERO)).thenReturn(100D);
        assertTrue(RaidMarchDiscipline.retainTarget(mob,target,Vec3.ZERO,32));
        when(mob.distanceToSqr(target)).thenReturn(2000D);
        assertFalse(RaidMarchDiscipline.retainTarget(mob,target,Vec3.ZERO,32));
        when(mob.distanceToSqr(target)).thenReturn(16D);when(mob.isAlliedTo(target)).thenReturn(true);
        assertFalse(RaidMarchDiscipline.retainTarget(mob,target,Vec3.ZERO,32));
        when(mob.isAlliedTo(target)).thenReturn(false);when(target.isAlive()).thenReturn(false);
        assertFalse(RaidMarchDiscipline.retainTarget(mob,target,Vec3.ZERO,32));
    }
}
