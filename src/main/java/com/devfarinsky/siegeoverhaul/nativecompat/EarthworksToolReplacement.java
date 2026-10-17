package com.devfarinsky.siegeoverhaul.nativecompat;

import com.talhanation.workers.entities.BuilderEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShovelItem;

/** Read-only bounded replacement decisions. The owning supply journal fences the one native swap. */
final class EarthworksToolReplacement {
    private EarthworksToolReplacement() {}
    record Pair(int wornSlot, int freshSlot) {}
    static boolean ordinaryIron(ItemStack tool, boolean allowLastDurability) {
        if (tool == null || tool.getClass() != ItemStack.class || !tool.is(Items.IRON_SHOVEL) || tool.getCount() != 1) return false;
        int damage = tool.getDamageValue();
        if (damage < 0 || damage >= tool.getMaxDamage() - (allowLastDurability ? 0 : 1)) return false;
        var expected = new ItemStack(Items.IRON_SHOVEL); expected.setDamageValue(damage);
        return ProtectedBuilderHandMirror.sameValue(tool, expected);
    }
    /** Null means no replacement is needed. Unknown/multiple/offhand tools are not silently removed. */
    static CompoundTag replacementNeeded(BuilderEntity worker) {
        var frame = EarthworksInventoryEvidence.inventory(worker); int count = 0; ItemStack tool = null;
        for (int i = 0; i < frame.slots().size(); i++) {
            var stack = frame.slots().get(i).reference();
            if (stack.getItem() instanceof ShovelItem) {
                if (i < 5 || ++count > 1 || !ordinaryIron(stack, true)) throw EarthworksInventoryEvidence.bad("Unsupported existing shovel set");
                tool = stack;
            }
        }
        EarthworksInventoryEvidence.unchanged(frame);
        return tool != null && tool.getDamageValue() == tool.getMaxDamage() - 1 ? tool.save(new CompoundTag()).copy() : null;
    }
    static Pair pair(BuilderEntity worker, CompoundTag worn) {
        ItemStack expected = ItemStack.of(worn);
        if (!ordinaryIron(expected, true) || expected.getDamageValue() != expected.getMaxDamage() - 1)
            throw EarthworksInventoryEvidence.bad("Replacement is not bound to a last-durability ordinary tool");
        var frame = EarthworksInventoryEvidence.inventory(worker); int old = -1, fresh = -1;
        for (int i = 0; i < frame.slots().size(); i++) {
            var item = frame.slots().get(i).reference();
            if (!(item.getItem() instanceof ShovelItem)) continue;
            if (i < 5) throw EarthworksInventoryEvidence.bad("Unselectable replacement shovel");
            if (ProtectedBuilderHandMirror.sameValue(item, expected) && old < 0) old = i;
            else if (EarthworksSupplyDemand.ordinary(item, Items.IRON_SHOVEL, true) && fresh < 0) fresh = i;
            else throw EarthworksInventoryEvidence.bad("Unknown extra shovel during replacement");
        }
        if (old < 0) throw EarthworksInventoryEvidence.bad("Original worn tool disappeared without an observed return");
        EarthworksInventoryEvidence.unchanged(frame); return new Pair(old, fresh);
    }
    static void swap(BuilderEntity worker, CompoundTag worn) {
        try {
            if (worker.getClass() != BuilderEntity.class || !BuilderEntity.class.getMethod("switchMainHandItem", java.util.function.Predicate.class)
                    .getDeclaringClass().getName().equals("com.talhanation.workers.entities.AbstractWorkerEntity"))
                throw EarthworksInventoryEvidence.bad("Unsupported native hand-switch implementation");
        } catch (NoSuchMethodException unavailable) { throw new IllegalStateException("Native hand-switch capability unavailable", unavailable); }
        Pair pair = pair(worker, worn);
        if (pair.freshSlot < 6) throw EarthworksInventoryEvidence.bad("Fresh replacement is not in cargo");
        if (!worker.isAlive() || !worker.shouldWork() || worker.needsToSleep() || worker.isPassenger() || worker.isLeashed()
                || worker.getTarget() != null || worker.isFleeing || worker.needsToGetItems()
                || ProtectedInventoryCleanup.outstanding(worker.getPersistentData())) throw EarthworksInventoryEvidence.bad("Native tool swap eligibility changed");
        var before = EarthworksInventoryEvidence.inventory(worker);
        worker.switchMainHandItem(stack -> EarthworksSupplyDemand.ordinary(stack, Items.IRON_SHOVEL, true));
        EarthworksInventoryEvidence.verifyNativeSwap(worker, before, pair.freshSlot);
        if (pair(worker, worn).freshSlot != 5) throw EarthworksInventoryEvidence.bad("Native replacement did not reach the main hand");
    }
}
