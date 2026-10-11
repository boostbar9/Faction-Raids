"""Execute actual Case L pure guards and final seal; this is not Forge/native acceptance."""
from pathlib import Path
import hashlib
import re
import subprocess
import unittest

from test_native_helper_partition_driver import compile_java

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/nativeQa/java/com/devfarinsky/siegeoverhaul/nativecompat/NativeLaterStageHelperQa.java'


def block(text, marker):
    start = text.index(marker); brace = text.index('{', start); depth = 0
    # Selected blocks have no braces inside their string literals.
    for i in range(brace, len(text)):
        depth += (text[i] == '{') - (text[i] == '}')
        if depth == 0: return text[start:i + 1]
    raise AssertionError(marker)


class DriverDecisionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        s = SOURCE.read_text()
        guards = '\n'.join(block(s, '    static final class ' + name) for name in ('CaseWindow', 'CapturedStage', 'CanceledObservation'))
        methods = '\n'.join(block(s, prefix) for prefix in ('    private static boolean observesNativeLifecycle()',
                   '    private static void sealCanceledEvidence(', '    private static void record(', '    public static void stopping('))
        harness = '''import java.util.*;
public class LaterStageDriverHarness {
 static final boolean ENABLED=true;
 enum Phase { START, FINISHED }
 static Phase phase=Phase.FINISHED;
 static long phaseAt=555, sequence=0, terminalObservedBrackets=0;
 static boolean finished,evidenceSealed,settledEndPending,helperPlacedA2=true;
 static Throwable failure;
 static Object observation=null,canceledReceipt=new Object();
 static final Map<Object,Object> PENDING_LOADS=new HashMap<>(),RAW_LOADS=new HashMap<>();
 static final Map<String,Object> REPORT=new LinkedHashMap<>();
 static final List<Map<String,Object>> EVENTS=new ArrayList<>(),WORK=new ArrayList<>(),JOINS=new ArrayList<>();
 static class MinecraftServer {boolean thread=true;boolean isSameThread(){return thread;} }
 static class ServerStoppingEvent {MinecraftServer getServer(){return new MinecraftServer();} }
 static class Transit {int tickets,cleanups;void assertNoTickets(MinecraftServer s){require(tickets==0,"Owned tickets remain");}void cleanup(MinecraftServer s){cleanups++;tickets=0;} }
 static Transit transit=new Transit();
 static void fail(Throwable t){if(failure==null)failure=t;}
 static void require(boolean v,String m){if(!v)throw new AssertionError(m);}
''' + guards + methods + '''
 static String decide(String mode) {
  try {
   switch(mode) {
    case "removal" -> CaseWindow.removal(0,0,-1);
    case "already-verified" -> CaseWindow.removal(0,1,-1);
    case "wrong-removal-stage" -> CaseWindow.removal(1,1,-1);
    case "delayed-removal-snapshot" -> CaseWindow.removal(0,0,100);
    case "load" -> CaseWindow.load(2,2,200,100);
    case "holding-time-counted" -> CaseWindow.load(2,2,199,100);
    case "early-load" -> CaseWindow.load(1,1,250,100);
    case "late-load" -> CaseWindow.load(3,3,250,100);
    case "fake-offline" -> CaseWindow.load(2,2,250,-1);
    case "a0-verified" -> CaseWindow.progress(0,1,"STAGE_VERIFIED",false);
    case "a1-verified" -> CaseWindow.progress(1,2,"STAGE_VERIFIED",false);
    case "a2-running" -> CaseWindow.progress(2,2,"RUNNING",false);
    case "a2-canceled" -> CaseWindow.progress(2,2,"CANCELED",true);
    case "a2-verified" -> CaseWindow.progress(2,3,"STAGE_VERIFIED",false);
    case "a3" -> CaseWindow.progress(3,3,"RUNNING",false);
    case "resumed-canceled" -> CaseWindow.progress(2,2,"RUNNING",true);
    case "frame" -> CapturedStage.frame(100,100,true);
    case "cross-tick" -> CapturedStage.frame(100,101,true);
    case "cross-world" -> CapturedStage.frame(100,100,false);
    case "captured-a0" -> CapturedStage.cells(true,0,false,Set.of(1L,2L),Set.of(1L),Set.of(3L),Set.of(1L));
    case "captured-a2" -> CapturedStage.cells(true,2,true,Set.of(1L,2L),Set.of(1L),Set.of(3L),Set.of(1L));
    case "pending-authority" -> CapturedStage.cells(false,2,true,Set.of(1L),Set.of(1L),Set.of(),Set.of(1L));
    case "helper-a1" -> CapturedStage.cells(true,1,true,Set.of(1L),Set.of(1L),Set.of(),Set.of(1L));
    case "foreign-stage-cell" -> CapturedStage.cells(true,2,true,Set.of(2L),Set.of(1L),Set.of(),Set.of(1L));
    case "unreceipted" -> CapturedStage.cells(true,2,true,Set.of(1L),Set.of(),Set.of(),Set.of(1L));
    case "double-placement" -> CapturedStage.cells(true,2,true,Set.of(1L),Set.of(1L),Set.of(1L),Set.of(1L));
    case "canceled-idle" -> CanceledObservation.verifyBracket(false,0,false,Map.of(1,2),Map.of(1,2),Map.of("a",1),Map.of("a",1));
    case "canceled-authority" -> CanceledObservation.verifyBracket(true,0,false,Map.of(),Map.of(),Map.of(),Map.of());
    case "canceled-placement" -> CanceledObservation.verifyBracket(false,1,false,Map.of(),Map.of(),Map.of(),Map.of());
    case "canceled-supply" -> CanceledObservation.verifyBracket(false,0,true,Map.of(),Map.of(),Map.of(),Map.of());
    case "hidden-stock-transfer" -> CanceledObservation.verify(Map.of(),Map.of(),Map.of("a",1,"b",2),Map.of("a",2,"b",1));
    default -> throw new IllegalArgumentException(mode);
   }
   return "ACCEPT";
  } catch(AssertionError rejected) {return "REJECT:"+rejected.getMessage();}
 }
 static String seal(String mode) {
  REPORT.put("boundedScenarioVerified",true);REPORT.put("terminalObservedBrackets",0L);REPORT.put("activeHelperTransitEvents",new ArrayList<>());
  record("final",Collections.singletonMap("projectSnapshot",null)); record("phase",Map.of("phase","FINISHED","gameTime",555L));
  MinecraftServer server=new MinecraftServer();
  switch(mode){case "unsettled"->settledEndPending=true;case "pending"->PENDING_LOADS.put("x","y");case "raw"->RAW_LOADS.put("x","y");
   case "missing-proof"->helperPlacedA2=false;case "missing-terminal"->canceledReceipt=null;case "open-bracket"->observation=new Object();
   case "wrong-thread"->server.thread=false;case "tickets"->transit.tickets=1;case "bad-counter"->terminalObservedBrackets=1;
   case "already-sealed"->evidenceSealed=true;default->{} }
  try {sealCanceledEvidence(server,555);}catch(AssertionError reject){return "REJECT:"+reject.getMessage()+";sealed="+evidenceSealed;}
  if(mode.equals("late-append")){try{record("late",Map.of());return "INCORRECT_ACCEPT";}catch(AssertionError reject){return "REJECT:"+reject.getMessage()+";sealed="+evidenceSealed;}}
  if(mode.equals("repeat")){try{sealCanceledEvidence(server,555);return "INCORRECT_ACCEPT";}catch(AssertionError reject){return "REJECT:"+reject.getMessage()+";sealed="+evidenceSealed;}}
  long old=sequence; if(observesNativeLifecycle())record("later-tick",Map.of());
  require(sequence==old && EVENTS.size()==2,"Late native callback mutated sealed observation");
  EVENTS.get(0).put("mutant","outside");require(!((List<Map<String,Object>>)REPORT.get("events")).get(0).containsKey("mutant"),"Snapshot retained mutable outer event");
  stopping(new ServerStoppingEvent()); require(transit.cleanups==1 && failure==null,"Sealed stop bypassed exact ticket cleanup");
  return "ACCEPT;sealed="+evidenceSealed+";sequence="+sequence;
 }
 public static void main(String[] args){for(String arg:args)System.out.println(arg+"="+(arg.startsWith("seal:")?seal(arg.substring(5)):decide(arg)));}
}
'''
        compile_java(cls, {'LaterStageDriverHarness.java': harness})
    @classmethod
    def tearDownClass(cls): cls.temp.cleanup()
    def run_modes(self, modes):
        result = subprocess.run([self.java, '-cp', str(self.root), 'LaterStageDriverHarness', *modes], capture_output=True, text=True, timeout=20)
        self.assertEqual(result.returncode, 0, result.stderr)
        return dict(line.split('=',1) for line in result.stdout.splitlines())
    def test_fixed_window_and_captured_receipt_positives(self):
        modes = ['removal','load','a0-verified','a1-verified','a2-running','a2-canceled','frame','captured-a0','captured-a2','canceled-idle']
        self.assertEqual(self.run_modes(modes), dict.fromkeys(modes,'ACCEPT'))
    def test_actual_guards_reject_stage_timing_authority_and_stock_negatives(self):
        modes=['already-verified','wrong-removal-stage','delayed-removal-snapshot','holding-time-counted','early-load','late-load','fake-offline',
               'a2-verified','a3','resumed-canceled','cross-tick','cross-world','pending-authority','helper-a1','foreign-stage-cell',
               'unreceipted','double-placement','canceled-authority','canceled-placement','canceled-supply','hidden-stock-transfer']
        for key,value in self.run_modes(modes).items(): self.assertTrue(value.startswith('REJECT:'),key+':'+value)
    def test_seal_accepts_only_final_server_cut_and_late_tick_cannot_append(self):
        self.assertEqual(self.run_modes(['seal:success'])['seal:success'],'ACCEPT;sealed=true;sequence=3')
    def test_seal_rejects_every_open_obligation(self):
        for mode in ['unsettled','pending','raw','missing-proof','missing-terminal','open-bracket','wrong-thread','tickets','bad-counter','already-sealed']:
            with self.subTest(mode=mode): self.assertTrue(self.run_modes(['seal:'+mode])['seal:'+mode].startswith('REJECT:'))
    def test_direct_late_append_and_repeated_seal_use_exact_production_reasons(self):
        self.assertEqual(self.run_modes(['seal:late-append'])['seal:late-append'], 'REJECT:Attempted observation append after canceled evidence seal;sealed=true')
        self.assertEqual(self.run_modes(['seal:repeat'])['seal:repeat'], 'REJECT:Cannot seal incomplete later-stage evidence;sealed=true')


class FinalizedRemovalSequenceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        s=SOURCE.read_text();a=s.index('            case L_EVICT -> {')+len('            case L_EVICT -> {');b=s.index('                finalizedOffline =',a)
        prefix=s[a:b]
        guard=block(s,'    static final class CaseWindow')
        harness='''import java.util.*;
public class RemovalHarness {
 static long phaseAt=100,unavailableAt=-1,finalizedOfflineAt=-1;static int captures,stage,receipts;
 static boolean held,chunk,registered,removed=true,destroyed,swept;
 static UUID helper=UUID.randomUUID();static class Entity{enum RemovalReason {UNLOADED_TO_CHUNK,DESTROYED;boolean shouldDestroy(){return this==DESTROYED;}boolean shouldSave(){return this==UNLOADED_TO_CHUNK;}}}
 static class Actor{boolean isRemoved(){return removed;}Entity.RemovalReason getRemovalReason(){return destroyed?Entity.RemovalReason.DESTROYED:Entity.RemovalReason.UNLOADED_TO_CHUNK;}}
 static final Map<UUID,Actor> WORKERS=Map.of(helper,new Actor());
 static class Level{boolean hasChunkAt(Object p){return chunk;}Actor getEntity(UUID id){return registered?WORKERS.get(id):null;}}
 static class Server{List<Level>getAllLevels(){return List.of(new Level());}}
 static class Transit{void assertNoTickets(Server s){require(!held,"Owned ticket remains");}Level holdingLevel(Server s){return new Level();}Object holdingFeet(){return new Object();}}
 static final Transit transit=new Transit();
 static class Project{int activeStage(){return stage;}List<Integer>receipts(){return Collections.nCopies(receipts,1);}}
 static Project project(Level l){return new Project();}
 static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
 static void requireFirstStage(Project p){require(stage==0 && receipts==0,"Not unfinished A0");}
 static void assertSuspended(Level l,Project p){require(swept,"Controller has not swept");}
'''+guard+'''
 static void evict(long tick){Server server=new Server();Level level=new Level();Project project=project(level);
 require(tick-phaseAt<=1800,"Eviction clock exceeded");
'''+prefix+'''
 captures++;finalizedOfflineAt=tick;
 }
 public static void main(String[]args){String mode=args[0];
  try{
   switch(mode){
    case "before-controller-sweep"->evict(102);
    case "delayed-native-finalization"->{removed=false;evict(102);require(captures==0 && unavailableAt==102 && phaseAt==100,"Changed wait clock");removed=true;evict(120);}
    case "already-verified"->{receipts=1;evict(102);}
    case "registered"->{registered=true;evict(102);}
    case "owned-ticket"->{held=true;evict(102);}
    case "destroyed"->{destroyed=true;evict(102);}
    case "late-finalization"->{removed=false;evict(102);removed=true;evict(303);}
    case "eviction-clock"->{chunk=true;evict(1901);}
    case "repeat-finalized-snapshot"->{evict(102);evict(103);}
    default->throw new IllegalArgumentException(mode);
   }
   System.out.println("ACCEPT;captures="+captures+";final="+finalizedOfflineAt+";phaseAt="+phaseAt+";swept="+swept);
  }catch(AssertionError e){System.out.println("REJECT:"+e.getMessage()+";captures="+captures);}
 }
}
'''
        compile_java(cls,{'RemovalHarness.java':harness})
    @classmethod
    def tearDownClass(cls):cls.temp.cleanup()
    def run_mode(self,mode):
        r=subprocess.run([self.java,'-cp',str(self.root),'RemovalHarness',mode],capture_output=True,text=True,timeout=10)
        self.assertEqual(r.returncode,0,r.stderr);return r.stdout.strip()
    def test_first_finalized_end_is_captured_without_waiting_for_controller(self):
        self.assertEqual(self.run_mode('before-controller-sweep'),'ACCEPT;captures=1;final=102;phaseAt=100;swept=false')
    def test_natural_finalization_wait_keeps_original_clock(self):
        self.assertEqual(self.run_mode('delayed-native-finalization'),'ACCEPT;captures=1;final=120;phaseAt=100;swept=false')
    def test_no_snapshot_for_wrong_custody_reason_stage_or_deadline(self):
        for mode in ['already-verified','registered','owned-ticket','destroyed','late-finalization','eviction-clock']:
            with self.subTest(mode=mode):self.assertRegex(self.run_mode(mode),r'^REJECT:.*;captures=0$')
    def test_first_finalized_snapshot_cannot_be_replaced(self):
        self.assertEqual(self.run_mode('repeat-finalized-snapshot'),'REJECT:First finalized disk removal already missed unverified A0;captures=1')


class BeforeControllerEndTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        method=block(SOURCE.read_text(),'    public static void beforeControllerEnd(')
        h='''public class EndHarness {
 static boolean ENABLED=true,sealed,ready=true;static Throwable failure;static Object accepted=new Object(),observation=new Object();
 static String actor="restored-detached",captured;static int flushes;
 static boolean observesNativeLifecycle(){return !sealed;}
 static boolean footprintReady(Object level){return ready;}
 static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
 static void fail(Throwable t){failure=t;}
 static class Server{Object overworld(){return this;}}
 static class TickEvent {enum Phase {START,END} static class ServerTickEvent{Phase phase;ServerTickEvent(Phase p){phase=p;}Server getServer(){return new Server();}}}
 static void flush(Object level){captured=actor;observation=null;flushes++;}
'''+method+'''
 public static void main(String[]a){String mode=a[0];if(mode.equals("sealed"))sealed=true;if(mode.equals("unloaded"))ready=false;
 if(mode.equals("empty"))observation=null;
 var event=new TickEvent.ServerTickEvent(mode.equals("start")?TickEvent.Phase.START:TickEvent.Phase.END);
 beforeControllerEnd(event);actor="controller-active-a2";
 System.out.println("flushes="+flushes+";captured="+captured+";failed="+(failure!=null)+";pending="+(observation!=null));}
}
'''
        compile_java(cls,{'EndHarness.java':h})
    @classmethod
    def tearDownClass(cls):cls.temp.cleanup()
    def run_mode(self,mode):
        r=subprocess.run([self.java,'-cp',str(self.root),'EndHarness',mode],capture_output=True,text=True,timeout=10)
        self.assertEqual(r.returncode,0,r.stderr);return r.stdout.strip()
    def test_captures_restoration_before_later_controller_binding(self):
        self.assertEqual(self.run_mode('ordinary'),'flushes=1;captured=restored-detached;failed=false;pending=false')
    def test_no_postseal_start_phase_or_empty_observation_reads(self):
        for mode,pending in [('sealed','true'),('start','true'),('empty','false')]:
            self.assertEqual(self.run_mode(mode),'flushes=0;captured=null;failed=false;pending='+pending)
    def test_observer_never_loads_a_missing_chunk(self):
        self.assertEqual(self.run_mode('unloaded'),'flushes=0;captured=null;failed=true;pending=true')
    def test_priority_is_before_the_real_normal_controller(self):
        s=SOURCE.read_text();a=s.index('    public static void beforeControllerEnd(')
        self.assertIn('@SubscribeEvent(priority = EventPriority.HIGHEST)',s[a-100:a])
        controller=(ROOT/'src/main/java/com/devfarinsky/siegeoverhaul/RaidEvents.java').read_text()
        self.assertIn('@SubscribeEvent\n    public static void onServerTick',controller)


class RoutingAndSourceTests(unittest.TestCase):
    def test_exact_opt_in_and_unknown_selector_rejection(self):
        g=(ROOT/'build.gradle').read_text(); old=(ROOT/'src/nativeQa/java/com/devfarinsky/siegeoverhaul/nativecompat/NativeActiveHelperQa.java').read_text();new=SOURCE.read_text()
        self.assertIn("activeHelperCase in ['same-stage-disk', 'cancel-while-absent', 'later-stage-disk']",g)
        self.assertIn("isPresent() && !activeHelperQa",g)
        self.assertIn("activeHelperQa && (!sharedCrewQa || nativeQaMode != 'staged-handoff')",g)
        self.assertIn('&& !"later-stage-disk".equals(System.getProperty("siegeoverhaul.nativeQa.activeHelperCase"))',old)
        self.assertIn('&& CASE.equals(System.getProperty("siegeoverhaul.nativeQa.activeHelperCase"))',new)
        self.assertIn('&& Boolean.getBoolean("siegeoverhaul.nativeQa.sharedCrew")',new)
    def test_no_accepted_preflight_or_production_mutation_calls(self):
        source=SOURCE.read_text()
        for forbidden in ['NativePerimeterProjects.tick(', '.setDeltaMovement(', '.setPos(', '.teleportTo(', '.setItem(', '.setCount(',
                          '.beginCrewStage(', '.activateCrew', '.reconcileWorker(', '.restore(', 'matchesProjectLease(', 'matchesProjectSnapshot(']:
            self.assertNotIn(forbidden,source)
        self.assertEqual(source.count('setNoAi(false)'),1)
        self.assertIn('CapturedStage.frame(',source); self.assertIn('CapturedStage.cells(',source)
        self.assertIn('CaseWindow.removal(',source); self.assertIn('CaseWindow.load(',source)
        self.assertIn('else observeStages(server, level)',source)
    def test_fixed_partition_and_no_default_directory_change(self):
        source=SOURCE.read_text(); candidate=ROOT/'src/nativeQa/resources/native-helper-partition/cap64-candidate-partition.json'
        self.assertIn(hashlib.sha256(candidate.read_bytes()).hexdigest(),source)
        self.assertIn('part -> validateNative(part, expected)',source)
        self.assertIn('JSON.toJsonTree(partition).equals(expected)',source)
        self.assertIn('REPORT.put("candidatePartition", candidate.deepCopy())',source)
        self.assertNotIn('JSON.fromJson(candidate, Object.class)',source)
        self.assertIn('Path.of("build", "native-helper-qa", "client")',source)
        self.assertNotIn('NativeHelperPartitionPreflightQa',source)
    def test_workflow_is_exact_read_only_labeled_alpha_pr(self):
        w=(ROOT/'.github/workflows/native-later-stage-helper-qa.yml').read_text()
        for text in ["github.event_name == 'pull_request'",'pull_request.number == 295',"base.ref == 'release/4.53.0-alpha.3'",
                     'head.repo.full_name == github.repository',"'native-later-stage-helper-qa'",'contents: read','persist-credentials: false',
                     'scripts/verify-native-later-stage-helper.py','timeout --signal=KILL 600s','--offline']:
            self.assertIn(text,w)
        self.assertEqual(w.count('-PnativeActiveHelperCase=later-stage-disk'),2)
        for forbidden in ['workflow_dispatch','pull_request_target','secrets.','contents: write','publish','CURSEFORGE']:self.assertNotIn(forbidden,w)


if __name__ == '__main__': unittest.main()
