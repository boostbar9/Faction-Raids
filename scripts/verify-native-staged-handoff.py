#!/usr/bin/env python3
"""Fail closed unless a real bounded representative native lifecycle produced all evidence."""
import json
import os
import sys
from pathlib import Path

def verify_shared_crew(data, root):
    """Validate recorded native evidence. Synthetic unit fixtures test rejection, not gameplay."""
    import re
    from collections import Counter
    from uuid import UUID

    assert data.get('sharedCrewRequested') is True and data.get('sharedCrewVerified') is True, data
    assert data.get('builderReplacementRequested') is False, data
    ids = data['sharedCrewBuilders']
    assert len(ids) == len(set(ids)) == 2, ids
    for worker in ids:
        assert str(UUID(worker)) == worker, worker
    for key, filename in [('sourceCommit', 'source-commit.txt'), ('sourceTree', 'source-tree.txt')]:
        assert re.fullmatch('[0-9a-f]{40}', data[key]), key
        assert data[key] == (root / filename).read_text().strip(), key
    admission = data['sharedCrewAdmission']
    assert [admission['coordinator'], admission['helper']] == ids, admission
    assert admission['project'] == data['projectId'] and admission['manifestHash'] == data['manifestHash'], admission
    assert str(UUID(admission['owner'])) == admission['owner'], admission
    stages = data['stageLayout']
    assert admission['area'] == stages[0]['area'], admission
    counts, supplied, observed = Counter(), set(), set()
    resumed = set()
    survivor_after_death = False
    death = data['sharedCrewSuccession']
    first_reload_tick = next(restart['gameTime'] for restart in data['restarts'] if restart['kind'] == 'mid-stage')
    for event in data['sharedCrewEvents']:
        worker = event['builder']
        assert worker in ids and event['area'] == stages[event['stage']]['area'], event
        assert event['gameTime'] >= data['commissionGameTime'], event
        cells = event['cells']
        changes = Counter(cell['material'] for cell in cells)
        if event['kind'] == 'native-placement':
            assert event['nativeStateBefore'] == 'PLACE_BLOCKS' and cells, event
            assert set(changes).issubset({'minecraft:cobblestone', 'minecraft:oak_planks'}), event
            for cell in cells:
                assert isinstance(cell['packed'], int) and cell['packed'] not in observed, cell
                packed = cell['packed'] & ((1 << 64) - 1)
                x, z, y = (packed >> 38) & ((1 << 26) - 1), (packed >> 12) & ((1 << 26) - 1), packed & 4095
                x = x - (1 << 26) if x >= (1 << 25) else x
                z = z - (1 << 26) if z >= (1 << 25) else z
                y = y - 4096 if y >= 2048 else y
                assert cell['position'] == f'{x}, {y}, {z}', cell
                inward = min(x - 127, 144 - x, z + 1, 16 - z)
                assert 1 <= inward <= 5 and 65 <= y <= 69, cell
                if y == 68:
                    assert cell['material'] == 'minecraft:oak_planks', cell
                else:
                    assert inward in [1, 5] and cell['material'] == 'minecraft:cobblestone', cell
                observed.add(cell['packed'])
            for name, material in [('cobble', 'minecraft:cobblestone'), ('oak', 'minecraft:oak_planks')]:
                assert event[name + 'Before'] - event[name + 'After'] == changes[material], event
                chest = 'chest' + name.title()
                assert event[chest + 'Before'] == event[chest + 'After'], event
            counts[worker] += len(cells)
            if worker == ids[0]:
                assert event['gameTime'] <= death['deathGameTime'], event
            elif death['deathGameTime'] < event['gameTime'] <= death['resumeGameTime']:
                survivor_after_death = True
            if first_reload_tick < event['gameTime'] < death['deathGameTime']:
                resumed.add(worker)
        else:
            assert event['kind'] == 'native-supply-withdrawal' and not cells, event
            increases = []
            for name in ['cobble', 'oak']:
                chest = 'chest' + name.title()
                change = event[name + 'After'] - event[name + 'Before']
                assert event[chest + 'Before'] - event[chest + 'After'] == change, event
                increases.append(change)
            assert any(change > 0 for change in increases), event
            supplied.add(worker)
        for name in ['cobble', 'oak', 'chestCobble', 'chestOak']:
            assert event[name + 'Before'] >= 0 and event[name + 'After'] >= 0, event
    assert survivor_after_death, death
    assert set(counts) == supplied == resumed == set(ids), (counts, supplied, resumed)
    assert dict(counts) == data['sharedCrewPlacementCounts'], counts
    assert sum(counts.values()) == len(observed) == data['sharedCrewDistinctTargets'] == data['retainedPlacedBlocks'], counts
    assert death['deadCoordinator'] == ids[0] and death['survivingHelper'] == ids[1], death
    assert first_reload_tick < death['deathGameTime'] < death['resumeGameTime'], death
    assert 0 < death['placedBefore'] < death['placedAfter'] <= data['retainedPlacedBlocks'] < 572, death
    assert death['extraCommission'] is False and death['newBuilderSpawnedAfterDeath'] is False, death
    assert death['manifestHash'] == data['manifestHash'], death
    assert data['sharedCrewPartialReloadVerified'] is True, data
    cargo = data['sharedCrewCargoReload-mid-stage']
    assert set(cargo['stopped']) == set(cargo['loaded']) == set(ids), cargo
    assert cargo['stopped'] == cargo['loaded'], cargo
    for reload in data['restarts']:
        cargo = data['sharedCrewCargoReload-' + reload['kind']]
        assert cargo['stopped'] == cargo['loaded'] and cargo['stopped'], cargo
    assert 'Spectator' in data['sharedCrewInterruption'], data
    assert 'Not exercised' in data['sharedCrewSleepCoverage'], data
    assert 'LivingTick' in data['sharedCrewAttribution'], data
    assert 'legacy hollow' in data['sharedCrewGeometry'], data


def main():
    root = Path(sys.argv[1]) if len(sys.argv) == 2 else Path('build/native-handoff-qa/evidence')
    data = json.loads((root / 'result.json').read_text())
    audit = json.loads((root / 'native-bytecode-audit.json').read_text())
    assert audit['status'] == 'passed' and audit['noVendorJarsUploaded'], audit
    assert sum(len(value['classes']) for value in audit['artifacts'].values()) == 22, audit
    assert data['mode'] == 'staged-handoff' and data['status'] == 'passed', data
    if os.environ.get('BUILDER_REPLACEMENT_QA') == 'true':
        assert data.get('builderReplacementRequested') is True, data
        replacement = data['builderReplacement']
        assert replacement['deadBuilder'] != replacement['replacement'], replacement
        assert len(replacement['deadBuilder']) == len(replacement['replacement']) == 36, replacement
        assert 0 < replacement['placedBefore'] < replacement['placedAfter'] < 572, replacement
        assert replacement['extraCommission'] is False, replacement
    if os.environ.get('SHARED_CREW_QA') == 'true':
        verify_shared_crew(data, root)
    else:
        assert data.get('sharedCrewRequested', False) is False, data
    assert data['geometryProfile'] == 'hollow-five-wide-one-claim', data
    assert data['geometrySourceCommit'] == 'acbc66025e09152db75ab419b34fad81a3dd290e', data
    assert data['hollowOracle'] == {'columns': 220, 'skinColumns': 88, 'cavityColumns': 132, 'targetCount': 572,
                                    'cavityAirCount': 396, 'headroomAirCount': 352, 'reservedCellCount': 1320}, data
    assert data['hollowCavityAirPreserved'] and data['walkwayHeadroomAirPreserved'], data
    assert data['nativeStageHollowReservationVerified-0'] and data['nativeStageHollowReservationVerified-1'], data
    section_cap = 192 if os.environ.get('BUILDER_REPLACEMENT_QA') == 'true' or os.environ.get('SHARED_CREW_QA') == 'true' else 96
    assert data['syntheticPartition'] is True and data['qaSectionTargetCap'] == section_cap, data
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
    assert all(0 < part['targets'] <= section_cap and len(part['digest']) == 64 for part in stages), stages
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

if __name__ == '__main__':
    main()
