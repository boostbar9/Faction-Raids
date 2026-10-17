package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.talhanation.recruits.inventory.RecruitSimpleContainer;
import com.talhanation.workers.entities.BuilderEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** API/item-data tests only. They do not execute native storage or establish gameplay evidence. */
class EarthworksSupplyDemandTest extends MinecraftTestSupport {
    @Test void exactMaterialDemandDoesNotAcceptAnotherNativeQueuedMaterialOrChangedMetadata() {
        assertTrue(EarthworksSupplyDemand.ordinary(new ItemStack(Items.DIRT, 64), Items.DIRT, false));
        assertFalse(EarthworksSupplyDemand.ordinary(new ItemStack(Items.OAK_PLANKS, 64), Items.DIRT, false));
        ItemStack named = new ItemStack(Items.DIRT); named.getOrCreateTag().putString("qa", "preserve this item");
        CompoundTag before = named.save(new CompoundTag());
        assertFalse(EarthworksSupplyDemand.ordinary(named, Items.DIRT, false));
        assertEquals(before, named.save(new CompoundTag()));
    }
    @Test void FreshShovelDemandAndSelectedWornToolAreSeparateContracts() {
        ItemStack shovel = new ItemStack(Items.IRON_SHOVEL);
        assertTrue(EarthworksSupplyDemand.ordinary(shovel, Items.IRON_SHOVEL, true));
        shovel.setDamageValue(12);
        assertFalse(EarthworksSupplyDemand.ordinary(shovel, Items.IRON_SHOVEL, true));
        assertTrue(EarthworksSupplyDemand.ordinary(shovel, Items.IRON_SHOVEL, false));
        shovel.setDamageValue(shovel.getMaxDamage() - 1);
        assertFalse(EarthworksSupplyDemand.ordinary(shovel, Items.IRON_SHOVEL, false));
    }
    @Test void FullToolMetadataCannotHideUnbreakableEnchantmentsOrCustomData() {
        for (String key : new String[]{"Unbreakable", "Enchantments", "ForgeCaps", "qa"}) {
            ItemStack shovel = new ItemStack(Items.IRON_SHOVEL); shovel.getOrCreateTag().putBoolean(key, true);
            assertFalse(EarthworksSupplyDemand.ordinary(shovel, Items.IRON_SHOVEL, false));
        }
    }
    @Test void ScopeRequiresAllExactFieldsAndSurvivesRoundTrip() {
        var scope = new EarthworksSupplyDemand.Scope(UUID.randomUUID(), 3, UUID.randomUUID(), "a".repeat(64), "b".repeat(64), 2);
        assertEquals(scope, EarthworksSupplyDemand.Scope.read(scope.save()));
        var malformed = scope.save(); malformed.putInt("Extra", 1);
        assertThrows(IllegalStateException.class, () -> EarthworksSupplyDemand.Scope.read(malformed));
        assertThrows(IllegalStateException.class, () -> new EarthworksSupplyDemand.Scope(scope.project(), 0, scope.area(), scope.manifest(), scope.binding(), 2));
        assertFalse(scope.sameJob(new EarthworksSupplyDemand.Scope(scope.project(), 4, scope.area(), scope.manifest(), scope.binding(), 2)));
    }
    @Test void MissingToolCanRequestSupplyButUnselectableAndMultipleToolsCannotDuplicateIt() {
        var fixture = fixture();
        assertFalse(EarthworksSupplyDemand.selectable(fixture.worker, Items.IRON_SHOVEL));
        fixture.inventory.setItem(4, new ItemStack(Items.IRON_SHOVEL));
        assertThrows(IllegalStateException.class, () -> EarthworksSupplyDemand.selectable(fixture.worker, Items.IRON_SHOVEL));
        fixture.inventory.setItem(4, ItemStack.EMPTY); fixture.inventory.setItem(6, new ItemStack(Items.IRON_SHOVEL));
        assertTrue(EarthworksSupplyDemand.selectable(fixture.worker, Items.IRON_SHOVEL));
        fixture.inventory.setItem(7, new ItemStack(Items.IRON_SHOVEL));
        assertThrows(IllegalStateException.class, () -> EarthworksSupplyDemand.selectable(fixture.worker, Items.IRON_SHOVEL));
    }
    @Test void firstNativeMatchingOffhandMaterialCannotAuthorizeCargoPlacement() {
        var fixture = fixture(); fixture.inventory.setItem(4, new ItemStack(Items.DIRT)); fixture.inventory.setItem(6, new ItemStack(Items.DIRT));
        assertThrows(IllegalStateException.class, () -> EarthworksSupplyDemand.selectable(fixture.worker, Items.DIRT));
    }
    @Test void splitHandAndAliasedCargoAreRejectedWithoutRebindingOrChangingAmounts() {
        var fixture = fixture(); ItemStack hand = new ItemStack(Items.DIRT, 3); fixture.inventory.setItem(5, hand);
        when(fixture.worker.getMainHandItem()).thenReturn(hand.copy());
        assertThrows(IllegalStateException.class, () -> EarthworksSupplyDemand.selectable(fixture.worker, Items.DIRT));
        assertEquals(3, hand.getCount());
        when(fixture.worker.getMainHandItem()).thenReturn(hand); fixture.inventory.setItem(6, hand);
        assertThrows(IllegalStateException.class, () -> EarthworksSupplyDemand.selectable(fixture.worker, Items.DIRT));
        verify(fixture.worker, never()).addNeededItem(any());
    }
    private record Fixture(BuilderEntity worker, RecruitSimpleContainer inventory) {}
    private static Fixture fixture() {
        var worker = mock(BuilderEntity.class); var inventory = new RecruitSimpleContainer(15, worker);
        when(worker.getInventory()).thenReturn(inventory); when(worker.getPersistentData()).thenReturn(new CompoundTag());
        when(worker.getUseItem()).thenReturn(ItemStack.EMPTY); when(worker.getMainHandItem()).thenAnswer(ignored -> inventory.getItem(5));
        return new Fixture(worker, inventory);
    }
}
