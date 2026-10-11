"""Execute Case L's actual dependency-free Java decision and audit its read-only adapter.

The installed compiler module compiles with source/target 17. These synthetic decision
fixtures are neither native source compilation nor save/load/gameplay evidence.
"""
from pathlib import Path
import shutil
import subprocess
import tempfile
import threading
import unittest

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/nativeQa/java/com/devfarinsky/siegeoverhaul/nativecompat/NativeLaterStageHelperProof.java'


def decision_source():
    text = SOURCE.read_text()
    start = text.index('    // BEGIN dependency-free decision:')
    end = text.index('    // END dependency-free decision.', start)
    return text[start:end]


HARNESS = r'''
    static final UUID PROJECT = id(1), HELPER = id(2), OWNER = id(3), COORDINATOR = id(4),
            LEDGER = id(5), A0 = id(6), RECEIPT = id(7), FOREIGN = id(8), A1 = id(9), A2 = id(10), A3 = id(11);
    static UUID id(long value) { return new UUID(0, value); }
    static <T> T replace(T record, String path, Object value) throws Exception {
        int dot = path.indexOf('.'); String name = dot < 0 ? path : path.substring(0, dot);
        var components = record.getClass().getRecordComponents();
        var types = new Class<?>[components.length]; var values = new Object[components.length]; boolean found = false;
        for (int i = 0; i < components.length; i++) {
            types[i] = components[i].getType(); values[i] = components[i].getAccessor().invoke(record);
            if (components[i].getName().equals(name)) {
                found = true; values[i] = dot < 0 ? value : replace(values[i], path.substring(dot + 1), value);
            }
        }
        if (!found) throw new AssertionError("Unknown source component " + path);
        @SuppressWarnings("unchecked") T result = (T) record.getClass().getDeclaredConstructor(types).newInstance(values);
        return result;
    }
    static Facts fixture(String mode, boolean pending) {
        var identity = new Identity(PROJECT, 17, "a".repeat(64), "team:fixture", "fixture", OWNER,
                COORDINATOR, 572, 1, 1, 64, "paid:64:survival", List.of(new Stage(0, A0, "0".repeat(64), 30),
                new Stage(1, A1, "1".repeat(64), 35), new Stage(2, A2, "2".repeat(64), 30), new Stage(3, A3, "3".repeat(64), 44)), LEDGER);
        var receipts = List.of(new Receipt(PROJECT, 17, "a".repeat(64), 0, A0, "0".repeat(64), 30),
                new Receipt(PROJECT, 17, "a".repeat(64), 1, A1, "1".repeat(64), 35));
        var progress = new Progress("RUNNING", 2, receipts, true);
        var hand = new Hand(RECEIPT, HELPER, OWNER, A0, LEDGER);
        var selector = new Selector(PROJECT, 17, "a".repeat(64), "team:fixture", LEDGER);
        var originalAssociation = new Association(selector, A0, LEDGER, A0, OWNER);
        var association = mode.equals("stale") ? originalAssociation : mode.equals("active")
                ? new Association(selector, A2, LEDGER, A2, OWNER) : new Association(selector, null, null, null, null);
        var data = new LinkedHashMap<String, String>();
        data.put(WHOLE, "exact-selector"); data.put(CHILD, uuidTag(A0)); data.put(CHILD_GENERATION, uuidTag(LEDGER));
        data.put(NATIVE_AREA, uuidTag(A0)); data.put(NATIVE_OWNER, uuidTag(OWNER)); data.put(NATIVE_POS, "123L");
        data.put("SiegePlayerFortificationMisses", "0"); data.put("SiegeWallGroundRecovery", "{Time:100L}");
        data.put(STATUS, "original pause"); data.put("SiegeProtectedHandLifecycle", "exact-original-hand");
        data.put("unrelated", "immutable"); data.put(REBINDS, "2");
        var frozen = new Frozen(hand, originalAssociation, data, 2, true);
        var current = new LinkedHashMap<>(data); boolean active = mode.equals("active");
        if (!mode.equals("stale")) {
            DETACHED_KEYS.forEach(current::remove); current.remove(STATUS); current.put(REBINDS, active ? "4" : "3");
        }
        if (active) {
            current.put(CHILD, uuidTag(A2)); current.put(CHILD_GENERATION, uuidTag(LEDGER)); current.put(NATIVE_AREA, uuidTag(A2));
            current.put(NATIVE_OWNER, uuidTag(OWNER)); current.put(NATIVE_POS, "456L");
        }
        if (pending) current.put(HAND_PENDING, "1b");
        var operations = new Operations(-1, false, false, false, false, false, true, true, true, true, false, !pending, pending);
        var actor = new Actor(HELPER, OWNER, hand, association, active ? A2 : null, true, 456L,
                operations, current, active ? 4 : mode.equals("stale") ? 2 : 3);
        var member = new Member(0, active, true, false, -1, !active, -1, active ? Set.of(0, 2) : Set.of(0), -1, false, false);
        var history = new History(true, true, true, true, true, true, true, true, active,
                active ? Set.of(A0, A2) : Set.of(A0), A0, PROJECT, member);
        return new Facts(identity, identity, progress, HELPER, OWNER, LEDGER, LEDGER, hand, hand, actor, frozen, history);
    }
    static Facts changeData(Facts f, String key, String value) throws Exception {
        var changed = new LinkedHashMap<>(f.actor().persistent());
        if (value == null) changed.remove(key); else changed.put(key, value);
        return replace(f, "actor.persistent", changed);
    }
    static Facts rebind(Facts f, int count) throws Exception {
        f = replace(f, "actor.rebinds", count); return changeData(f, REBINDS, Integer.toString(count));
    }
    static Facts receipt(Facts f, int index, String field, Object value) throws Exception {
        var changed = new ArrayList<>(f.progress().receipts()); changed.set(index, replace(changed.get(index), field, value));
        return replace(f, "progress.receipts", changed);
    }
    public static void main(String[] args) throws Exception {
        if (args.length > 0) { run(args[0]); return; }
        try (var input = new java.io.BufferedReader(new java.io.InputStreamReader(System.in))) {
            String argument;
            while ((argument = input.readLine()) != null) { run(argument); System.out.flush(); }
        }
    }
    static void run(String argument) throws Exception {
        String mode = argument.split(":", 2)[0], mutation = argument.split(":", 2)[1];
        Facts f = fixture(mode, mutation.startsWith("pending"));
        switch (mutation) {
            case "valid", "pending" -> { }
            case "current-exact-a0" -> f = replace(f, "actor.currentArea", A0);
            case "current-exact-a2" -> f = replace(f, "actor.currentArea", A2);
            case "current-null" -> f = replace(f, "actor.currentArea", null);
            case "current-foreign" -> f = replace(f, "actor.currentArea", FOREIGN);
            case "current-bad-owner" -> { f = replace(f, "actor.currentArea", mode.equals("active") ? A2 : A0); f = replace(f, "actor.currentAreaExact", false); }
            case "wrong-helper" -> f = replace(f, "actor.helper", FOREIGN);
            case "wrong-owner" -> f = replace(f, "actor.owner", FOREIGN);
            case "helper-coordinator" -> f = replace(f, "helper", COORDINATOR);
            case "wrong-project" -> f = replace(f, "current.project", FOREIGN);
            case "wrong-core" -> f = replace(f, "current.core", "team:other");
            case "wrong-hash" -> f = replace(f, "current.hash", "b".repeat(64));
            case "wrong-generation" -> f = replace(f, "current.generation", 18L);
            case "wrong-project-owner" -> f = replace(f, "current.owner", FOREIGN);
            case "wrong-coordinator" -> f = replace(f, "current.coordinator", FOREIGN);
            case "wrong-ledger" -> f = replace(f, "currentLedger", FOREIGN);
            case "wrong-original-ledger" -> f = replace(f, "originalLedger", FOREIGN);
            case "wrong-current-ledger" -> f = replace(f, "current.ledger", FOREIGN);
            case "wrong-payment" -> f = replace(f, "current.payment", "paid:0:creative");
            case "wrong-targets" -> f = replace(f, "current.targets", 573);
            case "wrong-fee" -> f = replace(f, "current.price", 128);
            case "wrong-immutable-map" -> f = replace(f, "progress.immutableExact", false);
            case "complete", "canceled", "a2-verified", "waiting", "blocked" ->
                f = replace(f, "progress.state", switch(mutation) {
                    case "complete" -> "COMPLETE"; case "canceled" -> "CANCELED"; case "a2-verified" -> "STAGE_VERIFIED";
                    case "waiting" -> "WAITING_FOR_NEXT_STAGE"; default -> "RECOVERY_BLOCKED";
                });
            case "active-a0" -> f = replace(f, "progress.activeStage", 0);
            case "active-a1" -> f = replace(f, "progress.activeStage", 1);
            case "active-a3" -> f = replace(f, "progress.activeStage", 3);
            case "missing-receipt" -> f = replace(f, "progress.receipts", f.progress().receipts().subList(0, 1));
            case "extra-receipt" -> {
                var receipts = new ArrayList<>(f.progress().receipts()); receipts.add(new Receipt(PROJECT, 17, "a".repeat(64), 2, A2, "2".repeat(64), 30));
                f = replace(f, "progress.receipts", receipts);
            }
            case "reordered-receipts" -> f = replace(f, "progress.receipts", List.of(f.progress().receipts().get(1), f.progress().receipts().get(0)));
            case "duplicate-receipts" -> f = replace(f, "progress.receipts", List.of(f.progress().receipts().get(0), f.progress().receipts().get(0)));
            case "receipt-project" -> f = receipt(f, 0, "project", FOREIGN);
            case "receipt-generation" -> f = receipt(f, 0, "generation", 18L);
            case "receipt-hash" -> f = receipt(f, 0, "hash", "b".repeat(64));
            case "receipt-index" -> f = receipt(f, 1, "stage", 0);
            case "receipt-area" -> f = receipt(f, 1, "area", FOREIGN);
            case "receipt-digest" -> f = receipt(f, 1, "digest", "c".repeat(64));
            case "receipt-count" -> f = receipt(f, 1, "targets", 34);
            case "wrong-actor-receipt" -> f = replace(f, "actor.hand.receipt", FOREIGN);
            case "wrong-ledger-receipt" -> f = replace(f, "ledgerHand.receipt", FOREIGN);
            case "wrong-frozen-receipt" -> f = replace(f, "frozen.hand.receipt", FOREIGN);
            case "reminted-hand" -> {
                f = replace(f, "actor.hand.receipt", FOREIGN); f = replace(f, "ledgerHand.receipt", FOREIGN); f = replace(f, "frozen.hand.receipt", FOREIGN);
            }
            case "changed-original-hand-to-a2" -> f = replace(f, "originalHand.area", A2);
            case "changed-all-hands-to-a2" -> {
                for (String field : List.of("originalHand", "actor.hand", "ledgerHand", "frozen.hand")) f = replace(f, field + ".area", A2);
            }
            case "wrong-original-hand-owner" -> f = replace(f, "originalHand.owner", FOREIGN);
            case "wrong-original-hand-builder" -> f = replace(f, "originalHand.builder", FOREIGN);
            case "wrong-original-hand-generation" -> f = replace(f, "originalHand.generation", FOREIGN);
            case "missing-member" -> f = replace(f, "history.member", null);
            case "unknown-member" -> f = replace(f, "history.known", false);
            case "foreign-reservation" -> f = replace(f, "history.projectIdentity", false);
            case "missing-a2-lease" -> f = replace(f, "history.a2Lease", false);
            case "unretired-a0-a1" -> f = replace(f, "history.pastRetired", false);
            case "future-lease" -> f = replace(f, "history.futureAbsent", false);
            case "replaced-coordinator" -> f = replace(f, "history.originalCoordinator", false);
            case "helper-was-coordinator" -> f = replace(f, "history.helperNeverCoordinator", false);
            case "destruction" -> f = replace(f, "history.destructionAbsent", false);
            case "wrong-work-grant" -> f = replace(f, "history.stageGranted", !f.history().stageGranted());
            case "empty-history" -> f = replace(f, "history.cleanupAreas", Set.of());
            case "a1-cleanup" -> f = replace(f, "history.cleanupAreas", Set.of(A0, A1));
            case "foreign-history" -> f = replace(f, "history.cleanupAreas", Set.of(A0, FOREIGN));
            case "wrong-admission-area" -> f = replace(f, "history.admissionArea", A2);
            case "wrong-unresolved" -> f = replace(f, "history.unresolved", FOREIGN);
            case "null-unresolved" -> f = replace(f, "history.unresolved", null);
            case "wrong-active-member" -> f = replace(f, "history.member.active", !f.history().member().active());
            case "not-ready" -> f = replace(f, "history.member.ready", false);
            case "withdrawn" -> f = replace(f, "history.member.withdrawn", true);
            case "rejoining" -> f = replace(f, "history.member.rejoin", 2);
            case "new-admission" -> f = replace(f, "history.member.admitted", 2);
            case "a1-grant" -> f = replace(f, "history.member.grants", Set.of(0, 1, 2));
            case "missing-a0-grant" -> f = replace(f, "history.member.grants", Set.of(2));
            case "missing-grants" -> f = replace(f, "history.member.grants", Set.of());
            case "wrong-a2-grant" -> f = replace(f, "history.member.grants", mode.equals("active") ? Set.of(0) : Set.of(0, 2));
            case "legacy-prefix" -> f = replace(f, "history.member.legacyEnd", 1);
            case "member-destroyed" -> f = replace(f, "history.member.destroyed", true);
            case "resolved" -> f = replace(f, "history.member.resolved", true);
            case "wrong-suspension" -> f = replace(f, "history.member.suspended", !f.history().member().suspended());
            case "retired" -> f = replace(f, "history.member.retired", 0);
            case "missing-selector" -> f = replace(f, "actor.association.selector", null);
            case "foreign-selector" -> f = replace(f, "actor.association.selector.project", FOREIGN);
            case "selector-generation" -> f = replace(f, "actor.association.selector.generation", 18L);
            case "selector-hash" -> f = replace(f, "actor.association.selector.hash", "b".repeat(64));
            case "selector-core" -> f = replace(f, "actor.association.selector.core", "team:other");
            case "selector-ledger" -> f = replace(f, "actor.association.selector.ledger", FOREIGN);
            case "frozen-selector-ledger" -> f = replace(f, "frozen.association.selector.ledger", FOREIGN);
            case "missing-child" -> f = replace(f, "actor.association.child", null);
            case "foreign-child" -> f = replace(f, "actor.association.child", FOREIGN);
            case "foreign-child-generation" -> f = replace(f, "actor.association.generation", FOREIGN);
            case "missing-child-generation" -> f = replace(f, "actor.association.generation", null);
            case "missing-native" -> f = replace(f, "actor.association.nativeArea", null);
            case "foreign-native" -> f = replace(f, "actor.association.nativeArea", FOREIGN);
            case "missing-native-owner" -> f = replace(f, "actor.association.nativeOwner", null);
            case "foreign-native-owner" -> f = replace(f, "actor.association.nativeOwner", FOREIGN);
            case "frozen-detached" -> f = replace(f, "frozen.association", new Association(null, null, null, null, null));
            case "frozen-not-closed" -> f = replace(f, "frozen.closed", false);
            case "retained-child" -> f = replace(f, "actor.association.child", A0);
            case "retained-native-owner" -> f = replace(f, "actor.association.nativeOwner", OWNER);
            case "active-native-a0" -> f = replace(f, "actor.association.nativeArea", A0);
            case "active-child-a0" -> f = replace(f, "actor.association.child", A0);
            case "active-full-a0" -> f = replace(f, "actor.association", f.frozen().association());
            case "borrowed-hand" -> f = replace(f, "actor.operations.beforeItemSlot", 5);
            case "active-use" -> f = replace(f, "actor.operations.activeUse", true);
            case "review" -> f = replace(f, "actor.operations.review", true);
            case "requests", "pending-requests" -> f = replace(f, "actor.operations.requests", true);
            case "cleanup" -> f = replace(f, "actor.operations.cleanup", true);
            case "storage" -> f = replace(f, "actor.operations.storage", true);
            case "nonordinary" -> f = replace(f, "actor.operations.ordinary", false);
            case "nonfinite" -> f = replace(f, "actor.operations.finitePose", false);
            case "unequal-hand", "pending-unequal-hand" -> f = replace(f, "actor.operations.equalHandValue", false);
            case "noncanonical", "pending-noncanonical" -> f = replace(f, "actor.operations.canonicalCounts", false);
            case "third-slot-alias", "pending-third-slot-alias" -> f = replace(f, "actor.operations.thirdSlotAlias", true);
            case "unobserved-split" -> f = replace(f, "actor.operations.mainIdentity", false);
            case "changed-unrelated" -> f = changeData(f, "unrelated", "changed");
            case "missing-unrelated" -> f = changeData(f, "unrelated", null);
            case "new-unrelated" -> f = changeData(f, "new-key", "1");
            case "changed-hand-tag" -> f = changeData(f, "SiegeProtectedHandLifecycle", "foreign");
            case "omitted-hand-tag" -> f = changeData(f, "SiegeProtectedHandLifecycle", null);
            case "missing-whole-tag" -> f = changeData(f, WHOLE, null);
            case "changed-whole-tag" -> f = changeData(f, WHOLE, "foreign");
            case "retained-position" -> f = changeData(f, NATIVE_POS, "123L");
            case "retained-pause" -> f = changeData(f, STATUS, "original pause");
            case "removed-pause" -> f = changeData(f, STATUS, null);
            case "changed-pause" -> f = changeData(f, STATUS, "foreign pause");
            case "restored-stale" -> { f = rebind(f, 3); f = changeData(f, STATUS, null); }
            case "changed-position" -> f = changeData(f, NATIVE_POS, "999L");
            case "changed-a2-marker" -> f = replace(f, "actor.currentPosition", 999L);
            case "omitted-native-data" -> f = changeData(f, NATIVE_AREA, null);
            case "pending-omitted-arm" -> f = changeData(f, HAND_PENDING, null);
            case "pending-false-arm" -> f = changeData(f, HAND_PENDING, "0b");
            case "spurious-pending" -> f = changeData(f, HAND_PENDING, "1b");
            case "rebind-backward" -> f = rebind(f, 1);
            case "rebind-too-many" -> f = rebind(f, 5);
            case "pending-rebind-too-many" -> f = rebind(f, mode.equals("stale") ? 3 : 4);
            case "rebind-no-persistent-proof" -> f = replace(f, "actor.rebinds", 2);
            case "same-tick-zero-rebind" -> f = rebind(f, 2);
            case "return-restored" -> f = rebind(f, 4);
            case "no-original-status" -> {
                var frozen = new LinkedHashMap<>(f.frozen().persistent()); frozen.remove(STATUS);
                f = replace(f, "frozen.persistent", frozen); f = changeData(f, STATUS, null);
            }
            default -> {
                if (mutation.startsWith("rejoin-prefix-")) {
                    f = replace(f, "history.member.rejoin", 2);
                    int prefix = Integer.parseInt(mutation.substring("rejoin-prefix-".length()));
                    if (prefix == 0) f = replace(f, "actor.association", new Association(f.actor().association().selector(), null, null, null, null));
                    if (prefix == 1) f = replace(f, "actor.association.nativeArea", null);
                    if (prefix == 2) f = replace(f, "actor.currentArea", null);
                    if (prefix == 3) f = replace(f, "actor.operations.pending", true);
                    if (prefix == 4) f = replace(f, "history.stageGranted", false);
                } else throw new AssertionError("Unknown mutation " + mutation);
            }
        }
        String before = f.toString(); Proof proof = decide(f);
        if (!before.equals(f.toString())) throw new AssertionError("Decision mutated its input");
        System.out.println(proof.state() + ":" + proof.reason()); System.out.println(proof.evidence());
    }
'''


class LaterStageProofJavaTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.java = shutil.which('java')
        if not cls.java:
            raise RuntimeError('Java with jdk.compiler is required')
        cls.temp = tempfile.TemporaryDirectory()
        cls.root = Path(cls.temp.name)
        harness = cls.root / 'LaterStageHarness.java'
        harness.write_text('import java.util.*;\npublic final class LaterStageHarness {\n' + decision_source() + HARNESS + '}\n')
        launcher = cls.root / 'CompileLaterStage.java'
        launcher.write_text('''import javax.tools.ToolProvider;
class CompileLaterStage { public static void main(String[] a) {
    var c = ToolProvider.getSystemJavaCompiler();
    if (c == null) throw new IllegalStateException("jdk.compiler unavailable");
    System.exit(c.run(null, null, null, "--source", "17", "--target", "17", "-d", a[0], a[1]));
}}\n''')
        result = subprocess.run([cls.java, str(launcher), str(cls.root), str(harness)], capture_output=True, text=True, timeout=30)
        if result.returncode:
            raise AssertionError('Actual Case L decision did not compile as Java 17: ' + result.stdout + result.stderr)
        # One JVM executes every independently rebuilt fixture. Avoid hundreds of JVM
        # startups; a per-command watchdog still makes a hung decision fail closed.
        cls.process = subprocess.Popen([cls.java, '-cp', str(cls.root), 'LaterStageHarness'],
                                       stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                       text=True, bufsize=1)

    @classmethod
    def tearDownClass(cls):
        cls.process.stdin.close()
        try:
            cls.process.wait(timeout=10)
        finally:
            if cls.process.poll() is None:
                cls.process.kill()
                cls.process.wait(timeout=10)
            cls.process.stdout.close()
            cls.temp.cleanup()

    def check(self, mode, state='REJECTED'):
        watchdog = threading.Timer(30, self.process.kill)
        watchdog.start()
        try:
            self.assertIsNone(self.process.poll(), 'Decision JVM exited before ' + mode)
            self.process.stdin.write(mode + '\n')
            self.process.stdin.flush()
            output = self.process.stdout.readline() + self.process.stdout.readline()
        finally:
            watchdog.cancel()
        self.assertTrue(output.startswith(state + ':'), mode + '\n' + output)
        return output

    def reject_all(self, mutations, states=('stale', 'detached', 'active')):
        for state in states:
            for mutation in mutations:
                with self.subTest(state=state, mutation=mutation):
                    self.check(state + ':' + mutation)

    def test_exact_stale_null_pointer_detached_suspended_and_active_a2(self):
        for mutation in ('valid', 'current-exact-a0', 'restored-stale', 'no-original-status'):
            self.check('stale:' + mutation, 'STALE_A0_SUSPENDED')
        for mutation in ('valid', 'same-tick-zero-rebind', 'return-restored', 'no-original-status'):
            self.check('detached:' + mutation, 'DETACHED_SUSPENDED')
        for mutation in ('valid', 'same-tick-zero-rebind', 'no-original-status'):
            output = self.check('active:' + mutation, 'ACTIVE_A2')
            self.assertIn('grantedStages=[0, 2]', output)
            self.assertIn('handArea=00000000-0000-0000-0000-000000000006', output)

    def test_only_recorded_load_census_may_use_pending_suspended_provenance(self):
        self.assertIn('pendingHand=true', self.check('stale:pending', 'STALE_A0_SUSPENDED'))
        self.assertIn('pendingHand=true', self.check('detached:pending', 'DETACHED_SUSPENDED'))
        self.check('active:pending')
        self.reject_all(('pending-requests', 'pending-unequal-hand', 'pending-noncanonical', 'pending-third-slot-alias',
                         'pending-omitted-arm', 'pending-false-arm', 'pending-rebind-too-many'), ('stale', 'detached'))

    def test_exact_project_identity_generation_payment_geometry_and_owner(self):
        self.reject_all(('wrong-helper', 'wrong-owner', 'helper-coordinator', 'wrong-project', 'wrong-core', 'wrong-hash',
                         'wrong-generation', 'wrong-project-owner', 'wrong-coordinator', 'wrong-ledger', 'wrong-original-ledger',
                         'wrong-current-ledger', 'wrong-payment', 'wrong-targets', 'wrong-fee', 'wrong-immutable-map'))

    def test_only_running_a2_with_exact_ordered_a0_a1_receipts(self):
        self.reject_all(('complete', 'canceled', 'a2-verified', 'waiting', 'blocked', 'active-a0', 'active-a1', 'active-a3',
                         'missing-receipt', 'extra-receipt', 'reordered-receipts', 'duplicate-receipts', 'receipt-project',
                         'receipt-generation', 'receipt-hash', 'receipt-index', 'receipt-area', 'receipt-digest', 'receipt-count'))

    def test_original_hand_is_never_reminted_or_moved_to_a2(self):
        self.reject_all(('wrong-actor-receipt', 'wrong-ledger-receipt', 'wrong-frozen-receipt', 'reminted-hand',
                         'changed-original-hand-to-a2', 'changed-all-hands-to-a2', 'wrong-original-hand-owner',
                         'wrong-original-hand-builder', 'wrong-original-hand-generation'))

    def test_exact_suspended_history_has_no_a1_or_coordinator_cleanup_authority(self):
        self.reject_all(('missing-member', 'unknown-member', 'foreign-reservation', 'missing-a2-lease', 'unretired-a0-a1',
                         'future-lease', 'replaced-coordinator', 'helper-was-coordinator', 'destruction', 'wrong-work-grant',
                         'empty-history', 'a1-cleanup', 'foreign-history', 'wrong-admission-area', 'wrong-unresolved',
                         'null-unresolved', 'wrong-active-member', 'not-ready', 'withdrawn', 'rejoining', 'new-admission',
                         'a1-grant', 'missing-a0-grant', 'missing-grants', 'wrong-a2-grant', 'legacy-prefix',
                         'member-destroyed', 'resolved', 'wrong-suspension', 'retired'))

    def test_no_partial_or_foreign_associations(self):
        self.reject_all(('missing-selector', 'foreign-selector', 'selector-generation', 'selector-hash', 'selector-core',
                         'selector-ledger', 'frozen-selector-ledger', 'foreign-child', 'foreign-child-generation',
                         'foreign-native', 'foreign-native-owner', 'current-foreign', 'current-bad-owner',
                         'frozen-detached', 'frozen-not-closed'))
        self.reject_all(('missing-child', 'missing-child-generation', 'missing-native', 'missing-native-owner'), ('stale', 'active'))
        self.reject_all(('retained-child', 'retained-native-owner', 'current-exact-a0', 'current-exact-a2'), ('detached',))
        self.reject_all(('current-null', 'current-exact-a0', 'active-native-a0', 'active-child-a0', 'active-full-a0'), ('active',))

    def test_every_rejoin_prefix_remains_nonworking_until_final_activation(self):
        self.reject_all(tuple('rejoin-prefix-' + str(i) for i in range(6)), ('active',))
        self.check('active:valid', 'ACTIVE_A2')
        self.check('active:valid', 'ACTIVE_A2')  # Replay is observational, has no grant/payment/inventory write.

    def test_closed_operations_never_waive_canonical_hand_or_inventory_safety(self):
        self.reject_all(('borrowed-hand', 'active-use', 'review', 'requests', 'cleanup', 'storage', 'nonordinary',
                         'nonfinite', 'unequal-hand', 'noncanonical', 'third-slot-alias', 'unobserved-split'))

    def test_only_exact_source_backed_persistent_deltas(self):
        self.reject_all(('changed-unrelated', 'missing-unrelated', 'new-unrelated', 'changed-hand-tag', 'omitted-hand-tag',
                         'missing-whole-tag', 'changed-whole-tag', 'changed-position', 'changed-pause', 'spurious-pending',
                         'rebind-backward', 'rebind-too-many'))
        self.reject_all(('rebind-no-persistent-proof', 'retained-pause'), ('detached', 'active'))
        self.reject_all(('omitted-native-data',), ('stale', 'active'))
        self.check('stale:removed-pause')
        self.check('detached:retained-position')
        self.check('active:changed-a2-marker')
        self.check('active:retained-position')


class LaterStageProofSourceContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = SOURCE.read_text()
        cls.native = cls.text.split('    // END dependency-free decision.', 1)[1]

    def test_adapter_is_read_only_and_does_not_load_world_or_populate_authority_cache(self):
        for forbidden in ('ConstructionEditLedger.get(', 'PerimeterProjectAuthority.snapshot(', 'validHandReceipt(',
                          'ledger.matchesProjectLease(', 'retireBuilderAssociation(', 'resolveCrewAssociation(',
                          'reconcileWorker(', 'sealCrewHistory(', 'resumeCrewMember(', 'beginCrewStage(', 'activateCrewMember(',
                          'setDirty(', '.getChunk(', '.getBlockState(', '.getBlockEntity(', '.getEntity(', '.overworld(', '.setItem', '.tick(',
                          '.arm(', '.restore(', '.setPos(', '.putUUID(', '.putBoolean('):
            with self.subTest(forbidden=forbidden):
                self.assertNotIn(forbidden, self.native)
        self.assertIn('worker.getPersistentData().copy()', self.native)
        self.assertIn('finalizedOffline.persistent()', self.native)
        self.assertIn('ledger.save(new CompoundTag())', self.native)
        self.assertNotRegex(self.native, r'\bdata\.(?:put|remove)\s*\(')

    def test_raw_native_receipt_history_and_every_association_channel_are_read(self):
        for required in ('worker.getUUID()', 'worker.getOwnerUUID()', 'ledger.handLifecycle(helper)',
                         'ProtectedBuilderHandLifecycle.read(', 'PerimeterProjectLink.read(data)',
                         'PerimeterProjectLink.ledgerGeneration(data)', 'worker.currentBuildArea', 'WorkersBridge.readOwner(current)',
                         'PerimeterProjectAuthority.read(current.getPersistentData())', 'marker.reservedBuilderId()',
                         'NativeConstructionGuard.commissionPaid(current)', 'ledger.knownCrewMember(currentProject, helper)',
                         'ledger.workerCleanupAreas(currentProject, helper)', 'ledger.crewAdmissionArea(currentProject, helper)',
                         'ledger.unresolvedCrewProject(helper)', 'ledger.crewBuilderDestructionReceipt(currentProject, helper)',
                         'ledger.sameGeneration(ledgerGeneration)', 'ledger.projectLeaseIndex(currentProject.header().projectId()) == 2',
                         'ledger.retired(a0) && ledger.retired(a1)', 'ledger.matches(a2,', 'ledger.assignedBuilder(currentProject)',
                         '!ledger.knownProjectBuilder(currentProject, helper)', 'ledger.crewStageGranted(currentProject, helper)'):
            with self.subTest(required=required):
                self.assertIn(required, self.native)
        self.assertIn('members.size() != 1', self.native)
        self.assertIn('new long[]{1L, 0L, 0L, 0L}', self.native)
        self.assertIn('new long[]{5L, 0L, 0L, 0L}', self.native)
        for key in ('Active', 'Ready', 'Withdrawn', 'RetiredStage', 'AdmittedStage', 'Suspended', 'RejoinStage', 'LegacyEnd', 'Resolved'):
            self.assertRegex(self.native, r'(?:integer|bool)\(saved, "' + key + r'"\)')

    def test_operations_remain_closed_and_pending_is_not_work_authority(self):
        for required in ('worker.getBeforeItemSlot()', 'ProtectedBuilderHandMirror.activeUse(worker)',
                         'worker.neededItems == null || !worker.neededItems.isEmpty()', 'ProtectedInventoryCleanup.read(data)',
                         'ProtectedStorageAccess.runningProblem(worker)', 'worker.forcedDeposit', 'worker.getFollowState() == 6',
                         'Double.isFinite(', 'Float.isFinite(', 'ProtectedBuilderHandMirror.sameValue(slot, main)',
                         'stack == slot || stack == main', 'stack.getCount() >= 0',
                         'Math.min(inventory.getMaxStackSize(), stack.getMaxStackSize())'):
            with self.subTest(required=required):
                self.assertIn(required, self.native)
        self.assertIn('&& (pending || mainIdentity)', decision_source())
        self.assertIn('&& !actor.operations().pending()) state = State.ACTIVE_A2', decision_source())
        self.assertIn('usable ONLY for that recorded load census', self.text)
        self.assertIn('exact A0/A1 verification/retirement journal snapshots', self.text)

    def test_cleanup_and_relink_deltas_are_bound_to_unchanged_production_methods(self):
        base = ROOT / 'src/main/java/com/devfarinsky/siegeoverhaul'
        guard = (base / 'nativecompat/NativeConstructionGuard.java').read_text()
        jobs = (base / 'core/PlayerFortificationJobs.java').read_text()
        projects = (base / 'nativecompat/NativePerimeterProjects.java').read_text()
        recovery = (base / 'core/BuilderGroundRecovery.java').read_text()
        mirror = (base / 'nativecompat/ProtectedBuilderHandMirror.java').read_text()
        retire = guard.split('static boolean retireBuilderAssociation(', 1)[1].split('static UUID protectedReceiptArea', 1)[0]
        self.assertIn('PlayerFortificationJobs.unlink(builder, areaId)', retire)
        self.assertIn('data.remove(PROTECTED_LINK); data.remove(PROTECTED_GENERATION)', retire)
        self.assertNotIn('PerimeterProjectLink.clear', retire)
        self.assertNotIn('data.remove(STATUS)', retire)
        for key in ('PLAYER_FORTIFICATION_AREA_ID', 'PLAYER_FORTIFICATION_OWNER', 'PLAYER_FORTIFICATION_POS', 'PLAYER_FORTIFICATION_MISSES'):
            self.assertIn('tag.remove(ModConstants.Tags.' + key + ')', jobs)
        link = jobs.split('public static void linkWorker(', 1)[1].split('public static void unlink(', 1)[0]
        self.assertIn('workerTag.putLong(ModConstants.Tags.PLAYER_FORTIFICATION_POS, area.blockPosition().asLong())', link)
        self.assertIn('workerTag.remove(ModConstants.Tags.PLAYER_FORTIFICATION_MISSES)', link)
        self.assertIn('builder.getPersistentData().remove(STATE)', recovery)
        self.assertIn('if (PerimeterProjectLink.reserved(builder)) return;', jobs)
        self.assertIn('if (slot == hand) { data.remove(PENDING); return null; }', mirror)
        self.assertIn('data.putInt(REBINDS,', mirror)
        self.assertIn('if (handProblem == null) handProblem = ProtectedBuilderHandMirror.restore(builder);', guard)
        self.assertIn('data.remove(STATUS); return true;', guard)
        self.assertIn('PlayerFortificationJobs.linkWorker(builder, area, accepted.owner)', guard)
        self.assertIn('if (!NativeConstructionGuard.retireBuilderAssociation(worker, old)) return false;', projects)
        detached = decision_source().split('private static final Set<String> DETACHED_KEYS', 1)[1].split(';', 1)[0]
        self.assertNotIn('WHOLE', detached)
        self.assertNotIn('STATUS', detached)


if __name__ == '__main__':
    unittest.main()
