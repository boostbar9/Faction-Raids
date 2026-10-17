package com.devfarinsky.siegeoverhaul.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlanReviewHudTest {
    @Test void differentHeldPlansHaveOneDeterministicTextOverlay() {
        // Each renderer asks the same pure presentation predicate with its own plan type.
        assertTrue(PlanReviewHud.shouldDisplay(true,false));
        assertFalse(PlanReviewHud.shouldDisplay(false,true));
        assertTrue(PlanReviewHud.shouldDisplay(false,false)); // Non-plan main hand permits the offhand review.
        assertEquals(1, (PlanReviewHud.shouldDisplay(true,false)?1:0)+(PlanReviewHud.shouldDisplay(false,true)?1:0));
    }
    @Test void wrappedReviewStaysInsideTheViewportAndAboveTheHotbar() {
        for(int[] size:new int[][]{{320,240},{480,360},{854,480}}) {
            for(int count:new int[]{1,4,10,100}) {
                var box=PlanReviewHud.bounds(size[0],size[1],count);
                assertTrue(box.x()>=0 && box.y()>=0);
                assertTrue(box.x()+box.width()<=size[0]);
                assertTrue(box.y()+box.height()<=size[1]-60);
                assertTrue(box.width()>0 && box.height()>0 && box.lineCapacity()>0);
                assertTrue(Math.min(count,box.lineCapacity())*11+10<=box.height());
            }
        }
    }
    @Test void reviewCardStaysClearOfTheActionBarAndShrinksToItsText() {
        for(int[] size:new int[][]{{320,240},{480,360},{854,480},{1440,960}}) {
            for(int count:new int[]{1,4,10}) {
                var box=PlanReviewHud.bounds(size[0],size[1],count,180);
                boolean inCentreThird=box.x()+box.width()>size[0]/3 && box.x()<size[0]*2/3;
                boolean inActionBarBand=box.y()+box.height()>size[1]-80 && box.y()<size[1]-60;
                assertFalse(inCentreThird && inActionBarBand,"plan card must not cover the action bar");
                assertTrue(box.width()<=180+12);
            }
        }
        assertTrue(PlanReviewHud.bounds(854,480,3,40).width()<PlanReviewHud.bounds(854,480,3,280).width());
    }
}
