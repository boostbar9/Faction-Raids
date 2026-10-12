"""Execute the actual JDK-only resolver from the QA source; no Forge/vendor launch."""
import importlib.util
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/nativeQa/java/com/devfarinsky/siegeoverhaul/nativecompat/NativeActiveHelperApiParity.java'


def resolver_source():
    text = SOURCE.read_text()
    start = text.index('    static final class ProviderLocation {')
    end = text.index('\n    private static String hash(Path path)', start)
    value = text[start:end]
    if not value.rstrip().endswith('}'):
        raise AssertionError('Exact dependency-free resolver boundary changed')
    return value


class ProviderLocationJavaTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.java = shutil.which('java')
        if not cls.java:
            raise RuntimeError('Java 17+ with jdk.compiler is required for actual resolver execution')
        cls.temp = tempfile.TemporaryDirectory(); cls.root = Path(cls.temp.name)
        cls.expected = cls.root / 'reviewed supplier.jar'; cls.expected.write_bytes(b'controlled resolver fixture, not vendor bytes')
        harness = cls.root / 'ProviderHarness.java'
        harness.write_text('''import java.net.*;
import java.nio.file.*;
public final class ProviderHarness {
''' + resolver_source() + '''
    public static void main(String[] args) throws Exception {
        URL location;
        if (args[0].startsWith("union:")) location = new URL(null, args[0], new URLStreamHandler() {
            @Override protected URLConnection openConnection(URL ignored) { throw new AssertionError("No union URL connection permitted"); }
        });
        else location = new URL(args[0]);
        System.out.println("CodeSource=" + location.toExternalForm());
        try { System.out.println(ProviderLocation.resolve(location, Path.of(args[1]))); }
        catch (IllegalStateException rejected) { System.err.println(rejected.getMessage()); System.exit(3); }
    }
}
''')
        # Use the already installed compiler module. No PATH shim, downloaded JDK, changed
        # environment, or Python reimplementation substitutes for this actual Java method.
        launcher = cls.root / 'CompileResolver.java'
        launcher.write_text('''import javax.tools.ToolProvider;
public final class CompileResolver {
    public static void main(String[] args) {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("jdk.compiler unavailable");
        System.exit(compiler.run(null, null, null, "--source", "17", "--target", "17", "-d", args[0], args[1]));
    }
}
''')
        compiled = subprocess.run([cls.java, str(launcher), str(cls.root), str(harness)], capture_output=True, text=True, timeout=30)
        if compiled.returncode:
            raise AssertionError('Actual source resolver did not compile for source17/target17: ' + compiled.stdout + compiled.stderr)

    @classmethod
    def tearDownClass(cls): cls.temp.cleanup()

    def run_resolver(self, url, expected=None, okay=False):
        target = self.expected if expected is None else expected
        result = subprocess.run([self.java, '-cp', str(self.root), 'ProviderHarness', url, str(target)], capture_output=True, text=True, timeout=10)
        if okay:
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(result.stdout.strip().splitlines()[-1], str(target))
        else:
            self.assertEqual(result.returncode, 3, result.stdout + result.stderr)
            self.assertIn(result.stdout.strip().splitlines()[0], result.stderr)
            self.assertIn('expected=' + str(target), result.stderr)
        return result

    def union(self, suffix='%23180%23187!/', path=None):
        return 'union:' + (self.expected if path is None else path).as_uri().removeprefix('file:') + suffix

    def test_direct_file_and_root_jar_remain_exact(self):
        self.run_resolver(self.expected.as_uri(), okay=True)
        self.run_resolver('jar:' + self.expected.as_uri() + '!/', okay=True)

    def test_single_and_nested_native_index_keys_decode_once(self):
        for suffix in ('%230!/', '%23180!/', '%23180%23187!/', '%230%231%232%233%234%235%236%237!/'):
            with self.subTest(suffix=suffix): self.run_resolver(self.union(suffix), okay=True)

    def test_legacy_root_without_slash_is_still_bounded(self):
        self.run_resolver(self.union('%23180%23187!'), okay=True)

    def test_wrong_canonical_supplier_is_never_guessed(self):
        other = self.root / 'other.jar'; other.write_bytes(self.expected.read_bytes())
        for url in (self.union(path=other), other.as_uri(), 'jar:' + other.as_uri() + '!/'):
            with self.subTest(url=url): self.run_resolver(url)

    def test_real_numeric_hash_file_makes_single_or_nested_url_ambiguous(self):
        literal = Path(str(self.expected) + '#180'); literal.write_bytes(b'another real file')
        try:
            self.assertIn('ambiguous', self.run_resolver(self.union('%23180!/')).stderr)
            self.assertIn('ambiguous', self.run_resolver(self.union()).stderr)
        finally: literal.unlink()

    def test_nested_literal_and_dangling_symlink_ambiguity_fail_closed(self):
        literal = Path(str(self.expected) + '#180#187'); literal.write_bytes(b'another literal file')
        try: self.run_resolver(self.union())
        finally: literal.unlink()
        literal = Path(str(self.expected) + '#180'); literal.symlink_to(self.root/'missing')
        try: self.run_resolver(self.union())
        finally: literal.unlink()

    def test_expected_numeric_hash_filename_is_not_a_union_base(self):
        literal = self.root/'real#180.jar'; literal.write_bytes(b'controlled literal')
        self.run_resolver(self.union('%23187!/', path=literal), expected=literal)
        self.run_resolver(literal.as_uri(), expected=literal)

    def test_bounded_indices_reject_garbage_overflow_and_extra_depth(self):
        for suffix in ('!/', '%23!/', '%23x!/', '%23-1!/', '%23001!/', '%232147483648!/', '%23180%23x!/', '%230'*9+'!/'):
            with self.subTest(suffix=suffix): self.run_resolver(self.union(suffix))

    def test_nonroot_entries_queries_fragments_and_authorities_fail(self):
        for url in (self.union()+'class.class', self.union()+'?query=1', self.union()+'#fragment',
                    self.union().replace('union:/','union://host/',1),
                    self.expected.as_uri()+'?query=1', self.expected.as_uri()+'#fragment',
                    'jar:'+self.expected.as_uri()+'!/class.class', 'jar:'+self.expected.as_uri()+'?query=1!/'):
            with self.subTest(url=url): self.run_resolver(url)

    def test_encoded_separators_traversal_and_double_decoding_fail(self):
        base = self.union()
        for url in (base.replace('/reviewed', '%2Freviewed'), base.replace('.jar','%2ejar'),
                    base.replace('%23180','%2523180'), base.replace('%23180','%5c180'),
                    base.replace('!/','%21/'), base.replace('/reviewed','/../reviewed'),
                    base.replace('%23180','#180')):
            with self.subTest(url=url): self.run_resolver(url)

    def test_expected_symlink_missing_file_and_unsupported_scheme_fail(self):
        link = self.root/'alias.jar'; link.symlink_to(self.expected)
        self.run_resolver(link.as_uri(), expected=link)
        absent = self.root/'absent.jar'; self.run_resolver(self.union(path=absent), expected=absent)
        self.run_resolver('https://example.invalid/supplier.jar')


class ProviderLocationIntegrationContracts(unittest.TestCase):
    def test_exact_hash_resource_and_new_diagnostics_remain_required(self):
        text = SOURCE.read_text()
        self.assertIn('ProviderLocation.resolve(location, Path.of(expected))',text)
        self.assertIn('codeSourceUrls.put(name, location.toExternalForm())',text)
        self.assertIn('"codeSourceUrls", codeSourceUrls',text)
        self.assertIn('hash.equals(hash(path))',text)
        self.assertIn('hash(bytes).equals(item.get("classSha256").getAsString())',text)
        self.assertIn('source.toString().equals(expected)',text)
        self.assertNotIn("path.lastIndexOf('#')",text)
        self.assertNotIn('Files.walk(',resolver_source())
        self.assertNotIn('newFileSystem(',resolver_source())


if __name__ == '__main__': unittest.main()
