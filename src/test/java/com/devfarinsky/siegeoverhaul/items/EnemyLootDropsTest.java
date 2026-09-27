package com.devfarinsky.siegeoverhaul.items;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EnemyLootDropsTest extends MinecraftTestSupport {
    @Test void exactDropDistributionHasNoRareOrEpicWindfall() {
        var random=mock(RandomSource.class);int common=0,uncommon=0,empty=0;
        for(int i=0;i<200;i++) {
            when(random.nextInt(200)).thenReturn(i);var tier=EnemyLootDrops.roll(random);
            if(tier==null)empty++;else if(tier==LootBoxItem.Tier.COMMON)common++;else if(tier==LootBoxItem.Tier.UNCOMMON)uncommon++;else fail();
        }
        assertEquals(4,common);assertEquals(1,uncommon);assertEquals(195,empty);
    }
    @Test void onlyEarnedMobKillsWithNonCreativeKillerAreMarked() {
        var mob=mock(Mob.class);var tag=new CompoundTag();when(mob.getPersistentData()).thenReturn(tag);
        var killer=mock(Player.class);var abilities=new Abilities();when(killer.getAbilities()).thenReturn(abilities);
        EnemyLootDrops.mark(mob,killer,true);assertTrue(tag.getBoolean(EnemyLootDrops.PENDING));
        EnemyLootDrops.mark(mob,killer,false);assertFalse(tag.contains(EnemyLootDrops.PENDING));
        abilities.instabuild=true;EnemyLootDrops.mark(mob,killer,true);assertFalse(tag.contains(EnemyLootDrops.PENDING));
        abilities.instabuild=false;when(killer.isSpectator()).thenReturn(true);
        EnemyLootDrops.mark(mob,killer,true);assertFalse(tag.contains(EnemyLootDrops.PENDING));
        EnemyLootDrops.mark(mob,null,true);assertFalse(tag.contains(EnemyLootDrops.PENDING));
        EnemyLootDrops.mark(mob,mock(Mob.class),true);assertTrue(tag.getBoolean(EnemyLootDrops.PENDING));
    }
    @Test void dropHookConsumesOpportunityOnceAndHonorsCancellationAndMobLoot() {
        for(int mode=0;mode<4;mode++) {
            var victim=mock(Mob.class);var level=mock(ServerLevel.class);var tag=new CompoundTag();
            var rules=mock(GameRules.class);var random=mock(RandomSource.class);
            when(victim.level()).thenReturn(level);when(victim.getPersistentData()).thenReturn(tag);
            when(level.getGameRules()).thenReturn(rules);when(level.getRandom()).thenReturn(random);
            when(rules.getBoolean(GameRules.RULE_DOMOBLOOT)).thenReturn(mode!=2);
            var event=mock(LivingDropsEvent.class);var drops=new ArrayList<ItemEntity>();
            when(event.getEntity()).thenReturn(victim);when(event.getDrops()).thenReturn(drops);
            when(event.isCanceled()).thenReturn(mode==1);
            if(mode!=3)tag.putBoolean(EnemyLootDrops.PENDING,true);
            try(var boxes=mockStatic(EnemyLootBoxes.class);var entities=mockConstruction(ItemEntity.class)) {
                boxes.when(()->EnemyLootBoxes.stack(LootBoxItem.Tier.COMMON)).thenReturn(new ItemStack(Items.CHEST));
                EnemyLootDrops.drops(event);EnemyLootDrops.drops(event);
                assertFalse(tag.contains(EnemyLootDrops.PENDING));
                assertEquals(mode==0?1:0,drops.size());
                verify(random,times(mode==0?1:0)).nextInt(200);
                if(mode==0)verify(entities.constructed().get(0)).setDefaultPickUpDelay();
            }
        }
    }
}
