package com.devfarinsky.siegeoverhaul;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RaidMarchTickTest extends MinecraftTestSupport {
    @Test void normalTrackedRaiderPassAppliesPaceAndRemovesItDuringOccupation() {
        var level=mock(ServerLevel.class); var mob=mock(Mob.class); var id=UUID.randomUUID();
        var raid=new RaidSavedData.RaidState("team:test","siege_core",0);raid.raiders.add(id);
        var tag=new CompoundTag();tag.putString(ModConstants.Tags.RAID_TEAM,raid.teamKey);
        var speed=new AttributeInstance(Attributes.MOVEMENT_SPEED,a->{});speed.setBaseValue(.3);
        when(level.getEntity(id)).thenReturn(mob);when(mob.getUUID()).thenReturn(id);
        when(mob.isAlive()).thenReturn(true);when(mob.blockPosition()).thenReturn(new BlockPos(800,64,0));
        when(mob.getPersistentData()).thenReturn(tag);when(mob.getAttribute(Attributes.MOVEMENT_SPEED)).thenReturn(speed);
        when(mob.distanceToSqr(any(Vec3.class))).thenReturn(800.0*800);
        try(var labels=mockStatic(RaiderLabels.class)) {
            RaidEvents.updateTrackedMobs(level,raid,new BlockPos(0,64,0));
            RaidEvents.updateTrackedMobs(level,raid,new BlockPos(0,64,0));
            assertEquals(.36,speed.getValue(),.00001);
            assertEquals(1,speed.getModifiers().size());
            assertTrue(raid.lastKnownChunks.containsKey(id));
            raid.coreCaptured=true;
            RaidEvents.updateTrackedMobs(level,raid,new BlockPos(0,64,0));
            assertEquals(.3,speed.getValue(),.00001);
            assertEquals(1,raid.raiders.size());
        }
    }
}
