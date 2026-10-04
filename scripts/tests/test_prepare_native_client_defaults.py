"""QA setup safety contracts; stdlib only, with no Minecraft or Gradle launch."""
import hashlib
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import tomllib
import unittest


REPO = Path(__file__).resolve().parents[2]
SCRIPT = REPO / 'scripts' / 'prepare-native-client-defaults.py'
SPEC = importlib.util.spec_from_file_location('prepare_defaults', SCRIPT)
setup = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(setup)


def leaves(values, prefix=''):
    result = {}
    for name, value in values.items():
        key = prefix + name
        if isinstance(value, dict):
            result.update(leaves(value, key + '.'))
        else:
            result[key] = value
    return result


def java_default(value):
    if isinstance(value, bool):
        return str(value).lower()
    if isinstance(value, list):
        return '[' + ', '.join(value) + ']'
    return str(value)


class NativeClientDefaultsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / 'checkout'
        self.root.mkdir()
        shutil.copyfile(REPO / 'build.gradle', self.root / 'build.gradle')
        shutil.copytree(REPO / 'scripts' / 'fixtures', self.root / 'scripts' / 'fixtures')
        self.game = self.root / setup.GAME_DIRECTORIES['baseline']

    def prepare(self, mode='baseline', version=setup.VERSION):
        return setup.prepare(self.root, mode, version)

    def test_captured_bytes_match_all_native_declared_defaults(self):
        fixture = self.root / 'scripts' / 'fixtures' / f'smallships-{setup.VERSION}'
        provenance = json.loads((fixture / 'provenance.json').read_text())
        self.assertEqual(provenance['version'], setup.VERSION)
        self.assertEqual(provenance['coordinate'], setup.COORDINATE)
        self.assertFalse(provenance['gameplayValuesChanged'])
        for name, count, schema in [('smallships-client.toml', 7, 2),
                                    ('smallships-common.toml', 49, 5)]:
            with self.subTest(name=name):
                data = (fixture / name).read_bytes()
                expected = provenance['configs'][name]
                self.assertEqual(hashlib.sha256(data).hexdigest(), setup.CONFIG_SHA256[name])
                self.assertEqual(expected['sha256'], setup.CONFIG_SHA256[name])
                parsed = tomllib.loads(data.decode())
                self.assertEqual(parsed['schematicVersion'], schema)
                flattened = leaves(parsed)
                self.assertEqual(len(flattened), count)
                self.assertEqual({k: java_default(v) for k, v in flattened.items()},
                                 expected['declaredDefaultStrings'])

    def test_each_known_mode_creates_exact_files_and_read_only_retry(self):
        for mode, relative in setup.GAME_DIRECTORIES.items():
            with self.subTest(mode=mode):
                directory = self.prepare(mode)
                self.assertEqual(directory, self.root / relative)
                self.assertEqual({p.name for p in directory.iterdir()}, {'config', setup.MARKER})
                before = {p.relative_to(directory): (p.read_bytes(), p.stat().st_mtime_ns)
                          for p in directory.rglob('*') if p.is_file()}
                self.assertEqual(self.prepare(mode), directory)
                after = {p.relative_to(directory): (p.read_bytes(), p.stat().st_mtime_ns)
                         for p in directory.rglob('*') if p.is_file()}
                self.assertEqual(before, after)

    def test_staged_handoff_uses_only_its_explicit_isolated_directory(self):
        directory = self.prepare('staged-handoff')
        self.assertEqual(directory, self.root / 'build/native-handoff-qa/client')
        self.assertEqual({p.name for p in (directory / 'config').iterdir()},
                         {'smallships-client.toml', 'smallships-common.toml'})
        marker = json.loads((directory / setup.MARKER).read_text())
        self.assertEqual(marker['qaMode'], 'staged-handoff')
        self.assertEqual(marker['gameDirectory'], 'build/native-handoff-qa/client')
        self.assertFalse(marker['smallshipsGameplayValuesChanged'])
        self.assertFalse(self.game.exists())

    def test_hud_uses_only_its_explicit_isolated_directory(self):
        directory = self.prepare('hud')
        self.assertEqual(directory, self.root / 'build/native-hud-qa/client')
        marker = json.loads((directory / setup.MARKER).read_text())
        self.assertEqual(marker['qaMode'], 'hud')
        self.assertEqual(marker['gameDirectory'], 'build/native-hud-qa/client')
        self.assertFalse(marker['smallshipsGameplayValuesChanged'])
        self.assertFalse(self.game.exists())
        (directory / 'saves').mkdir()
        with self.assertRaisesRegex(ValueError, 'not a fresh'):
            self.prepare('hud')

    def test_unknown_version_or_mode_makes_no_directory(self):
        for mode, version in [('baseline', '2.0.0'), ('baseline', ''),
                              ('../.minecraft', setup.VERSION), ('server', setup.VERSION)]:
            with self.subTest(mode=mode, version=version), self.assertRaises(ValueError):
                self.prepare(mode, version)
        self.assertFalse((self.root / 'build').exists())

    def test_unknown_artifact_pin_refused_before_writes(self):
        path = self.root / 'build.gradle'
        path.write_text(path.read_text().replace(setup.COORDINATE, 'curse.maven:small-ships-450659:9999999'))
        with self.assertRaisesRegex(ValueError, 'artifact differs'):
            self.prepare()
        self.assertFalse((self.root / 'build').exists())

    def test_changed_fixture_refused_before_writes(self):
        path = self.root / 'scripts' / 'fixtures' / f'smallships-{setup.VERSION}' / 'smallships-common.toml'
        path.write_bytes(path.read_bytes().replace(b'schematicVersion = 5', b'schematicVersion = 4'))
        with self.assertRaisesRegex(ValueError, 'Captured default bytes differ'):
            self.prepare()
        self.assertFalse((self.root / 'build').exists())

    def test_existing_unmarked_empty_directory_refused(self):
        self.game.mkdir(parents=True)
        with self.assertRaisesRegex(ValueError, 'not a fresh'):
            self.prepare()
        self.assertEqual(list(self.game.iterdir()), [])

    def test_identical_configs_without_helper_marker_refused(self):
        self.prepare()
        (self.game / setup.MARKER).unlink()
        with self.assertRaisesRegex(ValueError, 'not a fresh'):
            self.prepare()
        self.assertFalse((self.game / setup.MARKER).exists())

    def test_different_existing_config_is_never_overwritten(self):
        self.prepare()
        path = self.game / 'config' / 'smallships-client.toml'
        changed = path.read_bytes().replace(b'shipModSpeedUnit = 0', b'shipModSpeedUnit = 3')
        path.write_bytes(changed)
        with self.assertRaisesRegex(ValueError, 'Existing QA setup differs'):
            self.prepare()
        self.assertEqual(path.read_bytes(), changed)

    def test_unexpected_or_missing_config_refused(self):
        self.prepare()
        extra = self.game / 'config' / 'user-custom.toml'
        extra.write_text('keep = true\n')
        with self.assertRaisesRegex(ValueError, 'Unexpected or missing'):
            self.prepare()
        self.assertEqual(extra.read_text(), 'keep = true\n')
        extra.unlink()
        missing = self.game / 'config' / 'smallships-common.toml'
        missing.unlink()
        with self.assertRaisesRegex(ValueError, 'Unexpected or missing'):
            self.prepare()
        self.assertFalse(missing.exists())

    def test_already_started_client_refused_even_without_saves(self):
        self.prepare()
        (self.game / 'logs').mkdir()
        with self.assertRaisesRegex(ValueError, 'not a fresh'):
            self.prepare()

    def test_changed_provenance_marker_refused(self):
        self.prepare()
        path = self.game / setup.MARKER
        path.write_text('{}\n')
        with self.assertRaisesRegex(ValueError, 'Existing QA setup differs'):
            self.prepare()
        self.assertEqual(path.read_text(), '{}\n')

    def test_symlinked_ancestor_or_game_dir_refused(self):
        outside = self.root.parent / 'user-game'
        outside.mkdir()
        for relative in ['build', 'build/native-qa', 'build/native-qa/client']:
            with self.subTest(relative=relative):
                link = self.root / relative
                link.parent.mkdir(parents=True, exist_ok=True)
                link.symlink_to(outside, target_is_directory=True)
                with self.assertRaisesRegex(ValueError, 'Refusing symlink'):
                    self.prepare()
                self.assertEqual(list(outside.iterdir()), [])
                link.unlink()

    def test_symlinked_config_directory_or_files_refused(self):
        self.prepare()
        for relative in ['config', 'config/smallships-client.toml', setup.MARKER]:
            with self.subTest(relative=relative):
                path = self.game / relative
                # Keep the directory membership unchanged to reach link checks.
                moved = self.root.parent / 'saved'
                path.rename(moved)
                path.symlink_to(moved, target_is_directory=moved.is_dir())
                with self.assertRaisesRegex(ValueError, 'Refusing symlink'):
                    self.prepare()
                path.unlink()
                moved.rename(path)

    def test_symlinked_fixture_refused_before_writes(self):
        path = self.root / 'scripts' / 'fixtures' / f'smallships-{setup.VERSION}' / 'smallships-common.toml'
        saved = path.with_suffix('.saved')
        path.rename(saved)
        path.symlink_to(saved)
        with self.assertRaisesRegex(ValueError, 'Refusing symlink'):
            self.prepare()
        self.assertFalse((self.root / 'build').exists())

    def test_existing_file_instead_of_game_dir_refused(self):
        self.game.parent.mkdir(parents=True)
        self.game.write_text('keep this file\n')
        with self.assertRaisesRegex(ValueError, 'not a fresh'):
            self.prepare()
        self.assertEqual(self.game.read_text(), 'keep this file\n')

    def test_cli_has_no_arbitrary_destination_or_force_option(self):
        for option in ['--game-dir', '--force']:
            result = subprocess.run([sys.executable, str(SCRIPT), '--mode', 'baseline',
                                     '--smallships-version', setup.VERSION, option],
                                    capture_output=True, text=True, cwd=self.root)
            self.assertEqual(result.returncode, 2, result.stderr)
            self.assertIn('unrecognized arguments', result.stderr)


if __name__ == '__main__':
    unittest.main()
