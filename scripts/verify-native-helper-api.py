#!/usr/bin/env python3
"""Fail-closed prelaunch gate for the independently reviewed native helper API.

Run after exact runClient preparation and before the game. This tool never launches
Minecraft, downloads vendor JARs, or invokes a movement/ticket API. The receipt is
only prepared, raw/pre-mixin evidence; loaded-artifact parity and live acceptance
remain mandatory. Capture-only operation stays in capture-native-helper-api.py.
"""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import zipfile

SCRIPT_DIR = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location('native_helper_api_capture', SCRIPT_DIR / 'capture-native-helper-api.py')
CAPTURE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CAPTURE)

PIN_PATH = SCRIPT_DIR / 'native-api/helper-lifecycle-1.20.1-47.4.16.json'
REVIEW_PATH = SCRIPT_DIR / 'native-api/helper-lifecycle-semantic-review.json'
# Review-issued source artifact digests, not values generated from the runtime.
REVIEWED_PINS_SHA256 = 'a50351d2eef7e9581fc23deaf2d6d8780865a1e31d0dcf2cff5dec2d266f089c'
SEMANTIC_REVIEW_SHA256 = 'bb538793af047c7e1260fc58c0857e988facc2176fbf9552f1a9026d469d0900'
BASELINE_COMMIT = 'e51ff6d5d050dacb003a6fcaf04df3c3b8787656'
SOURCE_COMMIT = '06525b74da9d42caf096459990273cf3be3ff16d'
SOURCE_TREE = '34b5d27023b13f868fff5a94a234f9ef0a8b33a6'
SUPPLEMENTAL_CLASSES = frozenset({
    'com.talhanation.recruits.entities.BowmanEntity',
    'com.talhanation.recruits.inventory.RecruitSimpleContainer',
})
MAX_REVIEW_BYTES = 256 * 1024
MAX_RECEIPT_BYTES = 128 * 1024


def digest(data):
    return hashlib.sha256(data).hexdigest()


def require_hash(value, label):
    if not isinstance(value, str) or re.fullmatch('[0-9a-f]{64}', value) is None:
        raise ValueError(f'Missing or malformed {label} hash')
    return value


def read_bounded(path, limit):
    if path.absolute() != path.resolve(strict=True) or not path.is_file():
        raise ValueError('Evidence path is not a canonical regular file')
    if path.stat().st_size > limit:
        raise ValueError('Evidence exceeds bounded input')
    data = path.read_bytes()
    if len(data) > limit:
        raise ValueError('Evidence exceeds bounded input')
    return data


def load_reviewed_contract():
    """Accept only the checked-in, immutable independent review; no CLI pin override."""
    pin_bytes = read_bounded(PIN_PATH, MAX_REVIEW_BYTES)
    if digest(pin_bytes) != REVIEWED_PINS_SHA256:
        raise ValueError('Reviewed native API pins changed; independent review is required')
    pins = CAPTURE.parse_unique(pin_bytes)
    review = CAPTURE.parse_unique(read_bounded(REVIEW_PATH, MAX_REVIEW_BYTES))
    if not isinstance(review, dict) or review.get('formatVersion') != 1:
        raise ValueError('Malformed independent semantic review')
    review_text = review.get('reviewText')
    if (not isinstance(review_text, str)
            or digest(review_text.encode('utf-8')) != SEMANTIC_REVIEW_SHA256
            or review.get('reviewTextSha256') != SEMANTIC_REVIEW_SHA256
            or review.get('implementationBaselineCommit') != BASELINE_COMMIT
            or review.get('evidence') != dict(prepareOnlyRunId=38160280135,
                    prepareOnlyArtifactId=11709799787, supplementalNativeRunId=38159579852,
                    reviewedPinsSha256=REVIEWED_PINS_SHA256)):
        raise ValueError('Independent semantic/control-flow contract changed')
    if (pins.get('formatVersion') != 1 or pins.get('gameplayAccepted') is not False
            or pins.get('sourceCommit') != SOURCE_COMMIT or pins.get('sourceTree') != SOURCE_TREE
            or len(pins.get('artifacts', [])) != 3 or len(pins.get('classes', [])) != 60
            or len(pins.get('reviewedMethods', [])) != 74):
        raise ValueError('Unexpected independent review coverage')
    return dict(pins=pins, reviewedPinsSha256=REVIEWED_PINS_SHA256,
                semanticReviewSha256=SEMANTIC_REVIEW_SHA256,
                reviewedGateContractSha256=digest(pin_bytes + b'\0' + review_text.encode('utf-8')))


def unique_index(records, field, label, maximum):
    if not isinstance(records, list) or not 1 <= len(records) <= maximum:
        raise ValueError(f'Unexpected {label} count')
    result = {}
    for record in records:
        if not isinstance(record, dict) or not isinstance(record.get(field), str) or not record[field]:
            raise ValueError(f'Malformed {label}')
        key = record[field]
        if key in result:
            raise ValueError(f'Ambiguous {label}: {key}')
        result[key] = record
    return result


def validate_prepared_inventory(suppliers, artifacts, pins):
    """Reject unreviewed bytes before invoking javap, even in a fresh capture."""
    approved_artifacts = unique_index(pins.get('artifacts'), 'sha256', 'reviewed supplier digest', 16)
    approved_paths = unique_index(pins.get('artifacts'), 'path', 'reviewed supplier', 16)
    if len(artifacts) != len(approved_artifacts):
        raise ValueError('Missing or unexpected native supplier')
    seen = set()
    for artifact in artifacts.values():
        expected = approved_artifacts.get(artifact['sha256'])
        if expected is None or artifact['sha256'] in seen:
            raise ValueError('Changed, unreviewed or ambiguous native supplier')
        seen.add(artifact['sha256'])
        if any(artifact.get(key) != expected.get(key)
               for key in ('fileName', 'resolvedCoordinates', 'jarManifest')):
            raise ValueError('Native supplier metadata differs from independently reviewed identity')
    approved_classes = unique_index(pins.get('classes'), 'name', 'reviewed class', CAPTURE.MAX_CLASSES)
    if set(suppliers) != set(approved_classes):
        raise ValueError('Missing or unreviewed native class closure')
    for name, (path, class_hash) in suppliers.items():
        expected = approved_classes[name]
        historical_supplier = approved_paths.get(expected.get('supplier'))
        supplier_hash = historical_supplier['sha256'] if historical_supplier else expected.get('supplierSha256')
        if artifacts[str(path)]['sha256'] != supplier_hash:
            raise ValueError(f'Changed class supplier: {name}')
        if expected.get('classSha256') is not None:
            if class_hash != expected['classSha256']:
                raise ValueError(f'Raw class differs from reviewed control flow: {name}')
        elif (name not in SUPPLEMENTAL_CLASSES or not expected.get('rawClassHashStatus')
              or expected.get('evidence') != 'native38159579852/artifact'):
            raise ValueError(f'Class has no independent raw/supplier approval: {name}')


def validate_capture(report, capture_root, pins):
    """Compare fresh capture to reviewed suppliers, bodies and exact signatures.

    Full reviewed class/text equality is intentionally stricter than selected opcode
    matches: it binds every branch and inherited override to the reviewed control
    flow. A matching declaration or a newly calculated digest alone never passes.
    The only missing historical raw hashes are explicitly approved supplemental
    roots, whose exact containing JAR and legacy whole-class text are both pinned.
    """
    if (not isinstance(report, dict) or type(report.get('formatVersion')) is not int
            or report.get('formatVersion') != 1 or report.get('status') != 'captured-unreviewed'
            or report.get('gameplayAuthorized') is not False
            or report.get('versions') != CAPTURE.PINNED):
        raise ValueError('Unexpected fresh API capture identity')
    require_hash(report.get('runtimeManifestSha256'), 'runtime manifest')
    approved_by_path = unique_index(pins.get('artifacts'), 'path', 'reviewed supplier', 16)
    approved_by_hash = unique_index(pins.get('artifacts'), 'sha256', 'reviewed supplier digest', 16)
    artifacts = unique_index(report.get('artifacts'), 'path', 'captured supplier', 16)
    if len(artifacts) != len(approved_by_hash):
        raise ValueError('Missing or unexpected native supplier')
    seen_hashes = set()
    for path, artifact in artifacts.items():
        supplier_hash = require_hash(artifact.get('sha256'), 'supplier')
        expected = approved_by_hash.get(supplier_hash)
        if expected is None or supplier_hash in seen_hashes:
            raise ValueError('Changed, unreviewed or ambiguous native supplier')
        seen_hashes.add(supplier_hash)
        if any(artifact.get(key) != expected.get(key)
               for key in ('fileName', 'resolvedCoordinates', 'jarManifest')):
            raise ValueError('Native supplier metadata differs from independently reviewed identity')
        if not Path(path).is_absolute() or str(Path(path).resolve()) != path:
            raise ValueError('Native supplier path is not canonical')
    classes = unique_index(report.get('classes'), 'name', 'captured class', CAPTURE.MAX_CLASSES)
    approved_classes = unique_index(pins.get('classes'), 'name', 'reviewed class', CAPTURE.MAX_CLASSES)
    if set(classes) != set(approved_classes):
        raise ValueError('Missing or unreviewed native class closure')
    texts, result_classes = {}, []
    for name, record in sorted(classes.items()):
        expected = approved_classes[name]
        artifact = artifacts.get(record.get('supplier'))
        if artifact is None:
            raise ValueError(f'Class supplier is not in the reviewed runtime: {name}')
        expected_artifact = approved_by_path.get(expected.get('supplier'))
        expected_supplier_hash = (expected_artifact['sha256'] if expected_artifact
                                  else expected.get('supplierSha256'))
        if artifact['sha256'] != expected_supplier_hash:
            raise ValueError(f'Changed class supplier: {name}')
        class_hash = require_hash(record.get('classSha256'), 'raw class')
        expected_file = f'bytecode/{name}.javap.txt'
        if record.get('disassemblyFile') != expected_file:
            raise ValueError(f'Unexpected disassembly path: {name}')
        text_bytes = read_bounded(capture_root / expected_file, CAPTURE.MAX_DISASSEMBLY_BYTES)
        if digest(text_bytes) != require_hash(record.get('disassemblySha256'), 'disassembly'):
            raise ValueError(f'Captured disassembly changed: {name}')
        text = text_bytes.decode('utf-8')
        texts[name] = text
        if expected.get('classSha256') is not None:
            if class_hash != expected['classSha256']:
                raise ValueError(f'Raw class differs from reviewed control flow: {name}')
            comparison_bytes = text_bytes
            class_hash_status = 'previously-reviewed-raw-hash'
        else:
            if (name not in SUPPLEMENTAL_CLASSES or not expected.get('rawClassHashStatus')
                    or expected.get('evidence') != 'native38159579852/artifact'):
                raise ValueError(f'Class has no independent raw/supplier approval: {name}')
            # The supplemental audit ran javap -c -p -l, without -s. Remove only
            # descriptor lines; preserve all declarations, opcodes and branches.
            comparison_bytes = re.sub(r'^    descriptor: [^\n]+\n', '', text, flags=re.MULTILINE).encode('utf-8')
            class_hash_status = 'newly-measured-under-reviewed-supplier-pin'
        if digest(comparison_bytes) != expected.get('disassemblySha256'):
            raise ValueError(f'Whole-class disassembly differs from reviewed control flow: {name}')
        result_classes.append(dict(name=name, classSha256=class_hash,
                supplierSha256=artifact['sha256'], supplierCanonicalPath=record['supplier'],
                disassemblySha256=record['disassemblySha256'], classHashStatus=class_hash_status))
    methods = pins.get('reviewedMethods')
    if not isinstance(methods, list) or not 1 <= len(methods) <= 256:
        raise ValueError('Missing or unbounded independently reviewed method contract')
    seen_methods = set()
    for method in methods:
        if not isinstance(method, dict):
            raise ValueError('Malformed reviewed method contract')
        class_name, declaration, descriptor = (method.get(k) for k in ('className', 'declaration', 'descriptor'))
        if any(not isinstance(value, str) or not value for value in (class_name, declaration, descriptor)):
            raise ValueError('Malformed reviewed method signature')
        key = (class_name, declaration, descriptor)
        if key in seen_methods or class_name not in texts:
            raise ValueError('Ambiguous or uncaptured reviewed method')
        seen_methods.add(key)
        lines = texts[class_name].splitlines()
        matches = [index for index, line in enumerate(lines) if line == '  ' + declaration]
        if (len(matches) != 1 or matches[0] + 1 >= len(lines)
                or lines[matches[0] + 1] != '    descriptor: ' + descriptor):
            raise ValueError(f'Reviewed method declaration/descriptor changed: {class_name}: {declaration}')
    return result_classes


def read_source_identity(runtime_manifest):
    values, raw = {}, {}
    for key, filename in (('sourceCommit', 'source-commit.txt'), ('sourceTree', 'source-tree.txt')):
        data = read_bounded(runtime_manifest.parent / filename, 41)
        if re.fullmatch(rb'[0-9a-f]{40}\n?', data) is None:
            raise ValueError(f'Missing or malformed current prepared {filename}')
        values[key], raw[filename] = data.decode('ascii').strip(), data
    return values, raw


def expected_receipt(runtime_manifest, capture_root, report, contract, source_identity):
    pins = contract['pins']
    classes = validate_capture(report, capture_root, pins)
    # Recheck every exported path, including absent entries and directory shadows.
    # Java independently establishes parity with future loaded providers/resources.
    manifest_hash, suppliers, artifacts = CAPTURE.inspect_runtime(runtime_manifest)
    if (manifest_hash != report['runtimeManifestSha256']
            or artifacts != {a['path']: a for a in report['artifacts']}
            or suppliers != {c['name']: (Path(c['supplier']), c['classSha256']) for c in report['classes']}):
        raise ValueError('Prepared runtime changed during semantic gate')
    capture_bytes = read_bounded(capture_root / 'capture.json', MAX_REVIEW_BYTES)
    if json.dumps(CAPTURE.parse_unique(capture_bytes), sort_keys=True) != json.dumps(report, sort_keys=True):
        raise ValueError('Capture receipt changed during semantic gate')
    for record in report['classes']:
        if digest(read_bounded(capture_root / record['disassemblyFile'], CAPTURE.MAX_DISASSEMBLY_BYTES)) != record['disassemblySha256']:
            raise ValueError('Captured disassembly changed during semantic gate')
    if read_source_identity(runtime_manifest) != source_identity:
        raise ValueError('Current prepared source identity changed during semantic gate')
    if load_reviewed_contract() != contract:
        raise ValueError('Independent review changed during semantic gate')
    return dict(formatVersion=1, semanticApiGatePassed=True, gameplayAccepted=False,
            loadedArtifactParityRequired=True, gatePhase='prepared-prelaunch-raw-pre-mixin',
            **source_identity[0], reviewedSourceCommit=pins['sourceCommit'],
            reviewedSourceTree=pins['sourceTree'], baselineCommit=BASELINE_COMMIT,
            versions=dict(CAPTURE.PINNED),
            reviewedPinsSha256=contract['reviewedPinsSha256'],
            semanticReviewSha256=contract['semanticReviewSha256'],
            reviewedGateContractSha256=contract['reviewedGateContractSha256'],
            runtimeManifestCanonicalPath=str(runtime_manifest), runtimeManifestSha256=manifest_hash,
            captureSha256=digest(capture_bytes),
            reviewedMethodCount=len(pins['reviewedMethods']), reviewedClassCount=len(classes),
            suppliers=[dict(canonicalPath=a['path'], fileName=a['fileName'], sha256=a['sha256'],
                            resolvedCoordinates=a['resolvedCoordinates'])
                       for a in sorted(artifacts.values(), key=lambda a: a['path'])],
            classes=classes)


def verify(runtime_manifest, output, runner=CAPTURE.disassemble):
    contract = load_reviewed_contract()
    runtime_manifest = runtime_manifest.absolute()
    source_identity = read_source_identity(runtime_manifest)
    _, suppliers, artifacts = CAPTURE.inspect_runtime(runtime_manifest)
    validate_prepared_inventory(suppliers, artifacts, contract['pins'])
    if output.absolute() != output.resolve():
        raise ValueError('Refusing a symlinked gate output path')
    output.mkdir(parents=True, exist_ok=False)
    capture_root = output / 'api-capture'
    report = CAPTURE.capture(runtime_manifest, capture_root, runner)
    receipt = expected_receipt(runtime_manifest, capture_root, report, contract, source_identity)
    encoded = (json.dumps(receipt, indent=2, sort_keys=True) + '\n').encode('utf-8')
    if len(encoded) > MAX_RECEIPT_BYTES:
        raise ValueError('Semantic gate receipt exceeds bounded output')
    with (output / 'gate.json').open('xb') as stream:
        stream.write(encoded)
    return receipt


def verify_receipt(runtime_manifest, gate_path):
    """Read-only replay protection immediately before direct Gradle game launch.

    This never creates/corrects evidence. It checks the caller's exact prepared
    runtime, source identity, every supplier/class, saved disassembly, independent
    review, and the entire receipt rather than trusting its passed booleans.
    """
    contract = load_reviewed_contract()
    runtime_manifest = runtime_manifest.absolute()
    source_identity = read_source_identity(runtime_manifest)
    receipt_bytes = read_bounded(gate_path, MAX_RECEIPT_BYTES)
    receipt = CAPTURE.parse_unique(receipt_bytes)
    if not isinstance(receipt, dict) or gate_path.name != 'gate.json':
        raise ValueError('Malformed semantic gate receipt')
    capture_root = gate_path.parent / 'api-capture'
    report_bytes = read_bounded(capture_root / 'capture.json', MAX_REVIEW_BYTES)
    report = CAPTURE.parse_unique(report_bytes)
    expected = expected_receipt(runtime_manifest, capture_root, report, contract, source_identity)
    # Python considers True == 1 and 1 == 1.0; JSON types are part of the contract.
    if json.dumps(receipt, sort_keys=True) != json.dumps(expected, sort_keys=True):
        raise ValueError('Semantic gate receipt does not match the current reviewed runtime/source bundle')
    if (read_bounded(gate_path, MAX_RECEIPT_BYTES) != receipt_bytes
            or read_bounded(capture_root / 'capture.json', MAX_REVIEW_BYTES) != report_bytes):
        raise ValueError('Semantic gate bundle changed during receipt validation')
    return receipt


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('runtime_manifest', type=Path)
    parser.add_argument('output_directory', type=Path, nargs='?')
    parser.add_argument('--verify-receipt', type=Path, metavar='GATE_JSON',
                        help='Read-only revalidation of an existing gate bundle; do not recapture or write')
    args = parser.parse_args()
    if (args.output_directory is None) == (args.verify_receipt is None):
        parser.error('Choose exactly one fresh output directory or --verify-receipt GATE_JSON')
    try:
        receipt = (verify_receipt(args.runtime_manifest, args.verify_receipt) if args.verify_receipt
                   else verify(args.runtime_manifest, args.output_directory))
    except (OSError, ValueError, KeyError, TypeError, UnicodeError, zipfile.BadZipFile,
            subprocess.SubprocessError) as error:
        parser.exit(1, f'Refused native helper semantic API gate: {error}\n')
    print(f"Passed reviewed prepared API gate for {receipt['reviewedClassCount']} classes and "
          f"{receipt['reviewedMethodCount']} exact signatures; loaded parity and gameplay acceptance remain required.")


if __name__ == '__main__':
    main()
