#!/usr/bin/env python3
"""Read-only oracle for native partition preflight, never gameplay acceptance.

The rectangle, atomic split traversal, and all raw digests are independently
reconstructed. The regenerated candidate JSON must retain its immutable pinned hash.
The unchanged API gate is replayed against the exact prepared runtime; extra
serializer attestations bind raw resources to unique entries of that same Forge
JAR. Neither this oracle nor the attestations inspect transformed method bodies.
"""
import argparse
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import re
from urllib.parse import unquote, urlsplit
import zipfile

ROOT = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location('partition_helper_api_gate', ROOT / 'verify-native-helper-api.py')
GATE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GATE)
CANDIDATE_PATH = ROOT.parent / 'src/nativeQa/resources/native-helper-partition/cap64-candidate-partition.json'
CANDIDATE_SHA256 = '64d4deeadebea0808cf38cbca69ebf72b68b3e2ed089958e3a41d1a37a15a115'
LAYOUT_SHA256 = '1f25c389bc6c477295a33c74a73648f5aed71f64ab16d3da1dde5425b1837fe8'
MAX_RESULT_BYTES = 2 * 1024 * 1024
MAX_CANDIDATE_BYTES = 256 * 1024
MATERIALS = ('minecraft:cobblestone', 'minecraft:oak_planks')
SERIALIZER_CLASSES = (
    'net.minecraft.nbt.CompoundTag', 'net.minecraft.nbt.ListTag', 'net.minecraft.nbt.NbtUtils',
    'net.minecraft.network.FriendlyByteBuf', 'net.minecraft.network.syncher.EntityDataSerializers',
)
SERIALIZER_INTERFACE = 'net.minecraft.network.syncher.EntityDataSerializer'
FORGE_TAIL = 'net.minecraftforge.common.capabilities.CapabilityProvider'
CHAIN = [
    'com.talhanation.workers.entities.BuilderEntity', 'com.talhanation.workers.entities.AbstractWorkerEntity',
    'com.talhanation.recruits.entities.AbstractChunkLoaderEntity', 'com.talhanation.recruits.entities.BowmanEntity',
    'com.talhanation.recruits.entities.AbstractRecruitEntity', 'com.talhanation.recruits.entities.AbstractInventoryEntity',
    'com.talhanation.recruits.pathfinding.AsyncPathfinderMob', 'net.minecraft.world.entity.PathfinderMob',
    'net.minecraft.world.entity.Mob', 'net.minecraft.world.entity.LivingEntity', 'net.minecraft.world.entity.Entity', FORGE_TAIL,
]
PARITY_SCOPE = 'Exact loaded providers and raw class resources; transformed method bodies are not observed'
PREFLIGHT_SCOPE = 'Native registry/COMPOUND_TAG partition preflight at title screen only; no Case L gameplay'
RESOURCE_SCOPE = 'Raw resource equals unique entry in reviewed whole-JAR supplier; transformed method bodies are not observed'
PROOF_KEYS = {'name', 'provider', 'supplierSha256', 'rawResourceSha256', 'codeSourceUrl', 'scope'}
REPORT_KEYS = {
    'formatVersion', 'scenario', 'result', 'phase', 'sourceCommit', 'sourceTree', 'candidateSha256',
    'elapsedSeconds', 'worldCreated', 'integratedServerStarted', 'workersCreated', 'commissionPerformed',
    'paymentCharged', 'gameplayExecuted', 'productionFee', 'plan', 'partition', 'nativeRoundTrips',
    'loadedApiParity', 'serializerParity', 'compoundTagImplementation', 'serializerContract', 'scope',
}


def require(okay, message):
    if not okay:
        raise ValueError(message)


def exact(actual, expected, message):
    """JSON types and sequence order matter; True, 1 and 1.0 never alias."""
    require(json.dumps(actual, sort_keys=True, allow_nan=False, separators=(',', ':'))
            == json.dumps(expected, sort_keys=True, allow_nan=False, separators=(',', ':')), message)


def sha256(value):
    return hashlib.sha256(value).hexdigest()


def packed(x, y, z):
    return ((x & 0x3ffffff) << 38) | ((z & 0x3ffffff) << 12) | (y & 0xfff)


def coordinates(cell):
    # This independently fixed rectangle contains only nonnegative coordinates.
    return [cell >> 38, cell & 0xfff, (cell >> 12) & 0x3ffffff]


def rectangle_atoms():
    """Chebyshev inward bands of the fixed chunk, without production imports."""
    atoms = []
    for x in range(128, 144):
        for z in range(16):
            distance = min(x - 127, 144 - x, z + 1, 16 - z)
            if distance > 5:
                continue
            targets, clearance = {}, []
            for y in range(65, 71):
                cell = packed(x, y, z)
                if y == 68:
                    targets[cell] = MATERIALS[1]
                elif distance in (1, 5) and y in (65, 66, 67, 69):
                    targets[cell] = MATERIALS[0]
                else:
                    clearance.append(cell)
            atoms.append((packed(x, 0, z), targets, clearance))
    return atoms


def raw_stage(atoms, index):
    targets = {cell: material for _, cells, _ in atoms for cell, material in cells.items()}
    clearance = sorted(cell for _, _, cells in atoms for cell in cells)
    columns = [column for column, _, _ in atoms]
    points = [coordinates(cell) for cell in list(targets) + clearance]
    text = 'perimeter-stage-v1\n'
    text += ''.join(f'c:{cell}\n' for cell in columns)
    text += ''.join(f't:{cell}:{material}\n' for cell, material in sorted(targets.items()))
    text += ''.join(f'a:{cell}\n' for cell in clearance)
    return dict(index=index, columns=columns,
                targets=[dict(cell=cell, material=material) for cell, material in sorted(targets.items())],
                clearance=clearance, targetCount=len(targets), clearanceCount=len(clearance),
                reservationCount=len(targets) + len(clearance),
                materials={material: list(targets.values()).count(material) for material in MATERIALS},
                min=[min(point[axis] for point in points) for axis in range(3)],
                max=[max(point[axis] for point in points) for axis in range(3)], layoutStageDigest=sha256(text.encode()))


def derive_expected():
    """Reproduce cap-only recursive proposals, including rejected parent calls."""
    atoms = rectangle_atoms()
    whole = raw_stage(atoms, 0)
    plan = {key: whole[key] for key in ('targetCount', 'clearanceCount', 'materials', 'min', 'max', 'columns', 'targets', 'clearance')}
    plan['columnCount'] = len(atoms)
    stages, calls, current = [], [], []

    def seal():
        nonlocal current
        if current:
            stages.append(raw_stage(current, len(stages)))
            current = []

    def accept(parts):
        nonlocal current
        proposed = current + parts
        stage = raw_stage(proposed, len(stages))
        okay = stage['targetCount'] <= 64
        calls.append(dict(index=len(stages), columnCount=len(proposed), targetCount=stage['targetCount'],
                          layoutStageDigest=stage['layoutStageDigest'], capAccepts=okay))
        if okay:
            current = proposed
        elif current:
            seal()
            accept(parts)
        else:
            require(len(parts) > 1, 'Independent geometry has an unrepresentable column')
            middle = len(parts) // 2
            accept(parts[:middle])
            accept(parts[middle:])

    accept(atoms)
    seal()
    content = 'perimeter-layout-v1\n' + ''.join(f"{s['index']}:{s['layoutStageDigest']}\n" for s in stages)
    partition = dict(cap=64, layoutDigest=sha256(content.encode()), stages=stages, validatorCalls=calls)
    require(len(stages) == 16 and len(calls) == 46 and partition['layoutDigest'] == LAYOUT_SHA256,
            'Independent reconstruction differs from reviewed layout')
    require((plan['targetCount'], plan['clearanceCount'], plan['columnCount']) == (572, 748, 220)
            and plan['materials'] == dict(zip(MATERIALS, (352, 220))), 'Independent rectangle totals changed')
    return plan, partition


def load_candidate():
    raw = GATE.read_bounded(CANDIDATE_PATH, MAX_CANDIDATE_BYTES)
    require(sha256(raw) == CANDIDATE_SHA256, 'Pinned partition candidate changed; independent review is required')
    candidate = GATE.CAPTURE.parse_unique(raw)
    _, expected = derive_expected()
    exact(candidate['candidate'], expected, 'Pinned candidate differs from independent reconstruction')
    return candidate['candidate']


def verify_report(data):
    require(isinstance(data, dict) and set(data) == REPORT_KEYS, 'Incomplete or unexpected preflight result schema')
    for key, value in dict(formatVersion=1, scenario='native-helper-partition-preflight', result='passed', phase='FINISHED',
                           worldCreated=False, integratedServerStarted=False, workersCreated=0,
                           commissionPerformed=False, paymentCharged=0, gameplayExecuted=False, productionFee=64,
                           candidateSha256=CANDIDATE_SHA256, scope=PREFLIGHT_SCOPE).items():
        exact(data[key], value, 'Changed preflight identity, fee or no-gameplay claim: ' + key)
    for key in ('sourceCommit', 'sourceTree'):
        require(isinstance(data[key], str) and re.fullmatch('[0-9a-f]{40}', data[key]), 'Malformed source identity: ' + key)
    elapsed = data['elapsedSeconds']
    require(type(elapsed) in (int, float) and 0 <= elapsed <= 600 and math.isfinite(elapsed), 'Preflight exceeded its fixed time bound')
    plan, expected = derive_expected()
    exact(data['plan'], plan, 'Plan differs from independent rectangle: columns, targets, materials, clearance or bounds')
    exact(data['partition'], expected, 'Partition differs from independent atomic traversal, reservations or raw digests')
    exact(data['partition'], load_candidate(), 'Partition differs from immutable pinned candidate')
    calls = [dict(ordinal=ordinal, index=call['index'], layoutStageDigest=call['layoutStageDigest'],
                  blueprintBlockCount=call['targetCount'], targetCount=call['targetCount'],
                  nativeRoundTripPassed=True, problem=None)
             for ordinal, call in enumerate(expected['validatorCalls'])]
    exact(data['nativeRoundTrips'], calls, 'Missing, reordered, changed or failed native roundtrip call')
    serializer_resource_names(data)
    return True


def verify_provider_url(value, provider):
    """Recheck the accepted file/JAR-root/strict SecureJarHandler union shapes."""
    require(isinstance(value, str) and 0 < len(value) <= 8192
            and not any(character.isspace() or ord(character) < 32 or ord(character) == 127 for character in value),
            'Missing/bounded or malformed loaded provider URL')
    require(isinstance(provider, str) and provider.endswith('.jar') and '#' not in provider and '!' not in provider,
            'Malformed provider path')
    path = Path(provider)
    require(path.is_absolute() and str(path.resolve(strict=True)) == provider and path.is_file(), 'Noncanonical loaded provider')
    require(not re.search(r'%(?:2f|5c|2e|25|21)', value, re.I)
            and not re.search(r'%(?![0-9a-fA-F]{2})', value), 'Encoded provider separator/traversal')
    uri = urlsplit(value)
    require(not uri.netloc and not uri.query and not uri.fragment and '?' not in value and '#' not in value,
            'Provider authority, query or fragment is unsupported')
    if uri.scheme == 'jar':
        require(value.startswith('jar:file:') and value.endswith('!/') and value.count('!') == 1, 'Non-root or non-file JAR provider')
        verify_provider_url(value[4:-2], provider)
        return
    decoded = unquote(uri.path, encoding='utf-8', errors='strict')
    if uri.scheme == 'file':
        require(decoded == provider, 'Provider is not the exact reviewed supplier')
        return
    require(uri.scheme == 'union' and decoded.startswith(provider), 'Unreviewed provider protocol or supplier')
    suffix = decoded[len(provider):]
    require(re.fullmatch(r'(?:#(?:0|[1-9][0-9]{0,9})){1,8}!/?', suffix), 'Unreviewed union index/root shape')
    literal = provider
    for index in suffix[:suffix.index('!')].split('#')[1:]:
        require(int(index) <= 2**31 - 1, 'Union index exceeds native range')
        literal += '#' + index
        require(not Path(literal).exists() and not Path(literal).is_symlink(), 'Ambiguous union numeric-hash filename')


def verify_raw_entries(proofs, provider, supplier_hash):
    """Never load classes or execute serializer code in the independent oracle."""
    path = Path(provider)
    require(path.is_absolute() and str(path.resolve(strict=True)) == provider and path.is_file()
            and path.stat().st_size <= GATE.CAPTURE.MAX_RUNTIME_FILE_BYTES, 'Noncanonical/bounded serializer supplier')
    require(GATE.CAPTURE.file_hash(path) == supplier_hash, 'Serializer whole-JAR supplier changed')
    with zipfile.ZipFile(path) as jar:
        entries = jar.infolist()
        require(len(entries) <= GATE.CAPTURE.MAX_JAR_ENTRIES, 'Serializer JAR index exceeds bound')
        for proof in proofs:
            resource = proof['name'].replace('.', '/') + '.class'
            require(not any(entry.filename.startswith('META-INF/versions/') and entry.filename.endswith('/' + resource)
                            for entry in entries), 'Versioned serializer resource is ambiguous')
            matches = [entry for entry in entries if entry.filename == resource]
            require(len(matches) == 1 and not matches[0].is_dir(), 'Missing or duplicate serializer JAR entry')
            require(0 < matches[0].file_size <= GATE.CAPTURE.MAX_CLASS_BYTES, 'Empty/oversized serializer JAR entry')
            with jar.open(matches[0]) as stream:
                raw = stream.read(GATE.CAPTURE.MAX_CLASS_BYTES + 1)
            require(0 < len(raw) <= GATE.CAPTURE.MAX_CLASS_BYTES and sha256(raw) == proof['rawResourceSha256'],
                    'Serializer raw resource differs from exact reviewed JAR entry')
    require(GATE.CAPTURE.file_hash(path) == supplier_hash, 'Serializer whole-JAR changed during attestation')


def binary_class_name(value):
    require(isinstance(value, str) and 1 <= len(value) <= 512
            and re.fullmatch(r'[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*', value)
            and value not in {'void', 'boolean', 'byte', 'char', 'short', 'int', 'long', 'float', 'double'},
            'Malformed or unbounded serializer binary class name')
    return value


def serializer_resource_names(data):
    """Validate reflected actual-instance evidence without guessing its class shape.

    Read-result assignability and object/loader identity are checked by Java, not
    inferred from a naming pattern or from raw resources as transformed bytecode.
    All observed owners and any covariant result class join the pinned JAR closure.
    """
    implementation = binary_class_name(data['compoundTagImplementation'])
    require(implementation not in (*SERIALIZER_CLASSES, SERIALIZER_INTERFACE),
            'Serializer implementation is not a distinct concrete implementation')
    contract = data['serializerContract']
    require(isinstance(contract, dict) and set(contract) == {'interface', 'fieldOwner', 'field', 'implementation',
            'sameDefiningLoader', 'implementationConcrete', 'instanceStable', 'methods'}, 'Malformed actual serializer contract')
    for key, value in dict(interface=SERIALIZER_INTERFACE, fieldOwner=SERIALIZER_CLASSES[-1], field='COMPOUND_TAG',
                           implementation=implementation, sameDefiningLoader=True, implementationConcrete=True,
                           instanceStable=True).items():
        exact(contract[key], value, 'Changed actual serializer field/type/loader/instance contract: ' + key)
    methods = contract['methods']
    require(isinstance(methods, list) and len(methods) == 2, 'Missing exact write/read serializer method contract')
    for method, name, parameters in zip(methods, ('write', 'read'),
            ([SERIALIZER_CLASSES[3], 'java.lang.Object'], [SERIALIZER_CLASSES[3]])):
        require(isinstance(method, dict) and set(method) == {'name', 'owner', 'parameters', 'returnType',
                'public', 'static', 'abstract', 'returnCompatible', 'bridge', 'synthetic'}, 'Malformed actual serializer method contract')
        binary_class_name(method['owner'])
        require(type(method['bridge']) is bool and type(method['synthetic']) is bool,
                'Serializer bridge/synthetic observations must be strict booleans')
        for key, value in {'name': name, 'parameters': parameters, 'public': True,
                           'static': False, 'abstract': False, 'returnCompatible': True}.items():
            exact(method[key], value, 'Changed actual serializer method signature/visibility: ' + name + '.' + key)
        if name == 'write':
            exact(method['returnType'], 'void', 'Serializer write return type changed')
        else:
            binary_class_name(method['returnType'])
    names = list(SERIALIZER_CLASSES) + [SERIALIZER_INTERFACE, implementation, methods[0]['owner'], methods[1]['owner']]
    if methods[1]['returnType'] != 'java.lang.Object':
        names.append(methods[1]['returnType'])
    closure = list(dict.fromkeys(names))
    require(7 <= len(closure) <= 10, 'Actual serializer resource closure exceeds fixed bounds')
    return closure


def verify_attestations(data, gate, gate_hash):
    """gate must be the receipt returned by unchanged GATE.verify_receipt."""
    parity = data['loadedApiParity']
    require(isinstance(parity, dict) and set(parity) == {'passed', 'gateSha256', 'contractSha256', 'classes',
            'superclassChain', 'codeSourceUrls', 'forgeSuperclassTail', 'scope'}, 'Malformed loaded API parity')
    require(parity['passed'] is True and parity['scope'] == PARITY_SCOPE, 'Missing loaded parity or unproved transformed-code claim')
    require(parity['gateSha256'] == gate_hash and parity['contractSha256'] == gate['reviewedGateContractSha256'],
            'Loaded parity does not bind exact reviewed API receipt')
    expected = {entry['name']: dict(provider=entry['supplierCanonicalPath'], supplierSha256=entry['supplierSha256'],
                                   rawResourceSha256=entry['classSha256']) for entry in gate['classes']}
    exact(parity['classes'], expected, 'Loaded class closure differs from unchanged API gate')
    exact(parity['superclassChain'], CHAIN, 'Loaded worker superclass chain changed')
    urls = parity['codeSourceUrls']
    require(isinstance(urls, dict) and set(urls) == set(expected), 'Missing or extra loaded provider URLs')
    for name, url in urls.items():
        verify_provider_url(url, expected[name]['provider'])
    entity = expected['net.minecraft.world.entity.Entity']
    provider, supplier_hash = entity['provider'], entity['supplierSha256']
    tail = parity['forgeSuperclassTail']
    names = serializer_resource_names(data)
    serializers = data['serializerParity']
    require(isinstance(serializers, list) and len(serializers) == len(names), 'Missing exact serializer resource closure')
    require(all(isinstance(proof, dict) for proof in serializers), 'Malformed serializer attestation')
    exact([proof.get('name') for proof in serializers], names,
          'Missing, duplicate or reordered serializer roots/implementation/owners/return class')
    for proof, name in [(tail, FORGE_TAIL)] + list(zip(serializers, names)):
        require(isinstance(proof, dict) and set(proof) == PROOF_KEYS and proof['name'] == name, 'Malformed unique-entry raw-resource proof')
        require(proof['provider'] == provider and proof['supplierSha256'] == supplier_hash
                and proof['scope'] == RESOURCE_SCOPE, 'Serializer/tail proof changed exact Forge supplier or scope')
        GATE.require_hash(proof['rawResourceSha256'], 'raw serializer resource')
        verify_provider_url(proof['codeSourceUrl'], provider)
    verify_raw_entries([tail] + serializers, provider, supplier_hash)
    return True


def verify_world_free_directory(evidence):
    """An empty vanilla saves container is not world creation; contents are fatal."""
    client = evidence.parent / 'client'
    require(client.is_dir() and client.resolve(strict=True) == client and not client.is_symlink(),
            'Missing or noncanonical isolated preflight client directory')
    saves = client / 'saves'
    require(not saves.is_symlink(), 'World-free saves path is a symlink')
    try:
        saves.lstat()
    except FileNotFoundError:
        return
    require(saves.is_dir() and saves.resolve(strict=True) == saves,
            'World-free saves path is not a canonical real directory')
    require(next(saves.iterdir(), None) is None, 'World-free saves directory contains an entry')


def verify(evidence, runtime_manifest):
    evidence = evidence.absolute()
    verify_world_free_directory(evidence)
    path = evidence / 'result.json'
    raw = GATE.read_bounded(path, MAX_RESULT_BYTES)
    data = GATE.CAPTURE.parse_unique(raw)
    verify_report(data)
    gate_path = evidence / 'api-gate/gate.json'
    # Full replay is mandatory; no trusting a saved pass flag, recapture or repair.
    gate = GATE.verify_receipt(runtime_manifest, gate_path)
    gate_bytes = GATE.read_bounded(gate_path, GATE.MAX_RECEIPT_BYTES)
    exact(GATE.CAPTURE.parse_unique(gate_bytes), gate, 'API receipt changed after replay')
    source = GATE.read_source_identity(runtime_manifest.absolute())[0]
    for field, filename in [('sourceCommit', 'source-commit.txt'), ('sourceTree', 'source-tree.txt')]:
        recorded = GATE.read_bounded(evidence / filename, 41)
        require(re.fullmatch(rb'[0-9a-f]{40}\n?', recorded), 'Malformed result source file')
        require(data[field] == gate[field] == source[field] == recorded.decode('ascii').strip(),
                'Result, prepared source and prelaunch gate identity differ')
    verify_attestations(data, gate, sha256(gate_bytes))
    verify_world_free_directory(evidence)
    require(GATE.read_bounded(path, MAX_RESULT_BYTES) == raw
            and GATE.read_bounded(gate_path, GATE.MAX_RECEIPT_BYTES) == gate_bytes, 'Evidence changed during verification')
    return data


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('evidence', type=Path)
    parser.add_argument('runtime_manifest', type=Path)
    args = parser.parse_args()
    try:
        verify(args.evidence, args.runtime_manifest)
    except (ValueError, KeyError, TypeError, OSError, UnicodeError, zipfile.BadZipFile, RecursionError) as error:
        parser.exit(1, f'Native helper partition preflight rejected: {error}\n')
    print('Verified the fixed 16-stage partition and 46 native codec roundtrips; no world, payment or gameplay acceptance.')


if __name__ == '__main__':
    main()
