package com.devfarinsky.siegeoverhaul.nativecompat;

import com.talhanation.workers.entities.BuilderEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Bounded observations of actual native inventory callbacks. Never inserts, removes or selects an amount. */
final class EarthworksInventoryEvidence {
    private EarthworksInventoryEvidence() {}
    record Stack(ItemStack reference, int count, CompoundTag data) {
        String key() { CompoundTag value = data.copy(); value.remove("Count"); return WorkersEarthworksPort.canonical(value); }
    }
    record Frame(Container container, List<Stack> slots, String hash) {
        Frame { slots = List.copyOf(slots); }
        Map<String, Integer> totals() {
            var result = new TreeMap<String, Integer>();
            for (var slot : slots) if (slot.count > 0) result.merge(slot.key(), slot.count, Math::addExact);
            return result;
        }
    }
    record Deposit(Frame inventory, Frame storage, int wornSlot, String wornKey) {}
    record DepositResult(Frame inventory, Frame storage, Map<String, Integer> returned) {
        DepositResult { returned = Map.copyOf(returned); }
    }
    static Frame capture(Container container, IdentityHashMap<ItemStack, Boolean> identities) {
        if (container == null || container.getContainerSize() < 1 || container.getContainerSize() > 128
                || container.getMaxStackSize() < 1 || container.getMaxStackSize() > 64) throw bad("Unbounded native inventory");
        var slots = new ArrayList<Stack>(); var encoded = new ListTag();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack item = container.getItem(i);
            if (item == null || item.getClass() != ItemStack.class || item.getCount() < 0
                    || item.getCount() > Math.min(container.getMaxStackSize(), item.getMaxStackSize())
                    || !item.isEmpty() && identities.put(item, true) != null) throw bad("Aliased or invalid native stack");
            var data = item.save(new CompoundTag()).copy();
            if (WorkersEarthworksPort.canonical(data).length() > 8192) throw bad("Unbounded item data");
            slots.add(new Stack(item, item.getCount(), data)); encoded.add(data.copy());
        }
        return new Frame(container, slots, digest(WorkersEarthworksPort.canonical(encoded)));
    }
    static Frame inventory(BuilderEntity worker) {
        if (!ProtectedTransferCapacity.supportedInventory(worker.getInventory()) || worker.getInventory().getContainerSize() < 6
                || ProtectedBuilderHandMirror.activeUse(worker) || ProtectedBuilderHandMirror.pending(worker.getPersistentData())
                || ProtectedBuilderHandMirror.reviewNeeded(worker.getPersistentData())
                || worker.getInventory().getItem(5) != worker.getMainHandItem()) throw bad("Unverified native hand/inventory");
        return capture(worker.getInventory(), new IdentityHashMap<>());
    }
    static void unchanged(Frame before) {
        if (before.container.getContainerSize() != before.slots.size()) throw bad("Inventory size changed");
        for (int i = 0; i < before.slots.size(); i++) {
            var old = before.slots.get(i); ItemStack now = before.container.getItem(i);
            if (now != old.reference || now.getCount() != old.count || !old.data.equals(now.save(new CompoundTag())))
                throw bad("Inventory changed during preflight");
        }
    }
    static Deposit beforeDeposit(BuilderEntity worker, Container storage, CompoundTag worn) {
        Frame inventory = inventory(worker);
        var identities = new IdentityHashMap<ItemStack, Boolean>();
        inventory = capture(worker.getInventory(), identities); Frame destination = capture(storage, identities);
        for (var frame : List.of(inventory, destination)) for (var stack : frame.slots)
            if (!stack.reference.isEmpty() && stack.reference.getMaxStackSize() > storage.getMaxStackSize())
                throw bad("Native deposit could exceed the destination stack limit");
        int wornSlot = -1; String wornKey = "";
        if (worn != null) {
            ItemStack expected = ItemStack.of(worn); wornKey = key(worn);
            for (int i = 0; i < inventory.slots.size(); i++) if (inventory.slots.get(i).count > 0 && inventory.slots.get(i).key().equals(wornKey)) {
                if (i < 6 || wornSlot >= 0 || inventory.slots.get(i).count != 1
                        || !ProtectedBuilderHandMirror.sameValue(expected, inventory.slots.get(i).reference)) throw bad("Worn tool return identity changed");
                wornSlot = i;
            }
            if (wornSlot < 6) throw bad("The authenticated worn tool is not in cargo");
        }
        unchanged(inventory); unchanged(destination);
        return new Deposit(inventory, destination, wornSlot, wornKey);
    }
    static DepositResult afterDeposit(BuilderEntity worker, Container storage, Deposit before) {
        if (worker.getInventory() != before.inventory.container || storage != before.storage.container)
            throw bad("Native deposit inventories were replaced");
        var identities = new IdentityHashMap<ItemStack, Boolean>();
        Frame inventory = capture(worker.getInventory(), identities); Frame destination = capture(storage, identities);
        if (worker.getInventory().getItem(5) != worker.getMainHandItem() || inventory.slots.size() != before.inventory.slots.size()
                || destination.slots.size() != before.storage.slots.size()) throw bad("Native deposit changed hand/size");
        for (int i = 0; i < inventory.slots.size(); i++) {
            var old = before.inventory.slots.get(i); var now = inventory.slots.get(i);
            if (i < 6) {
                if (old.reference != now.reference || old.count != now.count || !old.data.equals(now.data)) throw bad("Deposit changed equipment");
            } else if (now.count > old.count || now.count > 0 && (old.reference != now.reference || !old.key().equals(now.key())))
                throw bad("Deposit manufactured or replaced cargo");
        }
        for (int i = 0; i < destination.slots.size(); i++) {
            var old = before.storage.slots.get(i); var now = destination.slots.get(i);
            if (now.count < old.count || old.count > 0 && (now.reference != old.reference || !old.key().equals(now.key())))
                throw bad("Deposit removed or replaced storage stock");
        }
        var returned = subtract(before.inventory.totals(), inventory.totals());
        if (!returned.equals(subtract(destination.totals(), before.storage.totals()))) throw bad("Native deposit did not conserve full item values");
        unchanged(inventory); unchanged(destination);
        return new DepositResult(inventory, destination, returned);
    }
    static Map<String, Integer> subtract(Map<String, Integer> greater, Map<String, Integer> lesser) {
        var result = new TreeMap<>(greater);
        lesser.forEach((key, count) -> {
            int next = result.getOrDefault(key, 0) - count;
            if (next < 0) throw bad("Inventory delta has an unexpected direction");
            if (next == 0) result.remove(key); else result.put(key, next);
        });
        return result;
    }
    static void verifyNativeSwap(BuilderEntity worker, Frame before, int cargoSlot) {
        if (worker.getInventory() != before.container || cargoSlot < 6 || cargoSlot >= before.slots.size()) throw bad("Native swap scope changed");
        Frame after = inventory(worker);
        if (!before.totals().equals(after.totals())) throw bad("Native swap changed full item totals");
        for (int i = 0; i < before.slots.size(); i++) {
            int expected = i == 5 ? cargoSlot : i == cargoSlot ? 5 : i;
            var old = before.slots.get(expected); var now = after.slots.get(i);
            if (old.reference != now.reference || old.count != now.count || !old.data.equals(now.data)) throw bad("Native swap changed an unexpected slot");
        }
        unchanged(after);
    }
    static String key(CompoundTag data) { var copy = data.copy(); copy.remove("Count"); return WorkersEarthworksPort.canonical(copy); }
    static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    static IllegalStateException bad(String reason) { return new IllegalStateException(reason); }
}
