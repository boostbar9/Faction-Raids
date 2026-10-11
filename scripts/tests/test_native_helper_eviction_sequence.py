"""Execute the real QA END/release boundary with controlled Java custody/ticket doubles."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/'src/nativeQa/java/com/devfarinsky/siegeoverhaul/nativecompat/NativeActiveHelperQa.java'


class EvictionSequenceJavaTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.java=shutil.which('java')
        if not cls.java: raise RuntimeError('Java with jdk.compiler required')
        cls.temp=tempfile.TemporaryDirectory();cls.root=Path(cls.temp.name)
        text=SOURCE.read_text()
        method=text[text.index('    private static void beginEviction('):text.index('    private static void remember(')]
        start=text.index('            if (settledEndPending) {')
        end=text.index('            ServerPlayer owner',start)
        end_block=text[start:end]
        harness=cls.root/'EvictionHarness.java'
        harness.write_text('''import java.util.*;
public final class EvictionHarness {
    enum Phase { SECOND_OUT, SECOND_SETTLE, EVICT }
    static class MinecraftServer {}
    static class Actor { Level level(){return new Level();} }
    static class Level { Dimension dimension(){return new Dimension();} }
    static class Dimension { String location(){return "minecraft:the_nether";} }
    static final UUID helper=UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final Actor transferredSource=new Actor(), transferredDestination=new Actor();
    static Phase phase=Phase.SECOND_SETTLE;
    static long transferredAt=100, settledAt=-1, absenceAt=-1, phaseAt=100;
    static boolean settledEndPending=true;
    static final List<String> effects=new ArrayList<>();
    static final List<Map<String,?>> proofs=new ArrayList<>();
    static String mode;
    static class Transit {
        boolean held=true;
        void assertSettled(Actor old, Actor next) {
            effects.add("custody");
            require(!mode.equals("unregistered") && !mode.equals("duplicate-custody"),"Native custody failed");
        }
        void releaseHoldingTicket(MinecraftServer server) {
            effects.add("release"); require(held && !mode.equals("remove-failed"),"Ticket remove failed");held=false;
        }
        void assertNoTickets(MinecraftServer server) {
            effects.add("zero");require(!held && !mode.equals("zero-failed"),"Zero ticket proof failed");
        }
    }
    static final Transit transit=new Transit();
    private static void record(String kind,Map<String,?> proof){effects.add(kind);proofs.add(proof);}
    private static void move(long tick,Phase next){effects.add("phase:"+next);phase=next;phaseAt=tick;}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
''' + method + '''
    private static void ordinaryEnd(long tick) {
''' + end_block + '''
    }
    public static void main(String[] args) {
        mode=args[0]; var server=new MinecraftServer(); long tick=101;
        if(mode.equals("wrong-phase"))phase=Phase.SECOND_OUT;
        if(mode.equals("same-tick"))tick=100;
        if(mode.equals("stale-proof")){settledEndPending=false;settledAt=101;tick=102;}
        try {
            if(!mode.equals("early-release") && !mode.equals("stale-proof"))ordinaryEnd(tick);
            beginEviction(server,tick);
        } catch(AssertionError rejected) {
            System.out.println("REJECT:"+rejected.getMessage());
        }
        System.out.println("phase="+phase+";absenceAt="+absenceAt+";phaseAt="+phaseAt+";held="+transit.held);
        System.out.println("effects="+effects);
        System.out.println("proofs="+proofs);
    }
}
''')
        compile=cls.root/'CompileEviction.java'
        compile.write_text('''import javax.tools.ToolProvider;
class CompileEviction {public static void main(String[] a){var c=ToolProvider.getSystemJavaCompiler();
if(c==null)throw new IllegalStateException("jdk.compiler missing");
System.exit(c.run(null,null,null,"--source","17","--target","17","-d",a[0],a[1]));}}
''')
        result=subprocess.run([cls.java,str(compile),str(cls.root),str(harness)],capture_output=True,text=True,timeout=30)
        if result.returncode:raise AssertionError(result.stdout+result.stderr)

    @classmethod
    def tearDownClass(cls):cls.temp.cleanup()

    def run_mode(self,mode):
        result=subprocess.run([self.java,'-cp',str(self.root),'EvictionHarness',mode],capture_output=True,text=True,timeout=10)
        self.assertEqual(result.returncode,0,result.stderr)
        return result.stdout

    def test_later_end_custody_precedes_one_release_zero_and_eviction_clock(self):
        value=self.run_mode('success');self.assertNotIn('REJECT:',value)
        self.assertIn('phase=EVICT;absenceAt=101;phaseAt=101;held=false',value)
        self.assertIn('effects=[custody, ordinary-end-settled-custody, release, zero, eviction-ticket-released-after-settled, phase:EVICT]',value)
        self.assertIn('transferGameTime=100',value);self.assertIn('settledGameTime=101',value)

    def test_early_same_tick_or_stale_proof_cannot_release_or_start_eviction(self):
        for mode in ('early-release','same-tick','stale-proof','wrong-phase'):
            with self.subTest(mode=mode):
                value=self.run_mode(mode);self.assertIn('REJECT:',value)
                self.assertIn('absenceAt=-1;phaseAt=100;held=true',value)
                self.assertNotIn(', release',value);self.assertNotIn('effects=[release',value)
                self.assertNotIn('eviction-ticket-released-after-settled',value)

    def test_failed_registration_or_duplicate_custody_keeps_ticket_for_failure_cleanup(self):
        for mode in ('unregistered','duplicate-custody'):
            with self.subTest(mode=mode):
                value=self.run_mode(mode);self.assertIn('REJECT:Native custody failed',value)
                self.assertIn('absenceAt=-1;phaseAt=100;held=true',value);self.assertIn('effects=[custody]',value)

    def test_failed_native_remove_does_not_start_eviction(self):
        value=self.run_mode('remove-failed');self.assertIn('REJECT:Ticket remove failed',value)
        self.assertIn('phase=SECOND_SETTLE;absenceAt=-1;phaseAt=100;held=true',value)
        self.assertNotIn('eviction-ticket-released-after-settled',value)

    def test_uncertain_zero_never_starts_clock_or_advances_phase(self):
        value=self.run_mode('zero-failed');self.assertIn('REJECT:Zero ticket proof failed',value)
        self.assertIn('phase=SECOND_SETTLE;absenceAt=-1;phaseAt=100;held=false',value)
        self.assertNotIn('eviction-ticket-released-after-settled',value)


if __name__=='__main__':unittest.main()
