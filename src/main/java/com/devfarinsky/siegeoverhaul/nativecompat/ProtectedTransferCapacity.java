package com.devfarinsky.siegeoverhaul.nativecompat;

import com.talhanation.recruits.entities.AbstractInventoryEntity;
import com.talhanation.recruits.inventory.RecruitSimpleContainer;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.entities.ai.BuilderWorkGoal;
import com.talhanation.workers.world.NeededItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** Read-only proof that native addItem cannot return a partial leftover in one guarded resupply tick. */
final class ProtectedTransferCapacity {
    static final int MAX_SLOTS = 128, MAX_REQUESTS = 8, MAX_MATCHES = 8192;
    record Demand(Predicate<ItemStack> matcher, int count) {}
    record Result(String problem, int emptySlotsRequired) {}
    private record StackState(ItemStack reference, int count, CompoundTag nbt) {}
    private record RequestState(NeededItem reference, Predicate<ItemStack> matcher, int count, boolean required, Object source) {}
    private static final class Remaining {
        final Predicate<ItemStack> matcher; int count;
        Remaining(Demand demand) { matcher = demand.matcher(); count = demand.count(); }
    }
    private ProtectedTransferCapacity() {}

    /** Never probes an unknown predicate or parses lambda names/private captured fields. */
    static boolean trustedMatcher(Object matcher) {
        if (!(matcher instanceof Predicate<?>)) return false;
        Class<?> type = matcher.getClass();
        return type.isHidden() && type.isSynthetic() && Modifier.isFinal(type.getModifiers())
                && type.getNestHost() == BuilderWorkGoal.class
                && type.getClassLoader() == BuilderWorkGoal.class.getClassLoader();
    }

    static String requestsProblem(BuilderEntity worker) {
        if (worker.neededItems == null || worker.neededItems.size() > MAX_REQUESTS)
            return "Paused: native supply request list is unsupported";
        var seen = new IdentityHashMap<NeededItem, Boolean>();
        for (NeededItem needed : worker.neededItems)
            if (needed == null || needed.getClass() != NeededItem.class || !trustedMatcher(needed.matcher)
                    || needed.count < 1 || needed.count > 64 || !needed.required || needed.sourceKey != null
                    || seen.put(needed, Boolean.TRUE) != null)
                return "Paused: native supply request provenance cannot be verified";
        return null;
    }

    /** Exact inherited insertion body; no unknown container subclass can replace its slot semantics. */
    static boolean supportedInventory(SimpleContainer inventory) {
        if (inventory == null) return false;
        Class<?> type = inventory.getClass();
        return type == RecruitSimpleContainer.class || type.isAnonymousClass()
                && type.getSuperclass() == RecruitSimpleContainer.class
                && type.getNestHost() == AbstractInventoryEntity.class
                && type.getClassLoader() == AbstractInventoryEntity.class.getClassLoader()
                && type.getDeclaredMethods().length == 0;
    }

    static String problem(BuilderEntity worker, Container source) {
        String requests = requestsProblem(worker); if (requests != null) return requests;
        SimpleContainer inventory = worker.getInventory();
        if (!supportedInventory(inventory)) return "Paused: native inventory insertion behavior is unsupported";
        var requestList = worker.neededItems;
        var needed = new ArrayList<RequestState>();
        // Capture the already-verified request references before any item/capability serialization.
        for (NeededItem request : requestList)
            needed.add(new RequestState(request, request.matcher, request.count, request.required, request.sourceKey));
        try {
            if (source == null || inventory.getContainerSize() < 6) throw new IllegalArgumentException();
            var identities = new IdentityHashMap<ItemStack, Boolean>();
            List<StackState> destination = capture(inventory, identities), supplies = capture(source, identities);
            var copies = new ArrayList<ItemStack>();
            for (StackState stack : supplies) {
                ItemStack copy = stack.reference().copy();
                if (!same(stack, copy)) throw new IllegalArgumentException("Capability-preserving copy unavailable");
                copies.add(copy);
            }
            int empty = 0;
            for (int slot = 6; slot < destination.size(); slot++) if (destination.get(slot).reference().isEmpty()) empty++;
            List<Demand> demands = needed.stream().map(n -> new Demand(n.matcher(), n.count())).toList();
            Result result = simulate(copies, demands, empty, inventory.getMaxStackSize());
            // Recheck full live values/references and native request identity/counts, not just totals.
            if (!unchanged(inventory, destination) || !unchanged(source, supplies) || worker.getInventory() != inventory
                    || worker.neededItems != requestList || worker.neededItems.size() != needed.size()) throw new IllegalArgumentException("Transfer inputs changed");
            for (int i = 0; i < needed.size(); i++) {
                var now = worker.neededItems.get(i); var before = needed.get(i);
                if (now != before.reference() || now.matcher != before.matcher() || now.count != before.count()
                        || now.required != before.required() || now.sourceKey != before.source())
                    throw new IllegalArgumentException("Native requests changed");
            }
            return capacityProblem(result.emptySlotsRequired(), empty, destination.size() - 6);
        } catch (RuntimeException | LinkageError unverified) {
            return "Paused: native transfer capacity or full item data cannot be verified";
        }
    }

    /** Native deposit merges by item only. Validate all possible cargo merge pairs, including
     * earlier cargo stacks inserted into an empty destination, without executing keep predicates. */
    static String depositProblem(BuilderEntity worker, Container destination) {
        return ordinaryTransferProblem(worker,destination,true);
    }

    /** Upkeep keeps its original one-item food/equipment/payment paths; this only verifies inputs. */
    static String upkeepProblem(BuilderEntity worker, Container source) {
        return ordinaryTransferProblem(worker,source,false);
    }

    private static String ordinaryTransferProblem(BuilderEntity worker,Container other,boolean deposit) {
        String requests=requestsProblem(worker);if(requests!=null)return requests;
        SimpleContainer inventory=worker.getInventory();
        if(!supportedInventory(inventory))return "Paused: native inventory insertion behavior is unsupported";
        var requestList=worker.neededItems;
        var requestStates=new ArrayList<RequestState>();
        for(var request:requestList)requestStates.add(new RequestState(request,request.matcher,request.count,request.required,request.sourceKey));
        try {
            var identities=new IdentityHashMap<ItemStack,Boolean>();
            var cargo=capture(inventory,identities);var external=capture(other,identities);
            // Native food/equipment paths copy source stacks. Reject capability-losing copies before
            // the original goal can use one. This does not call addItem or a matching/keep callback.
            for(var stack:external)if(!same(stack,stack.reference().copy()))throw new IllegalArgumentException();
            if(!deposit) {
                // Builders inherit IRangedRecruit. upkeepReequip ignores addItem leftovers for
                // whole arrow stacks. Reserve one empty cargo slot per possible arrow extraction;
                // food follows this loop and independently requires an empty cargo slot per item.
                int arrowStacks=0,empty=0;
                for(var stack:external)if(!stack.reference().isEmpty()
                        && stack.reference().is(net.minecraft.tags.ItemTags.ARROWS))arrowStacks++;
                for(int slot=6;slot<cargo.size();slot++)if(cargo.get(slot).reference().isEmpty())empty++;
                String capacity=capacityProblem(arrowStacks,empty,cargo.size()-6);
                if(capacity!=null)return capacity;
            }
            if(deposit) {
                var possible=new ArrayList<StackState>(external);
                for(int slot=6;slot<cargo.size();slot++) {
                    var incoming=cargo.get(slot);
                    if(incoming.reference().isEmpty() || incoming.reference().getItem() instanceof net.minecraft.world.item.ArmorItem)continue;
                    for(var target:possible)if(!target.reference().isEmpty()
                            && target.reference().is(incoming.reference().getItem())
                            && !sameItemData(incoming.nbt(),target.nbt()))
                        return "Paused: unload or separate differing item data before native deposit";
                    // Conservative: tools/food that native would retain remain candidates, avoiding
                    // extra arbitrary food-properties/keep callbacks during a read-only proof.
                    possible.add(incoming);
                }
            }
            if(worker.getInventory()!=inventory || worker.neededItems!=requestList
                    || !unchanged(inventory,cargo) || !unchanged(other,external)
                    || requestList.size()!=requestStates.size())throw new IllegalArgumentException();
            for(int i=0;i<requestStates.size();i++) {
                var now=requestList.get(i);var before=requestStates.get(i);
                if(now!=before.reference()||now.matcher!=before.matcher()||now.count!=before.count()
                        ||now.required!=before.required()||now.sourceKey!=before.source())throw new IllegalArgumentException();
            }
            return null;
        } catch(RuntimeException|LinkageError unavailable) {
            return "Paused: native transfer item data or container identity cannot be verified";
        }
    }

    static boolean sameItemData(CompoundTag first,CompoundTag second) {
        CompoundTag a=first.copy(),b=second.copy();a.remove("Count");b.remove("Count");return a.equals(b);
    }

    /** Detached arithmetic model. Production supplies only metadata-verified native predicates. */
    static Result simulate(List<ItemStack> supplies, List<Demand> demands, int emptyCargo, int inventoryLimit) {
        if (supplies == null || supplies.size() > MAX_SLOTS || demands == null || demands.size() > MAX_REQUESTS
                || emptyCargo < 0 || inventoryLimit < 1 || inventoryLimit > 64)
            throw new IllegalArgumentException("Unbounded transfer model");
        var remaining = new ArrayList<Remaining>();
        for (Demand demand : demands) {
            if (demand.matcher() == null || demand.count() < 1 || demand.count() > 64) throw new IllegalArgumentException();
            remaining.add(new Remaining(demand));
        }
        int slots = 0; int[] calls = {0};
        for (ItemStack supplied : supplies) {
            if (supplied.isEmpty()) continue;
            ItemStack inChest = supplied.copy();
            if (!ProtectedBuilderHandMirror.sameValue(supplied, inChest)
                    || inChest.getCount() < 1 || inChest.getCount() > Math.min(inventoryLimit, inChest.getMaxStackSize()))
                throw new IllegalArgumentException("Unsupported source stack");
            // Mirrors native reverse traversal and applyToNeededItems' second reverse match/removal.
            for (int j = remaining.size() - 1; j >= 0; j--) {
                Remaining need = remaining.get(j);
                if (!matches(need.matcher, inChest, calls)) continue;
                int amount = Math.min(need.count, inChest.getCount());
                ItemStack extracted = inChest.copy();
                if (!ProtectedBuilderHandMirror.sameValue(inChest, extracted)) throw new IllegalArgumentException();
                extracted.setCount(amount); inChest.shrink(amount); slots++;
                // Native RecruitSimpleContainer.addItem copies extracted; the original full extracted
                // count then goes to applyToNeededItems. Never call either mutating native method here.
                for (int k = remaining.size() - 1; k >= 0; k--) {
                    Remaining applied = remaining.get(k);
                    if (!matches(applied.matcher, extracted, calls)) continue;
                    int used = Math.min(extracted.getCount(), applied.count);
                    extracted.shrink(used);
                    if (used == applied.count) remaining.remove(k); else applied.count -= used;
                    break;
                }
                if (inChest.isEmpty()) break;
            }
        }
        return new Result(capacityProblem(slots, emptyCargo, MAX_SLOTS), slots);
    }

    static String capacityProblem(int required, int empty, int totalCargo) {
        if (required > totalCargo) return "Paused: consolidate supply stacks or use another storage source; this transfer needs "
                + required + " separate cargo slots but the builder has " + totalCargo;
        return required > empty ? "Paused: free at least " + required + " empty builder cargo slots for safe native resupply (have " + empty + ")" : null;
    }

    private static boolean matches(Predicate<ItemStack> matcher, ItemStack detached, int[] calls) {
        if (++calls[0] > MAX_MATCHES) throw new IllegalArgumentException("Native matching budget exceeded");
        int count = detached.getCount(); CompoundTag before = detached.save(new CompoundTag()).copy();
        boolean result = matcher.test(detached);
        if (detached.getCount() != count || !before.equals(detached.save(new CompoundTag())))
            throw new IllegalArgumentException("Native matcher modified its detached argument");
        return result;
    }

    private static List<StackState> capture(Container container, IdentityHashMap<ItemStack, Boolean> identities) {
        int size = container.getContainerSize(), limit = container.getMaxStackSize();
        if (size < 1 || size > MAX_SLOTS || limit < 1 || limit > 64) throw new IllegalArgumentException("Unsupported inventory size");
        List<StackState> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ItemStack stack = Objects.requireNonNull(container.getItem(i));
            if (stack.getClass() != ItemStack.class) throw new IllegalArgumentException("Unknown stack implementation");
            if (stack.getCount() < 0 || stack.getCount() > Math.min(limit, stack.getMaxStackSize())
                    || !stack.isEmpty() && identities.put(stack, Boolean.TRUE) != null)
                throw new IllegalArgumentException("Overstack or shared source/destination stack");
            result.add(new StackState(stack, stack.getCount(), stack.save(new CompoundTag()).copy()));
        }
        return List.copyOf(result);
    }
    private static boolean same(StackState before, ItemStack after) {
        return after != null && before.count() == after.getCount() && before.nbt().equals(after.save(new CompoundTag()));
    }
    private static boolean unchanged(Container container, List<StackState> before) {
        if (container.getContainerSize() != before.size()) return false;
        for (int i = 0; i < before.size(); i++) if (container.getItem(i) != before.get(i).reference()
                || !same(before.get(i), container.getItem(i))) return false;
        return true;
    }
}
