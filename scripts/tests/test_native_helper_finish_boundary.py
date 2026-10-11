"""Execute the real Case C evidence seal across delayed client finish and late callbacks."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/'src/nativeQa/java/com/devfarinsky/siegeoverhaul/nativecompat/NativeActiveHelperQa.java'


class FinishBoundaryJavaTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.java=shutil.which('java')
        if not cls.java:raise RuntimeError('Java with jdk.compiler required')
        cls.temp=tempfile.TemporaryDirectory();cls.root=Path(cls.temp.name);source=SOURCE.read_text()
        a=source.index('    private static boolean observesNativeLifecycle(');b=source.index('    private static void requireSettledNow(',a)
        seal=source[a:b]
        a=source.index('    private static void record(');b=source.index('    private static void screenshot(',a)
        record=source[a:b]
        harness=cls.root/'FinishBoundaryHarness.java'
        harness.write_text('''import java.util.*;
public class FinishBoundaryHarness {
    enum Phase { C_FINAL_STABLE, RELOADING, FINISHED }
    static boolean CANCELED_ABSENT=true,finished,canceledEvidenceSealed,settledEndPending,terminalResolvedObserved=true;
    static Throwable failure;
    static Object observation;
    static long phaseAt=555,sequence,terminalObservedBrackets=313;
    static Phase phase=Phase.FINISHED;
    static final Map<String,Object> REPORT=new LinkedHashMap<>();
    static final List<Map<String,Object>> EVENTS=new ArrayList<>(),WORK=new ArrayList<>(),JOINS=new ArrayList<>(),NATIVE=new ArrayList<>(),TICKETS=new ArrayList<>();
    static final Map<String,Object> PENDING_LOADS=new HashMap<>(),RAW_LOADS=new HashMap<>();
    static class MinecraftServer {boolean sameThread=true;boolean isSameThread(){return sameThread;}}
    static class Transit {
        boolean owned,failCleanup;int cleanupCalls;
        void assertNoTickets(MinecraftServer server){require(!owned,"Owned ticket remains");TICKETS.add(Map.of("kind","zero-owned-verified"));}
        void cleanup(MinecraftServer server){cleanupCalls++;if(failCleanup)throw new AssertionError("Native cleanup failed");owned=false;}
    }
    static final Transit transit=new Transit();
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
''' +seal+record+'''
    static void nativeCallback(String kind){
        if(!observesNativeLifecycle())return;
        if(kind.equals("living")||kind.equals("server-end")){terminalObservedBrackets++;record("terminal-native-bracket",Map.of("gameTime",556));}
        else if(kind.equals("join-high"))JOINS.add(Map.of("kind",kind));
        else if(kind.equals("join-low")||kind.equals("leave"))NATIVE.add(Map.of("kind",kind));
        else record(kind,Map.of());
    }
    static void shutdown(MinecraftServer server){
        transit.cleanup(server);transit.assertNoTickets(server);
        if(!observesNativeLifecycle()||phase!=Phase.RELOADING||failure!=null)return;
        record("actual-server-stop",Map.of());
    }
    public static void main(String[] args){
        String mode=args[0];var server=new MinecraftServer();
        for(int i=0;i<313;i++)record("terminal-native-bracket",Map.of("gameTime",555));
        record("c-final-loaded-stable",Map.of("gameTime",555));record("phase",Map.of("phase","FINISHED","gameTime",555));
        WORK.add(Map.of("kind","native-placement"));JOINS.add(Map.of("kind","native-join"));NATIVE.add(Map.of("kind","transfer-settled"));
        REPORT.put("events",EVENTS);REPORT.put("nativeWork",WORK);REPORT.put("nativeWorkerJoins",JOINS);
        REPORT.put("activeHelperTransitEvents",NATIVE);REPORT.put("activeHelperTicketEvents",TICKETS);
        REPORT.put("terminalObservedBrackets",terminalObservedBrackets);REPORT.put("boundedScenarioVerified",true);
        if(mode.equals("baseline")){CANCELED_ABSENT=false;canceledEvidenceSealed=true;nativeCallback("living");require(terminalObservedBrackets==314,"Baseline observer changed");System.out.println("BASELINE_UNCHANGED");return;}
        if(mode.equals("wrong-phase"))phase=Phase.C_FINAL_STABLE;
        if(mode.equals("wrong-tick"))phaseAt=554;
        if(mode.equals("off-thread"))server.sameThread=false;
        if(mode.equals("open-bracket"))observation=new Object();
        if(mode.equals("pending-hand"))PENDING_LOADS.put("helper",new Object());
        if(mode.equals("raw-join"))RAW_LOADS.put("helper",new Object());
        if(mode.equals("unsettled"))settledEndPending=true;
        if(mode.equals("unresolved"))terminalResolvedObserved=false;
        if(mode.equals("unverified"))REPORT.put("boundedScenarioVerified",false);
        if(mode.equals("prior-failure"))failure=new AssertionError("Earlier failure");
        if(mode.equals("held-ticket"))transit.owned=true;
        if(mode.equals("counter-mismatch"))REPORT.put("terminalObservedBrackets",314L);
        if(mode.equals("missing-in-window"))EVENTS.remove(0);
        try{
            sealCanceledEvidence(server,555);
            require(canceledEvidenceSealed && !finished,"Seal depends on client finish");
            if(mode.equals("repeated-seal")){sealCanceledEvidence(server,555);throw new AssertionError("Repeated seal accepted");}
            if(mode.equals("late-record")){record("late-forbidden",Map.of());throw new AssertionError("Late direct append accepted");}
            for(String kind:List.of("living","server-end","join-high","join-low","leave","reopen"))nativeCallback(kind);
            require(EVENTS.size()==315 && WORK.size()==1 && JOINS.size()==1 && NATIVE.size()==1 && terminalObservedBrackets==313,"Late native callback changed closed evidence");
            require(((List<?>)REPORT.get("events")).size()==315 && REPORT.get("terminalObservedBrackets").equals(313L),"Report lost final interval");
            require(((Map<?,?>)((List<?>)REPORT.get("events")).get(314)).get("phase").equals("FINISHED"),"Seal omitted transition");
            // Delayed client cleanup and unconditional shutdown remain live after the gameplay cut.
            if(mode.equals("cleanup-failure"))transit.failCleanup=true;
            try{shutdown(server);}catch(AssertionError problem){failure=problem;}
            if(mode.equals("cleanup-failure")){require(failure!=null,"Cleanup failure became green");System.out.println("CLEANUP_FAILED_CLOSED");return;}
            require(transit.cleanupCalls==1 && TICKETS.size()==2 && REPORT.get("activeHelperTicketEvents")==TICKETS,"Seal bypassed owned-ticket audit");
            for(String key:List.of("events","nativeWork","nativeWorkerJoins","activeHelperTransitEvents")){
                boolean immutable=false;try{((List)REPORT.get(key)).add(Map.of("late",true));}catch(UnsupportedOperationException expected){immutable=true;}
                require(immutable,"Mutable published evidence: "+key);
            }
            EVENTS.add(Map.of("unpublished",true));WORK.clear();JOINS.clear();NATIVE.clear();
            require(((List<?>)REPORT.get("events")).size()==315 && ((List<?>)REPORT.get("nativeWork")).size()==1
                && ((List<?>)REPORT.get("nativeWorkerJoins")).size()==1 && ((List<?>)REPORT.get("activeHelperTransitEvents")).size()==1,"Published evidence aliases live arrays");
            System.out.println("SEALED_313_THROUGH_555;CLIENT_FINISHED="+finished);
        }catch(AssertionError rejected){System.out.println("REJECT:"+rejected.getMessage()+";sealed="+canceledEvidenceSealed);}
    }
}
''')
        compiler=cls.root/'Compile.java';compiler.write_text('''import javax.tools.ToolProvider;
class Compile {public static void main(String[] a){var c=ToolProvider.getSystemJavaCompiler();if(c==null)throw new IllegalStateException("jdk.compiler missing");
System.exit(c.run(null,null,null,"--source","17","--target","17","-d",a[0],a[1]));}}
''')
        result=subprocess.run([cls.java,str(compiler),str(cls.root),str(harness)],capture_output=True,text=True,timeout=30)
        if result.returncode:raise AssertionError(result.stdout+result.stderr)
    @classmethod
    def tearDownClass(cls):cls.temp.cleanup()
    def run_mode(self,mode):
        value=subprocess.run([self.java,'-cp',str(self.root),'FinishBoundaryHarness',mode],capture_output=True,text=True,timeout=10)
        self.assertEqual(value.returncode,0,value.stderr);return value.stdout
    def test_late_tick_and_callbacks_cannot_extend_successful_interval(self):
        self.assertEqual(self.run_mode('success'),'SEALED_313_THROUGH_555;CLIENT_FINISHED=false\n')
    def test_incomplete_or_wrong_boundary_cannot_be_sealed(self):
        for mode in ('wrong-phase','wrong-tick','off-thread','open-bracket','pending-hand','raw-join','unsettled','unresolved','unverified','prior-failure','held-ticket','counter-mismatch','missing-in-window'):
            with self.subTest(mode=mode):
                value=self.run_mode(mode);self.assertIn('REJECT:',value);self.assertIn('sealed=false',value)
    def test_direct_late_append_and_repeated_seal_fail(self):
        expected={'late-record':'Attempted observation append after canceled evidence seal',
                  'repeated-seal':'Cannot seal an incomplete canceled-helper observation'}
        for mode,reason in expected.items():
            with self.subTest(mode=mode):self.assertEqual(self.run_mode(mode),'REJECT:'+reason+';sealed=true\n')
    def test_shutdown_cleanup_failure_is_not_hidden_by_seal(self):
        self.assertEqual(self.run_mode('cleanup-failure'),'CLEANUP_FAILED_CLOSED\n')
    def test_existing_same_stage_mode_keeps_observer_behavior(self):
        self.assertEqual(self.run_mode('baseline'),'BASELINE_UNCHANGED\n')


class FinishBoundarySourceTests(unittest.TestCase):
    def test_every_append_capable_callback_is_guarded(self):
        text=SOURCE.read_text()
        for name in ('serverTick','living','joined','joinedAfterProduction','left','started'):
            start=text.index('public static void '+name+'(');end=text.index('        try {',start)
            self.assertIn('!observesNativeLifecycle()',text[start:end],name)
        stop=text[text.index('public static void stopping('):text.index('public static void started(')]
        self.assertLess(stop.index('transit.cleanup('),stop.index('!observesNativeLifecycle()'))
        self.assertLess(stop.index('transit.assertNoTickets('),stop.index('!observesNativeLifecycle()'))
    def test_seal_follows_successful_final_proof_and_precedes_client_signal(self):
        text=SOURCE.read_text();part=text[text.index('case C_FINAL_STABLE -> {'):text.index('default -> throw new AssertionError("Unexpected canceled-absent phase")')]
        for before,after in [('transit.assertNoTickets(server)','boundary(server, "c-final-loaded-stable")'),
                             ('boundary(server, "c-final-loaded-stable")','move(tick, Phase.FINISHED)'),
                             ('move(tick, Phase.FINISHED)','sealCanceledEvidence(server, tick)'),
                             ('sealCanceledEvidence(server, tick)','action = Action.DONE')]:self.assertLess(part.index(before),part.index(after))
        self.assertEqual(text.count('sealCanceledEvidence(server, tick)'),1)
    def test_client_finish_and_ticket_cleanup_remain_available(self):
        text=SOURCE.read_text();client=text[text.index('public static void clientTick('):text.index('public static void serverTick(')]
        self.assertNotIn('observesNativeLifecycle()',client)
        finish=text[text.index('private static void finish('):]
        self.assertIn('server.execute(() -> {',finish);self.assertIn('transit.cleanup(server); transit.assertNoTickets(server)',finish)
        self.assertIn('catch (Throwable problem) { fail(problem); REPORT.put("ticketCleanupFailure"',finish)
        self.assertIn('Client finish preceded canceled server evidence seal',finish)
    def test_required_counter_and_interval_oracle_are_unchanged(self):
        text=(ROOT/'scripts/verify-native-absent-helper-cancel.py').read_text()
        self.assertIn("len(brackets)==integer(data.get('terminalObservedBrackets'),100,9999)",text)
        self.assertIn("e['sequence']<final['sequence']",text)
        self.assertIn("range(canceled['gameTime']+1,final['gameTime']+1)",text)
        self.assertIn("range(returned['gameTime']+1,final['gameTime']+1)",text)

if __name__=='__main__':unittest.main()
