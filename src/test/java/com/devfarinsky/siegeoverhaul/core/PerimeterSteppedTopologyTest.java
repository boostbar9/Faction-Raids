package com.devfarinsky.siegeoverhaul.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.devfarinsky.siegeoverhaul.core.PerimeterSteppedTopology.*;
import static com.devfarinsky.siegeoverhaul.core.PerimeterSteppedProfile.*;

class PerimeterSteppedTopologyTest {
    @Test void squareHasExactFiveWideClosedBands(){
        var layout=create(Set.of(new Chunk(0,0)));assertTrue(layout.valid(),layout.problem());assertFalse(layout.executable());assertEquals(220,layout.columns().size());assertEquals(1,layout.loops().size());assertEquals(44,layout.loops().get(0).centerline().size());assertEquals(8,layout.loops().get(0).bands().size());profileAccepts(layout);
    }
    @Test void irregularDisconnectedAndHoleLoopsKeepTheirOwnBoundary(){
        for(var claim:List.of(Set.of(new Chunk(0,0),new Chunk(1,0),new Chunk(0,1)),Set.of(new Chunk(-3,-2),new Chunk(0,0)),Set.of(new Chunk(0,0),new Chunk(1,1)))){
            var layout=create(claim);assertTrue(layout.valid(),layout.problem());profileAccepts(layout);for(var p:layout.columns().keySet())assertTrue(claim.contains(p.chunk()));
        }
        var ring=new HashSet<Chunk>();for(int x=0;x<3;x++)for(int z=0;z<3;z++)if(x!=1||z!=1)ring.add(new Chunk(x,z));var hole=create(ring);assertTrue(hole.valid(),hole.problem());assertEquals(2,hole.loops().size());assertEquals(1,hole.loops().stream().filter(WalkLoop::outer).count());assertFalse(hole.exterior().contains(new Chunk(1,1)));profileAccepts(hole);
    }
    @Test void allThreeByThreeClaimPatternsAreAtomicAndDeterministic(){
        int successes=0;for(int mask=1;mask<512;mask++){
            List<Chunk> cells=new ArrayList<>();for(int i=0;i<9;i++)if((mask&(1<<i))!=0)cells.add(new Chunk(i%3,i/3));
            var first=create(new LinkedHashSet<>(cells));Collections.reverse(cells);assertEquals(first,create(new LinkedHashSet<>(cells)),"mask "+mask);
            if(first.valid()){successes++;profileAccepts(first);Set<Cell> owned=new HashSet<>();for(var loop:first.loops())for(var band:loop.bands())for(var p:band.cells())assertTrue(owned.add(p));assertEquals(first.columns().keySet(),owned);}
            else{assertTrue(first.columns().isEmpty());assertTrue(first.loops().isEmpty());assertFalse(first.problem().isBlank());}
        }assertTrue(successes>400,"Common irregular patterns must remain feasible");
    }
    @Test void footprintEqualsAnIndependentLocalDistanceOracle(){
        var claim=Set.of(new Chunk(-1,0),new Chunk(0,0),new Chunk(0,1));var layout=create(claim);assertTrue(layout.valid(),layout.problem());
        for(var chunk:claim)for(int x=0;x<16;x++)for(int z=0;z<16;z++){
            Cell p=new Cell(chunk.x()*16+x,chunk.z()*16+z);int distance=6;
            for(int dx=-5;dx<=5;dx++)for(int dz=-5;dz<=5;dz++)if(!claim.contains(p.add(dx,dz).chunk()))distance=Math.min(distance,Math.max(Math.abs(dx),Math.abs(dz)));
            if(distance<=5)assertEquals(distance,layout.columns().get(p).inwardDistance());else assertFalse(layout.columns().containsKey(p));
        }
    }
    @Test void malformedOrUnboundedClaimsExposeNoPartialGeometry(){
        var nullable=new HashSet<Chunk>();nullable.add(null);
        for(Set<Chunk> bad:Arrays.asList(null,Set.<Chunk>of(),nullable,Set.of(new Chunk(0,0),new Chunk(16,0)),Set.of(new Chunk(Integer.MAX_VALUE,0)))){var result=create(bad);assertFalse(result.valid());assertTrue(result.columns().isEmpty());assertTrue(result.components().isEmpty());}
    }
    private static void profileAccepts(Layout layout){
        List<Loop> loops=new ArrayList<>();for(var loop:layout.loops()){
            List<Band> bands=new ArrayList<>();for(var band:loop.bands())bands.add(new Band(band.id(),band.length(),band.kind(),null,band.cells().stream().map(p->new Sample(p.x(),p.z(),64,Role.WALL,false)).toList(),false));
            loops.add(new Loop(loop.component(),loop.id(),loop.outer(),bands,loop.seams()));
        }
        var limits=new Limits(0,0,-63,314,4096,20_480,4_000_000,0,0,false);var result=propose(loops,limits);assertTrue(result.heightFeasible(),result.problems().toString());assertTrue(result.transitions().isEmpty());
    }
}
