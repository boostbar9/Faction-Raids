package com.devfarinsky.siegeoverhaul.items;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.ModConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.*;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OlympianRelicItemTest extends MinecraftTestSupport {
    private static OlympianRelicItem item(OlympianRelics.Kind kind) {
        var item=mock(OlympianRelicItem.class,CALLS_REAL_METHODS);
        try {
            var field=OlympianRelicItem.class.getDeclaredField("kind");field.setAccessible(true);field.set(item,kind);
        } catch(ReflectiveOperationException e) { throw new AssertionError(e); }
        return item;
    }
    private static class Fixture {
        final ServerLevel level=mock(ServerLevel.class);
        final Player player=mock(Player.class);
        final ItemStack held=new ItemStack(Items.BLAZE_POWDER,2);
        final ItemStack gear=new ItemStack(Items.DIAMOND_SWORD);
        final Abilities abilities=new Abilities();
        final CompoundTag data=new CompoundTag();
        final OlympianRelicItem relic;
        Fixture(OlympianRelics.Kind kind) {
            relic=item(kind);gear.setDamageValue(120);gear.enchant(Enchantments.SHARPNESS,3);
            when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(held);
            when(player.getItemInHand(InteractionHand.OFF_HAND)).thenReturn(gear);
            when(player.getAbilities()).thenReturn(abilities);when(player.getPersistentData()).thenReturn(data);
            when(player.getCooldowns()).thenReturn(mock(ItemCooldowns.class));
            when(player.getInventory()).thenReturn(new Inventory(player));
            when(player.isAlive()).thenReturn(true);when(level.getGameTime()).thenReturn(100L);
            when(player.getBoundingBox()).thenReturn(new AABB(0,64,0,1,66,1));
            when(level.getEntitiesOfClass(eq(LivingEntity.class),any(AABB.class),any())).thenReturn(List.of());
        }
        InteractionResultHolder<ItemStack> use(){return relic.use(level,player,InteractionHand.MAIN_HAND);}
    }
    @Test void emberRepairsOtherHandOnceWithoutChangingEnchantsOrSpendingExtraCharges() {
        var f=new Fixture(OlympianRelics.Kind.FORGE);var saved=f.gear.save(new CompoundTag());
        assertEquals(InteractionResult.CONSUME,f.use().getResult());
        assertEquals(40,f.gear.getDamageValue());assertEquals(1,f.held.getCount());
        saved.getCompound("tag").putInt("Damage",40);
        assertEquals(saved,f.gear.save(new CompoundTag()));
        assertEquals(300,f.data.getLong("SiegeRelicNextFORGE"));
        assertEquals(InteractionResult.FAIL,f.use().getResult());assertEquals(40,f.gear.getDamageValue());
        assertEquals(1,f.held.getCount());
    }
    @Test void emberSupportsOffhandActivationAndSmallRepairsWithoutUnderflow() {
        var f=new Fixture(OlympianRelics.Kind.FORGE);f.gear.setDamageValue(3);
        when(f.player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(f.gear);
        when(f.player.getItemInHand(InteractionHand.OFF_HAND)).thenReturn(f.held);
        f.relic.use(f.level,f.player,InteractionHand.OFF_HAND);
        assertEquals(0,f.gear.getDamageValue());assertEquals(1,f.held.getCount());
        assertFalse(OlympianRelicItem.repair(f.gear));
        assertFalse(OlympianRelicItem.repair(new ItemStack(Items.STONE)));
        var tool=new ItemStack(Items.WOODEN_PICKAXE);tool.setDamageValue(40);
        assertTrue(OlympianRelicItem.repair(tool));assertEquals(40-tool.getMaxDamage()/4,tool.getDamageValue());
    }
    @Test void failedActionsNeverConsumeOrCreateTheLongCooldown() {
        for(var kind:OlympianRelics.Kind.values()) {
            var f=new Fixture(kind);f.gear.setDamageValue(0);
            assertEquals(InteractionResult.FAIL,f.use().getResult());assertEquals(2,f.held.getCount());
            assertTrue(f.data.isEmpty());verify(f.player.getCooldowns()).addCooldown(f.relic,20);
        }
    }
    @Test void sunLaurelOnlyRemovesItsFiveAfflictionsAndPreservesBeneficialEffects() {
        var f=new Fixture(OlympianRelics.Kind.CLEANSE);
        when(f.player.removeEffect(MobEffects.POISON)).thenReturn(true);
        f.use();assertEquals(1,f.held.getCount());
        for(var effect:OlympianRelicItem.AFFLICTIONS)verify(f.player).removeEffect(effect);
        verify(f.player,never()).removeEffect(MobEffects.REGENERATION);
        verify(f.player,never()).removeEffect(MobEffects.DAMAGE_BOOST);
        verify(f.player,never()).removeEffect(MobEffects.BAD_OMEN);
        verify(f.player,never()).removeAllEffects();
    }
    @Test void watchSealMarksOnlyNearestEightEligibleLoadedSiegeEnemies() {
        var f=new Fixture(OlympianRelics.Kind.WATCH);var foes=new ArrayList<LivingEntity>();
        for(int i=11;i>=0;i--) {
            var enemy=mock(Mob.class);var tag=new CompoundTag();tag.putString(ModConstants.Tags.RAID_TEAM,"enemy");
            when(enemy.getPersistentData()).thenReturn(tag);when(enemy.isAlive()).thenReturn(true);
            when(enemy.getUUID()).thenReturn(new UUID(0,i));when(f.player.distanceToSqr(enemy)).thenReturn((double)i*i);
            when(enemy.addEffect(any())).thenReturn(true);foes.add(enemy);
        }
        when(f.level.getEntitiesOfClass(eq(LivingEntity.class),any(AABB.class),any())).thenAnswer(call->{
            Predicate<LivingEntity> predicate=call.getArgument(2);return foes.stream().filter(predicate).toList();
        });
        f.use();assertEquals(1,f.held.getCount());
        for(int i=0;i<foes.size();i++)verify(foes.get(i),times(i>=4?1:0)).addEffect(argThat(e->e.getEffect()==MobEffects.GLOWING && e.getDuration()==200));
        var nearest=foes.get(11);
        when(f.player.isAlliedTo(nearest)).thenReturn(true);assertFalse(OlympianRelicItem.eligible(f.player,nearest));
        when(f.player.isAlliedTo(nearest)).thenReturn(false);when(f.player.distanceToSqr(nearest)).thenReturn(257D);
        assertFalse(OlympianRelicItem.eligible(f.player,nearest));
        when(f.player.distanceToSqr(nearest)).thenReturn(1D);when(nearest.getPersistentData()).thenReturn(new CompoundTag());
        assertFalse(OlympianRelicItem.eligible(f.player,nearest));assertFalse(OlympianRelicItem.eligible(f.player,mock(Player.class)));
    }
    @Test void ineffectiveRepairOrExistingOutlineDoesNotSpendACharge() {
        var target=mock(ItemStack.class);
        when(target.isDamageableItem()).thenReturn(true);when(target.isDamaged()).thenReturn(true);
        when(target.getMaxDamage()).thenReturn(400);when(target.getDamageValue()).thenReturn(100);
        assertFalse(OlympianRelicItem.repair(target),"A companion item may reject damage changes");
        var f=new Fixture(OlympianRelics.Kind.WATCH);var enemy=mock(Mob.class);
        when(enemy.getUUID()).thenReturn(new UUID(0,1));
        when(enemy.getEffect(MobEffects.GLOWING)).thenReturn(new MobEffectInstance(MobEffects.GLOWING,400));
        when(f.level.getEntitiesOfClass(eq(LivingEntity.class),any(AABB.class),any())).thenReturn(List.of(enemy));
        f.use();assertEquals(2,f.held.getCount());verify(enemy,never()).addEffect(any());
        when(enemy.getEffect(MobEffects.GLOWING)).thenReturn(null);
        when(enemy.addEffect(any())).thenReturn(false); // Forge can cancel the effect.
        f.use();assertEquals(2,f.held.getCount());assertTrue(f.data.isEmpty());
    }
    @Test void clientSpectatorDeadAndPersistedCooldownCannotPerformActions() {
        for(int reason=0;reason<4;reason++) {
            var f=new Fixture(OlympianRelics.Kind.FORGE);
            if(reason==0)when(f.player.isSpectator()).thenReturn(true);
            if(reason==1)when(f.player.isAlive()).thenReturn(false);
            if(reason==2)f.data.putLong("SiegeRelicNextFORGE",200);
            var level=reason==3?mock(net.minecraft.world.level.Level.class):f.level;
            f.relic.use(level,f.player,InteractionHand.MAIN_HAND);
            assertEquals(120,f.gear.getDamageValue());assertEquals(2,f.held.getCount());
        }
        var creative=new Fixture(OlympianRelics.Kind.FORGE);creative.abilities.instabuild=true;
        creative.use();assertEquals(2,creative.held.getCount());assertEquals(40,creative.gear.getDamageValue());
    }
    @ParameterizedTest @EnumSource(LootBoxItem.Tier.class)
    void everyBoxHasARealRelicCategoryWithBoundedCountsAndActiveSuppliesRemainReachable(LootBoxItem.Tier tier) {
        try(var registry=new OlympianRelicRegistryFixture()) {
            var seen=new HashSet<Item>();
            for(int seed=0;seed<500;seed++) {
                var rewards=LootBoxItem.roll(RandomSource.create(seed),tier);var relic=rewards.get(2);
                assertTrue(relic.is(Items.BLAZE_POWDER)||relic.is(Items.PRISMARINE_CRYSTALS)||relic.is(Items.NAUTILUS_SHELL));
                assertEquals(tier.ordinal()>=2?2:1,relic.getCount());
                assertTrue(rewards.size()>=3+tier.ordinal() && rewards.size()<=4+tier.ordinal());
                for(int i=2;i<rewards.size();i++)seen.add(rewards.get(i).getItem());
            }
            for(int pick:OlympianLoot.AVAILABLE_SUPPLIES)assertTrue(seen.contains(OlympianLoot.supplies(tier,pick).getItem()), "Missing active supply " + pick);
            for(int pick:new int[]{2,3,7,10,11})assertFalse(seen.contains(OlympianLoot.supplies(tier,pick).getItem()), "Retired supply " + pick);
            assertSame(tier.ordinal()>=2?Potions.LONG_FIRE_RESISTANCE:Potions.FIRE_RESISTANCE,PotionUtils.getPotion(OlympianLoot.supplies(tier,8)));
            assertSame(tier.ordinal()>=2?Potions.LONG_WATER_BREATHING:Potions.WATER_BREATHING,PotionUtils.getPotion(OlympianLoot.supplies(tier,9)));
            var sunbow=OlympianLoot.armory(tier,4);var ammo=OlympianLoot.supplies(tier,0,sunbow);
            assertTrue(ammo.is(tier.ordinal()>=2?Items.ARROW:Items.SPECTRAL_ARROW));
        }
    }
    @Test void relicModelsAndNamesExistWithDistinctBoundedGeometry() throws Exception {
        var shapes=new HashSet<String>();
        var lang=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/resources/assets/siegeoverhaul/lang/en_us.json"))).getAsJsonObject();
        for(var id:List.of("forge_ember","owl_seal","sun_laurel")) {
            var text=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/assets/siegeoverhaul/models/item/"+id+".json"));
            assertNotNull(net.minecraft.client.renderer.block.model.BlockModel.fromString(text));
            var json=com.google.gson.JsonParser.parseString(text).getAsJsonObject();
            assertTrue(shapes.add(json.getAsJsonArray("elements").toString()));
            assertTrue(json.getAsJsonArray("elements").size()<=16);
            assertTrue(lang.has("item.siegeoverhaul."+id));
            for(var element:json.getAsJsonArray("elements")) {
                var box=element.getAsJsonObject();
                for(int axis=0;axis<3;axis++) {
                    float from=box.getAsJsonArray("from").get(axis).getAsFloat(),to=box.getAsJsonArray("to").get(axis).getAsFloat();
                    assertTrue(from>=0 && to<=16 && from<to);
                }
            }
            for(var context:List.of("gui","firstperson_righthand","firstperson_lefthand","thirdperson_righthand","thirdperson_lefthand"))
                assertTrue(json.getAsJsonObject("display").has(context));
        }
    }

}
