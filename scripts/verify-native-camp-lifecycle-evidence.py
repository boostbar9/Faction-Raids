#!/usr/bin/env python3
"""Fail-closed receipt contract for the actual opt-in integrated-client run."""
import json
from pathlib import Path
import sys


def verify(root):
    data = json.loads((root / 'result.json').read_text())
    assert data['mode'] == 'camp-lifecycle' and data['status'] == 'passed', data
    config = data['fixtureConfiguration']
    assert config == {'siegePreparationMinutes': 3, 'waterDepth': 24,
                      'scoutingTimersChanged': False, 'recoveryTimersChanged': False,
                      'raidFieldsWritten': False}, config
    versions = data['loadedModVersions']
    for mod, version in {'minecraft': '1.20.1', 'forge': '47.4.16', 'workers': '2.0.3',
                         'recruits': '1.15.2', 'smallships': '2.0.0-b1.4', 'siegeweapons': '0.2.5'}.items():
        assert versions[mod] == version, versions
    for mod in ['workers', 'recruits', 'smallships', 'siegeweapons']:
        assert len(data['loadedCompanionArtifacts'][mod]['sha256']) == 64, mod
    scenarios = data['scenarios']
    assert [s['scenario'] for s in scenarios] == ['no-safe-land-reload-fallback', 'recovery-only-island']
    for case in scenarios:
        assert case['status'] == 'passed' and case['entry'].startswith('Actual non-op client /siegeoverhaul start'), case
        assert all(case[k] for k in ['sawNaturalSearch', 'sawEarthworksSearch', 'sawRecoveryCooldown', 'sawWiderRecovery']), case
        assert case['searchTicksWithoutWaveOrAttackers'] > 100 and case['cooldownTicksWithoutWaveOrAttackers'] >= 1200, case
        assert set(case['passes']) == {'natural', 'earthworks', 'recovery'}, case
        for budget in case['passes'].values():
            assert 0 < budget['maxCandidates'] <= 200 and 0 < budget['maxElapsedTicks'] <= 4800, budget
        assert case['protectedCellCount'] >= 6144 and case['protectedChests'] >= 2, case
        assert case['restorationExcludedProtectedCells'] and case['wave'] >= 1 and case['totalSpawned'] > 0, case
        shot = case['framebuffer']
        assert shot['readyFrames'] >= 8 and shot['attempts'] > 0 and shot['changedPixelSamples'] > 50, shot
        assert 0 <= shot['elapsedSeconds'] < 30, shot
        content = (root / shot['file']).read_bytes()
        assert content.startswith(b'\x89PNG\r\n\x1a\n') and len(content) > 4096, shot
    impossible, recovery = scenarios
    assert impossible['realWorldReload'] and impossible['savedSearchAuthorityVerified'], impossible
    assert impossible['campSearchAbandoned'] and impossible['fallbackAtGameTime'] >= 0, impossible
    assert impossible['firstWaveAtGameTime'] - impossible['fallbackAtGameTime'] >= 3600, impossible
    assert impossible['finalState']['campPosition'] == 'null' and impossible['finalState']['preparationTicks'] == 0, impossible
    assert not recovery['campSearchAbandoned'] and recovery['fallbackAtGameTime'] == -1, recovery
    island = recovery['recoveryIsland']
    assert island['width'] == 57 and island['soilDepth'] == 24 and island['fixtureWrites'] == 57 * 57 * 24 + 4, island
    assert island['originalCandidatesCannotFitCore'] and island['completedAtGameTime'] < island['recoveryCooldownAtGameTime'], island
    assert recovery['recoveryCooldownAtGameTime'] <= recovery['establishedAtGameTime'] < recovery['firstWaveAtGameTime'], recovery
    camp = recovery['nativeCamp']
    assert camp['nativeClaimChunks'] == 25 and camp['enemyCore'] and camp['ordinaryTicksSinceEstablishment'] >= 60, camp
    assert camp['workers'] and len(camp['guards']) == 5 and camp['preservedEdgesRescue'], camp
    for unit in camp['workers'] + camp['guards']:
        assert not unit['noAi'] and unit['faction'] == camp['expectedEnemyFaction'], unit
    assert all(w['owner'] == camp['nativeJobOwner'] for w in camp['workers']), camp
    assert data['screenshots'] == ['01-bounded-campless-fallback.png', '02-recovered-native-camp.png']
    audit = json.loads((root / 'native-bytecode-audit.json').read_text())
    assert audit['status'] == 'passed' and audit['noVendorJarsUploaded'], audit
    assert sum(len(v['classes']) for v in audit['artifacts'].values()) == 18, audit
    return data


if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('usage: verify-native-camp-lifecycle-evidence.py EVIDENCE_DIRECTORY')
    verify(Path(sys.argv[1]))
    print('Actual bounded recovery, persisted cooldown, protected terrain, native camp and both permitted first waves verified. Review framebuffers separately.')
