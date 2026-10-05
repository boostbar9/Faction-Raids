package com.devfarinsky.siegeoverhaul.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;
import static com.devfarinsky.siegeoverhaul.core.PerimeterSteppedTopology.*;
import static com.devfarinsky.siegeoverhaul.core.PerimeterSteppedGeometry.*;

class PerimeterSteppedGeometryTest {
    private static class Flat implements Terrain {
        final Map<Cell,Integer> reads=new HashMap<>();final Map<String,Integer> passageReads=new HashMap<>();
        final Function<Cell,Ground> surface;
        Flat(){this(p->Ground.safe(64));}Flat(Function<Cell,Ground> surface){this.surface=surface;}
        public Ground ground(Cell p){reads.merge(p,1,Integer::sum);return surface.apply(p);}
        public String passageProblem(Cell p,int y,Region region){passageReads.merge(p+":"+y+":"+region,1,Integer::sum);return null;}
        void once(){assertTrue(reads.values().stream().allMatch(n->n==1));assertTrue(passageReads.values().stream().allMatch(n->n==1));}
    }
    @Test void exactSquareHasFourOpenThreeWideGatesAndAnIntactDeck(){
        var terrain=new Flat();var draft=compile(Set.of(new Chunk(0,0)),terrain,Block.STONE_BRICKS,Limits.DEFAULT);assertTrue(draft.feasible(),draft.problem());assertFalse(draft.executable());assertEquals(500,draft.targets().size());assertEquals(0,draft.fillCells());assertEquals(4,draft.gates().size());
        assertEquals(Set.of(PerimeterSteppedProfile.Facing.values()),draft.gates().stream().map(Gate::facing).collect(java.util.stream.Collectors.toSet()));
        for(var gate:draft.gates()){
            assertEquals(45,gate.passage().size());assertEquals(27,gate.inside().size());assertEquals(27,gate.outside().size());assertEquals(18,gate.footing().size());
            for(var p:gate.passage()){assertFalse(draft.targets().containsKey(p));assertTrue(draft.clearance().contains(p));assertEquals(Block.OAK_PLANKS,draft.targets().get(new Pos(p.x(),67,p.z())).block());}
            for(var p:gate.outside()){assertFalse(new Chunk(0,0).equals(p.column().chunk()));assertFalse(draft.targets().containsKey(p));}
        }
        assertTrue(draft.targets().keySet().stream().allMatch(p->p.column().chunk().equals(new Chunk(0,0))));assertEquals(draft.targets().size(),draft.targetCounts().values().stream().mapToInt(i->i).sum());terrain.once();
    }
    @Test void oneCellDipProducesOneDirtFillBeforeTheCompleteWall(){
        var terrain=new Flat(p->Ground.safe(p.equals(new Cell(0,0))?63:64));var result=compile(Set.of(new Chunk(0,0)),terrain,Block.COBBLESTONE,Limits.DEFAULT);assertTrue(result.feasible(),result.problem());assertEquals(1,result.fillCells());assertEquals(501,result.targets().size());
        assertEquals(new Target(Block.DIRT,Phase.FILL,0),result.targets().get(new Pos(0,63,0)));assertEquals(1,result.targets().values().stream().filter(t->t.phase()==Phase.FILL).count());assertTrue(result.readOnlyFooting().contains(new Pos(0,62,0)));assertEquals(64,result.levels().get(new Cell(0,0)));terrain.once();
    }
    @Test void irregularHolesAndDisconnectedComponentsNeverPutGatesOnInternalEdges(){
        var ring=new HashSet<Chunk>();for(int x=0;x<3;x++)for(int z=0;z<3;z++)if(x!=1||z!=1)ring.add(new Chunk(x,z));
        for(var claim:List.of(Set.of(new Chunk(0,0),new Chunk(1,0),new Chunk(0,1)),ring,Set.of(new Chunk(-3,-2),new Chunk(1,-2)))){
            var topology=create(claim);var draft=compile(claim,new Flat(),Block.STONE_BRICKS,Limits.DEFAULT);assertTrue(draft.feasible(),draft.problem());assertEquals(4*new HashSet<>(topology.components().values()).size(),draft.gates().size());
            for(var gate:draft.gates())for(var p:gate.outside())assertTrue(topology.exterior().contains(p.column().chunk()));
            for(var p:draft.targets().keySet())assertTrue(claim.contains(p.column().chunk()));assertTrue(Collections.disjoint(draft.targets().keySet(),draft.readOnlyFooting()));
        }
    }
    @Test void differentDisconnectedGroundLevelsStayIndependent(){
        var terrain=new Flat(p->Ground.safe(p.x()>=40?70:64));var draft=compile(Set.of(new Chunk(0,0),new Chunk(3,0)),terrain,Block.OAK_PLANKS,Limits.DEFAULT);assertTrue(draft.feasible(),draft.problem());assertEquals(8,draft.gates().size());assertEquals(64,draft.levels().get(new Cell(0,0)));assertEquals(70,draft.levels().get(new Cell(48,0)));assertEquals(0,draft.fillCells());
    }
    @Test void blockedOutsideWaterCliffAndUnsafeGroundRefuseTheEntireProposal(){
        for(int variant=0;variant<3;variant++){
            int which=variant;var terrain=new Flat(p->p.z()<0&&which==2?new Ground(0,false,"unsafe"):Ground.safe(p.z()<0&&which==1?80:64)){
                @Override public String passageProblem(Cell p,int y,Region role){return role==Region.OUTSIDE_APPROACH&&p.z()<0&&which==0?"water":super.passageProblem(p,y,role);}
            };
            var result=compile(Set.of(new Chunk(0,0)),terrain,Block.STONE_BRICKS,Limits.DEFAULT);atomicFailure(result);terrain.once();
        }
    }
    @Test void solverSelectedStepsAreRefusedWithoutAComponentwideFlatFallback(){
        Set<Chunk> claim=new HashSet<>();for(int x=0;x<4;x++)for(int z=0;z<4;z++)claim.add(new Chunk(x,z));var topology=create(claim);assertTrue(topology.valid());
        var low=topology.loops().get(0).bands().stream().filter(b->b.kind()==PerimeterSteppedProfile.Kind.STRAIGHT).skip(1).findFirst().orElseThrow();Set<Cell> dip=new HashSet<>(low.cells());
        var result=compile(claim,new Flat(p->Ground.safe(dip.contains(p)?63:64)),Block.STONE_BRICKS,Limits.DEFAULT);atomicFailure(result);assertTrue(result.problem().contains("Non-level seams"),result.problem());
    }
    @Test void sharedReadTargetAndObservationCapsAreAtomic(){
        for(var limits:List.of(new Limits(-64,320,8,499,65536,32768,16384),new Limits(-64,320,8,32768,100,32768,16384),new Limits(-64,320,8,32768,65536,219,16384),new Limits(-64,320,8,32768,65536,32768,1))){var terrain=new Flat();atomicFailure(compile(Set.of(new Chunk(0,0)),terrain,Block.STONE_BRICKS,limits));terrain.once();}
        atomicFailure(compile(Set.of(new Chunk(0,0)),new Flat(p->Ground.safe(p.equals(new Cell(0,0))?55:64)),Block.STONE_BRICKS,Limits.DEFAULT));
    }
    @Test void claimIterationAndTargetMaterialDoNotChangeGatePlacement(){
        var a=new LinkedHashSet<>(List.of(new Chunk(0,0),new Chunk(1,0),new Chunk(0,1)));var b=new LinkedHashSet<>(List.of(new Chunk(0,1),new Chunk(1,0),new Chunk(0,0)));
        var first=compile(a,new Flat(),Block.STONE_BRICKS,Limits.DEFAULT);var again=compile(b,new Flat(),Block.STONE_BRICKS,Limits.DEFAULT);assertTrue(first.feasible(),first.problem());assertEquals(first,again);assertEquals(first.gates(),compile(a,new Flat(),Block.COBBLESTONE,Limits.DEFAULT).gates());
    }
    @Test void canonicalIdentityBindsTargetsGatesSeamsAndChangedTerrain(){
        var claim=Set.of(new Chunk(0,0));var first=compile(claim,new Flat(),Block.STONE_BRICKS,Limits.DEFAULT);var repeat=compile(claim,new Flat(),Block.STONE_BRICKS,Limits.DEFAULT);
        assertTrue(first.feasible());assertEquals(1,first.geometryVersion());assertTrue(first.digest().matches("[0-9a-f]{64}"));assertEquals(first.digest(),repeat.digest());
        assertNotEquals(first.digest(),compile(claim,new Flat(),Block.COBBLESTONE,Limits.DEFAULT).digest());
        assertNotEquals(first.digest(),compile(claim,new Flat(p->Ground.safe(p.equals(new Cell(0,0))?63:64)),Block.STONE_BRICKS,Limits.DEFAULT).digest());
        assertTrue(Draft.class.isSealed());assertThrows(UnsupportedOperationException.class,()->first.targets().clear());
    }
    @Test void unavailableTerrainCallbacksFailClosedWithoutPartialOutput(){
        atomicFailure(compile(Set.of(new Chunk(0,0)),new Flat(p->{throw new LinkageError("missing terrain adapter");}),Block.STONE_BRICKS,Limits.DEFAULT));
        var terrain=new Flat(){@Override public String passageProblem(Cell p,int y,Region r){throw new LinkageError("missing passage adapter");}};
        atomicFailure(compile(Set.of(new Chunk(0,0)),terrain,Block.STONE_BRICKS,Limits.DEFAULT));
    }
    @Test void disconnectedComponentsShareOneObservationAndMutationBudget(){
        Set<Chunk> claim=new HashSet<>();for(int i=0;i<36;i++)claim.add(new Chunk((i%8)*2,(i/8)*2));var terrain=new Flat();
        var result=compile(claim,terrain,Block.STONE_BRICKS,Limits.DEFAULT);assertTrue(result.feasible(),result.problem());assertEquals(144,result.gates().size());assertEquals(18000,result.targets().size());terrain.once();
        claim.add(new Chunk(8,8));var refused=compile(claim,new Flat(),Block.STONE_BRICKS,Limits.DEFAULT);atomicFailure(refused);assertTrue(refused.problem().contains("observation budget"),refused.problem());
    }
    private static void atomicFailure(Draft draft){assertFalse(draft.feasible());assertTrue(draft.targets().isEmpty());assertTrue(draft.gates().isEmpty());assertTrue(draft.clearance().isEmpty());assertTrue(draft.readOnlyFooting().isEmpty());assertTrue(draft.levels().isEmpty());assertFalse(draft.problem().isBlank());}
}
