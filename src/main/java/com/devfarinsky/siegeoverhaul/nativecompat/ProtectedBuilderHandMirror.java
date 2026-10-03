package com.devfarinsky.siegeoverhaul.nativecompat;

import com.talhanation.workers.entities.BuilderEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Value-preserving repair of the pinned Recruits load-time main-hand mirror, for guarded jobs only. */
final class ProtectedBuilderHandMirror {
    private static final String PENDING = "SiegeProtectedHandRefreshPending",
            REVIEW = "SiegeProtectedHandReviewRequired", REBINDS = "SiegeProtectedHandRebinds";
    private static final String REVIEW_REASON = "Paused: protected builder inventory needs review; no item amounts were selected. Cancel remains available.";
    private ProtectedBuilderHandMirror() {}

    static void arm(CompoundTag data) { data.putBoolean(PENDING, true); }
    static boolean pending(CompoundTag data) { return data.getBoolean(PENDING); }
    static boolean reviewNeeded(CompoundTag data) { return data.getBoolean(REVIEW); }
    static int rebindCount(CompoundTag data) { return Math.max(0, data.getInt(REBINDS)); }
    static String reviewReason() { return REVIEW_REASON; }

    /** Read-only; ordinary use during first commissioning is a transient pre-payment blocker. */
    static boolean activeUse(Mob worker) {
        ItemStack useItem = worker.getUseItem();
        return worker.isUsingItem() || useItem == null || !useItem.isEmpty() || worker.getUseItemRemainingTicks() != 0;
    }

    /** Call only after the guard verifies the active durable receipt and exact companion runtime. */
    static String restore(Mob worker) {
        if (!(worker instanceof BuilderEntity builder)) return "Paused: protected builder inventory API is unavailable";
        CompoundTag data = worker.getPersistentData();
        try {
            if (activeUse(worker))
                return review(data); // Never stop use or replace an active-use stack reference.
            SimpleContainer inventory = builder.getInventory();
            return restore(data, inventory, () -> {
                if (builder.getInventory() != inventory) throw new IllegalStateException("Native inventory changed during hand binding");
                return builder.getMainHandItem();
            }, stack -> builder.setItemInHand(InteractionHand.MAIN_HAND, stack));
        } catch (RuntimeException | LinkageError unavailable) { return review(data); }
    }

    /** Testable contract around the one public native setter; never changes stack amounts itself. */
    static String restore(CompoundTag data, SimpleContainer inventory, Supplier<ItemStack> mainHand,
                          Consumer<ItemStack> nativeSetMainHand) {
        if (reviewNeeded(data)) return REVIEW_REASON; // Reload must not hide an earlier unequal pair.
        if (!pending(data)) return null;
        try {
            if (inventory == null || inventory.getContainerSize() < 6 || inventory.getContainerSize() > 128)
                return review(data);
            ItemStack slot = inventory.getItem(5), hand = mainHand.get();
            if (!sameValue(slot, hand)) return review(data);
            // SimpleContainer.setItem clamps to its maximum. Equality alone is not proof that the
            // native setter would preserve an overstack; reject before it can touch either view.
            if (slot.getCount() < 0 || slot.getCount() > Math.min(inventory.getMaxStackSize(), slot.getMaxStackSize()))
                return review(data);
            // Another slot sharing either nonempty object is not the audited load-only mirror defect.
            for (int i = 0; i < inventory.getContainerSize(); i++) if (i != 5 && !slot.isEmpty()
                    && (inventory.getItem(i) == slot || inventory.getItem(i) == hand)) return review(data);
            if (slot == hand) { data.remove(PENDING); return null; }
            var before = new ArrayList<StackSnapshot>(inventory.getContainerSize());
            for (int i = 0; i < inventory.getContainerSize(); i++) before.add(snapshot(inventory.getItem(i)));
            StackSnapshot handBefore = snapshot(hand);
            // Persist doubt before invoking native code. An exceptional/partial callback cannot become
            // an automatic retry after save/load, and this class never chooses one discrepant count.
            data.putBoolean(REVIEW, true);
            nativeSetMainHand.accept(slot);
            if (inventory.getItem(5) != slot || mainHand.get() != slot || !handBefore.equals(snapshot(mainHand.get())))
                return REVIEW_REASON;
            for (int i = 0; i < inventory.getContainerSize(); i++)
                if (!before.get(i).equals(snapshot(inventory.getItem(i)))) return REVIEW_REASON;
            data.remove(REVIEW); data.remove(PENDING);
            data.putInt(REBINDS, (int)Math.min(Integer.MAX_VALUE, (long)rebindCount(data) + 1));
            return null;
        } catch (RuntimeException | LinkageError unavailable) { return review(data); }
    }

    // ForgeCaps is stored separately from ItemStack.getTag(). Keep raw complete serialized NBT,
    // not ItemStack.copy(), so the proof does not reconstruct or discard optional capability state.
    private record StackSnapshot(Item item, int count, CompoundTag nbt) {}
    private static StackSnapshot snapshot(ItemStack stack) {
        if (stack == null) throw new IllegalArgumentException("Missing native stack");
        return new StackSnapshot(stack.getItem(), stack.getCount(), stack.save(new CompoundTag()).copy());
    }
    static boolean sameValue(ItemStack first, ItemStack second) {
        try { return snapshot(first).equals(snapshot(second)); }
        catch (RuntimeException | LinkageError unverified) { return false; }
    }
    private static String review(CompoundTag data) { data.putBoolean(REVIEW, true); return REVIEW_REASON; }
}
