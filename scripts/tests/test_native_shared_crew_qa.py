"""Fail-closed evidence contracts, using synthetic records; these do not run Minecraft."""
import copy
import importlib.util
from pathlib import Path
import tempfile
import unittest

REPO = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('handoff_verifier', REPO / 'scripts/verify-native-staged-handoff.py')
VERIFIER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(VERIFIER)
IDS = ['11111111-1111-4111-8111-111111111111', '22222222-2222-4222-8222-222222222222']


def record(worker, kind, tick, x=128, z=0):
    supplied = kind == 'native-supply-withdrawal'
    return dict(builder=worker, area='area-0', stage=0, gameTime=tick,
                nativeStateBefore='GET_ITEMS' if supplied else 'PLACE_BLOCKS', kind=kind,
                cells=[] if supplied else [dict(packed=(x << 38) | (z << 12) | 65,
                    position=f'{x}, 65, {z}', material='minecraft:cobblestone')],
                cobbleBefore=0 if supplied else 64, cobbleAfter=64 if supplied else 63,
                oakBefore=0, oakAfter=0, chestCobbleBefore=352, chestCobbleAfter=288 if supplied else 352,
                chestOakBefore=220, chestOakAfter=220)


def evidence():
    cargo = {worker: dict(slots=['{}'] * 10, main='{}', off='{}') for worker in IDS}
    events = [record(IDS[0], 'native-supply-withdrawal', 12), record(IDS[1], 'native-supply-withdrawal', 13),
              record(IDS[0], 'native-placement', 16), record(IDS[1], 'native-placement', 17, z=1),
              record(IDS[0], 'native-placement', 31, z=2), record(IDS[1], 'native-placement', 32, z=3),
              record(IDS[1], 'native-placement', 51, z=4)]
    return dict(sharedCrewRequested=True, sharedCrewVerified=True, builderReplacementRequested=False,
                sharedCrewBuilders=IDS, sourceCommit='a' * 40, sourceTree='b' * 40,
                projectId='project', manifestHash='c' * 64, commissionGameTime=10,
                sharedCrewAdmission=dict(coordinator=IDS[0], helper=IDS[1], project='project',
                    manifestHash='c' * 64, owner='33333333-3333-4333-8333-333333333333', area='area-0', gameTime=11),
                stageLayout=[dict(area='area-0')], sharedCrewEvents=events,
                sharedCrewPlacementCounts={IDS[0]: 2, IDS[1]: 3}, sharedCrewDistinctTargets=5, retainedPlacedBlocks=5,
                sharedCrewSuccession=dict(deadCoordinator=IDS[0], survivingHelper=IDS[1], placedBefore=4, placedAfter=5,
                    deathGameTime=40, resumeGameTime=51, manifestHash='c' * 64, extraCommission=False, newBuilderSpawnedAfterDeath=False),
                sharedCrewPartialReloadVerified=True,
                **{'sharedCrewCargoReload-mid-stage': dict(stopped=cargo, loaded=copy.deepcopy(cargo))},
                restarts=[dict(kind='mid-stage', gameTime=30)],
                sharedCrewInterruption='Spectator pause', sharedCrewSleepCoverage='Not exercised: fixed noon',
                sharedCrewAttribution='LivingTick observer', sharedCrewGeometry='explicit legacy hollow fixture')


class SharedCrewEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        (self.root / 'source-commit.txt').write_text('a' * 40 + '\n')
        (self.root / 'source-tree.txt').write_text('b' * 40 + '\n')

    def verify(self, data):
        VERIFIER.verify_shared_crew(data, self.root)

    def test_complete_synthetic_contract(self):
        self.verify(evidence())

    def test_rejects_unrequested_or_mixed_mode(self):
        for key, value in [('sharedCrewRequested', False), ('sharedCrewVerified', False), ('builderReplacementRequested', True)]:
            data = evidence(); data[key] = value
            with self.subTest(key=key), self.assertRaises(AssertionError): self.verify(data)

    def test_rejects_misattribution_or_duplicate_cell(self):
        for mutation in [lambda d: d['sharedCrewEvents'][3].update(builder=IDS[0]),
                         lambda d: d['sharedCrewEvents'][3].update(cells=copy.deepcopy(d['sharedCrewEvents'][2]['cells'])),
                         lambda d: d['sharedCrewEvents'][3].update(cobbleAfter=64),
                         lambda d: d['sharedCrewEvents'][3].update(chestCobbleAfter=351)]:
            data = evidence(); mutation(data)
            with self.subTest(mutation=mutation), self.assertRaises(AssertionError): self.verify(data)

    def test_rejects_replacement_hire_or_another_fee(self):
        for key in ['extraCommission', 'newBuilderSpawnedAfterDeath']:
            data = evidence(); data['sharedCrewSuccession'][key] = True
            with self.subTest(key=key), self.assertRaises(AssertionError): self.verify(data)

    def test_rejects_non_resuming_helper_or_bad_supply(self):
        for mutation in [lambda d: d['sharedCrewEvents'][5].update(gameTime=29),
                         lambda d: d['sharedCrewEvents'][1].update(chestCobbleAfter=352),
                         lambda d: d['sharedCrewEvents'][3]['cells'][0].update(position='0, 65, 0')]:
            data = evidence(); mutation(data)
            with self.subTest(mutation=mutation), self.assertRaises(AssertionError): self.verify(data)

    def test_rejects_missing_or_changed_reload_cargo(self):
        for mutation in [lambda d: d['sharedCrewCargoReload-mid-stage']['loaded'].pop(IDS[1]),
                         lambda d: d['sharedCrewCargoReload-mid-stage']['loaded'][IDS[1]].update(main='{Count:64b}')]:
            data = evidence(); mutation(data)
            with self.subTest(mutation=mutation), self.assertRaises(AssertionError): self.verify(data)

    def test_rejects_runtime_source_mismatch(self):
        data = evidence(); data['sourceCommit'] = 'd' * 40
        with self.assertRaises(AssertionError): self.verify(data)

    def test_opt_in_is_separate_default_false_and_alpha_base_limited(self):
        build = (REPO / 'build.gradle').read_text()
        self.assertIn("gradleProperty('nativeSharedCrewQa').map { it.toBoolean() }.getOrElse(false)", build)
        self.assertIn("sharedCrewQa && nativeQaMode != 'staged-handoff'", build)
        self.assertIn('Shared crew and builder replacement QA are separate scenarios', build)
        workflow = (REPO / '.github/workflows/native-staged-handoff-qa.yml').read_text()
        self.assertIn('branches: [main, release/4.53.0-alpha.3]', workflow)
        self.assertIn("github.event.pull_request.base.ref == 'release/4.53.0-alpha.3' && contains(github.event.pull_request.labels.*.name, 'native-shared-crew-qa')", workflow)
        self.assertIn('default: false', workflow)
        self.assertNotIn('secrets.', workflow)
        self.assertNotIn('contents: write', workflow)
        self.assertNotIn('pull-requests: write', workflow)
        self.assertEqual(2, workflow.count('-PnativeSharedCrewQa=$SHARED_CREW_QA'))

    def test_observer_does_not_run_goals_or_write_targets(self):
        java = (REPO / 'src/nativeQa/java/com/devfarinsky/siegeoverhaul/nativecompat/NativeStagedHandoffQa.java').read_text()
        observer = java.split('public static void observeCrewLivingTick', 1)[1].split('private static boolean advanceSharedCrew', 1)[0]
        self.assertNotRegex(observer, r'\.tick\(\)|\.setBlock\(|\.setNoAi\(|\.setState\(')
        self.assertIn('getLongArray("Completed")', observer)
        self.assertIn('Exact per-builder placement material debit', observer)
        self.assertIn('CREW_PLACEMENTS.values().stream().noneMatch', observer)
        self.assertIn('LOADED_CREW.equals(stopped.crewCargo())', java)
        self.assertIn('CREW_SUPPLIED.equals(Set.of(originalCoordinator, sharedHelper))', java)


if __name__ == '__main__':
    unittest.main()
