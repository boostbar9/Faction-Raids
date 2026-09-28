package com.devfarinsky.siegeoverhaul.spells;
import com.devfarinsky.siegeoverhaul.*;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OlympianBoltTest extends MinecraftTestSupport {
    @Test void elementalHitsAreDistinctAndAlliesNeverReceiveEffects() {
        for(int kind:new int[]{22,27,29,30}) {
            var level=mock(ServerLevel.class);var player=mock(Player.class);var target=mock(Mob.class);
            var data=new CompoundTag();data.putString(ModConstants.Tags.RAID_TEAM,"enemy");
            when(target.getPersistentData()).thenReturn(data);when(target.isAlive()).thenReturn(true);
            var bolt=mock(OlympianBolt.class,CALLS_REAL_METHODS);doNothing().when(bolt).discard();doReturn(kind).when(bolt).kind();doReturn(level).when(bolt).level();
            doReturn(new Vec3(1,0,0)).when(bolt).getDeltaMovement();
            var sources=mock(DamageSources.class);when(level.damageSources()).thenReturn(sources);
            var source=new DamageSource(Holder.direct(new DamageType("test",0)),player);
            when(sources.indirectMagic(bolt,player)).thenReturn(source);when(target.hurt(any(),anyFloat())).thenReturn(true);
            bolt.affect(player,target);verify(target).hurt(source,kind==29?2F:kind==22?5F:6F);
            verify(target,times(kind==27?1:0)).setSecondsOnFire(4);
            if(kind==30)verify(target).addEffect(argThat(e->e.getAmplifier()==1 && e.getDuration()==80));
            if(kind==29)verify(target).push(.35,.08,0);
            clearInvocations(target);when(player.isAlliedTo(target)).thenReturn(true);bolt.affect(player,target);
            verify(target,never()).hurt(any(),anyFloat());verify(target,never()).addEffect(any());
            assertFalse(OlympianBolt.eligible(player,mock(Player.class)));
            when(player.isAlliedTo(target)).thenReturn(false);data.remove(ModConstants.Tags.RAID_TEAM);
            assertFalse(OlympianBolt.eligible(player,target));
            verify(level,never()).setBlock(any(),any(),anyInt());
        }
    }
    @Test void solidImpactConsumesTheBoltWithoutChangingAnyBlocks() {
        var level=mock(ServerLevel.class);var player=mock(Player.class);
        when(player.isAlive()).thenReturn(true);var abilities=new net.minecraft.world.entity.player.Abilities();abilities.instabuild=true;
        when(player.getAbilities()).thenReturn(abilities);
        for(int kind:new int[]{22,27,29,30}) {
            var bolt=mock(OlympianBolt.class,CALLS_REAL_METHODS);doNothing().when(bolt).discard();doReturn(level).when(bolt).level();doReturn(kind).when(bolt).kind();
            doReturn(player).when(bolt).getOwner();doReturn(new Vec3(1,0,0)).when(bolt).getDeltaMovement();
            bolt.onHit(new net.minecraft.world.phys.BlockHitResult(Vec3.ZERO,net.minecraft.core.Direction.WEST,net.minecraft.core.BlockPos.ZERO,false));
            verify(bolt).discard();verify(level,never()).setBlock(any(),any(),anyInt());verify(level,never()).addFreshEntity(any());
        }
    }
    @Test void expiredProjectilesCannotTickTheirMotionOrLoadChunks() throws Exception {
        var bolt=mock(OlympianBolt.class,CALLS_REAL_METHODS);doNothing().when(bolt).discard();var level=mock(ServerLevel.class);
        doReturn(level).when(bolt).level();doReturn(new Vec3(1,0,0)).when(bolt).getDeltaMovement();
        var age=OlympianBolt.class.getDeclaredField("age");age.setAccessible(true);age.setInt(bolt,40);
        bolt.tick();verify(bolt).discard();verify(level,never()).getChunk(anyInt(),anyInt());
        assertFalse(bolt.shouldBeSaved());
    }
}
