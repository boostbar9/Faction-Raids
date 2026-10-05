#!/usr/bin/env python3
"""Validate actual addon evidence; synthetic parser tests do not establish gameplay."""
import hashlib
import json
import os
from pathlib import Path
import sys

MOD = 'sizeable_foliage'
VERSION = 'm2kFJSUs'
CLASS = 'com.craisinlord.sizeablefoliage.content.block.VeryShortGrassBlock'
ORIGINAL_SHA512 = '2edb316ea862cd0e7c9e748a2ccc05c363590f6e05a67eeee765ba559345104637e3134abc049f164b95123797ed7beea679ddc4322fb1ab1fb26a4962f45b92'
ORIGINAL_SHA256 = '19224fa993d92e605855261dc846d95239f05c3d4a74f1d199d025c918566e0a'
EXCLUDED = [MOD + ':' + name for name in ['very_tall_grass', 'very_large_fern', 'big_bush', 'big_bush_part',
    'big_sweet_berry_bush', 'big_sweet_berry_bush_part', 'fern_wall', 'torchflower_bush']]
EXCLUDED += ['minecraft:' + name for name in ['wheat', 'oak_sapling', 'chest', 'water', 'stone_bricks']]
PLANT_PROOFS = ['supportOutsideMutationPlan', 'survivedFreePreview', 'survivedUnpaidConfirmation',
    'paidAcceptanceRecorded', 'nativeClearedReceipt', 'receiptsSurvivedReload',
    'nativePlacedExpectedBlock', 'supportPreserved', 'exactMaterialConservation']


def verify(data):
    gameplay = data['gameplay']
    assert data['status'] == gameplay['status'] == 'passed', data
    runtime = data['sizeableFoliageRuntime']
    assert runtime['modId'] == MOD and runtime['version'] == '1.2.1', runtime
    assert runtime['officialVersionId'] == VERSION and runtime['runtimeClass'] == CLASS, runtime
    assert runtime['noDeclaredRemovalOrMultipartOverrides'] is True, runtime
    assert len(runtime['sha256']) == 64 and all(c in '0123456789abcdef' for c in runtime['sha256']), runtime
    assert runtime['fileName'].endswith('.jar'), runtime
    assert 'remapped runtime JAR' in runtime['artifactScope'], runtime
    forbidden = {'onRemove', 'playerDestroy', 'playerWillDestroy', 'neighborChanged', 'onPlace',
                 'updateShape', 'createBlockStateDefinition'}
    assert not forbidden.intersection(runtime['declaredMethods']), runtime
    review = gameplay['sizeableFoliageReviews']
    for key in ['shortGrassTargetAndClearanceAccepted', 'runtimeStateSafetyVerified',
                'plantsPreservedDuringReview', 'exactWorkerInventoryAndReceipts', 'fixtureTerrainRestored']:
        assert review[key] is True, (key, review)
    assert review['treasuryDebit'] == 0, review
    assert 'production prepare only' in review['scope'], review
    assert set(review['rejections']) == set(EXCLUDED), review
    for key, problem in review['rejections'].items():
        assert key in problem and review['targetCell'] in problem, (key, problem)
    plant = gameplay['singleCellPlant']
    assert plant['plant'] == MOD + ':very_short_grass', plant
    for key in PLANT_PROOFS:
        assert plant[key] is True, (key, plant)
    assert gameplay['completedManualBlocks'] == 83 and gameplay['manualTreasuryDebit'] == 8, gameplay
    assert gameplay['manualPaidTreasury'] == 992 and gameplay['completedManualRestart']['treasury'] == 992, gameplay
    assert '18-completed-manual-wall-and-builder.png' in data['screenshots'], data
    return {'status': 'passed', 'runtime': runtime, 'reviews': review, 'plant': plant,
            'scope': 'Actual-addon bounded native wall fixture; no complete territory or dedicated multiplayer claim'}


def audit_original(cache):
    directory = cache / 'modules-2/files-2.1/maven.modrinth/sizeable-foliage' / VERSION
    candidates = sorted(directory.glob('*/*.jar'))
    assert candidates, 'Original pinned Modrinth artifact is absent from the resolved Gradle module cache'
    for path in candidates:
        content = path.read_bytes()
        assert hashlib.sha512(content).hexdigest() == ORIGINAL_SHA512, f'Original official artifact checksum changed: {path.name}'
        assert hashlib.sha256(content).hexdigest() == ORIGINAL_SHA256, path.name
    return {'source': 'https://modrinth.com/mod/sizeable-foliage/version/' + VERSION,
            'coordinate': 'maven.modrinth:sizeable-foliage:' + VERSION,
            'sha512': ORIGINAL_SHA512, 'sha256': ORIGINAL_SHA256,
            'filesVerified': len(candidates), 'vendorJarsUploaded': False}


if __name__ == '__main__':
    result_path = Path(sys.argv[1])
    result = verify(json.loads(result_path.read_text()))
    result['originalRelease'] = audit_original(Path(os.environ['GRADLE_USER_HOME']) / 'caches')
    (result_path.parent / 'sizeable-foliage-evidence.json').write_text(json.dumps(result, indent=2) + '\n')
    print('Pinned actual Sizeable Foliage native acceptance, clearing, reload and wall placement passed.')
