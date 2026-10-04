"""Synthetic parser/static contracts only; these tests do not execute Minecraft."""
import copy
import importlib.util
from pathlib import Path
import unittest

REPO = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('verify_native_economy', REPO / 'scripts/verify-native-economy.py')
verify = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(verify)
HARNESS = REPO / 'src/nativeQa/java/com/devfarinsky/siegeoverhaul/nativecompat'


class NativeEconomyReceiptVerifier(unittest.TestCase):
    def setUp(self):
        self.data = {'status': 'passed', 'gameplay': {'status': 'passed', 'manualTreasuryDebit': 8,
            'manualPaidTreasury': 992, 'perimeterTreasuryDebit': 64, 'completedManualRestart': {'treasury': 992},
            'economy': {'status': 'passed',
                'scope': 'Controlled cleared-wave states; production processRaid. No raid combat was fought.',
                'territory': {'unownedRejected': [0, 1], 'ownedRejected': [0, 1], 'unavailableTreasuryDebit': 0,
                    'personalInventoryUnchanged': True, 'fixtureFunding': 1500, 'workingPrices': [900, 600],
                    'savedOwnershipMask': 15, 'activeCount': 2, 'retainedCount': 2},
                'practice': {'wave': 5, 'handlerPasses': 2, 'boxes': 0, 'treasuryDebitOrCredit': 0},
                'checkpoint': {'wave': 5, 'boxes': 1, 'beforeAnyBallot': True, 'waitReplayNoDuplicate': True,
                    'rolledTier': 2, 'treasury': 1032, 'treasuryReward': 40},
                'diskReload': {'newSavedDataAndRaidInstances': True, 'ownershipMask': 15, 'activeCount': 2,
                    'retainedCount': 2, 'exactPlayerInventory': True, 'exactCurrentWaveReceipt': True,
                    'replayNoDuplicate': True},
                'fullInventory': {'wave': 6, 'filledMainSlots': 36, 'droppedBoxes': 1, 'inventoryBoxes': 0,
                    'replayNoDuplicate': True, 'rolledTier': 3,
                    'dropEntity': '11111111-2222-3333-4444-555555555555', 'item': 'siegeoverhaul:loot_box_epic'}}}}

    def test_complete_synthetic_receipt_parses(self):
        self.assertEqual(verify.verify(self.data)['status'], 'passed')

    def test_missing_economy_receipt_fails(self):
        del self.data['gameplay']['economy']
        with self.assertRaises(KeyError): verify.verify(self.data)

    def test_incorrect_fees_balances_and_incomplete_result_fail(self):
        for field, value in [('manualTreasuryDebit', 90), ('manualPaidTreasury', 910),
                             ('perimeterTreasuryDebit', 63), ('status', 'incomplete')]:
            with self.subTest(field=field):
                data = copy.deepcopy(self.data); data['gameplay'][field] = value
                with self.assertRaises(AssertionError): verify.verify(data)

    def test_each_safety_or_delivery_regression_fails_closed(self):
        cases = [('territory', 'unownedRejected', [0]), ('territory', 'ownedRejected', []),
                 ('territory', 'unavailableTreasuryDebit', 1), ('territory', 'personalInventoryUnchanged', False),
                 ('territory', 'workingPrices', [899, 600]), ('territory', 'savedOwnershipMask', 12),
                 ('territory', 'activeCount', 4), ('territory', 'retainedCount', 0),
                 ('practice', 'boxes', 1), ('practice', 'treasuryDebitOrCredit', 40),
                 ('checkpoint', 'boxes', 0), ('checkpoint', 'beforeAnyBallot', False),
                 ('checkpoint', 'waitReplayNoDuplicate', False), ('checkpoint', 'rolledTier', 4),
                 ('checkpoint', 'treasury', 1033), ('diskReload', 'newSavedDataAndRaidInstances', False),
                 ('diskReload', 'ownershipMask', 12), ('diskReload', 'exactPlayerInventory', False),
                 ('diskReload', 'exactCurrentWaveReceipt', False), ('diskReload', 'replayNoDuplicate', False),
                 ('fullInventory', 'filledMainSlots', 9), ('fullInventory', 'droppedBoxes', 2),
                 ('fullInventory', 'inventoryBoxes', 1), ('fullInventory', 'replayNoDuplicate', False),
                 ('fullInventory', 'item', 'minecraft:emerald')]
        for group, field, value in cases:
            with self.subTest(group=group, field=field):
                data = copy.deepcopy(self.data); data['gameplay']['economy'][group][field] = value
                with self.assertRaises(AssertionError): verify.verify(data)

    def test_fixture_scope_cannot_omit_combat_exclusion(self):
        self.data['gameplay']['economy']['scope'] = 'Full raid passed'
        with self.assertRaises(AssertionError): verify.verify(self.data)


class NativeEconomySourceContracts(unittest.TestCase):
    def test_real_production_calls_disk_restart_and_existing_gates_remain(self):
        economy = (HARNESS / 'NativeEconomyContracts.java').read_text()
        gameplay = (HARNESS / 'NativeBuildingGameplay.java').read_text()
        for source in ['TerritoryBuffs.purchase(owner, corePos, index)',
                       'WaveLootRewards.awardClearedWave(data, state, List.of(owner))',
                       'getDeclaredMethod("processRaid"', 'process.invoke(null, server, data, key)',
                       'owner.server.saveEverything(false, true, true)',
                       'data != beforeReloadData', 'state != beforeReloadRaid',
                       'checkpointReceipt.equals(receipt(state, owner, 5))',
                       'owner.getInventory().getFreeSlot() == -1', 'newDrops.size() == 1',
                       'owner.getInventory().load(preservedInventory)']:
            self.assertIn(source, economy)
        for source in ['DefenseBlueprint.Kind.WALL.price == 8', 'balance(owner) == 992',
                       'NativeEconomyContracts.beforeReload', 'advance(now, 23, 0); return Action.RELOAD',
                       'NativeEconomyContracts.afterReload', 'conservation(level)',
                       'cavityObstructionRestartVerified', 'completedManualRestart',
                       'NativeConstructionGuard.hasReservation(level, jobId)']:
            self.assertIn(source, gameplay)
        self.assertNotIn('910', gameplay)
        self.assertNotIn('charges exactly 90 Treasury', gameplay)

    def test_workflow_requires_both_receipt_and_parser_regressions(self):
        workflow = (REPO / '.github/workflows/native-building-qa.yml').read_text()
        self.assertIn('python3 scripts/verify-native-economy.py build/native-qa/evidence/result.json', workflow)
        self.assertIn("-p 'test_native_economy_qa.py'", workflow)
        self.assertIn("assert gameplay['completedManualBlocks'] == 83", workflow)
        self.assertIn("assert canceled['globalTargets'] == projection['wholeTargets']", workflow)
        self.assertIn('contents: read', workflow)
        self.assertIn('persist-credentials: false', workflow)
        self.assertNotIn('secrets.', workflow)


if __name__ == '__main__':
    unittest.main()
