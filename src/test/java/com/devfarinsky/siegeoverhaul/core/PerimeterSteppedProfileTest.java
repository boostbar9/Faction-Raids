package com.devfarinsky.siegeoverhaul.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.devfarinsky.siegeoverhaul.core.PerimeterSteppedProfile.*;
import static com.devfarinsky.siegeoverhaul.core.PerimeterSteppedProfileFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class PerimeterSteppedProfileTest {
    @Test void followsLocalTerrainWithoutRaisingTheWholeComponentToItsHighestPoint() {
        int[] ground = {64,64,65,65,65,66,66,66,65,65,65,64,64,64,63,63,63,62,62,62,63,63,63,64};
        var loop = ring(0, 0, 0, 0, 0, 5, true, true, ground);
        var result = propose(List.of(loop));
        assertTrue(result.heightFeasible(), result.problems().toString());
        assertEquals(0, result.cutCells()); assertEquals(0, result.fillCells()); assertEquals(8, result.transitions().size());
        assertEquals(24, result.seams().size()); assertFalse(result.executable());
        for (int i = 0; i < ground.length; i++) assertEquals(ground[i], result.bands().get(i).baseY());
        long oldComponentMaxFill = loop.bands().stream().flatMap(b -> b.samples().stream())
                .filter(s -> s.role() == Role.WALL).mapToLong(s -> 66 - s.surfaceY()).sum();
        assertTrue(oldComponentMaxFill > 500, "The old component maximum creates substantial unnecessary support");
        for (var transition : result.transitions()) {
            assertEquals(1, Math.abs(transition.fromY() - transition.toY())); assertEquals(5, transition.seam().lanes().size());
            assertEquals(Kind.STRAIGHT, loop.bands().get(transition.fromBand()).kind());
            assertEquals(Kind.STRAIGHT, loop.bands().get(transition.toBand()).kind());
        }
    }

    @Test void levelCornersAndFourGatePadsRemainPinnedAndCannotHideAHeightConflict() {
        int[] ground = level(24, 64); ground[9] = 67; // Adjacent north/east gate pads need more than the two available rises.
        var result = propose(List.of(ring(0, 0, 0, 0, 0, 5, true, true, ground)));
        assertBlocked(result, ProblemCode.NO_HEIGHT_PROFILE);
    }

    @Test void conflictingInsideOutsidePadHeightsRejectBeforeAnyPropagationOrFallback() {
        var original = ring(0, 0, 0, 0, 0, 5, true, true, level(24, 64));
        var bands = new ArrayList<>(original.bands()); var gate = bands.get(3); var samples = new ArrayList<>(gate.samples());
        for (int i = 0; i < samples.size(); i++) if (samples.get(i).role() == Role.OUTSIDE_GATE_PAD) {
            Sample old = samples.get(i); samples.set(i, new Sample(old.x(), old.z(), 65, old.role(), false)); break;
        }
        bands.set(3, new Band(gate.id(), gate.length(), gate.kind(), gate.facing(), samples, gate.stepAfter()));
        assertBlocked(propose(List.of(new Loop(0, 0, true, bands, original.seams()))), ProblemCode.NO_HEIGHT_PROFILE);
    }

    @Test void closingSeamIsMandatoryAndCannotBecomeAnUnreportedCliff() {
        int[] ground = level(12, 64); ground[11] = 65;
        var result = propose(List.of(ring(0, 0, 0, 0, 0, 2, true, false, ground)), limits(0,0,10000,100,100,false));
        assertBlocked(result, ProblemCode.NO_HEIGHT_PROFILE);
    }

    @Test void missingBrokenOrWrongDirectionSeamsFailClosed() {
        var loop = ring(0, 0, 0, 0, 0, 2, true, false, level(12,64));
        var seams = new ArrayList<>(loop.seams()); seams.remove(0);
        assertBlocked(propose(List.of(new Loop(0,0,true,loop.bands(),seams)), limits(1,1,10000,1000,1000,false)), ProblemCode.INVALID_INPUT);
        seams = new ArrayList<>(loop.seams()); var first = seams.get(0);
        seams.set(0, new Seam(first.fromBand(),first.toBand(),Facing.WEST,first.lanes()));
        assertBlocked(propose(List.of(new Loop(0,0,true,loop.bands(),seams)), limits(1,1,10000,1000,1000,false)), ProblemCode.INVALID_INPUT);
        seams = new ArrayList<>(loop.seams()); seams.set(0,new Seam(first.fromBand(),first.toBand(),first.direction(),first.lanes().subList(0,4)));
        assertBlocked(propose(List.of(new Loop(0,0,true,loop.bands(),seams)), limits(1,1,10000,1000,1000,false)), ProblemCode.INVALID_INPUT);
    }

    @Test void disconnectedComponentsAndAnInnerLoopKeepIndependentLocalLevels() {
        var first = ring(0,0,0,-80,-40,15,true,true,level(64,64));
        var hole = ring(0,1,1000,-65,-25,5,false,false,level(24,69));
        var separate = ring(1,0,2000,20,20,5,true,true,level(24,72));
        var result = propose(List.of(separate,hole,first));
        assertTrue(result.heightFeasible(),result.problems().toString());
        assertEquals(0,result.cutCells());assertEquals(0,result.fillCells());assertTrue(result.transitions().isEmpty());
        for(var band:result.bands()) assertEquals(band.componentId()==1?72:band.loopId()==1?69:64,band.baseY());
        assertFalse(result.executable());
    }

    @Test void missingWallAndPadSamplesCannotMasqueradeAsCompleteShortBands() {
        var loop=ring(0,0,0,0,0,5,true,true,level(24,64));
        for(int remove:List.of(0,15)){
            var bands=new ArrayList<>(loop.bands());var gate=bands.get(3);var samples=new ArrayList<>(gate.samples());samples.remove(remove);
            bands.set(3,new Band(gate.id(),gate.length(),gate.kind(),gate.facing(),samples,gate.stepAfter()));
            assertBlocked(propose(List.of(new Loop(0,0,true,bands,loop.seams()))),ProblemCode.INVALID_INPUT);
        }
    }

    @Test void cardinalGateCoverageAndOuterLoopIdentityAreRequired() {
        var loop = ring(0,0,0,0,0,5,true,false,level(24,64));
        assertBlocked(propose(List.of(loop)),ProblemCode.INVALID_INPUT);
        var badHole = ring(0,1,100,60,60,5,false,true,level(24,64));
        assertBlocked(propose(List.of(badHole)),ProblemCode.INVALID_INPUT);
    }

    @Test void outsidePadsNeverGainImplicitGradingPermission() {
        var loop = ring(0,0,0,0,0,5,true,true,level(24,64)); var bands=new ArrayList<>(loop.bands());
        var gate=bands.get(3);var samples=new ArrayList<>(gate.samples());
        for(int i=0;i<samples.size();i++)if(samples.get(i).role()==Role.OUTSIDE_GATE_PAD){var s=samples.get(i);samples.set(i,new Sample(s.x(),s.z(),s.surfaceY(),s.role(),true));break;}
        bands.set(3,new Band(gate.id(),gate.length(),gate.kind(),gate.facing(),samples,gate.stepAfter()));
        assertBlocked(propose(List.of(new Loop(0,0,true,bands,loop.seams()))),ProblemCode.INVALID_INPUT);
    }

    @Test void workAndAggregateGradingBudgetsRejectWithoutPartialOutputs() {
        var loop=ring(0,0,0,0,0,2,true,false,level(12,64));
        var bounded=propose(List.of(loop),limits(1,1,1,1000,1000,false));
        assertBlocked(bounded,ProblemCode.WORK_BUDGET);assertEquals(1,bounded.operations());
        int[] ground=level(12,64);ground[0]=65;
        assertBlocked(propose(List.of(ring(0,0,0,0,0,2,true,false,ground)),limits(0,1,100000,0,0,false)),ProblemCode.GRADING_BUDGET);
    }

    @Test void loopRotationSampleOrderAndComponentInputOrderCannotChangeChosenLabels() {
        var a=ring(0,0,0,-50,-60,5,true,true,level(24,64));
        var b=ring(1,0,100,30,30,5,true,true,level(24,68));
        var first=propose(List.of(a,b));var bands=new ArrayList<Band>();
        for(var band:a.bands()){var samples=new ArrayList<>(band.samples());Collections.reverse(samples);bands.add(new Band(band.id(),band.length(),band.kind(),band.facing(),samples,band.stepAfter()));}
        Collections.rotate(bands,7);var seams=new ArrayList<>(a.seams());Collections.reverse(seams);
        assertEquals(first,propose(List.of(b,new Loop(0,0,true,bands,seams))));
        assertThrows(UnsupportedOperationException.class,()->first.bands().clear());
        assertThrows(UnsupportedOperationException.class,()->first.seams().get(0).seam().lanes().clear());
    }

    @Test void exactDynamicProgramMatchesIndependentSmallLoopBruteForce() {
        var random=new java.util.Random(276);
        for(int fixture=0;fixture<32;fixture++){
            int[] ground=new int[12];for(int i=0;i<ground.length;i++)ground[i]=64+random.nextInt(fixture % 2 == 0 ? 3 : 5);
            var loop=ring(0,0,0,0,0,2,true,false,ground);var rules=limits(1,1,100000,10000,10000,false);
            var result=propose(List.of(loop),rules);long oracle=brute(loop,0,new int[12],rules,Long.MAX_VALUE);
            assertEquals(oracle!=Long.MAX_VALUE,result.heightFeasible(),"Fixture "+fixture);
            if(result.heightFeasible())assertEquals(oracle,result.cutCells()+result.fillCells(),"Fixture "+fixture);
            else assertBlocked(result,ProblemCode.NO_HEIGHT_PROFILE);
        }
    }

    static long brute(Loop loop,int at,int[] heights,Limits limits,long best){
        if(at==heights.length){
            Band last=loop.bands().get(at-1),first=loop.bands().get(0);int step=last.stepAfter()&&last.kind()==Kind.STRAIGHT&&first.kind()==Kind.STRAIGHT?1:0;
            if(Math.abs(heights[at-1]-heights[0])>step)return best;
            long cost=0;for(int i=0;i<at;i++)for(Sample sample:loop.bands().get(i).samples())cost+=Math.abs(heights[i]-sample.surfaceY());
            return Math.min(best,cost);
        }
        Band band=loop.bands().get(at);int low=limits.minBaseY(),high=limits.maxBaseY();
        for(Sample sample:band.samples()){low=Math.max(low,sample.surfaceY()-(sample.gradingAllowed()?limits.maxCut():0));high=Math.min(high,sample.surfaceY()+(sample.gradingAllowed()?limits.maxFill():0));}
        for(int y=low;y<=high;y++){
            if(at>0){Band previous=loop.bands().get(at-1);int step=previous.stepAfter()&&previous.kind()==Kind.STRAIGHT&&band.kind()==Kind.STRAIGHT?1:0;if(Math.abs(y-heights[at-1])>step)continue;}
            heights[at]=y;best=brute(loop,at+1,heights,limits,best);
        }
        return best;
    }
    private static void assertBlocked(Proposal proposal,ProblemCode code){
        assertFalse(proposal.heightFeasible());assertFalse(proposal.executable());assertEquals(code,proposal.problems().get(0).code());
        assertTrue(proposal.bands().isEmpty());assertTrue(proposal.columns().isEmpty());assertTrue(proposal.transitions().isEmpty());assertTrue(proposal.seams().isEmpty());
    }
}
