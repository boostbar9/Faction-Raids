"""Synthetic fail-closed prelaunch gate tests; no native gameplay is exercised."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('helper_api_gate', ROOT / 'scripts/verify-native-helper-api.py')
GATE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GATE)
CAPTURE = GATE.CAPTURE


def sha(data):
    return hashlib.sha256(data).hexdigest()


class HelperApiGateTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name).resolve()
        self.jar = self.root / 'synthetic-runtime.jar'
        self.names = list(CAPTURE.ROOT_CLASSES) + ['net.minecraft.world.entity.Entity$RemovalReason']
        self.raw = {name: b'synthetic non-executable class: ' + name.encode() for name in self.names}
        self.write_jar()
        self.manifest = dict(formatVersion=1, **CAPTURE.PINNED,
                             entries=[self.entry(self.jar)])
        self.manifest_path = self.root / 'resolved-runtime.json'
        self.manifest_path.write_text(json.dumps(self.manifest))
        (self.root / 'source-commit.txt').write_text('a' * 40 + '\n')
        (self.root / 'source-tree.txt').write_text('b' * 40 + '\n')
        self.output = self.root / 'gate'
        self.artifact = dict(path=str(self.jar), fileName=self.jar.name,
                sha256=CAPTURE.file_hash(self.jar), resolvedCoordinates=['test:synthetic:1'],
                jarManifest='Manifest-Version: 1.0\nSynthetic: true\n')
        classes = []
        for name in self.names:
            text = self.disassemble(self.jar, name)
            if name in GATE.SUPPLEMENTAL_CLASSES:
                classes.append(dict(name=name, supplierSha256=self.artifact['sha256'],
                        disassemblySha256=sha(self.legacy_text(text).encode()),
                        rawClassHashStatus='Synthetic test of approved supplemental supplier binding',
                        evidence='native38159579852/artifact'))
            else:
                classes.append(dict(name=name, supplier=str(self.jar), classSha256=sha(self.raw[name]),
                                    disassemblySha256=sha(text.encode())))
        self.pins = dict(formatVersion=1, sourceCommit=GATE.SOURCE_COMMIT,
                         sourceTree=GATE.SOURCE_TREE, gameplayAccepted=False,
                         artifacts=[self.artifact], classes=classes, reviewedMethods=[dict(
                             className=self.names[0], declaration='public void testMethod();',
                             descriptor='()V', method='testMethod')])
        self.contract = dict(pins=self.pins, reviewedPinsSha256='1' * 64,
                             semanticReviewSha256='2' * 64, reviewedGateContractSha256='3' * 64)
        self.loader = patch.object(GATE, 'load_reviewed_contract', return_value=self.contract).start()
        self.addCleanup(patch.stopall)

    def write_jar(self, extra=None):
        with zipfile.ZipFile(self.jar, 'w') as jar:
            jar.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\nSynthetic: true\n')
            for name, data in {**self.raw, **(extra or {})}.items():
                jar.writestr(name.replace('.', '/') + '.class', data)

    def entry(self, path):
        return dict(path=str(path), kind='file', coordinates=['test:synthetic:1'], sha256=CAPTURE.file_hash(path))

    @staticmethod
    def disassemble(jar, name):
        return ('public class ' + name + ' {\n  public void testMethod();\n'
                '    descriptor: ()V\n    Code:\n       0: return\n}\n')

    @staticmethod
    def legacy_text(text):
        return re.sub(r'^    descriptor: [^\n]+\n', '', text, flags=re.MULTILINE)

    def run_gate(self, runner=None):
        return GATE.verify(self.manifest_path, self.output, runner or self.disassemble)

    def capture(self):
        return CAPTURE.capture(self.manifest_path, self.output, self.disassemble)

    def save_manifest(self):
        self.manifest_path.write_text(json.dumps(self.manifest))

    def expect_no_receipt(self):
        self.assertFalse((self.output / 'gate.json').exists())

    def test_fresh_gate_binds_current_source_and_every_supplier_class_to_review(self):
        receipt = self.run_gate()
        self.assertTrue(receipt['semanticApiGatePassed'])
        self.assertIs(False, receipt['gameplayAccepted'])
        self.assertTrue(receipt['loadedArtifactParityRequired'])
        self.assertNotIn('gameplayAuthorized', receipt)
        self.assertEqual('a' * 40, receipt['sourceCommit'])
        self.assertEqual('b' * 40, receipt['sourceTree'])
        self.assertEqual(GATE.SOURCE_COMMIT, receipt['reviewedSourceCommit'])
        self.assertEqual(GATE.SOURCE_TREE, receipt['reviewedSourceTree'])
        self.assertEqual(str(self.manifest_path), receipt['runtimeManifestCanonicalPath'])
        self.assertEqual(CAPTURE.file_hash(self.manifest_path), receipt['runtimeManifestSha256'])
        self.assertEqual(self.contract['reviewedGateContractSha256'], receipt['reviewedGateContractSha256'])
        self.assertEqual(len(self.names), receipt['reviewedClassCount'])
        self.assertEqual(1, receipt['reviewedMethodCount'])
        self.assertEqual(self.artifact['sha256'], receipt['suppliers'][0]['sha256'])
        self.assertEqual(str(self.jar), receipt['suppliers'][0]['canonicalPath'])
        for cls in receipt['classes']:
            self.assertEqual(sha(self.raw[cls['name']]), cls['classSha256'])
            self.assertEqual(self.artifact['sha256'], cls['supplierSha256'])
            self.assertEqual(str(self.jar), cls['supplierCanonicalPath'])
            expected = ('newly-measured-under-reviewed-supplier-pin' if cls['name'] in GATE.SUPPLEMENTAL_CLASSES
                        else 'previously-reviewed-raw-hash')
            self.assertEqual(expected, cls['classHashStatus'])
        self.assertFalse(list(self.output.rglob('*.jar')))
        self.assertFalse(list(self.output.rglob('*.class')))

    def test_read_only_revalidation_does_not_disassemble_or_write_any_evidence(self):
        receipt = self.run_gate()
        before = {p: (p.read_bytes(), p.stat().st_mtime_ns) for p in self.root.rglob('*') if p.is_file()}
        with patch.object(CAPTURE, 'disassemble', side_effect=AssertionError('read-only must not run javap')):
            self.assertEqual(receipt, GATE.verify_receipt(self.manifest_path, self.output / 'gate.json'))
        self.assertEqual(before, {p: (p.read_bytes(), p.stat().st_mtime_ns)
                                 for p in self.root.rglob('*') if p.is_file()})

    def test_existing_bundle_cannot_be_replaced(self):
        receipt = self.run_gate()
        with self.assertRaises(FileExistsError):
            self.run_gate()
        self.assertEqual(receipt, json.loads((self.output / 'gate.json').read_text()))

    def test_missing_or_malformed_current_source_blocks_before_capture(self):
        for value in ('', 'unreviewed', 'A' * 40, 'a' * 41, 'a' * 40 + '\nextra'):
            with self.subTest(value=value):
                (self.root / 'source-commit.txt').write_text(value)
                with self.assertRaises(ValueError):
                    self.run_gate(lambda *args: self.fail('Must not disassemble without source identity'))
                self.assertFalse(self.output.exists())
        (self.root / 'source-commit.txt').unlink()
        with self.assertRaises(FileNotFoundError):
            self.run_gate()

    def test_changed_supplier_with_a_newly_matching_manifest_hash_is_unreviewed(self):
        self.jar.write_bytes(self.jar.read_bytes() + b'unreviewed changed jar')
        self.manifest['entries'] = [self.entry(self.jar)]
        self.save_manifest()
        with self.assertRaisesRegex(ValueError, 'unreviewed'):
            self.run_gate(lambda *args: self.fail('Reject unreviewed supplier before disassembly'))
        self.expect_no_receipt()

    def test_unreviewed_raw_class_does_not_pass_a_newly_computed_hash(self):
        report = self.capture()
        report['classes'][0]['classSha256'] = '0' * 64
        with self.assertRaisesRegex(ValueError, 'Raw class differs'):
            GATE.validate_capture(report, self.output, self.pins)

    def test_changed_whole_class_control_flow_with_matching_new_text_hash_fails(self):
        def changed(jar, name):
            return self.disassemble(jar, name).replace('0: return', '0: goto 0')
        with self.assertRaisesRegex(ValueError, 'reviewed control flow'):
            self.run_gate(changed)
        self.expect_no_receipt()

    def test_signature_is_checked_even_when_a_synthetic_review_text_hash_matches(self):
        report = self.capture()
        self.pins['reviewedMethods'][0]['descriptor'] = '(I)V'
        with self.assertRaisesRegex(ValueError, 'declaration/descriptor changed'):
            GATE.validate_capture(report, self.output, self.pins)

    def test_duplicate_reviewed_method_or_missing_contract_is_rejected(self):
        report = self.capture()
        self.pins['reviewedMethods'].append(copy.deepcopy(self.pins['reviewedMethods'][0]))
        with self.assertRaisesRegex(ValueError, 'Ambiguous'):
            GATE.validate_capture(report, self.output, self.pins)
        self.pins['reviewedMethods'] = []
        with self.assertRaisesRegex(ValueError, 'method contract'):
            GATE.validate_capture(report, self.output, self.pins)

    def test_supplemental_text_is_pinned_without_inventing_an_old_raw_hash(self):
        report = self.capture()
        name = sorted(GATE.SUPPLEMENTAL_CLASSES)[0]
        record = next(c for c in report['classes'] if c['name'] == name)
        file = self.output / record['disassemblyFile']
        file.write_text(file.read_text().replace('0: return', '0: goto 0'))
        record['disassemblySha256'] = CAPTURE.file_hash(file)
        with self.assertRaisesRegex(ValueError, 'reviewed control flow'):
            GATE.validate_capture(report, self.output, self.pins)
        self.assertNotIn('classSha256', next(c for c in self.pins['classes'] if c['name'] == name))

    def test_missing_historical_raw_hash_is_allowed_only_for_explicit_supplemental_roots(self):
        report = self.capture()
        cls = next(c for c in self.pins['classes'] if c['name'] not in GATE.SUPPLEMENTAL_CLASSES)
        del cls['classSha256']
        cls['rawClassHashStatus'] = 'newly measured'
        cls['evidence'] = 'native38159579852/artifact'
        with self.assertRaisesRegex(ValueError, 'no independent raw/supplier approval'):
            GATE.validate_capture(report, self.output, self.pins)

    def test_unreviewed_class_closure_is_rejected_before_disassembly(self):
        self.pins['classes'].pop()
        with self.assertRaisesRegex(ValueError, 'class closure'):
            self.run_gate(lambda *args: self.fail('Reject closure before disassembly'))
        self.assertFalse(self.output.exists())

    def test_read_only_missing_or_nonobject_capture_cannot_reuse_a_receipt(self):
        self.run_gate()
        path = self.output / 'api-capture/capture.json'
        path.write_text('null')
        with self.assertRaisesRegex(ValueError, 'capture identity'):
            GATE.verify_receipt(self.manifest_path, self.output / 'gate.json')
        path.unlink()
        with self.assertRaises(FileNotFoundError):
            GATE.verify_receipt(self.manifest_path, self.output / 'gate.json')

    def test_unknown_nested_class_and_missing_reviewed_class_fail(self):
        report = self.capture()
        changed = copy.deepcopy(report)
        changed['classes'].append(dict(report['classes'][0], name='net.minecraft.world.entity.Entity$Unreviewed'))
        with self.assertRaisesRegex(ValueError, 'class closure'):
            GATE.validate_capture(changed, self.output, self.pins)
        changed = copy.deepcopy(report)
        changed['classes'].pop()
        with self.assertRaisesRegex(ValueError, 'class closure'):
            GATE.validate_capture(changed, self.output, self.pins)

    def test_duplicate_class_and_supplier_records_are_not_first_match_wins(self):
        report = self.capture()
        for field in ('classes', 'artifacts'):
            changed = copy.deepcopy(report)
            changed[field].append(copy.deepcopy(changed[field][0]))
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, 'Ambiguous'):
                GATE.validate_capture(changed, self.output, self.pins)

    def test_wrong_supplier_coordinates_or_manifest_fail(self):
        report = self.capture()
        for field in ('resolvedCoordinates', 'jarManifest', 'fileName'):
            changed = copy.deepcopy(report)
            changed['artifacts'][0][field] = ['test:wrong:1'] if field == 'resolvedCoordinates' else 'wrong'
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, 'supplier metadata'):
                GATE.validate_capture(changed, self.output, self.pins)

    def test_disassembly_path_escape_and_symlink_fail(self):
        report = self.capture()
        record = report['classes'][0]
        saved = record['disassemblyFile']
        record['disassemblyFile'] = '../outside.txt'
        with self.assertRaisesRegex(ValueError, 'disassembly path'):
            GATE.validate_capture(report, self.output, self.pins)
        record['disassemblyFile'] = saved
        path = self.output / saved
        other = self.root / 'outside.txt'
        other.write_bytes(path.read_bytes()); path.unlink(); path.symlink_to(other)
        with self.assertRaisesRegex(ValueError, 'canonical regular'):
            GATE.validate_capture(report, self.output, self.pins)

    def test_capture_text_tampering_fails_its_receipted_hash(self):
        report = self.capture()
        (self.output / report['classes'][0]['disassemblyFile']).write_text('changed')
        with self.assertRaisesRegex(ValueError, 'disassembly changed'):
            GATE.validate_capture(report, self.output, self.pins)

    def test_source_changes_during_capture_fail_closed(self):
        def changed(jar, name):
            (self.root / 'source-tree.txt').write_text('c' * 40 + '\n')
            return self.disassemble(jar, name)
        with self.assertRaisesRegex(ValueError, 'source identity changed'):
            self.run_gate(changed)
        self.expect_no_receipt()

    def test_late_directory_shadow_is_checked_before_a_receipt(self):
        directory = self.root / 'classes'; directory.mkdir()
        self.manifest['entries'].append(dict(path=str(directory), kind='directory', coordinates=[]))
        self.save_manifest()
        def changed(jar, name):
            if name == sorted(self.names)[-1]:
                shadow = directory / (self.names[0].replace('.', '/') + '.class')
                shadow.parent.mkdir(parents=True); shadow.write_bytes(b'shadow')
            return self.disassemble(jar, name)
        with self.assertRaisesRegex(ValueError, 'directory supplies'):
            self.run_gate(changed)
        self.expect_no_receipt()

    def test_late_absent_entry_appearance_is_checked_before_a_receipt(self):
        absent = self.root / 'appeared.jar'
        self.manifest['entries'].append(dict(path=str(absent), kind='absent', coordinates=[]))
        self.save_manifest()
        def changed(jar, name):
            absent.write_bytes(b'new runtime entry')
            return self.disassemble(jar, name)
        with self.assertRaisesRegex(ValueError, 'absent runtime entry appeared'):
            self.run_gate(changed)
        self.expect_no_receipt()

    def test_late_disassembly_mutation_during_final_inventory_scan_fails(self):
        inspect = CAPTURE.inspect_runtime
        calls = 0
        def changed(path):
            nonlocal calls
            calls += 1
            result = inspect(path)
            if calls == 3:
                text = next((self.output / 'api-capture/bytecode').glob('*.javap.txt'))
                text.write_text('late change')
            return result
        with patch.object(CAPTURE, 'inspect_runtime', side_effect=changed), self.assertRaisesRegex(ValueError, 'disassembly changed'):
            self.run_gate()
        self.expect_no_receipt()

    def test_late_capture_receipt_mutation_during_final_inventory_scan_fails(self):
        inspect = CAPTURE.inspect_runtime
        calls = 0
        def changed(path):
            nonlocal calls
            calls += 1
            result = inspect(path)
            if calls == 3:
                (self.output / 'api-capture/capture.json').write_text('{}')
            return result
        with patch.object(CAPTURE, 'inspect_runtime', side_effect=changed), self.assertRaisesRegex(ValueError, 'Capture receipt changed'):
            self.run_gate()
        self.expect_no_receipt()

    def test_read_only_revalidation_rejects_changed_source_or_runtime_or_disassembly(self):
        self.run_gate()
        gate_path = self.output / 'gate.json'
        path = self.root / 'source-tree.txt'; original = path.read_bytes()
        path.write_text('c' * 40 + '\n')
        with self.assertRaisesRegex(ValueError, 'does not match'):
            GATE.verify_receipt(self.manifest_path, gate_path)
        path.write_bytes(original)
        original = self.jar.read_bytes(); self.jar.write_bytes(original + b'changed')
        with self.assertRaisesRegex(ValueError, 'changed after preparation'):
            GATE.verify_receipt(self.manifest_path, gate_path)
        self.jar.write_bytes(original)
        report = json.loads((self.output / 'api-capture/capture.json').read_text())
        (self.output / 'api-capture' / report['classes'][0]['disassemblyFile']).write_text('tampered')
        with self.assertRaisesRegex(ValueError, 'disassembly changed'):
            GATE.verify_receipt(self.manifest_path, gate_path)

    def test_read_only_revalidation_rejects_boolean_only_forged_or_truncated_receipt(self):
        self.run_gate()
        path = self.output / 'gate.json'
        original = json.loads(path.read_text())
        mutations = [dict(semanticApiGatePassed=True, gameplayAccepted=False),
                     dict(original, reviewedGateContractSha256='9' * 64),
                     dict(original, classes=original['classes'][:-1]),
                     dict(original, sourceCommit=GATE.SOURCE_COMMIT),
                     dict(original, semanticApiGatePassed=1), dict(original, gameplayAccepted=0),
                     dict(original, formatVersion=True), dict(original, reviewedMethodCount=1.0)]
        for receipt in mutations:
            path.write_text(json.dumps(receipt))
            with self.subTest(keys=receipt.keys()), self.assertRaisesRegex(ValueError, 'does not match'):
                GATE.verify_receipt(self.manifest_path, path)

    def test_read_only_revalidation_rejects_duplicate_json_and_missing_bundle(self):
        self.run_gate()
        path = self.output / 'gate.json'
        path.write_text('{"semanticApiGatePassed":true,"semanticApiGatePassed":true}')
        with self.assertRaisesRegex(ValueError, 'Duplicate JSON'):
            GATE.verify_receipt(self.manifest_path, path)
        path.unlink()
        with self.assertRaises(FileNotFoundError):
            GATE.verify_receipt(self.manifest_path, path)

    def test_runtime_manifest_from_another_path_cannot_replay_the_receipt(self):
        self.run_gate()
        copied = self.root / 'different-runtime.json'; copied.write_bytes(self.manifest_path.read_bytes())
        with self.assertRaisesRegex(ValueError, 'does not match'):
            GATE.verify_receipt(copied, self.output / 'gate.json')

    def test_receipt_bound_cannot_be_bypassed(self):
        with patch.object(GATE, 'MAX_RECEIPT_BYTES', 20), self.assertRaisesRegex(ValueError, 'bounded output'):
            self.run_gate()
        self.expect_no_receipt()


class CheckedInReviewTest(unittest.TestCase):
    def test_exact_independent_review_is_preserved_and_contract_is_explicit(self):
        contract = GATE.load_reviewed_contract()
        pins = contract['pins']
        self.assertEqual(60, len(pins['classes']))
        self.assertEqual(74, len(pins['reviewedMethods']))
        self.assertEqual(3, len(pins['artifacts']))
        self.assertEqual('214c5fa633fdfae31f510016ade492ffab239ad8a1752730089979edb1b29aa5',
                         contract['reviewedGateContractSha256'])
        review = CAPTURE.load_unique(GATE.REVIEW_PATH)
        for requirement in ('before source removal', 'UNLOADED_TO_CHUNK', 'CHANGED_DIMENSION',
                            'ticking=true', 'partial failure', 'loadedFromDisk', '600-second'):
            self.assertIn(requirement.lower(), review['reviewText'].lower())
        self.assertEqual(GATE.SUPPLEMENTAL_CLASSES, {c['name'] for c in pins['classes'] if 'classSha256' not in c})

    def test_changed_pin_file_is_not_approved_by_recomputing_a_capture_hash(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'pins.json'; path.write_text('{"gameplayAccepted":false}')
            with patch.object(GATE, 'PIN_PATH', path), self.assertRaisesRegex(ValueError, 'independent review'):
                GATE.load_reviewed_contract()

    def test_changed_semantic_contract_fails_even_with_recomputed_metadata_hash(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'review.json'
            review = CAPTURE.load_unique(GATE.REVIEW_PATH)
            review['reviewText'] += '\nAllow anything.'
            review['reviewTextSha256'] = sha(review['reviewText'].encode())
            path.write_text(json.dumps(review))
            with patch.object(GATE, 'REVIEW_PATH', path), self.assertRaisesRegex(ValueError, 'contract changed'):
                GATE.load_reviewed_contract()

    def test_cli_never_accepts_capture_receipt_or_pin_override_as_approval(self):
        result = subprocess.run([sys.executable, str(ROOT / 'scripts/verify-native-helper-api.py'), '--help'],
                                text=True, capture_output=True, check=True)
        self.assertIn('--verify-receipt', result.stdout)
        self.assertNotIn('--pins', result.stdout)
        for args in ([], ['missing.json'], ['missing.json', 'out', '--verify-receipt', 'gate.json']):
            with self.subTest(args=args):
                result = subprocess.run([sys.executable, str(ROOT / 'scripts/verify-native-helper-api.py'), *args],
                                        text=True, capture_output=True)
                self.assertEqual(2, result.returncode)


if __name__ == '__main__':
    unittest.main()
