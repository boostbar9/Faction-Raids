package com.devfarinsky.siegeoverhaul.items;
import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.InteractionResult;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RetiredSupplyTest extends MinecraftTestSupport {
    @Test void newBoxesAndCreativeCatalogExcludeRetiredMaterialRewards() {
        try(var registry=new OlympianRelicRegistryFixture()) {
            for(var tier:LootBoxItem.Tier.values())for(int seed=0;seed<300;seed++)
                for(var item:LootBoxItem.roll(RandomSource.create(seed),tier)) {
                    assertRetiredAbsent(item);
                    assertFalse(item.getItem() instanceof BlockItem,item.getHoverName().getString());
                    assertFalse(item.getHoverName().getString().contains("Forge Stock"));
                    assertFalse(item.getHoverName().getString().contains("Tribute"));
                }
            for(var item:CreativeCatalog.entries(java.util.List.of())) {
                assertRetiredAbsent(item);
                // Faction banner standards remain intentional creative entries.
                if(item.getItem() instanceof BlockItem block)
                    assertTrue(block.getBlock() instanceof net.minecraft.world.level.block.AbstractBannerBlock);
            }
        }
    }
    private static void assertRetiredAbsent(ItemStack item) {
        assertFalse(item.is(Items.FISHING_ROD));
        assertFalse(item.getItem() instanceof HoeItem);
        assertNotEquals(Potions.WATER_BREATHING,PotionUtils.getPotion(item));
        assertNotEquals(Potions.LONG_WATER_BREATHING,PotionUtils.getPotion(item));
        assertNull(OlympianSupplyPowers.power(item));
    }
    @Test void newFoodPotionsAndAmmunitionNeverInterceptSneakUse() {
        try(var registry=new OlympianRelicRegistryFixture()) {
            var f=new OlympianSupplyPowersTest.Fixture();
            for(var tier:LootBoxItem.Tier.values()) {
                var food=OlympianLoot.provisions(tier);
                assertEquals(InteractionResult.PASS,f.use(food));
                assertTrue(food.isEdible());
                for(int pick:OlympianLoot.AVAILABLE_SUPPLIES) {
                    var stack=OlympianLoot.supplies(tier,pick);
                    int count=stack.getCount();
                    assertEquals(InteractionResult.PASS,f.use(stack));
                    assertEquals(count,stack.getCount());
                }
            }
            org.mockito.Mockito.verify(f.player,org.mockito.Mockito.never()).addEffect(org.mockito.ArgumentMatchers.any());
        }
    }
    @Test void retainedCombatArmoryIsReachableAtEveryTier() {
        try(var registry=new OlympianRelicRegistryFixture()) {
            for(var tier:LootBoxItem.Tier.values()) {
                var seen=new java.util.HashSet<String>();
                for(int seed=0;seed<1000;seed++)seen.add(LootBoxItem.roll(RandomSource.create(seed),tier).get(0).getHoverName().getString());
                for(int pick:OlympianLoot.availableArmory(tier))
                    assertTrue(seen.contains(OlympianLoot.armory(tier,pick).getHoverName().getString()));
                assertEquals(tier.ordinal()<2?10:11,seen.size());
            }
        }
    }

}
