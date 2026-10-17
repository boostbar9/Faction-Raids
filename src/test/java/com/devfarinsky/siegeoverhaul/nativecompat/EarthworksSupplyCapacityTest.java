package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class EarthworksSupplyCapacityTest extends MinecraftTestSupport {
    @Test void fullDistinctCargoReservationBoundsEverySubsetAndPositiveCountWidth() {
        var inventory=new SimpleContainer(15);inventory.setItem(6,new ItemStack(Items.DIRT,64));inventory.setItem(7,new ItemStack(Items.DIRT,64));
        inventory.setItem(8,new ItemStack(Items.COBBLESTONE,10));
        var frame=EarthworksInventoryEvidence.capture(inventory,new IdentityHashMap<>());var scope=scope();
        var reservation=EarthworksSupplyOperations.reserve(new ListTag(),scope,UUID.randomUUID(),"DEPOSIT",Long.MAX_VALUE,
                Set.of(new BlockPos(-30000000,-64,29999999),new BlockPos(-30000000,-63,29999999)),frame,"a".repeat(64));
        for(var subset:List.of(Map.<String,Integer>of(),Map.of(frame.slots().get(6).key(),1),Map.of(frame.slots().get(6).key(),99),frame.totals())) {
            var next=reservation.observe("b".repeat(64),"c".repeat(64),subset);
            assertTrue(WorkersEarthworksPort.canonical(next.getCompound(0)).length()<=reservation.recordLength());
            assertTrue(WorkersEarthworksPort.canonical(next).length()<=reservation.historyLength());
        }
        assertThrows(IllegalStateException.class,()->reservation.observe("b".repeat(64),"c".repeat(64),Map.of(frame.slots().get(6).key(),129)));
    }
    @Test void swapAndAlreadyHeldObservationsReserveTheirExactRecordWithoutSerializingUnrelatedCargo() {
        var inventory=new SimpleContainer(15);var large=new ItemStack(Items.COBBLESTONE);large.getOrCreateTag().putString("Note","x".repeat(1900));
        inventory.setItem(6,large);var frame=EarthworksInventoryEvidence.capture(inventory,new IdentityHashMap<>());
        for(String kind:List.of("SWAP","OBSERVED_HAND")) {
            var reservation=EarthworksSupplyOperations.reserve(new ListTag(),scope(),UUID.randomUUID(),kind,987654321L,Set.of(),frame,"");
            var observed=reservation.observe("f".repeat(64),"",Map.of());
            assertEquals(reservation.recordLength(),WorkersEarthworksPort.canonical(observed.getCompound(0)).length());
            assertEquals(reservation.historyLength(),WorkersEarthworksPort.canonical(observed).length());
        }
    }
    @Test void completeAppendedHistoryIncludingFramingIsCheckedBeforeAnotherSwap() {
        var frame=EarthworksInventoryEvidence.capture(new SimpleContainer(15),new IdentityHashMap<>());var scope=scope();var history=new ListTag();
        while(true){
            try {
                var reservation=EarthworksSupplyOperations.reserve(history,scope,UUID.randomUUID(),"OBSERVED_HAND",Long.MAX_VALUE,Set.of(),frame,"");
                history=reservation.observe("b".repeat(64),"",Map.of());
            } catch(IllegalStateException full){break;}
        }
        assertFalse(history.isEmpty());assertTrue(history.size()<=EarthworksSupplyOperations.MAX_OPERATIONS);
        var before=history.copy();var retained=history;
        // A fresh UUID can have a slightly shorter numeric encoding. Whichever result is possible,
        // the exact newly prebuilt complete history must fit, and refusal never mutates retained evidence.
        try {
            var reserved=EarthworksSupplyOperations.reserve(retained,scope,UUID.randomUUID(),"OBSERVED_HAND",Long.MAX_VALUE,Set.of(),frame,"");
            assertTrue(reserved.historyLength()<=EarthworksSupplyOperations.MAX_HISTORY_BYTES);
            var next=reserved.observe("b".repeat(64),"",Map.of());
            assertEquals(reserved.historyLength(),WorkersEarthworksPort.canonical(next).length());
        } catch(IllegalStateException full) { assertEquals(before,history); }
        assertEquals(before,history);
    }
    private static EarthworksSupplyDemand.Scope scope(){return new EarthworksSupplyDemand.Scope(UUID.randomUUID(),Long.MAX_VALUE,UUID.randomUUID(),"a".repeat(64),"b".repeat(64),NativeEarthworksAdapter.MAX_STEPS-1);}
}
