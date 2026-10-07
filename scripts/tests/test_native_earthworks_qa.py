"""Verifier rejection tests and source contracts only; these are not native gameplay evidence."""
import copy
import importlib.util
from pathlib import Path
import shutil
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


VERIFY = load('verify_earthworks', ROOT / 'scripts/verify-native-earthworks.py')
PREPARE = load('prepare_earthworks', ROOT / 'scripts/prepare-native-earthworks-client.py')


def row(tick, values, phase, receipts=0):
    return dict(zip(('chestDirt', 'workerDirt', 'worldDirt', 'looseDirt'), values),
                gameTime=tick, workerTickCount=tick, nativeStorageState=phase, supplyReceiptCount=receipts,
                requestCount=1 if phase == 'TAKE_NEEDED_ITEMS' else 0,
                journalState='STAGE_VERIFIED' if values[2] == 1 else 'READY',
                journalReceiptCount=values[2], cleanupOutstanding=False)


def illustrative_receipt():
    before = row(100, [2, 0, 0, 0], 'SELECT_STORAGE')
    take = row(200, [2, 0, 0, 0], 'TAKE_NEEDED_ITEMS')
    carried = row(201, [1, 1, 0, 0], 'CLOSE_CHEST_DONE', 1)
    after = row(240, [1, 0, 1, 0], 'DONE', 1)
    return {
        'status': 'passed', 'mode': 'earthworks-one-fill', 'ordinaryTicksOnly': True,
        'seededCompletedTargets': 0, 'postStartTeleports': 0, 'sourceStock': 2, 'journalState': 'STAGE_VERIFIED',
        'scope': 'Synthetic flat example for verifier tests only', 'syntheticSetup': ['Test object only'],
        'notCovered': ['CUT', 'Multi-step', 'ascent', 'COMPLETE', 'restart', 'v1', 'PR276'],
        'identity': dict.fromkeys(('owner', 'builder', 'project', 'area'), '12345678-1234-1234-1234-123456789abc') | {
            'actualPlayerListMember': True, 'normalProfileCacheMatched': True, 'manifestHash': 'a' * 64},
        'loadedModVersions': {'workers': '2.0.3', 'recruits': '1.15.2'},
        'loadedCompanionArtifacts': {mod: {'fileName': name, 'sha256': 'b' * 64} for mod, name in VERIFY.ARTIFACTS.items()},
        'groundingWarmup': {'ordinaryTicks': 2, 'startGameTime': 98, 'settledGameTime': 100,
                            'actualOnGround': True, 'unassigned': True, 'targetAir': True, 'bodyMinY': 65.0},
        'before': before, 'after': row(280, [1, 0, 1, 0], 'DONE', 1), 'ordinaryTicksObserved': 180, 'stabilityTicks': 40,
        'acceptedGameTime': 100, 'observedStartGameTime': 100, 'observedEndGameTime': 280,
        'observedTickCount': 180, 'stableSinceGameTime': 240,
        'nativeDispatchSequence': 1, 'nativeTransferTicks': 1, 'requestObservations': 20, 'repeatedAccepts': 3,
        'maximumWorkerDisplacement': .8, 'movementWhileNativeStorage': .7,
        'nativeStoragePhases': list(VERIFY.PHASES),
        'refusals': [{'case': name, 'reason': 'test refusal', 'noExtraDebit': True} for name in VERIFY.REFUSALS],
        'treasury': {'funding': 64, 'debit': 64, 'debitCount': 1, 'finalBalance': 0, 'observedTaxes': 0},
        'nativeAccountingReceipt': 'c' * 64, 'supplyReceipt': 'illustrative receipt',
        'samples': [row(101, [2, 0, 0, 0], 'SELECT_STORAGE'), carried, after, row(280, [1, 0, 1, 0], 'DONE', 1)],
        'transitions': [{'event': 'native-source-to-worker', 'before': take, 'after': carried},
                        {'event': 'native-worker-to-world', 'before': row(239, [1, 1, 0, 0], 'DONE', 1), 'after': after}]
    }


class EvidenceVerifierTest(unittest.TestCase):
    def setUp(self):
        self.receipt = illustrative_receipt()

    def test_accepts_only_complete_structural_receipt(self):
        self.assertIs(VERIFY.validate(self.receipt), self.receipt)

    def test_rejects_backward_custody_windows(self):
        for event in self.receipt['transitions']:
            event['before'] = copy.deepcopy(event['before'])
            event['after'] = copy.deepcopy(event['after'])
            event['before']['gameTime'] = event['before']['workerTickCount'] = 99999
            event['after']['gameTime'] = event['after']['workerTickCount'] = 0
        with self.assertRaises(ValueError): VERIFY.validate(self.receipt)

    def test_rejects_stability_exceeding_measured_work(self):
        self.receipt['stabilityTicks'] = 10000
        self.receipt['ordinaryTicksObserved'] = 1
        with self.assertRaises(ValueError): VERIFY.validate(self.receipt)

    def test_rejects_terminal_summary_disagreeing_with_sample(self):
        self.receipt['after'] = copy.deepcopy(self.receipt['after'])
        self.receipt['after'].update(journalState='PENDING', requestCount=5, supplyReceiptCount=0)
        with self.assertRaises(ValueError): VERIFY.validate(self.receipt)

    def test_rejects_wrong_native_fill_worker_cadence(self):
        self.receipt['transitions'][1]['after'] = copy.deepcopy(self.receipt['transitions'][1]['after'])
        self.receipt['transitions'][1]['after']['workerTickCount'] = 241
        with self.assertRaises(ValueError): VERIFY.validate(self.receipt)

    def test_rejects_coherent_but_wrong_native_fill_cadence(self):
        for event in self.receipt['transitions']:
            for side in ['before', 'after']:
                event[side] = copy.deepcopy(event[side])
        for sample in [self.receipt['before'], self.receipt['after'], *self.receipt['samples'],
                       *[event[side] for event in self.receipt['transitions'] for side in ['before', 'after']]]:
            sample['workerTickCount'] += 1
        with self.assertRaisesRegex(ValueError, 'cadence'): VERIFY.validate(self.receipt)

    def test_rejects_skipped_observer_tick(self):
        self.receipt['observedTickCount'] -= 1
        with self.assertRaises(ValueError): VERIFY.validate(self.receipt)

    def test_rejects_unobserved_stable_start(self):
        self.receipt['stableSinceGameTime'] = 239
        self.receipt['stabilityTicks'] = 41
        with self.assertRaises(ValueError): VERIFY.validate(self.receipt)

    def test_rejects_extra_terminal_native_receipt(self):
        self.receipt['after']['journalReceiptCount'] = 2
        with self.assertRaises(ValueError): VERIFY.validate(self.receipt)

    def test_rejects_false_terminal_cleanup(self):
        self.receipt['after']['cleanupOutstanding'] = True
        with self.assertRaises(ValueError): VERIFY.validate(self.receipt)

    def test_rejects_unbound_custody_outcome(self):
        self.receipt['transitions'][0]['after'] = copy.deepcopy(self.receipt['transitions'][0]['after'])
        self.receipt['transitions'][0]['after']['cleanupOutstanding'] = True
        with self.assertRaises(ValueError): VERIFY.validate(self.receipt)

    def test_rejects_more_requests_than_observed_ticks(self):
        self.receipt['requestObservations'] = self.receipt['observedTickCount'] + 1
        with self.assertRaises(ValueError): VERIFY.validate(self.receipt)

    def test_rejects_unbound_first_observation(self):
        self.receipt['samples'].pop(0)
        with self.assertRaises(ValueError): VERIFY.validate(self.receipt)

    def test_rejects_world_fill_before_actual_source_transfer(self):
        self.receipt['samples'].insert(1, row(150, [1, 0, 1, 0], 'DONE', 1))
        with self.assertRaises(ValueError): VERIFY.validate(self.receipt)

    def test_rejects_world_reversion_before_delayed_stable_window(self):
        self.receipt['after'] = row(300, [1, 0, 1, 0], 'DONE', 1)
        self.receipt.update(observedEndGameTime=300, ordinaryTicksObserved=200, observedTickCount=200,
                            stableSinceGameTime=260, stabilityTicks=40)
        self.receipt['samples'].insert(3, row(250, [1, 1, 0, 0], 'DONE', 1))
        self.receipt['samples'].insert(4, row(260, [1, 0, 1, 0], 'DONE', 1))
        with self.assertRaises(ValueError): VERIFY.validate(self.receipt)

    def test_rejects_cargo_before_source_transfer(self):
        self.receipt['samples'].insert(1, row(150, [1, 1, 0, 0], 'DONE', 1))
        with self.assertRaises(ValueError): VERIFY.validate(self.receipt)

    def test_rejects_replacement_request_after_delivery(self):
        delivered = row(210, [1, 1, 0, 0], 'CLOSE_CHEST_DONE', 1)
        delivered['requestCount'] = 1
        self.receipt['samples'].insert(2, delivered)
        with self.assertRaises(ValueError): VERIFY.validate(self.receipt)

    def test_rejects_invented_or_zero_tick_grounding_warmup(self):
        self.receipt['groundingWarmup']['ordinaryTicks'] = 0
        with self.assertRaises(ValueError): VERIFY.validate(self.receipt)

    def test_rejects_grounding_after_accepted_work_started(self):
        self.receipt['groundingWarmup'].update(startGameTime=100, settledGameTime=102)
        with self.assertRaises(ValueError): VERIFY.validate(self.receipt)

    def test_rejects_warmup_work_or_missing_actual_grounding(self):
        self.receipt['groundingWarmup']['actualOnGround'] = False
        with self.assertRaises(ValueError): VERIFY.validate(self.receipt)

    def test_rejects_failed_run(self):
        self.receipt['status'] = 'failed'
        with self.assertRaises(ValueError): VERIFY.validate(self.receipt)

    def test_rejects_seeded_target(self):
        self.receipt['before']['chestDirt'] = 1
        self.receipt['before']['worldDirt'] = 1
        with self.assertRaises(ValueError): VERIFY.validate(self.receipt)

    def test_rejects_duplicated_source_item(self):
        self.receipt['samples'][1]['chestDirt'] = 2
        with self.assertRaises(ValueError): VERIFY.validate(self.receipt)

    def test_rejects_transfer_without_actual_native_phase(self):
        self.receipt['transitions'][0]['before']['nativeStorageState'] = 'SELECT_STORAGE'
        with self.assertRaises(ValueError): VERIFY.validate(self.receipt)

    def test_rejects_missing_movement(self):
        self.receipt['movementWhileNativeStorage'] = 0
        with self.assertRaises(ValueError): VERIFY.validate(self.receipt)

    def test_rejects_missing_source_receipt(self):
        self.receipt['transitions'][0]['after']['supplyReceiptCount'] = 0
        with self.assertRaises(ValueError): VERIFY.validate(self.receipt)

    def test_rejects_repeated_native_dispatch(self):
        self.receipt['nativeDispatchSequence'] = 2
        with self.assertRaises(ValueError): VERIFY.validate(self.receipt)

    def test_rejects_missing_refusal(self):
        self.receipt['refusals'].pop()
        with self.assertRaises(ValueError): VERIFY.validate(self.receipt)

    def test_rejects_extra_payment(self):
        self.receipt['treasury']['debitCount'] = 2
        with self.assertRaises(ValueError): VERIFY.validate(self.receipt)

    def test_rejects_invented_complete(self):
        self.receipt['journalState'] = 'COMPLETE'
        with self.assertRaises(ValueError): VERIFY.validate(self.receipt)

    def test_rejects_absent_tick_progress(self):
        self.receipt['samples'][1]['workerTickCount'] = 0
        with self.assertRaises(ValueError): VERIFY.validate(self.receipt)

    def test_rejects_missing_custody_transition(self):
        self.receipt['transitions'].pop()
        with self.assertRaises(ValueError): VERIFY.validate(self.receipt)

    def test_rejects_wrong_native_parser_artifact(self):
        self.receipt['loadedCompanionArtifacts']['workers']['fileName'] = 'newer-workers.jar'
        with self.assertRaises(ValueError): VERIFY.validate(self.receipt)

    def test_rejects_omitted_scope_limitations(self):
        self.receipt['notCovered'] = []
        with self.assertRaises(ValueError): VERIFY.validate(self.receipt)


class FreshDefaultsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        (self.root / 'gradle').mkdir()
        shutil.copy(ROOT / 'build.gradle', self.root / 'build.gradle')
        shutil.copy(ROOT / 'gradle/native-earthworks-qa.gradle', self.root / 'gradle/native-earthworks-qa.gradle')
        shutil.copytree(ROOT / 'scripts/fixtures', self.root / 'scripts/fixtures')

    def tearDown(self): self.temp.cleanup()

    def test_new_defaults_are_exact_and_retry_is_read_only(self):
        directory = PREPARE.prepare(self.root)
        self.assertEqual(PREPARE.prepare(self.root), directory)
        self.assertEqual((directory / 'config/smallships-common.toml').read_bytes(),
                         (self.root / 'scripts/fixtures/smallships-2.0.0-b1.4/smallships-common.toml').read_bytes())

    def test_refuses_preexisting_directory(self):
        (self.root / 'build/native-earthworks-qa/client').mkdir(parents=True)
        with self.assertRaises(ValueError): PREPARE.prepare(self.root)

    def test_refuses_existing_world(self):
        directory = PREPARE.prepare(self.root)
        (directory / 'saves').mkdir()
        with self.assertRaises(ValueError): PREPARE.prepare(self.root)

    def test_refuses_changed_companion(self):
        path = self.root / 'gradle/native-earthworks-qa.gradle'
        path.write_text(path.read_text().replace('5566900', '9999999'))
        with self.assertRaises(ValueError): PREPARE.prepare(self.root)


class RuntimeSourceContractTest(unittest.TestCase):
    def test_separate_source_set_and_no_packaging(self):
        script = (ROOT / 'gradle/native-earthworks-qa.gradle').read_text()
        self.assertIn("gradleProperty('nativeEarthworksQa')", script)
        self.assertIn("java.srcDir 'src/nativeEarthworksQa/java'", script)
        self.assertNotIn('sourceSets.main.java', script)
        self.assertNotIn("tasks.named('jar'", script)
        self.assertIn('isolated invocation', script)

    def test_real_player_and_no_manual_native_tick_or_placement(self):
        qa = (ROOT / 'src/nativeEarthworksQa/java/com/devfarinsky/siegeoverhaul/nativecompat/NativeEarthworksQa.java').read_text()
        fixture = (ROOT / 'src/nativeEarthworksQa/java/com/devfarinsky/siegeoverhaul/nativecompat/NativeEarthworksFixture.java').read_text()
        self.assertIn('getPlayerList().getPlayer', qa)
        self.assertIn('getProfileCache()', fixture)
        self.assertNotIn('FakePlayer', qa + fixture)
        self.assertNotIn('.tick()', qa + fixture)
        self.assertNotIn('setBlock(TARGET, Blocks.DIRT', qa + fixture)
        self.assertNotIn('teleportTo', qa)
        self.assertEqual(qa.count('setNoAi(false)'), 2)
        self.assertIn('now > groundingStartTick', qa)
        self.assertNotIn('setOnGround', qa + fixture)
        self.assertIn('wrappers.get(0).delegate == fixture.originalStorageGoal()', qa)
        self.assertIn('BuildBlockParse.parseBlock(Blocks.DIRT)', qa)
        self.assertIn('BlockPos.betweenClosed(target.offset(-2, -2, -2), target.offset(2, 2, 2))', fixture)

    def test_setup_diagnostics_distinguish_grounding_and_treasury_without_changing_them(self):
        qa = (ROOT / 'src/nativeEarthworksQa/java/com/devfarinsky/siegeoverhaul/nativecompat/NativeEarthworksQa.java').read_text()
        for field in ['actualOnGround', 'noAi', 'noGravity', 'velocity', 'bodyBounds', 'followState', 'nativeGoals',
                      'bodyBlocks', 'bodyNoCollision', 'sturdyUp', 'hasSettlementTimestamp', 'settlementGameTime',
                      'elapsedSetupTicks', 'expectedPreFundingBalance']:
            self.assertIn('"' + field + '"', qa)
        self.assertNotIn('setOnGround', qa)
        self.assertNotIn('setNoGravity', qa)
        self.assertEqual(qa.count('setNoAi(false)'), 2)

    def test_read_only_workflow_and_real_runtime_gate(self):
        workflow = (ROOT / '.github/workflows/native-earthworks-qa.yml').read_text()
        self.assertIn('contents: read', workflow)
        self.assertIn('persist-credentials: false', workflow)
        self.assertIn('native-earthworks-qa', workflow)
        self.assertIn('prepareNativeEarthworksQaClient', workflow)
        self.assertIn('verify-native-earthworks.py', workflow)
        self.assertIn('audit-native-earthworks-bytecode.py', workflow)
        self.assertIn('--offline', workflow)
        self.assertNotIn('secrets.', workflow)


if __name__ == '__main__': unittest.main()
