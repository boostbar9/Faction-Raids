#!/usr/bin/env python3
"""Fail closed unless a real bounded representative native lifecycle produced all evidence."""
import json
import hashlib
import os
import sys
from pathlib import Path


def load_evidence_json(path):
    """Reject duplicate records rather than silently accepting the last JSON value."""
    def unique_object(pairs):
        result = {}
        for key, value in pairs:
            assert key not in result, ('Duplicate evidence key', key)
            result[key] = value
        return result

    def reject_constant(value):
        raise AssertionError(('Nonfinite evidence value', value))

    return json.loads(path.read_text(), object_pairs_hook=unique_object, parse_constant=reject_constant)


def verify_native_bytecode_audit(audit, shared_crew):
    assert audit.get('status') == 'passed' and audit.get('noVendorJarsUploaded') is True, audit
    artifacts = audit.get('artifacts')
    assert isinstance(artifacts, dict) and set(artifacts) == {'recruits', 'workers'}, audit
    expected_extra = {
        'recruits': {'com.talhanation.recruits.entities.AbstractChunkLoaderEntity',
                     'com.talhanation.recruits.pathfinding.AsyncPathfinderMob'},
        'workers': {'com.talhanation.workers.entities.BuilderEntity'},
    }
    captured = set()
    for mod, artifact in artifacts.items():
        assert isinstance(artifact, dict), artifact
        classes = artifact.get('classes')
        assert isinstance(classes, list), artifact
        names = []
        for record in classes:
            assert isinstance(record, dict) and isinstance(record.get('class'), str), record
            names.append(record['class'])
        assert len(names) == len(set(names)) and captured.isdisjoint(names), artifact
        captured.update(names)
        if shared_crew:
            assert expected_extra[mod].issubset(names), artifact
    assert len(captured) == (25 if shared_crew else 22), audit


def verify_final_materials(final, shared_crew):
    """Count actual owner pickups alongside construction, live workers and loose items."""
    assert isinstance(final, dict), final
    for material, initial in [('Cobble', 352), ('Oak', 220)]:
        counts = []
        for store in ['placed', 'chest', 'builder', 'loose', 'owner']:
            key = store + material
            value = final.get(key, 0) if store == 'owner' and not shared_crew else final.get(key)
            assert type(value) is int and value >= 0, (key, final)
            counts.append(value)
        assert sum(counts) == initial, (material, final)


def verify_shared_crew_death_cargo(data, ids, first_reload_tick):
    """Accept only the single, exact native transfer captured at coordinator death.

    NBT strings are bounded review diagnostics, not parsed inventory/debit proof.
    The authoritative proof is the exact item-entity delta with other stores fixed.
    """
    from collections import Counter
    from uuid import UUID

    proof = data.get('sharedCrewDeathCargo')
    keys = {'worker', 'helper', 'gameTime', 'stage', 'projectState', 'closedLifecycles',
            'dropsEnabled', 'originalAliveBefore', 'originalAliveAfter', 'verified', 'before',
            'newDrops', 'looseBefore', 'looseAfter', 'remainingStoresBefore', 'remainingStoresAfter',
            'originalCargoBefore', 'originalCargoAfter', 'accountingExclusion', 'baselineScope',
            'aliasedDropStacks'}
    assert isinstance(proof, dict) and set(proof) == keys, proof
    succession = data.get('sharedCrewSuccession')
    assert isinstance(succession, dict), succession
    assert proof.get('worker') == succession.get('deadCoordinator') == ids[0], proof
    assert proof.get('helper') == succession.get('survivingHelper') == ids[1], proof
    death_tick, resume_tick = succession.get('deathGameTime'), succession.get('resumeGameTime')
    assert type(first_reload_tick) is int and first_reload_tick >= 0, first_reload_tick
    assert type(death_tick) is int and type(resume_tick) is int, succession
    assert type(proof.get('gameTime')) is int and proof.get('gameTime') == death_tick, proof
    assert first_reload_tick < death_tick < resume_tick, proof
    assert type(proof.get('stage')) is int and proof.get('stage') == 0, proof
    assert proof.get('projectState') == 'RUNNING', proof
    for key in ['closedLifecycles', 'dropsEnabled', 'originalAliveBefore', 'verified']:
        assert proof.get(key) is True, (key, proof)
    for key in ['originalAliveAfter', 'aliasedDropStacks']:
        assert proof.get(key) is False, (key, proof)
    assert proof.get('accountingExclusion') == 'exact dying original after verified native drop transfer', proof
    assert proof.get('baselineScope') == 'Pinned baseline companions only; corpse addons not exercised', proof
    assert succession.get('extraCommission') is False and succession.get('newBuilderSpawnedAfterDeath') is False, succession
    handoff = succession.get('deathHandoff')
    assert isinstance(handoff, dict) and set(handoff) == {'from', 'to', 'area', 'cause', 'receipt', 'projectGeneration', 'ledgerGeneration'}, handoff
    assert handoff.get('from') == ids[0] and handoff.get('to') == ids[1], handoff
    assert handoff.get('area') == data['stageLayout'][0]['area'] and handoff.get('cause') == 'DEATH', handoff
    generation = handoff.get('projectGeneration')
    assert type(generation) is int and generation > 0, handoff
    ledger_generation = handoff.get('ledgerGeneration')
    assert isinstance(ledger_generation, str), handoff
    try:
        canonical_generation = str(UUID(ledger_generation))
    except ValueError:
        raise AssertionError(handoff) from None
    assert canonical_generation == ledger_generation, handoff
    receipt_input = ':'.join(['perimeter-builder-destroyed-v1', data['projectId'], str(generation),
                              data['manifestHash'], ids[0], handoff['area'], ledger_generation])
    assert handoff.get('receipt') == hashlib.sha256(receipt_input.encode('utf-8')).hexdigest(), handoff

    before = proof.get('before')
    assert isinstance(before, dict) and set(before) == {'cobble', 'oak'}, before
    assert all(type(value) is int and value >= 0 for value in before.values()), before
    assert before['cobble'] <= 352 and before['oak'] <= 220 and sum(before.values()) > 0, before
    store_keys = {'chestCobble', 'chestOak', 'helperCobble', 'helperOak', 'ownerCobble', 'ownerOak',
                  'placedCobble', 'placedOak'}
    for key in ['remainingStoresBefore', 'remainingStoresAfter']:
        stores = proof.get(key)
        assert isinstance(stores, dict) and set(stores) == store_keys, (key, stores)
        assert all(type(value) is int and value >= 0 for value in stores.values()), (key, stores)
    assert proof['remainingStoresBefore'] == proof['remainingStoresAfter'], proof

    def diagnostic(value):
        return isinstance(value, str) and 0 < len(value) <= 16384

    for key in ['originalCargoBefore', 'originalCargoAfter']:
        cargo = proof.get(key)
        assert isinstance(cargo, dict) and set(cargo) == {'slots', 'main', 'off'}, (key, cargo)
        slots = cargo.get('slots')
        assert isinstance(slots, list) and len(slots) <= 128, (key, cargo)
        assert all(diagnostic(value) for value in slots), (key, cargo)
        assert diagnostic(cargo.get('main')) and diagnostic(cargo.get('off')), (key, cargo)

    def drop_snapshot(key):
        rows = proof.get(key)
        assert isinstance(rows, list), (key, rows)
        snapshot = {}
        for row in rows:
            assert isinstance(row, dict) and set(row) == {'uuid', 'material', 'count', 'stack', 'tag'}, (key, row)
            identity = row.get('uuid')
            assert isinstance(identity, str), (key, row)
            try:
                canonical = str(UUID(identity))
            except ValueError:
                raise AssertionError((key, row)) from None
            assert canonical == identity and identity not in snapshot and identity not in ids, (key, row)
            material = row.get('material')
            assert isinstance(material, str) and material in {'minecraft:cobblestone', 'minecraft:oak_planks'}, (key, row)
            assert type(row.get('count')) is int and 1 <= row['count'] <= 64, (key, row)
            assert diagnostic(row.get('stack')) and row.get('tag') == '', (key, row)
            snapshot[identity] = row
        return snapshot

    loose_before = drop_snapshot('looseBefore')
    loose_after = drop_snapshot('looseAfter')
    new_drops = drop_snapshot('newDrops')
    assert all(loose_after.get(identity) == row for identity, row in loose_before.items()), proof
    assert set(new_drops).isdisjoint(loose_before), proof
    assert new_drops == {identity: row for identity, row in loose_after.items() if identity not in loose_before}, proof
    dropped = Counter()
    for row in new_drops.values():
        dropped[row['material']] += row['count']
    assert dropped['minecraft:cobblestone'] == before['cobble'], proof
    assert dropped['minecraft:oak_planks'] == before['oak'], proof
    for name, material, initial in [('cobble', 'minecraft:cobblestone', 352), ('oak', 'minecraft:oak_planks', 220)]:
        suffix = name.title()
        remaining = sum(proof['remainingStoresBefore'][store + suffix] for store in ['chest', 'helper', 'owner', 'placed'])
        loose_start = sum(row['count'] for row in loose_before.values() if row['material'] == material)
        loose_end = sum(row['count'] for row in loose_after.values() if row['material'] == material)
        assert before[name] + remaining + loose_start == initial, (material, proof)
        assert remaining + loose_end == initial, (material, proof)

    # A generic earlier contribution cannot substitute for each worker resuming after reopen.
    events = data.get('sharedCrewEvents')
    assert isinstance(events, list), events
    resumed = set()
    for event in events:
        assert isinstance(event, dict), event
        tick = event.get('gameTime')
        assert type(tick) is int and tick >= 0, event
        if event.get('kind') == 'native-placement' and event.get('cells') and first_reload_tick < tick < death_tick:
            assert event.get('builder') in ids, event
            assert type(event.get('stage')) is int and event.get('stage') == 0, event
            resumed.add(event.get('builder'))
    assert resumed == set(ids), (proof, resumed)


def verify_shared_crew_auxiliary_isolation(data, ids):
    """Independent fixed-fixture geometry: all unrelated bodies remain sealed below work."""
    import math
    from uuid import UUID

    parked = data.get('parkedUnrelatedStarterNpcIds')
    assert isinstance(parked, list) and 1 <= len(parked) <= 4 and len(set(parked)) == len(parked), parked
    assert set(parked).isdisjoint(ids + [data['sharedCrewAdmission']['owner']]), parked
    for identity in parked:
        assert isinstance(identity, str) and str(UUID(identity)) == identity, identity
    names = ['initial', 'mid-stage', 'canceled', 'final']
    if any(r.get('kind') == 'between-stages' for r in data['restarts']):
        names.insert(-2, 'between-stages')
    initial = None
    for name in names:
        proof = data.get('sharedCrewAuxiliaryIsolation-' + name)
        assert isinstance(proof, dict) and set(proof) == {'gameTime', 'feetY', 'planMinY',
                'standingVerticalRange', 'auxiliaries', 'baseline'}, (name, proof)
        assert type(proof['gameTime']) is int and proof['gameTime'] >= 0, proof
        assert all(type(proof[k]) is int for k in ['feetY', 'planMinY', 'standingVerticalRange']), proof
        assert (proof['feetY'], proof['planMinY'], proof['standingVerticalRange']) == (48, 65, 12), proof
        if name == 'initial':
            assert proof['gameTime'] < data['commissionGameTime'], proof
        else:
            assert proof['gameTime'] >= data['commissionGameTime'], proof
            if name != 'final':
                restart = next((r for r in data['restarts'] if r.get('kind') == name), None)
                assert restart is not None and type(restart.get('gameTime')) is int, (name, restart)
                assert proof['gameTime'] >= restart['gameTime'], (name, proof)
        actors = proof['auxiliaries']
        assert isinstance(actors, list) and len(actors) == len(parked), proof
        observed, centers, expected = set(), set(), {}
        for actor in actors:
            assert isinstance(actor, dict) and set(actor) == {'uuid', 'type', 'feet', 'bounds', 'alive',
                    'noAi', 'sameClaim', 'clearOfWorkEnvelope'}, actor
            assert actor['uuid'] in parked and actor['uuid'] not in observed, actor
            observed.add(actor['uuid'])
            assert isinstance(actor['type'], str) and actor['type'] not in {'workers:builder', 'minecraft:player'}, actor
            assert all(actor[k] is True for k in ['alive', 'noAi', 'sameClaim', 'clearOfWorkEnvelope']), actor
            feet = actor['feet']
            assert isinstance(feet, list) and len(feet) == 3 and all(type(v) is int for v in feet), actor
            x, y, z = feet
            assert x in (134, 137) and y == 48 and z in (6, 9) and tuple(feet) not in centers, actor
            centers.add(tuple(feet))
            box = actor['bounds']
            assert isinstance(box, list) and len(box) == 6 and all(type(v) in (int, float) and math.isfinite(v) for v in box), actor
            assert x <= box[0] < box[3] <= x + 1 and box[1] == 48 < box[4] <= 50 and z <= box[2] < box[5] <= z + 1, actor
            for dx in range(-1, 2):
                for dz in range(-1, 2):
                    expected[(x + dx, 64, z + dz)] = 'minecraft:stone'
                    for dy in range(-1, 3):
                        expected[(x + dx, y + dy, z + dz)] = ('minecraft:air' if dx == dz == 0 and dy in (0, 1)
                                                             else 'minecraft:stone')
        assert observed == set(parked), proof
        baseline = proof['baseline']
        assert isinstance(baseline, list) and len(baseline) == len(expected) == 45 * len(parked), proof
        actual = {}
        for cell in baseline:
            assert isinstance(cell, dict) and set(cell) == {'position', 'block'}, cell
            position = cell['position']
            assert isinstance(position, list) and len(position) == 3 and all(type(v) is int for v in position), cell
            assert tuple(position) not in actual, cell
            actual[tuple(position)] = cell['block']
        assert actual == expected, proof
        stable = {key: value for key, value in proof.items() if key != 'gameTime'}
        if initial is None:
            initial = stable
        else:
            assert stable == initial, (name, 'Auxiliary identity/body/baseline changed')
    final_tick = data['sharedCrewAuxiliaryIsolation-final']['gameTime']
    assert all(final_tick >= data['sharedCrewAuxiliaryIsolation-' + name]['gameTime'] for name in names), data


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
    verify_shared_crew_auxiliary_isolation(data, ids)
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
    verify_shared_crew_death_cargo(data, ids, first_reload_tick)
    for event in data['sharedCrewEvents']:
        worker = event['builder']
        assert worker in ids and event['area'] == stages[event['stage']]['area'], event
        assert event['gameTime'] >= data['commissionGameTime'], event
        for name in ['cobble', 'oak', 'chestCobble', 'chestOak', 'looseCobble', 'looseOak', 'ownerCobble', 'ownerOak']:
            for suffix in ['Before', 'After']:
                value = event.get(name + suffix)
                assert type(value) is int and value >= 0, (name + suffix, event)
        for key in ['ownerPresentBefore', 'ownerPresentAfter', 'verifiedDeathDropPickup']:
            assert type(event.get(key)) is bool, (key, event)
        pickup = event['verifiedDeathDropPickup']
        loose_decreases = {name: event['loose' + name.title() + 'Before'] - event['loose' + name.title() + 'After']
                           for name in ['cobble', 'oak']}
        if pickup:
            assert event['kind'] in ['native-placement', 'native-loose-pickup'], event
            assert worker == ids[1] and event['gameTime'] > death['deathGameTime'], event
            assert event['ownerPresentBefore'] is True and event['ownerPresentAfter'] is True, event
            assert all(value >= 0 for value in loose_decreases.values()) and any(value > 0 for value in loose_decreases.values()), event
            for name in ['chestCobble', 'chestOak', 'ownerCobble', 'ownerOak']:
                assert event[name + 'Before'] == event[name + 'After'], event
        else:
            assert all(value == 0 for value in loose_decreases.values()), event
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
                assert event[name + 'Before'] + loose_decreases[name] - event[name + 'After'] == changes[material], event
                chest = 'chest' + name.title()
                assert event[chest + 'Before'] == event[chest + 'After'], event
            counts[worker] += len(cells)
            if worker == ids[0]:
                assert event['gameTime'] <= death['deathGameTime'], event
            elif death['deathGameTime'] < event['gameTime'] <= death['resumeGameTime']:
                survivor_after_death = True
            if first_reload_tick < event['gameTime'] < death['deathGameTime']:
                resumed.add(worker)
        elif event['kind'] == 'native-supply-withdrawal':
            assert not pickup and not cells, event
            increases = []
            for name in ['cobble', 'oak']:
                chest = 'chest' + name.title()
                change = event[name + 'After'] - event[name + 'Before']
                assert event[chest + 'Before'] - event[chest + 'After'] == change, event
                increases.append(change)
            assert any(change > 0 for change in increases), event
            supplied.add(worker)
        else:
            assert event['kind'] == 'native-loose-pickup' and pickup and not cells, event
            for name in ['cobble', 'oak']:
                assert event[name + 'Before'] + loose_decreases[name] == event[name + 'After'], event
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
    data = load_evidence_json(root / 'result.json')
    audit = load_evidence_json(root / 'native-bytecode-audit.json')
    verify_native_bytecode_audit(audit, os.environ.get('SHARED_CREW_QA') == 'true')
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
    verify_final_materials(final, os.environ.get('SHARED_CREW_QA') == 'true')
    print('Representative native first-section completion, handoff, next-section placement and durable cancellation verified. Whole-plan completion was not attempted. Between-stage restart verified:', data['betweenStageRestartVerified'])

if __name__ == '__main__':
    main()
