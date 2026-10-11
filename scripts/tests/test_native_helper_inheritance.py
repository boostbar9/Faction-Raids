"""Execute the exact JDK-only inheritance and pinned-JAR tail checks from QA source."""
from pathlib import Path
import hashlib
import shutil
import subprocess
import tempfile
import unittest
import warnings
import zipfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/nativeQa/java/com/devfarinsky/siegeoverhaul/nativecompat/NativeActiveHelperApiParity.java'
RESOURCE = 'probe/NativeTail.class'


class NativeInheritanceJavaTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.java = shutil.which('java')
        if not cls.java: raise RuntimeError('Java with jdk.compiler required')
        cls.temp = tempfile.TemporaryDirectory(); cls.root = Path(cls.temp.name)
        text = SOURCE.read_text()
        # Compile the real methods, including their shared hash and provider checks.
        methods = text[text.index('    private static URL codeSource(Class<?> type)'):text.rfind('\n}')]
        harness = cls.root/'InheritanceHarness.java'
        harness.write_text('''import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.io.*;
public final class InheritanceHarness {
''' + methods + '''
    static class CapabilityRoot {}
    static class Entity extends CapabilityRoot {}
    static class Builder extends Entity {}
    static class Extra extends Builder {}
    static class ResourceLoader extends SecureClassLoader {
        final URL source; final byte[] bytes; final String mode;
        ResourceLoader(URL source, byte[] bytes, String mode) { super(null); this.source=source; this.bytes=bytes; this.mode=mode; }
        Class<?> define() { return defineClass("probe.NativeTail", bytes, 0, bytes.length,
                new CodeSource(source, (java.security.cert.Certificate[]) null)); }
        @Override public InputStream getResourceAsStream(String name) {
            if (!name.equals("probe/NativeTail.class")) throw new AssertionError("Unexpected class resource " + name);
            return switch (mode) {
                case "missing" -> null;
                case "changed" -> new ByteArrayInputStream(new byte[]{1,2,3});
                case "oversized" -> new ByteArrayInputStream(new byte[8*1024*1024+1]);
                default -> new ByteArrayInputStream(bytes);
            };
        }
    }
    public static void main(String[] args) throws Exception {
        try {
            if (args[0].equals("chain")) {
                var names = List.of(Builder.class.getName(),Entity.class.getName(),CapabilityRoot.class.getName());
                Class<?> leaf = Builder.class;
                switch (args[1]) {
                    case "omitted-tail" -> names = names.subList(0,2);
                    case "extra-tail" -> names = List.of(Builder.class.getName(),Entity.class.getName(),CapabilityRoot.class.getName(),Object.class.getName());
                    case "wrong-order" -> names = List.of(Builder.class.getName(),CapabilityRoot.class.getName(),Entity.class.getName());
                    case "unexpected-class" -> leaf = Extra.class;
                    case "null-leaf" -> leaf = null;
                }
                System.out.println(Inheritance.verify(leaf,names));
            } else {
                var type = new ResourceLoader(Path.of(args[2]).toUri().toURL(), Files.readAllBytes(Path.of(args[4])), args[5]).define();
                System.out.println(TailResource.verify(type,Path.of(args[1]),args[3]));
            }
        } catch (Exception | AssertionError failure) { failure.printStackTrace(); System.exit(3); }
    }
}
''')
        tail = cls.root/'NativeTail.java'; tail.write_text('package probe; public class NativeTail {}\n')
        launcher = cls.root/'CompileInheritance.java'
        launcher.write_text('''import javax.tools.ToolProvider;
public final class CompileInheritance {
    public static void main(String[] a) {
        var c=ToolProvider.getSystemJavaCompiler(); if(c==null)throw new IllegalStateException("jdk.compiler unavailable");
        System.exit(c.run(null,null,null,"--source","17","--target","17","-d",a[0],a[1],a[2]));
    }
}
''')
        result = subprocess.run([cls.java,str(launcher),str(cls.root),str(harness),str(tail)],capture_output=True,text=True,timeout=30)
        if result.returncode: raise AssertionError(result.stdout+result.stderr)
        cls.classfile=cls.root/RESOURCE; cls.raw=cls.classfile.read_bytes()

    @classmethod
    def tearDownClass(cls): cls.temp.cleanup()

    def run_java(self, args, okay=False):
        result=subprocess.run([self.java,'-cp',str(self.root),'InheritanceHarness']+args,capture_output=True,text=True,timeout=15)
        self.assertEqual(result.returncode,0 if okay else 3,result.stdout+result.stderr)
        return result

    def jar(self, entries=None):
        path=self.root/'reviewed-forge.jar'
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            with zipfile.ZipFile(path,'w') as archive:
                for name,data in ([(RESOURCE,self.raw)] if entries is None else entries): archive.writestr(name,data)
        return path

    def tail(self, jar, okay=False, source=None, digest=None, mode='normal'):
        result=self.run_java(['tail',str(jar),str(source or jar),digest or hashlib.sha256(jar.read_bytes()).hexdigest(),str(self.classfile),mode],okay)
        if not okay:
            self.assertIn('CodeSource=',result.stderr)
            self.assertIn('expected='+str(jar),result.stderr)
        return result

    def test_complete_exact_tail_is_accepted(self):
        self.assertIn('CapabilityRoot',self.run_java(['chain','exact'],True).stdout)

    def test_omitted_extra_reordered_and_unexpected_superclasses_fail_with_both_chains(self):
        for mode in ('omitted-tail','extra-tail','wrong-order','unexpected-class','null-leaf'):
            with self.subTest(mode=mode):
                failure=self.run_java(['chain',mode]).stderr
                self.assertIn('expected=',failure); self.assertIn('actual=',failure)

    def test_exact_unique_pinned_jar_and_resource_are_attested(self):
        result=self.tail(self.jar(),True)
        self.assertIn('rawResourceSha256='+hashlib.sha256(self.raw).hexdigest(),result.stdout)
        self.assertIn('codeSourceUrl=',result.stdout)
        self.assertIn('transformed method bodies are not observed',result.stdout)

    def test_wrong_provider_even_with_identical_jar_bytes_is_rejected(self):
        jar=self.jar(); other=self.root/'foreign.jar'; other.write_bytes(jar.read_bytes())
        self.tail(jar,source=other)

    def test_changed_whole_jar_pin_is_rejected_before_resource_acceptance(self):
        self.assertIn('Changed Forge tail supplier',self.tail(self.jar(),digest='0'*64).stderr)

    def test_missing_and_duplicate_raw_jar_entry_are_rejected(self):
        for entries in ([],[(RESOURCE,self.raw),(RESOURCE,self.raw)]):
            with self.subTest(entries=len(entries)): self.tail(self.jar(entries))

    def test_versioned_tail_is_rejected_even_when_base_matches(self):
        for version in ('17','21','invalid'):
            with self.subTest(version=version):
                failure=self.tail(self.jar([(RESOURCE,self.raw),('META-INF/versions/'+version+'/'+RESOURCE,self.raw)])).stderr
                self.assertIn('Versioned Forge tail entry',failure)

    def test_empty_and_oversized_raw_entry_are_rejected(self):
        for raw in (b'',b'x'*(8*1024*1024+1)):
            with self.subTest(size=len(raw)):
                self.assertIn('Oversized/empty Forge tail entry',self.tail(self.jar([(RESOURCE,raw)])).stderr)

    def test_missing_changed_and_oversized_loaded_resources_are_rejected(self):
        for mode in ('missing','changed','oversized'):
            with self.subTest(mode=mode): self.tail(self.jar(),mode=mode)

    def test_same_supplier_changed_entry_cannot_mask_resource_mismatch(self):
        self.assertIn('Changed raw Forge tail class resource',self.tail(self.jar([(RESOURCE,b'wrong raw class')])).stderr)

    def test_outer_gate_keeps_exact_chain_tail_supplier_and_native_method_owners(self):
        text=SOURCE.read_text()
        self.assertIn('"net.minecraftforge.common.capabilities.CapabilityProvider");',text)
        self.assertIn('Inheritance.verify(BuilderEntity.class, CHAIN)',text)
        self.assertIn('TailResource.verify(Entity.class.getSuperclass()',text)
        self.assertIn('classes.get(Entity.class.getName())',text)
        self.assertIn('"forgeSuperclassTail", tail',text)
        self.assertIn('owner("changeDimension", ServerLevel.class, ITeleporter.class) == Entity.class',text)
        self.assertIn('owner("restoreFrom", Entity.class) == Entity.class',text)
        self.assertIn('owner("removeAfterChangingDimensions") == net.minecraft.world.entity.Mob.class',text)
        self.assertLess(text.index('hash.equals(hash(path))'),text.index('TailResource.verify(Entity.class.getSuperclass()'))


if __name__ == '__main__': unittest.main()
