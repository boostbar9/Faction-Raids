package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.world.NeededItem;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProtectedTransferCapacityTest extends MinecraftTestSupport {
    @Test void depositRequiresFullItemDataEqualityIncludingCapabilities() {
        var a=new net.minecraft.nbt.CompoundTag();a.putString("id","minecraft:cobblestone");a.putByte("Count",(byte)3);
        var b=a.copy();b.putByte("Count",(byte)51);
        assertTrue(ProtectedTransferCapacity.sameItemData(a,b));
        var caps=new net.minecraft.nbt.CompoundTag();caps.putInt("example:charge",7);b.put("ForgeCaps",caps);
        assertFalse(ProtectedTransferCapacity.sameItemData(a,b));assertEquals(3,a.getByte("Count"));assertEquals(51,b.getByte("Count"));
    }

    @Test void nativeDepositDoesNotMergeDifferentTaggedCargoEvenIntoInitiallyEmptyStorage() {
        var worker=mock(BuilderEntity.class);worker.neededItems=new ArrayList<>();
        var inventory=new com.talhanation.recruits.inventory.RecruitSimpleContainer(15,worker);
        when(worker.getInventory()).thenReturn(inventory);
        ItemStack a=new ItemStack(Items.COBBLESTONE,3),b=new ItemStack(Items.COBBLESTONE,4);
        a.getOrCreateTag().putInt("different",1);b.getOrCreateTag().putInt("different",2);
        inventory.setItem(6,a);inventory.setItem(7,b);var source=new SimpleContainer(27);
        assertTrue(ProtectedTransferCapacity.depositProblem(worker,source).contains("differing item data"));
        assertEquals(3,a.getCount());assertEquals(4,b.getCount());assertTrue(source.isEmpty());
        b.setTag(a.getTag().copy());assertNull(ProtectedTransferCapacity.depositProblem(worker,source));
        assertSame(a,inventory.getItem(6));assertSame(b,inventory.getItem(7));
    }

    @Test void upkeepWholeArrowStackNeedsGuaranteedCapacityBeforeNativeIgnoredLeftoverBranch() {
        var worker=mock(BuilderEntity.class);worker.neededItems=new ArrayList<>();
        var inventory=new com.talhanation.recruits.inventory.RecruitSimpleContainer(15,worker);
        when(worker.getInventory()).thenReturn(inventory);
        inventory.setItem(6,new ItemStack(Items.ARROW,31));
        for(int slot=7;slot<15;slot++)inventory.setItem(slot,new ItemStack(Items.COBBLESTONE,64));
        var arrows=spy(new ItemStack(Items.ARROW,64));
        // Plain JUnit does not load datapack tags; model only this exact vanilla tag membership.
        when(arrows.is(net.minecraft.tags.ItemTags.ARROWS)).thenReturn(true);
        var source=new SimpleContainer(arrows);
        assertTrue(ProtectedTransferCapacity.upkeepProblem(worker,source).contains("empty builder cargo"));
        assertEquals(64,arrows.getCount());assertEquals(31,inventory.getItem(6).getCount());
        inventory.setItem(14,ItemStack.EMPTY);
        assertNull(ProtectedTransferCapacity.upkeepProblem(worker,source));
        assertEquals(64,arrows.getCount());assertTrue(inventory.getItem(14).isEmpty());
    }

    @Test void ordinaryFullSupplyChestRequiresOnlySlotsForActualNativeExtractions() {
        List<ItemStack> supplies = new ArrayList<>();
        for (int i=0;i<8;i++) supplies.add(new ItemStack(Items.COBBLESTONE,64));
        for (int i=0;i<8;i++) supplies.add(new ItemStack(Items.OAK_PLANKS,64));
        var requests = List.of(demand(Items.COBBLESTONE,64), demand(Items.OAK_PLANKS,64));
        var result = ProtectedTransferCapacity.simulate(supplies,requests,2,64);
        assertNull(result.problem()); assertEquals(2,result.emptySlotsRequired());
        assertTrue(supplies.stream().allMatch(stack -> stack.getCount()==64), "No live supplied stack is shrunk");
        assertEquals(64,requests.get(0).count()); assertEquals(64,requests.get(1).count());
    }

    @Test void laterNativeRequestCannotUseAnEmptySlotAlreadyReservedByTheFirst() {
        var result = ProtectedTransferCapacity.simulate(List.of(new ItemStack(Items.COBBLESTONE,64), new ItemStack(Items.OAK_PLANKS,64)),
                List.of(demand(Items.COBBLESTONE,64),demand(Items.OAK_PLANKS,64)),1,64);
        assertEquals(2,result.emptySlotsRequired()); assertNotNull(result.problem());
        var partialOnly = ProtectedTransferCapacity.simulate(List.of(new ItemStack(Items.COBBLESTONE,64)),
                List.of(demand(Items.COBBLESTONE,64)),0,64);
        assertNotNull(partialOnly.problem(), "A partially occupied matching stack is never counted as guaranteed capacity");
    }

    @Test void reverseRemovalAndOverlappingNativePredicatesReserveEveryExtraction() {
        var result=ProtectedTransferCapacity.simulate(List.of(new ItemStack(Items.COBBLESTONE,64)),
                List.of(new ProtectedTransferCapacity.Demand(stack->true,32), demand(Items.COBBLESTONE,32)),2,64);
        assertEquals(2,result.emptySlotsRequired()); assertNull(result.problem());
        // applyToNeededItems re-matches the extracted count, which can select a different request.
        result=ProtectedTransferCapacity.simulate(List.of(new ItemStack(Items.COBBLESTONE,30)),
                List.of(new ProtectedTransferCapacity.Demand(stack->true,10),
                        new ProtectedTransferCapacity.Demand(stack->stack.getCount()>=20,5)),2,64);
        assertEquals(2,result.emptySlotsRequired());
    }

    @Test void fragmentedSuppliesReportConsolidationRatherThanImpossibleFreeSlotInstruction() {
        var supplies=new ArrayList<ItemStack>(); for(int i=0;i<16;i++)supplies.add(new ItemStack(Items.COBBLESTONE));
        var result=ProtectedTransferCapacity.simulate(supplies,List.of(demand(Items.COBBLESTONE,64)),9,64);
        assertEquals(16,result.emptySlotsRequired());
        String message=ProtectedTransferCapacity.capacityProblem(result.emptySlotsRequired(),9,9);
        assertTrue(message.contains("consolidate")); assertTrue(message.contains("builder has 9"));
    }

    @Test void unknownCapturedPredicatesAndNeededItemSubclassesNeverExecute() {
        var calls=new AtomicInteger(); var worker=mock(BuilderEntity.class);
        worker.neededItems=new ArrayList<>(List.of(new NeededItem(stack->{calls.incrementAndGet();return true;},64,true)));
        assertNotNull(ProtectedTransferCapacity.requestsProblem(worker)); assertEquals(0,calls.get());
        worker.neededItems=new ArrayList<>(List.of(new NeededItem(stack->true,64,true){
            @Override public boolean matches(ItemStack stack){calls.incrementAndGet();return true;}
        }));
        assertNotNull(ProtectedTransferCapacity.requestsProblem(worker)); assertEquals(0,calls.get());
        assertFalse(ProtectedTransferCapacity.trustedMatcher((java.util.function.Predicate<ItemStack>) stack->true));
        assertFalse(ProtectedTransferCapacity.supportedInventory(new SimpleContainer(15)), "Generic insertion cannot stand in for native slot policy");
    }

    @Test void detachedPredicateMutationFailsWithoutChangingOriginalSupplies() {
        var source=new ItemStack(Items.COBBLESTONE,64);
        assertThrows(IllegalArgumentException.class,()->ProtectedTransferCapacity.simulate(List.of(source),
                List.of(new ProtectedTransferCapacity.Demand(stack->{stack.shrink(1);return true;},64)),4,64));
        assertEquals(64,source.getCount());
    }

    @Test void overstackedSourceAndUnboundedRequestsFailBeforeMatcherEvaluation() {
        var calls=new AtomicInteger(); var request=new ProtectedTransferCapacity.Demand(stack->{calls.incrementAndGet();return true;},64);
        assertThrows(IllegalArgumentException.class,()->ProtectedTransferCapacity.simulate(List.of(new ItemStack(Items.COBBLESTONE,127)),List.of(request),8,64));
        assertEquals(0,calls.get());
        assertThrows(IllegalArgumentException.class,()->ProtectedTransferCapacity.simulate(List.of(),java.util.Collections.nCopies(9,request),8,64));
        assertThrows(IllegalArgumentException.class,()->ProtectedTransferCapacity.simulate(List.of(),List.of(new ProtectedTransferCapacity.Demand(stack->true,65)),8,64));
    }

    @Test void fullItemTagsStayOnDetachedCopiesAndNoMatchingSourceNeedsNoSpace() {
        var named=new ItemStack(Items.COBBLESTONE,12); named.getOrCreateTag().putString("qa","keep this metadata");
        var before=named.save(new net.minecraft.nbt.CompoundTag());
        var result=ProtectedTransferCapacity.simulate(List.of(named),List.of(demand(Items.OAK_PLANKS,64)),0,64);
        assertNull(result.problem()); assertEquals(0,result.emptySlotsRequired());
        assertEquals(before,named.save(new net.minecraft.nbt.CompoundTag()));
    }

    private ProtectedTransferCapacity.Demand demand(net.minecraft.world.item.Item item,int count) {
        return new ProtectedTransferCapacity.Demand(stack->stack.is(item),count);
    }
}
