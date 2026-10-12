"""Execute the real Case L passive observer against callback-hostile, labeled Java fixtures.

All observer code, field readers, identity allocation and decisions are compiled unchanged
with source/target 17. Fixture classes supply field shapes, not native gameplay evidence.
Fresh hosted native compilation/runtime acceptance remains a separate required gate.
"""
from pathlib import Path
import re
import subprocess
import unittest

from test_native_helper_partition_driver import compile_java

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/nativeQa/java/com/devfarinsky/siegeoverhaul/nativecompat/NativeLaterStageTerminalObservation.java'
PACKAGE = 'com.devfarinsky.siegeoverhaul.nativecompat'


def java_fixture_files():
    files = {str(SOURCE.relative_to(ROOT / 'src/nativeQa/java')): SOURCE.read_text()}
    def add(name, body):
        package, _, cls = name.rpartition('.')
        files[name.replace('.', '/') + '.java'] = 'package ' + package + ';\n' + body
    add('net.minecraft.world.item.Item', '''public class Item {
 public int hashCode(){throw new AssertionError("Item hashCode callback");}public boolean equals(Object o){throw new AssertionError("Item equals callback");}
 public String toString(){throw new AssertionError("Item toString callback");}
}''')
    add('net.minecraft.world.item.Items', '''public class Items {public static final Item COBBLESTONE=new Item(),OAK_PLANKS=new Item();}''')
    add('net.minecraft.world.item.ItemStack', 'public class ItemStack {}')
    add('net.minecraft.resources.ResourceLocation', '''public record ResourceLocation(String namespace,String path) {
 public String getNamespace(){return namespace;} public String getPath(){return path;}
}''')
    add('net.minecraft.core.registries.BuiltInRegistries', '''import java.util.*;
import net.minecraft.world.item.Item;import net.minecraft.resources.ResourceLocation;
public class BuiltInRegistries { public static final Registry ITEM=new Registry();
 public static class Registry {
  public final Map<ResourceLocation,Item> values=new HashMap<>();
  public ResourceLocation getKey(Item item){throw new AssertionError("Reverse registry lookup callback");}
  public Item get(ResourceLocation key){return values.get(key);}
 }
}''')
    add('net.minecraft.nbt.CompoundTag', '''import java.util.*;
public class CompoundTag {public boolean present, malformed;public Map<String,Integer> journal=new LinkedHashMap<>();
 public CompoundTag copy(){var c=new CompoundTag();c.present=present;c.malformed=malformed;c.journal.putAll(journal);return c;}
 public boolean contains(String key){return present;}
}''')
    add('net.minecraft.world.entity.ai.goal.Goal', '''public class Goal {
 public int fixtureTimer=73;public boolean fixturePaymentPending=true;
 public void start(){throw new AssertionError("start callback");} public void stop(){throw new AssertionError("stop callback");}
 public void tick(){throw new AssertionError("tick callback");} public boolean canUse(){throw new AssertionError("canUse callback");}
 public String toString(){throw new AssertionError("goal toString callback");}
}''')
    add('net.minecraft.world.entity.ai.goal.WrappedGoal', '''public class WrappedGoal extends Goal {
 public Goal goal;public boolean running;public WrappedGoal(Goal g){goal=g;}
 public Goal getGoal(){return goal;} public boolean isRunning(){return running;}
}''')
    add('net.minecraft.world.entity.ai.goal.GoalSelector', '''import java.util.*;
public class GoalSelector {private final Set<WrappedGoal> availableGoals=new LinkedHashSet<>();public Set<WrappedGoal> getAvailableGoals(){throw new AssertionError("native selector getter callback");}}''')
    add('com.talhanation.recruits.entities.AbstractRecruitEntity', 'public class AbstractRecruitEntity {}')
    add('com.talhanation.workers.entities.AbstractWorkerEntity', '''import java.util.*;
import com.talhanation.workers.world.NeededItem;import com.talhanation.recruits.entities.AbstractRecruitEntity;
public class AbstractWorkerEntity extends AbstractRecruitEntity {public List<NeededItem> neededItems=new ArrayList<>();}''')
    add('com.talhanation.workers.entities.BuilderEntity', '''import java.util.*;
import net.minecraft.nbt.CompoundTag;import net.minecraft.world.entity.ai.goal.GoalSelector;
public class BuilderEntity extends AbstractWorkerEntity {
 public final CompoundTag data=new CompoundTag();public GoalSelector goalSelector=new GoalSelector();
 public CompoundTag getPersistentData(){return data;} public UUID getUUID(){return new UUID(0,42);}
}''')
    add('com.talhanation.workers.world.NeededItem', '''import java.util.function.Predicate;import net.minecraft.world.item.ItemStack;
public class NeededItem {
 public final Predicate<ItemStack> matcher;public int count;public final boolean required;public final Object sourceKey;
 private Object cachedMatchKey;private boolean matchKeyResolved;
 public NeededItem(Predicate<ItemStack> m,int c,boolean r,Object s){matcher=m;count=c;required=r;sourceKey=s;}
 public Object getMatchKey(){throw new AssertionError("getMatchKey callback");}
 public boolean matches(ItemStack s){throw new AssertionError("matches callback");}
 public boolean isSameRequest(NeededItem i){throw new AssertionError("isSameRequest callback");}
 public String toString(){throw new AssertionError("request toString callback");}
}''')
    add('com.talhanation.workers.entities.ai.BuilderWorkGoal', '''import java.util.function.Predicate;import java.lang.invoke.*;
import net.minecraft.world.item.ItemStack;import net.minecraft.world.entity.ai.goal.Goal;
public class BuilderWorkGoal extends Goal {
 public static Predicate<ItemStack> matcher(int token){return s->{throw new AssertionError("predicate callback "+token);};}
 public static class Shape implements Predicate<ItemStack>{public boolean test(ItemStack x){throw new AssertionError("shape callback");}}
 public static Object shaped(int flags)throws Exception{
  byte[] b;try(var in=BuilderWorkGoal.class.getResourceAsStream("MatcherShape.class")){b=in.readAllBytes();}
  int n=((b[8]&255)<<8)|(b[9]&255),p=10;
  for(int i=1;i<n;i++){int tag=b[p++]&255;switch(tag){case 1->{int len=((b[p]&255)<<8)|(b[p+1]&255);p+=2+len;}
   case 3,4,9,10,11,12,17,18->p+=4;case 5,6->{p+=8;i++;}case 7,8,16,19,20->p+=2;case 15->p+=3;default->throw new AssertionError(tag);}}
  int access=((b[p]&255)<<8)|(b[p+1]&255);access=(access&~(0x10|0x1000))|flags;
  b[p]=(byte)(access>>8);b[p+1]=(byte)access;
  Class<?> c=MethodHandles.lookup().defineHiddenClass(b,true,MethodHandles.Lookup.ClassOption.NESTMATE).lookupClass();
  return c.getDeclaredConstructor().newInstance();
 }
}''')
    add('com.talhanation.workers.entities.ai.MatcherShape', '''import java.util.function.Predicate;import net.minecraft.world.item.ItemStack;
public class MatcherShape implements Predicate<ItemStack> {public boolean test(ItemStack s){throw new AssertionError("shape callback");}}''')
    add('com.talhanation.workers.entities.ai.AbstractChestGoal', '''import net.minecraft.world.entity.ai.goal.Goal;
import com.talhanation.workers.entities.AbstractWorkerEntity;
public class AbstractChestGoal extends Goal {public AbstractWorkerEntity worker;public AbstractChestGoal(AbstractWorkerEntity w){worker=w;}}''')
    for cls in ('GetNeededItemsFromStorage', 'DepositItemsToStorage'):
        add('com.talhanation.workers.entities.ai.' + cls, f'''import com.talhanation.workers.entities.AbstractWorkerEntity;
public class {cls} extends AbstractChestGoal {{public {cls}(AbstractWorkerEntity w){{super(w);}}}}''')
    for cls in ('RecruitUpkeepPosGoal', 'RecruitUpkeepEntityGoal'):
        add('com.talhanation.recruits.entities.ai.' + cls, f'''import net.minecraft.world.entity.ai.goal.Goal;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
public class {cls} extends Goal {{public AbstractRecruitEntity recruit;public {cls}(AbstractRecruitEntity w){{recruit=w;}}}}''')
    add('com.talhanation.workers.entities.ai.RecruitStorageUpkeepGoal', '''import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.entities.ai.RecruitUpkeepEntityGoal;
public class RecruitStorageUpkeepGoal extends RecruitUpkeepEntityGoal {public RecruitStorageUpkeepGoal(AbstractRecruitEntity w){super(w);}}''')
    add(PACKAGE + '.ProtectedInventoryGoal', '''import java.util.*;import com.talhanation.workers.entities.BuilderEntity;
import net.minecraft.world.entity.ai.goal.Goal;
abstract class ProtectedInventoryGoal extends Goal {
 static final class Session {final BuilderEntity worker;final LinkedHashSet<ProtectedInventoryGoal> pending=new LinkedHashSet<>();
  private boolean draining;private ProtectedInventoryGoal upkeepOwner;
  private final EnumMap<ProtectedStorageAccess.Kind,ProtectedInventoryGoal> owners=new EnumMap<>(ProtectedStorageAccess.Kind.class);
  Session(BuilderEntity w){worker=w;} boolean ready(){throw new AssertionError("ready callback");}
 }
 final BuilderEntity worker;final Goal delegate;final Session session;private boolean started,protectedLifecycle,stopFailed;
 ProtectedInventoryGoal(BuilderEntity w,Goal d,Session s){worker=w;delegate=d;session=s;}
}''')
    add(PACKAGE + '.ProtectedStorageAccess', '''import com.talhanation.workers.entities.BuilderEntity;import net.minecraft.world.entity.ai.goal.Goal;
final class ProtectedStorageAccess extends ProtectedInventoryGoal {
 enum Kind {NEEDED,DEPOSIT,POSITION_UPKEEP,ENTITY_UPKEEP} final Kind kind;
 ProtectedStorageAccess(BuilderEntity w,Goal d,Kind k,Session s){super(w,d,s);kind=k;}
 static boolean install(BuilderEntity w){throw new AssertionError("install callback");}
 static boolean drainCleanup(BuilderEntity w){throw new AssertionError("drain callback");}
}''')
    add(PACKAGE + '.ProtectedUpkeepAccess', '''import com.talhanation.workers.entities.BuilderEntity;import net.minecraft.world.entity.ai.goal.Goal;
final class ProtectedUpkeepAccess extends ProtectedInventoryGoal {final ProtectedStorageAccess.Kind kind;
 ProtectedUpkeepAccess(BuilderEntity w,Goal d,ProtectedStorageAccess.Kind k,Session s){super(w,d,s);kind=k;}}''')
    add(PACKAGE + '.ProtectedInventoryCleanup', '''import java.util.*;import net.minecraft.nbt.CompoundTag;
final class ProtectedInventoryCleanup {static final String KEY="SiegeProtectedInventoryCleanup";
 static Map<ProtectedStorageAccess.Kind,Integer> read(CompoundTag t){
  if(t.malformed)throw new NativeLaterStageTerminalObservation.ObservationRejected("Malformed fixture journal");
  var result=new EnumMap<ProtectedStorageAccess.Kind,Integer>(ProtectedStorageAccess.Kind.class);
  for(var e:t.journal.entrySet())result.put(ProtectedStorageAccess.Kind.valueOf(e.getKey()),e.getValue());return result;
 }}''')
    add(PACKAGE + '.TerminalHarness', HARNESS)
    return files


HARNESS = r'''
import java.util.*;import java.lang.reflect.*;import java.util.function.Predicate;
import com.talhanation.workers.entities.*;import com.talhanation.workers.entities.ai.*;import com.talhanation.workers.world.NeededItem;
import com.talhanation.recruits.entities.ai.*;import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.item.*;import net.minecraft.resources.ResourceLocation;import net.minecraft.core.registries.BuiltInRegistries;
public final class TerminalHarness {
 static void check(boolean b,String text){if(!b)throw new AssertionError(text);}
 static void set(Object target,Class<?> owner,String name,Object value)throws Exception{var f=owner.getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
 static Set<WrappedGoal> goals(GoalSelector selector){try{return (Set<WrappedGoal>)get(selector,GoalSelector.class,"availableGoals");}catch(Exception e){throw new AssertionError(e);}}
 static Object get(Object target,Class<?> owner,String name)throws Exception{var f=owner.getDeclaredField(name);f.setAccessible(true);return f.get(target);}
 static Object call(String name,Class<?>[] types,Object target,Object...args)throws Exception{
  var m=NativeLaterStageTerminalObservation.class.getDeclaredMethod(name,types);m.setAccessible(true);
  try{return m.invoke(target,args);}catch(InvocationTargetException e){
   if(e.getCause() instanceof NativeLaterStageTerminalObservation.ObservationRejected r)throw r;
   if(e.getCause() instanceof Error x)throw x;throw e;}
 }
 static NeededItem request(int n){return new NeededItem(BuilderWorkGoal.matcher(n),n,true,null);}
 static Item item(){return Items.COBBLESTONE;}
 static void cache(NeededItem r,Object value,boolean resolved)throws Exception{set(r,NeededItem.class,"cachedMatchKey",value);set(r,NeededItem.class,"matchKeyResolved",resolved);}
 static class Hostile {public int hashCode(){throw new AssertionError("hashCode callback");}public boolean equals(Object o){throw new AssertionError("equals callback");}public String toString(){throw new AssertionError("toString callback");}}
 static class CustomList extends ArrayList<NeededItem>{public int size(){throw new AssertionError("list callback");}}
 static class CustomRequest extends NeededItem {CustomRequest(){super(BuilderWorkGoal.matcher(1),1,true,null);}}
 static class CustomSelector extends GoalSelector {public Set<WrappedGoal>getAvailableGoals(){throw new AssertionError("selector callback");}}
 static class CustomGoalSet extends LinkedHashSet<WrappedGoal>{
  public int size(){throw new AssertionError("goal-set size callback");}public Iterator<WrappedGoal>iterator(){throw new AssertionError("goal-set iteration callback");}}
 static class CustomIteratorGoalSet extends LinkedHashSet<WrappedGoal>{public Iterator<WrappedGoal>iterator(){throw new AssertionError("goal-set iteration callback");}}
 static class CustomWrapped extends WrappedGoal {CustomWrapped(Goal g){super(g);}public Goal getGoal(){throw new AssertionError("wrapped callback");}}
 static class CustomChest extends GetNeededItemsFromStorage {CustomChest(BuilderEntity w){super(w);}}
 static class Fields {private int value;private static boolean flag;public final Object object=new Object();}
 static class ChildFields extends Fields {}
 static class CustomPending extends LinkedHashSet<ProtectedInventoryGoal>{public int size(){throw new AssertionError("pending callback");}}
 static class CustomOwners extends EnumMap<ProtectedStorageAccess.Kind,ProtectedInventoryGoal>{CustomOwners(){super(ProtectedStorageAccess.Kind.class);}public int size(){throw new AssertionError("owners callback");}}
 enum ForeignKind {FOREIGN}
 static <T> T replace(T record,String key,Object value)throws Exception{
  var c=record.getClass().getRecordComponents();var types=new Class<?>[c.length];var values=new Object[c.length];boolean found=false;
  for(int i=0;i<c.length;i++){types[i]=c[i].getType();values[i]=c[i].getAccessor().invoke(record);if(c[i].getName().equals(key)){values[i]=value;found=true;}}
  check(found,"Missing record field "+key);return (T)record.getClass().getDeclaredConstructor(types).newInstance(values);
 }
 static NativeLaterStageTerminalObservation.RequestSnapshot snap(NativeLaterStageTerminalObservation o,BuilderEntity w){return o.requests(w);}
 static String requests(String mode)throws Exception{
  BuiltInRegistries.ITEM.values.clear();var o=new NativeLaterStageTerminalObservation();var w=new BuilderEntity();var r=request(1);w.neededItems.add(r);
  switch(mode){
   case "empty"->w.neededItems.clear();case "unresolved","hostile","retained"->{}
   case "resolved-null"->cache(r,null,true);case "resolved-item"->cache(r,item(),true);case "resolved-oak"->cache(r,Items.OAK_PLANKS,true);
   case "resolved-other-registered"->{var other=new Item();BuiltInRegistries.ITEM.values.put(new ResourceLocation("minecraft","other"),other);cache(r,other,true);}
   case "null-list"->w.neededItems=null;case "custom-list"->w.neededItems=new CustomList();
   case "oversized"->{for(int i=0;i<8;i++)w.neededItems.add(request(1));}
   case "null-request"->w.neededItems.add(null);case "subclass-request"->w.neededItems.set(0,new CustomRequest());
   case "wrong-request"->((List)w.neededItems).set(0,new Hostile());case "duplicate"->w.neededItems.add(r);
   case "zero"->r.count=0;case "negative"->r.count=-1;case "over64"->r.count=65;
   case "max64"->r.count=64;case "not-required"->w.neededItems.set(0,new NeededItem(r.matcher,1,false,null));
   case "source"->w.neededItems.set(0,new NeededItem(r.matcher,1,true,new Hostile()));
   case "null-matcher"->w.neededItems.set(0,new NeededItem(null,1,true,null));
   case "foreign-nest"->w.neededItems.set(0,new NeededItem(s->{throw new AssertionError("foreign predicate callback");},1,true,null));
   case "ordinary-matcher"->w.neededItems.set(0,new NeededItem(new Predicate<ItemStack>(){public boolean test(ItemStack s){throw new AssertionError("ordinary callback");}},1,true,null));
   case "nonfinal-matcher","nonsynthetic-matcher","trusted-shape"->{int flags=mode.equals("nonfinal-matcher")?0x1000:mode.equals("nonsynthetic-matcher")?0x10:0x1010;
    w.neededItems.set(0,new NeededItem((Predicate<ItemStack>)BuilderWorkGoal.shaped(flags),1,true,null));}
   case "foreign-loader"->{var loader=new java.net.URLClassLoader(new java.net.URL[]{TerminalHarness.class.getProtectionDomain().getCodeSource().getLocation()},ClassLoader.getPlatformClassLoader());
    Class<?> b=Class.forName(BuilderWorkGoal.class.getName(),true,loader);Object matcher=b.getMethod("matcher",int.class).invoke(null,1);
    w.neededItems.set(0,new NeededItem((Predicate<ItemStack>)matcher,1,true,null));}
   case "unresolved-item"->cache(r,item(),false);case "resolved-foreign"->cache(r,new Hostile(),true);
   case "unregistered-item"->cache(r,new Item(),true);
   default->{if(!mode.startsWith("mutation-"))throw new AssertionError("Unknown request test "+mode);}
  }
  if(mode.startsWith("mutation-")){
   w.neededItems.add(request(2));var frozen=snap(o,w);Map<String,Object> evidence=frozen.evidence();
   switch(mode.substring(9)){
    case "add"->w.neededItems.add(request(3));case "remove"->w.neededItems.remove(1);case "reorder"->Collections.swap(w.neededItems,0,1);
    case "count"->r.count=2;case "list"->w.neededItems=new ArrayList<>(w.neededItems);case "request"->w.neededItems.set(0,request(1));
    case "matcher"->set(r,NeededItem.class,"matcher",BuilderWorkGoal.matcher(1));case "cache-resolved"->cache(r,null,true);
    case "cache-item"->cache(r,item(),true);case "source"->set(r,NeededItem.class,"sourceKey",new Hostile());
    case "required"->set(r,NeededItem.class,"required",false);case "cache-replaced"->{var key=item();cache(r,key,true);frozen=snap(o,w);cache(r,Items.OAK_PLANKS,true);}
    default->throw new AssertionError("Unknown mutation");
   }
   var current=snap(o,w);check(!NativeLaterStageTerminalObservation.sameRequests(frozen,current),"Mutation was accepted");
   check(((List<?>)evidence.get("records")).size()==2,"Snapshot backed by live list");return "CHANGED";
  }
  var a=snap(o,w);var before=a.evidence();var b=snap(o,w);
  check(NativeLaterStageTerminalObservation.sameRequests(a,b),"Unchanged request rejected");
  check(before.equals(b.evidence()),"Observation changed request");
  check(a.count()==w.neededItems.size(),"Count mismatch");
  if(mode.equals("retained"))for(int i=0;i<100;i++)check(NativeLaterStageTerminalObservation.sameRequests(a,snap(o,w)),"Retained request mutated");
  if(mode.equals("unresolved")||mode.equals("hostile"))check(get(r,NeededItem.class,"cachedMatchKey")==null&&!((boolean)get(r,NeededItem.class,"matchKeyResolved")),"Cache was evaluated");
  if(mode.equals("resolved-item"))check(a.records().get(0).cacheRegistryKey().equals("minecraft:cobblestone"),"Wrong cache key");
  if(mode.equals("resolved-null"))check(a.records().get(0).cacheResolved()&&a.records().get(0).cacheRegistryKey()==null,"Resolved-null collapsed");
  return "ACCEPT";
 }
 record Fixture(BuilderEntity worker,ProtectedInventoryGoal.Session session,List<ProtectedInventoryGoal> adapters,List<WrappedGoal> wrapped){}
 static Fixture fixture(){var w=new BuilderEntity();var s=new ProtectedInventoryGoal.Session(w);var adapters=new ArrayList<ProtectedInventoryGoal>();var wrapped=new ArrayList<WrappedGoal>();
  Goal[] delegates={new GetNeededItemsFromStorage(w),new DepositItemsToStorage(w),new RecruitUpkeepPosGoal(w),new RecruitStorageUpkeepGoal(w)};
  for(int i=0;i<4;i++){var k=ProtectedStorageAccess.Kind.values()[i];ProtectedInventoryGoal a=i<2?new ProtectedStorageAccess(w,delegates[i],k,s):new ProtectedUpkeepAccess(w,delegates[i],k,s);
   adapters.add(a);var g=new WrappedGoal(a);wrapped.add(g);goals(w.goalSelector).add(g);}return new Fixture(w,s,adapters,wrapped);
 }
 static EnumMap<ProtectedStorageAccess.Kind,ProtectedInventoryGoal> owners(Fixture f)throws Exception{return (EnumMap<ProtectedStorageAccess.Kind,ProtectedInventoryGoal>)get(f.session(),ProtectedInventoryGoal.Session.class,"owners");}
 static void active(Fixture f,int i,boolean pending)throws Exception{var a=f.adapters().get(i);set(a,ProtectedInventoryGoal.class,"protectedLifecycle",true);set(a,ProtectedInventoryGoal.class,"started",!pending);
  owners(f).put(ProtectedStorageAccess.Kind.values()[i],a);if(pending)f.session().pending.add(a);
  if(i>=2)set(f.session(),ProtectedInventoryGoal.Session.class,"upkeepOwner",a);
 }
 static String storage(String spec)throws Exception{
  String[] parts=spec.split(":");String mode=parts[0];int i=parts.length>1?Integer.parseInt(parts[1]):0;
  var o=new NativeLaterStageTerminalObservation();var f=fixture();var w=f.worker();var a=f.adapters().get(i);
  var frozen=o.storage(w);
  switch(mode){
   case "closed","hostile","retained"->{}case "missing"->goals(w.goalSelector).remove(f.wrapped().get(i));
   case "duplicate"->goals(w.goalSelector).add(new WrappedGoal(a));case "running"->f.wrapped().get(i).running=true;
   case "started"->set(a,ProtectedInventoryGoal.class,"started",true);case "active"->active(f,i,false);
   case "pending","natural-cleanup"->active(f,i,true);case "stop-failed"->set(a,ProtectedInventoryGoal.class,"stopFailed",true);
   case "protected-orphan"->set(a,ProtectedInventoryGoal.class,"protectedLifecycle",true);
   case "foreign-wrapper-worker"->set(a,ProtectedInventoryGoal.class,"worker",new BuilderEntity());
   case "foreign-session"->set(a,ProtectedInventoryGoal.class,"session",new ProtectedInventoryGoal.Session(w));
   case "null-session"->set(a,ProtectedInventoryGoal.class,"session",null);
   case "foreign-session-worker"->set(f.session(),ProtectedInventoryGoal.Session.class,"worker",new BuilderEntity());
   case "foreign-delegate-worker"->{if(i<2)((AbstractChestGoal)a.delegate).worker=new BuilderEntity();else if(i==2)((RecruitUpkeepPosGoal)a.delegate).recruit=new BuilderEntity();else((RecruitUpkeepEntityGoal)a.delegate).recruit=new BuilderEntity();}
   case "wrong-delegate"->set(a,ProtectedInventoryGoal.class,"delegate",new Goal());
   case "subclass-delegate"->set(a,ProtectedInventoryGoal.class,"delegate",new CustomChest(w));
   case "unguarded"->{goals(w.goalSelector).remove(f.wrapped().get(i));goals(w.goalSelector).add(new WrappedGoal(a.delegate));}
   case "stock-entity-delegate"->set(f.adapters().get(3),ProtectedInventoryGoal.class,"delegate",new RecruitUpkeepEntityGoal(w));
   case "replaced-wrapped"->{goals(w.goalSelector).remove(f.wrapped().get(i));goals(w.goalSelector).add(new WrappedGoal(a));}
   case "replaced-wrapper"->{var k=ProtectedStorageAccess.Kind.values()[i];ProtectedInventoryGoal replacement=i<2?new ProtectedStorageAccess(w,a.delegate,k,f.session()):new ProtectedUpkeepAccess(w,a.delegate,k,f.session());set(f.wrapped().get(i),WrappedGoal.class,"goal",replacement);}
   case "replaced-delegate"->{Goal d=switch(i){case 0->new GetNeededItemsFromStorage(w);case 1->new DepositItemsToStorage(w);case 2->new RecruitUpkeepPosGoal(w);default->new RecruitStorageUpkeepGoal(w);};set(a,ProtectedInventoryGoal.class,"delegate",d);}
   case "replaced-all"->f=fixture();
   case "wrong-kind"->set(a,a.getClass(),"kind",ProtectedStorageAccess.Kind.values()[(i+1)%4]);
   case "custom-wrapped"->{goals(w.goalSelector).remove(f.wrapped().get(i));goals(w.goalSelector).add(new CustomWrapped(a));}
   case "null-wrapped"->goals(w.goalSelector).add(null);case "null-goal"->goals(w.goalSelector).add(new WrappedGoal(null));
   case "too-many-goals"->{for(int j=0;j<129;j++)goals(w.goalSelector).add(new WrappedGoal(new Goal()));}
   case "null-selector"->w.goalSelector=null;case "custom-selector"->w.goalSelector=new CustomSelector();
   case "null-goal-set"->set(w.goalSelector,GoalSelector.class,"availableGoals",null);case "custom-goal-set"->set(w.goalSelector,GoalSelector.class,"availableGoals",new CustomGoalSet());
   case "custom-iterator-set"->set(w.goalSelector,GoalSelector.class,"availableGoals",new CustomIteratorGoalSet());case "hash-goal-set"->set(w.goalSelector,GoalSelector.class,"availableGoals",new HashSet<>());
   case "null-pending-collection"->set(f.session(),ProtectedInventoryGoal.Session.class,"pending",null);
   case "custom-pending-collection"->set(f.session(),ProtectedInventoryGoal.Session.class,"pending",new CustomPending());
   case "null-owners-collection"->set(f.session(),ProtectedInventoryGoal.Session.class,"owners",null);
   case "custom-owners-collection"->set(f.session(),ProtectedInventoryGoal.Session.class,"owners",new CustomOwners());
   case "foreign-owner-kind"->{var foreign=new EnumMap<ForeignKind,ProtectedInventoryGoal>(ForeignKind.class);foreign.put(ForeignKind.FOREIGN,a);set(f.session(),ProtectedInventoryGoal.Session.class,"owners",foreign);}
   case "null-owner"->owners(f).put(ProtectedStorageAccess.Kind.values()[i],null);
   case "foreign-pending"->f.session().pending.add(fixture().adapters().get(0));case "null-pending"->f.session().pending.add(null);
   case "pending-not-owner"->f.session().pending.add(a);case "pending-started"->{active(f,i,false);f.session().pending.add(a);}
   case "foreign-owner"->owners(f).put(ProtectedStorageAccess.Kind.values()[i],fixture().adapters().get(i));
   case "owner-wrong-kind"->{active(f,i,false);owners(f).put(ProtectedStorageAccess.Kind.values()[(i+1)%4],a);}
   case "owner-not-protected"->owners(f).put(ProtectedStorageAccess.Kind.values()[i],a);
   case "foreign-upkeep"->set(f.session(),ProtectedInventoryGoal.Session.class,"upkeepOwner",fixture().adapters().get(2));
   case "missing-upkeep"->{active(f,2,false);set(f.session(),ProtectedInventoryGoal.Session.class,"upkeepOwner",null);}
   case "two-upkeep"->{active(f,2,false);active(f,3,false);}
   case "draining"->set(f.session(),ProtectedInventoryGoal.Session.class,"draining",true);
   case "journal","review"->{active(f,i,true);w.data.present=true;w.data.journal.put(ProtectedStorageAccess.Kind.values()[i].name(),mode.equals("review")?2:1);}
   case "empty-journal"->w.data.present=true;
   case "orphan-journal"->{w.data.present=true;w.data.journal.put("NEEDED",1);}
   case "bad-journal"->w.data.malformed=true;
   case "unknown-journal-value"->{active(f,i,true);w.data.present=true;w.data.journal.put(ProtectedStorageAccess.Kind.values()[i].name(),3);}
   default->throw new AssertionError("Unknown storage test "+mode);
  }
  var current=o.storage(f.worker());for(var saved:f.adapters()){check(saved.delegate.fixtureTimer==73&&saved.delegate.fixturePaymentPending,"Observation changed timer/payment state");}boolean same=NativeLaterStageTerminalObservation.sameStorageIdentity(frozen,current);
  if(mode.startsWith("replaced")){check(!same,"Replacement accepted");return "CHANGED";}
  check(same,"Unexpected identity change");
  boolean closed=NativeLaterStageTerminalObservation.closed(current);
  if(mode.equals("natural-cleanup")){
   check(!closed,"Pending cleanup called closed");check((boolean)get(a,ProtectedInventoryGoal.class,"protectedLifecycle"),"Observation drained cleanup");
   check(f.session().pending.size()==1,"Observation mutated pending");
   set(a,ProtectedInventoryGoal.class,"protectedLifecycle",false);f.session().pending.clear();owners(f).clear();set(f.session(),ProtectedInventoryGoal.Session.class,"upkeepOwner",null);
   check(NativeLaterStageTerminalObservation.closed(o.storage(w)),"Natural closure rejected");return "NATURALLY_CLOSED";
  }
  if(mode.equals("retained"))for(int j=0;j<100;j++)check(NativeLaterStageTerminalObservation.closed(o.storage(w)),"Closed storage changed");
  return closed?"CLOSED":"OPEN";
 }
 static String metadata(String mode)throws Exception{
  Class<?> owner=Fields.class,type=int.class;String name="value";int modifiers=Modifier.PRIVATE;
  switch(mode){case "valid"->{}case "owner"->owner=ChildFields.class;case "type"->type=long.class;case "modifiers"->modifiers=Modifier.PUBLIC;
   case "missing"->name="absent";case "static"->{name="flag";type=boolean.class;}case "final"->{name="object";type=Object.class;modifiers=Modifier.PUBLIC;}
   default->throw new AssertionError(mode);}
  call("field",new Class<?>[]{Class.class,String.class,Class.class,int.class},null,owner,name,type,modifiers);return "ACCEPT";
 }
 static String pure(String mode)throws Exception{
  var o=new NativeLaterStageTerminalObservation();var f=fixture();var original=o.storage(f.worker());var changed=original;
  var wrappers=new ArrayList<>(original.wrappers());var w=wrappers.get(0);
  switch(mode){case "zero-worker"->changed=replace(changed,"workerRef",0);case "zero-session"->changed=replace(changed,"sessionRef",0);
   case "aliased-session"->changed=replace(changed,"sessionRef",changed.workerRef());case "foreign-session-worker"->changed=replace(changed,"sessionWorkerRef",99999);
   case "missing-wrapper"->changed=replace(changed,"wrappers",wrappers.subList(0,3));case "extra-wrapper"->{wrappers.add(w);changed=replace(changed,"wrappers",wrappers);}
   case "aliased-wrapper"->{wrappers.set(1,replace(wrappers.get(1),"wrapperRef",w.wrapperRef()));changed=replace(changed,"wrappers",wrappers);}
   case "aliased-delegate"->{wrappers.set(0,replace(w,"delegateRef",w.wrapperRef()));changed=replace(changed,"wrappers",wrappers);}
   case "aliased-wrapped"->{wrappers.set(0,replace(w,"wrappedRef",original.workerRef()));changed=replace(changed,"wrappers",wrappers);}
   case "wrong-kind"->{wrappers.set(0,replace(w,"kind","DEPOSIT"));changed=replace(changed,"wrappers",wrappers);}
   case "wrong-class"->{wrappers.set(0,replace(w,"delegateClass","foreign"));changed=replace(changed,"wrappers",wrappers);}
   case "missing-reference"->{wrappers.set(0,replace(w,"delegateRef",0));changed=replace(changed,"wrappers",wrappers);}
   case "negative-reference"->{wrappers.set(0,replace(w,"wrapperRef",-1));changed=replace(changed,"wrappers",wrappers);}
   default->throw new AssertionError(mode);}
  check(!NativeLaterStageTerminalObservation.closed(changed),"Malformed closure accepted");
  check(!NativeLaterStageTerminalObservation.sameStorageIdentity(original,changed),"Malformed identity accepted");return "INVALID";
 }
 static String references(String mode)throws Exception{
  var o=new NativeLaterStageTerminalObservation();var types=new Class<?>[]{Object.class};
  check((int)call("reference",types,o,new Object[]{null})==0,"Null reference ID");
  var first=new Hostile();check((int)call("reference",types,o,first)==1,"First reference ID");
  check((int)call("reference",types,o,first)==1,"Reference ID not stable");
  for(int i=2;i<=1000;i++)check((int)call("reference",types,o,new Hostile())==i,"ID missing/aliased/nonsequential");
  var m=o.metadata();int trusted=(int)m.get("builderWorkGoalLoaderRef");var w=new BuilderEntity();w.neededItems.add(request(1));
  check(o.requests(w).records().get(0).matcherLoaderRef()==trusted,"Trusted loader not recorded");o.storage(fixture().worker());
  var catalog=o.referenceEvidence();for(int i=0;i<catalog.size();i++)check(catalog.get(i).get("id").equals(i+1),"Catalog gap");
  check(catalog.stream().anyMatch(x->x.containsKey("workerUuid")),"Worker UUID missing");
  int old=catalog.size();call("reference",types,o,new Hostile());check(catalog.size()==old,"Catalog is live");
  if(mode.equals("bound")){for(int i=o.referenceEvidence().size();i<65536;i++)call("reference",types,o,new Hostile());call("reference",types,o,new Hostile());throw new AssertionError("Bound not enforced");}
  if(mode.equals("immutable")){
   boolean list=false,map=false,records=false;try{catalog.clear();}catch(UnsupportedOperationException expected){list=true;}
   try{catalog.get(0).put("id",0);}catch(UnsupportedOperationException expected){map=true;}
   try{o.requests(w).records().clear();}catch(UnsupportedOperationException expected){records=true;}
   check(list&&map&&records,"Mutable evidence");
  }
  return "ACCEPT";
 }
 public static void main(String[] args)throws Exception{
  for(String arg:args){String[] split=arg.split("/",2);String result;
   try{result=switch(split[0]){case "requests"->requests(split[1]);case "storage"->storage(split[1]);case "metadata"->metadata(split[1]);case "references"->references(split[1]);case "pure"->pure(split[1]);default->throw new AssertionError(arg);};}
   catch(NativeLaterStageTerminalObservation.ObservationRejected rejected){result="REJECT:"+rejected.getMessage();}
   System.out.println(arg+"="+result);
  }
 }
}
'''


def driver_sequence_source():
    # Extract actual phase branches and transition method. Unrelated world and
    # receipt fixtures are labeled stubs; closure still uses the real observer.
    source = (SOURCE.parent / 'NativeLaterStageHelperQa.java').read_text()
    def block(marker):
        start = source.index(marker)
        brace = source.index('{', start)
        depth = 0
        for end in range(brace, len(source)):
            depth += (source[end] == '{') - (source[end] == '}')
            if depth == 0:
                return source[start:end + 1]
        raise AssertionError(marker)
    deadline = re.search(r'require\(tick - phaseAt <= .*?;', source).group(0)
    cases = '\n'.join(block('            case ' + name + ' ->') for name in ('L_CANCEL_WAIT', 'L_FINAL_STABLE'))
    verify = block('    private static boolean verifyCanceled(')
    tail = verify[verify.index('        assertCanceledFreeze(level.getServer(), level);'):]
    verify = '    private static boolean verifyCanceled(ServerLevel level) {\n' + tail
    return '''package com.devfarinsky.siegeoverhaul.nativecompat;
import java.util.*;import com.talhanation.workers.entities.BuilderEntity;
public class DriverSequenceHarness {
 enum Phase {L_CANCEL_WAIT,L_FINAL_STABLE,FINISHED,L_EVICT} enum Action{NONE,DONE}
 static Phase phase=Phase.L_CANCEL_WAIT;static Action action=Action.NONE;
 static long phaseAt=100,canceledAuthorityAt=90,terminalObservedBrackets=200;
 static boolean cancelSent=true,helperActiveObserved=true,helperPlacedA2=true,terminalPresent=true;
 static Object canceledReceipt,coreKey,projectId,fixture,owner;
 static int entries,seals,moves;
 static final Object canceledWorld=Map.of(),canceledStock=Map.of();
 static final Map<Object,Object> PENDING_LOADS=new HashMap<>(),RAW_LOADS=new HashMap<>();
 static final List<Integer> JOINS=List.of(1,2,3);static final Map<String,Object> REPORT=new HashMap<>();
 static final Map<UUID,BuilderEntity> WORKERS=new LinkedHashMap<>();
 static final NativeLaterStageTerminalObservation requestObserver=new NativeLaterStageTerminalObservation();
 static final Map<UUID,NativeLaterStageTerminalObservation.RequestSnapshot> CANCELED_REQUESTS=new HashMap<>();
 static final Map<UUID,NativeLaterStageTerminalObservation.StorageSnapshot> CANCELED_STORAGE=new HashMap<>();
 static class MinecraftServer {} static class ServerLevel {MinecraftServer getServer(){return new MinecraftServer();}}
 static class Terminal {Object save(){return "receipt";}}
 static class Snapshot {Terminal terminal(Object id){return terminalPresent?new Terminal():null;}}
 static class PerimeterProjectAuthority {static Snapshot snapshot(ServerLevel l,Object k){return new Snapshot();}}
 static class Transit {void assertNoTickets(MinecraftServer s){}}static Transit transit=new Transit();
 static class NativeStagedHandoffFixture {static Object auxiliaryIsolationSnapshot(ServerLevel l,Object f){return Map.of();}}
 static class CanceledObservation {static void verify(Object a,Object b,Object c,Object d){require(a.equals(b)&&c.equals(d),"Changed frozen accounting");}}
 static Object worldTargets(ServerLevel l){return Map.of();} static Object constructionStock(MinecraftServer s){return Map.of();}
 static void require(boolean b,String text){if(!b)throw new AssertionError(text);}
 static void boundary(MinecraftServer s,String text){if(text.equals("l-compacted-loaded"))entries++;}
 static void record(String kind,Map<String,Object> data){if(kind.equals("phase"))moves++;}
 static void sealCanceledEvidence(MinecraftServer s,long tick){seals++;}
 static boolean bothContributed(){return true;}static Map<String,Integer>counts(){return Map.of();}
 static Set<Integer>pending(ServerLevel l){return Set.of(1);}
 static Object materialCensus(MinecraftServer s,Object o){return Map.of();}
 static void step(MinecraftServer server,ServerLevel level,long tick)throws Exception {switch(phase){
''' + cases + '\ncase FINISHED, L_EVICT -> {} }}\n' + block('    private static void move(') + '\n' + verify + '\n' + block('    private static void verifyCanceledRequests(') + '\n' + block('    private static void assertCanceledFreeze(') + '\n static void end(MinecraftServer server,ServerLevel level,long tick)throws Exception {' + deadline + 'step(server,level,tick); }\n' + '''
 public static void main(String[] args)throws Exception{
  var f=TerminalHarness.fixture();var worker=f.worker();var request=TerminalHarness.request(1);worker.neededItems.add(request);
  TerminalHarness.active(f,0,true);worker.data.present=true;worker.data.journal.put("NEEDED",1);WORKERS.put(worker.getUUID(),worker);
  CANCELED_REQUESTS.put(worker.getUUID(),requestObserver.requests(worker));CANCELED_STORAGE.put(worker.getUUID(),requestObserver.storage(worker));
  var list=worker.neededItems;var server=new MinecraftServer();var level=new ServerLevel();
  for(long tick:new long[]{101,200,600}){end(server,level,tick);require(phase==Phase.L_CANCEL_WAIT&&phaseAt==100&&moves==0&&entries==0,"Pending cleanup reset or entered final window");}
  require(worker.neededItems==list&&list.size()==1&&list.get(0)==request&&request.count==1,"Pending observation cleared requests");
  if(args[0].equals("pending")){System.out.println("PENDING_UNCHANGED");return;}
  if(args[0].equals("timeout")){
   end(server,level,1300);boolean rejected=false;try{end(server,level,1301);}catch(AssertionError e){require(e.getMessage().equals("Bounded later-stage phase timed out: L_CANCEL_WAIT"),"Unexpected timeout reason");rejected=true;}
   require(rejected&&phaseAt==100&&entries==0&&moves==0,"Pending cleanup extended timeout");System.out.println("ORIGINAL_TIMEOUT_RETAINED");return;
  }
  TerminalHarness.set(f.adapters().get(0),ProtectedInventoryGoal.class,"protectedLifecycle",false);f.session().pending.clear();TerminalHarness.owners(f).clear();worker.data.present=false;worker.data.journal.clear();
  end(server,level,601);require(phase==Phase.L_FINAL_STABLE&&phaseAt==601&&entries==1&&moves==1,"Natural closure did not enter final window once");
  if(args[0].equals("reopened")){
   f.wrapped().get(0).running=true;boolean rejected=false;
   try{end(server,level,602);}catch(AssertionError e){require(e.getMessage().equals("Closed terminal inventory lifecycle resumed"),"Unexpected rejection");rejected=true;}
   require(rejected&&phaseAt==601&&moves==1&&seals==0,"Reopened lifecycle accepted or timer reset");System.out.println("REOPEN_REJECTED");return;
  }
  for(long tick:new long[]{601,650,700}){end(server,level,tick);require(phase==Phase.L_FINAL_STABLE&&phaseAt==601&&entries==1&&moves==1&&seals==0,"Final window clock extended or shortened");}
  end(server,level,701);require(phase==Phase.FINISHED&&phaseAt==701&&entries==1&&moves==2&&seals==1&&action==Action.DONE,"Final window did not finish at exact 100 ticks");
  require(worker.neededItems==list&&list.size()==1&&list.get(0)==request&&request.count==1,"Terminal observation cleared request");
  end(server,level,702);require(seals==1&&moves==2,"Final window entered twice");System.out.println("NATURAL_CLOSURE_100_TICKS");
 }
}
'''


class TerminalDriverSequenceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        files = java_fixture_files()
        files[PACKAGE.replace('.', '/') + '/DriverSequenceHarness.java'] = driver_sequence_source()
        compile_java(cls, files)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def test_actual_cancel_wait_and_stable_branches_preserve_clocks_and_requests(self):
        for mode, expected in [('pending', 'PENDING_UNCHANGED'), ('natural', 'NATURAL_CLOSURE_100_TICKS'),
                               ('reopened', 'REOPEN_REJECTED'), ('timeout', 'ORIGINAL_TIMEOUT_RETAINED')]:
            with self.subTest(mode=mode):
                result = subprocess.run([self.java, '-cp', str(self.root), PACKAGE + '.DriverSequenceHarness', mode],
                                        capture_output=True, text=True, timeout=10)
                self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                self.assertEqual(result.stdout.strip(), expected)

    def test_pending_cleanup_is_not_preempted_by_the_old_empty_journal_assertion(self):
        source = (SOURCE.parent / 'NativeLaterStageHelperQa.java').read_text()
        verify = source.split('private static boolean verifyCanceled(', 1)[1].split('private static void conservation(', 1)[0]
        self.assertNotIn('ProtectedInventoryCleanup.read(worker.getPersistentData()).isEmpty()', verify)
        self.assertNotIn('neededItems.clear', source)
        self.assertNotRegex(source, r'\bneededItems\s*=(?!=)')



class TerminalObservationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        compile_java(cls, java_fixture_files())

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def run_modes(self, modes):
        result = subprocess.run([self.java, '-cp', str(self.root), PACKAGE + '.TerminalHarness', *modes],
                                capture_output=True, text=True, timeout=30)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        lines = dict(line.split('=', 1) for line in result.stdout.splitlines())
        self.assertEqual(set(lines), set(modes), 'Missing execution output')
        return lines

    def expect(self, prefix, modes, expected):
        for mode, result in self.run_modes([prefix + '/' + mode for mode in modes]).items():
            with self.subTest(mode=mode):
                if expected == 'REJECT':
                    self.assertTrue(result.startswith('REJECT:'), result)
                else:
                    self.assertEqual(result, expected)

    def test_empty_unresolved_resolved_cache_and_hostile_callbacks(self):
        self.expect('requests', ['empty', 'unresolved', 'resolved-null', 'resolved-item', 'resolved-oak', 'hostile', 'max64',
                                 'trusted-shape', 'retained'], 'ACCEPT')

    def test_exact_list_request_matcher_value_and_cache_negatives(self):
        self.expect('requests', ['null-list', 'custom-list', 'oversized', 'null-request', 'subclass-request',
                                 'wrong-request', 'duplicate', 'zero', 'negative', 'over64', 'not-required', 'source',
                                 'null-matcher', 'foreign-nest', 'ordinary-matcher', 'nonfinal-matcher',
                                 'nonsynthetic-matcher', 'foreign-loader', 'unresolved-item', 'resolved-foreign',
                                 'unregistered-item', 'resolved-other-registered'], 'REJECT')

    def test_all_post_cancel_request_identity_and_value_changes_fail_continuity(self):
        self.expect('requests', ['mutation-' + name for name in ('add', 'remove', 'reorder', 'count', 'list', 'request',
                                                               'matcher', 'cache-resolved', 'cache-item', 'cache-replaced')], 'CHANGED')
        self.expect('requests', ['mutation-source', 'mutation-required'], 'REJECT')

    def test_validated_field_metadata_reader_is_executed(self):
        self.expect('metadata', ['valid'], 'ACCEPT')
        self.expect('metadata', ['owner', 'type', 'modifiers', 'missing', 'static', 'final'], 'REJECT')

    def test_reference_registry_has_no_callbacks_aliases_gaps_or_mutable_catalog(self):
        self.expect('references', ['valid', 'immutable'], 'ACCEPT')
        self.expect('references', ['bound'], 'REJECT')

    def test_four_closed_wrappers_and_retained_state_are_passive(self):
        self.expect('storage', ['closed', 'hostile', 'retained'], 'CLOSED')
        self.expect('storage', ['natural-cleanup:' + str(i) for i in range(4)], 'NATURALLY_CLOSED')

    def test_each_wrapper_flags_and_pending_cleanup_are_observed_not_drained(self):
        self.expect('storage', [mode + ':' + str(i) for i in range(4)
                               for mode in ('running', 'active', 'pending', 'stop-failed', 'journal', 'review')], 'OPEN')
        self.expect('storage', ['draining', 'empty-journal'], 'OPEN')

    def test_each_wrapper_missing_duplicate_foreign_and_unguarded_is_rejected(self):
        self.expect('storage', [mode + ':' + str(i) for i in range(4)
                               for mode in ('missing', 'duplicate', 'started', 'protected-orphan', 'foreign-wrapper-worker',
                                            'foreign-session', 'null-session', 'foreign-delegate-worker', 'wrong-delegate',
                                            'subclass-delegate', 'unguarded', 'wrong-kind', 'custom-wrapped',
                                            'foreign-owner', 'owner-wrong-kind', 'owner-not-protected', 'pending-started')], 'REJECT')

    def test_actual_selector_field_metadata_drift_is_rejected_before_any_getter(self):
        from types import SimpleNamespace
        path = 'net/minecraft/world/entity/ai/goal/GoalSelector.java'
        variants = {
            'public': ('private final Set<WrappedGoal> availableGoals', 'public final Set<WrappedGoal> availableGoals'),
            'nonfinal': ('private final Set<WrappedGoal> availableGoals', 'private Set<WrappedGoal> availableGoals'),
            'type': ('private final Set<WrappedGoal> availableGoals', 'private final Collection<WrappedGoal> availableGoals'),
            'missing': ('private final Set<WrappedGoal> availableGoals', 'private final Set<WrappedGoal> missing'),
        }
        for mode, (old, new) in variants.items():
            with self.subTest(mode=mode):
                files = java_fixture_files()
                files[path] = files[path].replace(old, new)
                files[PACKAGE.replace('.', '/') + '/SelectorFieldHarness.java'] = 'package ' + PACKAGE + ";" + r'''
public class SelectorFieldHarness {public static void main(String[] args) {
 try {new NativeLaterStageTerminalObservation().storage(new com.talhanation.workers.entities.BuilderEntity());}
 catch(NativeLaterStageTerminalObservation.ObservationRejected rejected) {System.out.println(rejected.getMessage());return;}
 throw new AssertionError("Malformed selector metadata accepted");
}}'''
                fixture = SimpleNamespace()
                compile_java(fixture, files)
                try:
                    result = subprocess.run([fixture.java, '-cp', str(fixture.root), PACKAGE + '.SelectorFieldHarness'],
                                            capture_output=True, text=True, timeout=10)
                    self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                    expected = 'Missing native field: ' if mode == 'missing' else 'Native field metadata differs: '
                    self.assertEqual(result.stdout.strip(), expected + 'net.minecraft.world.entity.ai.goal.GoalSelector.availableGoals')
                finally:
                    fixture.temp.cleanup()

    def test_selector_and_set_callbacks_are_rejected_before_dispatch_with_observed_class(self):
        outputs = self.run_modes(['storage/custom-selector', 'storage/custom-goal-set', 'storage/custom-iterator-set',
                                  'storage/null-goal-set', 'storage/hash-goal-set'])
        self.assertEqual(outputs['storage/custom-selector'], 'REJECT:Unsupported native goal selector: ' + PACKAGE + '.TerminalHarness$CustomSelector')
        for mode, class_name in [('custom-goal-set', PACKAGE + '.TerminalHarness$CustomGoalSet'),
                                 ('custom-iterator-set', PACKAGE + '.TerminalHarness$CustomIteratorGoalSet'),
                                 ('null-goal-set', 'null'), ('hash-goal-set', 'java.util.HashSet')]:
            self.assertEqual(outputs['storage/' + mode], 'REJECT:Unsupported native available-goals collection: ' + class_name)

    def test_wrapper_and_delegate_replacement_cannot_pass_identity_continuity(self):
        self.expect('storage', [mode + ':' + str(i) for i in range(4)
                               for mode in ('replaced-wrapped', 'replaced-wrapper', 'replaced-delegate')], 'CHANGED')
        self.expect('storage', ['replaced-all'], 'CHANGED')

    def test_shared_session_and_cleanup_journal_failures(self):
        self.expect('storage', ['foreign-session-worker', 'stock-entity-delegate', 'null-wrapped', 'null-goal',
                               'too-many-goals', 'null-selector', 'custom-selector', 'null-goal-set', 'custom-goal-set', 'custom-iterator-set', 'hash-goal-set', 'foreign-pending', 'null-pending', 'pending-not-owner',
                               'foreign-upkeep', 'missing-upkeep', 'two-upkeep', 'orphan-journal', 'bad-journal',
                               'unknown-journal-value', 'null-pending-collection', 'custom-pending-collection', 'null-owners-collection',
                               'custom-owners-collection', 'foreign-owner-kind', 'null-owner'], 'REJECT')

    def test_pure_closed_and_identity_decisions_reject_missing_or_aliased_references(self):
        self.expect('pure', ['zero-worker', 'zero-session', 'aliased-session', 'foreign-session-worker', 'missing-wrapper',
                             'extra-wrapper', 'aliased-wrapper', 'aliased-delegate', 'aliased-wrapped', 'wrong-kind',
                             'wrong-class', 'missing-reference', 'negative-reference'], 'INVALID')

    def test_observer_source_has_no_authority_or_lifecycle_mutators(self):
        source = SOURCE.read_text()
        for forbidden in ('getMatchKey(', '.matches(', '.test(', '.toString()', '.getDeclaredFields(',
                          'getCaptured', 'BuiltInRegistries.', '.start(', '.stop(', '.tick(', '.ready(', '.install(', 'drainCleanup(',
                          'readyForRetirement(', 'ConstructionEditLedger.get(', 'PerimeterProjectAuthority.snapshot(',
                          'identityHashCode(', '.set(', '.setInt(', '.setBoolean('):
            with self.subTest(forbidden=forbidden):
                # UUID.toString is the only allowed serialization, never a callback on a request or cache.
                inspected = source.replace('((BuilderEntity) value).getUUID().toString()', 'UUID_TEXT')
                self.assertNotIn(forbidden, inspected)
        self.assertIn('IdentityHashMap<Object, Integer>', source)
        self.assertIn('getClass() == ArrayList.class', source)
        self.assertIn('selector.getClass() == GoalSelector.class', source)
        self.assertIn('field(GoalSelector.class, "availableGoals", Set.class,', source)
        self.assertNotIn('.getAvailableGoals(', source)
        self.assertIn('rawAvailable.getClass() == LinkedHashSet.class', source)
        self.assertIn('field.getModifiers() == modifiers', source)
        self.assertIn('ProtectedInventoryCleanup.read(data)', source)
        self.assertNotRegex(source, r'catch\s*\(\s*(?:Throwable|AssertionError|Error)')


if __name__ == '__main__':
    unittest.main()
