package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksManifest;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.world.NeededItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShovelItem;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/** One exact active-step demand, served only by the original native storage goal. No stock mutators. */
final class EarthworksSupplyDemand {
    static final String KEY = "SiegeEarthworksSupplyV1";
    static final int MAX_RECEIPTS = 512;
    private static final int RETRY_TICKS = 1200;
    private static final Set<String> KEYS = Set.of("Version", "Scope", "Request", "Item", "State", "RetryAfter", "Receipts");
    private static final Set<String> V2_KEYS = Set.of("Version", "Scope", "Request", "Item", "State", "RetryAfter", "Receipts", "Operations", "Tool", "Resume");
    private static final Set<String> IN_FLIGHT = Set.of("TRANSFER", "DEPOSIT", "SWAP");
    private static final Set<String> RECEIPT_KEYS = Set.of("Request", "Scope", "Item", "GameTime", "SourceCells", "InventoryBefore", "InventoryAfter", "SourceBefore", "SourceAfter");
    private static final Set<String> SCOPE_KEYS = Set.of("Project", "Generation", "Area", "Manifest", "Binding", "Step");
    private EarthworksSupplyDemand() {}

    record Scope(UUID project, long generation, UUID area, String manifest, String binding, int step) {
        Scope {
            if (project == null || area == null || project.equals(new UUID(0, 0)) || area.equals(new UUID(0, 0))
                    || generation < 1 || !digest(manifest) || !digest(binding) || step < 0 || step >= NativeEarthworksAdapter.MAX_STEPS)
                throw invalid("Invalid exact supply scope");
        }
        static Scope from(NativeEarthworksJobs.InventoryLease lease) {
            return new Scope(lease.project(), lease.generation(), lease.area(), lease.manifestHash(), lease.bindingHash(), lease.nextStep());
        }
        CompoundTag save() {
            var tag = new CompoundTag(); tag.putUUID("Project", project); tag.putLong("Generation", generation);
            tag.putUUID("Area", area); tag.putString("Manifest", manifest); tag.putString("Binding", binding); tag.putInt("Step", step); return tag;
        }
        static Scope read(CompoundTag tag) {
            if (!tag.getAllKeys().equals(SCOPE_KEYS) || !tag.hasUUID("Project") || !tag.hasUUID("Area")
                    || !tag.contains("Generation", Tag.TAG_LONG) || !tag.contains("Step", Tag.TAG_INT)
                    || !tag.contains("Manifest", Tag.TAG_STRING) || !tag.contains("Binding", Tag.TAG_STRING)) throw invalid("Malformed supply scope");
            return new Scope(tag.getUUID("Project"), tag.getLong("Generation"), tag.getUUID("Area"), tag.getString("Manifest"), tag.getString("Binding"), tag.getInt("Step"));
        }
        boolean sameJob(Scope other) {
            return project.equals(other.project) && generation == other.generation && area.equals(other.area)
                    && manifest.equals(other.manifest) && binding.equals(other.binding);
        }
    }
    /** Private final matcher: no caller-supplied predicate, subclass or mutable expected stack. */
    private static final class ExactMatcher implements Predicate<ItemStack> {
        private final Scope scope;
        private final UUID request;
        private final Item item;
        private final NeededItem nativeRequest;
        private ExactMatcher(Scope scope, UUID request, Item item) {
            this.scope = scope; this.request = request; this.item = item;
            this.nativeRequest = new NeededItem(this, 1, true);
        }
        @Override public boolean test(ItemStack stack) { return ordinary(stack, item, item == Items.IRON_SHOVEL); }
    }
    private record Active(Scope scope, UUID request, Item item, String state, long retryAfter, ListTag receipts,
                          ListTag operations, CompoundTag tool, String resume) {
        Active(Scope scope, UUID request, Item item, String state, long retryAfter, ListTag receipts) {
            this(scope, request, item, state, retryAfter, receipts, new ListTag(), new CompoundTag(), "");
        }
        CompoundTag save() {
            var tag = new CompoundTag(); tag.putInt("Version", 2); tag.put("Scope", scope.save()); tag.putUUID("Request", request);
            tag.putString("Item", BuiltInRegistries.ITEM.getKey(item).toString()); tag.putString("State", state);
            tag.putLong("RetryAfter", retryAfter); tag.put("Receipts", receipts.copy()); tag.put("Operations", operations.copy());
            tag.put("Tool", tool.copy()); tag.putString("Resume", resume); return tag;
        }
        Active state(String next) { return new Active(scope, request, item, next, retryAfter, receipts, operations, tool, ""); }
        Active observed(String next, ListTag observed, CompoundTag nextTool) {
            return new Active(scope, request, item, next, retryAfter, receipts, observed, nextTool, "");
        }
    }
    private static Active read(BuilderEntity worker) {
        var data = worker.getPersistentData();
        if (!data.contains(KEY)) {
            if (!"".equals(NativeEarthworksJobs.supplyDigest(worker))) throw invalid("World supply history has no matching entity snapshot");
            return null;
        }
        if (!data.contains(KEY, Tag.TAG_COMPOUND)) throw invalid("Malformed supply history");
        var tag = data.getCompound(KEY);
        if (!hash(tag).equals(NativeEarthworksJobs.supplyDigest(worker))) throw invalid("Entity/world supply snapshots disagree");
        int version = tag.getInt("Version");
        if (!tag.getAllKeys().equals(version == 1 ? KEYS : V2_KEYS) || !tag.contains("Version", Tag.TAG_INT) || version < 1 || version > 2
                || !tag.contains("Scope", Tag.TAG_COMPOUND) || !tag.hasUUID("Request") || !tag.contains("Item", Tag.TAG_STRING)
                || !tag.contains("State", Tag.TAG_STRING) || !tag.contains("RetryAfter", Tag.TAG_LONG)
                || !tag.contains("Receipts", Tag.TAG_LIST)) throw invalid("Malformed supply history");
        Item item = item(tag.getString("Item")); String state = tag.getString("State");
        if (version == 1 && !Set.of("REQUESTED", "TRANSFER", "DELIVERED", "AVAILABLE").contains(state)) throw invalid("Unsupported legacy supply state");
        var receipts = tag.getList("Receipts", Tag.TAG_COMPOUND);
        if (!Set.of("IDLE", "REQUESTED", "TRANSFER", "DELIVERED", "AVAILABLE", "DEPOSIT", "RETURNED", "SWAP").contains(state)
                || receipts.size() > MAX_RECEIPTS || ((ListTag)tag.get("Receipts")).size() != receipts.size()) throw invalid("Unsupported supply history");
        Scope scope = Scope.read(tag.getCompound("Scope")); UUID request = tag.getUUID("Request");
        if (request.equals(new UUID(0, 0))) throw invalid("Missing supply request identity");
        var seen = new java.util.HashSet<UUID>();
        for (Tag value : receipts) {
            var receipt = (CompoundTag)value;
            if (!receipt.getAllKeys().equals(RECEIPT_KEYS) || !receipt.hasUUID("Request")
                    || !receipt.contains("Scope", Tag.TAG_COMPOUND) || !receipt.contains("Item", Tag.TAG_COMPOUND)
                    || !receipt.contains("GameTime", Tag.TAG_LONG) || !receipt.contains("SourceCells", Tag.TAG_LONG_ARRAY)
                    || receipt.getLongArray("SourceCells").length < 1 || receipt.getLongArray("SourceCells").length > 2
                    || !seen.add(receipt.getUUID("Request")) || WorkersEarthworksPort.canonical(receipt).length() > 16384)
                throw invalid("Malformed observed supply receipt");
            Scope original = Scope.read(receipt.getCompound("Scope"));
            if (!scope.sameJob(original) || original.step > scope.step || receipt.getCompound("Item").getByte("Count") != 1)
                throw invalid("Foreign supply receipt");
            for (String key : List.of("InventoryBefore", "InventoryAfter", "SourceBefore", "SourceAfter"))
                if (!receipt.contains(key, Tag.TAG_STRING) || !digest(receipt.getString(key))) throw invalid("Incomplete supply observation");
        }
        if (state.equals("DELIVERED") && !seen.contains(request)) throw invalid("Delivery lacks its observed receipt");
        ListTag operations = new ListTag(); CompoundTag tool = new CompoundTag(); String resume = "";
        if (version == 2) {
            if (!tag.contains("Operations", Tag.TAG_LIST) || !tag.contains("Tool", Tag.TAG_COMPOUND) || !tag.contains("Resume", Tag.TAG_STRING))
                throw invalid("Incomplete native lifecycle evidence");
            operations = tag.getList("Operations", Tag.TAG_COMPOUND).copy();
            if (((ListTag)tag.get("Operations")).size() != operations.size()) throw invalid("Malformed operation history");
            EarthworksSupplyOperations.validate(operations, scope); tool = tag.getCompound("Tool").copy(); resume = tag.getString("Resume");
            if (!tool.isEmpty()) {
                if (!tool.getAllKeys().equals(Set.of("Phase", "Worn")) || !tool.contains("Phase", Tag.TAG_STRING) || !tool.contains("Worn", Tag.TAG_COMPOUND)
                        || !Set.of("FETCH", "RETURN", "DONE").contains(tool.getString("Phase")) || item != Items.IRON_SHOVEL
                        || !EarthworksToolReplacement.ordinaryIron(ItemStack.of(tool.getCompound("Worn")), true)
                        || ItemStack.of(tool.getCompound("Worn")).getDamageValue() != new ItemStack(Items.IRON_SHOVEL).getMaxDamage() - 1)
                    throw invalid("Malformed native tool lifecycle");
            }
            if (state.equals("DEPOSIT") ? !Set.of("IDLE", "REQUESTED", "DELIVERED", "AVAILABLE", "RETURNED").contains(resume) : !resume.isEmpty())
                throw invalid("Unverified deposit continuation");
        }
        return new Active(scope, request, item, state, tag.getLong("RetryAfter"), receipts.copy(), operations, tool, resume);
    }
    private static void save(BuilderEntity worker, Active active) {
        var data = worker.getPersistentData();
        String before = data.contains(KEY, Tag.TAG_COMPOUND) ? hash(data.getCompound(KEY)) : "";
        if (data.contains(KEY) && !data.contains(KEY, Tag.TAG_COMPOUND)) throw invalid("Malformed supply snapshot");
        CompoundTag next = active.save(); String nextDigest = hash(next);
        // Cross-file saves are not atomic. Mismatched world/entity values pause after reload; neither side wins.
        if (!NativeEarthworksJobs.compareSupplyDigest(worker, before, nextDigest)) throw invalid("World supply comparison failed");
        data.put(KEY, next);
        if (!nextDigest.equals(NativeEarthworksJobs.supplyDigest(worker)) || !next.equals(data.getCompound(KEY)))
            throw invalid("Supply snapshot was not retained");
    }

    static String requestsProblem(BuilderEntity worker) {
        try {
            var lease = NativeEarthworksJobs.inventoryLease(worker);
            if (lease == null || NativeConstructionGuard.hasProtectedReceipt(worker)) throw invalid("No exact new-job lease");
            if (worker.neededItems == null || worker.neededItems.size() > 1) throw invalid("Mixed native requests");
            Active active = read(worker);
            if (active != null && (!active.scope.sameJob(Scope.from(lease)) || IN_FLIGHT.contains(active.state) || active.scope.step > lease.nextStep()
                    || active.scope.step != lease.nextStep() && (active.state.equals("REQUESTED")
                        || !active.tool.isEmpty() && !active.tool.getString("Phase").equals("DONE")))) throw invalid("Unresolved supply transaction");
            if (worker.neededItems.isEmpty()) return null;
            var need = worker.neededItems.get(0);
            if (active == null || !active.scope.equals(Scope.from(lease)) || !active.state.equals("REQUESTED")
                    || need == null || need.getClass() != NeededItem.class || need.count != 1 || !need.required || need.sourceKey != null
                    || !(need.matcher instanceof ExactMatcher matcher) || !matcher.scope.equals(active.scope)
                    || need != matcher.nativeRequest || !matcher.request.equals(active.request) || matcher.item != active.item || active.item != requiredItem(lease.step()))
                throw invalid("Unverified exact supply request");
            return null;
        } catch (RuntimeException | LinkageError unavailable) { return "Paused: exact earthworks supply provenance cannot be verified"; }
    }

    static boolean prepare(BuilderEntity worker, PerimeterEarthworksManifest.Step step) {
        var lease = NativeEarthworksJobs.inventoryLease(worker);
        if (lease == null || !lease.step().equals(step)) throw invalid("Exact supply step changed");
        var scope = Scope.from(lease); Item item = requiredItem(step);
        String problem = requestsProblem(worker); if (problem != null) throw invalid(problem);
        Active active = read(worker);
        if (active != null && !active.tool.isEmpty() && !active.tool.getString("Phase").equals("DONE")) {
            if (!active.scope.equals(scope) || item != Items.IRON_SHOVEL) throw invalid("Tool lifecycle escaped its exact step");
            return prepareTool(worker, active);
        }
        if (item == Items.IRON_SHOVEL) {
            CompoundTag worn = EarthworksToolReplacement.replacementNeeded(worker);
            if (worn != null) {
                if (!worker.neededItems.isEmpty() || ProtectedInventoryCleanup.outstanding(worker.getPersistentData())) return false;
                var tool = new CompoundTag(); tool.putString("Phase", "FETCH"); tool.put("Worn", worn);
                active = new Active(scope, UUID.randomUUID(), item, "IDLE", 0, active == null ? new ListTag() : active.receipts,
                        active == null ? new ListTag() : active.operations, tool, "");
                save(worker, active); return prepareTool(worker, active);
            }
        }
        boolean ready = selectable(worker, item);
        if (active != null && active.scope.equals(scope) && active.item != item) throw invalid("Supply item changed within step");
        if (active != null && active.scope.equals(scope) && Set.of("DELIVERED", "AVAILABLE").contains(active.state)) {
            if (!ready) throw invalid("Previously observed supply was removed; no replacement delivery was issued");
            return worker.neededItems.isEmpty();
        }
        if (ready) {
            // Current actual inventory can include ordinary player delivery/pickup. It is not a chest-transfer receipt.
            if (!worker.neededItems.isEmpty()) return false;
            if (active != null && active.scope.equals(scope)) save(worker, active.state("AVAILABLE"));
            return true;
        }
        if (!worker.neededItems.isEmpty()) return false;
        long now = worker.level().getGameTime();
        if (active != null && active.scope.equals(scope) && now < active.retryAfter) return false;
        if (ProtectedInventoryCleanup.outstanding(worker.getPersistentData())) return false;
        if (active != null && !active.scope.equals(scope) && (active.scope.step >= scope.step || active.state.equals("REQUESTED")))
            throw invalid("Stale or unresolved prior supply step");
        ListTag receipts = active == null ? new ListTag() : active.receipts;
        if (receipts.size() >= MAX_RECEIPTS) throw invalid("Supply receipt history is full");
        UUID request = active != null && active.scope.equals(scope) && active.state.equals("REQUESTED") ? active.request : UUID.randomUUID();
        active = new Active(scope, request, item, "REQUESTED", Math.addExact(now, RETRY_TICKS), receipts,
                active == null ? new ListTag() : active.operations, new CompoundTag(), "");
        // Retain request provenance before publishing the native request. An exception never grants stock.
        save(worker, active);
        worker.neededItems.add(new ExactMatcher(scope, request, item).nativeRequest);
        if (requestsProblem(worker) != null) throw invalid("Native request publication was not retained");
        return false;
    }

    private static boolean satisfied(BuilderEntity worker, Active active) {
        return !active.tool.isEmpty() && active.tool.getString("Phase").equals("FETCH")
                ? EarthworksToolReplacement.pair(worker, active.tool.getCompound("Worn")).freshSlot() >= 5
                : selectable(worker, active.item);
    }
    private static boolean prepareTool(BuilderEntity worker, Active active) {
        CompoundTag worn = active.tool.getCompound("Worn"); var pair = EarthworksToolReplacement.pair(worker, worn);
        if (active.tool.getString("Phase").equals("FETCH")) {
            if (pair.freshSlot() < 0) {
                if (Set.of("DELIVERED", "AVAILABLE").contains(active.state)) throw invalid("Delivered replacement disappeared without an observed native return");
                if (!worker.neededItems.isEmpty() || ProtectedInventoryCleanup.outstanding(worker.getPersistentData())
                        || active.state.equals("REQUESTED") && worker.level().getGameTime() < active.retryAfter) return false;
                if (active.receipts.size() >= MAX_RECEIPTS) throw invalid("Supply receipt history is full");
                UUID request = active.state.equals("REQUESTED") ? active.request : UUID.randomUUID();
                active = new Active(active.scope, request, active.item, "REQUESTED", Math.addExact(worker.level().getGameTime(), RETRY_TICKS),
                        active.receipts, active.operations, active.tool, "");
                save(worker, active); worker.neededItems.add(new ExactMatcher(active.scope, request, active.item).nativeRequest);
                if (requestsProblem(worker) != null) throw invalid("Replacement request was not retained");
                return false;
            }
            if (!worker.neededItems.isEmpty()) { reconcileAvailable(worker); active = read(worker); }
            if (ProtectedInventoryCleanup.outstanding(worker.getPersistentData())) return false;
            if (!nativeMaintenanceEligible(worker)) return false;
            var before = EarthworksInventoryEvidence.inventory(worker);
            String kind = pair.freshSlot() == 5 ? "OBSERVED_HAND" : "SWAP";
            var reservation = EarthworksSupplyOperations.reserve(active.operations, active.scope, active.request, kind,
                    worker.level().getGameTime(), Set.of(), before, "");
            save(worker, active.state("SWAP")); // Unknown/exceptional swaps never auto-replay.
            try {
                requireLease(worker, active.scope);
                String authority = EarthworksInventoryAccess.problem(worker, Set.of());
                if (authority != null) throw invalid(authority);
                EarthworksInventoryEvidence.unchanged(before);
                if (pair.freshSlot() != 5) {
                    EarthworksToolReplacement.swap(worker, worn);
                    EarthworksInventoryEvidence.verifyNativeSwap(worker, before, pair.freshSlot());
                } else EarthworksInventoryEvidence.unchanged(before);
                var after = EarthworksInventoryEvidence.inventory(worker);
                var operations = reservation.observe(after.hash(), "", Map.of());
                CompoundTag tool = active.tool.copy(); tool.putString("Phase", "RETURN");
                active = new Active(active.scope, active.request, active.item, "AVAILABLE", 0, active.receipts,
                        operations, tool, "");
                save(worker, active);
            } catch (RuntimeException | LinkageError uncertain) {
                ProtectedBuilderHandMirror.requireInventoryReview(worker.getPersistentData()); throw uncertain;
            }
        }
        // Only the native deposit goal moves the worn tool out of cargo. No raw item edits or synthetic refunds.
        pair = EarthworksToolReplacement.pair(worker, active.tool.getCompound("Worn"));
        if (!active.tool.getString("Phase").equals("RETURN") || pair.freshSlot() != 5 || pair.wornSlot() < 6)
            throw invalid("Tool return no longer has the exact fresh hand and worn cargo pair");
        if (nativeMaintenanceEligible(worker) && worker.level().getGameTime() >= active.retryAfter) worker.forcedDeposit = true;
        return false;
    }
    private static boolean nativeMaintenanceEligible(BuilderEntity worker) {
        return worker.isAlive() && worker.shouldWork() && !worker.needsToSleep() && !worker.isPassenger() && !worker.isLeashed()
                && worker.getTarget() == null && !worker.isFleeing && !ProtectedBuilderHandMirror.activeUse(worker)
                && worker.neededItems.isEmpty() && !ProtectedInventoryCleanup.outstanding(worker.getPersistentData());
    }
    private static void requireLease(BuilderEntity worker, Scope scope) {
        var lease = NativeEarthworksJobs.inventoryLease(worker);
        if (lease == null || !scope.equals(Scope.from(lease))) throw invalid("Native inventory operation lease changed");
    }

    static final class DepositTransfer {
        private final Active active;
        private final EarthworksInventoryEvidence.Deposit before;
        private final Set<BlockPos> cells;
        private final List<NeededItem> requests;
        private final NeededItem request;
        private final EarthworksSupplyOperations.Reservation reservation;
        private DepositTransfer(Active active, EarthworksInventoryEvidence.Deposit before, Set<BlockPos> cells, List<NeededItem> requests,
                                EarthworksSupplyOperations.Reservation reservation) {
            this.active = active; this.before = before; this.cells = Set.copyOf(cells); this.requests = requests;
            this.request = requests.isEmpty() ? null : requests.get(0); this.reservation=reservation;
        }
    }
    static DepositTransfer beforeDeposit(BuilderEntity worker, Container destination, Set<BlockPos> cells) {
        if (requestsProblem(worker) != null || cells == null || cells.isEmpty() || cells.size() > 2) throw invalid("Unverified native deposit authority");
        var lease = NativeEarthworksJobs.inventoryLease(worker); Scope scope = Scope.from(Objects.requireNonNull(lease));
        Active active = read(worker);
        if (active == null || !active.scope.equals(scope)) {
            active = new Active(scope, UUID.randomUUID(), requiredItem(lease.step()), "IDLE", 0, active == null ? new ListTag() : active.receipts,
                    active == null ? new ListTag() : active.operations, new CompoundTag(), "");
            save(worker, active);
        }
        CompoundTag worn = null;
        if (!active.tool.isEmpty() && active.tool.getString("Phase").equals("RETURN")) {
            var pair = EarthworksToolReplacement.pair(worker, active.tool.getCompound("Worn"));
            if (pair.freshSlot() != 5 || pair.wornSlot() < 6) throw invalid("Worn tool return was changed by an owner edit");
            worn = active.tool.getCompound("Worn");
        }
        var before = EarthworksInventoryEvidence.beforeDeposit(worker, destination, worn);
        var reservation = EarthworksSupplyOperations.reserve(active.operations, active.scope, active.request, "DEPOSIT",
                worker.level().getGameTime(), cells, before.inventory(), before.storage().hash());
        var operation = new DepositTransfer(active, before, cells, worker.neededItems, reservation);
        save(worker, new Active(active.scope, active.request, active.item, "DEPOSIT", active.retryAfter, active.receipts,
                active.operations, active.tool, active.state));
        return operation;
    }
    static void afterDeposit(BuilderEntity worker, Container destination, DepositTransfer transfer) {
        Active fenced = read(worker); Active active = transfer.active;
        if (fenced == null || !fenced.state.equals("DEPOSIT") || !fenced.request.equals(active.request)
                || !fenced.scope.equals(active.scope) || !fenced.resume.equals(active.state) || worker.neededItems != transfer.requests
                || (transfer.request == null ? !worker.neededItems.isEmpty() : requestsWithoutFenceProblem(worker, active) != null))
            throw invalid("Native deposit fence or requests changed");
        requireLease(worker, active.scope);
        var observed = EarthworksInventoryEvidence.afterDeposit(worker, destination, transfer.before);
        ListTag operations = active.operations;
        if (!observed.returned().isEmpty()) {
            operations = transfer.reservation.observe(observed.inventory().hash(), observed.storage().hash(), observed.returned());
        }
        String state = active.state; CompoundTag tool = active.tool.copy(); long retry = active.retryAfter;
        if (!tool.isEmpty() && tool.getString("Phase").equals("RETURN")) {
            String wornKey = transfer.before.wornKey();
            if (!observed.inventory().totals().containsKey(wornKey)) {
                if (observed.returned().getOrDefault(wornKey, 0) != 1 || !ordinary(worker.getMainHandItem(), Items.IRON_SHOVEL, true))
                    throw invalid("Worn tool disappeared without its exact storage return");
                tool.putString("Phase", "DONE"); state = "AVAILABLE"; retry = 0;
                // This also rejects a newly introduced third shovel before any later mining callback.
                if (!selectable(worker, Items.IRON_SHOVEL)) throw invalid("Replacement hand is not safely selectable");
            } else retry = Math.addExact(worker.level().getGameTime(), RETRY_TICKS);
        } else {
            String itemKey = EarthworksInventoryEvidence.key(new ItemStack(active.item).save(new CompoundTag()));
            if (transfer.before.inventory().totals().getOrDefault(itemKey, 0) > 0 && !observed.inventory().totals().containsKey(itemKey)
                    && observed.returned().getOrDefault(itemKey, 0) > 0) {
                state = active.state.equals("REQUESTED") ? "REQUESTED" : "RETURNED"; retry = 0;
            }
        }
        save(worker, new Active(active.scope, active.request, active.item, state, retry, active.receipts, operations, tool, ""));
    }

    /** A real player/pickup delivery may satisfy a pending demand while the worker is travelling.
     * Remove only our one authenticated request, never inventory or an unrelated native request. */
    static boolean reconcileAvailable(BuilderEntity worker) {
        if (requestsProblem(worker) != null) throw invalid("Unverified pending supply");
        Active active = read(worker);
        if (active == null || !active.state.equals("REQUESTED") || worker.neededItems.isEmpty()) return false;
        if (!satisfied(worker, active)) return false;
        NeededItem request = worker.neededItems.get(0);
        // A failed publication leaves REQUESTED and cannot silently issue a second delivery.
        save(worker, active.state("AVAILABLE"));
        if (worker.neededItems.size() != 1 || worker.neededItems.get(0) != request) throw invalid("Pending supply changed");
        worker.neededItems.remove(0);
        return true;
    }

    private static Item requiredItem(PerimeterEarthworksManifest.Step step) {
        if (step.kind() == PerimeterEarthworksManifest.Kind.CUT) return Items.IRON_SHOVEL;
        Item item = step.after().getBlock().asItem();
        if (!supported(item) || item == Items.IRON_SHOVEL) throw invalid("Unsupported exact material demand");
        return item;
    }
    private static Item item(String id) {
        for (Item item : new Item[]{Items.DIRT, Items.COBBLESTONE, Items.STONE_BRICKS, Items.OAK_PLANKS, Items.IRON_SHOVEL})
            if (BuiltInRegistries.ITEM.getKey(item).toString().equals(id)) return item;
        throw invalid("Unsupported exact supply item");
    }
    private static boolean supported(Item item) {
        return item == Items.DIRT || item == Items.COBBLESTONE || item == Items.STONE_BRICKS || item == Items.OAK_PLANKS || item == Items.IRON_SHOVEL;
    }
    /** Bounded first slice: ordinary vanilla metadata only; full values are still observed for every stack. */
    static boolean ordinary(ItemStack stack, Item item, boolean freshTool) {
        if (stack == null || stack.getClass() != ItemStack.class || stack.isEmpty() || !stack.is(item)
                || stack.getCount() < 1 || stack.getCount() > stack.getMaxStackSize()) return false;
        var data = stack.save(new CompoundTag()); data.remove("Count");
        var expectedStack = new ItemStack(item);
        if (item == Items.IRON_SHOVEL && !freshTool) {
            int damage = stack.getDamageValue();
            if (damage < 0 || damage >= stack.getMaxDamage() - 1) return false;
            expectedStack.setDamageValue(damage);
        }
        var expected = expectedStack.save(new CompoundTag()); expected.remove("Count");
        return data.equals(expected);
    }
    static boolean selectable(BuilderEntity worker, Item item) {
        SimpleContainer inventory = worker.getInventory();
        if (!ProtectedTransferCapacity.supportedInventory(inventory) || inventory.getContainerSize() < 6 || inventory.getContainerSize() > 128)
            throw invalid("Unsupported native inventory");
        if (ProtectedBuilderHandMirror.activeUse(worker) || ProtectedBuilderHandMirror.pending(worker.getPersistentData())
                || ProtectedBuilderHandMirror.reviewNeeded(worker.getPersistentData()) || inventory.getItem(5) != worker.getMainHandItem())
            throw invalid("Unverified native hand");
        var identities = new IdentityHashMap<ItemStack, Boolean>(); capture(inventory, identities);
        if (item == Items.IRON_SHOVEL) {
            ItemStack selected = worker.getMainHandItem(); int shovels = 0;
            if (!(selected.getItem() instanceof ShovelItem)) {
                selected = ItemStack.EMPTY;
                for (int i = 6; i < inventory.getContainerSize(); i++) if (inventory.getItem(i).getItem() instanceof ShovelItem) { selected = inventory.getItem(i); break; }
            }
            for (int i = 0; i < inventory.getContainerSize(); i++) if (inventory.getItem(i).getItem() instanceof ShovelItem) shovels += inventory.getItem(i).getCount();
            if (shovels > 0 && (shovels != 1 || !ordinary(selected, item, false))) throw invalid("Unsupported shovel selection or durability; supply was not duplicated");
            return shovels == 1;
        }
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.is(item)) {
                if (i < 5 || !ordinary(stack, item, false)) throw invalid("Native first matching material is unselectable or has unreviewed metadata");
                return true;
            }
        }
        return false;
    }

    private record StackValue(ItemStack reference, int count, CompoundTag data) {}
    static final class Transfer {
        private final Active active;
        private final SimpleContainer inventory;
        private final Container source;
        private final List<NeededItem> requests;
        private final List<StackValue> cargo, supplies;
        private final Set<BlockPos> cells;
        private final int sourceSlot, destinationSlot;
        private Transfer(Active active, SimpleContainer inventory, Container source, List<NeededItem> requests, List<StackValue> cargo,
                         List<StackValue> supplies, Set<BlockPos> cells, int sourceSlot, int destinationSlot) {
            this.active = active; this.inventory = inventory; this.source = source; this.requests = requests; this.cargo = cargo;
            this.supplies = supplies; this.cells = Set.copyOf(cells); this.sourceSlot = sourceSlot; this.destinationSlot = destinationSlot;
        }
    }
    static Transfer beforeTransfer(BuilderEntity worker, Container source, Set<BlockPos> cells) {
        if (requestsProblem(worker) != null || worker.neededItems.size() != 1) throw invalid("Missing exact native demand");
        Active active = Objects.requireNonNull(read(worker));
        if (satisfied(worker, active)) throw invalid("Requested item already exists; do not deliver it twice");
        var identities = new IdentityHashMap<ItemStack, Boolean>();
        var cargo = capture(worker.getInventory(), identities); var supplies = capture(source, identities);
        int slot = -1;
        for (int i = 0; i < supplies.size(); i++) if (ordinary(supplies.get(i).reference, active.item, active.item == Items.IRON_SHOVEL)) { slot = i; break; }
        if (slot >= 0) {
            ItemStack incoming = supplies.get(slot).reference;
            for (var carried : cargo) if (!carried.reference.isEmpty() && ItemStack.isSameItemSameTags(carried.reference, incoming)
                    && !ProtectedTransferCapacity.sameItemData(carried.data, supplies.get(slot).data)) throw invalid("Native merge would lose full item metadata");
        }
        if (cells == null || cells.isEmpty() || cells.size() > 2) throw invalid("Unknown supply source identity");
        int destinationSlot = -1;
        for (int i = 6; i < cargo.size(); i++) if (cargo.get(i).reference.isEmpty()) { destinationSlot = i; break; }
        if (slot >= 0 && destinationSlot < 0) throw invalid("No finite cargo slot for exact delivery");
        if (!unchanged(worker.getInventory(), cargo) || !unchanged(source, supplies)) throw invalid("Supply preflight inputs changed");
        var transfer = new Transfer(active, worker.getInventory(), source, worker.neededItems, cargo, supplies, cells, slot, destinationSlot);
        save(worker, active.state("TRANSFER"));
        return transfer;
    }
    static void afterTransfer(BuilderEntity worker, Transfer transfer) {
        Active active = read(worker);
        if (active == null || !active.state.equals("TRANSFER") || !active.request.equals(transfer.active.request)
                || !active.scope.equals(transfer.active.scope) || worker.getInventory() != transfer.inventory || worker.neededItems != transfer.requests)
            throw invalid("Supply transfer fence changed");
        var lease = NativeEarthworksJobs.inventoryLease(worker);
        if (lease == null || !active.scope.equals(Scope.from(lease))) throw invalid("Supply lease changed during callback");
        var identities = new IdentityHashMap<ItemStack, Boolean>();
        var afterCargo = capture(transfer.inventory, identities); var afterSource = capture(transfer.source, identities);
        int taken = transfer.sourceSlot < 0 ? 0 : 1;
        var expectedSource = totals(transfer.supplies); var expectedCargo = totals(transfer.cargo);
        CompoundTag moved = null;
        if (taken == 1) {
            var stack = transfer.supplies.get(transfer.sourceSlot); moved = stack.data.copy(); moved.putByte("Count", (byte)1);
            String key = itemKey(stack.data); change(expectedSource, key, -1); change(expectedCargo, key, 1);
        }
        if (!expectedSource.equals(totals(afterSource)) || !expectedCargo.equals(totals(afterCargo))
                || !sourceSlotsMatch(transfer.supplies, afterSource, transfer.sourceSlot)
                || !destinationSlotsMatch(transfer.cargo, afterCargo, taken == 0 ? -1 : transfer.destinationSlot, moved)
                || (taken == 1 ? !worker.neededItems.isEmpty() : requestsWithoutFenceProblem(worker, active) != null)
                || transfer.inventory.getItem(5) != worker.getMainHandItem())
            throw invalid("Native supply outcome did not conserve the exact source and item");
        if (taken == 0) { save(worker, active.state("REQUESTED")); return; }
        if (active.receipts.size() >= MAX_RECEIPTS) throw invalid("Supply receipt history is full");
        var receipt = new CompoundTag(); receipt.putUUID("Request", active.request); receipt.put("Scope", active.scope.save());
        receipt.put("Item", moved); receipt.putLong("GameTime", worker.level().getGameTime());
        receipt.putLongArray("SourceCells", transfer.cells.stream().mapToLong(BlockPos::asLong).sorted().toArray());
        receipt.putString("InventoryBefore", frameHash(transfer.cargo)); receipt.putString("InventoryAfter", frameHash(afterCargo));
        receipt.putString("SourceBefore", frameHash(transfer.supplies)); receipt.putString("SourceAfter", frameHash(afterSource));
        var receipts = active.receipts.copy(); receipts.add(receipt);
        save(worker, new Active(active.scope, active.request, active.item, "DELIVERED", active.retryAfter, receipts, active.operations, active.tool, ""));
    }
    private static String requestsWithoutFenceProblem(BuilderEntity worker, Active active) {
        if (worker.neededItems.size() != 1) return "Changed request list";
        var need = worker.neededItems.get(0);
        return need != null && need.getClass() == NeededItem.class && need.count == 1 && need.required && need.sourceKey == null
                && need.matcher instanceof ExactMatcher matcher && matcher.scope.equals(active.scope)
                && need == matcher.nativeRequest && matcher.request.equals(active.request) && matcher.item == active.item ? null : "Changed request";
    }
    private static boolean sourceSlotsMatch(List<StackValue> before, List<StackValue> after, int extracted) {
        if (before.size() != after.size()) return false;
        for (int i = 0; i < before.size(); i++) {
            var a = before.get(i); var b = after.get(i);
            if (i == extracted) {
                if (a.reference != b.reference || b.count != a.count - 1
                        || b.count > 0 && !itemKey(a.data).equals(itemKey(b.data))) return false;
            } else if (a.reference != b.reference || a.count != b.count || !a.data.equals(b.data)) return false;
        }
        return true;
    }
    private static boolean destinationSlotsMatch(List<StackValue> before, List<StackValue> after, int inserted, CompoundTag moved) {
        if (before.size() != after.size()) return false;
        for (int i = 0; i < before.size(); i++) {
            var a = before.get(i); var b = after.get(i);
            if (i == inserted) {
                if (i < 6 || a.count != 0 || b.count != 1 || !b.data.equals(moved)) return false;
            } else if (a.reference != b.reference || a.count != b.count || !a.data.equals(b.data)) return false;
        }
        return true;
    }
    private static boolean unchanged(Container container, List<StackValue> before) {
        if (container.getContainerSize() != before.size()) return false;
        for (int i = 0; i < before.size(); i++) {
            var stack = container.getItem(i); var prior = before.get(i);
            if (stack != prior.reference || stack.getCount() != prior.count || !stack.save(new CompoundTag()).equals(prior.data)) return false;
        }
        return true;
    }
    private static List<StackValue> capture(Container container, IdentityHashMap<ItemStack, Boolean> identities) {
        if (container == null || container.getContainerSize() < 1 || container.getContainerSize() > 128) throw invalid("Unbounded inventory");
        var result = new ArrayList<StackValue>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack == null || stack.getClass() != ItemStack.class || stack.getCount() < 0
                    || stack.getCount() > Math.min(container.getMaxStackSize(), stack.getMaxStackSize())
                    || !stack.isEmpty() && identities.put(stack, true) != null) throw invalid("Aliased or invalid native inventory");
            CompoundTag data = stack.save(new CompoundTag()).copy();
            if (WorkersEarthworksPort.canonical(data).length() > 8192) throw invalid("Unbounded item metadata");
            result.add(new StackValue(stack, stack.getCount(), data));
        }
        return List.copyOf(result);
    }
    private static Map<String, Integer> totals(List<StackValue> values) {
        var totals = new HashMap<String, Integer>();
        for (var value : values) if (value.count > 0) totals.merge(itemKey(value.data), value.count, Math::addExact);
        return totals;
    }
    private static void change(Map<String, Integer> totals, String key, int delta) {
        int next = Math.addExact(totals.getOrDefault(key, 0), delta); if (next < 0) throw invalid("Missing actual supply");
        if (next == 0) totals.remove(key); else totals.put(key, next);
    }
    private static String itemKey(CompoundTag data) { var copy = data.copy(); copy.remove("Count"); return WorkersEarthworksPort.canonical(copy); }
    private static String frameHash(List<StackValue> values) {
        var tag = new ListTag(); for (var value : values) tag.add(value.data.copy());
        return hash(tag);
    }
    private static String hash(Tag tag) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(WorkersEarthworksPort.canonical(tag).getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static boolean digest(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    private static IllegalStateException invalid(String reason) { return new IllegalStateException(reason); }
}
