package com.devfarinsky.siegeoverhaul.client;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HeroCastShapeTest {
    List<List<Double>> geometry(int role,int phase,float age) {
        var points=new ArrayList<List<Double>>();
        HeroCastShape.emit(role,phase,age,(a,b,c,d,e,f)->points.add(List.of(a,b,c,d,e,f)));
        return points;
    }
    @Test void eachSchoolHasDistinctDeterministicChargeAndReleaseSilhouettes() {
        for(int phase=0;phase<=1;phase++) {
            var storm=geometry(22,phase,5);var fire=geometry(27,phase,5);var tide=geometry(29,phase,5);
            assertFalse(storm.isEmpty());assertFalse(fire.isEmpty());assertFalse(tide.isEmpty());
            assertNotEquals(storm,fire);assertNotEquals(storm,tide);assertNotEquals(fire,tide);
            assertEquals(storm,geometry(22,phase,5));
        }
    }
    @Test void completeAnimationIsFiniteBoundedAndHasVerticalVolume() {
        for(int role:new int[]{22,27,29})for(int phase=0;phase<=1;phase++)for(float age=0;age<20;age+=.25F) {
            var points=geometry(role,phase,age);assertTrue(points.size()<=HeroCastShape.MAX_SEGMENTS);
            for(var line:points)for(double n:line)assertTrue(Double.isFinite(n)&&Math.abs(n)<=11);
        }
        for(int role:new int[]{22,27,29}) {
            var heights=geometry(role,1,5).stream().flatMap(p->java.util.stream.Stream.of(p.get(1),p.get(4))).mapToDouble(Double::doubleValue).summaryStatistics();
            assertTrue(heights.getMax()-heights.getMin()>.5);
        }
    }
    @Test void cancelledExpiredAndInvalidCastsEmitNothing() {
        assertTrue(geometry(22,2,0).isEmpty());assertTrue(geometry(22,1,12).isEmpty());
        assertTrue(geometry(22,0,20).isEmpty());assertTrue(geometry(22,0,-1).isEmpty());
        assertTrue(geometry(22,0,Float.NaN).isEmpty());assertTrue(geometry(0,0,5).isEmpty());
    }
}
