"""Synthetic capture-boundary tests; no native API or gameplay approval is inferred."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('helper_api_capture', ROOT / 'scripts/capture-native-helper-api.py')
CAPTURE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CAPTURE)


class HelperApiCaptureTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name).resolve()
        self.jar = self.root / 'synthetic-runtime.jar'
        self.names = list(CAPTURE.ROOT_CLASSES) + ['net.minecraft.world.entity.Entity$RemovalReason',
                                                'net.minecraftforge.common.world.ForgeChunkManager$SyntheticInner']
        self.write_jar(self.jar, self.names)
        self.manifest = dict(formatVersion=1, **CAPTURE.PINNED,
                             entries=[self.entry(self.jar)])
        self.manifest_path = self.root / 'resolved-runtime.json'
        self.output = self.root / 'capture'

    def write_jar(self, path, names):
        with zipfile.ZipFile(path, 'w') as jar:
            jar.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\nSynthetic: true\n')
            for name in names:
                jar.writestr(name.replace('.', '/') + '.class', b'not executable: synthetic class ' + name.encode())

    def entry(self, path):
        return dict(path=str(path), kind='file', coordinates=['test:synthetic:1'], sha256=CAPTURE.file_hash(path))

    def capture(self, runner=None):
        self.manifest_path.write_text(json.dumps(self.manifest))
        return CAPTURE.capture(self.manifest_path, self.output, runner or
                               (lambda jar, name: 'public class ' + name + ' {\n  descriptor: ()V\n}\n'))

    def test_capture_preserves_exact_supplier_class_and_disassembly_hashes_without_approval(self):
        report = self.capture()
        self.assertEqual('captured-unreviewed', report['status'])
        self.assertIs(False, report['gameplayAuthorized'])
        self.assertTrue(report['noVendorJarsUploaded'])
        self.assertEqual(set(self.names), {c['name'] for c in report['classes']})
        self.assertEqual(CAPTURE.file_hash(self.jar), report['artifacts'][0]['sha256'])
        self.assertEqual(['test:synthetic:1'], report['artifacts'][0]['resolvedCoordinates'])
        for record in report['classes']:
            self.assertEqual(str(self.jar), record['supplier'])
            expected = hashlib.sha256(b'not executable: synthetic class ' + record['name'].encode()).hexdigest()
            self.assertEqual(expected, record['classSha256'])
            self.assertEqual(CAPTURE.file_hash(self.output / record['disassemblyFile']), record['disassemblySha256'])
        self.assertFalse(list(self.output.rglob('*.jar')))
        self.assertFalse(list(self.output.rglob('*.class')))

    def test_changed_prepared_artifact_is_rejected_before_disassembly(self):
        self.jar.write_bytes(self.jar.read_bytes() + b'changed')
        with self.assertRaisesRegex(ValueError, 'changed after preparation'):
            self.capture(lambda *args: self.fail('Must not disassemble changed artifacts'))

    def test_duplicate_class_supplier_fails_instead_of_choosing_first(self):
        duplicate = self.root / 'duplicate.jar'
        self.write_jar(duplicate, [CAPTURE.ROOT_CLASSES[0]])
        self.manifest['entries'].append(self.entry(duplicate))
        with self.assertRaisesRegex(ValueError, 'Ambiguous required class'):
            self.capture()

    def test_multi_release_root_or_nested_variant_in_any_supplier_fails(self):
        for name in [CAPTURE.ROOT_CLASSES[0], 'net.minecraft.world.entity.Entity$RemovalReason']:
            for separate in [False, True]:
                with self.subTest(name=name, separate=separate):
                    path = self.root / 'versioned.jar' if separate else self.jar
                    self.write_jar(self.jar, self.names)
                    if separate:
                        self.write_jar(path, [])
                    with zipfile.ZipFile(path, 'a') as jar:
                        jar.writestr('META-INF/versions/17/' + name.replace('.', '/') + '.class', b'versioned')
                    self.manifest['entries'] = [self.entry(self.jar)]
                    if separate:
                        self.manifest['entries'].append(self.entry(path))
                    with self.assertRaisesRegex(ValueError, 'Multi-release variant'):
                        self.capture(lambda *args: self.fail('Must reject before disassembly'))

    def test_missing_root_or_removal_semantics_class_fails(self):
        for absent in [CAPTURE.ROOT_CLASSES[0], 'net.minecraft.world.entity.Entity$RemovalReason']:
            self.write_jar(self.jar, [n for n in self.names if n != absent])
            self.manifest['entries'] = [self.entry(self.jar)]
            with self.subTest(absent=absent), self.assertRaisesRegex(ValueError, 'lacks a required'):
                self.capture()

    def test_directory_shadow_of_root_or_nested_class_fails(self):
        directory = self.root / 'classes'; directory.mkdir()
        entry = dict(path=str(directory), kind='directory', coordinates=[])
        self.manifest['entries'].insert(0, entry)
        file = directory / 'net/minecraft/world/entity/Entity$RemovalReason.class'
        file.parent.mkdir(parents=True); file.write_bytes(b'shadow')
        with self.assertRaisesRegex(ValueError, 'directory supplies'):
            self.capture()

    def test_version_and_mapping_changes_fail(self):
        for key in CAPTURE.PINNED:
            saved = self.manifest[key]; self.manifest[key] = 'unexpected'
            with self.subTest(key=key), self.assertRaisesRegex(ValueError, 'version'):
                self.capture()
            self.manifest[key] = saved

    def test_partial_disassembly_never_produces_capture_receipt(self):
        with self.assertRaisesRegex(ValueError, 'Incomplete disassembly'):
            self.capture(lambda jar, name: name)
        self.assertFalse((self.output / 'capture.json').exists())

    def test_changed_artifact_during_disassembly_and_existing_output_fail(self):
        def mutate(jar, name):
            if name == sorted(self.names)[-1]:
                self.jar.write_bytes(self.jar.read_bytes() + b'changed')
            return name + '\n descriptor: ()V\n'
        with self.assertRaisesRegex(ValueError, 'changed during capture'):
            self.capture(mutate)
        self.assertFalse((self.output / 'capture.json').exists())
        self.manifest['entries'] = [self.entry(self.jar)]
        with self.assertRaises(FileExistsError):
            self.capture()

    def test_duplicate_json_keys_are_rejected(self):
        self.manifest_path.write_text('{"entries": [], "entries": []}')
        with self.assertRaisesRegex(ValueError, 'Duplicate JSON'):
            CAPTURE.load_unique(self.manifest_path)

    def test_changed_manifest_during_disassembly_never_labels_earlier_bytes_with_new_hash(self):
        def mutate(jar, name):
            self.manifest_path.write_text(json.dumps(dict(self.manifest, purpose='changed during capture')))
            return name + '\n descriptor: ()V\n'
        with self.assertRaisesRegex(ValueError, 'manifest changed during capture'):
            self.capture(mutate)
        self.assertFalse((self.output / 'capture.json').exists())

    def test_unexpected_class_size_and_runtime_path_alias_fail(self):
        with patch.object(CAPTURE, 'MAX_CLASS_BYTES', 1), self.assertRaisesRegex(ValueError, 'class size'):
            self.capture()
        self.manifest['entries'].append(copy.deepcopy(self.manifest['entries'][0]))
        with self.assertRaisesRegex(ValueError, 'unique and canonical'):
            self.capture()

    def test_prepare_only_workflow_never_launches_gameplay_and_gradle_blocks_accidental_launch(self):
        workflow = (ROOT / '.github/workflows/native-active-helper-api-qa.yml').read_text()
        gradle = (ROOT / 'gradle/native-active-helper-api-qa.gradle').read_text()
        self.assertIn('workflow_dispatch:', workflow)
        self.assertIn('pull_request:', workflow)
        self.assertIn('branches: [release/4.53.0-alpha.3]', workflow)
        self.assertIn('types: [opened, reopened, synchronize, labeled]', workflow)
        self.assertIn('github.event.pull_request.number == 295', workflow)
        self.assertIn('github.event.pull_request.head.repo.full_name == github.repository', workflow)
        self.assertIn("contains(github.event.pull_request.labels.*.name, 'native-active-helper-api-qa')", workflow)
        self.assertNotIn('pull_request_target', workflow)
        self.assertIn('contents: read', workflow)
        self.assertIn('persist-credentials: false', workflow)
        self.assertIn('cache-read-only: true', workflow)
        self.assertLess(workflow.index('prepareNativeActiveHelperApiEvidence'), workflow.index('scripts/capture-native-helper-api.py'))
        self.assertNotIn(' runClient', workflow)
        self.assertIn("dependsOn 'prepareNativeQaClient'", gradle)
        self.assertIn('clientRun.minecraftArtifacts, clientRun.runtimeClasspathArtifacts', gradle)
        self.assertIn('clientRun.runConfig.get().allSources.collect { it.runtimeClasspath }', gradle)
        self.assertIn("tasks.named('runClient').configure", gradle)
        self.assertIn("doFirst { throw new GradleException('API capture is prepare-only", gradle)


if __name__ == '__main__':
    unittest.main()
