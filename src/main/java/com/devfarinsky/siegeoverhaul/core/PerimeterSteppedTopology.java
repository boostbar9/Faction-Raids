package com.devfarinsky.siegeoverhaul.core;

import java.util.*;
import static com.devfarinsky.siegeoverhaul.core.PerimeterSteppedProfile.*;

/** Newly rebuilt pure claim topology. No world reads, accepted-plan migration or work authority. */
public final class PerimeterSteppedTopology {
    public static final int VERSION=1, MAX_CHUNKS=4096, MAX_SPAN=256, MAX_COLUMNS=20_480, MAX_BANDS=4096;
    private PerimeterSteppedTopology() {}
    public record Chunk(int x,int z) implements Comparable<Chunk>{public int compareTo(Chunk b){int c=Integer.compare(x,b.x);return c!=0?c:Integer.compare(z,b.z);}}
    public record Cell(int x,int z) implements Comparable<Cell>{
        public int compareTo(Cell b){int c=Integer.compare(x,b.x);return c!=0?c:Integer.compare(z,b.z);}
        Cell add(int x,int z){return new Cell(this.x+x,this.z+z);}
        Chunk chunk(){return new Chunk(Math.floorDiv(x,16),Math.floorDiv(z,16));}
    }
    public record Column(int component,int inwardDistance) {}
    public record BandFootprint(int id,int length,Kind kind,List<Cell> cells){public BandFootprint{cells=List.copyOf(cells);}}
    public record WalkLoop(int component,int id,boolean outer,List<Cell> centerline,List<BandFootprint> bands,List<Seam> seams){
        public WalkLoop{centerline=List.copyOf(centerline);bands=List.copyOf(bands);seams=List.copyOf(seams);}
    }
    public record Layout(Map<Chunk,Integer> components,Set<Chunk> exterior,Map<Cell,Column> columns,List<WalkLoop> loops,String problem){
        public Layout{components=Map.copyOf(components);exterior=Set.copyOf(exterior);columns=Collections.unmodifiableMap(new TreeMap<>(columns));loops=List.copyOf(loops);}
        public boolean valid(){return problem.isEmpty()&&!loops.isEmpty();}
        public boolean executable(){return false;}
    }
    private static final int[][] DIRS={{0,-1},{1,0},{0,1},{-1,0}};
    public static Layout create(Set<Chunk> input){
        try{return build(input);}catch(Refused refused){return new Layout(Map.of(),Set.of(),Map.of(),List.of(),refused.getMessage());}
    }
    private static Layout build(Set<Chunk> input){
        need(input!=null&&!input.isEmpty()&&input.size()<=MAX_CHUNKS,"A bounded nonempty claim is required");
        need(input.stream().noneMatch(Objects::isNull),"Null claim chunk");var claim=new TreeSet<>(input);
        int minX=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        for(var c:claim){long x=16L*c.x,z=16L*c.z;need(x>=-29_999_984&&z>=-29_999_984&&x+15<29_999_984&&z+15<29_999_984,"Claim exceeds the bounded world envelope");minX=Math.min(minX,c.x);minZ=Math.min(minZ,c.z);maxX=Math.max(maxX,c.x);maxZ=Math.max(maxZ,c.z);}
        need(16L*(maxX-minX+1)<=MAX_SPAN&&16L*(maxZ-minZ+1)<=MAX_SPAN,"Claim exceeds the256-block planning span");
        Map<Chunk,Integer> components=new TreeMap<>();int component=0;
        for(var seed:claim)if(!components.containsKey(seed)){var queue=new ArrayDeque<Chunk>();queue.add(seed);components.put(seed,component);
            while(!queue.isEmpty()){var c=queue.remove();for(var d:DIRS){var next=new Chunk(c.x+d[0],c.z+d[1]);if(claim.contains(next)&&components.putIfAbsent(next,component)==null)queue.add(next);}}component++;}
        Set<Chunk> exterior=new HashSet<>();var queue=new ArrayDeque<Chunk>();var seed=new Chunk(minX-1,minZ-1);queue.add(seed);exterior.add(seed);
        while(!queue.isEmpty()){var c=queue.remove();for(var d:DIRS){var n=new Chunk(c.x+d[0],c.z+d[1]);if(n.x>=minX-1&&n.x<=maxX+1&&n.z>=minZ-1&&n.z<=maxZ+1&&!claim.contains(n)&&exterior.add(n))queue.add(n);}}
        Map<Cell,Column> columns=new TreeMap<>();
        // Same five Chebyshev-distance layers as the published PerimeterBlueprint,
        // including diagonal absent chunks at inward corners.
        for(var c:claim)for(int x=0;x<16;x++)for(int z=0;z<16;z++){
            int distance=16;for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if(!claim.contains(new Chunk(c.x+dx,c.z+dz))){int gx=dx<0?x+1:dx>0?16-x:0,gz=dz<0?z+1:dz>0?16-z:0;distance=Math.min(distance,Math.max(gx,gz));}
            if(distance<=5){columns.put(new Cell(c.x*16+x,c.z*16+z),new Column(components.get(c),distance));need(columns.size()<=MAX_COLUMNS,"Wall footprint exceeds column budget");}
        }
        Set<Cell> centers=new TreeSet<>();columns.forEach((p,c)->{if(c.inwardDistance==3)centers.add(p);});
        Map<Cell,List<Cell>> adjacent=new TreeMap<>();
        for(var p:centers){var next=new ArrayList<Cell>();for(var d:DIRS)if(centers.contains(p.add(d[0],d[1])))next.add(p.add(d[0],d[1]));Collections.sort(next);need(next.size()==2,"Claim cannot fit an unbranched three-lane walk");adjacent.put(p,List.copyOf(next));}
        List<WalkLoop> loops=new ArrayList<>();Set<Cell> seen=new HashSet<>(),owned=new HashSet<>();int bandId=0;
        for(var start:centers)if(!seen.contains(start)){
            List<Cell> path=new ArrayList<>();Cell previous=null,current=start;
            do{need(seen.add(current),"Walk revisits a different loop");path.add(current);var neighbors=adjacent.get(current);Cell next=neighbors.get(0).equals(previous)?neighbors.get(1):neighbors.get(0);previous=current;current=next;need(path.size()<=MAX_COLUMNS,"Walk exceeds traversal budget");}while(!current.equals(start));
            int comp=columns.get(start).component;for(var p:path)need(columns.get(p).component==comp,"Walk crosses claim components");
            List<Integer> bends=new ArrayList<>();for(int i=0;i<path.size();i++){Cell a=path.get((i+path.size()-1)%path.size()),b=path.get(i),c=path.get((i+1)%path.size());if(c.x-b.x!=b.x-a.x||c.z-b.z!=b.z-a.z)bends.add(i);}
            need(bends.size()>=4,"A closed walk needs complete corners");
            List<BandFootprint> bands=new ArrayList<>();
            for(int b=0;b<bends.size();b++){
                int index=bends.get(b),next=bends.get((b+1)%bends.size());int distance=(next-index+path.size())%path.size();need(distance>=5,"Corners overlap their five-wide footprints");
                Cell corner=path.get(index);List<Cell> cells=new ArrayList<>();for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)cells.add(corner.add(x,z));
                bands.add(band(bandId++,5,Kind.CORNER,cells,comp,columns,owned));
                int remaining=distance-5;need(remaining==0||remaining>=3,"A short wall span cannot fit a complete band");
                int parts=(remaining+7)/8,offset=3;
                for(int n=0;n<parts;n++){int length=remaining/(parts-n);need(length>=3&&length<=8,"Invalid short wall partition");List<Cell> strip=new ArrayList<>();
                    for(int step=0;step<length;step++){Cell p=path.get((index+offset+step)%path.size()),q=path.get((index+offset+step+1)%path.size());int dx=q.x-p.x,dz=q.z-p.z;for(int lane=-2;lane<=2;lane++)strip.add(p.add(-dz*lane,dx*lane));}
                    bands.add(band(bandId++,length,Kind.STRAIGHT,strip,comp,columns,owned));remaining-=length;offset+=length;
                }
            }
            need(bandId<=MAX_BANDS,"Short-band budget exceeded");
            List<Seam> seams=new ArrayList<>();for(int i=0;i<bands.size();i++)seams.add(seam(bands.get(i),bands.get((i+1)%bands.size())));
            boolean outer=false;for(var p:path)for(var d:DIRS)if(exterior.contains(p.add(d[0]*3,d[1]*3).chunk()))outer=true;
            loops.add(new WalkLoop(comp,loops.size(),outer,path,bands,seams));
        }
        need(owned.equals(columns.keySet()),"Short bands do not cover every exact wall column once");
        for(int c=0;c<component;c++){int id=c;need(loops.stream().filter(l->l.component==id&&l.outer).count()==1,"Each component needs exactly one exterior walk loop");}
        return new Layout(components,exterior,columns,loops,"");
    }
    private static BandFootprint band(int id,int length,Kind kind,List<Cell> cells,int component,Map<Cell,Column> columns,Set<Cell> owned){
        cells.sort(Comparator.naturalOrder());need(new HashSet<>(cells).size()==length*5,"Repeated band columns");
        for(var p:cells){var c=columns.get(p);need(c!=null&&c.component==component&&owned.add(p),"Bands overlap or leave the claimed wall footprint");}
        return new BandFootprint(id,length,kind,cells);
    }
    private static Seam seam(BandFootprint from,BandFootprint to){
        Set<Cell> end=new HashSet<>(to.cells);List<Lane> lanes=new ArrayList<>();
        for(var a:from.cells)for(var d:DIRS){Cell b=a.add(d[0],d[1]);if(end.contains(b))lanes.add(new Lane(a.x,a.z,b.x,b.z));}
        lanes.sort(Comparator.comparingInt(Lane::fromX).thenComparingInt(Lane::fromZ));need(lanes.size()==5,"A join needs exactly five neighboring lane pairs");
        Lane first=lanes.get(0);int dx=first.toX()-first.fromX(),dz=first.toZ()-first.fromZ();
        for(var lane:lanes)need(lane.toX()-lane.fromX()==dx&&lane.toZ()-lane.fromZ()==dz,"A seam changes direction");
        Facing direction=dx==1?Facing.EAST:dx==-1?Facing.WEST:dz==1?Facing.SOUTH:Facing.NORTH;return new Seam(from.id,to.id,direction,lanes);
    }
    private static void need(boolean condition,String reason){if(!condition)throw new Refused(reason);}
    private static final class Refused extends RuntimeException{Refused(String reason){super(reason);}}
}
