"""Execute the actual JDK-only Case C decision; native reads get separate source contracts.

This is not a Forge/vendor type compile or native game execution. The installed JDK's
ToolProvider compiles the extracted production decision with source/target 17.
"""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/nativeQa/java/com/devfarinsky/siegeoverhaul/nativecompat/NativeTerminalHelperProof.java'


def decision_source():
    text = SOURCE.read_text()
    start = text.index('    // BEGIN dependency-free decision:')
    end = text.index('    // END dependency-free decision.', start)
    return text[start:end]


HARNESS = r'''
    static final UUID PROJECT = id(1), HELPER = id(2), OWNER = id(3), COORDINATOR = id(4),
            LEDGER = id(5), AREA = id(6), RECEIPT = id(7), FOREIGN = id(8);
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
    static Facts fixture(boolean detached, boolean pending) {
        var identity = new Identity(PROJECT, 17, "a".repeat(64), "team:fixture", "fixture", OWNER,
                COORDINATOR, "CANCELED", 0, 0, 572, 1, 1, 64, "paid:64:survival", List.of("A0:139", "A1:139"), LEDGER);
        var hand = new Hand(RECEIPT, HELPER, OWNER, AREA, LEDGER);
        var association = new Association(new Selector(PROJECT, 17, "a".repeat(64), "team:fixture", LEDGER), AREA, LEDGER, AREA, OWNER);
        var detachedAssociation = new Association(null, null, null, null, null);
        var data = new LinkedHashMap<String, String>();
        data.put(WHOLE, "exact-selector"); data.put(CHILD, "A0"); data.put(CHILD_GENERATION, "ledger");
        data.put(NATIVE_AREA, "A0"); data.put(NATIVE_OWNER, "owner"); data.put("SiegePlayerFortificationPos", "123L");
        data.put("SiegePlayerFortificationMisses", "0"); data.put("SiegeWallGroundRecovery", "{Time:100L}");
        data.put("SiegeConstructionPause", "original pause");
        data.put("SiegeProtectedHandLifecycle", "exact-original-hand"); data.put("unrelated", "immutable"); data.put(REBINDS, "2");
        var frozen = new Frozen(hand, association, data, 2, true);
        var current = new LinkedHashMap<>(data);
        if (detached) { DETACHED_KEYS.forEach(current::remove); current.put(REBINDS, "3"); }
        if (pending) current.put(HAND_PENDING, "1b");
        var operations = new Operations(-1, false, false, false, false, false, true, true, true, true, false, !pending, pending);
        var actor = new Actor(HELPER, OWNER, hand, detached ? detachedAssociation : association, null, true,
                operations, current, detached ? 3 : 2);
        var member = new Member(0, false, true, false, detached ? 0 : -1, !detached, -1, Set.of(0), -1, detached, false);
        var history = new History(true, true, true, Set.of(AREA), AREA, detached ? null : PROJECT, member);
        return new Facts(identity, identity, HELPER, OWNER, LEDGER, LEDGER, AREA, hand, hand, actor, frozen, history);
    }
    static Facts changeData(Facts f, String key, String value) throws Exception {
        var changed = new LinkedHashMap<>(f.actor().persistent());
        if (value == null) changed.remove(key); else changed.put(key, value);
        return replace(f, "actor.persistent", changed);
    }
    public static void main(String[] args) throws Exception {
        String mode = args[0]; boolean detached = mode.startsWith("detached:"), pending = mode.substring(mode.indexOf(':') + 1).startsWith("pending");
        String mutation = mode.substring(mode.indexOf(':') + 1); Facts f = fixture(detached, pending);
        switch (mutation) {
            case "valid", "pending" -> { }
            case "current-exact" -> f = replace(f, "actor.currentArea", AREA);
            case "current-foreign" -> f = replace(f, "actor.currentArea", FOREIGN);
            case "current-bad-owner" -> { f = replace(f, "actor.currentArea", AREA); f = replace(f, "actor.currentAreaExact", false); }
            case "wrong-helper" -> f = replace(f, "actor.helper", FOREIGN);
            case "wrong-owner" -> f = replace(f, "actor.owner", FOREIGN);
            case "wrong-project" -> f = replace(f, "terminal.project", FOREIGN);
            case "wrong-core" -> f = replace(f, "terminal.core", "team:other");
            case "wrong-hash" -> f = replace(f, "terminal.hash", "b".repeat(64));
            case "wrong-generation" -> f = replace(f, "terminal.generation", 18L);
            case "wrong-terminal-owner" -> f = replace(f, "terminal.owner", FOREIGN);
            case "wrong-coordinator" -> f = replace(f, "terminal.coordinator", FOREIGN);
            case "wrong-ledger" -> f = replace(f, "currentLedger", FOREIGN);
            case "wrong-terminal-ledger" -> f = replace(f, "terminal.ledger", FOREIGN);
            case "wrong-payment" -> f = replace(f, "terminal.payment", "paid:0:creative");
            case "wrong-summary" -> f = replace(f, "terminal.stages", List.of("A0:138", "A1:140"));
            case "wrong-state" -> f = replace(f, "terminal.state", "COMPLETE");
            case "wrong-active-stage" -> f = replace(f, "terminal.activeStage", 1);
            case "wrong-verified" -> f = replace(f, "terminal.verifiedStages", 1);
            case "wrong-targets" -> f = replace(f, "terminal.targets", 573);
            case "wrong-fee" -> f = replace(f, "terminal.price", 128);
            case "wrong-actor-receipt" -> f = replace(f, "actor.hand.receipt", FOREIGN);
            case "wrong-ledger-receipt" -> f = replace(f, "ledgerHand.receipt", FOREIGN);
            case "wrong-frozen-receipt" -> f = replace(f, "frozen.hand.receipt", FOREIGN);
            case "wrong-original-hand-area" -> f = replace(f, "originalHand.area", FOREIGN);
            case "wrong-original-hand-owner" -> f = replace(f, "originalHand.owner", FOREIGN);
            case "wrong-original-hand-generation" -> f = replace(f, "originalHand.generation", FOREIGN);
            case "missing-member" -> f = replace(f, "history.member", null);
            case "unknown-member" -> f = replace(f, "history.known", false);
            case "live-reservation" -> f = replace(f, "history.reservationsAbsent", false);
            case "destruction" -> f = replace(f, "history.destructionAbsent", false);
            case "empty-history" -> f = replace(f, "history.cleanupAreas", Set.of());
            case "foreign-history" -> f = replace(f, "history.cleanupAreas", Set.of(AREA, FOREIGN));
            case "wrong-admission-area" -> f = replace(f, "history.admissionArea", FOREIGN);
            case "wrong-unresolved" -> f = replace(f, "history.unresolved", FOREIGN);
            case "null-unresolved" -> f = replace(f, "history.unresolved", null);
            case "active-member" -> f = replace(f, "history.member.active", true);
            case "not-ready" -> f = replace(f, "history.member.ready", false);
            case "withdrawn" -> f = replace(f, "history.member.withdrawn", true);
            case "rejoining" -> f = replace(f, "history.member.rejoin", 0);
            case "new-admission" -> f = replace(f, "history.member.admitted", 1);
            case "new-grant" -> f = replace(f, "history.member.grants", Set.of(0, 1));
            case "missing-grant" -> f = replace(f, "history.member.grants", Set.of());
            case "legacy-prefix" -> f = replace(f, "history.member.legacyEnd", 0);
            case "member-destroyed" -> f = replace(f, "history.member.destroyed", true);
            case "early-resolution" -> f = replace(f, "history.member.resolved", true);
            case "not-resolved" -> f = replace(f, "history.member.resolved", false);
            case "still-suspended" -> f = replace(f, "history.member.suspended", true);
            case "not-suspended" -> f = replace(f, "history.member.suspended", false);
            case "wrong-retirement" -> f = replace(f, "history.member.retired", 1);
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
            case "missing-native" -> f = replace(f, "actor.association.nativeArea", null);
            case "foreign-native" -> f = replace(f, "actor.association.nativeArea", FOREIGN);
            case "missing-native-owner" -> f = replace(f, "actor.association.nativeOwner", null);
            case "foreign-native-owner" -> f = replace(f, "actor.association.nativeOwner", FOREIGN);
            case "frozen-detached" -> f = replace(f, "frozen.association", new Association(null, null, null, null, null));
            case "frozen-not-closed" -> f = replace(f, "frozen.closed", false);
            case "retained-child" -> f = replace(f, "actor.association.child", AREA);
            case "retained-native-owner" -> f = replace(f, "actor.association.nativeOwner", OWNER);
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
            case "retained-position" -> f = changeData(f, "SiegePlayerFortificationPos", "123L");
            case "retained-pause" -> f = changeData(f, "SiegeConstructionPause", "original pause");
            case "removed-pause" -> f = changeData(f, "SiegeConstructionPause", null);
            case "changed-pause" -> f = changeData(f, "SiegeConstructionPause", "foreign pause");
            case "stale-restored" -> {
                f = replace(f, "actor.rebinds", 3); f = changeData(f, REBINDS, "3");
                f = changeData(f, "SiegeConstructionPause", null);
            }
            case "changed-position" -> f = changeData(f, "SiegePlayerFortificationPos", "124L");
            case "omitted-native-data" -> f = changeData(f, NATIVE_AREA, null);
            case "pending-omitted-arm" -> f = changeData(f, HAND_PENDING, null);
            case "pending-false-arm" -> f = changeData(f, HAND_PENDING, "0b");
            case "spurious-pending" -> f = changeData(f, HAND_PENDING, "1b");
            case "rebind-backward" -> { f = replace(f, "actor.rebinds", 1); f = changeData(f, REBINDS, "1"); }
            case "rebind-too-many" -> { f = replace(f, "actor.rebinds", 5); f = changeData(f, REBINDS, "5"); }
            case "rebind-no-persistent-proof" -> f = replace(f, "actor.rebinds", 4);
            case "same-tick-cleanup" -> {
                if (!detached) throw new AssertionError();
                // Already-aliased restoration closes pending and removes pause without an increment.
                f = replace(f, "actor.rebinds", 2); f = changeData(f, REBINDS, "2");
            }
            case "return-restored" -> { f = replace(f, "actor.rebinds", 4); f = changeData(f, REBINDS, "4"); }
            default -> throw new AssertionError("Unknown mutation " + mutation);
        }
        var before = f.toString(); Proof proof = decide(f);
        if (!before.equals(f.toString())) throw new AssertionError("Decision mutated its input");
        System.out.println(proof.state() + ":" + proof.reason());
        System.out.println(proof.evidence());
    }
'''


class TerminalProofJavaTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.java = shutil.which('java')
        if not cls.java:
            raise RuntimeError('Java with jdk.compiler is required')
        cls.temp = tempfile.TemporaryDirectory()
        cls.root = Path(cls.temp.name)
        harness = cls.root / 'TerminalHarness.java'
        harness.write_text('import java.util.*;\npublic final class TerminalHarness {\n' + decision_source() + HARNESS + '}\n')
        launcher = cls.root / 'CompileTerminal.java'
        launcher.write_text('''import javax.tools.ToolProvider;
class CompileTerminal { public static void main(String[] a) {
    var c = ToolProvider.getSystemJavaCompiler();
    if (c == null) throw new IllegalStateException("jdk.compiler unavailable");
    System.exit(c.run(null, null, null, "--source", "17", "--target", "17", "-d", a[0], a[1]));
}}\n''')
        result = subprocess.run([cls.java, str(launcher), str(cls.root), str(harness)],
                                capture_output=True, text=True, timeout=30)
        if result.returncode:
            raise AssertionError('Actual decision did not compile as Java 17: ' + result.stdout + result.stderr)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def check(self, mode, state='REJECTED'):
        result = subprocess.run([self.java, '-cp', str(self.root), 'TerminalHarness', mode],
                                capture_output=True, text=True, timeout=10)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertTrue(result.stdout.startswith(state + ':'), mode + '\n' + result.stdout)
        return result.stdout

    def test_exact_stale_and_detached_states(self):
        self.check('stale:valid', 'STALE_UNRESOLVED')
        self.check('stale:current-exact', 'STALE_UNRESOLVED')
        self.check('stale:stale-restored', 'STALE_UNRESOLVED')
        self.check('detached:valid', 'DETACHED_RESOLVED')
        self.check('detached:same-tick-cleanup', 'DETACHED_RESOLVED')
        self.check('detached:return-restored', 'DETACHED_RESOLVED')

    def test_pending_load_proof_accepts_exact_stale_and_detached_before_restoration(self):
        self.assertIn('pendingHand=true', self.check('stale:pending', 'STALE_UNRESOLVED'))
        self.assertIn('pendingHand=true', self.check('detached:pending', 'DETACHED_RESOLVED'))

    def test_native_and_terminal_identity_mutations(self):
        for mutation in ('wrong-helper', 'wrong-owner', 'wrong-project', 'wrong-core', 'wrong-hash',
                         'wrong-generation', 'wrong-terminal-owner', 'wrong-coordinator', 'wrong-ledger',
                         'wrong-terminal-ledger', 'wrong-payment', 'wrong-summary', 'wrong-state',
                         'wrong-active-stage', 'wrong-verified', 'wrong-targets', 'wrong-fee',
                         'wrong-actor-receipt', 'wrong-ledger-receipt', 'wrong-frozen-receipt',
                         'wrong-original-hand-area', 'wrong-original-hand-owner', 'wrong-original-hand-generation'):
            for state in ('stale', 'detached'):
                with self.subTest(state=state, mutation=mutation):
                    self.check(state + ':' + mutation)

    def test_known_exact_history_without_new_work_death_or_dismissal(self):
        for mutation in ('missing-member', 'unknown-member', 'live-reservation', 'destruction', 'empty-history',
                         'foreign-history', 'wrong-admission-area', 'wrong-unresolved', 'active-member', 'not-ready',
                         'withdrawn', 'rejoining', 'new-admission', 'new-grant', 'missing-grant', 'legacy-prefix',
                         'member-destroyed', 'wrong-retirement'):
            for state in ('stale', 'detached'):
                with self.subTest(state=state, mutation=mutation):
                    self.check(state + ':' + mutation)
        for mode in ('stale:null-unresolved', 'stale:early-resolution', 'stale:not-suspended',
                     'detached:not-resolved', 'detached:still-suspended'):
            self.check(mode)

    def test_no_loose_optional_association_channels(self):
        for mutation in ('missing-selector', 'foreign-selector', 'selector-generation', 'selector-hash', 'selector-core',
                         'selector-ledger', 'frozen-selector-ledger', 'missing-child', 'foreign-child',
                         'foreign-child-generation', 'missing-native', 'foreign-native', 'missing-native-owner',
                         'foreign-native-owner', 'current-foreign', 'current-bad-owner'):
            with self.subTest(mutation=mutation):
                self.check('stale:' + mutation)
        for mutation in ('retained-child', 'retained-native-owner', 'current-exact', 'current-foreign'):
            self.check('detached:' + mutation)
        for state in ('stale', 'detached'):
            self.check(state + ':frozen-detached')
            self.check(state + ':frozen-not-closed')

    def test_canonical_operations_and_pending_hand_bounds(self):
        for mutation in ('borrowed-hand', 'active-use', 'review', 'requests', 'cleanup', 'storage',
                         'nonordinary', 'nonfinite', 'unequal-hand', 'noncanonical', 'third-slot-alias',
                         'unobserved-split', 'pending-requests', 'pending-unequal-hand', 'pending-noncanonical',
                         'pending-third-slot-alias'):
            for state in ('stale', 'detached'):
                with self.subTest(state=state, mutation=mutation):
                    self.check(state + ':' + mutation)

    def test_only_exact_authorized_frozen_persistent_deltas(self):
        for mutation in ('changed-unrelated', 'missing-unrelated', 'new-unrelated', 'changed-hand-tag',
                         'omitted-hand-tag', 'changed-position', 'changed-pause', 'pending-omitted-arm', 'pending-false-arm',
                         'spurious-pending', 'rebind-backward', 'rebind-too-many', 'rebind-no-persistent-proof'):
            for state in ('stale', 'detached'):
                with self.subTest(state=state, mutation=mutation):
                    self.check(state + ':' + mutation)
        self.check('stale:omitted-native-data')
        self.check('detached:retained-position')
        self.check('detached:retained-pause')
        self.check('stale:removed-pause')


class TerminalProofSourceContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = SOURCE.read_text()
        cls.native = cls.text.split('    // END dependency-free decision.', 1)[1]

    def test_no_production_repair_world_load_or_cache_calls(self):
        for forbidden in ('ConstructionEditLedger.get(', 'PerimeterProjectAuthority.snapshot(', 'validHandReceipt(',
                          'retireBuilderAssociation(', 'resolveCrewAssociation(', 'reconcileWorker(', 'sealCrewHistory(',
                          'setDirty(', '.getChunk(', '.getBlockState(', '.getBlockEntity(', '.setItem', '.tick(',
                          '.arm(', '.restore(', '.setPos(', '.putUUID(', '.putBoolean('):
            with self.subTest(forbidden=forbidden):
                self.assertNotIn(forbidden, self.text)
        self.assertNotIn('worker.isAlive()', self.native)
        self.assertNotIn('worker.isRemoved()', self.native)
        self.assertIn('worker.getPersistentData().copy()', self.native)
        self.assertIn('finalizedOffline.persistent()', self.native)
        self.assertIn('ledger.save(new CompoundTag())', self.native)
        self.assertNotRegex(self.native, r'\bdata\.(?:put|remove)\s*\(')

    def test_native_actor_receipt_history_and_channels_are_all_read(self):
        for required in ('worker.getUUID()', 'worker.getOwnerUUID()', 'ledger.handLifecycle(helper)',
                         'ProtectedBuilderHandLifecycle.read(', 'PerimeterProjectLink.read(data)',
                         'PerimeterProjectLink.ledgerGeneration(data)', 'worker.currentBuildArea',
                         'WorkersBridge.readOwner(current)', 'PerimeterProjectAuthority.read(current.getPersistentData())',
                         'ledger.knownTerminalCrewMember(terminal, helper)', 'ledger.workerCleanupAreas(terminal, helper)',
                         'ledger.crewAdmissionArea(terminal, helper)', 'ledger.unresolvedCrewProject(helper)',
                         'ledger.terminalCrewBuilderDestructionReceipt(terminal, helper)',
                         'ledger.sameGeneration(ledgerGeneration)', 'ledger.contains(terminal.projectId())'):
            with self.subTest(required=required):
                self.assertIn(required, self.native)
        self.assertIn('members.size() != 1', self.native)
        self.assertIn('new long[]{1L, 0L, 0L, 0L}', self.native)
        for key in ('Active', 'Ready', 'Withdrawn', 'RetiredStage', 'AdmittedStage', 'Suspended', 'RejoinStage', 'LegacyEnd', 'Resolved'):
            self.assertRegex(self.native, r'(?:integer|bool)\(saved, "' + key + r'"\)')

    def test_native_operations_are_read_without_repair_or_pending_exemption(self):
        for required in ('worker.getBeforeItemSlot()', 'ProtectedBuilderHandMirror.activeUse(worker)',
                         'worker.neededItems == null || !worker.neededItems.isEmpty()', 'ProtectedInventoryCleanup.read(data)',
                         'ProtectedStorageAccess.runningProblem(worker)', 'worker.forcedDeposit', 'worker.getFollowState() == 6',
                         'Double.isFinite(', 'Float.isFinite(', 'ProtectedBuilderHandMirror.sameValue(slot, main)',
                         'stack == slot || stack == main', 'stack.getCount() >= 0',
                         'Math.min(inventory.getMaxStackSize(), stack.getMaxStackSize())'):
            with self.subTest(required=required):
                self.assertIn(required, self.native)
        self.assertIn('pending || mainIdentity', decision_source())
        self.assertIn('&& ordinary && finitePose && equalHandValue && canonicalCounts && !thirdSlotAlias', decision_source())

    def test_existing_production_keys_and_cleanup_delta_are_pinned(self):
        guard = (ROOT / 'src/main/java/com/devfarinsky/siegeoverhaul/nativecompat/NativeConstructionGuard.java').read_text()
        jobs = (ROOT / 'src/main/java/com/devfarinsky/siegeoverhaul/core/PlayerFortificationJobs.java').read_text()
        recovery = (ROOT / 'src/main/java/com/devfarinsky/siegeoverhaul/core/BuilderGroundRecovery.java').read_text()
        mirror = (ROOT / 'src/main/java/com/devfarinsky/siegeoverhaul/nativecompat/ProtectedBuilderHandMirror.java').read_text()
        self.assertIn('data.remove(STATUS)', guard)
        self.assertIn('STATUS = "SiegeConstructionPause"', guard)
        for key in ('SiegeProtectedAreaReceipt', 'SiegeProtectedLedgerGeneration'):
            self.assertIn('"' + key + '"', guard)
        for key in ('PLAYER_FORTIFICATION_AREA_ID', 'PLAYER_FORTIFICATION_OWNER', 'PLAYER_FORTIFICATION_POS', 'PLAYER_FORTIFICATION_MISSES'):
            self.assertIn('tag.remove(ModConstants.Tags.' + key + ')', jobs)
        self.assertIn('"SiegeWallGroundRecovery"', recovery)
        self.assertIn('builder.getPersistentData().remove(STATE)', recovery)
        for key in ('SiegeProtectedHandRefreshPending', 'SiegeProtectedHandRebinds'):
            self.assertIn('"' + key + '"', mirror)


if __name__ == '__main__':
    unittest.main()
