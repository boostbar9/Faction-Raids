package com.devfarinsky.siegeoverhaul.items;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OlympianSupplyPowersTest extends MinecraftTestSupport {
    static class Fixture {
        final Player player=mock(Player.class);
        final ServerLevel level=mock(ServerLevel.class);
        final CompoundTag data=new CompoundTag();
        final Abilities abilities=new Abilities();
        Fixture() {
            when(player.isAlive()).thenReturn(true);when(player.isShiftKeyDown()).thenReturn(true);
            when(player.getPersistentData()).thenReturn(data);when(player.getAbilities()).thenReturn(abilities);
            when(player.getInventory()).thenReturn(new Inventory(player));when(level.getGameTime()).thenReturn(100L);
            when(player.addEffect(any())).thenReturn(true);
        }
        InteractionResult use(ItemStack stack) {return OlympianSupplyPowers.activate(level,player,stack);}
    }
    @Test void everySupplyAndProvisionHasPersistentPowerAndExactActivationLore() {
        try(var registry=new OlympianRelicRegistryFixture()) {
            for(var tier:LootBoxItem.Tier.values()) {
                var rewards=new ArrayList<ItemStack>();rewards.add(OlympianLoot.provisions(tier));
                for(int i=0;i<OlympianLoot.RELIC_FIRST;i++)rewards.add(OlympianLoot.supplies(tier,i));
                rewards.add(OlympianLoot.supplies(tier,0,OlympianLoot.armory(tier,4)));
                for(var stack:rewards) {
                    var power=OlympianSupplyPowers.power(stack);assertNotNull(power);
                    assertTrue(ItemStack.matches(stack,ItemStack.of(stack.save(new CompoundTag()))));
                    var loreTags=stack.getTagElement("display").getList("Lore",Tag.TAG_STRING);
                    var lore=new StringBuilder();
                    for(int line=0;line<loreTags.size();line++)lore.append(net.minecraft.network.chat.Component.Serializer.fromJson(loreTags.getString(line)).getString()).append('\n');
                    String visibleLore=lore.toString();
                    assertTrue(visibleLore.contains("Sneak-use in air: spend "+power.cost(stack)));
                    for(var buff:power.buffs)assertTrue(visibleLore.contains(buff.description()));
                    assertTrue(visibleLore.contains("10s shared supply cooldown"));
                }
            }
        }
    }
    @Test void allPowersApplyTheirAdvertisedEffectsAndExactCost() {
        for(var power:OlympianSupplyPowers.Power.values()) for(var item:power.items) {
            var f=new Fixture();var stack=OlympianSupplyPowers.imbue(new ItemStack(item,16),power);
            assertEquals(InteractionResult.CONSUME,f.use(stack));assertEquals(16-power.cost(stack),stack.getCount());
            var effects=ArgumentCaptor.forClass(MobEffectInstance.class);
            verify(f.player,times(power.buffs.size())).addEffect(effects.capture());
            for(int i=0;i<power.buffs.size();i++) {
                var expected=power.buffs.get(i);var actual=effects.getAllValues().get(i);
                assertSame(expected.effect(),actual.getEffect());assertEquals(expected.seconds()*20,actual.getDuration());
                assertEquals(expected.amplifier(),actual.getAmplifier());
            }
            assertEquals(300,f.data.getLong(OlympianSupplyPowers.NEXT));
        }
    }
    @Test void strongerInfiniteCanceledAndAlreadyLongEffectsDoNotWasteSupplies() {
        for(int mode=0;mode<4;mode++) {
            var f=new Fixture();var stack=OlympianLoot.supplies(LootBoxItem.Tier.COMMON,0);
            if(mode<3)when(f.player.getEffect(MobEffects.NIGHT_VISION)).thenReturn(
                    new MobEffectInstance(MobEffects.NIGHT_VISION,mode==0?-1:2000,mode==2?1:0));
            else when(f.player.addEffect(any())).thenReturn(false);
            assertEquals(InteractionResult.FAIL,f.use(stack));assertEquals(16,stack.getCount());assertTrue(f.data.isEmpty());
            if(mode<3)verify(f.player,never()).addEffect(any());
        }
    }
    @Test void sharedCooldownSurvivesCopyingPlayerDataAndSwitchingSupplyTypes() {
        var f=new Fixture();assertEquals(InteractionResult.CONSUME,f.use(OlympianLoot.supplies(LootBoxItem.Tier.COMMON,0)));
        var other=new Fixture();when(other.player.getPersistentData()).thenReturn(f.data.copy());
        var stock=OlympianLoot.supplies(LootBoxItem.Tier.COMMON,2);
        assertEquals(InteractionResult.FAIL,other.use(stock));assertEquals(8,stock.getCount());
        when(other.level.getGameTime()).thenReturn(300L);
        assertEquals(InteractionResult.CONSUME,other.use(stock));assertEquals(4,stock.getCount());
    }
    @Test void vanillaItemsNormalUseAndInvalidTagsRemainUntouched() {
        var f=new Fixture();var plain=new ItemStack(Items.STONE_BRICKS,32);
        assertEquals(InteractionResult.PASS,f.use(plain));
        plain.getOrCreateTag().putString(OlympianSupplyPowers.POWER,"FORGE_WARD");
        assertEquals(InteractionResult.PASS,f.use(plain));
        plain.getOrCreateTag().putString(OlympianSupplyPowers.POWER,"bogus");
        assertEquals(InteractionResult.PASS,f.use(plain));
        when(f.player.isShiftKeyDown()).thenReturn(false);
        assertEquals(InteractionResult.PASS,f.use(OlympianLoot.supplies(LootBoxItem.Tier.COMMON,2)));
        verify(f.player,never()).addEffect(any());
    }
    @Test void insufficientStockClientAndIneligiblePlayersCannotSpendOrAct() {
        for(int mode=0;mode<4;mode++) {
            var f=new Fixture();var stock=OlympianLoot.supplies(LootBoxItem.Tier.COMMON,2);
            if(mode==0)stock.setCount(1);if(mode==1)when(f.player.isSpectator()).thenReturn(true);
            if(mode==2)when(f.player.isAlive()).thenReturn(false);
            int count=stock.getCount();
            OlympianSupplyPowers.activate(mode==3?mock(net.minecraft.world.level.Level.class):f.level,f.player,stock);
            assertEquals(count,stock.getCount());verify(f.player,never()).addEffect(any());
        }
        var f=new Fixture();f.abilities.instabuild=true;var stock=OlympianLoot.supplies(LootBoxItem.Tier.COMMON,2);stock.setCount(1);
        assertEquals(InteractionResult.CONSUME,f.use(stock));assertEquals(1,stock.getCount());
    }
}
