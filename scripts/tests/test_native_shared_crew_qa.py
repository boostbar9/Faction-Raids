"""Fail-closed evidence contracts, using synthetic records; these do not run Minecraft."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

REPO = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('handoff_verifier', REPO / 'scripts/verify-native-staged-handoff.py')
VERIFIER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(VERIFIER)
IDS = ['11111111-1111-4111-8111-111111111111', '22222222-2222-4222-8222-222222222222']
DROP_IDS = ['44444444-4444-4444-8444-444444444444', '55555555-5555-4555-8555-555555555555',
            '66666666-6666-4666-8666-666666666666', '77777777-7777-4777-8777-777777777777']


def death_handoff():
    generation = '88888888-8888-4888-8888-888888888888'
    receipt_input = ':'.join(['perimeter-builder-destroyed-v1', 'project', '1', 'c' * 64,
                              IDS[0], 'area-0', generation])
    return {'from': IDS[0], 'to': IDS[1], 'area': 'area-0', 'cause': 'DEATH',
            'projectGeneration': 1, 'ledgerGeneration': generation,
            'receipt': hashlib.sha256(receipt_input.encode('utf-8')).hexdigest()}


def drop(identity, material, count):
    return dict(uuid=identity, material='minecraft:' + material, count=count,
                stack='{id:"minecraft:' + material + '",Count:' + str(count) + 'b}', tag='')


def death_cargo():
    loose_before = [drop(DROP_IDS[0], 'cobblestone', 2), drop(DROP_IDS[3], 'oak_planks', 1)]
    new_drops = [drop(DROP_IDS[1], 'cobblestone', 5), drop(DROP_IDS[2], 'oak_planks', 3)]
    stores = dict(chestCobble=337, chestOak=210, helperCobble=4, helperOak=6,
                  ownerCobble=0, ownerOak=0, placedCobble=4, placedOak=0)
    original_cargo = dict(slots=[new_drops[0]['stack'], new_drops[1]['stack']] + ['{}'] * 8, main='{}', off='{}')
    return dict(worker=IDS[0], helper=IDS[1], gameTime=40, stage=0, projectState='RUNNING',
                closedLifecycles=True, dropsEnabled=True, originalAliveBefore=True, originalAliveAfter=False,
                verified=True, aliasedDropStacks=False, before=dict(cobble=5, oak=3),
                newDrops=new_drops, looseBefore=loose_before, looseAfter=copy.deepcopy(loose_before + new_drops),
                remainingStoresBefore=stores, remainingStoresAfter=copy.deepcopy(stores),
                originalCargoBefore=original_cargo, originalCargoAfter=copy.deepcopy(original_cargo),
                accountingExclusion='exact dying original after verified native drop transfer',
                baselineScope='Pinned baseline companions only; corpse addons not exercised')


def record(worker, kind, tick, x=128, z=0):
    supplied = kind == 'native-supply-withdrawal'
    return dict(builder=worker, area='area-0', stage=0, gameTime=tick,
                nativeStateBefore='GET_ITEMS' if supplied else 'PLACE_BLOCKS', kind=kind,
                cells=[] if supplied else [dict(packed=(x << 38) | (z << 12) | 65,
                    position=f'{x}, 65, {z}', material='minecraft:cobblestone')],
                cobbleBefore=0 if supplied else 64, cobbleAfter=64 if supplied else 63,
                oakBefore=0, oakAfter=0, chestCobbleBefore=352, chestCobbleAfter=288 if supplied else 352,
                chestOakBefore=220, chestOakAfter=220,
                looseCobbleBefore=0, looseCobbleAfter=0, looseOakBefore=0, looseOakAfter=0,
                ownerCobbleBefore=0, ownerCobbleAfter=0, ownerOakBefore=0, ownerOakAfter=0,
                ownerPresentBefore=True, ownerPresentAfter=True, verifiedDeathDropPickup=False)


def pickup_evidence(placement=False):
    data = evidence()
    event = data['sharedCrewEvents'][-1] if placement else record(IDS[1], 'native-loose-pickup', 50)
    event.update(verifiedDeathDropPickup=True, cobbleBefore=0, cobbleAfter=4 if placement else 5,
                 oakBefore=0, oakAfter=3, looseCobbleBefore=7, looseCobbleAfter=2,
                 looseOakBefore=4, looseOakAfter=1)
    if not placement:
        event['cells'] = []
        data['sharedCrewEvents'].insert(-1, event)
    return data


def evidence():
    cargo = {worker: dict(slots=['{}'] * 10, main='{}', off='{}') for worker in IDS}
    events = [record(IDS[0], 'native-supply-withdrawal', 12), record(IDS[1], 'native-supply-withdrawal', 13),
              record(IDS[0], 'native-placement', 16), record(IDS[1], 'native-placement', 17, z=1),
              record(IDS[0], 'native-placement', 31, z=2), record(IDS[1], 'native-placement', 32, z=3),
              record(IDS[1], 'native-placement', 51, z=4)]
    return dict(sharedCrewRequested=True, sharedCrewVerified=True, builderReplacementRequested=False,
                sharedCrewBuilders=IDS, sourceCommit='a' * 40, sourceTree='b' * 40,
                projectId='project', manifestHash='c' * 64, commissionGameTime=10,
                sharedCrewAdmission=dict(coordinator=IDS[0], helper=IDS[1], project='project',
                    manifestHash='c' * 64, owner='33333333-3333-4333-8333-333333333333', area='area-0', gameTime=11),
                stageLayout=[dict(area='area-0')], sharedCrewEvents=events,
                sharedCrewPlacementCounts={IDS[0]: 2, IDS[1]: 3}, sharedCrewDistinctTargets=5, retainedPlacedBlocks=5,
                sharedCrewSuccession=dict(deadCoordinator=IDS[0], survivingHelper=IDS[1], placedBefore=4, placedAfter=5,
                    deathGameTime=40, resumeGameTime=51, manifestHash='c' * 64, extraCommission=False,
                    newBuilderSpawnedAfterDeath=False, deathHandoff=death_handoff()),
                sharedCrewDeathCargo=death_cargo(),
                sharedCrewPartialReloadVerified=True,
                **{'sharedCrewCargoReload-mid-stage': dict(stopped=cargo, loaded=copy.deepcopy(cargo))},
                restarts=[dict(kind='mid-stage', gameTime=30)],
                sharedCrewInterruption='Spectator pause', sharedCrewSleepCoverage='Not exercised: fixed noon',
                sharedCrewAttribution='LivingTick observer', sharedCrewGeometry='explicit legacy hollow fixture')


class SharedCrewEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        (self.root / 'source-commit.txt').write_text('a' * 40 + '\n')
        (self.root / 'source-tree.txt').write_text('b' * 40 + '\n')

    def verify(self, data):
        VERIFIER.verify_shared_crew(data, self.root)

    def test_complete_synthetic_contract(self):
        self.verify(evidence())

    def test_final_material_conservation_includes_owner_pickup_and_baseline_compatibility(self):
        final = dict(placedCobble=10, chestCobble=293, builderCobble=40, looseCobble=9,
                     placedOak=5, chestOak=200, builderOak=10, looseOak=5)
        VERIFIER.verify_final_materials(final, False)
        with self.assertRaises(AssertionError): VERIFIER.verify_final_materials(final, True)
        final.update(ownerCobble=0, ownerOak=0)
        VERIFIER.verify_final_materials(final, True)
        final.update(ownerCobble=7, looseCobble=2, ownerOak=3, looseOak=2)
        VERIFIER.verify_final_materials(final, True)
        VERIFIER.verify_final_materials(final, False)
        for key in ['ownerCobble', 'ownerOak']:
            missing = copy.deepcopy(final); missing.pop(key)
            with self.subTest(missing=key), self.assertRaises(AssertionError): VERIFIER.verify_final_materials(missing, True)
            for value in [-1, True, 0.0, '0', None, final[key] + 1]:
                for shared in [False, True]:
                    changed = copy.deepcopy(final); changed[key] = value
                    with self.subTest(key=key, value=value, shared=shared), self.assertRaises(AssertionError):
                        VERIFIER.verify_final_materials(changed, shared)
        double_counted = copy.deepcopy(final); double_counted.update(looseCobble=9, looseOak=5)
        with self.assertRaises(AssertionError): VERIFIER.verify_final_materials(double_counted, True)

    def test_mode_specific_native_audit_preserves_baseline_and_requires_death_inheritance(self):
        spec = importlib.util.spec_from_file_location('native_audit', REPO / 'scripts/audit-native-inventory-bytecode.py')
        audit_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(audit_module)
        baseline = dict(status='passed', noVendorJarsUploaded=True,
                        artifacts={mod: dict(classes=[{'class': name} for name in classes])
                                   for mod, (_, classes) in audit_module.TARGETS.items()})
        shared = copy.deepcopy(baseline)
        for mod, classes in audit_module.SHARED_DEATH_TARGETS.items():
            shared['artifacts'][mod]['classes'].extend({'class': name} for name in classes)
        self.assertEqual(22, sum(len(value['classes']) for value in baseline['artifacts'].values()))
        self.assertEqual(25, sum(len(value['classes']) for value in shared['artifacts'].values()))
        VERIFIER.verify_native_bytecode_audit(baseline, False)
        VERIFIER.verify_native_bytecode_audit(shared, True)
        with self.assertRaises(AssertionError): VERIFIER.verify_native_bytecode_audit(baseline, True)
        with self.assertRaises(AssertionError): VERIFIER.verify_native_bytecode_audit(shared, False)
        expected = [('recruits', 'com.talhanation.recruits.entities.AbstractChunkLoaderEntity'),
                    ('recruits', 'com.talhanation.recruits.pathfinding.AsyncPathfinderMob'),
                    ('workers', 'com.talhanation.workers.entities.BuilderEntity')]
        for mod, name in expected:
            data = copy.deepcopy(shared)
            target = next(row for row in data['artifacts'][mod]['classes'] if row['class'] == name)
            target['class'] = 'unrelated.NativeClass'
            with self.subTest(missing=name), self.assertRaises(AssertionError): VERIFIER.verify_native_bytecode_audit(data, True)
        data = copy.deepcopy(shared)
        data['artifacts']['workers']['classes'][0] = copy.deepcopy(data['artifacts']['workers']['classes'][1])
        with self.assertRaises(AssertionError): VERIFIER.verify_native_bytecode_audit(data, True)
        data = copy.deepcopy(shared)
        workers, recruits = data['artifacts']['workers']['classes'], data['artifacts']['recruits']['classes']
        workers[-1], recruits[-1] = recruits[-1], workers[-1]
        with self.assertRaises(AssertionError): VERIFIER.verify_native_bytecode_audit(data, True)

    def test_succession_requires_one_authenticated_original_to_helper_death_handoff(self):
        data = evidence(); data['sharedCrewSuccession'].pop('deathHandoff')
        with self.assertRaises(AssertionError): self.verify(data)
        for value in [None, [], {}, [death_handoff()], [death_handoff(), death_handoff()]]:
            data = evidence(); data['sharedCrewSuccession']['deathHandoff'] = value
            with self.subTest(handoff=value), self.assertRaises(AssertionError): self.verify(data)
        for key in death_handoff():
            data = evidence(); data['sharedCrewSuccession']['deathHandoff'].pop(key)
            with self.subTest(missing=key), self.assertRaises(AssertionError): self.verify(data)
        for key, value in [('from', IDS[1]), ('to', IDS[0]), ('from', DROP_IDS[0]), ('to', DROP_IDS[0]),
                           ('area', 'area-1'), ('cause', 'UNAVAILABLE'), ('cause', 'DISMISSED'),
                           ('receipt', 'a' * 64), ('receipt', death_handoff()['receipt'].upper()),
                           ('receipt', ''), ('receipt', None), ('projectGeneration', 0), ('projectGeneration', -1),
                           ('projectGeneration', True), ('projectGeneration', 1.0), ('projectGeneration', '1'),
                           ('projectGeneration', 2), ('ledgerGeneration', 'invalid'), ('ledgerGeneration', None),
                           ('ledgerGeneration', DROP_IDS[0]), ('extra', 'second-handoff')]:
            data = evidence(); data['sharedCrewSuccession']['deathHandoff'][key] = value
            with self.subTest(key=key, value=value), self.assertRaises(AssertionError): self.verify(data)

    def test_json_loader_rejects_duplicate_death_records_or_nested_keys(self):
        path = self.root / 'result.json'
        serialized = json.dumps(evidence())
        path.write_text(serialized)
        self.verify(VERIFIER.load_evidence_json(path))
        duplicated_record = serialized[:-1] + ',"sharedCrewDeathCargo":' + json.dumps(death_cargo()) + '}'
        duplicated_count = serialized.replace('"cobble": 5', '"cobble": 4, "cobble": 5', 1)
        for contents in [duplicated_record, duplicated_count]:
            path.write_text(contents)
            with self.subTest(contents=contents[:80]), self.assertRaises(AssertionError):
                VERIFIER.load_evidence_json(path)
        for value in ['NaN', 'Infinity', '-Infinity']:
            path.write_text('{"diagnostic":' + value + '}')
            with self.subTest(value=value), self.assertRaises(AssertionError): VERIFIER.load_evidence_json(path)

    def reject_death_mutation(self, mutation):
        data = evidence()
        mutation(data['sharedCrewDeathCargo'])
        with self.assertRaises(AssertionError):
            self.verify(data)

    def test_death_cargo_requires_exactly_one_complete_object(self):
        data = evidence()
        data.pop('sharedCrewDeathCargo')
        with self.assertRaises(AssertionError): self.verify(data)
        for value in [None, False, 0, 'verified', [], [death_cargo()], [death_cargo(), death_cargo()], {}]:
            data = evidence(); data['sharedCrewDeathCargo'] = value
            with self.subTest(value=value), self.assertRaises(AssertionError): self.verify(data)
        for key in death_cargo():
            with self.subTest(missing=key): self.reject_death_mutation(lambda proof: proof.pop(key))
        self.reject_death_mutation(lambda proof: proof.update(compensation={'cobble': 5}))

    def test_death_cargo_requires_native_closed_lifecycle_and_actual_death(self):
        for key in ['closedLifecycles', 'dropsEnabled', 'originalAliveBefore', 'verified']:
            for value in [False, 1, 'true', None]:
                with self.subTest(key=key, value=value):
                    self.reject_death_mutation(lambda proof: proof.update({key: value}))
        for key in ['originalAliveAfter', 'aliasedDropStacks']:
            for value in [True, 0, 'false', None]:
                with self.subTest(key=key, value=value):
                    self.reject_death_mutation(lambda proof: proof.update({key: value}))
        for key, value in [('projectState', 'WAITING_FOR_NEXT_STAGE'), ('stage', 1), ('stage', False),
                           ('stage', 0.0), ('accountingExclusion', 'all dead workers'),
                           ('baselineScope', 'corpse addons verified')]:
            with self.subTest(key=key, value=value):
                self.reject_death_mutation(lambda proof: proof.update({key: value}))

    def test_death_cargo_requires_correct_original_and_helper(self):
        for key in ['worker', 'helper']:
            for value in [IDS[1] if key == 'worker' else IDS[0], DROP_IDS[0], None, []]:
                with self.subTest(key=key, value=value):
                    self.reject_death_mutation(lambda proof: proof.update({key: value}))

    def test_death_cargo_requires_exact_succession_tick_after_both_reload_contributions(self):
        for value in [0, 30, 31, 32, 39, 41, 51, 52, True, 40.0, '40', None]:
            with self.subTest(value=value):
                self.reject_death_mutation(lambda proof: proof.update(gameTime=value))
        for value in [30, 31, 32, 51, 52]:
            data = evidence(); data['sharedCrewDeathCargo']['gameTime'] = value
            data['sharedCrewSuccession']['deathGameTime'] = value
            with self.subTest(aligned_tick=value), self.assertRaises(AssertionError): self.verify(data)
        for index in [4, 5]:
            for value in [29, 30, 40, 41]:
                data = evidence(); data['sharedCrewEvents'][index]['gameTime'] = value
                with self.subTest(worker=IDS[index - 4], tick=value), self.assertRaises(AssertionError): self.verify(data)
        for value in [True, 31.0, '31', None]:
            data = evidence(); data['sharedCrewEvents'][4]['gameTime'] = value
            with self.subTest(event_tick=value), self.assertRaises(AssertionError): self.verify(data)
        for index in [4, 5]:
            for value in [1, True, 0.0, None]:
                data = evidence(); data['sharedCrewEvents'][index]['stage'] = value
                with self.subTest(event=index, stage=value), self.assertRaises(AssertionError): self.verify(data)

    def test_death_cargo_rejects_empty_negative_boolean_or_excess_material_totals(self):
        for value in [None, [], {}, dict(cobble=0, oak=0), dict(cobble=-1, oak=3),
                      dict(cobble=5, oak=-1), dict(cobble=True, oak=3), dict(cobble=5, oak=False),
                      dict(cobble=5.0, oak=3), dict(cobble='5', oak=3), dict(cobble=353, oak=3),
                      dict(cobble=5, oak=221), dict(cobble=5), dict(cobble=5, oak=3, emerald=1)]:
            with self.subTest(before=value):
                self.reject_death_mutation(lambda proof: proof.update(before=value))
        data = evidence(); proof = data['sharedCrewDeathCargo']
        proof['before'] = dict(cobble=0, oak=0); proof['newDrops'] = []
        proof['looseAfter'] = copy.deepcopy(proof['looseBefore'])
        with self.assertRaises(AssertionError): self.verify(data)

    def test_death_cargo_requires_exact_drop_shape_and_material(self):
        for list_key in ['newDrops', 'looseBefore', 'looseAfter']:
            for value in [None, {}, 'drop', True]:
                with self.subTest(list_key=list_key, value=value):
                    self.reject_death_mutation(lambda proof: proof.update({list_key: value}))
            for value in [None, [], 'drop', True, {}]:
                with self.subTest(list_key=list_key, row=value):
                    self.reject_death_mutation(lambda proof: proof[list_key].__setitem__(0, value))
            for key in ['uuid', 'material', 'count', 'stack', 'tag']:
                with self.subTest(list_key=list_key, missing=key):
                    self.reject_death_mutation(lambda proof: proof[list_key][0].pop(key))
            self.reject_death_mutation(lambda proof: proof[list_key][0].update(compensation=True))
        for value in ['', 'minecraft:stone', 'cobblestone', None, [], True]:
            with self.subTest(material=value):
                self.reject_death_mutation(lambda proof: proof['newDrops'][0].update(material=value))
        for value in [0, -1, 65, True, 5.0, '5', None]:
            with self.subTest(count=value):
                self.reject_death_mutation(lambda proof: proof['newDrops'][0].update(count=value))

    def test_death_cargo_rejects_invalid_duplicate_or_actor_drop_uuids(self):
        for value in ['', 'invalid', '55555555555545558555555555555555',
                      '{55555555-5555-4555-8555-555555555555}',
                      'ABCDEFAB-CDEF-4ABC-8DEF-ABCDEFABCDEF', None, 123, IDS[0], IDS[1]]:
            with self.subTest(uuid=value):
                self.reject_death_mutation(lambda proof: proof['newDrops'][0].update(uuid=value))
        for list_key in ['newDrops', 'looseBefore', 'looseAfter']:
            with self.subTest(list_key=list_key, duplicate='same material'):
                self.reject_death_mutation(lambda proof: proof[list_key].append(copy.deepcopy(proof[list_key][0])))
            with self.subTest(list_key=list_key, duplicate='different material'):
                self.reject_death_mutation(lambda proof: proof[list_key][1].update(uuid=proof[list_key][0]['uuid']))

    def test_death_cargo_rejects_missing_extra_reused_or_changed_drop_delta(self):
        mutations = [lambda proof: proof['newDrops'].pop(),
                     lambda proof: proof['looseAfter'].pop(),
                     lambda proof: proof['looseAfter'].pop(0),
                     lambda proof: proof['newDrops'].append(copy.deepcopy(proof['looseBefore'][0])),
                     lambda proof: proof['looseAfter'][0].update(count=1),
                     lambda proof: proof['looseAfter'][0].update(material='minecraft:oak_planks'),
                     lambda proof: proof['looseAfter'][0].update(stack='{}'),
                     lambda proof: proof['newDrops'][0].update(count=4),
                     lambda proof: proof['before'].update(cobble=4),
                     lambda proof: proof['newDrops'].append(drop('88888888-8888-4888-8888-888888888888', 'cobblestone', 1)),
                     lambda proof: proof['looseAfter'].append(drop('88888888-8888-4888-8888-888888888888', 'cobblestone', 1))]
        for index, mutation in enumerate(mutations):
            with self.subTest(mutation=index): self.reject_death_mutation(mutation)
        data = evidence(); proof = data['sharedCrewDeathCargo']
        extra = drop('88888888-8888-4888-8888-888888888888', 'cobblestone', 1)
        proof['newDrops'].append(extra); proof['looseAfter'].append(copy.deepcopy(extra))
        with self.assertRaises(AssertionError): self.verify(data)

    def test_death_cargo_rejects_material_swaps_even_when_total_drop_count_matches(self):
        data = evidence(); proof = data['sharedCrewDeathCargo']
        proof['newDrops'][0]['count'] = 4; proof['newDrops'][1]['count'] = 4
        proof['looseAfter'] = copy.deepcopy(proof['looseBefore'] + proof['newDrops'])
        with self.assertRaises(AssertionError): self.verify(data)

    def test_death_cargo_requires_untagged_stacks_and_bounded_diagnostic_strings(self):
        for value in [None, {}, [], '', 'x' * 16385]:
            with self.subTest(stack=value if not isinstance(value, str) else len(value)):
                self.reject_death_mutation(lambda proof: proof['newDrops'][0].update(stack=value))
        for value in [None, {}, '{}', '{custom:1}', 0]:
            with self.subTest(tag=value):
                self.reject_death_mutation(lambda proof: proof['newDrops'][0].update(tag=value))
        for key in ['originalCargoBefore', 'originalCargoAfter']:
            for value in [None, [], {}, dict(slots=[], main='{}'), dict(slots=[], main='{}', off='{}', extra='{}')]:
                with self.subTest(cargo=key, value=value):
                    self.reject_death_mutation(lambda proof: proof.update({key: value}))
            for value in [None, {}, '{}', ['{}'] * 129, [''], [None], ['x' * 16385]]:
                with self.subTest(cargo=key, slots=repr(value)[:80]):
                    self.reject_death_mutation(lambda proof: proof[key].update(slots=value))
            for hand in ['main', 'off']:
                for value in [None, {}, '', 'x' * 16385]:
                    with self.subTest(cargo=key, hand=hand, value=repr(value)[:80]):
                        self.reject_death_mutation(lambda proof: proof[key].update({hand: value}))

    def test_death_cargo_requires_unchanged_exact_nonnegative_other_stores(self):
        keys = list(death_cargo()['remainingStoresBefore'])
        for record_key in ['remainingStoresBefore', 'remainingStoresAfter']:
            for value in [None, [], {}]:
                with self.subTest(record=record_key, value=value):
                    self.reject_death_mutation(lambda proof: proof.update({record_key: value}))
            for key in keys:
                with self.subTest(record=record_key, missing=key):
                    self.reject_death_mutation(lambda proof: proof[record_key].pop(key))
                for value in [-1, True, 0.0, '0', None]:
                    with self.subTest(record=record_key, store=key, value=value):
                        self.reject_death_mutation(lambda proof: proof[record_key].update({key: value}))
            self.reject_death_mutation(lambda proof: proof[record_key].update(compensationCobble=5))
        for key in keys:
            with self.subTest(changed_store=key):
                self.reject_death_mutation(lambda proof: proof['remainingStoresAfter'].update({key: proof['remainingStoresAfter'][key] + 1}))
        data = evidence(); proof = data['sharedCrewDeathCargo']
        for side in ['remainingStoresBefore', 'remainingStoresAfter']: proof[side]['ownerCobble'] = False
        with self.assertRaises(AssertionError): self.verify(data)

    def test_death_cargo_requires_independent_finite_totals_for_both_materials(self):
        for key in death_cargo()['remainingStoresBefore']:
            data = evidence(); proof = data['sharedCrewDeathCargo']
            proof['remainingStoresBefore'][key] += 1; proof['remainingStoresAfter'][key] += 1
            with self.subTest(store=key), self.assertRaises(AssertionError): self.verify(data)
        for index, material in [(0, 'cobblestone'), (1, 'oak_planks')]:
            data = evidence(); proof = data['sharedCrewDeathCargo']
            proof['looseBefore'][index]['count'] += 1
            proof['looseAfter'] = copy.deepcopy(proof['looseBefore'] + proof['newDrops'])
            with self.subTest(loose_material=material), self.assertRaises(AssertionError): self.verify(data)

    def test_death_cargo_supports_one_material_multi_stack_and_unordered_drop_records(self):
        data = evidence(); proof = data['sharedCrewDeathCargo']
        proof['before'] = dict(cobble=65, oak=0)
        proof['newDrops'] = [drop(DROP_IDS[1], 'cobblestone', 64), drop(DROP_IDS[2], 'cobblestone', 1)]
        proof['looseAfter'] = copy.deepcopy(proof['newDrops'] + list(reversed(proof['looseBefore'])))
        for side in ['remainingStoresBefore', 'remainingStoresAfter']:
            proof[side].update(chestCobble=277, chestOak=213)
        self.verify(data)

    def test_death_cargo_nbt_is_diagnostic_only_and_cannot_replace_entity_proof(self):
        data = evidence(); proof = data['sharedCrewDeathCargo']
        for key in ['originalCargoBefore', 'originalCargoAfter']:
            proof[key] = dict(slots=['bounded unparsed diagnostic'] * 128, main='{}', off='{}')
        self.verify(data)
        proof['newDrops'] = []; proof['looseAfter'] = copy.deepcopy(proof['looseBefore'])
        with self.assertRaises(AssertionError): self.verify(data)

    def test_native_loose_pickup_and_same_tick_placement_are_exactly_accounted(self):
        self.verify(pickup_evidence())
        self.verify(pickup_evidence(placement=True))

    def test_loose_pickup_requires_surviving_helper_after_verified_death(self):
        for placement in [False, True]:
            for key, value in [('builder', IDS[0]), ('builder', DROP_IDS[0]), ('gameTime', 39),
                               ('gameTime', 40), ('ownerPresentBefore', False), ('ownerPresentAfter', False),
                               ('kind', 'native-supply-withdrawal'), ('verifiedDeathDropPickup', False)]:
                data = pickup_evidence(placement)
                event = data['sharedCrewEvents'][-1 if placement else -2]
                event[key] = value
                with self.subTest(placement=placement, key=key, value=value), self.assertRaises(AssertionError): self.verify(data)
            data = pickup_evidence(placement); data.pop('sharedCrewDeathCargo')
            with self.subTest(placement=placement, proof='missing'), self.assertRaises(AssertionError): self.verify(data)
            data = pickup_evidence(placement); data['sharedCrewDeathCargo']['verified'] = False
            with self.subTest(placement=placement, proof='unverified'), self.assertRaises(AssertionError): self.verify(data)

    def test_loose_pickup_requires_exact_nonnegative_delta_with_unchanged_other_stores(self):
        for placement in [False, True]:
            for key in ['chestCobbleBefore', 'chestCobbleAfter', 'chestOakBefore', 'chestOakAfter',
                        'ownerCobbleBefore', 'ownerCobbleAfter', 'ownerOakBefore', 'ownerOakAfter',
                        'cobbleBefore', 'cobbleAfter', 'oakBefore', 'oakAfter',
                        'looseCobbleBefore', 'looseCobbleAfter', 'looseOakBefore', 'looseOakAfter']:
                data = pickup_evidence(placement); event = data['sharedCrewEvents'][-1 if placement else -2]
                event[key] += 1
                with self.subTest(placement=placement, key=key), self.assertRaises(AssertionError): self.verify(data)
            for material in ['Cobble', 'Oak']:
                data = pickup_evidence(placement); event = data['sharedCrewEvents'][-1 if placement else -2]
                event['loose' + material + 'After'] = event['loose' + material + 'Before'] + 1
                # Balance the debit too; even internally balanced negative pickup is forbidden.
                event[material.lower() + 'After'] = event[material.lower() + 'Before'] + 10
                event[material.lower() + 'Before'] += 12 if placement and material == 'Cobble' else 11
                with self.subTest(placement=placement, negative=material), self.assertRaises(AssertionError): self.verify(data)
            data = pickup_evidence(placement); event = data['sharedCrewEvents'][-1 if placement else -2]
            event.update(looseCobbleAfter=event['looseCobbleBefore'], looseOakAfter=event['looseOakBefore'],
                         cobbleBefore=1, cobbleAfter=0 if placement else 1, oakBefore=0, oakAfter=0)
            with self.subTest(placement=placement, delta='zero'), self.assertRaises(AssertionError): self.verify(data)

    def test_loose_pickup_cannot_replace_either_genuine_chest_withdrawal(self):
        for worker in IDS:
            data = pickup_evidence()
            data['sharedCrewEvents'] = [event for event in data['sharedCrewEvents']
                                       if not (event['builder'] == worker and event['kind'] == 'native-supply-withdrawal')]
            with self.subTest(worker=worker), self.assertRaises(AssertionError): self.verify(data)
        data = pickup_evidence(); data['sharedCrewEvents'][1]['chestCobbleAfter'] = 352
        with self.assertRaises(AssertionError): self.verify(data)

    def test_loose_pickup_preserves_actual_placement_evidence(self):
        for key, value in [('nativeStateBefore', 'GET_ITEMS'), ('cells', []), ('kind', 'native-loose-pickup')]:
            data = pickup_evidence(placement=True); data['sharedCrewEvents'][-1][key] = value
            with self.subTest(key=key), self.assertRaises(AssertionError): self.verify(data)
        data = pickup_evidence(); data['sharedCrewEvents'][-2]['cells'] = copy.deepcopy(data['sharedCrewEvents'][-1]['cells'])
        with self.assertRaises(AssertionError): self.verify(data)
        data = pickup_evidence(placement=True); data['sharedCrewEvents'][-1]['cells'][0]['position'] = '0, 65, 0'
        with self.assertRaises(AssertionError): self.verify(data)

    def test_all_events_require_typed_loose_owner_and_pickup_fields(self):
        fields = ['looseCobbleBefore', 'looseCobbleAfter', 'looseOakBefore', 'looseOakAfter',
                  'ownerCobbleBefore', 'ownerCobbleAfter', 'ownerOakBefore', 'ownerOakAfter']
        for index in [0, 3, 6]:
            for key in fields:
                data = evidence(); data['sharedCrewEvents'][index].pop(key)
                with self.subTest(index=index, missing=key), self.assertRaises(AssertionError): self.verify(data)
                for value in [-1, True, 0.0, '0', None]:
                    data = evidence(); data['sharedCrewEvents'][index][key] = value
                    with self.subTest(index=index, key=key, value=value), self.assertRaises(AssertionError): self.verify(data)
            for key in ['ownerPresentBefore', 'ownerPresentAfter', 'verifiedDeathDropPickup']:
                data = evidence(); data['sharedCrewEvents'][index].pop(key)
                with self.subTest(index=index, missing=key), self.assertRaises(AssertionError): self.verify(data)
                for value in [None, 0, 1, 'false', 'true']:
                    data = evidence(); data['sharedCrewEvents'][index][key] = value
                    with self.subTest(index=index, key=key, value=value), self.assertRaises(AssertionError): self.verify(data)

    def test_regular_supply_and_placement_cannot_hide_unverified_loose_changes(self):
        for index in [0, 3, 6]:
            for key in ['looseCobbleBefore', 'looseCobbleAfter', 'looseOakBefore', 'looseOakAfter']:
                data = evidence(); data['sharedCrewEvents'][index][key] = 1
                with self.subTest(index=index, key=key), self.assertRaises(AssertionError): self.verify(data)

    def test_rejects_unrequested_or_mixed_mode(self):
        for key, value in [('sharedCrewRequested', False), ('sharedCrewVerified', False), ('builderReplacementRequested', True)]:
            data = evidence(); data[key] = value
            with self.subTest(key=key), self.assertRaises(AssertionError): self.verify(data)

    def test_rejects_misattribution_or_duplicate_cell(self):
        for mutation in [lambda d: d['sharedCrewEvents'][3].update(builder=IDS[0]),
                         lambda d: d['sharedCrewEvents'][3].update(cells=copy.deepcopy(d['sharedCrewEvents'][2]['cells'])),
                         lambda d: d['sharedCrewEvents'][3].update(cobbleAfter=64),
                         lambda d: d['sharedCrewEvents'][3].update(chestCobbleAfter=351)]:
            data = evidence(); mutation(data)
            with self.subTest(mutation=mutation), self.assertRaises(AssertionError): self.verify(data)

    def test_rejects_replacement_hire_or_another_fee(self):
        for key in ['extraCommission', 'newBuilderSpawnedAfterDeath']:
            data = evidence(); data['sharedCrewSuccession'][key] = True
            with self.subTest(key=key), self.assertRaises(AssertionError): self.verify(data)

    def test_rejects_non_resuming_helper_or_bad_supply(self):
        for mutation in [lambda d: d['sharedCrewEvents'][5].update(gameTime=29),
                         lambda d: d['sharedCrewEvents'][1].update(chestCobbleAfter=352),
                         lambda d: d['sharedCrewEvents'][3]['cells'][0].update(position='0, 65, 0')]:
            data = evidence(); mutation(data)
            with self.subTest(mutation=mutation), self.assertRaises(AssertionError): self.verify(data)

    def test_rejects_missing_or_changed_reload_cargo(self):
        for mutation in [lambda d: d['sharedCrewCargoReload-mid-stage']['loaded'].pop(IDS[1]),
                         lambda d: d['sharedCrewCargoReload-mid-stage']['loaded'][IDS[1]].update(main='{Count:64b}')]:
            data = evidence(); mutation(data)
            with self.subTest(mutation=mutation), self.assertRaises(AssertionError): self.verify(data)

    def test_rejects_runtime_source_mismatch(self):
        data = evidence(); data['sourceCommit'] = 'd' * 40
        with self.assertRaises(AssertionError): self.verify(data)

    def test_opt_in_is_separate_default_false_and_alpha_base_limited(self):
        build = (REPO / 'build.gradle').read_text()
        self.assertIn("gradleProperty('nativeSharedCrewQa').map { it.toBoolean() }.getOrElse(false)", build)
        self.assertIn("sharedCrewQa && nativeQaMode != 'staged-handoff'", build)
        self.assertIn('Shared crew and builder replacement QA are separate scenarios', build)
        workflow = (REPO / '.github/workflows/native-staged-handoff-qa.yml').read_text()
        self.assertIn('branches: [main, release/4.53.0-alpha.3]', workflow)
        self.assertIn("github.event.pull_request.base.ref == 'release/4.53.0-alpha.3' && contains(github.event.pull_request.labels.*.name, 'native-shared-crew-qa')", workflow)
        self.assertIn('default: false', workflow)
        self.assertNotIn('secrets.', workflow)
        self.assertNotIn('contents: write', workflow)
        self.assertNotIn('pull-requests: write', workflow)
        self.assertEqual(2, workflow.count('-PnativeSharedCrewQa=$SHARED_CREW_QA'))

    def test_shared_helper_starts_with_bread_only_without_post_commission_cargo_edits(self):
        fixture = (REPO / 'src/nativeQa/java/com/devfarinsky/siegeoverhaul/nativecompat/NativeStagedHandoffFixture.java').read_text()
        helper = fixture.split('static BuilderEntity spawnSharedHelper', 1)[1].split('record HollowOracle', 1)[0]
        self.assertIn('getContainerSize() <= 128', helper)
        self.assertIn('for (int slot = 6; slot < helper.getInventory().getContainerSize(); slot++)', helper)
        self.assertIn('getItem(slot).isEmpty()', helper)
        self.assertEqual(helper.count('.setItem('), 1)
        self.assertIn('setItem(6, new ItemStack(Items.BREAD, 64))', helper)
        self.assertNotIn('Items.DIAMOND_', helper)
        self.assertNotRegex(helper, r'\.clearContent\(|\.removeItem\(|\.setCount\(|\.shrink\(')
        self.assertLess(helper.index('getItem(slot).isEmpty()'), helper.index('.setItem('))
        self.assertLess(helper.index('.setItem('), helper.index('level.addFreshEntity(helper)'))
        coordinator = fixture.split('static BuilderEntity spawnSharedHelper', 1)[0]
        for item in ['DIAMOND_PICKAXE', 'DIAMOND_AXE', 'DIAMOND_SHOVEL']:
            self.assertIn('new ItemStack(Items.' + item + ')', coordinator)
        java = (REPO / 'src/nativeQa/java/com/devfarinsky/siegeoverhaul/nativecompat/NativeStagedHandoffQa.java').read_text()
        initial = java.split('case 0 -> {', 1)[1].split('case 1 -> {', 1)[0]
        self.assertIn('"sharedCrewInitialHelperKit"', initial)
        self.assertIn('"beforeCommission", projectId == null', initial)
        self.assertIn('"cargo", initialHelperCargo', initial)

    def test_observer_does_not_run_goals_or_write_targets(self):
        java = (REPO / 'src/nativeQa/java/com/devfarinsky/siegeoverhaul/nativecompat/NativeStagedHandoffQa.java').read_text()
        observer = java.split('public static void observeCrewLivingTick', 1)[1].split('private static boolean advanceSharedCrew', 1)[0]
        self.assertNotRegex(observer, r'\.tick\(\)|\.setBlock\(|\.setNoAi\(|\.setState\(')
        self.assertIn('getLongArray("Completed")', observer)
        self.assertIn('Exact per-builder placement material debit', observer)
        self.assertIn('CREW_PLACEMENTS.values().stream().noneMatch', observer)
        self.assertIn('LOADED_CREW.equals(stopped.crewCargo())', java)
        self.assertIn('CREW_SUPPLIED.equals(Set.of(originalCoordinator, sharedHelper))', java)


if __name__ == '__main__':
    unittest.main()
