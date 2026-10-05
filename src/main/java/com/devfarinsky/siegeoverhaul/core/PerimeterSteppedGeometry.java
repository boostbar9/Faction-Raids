package com.devfarinsky.siegeoverhaul.core;

import java.util.*;
import static com.devfarinsky.siegeoverhaul.core.PerimeterSteppedProfile.*;
import static com.devfarinsky.siegeoverhaul.core.PerimeterSteppedTopology.*;

/** Pure rebuilt physical proposal. First checkpoint supports level seams and bounded fill only. */
public final class PerimeterSteppedGeometry {
    public static final int VERSION=1, PASSAGE_WIDTH=3, PASSAGE_HEIGHT=3, APPROACH_DEPTH=3;
    private PerimeterSteppedGeometry() {}
    public enum Block { COBBLESTONE, STONE_BRICKS, OAK_PLANKS, DIRT }
    public enum Phase { FILL, STRUCTURE }
    public enum Region { WALL, INSIDE_APPROACH, OUTSIDE_APPROACH }
    public record Ground(int surfaceY,boolean gradingAllowed,String problem) { public static Ground safe(int y){return new Ground(y,true,"");} }
    /** Caller supplies immutable read-only observations, never permission or a mutation callback. */
    public interface Terrain {
        Ground ground(Cell column);
        /** Null means exact three-high headroom and dry safe footing at these feet; WALL may use planned fill. */
        String passageProblem(Cell column,int feetY,Region region);
    }
    public record Pos(int x,int y,int z) implements Comparable<Pos>{
        public int compareTo(Pos b){int c=Integer.compare(x,b.x);if(c==0)c=Integer.compare(z,b.z);return c!=0?c:Integer.compare(y,b.y);}
        Cell column(){return new Cell(x,z);}
        Pos above(int dy){return new Pos(x,y+dy,z);}
    }
    public record Target(Block block,Phase phase,int component) {}
    public record Gate(int component,int band,Facing facing,Pos outerFeet,Set<Pos> passage,Set<Pos> inside,Set<Pos> outside,Set<Pos> footing){
        public Gate{passage=Set.copyOf(passage);inside=Set.copyOf(inside);outside=Set.copyOf(outside);footing=Set.copyOf(footing);}
    }
    public record Limits(int minY,int maxY,int maxFillDepth,int maxTargets,int maxObservations,int maxGroundReads,int maxPassageReads){
        public static final Limits DEFAULT=new Limits(-64,320,8,32768,65536,32768,16384);
        public Limits{if(minY< -2048||maxY>2048||maxY-minY<7||maxFillDepth<0||maxFillDepth>8||maxTargets<1||maxTargets>32768||maxObservations<1||maxObservations>65536||maxGroundReads<1||maxGroundReads>32768||maxPassageReads<1||maxPassageReads>16384)throw new IllegalArgumentException("Invalid physical proposal budget");}
    }
    public sealed interface Draft permits Result {
        Map<Pos,Target> targets(); Set<Pos> clearance(); Set<Pos> readOnlyFooting(); List<Gate> gates();
        List<SeamLevel> seams(); Map<Cell,Integer> levels(); int fillCells(); Map<Block,Integer> targetCounts(); String problem();
        default boolean feasible(){return problem().isEmpty()&&!targets().isEmpty();}
        default boolean executable(){return false;}
        default int geometryVersion(){return VERSION;}
        /** Canonical proposal content only, never original-state provenance or native item demand. Sorting is O(n log n); hashing is linear. */
        default String digest(){return feasible()?contentDigest(this):"";}
    }
    private record Result(Map<Pos,Target> targets,Set<Pos> clearance,Set<Pos> readOnlyFooting,List<Gate> gates,
                        List<SeamLevel> seams,Map<Cell,Integer> levels,int fillCells,Map<Block,Integer> targetCounts,String problem) implements Draft {
        private Result{targets=Collections.unmodifiableMap(new TreeMap<>(targets));clearance=Set.copyOf(clearance);readOnlyFooting=Set.copyOf(readOnlyFooting);gates=List.copyOf(gates);seams=List.copyOf(seams);levels=Map.copyOf(levels);targetCounts=Map.copyOf(targetCounts);}
        public boolean feasible(){return problem.isEmpty()&&!targets.isEmpty();}
        public boolean executable(){return false;}
    }
    public static Draft compile(Set<Chunk> claim,Terrain terrain,Block wall,Limits limits){
        if(terrain==null||limits==null)throw new IllegalArgumentException("Immutable terrain and limits are required");
        try{return build(claim,terrain,wall,limits);}catch(Refused refusal){return failed(refusal.getMessage());}
    }
    private record Candidate(int component,int loop,int band,Facing facing,Cell center,List<Cell> inside,List<Cell> outside) {}
    private record Probe(Cell column,int y,Region region) {}
    private static final class Observations {
        final Terrain terrain;final Limits limits;final Map<Cell,Ground> ground=new HashMap<>();final Map<Probe,Boolean> passages=new HashMap<>();
        Observations(Terrain terrain,Limits limits){this.terrain=terrain;this.limits=limits;}
        Ground ground(Cell p){
            if(!ground.containsKey(p)){
                need(ground.size()<limits.maxGroundReads,"Ground observation budget exhausted");
                Ground g;try{g=terrain.ground(p);}catch(RuntimeException|LinkageError unavailable){g=null;}
                ground.put(p,g==null?new Ground(0,false,"unavailable"):g);
            }
            Ground g=ground.get(p);need(g.problem()!=null&&g.problem().isEmpty()&&g.surfaceY()>limits.minY&&(long)g.surfaceY()+5<limits.maxY,"Unsafe or unavailable ground");return g;
        }
        boolean passage(Cell p,int y,Region region){var key=new Probe(p,y,region);if(passages.containsKey(key))return passages.get(key);need(passages.size()<limits.maxPassageReads,"Passage observation budget exhausted");boolean safe;try{safe=terrain.passageProblem(p,y,region)==null;}catch(RuntimeException|LinkageError unavailable){safe=false;}passages.put(key,safe);return safe;}
    }
    private static Draft build(Set<Chunk> claim,Terrain terrain,Block wall,Limits limits){
        need(wall==Block.COBBLESTONE||wall==Block.STONE_BRICKS||wall==Block.OAK_PLANKS,"Unsupported wall target state");
        Layout topology=PerimeterSteppedTopology.create(claim);need(topology.valid(),topology.problem());var observed=new Observations(terrain,limits);
        // Read every wall column once before candidate-dependent observations.
        topology.columns().keySet().forEach(observed::ground);
        Map<Integer,List<Candidate>> candidates=new TreeMap<>();int candidateCount=0;
        for(var loop:topology.loops())if(loop.outer())for(var band:loop.bands())if(band.kind()==Kind.STRAIGHT){
            Set<Cell> cells=new HashSet<>(band.cells());for(var center:band.cells())if(topology.columns().get(center).inwardDistance()==3)for(var facing:Facing.values()){
                Candidate c=candidate(topology,loop,band,cells,center,facing);if(c!=null){need(++candidateCount<=16384,"Gate geometry budget exhausted");candidates.computeIfAbsent(loop.component(),x->new ArrayList<>()).add(c);}
            }
        }
        List<Gate> gates=new ArrayList<>();Map<Integer,Gate> gateBands=new HashMap<>();
        for(int component:new TreeSet<>(topology.components().values()))for(Facing facing:Facing.values()){
            var options=candidates.getOrDefault(component,List.of()).stream().filter(c->c.facing==facing).sorted(candidateOrder(topology,component,facing)).toList();
            need(!options.isEmpty(),"No exterior "+facing+" gate fits component "+component);
            Gate gate=null;for(var option:options){if(gateBands.containsKey(option.band))continue;gate=gate(option,topology,observed);if(gate!=null)break;}
            need(gate!=null,"No unchanged dry level "+facing+" approach fits component "+component);gates.add(gate);gateBands.put(gate.band,gate);
        }
        List<Loop> profileLoops=new ArrayList<>();
        for(var loop:topology.loops()){
            List<Band> bands=new ArrayList<>();for(var band:loop.bands()){
                List<Sample> samples=new ArrayList<>();for(var p:band.cells()){Ground g=observed.ground(p);samples.add(new Sample(p.x(),p.z(),g.surfaceY,Role.WALL,g.gradingAllowed));}
                Gate gate=gateBands.get(band.id());if(gate!=null){for(var p:gate.inside)if(p.y==gate.outerFeet.y)samples.add(new Sample(p.x,p.z,p.y,Role.INSIDE_GATE_PAD,false));for(var p:gate.outside)if(p.y==gate.outerFeet.y)samples.add(new Sample(p.x,p.z,p.y,Role.OUTSIDE_GATE_PAD,false));}
                bands.add(new Band(band.id(),band.length(),gate==null?band.kind():Kind.GATE,gate==null?null:gate.facing,samples,true));
            }profileLoops.add(new Loop(loop.component(),loop.id(),loop.outer(),bands,loop.seams()));
        }
        var heightLimits=new PerimeterSteppedProfile.Limits(0,limits.maxFillDepth,Math.max(-2047,limits.minY+1),Math.min(2042,limits.maxY-6),4096,20480,4_000_000,0,limits.maxTargets,true);
        Proposal profile=propose(profileLoops,heightLimits);need(profile.heightFeasible(),"No bounded height profile: "+profile.problems());
        Map<Cell,Integer> levels=new TreeMap<>();for(var c:profile.columns())if(c.sample().role()==Role.WALL)levels.put(new Cell(c.sample().x(),c.sample().z()),c.baseY());
        Set<Pos> openings=new HashSet<>(),clearance=new HashSet<>(),footing=new HashSet<>();for(var gate:gates){openings.addAll(gate.passage);clearance.addAll(gate.inside);clearance.addAll(gate.outside);footing.addAll(gate.footing);}
        Map<Pos,Target> targets=new TreeMap<>();int fill=0;
        for(var entry:topology.columns().entrySet()){
            Cell p=entry.getKey();Column column=entry.getValue();int base=levels.get(p),surface=observed.ground(p).surfaceY;need(surface<=base&&base-surface<=limits.maxFillDepth,"Unsupported terrain cut or deep support");footing.add(new Pos(p.x(),surface-1,p.z()));
            for(int y=surface;y<base;y++){put(targets,new Pos(p.x(),y,p.z()),new Target(Block.DIRT,Phase.FILL,column.component()),limits);fill++;}
            boolean skin=column.inwardDistance()==1||column.inwardDistance()==5;
            for(int dy=0;dy<6;dy++){
                Pos pos=new Pos(p.x(),base+dy,p.z());Block block=dy==3?Block.OAK_PLANKS:skin&&(dy<3||dy==4)?wall:null;
                if(openings.contains(pos))block=null;
                if(block==null)clearance.add(pos);else put(targets,pos,new Target(block,Phase.STRUCTURE,column.component()),limits);
            }
        }
        need(Collections.disjoint(targets.keySet(),clearance)&&Collections.disjoint(targets.keySet(),footing),"Target collides with protected headroom or original footing");
        need(fill==profile.fillCells(),"Profile and unique physical fill counts differ");
        Set<Pos> all=new HashSet<>(targets.keySet());all.addAll(clearance);all.addAll(footing);need(all.size()<=limits.maxObservations,"Whole proposal exceeds observation budget");
        long minY=all.stream().mapToInt(Pos::y).min().orElseThrow(),maxY=all.stream().mapToInt(Pos::y).max().orElseThrow();
        long minX=all.stream().mapToInt(Pos::x).min().orElseThrow(),maxX=all.stream().mapToInt(Pos::x).max().orElseThrow(),minZ=all.stream().mapToInt(Pos::z).min().orElseThrow(),maxZ=all.stream().mapToInt(Pos::z).max().orElseThrow();
        need((maxX-minX+1)*(maxZ-minZ+1)*(maxY-minY+1)<=1_048_576,"Whole physical envelope exceeds native volume budget");
        checkWalk(topology,levels,targets,clearance);
        Map<Block,Integer> counts=new EnumMap<>(Block.class);targets.values().forEach(t->counts.merge(t.block,1,Integer::sum));
        return new Result(targets,clearance,footing,gates,profile.seams(),levels,fill,counts,"");
    }
    private static Candidate candidate(Layout t,WalkLoop loop,BandFootprint band,Set<Cell> bandCells,Cell center,Facing facing){
        int dx=dx(facing),dz=dz(facing);List<Cell> inside=new ArrayList<>(),outside=new ArrayList<>();
        for(int across=-1;across<=1;across++)for(int depth=-5;depth<=5;depth++){
            Cell p=center.add(dx*depth-dz*across,dz*depth+dx*across);Column c=t.columns().get(p);
            if(Math.abs(depth)<=2){if(!bandCells.contains(p)||c==null||c.component()!=loop.component()||c.inwardDistance()!=3-depth)return null;}
            else{if(c!=null)return null;if(depth<0){if(!Objects.equals(t.components().get(p.chunk()),loop.component()))return null;inside.add(p);}else{if(!t.exterior().contains(p.chunk())||t.components().containsKey(p.chunk()))return null;outside.add(p);}}
        }return new Candidate(loop.component(),loop.id(),band.id(),facing,center,List.copyOf(inside),List.copyOf(outside));
    }
    private static Gate gate(Candidate c,Layout topology,Observations observed){
        int level;try{level=observed.ground(c.outside.get(0)).surfaceY;}catch(Refused unsafe){if(unsafe.getMessage().contains("budget"))throw unsafe;return null;}
        for(var p:c.inside)if(!pad(p,level,Region.INSIDE_APPROACH,observed))return null;for(var p:c.outside)if(!pad(p,level,Region.OUTSIDE_APPROACH,observed))return null;
        Set<Pos> passage=new HashSet<>(),inside=new HashSet<>(),outside=new HashSet<>(),footing=new HashSet<>();int dx=dx(c.facing),dz=dz(c.facing);
        for(int across=-1;across<=1;across++)for(int depth=-2;depth<=2;depth++){Cell p=c.center.add(dx*depth-dz*across,dz*depth+dx*across);Ground g=observed.ground(p);if(g.surfaceY>level||level-g.surfaceY>observed.limits.maxFillDepth||!observed.passage(p,level,Region.WALL))return null;for(int y=0;y<3;y++)passage.add(new Pos(p.x(),level+y,p.z()));}
        for(var p:c.inside){for(int y=0;y<3;y++)inside.add(new Pos(p.x(),level+y,p.z()));footing.add(new Pos(p.x(),level-1,p.z()));}for(var p:c.outside){for(int y=0;y<3;y++)outside.add(new Pos(p.x(),level+y,p.z()));footing.add(new Pos(p.x(),level-1,p.z()));}
        return new Gate(c.component,c.band,c.facing,new Pos(c.center.x()+2*dx,level,c.center.z()+2*dz),passage,inside,outside,footing);
    }
    private static boolean pad(Cell p,int y,Region region,Observations observed){try{return observed.ground(p).surfaceY==y&&observed.passage(p,y,region);}catch(Refused unsafe){if(unsafe.getMessage().contains("budget"))throw unsafe;return false;}}
    private static Comparator<Candidate> candidateOrder(Layout t,int component,Facing face){
        var chunks=t.components().entrySet().stream().filter(e->e.getValue()==component).map(Map.Entry::getKey).toList();long cx=16L*(chunks.stream().mapToInt(Chunk::x).min().orElseThrow()+chunks.stream().mapToInt(Chunk::x).max().orElseThrow())+15,cz=16L*(chunks.stream().mapToInt(Chunk::z).min().orElseThrow()+chunks.stream().mapToInt(Chunk::z).max().orElseThrow())+15;
        return Comparator.comparingInt((Candidate c)->-c.center.x()*dx(face)-c.center.z()*dz(face)).thenComparingLong(c->Math.abs(2L*(dx(face)==0?c.center.x():c.center.z())-(dx(face)==0?cx:cz))).thenComparing(Candidate::center);
    }
    private static void checkWalk(Layout t,Map<Cell,Integer> levels,Map<Pos,Target> targets,Set<Pos> clearance){
        for(var e:t.columns().entrySet())if(e.getValue().inwardDistance()>=2&&e.getValue().inwardDistance()<=4){Cell p=e.getKey();int base=levels.get(p);Pos deck=new Pos(p.x(),base+3,p.z());need(targets.get(deck)!=null&&targets.get(deck).block==Block.OAK_PLANKS&&clearance.contains(deck.above(1))&&clearance.contains(deck.above(2)),"Walk deck/headroom is incomplete");
            for(var face:Facing.values()){
                Cell q=p.add(dx(face),dz(face));Column neighbor=t.columns().get(q);need(neighbor!=null,"Walk has an exposed edge");
                Integer other=levels.get(q);need(other!=null&&Math.abs(other-base)<=1,"Adjacent walk/skin levels are disconnected");
                if(neighbor.inwardDistance()==1||neighbor.inwardDistance()==5)need(targets.containsKey(new Pos(q.x(),other+4,q.z())),"Walk is missing its safety rail");
                else need(targets.get(new Pos(q.x(),other+3,q.z()))!=null&&targets.get(new Pos(q.x(),other+3,q.z())).block==Block.OAK_PLANKS
                        &&clearance.contains(new Pos(q.x(),other+4,q.z()))&&clearance.contains(new Pos(q.x(),other+5,q.z())),
                        "Stepped walk transition lacks deck or headroom");
            }
        }
    }
    private static int dx(Facing f){return f==Facing.EAST?1:f==Facing.WEST?-1:0;}private static int dz(Facing f){return f==Facing.SOUTH?1:f==Facing.NORTH?-1:0;}
    private static void put(Map<Pos,Target> targets,Pos p,Target t,Limits limits){need(p.y>limits.minY&&p.y<limits.maxY,"Target exceeds world height");need(targets.putIfAbsent(p,t)==null,"Duplicate physical mutation ownership");need(targets.size()<=limits.maxTargets,"Whole proposal exceeds target budget");}
    private static Draft failed(String problem){return new Result(Map.of(),Set.of(),Set.of(),List.of(),List.of(),Map.of(),0,Map.of(),problem);}
    private static String contentDigest(Draft draft){
        try {
            var hash=java.security.MessageDigest.getInstance("SHA-256");
            var out=new java.io.DataOutputStream(new java.security.DigestOutputStream(java.io.OutputStream.nullOutputStream(),hash));
            out.writeUTF("siege-rebuilt-physical-proposal");out.writeInt(VERSION);out.writeInt(draft.fillCells());
            out.writeInt(draft.targets().size());for(var e:new TreeMap<>(draft.targets()).entrySet()){write(out,e.getKey());out.writeInt(e.getValue().block.ordinal());out.writeInt(e.getValue().phase.ordinal());out.writeInt(e.getValue().component);}
            write(out,draft.clearance());write(out,draft.readOnlyFooting());
            out.writeInt(draft.levels().size());for(var e:new TreeMap<>(draft.levels()).entrySet()){out.writeInt(e.getKey().x());out.writeInt(e.getKey().z());out.writeInt(e.getValue());}
            out.writeInt(draft.gates().size());for(var gate:draft.gates()){out.writeInt(gate.component);out.writeInt(gate.band);out.writeInt(gate.facing.ordinal());write(out,gate.outerFeet);write(out,gate.passage);write(out,gate.inside);write(out,gate.outside);write(out,gate.footing);}
            out.writeInt(draft.seams().size());for(var level:draft.seams()){out.writeInt(level.componentId());out.writeInt(level.loopId());out.writeInt(level.fromY());out.writeInt(level.toY());var seam=level.seam();out.writeInt(seam.fromBand());out.writeInt(seam.toBand());out.writeInt(seam.direction().ordinal());out.writeInt(seam.lanes().size());for(var lane:seam.lanes()){out.writeInt(lane.fromX());out.writeInt(lane.fromZ());out.writeInt(lane.toX());out.writeInt(lane.toZ());}}
            for(var block:Block.values())out.writeInt(draft.targetCounts().getOrDefault(block,0));out.flush();return HexFormat.of().formatHex(hash.digest());
        }catch(java.io.IOException|java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static void write(java.io.DataOutputStream out,Pos p)throws java.io.IOException{out.writeInt(p.x);out.writeInt(p.y);out.writeInt(p.z);}
    private static void write(java.io.DataOutputStream out,Set<Pos> cells)throws java.io.IOException{out.writeInt(cells.size());for(var p:new TreeSet<>(cells))write(out,p);}
    private static void need(boolean condition,String reason){if(!condition)throw new Refused(reason);}private static final class Refused extends RuntimeException{Refused(String reason){super(reason);}}
}
