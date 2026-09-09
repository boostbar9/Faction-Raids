package com.devfarinsky.siegeoverhaul.raid;
import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class RaidMarchDisciplineTest extends MinecraftTestSupport {
    @Test void removesAmbientWalkingButPreservesCombatAndClearsOldRestriction() throws ReflectiveOperationException {
        Mob mob=mock(Mob.class);
        var field=Mob.class.getDeclaredField("goalSelector");field.setAccessible(true);
        field.set(mob,new GoalSelector(()->net.minecraft.util.profiling.InactiveProfiler.INSTANCE));
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
    @Test void assaultSpecialistsSwitchFromApproachToCombatAndBack() {
        for (String role : new String[]{"commander", "breacher"}) {
            assertTrue(RaidMarchDiscipline.pushPastDefenders(role, true, false));
            assertFalse(RaidMarchDiscipline.pushPastDefenders(role, true, true));
            assertTrue(RaidMarchDiscipline.pushPastDefenders(role, true, false));
            assertFalse(RaidMarchDiscipline.pushPastDefenders(role, false, false));
        }
        assertFalse(RaidMarchDiscipline.pushPastDefenders("captain", true, false));
        assertFalse(RaidMarchDiscipline.pushPastDefenders("marksman", true, false));
    }
    @Test void objectiveCombatStillRejectsInvalidTargets() {
        Mob commander=mock(Mob.class); LivingEntity defender=mock(LivingEntity.class);
        when(defender.isAlive()).thenReturn(true);
        when(commander.distanceToSqr(defender)).thenReturn(16D);
        when(defender.distanceToSqr(Vec3.ZERO)).thenReturn(25D);
        assertFalse(RaidMarchDiscipline.pushPastDefenders("commander",true,true));
        assertTrue(RaidMarchDiscipline.retainTarget(commander,defender,Vec3.ZERO,32));
        when(commander.isAlliedTo(defender)).thenReturn(true);
        assertFalse(RaidMarchDiscipline.retainTarget(commander,defender,Vec3.ZERO,32));
        when(commander.isAlliedTo(defender)).thenReturn(false);
        when(defender.isAlive()).thenReturn(false);
        assertFalse(RaidMarchDiscipline.retainTarget(commander,defender,Vec3.ZERO,32));
    }
    public enum Patrol { IDLE, ATTACKING, RETREATING }
    public static class Leader {
        public byte action = 1;
        public Patrol patrol = Patrol.RETREATING;
        public int follow = 2;
        public void setEnemyAction(byte value) { action = value; }
        public void setPatrolState(Patrol value) { patrol = value; }
        public void setFollowState(int value) { follow = value; }
    }
    @Test void nativeLeaderCannotRestartHoldOrRetreatControllerAfterSiegeLoad() {
        Leader leader = new Leader();
        assertTrue(RaidMarchDiscipline.releaseNativePatrol(leader));
        assertEquals(2, leader.action); assertEquals(Patrol.IDLE, leader.patrol); assertEquals(0, leader.follow);
        assertTrue(RaidMarchDiscipline.releaseNativePatrol(leader));
        assertEquals(Patrol.IDLE, leader.patrol);
    }
    @Test void mobsWithoutLeaderApiAreUnaffected() {
        assertFalse(RaidMarchDiscipline.releaseNativePatrol(new Object()));
    }
}
