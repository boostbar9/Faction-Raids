#!/usr/bin/env python3
"""Fail closed unless a real bounded representative native lifecycle produced all evidence."""
import json
import sys
from pathlib import Path

root = Path(sys.argv[1]) if len(sys.argv) == 2 else Path('build/native-handoff-qa/evidence')
data = json.loads((root / 'result.json').read_text())
audit = json.loads((root / 'native-bytecode-audit.json').read_text())
assert audit['status'] == 'passed' and audit['noVendorJarsUploaded'], audit
assert sum(len(value['classes']) for value in audit['artifacts'].values()) == 22, audit
assert data['mode'] == 'staged-handoff' and data['status'] == 'passed', data
assert data['geometryProfile'] == 'hollow-five-wide-one-claim', data
assert data['geometrySourceCommit'] == 'acbc66025e09152db75ab419b34fad81a3dd290e', data
assert data['hollowOracle'] == {'columns': 220, 'skinColumns': 88, 'cavityColumns': 132, 'targetCount': 572,
                                'cavityAirCount': 396, 'headroomAirCount': 352, 'reservedCellCount': 1320}, data
assert data['hollowCavityAirPreserved'] and data['walkwayHeadroomAirPreserved'], data
assert data['nativeStageHollowReservationVerified-0'] and data['nativeStageHollowReservationVerified-1'], data
assert data['syntheticPartition'] is True and data['qaSectionTargetCap'] == 96, data
assert 'direct server commission' in data['commissionPath'], data
assert data['wholePlanCompleted'] is False, data
assert data['totalLimitSeconds'] == 600 and data['constructionLimitSeconds'] == 540, data
assert 0 < data['elapsedSeconds'] < 600, data
assert 0 < data['constructionSeconds'] <= 540, data
assert data['territoryChunkCount'] == 1 and len(data['nativeClaimRecords']) == 1, data
assert data['materialCounts'] == {'minecraft:cobblestone': 352, 'minecraft:oak_planks': 220}, data
assert data['treasuryDebit'] == 64 and len(data['manifestHash']) == len(data['reviewFingerprint']) == 64, data
stages = data['stageLayout']
assert len(stages) == data['reviewedStageCount'] > 2 and sum(part['targets'] for part in stages) == 572, stages
assert all(0 < part['targets'] <= 96 and len(part['digest']) == 64 for part in stages), stages
assert [part['index'] for part in stages] == list(range(len(stages))), stages
assert len({part['area'] for part in stages}) == len(stages), stages
assert data['firstSectionCompletionObserved'] is True and data['firstSectionTargetCount'] == stages[0]['targets'], data
assert 0 < data['nextSectionNativePlaced'] < stages[1]['targets'], data
assert stages[0]['targets'] + data['nextSectionNativePlaced'] <= data['retainedPlacedBlocks'] < 572, data
assert data['midStageRestartVerified'] is True and data['canceledRestartVerified'] is True, data
closed = data['partialRestartClosedLifecycle']
assert closed['consecutiveServerTicks'] >= closed['minimumTicks'] == 40, closed
assert closed['cleanupJournalEmpty'] and closed['storageRunningProblemAbsent'] and not closed['activeHandUse'], closed
restarts = data['restarts']
expected_kinds = ['mid-stage', 'between-stages', 'canceled'] if data['betweenStageRestartVerified'] else ['mid-stage', 'canceled']
assert [restart['kind'] for restart in restarts] == expected_kinds, restarts
assert restarts[0]['projectState'] == 'RUNNING' and restarts[0]['activeStage'] == 0, restarts
assert 0 < restarts[0]['placed'] < stages[0]['targets'], restarts
assert restarts[-1]['projectState'] == 'CANCELED' and restarts[-1]['activeStage'] == 1, restarts
if data['betweenStageRestartVerified']:
    assert restarts[1]['projectState'] == 'WAITING_FOR_NEXT_STAGE' and restarts[1]['activeStage'] == 1, restarts
    assert restarts[1]['placed'] == stages[0]['targets'], restarts
for restart in restarts:
    assert 0 < restart['placed'] < 572 and restart['placed'] + restart['pending'] == 572, restart
    assert len(restart['ledgerGeneration']) == 36, restart
    for suffix in ['before-shutdown', 'final-stop', 'after-reopen']:
        assert data['authority-' + restart['kind'] + '-' + suffix], restart
assert data['authority-commission'] and data['authority-native-handoff'] and data['authority-canceled-before-reopen'], data
joins = data['nativeStageJoins']
assert {entry['area'] for entry in joins} == {stages[0]['area'], stages[1]['area']}, joins
assert all(entry['marker'] == data['originalMarker'] for entry in joins), joins
assert sum(not entry['loadedFromDisk'] for entry in joins) == 2, joins
assert any(entry['loadedFromDisk'] and entry['area'] == stages[0]['area'] for entry in joins), joins
assert data['cancellationAuthority'] == 'CANCELED compact terminal receipt' and data['cancellationAuthorityNbt'], data
assert data['cancellationCleanup'] == {'markersGone': True, 'builderDetached': True, 'reservationsGone': True}, data
assert data['keyboardEscapeObservationTicks'] >= 20, data
assert data['keyboardEscapePreservedProject'] is True and 'no physical OS input' in data['keyboardInputScope'], data
keyboard = data['keyboardInteractions']
assert [entry['control'] for entry in keyboard] == ['Building', 'Construction', 'Cancel entire perimeter',
                                                  'Cancel confirmation', 'Cancel entire perimeter', 'Yes'], keyboard
assert keyboard[3]['key'] == 'Escape' and keyboard[3]['returnedWithoutCancelRequest'] is True, keyboard
for entry in keyboard[:3] + keyboard[4:]:
    assert entry['keys'] == 'Tab/Enter' and entry['focusMoved'] is True and 1 <= entry['tabHandlers'] <= 128, entry
expected_shots = ['01-direct-server-commission.png', '02-authenticated-core-cancel.png', '03-confirm-whole-project-cancel.png', '04-canceled-after-reopen.png']
assert data['screenshots'] == expected_shots, data
for name in expected_shots:
    content = (root / name).read_bytes()
    assert content.startswith(b'\x89PNG\r\n\x1a\n') and len(content) > 4096, name
versions = data['loadedModVersions']
assert versions['workers'] == '2.0.3' and versions['recruits'] == '1.15.2', versions
assert versions['minecraft'] == '1.20.1' and versions['forge'] == '47.4.16', versions
assert versions['smallships'] == '2.0.0-b1.4', versions
for mod in ['workers', 'recruits', 'smallships', 'siegeweapons']:
    assert len(data['loadedCompanionArtifacts'][mod]['sha256']) == 64, mod
final, bank = data['finalDiagnostics'], data['treasuryAccounting']
assert bank['startingFunds'] == 2000 and bank['commissionDebit'] == 64 and bank['initialPaidBalance'] == 1936, bank
assert bank['singleNegativeLedgerDelta'] == -64, bank
assert bank['expectedFinalBalance'] == 1936 + bank['interestCredits'] + bank['taxCredits'], bank
assert final['treasury'] == bank['expectedFinalBalance'] and final['placed'] == data['retainedPlacedBlocks'], final
assert len(data['treasuryObserverContracts']) == 10, data
assert final['placedCobble'] + final['chestCobble'] + final['builderCobble'] + final['looseCobble'] == 352, final
assert final['placedOak'] + final['chestOak'] + final['builderOak'] + final['looseOak'] == 220, final
print('Representative native first-section completion, handoff, next-section placement and durable cancellation verified. Whole-plan completion was not attempted. Between-stage restart verified:', data['betweenStageRestartVerified'])
