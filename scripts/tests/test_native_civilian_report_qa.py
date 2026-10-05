"""Static and synthetic receipt checks only; these are not native gameplay evidence."""
import copy
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('native_civilian_verifier', ROOT / 'scripts/verify-native-civilians.py')
verifier = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(verifier)


class CivilianReceiptTests(unittest.TestCase):
    def setUp(self):
        result = dict(status='passed', recruitActions=0, firstMenuId=1, reopenedMenuId=2, residentCount=1,
                      scope='Existing startup NPC AI was already paused by the bounded fixture.',
                      notCovered='Native AI and tax accrual are not tested.',
                      serverRows=[dict(uuid='00000000-0000-0000-0000-000000000001', name='Native resident',
                                       profession='minecraft:farmer', type='minecraft:plains', level=2,
                                       status='Loaded', nativeAiPaused=True)])
        result.update({key: True for key in verifier.FLAGS})
        self.data = dict(status='passed', screenshots=verifier.SHOTS.copy(),
                         gameplay=dict(status='passed', civilianReport=result))

    def test_complete_synthetic_receipt(self):
        self.assertEqual(verifier.verify(self.data)['residentCount'], 1)

    def test_every_transport_and_no_mutation_guard_is_required(self):
        for flag in verifier.FLAGS:
            data = copy.deepcopy(self.data); data['gameplay']['civilianReport'][flag] = False
            with self.subTest(flag=flag), self.assertRaises(AssertionError): verifier.verify(data)

    def test_stale_menu_empty_rows_missing_capture_and_undisclosed_ai_pause_fail(self):
        for key, value in [('reopenedMenuId', 1), ('recruitActions', 1), ('residentCount', 0),
                           ('serverRows', []), ('scope', 'Actual playtest')]:
            data = copy.deepcopy(self.data); data['gameplay']['civilianReport'][key] = value
            with self.subTest(key=key), self.assertRaises(AssertionError): verifier.verify(data)
        self.data['screenshots'].pop()
        with self.assertRaises(AssertionError): verifier.verify(self.data)

    def test_helper_only_reads_existing_entities_and_uses_actual_client_flow(self):
        helper = (ROOT / 'src/nativeQa/java/com/devfarinsky/siegeoverhaul/nativecompat/NativeCivilianReportQa.java').read_text()
        for required in ['CoreCivilians.snapshot(owner)', 'owner.server.overworld().getEntity(resident.id())',
                         'villager.getBrain().getMemory', 'menu.civilianReport() != firstReceived',
                         'mc.player.closeContainer()', 'mc.gameMode.useItemOn(',
                         'NativeBuildingQa.clickVisibleButton', 'ledgerBefore.equals(',
                         'inventoryBefore.equals(', 'core.getLong("CivilianTaxesTotal")']:
            self.assertIn(required, helper)
        for forbidden in ['.recruit(', '.tryStarters(', '.setNoAi(', '.civilianReport(expected)',
                          '.expectCivilianReport(', '.setData(', '.setBlock(', '.setGameTime(', '.putLong(']:
            self.assertNotIn(forbidden, helper)
        gameplay = (ROOT / 'src/nativeQa/java/com/devfarinsky/siegeoverhaul/nativecompat/NativeBuildingGameplay.java').read_text()
        for required in ['NativeCivilianReportQa.begin(owner, fixture.corePos())', 'NativeCivilianReportQa.tick(mc)',
                         'NativeCivilianReportQa.finish(owner)', 'NativeSizeableFoliageContracts.verifyReviews']:
            self.assertIn(required, gameplay)
        workflow = (ROOT / '.github/workflows/native-building-qa.yml').read_text()
        self.assertIn('scripts/verify-native-civilians.py', workflow)
        for shot in verifier.SHOTS: self.assertIn(shot, workflow)


if __name__ == '__main__':
    unittest.main()
