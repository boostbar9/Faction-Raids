import importlib.util
from pathlib import Path
import shutil
import tempfile
import unittest

SOURCE = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('ships_defaults', SOURCE / 'prepare-native-ships-defaults.py')
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class NativeShipDefaultsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / 'gradle').mkdir()
        shutil.copy(SOURCE.parent / 'gradle/native-smallships-qa.gradle', self.root / 'gradle')
        shutil.copytree(SOURCE / 'fixtures/smallships-2.0.0', self.root / 'scripts/fixtures/smallships-2.0.0')
        shutil.copytree(SOURCE / 'fixtures/smallships-2.0.0-b1.4', self.root / 'scripts/fixtures/smallships-2.0.0-b1.4')

    def test_server_defaults_only_create_fresh_qa_files(self):
        directory = MODULE.prepare(self.root, 'final')
        self.assertTrue((directory / 'defaultconfigs/smallships-server.toml').is_file())
        self.assertFalse((directory / 'worlds').exists())
        self.assertFalse((directory / 'eula.txt').exists())
        with self.assertRaises(ValueError):
            MODULE.prepare(self.root, 'final')

    def test_client_defaults_and_server_defaults_are_exact(self):
        directory = MODULE.prepare(self.root, 'final-client')
        source = self.root / 'scripts/fixtures/smallships-2.0.0'
        self.assertEqual((source / 'smallships-client.toml').read_bytes(), (directory / 'config/smallships-client.toml').read_bytes())
        self.assertEqual((source / 'smallships-server.toml').read_bytes(), (directory / 'defaultconfigs/smallships-server.toml').read_bytes())

    def test_changed_defaults_are_rejected_before_creating_game_directory(self):
        (self.root / 'scripts/fixtures/smallships-2.0.0/smallships-server.toml').write_text('changed')
        with self.assertRaises(ValueError):
            MODULE.prepare(self.root, 'final')
        self.assertFalse((self.root / MODULE.DIRECTORIES['final']).exists())

    def test_symlink_and_unreviewed_release_are_rejected(self):
        target = self.root / 'elsewhere'; target.mkdir()
        (self.root / 'build').symlink_to(target, target_is_directory=True)
        with self.assertRaises(ValueError):
            MODULE.prepare(self.root, 'final')
        self.assertFalse(list(target.iterdir()))
        with self.assertRaises(ValueError):
            MODULE.prepare(self.root, 'unreviewed')

    def test_legacy_server_uses_only_its_own_reviewed_common_defaults(self):
        directory = MODULE.prepare(self.root, 'legacy')
        expected = self.root / 'scripts/fixtures/smallships-2.0.0-b1.4/smallships-common.toml'
        self.assertEqual(expected.read_bytes(), (directory / 'config/smallships-common.toml').read_bytes())
        self.assertFalse((directory / 'defaultconfigs').exists())
        self.assertFalse((directory / 'config/smallships-client.toml').exists())

    def test_changed_artifact_pin_is_rejected(self):
        (self.root / 'gradle/native-smallships-qa.gradle').write_text('different artifact')
        with self.assertRaises(ValueError):
            MODULE.prepare(self.root, 'final')
        self.assertFalse((self.root / 'build').exists())


if __name__ == '__main__':
    unittest.main()
