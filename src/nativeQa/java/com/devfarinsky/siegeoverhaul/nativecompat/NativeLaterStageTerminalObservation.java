package com.devfarinsky.siegeoverhaul.nativecompat;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.entities.ai.RecruitUpkeepEntityGoal;
import com.talhanation.recruits.entities.ai.RecruitUpkeepPosGoal;
import com.talhanation.workers.entities.AbstractWorkerEntity;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.entities.ai.AbstractChestGoal;
import com.talhanation.workers.entities.ai.BuilderWorkGoal;
import com.talhanation.workers.entities.ai.DepositItemsToStorage;
import com.talhanation.workers.entities.ai.GetNeededItemsFromStorage;
import com.talhanation.workers.entities.ai.RecruitStorageUpkeepGoal;
import com.talhanation.workers.world.NeededItem;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Case L evidence only. Reading this observer cannot finish a native lifecycle or resolve a matcher. */
final class NativeLaterStageTerminalObservation {
    private static final int MAX_REQUESTS = 8, MAX_GOALS = 128, MAX_REFERENCES = 65536;
    private static final List<String> KINDS = List.of("NEEDED", "DEPOSIT", "POSITION_UPKEEP", "ENTITY_UPKEEP");
    private final IdentityHashMap<Object, Integer> references = new IdentityHashMap<>();
    private final List<Map<String, Object>> referenceRecords = new ArrayList<>();

    static final class ObservationRejected extends IllegalStateException {
        ObservationRejected(String message) { super(message); }
    }

    record RequestRecord(int requestRef, int matcherRef, String matcherClass, boolean matcherHidden,
                         boolean matcherSynthetic, boolean matcherFinal, String matcherNestHost,
                         int matcherLoaderRef, int count, boolean required, int sourceRef,
                         boolean cacheResolved, int cacheRef, String cacheRegistryKey) {
        Map<String, Object> evidence() {
            return immutableMap("requestRef", requestRef, "matcherRef", matcherRef, "matcherClass", matcherClass,
                    "matcherHidden", matcherHidden, "matcherSynthetic", matcherSynthetic, "matcherFinal", matcherFinal,
                    "matcherNestHost", matcherNestHost, "matcherLoaderRef", matcherLoaderRef, "count", count,
                    "required", required, "sourceRef", sourceRef, "cacheResolved", cacheResolved,
                    "cacheRef", cacheRef, "cacheRegistryKey", cacheRegistryKey);
        }
    }

    record RequestSnapshot(int listRef, List<RequestRecord> records) {
        RequestSnapshot { records = List.copyOf(records); }
        int count() { return records.size(); }
        Map<String, Object> evidence() {
            return immutableMap("listRef", listRef, "listClass", ArrayList.class.getName(), "count", count(),
                    "records", records.stream().map(RequestRecord::evidence).toList());
        }
    }

    record WrapperRecord(String kind, int wrappedRef, int wrapperRef, int delegateRef, int sessionRef,
                         int workerRef, int delegateWorkerRef, String wrapperClass, String delegateClass,
                         boolean wrappedRunning, boolean started, boolean protectedLifecycle, boolean stopFailed) {
        Map<String, Object> evidence() {
            return immutableMap("kind", kind, "wrappedRef", wrappedRef, "wrapperRef", wrapperRef,
                    "delegateRef", delegateRef, "sessionRef", sessionRef, "workerRef", workerRef,
                    "delegateWorkerRef", delegateWorkerRef, "wrapperClass", wrapperClass, "delegateClass", delegateClass,
                    "wrappedRunning", wrappedRunning, "started", started,
                    "protectedLifecycle", protectedLifecycle, "stopFailed", stopFailed);
        }
    }

    record StorageSnapshot(int workerRef, int sessionRef, int sessionWorkerRef, List<WrapperRecord> wrappers,
                           List<Integer> pending, Map<String, Integer> owners, int upkeepOwnerRef,
                           boolean draining, boolean cleanupJournalPresent, Map<String, Integer> cleanupJournal) {
        StorageSnapshot {
            wrappers = List.copyOf(wrappers); pending = List.copyOf(pending);
            owners = Map.copyOf(owners); cleanupJournal = Map.copyOf(cleanupJournal);
        }
        Map<String, Object> evidence() {
            return immutableMap("workerRef", workerRef, "sessionRef", sessionRef, "sessionWorkerRef", sessionWorkerRef,
                    "wrappers", wrappers.stream().map(WrapperRecord::evidence).toList(), "pending", pending,
                    "owners", owners, "upkeepOwnerRef", upkeepOwnerRef, "draining", draining,
                    "cleanupJournalPresent", cleanupJournalPresent, "cleanupJournal", cleanupJournal,
                    "closed", closed(this));
        }
    }

    /** Every ID is assigned by reference identity, with zero reserved exclusively for null. */
    private int reference(Object value) {
        if (value == null) return 0;
        Integer existing = references.get(value);
        if (existing != null) return existing;
        require(references.size() < MAX_REFERENCES, "Reference evidence limit exceeded");
        int next = references.size() + 1;
        references.put(value, next);
        referenceRecords.add(value.getClass() == BuilderEntity.class
                ? immutableMap("id", next, "class", value.getClass().getName(), "workerUuid", ((BuilderEntity) value).getUUID().toString())
                : immutableMap("id", next, "class", value.getClass().getName()));
        return next;
    }

    List<Map<String, Object>> referenceEvidence() { return List.copyOf(referenceRecords); }

    Map<String, Object> metadata() {
        return immutableMap("neededItemClass", NeededItem.class.getName(),
                "neededItemLoaderRef", reference(NeededItem.class.getClassLoader()),
                "builderWorkGoalClass", BuilderWorkGoal.class.getName(),
                "builderWorkGoalLoaderRef", reference(BuilderWorkGoal.class.getClassLoader()),
                "fields", requestFieldMetadata());
    }

    static boolean sameRequests(RequestSnapshot frozen, RequestSnapshot current) {
        return frozen != null && frozen.equals(current);
    }

    /** Lifecycle flags may naturally settle; the four originally observed wrappers cannot be replaced. */
    static boolean sameStorageIdentity(StorageSnapshot frozen, StorageSnapshot current) {
        if (frozen == null || current == null || !validStorage(frozen) || !validStorage(current)
                || frozen.workerRef() != current.workerRef() || frozen.sessionRef() != current.sessionRef()
                || frozen.sessionWorkerRef() != current.sessionWorkerRef()) return false;
        for (int i = 0; i < KINDS.size(); i++) {
            WrapperRecord a = frozen.wrappers().get(i), b = current.wrappers().get(i);
            if (!a.kind().equals(b.kind()) || a.wrappedRef() != b.wrappedRef() || a.wrapperRef() != b.wrapperRef()
                    || a.delegateRef() != b.delegateRef() || a.sessionRef() != b.sessionRef()
                    || a.workerRef() != b.workerRef() || a.delegateWorkerRef() != b.delegateWorkerRef()
                    || !a.wrapperClass().equals(b.wrapperClass()) || !a.delegateClass().equals(b.delegateClass())) return false;
        }
        return true;
    }

    static boolean closed(StorageSnapshot snapshot) {
        return snapshot != null && validStorage(snapshot) && !snapshot.draining()
                && snapshot.pending().isEmpty() && snapshot.owners().isEmpty() && snapshot.upkeepOwnerRef() == 0
                && !snapshot.cleanupJournalPresent() && snapshot.cleanupJournal().isEmpty()
                && snapshot.wrappers().stream().noneMatch(w -> w.wrappedRunning() || w.started()
                || w.protectedLifecycle() || w.stopFailed());
    }

    /** Pure identity/ownership validation; pending cleanup is a valid, explicitly non-closed observation. */
    private static boolean validStorage(StorageSnapshot s) {
        if (s.workerRef() <= 0 || s.sessionRef() <= 0 || s.workerRef() == s.sessionRef()
                || s.sessionWorkerRef() != s.workerRef() || s.wrappers().size() != 4
                || s.pending().size() > 4 || s.owners().size() > 4 || s.cleanupJournal().size() > 4) return false;
        Set<Integer> unique = new HashSet<>(Set.of(s.workerRef(), s.sessionRef()));
        Map<Integer, WrapperRecord> members = new LinkedHashMap<>();
        for (int i = 0; i < 4; i++) {
            WrapperRecord w = s.wrappers().get(i);
            if (!KINDS.get(i).equals(w.kind()) || w.workerRef() != s.workerRef()
                    || w.delegateWorkerRef() != s.workerRef() || w.sessionRef() != s.sessionRef()
                    || w.wrappedRef() <= 0 || w.wrapperRef() <= 0 || w.delegateRef() <= 0
                    || !unique.add(w.wrappedRef()) || !unique.add(w.wrapperRef()) || !unique.add(w.delegateRef())
                    || !wrapperClass(i).getName().equals(w.wrapperClass())
                    || !delegateClass(i).getName().equals(w.delegateClass())) return false;
            members.put(w.wrapperRef(), w);
        }
        int expectedUpkeep = 0;
        for (var entry : s.owners().entrySet()) {
            WrapperRecord w = members.get(entry.getValue());
            if (w == null || !w.kind().equals(entry.getKey()) || !w.protectedLifecycle()) return false;
            if (w.kind().endsWith("UPKEEP")) {
                if (expectedUpkeep != 0) return false;
                expectedUpkeep = w.wrapperRef();
            }
        }
        if (s.upkeepOwnerRef() != expectedUpkeep) return false;
        Set<Integer> pending = new HashSet<>();
        for (int id : s.pending()) {
            WrapperRecord w = members.get(id);
            if (w == null || !pending.add(id) || !Integer.valueOf(id).equals(s.owners().get(w.kind()))
                    || w.started() || !w.protectedLifecycle()) return false;
        }
        for (WrapperRecord w : s.wrappers()) {
            if (w.started() && !w.protectedLifecycle()) return false;
            if (w.protectedLifecycle() && !Integer.valueOf(w.wrapperRef()).equals(s.owners().get(w.kind()))) return false;
        }
        if (!s.cleanupJournalPresent() && !s.cleanupJournal().isEmpty()) return false;
        for (var entry : s.cleanupJournal().entrySet()) {
            if (!s.owners().containsKey(entry.getKey()) || (entry.getValue() != 1 && entry.getValue() != 2)) return false;
        }
        return true;
    }

    RequestSnapshot requests(BuilderEntity worker) {
        require(worker != null && worker.getClass() == BuilderEntity.class, "Unsupported request worker");
        Object raw = read(field(AbstractWorkerEntity.class, "neededItems", List.class, Modifier.PUBLIC), worker);
        require(raw != null && raw.getClass() == ArrayList.class, "Unsupported native request list");
        ArrayList<?> list = (ArrayList<?>) raw;
        require(list.size() <= MAX_REQUESTS, "Native request list exceeds bound");
        int listRef = reference(list);
        var result = new ArrayList<RequestRecord>();
        var seen = new IdentityHashMap<Object, Boolean>();
        for (Object request : list) {
            require(request != null && request.getClass() == NeededItem.class && seen.put(request, true) == null,
                    "Unsupported or duplicate native request");
            Object matcher = read(requestField("matcher", Predicate.class, Modifier.PUBLIC | Modifier.FINAL), request);
            require(trustedMatcher(matcher), "Untrusted native request matcher");
            int count = (int) read(requestField("count", int.class, Modifier.PUBLIC), request);
            boolean required = (boolean) read(requestField("required", boolean.class, Modifier.PUBLIC | Modifier.FINAL), request);
            Object source = read(requestField("sourceKey", Object.class, Modifier.PUBLIC | Modifier.FINAL), request);
            Object cache = read(requestField("cachedMatchKey", Object.class, Modifier.PRIVATE), request);
            boolean resolved = (boolean) read(requestField("matchKeyResolved", boolean.class, Modifier.PRIVATE), request);
            require(count >= 1 && count <= 64 && required && source == null, "Unsupported native request values");
            require(resolved || cache == null, "Unresolved native request has a cache value");
            String cacheKey = null;
            if (cache != null) {
                // This two-material fixture needs no reverse registry lookup, hashing, or item callback.
                if (cache == Items.COBBLESTONE) cacheKey = "minecraft:cobblestone";
                else if (cache == Items.OAK_PLANKS) cacheKey = "minecraft:oak_planks";
                else throw new ObservationRejected("Resolved request key is outside the two-material observer scope");
            }
            Class<?> type = matcher.getClass();
            result.add(new RequestRecord(reference(request), reference(matcher), type.getName(), type.isHidden(),
                    type.isSynthetic(), Modifier.isFinal(type.getModifiers()), type.getNestHost().getName(),
                    reference(type.getClassLoader()), count, required, reference(source), resolved, reference(cache), cacheKey));
        }
        return new RequestSnapshot(listRef, result);
    }

    private static boolean trustedMatcher(Object matcher) {
        if (!(matcher instanceof Predicate<?>)) return false;
        Class<?> type = matcher.getClass();
        return type.isHidden() && type.isSynthetic() && Modifier.isFinal(type.getModifiers())
                && type.getNestHost() == BuilderWorkGoal.class
                && type.getClassLoader() == BuilderWorkGoal.class.getClassLoader();
    }

    StorageSnapshot storage(BuilderEntity worker) {
        require(worker != null && worker.getClass() == BuilderEntity.class, "Unsupported storage worker");
        GoalSelector selector = worker.goalSelector;
        require(selector != null && selector.getClass() == GoalSelector.class,
                "Unsupported native goal selector: " + (selector == null ? "null" : selector.getClass().getName()));
        Object rawAvailable = read(field(GoalSelector.class, "availableGoals", Set.class,
                Modifier.PRIVATE | Modifier.FINAL), selector);
        // Conservative capability fence, not a claim that this runtime shape was previously measured.
        // The 1.20.1 upstream source uses Sets.newLinkedHashSet(); unsupported shapes fail before callbacks.
        require(rawAvailable != null && rawAvailable.getClass() == LinkedHashSet.class,
                "Unsupported native available-goals collection: " + (rawAvailable == null ? "null" : rawAvailable.getClass().getName()));
        LinkedHashSet<?> available = (LinkedHashSet<?>) rawAvailable;
        require(available.size() <= MAX_GOALS, "Oversized goal selector");
        var wrappers = new WrapperRecord[4];
        Object sharedSession = null;
        int workerRef = reference(worker);
        for (Object entry : available) {
            require(entry != null && entry.getClass() == WrappedGoal.class, "Unsupported wrapped goal");
            WrappedGoal wrapped = (WrappedGoal) entry;
            Goal goal = wrapped.getGoal();
            require(goal != null, "Null native goal");
            if (!(goal instanceof ProtectedInventoryGoal) && !(goal instanceof AbstractChestGoal)
                    && !(goal instanceof RecruitUpkeepPosGoal) && !(goal instanceof RecruitUpkeepEntityGoal)) continue;
            require(goal.getClass() == ProtectedStorageAccess.class || goal.getClass() == ProtectedUpkeepAccess.class,
                    "Unguarded or unknown inventory wrapper");
            Object rawKind = read(field(goal.getClass(), "kind", ProtectedStorageAccess.Kind.class, Modifier.FINAL), goal);
            require(rawKind != null, "Missing inventory wrapper kind");
            int index = ((ProtectedStorageAccess.Kind) rawKind).ordinal();
            require(index < 4 && goal.getClass() == wrapperClass(index) && wrappers[index] == null,
                    "Missing, duplicate, or mismatched inventory wrapper kind");
            Object owner = read(field(ProtectedInventoryGoal.class, "worker", BuilderEntity.class, Modifier.FINAL), goal);
            Object delegate = read(field(ProtectedInventoryGoal.class, "delegate", Goal.class, Modifier.FINAL), goal);
            Object session = read(field(ProtectedInventoryGoal.class, "session", ProtectedInventoryGoal.Session.class, Modifier.FINAL), goal);
            require(owner == worker && delegate != null && delegate.getClass() == delegateClass(index)
                    && session != null && session.getClass() == ProtectedInventoryGoal.Session.class,
                    "Foreign inventory wrapper/delegate/session");
            Object delegateOwner = index < 2
                    ? read(field(AbstractChestGoal.class, "worker", AbstractWorkerEntity.class, Modifier.PUBLIC), delegate)
                    : read(field(index == 2 ? RecruitUpkeepPosGoal.class : RecruitUpkeepEntityGoal.class,
                    "recruit", AbstractRecruitEntity.class, Modifier.PUBLIC), delegate);
            require(delegateOwner == worker && (sharedSession == null || sharedSession == session),
                    "Foreign native delegate owner or split inventory session");
            sharedSession = session;
            wrappers[index] = new WrapperRecord(KINDS.get(index), reference(wrapped), reference(goal), reference(delegate),
                    reference(session), workerRef, reference(delegateOwner), goal.getClass().getName(), delegate.getClass().getName(),
                    wrapped.isRunning(), flag(goal, "started"), flag(goal, "protectedLifecycle"), flag(goal, "stopFailed"));
        }
        for (WrapperRecord wrapper : wrappers) require(wrapper != null, "Missing native inventory wrapper");
        Class<?> sessionClass = ProtectedInventoryGoal.Session.class;
        Object sessionWorker = read(field(sessionClass, "worker", BuilderEntity.class, Modifier.FINAL), sharedSession);
        require(sessionWorker == worker, "Foreign inventory session worker");
        Object rawPending = read(field(sessionClass, "pending", LinkedHashSet.class, Modifier.FINAL), sharedSession);
        Object rawOwners = read(field(sessionClass, "owners", EnumMap.class, Modifier.PRIVATE | Modifier.FINAL), sharedSession);
        require(rawPending != null && rawPending.getClass() == LinkedHashSet.class
                && rawOwners != null && rawOwners.getClass() == EnumMap.class, "Unsupported inventory session collections");
        var pendingSet = (LinkedHashSet<?>) rawPending;
        var ownerMap = (EnumMap<?, ?>) rawOwners;
        require(pendingSet.size() <= 4 && ownerMap.size() <= 4, "Inventory session exceeds member bound");
        var pending = new ArrayList<Integer>();
        for (Object member : pendingSet) pending.add(reference(member));
        var owners = new LinkedHashMap<String, Integer>();
        for (var entry : ownerMap.entrySet()) {
            require(entry.getKey() instanceof ProtectedStorageAccess.Kind, "Unknown inventory owner kind");
            owners.put(((ProtectedStorageAccess.Kind) entry.getKey()).name(), reference(entry.getValue()));
        }
        Object upkeepOwner = read(field(sessionClass, "upkeepOwner", ProtectedInventoryGoal.class, Modifier.PRIVATE), sharedSession);
        boolean draining = (boolean) read(field(sessionClass, "draining", boolean.class, Modifier.PRIVATE), sharedSession);
        var data = worker.getPersistentData().copy();
        var journal = new LinkedHashMap<String, Integer>();
        ProtectedInventoryCleanup.read(data).forEach((kind, state) -> journal.put(kind.name(), state));
        var snapshot = new StorageSnapshot(workerRef, reference(sharedSession), reference(sessionWorker), List.of(wrappers),
                pending, owners, reference(upkeepOwner), draining, data.contains(ProtectedInventoryCleanup.KEY), journal);
        require(validStorage(snapshot), "Malformed native inventory ownership graph");
        return snapshot;
    }

    private static Class<?> wrapperClass(int index) { return index < 2 ? ProtectedStorageAccess.class : ProtectedUpkeepAccess.class; }
    private static Class<?> delegateClass(int index) {
        return switch (index) {
            case 0 -> GetNeededItemsFromStorage.class;
            case 1 -> DepositItemsToStorage.class;
            case 2 -> RecruitUpkeepPosGoal.class;
            case 3 -> RecruitStorageUpkeepGoal.class;
            default -> throw new ObservationRejected("Unknown inventory wrapper kind");
        };
    }
    private static boolean flag(Object goal, String name) {
        return (boolean) read(field(ProtectedInventoryGoal.class, name, boolean.class, Modifier.PRIVATE), goal);
    }
    private static Field requestField(String name, Class<?> type, int modifiers) {
        return field(NeededItem.class, name, type, modifiers);
    }
    private static Field field(Class<?> owner, String name, Class<?> type, int modifiers) {
        try {
            Field field = owner.getDeclaredField(name);
            require(field.getDeclaringClass() == owner && field.getType() == type && field.getModifiers() == modifiers
                    && !field.isSynthetic(), "Native field metadata differs: " + owner.getName() + "." + name);
            require(field.trySetAccessible(), "Native field is inaccessible: " + owner.getName() + "." + name);
            return field;
        } catch (NoSuchFieldException failure) {
            throw new ObservationRejected("Missing native field: " + owner.getName() + "." + name);
        }
    }
    private static Object read(Field field, Object receiver) {
        try { return field.get(receiver); }
        catch (IllegalAccessException failure) { throw new ObservationRejected("Cannot read validated native field"); }
    }
    static List<Map<String, Object>> requestFieldMetadata() {
        var fields = List.of(field(AbstractWorkerEntity.class, "neededItems", List.class, Modifier.PUBLIC),
                requestField("matcher", Predicate.class, Modifier.PUBLIC | Modifier.FINAL),
                requestField("count", int.class, Modifier.PUBLIC),
                requestField("required", boolean.class, Modifier.PUBLIC | Modifier.FINAL),
                requestField("sourceKey", Object.class, Modifier.PUBLIC | Modifier.FINAL),
                requestField("cachedMatchKey", Object.class, Modifier.PRIVATE),
                requestField("matchKeyResolved", boolean.class, Modifier.PRIVATE));
        return fields.stream().map(f -> immutableMap("owner", f.getDeclaringClass().getName(), "name", f.getName(),
                "type", f.getType().getName(), "modifiers", f.getModifiers(), "synthetic", f.isSynthetic())).toList();
    }
    private static Map<String, Object> immutableMap(Object... entries) {
        var map = new LinkedHashMap<String, Object>();
        for (int i = 0; i < entries.length; i += 2) map.put((String) entries[i], entries[i + 1]);
        return Collections.unmodifiableMap(map);
    }
    private static void require(boolean allowed, String reason) {
        if (!allowed) throw new ObservationRejected(reason);
    }
}
