package com.devfarinsky.siegeoverhaul.raid;

import com.devfarinsky.siegeoverhaul.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MarchPaceTest extends MinecraftTestSupport {
    @Test void nativeFormationAndDirectMarchGainOneTransientBonusWithoutChangingBase() {
        var mob=mock(Mob.class); var raid=new RaidSavedData.RaidState("team:test","siege_core",0);
        var tags=new CompoundTag(); tags.putString(ModConstants.Tags.RAID_TEAM,raid.teamKey);
        tags.putBoolean(ModConstants.Tags.FORMATION_MARCH,true);
        var speed=new AttributeInstance(Attributes.MOVEMENT_SPEED,a->{}); speed.setBaseValue(.3);
        when(mob.getUUID()).thenReturn(UUID.randomUUID()); when(mob.isAlive()).thenReturn(true);
        when(mob.getPersistentData()).thenReturn(tags); when(mob.getAttribute(Attributes.MOVEMENT_SPEED)).thenReturn(speed);
        when(mob.distanceToSqr(Vec3.ZERO)).thenReturn(600.0*600);
        for(int i=0;i<5;i++) MarchPace.update(mob,raid,Vec3.ZERO);
        assertEquals(.36,speed.getValue(),.00001); assertEquals(.3,speed.getBaseValue());
        assertEquals(1,speed.getModifiers().size());
        assertFalse(speed.save().contains("Modifiers"),"marching bonus must not persist into saves");
        tags.remove(ModConstants.Tags.FORMATION_MARCH); MarchPace.update(mob,raid,Vec3.ZERO);
        assertEquals(.36,speed.getValue(),.00001);
        when(mob.distanceToSqr(Vec3.ZERO)).thenReturn(64.0*64); MarchPace.update(mob,raid,Vec3.ZERO);
        assertEquals(.3,speed.getValue(),.00001);
    }
    @Test void combatOccupationAndWorkingCrewsLoseOnlyTheMarchModifier() {
        var mob=mock(Mob.class); var raid=new RaidSavedData.RaidState("team:test","siege_core",0);
        var tags=new CompoundTag(); tags.putString(ModConstants.Tags.RAID_TEAM,raid.teamKey);
        var speed=new AttributeInstance(Attributes.MOVEMENT_SPEED,a->{});speed.setBaseValue(.3);
        var other=new AttributeModifier(UUID.randomUUID(),"other mod",.1,AttributeModifier.Operation.ADDITION);
        speed.addTransientModifier(other);
        when(mob.getUUID()).thenReturn(UUID.randomUUID());when(mob.isAlive()).thenReturn(true);
        when(mob.getPersistentData()).thenReturn(tags);when(mob.getAttribute(Attributes.MOVEMENT_SPEED)).thenReturn(speed);
        when(mob.distanceToSqr(Vec3.ZERO)).thenReturn(600.0*600);
        MarchPace.update(mob,raid,Vec3.ZERO);assertNotNull(speed.getModifier(MarchPace.ID));
        var target=mock(Mob.class);when(target.isAlive()).thenReturn(true);when(mob.getTarget()).thenReturn(target);
        MarchPace.update(mob,raid,Vec3.ZERO);assertNull(speed.getModifier(MarchPace.ID));
        assertNotNull(speed.getModifier(other.getId()));assertEquals(.4,speed.getValue(),.00001);
        when(mob.getTarget()).thenReturn(null);
        for(int mode=0;mode<5;mode++) {
            MarchPace.update(mob,raid,Vec3.ZERO);assertNotNull(speed.getModifier(MarchPace.ID));
            switch(mode) {
                case 0 -> raid.coreCaptured=true;
                case 1 -> raid.preparationTicks=20;
                case 2 -> tags.putString(ModConstants.Tags.CAMP_WORKER_TEAM,raid.teamKey);
                case 3 -> when(mob.isPassenger()).thenReturn(true);
                case 4 -> raid.campGuards.add(mob.getUUID());
            }
            MarchPace.update(mob,raid,Vec3.ZERO);assertNull(speed.getModifier(MarchPace.ID));
            raid.coreCaptured=false;raid.preparationTicks=0;tags.remove(ModConstants.Tags.CAMP_WORKER_TEAM);
            when(mob.isPassenger()).thenReturn(false);raid.campGuards.clear();
        }
    }
    @Test void ladderWorkRemovesTravelBonusAndResumesWhenTheWorkEnds() {
        var mob=mock(Mob.class);var raid=new RaidSavedData.RaidState("team:test","siege_core",0);
        var tag=new CompoundTag();tag.putString(ModConstants.Tags.RAID_TEAM,raid.teamKey);
        var speed=new AttributeInstance(Attributes.MOVEMENT_SPEED,a->{});speed.setBaseValue(.3);
        when(mob.getUUID()).thenReturn(UUID.randomUUID());when(mob.isAlive()).thenReturn(true);
        when(mob.getPersistentData()).thenReturn(tag);when(mob.getAttribute(Attributes.MOVEMENT_SPEED)).thenReturn(speed);
        when(mob.distanceToSqr(Vec3.ZERO)).thenReturn(800.0*800);
        try(var ladders=mockStatic(com.devfarinsky.siegeoverhaul.siege.RaiderLadderGoal.class)) {
            MarchPace.update(mob,raid,Vec3.ZERO);assertEquals(.36,speed.getValue(),.00001);
            ladders.when(()->com.devfarinsky.siegeoverhaul.siege.RaiderLadderGoal.assigned(mob)).thenReturn(true);
            MarchPace.update(mob,raid,Vec3.ZERO);assertEquals(.3,speed.getValue(),.00001);
            ladders.when(()->com.devfarinsky.siegeoverhaul.siege.RaiderLadderGoal.assigned(mob)).thenReturn(false);
            MarchPace.update(mob,raid,Vec3.ZERO);assertEquals(.36,speed.getValue(),.00001);
        }
    }
}
