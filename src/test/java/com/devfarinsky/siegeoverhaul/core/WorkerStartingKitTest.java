package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkerStartingKitTest extends MinecraftTestSupport {
    private static Mob worker(CompoundTag data) {
        Mob mob=mock(Mob.class);when(mob.getPersistentData()).thenReturn(data);return mob;
    }
    @Test void allHireableWorkersReceiveTheirFiniteJobSuppliesInCargoSlots() {
        for(int role=4;role<10;role++) {
            var inventory=new SimpleContainer(36);var mob=worker(new CompoundTag());
            WorkerStartingKit.prepare(mob,role,inventory);
            for(var stack:WorkerStartingKit.kit(role)) assertEquals(stack.getCount(),inventory.countItem(stack.getItem()));
            for(int i=0;i<6;i++) assertTrue(inventory.getItem(i).isEmpty());
        }
        var farmer=new SimpleContainer(36);WorkerStartingKit.prepare(worker(new CompoundTag()),4,farmer);
        assertEquals(1,farmer.countItem(Items.DIAMOND_HOE));assertEquals(1,farmer.countItem(Items.WATER_BUCKET));
        assertEquals(32,farmer.countItem(Items.WHEAT_SEEDS));
    }
    @Test void preparationPreservesExistingItemsAndNeverRefillsAfterReload() {
        var tag=new CompoundTag();var inventory=new SimpleContainer(36);
        var custom=new ItemStack(Items.DIAMOND_HOE);custom.setDamageValue(100);
        inventory.setItem(5,custom);inventory.setItem(6,new ItemStack(Items.WHEAT_SEEDS,10));
        WorkerStartingKit.prepare(worker(tag),4,inventory);
        assertSame(custom,inventory.getItem(5));assertEquals(1,inventory.countItem(Items.DIAMOND_HOE));
        assertEquals(32,inventory.countItem(Items.WHEAT_SEEDS));
        for(int i=6;i<36;i++)inventory.setItem(i,ItemStack.EMPTY);
        WorkerStartingKit.prepare(worker(tag.copy()),4,inventory);
        assertEquals(0,inventory.countItem(Items.WHEAT_SEEDS));assertSame(custom,inventory.getItem(5));
    }
    @Test void insufficientSpaceDoesNotPartiallyGrantItemsOrConsumeTheMarker() {
        var tag=new CompoundTag();var inventory=new SimpleContainer(8);
        var original=new ItemStack(Items.DIRT,64);inventory.setItem(6,original);
        assertThrows(IllegalStateException.class,()->WorkerStartingKit.prepare(worker(tag),4,inventory));
        assertSame(original,inventory.getItem(6));assertTrue(inventory.getItem(7).isEmpty());assertTrue(tag.isEmpty());
    }
    @Test void nonWorkersAreUntouched() {
        for(int role:new int[]{-1,0,3,10,13}) {
            Mob mob=mock(Mob.class);var inventory=new SimpleContainer(36);
            WorkerStartingKit.prepare(mob,role,inventory);verifyNoInteractions(mob);assertTrue(inventory.isEmpty());
        }
    }
}
