"""Execute the real read-only cancellation observer decisions using Java compiler module."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/'src/nativeQa/java/com/devfarinsky/siegeoverhaul/nativecompat/NativeActiveHelperQa.java'

class CanceledObservationJavaTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.java=shutil.which('java')
        if not cls.java:raise RuntimeError('Java with jdk.compiler required')
        cls.temp=tempfile.TemporaryDirectory();cls.root=Path(cls.temp.name)
        text=SOURCE.read_text();a=text.index('    static final class CanceledObservation {');b=text.index('    private static void verifyCanceledTerminal(',a)
        decision=text[a:b]
        harness=cls.root/'CanceledObservationHarness.java'
        harness.write_text('''import java.util.*;
public final class CanceledObservationHarness {
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
'''+decision+'''
    public static void main(String[] args) {
        String mode=args[0];
        var beforeWorld=Map.of("target","air","other","cobblestone");
        var afterWorld=new HashMap<String,String>(beforeWorld);
        var beforeStock=Map.of("coordinator",List.of(63,0),"helper-offline",List.of(62,0),"chest",List.of(224,220));
        var afterStock=new HashMap<String,List<Integer>>(beforeStock);
        if(mode.equals("conserved-withdrawal")){afterStock.put("coordinator",List.of(64,0));afterStock.put("chest",List.of(223,220));}
        if(mode.equals("conserved-placement")){afterWorld.put("target","cobblestone");afterStock.put("coordinator",List.of(62,0));}
        if(mode.equals("wrong-target"))afterWorld.put("target","foreign-block");
        if(mode.equals("lost-offline"))afterStock.remove("helper-offline");
        if(mode.equals("double-offline"))afterStock.put("helper-loaded",List.of(62,0));
        if(mode.equals("recreated-authority")){
            require(!CanceledObservation.mayWork(false,true,true),"Terminal has work authority");
            require(!CanceledObservation.mayWork(true,false,true),"Foreign participant has work authority");
            require(!CanceledObservation.mayWork(true,true,false),"Foreign owner has work authority");
            require(CanceledObservation.mayWork(true,true,true),"Exact running participant lost authority");
        }
        try {
            CanceledObservation.verifyBracket(mode.equals("authorized-terminal"), mode.equals("placement-receipt")?1:0,
                mode.equals("withdrawal-receipt"),beforeWorld,afterWorld,beforeStock,afterStock);
            System.out.println("ACCEPT");
        } catch(AssertionError rejected){System.out.println("REJECT:"+rejected.getMessage());}
        require(beforeWorld.equals(Map.of("target","air","other","cobblestone")),"Observer mutated target baseline");
        require(beforeStock.size()==3 && beforeStock.get("helper-offline").equals(List.of(62,0)),"Observer mutated custody baseline");
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
        result=subprocess.run([self.java,'-cp',str(self.root),'CanceledObservationHarness',mode],capture_output=True,text=True,timeout=10)
        self.assertEqual(result.returncode,0,result.stderr);return result.stdout
    def test_exact_terminal_idle_and_running_authority_predicate(self):
        for mode in ('idle','recreated-authority'):self.assertEqual(self.run_mode(mode),'ACCEPT\n')
    def test_forbidden_mutations_fail_even_if_total_material_conserved(self):
        for mode in ('conserved-withdrawal','conserved-placement','wrong-target','lost-offline','double-offline'):
            with self.subTest(mode=mode):self.assertTrue(self.run_mode(mode).startswith('REJECT:'))
    def test_terminal_cannot_borrow_prior_authority_or_completion_receipt(self):
        for mode in ('authorized-terminal','placement-receipt','withdrawal-receipt'):
            with self.subTest(mode=mode):self.assertTrue(self.run_mode(mode).startswith('REJECT:'))

class CanceledSequenceJavaTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.java=shutil.which('java');cls.temp=tempfile.TemporaryDirectory();cls.root=Path(cls.temp.name)
        text=SOURCE.read_text()
        release=text[text.index('    private static void requireSettledNow('):text.index('    private static void assertHelperOffline(')]
        outbound=text[text.index('            case C_SETTLE -> {'):text.index('            case C_EVICT -> {')]
        returned=text[text.index('            case C_RETURN_SETTLE -> {'):text.index('            case C_FINAL_STABLE -> {')]
        start=text.index('            if (settledEndPending) {');end=text.index('            ServerPlayer owner',start)
        end_block=text[start:end]
        harness=cls.root/'CanceledSequenceHarness.java'
        harness.write_text('''import java.util.*;
public class CanceledSequenceHarness {
    enum Phase { C_SETTLE, C_EVICT, C_RETURN_SETTLE, C_FINAL_STABLE }
    static class NativeTerminalHelperProof { enum State { DETACHED_RESOLVED } }
    static class Actor { Actor level(){return this;} Actor dimension(){return this;} String location(){return "destination";} }
    static final Actor transferredSource=new Actor(),transferredDestination=new Actor();
    static final UUID helper=UUID.randomUUID();
    static boolean settledEndPending=true;
    static long transferredAt=100,settledAt=-1,phaseAt=100;
    static Phase phase;
    static String mode;
    static final List<String> effects=new ArrayList<>();
    static class Transit {
        boolean held=true;
        void assertSettled(Actor old,Actor next){effects.add("custody");require(!mode.equals("custody-failed"),"Registration lost");}
        void releaseHoldingTicket(Object server){effects.add("release");require(held && !mode.equals("remove-failed"),"Removal failed");held=false;}
        void assertNoTickets(Object server){effects.add("zero");require(!held && !mode.equals("zero-failed"),"Zero failed");}
    }
    static final Transit transit=new Transit();
    static void require(boolean v,String m){if(!v)throw new AssertionError(m);}
    static void requireFirstStage(Object p){require(!mode.equals("wrong-stage"),"Lost first stage");}
    static void verifyCanceledTerminal(Object l){require(!mode.equals("wrong-terminal"),"Changed terminal");}
    static void requireTerminalState(NativeTerminalHelperProof.State s){require(!mode.equals("unresolved"),"Not cleaned");}
    static void record(String k,Map<String,?> v){effects.add(k);}
    static void boundary(Object s,String k){effects.add(k);}
    static void move(long tick,Phase next){effects.add("phase:"+next);phase=next;phaseAt=tick;}
'''+release+'''
    static void end(long tick) {
'''+end_block+'''
    }
    static void release(long tick) {
        Object server=new Object(),level=new Object(),project=new Object();
        switch(phase){
'''+outbound+returned+'''
            default -> throw new AssertionError("Wrong release phase");
        }
    }
    public static void main(String[] args){
        phase=args[0].equals("return")?Phase.C_RETURN_SETTLE:Phase.C_SETTLE;mode=args[1];long tick=101;
        if(mode.equals("same-tick"))tick=100;
        if(mode.equals("stale-end")){settledEndPending=false;settledAt=101;tick=102;}
        try{if(!mode.equals("early-release") && !mode.equals("stale-end"))end(tick);release(tick);}
        catch(AssertionError rejected){System.out.println("REJECT:"+rejected.getMessage());}
        System.out.println("phase="+phase+";phaseAt="+phaseAt+";held="+transit.held+";effects="+effects);
    }
}
''')
        compiler=cls.root/'Compile.java';compiler.write_text('''import javax.tools.ToolProvider;
class Compile {public static void main(String[] a){System.exit(ToolProvider.getSystemJavaCompiler().run(null,null,null,"--source","17","--target","17","-d",a[0],a[1]));}}
''')
        result=subprocess.run([cls.java,str(compiler),str(cls.root),str(harness)],capture_output=True,text=True,timeout=30)
        if result.returncode:raise AssertionError(result.stdout+result.stderr)
    @classmethod
    def tearDownClass(cls):cls.temp.cleanup()
    def run_mode(self,route,mode):
        result=subprocess.run([self.java,'-cp',str(self.root),'CanceledSequenceHarness',route,mode],capture_output=True,text=True,timeout=10)
        self.assertEqual(result.returncode,0,result.stderr);return result.stdout
    def test_both_case_c_releases_follow_exact_later_end(self):
        for route,next_phase in [('outbound','C_EVICT'),('return','C_FINAL_STABLE')]:
            value=self.run_mode(route,'success');self.assertNotIn('REJECT:',value)
            self.assertIn('phase='+next_phase+';phaseAt=101;held=false',value)
            self.assertIn('effects=[custody, ordinary-end-settled-custody, release, zero,',value)
    def test_failed_custody_early_same_tick_and_stale_proof_never_release(self):
        for route in ('outbound','return'):
            for mode in ('early-release','same-tick','stale-end','custody-failed'):
                with self.subTest(route=route,mode=mode):
                    value=self.run_mode(route,mode);self.assertIn('REJECT:',value);self.assertNotIn(', release',value);self.assertIn('held=true',value)
    def test_missing_terminal_cleanup_or_first_stage_blocks_release(self):
        for route,mode in [('return','wrong-terminal'),('return','unresolved'),('outbound','wrong-stage')]:
            value=self.run_mode(route,mode);self.assertIn('REJECT:',value);self.assertNotIn(', release',value);self.assertIn('held=true',value)
    def test_failed_remove_or_zero_cannot_advance_phase(self):
        for route in ('outbound','return'):
            for mode in ('remove-failed','zero-failed'):
                value=self.run_mode(route,mode);self.assertIn('REJECT:',value);self.assertIn('phaseAt=100',value)

class CanceledControllerLagJavaTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.java=shutil.which('java');cls.temp=tempfile.TemporaryDirectory();cls.root=Path(cls.temp.name)
        text=SOURCE.read_text();start=text.index('            case C_EVICT -> {')+len('            case C_EVICT -> {')
        prefix=text[start:text.index('                terminalOffline = NativeActiveHelperTransit.inventory(member);',start)]
        harness=cls.root/'CanceledControllerLagHarness.java'
        harness.write_text('''import java.util.*;
public class CanceledControllerLagHarness {
    static final UUID helper=UUID.randomUUID();
    static boolean swept,wrongCustody,chunkAvailable;
    static long unavailableAt=-1,phaseAt=100;
    static int accepted,zeroCalls;
    static class Entity {enum RemovalReason { UNLOADED_TO_CHUNK; boolean shouldDestroy(){return false;} boolean shouldSave(){return true;} }}
    static class Actor {boolean isRemoved(){return true;} Entity.RemovalReason getRemovalReason(){return Entity.RemovalReason.UNLOADED_TO_CHUNK;} }
    static final Map<UUID,Actor> WORKERS=Map.of(helper,new Actor());
    static class Level {boolean hasChunkAt(Object feet){return chunkAvailable;} Actor getEntity(UUID id){return wrongCustody?WORKERS.get(id):null;} }
    static class Server {List<Level> getAllLevels(){return List.of(new Level());} }
    static class Transit {void assertNoTickets(Server server){zeroCalls++;} Level holdingLevel(Server server){return new Level();} Object holdingFeet(){return new Object();} }
    static final Transit transit=new Transit();
    static class ConstructionEditLedger {
        static ConstructionEditLedger get(Level level){return new ConstructionEditLedger();}
        boolean suspendedCrewMember(Object project,UUID id){return swept;}
    }
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void requireFirstStage(Object project){}
    static void assertSuspended(Level level,Object project){require(swept,"Controller has not swept");}
    static void evict(long tick){
        require(tick-phaseAt<=1800,"Eviction cap");Server server=new Server();Level level=new Level();Object project=new Object();
'''+prefix+'''
        accepted++;
    }
    public static void main(String[] args){
        String mode=args[0];
        try{
            if(mode.equals("late-sweep")){evict(102);evict(119);require(accepted==0 && phaseAt==100 && unavailableAt==102,"Wait rewrote authority or clocks");swept=true;evict(120);require(accepted==1,"Real sweep never accepted");}
            if(mode.equals("no-sweep")){evict(102);evict(302);evict(303);}
            if(mode.equals("wrong-custody")){wrongCustody=true;evict(102);}
            if(mode.equals("eviction-cap")){chunkAvailable=true;evict(102);evict(1901);}
            System.out.println("ACCEPT;accepted="+accepted+";phaseAt="+phaseAt+";unavailableAt="+unavailableAt+";zero="+zeroCalls);
        }catch(AssertionError rejected){System.out.println("REJECT:"+rejected.getMessage()+";accepted="+accepted+";phaseAt="+phaseAt);}
    }
}
''')
        compiler=cls.root/'Compile.java';compiler.write_text('''import javax.tools.ToolProvider;
class Compile {public static void main(String[] a){System.exit(ToolProvider.getSystemJavaCompiler().run(null,null,null,"--source","17","--target","17","-d",a[0],a[1]));}}
''')
        result=subprocess.run([cls.java,str(compiler),str(cls.root),str(harness)],capture_output=True,text=True,timeout=30)
        if result.returncode:raise AssertionError(result.stdout+result.stderr)
    @classmethod
    def tearDownClass(cls):cls.temp.cleanup()
    def run_mode(self,mode):
        value=subprocess.run([self.java,'-cp',str(self.root),'CanceledControllerLagHarness',mode],capture_output=True,text=True,timeout=10)
        self.assertEqual(value.returncode,0,value.stderr);return value.stdout
    def test_controller_can_suspend_later_without_extending_clocks(self):
        self.assertIn('ACCEPT;accepted=1;phaseAt=100;unavailableAt=102;zero=3',self.run_mode('late-sweep'))
    def test_missing_sweep_custody_and_eviction_caps_fail_without_acceptance(self):
        for mode in ('no-sweep','wrong-custody','eviction-cap'):
            with self.subTest(mode=mode):
                result=self.run_mode(mode);self.assertIn('REJECT:',result);self.assertIn('accepted=0;phaseAt=100',result)

if __name__=='__main__':unittest.main()
