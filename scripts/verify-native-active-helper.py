#!/usr/bin/env python3
"""Strict oracle for the separately opted-in same-stage helper/disk scenario."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import uuid

ROOT = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location('helper_api_gate', ROOT / 'verify-native-helper-api.py')
GATE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GATE)


def require(okay, message):
    if not okay:
        raise ValueError(message)


def integer(value, minimum=0, maximum=2**63-1):
    require(type(value) is int and minimum <= value <= maximum, 'Invalid bounded integer evidence')
    return value


def uid(value):
    require(isinstance(value, str) and str(uuid.UUID(value)) == value and int(uuid.UUID(value)) != 0, 'Invalid UUID')
    return value


def inventory(value):
    require(isinstance(value, dict) and set(value) == {'slots', 'main', 'off', 'beforeItemSlot', 'persistent'}, 'Incomplete inventory evidence')
    require(isinstance(value['slots'], list) and 6 <= len(value['slots']) <= 128, 'Invalid native inventory shape')
    for item in value['slots'] + [value['main'], value['off'], value['persistent']]:
        require(isinstance(item, str) and 1 <= len(item) <= 256*1024, 'Missing/bounded inventory value')
    integer(value['beforeItemSlot'], -1, 127)
    return value


def cargo(value):
    inventory(value)
    return {key: value[key] for key in ('slots', 'main', 'off', 'beforeItemSlot')}


def materials(value):
    require(isinstance(value, dict) and set(value) == {'cobble', 'oak'}, 'Missing material census')
    for name, total in [('cobble', 352), ('oak', 220)]:
        counts = value[name]
        require(isinstance(counts, dict) and set(counts) == {'built', 'chest', 'workerCustody', 'owner', 'loose', 'total'}, 'Malformed material census')
        for count in counts.values(): integer(count, 0, total)
        require(counts['total'] == total and counts['owner'] == counts['loose'] == 0
                and sum(counts[key] for key in ('built', 'chest', 'workerCustody', 'owner', 'loose')) == total, 'Finite material conservation mismatch')


def ordered(values, key, limit):
    require(isinstance(values, list) and 0 < len(values) <= limit, 'Missing/bounded ordered evidence')
    numbers = [integer(value.get(key), 1) for value in values]
    require(numbers == sorted(set(numbers)), 'Duplicate/reordered event evidence')


def verify_transfers(data, helper, owner):
    events = data.get('activeHelperTransitEvents'); ordered(events, 'ordinal', 128)
    before = [e for e in events if e.get('kind') == 'transfer-before']
    require(len(before) == 4, 'Exactly four supported native transfers required')
    expected = [('minecraft:overworld', 'minecraft:the_nether'), ('minecraft:the_nether', 'minecraft:overworld')]*2
    allowed = {'transfer-before', 'join-high', 'join-low', 'dimension-source-leave', 'transfer-settled', 'leave-observed-not-final-removal'}
    require(all(e.get('kind') in allowed and e.get('uuid') == helper for e in events), 'Unexpected transfer/failure/actor evidence')
    for index, start in enumerate(before):
        end = before[index+1]['ordinal'] if index+1 < len(before) else 2**63
        group = [e for e in events if start['ordinal'] <= e['ordinal'] < end]
        # A real disk join is separate from native non-disk transfer admission.
        transfer = [e for e in group if e['kind'] in {'transfer-before', 'dimension-source-leave', 'transfer-settled'}
                    or e['kind'] in {'join-high', 'join-low'} and e.get('loadedFromDisk') is False]
        require([e['kind'] for e in transfer] == ['transfer-before', 'join-high', 'join-low', 'dimension-source-leave', 'transfer-settled'], 'Missing/reordered native callback custody')
        first, high, low, leave, settled = transfer
        require(first['dimension'] == expected[index][0] and high['dimension'] == low['dimension'] == settled['dimension'] == expected[index][1], 'Wrong transfer route')
        for event in (first, high, low, settled):
            require(event.get('owner') == owner and event.get('followState') == 6 and event.get('alive') is True
                    and event.get('removed') is False and event.get('neededItems') == 0, 'Transfer lost ordinary live owner/closed request state')
            require(inventory(event.get('inventory'))['beforeItemSlot'] == -1, 'Noncanonical borrowed hand at transfer')
        require(high.get('canceled') is False and low.get('canceled') is False, 'Canceled transfer admission')
        require(first['inventory'] == high['inventory'] and cargo(first['inventory']) == cargo(low['inventory'])
                and low['inventory'] == settled['inventory'], 'Native transfer changed cargo or raw/settled receipts')
        require(leave.get('sourceInventoryResampled') is False and leave.get('dimension') == expected[index][0], 'Removed source was counted as custody')


def verify_mirrors(data):
    events, joins = data['events'], data['nativeWorkerJoins']
    armed = [event for event in events if event.get('kind') == 'load-mirror-armed']
    restored = [event for event in events if event.get('kind') == 'load-mirror-restored']
    require(len(armed) == len(restored) == len(joins) == 6, 'Missing observed native load-mirror lifecycle')
    for actor in data['workers']:
        admissions = [event for event in joins if event['worker'] == actor]
        starts = [event for event in armed if event.get('worker') == actor]
        ends = [event for event in restored if event.get('worker') == actor]
        require(len(admissions) == len(starts) == len(ends), 'Wrong actor mirror custody')
        for index, (join, before, after) in enumerate(zip(admissions, starts, ends)):
            require(join['sequence'] < before['sequence'] < after['sequence']
                    and (index + 1 == len(starts) or after['sequence'] < starts[index+1]['sequence']), 'Overlapping or reordered mirror custody')
            require(inventory(before.get('raw')) == inventory(join.get('inventory'))
                    and cargo(before['raw']) == cargo(before.get('armed'))
                    and inventory(after.get('before')) == before['armed'], 'Load-mirror comparison lost raw custody')
            require(type(before.get('split')) is bool and after.get('split') is before['split']
                    and after.get('mainAliasesSlot5') is True, 'Production did not restore canonical hand identity')
            require(integer(after.get('rebindsAfter')) >= integer(after.get('rebindsBefore')) + int(before['split']), 'Missing ordinary production mirror rebind')
            inventory(after.get('after'))


def verify_tickets(data):
    owner = uid(data.get('activeHelperTicketOwner')); require(owner not in data['workers'], 'Ticket owner aliases worker')
    values = data.get('activeHelperTicketEvents'); ordered(values, 'ordinal', 10000)
    key = 'minecraft:the_nether/siegeoverhaul/0/TickingEntities/' + owner
    allowed = {'add-pending', 'add-return', 'remove-pending', 'remove-return', 'zero-owned-verified'}
    state, baseline, cycles = 'zero', None, 0
    for event in values:
        require(event.get('kind') in allowed and event.get('owner') == owner and event.get('dimension') == 'minecraft:the_nether'
                and event.get('mod') == 'siegeoverhaul' and event.get('chunkX') == 0 and event.get('chunkZ') == 0
                and event.get('ticking') is True, 'Failed/unapproved persistent ticket event')
        census = event.get('census'); require(isinstance(census, dict), 'Missing exact ticket census')
        own = [k for k in census if k.endswith('/' + owner)]
        require(own in ([], [key]), 'Wrong/multiple owned ticket tuple')
        kind = event['kind']
        if kind == 'add-pending':
            require(state == 'zero' and not own, 'Add lacks absent preflight'); baseline = census; state = 'adding'
        elif kind == 'add-return':
            require(state == 'adding' and event.get('nativeReturn') is True and own == [key]
                    and {k:v for k,v in census.items() if k != key} == baseline, 'Partial/unmatched native ticket add'); state = 'held'
        elif kind == 'remove-pending':
            require(state == 'held' and own == [key] and {k:v for k,v in census.items() if k != key} == baseline, 'Removal changed unrelated census'); state = 'removing'
        elif kind == 'remove-return':
            require(state == 'removing' and event.get('nativeReturn') is True and not own and census == baseline, 'Unverified exact native removal'); state = 'zero'; cycles += 1
        else:
            require(state == 'zero' and not own, 'Zero-ticket claim while intent/tuple remains')
    require(state == 'zero' and cycles == 3 and data.get('finalOwnedTicketCount') == 0, 'Missing three exact add/remove cycles or final zero')



def verify_eviction_release(data, helper):
    events = data['events']
    settled = [e for e in events if e.get('kind') == 'ordinary-end-settled-custody']
    releases = [e for e in events if e.get('kind') == 'eviction-ticket-released-after-settled']
    evicts = [e for e in events if e.get('kind') == 'phase' and e.get('phase') == 'EVICT']
    before = [e for e in events if e.get('kind') == 'before-disk-transfer']
    transfers = [e for e in data['activeHelperTransitEvents'] if e.get('kind') == 'transfer-settled']
    removes = [e for e in data['activeHelperTicketEvents'] if e.get('kind') == 'remove-pending']
    removed = [e for e in data['activeHelperTicketEvents'] if e.get('kind') == 'remove-return']
    require(len(settled) == len(transfers) == 4 and len(releases) == len(evicts) == len(before) == 1
            and len(removes) == len(removed) == 3, 'Missing exact settled-before-eviction release evidence')
    end, release, evict = settled[2], releases[0], evicts[0]
    start_tick = integer(before[0].get('gameTime')); end_tick = integer(end.get('gameTime'))
    require(end.get('worker') == release.get('worker') == helper and end.get('dimension') == 'minecraft:the_nether'
            and end.get('phase') == release.get('phase') == 'SECOND_SETTLE'
            and integer(end.get('transferGameTime')) == integer(release.get('transferGameTime')) == start_tick < end_tick
            and integer(release.get('settledGameTime')) == integer(release.get('gameTime')) == integer(evict.get('gameTime')) == end_tick,
            'Eviction clock/release precedes the later ordinary END custody proof')
    require(before[0]['sequence'] < end['sequence'] < release['sequence'] < evict['sequence'], 'Eviction phase precedes settled release')
    require(100 <= integer(data.get('offlineRemovalProof', {}).get('gameTime')) - end_tick <= 1800,
            'Finalized unload lacks the fixed post-release absence window')
    call_ns, end_ns, pending_ns, removed_ns, release_ns = [integer(e.get('nanoTime'),1) for e in (transfers[2],end,removes[1],removed[1],release)]
    require(call_ns < end_ns < pending_ns < removed_ns < release_ns, 'Holding ticket was released before settled custody')
    require(any(e.get('kind') == 'zero-owned-verified' and removed_ns < integer(e.get('nanoTime'),1) < release_ns
                for e in data['activeHelperTicketEvents']), 'Eviction starts without post-removal zero-ticket proof')


def verify_absence_work(work, bounds, helper):
    for left, right in [('before-first-transfer','first-native-return'),('before-disk-transfer','disk-native-return')]:
        require(not any(w['worker'] == helper and bounds[left]['sequence'] < w['sequence'] < bounds[right]['sequence'] for w in work), 'Absent helper supplied or placed native work')


def verify_report(data):
    require(isinstance(data, dict) and type(data.get('formatVersion')) is int and data['formatVersion'] == 1 and data.get('status') == 'passed'
            and data.get('phase') == 'FINISHED' and data.get('mode') == 'active-helper-same-stage-disk', 'Scenario did not pass')
    require('failure' not in data and 'ticketCleanupFailure' not in data, 'Failure hidden by pass flag')
    for field in ('boundedScenarioVerified', 'sameStageReturnVerified', 'helperDiskEvictionVerified', 'coordinatorFirstDiskOrderVerified', 'postDiskHelperPlacementVerified'):
        require(data.get(field) is True, 'Missing bounded lifecycle proof: ' + field)
    for field in ('stageSkippedReturnVerified', 'canceledWhileAbsentVerified', 'wholeSiteEvictionVerified', 'wholePlanCompleted', 'nativeWorkerChunkLoading'):
        require(data.get(field) is False, 'Unproved/unsafe coverage claim: ' + field)
    for field, expected in [('totalLimitSeconds', 600), ('phaseLimitTicks', 1200), ('evictionLimitTicks', 1800), ('finalizationLimitTicks', 200), ('stableGroundedEndTicks', 5), ('absenceObservationTicks', 100), ('paidFee', 64)]:
        require(type(data.get(field)) is int and data[field] == expected, 'Changed fixed scenario bound: ' + field)
    require(type(data.get('elapsedSeconds')) in (int, float) and 0 < data['elapsedSeconds'] < 600 and data.get('ownerMode') == 'non-op Survival', 'Runtime/owner mode changed')
    require(integer(data.get('cancellationStableTicks'), 100, 1200) >= 100, 'Cancellation stability missing')
    ids = data.get('workers'); require(isinstance(ids, list) and len(ids) == len(set(ids)) == 2, 'Not exactly two original workers')
    coordinator, helper = [uid(value) for value in ids]; owner = uid(data.get('owner')); area = uid(data.get('firstStageArea'))
    uid(data.get('projectId')); uid(data.get('ledgerGeneration'))
    require(owner not in ids and re.fullmatch('[0-9a-f]{64}', data.get('manifestHash', '')), 'Invalid paid identity')
    targets = data.get('firstStageTargets'); require(isinstance(targets, list) and len(targets) == len(set(targets)) == 139, 'Changed first-stage envelope')
    for cell in targets: integer(cell, -(2**63))
    target_materials = data.get('firstStageMaterials')
    require(isinstance(target_materials, dict) and set(target_materials) == {str(cell) for cell in targets}
            and all(value in ('minecraft:cobblestone', 'minecraft:oak_planks') for value in target_materials.values()), 'Missing exact first-stage material targets')
    events, work, joins = data.get('events'), data.get('nativeWork'), data.get('nativeWorkerJoins')
    for values, limit in [(events, 4096), (work, 1024), (joins, 16)]: ordered(values, 'sequence', limit)
    sequences = [event['sequence'] for values in (events, work, joins) for event in values]
    require(len(sequences) == len(set(sequences)), 'Evidence sequence aliases')
    def one(kind):
        matches = [event for event in events if event.get('kind') == kind]
        require(len(matches) == 1, 'Missing/duplicate boundary: ' + kind)
        return matches[0]
    kinds = ['commission', 'before-first-transfer', 'same-stage-absence', 'first-native-return', 'same-stage-resumed',
             'before-disk-transfer', 'genuine-helper-disk-unload', 'actual-server-stop', 'actual-server-reopen-before-ticks', 'coordinator-first-disk-reopen',
             'coordinator-native-progress-while-helper-offline', 'later-helper-disk-join', 'disk-native-return', 'disk-return-resumed', 'loaded-workers-canceled']
    bounds = {kind: one(kind) for kind in kinds}
    require([bounds[k]['sequence'] for k in kinds] == sorted(bounds[k]['sequence'] for k in kinds), 'Lifecycle boundaries out of order')
    counts = {coordinator: 0, helper: 0}; supplied = set(); seen = set()
    for event in work:
        actor = event.get('worker'); require(actor in ids and event.get('area') == area and event.get('authorized') is True, 'Foreign/unprotected native work')
        before, after = event.get('before'), event.get('after'); require(isinstance(before, list) and isinstance(after, list) and len(before) == len(after) == 4, 'Missing material debit')
        for number in before + after: integer(number, 0, 572)
        cells = event.get('cells'); require(isinstance(cells, list) and len(cells) == len(set(cells)), 'Malformed placement cells')
        cb, oak = integer(event.get('placedCobble'), 0, 139), integer(event.get('placedOak'), 0, 139)
        if event.get('kind') == 'native-placement':
            require(event.get('nativeState') == 'PLACE_BLOCKS' and cells and set(cells) <= set(targets) and not seen.intersection(cells)
                    and cb == sum(target_materials[str(cell)] == 'minecraft:cobblestone' for cell in cells)
                    and cb + oak == len(cells) and before[0]-after[0] == cb and before[1]-after[1] == oak
                    and before[2:] == after[2:], 'Unmatched/duplicate native placement debit')
            seen.update(cells); counts[actor] += len(cells)
        else:
            require(event.get('kind') == 'native-supply' and not cells and cb == oak == 0
                    and after[0] >= before[0] and after[1] >= before[1] and after[:2] != before[:2]
                    and after[0]-before[0] == before[2]-after[2] and after[1]-before[1] == before[3]-after[3], 'Supply lacks finite original chest withdrawal')
            supplied.add(actor)
    require(supplied == set(ids) and all(counts.values()) and data.get('placementCounts') == counts
            and data.get('retainedPlacedBlocks') == len(seen) < 139, 'Missing original contributions or partial-work retention')
    for kind, boundary in bounds.items():
        if kind in ('actual-server-stop', 'actual-server-reopen-before-ticks'): continue
        require({'placementCounts','materials','ownerMode','pendingTargets','workers','claims','gameTime'} <= set(boundary), 'Incomplete required boundary schema: ' + kind)
        require(isinstance(boundary['workers'], dict) and set(boundary['workers']) == set(ids)
                and isinstance(boundary['claims'], list) and len(boundary['claims']) <= 2, 'Missing exact worker/claim boundary')
        for actor in ids:
            value = boundary['workers'][actor]; inventory(value.get('inventory'))
            require(value.get('dimension') in ('minecraft:overworld','minecraft:the_nether')
                    and type(value.get('removed')) is bool and type(value.get('handPending')) is bool
                    and type(value.get('mainAliasesSlot5')) is bool and isinstance(value.get('nativeState'), str), 'Malformed worker lifecycle boundary')
            integer(value.get('entityTick')); integer(value.get('handRebindCount'))
        claim_targets = []
        for claim in boundary['claims']:
            require(claim.get('worker') in ids and claim.get('area') == area and claim.get('target') in targets, 'Foreign boundary claim')
            claim_targets.append(claim['target'])
        require(len(claim_targets) == len(set(claim_targets)), 'Duplicate boundary claim cell')
        calculated = {actor: sum(len(w['cells']) for w in work if w['kind'] == 'native-placement' and w['worker'] == actor and w['sequence'] < boundary['sequence']) for actor in ids}
        require(boundary['placementCounts'] == calculated and boundary.get('ownerMode') == 'non-op Survival'
                and boundary.get('pendingTargets') == 572 - sum(calculated.values()), 'Boundary native progress differs')
        materials(boundary.get('materials'))
        for name, key in [('cobble','placedCobble'),('oak','placedOak')]:
            require(boundary['materials'][name]['built'] == sum(w[key] for w in work if w['kind']=='native-placement' and w['sequence'] < boundary['sequence']), 'Boundary world material differs from native receipts')
        if kind != 'loaded-workers-canceled': require(boundary.get('projectState') == 'RUNNING' and boundary.get('stage') == 0, 'Stage advanced before bounded proof')
    for value in data.get('placementCounts', {}).values(): integer(value, 1, 138)
    first = bounds['before-first-transfer']
    require(all(first['placementCounts'].values()) and {w['worker'] for w in work if w['kind'] == 'native-supply' and w['sequence'] < first['sequence']} == set(ids), 'Both workers must contribute and withdraw before absence')
    for kind in ('same-stage-absence', 'genuine-helper-disk-unload', 'coordinator-first-disk-reopen', 'coordinator-native-progress-while-helper-offline', 'later-helper-disk-join'):
        b = bounds[kind]; require(b.get('helperSuspended') is True and b.get('helperActive') is False and b.get('helperGranted') is False, 'Absence retained work authority')
    for kind in ('same-stage-resumed', 'disk-return-resumed'):
        b = bounds[kind]; require(b.get('helperActive') is True and b.get('helperGranted') is True and b.get('helperSuspended') is False and b.get('helperRejoining') is False, 'Returned worker never fully rejoined')
    require(bounds['same-stage-absence']['gameTime'] - first['gameTime'] >= 100
            and bounds['coordinator-native-progress-while-helper-offline']['gameTime'] - bounds['coordinator-first-disk-reopen']['gameTime'] >= 100, 'Missing ordinary absence observation window')
    require(bounds['same-stage-absence']['placementCounts'][coordinator] > first['placementCounts'][coordinator]
            and bounds['same-stage-resumed']['placementCounts'][helper] > first['placementCounts'][helper]
            and bounds['coordinator-native-progress-while-helper-offline']['placementCounts'][coordinator] > bounds['coordinator-first-disk-reopen']['placementCounts'][coordinator]
            and bounds['disk-return-resumed']['placementCounts'][helper] > bounds['later-helper-disk-join']['placementCounts'][helper], 'Missing real continuation placements')
    proof = data.get('offlineRemovalProof'); require(isinstance(proof, dict) and proof.get('reason') == 'UNLOADED_TO_CHUNK'
            and proof.get('shouldDestroy') is False and proof.get('shouldSave') is True and proof.get('chunkAvailable') is False
            and proof.get('registered') is False and 0 <= integer(proof.get('gameTime'))-integer(proof.get('unavailableAt')) <= 200, 'Missing finalized native eviction')
    disks = [e for e in joins if e.get('loadedFromDisk') is True]
    require(len(joins) == 6 and all(e.get('worker') in ids and type(e.get('loadedFromDisk')) is bool for e in joins), 'Extra or malformed worker admission')
    require(len(disks) == 2 and [e.get('worker') for e in disks] == ids and disks[0].get('dimension') == 'minecraft:overworld'
            and disks[1].get('dimension') == 'minecraft:the_nether', 'Missing real coordinator-first disk admission')
    require(disks[1]['sequence'] > bounds['coordinator-native-progress-while-helper-offline']['sequence'], 'Helper disk admission occurred too early')
    require(inventory(data.get('finalizedOfflineInventory')) == inventory(disks[1].get('inventory'))
            and inventory(bounds['actual-server-stop'].get('offlineHelper')) == disks[1]['inventory']
            and inventory(bounds['actual-server-stop'].get('coordinator')) == inventory(disks[0].get('inventory')), 'Disk inventory custody changed')
    stop, reopen = bounds['actual-server-stop'], bounds['actual-server-reopen-before-ticks']
    require(stop.get('authority') == reopen.get('authority') and stop.get('ledger') == reopen.get('ledger') and isinstance(stop.get('ledger'), str), 'Pre-tick durable authority changed')
    require(len([e for e in events if e.get('kind') == 'ordinary-end-settled-custody']) == 4, 'Missing ordinary END single-custody recheck')
    verify_absence_work(work, bounds, helper)
    for name,key in [('cobble','placedCobble'),('oak','placedOak')]:
        require(data.get('finalMaterials',{}).get(name,{}).get('built') == sum(w[key] for w in work if w['kind']=='native-placement'), 'Final built material differs from native receipts')
    verify_transfers(data, helper, owner); verify_mirrors(data); verify_tickets(data); verify_eviction_release(data, helper); materials(data.get('finalMaterials'))
    initial, final = data.get('auxiliaryIsolationInitial'), data.get('auxiliaryIsolationFinal')
    require(isinstance(initial, dict) and isinstance(final, dict)
            and {k:v for k,v in initial.items() if k != 'gameTime'} == {k:v for k,v in final.items() if k != 'gameTime'}
            and integer(final.get('gameTime')) > integer(initial.get('gameTime'))
            and initial.get('feetY') == 48 and initial.get('standingVerticalRange') == 12
            and isinstance(initial.get('auxiliaries'), list) and 1 <= len(initial['auxiliaries']) <= 4
            and isinstance(initial.get('baseline'), list) and len(initial['baseline']) == len(initial['auxiliaries'])*45, 'Auxiliary isolation baseline changed')
    treasury = data.get('treasuryAccounting'); require(isinstance(treasury, dict) and treasury.get('startingFunds') == 2000
            and treasury.get('commissionDebit') == 64 and treasury.get('initialPaidBalance') == 1936 and treasury.get('singleNegativeLedgerDelta') == -64
            and treasury.get('expectedFinalBalance') == 1936 + integer(treasury.get('interestCredits')) + integer(treasury.get('taxCredits')), 'Unexplained Treasury debit or compensation')
    require(data.get('activeHelperHoldingSetup', {}).get('synthetic') is True and data['activeHelperHoldingSetup'].get('dimension') == 'minecraft:the_nether', 'Unlabeled/wrong synthetic holding site')
    require(data.get('screenshots') == ['03-authenticated-cancel.png', '04-helper-lifecycle-complete.png'], 'Missing actual client evidence')
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('evidence', type=Path); parser.add_argument('runtime_manifest', type=Path); args = parser.parse_args()
    try:
        path = args.evidence / 'result.json'; require(path.stat().st_size <= 32*1024*1024, 'Result exceeds bounded size')
        data = GATE.CAPTURE.parse_unique(path.read_bytes()); verify_report(data)
        gate_path = args.evidence / 'api-gate/gate.json'; gate = GATE.verify_receipt(args.runtime_manifest, gate_path)
        for field, file in [('sourceCommit', 'source-commit.txt'), ('sourceTree', 'source-tree.txt')]:
            require(data.get(field) == gate[field] == (args.evidence/file).read_text().strip(), 'Result/source/prelaunch identity differs')
        parity = data.get('loadedApiParity'); require(isinstance(parity, dict) and parity.get('passed') is True
                and parity.get('gateSha256') == hashlib.sha256(gate_path.read_bytes()).hexdigest()
                and parity.get('contractSha256') == gate['reviewedGateContractSha256'], 'Missing loaded-provider parity')
        expected = {c['name']: {'provider': c['supplierCanonicalPath'], 'supplierSha256': c['supplierSha256'], 'rawResourceSha256': c['classSha256']} for c in gate['classes']}
        require(parity.get('classes') == expected, 'Loaded class closure differs from prelaunch pins')
        for name in data['screenshots']:
            image = args.evidence/name; require(image.is_file() and image.stat().st_size > 4096 and image.read_bytes()[:8] == b'\x89PNG\r\n\x1a\n', 'Missing/non-PNG screenshot')
    except (ValueError, KeyError, TypeError, OSError) as error:
        parser.exit(1, f'Active helper acceptance rejected: {error}\n')
    print('Verified bounded same-stage helper transit and coordinator-first disk scenario; broader lifecycle variants remain unproved.')


if __name__ == '__main__': main()
