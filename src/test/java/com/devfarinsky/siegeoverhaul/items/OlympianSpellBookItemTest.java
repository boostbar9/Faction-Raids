package com.devfarinsky.siegeoverhaul.items;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.ModConstants;
import com.devfarinsky.siegeoverhaul.core.HeroCastPackets;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OlympianSpellBookItemTest extends MinecraftTestSupport {
    private static class Fixture {
        final ServerLevel level=mock(ServerLevel.class);
        final Player player=mock(Player.class);
        final Mob enemy=mock(Mob.class);
        final CompoundTag tag=new CompoundTag(),enemyTag=new CompoundTag();
        final Abilities abilities=new Abilities();
        final OlympianSpellBookItem book;
        final ItemStack stack=new ItemStack(Items.BOOK);
        final DamageSource damage=new DamageSource(Holder.direct(new DamageType("test",0)),player);
        Fixture(int role) {
            book=new OlympianSpellBookItem(role);abilities.instabuild=true;
            when(player.getAbilities()).thenReturn(abilities);when(player.getPersistentData()).thenReturn(tag);
            when(player.isAlive()).thenReturn(true);when(enemy.isAlive()).thenReturn(true);
            when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(stack);
            when(player.getCooldowns()).thenReturn(mock(ItemCooldowns.class));
            when(player.getBoundingBox()).thenReturn(new AABB(0,64,0,1,66,1));
            when(player.hasLineOfSight(enemy)).thenReturn(true);when(enemy.getPersistentData()).thenReturn(enemyTag);
            enemyTag.putString(ModConstants.Tags.RAID_TEAM,"team:enemy");
            when(level.getGameTime()).thenReturn(100L);
            when(level.getEntitiesOfClass(eq(LivingEntity.class),any(AABB.class),any())).thenAnswer(call->{Predicate<LivingEntity> filter=call.getArgument(2);return filter.test(enemy)?List.of(enemy):List.of();});
            var sources=mock(DamageSources.class);when(level.damageSources()).thenReturn(sources);when(sources.indirectMagic(player,player)).thenReturn(damage);
            when(enemy.hurt(any(),anyFloat())).thenReturn(true);
        }
        void start(){book.use(level,player,InteractionHand.MAIN_HAND);}
        void finish(){when(level.getGameTime()).thenReturn(120L);book.finishUsingItem(stack,level,player);}
    }
    @Test void booksReleaseCorrectEffectsOnceAndPreserveTheItem() {
        try(var packets=mockStatic(HeroCastPackets.class)) {
            for(int role:new int[]{22,27,29}) {
                var f=new Fixture(role);f.start();verify(f.enemy,never()).hurt(any(),anyFloat());f.finish();f.finish();
                if(role==29)verify(f.enemy,times(1)).addEffect(argThat(e->e.getAmplifier()==4 && e.getDuration()==80));
                else verify(f.enemy,times(1)).hurt(f.damage,role==22?5F:6F);
                verify(f.enemy,times(role==27?1:0)).setSecondsOnFire(4);assertEquals(1,f.stack.getCount());
            }
        }
    }
    @Test void survivalSpectatorsAndCooldownCannotStartACast() {
        try(var packets=mockStatic(HeroCastPackets.class)) {
            for(int reason=0;reason<3;reason++) {
                var f=new Fixture(22);
                if(reason==0)f.abilities.instabuild=false;
                if(reason==1)when(f.player.isSpectator()).thenReturn(true);
                if(reason==2)f.tag.putLong("SiegeCreativeSpellNext22",200);
                assertEquals(InteractionResult.FAIL,f.book.use(f.level,f.player,InteractionHand.MAIN_HAND).getResult());
                verify(f.player,never()).startUsingItem(any());assertFalse(f.tag.contains("SiegeCreativeSpellStart"));
            }
        }
    }
    @Test void cancellationAndLossOfCreativePreventDamageWithoutRefundingCooldown() {
        try(var packets=mockStatic(HeroCastPackets.class)) {
            for(boolean cancel:new boolean[]{true,false}) {
                var f=new Fixture(27);f.start();
                if(cancel)f.book.releaseUsing(f.stack,f.level,f.player,10);else f.abilities.instabuild=false;
                f.finish();verify(f.enemy,never()).hurt(any(),anyFloat());assertEquals(500,f.tag.getLong("SiegeCreativeSpellNext27"));
            }
        }
    }
    @Test void friendlyHiddenAndUnmarkedUnitsAreProtectedAtRelease() {
        try(var packets=mockStatic(HeroCastPackets.class)) {
            for(int reason=0;reason<4;reason++) {
                var f=new Fixture(22);f.start();
                if(reason==0)when(f.player.isAlliedTo(f.enemy)).thenReturn(true);
                if(reason==1)when(f.player.hasLineOfSight(f.enemy)).thenReturn(false);
                if(reason==2)f.enemyTag.remove(ModConstants.Tags.RAID_TEAM);
                if(reason==3)when(f.enemy.isAlive()).thenReturn(false);
                f.finish();verify(f.enemy,never()).hurt(any(),anyFloat());
            }
            var f=new Fixture(22);assertFalse(OlympianSpellBookItem.eligible(f.player,mock(Player.class)));
        }
    }
}
