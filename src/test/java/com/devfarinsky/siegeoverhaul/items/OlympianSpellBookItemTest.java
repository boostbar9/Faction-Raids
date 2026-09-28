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
    // Items are registered during Forge startup in production. The JUnit registry is
    // already frozen, so bind the role on an unregistered item and run its real methods.
    private static OlympianSpellBookItem item(int role) {
        var item = mock(OlympianSpellBookItem.class, CALLS_REAL_METHODS);
        try {
            var field = OlympianSpellBookItem.class.getDeclaredField("role");
            field.setAccessible(true);field.setInt(item,role);
            return item;
        } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
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
            book=item(role);abilities.instabuild=true;
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
    @Test void aimedBooksLaunchExactlyOnceAfterCharging() {
        try(var packets=mockStatic(HeroCastPackets.class);var bolts=mockStatic(com.devfarinsky.siegeoverhaul.spells.OlympianBolt.class)) {
            for(int role:new int[]{22,27,30}) {
                var f=new Fixture(role);f.start();f.finish();f.finish();
                bolts.verify(()->com.devfarinsky.siegeoverhaul.spells.OlympianBolt.launch(f.level,f.player,role),times(1));
                verify(f.enemy,never()).hurt(any(),anyFloat());assertEquals(1,f.stack.getCount());
            }
        }
    }
    @Test void waterChannelIsBoundedAndStopsImmediatelyOnRelease() {
        try(var packets=mockStatic(HeroCastPackets.class);var bolts=mockStatic(com.devfarinsky.siegeoverhaul.spells.OlympianBolt.class)) {
            var f=new Fixture(29);f.start();
            for(int tick=0;tick<80;tick++) {
                when(f.level.getGameTime()).thenReturn(100L+tick);
                f.book.onUseTick(f.level,f.player,f.stack,60-tick);
                f.book.onUseTick(f.level,f.player,f.stack,60-tick);
            }
            bolts.verify(()->com.devfarinsky.siegeoverhaul.spells.OlympianBolt.launch(f.level,f.player,29),times(8));
            f.book.releaseUsing(f.stack,f.level,f.player,20);
            when(f.level.getGameTime()).thenReturn(140L);f.book.onUseTick(f.level,f.player,f.stack,20);
            bolts.verifyNoMoreInteractions();
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
}
