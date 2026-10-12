"""Synthetic preflight oracle/attestation negatives, never native codec execution.

Tiny test JARs contain labeled synthetic resource bytes. Only the end-to-end
plumbing tests mock the already separately tested API-gate replay; no fixture is
accepted as a real Forge runtime or as a successful native preflight.
"""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import warnings
import zipfile

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('partition_oracle', ROOT / 'scripts/verify-native-helper-partition.py')
VERIFY = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(VERIFY)
SOURCE = '1' * 40
TREE = '2' * 40
IMPLEMENTATION = 'net.minecraft.network.syncher.SyntheticCompoundSerializer'


def serializer_contract(implementation=IMPLEMENTATION, write_owner=None, read_owner=None, read_return='java.lang.Object'):
    # Synthetic reflection report only: these Python tests do not execute Java
    # reflection, COMPOUND_TAG or native assignability/instance-stability checks.
    return {'interface': VERIFY.SERIALIZER_INTERFACE, 'fieldOwner': VERIFY.SERIALIZER_CLASSES[-1], 'field': 'COMPOUND_TAG',
            'implementation': implementation, 'sameDefiningLoader': True, 'implementationConcrete': True,
            'instanceStable': True, 'methods': [
                {'name': 'write', 'owner': write_owner or implementation,
                 'parameters': ['net.minecraft.network.FriendlyByteBuf', 'java.lang.Object'], 'returnType': 'void',
                 'public': True, 'static': False, 'abstract': False, 'returnCompatible': True, 'bridge': False, 'synthetic': False},
                {'name': 'read', 'owner': read_owner or implementation,
                 'parameters': ['net.minecraft.network.FriendlyByteBuf'], 'returnType': read_return,
                 'public': True, 'static': False, 'abstract': False, 'returnCompatible': True, 'bridge': False, 'synthetic': False}]}


def fixture():
    # Expected geometry comes from the independent immutable artifact, not the
    # verifier's reconstruction. Mutations are applied to a fresh deep copy.
    candidate = json.loads(VERIFY.CANDIDATE_PATH.read_text())['candidate']
    stages = candidate['stages']
    plan = dict(targetCount=572, clearanceCount=748, columnCount=220,
                materials={'minecraft:cobblestone': 352, 'minecraft:oak_planks': 220},
                min=[128, 65, 0], max=[143, 70, 15],
                columns=sorted(column for stage in stages for column in stage['columns']),
                targets=sorted((target for stage in stages for target in stage['targets']), key=lambda target: target['cell']),
                clearance=sorted(cell for stage in stages for cell in stage['clearance']))
    calls = [dict(ordinal=ordinal, index=call['index'], layoutStageDigest=call['layoutStageDigest'],
                  blueprintBlockCount=call['targetCount'], targetCount=call['targetCount'], nativeRoundTripPassed=True, problem=None)
             for ordinal, call in enumerate(candidate['validatorCalls'])]
    return dict(formatVersion=1, scenario='native-helper-partition-preflight', result='passed', phase='FINISHED',
                sourceCommit=SOURCE, sourceTree=TREE, candidateSha256=VERIFY.CANDIDATE_SHA256, elapsedSeconds=12.5,
                worldCreated=False, integratedServerStarted=False, workersCreated=0, commissionPerformed=False,
                paymentCharged=0, gameplayExecuted=False, productionFee=64, plan=plan, partition=candidate,
                nativeRoundTrips=calls, loadedApiParity={}, serializerParity=[], compoundTagImplementation=IMPLEMENTATION,
                serializerContract=serializer_contract(), scope=VERIFY.PREFLIGHT_SCOPE)


def encode(data):
    return (json.dumps(data, sort_keys=True, indent=2, allow_nan=False) + '\n').encode()


def update_digests(partition):
    """Mutation helper: recompute submitted hashes, without making them approved."""
    for stage in partition['stages']:
        text = 'perimeter-stage-v1\n'
        text += ''.join(f'c:{cell}\n' for cell in stage['columns'])
        text += ''.join(f"t:{target['cell']}:{target['material']}\n" for target in stage['targets'])
        text += ''.join(f'a:{cell}\n' for cell in stage['clearance'])
        stage['layoutStageDigest'] = hashlib.sha256(text.encode()).hexdigest()
    text = 'perimeter-layout-v1\n' + ''.join(f"{s['index']}:{s['layoutStageDigest']}\n" for s in partition['stages'])
    partition['layoutDigest'] = hashlib.sha256(text.encode()).hexdigest()


class GeometryTests(unittest.TestCase):
    def reject(self, mutate, pattern=None):
        value = fixture()
        mutate(value)
        with self.assertRaisesRegex(ValueError, pattern or '.'):
            VERIFY.verify_report(value)

    def test_immutable_candidate_and_independent_rectangle_match(self):
        self.assertTrue(VERIFY.verify_report(fixture()))
        plan, partition = VERIFY.derive_expected()
        self.assertEqual(partition, VERIFY.load_candidate())
        self.assertEqual(plan['materials'], {'minecraft:cobblestone': 352, 'minecraft:oak_planks': 220})
        self.assertEqual([stage['targetCount'] for stage in partition['stages']],
                         [30, 35, 30, 44, 21, 54, 38, 34, 33, 38, 50, 26, 43, 26, 35, 35])
        self.assertEqual(len(partition['validatorCalls']), 46)
        self.assertEqual(partition['stages'][0]['layoutStageDigest'], '876817a2a3e8b3d3113b84787cb89e687a9ffa6d7254c6fe36a7ea5aab907fb9')
        self.assertEqual(partition['layoutDigest'], '1f25c389bc6c477295a33c74a73648f5aed71f64ab16d3da1dde5425b1837fe8')

    def test_lossless_atomic_reservation_union_is_independent(self):
        plan, partition = VERIFY.derive_expected()
        reserved, columns = set(), set()
        for stage in partition['stages']:
            targets = {target['cell'] for target in stage['targets']}
            clear = set(stage['clearance'])
            self.assertFalse(targets & clear)
            self.assertFalse(reserved & (targets | clear))
            self.assertFalse(columns & set(stage['columns']))
            self.assertEqual({cell & ~0xfff for cell in targets | clear}, set(stage['columns']))
            self.assertEqual(len(targets | clear), len(stage['columns']) * 6)
            reserved |= targets | clear
            columns.update(stage['columns'])
        self.assertEqual(len(reserved), 1320)
        self.assertEqual(reserved, {target['cell'] for target in plan['targets']} | set(plan['clearance']))
        self.assertEqual(columns, set(plan['columns']))

    def test_missing_or_extra_root_evidence_is_rejected(self):
        for key in VERIFY.REPORT_KEYS:
            with self.subTest(key=key):
                self.reject(lambda d: d.pop(key))
        for key in ('failure', 'world', 'nativeSerializerSimulated', 'transformedBodiesVerified', 'paymentRefunded'):
            with self.subTest(key=key):
                self.reject(lambda d: d.update({key: True}))

    def test_scenario_status_phase_and_version_are_fixed(self):
        for key, value in [('formatVersion', 2), ('scenario', 'case-l-gameplay'), ('result', 'failed'), ('phase', 'PARTITIONED'), ('scope', 'Case L gameplay passed')]:
            with self.subTest(key=key):
                self.reject(lambda d: d.update({key: value}))

    def test_no_world_server_worker_commission_payment_or_gameplay(self):
        for key in ('worldCreated', 'integratedServerStarted', 'commissionPerformed', 'gameplayExecuted'):
            with self.subTest(key=key):
                self.reject(lambda d: d.update({key: True}))
        for key in ('workersCreated', 'paymentCharged'):
            for value in (-1, 1, 64, False, 0.0):
                with self.subTest(key=key, value=value):
                    self.reject(lambda d: d.update({key: value}))

    def test_fee_is_observed_exactly_and_never_negated_or_substituted(self):
        for value in (0, 63, 65, 128, 64.0, True, '64'):
            with self.subTest(value=value):
                self.reject(lambda d: d.update(productionFee=value))

    def test_time_is_bounded_finite_and_non_boolean(self):
        for value in (-1, 600.001, 10**500, float('nan'), float('inf'), True, '12'):
            with self.subTest(value=value):
                self.reject(lambda d: d.update(elapsedSeconds=value))
        for value in (0, 0.0, 600, 599.999):
            data = fixture()
            data['elapsedSeconds'] = value
            self.assertTrue(VERIFY.verify_report(data))

    def test_source_identity_is_lowercase_sha1(self):
        for value in ('', 'a' * 39, 'G' * 40, 123, None):
            for key in ('sourceCommit', 'sourceTree'):
                with self.subTest(key=key, value=value):
                    self.reject(lambda d: d.update({key: value}))

    def test_changed_report_or_resource_candidate_hash_is_rejected(self):
        self.reject(lambda d: d.update(candidateSha256='0' * 64))
        with tempfile.TemporaryDirectory() as directory:
            candidate = Path(directory) / 'candidate.json'
            candidate.write_bytes(VERIFY.CANDIDATE_PATH.read_bytes() + b' ')
            with patch.object(VERIFY, 'CANDIDATE_PATH', candidate), self.assertRaisesRegex(ValueError, 'candidate changed'):
                VERIFY.load_candidate()

    def test_changed_cap_counts_material_maps_and_bounds(self):
        for key, value in [('targetCount', 571), ('clearanceCount', 747), ('columnCount', 219),
                           ('min', [128, 66, 0]), ('max', [143, 69, 15])]:
            with self.subTest(key=key):
                self.reject(lambda d: d['plan'].update({key: value}))
        self.reject(lambda d: d['partition'].update(cap=65))
        self.reject(lambda d: d['plan']['materials'].update({'minecraft:cobblestone': 351, 'minecraft:oak_planks': 221}))
        self.reject(lambda d: d['plan']['targets'][0].update(material='minecraft:stone_bricks'))
        self.reject(lambda d: d['partition']['stages'][0]['materials'].update({'minecraft:cobblestone': 23}))

    def test_packed_column_target_and_clearance_order_are_exact(self):
        for key in ('columns', 'targets', 'clearance'):
            with self.subTest(key=key):
                self.reject(lambda d: d['plan'][key].reverse())
                self.reject(lambda d: d['partition']['stages'][0][key].reverse())
        self.reject(lambda d: d['plan']['columns'].__setitem__(0, d['plan']['columns'][0] + 65))

    def test_missing_duplicate_or_foreign_targets_and_clearance(self):
        for key in ('columns', 'targets', 'clearance'):
            with self.subTest(key=key):
                self.reject(lambda d: d['plan'][key].pop())
                self.reject(lambda d: d['plan'][key].append(copy.deepcopy(d['plan'][key][0])))
                self.reject(lambda d: d['partition']['stages'][0][key].append(copy.deepcopy(d['partition']['stages'][1][key][0])))
        self.reject(lambda d: d['partition']['stages'][0]['clearance'].__setitem__(0, d['partition']['stages'][0]['targets'][0]['cell']))

    def test_stage_bounds_counts_and_reservation_count_are_pinned(self):
        for key, value in [('index', 1), ('targetCount', 31), ('clearanceCount', 5), ('reservationCount', 35),
                           ('min', [128, 64, 0]), ('max', [128, 70, 6])]:
            with self.subTest(key=key):
                self.reject(lambda d: d['partition']['stages'][0].update({key: value}))

    def test_missing_duplicate_reordered_stages_and_changed_digests(self):
        self.reject(lambda d: d['partition']['stages'].pop())
        self.reject(lambda d: d['partition']['stages'].append(copy.deepcopy(d['partition']['stages'][0])))
        self.reject(lambda d: d['partition']['stages'].reverse())
        self.reject(lambda d: d['partition'].update(layoutDigest='0' * 64))
        self.reject(lambda d: d['partition']['stages'][0].update(layoutStageDigest='0' * 64))

    def test_self_consistent_rehash_cannot_adopt_a_changed_atom(self):
        def mutate(data):
            stage = data['partition']['stages'][0]
            stage['targets'][0]['material'] = 'minecraft:oak_planks'
            stage['materials']['minecraft:cobblestone'] -= 1
            stage['materials']['minecraft:oak_planks'] += 1
            update_digests(data['partition'])
        self.reject(mutate)

    def test_column_split_with_lossless_global_union_still_fails(self):
        def mutate(data):
            first, second = data['partition']['stages'][:2]
            target = first['targets'].pop()
            second['targets'].insert(0, target)
            first['targetCount'] -= 1
            second['targetCount'] += 1
            first['reservationCount'] -= 1
            second['reservationCount'] += 1
            material = target['material']
            first['materials'][material] -= 1
            second['materials'][material] += 1
            second['columns'].insert(0, target['cell'] & ~0xfff)
            update_digests(data['partition'])
        self.reject(mutate)

    def test_clearance_reservation_split_or_duplicate_fails_even_if_rehashed(self):
        def mutate(data):
            first, second = data['partition']['stages'][:2]
            second['clearance'].insert(0, first['clearance'].pop())
            first['clearanceCount'] -= 1
            second['clearanceCount'] += 1
            first['reservationCount'] -= 1
            second['reservationCount'] += 1
            update_digests(data['partition'])
        self.reject(mutate)
        self.reject(lambda d: d['partition']['stages'][1]['columns'].insert(0, d['partition']['stages'][0]['columns'][0]))

    def test_missing_reordered_or_extra_native_calls(self):
        self.reject(lambda d: d['nativeRoundTrips'].pop(0))
        self.reject(lambda d: d['nativeRoundTrips'].reverse())
        self.reject(lambda d: d['nativeRoundTrips'].append(copy.deepcopy(d['nativeRoundTrips'][0])))
        self.reject(lambda d: d['nativeRoundTrips'].clear())

    def test_native_error_cannot_be_hidden_by_cap_repartition_or_pass_flag(self):
        for index in (0, 1, 5, 45):
            with self.subTest(index=index):
                self.reject(lambda d: d['nativeRoundTrips'][index].update(nativeRoundTripPassed=False, problem='write/read mismatch'))
                self.reject(lambda d: d['nativeRoundTrips'][index].update(problem='unread bytes'))
        self.reject(lambda d: d['nativeRoundTrips'][0].update(problem=''))

    def test_native_proposal_block_count_index_and_digest_are_exact(self):
        for key, value in [('ordinal', 1), ('index', 1), ('blueprintBlockCount', 571), ('targetCount', 571),
                           ('layoutStageDigest', '0' * 64)]:
            with self.subTest(key=key):
                self.reject(lambda d: d['nativeRoundTrips'][0].update({key: value}))
        self.reject(lambda d: d['nativeRoundTrips'][0].pop('problem'))
        self.reject(lambda d: d['nativeRoundTrips'][0].update(simulated=True))

    def test_validator_trace_is_pinned_independently_of_native_trace(self):
        self.reject(lambda d: d['partition']['validatorCalls'].pop())
        self.reject(lambda d: d['partition']['validatorCalls'].reverse())
        for key, value in [('columnCount', 219), ('targetCount', 571), ('index', 1), ('capAccepts', True)]:
            with self.subTest(key=key):
                self.reject(lambda d: d['partition']['validatorCalls'][0].update({key: value}))

    def test_integer_and_boolean_aliases_do_not_pass_typed_json_comparison(self):
        self.reject(lambda d: d.update(formatVersion=True))
        self.reject(lambda d: d.update(worldCreated=0))
        self.reject(lambda d: d['plan'].update(targetCount=572.0))
        self.reject(lambda d: d['partition'].update(cap=64.0))
        self.reject(lambda d: d['partition']['stages'][0].update(index=False))
        self.reject(lambda d: d['nativeRoundTrips'][0].update(ordinal=False))
        self.reject(lambda d: d['nativeRoundTrips'][0].update(nativeRoundTripPassed=1))


class AttestationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.addCleanup(self.temp.cleanup)
        self.jar = self.root / 'synthetic-forge.jar'
        self.names = [VERIFY.FORGE_TAIL] + list(VERIFY.SERIALIZER_CLASSES) + [VERIFY.SERIALIZER_INTERFACE, IMPLEMENTATION]
        self.entries = [(name.replace('.', '/') + '.class', ('SYNTHETIC RESOURCE ' + name).encode()) for name in self.names]
        self.write_jar()
        self.data = fixture()
        raw_hash = hashlib.sha256(self.jar.read_bytes()).hexdigest()
        # Actual gate replay is tested separately. This fixture exercises only
        # post-replay binding against synthetic bytes, never real runtime pins.
        contract = VERIFY.GATE.load_reviewed_contract()
        self.gate = dict(sourceCommit=SOURCE, sourceTree=TREE,
                         reviewedGateContractSha256=contract['reviewedGateContractSha256'],
                         classes=[dict(name=record['name'], supplierCanonicalPath=str(self.jar),
                                       supplierSha256=raw_hash, classSha256='a' * 64)
                                  for record in contract['pins']['classes']])
        self.data['serializerParity'] = [self.proof(name) for name in self.names[1:]]
        self.data['loadedApiParity'] = dict(passed=True, gateSha256=hashlib.sha256(encode(self.gate)).hexdigest(),
                                            contractSha256=self.gate['reviewedGateContractSha256'],
                                            classes={entry['name']: dict(provider=str(self.jar), supplierSha256=raw_hash, rawResourceSha256='a' * 64)
                                                     for entry in self.gate['classes']},
                                            superclassChain=list(VERIFY.CHAIN),
                                            codeSourceUrls={entry['name']: self.jar.as_uri() for entry in self.gate['classes']},
                                            forgeSuperclassTail=self.proof(VERIFY.FORGE_TAIL), scope=VERIFY.PARITY_SCOPE)

    def write_jar(self, entries=None):
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            with zipfile.ZipFile(self.jar, 'w') as archive:
                for name, content in self.entries if entries is None else entries:
                    archive.writestr(name, content)

    def proof(self, name):
        raw = dict(self.entries)[name.replace('.', '/') + '.class']
        return dict(name=name, provider=str(self.jar), supplierSha256=hashlib.sha256(self.jar.read_bytes()).hexdigest(),
                    rawResourceSha256=hashlib.sha256(raw).hexdigest(), codeSourceUrl=self.jar.as_uri(), scope=VERIFY.RESOURCE_SCOPE)

    def verify(self):
        return VERIFY.verify_attestations(self.data, self.gate, hashlib.sha256(encode(self.gate)).hexdigest())

    def reject(self, mutate):
        data, gate = copy.deepcopy(self.data), copy.deepcopy(self.gate)
        mutate()
        try:
            with self.assertRaises((ValueError, OSError)):
                self.verify()
        finally:
            self.data, self.gate = data, gate

    def repin_synthetic_jar(self):
        # Only to reach unique-entry negatives after changing the synthetic ZIP.
        # Production gate replay forbids accepting a newly computed supplier pin.
        digest = hashlib.sha256(self.jar.read_bytes()).hexdigest()
        for record in self.gate['classes']:
            record['supplierSha256'] = digest
        for proof in self.data['loadedApiParity']['classes'].values():
            proof['supplierSha256'] = digest
        for proof in [self.data['loadedApiParity']['forgeSuperclassTail']] + self.data['serializerParity']:
            proof['supplierSha256'] = digest
        self.data['loadedApiParity']['gateSha256'] = hashlib.sha256(encode(self.gate)).hexdigest()

    def test_exact_synthetic_unique_entry_attestations_pass(self):
        self.assertTrue(self.verify())

    def test_unchanged_review_contract_retains_fixed_pin(self):
        contract = VERIFY.GATE.load_reviewed_contract()
        self.assertEqual(contract['reviewedGateContractSha256'], '214c5fa633fdfae31f510016ade492ffab239ad8a1752730089979edb1b29aa5')
        self.assertEqual(len(contract['pins']['classes']), 60)
        self.assertEqual(len(contract['pins']['reviewedMethods']), 74)
        self.assertEqual([artifact['sha256'] for artifact in contract['pins']['artifacts'] if artifact['fileName'].startswith('forge-')],
                         ['d50f909e092de1f72cfb86d854ba9fd845f68d0853990f0493c885b8ebc3bf8a'])

    def test_missing_duplicate_reordered_serializer_proofs_fail(self):
        self.reject(lambda: self.data['serializerParity'].pop())
        self.reject(lambda: self.data['serializerParity'].reverse())
        self.reject(lambda: self.data['serializerParity'].__setitem__(1, copy.deepcopy(self.data['serializerParity'][0])))

    def test_missing_raw_proof_fields_and_extra_transformed_claim_fail(self):
        for key in VERIFY.PROOF_KEYS:
            with self.subTest(key=key):
                self.reject(lambda: self.data['serializerParity'][0].pop(key))
        self.reject(lambda: self.data['serializerParity'][0].update(transformedMethodsVerified=True))
        self.reject(lambda: self.data['serializerParity'][0].update(scope='Verified transformed serializer bodies'))

    def test_compound_serializer_identity_must_match_the_actual_contract(self):
        for value in ('net.minecraft.nbt.CompoundTag', VERIFY.SERIALIZER_CLASSES[-1], VERIFY.SERIALIZER_INTERFACE,
                      IMPLEMENTATION + '.Other', 'foreign.Serializer$16', None):
            with self.subTest(value=value):
                self.reject(lambda: self.data.update(compoundTagImplementation=value))
        self.reject(lambda: self.data.update(compoundTagImplementation=IMPLEMENTATION + '7'))

    def set_synthetic_contract(self, implementation=IMPLEMENTATION, write_owner=None, read_owner=None, read_return='java.lang.Object'):
        self.data['compoundTagImplementation'] = implementation
        self.data['serializerContract'] = serializer_contract(implementation, write_owner, read_owner, read_return)
        names = list(VERIFY.SERIALIZER_CLASSES) + [VERIFY.SERIALIZER_INTERFACE, implementation,
                                                write_owner or implementation, read_owner or implementation]
        if read_return != 'java.lang.Object':
            names.append(read_return)
        self.names = [VERIFY.FORGE_TAIL] + list(dict.fromkeys(names))
        self.entries = [(name.replace('.', '/') + '.class', ('SYNTHETIC RESOURCE ' + name).encode()) for name in self.names]
        self.write_jar()
        self.data['serializerParity'] = [self.proof(name) for name in self.names[1:]]
        self.repin_synthetic_jar()

    def test_shape_free_actual_implementation_and_inherited_owner_are_accepted(self):
        self.set_synthetic_contract('net.minecraft.network.syncher.SyntheticConcreteCodec',
                                   write_owner='net.minecraft.network.syncher.SyntheticBaseCodec',
                                   read_owner='net.minecraft.network.syncher.SyntheticBaseCodec')
        self.assertTrue(self.verify())
        self.assertEqual(len(self.data['serializerParity']), 8)
        self.assertEqual([proof['name'] for proof in self.data['serializerParity']][-1],
                         'net.minecraft.network.syncher.SyntheticBaseCodec')

    def test_covariant_compound_return_adds_its_exact_resource_once(self):
        self.set_synthetic_contract(read_return='net.minecraft.nbt.SyntheticCompoundSubclass')
        self.assertTrue(self.verify())
        self.assertEqual(len(self.data['serializerParity']), 8)
        self.assertEqual(self.data['serializerParity'][-1]['name'], 'net.minecraft.nbt.SyntheticCompoundSubclass')
        self.set_synthetic_contract(read_return='net.minecraft.nbt.CompoundTag')
        self.assertTrue(self.verify())
        self.assertEqual(len(self.data['serializerParity']), 7)

    def test_object_return_bridge_and_distinct_reflected_owners_are_accepted(self):
        self.set_synthetic_contract('net.minecraft.network.syncher.SyntheticBridgeCodec',
                                   write_owner='net.minecraft.network.syncher.SyntheticWriteBase',
                                   read_owner='net.minecraft.network.syncher.SyntheticReadBase')
        self.data['serializerContract']['methods'][1].update(bridge=True, synthetic=True)
        self.assertTrue(self.verify())
        self.assertEqual(len(self.data['serializerParity']), 9)
        self.assertNotIn('java.lang.Object', [proof['name'] for proof in self.data['serializerParity']])
        self.set_synthetic_contract(write_owner='net.minecraft.network.syncher.SyntheticWriteBase',
                                   read_owner='net.minecraft.network.syncher.SyntheticReadBase',
                                   read_return='net.minecraft.nbt.SyntheticCompoundSubclass')
        self.assertTrue(self.verify())
        self.assertEqual(len(self.data['serializerParity']), 10)

    def test_wrong_interface_field_type_loader_or_unstable_instance_fails(self):
        for key, value in [('interface', 'foreign.Serializer'), ('fieldOwner', 'foreign.Serializers'), ('field', 'OTHER'),
                           ('implementation', 'foreign.Implementation'), ('sameDefiningLoader', False),
                           ('implementationConcrete', False), ('instanceStable', False), ('instanceStable', 1)]:
            with self.subTest(key=key, value=value):
                self.reject(lambda: self.data['serializerContract'].update({key: value}))
        for key in serializer_contract():
            with self.subTest(missing=key):
                self.reject(lambda: self.data['serializerContract'].pop(key))
        self.reject(lambda: self.data['serializerContract'].update(transformedMethodsVerified=True))

    def test_missing_reordered_or_malformed_method_contracts_fail(self):
        self.reject(lambda: self.data['serializerContract']['methods'].pop())
        self.reject(lambda: self.data['serializerContract']['methods'].reverse())
        self.reject(lambda: self.data['serializerContract']['methods'].append(copy.deepcopy(self.data['serializerContract']['methods'][0])))
        for ordinal in range(2):
            for key in ('name', 'owner', 'parameters', 'returnType', 'public', 'static', 'abstract', 'returnCompatible', 'bridge', 'synthetic'):
                with self.subTest(ordinal=ordinal, key=key):
                    self.reject(lambda: self.data['serializerContract']['methods'][ordinal].pop(key))
            for key, value in [('public', False), ('static', True), ('abstract', True), ('returnCompatible', False), ('returnCompatible', 1)]:
                with self.subTest(ordinal=ordinal, key=key):
                    self.reject(lambda: self.data['serializerContract']['methods'][ordinal].update({key: value}))

    def test_bridge_and_synthetic_flags_are_typed_observations_not_guessed_shapes(self):
        for bridge, synthetic in ((False, False), (False, True), (True, False), (True, True)):
            for method in self.data['serializerContract']['methods']:
                method.update(bridge=bridge, synthetic=synthetic)
            with self.subTest(bridge=bridge, synthetic=synthetic):
                self.assertTrue(self.verify())
        for ordinal in range(2):
            for key in ('bridge', 'synthetic'):
                for value in (0, 1, 'true', None, [], {}):
                    with self.subTest(ordinal=ordinal, key=key, value=value):
                        self.reject(lambda: self.data['serializerContract']['methods'][ordinal].update({key: value}))

    def test_wrong_write_read_parameters_and_return_types_fail(self):
        self.reject(lambda: self.data['serializerContract']['methods'][0].update(parameters=['net.minecraft.network.FriendlyByteBuf', 'net.minecraft.nbt.CompoundTag']))
        self.reject(lambda: self.data['serializerContract']['methods'][0].update(returnType='java.lang.Object'))
        self.reject(lambda: self.data['serializerContract']['methods'][1].update(parameters=['java.lang.Object']))
        for value in ('void', 'int', '[Lnet.minecraft.nbt.CompoundTag;', None):
            with self.subTest(value=value):
                self.reject(lambda: self.data['serializerContract']['methods'][1].update(returnType=value))

    def test_binary_names_are_bounded_and_never_paths_or_descriptors(self):
        for value in ('', '../foreign', 'net/minecraft/Codec', 'Lnet.minecraft.Codec;', '.Codec', 'net..Codec',
                      'net.Codec[', 'net.Codec\\Nested', 'net.Codec with spaces', 'x' * 513, None):
            with self.subTest(value=value):
                self.reject(lambda: self.data['serializerContract']['methods'][0].update(owner=value))

    def test_inherited_covariant_resource_closure_is_exact_not_a_name_allowlist(self):
        self.set_synthetic_contract(write_owner='net.minecraft.network.syncher.SyntheticWriteBase',
                                   read_owner='net.minecraft.network.syncher.SyntheticReadBase',
                                   read_return='net.minecraft.nbt.SyntheticCompoundSubclass')
        for ordinal in (5, 6, 7, 8, 9):
            with self.subTest(ordinal=ordinal):
                self.reject(lambda: self.data['serializerParity'].pop(ordinal))
                self.reject(lambda: self.data['serializerParity'][ordinal].update(rawResourceSha256='0' * 64))
                self.reject(lambda: self.data['serializerParity'][ordinal].update(supplierSha256='0' * 64))
        self.reject(lambda: self.data['serializerParity'].append(copy.deepcopy(self.data['serializerParity'][0])))
        self.reject(lambda: self.data['serializerParity'].reverse())
        self.reject(lambda: self.data['serializerContract']['methods'][0].update(owner='net.minecraft.network.syncher.DifferentOwner'))

    def test_supplier_path_hash_and_raw_entry_cannot_drift(self):
        other = self.root / 'same-bytes-wrong-provider.jar'
        other.write_bytes(self.jar.read_bytes())
        for key, value in [('provider', str(other)), ('supplierSha256', '0' * 64), ('rawResourceSha256', '0' * 64)]:
            with self.subTest(key=key):
                self.reject(lambda: self.data['serializerParity'][0].update({key: value}))
        self.jar.write_bytes(self.jar.read_bytes() + b'CHANGED')
        with self.assertRaisesRegex(ValueError, 'whole-JAR'):
            self.verify()

    def test_tail_must_bind_entity_exact_forge_supplier_and_entry(self):
        for key, value in [('name', 'foreign.CapabilityProvider'), ('provider', '/other.jar'), ('supplierSha256', '0' * 64),
                           ('rawResourceSha256', '0' * 64), ('scope', 'transformed bytecode verified')]:
            with self.subTest(key=key):
                self.reject(lambda: self.data['loadedApiParity']['forgeSuperclassTail'].update({key: value}))

    def test_loaded_parity_gate_contract_classes_chain_and_scope_are_exact(self):
        for key, value in [('passed', False), ('passed', 1), ('gateSha256', '0' * 64), ('contractSha256', '0' * 64),
                           ('scope', 'transformed methods observed'), ('superclassChain', VERIFY.CHAIN[:-1])]:
            with self.subTest(key=key):
                self.reject(lambda: self.data['loadedApiParity'].update({key: value}))
        self.reject(lambda: self.data['loadedApiParity']['classes'].pop('net.minecraft.world.entity.Entity'))
        self.reject(lambda: self.data['loadedApiParity']['classes']['net.minecraft.world.entity.Entity'].update(rawResourceSha256='0' * 64))
        self.reject(lambda: self.data['loadedApiParity']['codeSourceUrls'].pop('net.minecraft.world.entity.Entity'))

    def test_code_source_url_cannot_name_foreign_or_ambiguous_provider(self):
        for value in ('https://example.invalid/forge.jar', 'file://host' + str(self.jar), self.jar.as_uri() + '#fragment',
                      self.jar.as_uri() + '?query', 'jar:' + self.jar.as_uri() + '!/nested.class',
                      'union:' + str(self.jar) + '%231!/', 'union:' + str(self.jar) + '%232147483648!/',
                      self.jar.as_uri().replace('/synthetic', '/%2esynthetic'),
                      self.jar.as_uri().replace('file:', 'file:\n'), self.jar.as_uri() + '\t'):
            # %231 is valid SecureJarHandler encoding; test another exact filename instead.
            if value == 'union:' + str(self.jar) + '%231!/':
                value = 'union:' + str(self.jar) + '-foreign%231!/'
            with self.subTest(value=value):
                self.reject(lambda: self.data['serializerParity'][0].update(codeSourceUrl=value))

    def test_reviewed_file_jar_root_and_union_provider_forms_remain_supported(self):
        for value in (self.jar.as_uri(), 'jar:' + self.jar.as_uri() + '!/',
                      'union:' + str(self.jar) + '%231!/', 'union:' + str(self.jar) + '%230%232147483647!'):
            with self.subTest(value=value):
                VERIFY.verify_provider_url(value, str(self.jar))
        (self.root / (self.jar.name + '#1')).write_text('ambiguous real filename')
        with self.assertRaisesRegex(ValueError, 'Ambiguous'):
            VERIFY.verify_provider_url('union:' + str(self.jar) + '%231!/', str(self.jar))

    def test_missing_duplicate_versioned_empty_and_oversized_entries_fail(self):
        resource, raw = self.entries[1]
        variants = [self.entries[:1] + self.entries[2:], self.entries + [(resource, raw)],
                    self.entries + [('META-INF/versions/17/' + resource, raw)],
                    self.entries + [('META-INF/versions/invalid/' + resource, raw)],
                    self.entries[:1] + [(resource, b'')] + self.entries[2:],
                    self.entries[:1] + [(resource, b'x' * (VERIFY.GATE.CAPTURE.MAX_CLASS_BYTES + 1))] + self.entries[2:]]
        for entries in variants:
            with self.subTest(entries=len(entries), length=len(entries[1][1]) if len(entries) > 1 else 0):
                self.write_jar(entries)
                self.repin_synthetic_jar()
                with self.assertRaises(ValueError):
                    self.verify()

    def test_changed_unique_entry_is_not_proved_by_whole_jar_flag(self):
        changed = list(self.entries)
        changed[1] = (changed[1][0], b'different synthetic raw resource')
        self.write_jar(changed)
        self.repin_synthetic_jar()
        with self.assertRaisesRegex(ValueError, 'raw resource differs'):
            self.verify()

    def prepare_bundle(self):
        evidence = self.root / 'evidence'
        (evidence / 'api-gate').mkdir(parents=True)
        (self.root / 'client').mkdir()
        manifest = self.root / 'runtime.json'
        manifest.write_text('{"synthetic": true}\n')
        for directory in (self.root, evidence):
            (directory / 'source-commit.txt').write_text(SOURCE + '\n')
            (directory / 'source-tree.txt').write_text(TREE + '\n')
        (evidence / 'result.json').write_bytes(encode(self.data))
        (evidence / 'api-gate/gate.json').write_bytes(encode(self.gate))
        return evidence, manifest

    def test_outer_oracle_replays_gate_then_binds_source_and_all_attestations(self):
        evidence, manifest = self.prepare_bundle()
        with patch.object(VERIFY.GATE, 'verify_receipt', return_value=self.gate) as replay:
            self.assertEqual(VERIFY.verify(evidence, manifest), self.data)
            replay.assert_called_once_with(manifest, evidence / 'api-gate/gate.json')

    def test_absent_or_canonical_empty_saves_container_is_world_free(self):
        evidence, manifest = self.prepare_bundle()
        saves = self.root / 'client/saves'
        for empty_container in (False, True):
            if empty_container:
                saves.mkdir()
            with self.subTest(empty_container=empty_container), patch.object(VERIFY.GATE, 'verify_receipt', return_value=self.gate):
                self.assertEqual(VERIFY.verify(evidence, manifest), self.data)
        self.assertEqual(list(saves.iterdir()), [])
        self.data['worldCreated'] = True
        (evidence / 'result.json').write_bytes(encode(self.data))
        with self.assertRaisesRegex(ValueError, 'no-gameplay claim'):
            VERIFY.verify(evidence, manifest)

    def test_nonempty_saves_container_rejects_files_directories_and_hidden_entries(self):
        evidence, manifest = self.prepare_bundle()
        saves = self.root / 'client/saves'
        saves.mkdir()
        for name, directory in [('synthetic-entry.txt', False), ('synthetic-directory', True), ('.hidden', False)]:
            entry = saves / name
            if directory:
                entry.mkdir()
            else:
                entry.write_text('Synthetic boundary test; not a Minecraft world')
            with self.subTest(name=name), patch.object(VERIFY.GATE, 'verify_receipt') as replay:
                with self.assertRaisesRegex(ValueError, 'contains an entry'):
                    VERIFY.verify(evidence, manifest)
                replay.assert_not_called()
            entry.rmdir() if directory else entry.unlink()

    def test_saves_file_and_live_or_dangling_symlinks_are_rejected(self):
        evidence, manifest = self.prepare_bundle()
        saves = self.root / 'client/saves'
        saves.write_text('Synthetic non-directory')
        with self.assertRaisesRegex(ValueError, 'canonical real directory'):
            VERIFY.verify(evidence, manifest)
        saves.unlink()
        empty = self.root / 'empty-external-directory'
        empty.mkdir()
        for target in (empty, self.root / 'missing-target'):
            saves.symlink_to(target, target_is_directory=True)
            with self.subTest(target=target), self.assertRaisesRegex(ValueError, 'symlink'):
                VERIFY.verify(evidence, manifest)
            saves.unlink()
        self.assertTrue(empty.is_dir())
        self.assertEqual(list(empty.iterdir()), [])

    def test_client_directory_cannot_hide_saves_through_an_ancestor_symlink(self):
        evidence, manifest = self.prepare_bundle()
        client = self.root / 'client'
        client.rmdir()
        external = self.root / 'external-client'
        external.mkdir()
        client.symlink_to(external, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, 'noncanonical'):
            VERIFY.verify(evidence, manifest)

    def test_saves_boundary_is_rechecked_after_runtime_verification(self):
        evidence, manifest = self.prepare_bundle()
        saves = self.root / 'client/saves'
        def changed_after_replay(*args):
            saves.mkdir()
            (saves / 'unexpected-entry.txt').write_text('Synthetic late mutation')
            return self.gate
        with patch.object(VERIFY.GATE, 'verify_receipt', side_effect=changed_after_replay):
            with self.assertRaisesRegex(ValueError, 'contains an entry'):
                VERIFY.verify(evidence, manifest)

    def test_gate_replay_failure_is_not_adapted_recaptured_or_ignored(self):
        evidence, manifest = self.prepare_bundle()
        with patch.object(VERIFY.GATE, 'verify_receipt', side_effect=ValueError('runtime/pins changed')) as replay:
            with self.assertRaisesRegex(ValueError, 'runtime/pins changed'):
                VERIFY.verify(evidence, manifest)
            replay.assert_called_once()

    def test_gate_receipt_cannot_change_after_replay(self):
        evidence, manifest = self.prepare_bundle()
        different = copy.deepcopy(self.gate)
        different['sourceTree'] = '3' * 40
        (evidence / 'api-gate/gate.json').write_bytes(encode(different))
        with patch.object(VERIFY.GATE, 'verify_receipt', return_value=self.gate), self.assertRaisesRegex(ValueError, 'receipt changed'):
            VERIFY.verify(evidence, manifest)

    def test_source_result_gate_and_prepared_identity_must_all_match(self):
        evidence, manifest = self.prepare_bundle()
        for directory in (self.root, evidence):
            for name in ('source-commit.txt', 'source-tree.txt'):
                path = directory / name
                original = path.read_bytes()
                path.write_text('3' * 40 + '\n')
                with self.subTest(path=path), patch.object(VERIFY.GATE, 'verify_receipt', return_value=self.gate), self.assertRaisesRegex(ValueError, 'identity differ'):
                    VERIFY.verify(evidence, manifest)
                path.write_bytes(original)
        self.data['sourceCommit'] = '3' * 40
        (evidence / 'result.json').write_bytes(encode(self.data))
        with patch.object(VERIFY.GATE, 'verify_receipt', return_value=self.gate), self.assertRaisesRegex(ValueError, 'identity differ'):
            VERIFY.verify(evidence, manifest)

    def test_duplicate_keys_and_nonfinite_json_are_rejected_before_gate(self):
        evidence, manifest = self.prepare_bundle()
        for raw in (b'{"formatVersion":1,"formatVersion":1}', b'{"elapsedSeconds":NaN}'):
            (evidence / 'result.json').write_bytes(raw)
            with self.subTest(raw=raw), patch.object(VERIFY.GATE, 'verify_receipt') as replay, self.assertRaises(ValueError):
                VERIFY.verify(evidence, manifest)
            replay.assert_not_called()

    def test_oversized_or_symlinked_result_is_rejected(self):
        evidence, manifest = self.prepare_bundle()
        result = evidence / 'result.json'
        result.write_bytes(b' ' * (VERIFY.MAX_RESULT_BYTES + 1))
        with self.assertRaisesRegex(ValueError, 'bounded'):
            VERIFY.verify(evidence, manifest)
        result.unlink()
        target = self.root / 'external.json'
        target.write_bytes(encode(self.data))
        result.symlink_to(target)
        with self.assertRaisesRegex(ValueError, 'canonical'):
            VERIFY.verify(evidence, manifest)

    def test_cli_has_no_candidate_or_runtime_pin_override(self):
        result = subprocess.run([sys.executable, str(ROOT / 'scripts/verify-native-helper-partition.py'), '--help'],
                                capture_output=True, text=True, timeout=10)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('runtime_manifest', result.stdout)
        self.assertNotIn('--candidate', result.stdout)
        self.assertNotIn('--skip', result.stdout)
        self.assertNotIn('--pin', result.stdout)
        result = subprocess.run([sys.executable, str(ROOT / 'scripts/verify-native-helper-partition.py'),
                                 str(self.root / 'missing'), str(self.root / 'runtime.json')],
                                capture_output=True, text=True, timeout=10)
        self.assertEqual(result.returncode, 1)
        self.assertIn('preflight rejected', result.stderr)


if __name__ == '__main__':
    unittest.main()
