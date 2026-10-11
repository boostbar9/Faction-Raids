"""Executed production partition/QA guards with labeled stubs, never native codec acceptance."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
DRIVER = ROOT / 'src/nativeQa/java/com/devfarinsky/siegeoverhaul/nativecompat/NativeHelperPartitionPreflightQa.java'
PINS = ROOT / 'src/nativeQa/resources/native-helper-partition/cap64-candidate-partition.json'
PIN_SHA256 = '64d4deeadebea0808cf38cbca69ebf72b68b3e2ed089958e3a41d1a37a15a115'


def compile_java(test, files):
    test.java = shutil.which('java')
    if not test.java: raise RuntimeError('Java with jdk.compiler required')
    test.temp = tempfile.TemporaryDirectory(); test.root = Path(test.temp.name)
    paths = []
    for name, content in files.items():
        path = test.root / name; path.parent.mkdir(parents=True, exist_ok=True); path.write_text(content); paths.append(str(path))
    compiler = test.root / 'Compile.java'
    compiler.write_text('''import javax.tools.ToolProvider;import java.util.*;
class Compile {public static void main(String[] a){var c=ToolProvider.getSystemJavaCompiler();if(c==null)throw new IllegalStateException("jdk.compiler missing");
var args=new ArrayList<String>(List.of("--source","17","--target","17","-d",a[0]));args.addAll(Arrays.asList(a).subList(1,a.length));System.exit(c.run(null,null,null,args.toArray(new String[0])));}}''')
    result = subprocess.run([test.java, str(compiler), str(test.root), *paths], capture_output=True, text=True, timeout=30)
    if result.returncode: raise AssertionError(result.stdout + result.stderr)


def java_run(test, main, mode):
    result = subprocess.run([test.java, '-cp', str(test.root), main, mode, str(test.root)], capture_output=True, text=True, timeout=10)
    test.assertEqual(result.returncode, 0, result.stderr)
    return result.stdout.strip()


class PartitionDecisionJavaTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        source = DRIVER.read_text(); start = source.index('    static final class Decision {')
        decision = source[start:source.index('    private static Map<String, Object> stageEvidence', start)]
        start = source.index('    private static boolean emptyWorldStorage(')
        storage = source[start:source.index('    private static JsonObject readPins(', start)]
        cls.pins = json.loads(PINS.read_text())['candidate']
        files = {
            'net/minecraft/core/BlockPos.java': '''package net.minecraft.core;
public record BlockPos(int x,int y,int z) {
 public int getX(){return x;} public int getY(){return y;} public int getZ(){return z;} public BlockPos immutable(){return this;}
 public long asLong(){return ((long)x & 0x3ffffffL)<<38 | ((long)z & 0x3ffffffL)<<12 | ((long)y & 0xfffL);}
 public static BlockPos of(long p){return new BlockPos((int)(p>>38),(int)(p<<52>>52),(int)(p<<26>>38));}
}''',
            'net/minecraft/world/level/ChunkPos.java': '''package net.minecraft.world.level;
public class ChunkPos {public static long asLong(int x,int z){return ((long)z & 0xffffffffL)<<32 | ((long)x & 0xffffffffL);}}''',
            'com/devfarinsky/siegeoverhaul/core/PerimeterTerritory.java': '''package com.devfarinsky.siegeoverhaul.core;
public final class PerimeterTerritory { public static final int MAX_CHUNKS=4096; }''',
            'com/devfarinsky/siegeoverhaul/core/PerimeterBlueprint.java': '''package com.devfarinsky.siegeoverhaul.core;
import java.util.*; import net.minecraft.core.BlockPos;
public final class PerimeterBlueprint {
 public record Column(BlockPos base,int supportDepth,int inwardDistance,int componentId){}
 public record Plan(Map<Long,String> blocks,List<Column> columns,Set<Long> clearance,BlockPos min,BlockPos max){public boolean valid(){return !blocks.isEmpty();}}
}''',
            'PartitionHarness.java': '''import java.util.*;import java.nio.file.*;
import net.minecraft.core.BlockPos; import com.devfarinsky.siegeoverhaul.core.*;
public class PartitionHarness {
''' + decision + storage + '''
 public static void main(String[] args) throws Exception {
  String mode=args[0];
  if(mode.startsWith("storage:")){System.out.println(emptyWorldStorage(Path.of(mode.substring(8)))?"EMPTY":"REJECT_STORAGE");return;}
  if(mode.startsWith("count:")){
   try{String value=Decision.problem(null,Integer.parseInt(mode.substring(6)));System.out.println(value==null?"ACCEPT":"CAP");}
   catch(AssertionError failure){System.out.println("REJECT:"+failure.getMessage());}return;
  }
  var blocks=new LinkedHashMap<Long,String>(); var clear=new LinkedHashSet<Long>(); var columns=new ArrayList<PerimeterBlueprint.Column>();
  for(int x=128;x<144;x++)for(int z=0;z<16;z++){
   int d=Math.min(Math.min(x-127,144-x),Math.min(z+1,16-z)); if(d>5)continue;
   columns.add(new PerimeterBlueprint.Column(new BlockPos(x,65,z),0,d,0));
   for(int y=65;y<71;y++){long p=new BlockPos(x,y,z).asLong();
    if(y==68)blocks.put(p,"minecraft:oak_planks"); else if((d==1||d==5)&&(y<68||y==69))blocks.put(p,"minecraft:cobblestone");else clear.add(p);
   }
  }
  var plan=new PerimeterBlueprint.Plan(blocks,columns,clear,new BlockPos(128,65,0),new BlockPos(143,70,15));
  int[] calls={0}; int failureAt=mode.startsWith("fail:")?Integer.parseInt(mode.substring(5)):-1;
  try{
   var layout=PerimeterStageLayout.partition(plan,part->{
    int ordinal=calls[0]++;
    String result=Decision.problem(ordinal==failureAt?"synthetic native rejection":null,part.targets().size());
    System.out.println("CALL "+part.index()+" "+part.columns().size()+" "+part.targets().size()+" "+part.digest()+" "+(result==null));
    return result;
   });
   PerimeterStageLayout.validate(plan,layout);
   System.out.println("LAYOUT "+layout.digest()+" "+calls[0]);
   for(var stage:layout.stages())System.out.println("STAGE "+stage.index()+" "+stage.targets().size()+" "+stage.columns().size()+" "+stage.clearance().size()+" "+stage.digest());
  }catch(AssertionError failure){System.out.println("ABORT "+calls[0]+" "+failure.getMessage());}
 }
}''',
        }
        path = 'src/main/java/com/devfarinsky/siegeoverhaul/core/PerimeterStageLayout.java'
        files['com/devfarinsky/siegeoverhaul/core/PerimeterStageLayout.java'] = (ROOT/path).read_text()
        compile_java(cls, files)
    @classmethod
    def tearDownClass(cls): cls.temp.cleanup()
    def run_mode(self, mode): return java_run(self, 'PartitionHarness', mode).splitlines()
    def test_actual_production_partition_and_decision_match_all_calls_and_stages(self):
        lines = self.run_mode('success'); calls = [line.split() for line in lines if line.startswith('CALL ')]
        self.assertEqual(len(calls), 46)
        for actual, expected in zip(calls, self.pins['validatorCalls']):
            self.assertEqual(actual[1:], [str(expected['index']), str(expected['columnCount']), str(expected['targetCount']), expected['layoutStageDigest'], str(expected['capAccepts']).lower()])
        self.assertIn('LAYOUT '+self.pins['layoutDigest']+' 46', lines)
        stages = [line.split() for line in lines if line.startswith('STAGE ')]
        self.assertEqual(len(stages), 16)
        for actual, expected in zip(stages, self.pins['stages']):
            self.assertEqual(actual[1:], [str(expected['index']), str(expected['targetCount']), str(len(expected['columns'])), str(expected['clearanceCount']), expected['layoutStageDigest']])
    def test_native_error_aborts_before_adaptive_partition_for_large_and_small_proposals(self):
        for ordinal in (0, 5, 45):
            with self.subTest(ordinal=ordinal):
                lines = self.run_mode('fail:'+str(ordinal))
                self.assertEqual(lines[-1], f'ABORT {ordinal+1} Native serializer rejected reviewed proposal: synthetic native rejection')
                self.assertFalse(any(line.startswith(('LAYOUT ', 'STAGE ')) for line in lines))
                self.assertEqual(sum(line.startswith('CALL ') for line in lines), ordinal)
    def test_fixed_cap_and_bounded_counts(self):
        for count, expected in ((1,'ACCEPT'),(64,'ACCEPT'),(65,'CAP'),(572,'CAP'),(0,'REJECT:Unexpected bounded native proposal target count'),(573,'REJECT:Unexpected bounded native proposal target count')):
            with self.subTest(count=count): self.assertEqual(self.run_mode('count:'+str(count)), [expected])
    def test_actual_storage_guard_allows_only_absent_or_canonical_empty_container(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary).resolve(); saves=root/'saves'
            self.assertEqual(self.run_mode('storage:'+str(saves)), ['EMPTY'])
            saves.mkdir(); self.assertEqual(self.run_mode('storage:'+str(saves)), ['EMPTY'])
            world=saves/'unexpected-world'; world.mkdir()
            self.assertEqual(self.run_mode('storage:'+str(saves)), ['REJECT_STORAGE'])
            world.rmdir(); saves.rmdir(); saves.write_text('not a directory')
            self.assertEqual(self.run_mode('storage:'+str(saves)), ['REJECT_STORAGE'])
            saves.unlink(); target=root/'target'; target.mkdir(); saves.symlink_to(target, target_is_directory=True)
            self.assertEqual(self.run_mode('storage:'+str(saves)), ['REJECT_STORAGE'])
            saves.unlink(); saves.symlink_to(root/'absent', target_is_directory=True)
            self.assertEqual(self.run_mode('storage:'+str(saves)), ['REJECT_STORAGE'])
            saves.unlink(); saves.mkdir()
            self.assertEqual(self.run_mode('storage:'+str(root)+'/./saves'), ['REJECT_STORAGE'])


class StartupWorldObservationJavaTests(unittest.TestCase):
    """Execute the actual startup prefix, world checks and finish latch with client API stubs."""
    @classmethod
    def setUpClass(cls):
        source = DRIVER.read_text()
        start = source.index('    public static void clientTick(')
        prefix = source[start:source.index('            initialize(mc);', start)]
        # Only downstream codec work is replaced by a success counter. The actual
        # startup ordering, failure handler, world checks and terminal latch run.
        catch = source[source.index('        } catch (Throwable problem) { finish(mc, problem); }', start):]
        catch = catch[:catch.index('\n    private static void initialize(')]
        start = source.index('    private static void assertWorldFree(')
        world = source[start:source.index('    private static JsonObject readPins(', start)]
        start = source.index('    private static void finish(')
        finish = source[start:source.rindex('\n}')]
        files = {'StartupHarness.java': '''import java.nio.file.*;import java.util.*;
public class StartupHarness {
 static final boolean ENABLED=true;static final long LIMIT_NANOS=600_000_000_000L;
 static boolean finished,attempted,serverObserved;static long started;static Path evidence;
 static int passes;static final Map<String,Object> REPORT=new LinkedHashMap<>();
 static final class Json {String toJson(Object value){return value.toString();}}static final Json JSON=new Json();
 static final class TitleScreen {}
 static final class TickEvent {enum Phase {START,END} static final class ClientTickEvent {final Phase phase=Phase.END;}}
 static final class Minecraft {static Minecraft instance;Object level,player,server,screen;java.io.File gameDirectory;int stops;
  static Minecraft getInstance(){return instance;}Object getSingleplayerServer(){return server;}void stop(){stops++;}}
''' + prefix + '''            passes++; finish(mc, null);
''' + catch + world + finish + '''
 public static void main(String[] args)throws Exception {
  Minecraft mc=new Minecraft();Minecraft.instance=mc;mc.gameDirectory=new java.io.File(args[1]);
  if(args[0].equals("level"))mc.level=new Object();
  if(args[0].equals("player"))mc.player=new Object();
  if(args[0].equals("server"))mc.server=new Object();
  if(args[0].equals("server-event"))serverObserved=true;
  // Observe a non-title END first, then a clean title-screen END.
  clientTick(new TickEvent.ClientTickEvent());
  mc.level=null;mc.player=null;mc.server=null;serverObserved=false;mc.screen=new TitleScreen();
  clientTick(new TickEvent.ClientTickEvent());
  System.out.println("result="+REPORT.get("result")+";passes="+passes+";stops="+mc.stops+";finished="+finished+";failure="+REPORT.get("failure"));
 }
}''', 'com/devfarinsky/siegeoverhaul/FactionLogger.java': '''package com.devfarinsky.siegeoverhaul;
public class FactionLogger {public static final Logger LOG=new Logger();public static class Logger {public void error(String message,Throwable failure){}}}'''}
        compile_java(cls, files)
    @classmethod
    def tearDownClass(cls): cls.temp.cleanup()
    def test_clean_non_title_startup_can_later_reach_preflight_once(self):
        self.assertEqual(java_run(self, 'StartupHarness', 'clean'),
                         'result=passed;passes=1;stops=1;finished=true;failure=null')
    def test_non_title_world_player_or_server_is_latched_before_later_clean_title(self):
        for mode in ('level', 'player', 'server', 'server-event'):
            with self.subTest(mode=mode):
                self.assertEqual(java_run(self, 'StartupHarness', mode),
                                 'result=failed;passes=0;stops=1;finished=true;failure=java.lang.AssertionError: World/server/player appeared during world-free preflight')


class SerializerShapeJavaTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        source=DRIVER.read_text(); start=source.index('    static final class SerializerShape {')
        shape=source[start:source.index('    private static String validateNative(',start)]
        files={'SerializerHarness.java':'''import java.lang.reflect.*;import java.nio.file.*;import java.util.*;
public class SerializerHarness {
 public static class Buffer {} public static class Tag {} public static class SubTag extends Tag {}
 public interface Codec<T> {void write(Buffer buffer,T value);T read(Buffer buffer);}
 public static class Good implements Codec<Tag> {public void write(Buffer b,Tag t){} public Tag read(Buffer b){return new Tag();}}
 public static class Inherited extends Good {}
 public static class Erased implements Codec<Object> {public void write(Buffer b,Object t){} public Object read(Buffer b){return new Tag();}}
 public static class Covariant implements Codec<SubTag> {public void write(Buffer b,SubTag t){} public SubTag read(Buffer b){return new SubTag();}}
 public abstract static class AbstractCodec implements Codec<Tag> {}
 public static class Missing {public Object read(Buffer b){return null;}}
 public static class Private {private void write(Buffer b,Object t){} public Object read(Buffer b){return null;}}
 public static class Static {public static void write(Buffer b,Object t){} public Object read(Buffer b){return null;}}
 public abstract static class AbstractMethods {public abstract void write(Buffer b,Object t);public abstract Object read(Buffer b);}
 public static class WrongWriteParameters {public void write(String b,Object t){} public Object read(Buffer b){return null;}}
 public static class WrongReadParameters {public void write(Buffer b,Object t){} public Object read(String b){return null;}}
 public static class WrongWriteReturn {public int write(Buffer b,Object t){return 1;}public Object read(Buffer b){return null;}}
 public static class WrongReadReturn {public void write(Buffer b,Object t){}public String read(Buffer b){return "bad";}}
 public static class Loader extends ClassLoader {public Class<?> make(byte[] bytes){return defineClass("ChildLoaded",bytes,0,bytes.length);}}
''' +shape+'''
 public static void main(String[] args)throws Exception {
  String mode=args[0];Object object=new Good();Class<?> type=Codec.class,owner=SerializerHarness.class;
  try{
   if(mode.equals("missing")){SerializerShape.methods(Missing.class,Buffer.class,Tag.class);}
   else if(mode.equals("private")){SerializerShape.methods(Private.class,Buffer.class,Tag.class);}
   else if(mode.equals("static")){SerializerShape.methods(Static.class,Buffer.class,Tag.class);}
   else if(mode.equals("abstract-method")){SerializerShape.methods(AbstractMethods.class,Buffer.class,Tag.class);}
   else if(mode.equals("wrong-write-parameters")){SerializerShape.methods(WrongWriteParameters.class,Buffer.class,Tag.class);}
   else if(mode.equals("wrong-read-parameters")){SerializerShape.methods(WrongReadParameters.class,Buffer.class,Tag.class);}
   else if(mode.equals("wrong-write-return")){SerializerShape.methods(WrongWriteReturn.class,Buffer.class,Tag.class);}
   else if(mode.equals("wrong-read-return")){SerializerShape.methods(WrongReadReturn.class,Buffer.class,Tag.class);}
   else if(mode.equals("abstract-type")){SerializerShape.verifyType(AbstractCodec.class,type,owner);}
   else if(mode.equals("interface-type")){SerializerShape.verifyType(type,type,owner);}
   else {
    if(mode.equals("null"))object=null;
    if(mode.equals("wrong-type"))object=new Object();
    if(mode.equals("wrong-interface"))type=Good.class;
    if(mode.equals("wrong-field-loader"))owner=String.class;
    if(mode.equals("wrong-instance-loader"))object=new Loader().make(Files.readAllBytes(Path.of(args[1],"ChildLoaded.class"))).getConstructor().newInstance();
    if(mode.equals("inherited"))object=new Inherited();
    if(mode.equals("erased"))object=new Erased();
    if(mode.equals("covariant"))object=new Covariant();
    var methods=SerializerShape.verify(object,type,owner,Buffer.class,Tag.class);
    SerializerShape.requireStable(object,mode.equals("changed-instance")?new Good():object);
    System.out.println("ACCEPT:"+methods.get(0).getDeclaringClass().getSimpleName()+":"+methods.get(1).getReturnType().getSimpleName()+":"+methods.get(0).isBridge());return;
   }
   System.out.println("UNEXPECTED_ACCEPT");
  }catch(AssertionError|ReflectiveOperationException rejected){System.out.println("REJECT:"+rejected.getClass().getSimpleName()+":"+rejected.getMessage());}
 }
}''',
            'ChildLoaded.java':'public class ChildLoaded extends SerializerHarness.Good {}',
            'io/netty/buffer/Unpooled.java':'''package io.netty.buffer;public class Unpooled {public static Object buffer(int initial,int maximum){if(maximum!=4*1024*1024)throw new AssertionError("Wrong allocation bound");return new Object();}}''',
            'net/minecraft/nbt/CompoundTag.java':'package net.minecraft.nbt;public record CompoundTag(String value) {}',
            'net/minecraft/network/FriendlyByteBuf.java':'''package net.minecraft.network;public class FriendlyByteBuf {public static int releases;public FriendlyByteBuf(Object b){}public boolean isReadable(){return net.minecraft.network.syncher.EntityDataSerializers.mode.equals("trailing");}public void release(){releases++;}}''',
            'net/minecraft/network/syncher/EntityDataSerializers.java':'''package net.minecraft.network.syncher;
import net.minecraft.nbt.CompoundTag;import net.minecraft.network.FriendlyByteBuf;
public class EntityDataSerializers {public static String mode;public static final Codec COMPOUND_TAG=new Codec();public static class Codec {
 CompoundTag saved;public void write(FriendlyByteBuf b,CompoundTag tag){if(mode.equals("throws"))throw new IllegalArgumentException("synthetic encoder failure");saved=tag;}
 public CompoundTag read(FriendlyByteBuf b){return mode.equals("mismatch")?new CompoundTag("different"):saved;}}}''',
            'BudgetHarness.java':'''import net.minecraft.network.syncher.EntityDataSerializers;import net.minecraft.network.FriendlyByteBuf;import net.minecraft.nbt.CompoundTag;
import com.devfarinsky.siegeoverhaul.nativecompat.BlueprintNetworkBudget;
public class BudgetHarness {public static void main(String[] a){EntityDataSerializers.mode=a[0];String result=BlueprintNetworkBudget.problem(new CompoundTag("reviewed"));System.out.println((result==null?"ACCEPT":"REJECT:"+result)+";released="+FriendlyByteBuf.releases);}}''',
        }
        files['com/devfarinsky/siegeoverhaul/nativecompat/BlueprintNetworkBudget.java']=(ROOT/'src/main/java/com/devfarinsky/siegeoverhaul/nativecompat/BlueprintNetworkBudget.java').read_text()
        compile_java(cls, files)
    @classmethod
    def tearDownClass(cls): cls.temp.cleanup()
    def run_mode(self,mode,main='SerializerHarness'): return java_run(self,main,mode)
    def test_actual_shape_guard_accepts_inherited_covariant_and_erased_bridge_forms(self):
        expected={'good':'ACCEPT:Good:Tag:true','inherited':'ACCEPT:Good:Tag:true','erased':'ACCEPT:Erased:Object:false','covariant':'ACCEPT:Covariant:SubTag:true'}
        for mode,result in expected.items():
            with self.subTest(mode=mode): self.assertEqual(self.run_mode(mode),result)
    def test_actual_shape_guard_rejects_wrong_type_concreteness_loader_and_instance(self):
        expected={'null':'Wrong native serializer instance/type','wrong-type':'Wrong native serializer instance/type','wrong-interface':'Wrong native serializer instance/type','abstract-type':'Native serializer implementation is not concrete','interface-type':'Native serializer implementation is not concrete','wrong-field-loader':'Native serializer defining loader differs','wrong-instance-loader':'Native serializer defining loader differs','changed-instance':'Native COMPOUND_TAG instance changed'}
        for mode,message in expected.items():
            with self.subTest(mode=mode): self.assertEqual(self.run_mode(mode),'REJECT:AssertionError:'+message)
    def test_actual_method_guard_rejects_wrong_visibility_parameters_modifiers_and_returns(self):
        for mode in ('missing','private','wrong-write-parameters','wrong-read-parameters'):
            with self.subTest(mode=mode): self.assertTrue(self.run_mode(mode).startswith('REJECT:NoSuchMethodException:'),mode)
        expected={'static':'Native serializer method is not public concrete instance behavior','abstract-method':'Native serializer method is not public concrete instance behavior','wrong-write-return':'Native serializer write return changed','wrong-read-return':'Native serializer read return is incompatible'}
        for mode,message in expected.items():
            with self.subTest(mode=mode): self.assertEqual(self.run_mode(mode),'REJECT:AssertionError:'+message)
    def test_actual_budget_wrapper_rejects_trailing_bytes_nbt_mismatch_and_encoder_failure(self):
        self.assertEqual(self.run_mode('good','BudgetHarness'),'ACCEPT;released=1')
        for mode in ('trailing','mismatch'):
            with self.subTest(mode=mode): self.assertEqual(self.run_mode(mode,'BudgetHarness'),'REJECT:The complete native blueprint could not be synchronized safely.;released=1')
        self.assertEqual(self.run_mode('throws','BudgetHarness'),"REJECT:The entire perimeter exceeds native Workers' client blueprint capacity. No payment or partial job was created.;released=1")


class PartitionPreflightSourceTests(unittest.TestCase):
    def test_pin_is_unchanged_and_qa_resource_is_unshipped(self):
        self.assertEqual(hashlib.sha256(PINS.read_bytes()).hexdigest(), PIN_SHA256)
        build=(ROOT/'build.gradle').read_text()
        self.assertIn("resources.srcDir 'src/nativeQa/resources'",build)
        self.assertNotIn('from sourceSets.nativeQa',build)
    def test_driver_is_world_free_and_calls_real_budget_before_fixed_cap(self):
        source=DRIVER.read_text()
        for forbidden in ('createFreshLevel(','.loadLevel(','.setup(','.commission(','.forceChunk(','.changeDimension(','.setBlock(','.tick(','.setItem(','.reconcileWorker('): self.assertNotIn(forbidden,source)
        self.assertIn('PerimeterBlueprint.create(Set.of(new ChunkPos(8, 0))',source)
        self.assertIn('BlueprintNetworkBudget.problem(blueprint)',source)
        self.assertLess(source.index('BlueprintNetworkBudget.problem(blueprint)'),source.index('Decision.problem(nativeProblem'))
        self.assertIn('NativeActiveHelperApiParity.verify(evidence)',source)
        self.assertIn('NativeActiveHelperApiParity.TailResource.verify(type, path, hash)',source)
        self.assertNotIn('startsWith(EntityDataSerializers.class.getName()',source)
        self.assertGreaterEqual(source.count('SerializerShape.requireStable(serializerInstance, EntityDataSerializers.COMPOUND_TAG)'),4)
        self.assertIn('ServerAboutToStartEvent',source)
        self.assertIn('mc.level == null && mc.player == null && mc.getSingleplayerServer() == null',source)
        self.assertIn('StandardOpenOption.CREATE_NEW',source)
        for field in ('worldCreated','integratedServerStarted','commissionPerformed','gameplayExecuted'): self.assertIn(f'REPORT.put("{field}", false)',source)
    def test_existing_fixture_modes_cannot_run_under_preflight(self):
        base=(ROOT/'src/nativeQa/java/com/devfarinsky/siegeoverhaul/nativecompat/NativeBuildingQa.java').read_text()
        self.assertIn('"hud", "helper-partition-preflight")',base)
        active=(ROOT/'src/nativeQa/java/com/devfarinsky/siegeoverhaul/nativecompat/NativeActiveHelperQa.java').read_text()
        self.assertIn('&& "staged-handoff".equals(System.getProperty("siegeoverhaul.nativeQa.mode"))',active)
        build=(ROOT/'build.gradle').read_text()
        self.assertIn("throw new GradleException('Unsupported nativeQaMode')",build)
        self.assertIn("'helper-partition-preflight': 'build/native-helper-partition-qa/client'",build)
        self.assertIn('if (partitionPreflight && (sharedCrewQa || activeHelperQa',build)
    def test_prepare_stays_launch_blocked_and_launch_replays_same_api_gate(self):
        prepare=(ROOT/'gradle/native-active-helper-api-qa.gradle').read_text()
        self.assertIn("captureMode == 'helper-partition-preflight' && !captureShared",prepare)
        self.assertIn("tasks.matching { it.name == 'runClient' }.configureEach",prepare)
        self.assertIn("throw new GradleException('API capture is prepare-only",prepare)
        launch=(ROOT/'gradle/native-helper-partition-preflight-qa.gradle').read_text()
        self.assertIn('--verify-receipt',launch)
        self.assertIn("new File(client, 'saves').exists()",launch)
        self.assertIn("nativeActiveHelperCase').isPresent()",launch)
    def test_workflow_is_exact_opt_in_read_only_and_has_no_world_launch_route(self):
        source=(ROOT/'.github/workflows/native-helper-partition-preflight-qa.yml').read_text()
        for required in ("github.event_name == 'pull_request'",'github.event.pull_request.number == 295','github.event.pull_request.head.repo.full_name == github.repository',"github.event.pull_request.base.ref == 'release/4.53.0-alpha.3'","'native-helper-partition-preflight-qa'",'contents: read','persist-credentials: false','cache-read-only: true','--offline','timeout --signal=KILL 600s','test ! -e build/native-helper-partition-qa/client/saves'): self.assertIn(required,source)
        for forbidden in ('workflow_dispatch','secrets.','contents: write','nativeActiveHelperQa=true','nativeSharedCrewQa=true'): self.assertNotIn(forbidden,source)
        self.assertLess(source.index('prepareNativeActiveHelperApiEvidence'),source.index('scripts/verify-native-helper-api.py'))
        self.assertLess(source.index('scripts/verify-native-helper-api.py'),source.index(' runClient'))
        self.assertLess(source.index(' runClient'),source.index('scripts/verify-native-helper-partition.py'))


if __name__ == '__main__': unittest.main()
