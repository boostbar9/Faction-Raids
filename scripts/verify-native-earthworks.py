#!/usr/bin/env python3
"""Reject incomplete, synthetic-completion or non-conserving native one-FILL evidence."""
import hashlib
import json
from pathlib import Path
import re
import struct
import sys

PHASES = {'MOVE_TO_STORAGE', 'SCAN_STORAGE', 'MOVE_TO_CHEST', 'OPEN_CHEST', 'TAKE_NEEDED_ITEMS', 'CLOSE_CHEST_DONE'}
REFUSALS = {'changed-support-after-review', 'unclaimed-write-cell', 'unloaded-observation',
            'missing-core-payment-retry', 'stale-review-after-accept'}
ARTIFACTS = {'workers': 'workers-567450-8351157_mapped_official_1.20.1.jar',
             'recruits': 'recruits-523860-8339846_mapped_official_1.20.1.jar'}


def require(value, message):
    if not value:
        raise ValueError(message)


def integer(value, name, minimum=0):
    require(type(value) is int and value >= minimum, f'Invalid {name}')
    return value


def sample(row):
    values = [integer(row.get(key), key) for key in ('chestDirt', 'workerDirt', 'worldDirt', 'looseDirt')]
    require(sum(values) == 2 and values[3] == 0, 'Finite source/worker/world conservation failed')
    require(1 <= values[0] <= 2 and 0 <= values[1] <= 1 and 0 <= values[2] <= 1, 'Exceeded exact demand')
    integer(row.get('gameTime'), 'gameTime')
    integer(row.get('workerTickCount'), 'workerTickCount')
    require(integer(row.get('requestCount'), 'requestCount') <= 1, 'Mixed native requests')
    require(integer(row.get('supplyReceiptCount'), 'supplyReceiptCount') <= 1, 'Repeated supply receipt')
    require(integer(row.get('journalReceiptCount'), 'journalReceiptCount') <= 1, 'Repeated native work receipt')
    require(type(row.get('cleanupOutstanding')) is bool, 'Missing cleanup observation')
    require(row.get('journalState') in {'READY', 'PENDING', 'STAGE_VERIFIED'}, 'Unexpected journal phase')
    return values


def terminal(row):
    require(sample(row) == [1, 0, 1, 0] and row['journalState'] == 'STAGE_VERIFIED'
            and row['requestCount'] == 0 and row['supplyReceiptCount'] == row['journalReceiptCount'] == 1
            and row['cleanupOutstanding'] is False, 'Terminal sample disagrees with first-slice completion')


def validate(result):
    require(result.get('status') == 'passed' and result.get('mode') == 'earthworks-one-fill', 'Actual successful one-FILL result absent')
    require(result.get('ordinaryTicksOnly') is True and result.get('seededCompletedTargets') == 0
            and result.get('postStartTeleports') == 0, 'Unsupported work shortcut')
    require(result.get('sourceStock') == 2 and result.get('journalState') == 'STAGE_VERIFIED', 'Wrong finite first-slice boundary')
    limits = ' '.join(result.get('notCovered', []))
    for limitation in ['CUT', 'Multi-step', 'ascent', 'COMPLETE', 'restart', 'v1', 'PR276']:
        require(limitation.lower() in limits.lower(), f'Missing scope limitation: {limitation}')
    require(result.get('syntheticSetup') and 'Synthetic flat' in result.get('scope', ''), 'Synthetic setup was not disclosed')
    identity = result.get('identity', {})
    require(identity.get('actualPlayerListMember') is True and identity.get('normalProfileCacheMatched') is True,
            'Normal integrated player identity proof absent')
    require(re.fullmatch('[0-9a-f]{64}', identity.get('manifestHash', '')), 'Manifest hash absent')
    for name in ('owner', 'builder', 'project', 'area'):
        require(re.fullmatch('[0-9a-f-]{36}', identity.get(name, '')), f'Missing {name} identity')
    require(result.get('loadedModVersions', {}).get('workers') == '2.0.3'
            and result.get('loadedModVersions', {}).get('recruits') == '1.15.2', 'Wrong pinned native release')
    for mod, filename in ARTIFACTS.items():
        receipt = result.get('loadedCompanionArtifacts', {}).get(mod, {})
        require(receipt.get('fileName') == filename and re.fullmatch('[0-9a-f]{64}', receipt.get('sha256', '')), 'Unpinned runtime bytes')
    initial, final = result['before'], result['after']
    require(sample(initial) == [2, 0, 0, 0] and initial['journalState'] == 'READY'
            and initial['requestCount'] == initial['supplyReceiptCount'] == initial['journalReceiptCount'] == 0
            and initial['cleanupOutstanding'] is False, 'Target was completed or worker stocked/requesting before ordinary work')
    terminal(final)
    start = integer(result.get('acceptedGameTime'), 'acceptedGameTime')
    observed_start = integer(result.get('observedStartGameTime'), 'observedStartGameTime')
    end = integer(result.get('observedEndGameTime'), 'observedEndGameTime')
    stable_start = integer(result.get('stableSinceGameTime'), 'stableSinceGameTime')
    elapsed = integer(result.get('ordinaryTicksObserved'), 'ordinaryTicksObserved')
    ticks = integer(result.get('observedTickCount'), 'observedTickCount')
    stability = integer(result.get('stabilityTicks'), 'stabilityTicks')
    require(initial['gameTime'] == start and final['gameTime'] == end and 0 < elapsed == end - start <= 3000,
            'Measured start/end chronology differs from actual boundary observations')
    require(start <= observed_start < end and ticks == end - observed_start > 0,
            'Ordinary observation window is missing ticks or lies outside accepted work')
    require(observed_start < stable_start <= end and 40 <= stability == end - stable_start < ticks <= elapsed,
            'Stable window is outside the ordinary measured tick window')

    def bound(row):
        sample(row)
        require(start <= row['gameTime'] <= end, 'Observation outside accepted work window')
        require(row['workerTickCount'] - initial['workerTickCount'] == row['gameTime'] - start,
                'Worker progression differs from exact ordinary server ticks')

    bound(final)
    warmup = result.get('groundingWarmup', {})
    require(warmup.get('actualOnGround') is True and warmup.get('unassigned') is True and warmup.get('targetAir') is True,
            'Ordinary unassigned grounding evidence missing')
    warmup_ticks = integer(warmup.get('ordinaryTicks'), 'grounding ordinaryTicks', 1)
    require(integer(warmup.get('settledGameTime'), 'settledGameTime') - integer(warmup.get('startGameTime'), 'grounding startGameTime')
            == warmup_ticks and warmup['settledGameTime'] <= start, 'Grounding warmup did not precede measured accepted work')
    require(result.get('nativeDispatchSequence') == 1 and result.get('nativeTransferTicks') == 1, 'Native callback/transfer repeated or absent')
    require(0 < integer(result.get('requestObservations'), 'requestObservations') <= ticks, 'Native demand observation count exceeds actual ticks')
    require(integer(result.get('repeatedAccepts'), 'repeatedAccepts') >= 2, 'Paid retry not exercised')
    require(result.get('maximumWorkerDisplacement', 0) > .1 and result.get('movementWhileNativeStorage', 0) > .1, 'No actual short native storage movement')
    require(PHASES <= set(result.get('nativeStoragePhases', [])), 'Missing native storage lifecycle')
    refusals = result.get('refusals', [])
    require(REFUSALS <= {row.get('case') for row in refusals}, 'Missing protected/stale/unloaded/support/payment refusal')
    require(all(row.get('reason') and row.get('noExtraDebit') is True for row in refusals), 'Unproven refusal')
    money = result.get('treasury', {})
    require(money.get('funding') == money.get('debit') == 64 and money.get('debitCount') == 1, 'Wrong single fee')
    require(integer(money.get('finalBalance'), 'finalBalance') == integer(money.get('observedTaxes'), 'observedTaxes'), 'Unexplained Treasury money')
    require(re.fullmatch('[0-9a-f]{64}', result.get('nativeAccountingReceipt', '')), 'Missing native material receipt')
    require(result.get('supplyReceipt'), 'Missing actual persisted source receipt')
    samples = result.get('samples', [])
    require(len(samples) >= 2, 'Missing native tick observations')
    last_tick = last_worker_tick = -1
    stable_sample_found = False
    for row in samples:
        bound(row)
        require(observed_start < row['gameTime'] <= end, 'END sample outside actual observer window')
        require(row['gameTime'] > last_tick and row['workerTickCount'] > last_worker_tick, 'Non-progressing native observations')
        if row['gameTime'] >= stable_start:
            terminal(row)
        stable_sample_found |= row['gameTime'] == stable_start
        last_tick, last_worker_tick = row['gameTime'], row['workerTickCount']
    require(samples[0]['gameTime'] == observed_start + 1, 'First END sample does not bind observer start')
    require(stable_sample_found, 'Stable interval has no matching actual starting sample')
    transitions = result.get('transitions', [])
    require(len(transitions) == 2 and [row.get('event') for row in transitions] == ['native-source-to-worker', 'native-worker-to-world'],
            'Missing/extra/out-of-order native custody transitions')
    for transition in transitions:
        before, after = transition['before'], transition['after']
        bound(before); bound(after)
        require(after in samples, 'Custody callback outcome is absent from actual END observations')
        require(observed_start <= before['gameTime'] < after['gameTime'] <= end
                and after['gameTime'] == before['gameTime'] + 1
                and after['workerTickCount'] == before['workerTickCount'] + 1,
                'Custody callback did not occur within one exact observed ordinary tick')
    first, second = transitions
    require(first['after']['gameTime'] <= second['before']['gameTime']
            and second['after']['gameTime'] <= stable_start, 'Custody/placement/stability windows are out of order')
    require(sample(first['before']) == [2, 0, 0, 0] and sample(first['after']) == [1, 1, 0, 0], 'Wrong actual source transfer')
    require(first['before']['nativeStorageState'] == 'TAKE_NEEDED_ITEMS'
            and first['after']['nativeStorageState'].startswith('CLOSE_CHEST_')
            and first['before']['requestCount'] == 1 and first['after']['requestCount'] == 0
            and first['before']['supplyReceiptCount'] == 0 and first['after']['supplyReceiptCount'] == 1
            and first['before']['journalReceiptCount'] == first['after']['journalReceiptCount'] == 0,
            'Transfer not the actual retained native callback/request/receipt transition')
    require(sample(second['before']) == [1, 1, 0, 0] and sample(second['after']) == [1, 0, 1, 0], 'Wrong actual placement consumption')
    require(second['before']['requestCount'] == second['after']['requestCount'] == 0
            and second['before']['supplyReceiptCount'] == second['after']['supplyReceiptCount'] == 1
            and second['before']['journalReceiptCount'] == 0 and second['after']['journalReceiptCount'] == 1
            and second['after']['journalState'] == 'STAGE_VERIFIED', 'Placement lacks exact native receipt transition')
    require(second['after']['workerTickCount'] % 5 == 0, 'FILL callback violated native five-tick cadence')
    transfer_time, placement_time = first['after']['gameTime'], second['after']['gameTime']
    # Every observation must describe the same irreversible finite custody history.
    # Monotonic timestamps alone do not rule out a prebuilt cell or post-placement reversion.
    for row in [initial, final, *samples, *[event[side] for event in transitions for side in ('before', 'after')]]:
        time = row['gameTime']
        if time < transfer_time:
            require(sample(row) == [2, 0, 0, 0] and row['supplyReceiptCount'] == row['journalReceiptCount'] == 0
                    and row['journalState'] == 'READY', 'Observed custody/receipts precede the genuine source transfer')
        elif time < placement_time:
            require(sample(row) == [1, 1, 0, 0] and row['supplyReceiptCount'] == 1 and row['journalReceiptCount'] == 0
                    and row['requestCount'] == 0 and row['journalState'] == 'READY',
                    'Observed custody/receipts do not match the delivered pre-placement item')
        else:
            require(sample(row) == [1, 0, 1, 0] and row['supplyReceiptCount'] == row['journalReceiptCount'] == 1
                    and row['requestCount'] == 0 and row['journalState'] == 'STAGE_VERIFIED',
                    'Observed custody/receipts reverted or requested replacement after genuine placement')
    return result


def verify_directory(directory):
    directory = Path(directory).resolve(strict=True)
    result = validate(json.loads((directory / 'result.json').read_text()))
    for name in ('source-commit.txt', 'source-tree.txt'):
        require(re.fullmatch('[0-9a-f]{40}', (directory / name).read_text().strip()), 'Exact source identity absent')
    audit = json.loads((directory / 'native-bytecode-audit.json').read_text())
    require(audit.get('status') == 'passed' and audit.get('noVendorJarsUploaded') is True, 'Actual loaded native bytecode audit absent')
    for mod, expected in ARTIFACTS.items():
        actual = audit.get('artifacts', {}).get(mod, {})
        require(actual.get('fileName') == expected and actual.get('sha256') == result['loadedCompanionArtifacts'][mod]['sha256'], 'Bytecode differs from loaded runtime')
        classes = actual.get('classes', [])
        required = {'com.talhanation.workers.world.BuildBlockParse', 'com.talhanation.workers.entities.ai.GetNeededItemsFromStorage',
                    'com.talhanation.workers.entities.ai.BuilderWorkGoal'} if mod == 'workers' else {'com.talhanation.recruits.inventory.RecruitSimpleContainer'}
        require(required <= {row.get('class') for row in classes}, 'Relevant pinned native bytecode missing')
        for row in classes:
            name = row['file']; require(Path(name).name == name and name.endswith('.javap.txt'), 'Unsafe disassembly path')
            data = (directory / 'native-bytecode' / name).read_bytes()
            require(hashlib.sha256(data).hexdigest() == row['sha256'], 'Native disassembly digest differs')
    require({'01-accepted-empty-target.png', '02-native-filled-target.png'} <= set(result.get('screenshots', [])), 'Missing actual client screenshots')
    for name in result['screenshots']:
        require(Path(name).name == name and name.endswith('.png'), 'Unsafe screenshot path')
        data = (directory / name).read_bytes()
        require(data[:8] == b'\x89PNG\r\n\x1a\n' and len(data) > 1024, 'Invalid screenshot')
        width, height = struct.unpack('>II', data[16:24]); require(width >= 640 and height >= 360, 'Framebuffer too small')
    return result


if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('usage: verify-native-earthworks.py EVIDENCE_DIRECTORY')
    verify_directory(sys.argv[1])
    print('Passed genuine native one-FILL supply/accounting evidence. Partial STAGE_VERIFIED only; no CUT, retirement or recovery claim.')
