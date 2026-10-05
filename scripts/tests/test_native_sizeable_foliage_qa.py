"""Synthetic parser/static checks only; no Minecraft gameplay is executed here."""
import copy
import importlib.util
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('verify_sizeable', ROOT / 'scripts/verify-native-sizeable-foliage.py')
verify = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(verify)


class SizeableFoliageEvidenceTests(unittest.TestCase):
    def setUp(self):
        self.data = {'status': 'passed', 'screenshots': ['18-completed-manual-wall-and-builder.png'],
            'sizeableFoliageRuntime': {'modId': verify.MOD, 'version': '1.2.1', 'officialVersionId': verify.VERSION,
                'runtimeClass': verify.CLASS, 'declaredMethods': ['getShape', 'performBonemeal'],
                'noDeclaredRemovalOrMultipartOverrides': True, 'fileName': 'test_mapped.jar', 'sha256': 'a' * 64,
                'artifactScope': 'Actual ForgeGradle remapped runtime JAR'},
            'gameplay': {'status': 'passed', 'completedManualBlocks': 83, 'manualTreasuryDebit': 8,
                'manualPaidTreasury': 992, 'completedManualRestart': {'treasury': 992},
                'sizeableFoliageReviews': {'targetCell': '128, 65, 0', 'clearanceCell': '130, 65, 8',
                    'shortGrassTargetAndClearanceAccepted': True, 'runtimeStateSafetyVerified': True,
                    'plantsPreservedDuringReview': True, 'shortGrassStandingSiteAccepted': True, 'exactWorkerInventoryAndReceipts': True,
                    'fixtureTerrainRestored': True, 'treasuryDebit': 0, 'scope': 'production prepare and standing-site checks only',
                    'rejections': {name: f'Rejected 128, 65, 0 ({name})' for name in verify.EXCLUDED}},
                'singleCellPlant': {'plant': 'sizeable_foliage:very_short_grass',
                    **{name: True for name in verify.PLANT_PROOFS}}}}

    def test_complete_synthetic_receipt_is_accepted(self):
        self.assertEqual(verify.verify(self.data)['status'], 'passed')

    def test_each_native_proof_is_required(self):
        for name in verify.PLANT_PROOFS:
            with self.subTest(name=name):
                data = copy.deepcopy(self.data)
                data['gameplay']['singleCellPlant'][name] = False
                with self.assertRaises(AssertionError): verify.verify(data)

    def test_missing_or_false_standing_proof_fails(self):
        for missing in [False, True]:
            data = copy.deepcopy(self.data)
            if missing: del data['gameplay']['sizeableFoliageReviews']['shortGrassStandingSiteAccepted']
            else: data['gameplay']['sizeableFoliageReviews']['shortGrassStandingSiteAccepted'] = False
            with self.assertRaises((AssertionError, KeyError)): verify.verify(data)

    def test_wrong_runtime_or_missing_native_rejections_fail(self):
        for key, value in [('version', '1.2.2'), ('officialVersionId', 'changed'),
                           ('runtimeClass', 'net.minecraft.world.level.block.BushBlock'),
                           ('declaredMethods', ['onRemove']), ('sha256', 'not-a-hash')]:
            with self.subTest(key=key):
                data = copy.deepcopy(self.data); data['sizeableFoliageRuntime'][key] = value
                with self.assertRaises(AssertionError): verify.verify(data)
        data = copy.deepcopy(self.data)
        del data['gameplay']['sizeableFoliageReviews']['rejections']['sizeable_foliage:very_tall_grass']
        with self.assertRaises(AssertionError): verify.verify(data)

    def test_payments_completion_and_actual_addon_identity_are_required(self):
        for key, value in [('manualTreasuryDebit', 0), ('manualPaidTreasury', 1000), ('completedManualBlocks', 82),
                           ('status', 'incomplete')]:
            data = copy.deepcopy(self.data); data['gameplay'][key] = value
            with self.assertRaises(AssertionError): verify.verify(data)
        self.data['gameplay']['singleCellPlant']['plant'] = 'minecraft:dandelion'
        with self.assertRaises(AssertionError): verify.verify(self.data)

    def test_missing_or_modified_original_artifact_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            cache = Path(temporary)
            with self.assertRaises(AssertionError): verify.audit_original(cache)
            directory = cache / 'modules-2/files-2.1/maven.modrinth/sizeable-foliage' / verify.VERSION / 'fake-hash'
            directory.mkdir(parents=True)
            (directory / 'sizeable-foliage.jar').write_bytes(b'not the pinned official jar')
            with self.assertRaises(AssertionError): verify.audit_original(cache)

    def test_fixture_wiring_remains_opt_in_and_preserves_production_package(self):
        gradle = (ROOT / 'build.gradle').read_text()
        self.assertIn("if (sizeableFoliageQa) nativeQaCompanions fg.deobf('maven.modrinth:sizeable-foliage:m2kFJSUs')", gradle)
        self.assertNotIn('sizeable_foliage', (ROOT / 'src/main/resources/META-INF/mods.toml').read_text())
        workflow = (ROOT / '.github/workflows/native-building-qa.yml').read_text()
        for part in ['native-sizeable-foliage-qa', 'inputs.sizeable_foliage', '-PnativeQaSizeableFoliage="$SIZEABLE_FOLIAGE_QA"',
                     "if: env.SIZEABLE_FOLIAGE_QA == 'true'", 'scripts/verify-native-sizeable-foliage.py',
                     'scripts/verify-native-economy.py', 'contents: read', 'persist-credentials: false']:
            self.assertIn(part, workflow)
        self.assertNotIn('secrets.', workflow)
        gameplay = (ROOT / 'src/nativeQa/java/com/devfarinsky/siegeoverhaul/nativecompat/NativeBuildingGameplay.java').read_text()
        for part in ['NativeSizeableFoliageContracts.plantState()', 'hasAcceptedPlant(level) && hasClearedPlant(level)',
                     'conservation(level)', 'NativeConstructionGuard.hasReservation(level, jobId)',
                     'BlockState plant = level.getBlockState(plantCell)']:
            self.assertIn(part, gameplay)


if __name__ == '__main__':
    unittest.main()
